package fr.atraxterra.dohy.upgrade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Les ameliorations permanentes, chargees depuis le datapack.
 *
 * <p>Ordre d'affichage : par prix du premier palier. Une map JSON n'a pas
 * d'ordre garanti, et un ecran dont les vignettes changent de place entre deux
 * ouvertures est inutilisable.
 */
@EventBusSubscriber
public final class UpgradeConfig {

    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "upgrades.json");

    private static volatile Config config = Config.FALLBACK;

    private UpgradeConfig() {
    }

    public static Config config() {
        return config;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        load(event.getServer());
    }

    public static boolean load(MinecraftServer server) {
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            DoHy.LOGGER.error("Configuration des ameliorations introuvable : data/{}/{}",
                    LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Configuration des ameliorations illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            DoHy.LOGGER.info("Ameliorations chargees : {} branches, {} paliers au total",
                    config.upgrades().size(), config.totalLevels());
            config.warnUnknownEffects();
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement des ameliorations", error);
            return false;
        }
    }

    /** Un palier : son prix, et l'amplitude de l'effet une fois atteint. */
    public record Level(long price, double amount) {
        static final Codec<Level> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("price").forGetter(Level::price),
                Codec.DOUBLE.fieldOf("amount").forGetter(Level::amount)
        ).apply(instance, Level::new));
    }

    /**
     * Une branche d'amelioration.
     *
     * <p>{@code amount} n'est pas cumulatif d'un palier a l'autre : c'est la
     * valeur totale a ce palier. Un cumul obligerait a relire tous les paliers
     * precedents et rendrait impossible une echelle non lineaire.
     */
    public record Upgrade(String label, String icon, String effect,
                          String description, List<Level> levels) {
        static final Codec<Upgrade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("label", "Amelioration").forGetter(Upgrade::label),
                Codec.STRING.optionalFieldOf("icon", "minecraft:paper").forGetter(Upgrade::icon),
                Codec.STRING.fieldOf("effect").forGetter(Upgrade::effect),
                Codec.STRING.optionalFieldOf("description", "").forGetter(Upgrade::description),
                Level.CODEC.listOf().optionalFieldOf("levels", List.of()).forGetter(Upgrade::levels)
        ).apply(instance, Upgrade::new));

        public int maxLevel() {
            return levels.size();
        }

        /** L'amplitude a ce niveau, ou zero s'il n'est pas atteint. */
        public double amountAt(int level) {
            return level <= 0 || level > levels.size() ? 0.0d : levels.get(level - 1).amount();
        }

        /** Le prix du prochain palier, ou {@link Optional#empty()} au maximum. */
        public Optional<Long> priceOf(int currentLevel) {
            return currentLevel >= levels.size()
                    ? Optional.empty()
                    : Optional.of(levels.get(currentLevel).price());
        }
    }

    public record Config(Map<String, Upgrade> upgrades) {

        static final Config FALLBACK = new Config(Map.of());

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Codec.STRING, Upgrade.CODEC)
                        .optionalFieldOf("upgrades", Map.of()).forGetter(Config::upgrades)
        ).apply(instance, Config::new));

        public Optional<Upgrade> get(String id) {
            return Optional.ofNullable(upgrades.get(id));
        }

        /** Les identifiants dans un ordre stable : du moins cher au plus cher. */
        public List<String> ordered() {
            List<String> ids = new ArrayList<>(upgrades.keySet());
            ids.sort((left, right) -> {
                long a = firstPrice(left);
                long b = firstPrice(right);
                return a != b ? Long.compare(a, b) : left.compareTo(right);
            });
            return ids;
        }

        private long firstPrice(String id) {
            Upgrade upgrade = upgrades.get(id);
            return upgrade == null || upgrade.levels().isEmpty()
                    ? Long.MAX_VALUE
                    : upgrade.levels().get(0).price();
        }

        public int totalLevels() {
            return upgrades.values().stream().mapToInt(Upgrade::maxLevel).sum();
        }

        /**
         * Signale un effet qu'aucune partie du code ne lit.
         *
         * <p>Meme filet que pour les perks de skill, et pour la meme raison : un
         * effet annonce au joueur mais branche nulle part est pire que pas
         * d'effet. Ce controle a ete ajoute apres avoir decouvert trente-trois
         * perks inertes.
         */
        void warnUnknownEffects() {
            upgrades.forEach((id, upgrade) -> {
                if (!UpgradeApi.isKnownEffect(upgrade.effect())) {
                    DoHy.LOGGER.warn("Amelioration '{}' : effet '{}' lu par personne",
                            id, upgrade.effect());
                }
            });
        }
    }
}

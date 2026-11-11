package fr.atraxterra.dohy.museum;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Les ailes du musee, leurs pieces et leurs recompenses, chargees depuis le
 * datapack ({@code data/dohy/museum.json}).
 *
 * <p>Les pieces sont <b>nommees une a une</b> : le musee n'expose que des objets
 * rares, et la rarete ne se deduit pas d'un catalogue de prix. Une piece
 * inconnue du jeu (un mod absent) est ignoree, et une aile sans piece ne
 * s'ouvre pas.
 */
@EventBusSubscriber
public final class MuseumConfig {

    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "museum.json");

    private static volatile Config config = Config.FALLBACK;

    private MuseumConfig() {
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
            DoHy.LOGGER.error("Configuration du musee introuvable : data/{}/{}",
                    LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Configuration du musee illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            Config parsed = result.getOrThrow();
            List<Identifier> absent = new ArrayList<>();
            Set<Identifier> seen = new HashSet<>();
            List<Wing> wings = new ArrayList<>();
            for (Wing wing : parsed.wings()) {
                wings.add(wing.resolved(seen, absent));
            }
            Wing patron = parsed.patron().resolved(seen, absent);
            config = new Config(parsed.globalXpPerDonation(), List.copyOf(wings), patron);

            int pieces = 0;
            int open = 0;
            for (Wing wing : wings) {
                pieces += wing.pieces().size();
                if (!wing.pieces().isEmpty()) {
                    open++;
                }
            }
            DoHy.LOGGER.info("Musee charge : {} ailes ouvertes sur {}, {} pieces, {} au panneau mecene",
                    open, wings.size(), pieces, patron.pieces().size());
            if (!absent.isEmpty()) {
                // Une piece d'un mod absent (NTM NEXT sans NTM) n'est pas une
                // erreur : l'aile se ferme. Une faute de frappe se lit ici.
                DoHy.LOGGER.info("Musee : pieces absentes de ce jeu, ignorees : {}", absent);
            }
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement de la configuration du musee", error);
            return false;
        }
    }

    /** Une recompense de completion. Memes types que les perks de metier. */
    public record Reward(String type, Optional<String> attribute, double amount,
                         Optional<String> operation) {
        static final Codec<Reward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(Reward::type),
                Codec.STRING.optionalFieldOf("attribute").forGetter(Reward::attribute),
                Codec.DOUBLE.optionalFieldOf("amount", 0.0d).forGetter(Reward::amount),
                Codec.STRING.optionalFieldOf("operation").forGetter(Reward::operation)
        ).apply(instance, Reward::new));
    }

    /**
     * Une aile : ses pieces, nommees une a une, et ce que sa completion accorde.
     *
     * @param id      la cle de l'aile, stable : elle nomme les modificateurs d'attribut
     * @param display le nom affiche
     * @param icon    l'objet de la carte ; a defaut, la premiere piece
     * @param items   les pieces demandees, dans l'ordre des vitrines
     * @param hints   ou trouver une piece, en une ligne, pour la vitrine vide
     * @param rewards les bonus de l'aile complete
     * @param pieces  les pieces qui existent dans ce jeu ; calcule au chargement
     */
    public record Wing(String id, String display, Optional<Identifier> icon, List<Identifier> items,
                       Map<Identifier, String> hints, List<Reward> rewards, List<Identifier> pieces) {

        static final Codec<Wing> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("id", "").forGetter(Wing::id),
                Codec.STRING.optionalFieldOf("display", "").forGetter(Wing::display),
                Identifier.CODEC.optionalFieldOf("icon").forGetter(Wing::icon),
                Identifier.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Wing::items),
                Codec.unboundedMap(Identifier.CODEC, Codec.STRING)
                        .optionalFieldOf("hints", Map.of()).forGetter(Wing::hints),
                Reward.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(Wing::rewards)
        ).apply(instance, (id, display, icon, items, hints, rewards) ->
                new Wing(id, display, icon, items, hints, rewards, List.of())));

        static final Wing NONE = new Wing("", "", Optional.empty(), List.of(), Map.of(), List.of(), List.of());

        /**
         * La meme aile, reduite aux pieces que ce jeu connait.
         *
         * <p>Une piece ne vit que dans une aile : la premiere qui la nomme la
         * garde, sinon un seul don completerait deux vitrines.
         */
        Wing resolved(Set<Identifier> seen, List<Identifier> absent) {
            List<Identifier> found = new ArrayList<>();
            for (Identifier item : items) {
                if (!BuiltInRegistries.ITEM.containsKey(item)) {
                    absent.add(item);
                } else if (seen.add(item)) {
                    found.add(item);
                }
            }
            return new Wing(id, display, icon, items, hints, rewards, List.copyOf(found));
        }

        public String name() {
            return display.isEmpty() ? id : display;
        }
    }

    public record Config(long globalXpPerDonation, List<Wing> wings, Wing patron) {

        public static final String PATRON = "patron";

        static final Wing FALLBACK_PATRON =
                new Wing(PATRON, "Mecene", Optional.empty(), List.of(), Map.of(), List.of(), List.of());

        static final Config FALLBACK = new Config(100L, List.of(), FALLBACK_PATRON);

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("global_xp_per_donation", 100L)
                        .forGetter(Config::globalXpPerDonation),
                Wing.CODEC.listOf().optionalFieldOf("wings", List.of()).forGetter(Config::wings),
                Wing.CODEC.optionalFieldOf("patron", FALLBACK_PATRON).forGetter(Config::patron)
        ).apply(instance, (xp, wings, patron) -> new Config(xp, wings, new Wing(PATRON,
                patron.display(), patron.icon(), patron.items(), patron.hints(), patron.rewards(),
                patron.pieces()))));

        /** L'aile de cette cle, panneau mecene compris ; une aile vide si elle n'existe pas. */
        public Wing wing(String id) {
            if (PATRON.equals(id)) {
                return patron;
            }
            for (Wing wing : wings) {
                if (wing.id().equals(id)) {
                    return wing;
                }
            }
            return Wing.NONE;
        }
    }
}

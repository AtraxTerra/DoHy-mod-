package fr.atraxterra.dohy.skill;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Courbes, sources d'XP et perks, charges depuis le datapack.
 *
 * <p>Les perks non encore implementes sont charges et ignores, avec un
 * avertissement unique au chargement : il vaut mieux que le fichier decrive la
 * cible complete et que le journal dise ce qui manque, qu'un fichier amputé qui
 * laisse croire que tout est en place.
 */
@EventBusSubscriber
public final class SkillConfig {

    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "skills.json");

    /** Types de perks reellement appliques aujourd'hui. */
    /**
     * Les types de perks reellement appliques.
     *
     * <p>Tenu a jour avec {@link SkillPerks} (attributs) et
     * {@link SkillEffects} (le reste). Un type absent d'ici est signale au
     * demarrage : c'est le filet qui a permis de decouvrir que trente-trois
     * perks etaient declares et inertes, et il doit le rester.
     */
    private static final Set<String> IMPLEMENTED = Set.of(
            "attribute", "double_drop_chance", "xp_bonus",
            "harvest_bonus", "tree_chop", "loot_bonus", "treasure_chance",
            "no_fall_damage_below", "anvil_cost_reduction");

    /**
     * Vrai si ce type de perk a un effet en jeu.
     *
     * <p>Expose pour que l'interface pose la question au lieu de tenir sa propre
     * liste : deux listes auraient derive, et c'est l'ecran des skills qui
     * aurait fini par mentir.
     */
    public static boolean isImplemented(String type) {
        return IMPLEMENTED.contains(type);
    }

    private static volatile Config config = Config.FALLBACK;

    private SkillConfig() {
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
            DoHy.LOGGER.error("Configuration des skills introuvable : data/{}/{}",
                    LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }

        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Configuration des skills illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            report(config);
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement de la configuration des skills", error);
            return false;
        }
    }

    private static void report(Config loaded) {
        int sources = loaded.skills().values().stream()
                .mapToInt(skill -> skill.sources().map(Sources::total).orElse(0))
                .sum();
        DoHy.LOGGER.info("Skills charges : {} skills, {} sources d'XP",
                loaded.skills().size(), sources);

        Set<String> pending = new TreeSet<>();
        loaded.skills().values().forEach(skill -> skill.perks()
                .forEach(perk -> {
                    if (!IMPLEMENTED.contains(perk.type())) {
                        pending.add(perk.type());
                    }
                }));
        if (!pending.isEmpty()) {
            DoHy.LOGGER.warn("Types de perks declares mais pas encore appliques : {}", pending);
        }
    }

    public record Curve(int maxLevel, double xpBase, double xpExponent,
                        double moneyBase, double moneyExponent, int perkEvery) {
        static final Codec<Curve> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("max_level", 50).forGetter(Curve::maxLevel),
                Codec.DOUBLE.optionalFieldOf("xp_base", 100.0d).forGetter(Curve::xpBase),
                Codec.DOUBLE.optionalFieldOf("xp_exponent", 1.8d).forGetter(Curve::xpExponent),
                Codec.DOUBLE.optionalFieldOf("money_base", 750.0d).forGetter(Curve::moneyBase),
                Codec.DOUBLE.optionalFieldOf("money_exponent", 1.5d).forGetter(Curve::moneyExponent),
                Codec.INT.optionalFieldOf("perk_every_n_levels", 5).forGetter(Curve::perkEvery)
        ).apply(instance, Curve::new));
    }

    /**
     * Sources d'XP. Les cles de {@code blocks} peuvent etre un identifiant ou
     * un tag prefixe de {@code #} ; la resolution est faite a l'usage, pour que
     * le chargement ne depende pas de l'ordre d'initialisation des registres.
     */
    public record Sources(Map<String, Long> blocks, Map<String, Long> entities, Map<String, Long> actions) {
        static final Codec<Sources> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("blocks", Map.of())
                        .forGetter(Sources::blocks),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("entities", Map.of())
                        .forGetter(Sources::entities),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("actions", Map.of())
                        .forGetter(Sources::actions)
        ).apply(instance, Sources::new));

        int total() {
            return blocks.size() + entities.size() + actions.size();
        }
    }

    public record Perk(int level, String type, Optional<String> attribute,
                       double amount, Optional<String> operation, Optional<String> appliesTo) {
        static final Codec<Perk> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("level").forGetter(Perk::level),
                Codec.STRING.fieldOf("type").forGetter(Perk::type),
                Codec.STRING.optionalFieldOf("attribute").forGetter(Perk::attribute),
                Codec.DOUBLE.optionalFieldOf("amount", 0.0d).forGetter(Perk::amount),
                Codec.STRING.optionalFieldOf("operation").forGetter(Perk::operation),
                Codec.STRING.optionalFieldOf("applies_to").forGetter(Perk::appliesTo)
        ).apply(instance, Perk::new));
    }

    public record SkillDef(Optional<Sources> sources, List<Perk> perks) {
        static final Codec<SkillDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Sources.CODEC.optionalFieldOf("sources").forGetter(SkillDef::sources),
                Perk.CODEC.listOf().optionalFieldOf("perks", List.of()).forGetter(SkillDef::perks)
        ).apply(instance, SkillDef::new));
    }

    public record Config(Curve curve, Map<String, SkillDef> skills) {

        /** Repli : la courbe de {@code BALANCE.md}, sans aucune source d'XP. */
        static final Config FALLBACK = new Config(
                new Curve(100, 100.0d, 1.6d, 750.0d, 1.4d, 5), Map.of());

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Curve.CODEC.optionalFieldOf("curve", FALLBACK.curve()).forGetter(Config::curve),
                Codec.unboundedMap(Codec.STRING, SkillDef.CODEC).optionalFieldOf("skills", Map.of())
                        .forGetter(Config::skills)
        ).apply(instance, Config::new));

        public SkillDef def(Skill skill) {
            return skills.getOrDefault(skill.key(), new SkillDef(Optional.empty(), List.of()));
        }
    }
}

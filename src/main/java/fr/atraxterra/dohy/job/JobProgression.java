package fr.atraxterra.dohy.job;

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
 * La progression des metiers : courbe, sources d'XP et perks, chargees depuis
 * le datapack ({@code job_progression.json}).
 *
 * <p>Les deblocages et les recompenses en objets vivent a cote, dans
 * {@code jobs.json} ({@link JobConfig}) : ceux-la sont generes depuis le
 * bytecode de PalaMod, alors que la progression est l'equilibrage propre a
 * DoHy. Deux fichiers parce qu'il y a deux sources de verite.
 *
 * <p>Les perks non implementes sont charges et ignores, avec un avertissement
 * unique au chargement : il vaut mieux que le fichier decrive la cible et que
 * le journal dise ce qui manque, qu'un fichier ampute qui laisse croire que
 * tout est en place.
 */
@EventBusSubscriber
public final class JobProgression {

    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "job_progression.json");

    /**
     * Les types de perks reellement appliques.
     *
     * <p>Tenu a jour avec {@link JobPerks} (attributs) et {@link JobEffects}
     * (le reste). Un type absent d'ici est signale au demarrage. L'abattage, la
     * trouvaille de peche, la chute amortie et la remise d'enclume sont partis
     * avec les skills qui les portaient.
     */
    private static final Set<String> IMPLEMENTED = Set.of(
            "attribute", "double_drop_chance", "xp_bonus", "harvest_bonus", "loot_bonus");

    /**
     * Vrai si ce type de perk a un effet en jeu.
     *
     * <p>Expose pour que l'interface pose la question au lieu de tenir sa propre
     * liste : deux listes auraient derive, et c'est l'ecran qui aurait fini par
     * mentir.
     */
    public static boolean isImplemented(String type) {
        return IMPLEMENTED.contains(type);
    }

    private static volatile Config config = Config.FALLBACK;

    private JobProgression() {
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
            DoHy.LOGGER.error("Progression des metiers introuvable : data/{}/{}",
                    LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }

        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Progression des metiers illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            report(config);
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement de la progression des metiers", error);
            return false;
        }
    }

    private static void report(Config loaded) {
        int sources = loaded.jobs().values().stream()
                .mapToInt(job -> job.sources().map(Sources::total).orElse(0))
                .sum();
        DoHy.LOGGER.info("Progression des metiers chargee : {} metiers, {} sources d'XP",
                loaded.jobs().size(), sources);

        Set<String> unknown = new TreeSet<>();
        loaded.jobs().keySet().forEach(key -> {
            if (Job.byKey(key) == null) {
                unknown.add(key);
            }
        });
        if (!unknown.isEmpty()) {
            DoHy.LOGGER.error("Metiers inconnus dans job_progression.json, ignores : {}", unknown);
        }

        Set<String> pending = new TreeSet<>();
        loaded.jobs().values().forEach(job -> job.perks()
                .forEach(perk -> {
                    if (!IMPLEMENTED.contains(perk.type())) {
                        pending.add(perk.type());
                    }
                }));
        if (!pending.isEmpty()) {
            DoHy.LOGGER.warn("Types de perks declares mais pas appliques : {}", pending);
        }
    }

    public record Curve(int maxLevel, double xpBase, double xpExponent,
                        double moneyBase, double moneyExponent, int perkEvery) {
        static final Codec<Curve> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("max_level", 100).forGetter(Curve::maxLevel),
                Codec.DOUBLE.optionalFieldOf("xp_base", 100.0d).forGetter(Curve::xpBase),
                Codec.DOUBLE.optionalFieldOf("xp_exponent", 1.6d).forGetter(Curve::xpExponent),
                Codec.DOUBLE.optionalFieldOf("money_base", 750.0d).forGetter(Curve::moneyBase),
                Codec.DOUBLE.optionalFieldOf("money_exponent", 1.4d).forGetter(Curve::moneyExponent),
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

    public record JobDef(Optional<Sources> sources, List<Perk> perks) {
        static final Codec<JobDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Sources.CODEC.optionalFieldOf("sources").forGetter(JobDef::sources),
                Perk.CODEC.listOf().optionalFieldOf("perks", List.of()).forGetter(JobDef::perks)
        ).apply(instance, JobDef::new));
    }

    public record Config(Curve curve, Map<String, JobDef> jobs) {

        /** Repli : la courbe de {@code BALANCE.md}, sans aucune source d'XP. */
        static final Config FALLBACK = new Config(
                new Curve(100, 100.0d, 1.6d, 750.0d, 1.4d, 5), Map.of());

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Curve.CODEC.optionalFieldOf("curve", FALLBACK.curve()).forGetter(Config::curve),
                Codec.unboundedMap(Codec.STRING, JobDef.CODEC).optionalFieldOf("jobs", Map.of())
                        .forGetter(Config::jobs)
        ).apply(instance, Config::new));

        public JobDef def(Job job) {
            return jobs.getOrDefault(job.key(), new JobDef(Optional.empty(), List.of()));
        }
    }
}

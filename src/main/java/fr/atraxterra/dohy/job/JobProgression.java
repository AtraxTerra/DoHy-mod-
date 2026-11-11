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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * La progression des metiers : courbe, primes de palier et sources d'XP,
 * chargees depuis le datapack ({@code job_progression.json}).
 *
 * <p>Les deblocages et les recompenses en objets vivent a cote, dans
 * {@code jobs.json} ({@link JobConfig}) : ceux-la sont generes depuis le
 * bytecode de PalaMod, alors que la progression est l'equilibrage propre a
 * DoHy. Deux fichiers parce qu'il y a deux sources de verite.
 *
 * <p>Les primes ont remplace les perks le 2026-11-11 : un palier paie de
 * l'argent ({@link JobPrimes}) au lieu d'accorder un attribut ou une chance de
 * drop. Une cle {@code perks} restee dans un ancien fichier est ignoree.
 */
@EventBusSubscriber
public final class JobProgression {

    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "job_progression.json");

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
    }

    /**
     * La courbe : XP et argent par niveau, et les primes de palier.
     *
     * @param primeEvery  un palier tous les N niveaux, 0 pour aucun
     * @param primeFactor la prime d'un palier, en multiple de l'argent de ce niveau
     */
    public record Curve(int maxLevel, double xpBase, double xpExponent,
                        double moneyBase, double moneyExponent, int primeEvery, double primeFactor) {
        static final Codec<Curve> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("max_level", 100).forGetter(Curve::maxLevel),
                Codec.DOUBLE.optionalFieldOf("xp_base", 100.0d).forGetter(Curve::xpBase),
                Codec.DOUBLE.optionalFieldOf("xp_exponent", 1.6d).forGetter(Curve::xpExponent),
                Codec.DOUBLE.optionalFieldOf("money_base", 750.0d).forGetter(Curve::moneyBase),
                Codec.DOUBLE.optionalFieldOf("money_exponent", 1.4d).forGetter(Curve::moneyExponent),
                Codec.INT.optionalFieldOf("prime_every_n_levels", 5).forGetter(Curve::primeEvery),
                Codec.DOUBLE.optionalFieldOf("prime_factor", 2.0d).forGetter(Curve::primeFactor)
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

    public record JobDef(Optional<Sources> sources) {
        static final Codec<JobDef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Sources.CODEC.optionalFieldOf("sources").forGetter(JobDef::sources)
        ).apply(instance, JobDef::new));
    }

    public record Config(Curve curve, Map<String, JobDef> jobs) {

        /** Repli : la courbe de {@code BALANCE.md}, sans aucune source d'XP. */
        static final Config FALLBACK = new Config(
                new Curve(100, 100.0d, 1.6d, 750.0d, 1.4d, 5, 2.0d), Map.of());

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Curve.CODEC.optionalFieldOf("curve", FALLBACK.curve()).forGetter(Config::curve),
                Codec.unboundedMap(Codec.STRING, JobDef.CODEC).optionalFieldOf("jobs", Map.of())
                        .forGetter(Config::jobs)
        ).apply(instance, Config::new));

        public JobDef def(Job job) {
            return jobs.getOrDefault(job.key(), new JobDef(Optional.empty()));
        }
    }
}

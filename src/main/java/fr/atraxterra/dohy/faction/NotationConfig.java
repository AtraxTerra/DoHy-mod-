package fr.atraxterra.dohy.faction;

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

/**
 * Les reglages de la notation de faction, charges depuis le datapack
 * ({@code data/dohy/notations.json}) : le plafond de chaque critere, ses
 * echelles, et ce que vaut un point.
 */
@EventBusSubscriber
public final class NotationConfig {

    private static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "notations.json");

    private static volatile Config config = Config.FALLBACK;

    private NotationConfig() {
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
            DoHy.LOGGER.error("Configuration des notations introuvable : data/{}/{}",
                    LOCATION.getNamespace(), LOCATION.getPath());
            return false;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Configuration des notations illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            double total = config.activity().max() + config.economy().max() + config.jobs().max()
                    + config.contracts().max() + config.military().max() + config.heritage().max()
                    + config.treasury().max();
            DoHy.LOGGER.info("Notations chargees : note sur {}, {} $ le point", total, config.boursePerPoint());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement de la configuration des notations", error);
            return false;
        }
    }

    /** Une lettre et la note a partir de laquelle elle s'obtient. */
    public record Grade(String label, double from) {
        static final Codec<Grade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("label").forGetter(Grade::label),
                Codec.DOUBLE.fieldOf("from").forGetter(Grade::from)
        ).apply(instance, Grade::new));
    }

    public record Activity(double max, int fullDays) {
        static final Codec<Activity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("max", 10.0d).forGetter(Activity::max),
                Codec.INT.optionalFieldOf("full_days", 5).forGetter(Activity::fullDays)
        ).apply(instance, Activity::new));
    }

    /** Une echelle logarithmique : 0 point a {@code zeroAt}, le plafond a {@code fullAt}. */
    public record LogScale(double max, long zeroAt, long fullAt) {
        static Codec<LogScale> codec(double max, long zeroAt, long fullAt) {
            return RecordCodecBuilder.create(instance -> instance.group(
                    Codec.DOUBLE.optionalFieldOf("max", max).forGetter(LogScale::max),
                    Codec.LONG.optionalFieldOf("zero_at", zeroAt).forGetter(LogScale::zeroAt),
                    Codec.LONG.optionalFieldOf("full_at", fullAt).forGetter(LogScale::fullAt)
            ).apply(instance, LogScale::new));
        }

        public double points(long amount) {
            if (amount <= zeroAt || fullAt <= zeroAt || zeroAt <= 0L) {
                return 0.0d;
            }
            double part = Math.log((double) amount / zeroAt) / Math.log((double) fullAt / zeroAt);
            return max * Math.max(0.0d, Math.min(1.0d, part));
        }
    }

    public record Jobs(double max) {
        static final Codec<Jobs> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("max", 15.0d).forGetter(Jobs::max)
        ).apply(instance, Jobs::new));
    }

    public record Contracts(double max, double perDaily, double perWeekly, double perOrder) {
        static final Codec<Contracts> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("max", 15.0d).forGetter(Contracts::max),
                Codec.DOUBLE.optionalFieldOf("per_daily", 0.5d).forGetter(Contracts::perDaily),
                Codec.DOUBLE.optionalFieldOf("per_weekly", 3.5d).forGetter(Contracts::perWeekly),
                Codec.DOUBLE.optionalFieldOf("per_order", 0.5d).forGetter(Contracts::perOrder)
        ).apply(instance, Contracts::new));
    }

    public record Military(double max, double perWin) {
        static final Codec<Military> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("max", 20.0d).forGetter(Military::max),
                Codec.DOUBLE.optionalFieldOf("per_win", 4.0d).forGetter(Military::perWin)
        ).apply(instance, Military::new));
    }

    public record Heritage(double max, int fullClaims) {
        static final Codec<Heritage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("max", 10.0d).forGetter(Heritage::max),
                Codec.INT.optionalFieldOf("full_claims", 16).forGetter(Heritage::fullClaims)
        ).apply(instance, Heritage::new));
    }

    public record Config(long boursePerPoint, List<Grade> grades, Activity activity, LogScale economy, Jobs jobs,
                         Contracts contracts, Military military, Heritage heritage, LogScale treasury) {

        private static final List<Grade> GRADES = List.of(new Grade("S", 85.0d), new Grade("A", 70.0d),
                new Grade("B", 50.0d), new Grade("C", 30.0d), new Grade("D", 0.0d));

        static final Config FALLBACK = new Config(2500L, GRADES, new Activity(10.0d, 5),
                new LogScale(20.0d, 10_000L, 10_000_000L), new Jobs(15.0d),
                new Contracts(15.0d, 0.5d, 3.5d, 0.5d), new Military(20.0d, 4.0d), new Heritage(10.0d, 16),
                new LogScale(10.0d, 100_000L, 100_000_000L));

        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("bourse_per_point", 2500L).forGetter(Config::boursePerPoint),
                Grade.CODEC.listOf().optionalFieldOf("grades", GRADES).forGetter(Config::grades),
                Activity.CODEC.optionalFieldOf("activity", FALLBACK.activity()).forGetter(Config::activity),
                LogScale.codec(20.0d, 10_000L, 10_000_000L).optionalFieldOf("economy", FALLBACK.economy())
                        .forGetter(Config::economy),
                Jobs.CODEC.optionalFieldOf("jobs", FALLBACK.jobs()).forGetter(Config::jobs),
                Contracts.CODEC.optionalFieldOf("contracts", FALLBACK.contracts()).forGetter(Config::contracts),
                Military.CODEC.optionalFieldOf("military", FALLBACK.military()).forGetter(Config::military),
                Heritage.CODEC.optionalFieldOf("heritage", FALLBACK.heritage()).forGetter(Config::heritage),
                LogScale.codec(10.0d, 100_000L, 100_000_000L).optionalFieldOf("treasury", FALLBACK.treasury())
                        .forGetter(Config::treasury)
        ).apply(instance, Config::new));

        /** La lettre d'une note : la premiere dont le seuil est atteint, dans l'ordre du fichier. */
        public String grade(double total) {
            for (Grade grade : grades) {
                if (total >= grade.from()) {
                    return grade.label();
                }
            }
            return grades.isEmpty() ? "-" : grades.get(grades.size() - 1).label();
        }
    }
}

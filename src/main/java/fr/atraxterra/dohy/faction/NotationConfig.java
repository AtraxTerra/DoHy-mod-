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
            double total = config.activity().total() + config.economy().max() + config.trade().max()
                    + config.military().max() + config.territory().max();
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

    public record Activity(double max, int fullDays, double contractsMax, double perDaily, double perWeekly,
                           double perOrder) {
        static final Codec<Activity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("max", 10.0d).forGetter(Activity::max),
                Codec.INT.optionalFieldOf("full_days", 5).forGetter(Activity::fullDays),
                Codec.DOUBLE.optionalFieldOf("contracts_max", 10.0d).forGetter(Activity::contractsMax),
                Codec.DOUBLE.optionalFieldOf("per_daily", 0.4d).forGetter(Activity::perDaily),
                Codec.DOUBLE.optionalFieldOf("per_weekly", 3.0d).forGetter(Activity::perWeekly),
                Codec.DOUBLE.optionalFieldOf("per_order", 0.4d).forGetter(Activity::perOrder)
        ).apply(instance, Activity::new));

        public double total() {
            return max + contractsMax;
        }
    }

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

    public record Military(double max, double perWin) {
        static final Codec<Military> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("max", 20.0d).forGetter(Military::max),
                Codec.DOUBLE.optionalFieldOf("per_win", 4.0d).forGetter(Military::perWin)
        ).apply(instance, Military::new));
    }

    /**
     * Le territoire : la capitale, les pays (racine du nombre tenu sur
     * {@code fullCountries}), les claims et le musee. Sans carte de la Terre,
     * les points de la capitale et des pays vont aux claims et au musee.
     */
    public record Territory(double capital, double countries, int fullCountries, double claims, int fullClaims,
                            double museum) {
        static final Codec<Territory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("capital", 3.0d).forGetter(Territory::capital),
                Codec.DOUBLE.optionalFieldOf("countries", 12.0d).forGetter(Territory::countries),
                Codec.INT.optionalFieldOf("full_countries", 12).forGetter(Territory::fullCountries),
                Codec.DOUBLE.optionalFieldOf("claims", 5.0d).forGetter(Territory::claims),
                Codec.INT.optionalFieldOf("full_claims", 16).forGetter(Territory::fullClaims),
                Codec.DOUBLE.optionalFieldOf("museum", 5.0d).forGetter(Territory::museum)
        ).apply(instance, Territory::new));

        public double max() {
            return capital + countries + claims + museum;
        }
    }

    /**
     * Ce que rapporte la semaine en plus de la bourse.
     *
     * @param conquestPerPoint points de conquete sur le pays vise, par point de note
     * @param podium           le multiplicateur de ces points pour le 1er, le 2e et le 3e
     * @param lotValue         la valeur d'un lot de ressources, au prix de base
     * @param prestigeClaims   le plafond des claims gagnes en finissant premier
     */
    public record Rewards(double conquestPerPoint, List<Double> podium, long lotValue, int prestigeClaims) {
        static final Rewards DEFAULT = new Rewards(5.0d, List.of(2.0d, 1.5d, 1.25d), 10_000L, 8);
        static final Codec<Rewards> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("conquest_per_point", 5.0d).forGetter(Rewards::conquestPerPoint),
                Codec.DOUBLE.listOf().optionalFieldOf("podium", DEFAULT.podium()).forGetter(Rewards::podium),
                Codec.LONG.optionalFieldOf("lot_value", 10_000L).forGetter(Rewards::lotValue),
                Codec.INT.optionalFieldOf("prestige_claims", 8).forGetter(Rewards::prestigeClaims)
        ).apply(instance, Rewards::new));

        /** Le multiplicateur de ce rang (1 pour le premier) ; 1 hors du podium. */
        public double multiplier(int rank) {
            return rank >= 1 && rank <= podium.size() ? podium.get(rank - 1) : 1.0d;
        }

        /** Les lots de ressources de ce rang : trois, deux, un, puis rien. */
        public int lots(int rank) {
            return rank >= 1 && rank <= 3 ? 4 - rank : 0;
        }
    }

    public record Config(long boursePerPoint, List<Grade> grades, Activity activity, LogScale economy,
                         LogScale trade, Military military, Territory territory, Rewards rewards) {
        private static final List<Grade> GRADES = List.of(new Grade("S", 85.0d), new Grade("A", 70.0d),
                new Grade("B", 50.0d), new Grade("C", 30.0d), new Grade("D", 0.0d));
        static final Config FALLBACK = new Config(2500L, GRADES, new Activity(10.0d, 5, 10.0d, 0.4d, 3.0d, 0.4d),
                new LogScale(20.0d, 10_000L, 10_000_000L), new LogScale(15.0d, 5_000L, 2_000_000L),
                new Military(20.0d, 4.0d), new Territory(3.0d, 12.0d, 12, 5.0d, 16, 5.0d), Rewards.DEFAULT);
        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("bourse_per_point", 2500L).forGetter(Config::boursePerPoint),
                Grade.CODEC.listOf().optionalFieldOf("grades", GRADES).forGetter(Config::grades),
                Activity.CODEC.optionalFieldOf("activity", FALLBACK.activity()).forGetter(Config::activity),
                LogScale.codec(20.0d, 10_000L, 10_000_000L).optionalFieldOf("economy", FALLBACK.economy())
                        .forGetter(Config::economy),
                LogScale.codec(15.0d, 5_000L, 2_000_000L).optionalFieldOf("trade", FALLBACK.trade())
                        .forGetter(Config::trade),
                Military.CODEC.optionalFieldOf("military", FALLBACK.military()).forGetter(Config::military),
                Territory.CODEC.optionalFieldOf("territory", FALLBACK.territory()).forGetter(Config::territory),
                Rewards.CODEC.optionalFieldOf("rewards", Rewards.DEFAULT).forGetter(Config::rewards)
        ).apply(instance, Config::new));

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

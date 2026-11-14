package fr.atraxterra.dohy.earth.disaster;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

/**
 * La table des catastrophes de la Terre ({@code data/dohy/earth/disasters.json},
 * ecrite par {@code tools/earth_disasters.py}) : les volcans, les zones
 * sismiques, les bassins cycloniques, les regions de mousson et les reglages.
 *
 * <p>Tout est donne en latitude et longitude, comme les lieux de la Terre : la
 * table vaut pour toute carte calee sur le globe. Les positions en blocs sont
 * calculees pour la carte du monde ouvert ({@link #placed()}).
 */
public final class DisasterConfig {

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "earth/disasters.json");

    /** Un kilometre a l'equateur, en degres de latitude. */
    private static final double KM_PER_DEGREE = 111.32d;

    /**
     * Les reglages : les durees moyennes entre deux catastrophes (en minutes de presence d'un joueur dans la
     * region, ou dans le monde pour les nouvelles lointaines), le delai du retour du terrain.
     */
    public record Settings(int checkSeconds, double quakeMinutes, double quakeNewsMinutes, double eruptionMinutes,
                           int eruptionNearBlocks, double eruptionNewsMinutes, int eruptionWarningSeconds,
                           double hurricaneMinutes, double hurricaneTargetShare, double monsoonMinutes,
                           double restoreHours, int maxActive) {
        static final Settings DEFAULT = new Settings(20, 45.0d, 150.0d, 75.0d, 400, 300.0d, 300, 80.0d, 0.65d,
                25.0d, 3.0d, 6);

        static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(5, 600).optionalFieldOf("check_seconds", 20).forGetter(Settings::checkSeconds),
                Codec.doubleRange(1.0d, 100_000.0d).optionalFieldOf("quake_minutes", 45.0d)
                        .forGetter(Settings::quakeMinutes),
                Codec.doubleRange(1.0d, 100_000.0d).optionalFieldOf("quake_news_minutes", 150.0d)
                        .forGetter(Settings::quakeNewsMinutes),
                Codec.doubleRange(1.0d, 100_000.0d).optionalFieldOf("eruption_minutes", 75.0d)
                        .forGetter(Settings::eruptionMinutes),
                Codec.intRange(16, 10_000).optionalFieldOf("eruption_near_blocks", 400)
                        .forGetter(Settings::eruptionNearBlocks),
                Codec.doubleRange(1.0d, 100_000.0d).optionalFieldOf("eruption_news_minutes", 300.0d)
                        .forGetter(Settings::eruptionNewsMinutes),
                Codec.intRange(0, 3600).optionalFieldOf("eruption_warning_seconds", 300)
                        .forGetter(Settings::eruptionWarningSeconds),
                Codec.doubleRange(1.0d, 100_000.0d).optionalFieldOf("hurricane_minutes", 80.0d)
                        .forGetter(Settings::hurricaneMinutes),
                Codec.doubleRange(0.0d, 1.0d).optionalFieldOf("hurricane_target_share", 0.65d)
                        .forGetter(Settings::hurricaneTargetShare),
                Codec.doubleRange(1.0d, 100_000.0d).optionalFieldOf("monsoon_minutes", 25.0d)
                        .forGetter(Settings::monsoonMinutes),
                Codec.doubleRange(0.01d, 1000.0d).optionalFieldOf("restore_hours", 3.0d)
                        .forGetter(Settings::restoreHours),
                Codec.intRange(1, 64).optionalFieldOf("max_active", 6).forGetter(Settings::maxActive)
        ).apply(instance, Settings::new));
    }

    /** Un volcan actif : son sommet, deja pose sur la terre de la carte par l'outil. */
    public record Volcano(String id, String name, String country, double latitude, double longitude) {
        static final Codec<Volcano> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Volcano::id),
                Codec.STRING.fieldOf("name").forGetter(Volcano::name),
                Codec.STRING.optionalFieldOf("country", "").forGetter(Volcano::country),
                Codec.doubleRange(-90.0d, 90.0d).fieldOf("latitude").forGetter(Volcano::latitude),
                Codec.doubleRange(-180.0d, 180.0d).fieldOf("longitude").forGetter(Volcano::longitude)
        ).apply(instance, Volcano::new));
    }

    /** Une zone sismique : une ligne de failles ou de fosse, et sa demi-largeur. */
    public record Zone(String id, String name, List<List<Double>> points, double widthKm, double maxMagnitude,
                       double weight) {
        static final Codec<Zone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Zone::id),
                Codec.STRING.fieldOf("name").forGetter(Zone::name),
                Codec.DOUBLE.listOf().listOf().fieldOf("points").forGetter(Zone::points),
                Codec.doubleRange(10.0d, 2000.0d).optionalFieldOf("width_km", 150.0d).forGetter(Zone::widthKm),
                Codec.doubleRange(4.0d, 10.0d).optionalFieldOf("max_magnitude", 8.0d).forGetter(Zone::maxMagnitude),
                Codec.doubleRange(0.0d, 100.0d).optionalFieldOf("weight", 1.0d).forGetter(Zone::weight)
        ).apply(instance, Zone::new));
    }

    /** La ou naissent les tempetes d'un bassin, et le cap qu'elles prennent (degres, 0 au nord, 90 a l'est). */
    public record Genesis(double south, double north, double west, double east, double heading) {
        static final Codec<Genesis> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.fieldOf("south").forGetter(Genesis::south),
                Codec.DOUBLE.fieldOf("north").forGetter(Genesis::north),
                Codec.DOUBLE.fieldOf("west").forGetter(Genesis::west),
                Codec.DOUBLE.fieldOf("east").forGetter(Genesis::east),
                Codec.DOUBLE.fieldOf("heading").forGetter(Genesis::heading)
        ).apply(instance, Genesis::new));
    }

    /**
     * Un bassin cyclonique.
     *
     * @param word            ouragan, typhon ou cyclone
     * @param season          la fenetre de l'annee locale ou il est actif (0 ouvre l'hiver, 0,5 l'ete)
     * @param recurveLatitude passee cette latitude, la tempete remonte vers le pole et l'est
     * @param area            sud, nord, ouest, est : la region que ses tempetes balaient
     */
    public record Basin(String id, String name, String word, List<Double> season, double weight,
                        double recurveLatitude, List<Double> area, List<Genesis> genesis) {
        static final Codec<Basin> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Basin::id),
                Codec.STRING.fieldOf("name").forGetter(Basin::name),
                Codec.STRING.optionalFieldOf("word", "ouragan").forGetter(Basin::word),
                Codec.DOUBLE.listOf().fieldOf("season").forGetter(Basin::season),
                Codec.doubleRange(0.0d, 100.0d).optionalFieldOf("weight", 1.0d).forGetter(Basin::weight),
                Codec.DOUBLE.fieldOf("recurve_latitude").forGetter(Basin::recurveLatitude),
                Codec.DOUBLE.listOf().fieldOf("area").forGetter(Basin::area),
                Genesis.CODEC.listOf().fieldOf("genesis").forGetter(Basin::genesis)
        ).apply(instance, Basin::new));

        public boolean southern() {
            return recurveLatitude < 0.0d;
        }

        /** Vrai si ce point (en degres) est dans la region du bassin. */
        public boolean covers(double latitude, double longitude) {
            return area.size() >= 4 && latitude >= area.get(0) && latitude <= area.get(1)
                    && longitude >= area.get(2) && longitude <= area.get(3);
        }

        /** Vrai si la phase locale de l'hemisphere du bassin est dans sa saison. */
        public boolean inSeason(double local) {
            return season.size() >= 2 && local >= season.get(0) && local <= season.get(1);
        }

        public double referenceLatitude() {
            return area.size() >= 2 ? (area.get(0) + area.get(1)) / 2.0d : recurveLatitude;
        }
    }

    /** Une region de mousson : un cercle, et sa saison des pluies (la moitie chaude de l'annee). */
    public record Monsoon(String id, String name, double latitude, double longitude, double radiusKm) {
        static final Codec<Monsoon> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Monsoon::id),
                Codec.STRING.fieldOf("name").forGetter(Monsoon::name),
                Codec.doubleRange(-90.0d, 90.0d).fieldOf("latitude").forGetter(Monsoon::latitude),
                Codec.doubleRange(-180.0d, 180.0d).fieldOf("longitude").forGetter(Monsoon::longitude),
                Codec.doubleRange(10.0d, 5000.0d).fieldOf("radius_km").forGetter(Monsoon::radiusKm)
        ).apply(instance, Monsoon::new));
    }

    public record Data(int version, Settings settings, List<Volcano> volcanoes, List<Zone> zones, List<Basin> basins,
                       List<Monsoon> monsoons, List<String> names) {
        static final Data EMPTY = new Data(1, Settings.DEFAULT, List.of(), List.of(), List.of(), List.of(),
                List.of("Alice"));

        static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 1_000_000).optionalFieldOf("version", 1).forGetter(Data::version),
                Settings.CODEC.optionalFieldOf("settings", Settings.DEFAULT).forGetter(Data::settings),
                Volcano.CODEC.listOf().optionalFieldOf("volcanoes", List.of()).forGetter(Data::volcanoes),
                Zone.CODEC.listOf().optionalFieldOf("zones", List.of()).forGetter(Data::zones),
                Basin.CODEC.listOf().optionalFieldOf("basins", List.of()).forGetter(Data::basins),
                Monsoon.CODEC.listOf().optionalFieldOf("monsoons", List.of()).forGetter(Data::monsoons),
                Codec.STRING.listOf().optionalFieldOf("names", List.of("Alice")).forGetter(Data::names)
        ).apply(instance, Data::new));
    }

    // -- Sur la carte ---------------------------------------------------------------------

    /** Un volcan sur la carte : son centre au milieu de son chunk (le cratere y tient tout entier). */
    public record PlacedVolcano(Volcano volcano, int x, int z) {
        public double distance(double px, double pz) {
            return Math.hypot(px - x, pz - z);
        }
    }

    /** Une zone sismique sur la carte : sa ligne en blocs et sa demi-largeur en blocs. */
    public record PlacedZone(Zone zone, double[] xs, double[] zs, double half) {
        /** La distance de ce point a la ligne de la zone. */
        public double distance(double px, double pz) {
            double best = Double.MAX_VALUE;
            if (xs.length == 1) {
                return Math.hypot(px - xs[0], pz - zs[0]);
            }
            for (int i = 0; i + 1 < xs.length; i++) {
                best = Math.min(best, segment(px, pz, xs[i], zs[i], xs[i + 1], zs[i + 1]));
            }
            return best;
        }

        public boolean contains(double px, double pz) {
            return distance(px, pz) <= half;
        }

        /** Un point de la ligne, tire au hasard selon la longueur des segments ; {@code t} de 0 a 1. */
        public double[] along(double t) {
            if (xs.length == 1) {
                return new double[] {xs[0], zs[0]};
            }
            double total = 0.0d;
            for (int i = 0; i + 1 < xs.length; i++) {
                total += Math.hypot(xs[i + 1] - xs[i], zs[i + 1] - zs[i]);
            }
            double wanted = t * total;
            for (int i = 0; i + 1 < xs.length; i++) {
                double length = Math.hypot(xs[i + 1] - xs[i], zs[i + 1] - zs[i]);
                if (wanted <= length || i + 2 == xs.length) {
                    double f = length <= 0.0d ? 0.0d : Math.min(1.0d, wanted / length);
                    return new double[] {xs[i] + (xs[i + 1] - xs[i]) * f, zs[i] + (zs[i + 1] - zs[i]) * f};
                }
                wanted -= length;
            }
            return new double[] {xs[0], zs[0]};
        }

        private static double segment(double px, double pz, double ax, double az, double bx, double bz) {
            double dx = bx - ax;
            double dz = bz - az;
            double length = dx * dx + dz * dz;
            double t = length <= 0.0d ? 0.0d : Math.max(0.0d, Math.min(1.0d, ((px - ax) * dx + (pz - az) * dz) / length));
            return Math.hypot(px - (ax + dx * t), pz - (az + dz * t));
        }
    }

    /** Une region de mousson sur la carte. */
    public record PlacedMonsoon(Monsoon monsoon, double x, double z, double radius) {
        public boolean contains(double px, double pz) {
            return Math.hypot(px - x, pz - z) <= radius;
        }
    }

    public record Placed(List<PlacedVolcano> volcanoes, List<PlacedZone> zones, List<PlacedMonsoon> monsoons) {
        static final Placed EMPTY = new Placed(List.of(), List.of(), List.of());
    }

    private static volatile Data data = Data.EMPTY;
    private static volatile Placed placed = Placed.EMPTY;
    private static volatile EarthData placedFor;

    private DisasterConfig() {
    }

    public static Data data() {
        return data;
    }

    public static Settings settings() {
        return data.settings();
    }

    static boolean load(MinecraftServer server) {
        data = Data.EMPTY;
        placed = Placed.EMPTY;
        placedFor = null;
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            return true;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Data.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Catastrophes de la Terre illisibles : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            data = result.getOrThrow();
            DoHy.LOGGER.info("Catastrophes de la Terre : {} volcans, {} zones sismiques, {} bassins cycloniques, "
                    + "{} regions de mousson", data.volcanoes().size(), data.zones().size(), data.basins().size(),
                    data.monsoons().size());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement des catastrophes de la Terre", error);
            return false;
        }
    }

    static void clear() {
        data = Data.EMPTY;
        placed = Placed.EMPTY;
        placedFor = null;
    }

    /** Des kilometres en blocs, a cette latitude (la projection etire les longitudes vers les poles). */
    static double blocks(EarthData earth, double km, double latitude) {
        double cosine = Math.max(0.2d, Math.cos(Math.toRadians(latitude)));
        return km * earth.config().blocksPerDegree() / (KM_PER_DEGREE * cosine);
    }

    /** Les volcans, zones et regions places sur la carte de ce monde ; vide sans carte. */
    public static Placed placed() {
        EarthData earth = EarthData.get();
        if (earth == null) {
            return Placed.EMPTY;
        }
        if (placedFor != earth) {
            List<PlacedVolcano> volcanoes = new ArrayList<>();
            for (Volcano volcano : data.volcanoes()) {
                int x = (int) Math.floor(earth.x(volcano.longitude()));
                int z = (int) Math.floor(earth.z(volcano.latitude()));
                volcanoes.add(new PlacedVolcano(volcano, (x >> 4 << 4) + 8, (z >> 4 << 4) + 8));
            }
            List<PlacedZone> zones = new ArrayList<>();
            for (Zone zone : data.zones()) {
                int count = zone.points().size();
                double[] xs = new double[count];
                double[] zs = new double[count];
                double latitude = 0.0d;
                for (int i = 0; i < count; i++) {
                    List<Double> point = zone.points().get(i);
                    xs[i] = earth.x(point.get(1));
                    zs[i] = earth.z(point.get(0));
                    latitude += point.get(0);
                }
                if (count > 0) {
                    zones.add(new PlacedZone(zone, xs, zs, blocks(earth, zone.widthKm(), latitude / count)));
                }
            }
            List<PlacedMonsoon> monsoons = new ArrayList<>();
            for (Monsoon monsoon : data.monsoons()) {
                monsoons.add(new PlacedMonsoon(monsoon, earth.x(monsoon.longitude()), earth.z(monsoon.latitude()),
                        blocks(earth, monsoon.radiusKm(), monsoon.latitude())));
            }
            placed = new Placed(List.copyOf(volcanoes), List.copyOf(zones), List.copyOf(monsoons));
            placedFor = earth;
        }
        return placed;
    }

    /** Le volcan de cet identifiant, ou {@code null}. */
    public static PlacedVolcano volcano(String id) {
        for (PlacedVolcano volcano : placed().volcanoes()) {
            if (volcano.volcano().id().equals(id)) {
                return volcano;
            }
        }
        return null;
    }

    /** Le volcan le plus proche de ce point, ou {@code null}. */
    public static PlacedVolcano nearestVolcano(double x, double z) {
        PlacedVolcano best = null;
        for (PlacedVolcano volcano : placed().volcanoes()) {
            if (best == null || volcano.distance(x, z) < best.distance(x, z)) {
                best = volcano;
            }
        }
        return best;
    }

    /** La zone sismique ou est ce point (la plus proche de sa ligne), ou {@code null}. */
    public static PlacedZone zoneAt(double x, double z) {
        PlacedZone best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PlacedZone zone : placed().zones()) {
            double distance = zone.distance(x, z);
            if (distance <= zone.half() && distance < bestDistance) {
                best = zone;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** La region de mousson ou est ce point, ou {@code null}. */
    public static PlacedMonsoon monsoonAt(double x, double z) {
        for (PlacedMonsoon monsoon : placed().monsoons()) {
            if (monsoon.contains(x, z)) {
                return monsoon;
            }
        }
        return null;
    }
}

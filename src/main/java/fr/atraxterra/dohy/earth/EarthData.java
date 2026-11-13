package fr.atraxterra.dohy.earth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Une carte de la Terre calee sur le globe : ou est-on, en latitude et en
 * longitude, et dans quel pays.
 *
 * <p>Rien de cela n'existe dans un monde ordinaire. C'est un datapack qui
 * l'apporte, pour <i>une</i> carte donnee : il remplace
 * {@code data/dohy/earth/earth.json} (livre eteint par le mod) par le calage
 * de sa carte — projection, blocs par degre, ou tombent le meridien de
 * Greenwich et l'equateur — et fournit la grille des pays, un fichier binaire
 * compresse. Sans ce datapack, {@link #get()} rend {@code null} et tout ce
 * qui s'appuie dessus reste inerte.
 *
 * <p>La grille : une case pour quelques blocs, deux octets par case, ligne par
 * ligne du nord au sud. Les quinze bits de poids faible sont le numero du pays
 * (0 : la haute mer) ; le bit de poids fort marque une case qui porte un pays
 * sans etre une terre — ses eaux cotieres.
 */
public final class EarthData {

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "earth/earth.json");
    private static final int WATER = 0x8000;

    private static volatile EarthData current;

    /** Un pays de la grille. {@code color} est un numero de teinte de carte, de 1 a 7. */
    public record Country(int id, String code, String name, int color) {
        static final Codec<Country> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 0x7FFF).fieldOf("id").forGetter(Country::id),
                Codec.STRING.fieldOf("code").forGetter(Country::code),
                Codec.STRING.fieldOf("name").forGetter(Country::name),
                Codec.intRange(0, 15).optionalFieldOf("color", 1).forGetter(Country::color)
        ).apply(instance, Country::new));
    }

    /** Ou la grille est posee dans le monde : son coin nord-ouest, la taille d'une case, ses dimensions. */
    public record Grid(Identifier file, int minX, int minZ, int cell, int width, int height) {
        static final Grid NONE = new Grid(Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "earth/countries.bin"),
                0, 0, 8, 0, 0);

        static final Codec<Grid> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("file").forGetter(Grid::file),
                Codec.INT.fieldOf("min_x").forGetter(Grid::minX),
                Codec.INT.fieldOf("min_z").forGetter(Grid::minZ),
                Codec.intRange(1, 1024).fieldOf("cell").forGetter(Grid::cell),
                Codec.intRange(0, 16384).fieldOf("width").forGetter(Grid::width),
                Codec.intRange(0, 16384).fieldOf("height").forGetter(Grid::height)
        ).apply(instance, Grid::new));
    }

    /**
     * Le calage d'une carte.
     *
     * @param projection      {@code miller} (cylindrique de Miller) ou {@code equirectangular}
     * @param blocksPerDegree blocs par degre de longitude
     * @param originX         le x du meridien de Greenwich
     * @param equatorZ        le z de l'equateur
     */
    public record Config(boolean enabled, Identifier dimension, String projection, double blocksPerDegree,
                         double originX, double equatorZ, Grid grid, List<Country> countries) {
        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enabled", false).forGetter(Config::enabled),
                Identifier.CODEC.optionalFieldOf("dimension", Identifier.withDefaultNamespace("overworld"))
                        .forGetter(Config::dimension),
                Codec.STRING.optionalFieldOf("projection", "miller").forGetter(Config::projection),
                Codec.doubleRange(0.01d, 100_000.0d).optionalFieldOf("blocks_per_degree", 64.0d)
                        .forGetter(Config::blocksPerDegree),
                Codec.DOUBLE.optionalFieldOf("origin_x", 0.0d).forGetter(Config::originX),
                Codec.DOUBLE.optionalFieldOf("equator_z", 0.0d).forGetter(Config::equatorZ),
                Grid.CODEC.optionalFieldOf("grid", Grid.NONE).forGetter(Config::grid),
                Country.CODEC.listOf().optionalFieldOf("countries", List.of()).forGetter(Config::countries)
        ).apply(instance, Config::new));
    }

    private final Config config;
    private final ResourceKey<Level> dimension;
    private final boolean miller;
    private final short[] cells;
    private final Country[] byId;
    private final Map<String, Country> byCode = new HashMap<>();

    private EarthData(Config config, short[] cells) {
        this.config = config;
        this.dimension = ResourceKey.create(Registries.DIMENSION, config.dimension());
        this.miller = !config.projection().equals("equirectangular");
        this.cells = cells;
        int top = 0;
        for (Country country : config.countries()) {
            top = Math.max(top, country.id());
        }
        this.byId = new Country[top + 1];
        for (Country country : config.countries()) {
            byId[country.id()] = country;
            byCode.put(country.code(), country);
        }
    }

    /** La carte de ce monde, ou {@code null} s'il n'en a pas. */
    public static EarthData get() {
        return current;
    }

    /** Lit le calage et la grille dans les donnees du serveur ; vrai si rien n'a echoue. */
    public static boolean load(MinecraftServer server) {
        current = null;
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            return true;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Carte de la Terre illisible : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            Config config = result.getOrThrow();
            if (!config.enabled()) {
                return true;
            }
            Grid grid = config.grid();
            var file = server.getResourceManager().getResource(grid.file());
            if (file.isEmpty() || grid.width() == 0 || grid.height() == 0) {
                DoHy.LOGGER.error("Carte de la Terre : grille des pays introuvable ({})", grid.file());
                return false;
            }
            short[] cells = new short[grid.width() * grid.height()];
            try (InputStream raw = file.get().open();
                 DataInputStream data = new DataInputStream(new GZIPInputStream(raw, 1 << 16))) {
                byte[] row = new byte[grid.width() * 2];
                for (int z = 0; z < grid.height(); z++) {
                    data.readFully(row);
                    for (int x = 0; x < grid.width(); x++) {
                        cells[z * grid.width() + x] = (short) ((row[x * 2] & 0xFF) << 8 | (row[x * 2 + 1] & 0xFF));
                    }
                }
            }
            current = new EarthData(config, cells);
            DoHy.LOGGER.info("Carte de la Terre : {} pays, grille de {} x {} cases de {} blocs, {} blocs par degre",
                    config.countries().size(), grid.width(), grid.height(), grid.cell(), config.blocksPerDegree());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement de la carte de la Terre", error);
            return false;
        }
    }

    static void unload() {
        current = null;
    }

    public Config config() {
        return config;
    }

    /** Vrai si ce monde-ci est celui de la carte. */
    public boolean covers(Level level) {
        return level.dimension() == dimension;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    private int cell(int x, int z) {
        Grid grid = config.grid();
        int column = Math.floorDiv(x - grid.minX(), grid.cell());
        int row = Math.floorDiv(z - grid.minZ(), grid.cell());
        if (column < 0 || row < 0 || column >= grid.width() || row >= grid.height()) {
            return 0;
        }
        return cells[row * grid.width() + column] & 0xFFFF;
    }

    /** Le pays de ce point, eaux cotieres comprises ; {@code null} en haute mer et hors de la carte. */
    public Country countryAt(int x, int z) {
        int id = cell(x, z) & ~WATER;
        return id > 0 && id < byId.length ? byId[id] : null;
    }

    /** Vrai si la grille dit de ce point qu'il est une terre. */
    public boolean landAt(int x, int z) {
        int value = cell(x, z);
        return value != 0 && (value & WATER) == 0;
    }

    public Country byCode(String code) {
        return byCode.get(code);
    }

    /** Le pays de ce numero, ou {@code null}. */
    public Country country(int id) {
        return id > 0 && id < byId.length ? byId[id] : null;
    }

    /** Un de plus que le plus grand numero de pays : la taille d'un tableau indexe par pays. */
    int countrySlots() {
        return byId.length;
    }

    /** La grille brute, ligne par ligne du nord au sud ; a lire seulement. */
    short[] cells() {
        return cells;
    }

    public double longitude(double x) {
        return (x - config.originX()) / config.blocksPerDegree();
    }

    /** La latitude d'un z : l'inverse de la projection, le nord vers les z negatifs. */
    public double latitude(double z) {
        double projected = (config.equatorZ() - z) / config.blocksPerDegree();
        if (!miller) {
            return Math.max(-90.0d, Math.min(90.0d, projected));
        }
        double radians = Math.toRadians(projected);
        return Math.toDegrees(2.5d * Math.atan(Math.exp(0.8d * radians)) - 0.625d * Math.PI);
    }

    /** Le x d'une longitude. */
    public double x(double longitude) {
        return config.originX() + longitude * config.blocksPerDegree();
    }

    /** Le z d'une latitude : la projection, le nord vers les z negatifs. */
    public double z(double latitude) {
        double projected = latitude;
        if (miller) {
            double radians = Math.toRadians(Math.max(-89.0d, Math.min(89.0d, latitude)));
            projected = Math.toDegrees(1.25d * Math.log(Math.tan(Math.PI / 4.0d + 0.4d * radians)));
        }
        return config.equatorZ() - projected * config.blocksPerDegree();
    }

    /** « 48,9° N  2,3° E ». */
    public String coordinates(double x, double z) {
        double latitude = latitude(z);
        double longitude = longitude(x);
        return String.format(java.util.Locale.ROOT, "%.1f° %s  %.1f° %s", Math.abs(latitude),
                latitude >= 0 ? "N" : "S", Math.abs(longitude), longitude >= 0 ? "E" : "O").replace('.', ',');
    }

    /**
     * Une case de terre tiree au hasard, la meme chance pour chaque case ;
     * {@code null} si la grille n'en a pas. Rend le centre de la case, en blocs.
     */
    public int[] randomLand(RandomSource random) {
        Grid grid = config.grid();
        for (int attempt = 0; attempt < 4096; attempt++) {
            int column = random.nextInt(grid.width());
            int row = random.nextInt(grid.height());
            int value = cells[row * grid.width() + column] & 0xFFFF;
            if (value != 0 && (value & WATER) == 0) {
                return new int[] {grid.minX() + column * grid.cell() + grid.cell() / 2,
                        grid.minZ() + row * grid.cell() + grid.cell() / 2};
            }
        }
        return null;
    }

    /** Le pays au centre d'un pixel de la carte reduite ({@link #mosaic}), ou {@code null}. */
    public Country countryOfPixel(int columns, int column, int row) {
        Grid grid = config.grid();
        int step = Math.max(1, grid.width() / columns);
        int x = grid.minX() + (column * step + step / 2) * grid.cell();
        int z = grid.minZ() + (row * step + step / 2) * grid.cell();
        Country centre = countryAt(x, z);
        if (centre != null && landAt(x, z)) {
            return centre;
        }
        // le centre du pixel est en mer : le pays qui y a le plus de terre
        int[] votes = new int[byId.length];
        int best = 0;
        for (int dz = 0; dz < step; dz++) {
            for (int dx = 0; dx < step; dx++) {
                int value = cells[(row * step + dz) * grid.width() + column * step + dx] & 0xFFFF;
                if (value != 0 && (value & WATER) == 0 && value < votes.length && ++votes[value] > votes[best]) {
                    best = value;
                }
            }
        }
        return best > 0 ? byId[best] : null;
    }

    /**
     * La carte reduite, pour la dessiner : un numero de teinte par pixel
     * (0 : la mer), la teinte du pays qui y a le plus de terre. Un pixel est
     * une terre si un tiers au moins de ses cases en sont.
     *
     * @param columns largeur voulue, en pixels ; la hauteur suit
     */
    public int[][] mosaic(int columns) {
        Grid grid = config.grid();
        int step = Math.max(1, grid.width() / columns);
        int width = grid.width() / step;
        int height = grid.height() / step;
        int[][] picture = new int[height][width];
        int[] votes = new int[byId.length];
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                java.util.Arrays.fill(votes, 0);
                int land = 0;
                int best = 0;
                for (int dz = 0; dz < step; dz++) {
                    for (int dx = 0; dx < step; dx++) {
                        int value = cells[(row * step + dz) * grid.width() + column * step + dx] & 0xFFFF;
                        if (value != 0 && (value & WATER) == 0 && value < votes.length) {
                            land++;
                            if (++votes[value] > votes[best]) {
                                best = value;
                            }
                        }
                    }
                }
                Country country = best > 0 ? byId[best] : null;
                picture[row][column] = land * 3 >= step * step && country != null ? Math.max(1, country.color()) : 0;
            }
        }
        return picture;
    }
}

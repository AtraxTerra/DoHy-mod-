package fr.atraxterra.dohy.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Le globe vu de pres : des dalles redessinees lisses a partir de la grille des
 * pays, au lieu d'agrandir ses cases de 8 blocs.
 *
 * <p>Chaque pixel d'une dalle melange les quatre cases voisines selon sa position
 * (comme un filtrage bilineaire), puis tranche : les cotes et les frontieres
 * deviennent des courbes, avec un bord adouci sur un pixel, et les frontieres un
 * trait fin. Quatre finesses (2, 4, 16 et 64 pixels par case) suivent le zoom.
 *
 * <p>Le calcul se fait sur un fil a part ; la carte affiche le globe agrandi le temps
 * qu'une dalle arrive. Le calque des pays tenus suit le meme dessin.
 */
final class GlobeTiles {

    static final int SIZE = 256;
    private static final int CACHE = 160;
    private static final int QUEUE = 128;
    /** La demi-largeur des traits, en pixels de dalle. */
    private static final double HALF_LINE = 0.45d;
    /** Le poids de terre ou passe la cote. */
    private static final double LAND = 0.42d;
    /** Les niveaux de detail de l'image entiere des pays tenus. */
    static final int WHOLE_LEVELS = 7;

    /** Le fond (pays, mers) ou le calque des pays tenus. */
    enum Kind {
        BASE, HOLDERS, WHOLE
    }

    record Key(Kind kind, int density, int tx, int tz) {
    }

    /**
     * Une dalle prete, a la version des donnees qui l'ont dessinee ; {@code id} vide si
     * elle ne montre rien (aucun pays tenu dessous).
     */
    static final class Tile {
        final Identifier id;
        final int version;

        Tile(Identifier id, int version) {
            this.id = id;
            this.version = version;
        }
    }

    /** Ce que le fil de calcul lit : une copie figee de la grille et des couleurs. */
    record Snapshot(int globeVersion, int holderVersion, short[] cells, int width, int height, int[] land,
                    int[] holder, boolean holders) {
        int cell(int column, int row) {
            if (column < 0 || row < 0 || column >= width || row >= height) {
                return 0;
            }
            return cells[row * width + column] & 0xFFFF;
        }

        int version(Kind kind) {
            return kind == Kind.BASE ? globeVersion : holderVersion;
        }
    }

    private record Result(Key key, int version, NativeImage image, NativeImage[] levels) {
        void close() {
            if (image != null) {
                image.close();
            }
            if (levels != null) {
                for (NativeImage level : levels) {
                    level.close();
                }
            }
        }
    }

    private static final Map<Key, Tile> TILES = new LinkedHashMap<>(64, 0.75f, true);
    private static final BlockingDeque<Key> QUEUED = new LinkedBlockingDeque<>();
    private static final Set<Key> WANTED = new HashSet<>();
    private static final ConcurrentLinkedQueue<Result> DONE = new ConcurrentLinkedQueue<>();
    private static volatile Snapshot snapshot;
    private static int counter;
    private static Thread worker;
    /** Recoit l'image entiere des pays tenus, avec ses niveaux, une fois calculee. */
    private static java.util.function.BiConsumer<Integer, NativeImage[]> whole;

    private GlobeTiles() {
    }

    /**
     * Reprend les donnees du globe. Une nouvelle grille efface tout ; de nouveaux pays
     * tenus laissent le fond et gardent l'ancien calque affiche le temps du nouveau.
     */
    static void sync(int[] land, int[] holder, short[] cells, int width, int height, int globeVersion,
                     int nationsVersion) {
        Snapshot current = snapshot;
        if (current != null && current.globeVersion() == globeVersion && current.holderVersion() == nationsVersion) {
            return;
        }
        if (current == null || current.globeVersion() != globeVersion || current.cells() != cells) {
            clear();
            counter++;
        }
        boolean any = false;
        for (int color : holder) {
            if (color != 0) {
                any = true;
                break;
            }
        }
        snapshot = new Snapshot(globeVersion, nationsVersion, cells, width, height, land.clone(), holder.clone(),
                any);
    }

    /** Le fond d'une dalle s'il est pret ; sinon il est demande au fil de calcul et vient plus tard. */
    static Identifier base(int density, int tx, int tz) {
        Tile tile = tile(new Key(Kind.BASE, density, tx, tz));
        return tile == null ? null : tile.id;
    }

    /**
     * Le calque des pays tenus d'une dalle, meme d'une version passee (redemande alors) ;
     * {@code null} s'il n'a jamais ete dessine.
     */
    static Tile holders(int density, int tx, int tz) {
        return tile(new Key(Kind.HOLDERS, density, tx, tz));
    }

    private static Tile tile(Key key) {
        Snapshot current = snapshot;
        Tile tile = TILES.get(key);
        if (current == null || (tile != null && tile.version == current.version(key.kind()))) {
            return tile;
        }
        request(key);
        return tile;
    }

    /** Demande l'image entiere des pays tenus ; {@code done} la recoit sur le fil du rendu. */
    static void whole(java.util.function.BiConsumer<Integer, NativeImage[]> done) {
        whole = done;
        request(new Key(Kind.WHOLE, 0, 0, 0));
    }

    private static void request(Key key) {
        synchronized (WANTED) {
            if (WANTED.add(key)) {
                // Le fond d'abord (il se voit le plus), le calque des pays tenus ensuite.
                if (key.kind() == Kind.HOLDERS) {
                    QUEUED.offerLast(key);
                } else {
                    QUEUED.offerFirst(key);
                }
                while (QUEUED.size() > QUEUE) {
                    Key dropped = QUEUED.pollLast();
                    if (dropped != null) {
                        WANTED.remove(dropped);
                    }
                }
            }
        }
        start();
    }

    /**
     * Transforme en textures les dalles calculees : au plus {@code max} par image, et
     * pas plus de deux millisecondes.
     */
    static void pump(int max) {
        Snapshot current = snapshot;
        long until = System.nanoTime() + 2_000_000L;
        for (int i = 0; i < max && (i < 4 || System.nanoTime() < until); i++) {
            Result result = DONE.poll();
            if (result == null) {
                return;
            }
            Key key = result.key();
            synchronized (WANTED) {
                WANTED.remove(key);
            }
            if (current == null || result.version() != current.version(key.kind())) {
                result.close();
                continue;
            }
            if (key.kind() == Kind.WHOLE) {
                if (whole != null) {
                    whole.accept(result.version(), result.levels());
                } else {
                    result.close();
                }
                continue;
            }
            Tile before = TILES.get(key);
            if (before != null && before.version == result.version()) {
                result.close();
                continue;
            }
            Identifier id = result.image() == null ? null : texture(key, result.version(), result.image());
            if (before != null && before.id != null) {
                Minecraft.getInstance().getTextureManager().release(before.id);
            }
            TILES.put(key, new Tile(id, result.version()));
            evict();
        }
    }

    private static Identifier texture(Key key, int version, NativeImage image) {
        Identifier id = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "atlas_globe/"
                + key.kind().name().toLowerCase(java.util.Locale.ROOT) + counter + "_" + version + "_"
                + key.density() + "_" + (key.tx() < 0 ? "m" + -key.tx() : key.tx()) + "_"
                + (key.tz() < 0 ? "m" + -key.tz() : key.tz()));
        MapTexture texture = new MapTexture("dohy atlas globe " + id, SIZE, SIZE, 1, MapTexture.smooth());
        texture.upload(image, null);
        image.close();
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return id;
    }

    private static void evict() {
        Iterator<Map.Entry<Key, Tile>> iterator = TILES.entrySet().iterator();
        while (TILES.size() > CACHE && iterator.hasNext()) {
            Tile tile = iterator.next().getValue();
            iterator.remove();
            if (tile.id != null) {
                Minecraft.getInstance().getTextureManager().release(tile.id);
            }
        }
    }

    /** Rend toutes les textures et oublie les demandes. */
    static void clear() {
        for (Tile tile : TILES.values()) {
            if (tile.id != null) {
                Minecraft.getInstance().getTextureManager().release(tile.id);
            }
        }
        TILES.clear();
        synchronized (WANTED) {
            QUEUED.clear();
            WANTED.clear();
        }
        Result result;
        while ((result = DONE.poll()) != null) {
            result.close();
        }
        snapshot = null;
    }

    private static synchronized void start() {
        if (worker != null && worker.isAlive()) {
            return;
        }
        worker = new Thread(GlobeTiles::run, "DoHy atlas globe");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    private static void run() {
        while (true) {
            Key key = null;
            try {
                key = QUEUED.takeFirst();
                Snapshot current = snapshot;
                if (current == null) {
                    synchronized (WANTED) {
                        WANTED.remove(key);
                    }
                    continue;
                }
                DONE.add(switch (key.kind()) {
                    case WHOLE -> new Result(key, current.holderVersion(), null, wholeHolders(current));
                    case BASE -> new Result(key, current.globeVersion(), generate(current, key, false), null);
                    case HOLDERS -> new Result(key, current.holderVersion(), generate(current, key, true), null);
                });
            } catch (InterruptedException interrupted) {
                return;
            } catch (Throwable error) {
                if (key != null) {
                    synchronized (WANTED) {
                        WANTED.remove(key);
                    }
                }
                DoHy.LOGGER.warn("Atlas : dalle du globe non calculee ({})", error.toString());
            }
        }
    }

    /** L'image entiere des pays tenus, une case par pixel, et ses niveaux : vue de loin. */
    private static NativeImage[] wholeHolders(Snapshot snap) {
        NativeImage image = new NativeImage(snap.width(), snap.height(), true);
        for (int row = 0; row < snap.height(); row++) {
            for (int column = 0; column < snap.width(); column++) {
                int cell = snap.cells()[row * snap.width() + column] & 0xFFFF;
                int id = cell & 0x7FFF;
                int color = id != 0 && (cell & 0x8000) == 0 && id < snap.holder().length ? snap.holder()[id] : 0;
                if (color != 0) {
                    image.setPixel(column, row, (color & 0x00FFFFFF) | 0x60000000);
                }
            }
        }
        return MapTexture.levels(image, Math.min(WHOLE_LEVELS, MapTexture.maxLevels(snap.width(), snap.height())));
    }

    // -- Le dessin d'une dalle --------------------------------------------------------------------

    private static double clamp(double value) {
        return value < 0.0d ? 0.0d : value > 1.0d ? 1.0d : value;
    }

    static int mix(int from, int to, double t) {
        double c = clamp(t);
        int r = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * c);
        int g = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * c);
        int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * c);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /**
     * Les poids d'une B-spline cubique pour les quatre cases autour d'un point : un
     * lissage doux, sans rebond, qui efface les marches de la grille.
     */
    private static void spline(double t, double[] w) {
        double t2 = t * t;
        double t3 = t2 * t;
        w[0] = (1.0d - 3.0d * t + 3.0d * t2 - t3) / 6.0d;
        w[1] = (3.0d * t3 - 6.0d * t2 + 4.0d) / 6.0d;
        w[2] = (-3.0d * t3 + 3.0d * t2 + 3.0d * t + 1.0d) / 6.0d;
        w[3] = t3 / 6.0d;
    }

    /** Le fond ou le calque des pays tenus d'une dalle ; {@code null} pour un calque vide. */
    static NativeImage generate(Snapshot snap, Key key, boolean holdersLayer) {
        int density = key.density();
        int cells = SIZE / density;
        // La grille de la dalle et sa marge (deux cases avant, trois apres), lue une fois.
        int span = cells + 5;
        int originX = key.tx() * cells - 2;
        int originZ = key.tz() * cells - 2;
        int[] grid = new int[span * span];
        for (int r = 0; r < span; r++) {
            for (int c = 0; c < span; c++) {
                grid[r * span + c] = snap.cell(originX + c, originZ + r);
            }
        }
        if (holdersLayer && !snap.holders()) {
            return null;
        }
        NativeImage image = new NativeImage(SIZE, SIZE, false);
        boolean anyHolder = false;
        int[] sample = new int[16];
        double[] weight = new double[16];
        double[] wx = new double[4];
        double[] wz = new double[4];
        // Les poids ne dependent que de la position dans la case : une table par colonne de pixels.
        int[] columns = new int[SIZE];
        double[] columnWeights = new double[SIZE * 4];
        for (int px = 0; px < SIZE; px++) {
            double u = key.tx() * (double) cells + (px + 0.5d) / density - 0.5d;
            int column = (int) Math.floor(u);
            columns[px] = column - 1 - originX;
            spline(u - column, wx);
            System.arraycopy(wx, 0, columnWeights, px * 4, 4);
        }
        int[] ids = new int[16];
        double[] sums = new double[16];
        int[] keys = new int[16];
        double[] holderSums = new double[16];
        for (int py = 0; py < SIZE; py++) {
            double v = key.tz() * (double) cells + (py + 0.5d) / density - 0.5d;
            int row = (int) Math.floor(v);
            spline(v - row, wz);
            int localRow = row - 1 - originZ;
            for (int px = 0; px < SIZE; px++) {
                int localColumn = columns[px];
                int weights = px * 4;
                for (int j = 0; j < 4; j++) {
                    int base4 = (localRow + j) * span + localColumn;
                    for (int i = 0; i < 4; i++) {
                        sample[j * 4 + i] = grid[base4 + i];
                        weight[j * 4 + i] = columnWeights[weights + i] * wz[j];
                    }
                }
                if (holdersLayer) {
                    int pixel = holder(snap, sample, weight, density, keys, holderSums);
                    if ((pixel >>> 24) != 0) {
                        anyHolder = true;
                    }
                    image.setPixel(px, py, pixel);
                    continue;
                }
                // La terre : son poids, et ses pays par poids.
                double land = 0.0d;
                double coast = 0.0d;
                double sea = 0.0d;
                int count = 0;
                for (int k = 0; k < 16; k++) {
                    int id = sample[k] & 0x7FFF;
                    if (id == 0) {
                        sea += weight[k];
                    } else if ((sample[k] & 0x8000) != 0) {
                        coast += weight[k];
                    } else {
                        land += weight[k];
                        int slot = -1;
                        for (int s = 0; s < count; s++) {
                            if (ids[s] == id) {
                                slot = s;
                                break;
                            }
                        }
                        if (slot < 0) {
                            slot = count++;
                            ids[slot] = id;
                            sums[slot] = 0.0d;
                        }
                        sums[slot] += weight[k];
                    }
                }
                double wet = coast + sea;
                int water = wet <= 1.0e-9d ? AtlasGlobe.COAST
                        : mix(AtlasGlobe.SEA, AtlasGlobe.COAST, 0.5d + (coast / wet - 0.5d) * density);
                int color = water;
                if (count > 0) {
                    int top = 0;
                    double best = -1.0d;
                    double second = 0.0d;
                    for (int s = 0; s < count; s++) {
                        if (sums[s] > best) {
                            second = Math.max(second, best);
                            best = sums[s];
                            top = ids[s];
                        } else {
                            second = Math.max(second, sums[s]);
                        }
                    }
                    int ground = snap.land()[Math.min(top, snap.land().length - 1)];
                    if (second > 0.0d) {
                        double line = clamp(HALF_LINE + 0.5d - (best - second) * density / 2.0d);
                        ground = mix(ground, AtlasScanner.shaded(ground, 0.6d), line);
                    }
                    // Le trait de cote passe la ou la terre pese 0,42 : les ilots d'une case restent.
                    double edge = (land - LAND) * density;
                    color = mix(water, ground, 0.5d + edge);
                    double shore = clamp(HALF_LINE * 0.8d + 0.5d - Math.abs(edge)) * 0.45d;
                    if (shore > 0.0d) {
                        color = mix(color, AtlasGlobe.SHORE, shore);
                    }
                }
                image.setPixel(px, py, color);
            }
        }
        if (holdersLayer && !anyHolder) {
            image.close();
            return null;
        }
        return image;
    }

    /** Le calque des pays tenus en un pixel : une teinte legere et un trait au bord. */
    private static int holder(Snapshot snap, int[] sample, double[] weight, int density, int[] keys,
                              double[] sums) {
        int count = 0;
        for (int k = 0; k < sample.length; k++) {
            int id = sample[k] & 0x7FFF;
            boolean ground = id != 0 && (sample[k] & 0x8000) == 0;
            int color = ground && id < snap.holder().length ? snap.holder()[id] : 0;
            int slot = -1;
            for (int s = 0; s < count; s++) {
                if (keys[s] == color) {
                    slot = s;
                    break;
                }
            }
            if (slot < 0) {
                slot = count++;
                keys[slot] = color;
                sums[slot] = 0.0d;
            }
            sums[slot] += weight[k];
        }
        int top = 0;
        int other = 0;
        double best = -1.0d;
        double second = 0.0d;
        for (int s = 0; s < count; s++) {
            if (sums[s] > best) {
                if (best >= 0.0d) {
                    second = best;
                    other = top;
                }
                best = sums[s];
                top = keys[s];
            } else if (sums[s] > second) {
                second = sums[s];
                other = keys[s];
            }
        }
        double distance = (best - second) * density / 2.0d;
        double fill = top == 0 ? 0.0d : 0.36d * clamp(0.5d + distance);
        double line = count > 1 ? clamp(HALF_LINE * 1.4d + 0.5d - distance) * 0.9d : 0.0d;
        int color = top != 0 ? top : other;
        if (color == 0) {
            return 0;
        }
        double alpha = fill + line * (1.0d - fill);
        return (int) Math.round(clamp(alpha) * 255.0d) << 24 | (color & 0xFFFFFF);
    }
}

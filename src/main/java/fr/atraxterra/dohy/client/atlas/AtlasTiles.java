package fr.atraxterra.dohy.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuSampler;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Le terrain explore, en dalles d'images gardees sur le disque.
 *
 * <p>Trois niveaux : les dalles fines (un pixel par bloc, 256 blocs de cote), les
 * dalles moyennes (un pixel pour 4 blocs) et les dalles d'ensemble (un pixel pour
 * 16 blocs), pour voir loin sans charger des centaines de dalles fines. Les deux
 * derniers se nourrissent des niveaux de detail des dalles fines : chaque pixel y
 * est la moyenne de ses blocs, pas un bloc pris au hasard.
 *
 * <p>Chaque niveau garde un nombre borne de dalles en memoire ; les autres attendent
 * sur le disque, dans {@code dohy_atlas/<monde>/<dimension>/}.
 */
public final class AtlasTiles {

    public static final int SIZE = 256;
    public static final int OVERVIEW = 16;
    public static final int MIDDLE = 4;
    /** Les niveaux de detail d'une dalle : 256, 128, 64, 32 et 16 pixels. */
    static final int LEVELS = 5;

    /** Une dalle : son image, sa texture, et ce qu'il reste a faire d'elle. */
    static final class Tile {
        final int tx;
        final int tz;
        final NativeImage image;
        final MapTexture texture;
        final Identifier id;
        boolean dirty;
        boolean unsaved;

        Tile(int tx, int tz, NativeImage image, Identifier id, GpuSampler sampler) {
            this.tx = tx;
            this.tz = tz;
            this.image = image;
            this.id = id;
            this.texture = new MapTexture("dohy atlas " + id, SIZE, SIZE, LEVELS, sampler);
            this.dirty = true;
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
    }

    private final String prefix;
    private final int scale;
    private final int cache;
    private final boolean crisp;
    private final Path folder;
    private final Map<Long, Tile> tiles = new LinkedHashMap<>(64, 0.75f, true);
    /** Les dalles qui n'existent ni en memoire ni sur le disque : on ne les cherche plus. */
    private final Set<Long> missing = new HashSet<>();
    /** Les niveaux plus grossiers que nourrit celui-ci (les dalles fines seulement). */
    private AtlasTiles middle;
    private AtlasTiles overview;

    AtlasTiles(Path folder, String prefix, int scale, int cache, boolean crisp) {
        this.folder = folder;
        this.prefix = prefix;
        this.scale = scale;
        this.cache = cache;
        this.crisp = crisp;
    }

    /** Les niveaux nourris par les niveaux de detail de ces dalles. */
    void feeds(AtlasTiles middle, AtlasTiles overview) {
        this.middle = middle;
        this.overview = overview;
    }

    /** Blocs par pixel. */
    int scale() {
        return scale;
    }

    /** Blocs couverts par une dalle, de cote. */
    int span() {
        return SIZE * scale;
    }

    /** Le nombre de dalles qu'on peut afficher a la fois sans les recharger sans cesse. */
    int budget() {
        return cache * 2 / 3;
    }

    private static long key(int tx, int tz) {
        return (tx & 0xFFFFFFFFL) | ((long) tz << 32);
    }

    private Path file(int tx, int tz) {
        return folder.resolve(prefix + "." + tx + "." + tz + ".png");
    }

    /** La dalle si elle est deja en memoire, sans toucher au disque. */
    Tile loaded(int tx, int tz) {
        return tiles.get(key(tx, tz));
    }

    /** Vrai si la dalle n'est ni en memoire ni connue absente : la demander lirait le disque. */
    boolean unknown(int tx, int tz) {
        long key = key(tx, tz);
        return !tiles.containsKey(key) && !missing.contains(key);
    }

    /** La dalle, chargee du disque au besoin ; {@code null} si elle n'existe pas et {@code create} est faux. */
    Tile tile(int tx, int tz, boolean create) {
        long key = key(tx, tz);
        Tile tile = tiles.get(key);
        if (tile != null) {
            return tile;
        }
        if (!create && missing.contains(key)) {
            return null;
        }
        NativeImage image = null;
        Path path = file(tx, tz);
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                image = NativeImage.read(in);
                if (image.getWidth() != SIZE || image.getHeight() != SIZE) {
                    image.close();
                    image = null;
                }
            } catch (IOException | RuntimeException error) {
                DoHy.LOGGER.warn("Atlas : dalle illisible {} ({})", path, error.toString());
            }
        }
        if (image == null) {
            if (!create) {
                missing.add(key);
                return null;
            }
            image = new NativeImage(SIZE, SIZE, true);
        }
        missing.remove(key);
        tile = new Tile(tx, tz, image, Identifier.fromNamespaceAndPath(DoHy.MOD_ID,
                "atlas/" + prefix + "_" + (tx < 0 ? "m" + -tx : tx) + "_" + (tz < 0 ? "m" + -tz : tz)),
                crisp ? MapTexture.crisp() : MapTexture.smooth());
        tiles.put(key, tile);
        evict();
        return tile;
    }

    /** Ecrit la couleur d'un bloc (ou d'une case de {@code scale} blocs). */
    void set(int x, int z, int argb) {
        int px = Math.floorDiv(x, scale);
        int pz = Math.floorDiv(z, scale);
        Tile tile = tile(Math.floorDiv(px, SIZE), Math.floorDiv(pz, SIZE), true);
        int lx = Math.floorMod(px, SIZE);
        int lz = Math.floorMod(pz, SIZE);
        if (tile.image.getPixel(lx, lz) != argb) {
            tile.image.setPixel(lx, lz, argb);
            tile.dirty = true;
            tile.unsaved = true;
        }
    }

    /** Envoie a la carte graphique les dalles modifiees (au plus {@code max}), avec leurs niveaux. */
    void upload(int max) {
        List<Tile> pending = new ArrayList<>();
        for (Tile tile : tiles.values()) {
            if (tile.dirty) {
                pending.add(tile);
                if (pending.size() >= max) {
                    break;
                }
            }
        }
        for (Tile tile : pending) {
            tile.dirty = false;
            tile.texture.upload(tile.image, middle == null && overview == null ? null : (level, image) ->
                    feed(tile, level, image));
        }
    }

    /** Passe un niveau de detail d'une dalle fine aux niveaux grossiers qu'il remplit. */
    private void feed(Tile tile, int level, NativeImage image) {
        if (level == 2 && middle != null) {
            middle.paste(tile.tx, tile.tz, SIZE / MIDDLE, image);
        } else if (level == 4 && overview != null) {
            overview.paste(tile.tx, tile.tz, SIZE / OVERVIEW, image);
        }
    }

    /**
     * Recopie le niveau de detail d'une dalle fine dans la dalle de ce niveau qui la
     * contient ; rien ne change si les pixels sont deja les memes.
     */
    private void paste(int fineX, int fineZ, int size, NativeImage source) {
        int ratio = SIZE / size;
        int tx = Math.floorDiv(fineX, ratio);
        int tz = Math.floorDiv(fineZ, ratio);
        int ox = Math.floorMod(fineX, ratio) * size;
        int oz = Math.floorMod(fineZ, ratio) * size;
        Tile tile = tiles.get(key(tx, tz));
        if (tile == null) {
            boolean empty = true;
            for (int y = 0; y < size && empty; y++) {
                for (int x = 0; x < size; x++) {
                    if ((source.getPixel(x, y) >>> 24) != 0) {
                        empty = false;
                        break;
                    }
                }
            }
            if (empty) {
                return;
            }
            tile = tile(tx, tz, true);
        }
        boolean changed = false;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int color = source.getPixel(x, y);
                if ((color >>> 24) == 0) {
                    // L'inexplore de la dalle fine n'efface pas ce que la dalle grossiere savait.
                    continue;
                }
                if (tile.image.getPixel(ox + x, oz + y) != color) {
                    tile.image.setPixel(ox + x, oz + y, color);
                    changed = true;
                }
            }
        }
        if (changed) {
            tile.dirty = true;
            tile.unsaved = true;
        }
    }

    /** Sauve les dalles modifiees (au plus {@code max}, toutes si negatif). */
    void save(int max) {
        for (Tile tile : tiles.values()) {
            if (tile.unsaved && max-- != 0) {
                write(tile);
            }
        }
    }

    private void write(Tile tile) {
        try {
            Files.createDirectories(folder);
            tile.image.writeToFile(file(tile.tx, tile.tz));
            tile.unsaved = false;
        } catch (IOException | RuntimeException error) {
            DoHy.LOGGER.warn("Atlas : dalle non sauvee ({})", error.toString());
        }
    }

    private void evict() {
        if (tiles.size() <= cache) {
            return;
        }
        List<Long> drop = new ArrayList<>();
        for (Map.Entry<Long, Tile> entry : tiles.entrySet()) {
            if (tiles.size() - drop.size() <= cache) {
                break;
            }
            drop.add(entry.getKey());
        }
        for (Long key : drop) {
            release(tiles.remove(key));
        }
    }

    private void release(Tile tile) {
        if (tile.dirty && (middle != null || overview != null)) {
            // Jamais envoyee depuis son dernier changement : les niveaux grossiers l'apprennent quand meme.
            MapTexture.chain(tile.image, LEVELS, (level, image) -> feed(tile, level, image));
        }
        if (tile.unsaved) {
            write(tile);
        }
        Minecraft.getInstance().getTextureManager().release(tile.id);
        tile.image.close();
    }

    /** Sauve tout et rend toutes les textures. */
    void close() {
        for (Tile tile : tiles.values()) {
            release(tile);
        }
        tiles.clear();
        missing.clear();
    }
}

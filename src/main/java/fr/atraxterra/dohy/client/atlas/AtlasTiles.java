package fr.atraxterra.dohy.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
 * <p>Deux niveaux : les dalles fines (un pixel par bloc, 256 blocs de cote) et
 * les dalles d'ensemble (un pixel pour 16 blocs, 4 096 blocs de cote), pour
 * voir loin sans charger des centaines de dalles fines. Chaque niveau garde au
 * plus {@value #CACHE} dalles en memoire ; les autres attendent sur le disque,
 * dans {@code dohy_atlas/<monde>/<dimension>/}.
 */
public final class AtlasTiles {

    public static final int SIZE = 256;
    public static final int OVERVIEW = 16;
    private static final int CACHE = 96;

    /** Une dalle : son image, sa texture, et ce qu'il reste a faire d'elle. */
    static final class Tile {
        final int tx;
        final int tz;
        final NativeImage image;
        final DynamicTexture texture;
        final Identifier id;
        boolean dirty;
        boolean unsaved;

        Tile(int tx, int tz, NativeImage image, Identifier id) {
            this.tx = tx;
            this.tz = tz;
            this.image = image;
            this.id = id;
            this.texture = new DynamicTexture(() -> "dohy atlas " + id, image);
            this.dirty = true;
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
    }

    private final String prefix;
    private final int scale;
    private final Path folder;
    private final Map<Long, Tile> tiles = new LinkedHashMap<>(64, 0.75f, true);
    /** Les dalles qui n'existent ni en memoire ni sur le disque : on ne les cherche plus. */
    private final Set<Long> missing = new HashSet<>();

    AtlasTiles(Path folder, String prefix, int scale) {
        this.folder = folder;
        this.prefix = prefix;
        this.scale = scale;
    }

    /** Blocs par pixel. */
    int scale() {
        return scale;
    }

    /** Blocs couverts par une dalle, de cote. */
    int span() {
        return SIZE * scale;
    }

    private static long key(int tx, int tz) {
        return (tx & 0xFFFFFFFFL) | ((long) tz << 32);
    }

    private Path file(int tx, int tz) {
        return folder.resolve(prefix + "." + tx + "." + tz + ".png");
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
                "atlas/" + prefix + "_" + (tx < 0 ? "m" + -tx : tx) + "_" + (tz < 0 ? "m" + -tz : tz)));
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

    /** Envoie a la carte graphique les dalles modifiees (au plus {@code max}). */
    void upload(int max) {
        for (Tile tile : tiles.values()) {
            if (tile.dirty && max-- > 0) {
                tile.texture.upload();
                tile.dirty = false;
            }
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
        if (tiles.size() <= CACHE) {
            return;
        }
        List<Long> drop = new ArrayList<>();
        for (Map.Entry<Long, Tile> entry : tiles.entrySet()) {
            if (tiles.size() - drop.size() <= CACHE) {
                break;
            }
            drop.add(entry.getKey());
        }
        for (Long key : drop) {
            release(tiles.remove(key));
        }
    }

    private void release(Tile tile) {
        if (tile.unsaved) {
            write(tile);
        }
        Minecraft.getInstance().getTextureManager().release(tile.id);
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

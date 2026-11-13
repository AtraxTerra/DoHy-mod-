package fr.atraxterra.dohy.client.atlas;

import java.nio.file.Path;

/**
 * Le terrain explore d'une dimension d'un monde : les dalles fines et les
 * dalles d'ensemble, dans {@code dohy_atlas/<monde>/<dimension>/}.
 */
final class AtlasMap {

    final String world;
    final String dimension;
    final AtlasTiles fine;
    final AtlasTiles overview;

    AtlasMap(Path root, String world, String dimension) {
        this.world = world;
        this.dimension = dimension;
        Path folder = root.resolve(world).resolve(dimension);
        this.fine = new AtlasTiles(folder, "t", 1);
        this.overview = new AtlasTiles(folder, "o", AtlasTiles.OVERVIEW);
    }

    /** La couleur d'une colonne ; la vue d'ensemble prend le bloc du milieu de chaque case de 16. */
    void set(int x, int z, int argb) {
        fine.set(x, z, argb);
        if ((x & 15) == 8 && (z & 15) == 8) {
            overview.set(x, z, argb);
        }
    }

    void upload() {
        fine.upload(8);
        overview.upload(2);
    }

    void save(int max) {
        fine.save(max);
        overview.save(max);
    }

    void close() {
        fine.close();
        overview.close();
    }
}

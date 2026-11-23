package fr.atraxterra.dohy.client.atlas;

import java.nio.file.Path;

/**
 * Le terrain explore d'une dimension d'un monde : les dalles fines, moyennes et
 * d'ensemble, dans {@code dohy_atlas/<monde>/<dimension>/} ; et, a cote, les memes
 * niveaux pour le monde entier tel que le serveur le lit dans ses fichiers (la carte
 * revelee, {@link AtlasRemote}), dessines sous ce que tu as explore.
 */
final class AtlasMap {

    final String world;
    final String dimension;
    final AtlasTiles fine;
    final AtlasTiles middle;
    final AtlasTiles overview;
    final AtlasTiles revealFine;
    final AtlasTiles revealMiddle;
    final AtlasTiles revealOverview;

    AtlasMap(Path root, String world, String dimension) {
        this.world = world;
        this.dimension = dimension;
        Path folder = root.resolve(world).resolve(dimension);
        this.fine = new AtlasTiles(folder, "t", 1, 72, true);
        this.middle = new AtlasTiles(folder, "m", AtlasTiles.MIDDLE, 48, false);
        this.overview = new AtlasTiles(folder, "o", AtlasTiles.OVERVIEW, 48, false);
        this.fine.feeds(middle, overview);
        this.revealFine = new AtlasTiles(folder, "rt", 1, 72, true);
        this.revealMiddle = new AtlasTiles(folder, "rm", AtlasTiles.MIDDLE, 48, false);
        this.revealOverview = new AtlasTiles(folder, "ro", AtlasTiles.OVERVIEW, 48, false);
    }

    /** La couleur d'une colonne ; les niveaux grossiers suivent a l'envoi des dalles fines. */
    void set(int x, int z, int argb) {
        fine.set(x, z, argb);
    }

    void upload() {
        fine.upload(3);
        middle.upload(2);
        overview.upload(2);
        revealFine.upload(2);
        revealMiddle.upload(2);
        revealOverview.upload(2);
    }

    void save(int max) {
        fine.save(max);
        middle.save(max);
        overview.save(max);
        revealFine.save(max);
        revealMiddle.save(max);
        revealOverview.save(max);
    }

    void close() {
        fine.close();
        middle.close();
        overview.close();
        revealFine.close();
        revealMiddle.close();
        revealOverview.close();
    }
}

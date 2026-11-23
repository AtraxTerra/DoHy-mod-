package fr.atraxterra.dohy.atlas;

import net.minecraft.world.level.material.MapColor;

/**
 * Les teintes de l'atlas, communes au releve du client (ce qu'il explore) et au rendu du serveur
 * (le monde entier, lu dans ses fichiers) : les deux doivent donner la meme carte.
 */
public final class MapShade {

    /** Plus loin que ca sous la surface, l'eau ne fonce plus. */
    public static final int WATER_DEPTH = 12;

    private MapShade() {
    }

    /** Une couleur eclaircie ({@code factor} > 1) ou assombrie, opaque. */
    public static int shaded(int color, double factor) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** L'eau, plus sombre quand elle est profonde ({@code depth} blocs d'eau, de 1 a {@value #WATER_DEPTH}). */
    public static int water(int depth) {
        return shaded(MapColor.WATER.col, 1.05d - depth * 0.045d);
    }

    /**
     * La pente, comme les cartes du jeu : plus clair qu'au nord quand le sol monte, plus sombre quand
     * il descend ({@code north} vaut {@link Integer#MIN_VALUE} quand le voisin n'est pas connu).
     */
    public static double slope(int y, int north) {
        return north == Integer.MIN_VALUE || north == y ? 1.0d : y > north ? 1.12d : 0.84d;
    }
}

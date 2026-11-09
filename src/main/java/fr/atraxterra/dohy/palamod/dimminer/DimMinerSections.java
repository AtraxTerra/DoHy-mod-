package fr.atraxterra.dohy.palamod.dimminer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Le decoupage de la dimension en sections ({@code DimMinerManager}).
 *
 * <p>Chaque session recoit un carre neuf de 5000 x 5000 blocs, pris dans une
 * spirale carree autour de l'origine : 0 en (0, 0), 1 en (5000, 0), 2 en
 * (5000, -5000), 3 en (0, -5000)... (directions +X, -Z, -X, +Z, le segment
 * s'allongeant d'un pas tous les deux virages).
 *
 * <p><b>Ecart</b> : l'original gardait le compteur en memoire, remis a zero a
 * chaque redemarrage du serveur, et donnait une nouvelle section a chaque
 * connexion. Ici le compteur est sauvegarde avec le monde, et une section
 * sert toute la session : on retrouve son chantier en revenant.
 */
public final class DimMinerSections {

    /** {@code SECTION_SIZE}. */
    public static final int SIZE = 5000;
    private static final int HALF = SIZE / 2;

    public record Section(int index, int centerX, int centerZ) {
        public int minX() {
            return centerX - HALF;
        }

        public int maxX() {
            return centerX + HALF;
        }

        public int minZ() {
            return centerZ - HALF;
        }

        public int maxZ() {
            return centerZ + HALF;
        }

        /** {@code isInside} : bornes comprises. */
        public boolean contains(double x, double z) {
            return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
        }
    }

    private DimMinerSections() {
    }

    public static Section of(int index) {
        int x = 0;
        int z = 0;
        int direction = 0;
        int length = 1;
        int walked = 0;
        int turns = 0;
        for (int step = 0; step < index; step++) {
            switch (direction) {
                case 0 -> x += SIZE;
                case 1 -> z -= SIZE;
                case 2 -> x -= SIZE;
                default -> z += SIZE;
            }
            if (++walked == length) {
                walked = 0;
                direction = (direction + 1) % 4;
                if (++turns % 2 == 0) {
                    length++;
                }
            }
        }
        return new Section(index, x, z);
    }

    /** {@code generateNextSection} : la suivante de la spirale, compteur sauvegarde dans l'overworld. */
    public static Section next(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        int index = overworld.getData(PalaDimMiner.NEXT_SECTION.get());
        overworld.setData(PalaDimMiner.NEXT_SECTION.get(), index + 1);
        return of(index);
    }
}

package fr.atraxterra.dohy.palette;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Le decoupage de palettes voisines en groupes d'au plus 3 x 3.
 *
 * <p>Des palettes posees cote a cote, au meme niveau, se rassemblent : un seul
 * stock, une seule pile de caisses. Au-dela de 3 x 3, l'ensemble est coupe en
 * plusieurs groupes. Le decoupage part du coin nord-ouest (plus petit z, puis
 * plus petit x) et, pour chaque palette encore libre, garde le plus grand des
 * deux rectangles qu'elle peut ouvrir : en s'etendant d'abord vers l'est, ou
 * d'abord vers le sud.
 *
 * <p>Pur Java, sans Minecraft : {@code tools/PaletteLayoutTest} le verifie
 * seul, avec la pile de caisses ({@link CrateLayout}).
 */
public final class PalettePlan {

    /** Le cote maximal d'un groupe, en palettes. */
    public static final int MAX = 3;

    private PalettePlan() {
    }

    /** Un groupe : son coin nord-ouest et sa taille, en palettes. */
    public record Rect(int x, int z, int w, int d) {

        public boolean contains(int px, int pz) {
            return px >= x && px < x + w && pz >= z && pz < z + d;
        }

        public int size() {
            return w * d;
        }
    }

    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int keyX(long key) {
        return (int) (key >> 32);
    }

    public static int keyZ(long key) {
        return (int) key;
    }

    /** Coupe les cases ({@link #key}) en rectangles, du nord-ouest vers le sud-est. */
    public static List<Rect> partition(Collection<Long> cells) {
        List<Long> order = new ArrayList<>(cells);
        order.sort((a, b) -> keyZ(a) != keyZ(b) ? Integer.compare(keyZ(a), keyZ(b))
                : Integer.compare(keyX(a), keyX(b)));
        Set<Long> free = new HashSet<>(cells);
        List<Rect> rects = new ArrayList<>();
        for (long cell : order) {
            if (!free.contains(cell)) {
                continue;
            }
            int x = keyX(cell);
            int z = keyZ(cell);
            Rect east = grow(free, x, z, true);
            Rect south = grow(free, x, z, false);
            Rect best = south.size() > east.size() ? south : east;
            for (int dx = 0; dx < best.w(); dx++) {
                for (int dz = 0; dz < best.d(); dz++) {
                    free.remove(key(best.x() + dx, best.z() + dz));
                }
            }
            rects.add(best);
        }
        return rects;
    }

    private static Rect grow(Set<Long> free, int x, int z, boolean eastFirst) {
        if (eastFirst) {
            int w = 1;
            while (w < MAX && free.contains(key(x + w, z))) {
                w++;
            }
            int d = 1;
            while (d < MAX && line(free, x, z + d, w, true)) {
                d++;
            }
            return new Rect(x, z, w, d);
        }
        int d = 1;
        while (d < MAX && free.contains(key(x, z + d))) {
            d++;
        }
        int w = 1;
        while (w < MAX && line(free, x + w, z, d, false)) {
            w++;
        }
        return new Rect(x, z, w, d);
    }

    /** Une rangee (vers l'est) ou une colonne (vers le sud) entierement libre. */
    private static boolean line(Set<Long> free, int x, int z, int length, boolean eastward) {
        for (int i = 0; i < length; i++) {
            if (!free.contains(eastward ? key(x + i, z) : key(x, z + i))) {
                return false;
            }
        }
        return true;
    }
}

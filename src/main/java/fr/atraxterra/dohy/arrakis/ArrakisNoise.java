package fr.atraxterra.dohy.arrakis;

import java.util.Random;

/**
 * Un bruit de simplexe 2D a graine fixe : le relief d'Arrakis est le meme dans
 * tous les mondes, et l'astroport sait ou poser ses dalles sans rien lire.
 *
 * <p>Calcul pur et sans etat : les fils de generation le partagent sans verrou.
 */
final class ArrakisNoise {

    private static final double F2 = 0.5d * (Math.sqrt(3.0d) - 1.0d);
    private static final double G2 = (3.0d - Math.sqrt(3.0d)) / 6.0d;
    private static final int[][] GRAD = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}, {1, 0}, {-1, 0}, {1, 0}, {-1, 0},
            {0, 1}, {0, -1}, {0, 1}, {0, -1}};

    private final int[] perm = new int[512];

    ArrakisNoise(long seed) {
        Random random = new Random(seed);
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = p[i];
            p[i] = p[j];
            p[j] = swap;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
        }
    }

    /** Une valeur dans [-1, 1] a peu pres. */
    double sample(double xin, double yin) {
        double s = (xin + yin) * F2;
        int i = (int) Math.floor(xin + s);
        int j = (int) Math.floor(yin + s);
        double t = (i + j) * G2;
        double x0 = xin - (i - t);
        double y0 = yin - (j - t);
        int i1;
        int j1;
        if (x0 > y0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }
        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0d + 2.0d * G2;
        double y2 = y0 - 1.0d + 2.0d * G2;
        int ii = i & 255;
        int jj = j & 255;
        double total = 0.0d;
        double t0 = 0.5d - x0 * x0 - y0 * y0;
        if (t0 > 0.0d) {
            int[] g = GRAD[perm[ii + perm[jj]] % 12];
            t0 *= t0;
            total += t0 * t0 * (g[0] * x0 + g[1] * y0);
        }
        double t1 = 0.5d - x1 * x1 - y1 * y1;
        if (t1 > 0.0d) {
            int[] g = GRAD[perm[ii + i1 + perm[jj + j1]] % 12];
            t1 *= t1;
            total += t1 * t1 * (g[0] * x1 + g[1] * y1);
        }
        double t2 = 0.5d - x2 * x2 - y2 * y2;
        if (t2 > 0.0d) {
            int[] g = GRAD[perm[ii + 1 + perm[jj + 1]] % 12];
            t2 *= t2;
            total += t2 * t2 * (g[0] * x2 + g[1] * y2);
        }
        return 70.0d * total;
    }
}

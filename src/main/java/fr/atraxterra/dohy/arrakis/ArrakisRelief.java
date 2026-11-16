package fr.atraxterra.dohy.arrakis;

/**
 * Le relief d'Arrakis, colonne par colonne : l'erg et ses dunes, les massifs
 * de roche, les cuvettes de sel, les calottes polaires, et autour de l'origine
 * la cuvette d'Arrakeen fermee par la Muraille.
 *
 * <p>Tout est fonction de x et z, a graine fixe. {@link ArrakisTerrain} en tire
 * la densite du terrain, la surface de reference des regles de surface et le
 * biome ; l'astroport s'y fie pour son sol, plat a {@link #CITY_GROUND}.
 */
public final class ArrakisRelief {

    /** Le dernier bloc plein du sol d'Arrakeen : on y marche a {@code CITY_GROUND + 1}. */
    public static final int CITY_GROUND = 80;
    /** Rayon de la cuvette parfaitement plate ou s'etend la ville. */
    public static final double CITY_R = 150.0d;

    /** Les cinq biomes de Duna, repris tels quels pour Arrakis. */
    public enum Region {
        PLAINS("duna_plains", 122),
        LOWLANDS("duna_lowlands", 121),
        HILLS("duna_hills", 119),
        POLAR("duna_polar", 120),
        POLAR_HILLS("duna_polar_hills", 118);

        final String biome;
        /** Numero du biome dans NTM Space, si on ne peut pas le lui demander. */
        final int fallbackId;

        Region(String biome, int fallbackId) {
            this.biome = biome;
            this.fallbackId = fallbackId;
        }
    }

    /** Ce que la densite demande d'une colonne. */
    public record Column(double height, double base, double erosion, Region region) {
    }

    private static final long SEED = 0x41524B53L;
    private static final ArrakisNoise[] N = new ArrakisNoise[20];

    static {
        for (int k = 0; k < N.length; k++) {
            N[k] = new ArrakisNoise(SEED + 7919L * k);
        }
    }

    private static final double WIND_C = Math.cos(Math.toRadians(18.0d));
    private static final double WIND_S = Math.sin(Math.toRadians(18.0d));
    private static final double SEC_C = Math.cos(Math.toRadians(34.0d));
    private static final double SEC_S = Math.sin(Math.toRadians(34.0d));
    /** Les passes de la Muraille : angle (radians, vers +x puis +z) et demi-largeur au sommet. */
    private static final double[][] PASSES = {
            {Math.toRadians(90.0d), 46.0d}, {Math.toRadians(-38.0d), 15.0d}, {Math.toRadians(188.0d), 19.0d}};

    private static final int CACHE = 256;
    private static final ThreadLocal<Entry[]> LAST = ThreadLocal.withInitial(() -> new Entry[CACHE]);

    private static final class Entry {
        final int x;
        final int z;
        final Column column;

        Entry(int x, int z, Column column) {
            this.x = x;
            this.z = z;
            this.column = column;
        }
    }

    private ArrakisRelief() {
    }

    /** La colonne en (x, z), gardee un moment par fil : chaque sortie de la densite la redemande. */
    public static Column column(int x, int z) {
        Entry[] cache = LAST.get();
        int slot = (x * 31 + z * 17 + (x >> 4) * 7) & (CACHE - 1);
        Entry entry = cache[slot];
        if (entry != null && entry.x == x && entry.z == z) {
            return entry.column;
        }
        Column column = compute(x, z);
        cache[slot] = new Entry(x, z, column);
        return column;
    }

    private static double n(int k, double x, double z) {
        return N[k].sample(x, z);
    }

    private static double clamp(double v, double a, double b) {
        return v < a ? a : v > b ? b : v;
    }

    static double smooth(double e0, double e1, double v) {
        double t = clamp((v - e0) / (e1 - e0), 0.0d, 1.0d);
        return t * t * (3.0d - 2.0d * t);
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }

    /** Une dune vue de profil : pente douce au vent sur {@code a} de la longueur, face d'avalanche ensuite. */
    private static double profile(double t, double a) {
        if (t < a) {
            return 0.5d - 0.5d * Math.cos(Math.PI * t / a);
        }
        return 0.5d + 0.5d * Math.cos(Math.PI * (t - a) / (1.0d - a));
    }

    private static double terrace(double h, double step, double strength) {
        double f = h / step;
        double fl = Math.floor(f);
        double stepped = (fl + smooth(0.70d, 1.0d, f - fl)) * step;
        return lerp(strength, h, stepped);
    }

    private static double wrap(double a) {
        while (a > Math.PI) {
            a -= 2.0d * Math.PI;
        }
        while (a < -Math.PI) {
            a += 2.0d * Math.PI;
        }
        return a;
    }

    /** Le desert ouvert : hauteur, socle, poids de la roche, poids du sel, bruit de la roche. */
    private static double[] natural(double x, double z) {
        double base = 66.0d + 5.0d * n(0, x / 1100.0d, z / 1100.0d) + 2.0d * n(1, x / 310.0d, z / 310.0d);
        double rockN = 0.74d * n(2, x / 900.0d, z / 900.0d) + 0.22d * n(3, x / 260.0d, z / 260.0d)
                + 0.05d * n(4, x / 60.0d, z / 60.0d);
        double rock = smooth(0.50d, 0.515d, rockN);
        double panN = n(5, x / 1300.0d, z / 1300.0d) + 0.15d * n(6, x / 180.0d, z / 180.0d);
        double pan = smooth(0.62d, 0.68d, panN) * (1.0d - smooth(0.30d, 0.45d, rockN));
        // les grandes dunes transversales, cretes face au vent d'ouest-sud-ouest
        double u = x * WIND_C + z * WIND_S;
        double v = -x * WIND_S + z * WIND_C;
        double warp = 55.0d * n(7, u / 520.0d, v / 520.0d) + 12.0d * n(8, u / 150.0d, v / 150.0d);
        double ph = (u + warp) / 260.0d;
        double p = profile(ph - Math.floor(ph), 0.68d);
        double amp = 8.0d + 42.0d * clamp(0.5d + 0.6d * n(9, u / 800.0d, v / 800.0d), 0.0d, 1.0d);
        double crest = clamp(0.7d + 0.45d * n(10, u / 320.0d, v / 200.0d), 0.15d, 1.0d);
        double d1 = amp * crest * p;
        // les petites dunes qui courent sur les grandes
        double u2 = x * SEC_C + z * SEC_S;
        double ph2 = (u2 + 12.0d * n(11, x / 90.0d, z / 90.0d)) / 55.0d;
        double p2 = profile(ph2 - Math.floor(ph2), 0.62d);
        double d2 = 3.5d * (0.55d + 0.45d * n(12, x / 260.0d, z / 260.0d)) * p2 * (1.0d - 0.6d * p);
        double sand = lerp(pan, base + d1 + d2, base - 3.0d);
        // les massifs : flancs raides, sommets qui montent par larges paliers
        double q = clamp((rockN - 0.50d) / 0.20d, 0.0d, 1.0d);
        double mh = base + 25.0d + 80.0d * Math.pow(q, 0.6d) + 5.0d * n(13, x / 55.0d, z / 55.0d);
        double mt = terrace(mh, 9.0d, 0.6d);
        double height = lerp(rock, sand, Math.max(sand, mt));
        return new double[] {height, base, rock, pan, rockN};
    }

    /** La Muraille : hauteur, poids de la paroi, hauteur du glacis interieur, rayon de la crete. */
    private static double[] city(double x, double z, double r) {
        double th = Math.atan2(z, x);
        double cx = Math.cos(th);
        double cz = Math.sin(th);
        double rc = 300.0d + 30.0d * n(14, cx * 1.8d, cz * 1.8d);
        double hw = 172.0d + 20.0d * n(15, cx * 2.6d + 11.0d, cz * 2.6d - 7.0d) + 6.0d * n(4, x / 40.0d, z / 40.0d);
        double w;
        if (r < rc - 35.0d) {
            w = 0.0d;
        } else if (r < rc - 8.0d) {
            w = smooth(rc - 35.0d, rc - 8.0d, r);
        } else if (r <= rc + 12.0d) {
            w = 1.0d;
        } else if (r < rc + 75.0d) {
            w = 1.0d - smooth(rc + 12.0d, rc + 75.0d, r);
        } else {
            w = 0.0d;
        }
        double gap = 1.0d;
        for (double[] pass : PASSES) {
            double arc = Math.abs(wrap(th - pass[0])) * Math.max(r, 1.0d);
            gap *= smooth(pass[1], pass[1] + 24.0d, arc);
        }
        double wall = terrace(CITY_GROUND + 1.0d + (hw - CITY_GROUND - 1.0d) * w * gap, 9.0d, 0.5d);
        double apron = CITY_GROUND + 1.0d + 9.0d * smooth(CITY_R, rc - 40.0d, r)
                + 2.5d * n(3, x / 40.0d, z / 40.0d) * smooth(CITY_R, CITY_R + 50.0d, r);
        return new double[] {wall, w * gap, apron, rc};
    }

    private static Column compute(double x, double z) {
        double[] nat = natural(x, z);
        double natH = nat[0];
        double rock = nat[2];
        double h = natH;
        double base = nat[1];
        double erosion = rock;
        Region region = null;
        double r = Math.sqrt(x * x + z * z);
        if (r < 480.0d) {
            double[] wall = city(x, z, r);
            double rc = wall[3];
            double hc;
            if (r <= CITY_R) {
                hc = CITY_GROUND + 1.0d;
            } else if (r <= rc) {
                hc = Math.max(wall[2], wall[0]);
            } else {
                double apronAtCrest = city(x * rc / r, z * rc / r, rc)[2];
                hc = Math.max(wall[0], lerp(smooth(rc, rc + 140.0d, r), apronAtCrest, natH));
            }
            double blend = smooth(rc + 120.0d, rc + 140.0d, r);
            h = lerp(blend, hc, natH);
            base = lerp(blend, CITY_GROUND + 6.0d, nat[1]);
            erosion = lerp(blend, wall[1], rock);
            if (r < rc + 60.0d) {
                region = Region.HILLS;
            }
        }
        double pol = smooth(9000.0d, 11000.0d, Math.abs(z));
        if (pol > 0.0d) {
            double q = clamp((nat[4] - 0.42d) / 0.22d, 0.0d, 1.0d);
            double hp = nat[1] + 10.0d + 6.0d * n(1, x / 400.0d, z / 400.0d) + rock * 60.0d * q;
            h = lerp(pol, h, hp);
        }
        if (region == null) {
            if (pol > 0.5d) {
                region = rock > 0.5d ? Region.POLAR_HILLS : Region.POLAR;
            } else if (rock > 0.5d) {
                region = Region.HILLS;
            } else if (nat[3] > 0.5d) {
                region = Region.LOWLANDS;
            } else {
                region = Region.PLAINS;
            }
        }
        return new Column(h, base, erosion * 0.3d, region);
    }
}

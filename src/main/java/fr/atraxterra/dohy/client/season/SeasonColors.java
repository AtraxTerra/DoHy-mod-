package fr.atraxterra.dohy.client.season;

import fr.atraxterra.dohy.season.SeasonClimate;
import fr.atraxterra.dohy.season.SeasonMath;
import net.minecraft.core.BlockPos;

/**
 * Les teintes de saison de l'herbe et du feuillage.
 *
 * <h2>Le modele</h2>
 *
 * <p>Chaque saison a une teinte vers laquelle elle tire les couleurs du jeu, et
 * une force :
 * <ul>
 *   <li><b>automne</b> : le feuillage rougit franchement, et pas d'une seule
 *       couleur — rouge, orange, jaune ou roux selon le bosquet (des taches de 4
 *       blocs, a peu pres un arbre) ; l'herbe tourne a la paille ;</li>
 *   <li><b>hiver</b> : feuillage brun gris, herbe seche et terne ;</li>
 *   <li><b>printemps</b> : un vert tendre et vif ;</li>
 *   <li><b>ete</b> : les couleurs du jeu, intactes.</li>
 * </ul>
 * Les saisons voisines se melent selon {@link SeasonMath#weight} : les teintes
 * glissent sans saut d'une semaine a l'autre. La force est multipliee par celle
 * des couleurs a la latitude du bloc ({@link SeasonMath#colorStrength}) : rien
 * sous les tropiques, toute des 45° — la France comprise. Les sapins et les
 * bouleaux gardent leur couleur fixe : les resineux ne rougissent pas.
 *
 * <h2>Les paliers</h2>
 *
 * <p>Un chunk garde les couleurs de sa construction. Les teintes avancent donc
 * par paliers — 64 par annee, un peu plus de dix heures — et chaque palier fait
 * reconstruire les chunks affiches ({@link ClientSeasons}). Entre deux, rien ne
 * bouge, et les fils de construction ne lisent que des valeurs figees.
 */
public final class SeasonColors {

    /** Paliers de couleur par annee. */
    public static final int STEPS = 64;

    // hiver, printemps, ete, automne ; la teinte d'automne du feuillage vient de AUTUMN_LEAVES
    private static final int[] FOLIAGE = {0x7A6A4F, 0x7EDB3A, 0x000000, 0x000000};
    private static final double[] FOLIAGE_FORCE = {0.75d, 0.50d, 0.0d, 0.85d};
    private static final int[] GRASS = {0x9C8F6A, 0x86E04A, 0x000000, 0xC2A24A};
    private static final double[] GRASS_FORCE = {0.75d, 0.50d, 0.0d, 0.65d};

    /** Les feuillages d'automne : rouge, orange, jaune, roux. */
    private static final int[] AUTUMN_LEAVES = {0xC8321E, 0xE0701A, 0xD8A82A, 0xA8482A};

    /**
     * La teinte d'un hemisphere a un palier.
     *
     * @param grass        couleur visee par l'herbe, et sa force
     * @param foliage      couleur visee par le feuillage hors automne (hiver, printemps), et sa force
     * @param autumnForce  la part de l'automne dans le feuillage ; sa couleur depend du bosquet
     */
    private record Tint(int grass, float grassForce, int foliage, float foliageForce, float autumnForce) {
        static final Tint NONE = new Tint(0, 0.0f, 0, 0.0f, 0.0f);
    }

    private static volatile Tint north = Tint.NONE;
    private static volatile Tint south = Tint.NONE;

    private SeasonColors() {
    }

    /** Le palier de couleur d'une phase de l'annee. */
    static int step(double phase) {
        return (int) Math.floor(phase * STEPS);
    }

    /** Recalcule les teintes au milieu du palier de cette phase. */
    static void update(double phase) {
        double middle = (step(phase) + 0.5d) / STEPS;
        north = tint(SeasonMath.local(middle, 45.0d));
        south = tint(SeasonMath.local(middle, -45.0d));
    }

    static void clear() {
        north = Tint.NONE;
        south = Tint.NONE;
    }

    private static Tint tint(double local) {
        double[] grass = new double[3];
        double[] foliage = new double[3];
        double grassForce = 0.0d;
        double foliageForce = 0.0d;
        double autumnForce = 0.0d;
        for (int season = 0; season < 4; season++) {
            double weight = SeasonMath.weight(local, season);
            double g = weight * GRASS_FORCE[season];
            add(grass, GRASS[season], g);
            grassForce += g;
            double f = weight * FOLIAGE_FORCE[season];
            if (season == SeasonMath.AUTUMN) {
                autumnForce += f;
            } else {
                add(foliage, FOLIAGE[season], f);
                foliageForce += f;
            }
        }
        return new Tint(color(grass, grassForce), (float) grassForce, color(foliage, foliageForce),
                (float) foliageForce, (float) autumnForce);
    }

    private static void add(double[] sum, int rgb, double weight) {
        sum[0] += ((rgb >> 16) & 0xFF) * weight;
        sum[1] += ((rgb >> 8) & 0xFF) * weight;
        sum[2] += (rgb & 0xFF) * weight;
    }

    private static int color(double[] sum, double total) {
        if (total <= 0.0d) {
            return 0;
        }
        int red = (int) Math.round(sum[0] / total);
        int green = (int) Math.round(sum[1] / total);
        int blue = (int) Math.round(sum[2] / total);
        return red << 16 | green << 8 | blue;
    }

    /** L'herbe de ce bloc a la saison. */
    public static int grass(int base, BlockPos pos) {
        float strength = SeasonClimate.CLIENT.strength(pos.getX(), pos.getZ());
        if (strength == 0.0f) {
            return base;
        }
        Tint tint = strength > 0.0f ? north : south;
        return blend(base, tint.grass(), Math.abs(strength) * tint.grassForce());
    }

    /** Le feuillage de ce bloc a la saison. */
    public static int foliage(int base, BlockPos pos) {
        float strength = SeasonClimate.CLIENT.strength(pos.getX(), pos.getZ());
        if (strength == 0.0f) {
            return base;
        }
        Tint tint = strength > 0.0f ? north : south;
        float total = tint.foliageForce() + tint.autumnForce();
        if (total <= 0.0f) {
            return base;
        }
        int target = tint.foliage();
        if (tint.autumnForce() > 0.0f) {
            target = mix(target, AUTUMN_LEAVES[grove(pos)], tint.autumnForce() / total);
        }
        return blend(base, target, Math.abs(strength) * total);
    }

    /** Le bosquet d'un bloc : une couleur d'automne par tache de 4 blocs, tiree d'un hachage de la position. */
    private static int grove(BlockPos pos) {
        long hash = (pos.getX() >> 2) * 0x9E3779B97F4A7C15L ^ (pos.getZ() >> 2) * 0xC2B2AE3D27D4EB4FL;
        hash ^= hash >>> 31;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 29;
        return (int) Math.floorMod(hash, (long) AUTUMN_LEAVES.length);
    }

    /** Un melange de deux couleurs, {@code share} de la seconde. */
    private static int mix(int first, int second, float share) {
        float keep = 1.0f - share;
        int red = Math.round(((first >> 16) & 0xFF) * keep + ((second >> 16) & 0xFF) * share);
        int green = Math.round(((first >> 8) & 0xFF) * keep + ((second >> 8) & 0xFF) * share);
        int blue = Math.round((first & 0xFF) * keep + (second & 0xFF) * share);
        return red << 16 | green << 8 | blue;
    }

    private static int blend(int base, int target, float amount) {
        if (amount <= 0.0f) {
            return base;
        }
        return base & 0xFF000000 | mix(base & 0xFFFFFF, target, Math.min(1.0f, amount));
    }
}

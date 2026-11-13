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
 *   <li><b>automne</b> : feuillage roux, herbe paille ;</li>
 *   <li><b>hiver</b> : feuillage et herbe ternes, brun olive ;</li>
 *   <li><b>printemps</b> : un vert plus vif, doucement ;</li>
 *   <li><b>ete</b> : les couleurs du jeu, intactes.</li>
 * </ul>
 * Les saisons voisines se melent selon {@link SeasonMath#weight} : les teintes
 * glissent sans saut d'une semaine a l'autre. La force est multipliee par celle
 * des saisons a la latitude du bloc : rien sous les tropiques, tout au-dela de
 * 65°. Les sapins et les bouleaux gardent leur couleur fixe — les resineux ne
 * rougissent pas.
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

    private static final int[] FOLIAGE = {0x8A7F62, 0x6FC23F, 0x000000, 0xD2691E};
    private static final double[] FOLIAGE_FORCE = {0.55d, 0.25d, 0.0d, 0.60d};
    private static final int[] GRASS = {0x9A9370, 0x7CCB4A, 0x000000, 0xB5A04A};
    private static final double[] GRASS_FORCE = {0.50d, 0.25d, 0.0d, 0.40d};

    /** La teinte d'un hemisphere a un palier : couleur visee et force, pour l'herbe et le feuillage. */
    private record Tint(int grass, float grassForce, int foliage, float foliageForce) {
        static final Tint NONE = new Tint(0, 0.0f, 0, 0.0f);
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
        double[] grass = new double[4];
        double[] foliage = new double[4];
        double grassForce = 0.0d;
        double foliageForce = 0.0d;
        for (int season = 0; season < 4; season++) {
            double weight = SeasonMath.weight(local, season);
            double g = weight * GRASS_FORCE[season];
            double f = weight * FOLIAGE_FORCE[season];
            add(grass, GRASS[season], g);
            add(foliage, FOLIAGE[season], f);
            grassForce += g;
            foliageForce += f;
        }
        return new Tint(color(grass, grassForce), (float) grassForce, color(foliage, foliageForce),
                (float) foliageForce);
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
        return blend(base, tint.foliage(), Math.abs(strength) * tint.foliageForce());
    }

    private static int blend(int base, int target, float amount) {
        if (amount <= 0.0f) {
            return base;
        }
        float keep = 1.0f - amount;
        int red = Math.round(((base >> 16) & 0xFF) * keep + ((target >> 16) & 0xFF) * amount);
        int green = Math.round(((base >> 8) & 0xFF) * keep + ((target >> 8) & 0xFF) * amount);
        int blue = Math.round((base & 0xFF) * keep + (target & 0xFF) * amount);
        return base & 0xFF000000 | red << 16 | green << 8 | blue;
    }
}

package fr.atraxterra.dohy.client.season;

import fr.atraxterra.dohy.season.SeasonClimate;
import fr.atraxterra.dohy.season.SeasonMath;
import fr.atraxterra.dohy.season.SeasonPalette;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.FoliageColor;

/**
 * Les teintes de saison de l'herbe, du feuillage et des bouleaux.
 *
 * <h2>Le modele</h2>
 *
 * <p>Les teintes viennent de {@link SeasonPalette} : douze temps de l'annee
 * (debut, coeur et fin de chaque saison), dans l'esprit de Serene Seasons, qui
 * se fondent l'un dans l'autre. L'herbe jaunit a la fin de l'ete, le feuillage
 * s'embrase a l'automne — une couleur par bosquet, une tache de 4 blocs, a peu
 * pres un arbre — puis brunit ; l'hiver laisse des arbres nus et une herbe pale ;
 * le printemps reverdit en vert tendre. Les bosquets sont en avance ou en
 * retard d'un jour au plus : les arbres ne tournent pas tous ensemble. Les
 * bouleaux dorent a l'automne ; les sapins gardent leur couleur fixe.
 *
 * <p>Deux forces, lues par bande de latitude dans {@link SeasonClimate} :
 * celle des saisons temperees ({@link SeasonMath#colorStrength}, toute des 40°)
 * et celle de la saison seche et de la saison des pluies sous les tropiques
 * ({@link SeasonMath#tropicsColorStrength}). Un bloc recoit la premiere, puis la
 * seconde par-dessus ; elles ne se croisent qu'entre 23 et 30°, faibles l'une et
 * l'autre.
 *
 * <h2>Les paliers</h2>
 *
 * <p>Un chunk garde les couleurs de sa construction. Les teintes avancent donc
 * par paliers — {@link #STEPS} par annee, sept heures — et chaque palier fait
 * reconstruire les chunks affiches ({@link ClientSeasons}). Entre deux, rien ne
 * bouge : les fils de construction ne lisent que des tables figees, refaites
 * d'un bloc a chaque palier.
 */
public final class SeasonColors {

    /** Paliers de couleur par annee. */
    public static final int STEPS = 96;

    private static final int SHADES = SeasonPalette.AUTUMN.length;

    /**
     * Les teintes d'un hemisphere a un palier.
     *
     * @param grass          teinte visee par l'herbe, et sa force
     * @param foliage        teinte visee par le feuillage, par niveau d'avance puis couleur d'automne du bosquet
     * @param foliageForce   force du feuillage, par niveau d'avance
     * @param birch          teinte visee par les bouleaux, par niveau d'avance
     * @param birchForce     force des bouleaux, par niveau d'avance
     * @param tropicGrass    sous les tropiques, teinte visee par l'herbe, et sa force
     * @param tropicFoliage  sous les tropiques, teinte visee par le feuillage et les bouleaux, et sa force
     */
    private record Tint(int grass, float grassForce, int[] foliage, float[] foliageForce, int[] birch,
                        float[] birchForce, int tropicGrass, float tropicGrassForce, int tropicFoliage,
                        float tropicFoliageForce) {
        static final Tint NONE = new Tint(0, 0.0f, new int[SeasonPalette.LEVELS * SHADES],
                new float[SeasonPalette.LEVELS], new int[SeasonPalette.LEVELS], new float[SeasonPalette.LEVELS], 0,
                0.0f, 0, 0.0f);
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
        SeasonPalette.Key now = SeasonPalette.at(local);
        int[] foliage = new int[SeasonPalette.LEVELS * SHADES];
        float[] foliageForce = new float[SeasonPalette.LEVELS];
        int[] birch = new int[SeasonPalette.LEVELS];
        float[] birchForce = new float[SeasonPalette.LEVELS];
        for (int level = 0; level < SeasonPalette.LEVELS; level++) {
            SeasonPalette.Key key = SeasonPalette.at(local + SeasonPalette.precocity(level));
            foliageForce[level] = (float) key.foliageForce();
            for (int shade = 0; shade < SHADES; shade++) {
                foliage[level * SHADES + shade] = SeasonPalette.foliageTarget(key, shade);
            }
            birch[level] = key.birch();
            birchForce[level] = (float) key.birchForce();
        }
        SeasonPalette.Tropics tropics = SeasonPalette.tropics(local);
        return new Tint(now.grass(), (float) now.grassForce(), foliage, foliageForce, birch, birchForce,
                tropics.grass(), (float) tropics.grassForce(), tropics.foliage(), (float) tropics.foliageForce());
    }

    /** L'hemisphere d'un bloc d'apres ses forces signees ; {@code null} si aucune saison ne le colore. */
    private static Tint hemisphere(float strength, float tropics) {
        if (strength == 0.0f && tropics == 0.0f) {
            return null;
        }
        return (strength != 0.0f ? strength : tropics) > 0.0f ? north : south;
    }

    /** L'herbe de ce bloc a la saison. */
    public static int grass(int base, BlockPos pos) {
        float strength = SeasonClimate.CLIENT.strength(pos.getX(), pos.getZ());
        float tropics = SeasonClimate.CLIENT.tropics(pos.getX(), pos.getZ());
        Tint tint = hemisphere(strength, tropics);
        if (tint == null) {
            return base;
        }
        int color = SeasonPalette.blend(base, tint.grass(), Math.abs(strength) * tint.grassForce());
        return SeasonPalette.blend(color, tint.tropicGrass(), Math.abs(tropics) * tint.tropicGrassForce());
    }

    /** Le feuillage de ce bloc a la saison (feuilles des feuillus, lianes, feuilles mortes au sol). */
    public static int foliage(int base, BlockPos pos) {
        float strength = SeasonClimate.CLIENT.strength(pos.getX(), pos.getZ());
        float tropics = SeasonClimate.CLIENT.tropics(pos.getX(), pos.getZ());
        Tint tint = hemisphere(strength, tropics);
        if (tint == null) {
            return base;
        }
        int grove = grove(pos);
        int level = grove % SeasonPalette.LEVELS;
        int shade = grove / SeasonPalette.LEVELS % SHADES;
        int color = SeasonPalette.blend(base, tint.foliage()[level * SHADES + shade],
                Math.abs(strength) * tint.foliageForce()[level]);
        return SeasonPalette.blend(color, tint.tropicFoliage(), Math.abs(tropics) * tint.tropicFoliageForce());
    }

    /** Les feuilles de bouleau de ce bloc a la saison : la couleur fixe du jeu, tiree vers celle de l'annee. */
    public static int birch(BlockPos pos) {
        int base = FoliageColor.FOLIAGE_BIRCH;
        float strength = SeasonClimate.CLIENT.strength(pos.getX(), pos.getZ());
        float tropics = SeasonClimate.CLIENT.tropics(pos.getX(), pos.getZ());
        Tint tint = hemisphere(strength, tropics);
        if (tint == null) {
            return base;
        }
        int level = grove(pos) % SeasonPalette.LEVELS;
        int color = SeasonPalette.blend(base, tint.birch()[level], Math.abs(strength) * tint.birchForce()[level]);
        return SeasonPalette.blend(color, tint.tropicFoliage(), Math.abs(tropics) * tint.tropicFoliageForce());
    }

    /**
     * Le bosquet d'un bloc : un nombre tire d'un hachage de sa tache de 4 blocs
     * (sans la hauteur : un arbre garde la meme couleur de haut en bas). Ses bits
     * bas donnent l'avance, les suivants la couleur d'automne.
     */
    private static int grove(BlockPos pos) {
        long hash = (pos.getX() >> 2) * 0x9E3779B97F4A7C15L ^ (pos.getZ() >> 2) * 0xC2B2AE3D27D4EB4FL;
        hash ^= hash >>> 31;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 29;
        return (int) (hash >>> 33);
    }
}

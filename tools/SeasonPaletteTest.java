package fr.atraxterra.dohy.season;

/**
 * Test des couleurs des saisons, sans Minecraft.
 *
 * <p>{@link SeasonPalette} ne depend que de {@code java.lang} et de
 * {@link SeasonMath} : il se compile et s'execute seul, et la CI le lance a
 * chaque poussee. On y verifie que les douze temps de l'annee se fondent sans
 * saut, que l'annee boucle, et que chaque saison a bien sa tete : un ete fidele
 * au jeu, un automne embrase et varie, des arbres nus l'hiver, un vert tendre au
 * printemps, des bouleaux dores, et la saison seche des tropiques.
 *
 * <pre>
 *   javac -d /tmp/palette-season src/main/java/fr/atraxterra/dohy/season/SeasonMath.java \
 *       src/main/java/fr/atraxterra/dohy/season/SeasonPalette.java tools/SeasonPaletteTest.java
 *   java -cp /tmp/palette-season fr.atraxterra.dohy.season.SeasonPaletteTest
 * </pre>
 */
public final class SeasonPaletteTest {

    // Les couleurs du jeu pour une foret : l'herbe et le feuillage que les saisons tirent.
    private static final int FOREST_GRASS = 0x79C05A;
    private static final int FOREST_FOLIAGE = 0x59AE30;

    // Le coeur de chaque saison du nord.
    private static final double MID_WINTER = 0.125d;
    private static final double MID_SPRING = 0.375d;
    private static final double MID_SUMMER = 0.625d;
    private static final double LATE_SUMMER = 0.708d;
    private static final double MID_AUTUMN = 0.875d;

    private static int failures;

    private SeasonPaletteTest() {
    }

    public static void main(String[] args) {
        // Aucun saut : d'un millieme d'annee au suivant, aucune teinte ne bouge de plus de 4 sur 255
        // (un vrai saut se compterait en dizaines).
        int worst = 0;
        double worstAt = 0.0d;
        SeasonPalette.Key previous = SeasonPalette.at(0.0d);
        for (int i = 1; i <= 1000; i++) {
            double local = i / 1000.0d;
            SeasonPalette.Key key = SeasonPalette.at(local);
            int jump = Math.max(Math.max(distance(previous.grass(), key.grass()), distance(previous.green(), key.green())),
                    distance(previous.birch(), key.birch()));
            for (int shade = 0; shade < SeasonPalette.AUTUMN.length; shade++) {
                jump = Math.max(jump, distance(SeasonPalette.foliageTarget(previous, shade),
                        SeasonPalette.foliageTarget(key, shade)));
            }
            double forces = Math.max(Math.max(Math.abs(previous.grassForce() - key.grassForce()),
                    Math.abs(previous.foliageForce() - key.foliageForce())), Math.abs(previous.birchForce() - key.birchForce()));
            jump = Math.max(jump, (int) Math.ceil(forces * 255.0d));
            if (jump > worst) {
                worst = jump;
                worstAt = local;
            }
            previous = key;
        }
        check("aucun saut d'un millieme d'annee au suivant (pire : " + worst + " a " + worstAt + ")", worst <= 4);
        check("l'annee boucle : la fin de l'automne rejoint le debut de l'hiver",
                SeasonPalette.at(0.0d).equals(SeasonPalette.at(1.0d)) && SeasonPalette.at(-0.25d).equals(SeasonPalette.at(0.75d)));
        check("au coeur d'un temps, ses teintes exactement", SeasonPalette.at(MID_AUTUMN).equals(SeasonPalette.key(10)));

        // L'ete : les couleurs du jeu, a peine touchees.
        SeasonPalette.Key summer = SeasonPalette.at(MID_SUMMER);
        check("au coeur de l'ete, l'herbe reste celle du jeu", summer.grassForce() <= 0.15d);
        check("au coeur de l'ete, le feuillage reste celui du jeu", summer.foliageForce() <= 0.15d
                && summer.autumn() == 0.0d && summer.wither() == 0.0d);
        check("au coeur de l'ete, les bouleaux gardent leur couleur", summer.birchForce() <= 0.05d);

        // La fin de l'ete : l'herbe jaunit.
        int summerGrass = paint(FOREST_GRASS, summer.grass(), summer.grassForce());
        SeasonPalette.Key late = SeasonPalette.at(LATE_SUMMER);
        int lateGrass = paint(FOREST_GRASS, late.grass(), late.grassForce());
        check("a la fin de l'ete, l'herbe jaunit", warmth(lateGrass) > warmth(summerGrass) + 0.08d);

        // L'automne : embrase, et varie d'un bosquet a l'autre.
        SeasonPalette.Key autumn = SeasonPalette.at(MID_AUTUMN);
        check("au coeur de l'automne, toutes les couleurs d'automne", autumn.autumn() >= 0.95d);
        check("au coeur de l'automne, le feuillage change franchement", autumn.foliageForce() >= 0.9d);
        int summerLeaves = paint(FOREST_FOLIAGE, SeasonPalette.foliageTarget(summer, 0), summer.foliageForce());
        int reddest = 0;
        for (int shade = 0; shade < SeasonPalette.AUTUMN.length; shade++) {
            int leaves = paint(FOREST_FOLIAGE, SeasonPalette.foliageTarget(autumn, shade), autumn.foliageForce());
            check("bosquet " + shade + " : chaud (rouge au-dessus du vert)", red(leaves) > green(leaves));
            check("bosquet " + shade + " : loin du vert de l'ete", distance(leaves, summerLeaves) >= 90);
            reddest = Math.max(reddest, red(leaves) - green(leaves));
        }
        int spread = 0;
        for (int a = 0; a < SeasonPalette.AUTUMN.length; a++) {
            for (int b = a + 1; b < SeasonPalette.AUTUMN.length; b++) {
                spread = Math.max(spread, distance(SeasonPalette.foliageTarget(autumn, a), SeasonPalette.foliageTarget(autumn, b)));
            }
        }
        check("des bosquets bien differents (ecart " + spread + ")", spread >= 120);
        check("au moins un bosquet franchement rouge", reddest >= 120);

        // Les bosquets precoces tournent avant les tardifs.
        near("le plus tardif a un jour de retard", SeasonPalette.precocity(0), -SeasonPalette.SPREAD);
        near("le plus precoce a un jour d'avance", SeasonPalette.precocity(SeasonPalette.LEVELS - 1), SeasonPalette.SPREAD);
        double start = 0.76d;
        double early = SeasonPalette.at(start + SeasonPalette.precocity(SeasonPalette.LEVELS - 1)).autumn();
        double tardy = SeasonPalette.at(start + SeasonPalette.precocity(0)).autumn();
        check("au debut de l'automne, les precoces ont tourne et pas les tardifs (" + round(early) + " / " + round(tardy) + ")",
                early >= 0.6d && tardy <= 0.15d);

        // La fin de l'automne : le feuillage brunit, l'hiver : des arbres nus.
        check("a la fin de l'automne, les feuilles brunissent", SeasonPalette.key(11).wither() >= 0.5d);
        SeasonPalette.Key winter = SeasonPalette.at(MID_WINTER);
        int bare = paint(FOREST_FOLIAGE, SeasonPalette.foliageTarget(winter, 0), winter.foliageForce());
        check("en hiver, des arbres nus : gris brun", red(bare) >= green(bare) - 4 && saturation(bare) < 0.3d);
        int winterGrass = paint(FOREST_GRASS, winter.grass(), winter.grassForce());
        check("en hiver, une herbe pale et terne", saturation(winterGrass) < saturation(FOREST_GRASS) * 0.55d);

        // Le printemps : un vert tendre, plus vif que le jeu.
        SeasonPalette.Key spring = SeasonPalette.at(MID_SPRING);
        int springLeaves = paint(FOREST_FOLIAGE, SeasonPalette.foliageTarget(spring, 0), spring.foliageForce());
        check("au printemps, le feuillage est d'un vert vif", green(springLeaves) > green(FOREST_FOLIAGE) + 20
                && green(springLeaves) > red(springLeaves) + 60);

        // Les bouleaux : or a l'automne.
        int birch = paint(0x80A755, autumn.birch(), autumn.birchForce());
        check("a l'automne, les bouleaux dorent", red(birch) > 200 && blue(birch) < 80 && red(birch) > green(birch));

        // Les tropiques : saison seche doree, saison des pluies vive, rien aux mi-saisons.
        SeasonPalette.Tropics dry = SeasonPalette.tropics(MID_WINTER);
        SeasonPalette.Tropics wet = SeasonPalette.tropics(MID_SUMMER);
        SeasonPalette.Tropics turn = SeasonPalette.tropics(MID_SPRING);
        check("au coeur de la saison seche, l'herbe dore", dry.grassForce() >= 0.5d
                && warmth(paint(0xBFB755, dry.grass(), dry.grassForce())) > warmth(0xBFB755));
        check("au coeur des pluies, l'herbe reverdit", wet.grassForce() >= 0.3d && green(wet.grass()) > red(wet.grass()) + 80);
        check("aux mi-saisons des tropiques, rien", turn.grassForce() < 1e-9 && turn.foliageForce() < 1e-9);

        // Le melange garde la transparence de la couleur du jeu.
        eq("la transparence est gardee", SeasonPalette.blend(0xFF123456, 0xABCDEF, 0.5d) >>> 24, 0xFF);
        eq("force nulle : la couleur du jeu", SeasonPalette.blend(0xFF123456, 0xABCDEF, 0.0d), 0xFF123456);

        if (failures > 0) {
            System.out.println(failures + " echec(s)");
            System.exit(1);
        }
        System.out.println("couleurs des saisons : tout passe");
    }

    /** Ce que donne une couleur du jeu tiree vers une teinte. */
    private static int paint(int base, int target, double force) {
        return SeasonPalette.blend(base, target, force) & 0xFFFFFF;
    }

    private static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    private static int green(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    private static int blue(int rgb) {
        return rgb & 0xFF;
    }

    /** L'ecart entre deux couleurs : la plus grande difference d'un canal. */
    private static int distance(int a, int b) {
        return Math.max(Math.max(Math.abs(red(a) - red(b)), Math.abs(green(a) - green(b))), Math.abs(blue(a) - blue(b)));
    }

    /** Rouge sur vert : monte quand une herbe jaunit. */
    private static double warmth(int rgb) {
        return red(rgb) / (double) Math.max(1, green(rgb));
    }

    /** La saturation, de 0 (gris) a 1. */
    private static double saturation(int rgb) {
        int max = Math.max(red(rgb), Math.max(green(rgb), blue(rgb)));
        int min = Math.min(red(rgb), Math.min(green(rgb), blue(rgb)));
        return max == 0 ? 0.0d : (max - min) / (double) max;
    }

    private static double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private static void near(String what, double actual, double expected) {
        report(Math.abs(actual - expected) < 1e-9, what, actual + " vs " + expected);
    }

    private static void eq(String what, long actual, long expected) {
        report(actual == expected, what, actual + " vs " + expected);
    }

    private static void check(String what, boolean passed) {
        report(passed, what, passed ? "vrai" : "faux");
    }

    private static void report(boolean passed, String what, String detail) {
        if (!passed) {
            failures++;
        }
        System.out.println((passed ? "ok    " : "ECHEC ") + what + "  (" + detail + ")");
    }
}

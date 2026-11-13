package fr.atraxterra.dohy.season;

/**
 * Test des saisons, sans Minecraft.
 *
 * <p>{@link SeasonMath} ne depend que de {@code java.lang} : il se compile et
 * s'execute seul, et la CI le lance a chaque poussee. On y verifie le
 * calendrier (une saison par semaine, le sud a contre-temps) et surtout le
 * reglage « realiste » choisi par le joueur : des plaines vertes a Paris en
 * plein hiver, de la neige a Stockholm, des glaciers qui tiennent l'ete.
 *
 * <pre>
 *   javac -d /tmp/season src/main/java/fr/atraxterra/dohy/season/SeasonMath.java tools/SeasonMathTest.java
 *   java -cp /tmp/season fr.atraxterra.dohy.season.SeasonMathTest
 * </pre>
 */
public final class SeasonMathTest {

    private static final long DAY = 86_400_000L;
    private static final long YEAR = 28L * DAY;
    private static final long ANCHOR = 1_795_320_000_000L;

    /** Le coeur de l'hiver du nord, et celui de son ete. */
    private static final double MID_WINTER = 0.125d;
    private static final double MID_SUMMER = 0.625d;

    // Temperatures de biomes du jeu.
    private static final double PLAINS = 0.8d;
    private static final double FOREST = 0.7d;
    private static final double TAIGA = 0.25d;
    private static final double SNOWY_PLAINS = 0.0d;
    private static final double FROZEN_PEAKS = -0.7d;

    private static int failures;

    private SeasonMathTest() {
    }

    public static void main(String[] args) {
        // Le calendrier.
        near("l'ancre ouvre l'annee", SeasonMath.phase(ANCHOR, ANCHOR, YEAR), 0.0d);
        near("une semaine plus tard, un quart d'annee", SeasonMath.phase(ANCHOR + 7 * DAY, ANCHOR, YEAR), 0.25d);
        near("la veille de l'ancre, la fin de l'annee precedente",
                SeasonMath.phase(ANCHOR - DAY, ANCHOR, YEAR), 27.0d / 28.0d);
        eq("au nord, la premiere semaine est l'hiver", SeasonMath.season(SeasonMath.local(0.1d, 48.0d)),
                SeasonMath.WINTER);
        eq("au sud, la meme semaine est l'ete", SeasonMath.season(SeasonMath.local(0.1d, -34.0d)),
                SeasonMath.SUMMER);
        eq("la deuxieme semaine est le printemps du nord", SeasonMath.season(SeasonMath.local(0.3d, 48.0d)),
                SeasonMath.SPRING);
        eq("le premier jour d'une saison est le jour 1", SeasonMath.day(0.0d, 7), 1);
        eq("le dernier instant d'une saison est le jour 7", SeasonMath.day(0.2499d, 7), 7);
        near("a l'ouverture d'une saison, il en reste une entiere", SeasonMath.untilNext(0.25d), 0.25d);

        // La force des saisons.
        near("pas d'hiver a l'equateur", SeasonMath.strength(0.0d), 0.0d);
        near("pas d'hiver sous les tropiques", SeasonMath.strength(23.0d), 0.0d);
        near("toute la force a 65°", SeasonMath.strength(65.0d), 1.0d);
        near("toute la force au-dela", SeasonMath.strength(80.0d), 1.0d);
        near("le sud comme le nord", SeasonMath.strength(-50.0d), SeasonMath.strength(50.0d));
        check("la force monte avec la latitude",
                SeasonMath.strength(35.0d) < SeasonMath.strength(45.0d)
                        && SeasonMath.strength(45.0d) < SeasonMath.strength(55.0d));
        near("le plein hiver a 70° retire 0,75", SeasonMath.offset(70.0d, MID_WINTER), -0.75d);
        near("au meme moment, l'ete du sud ajoute 0,225", SeasonMath.offset(-70.0d, MID_WINTER), 0.225d);
        near("l'equateur ne bouge pas", SeasonMath.offset(0.0d, MID_WINTER), 0.0d);

        // Le reglage « realiste ».
        check("Paris (48,9° N), plaine, plein hiver : pas de neige",
                PLAINS + SeasonMath.offset(48.85d, MID_WINTER) >= SeasonMath.SNOW_LINE);
        check("Paris, foret, plein hiver : pas de neige",
                FOREST + SeasonMath.offset(48.85d, MID_WINTER) >= SeasonMath.SNOW_LINE);
        check("Stockholm (59,3° N), foret, plein hiver : neige",
                FOREST + SeasonMath.offset(59.3d, MID_WINTER) < SeasonMath.SNOW_LINE);
        check("Berlin (52,5° N), foret, plein hiver : neige",
                FOREST + SeasonMath.offset(52.5d, MID_WINTER) < SeasonMath.SNOW_LINE);
        check("Moscou (55,8° N), plaine, plein hiver : neige",
                PLAINS + SeasonMath.offset(55.75d, MID_WINTER) < SeasonMath.SNOW_LINE);
        check("Stockholm, plaine, plein hiver : neige",
                PLAINS + SeasonMath.offset(59.3d, MID_WINTER) < SeasonMath.SNOW_LINE);
        check("taiga a 45°, plein hiver : neige",
                TAIGA + SeasonMath.offset(45.0d, MID_WINTER) < SeasonMath.SNOW_LINE);
        check("Stockholm au printemps (mi-saison) : la neige fond",
                FOREST + SeasonMath.offset(59.3d, 0.375d) >= SeasonMath.MELT_LINE);
        check("Buenos Aires (34,6° S), plaine, son hiver : pas de neige",
                PLAINS + SeasonMath.offset(-34.6d, MID_SUMMER) >= SeasonMath.SNOW_LINE);
        check("Ushuaia (54,8° S), foret, son hiver : neige",
                FOREST + SeasonMath.offset(-54.8d, MID_SUMMER) < SeasonMath.SNOW_LINE);
        check("Svalbard (78° N), plaine enneigee, plein ete : degel",
                SNOWY_PLAINS + SeasonMath.offset(78.0d, MID_SUMMER) >= SeasonMath.MELT_LINE);
        check("Svalbard, sommets geles, plein ete : le glacier tient",
                FROZEN_PEAKS + SeasonMath.offset(78.0d, MID_SUMMER) < SeasonMath.SNOW_LINE);
        check("la limite de fonte est au-dessus de celle de la neige",
                SeasonMath.MELT_LINE > SeasonMath.SNOW_LINE);

        // Les cultures.
        near("sous les tropiques, tout pousse", SeasonMath.growth(10.0d, MID_WINTER), 1.0d);
        near("coeur de l'hiver a 70° : une pousse sur cinq", SeasonMath.growth(70.0d, MID_WINTER), 0.2d);
        near("ete a 70° : tout pousse", SeasonMath.growth(70.0d, MID_SUMMER), 1.0d);
        check("a 45° en plein hiver, entre les deux",
                SeasonMath.growth(45.0d, MID_WINTER) > 0.2d && SeasonMath.growth(45.0d, MID_WINTER) < 1.0d);

        // La force des couleurs : nulle sous les tropiques, entiere des 45° (la France).
        near("pas de couleurs de saison sous les tropiques", SeasonMath.colorStrength(20.0d), 0.0d);
        near("toutes les couleurs a Paris", SeasonMath.colorStrength(48.85d), 1.0d);
        near("toutes les couleurs a 45° sud", SeasonMath.colorStrength(-45.0d), 1.0d);
        check("les couleurs viennent avant le froid",
                SeasonMath.colorStrength(35.0d) > SeasonMath.strength(35.0d));

        // Les couleurs : deux saisons voisines se partagent toujours le tout.
        for (double local = 0.0d; local < 1.0d; local += 0.01d) {
            double sum = 0.0d;
            for (int season = 0; season < 4; season++) {
                sum += SeasonMath.weight(local, season);
            }
            if (Math.abs(sum - 1.0d) > 1e-9) {
                report(false, "les poids des saisons font 1 a la phase " + local, "somme " + sum);
            }
        }
        near("au coeur de l'hiver, l'hiver seul", SeasonMath.weight(MID_WINTER, SeasonMath.WINTER), 1.0d);
        near("au coeur de l'hiver, rien de l'ete", SeasonMath.weight(MID_WINTER, SeasonMath.SUMMER), 0.0d);
        near("a la fin de l'automne, moitie automne", SeasonMath.weight(0.0d, SeasonMath.AUTUMN), 0.5d);

        // Les noms et les degres.
        text("le nom de l'hiver du nord", SeasonMath.name(48.0d, 0.1d), "Hiver");
        text("l'ete austral", SeasonMath.name(-40.0d, 0.1d), "Ete");
        text("la saison seche des tropiques", SeasonMath.name(10.0d, MID_WINTER), "Saison seche");
        text("la saison des pluies des tropiques", SeasonMath.name(10.0d, MID_SUMMER), "Saison des pluies");
        eq("la limite de la neige vaut -1 °C", SeasonMath.celsius(SeasonMath.SNOW_LINE), -1);
        eq("une plaine vaut 15 °C", SeasonMath.celsius(PLAINS), 15);

        if (failures > 0) {
            System.out.println(failures + " echec(s)");
            System.exit(1);
        }
        System.out.println("saisons : tout passe");
    }

    private static void near(String what, double actual, double expected) {
        report(Math.abs(actual - expected) < 1e-6, what, actual + " vs " + expected);
    }

    private static void eq(String what, long actual, long expected) {
        report(actual == expected, what, actual + " vs " + expected);
    }

    private static void text(String what, String actual, String expected) {
        report(expected.equals(actual), what, actual + " vs " + expected);
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

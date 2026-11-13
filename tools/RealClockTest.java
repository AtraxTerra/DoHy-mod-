package fr.atraxterra.dohy.util;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Test de l'horloge reelle, sans Minecraft.
 *
 * <p>{@link RealClock} ne depend que de {@code java.time}, donc il se compile et
 * s'execute seul — c'est le seul morceau du mod qu'on peut verifier autrement
 * qu'en jeu, et c'est justement celui ou une erreur ne se verrait qu'a 5 h du
 * matin trois jours plus tard. La CI le lance a chaque poussee.
 *
 * <p>Le test vit dans le meme paquet que la classe pour atteindre ses variantes
 * a instant injecte sans reflexion.
 *
 * <pre>
 *   javac -d /tmp/clock src/main/java/fr/atraxterra/dohy/util/RealClock.java tools/RealClockTest.java
 *   java -cp /tmp/clock fr.atraxterra.dohy.util.RealClockTest
 * </pre>
 */
public final class RealClockTest {

    private static int failures;

    private RealClockTest() {
    }

    public static void main(String[] args) {
        // 2026-11-05 est un jeudi ; 2026-11-09 un lundi.
        long thursdayEvening = day(5, "2026-11-05T20:00");

        eq("jeudi 20h et jeudi 23h59, meme journee",
                thursdayEvening, day(5, "2026-11-05T23:59"));
        eq("minuit ne change rien : 2h du matin reste la soiree de la veille",
                thursdayEvening, day(5, "2026-11-06T02:00"));
        eq("4h59 est encore la veille",
                thursdayEvening, day(5, "2026-11-06T04:59"));
        ne("5h00 fait basculer",
                thursdayEvening, day(5, "2026-11-06T05:00"));
        eq("5h00 et 20h sont la meme journee",
                day(5, "2026-11-06T05:00"), day(5, "2026-11-06T20:00"));
        eq("un jour d'ecart vaut exactement 1",
                day(5, "2026-11-06T20:00") - thursdayEvening, 1L);

        // Le defaut qui a motive tout ce changement : en temps de jeu, lundi
        // 20h et mardi 19h tombaient dans la meme periode de 24 heures de jeu,
        // donc aucun nouveau contrat.
        ne("lundi 20h et mardi 19h sont deux journees",
                day(5, "2026-11-09T20:00"), day(5, "2026-11-10T19:00"));

        long weekOfMonday9 = week(5, DayOfWeek.MONDAY, "2026-11-09T06:00");
        eq("dimanche soir appartient encore a la semaine du lundi precedent",
                week(5, DayOfWeek.MONDAY, "2026-11-15T23:00"), weekOfMonday9);
        eq("lundi 4h59 appartient encore a la semaine precedente",
                week(5, DayOfWeek.MONDAY, "2026-11-16T04:59"), weekOfMonday9);
        ne("lundi 5h00 ouvre une nouvelle semaine",
                week(5, DayOfWeek.MONDAY, "2026-11-16T05:00"), weekOfMonday9);
        eq("une semaine d'ecart vaut 7 jours",
                week(5, DayOfWeek.MONDAY, "2026-11-16T05:00") - weekOfMonday9, 7L);

        eq("de 20h a la bascule de 5h : 9 h", until(5, "2026-11-05T20:00"), 540L);
        eq("de 4h59 a 5h : 1 min", until(5, "2026-11-06T04:59"), 1L);
        eq("de 5h00 a la bascule suivante : 24 h", until(5, "2026-11-06T05:00"), 1440L);
        inRange("le compte quotidien reste dans (0, 1440]",
                until(5, "2026-11-05T13:37"), 1L, 1440L);
        inRange("le compte hebdomadaire reste dans (0, 10080]",
                untilWeek(5, DayOfWeek.MONDAY, "2026-11-05T13:37"), 1L, 10080L);
        // Jeudi 20h a lundi 5h : trois nuits pleines plus neuf heures.
        eq("jeudi 20h vers lundi 5h : 3 j 9 h",
                untilWeek(5, DayOfWeek.MONDAY, "2026-11-05T20:00"), (3L * 24 + 9) * 60);

        eq("une heure de bascule negative se comporte comme 0",
                day(-3, "2026-11-05T20:00"), day(0, "2026-11-05T20:00"));
        eq("une heure de bascule au-dela de 23 se comporte comme 23",
                day(99, "2026-11-05T20:00"), day(23, "2026-11-05T20:00"));

        eq("un jour de la semaine inconnu retombe sur lundi",
                RealClock.parseDay("pas-un-jour").getValue(), DayOfWeek.MONDAY.getValue());
        eq("un jour de la semaine se lit sans tenir compte de la casse",
                RealClock.parseDay("sunday").getValue(), DayOfWeek.SUNDAY.getValue());

        // Le fuseau : l'heure de Paris par defaut, quelle que soit la machine.
        zone("sans option, l'heure de Paris", RealClock.resolve(null), "Europe/Paris");
        zone("une option vide, l'heure de Paris", RealClock.resolve("  "), "Europe/Paris");
        zone("un nom IANA est pris tel quel", RealClock.resolve("America/Montreal"), "America/Montreal");
        zone("les espaces autour ne comptent pas", RealClock.resolve(" UTC "), "UTC");
        zone("un nom inconnu retombe sur Paris", RealClock.resolve("Paris/Lune"), "Europe/Paris");
        zone("systeme reprend le fuseau de la machine", RealClock.resolve("Systeme"),
                ZoneId.systemDefault().getId());

        if (failures > 0) {
            System.out.println(failures + " echec(s)");
            System.exit(1);
        }
        System.out.println("horloge reelle : tout passe");
    }

    private static long day(int resetHour, String localTime) {
        return RealClock.dayKey(resetHour, LocalDateTime.parse(localTime));
    }

    private static long week(int resetHour, DayOfWeek start, String localTime) {
        return RealClock.weekKey(resetHour, start, LocalDateTime.parse(localTime));
    }

    private static long until(int resetHour, String localTime) {
        return RealClock.minutesUntilNextDay(resetHour, LocalDateTime.parse(localTime));
    }

    private static long untilWeek(int resetHour, DayOfWeek start, String localTime) {
        return RealClock.minutesUntilNextWeek(resetHour, start, LocalDateTime.parse(localTime));
    }

    private static void eq(String what, long actual, long expected) {
        report(actual == expected, what, actual + " vs " + expected);
    }

    private static void zone(String what, ZoneId actual, String expected) {
        report(actual.getId().equals(expected), what, actual.getId() + " vs " + expected);
    }

    private static void ne(String what, long actual, long other) {
        report(actual != other, what, actual + " vs " + other);
    }

    private static void inRange(String what, long value, long low, long high) {
        report(value >= low && value <= high, what, value + " dans [" + low + ", " + high + "]");
    }

    private static void report(boolean passed, String what, String detail) {
        if (!passed) {
            failures++;
        }
        System.out.println((passed ? "ok    " : "ECHEC ") + what + "  (" + detail + ")");
    }
}

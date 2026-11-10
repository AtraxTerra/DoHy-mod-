package fr.atraxterra.dohy.palamod.trixium;


import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Les saisons du concours Trixium : <b>un mois reel</b> (choix du joueur), du
 * 1er a 5 h au 1er du mois suivant a 5 h, heure du serveur.
 *
 * <p>Paladium faisait des saisons de version (la V8 a dure 206 jours) ; en solo,
 * un mois donne un classement a viser sans le faire attendre six mois. La
 * bascule est a 5 h et non a minuit, comme les contrats du jour : une soiree
 * qui deborde ne voit pas sa saison se fermer sous ses yeux.
 */
public final class TrixiumSeason {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final int SWITCH_HOUR = 5;

    /** Decalage d'horloge, pour essayer une fin de saison ({@code /trixium admin avancer}). */
    private static volatile long offsetMillis;

    private TrixiumSeason() {
    }

    public static ZonedDateTime now() {
        return ZonedDateTime.now(ZONE).plus(Duration.ofMillis(offsetMillis));
    }

    public static long nowMillis() {
        return now().toInstant().toEpochMilli();
    }

    public static void advance(Duration duration) {
        offsetMillis += duration.toMillis();
    }

    public static void resetClock() {
        offsetMillis = 0L;
    }

    /** La saison d'un instant : annee x 12 + mois (0 a 11). */
    public static int id(ZonedDateTime time) {
        ZonedDateTime shifted = time.minusHours(SWITCH_HOUR);
        return shifted.getYear() * 12 + shifted.getMonthValue() - 1;
    }

    public static int current() {
        return id(now());
    }

    public static ZonedDateTime start(int season) {
        return LocalDate.of(season / 12, season % 12 + 1, 1).atTime(SWITCH_HOUR, 0).atZone(ZONE);
    }

    public static ZonedDateTime end(int season) {
        return start(season + 1);
    }

    /** « novembre 2026 ». */
    public static String label(int season) {
        String[] months = {"janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout", "septembre",
                "octobre", "novembre", "decembre"};
        return months[season % 12] + " " + season / 12;
    }

    /** Le temps restant : « 12 j 4 h », « 3 h 05 », « 25 min ». */
    public static String remaining(int season) {
        long minutes = Math.max(0L, Duration.between(now(), end(season)).toMinutes());
        long days = minutes / 1440L;
        long hours = minutes / 60L % 24L;
        if (days > 0) {
            return days + " j " + hours + " h";
        }
        return hours > 0 ? hours + " h " + String.format("%02d", minutes % 60L) : minutes + " min";
    }
}

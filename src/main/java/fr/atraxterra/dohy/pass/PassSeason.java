package fr.atraxterra.dohy.pass;

import fr.atraxterra.dohy.util.RealClock;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Le calendrier du pass : <b>un mois reel</b>, du 1er a 5 h au 1er du mois
 * suivant a 5 h, a l'heure de {@link RealClock} — comme le concours Trixium et
 * les contrats, une soiree qui deborde minuit ne voit pas son pass se fermer
 * sous ses yeux. Les quetes du jour basculent a la meme heure.
 *
 * <p>Une saison est un numero : annee x 12 + mois (0 a 11).
 */
public final class PassSeason {

    private static final ZoneId ZONE = RealClock.zone();
    private static final int SWITCH_HOUR = 5;
    private static final String[] MONTHS = {"janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout",
            "septembre", "octobre", "novembre", "decembre"};

    /** Decalage d'horloge, pour essayer une bascule ({@code /pass admin avancer}). */
    private static volatile long offsetMillis;

    private PassSeason() {
    }

    public static ZonedDateTime now() {
        return ZonedDateTime.now(ZONE).plus(Duration.ofMillis(offsetMillis));
    }

    public static void advance(Duration duration) {
        offsetMillis += duration.toMillis();
    }

    public static void resetClock() {
        offsetMillis = 0L;
    }

    public static boolean shifted() {
        return offsetMillis != 0L;
    }

    /** La saison d'un instant. */
    public static int id(ZonedDateTime time) {
        ZonedDateTime shifted = time.minusHours(SWITCH_HOUR);
        return shifted.getYear() * 12 + shifted.getMonthValue() - 1;
    }

    public static int current() {
        return id(now());
    }

    /** Le mois d'une saison, de 1 a 12. */
    public static int month(int season) {
        return Math.floorMod(season, 12) + 1;
    }

    public static int year(int season) {
        return Math.floorDiv(season, 12);
    }

    /** Le jour des quetes : il change a 5 h. */
    public static long dayKey() {
        return now().minusHours(SWITCH_HOUR).toLocalDate().toEpochDay();
    }

    public static ZonedDateTime start(int season) {
        return LocalDate.of(year(season), month(season), 1).atTime(SWITCH_HOUR, 0).atZone(ZONE);
    }

    public static ZonedDateTime end(int season) {
        return start(season + 1);
    }

    /** « novembre ». */
    public static String monthName(int season) {
        return MONTHS[month(season) - 1];
    }

    /** « novembre 2026 ». */
    public static String label(int season) {
        return monthName(season) + " " + year(season);
    }

    /** « de novembre », « d'octobre », « d'avril », « d'aout ». */
    public static String ofMonth(int season) {
        String name = monthName(season);
        return "aeiou".indexOf(name.charAt(0)) >= 0 ? "d'" + name : "de " + name;
    }

    /** Le temps restant avant la fin du mois : « 12 j 4 h », « 3 h 05 », « 25 min ». */
    public static String remaining(int season) {
        return format(Math.max(0L, Duration.between(now(), end(season)).toMinutes()));
    }

    /** Le temps restant avant les quetes du jour suivant. */
    public static String untilTomorrow() {
        ZonedDateTime now = now();
        ZonedDateTime next = now.minusHours(SWITCH_HOUR).toLocalDate().plusDays(1).atTime(SWITCH_HOUR, 0)
                .atZone(ZONE);
        return format(Math.max(0L, Duration.between(now, next).toMinutes()));
    }

    static String format(long minutes) {
        long days = minutes / 1440L;
        long hours = minutes / 60L % 24L;
        if (days > 0) {
            return days + " j " + hours + " h";
        }
        return hours > 0 ? hours + " h " + String.format("%02d", minutes % 60L) : minutes + " min";
    }
}

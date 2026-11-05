package fr.atraxterra.dohy.util;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * L'horloge reelle, pour tout ce qui doit tourner meme monde ferme.
 *
 * <h2>Pourquoi des cles de periode et non des comptes a rebours</h2>
 *
 * <p>Un compte a rebours de vingt-quatre heures depuis le dernier tirage donne
 * des contrats « du jour » qui glissent : tires a 20 h lundi, renouveles a 20 h
 * mardi, et une connexion a 19 h mardi ne trouve rien de neuf. On stocke donc la
 * <b>cle de la periode</b> ou le tirage a eu lieu, et on retire des que la cle
 * courante differe. Le renouvellement tombe ainsi a heure fixe, et sauter trois
 * jours ne produit pas trois tirages de rattrapage — juste un seul.
 *
 * <p>Conséquence voulue : l'heure de renouvellement est anticipable. C'est ce
 * qui permet de dire « demain soir, j'aurai trois nouveaux contrats », ce qu'un
 * compteur en temps de jeu ne permettait pas.
 *
 * <h2>Pourquoi une heure de bascule a 5 h</h2>
 *
 * <p>A minuit, une soiree qui depasse l'heure change de journee en plein jeu :
 * les contrats se renouvellent sous le nez du joueur et il perd la progression
 * de ceux qu'il etait en train de finir. Une bascule au petit matin place la
 * frontiere la ou personne ne joue.
 *
 * <p>Le fuseau est celui de la machine qui fait tourner le monde, ce qui en solo
 * est la machine du joueur.
 */
public final class RealClock {

    private RealClock() {
    }

    /** Minutes depuis l'epoque. L'unite commune de tous les rattrapages. */
    public static long epochMinutes() {
        return System.currentTimeMillis() / 60_000L;
    }

    /**
     * La cle du jour courant : le numero de jour de la date locale, apres avoir
     * recule de {@code resetHour} heures.
     *
     * <p>Avec une bascule a 5 h, il est donc encore « hier » jusqu'a 4 h 59.
     */
    public static long dayKey(int resetHour) {
        return dayKey(resetHour, LocalDateTime.now(zone()));
    }

    /** Variante testable : la meme regle, a un instant local donne. */
    static long dayKey(int resetHour, LocalDateTime now) {
        return localDay(resetHour, now).toEpochDay();
    }

    /** La cle de la semaine courante : le jour de son premier {@code start}. */
    public static long weekKey(int resetHour, DayOfWeek start) {
        return weekKey(resetHour, start, LocalDateTime.now(zone()));
    }

    static long weekKey(int resetHour, DayOfWeek start, LocalDateTime now) {
        return localDay(resetHour, now)
                .with(TemporalAdjusters.previousOrSame(start))
                .toEpochDay();
    }

    /** Minutes restantes avant la prochaine bascule quotidienne. */
    public static long minutesUntilNextDay(int resetHour) {
        return minutesUntilNextDay(resetHour, LocalDateTime.now(zone()));
    }

    static long minutesUntilNextDay(int resetHour, LocalDateTime now) {
        LocalDateTime next = localDay(resetHour, now)
                .plusDays(1)
                .atStartOfDay()
                .plusHours(clamp(resetHour));
        // Une duree negative ne peut venir que d'un changement d'heure en cours
        // de calcul ; on rend zero plutot qu'un compte a rebours a l'envers.
        return Math.max(0L, Duration.between(now, next).toMinutes());
    }

    /** Minutes restantes avant la prochaine bascule hebdomadaire. */
    public static long minutesUntilNextWeek(int resetHour, DayOfWeek start) {
        return minutesUntilNextWeek(resetHour, start, LocalDateTime.now(zone()));
    }

    static long minutesUntilNextWeek(int resetHour, DayOfWeek start, LocalDateTime now) {
        LocalDateTime next = localDay(resetHour, now)
                .with(TemporalAdjusters.next(start))
                .atStartOfDay()
                .plusHours(clamp(resetHour));
        return Math.max(0L, Duration.between(now, next).toMinutes());
    }

    /**
     * Lit un jour de la semaine ecrit dans le datapack.
     *
     * <p>Un nom inconnu rend lundi plutot que d'echouer : une faute de frappe
     * dans {@code contracts.json} ne doit pas empecher le monde de demarrer.
     */
    public static DayOfWeek parseDay(String raw) {
        try {
            return DayOfWeek.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return DayOfWeek.MONDAY;
        }
    }

    private static LocalDate localDay(int resetHour, LocalDateTime now) {
        return now.minusHours(clamp(resetHour)).toLocalDate();
    }

    private static int clamp(int resetHour) {
        return Math.max(0, Math.min(23, resetHour));
    }

    private static ZoneId zone() {
        return ZoneId.systemDefault();
    }
}

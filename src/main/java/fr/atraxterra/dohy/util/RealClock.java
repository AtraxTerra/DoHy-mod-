package fr.atraxterra.dohy.util;

import java.time.DateTimeException;
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
 * <h2>Le fuseau : celui du joueur, pas celui de la machine</h2>
 *
 * <p>Toutes les heures du mod — la bascule de 5 h, le lundi du classement, les
 * events de faction, les saisons du trixium, les dates affichees — se lisent
 * dans un seul fuseau, {@link #zone()}. Par defaut, c'est l'heure de Paris, et
 * non celle de la machine : en solo les deux se confondent, mais un serveur
 * loue tourne souvent en UTC, et l'event de 21 h y tomberait a 22 h ou 23 h.
 *
 * <p>Un serveur ailleurs peut le changer au lancement, par une option de la
 * JVM : {@code -Ddohy.fuseau=America/Montreal} (un nom de fuseau IANA), ou
 * {@code -Ddohy.fuseau=systeme} pour reprendre le fuseau de la machine.
 * {@link ClockReport} ecrit au demarrage le fuseau retenu dans le journal.
 *
 * <p>Cette classe ne depend que de {@code java.time} : la CI la compile seule
 * pour son test ({@code tools/RealClockTest.java}). Rien de Minecraft ici.
 */
public final class RealClock {

    /** Le fuseau par defaut : celui du joueur. */
    static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    /** L'option de lancement telle qu'ecrite, ou {@code null}. */
    private static final String OPTION = System.getProperty("dohy.fuseau");

    /** Lu une fois : changer de fuseau en cours de partie decalerait toutes les cles de periode. */
    private static final ZoneId ZONE = resolve(OPTION);

    private RealClock() {
    }

    /** Le fuseau de toutes les horloges du mod. */
    public static ZoneId zone() {
        return ZONE;
    }

    /** L'option {@code dohy.fuseau} si elle n'a pas ete comprise, pour le journal ; sinon {@code null}. */
    public static String unknownOption() {
        return OPTION != null && !OPTION.isBlank() && parse(OPTION) == null ? OPTION.trim() : null;
    }

    /**
     * Lit l'option {@code dohy.fuseau}.
     *
     * <p>Un nom inconnu rend Paris plutot que d'echouer, comme {@link #parseDay} :
     * une faute de frappe dans la ligne de lancement ne doit pas empecher le
     * serveur de demarrer. Le journal le signale ({@link #unknownOption()}).
     */
    static ZoneId resolve(String raw) {
        ZoneId parsed = raw == null || raw.isBlank() ? null : parse(raw);
        return parsed != null ? parsed : PARIS;
    }

    private static ZoneId parse(String raw) {
        String value = raw.trim();
        if (value.equalsIgnoreCase("systeme") || value.equalsIgnoreCase("system")) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(value);
        } catch (DateTimeException unknown) {
            return null;
        }
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
}

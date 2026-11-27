package fr.atraxterra.dohy.faction.marks;

import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.util.RandomSource;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Une manche d'A vos marques : quand elle part, son objectif, et si c'est la
 * manche aux points.
 *
 * <p>Le calendrier (choix pour le solo, le wiki dit seulement « chaque jour ») :
 * une manche de 30 minutes a chaque heure et demie, de 10 h 30 a 23 h 30, heure
 * de {@link fr.atraxterra.dohy.util.RealClock}. Elle finit a l'heure pile, quand
 * la Mine se remet a neuf (10 h, 15 h, 20 h) : aucune manche n'est coupee par la
 * remise a neuf.
 *
 * <p>Les objectifs tournent : chaque jour tire un ordre des quatre, et la manche
 * {@code n} prend le {@code n}-ieme ; une manche sur trois (la troisieme, la
 * sixieme...) est la manche aux points, la « deuxieme variante » du wiki, ou
 * toutes les actions comptent et ou un compteur de points s'affiche. Tout se
 * deduit de l'heure : un redemarrage retrouve la meme manche, et la progression
 * gardee par le joueur ({@link MarksData}) la suit.
 */
public record MarksRound(long start, MarksGoal goal, boolean points, boolean forced) {

    /** La duree d'une manche. */
    public static final long SECONDS = 30L * 60L;
    /** La premiere et la derniere heure de depart. */
    static final int FIRST_HOUR = 10;
    static final int LAST_HOUR = 23;
    /** La minute de depart. */
    static final int MINUTE = 30;
    /** L'objectif de la manche aux points. */
    public static final long POINTS_TARGET = 1000L;

    /** La fin, en secondes depuis l'epoque. */
    public long end() {
        return start + SECONDS;
    }

    /** L'identite de la manche : son depart, son objectif et sa variante. */
    public long id() {
        return start / 60L * 16L + goal.ordinal() * 2L + (points ? 1L : 0L);
    }

    public long target() {
        return points ? POINTS_TARGET : goal.target();
    }

    /** Cette action fait-elle avancer la manche ? */
    public boolean counts(MarksGoal action) {
        return points || action == goal;
    }

    /** Les compteurs s'affichent-ils dans ce monde ? */
    public boolean shows(String dimension) {
        if (!points) {
            return goal.worlds().contains(dimension);
        }
        for (MarksGoal each : MarksGoal.values()) {
            if (each.worlds().contains(dimension)) {
                return true;
            }
        }
        return false;
    }

    /** « Mine 1 000 blocs » ou « 1 000 points ». */
    public String objective() {
        return points ? format(POINTS_TARGET) + " points" : goal.order() + " " + format(goal.target()) + " "
                + goal.unit();
    }

    /** Le bareme de la manche aux points : « bloc mine 1, buche 4, creature 20, prise 40 ». */
    public static String scale() {
        StringBuilder text = new StringBuilder();
        for (MarksGoal each : MarksGoal.values()) {
            text.append(text.isEmpty() ? "" : ", ").append(each.single()).append(" ").append(each.points());
        }
        return text.toString();
    }

    /** La manche prevue qui couvre cet instant, ou {@code null}. */
    public static MarksRound scheduledAt(ZonedDateTime now) {
        int hour = now.getHour();
        if (hour < FIRST_HOUR || hour > LAST_HOUR || now.getMinute() < MINUTE) {
            return null;
        }
        return of(now.truncatedTo(ChronoUnit.HOURS).withMinute(MINUTE));
    }

    /** Le prochain depart prevu, strictement apres {@code now}. */
    public static ZonedDateTime nextStart(ZonedDateTime now) {
        ZonedDateTime start = now.truncatedTo(ChronoUnit.HOURS).withMinute(MINUTE);
        if (!start.isAfter(now)) {
            start = start.plusHours(1L);
        }
        for (int guard = 0; guard < 48 && (start.getHour() < FIRST_HOUR || start.getHour() > LAST_HOUR); guard++) {
            start = start.plusHours(1L);
        }
        return start;
    }

    /** La manche prevue qui part a cette heure. */
    public static MarksRound of(ZonedDateTime start) {
        int index = Math.max(0, start.getHour() - FIRST_HOUR);
        MarksGoal[] order = order(start.toLocalDate().toEpochDay());
        return new MarksRound(start.toEpochSecond(), order[index % order.length], index % 3 == 2, false);
    }

    /** Une manche lancee a la main (commande d'operateur), qui part maintenant. */
    public static MarksRound forced(long now, MarksGoal goal, boolean points) {
        return new MarksRound(now, goal, points, true);
    }

    /** L'ordre des objectifs de ce jour. */
    static MarksGoal[] order(long day) {
        MarksGoal[] goals = MarksGoal.values().clone();
        RandomSource random = RandomSource.create(day * 31L + 7L);
        for (int i = goals.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            MarksGoal swap = goals[i];
            goals[i] = goals[j];
            goals[j] = swap;
        }
        return goals;
    }

    public ZonedDateTime startTime(ZoneId zone) {
        return Instant.ofEpochSecond(start).atZone(zone);
    }

    /** « 1 000 ». */
    static String format(long value) {
        return MoneyFormat.exact(value);
    }
}

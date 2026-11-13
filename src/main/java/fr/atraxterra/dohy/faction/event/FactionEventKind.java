package fr.atraxterra.dohy.faction.event;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Les events de faction et leur calendrier, aux heures du wiki (« Java | Event
 * faction », « Java | Boss », « Java | Totem »), a l'heure de Paris
 * ({@link fr.atraxterra.dohy.util.RealClock}).
 *
 * <table>
 *   <tr><th>Event</th><th>Quand</th><th>ELO</th></tr>
 *   <tr><td>Boss</td><td>tous les jours, 1 h et 18 h</td><td>700</td></tr>
 *   <tr><td>Egg Hunt</td><td>mardi et dimanche, 19 h, 60 min</td><td>3 000</td></tr>
 *   <tr><td>Totem</td><td>mercredi, 21 h</td><td>10 000</td></tr>
 *   <tr><td>KOTH a points</td><td>samedi, 21 h 30, 30 min</td><td>10 000</td></tr>
 * </table>
 *
 * <p>Les quatre tournent (lot 22f). L'End du wiki n'est pas repris : choix du
 * joueur pour sa partie solo.
 */
public enum FactionEventKind {

    BOSS("boss", "Boss", Items.WITHER_SKELETON_SKULL, 700L, EnumSet.allOf(DayOfWeek.class),
            List.of(LocalTime.of(1, 0), LocalTime.of(18, 0)), true,
            "Un des cinq boss, dans l'arene", "700 ELO selon les degats"),
    EGG_HUNT("egg_hunt", "Egg Hunt", Items.DRAGON_EGG, 3_000L, EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY),
            List.of(LocalTime.of(19, 0)), true,
            "Abats Volcania, garde l'oeuf", "60 min de chasse"),
    TOTEM("totem", "Totem", Items.TOTEM_OF_UNDYING, 10_000L, EnumSet.of(DayOfWeek.WEDNESDAY),
            List.of(LocalTime.of(21, 0)), true,
            "Casse le totem bloc par bloc", "500 points par bloc depose"),
    KOTH("koth", "KOTH a points", Items.BEACON, 10_000L, EnumSet.of(DayOfWeek.SATURDAY),
            List.of(LocalTime.of(21, 30)), true,
            "Tiens la zone 30 min", "1 point par seconde");

    private final String id;
    private final String label;
    private final Item icon;
    private final long elo;
    private final Set<DayOfWeek> days;
    private final List<LocalTime> times;
    private final boolean ready;
    private final String line1;
    private final String line2;

    FactionEventKind(String id, String label, Item icon, long elo, Set<DayOfWeek> days, List<LocalTime> times,
                     boolean ready, String line1, String line2) {
        this.id = id;
        this.label = label;
        this.icon = icon;
        this.elo = elo;
        this.days = days;
        this.times = times;
        this.ready = ready;
        this.line1 = line1;
        this.line2 = line2;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public Item icon() {
        return icon;
    }

    /** L'ELO mis en jeu, reparti entre les factions selon leur rang. */
    public long elo() {
        return elo;
    }

    /** Vrai si l'event tourne deja ; les autres attendent leur lot. */
    public boolean ready() {
        return ready;
    }

    public String line1() {
        return line1;
    }

    public String line2() {
        return line2;
    }

    /** Le prochain depart a partir de {@code now} (la minute en cours comprise). */
    public ZonedDateTime next(ZonedDateTime now) {
        ZonedDateTime minute = now.truncatedTo(ChronoUnit.MINUTES);
        for (int day = 0; day <= 7; day++) {
            ZonedDateTime date = minute.plusDays(day);
            if (!days.contains(date.getDayOfWeek())) {
                continue;
            }
            for (LocalTime time : times) {
                ZonedDateTime start = date.with(time);
                if (!start.isBefore(minute)) {
                    return start;
                }
            }
        }
        return minute.plusWeeks(1);
    }

    /** « Tous les jours, 1 h et 18 h », « Samedi, 21 h 30 »... */
    public String schedule() {
        StringBuilder text = new StringBuilder();
        if (days.size() == 7) {
            text.append("Tous les jours");
        } else {
            boolean first = true;
            for (DayOfWeek day : days) {
                String name = DAY_NAMES[day.ordinal()];
                text.append(first ? name : " et " + name.toLowerCase());
                first = false;
            }
        }
        text.append(", ");
        for (int i = 0; i < times.size(); i++) {
            LocalTime time = times.get(i);
            text.append(i == 0 ? "" : " et ").append(time.getHour()).append(" h")
                    .append(time.getMinute() == 0 ? "" : " " + time.getMinute());
        }
        return text.toString();
    }

    private static final String[] DAY_NAMES = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi",
            "Dimanche"};

    public static FactionEventKind byId(String id) {
        for (FactionEventKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }
}

package fr.atraxterra.dohy.faction.enterprise;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Les affaires de la nation (lot 24b), en piece jointe du joueur, gardees a la mort : ses
 * entreprises, son argent sale, ses stocks, ses relations avec les six organisations
 * criminelles, l'historique de ses revenus et le journal de tout cela.
 */
public final class Holdings {

    /** Ce que la nation et une organisation criminelle sont l'une pour l'autre. */
    public enum Status {
        NEUTRE("Neutre"), ACCORD("Accord"), ALLIANCE("Alliee"), GUERRE("Guerre de territoire");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        static Status of(String name) {
            for (Status status : values()) {
                if (status.name().equals(name)) {
                    return status;
                }
            }
            return NEUTRE;
        }
    }

    /**
     * Une relation avec une organisation criminelle.
     *
     * @param hostility son hostilite, de 0 a 100 : a 75, elle te fait la guerre
     * @param until     la fin de l'accord, de l'alliance ou de la guerre (ms)
     * @param weakened  affaiblie par une riposte jusqu'a cette heure (ms)
     */
    public record Standing(String syndicate, double hostility, String status, long until, long weakened) {
        static final Codec<Standing> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("syndicate").forGetter(Standing::syndicate),
                Codec.DOUBLE.optionalFieldOf("hostility", 30.0d).forGetter(Standing::hostility),
                Codec.STRING.optionalFieldOf("status", Status.NEUTRE.name()).forGetter(Standing::status),
                Codec.LONG.optionalFieldOf("until", 0L).forGetter(Standing::until),
                Codec.LONG.optionalFieldOf("weakened", 0L).forGetter(Standing::weakened)
        ).apply(instance, Standing::new));

        public Status state() {
            return Status.of(status);
        }

        Standing with(double nextHostility, Status nextStatus, long nextUntil) {
            return new Standing(syndicate, Math.max(0.0d, Math.min(100.0d, nextHostility)), nextStatus.name(),
                    nextUntil, weakened);
        }

        Standing weakenedUntil(long until) {
            return new Standing(syndicate, hostility, status, this.until, until);
        }

        Standing shifted(long shift) {
            return new Standing(syndicate, hostility, status, until > 0L ? until - shift : 0L,
                    weakened > 0L ? weakened - shift : 0L);
        }
    }

    /** Une journee de comptes, a l'heure de Paris. */
    public record Day(long day, long clean, long dirty, long costs, long laundered, long seized) {
        static final Codec<Day> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("day").forGetter(Day::day),
                Codec.LONG.optionalFieldOf("clean", 0L).forGetter(Day::clean),
                Codec.LONG.optionalFieldOf("dirty", 0L).forGetter(Day::dirty),
                Codec.LONG.optionalFieldOf("costs", 0L).forGetter(Day::costs),
                Codec.LONG.optionalFieldOf("laundered", 0L).forGetter(Day::laundered),
                Codec.LONG.optionalFieldOf("seized", 0L).forGetter(Day::seized)
        ).apply(instance, Day::new));

        Day plus(long moreClean, long moreDirty, long moreCosts, long moreLaundered, long moreSeized) {
            return new Day(day, clean + moreClean, dirty + moreDirty, costs + moreCosts, laundered + moreLaundered,
                    seized + moreSeized);
        }
    }

    /** Le dernier versement : ce que le rapport d'absence et le message du retour en disent. */
    public record Report(long time, long hours, long clean, long dirty, long laundered, long costs, int raids,
                         long sold, long lost) {
        public static final Report NONE = new Report(0L, 0L, 0L, 0L, 0L, 0L, 0, 0L, 0L);
        static final Codec<Report> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("time", 0L).forGetter(Report::time),
                Codec.LONG.optionalFieldOf("hours", 0L).forGetter(Report::hours),
                Codec.LONG.optionalFieldOf("clean", 0L).forGetter(Report::clean),
                Codec.LONG.optionalFieldOf("dirty", 0L).forGetter(Report::dirty),
                Codec.LONG.optionalFieldOf("laundered", 0L).forGetter(Report::laundered),
                Codec.LONG.optionalFieldOf("costs", 0L).forGetter(Report::costs),
                Codec.INT.optionalFieldOf("raids", 0).forGetter(Report::raids),
                Codec.LONG.optionalFieldOf("sold", 0L).forGetter(Report::sold),
                Codec.LONG.optionalFieldOf("lost", 0L).forGetter(Report::lost)
        ).apply(instance, Report::new));
    }

    public static final MapCodec<Holdings> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Enterprise.CODEC.listOf().optionalFieldOf("enterprises", List.of())
                    .forGetter(data -> List.copyOf(data.enterprises)),
            Codec.LONG.optionalFieldOf("dirty", 0L).forGetter(data -> data.dirty),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("stock", Map.of())
                    .forGetter(data -> Map.copyOf(data.stock)),
            Codec.LONG.optionalFieldOf("paid_until", 0L).forGetter(data -> data.paidUntil),
            Standing.CODEC.listOf().optionalFieldOf("syndicates", List.of())
                    .forGetter(data -> List.copyOf(data.standings)),
            Day.CODEC.listOf().optionalFieldOf("days", List.of()).forGetter(data -> List.copyOf(data.days)),
            Codec.LONG.listOf().optionalFieldOf("hours", List.of()).forGetter(data -> List.copyOf(data.hours)),
            Codec.LONG.listOf().optionalFieldOf("hour_costs", List.of())
                    .forGetter(data -> List.copyOf(data.hourCosts)),
            Codec.STRING.listOf().optionalFieldOf("journal", List.of()).forGetter(data -> List.copyOf(data.journal)),
            Codec.INT.optionalFieldOf("counter", 0).forGetter(data -> data.counter),
            Report.CODEC.optionalFieldOf("report", Report.NONE).forGetter(data -> data.report),
            Codec.LONG.optionalFieldOf("day_tick", 0L).forGetter(data -> data.dayTick)
    ).apply(instance, Holdings::new));

    /** Lignes de journal gardees. */
    static final int JOURNAL = 30;
    /** Heures gardees pour la courbe des revenus. */
    static final int HOURS = 48;
    /** Jours gardes pour les comptes. */
    static final int DAYS = 7;

    final List<Enterprise> enterprises;
    /** L'argent sale : ce que rapportent les affaires illegales, a blanchir avant d'aller a la banque. */
    long dirty;
    /** Les stocks, en unites : « raw:LIGNE », « drug:LIGNE », « crates ». */
    final Map<String, Long> stock;
    /** Les entreprises ont tourne jusqu'a cette heure (ms). */
    long paidUntil;
    final List<Standing> standings;
    final List<Day> days;
    /** Le revenu brut (propre et sale) de chaque heure, le plus recent a la fin. */
    final List<Long> hours;
    /** Les frais de chaque heure, alignes sur {@link #hours}. */
    final List<Long> hourCosts;
    /** « ms|texte », le plus recent a la fin. */
    final List<String> journal;
    int counter;
    Report report;
    /** La derniere journee passee pour les organisations criminelles (ms). */
    long dayTick;

    public Holdings() {
        this(List.of(), 0L, Map.of(), 0L, List.of(), List.of(), List.of(), List.of(), List.of(), 0, Report.NONE, 0L);
    }

    private Holdings(List<Enterprise> enterprises, long dirty, Map<String, Long> stock, long paidUntil,
                     List<Standing> standings, List<Day> days, List<Long> hours, List<Long> hourCosts,
                     List<String> journal, int counter, Report report, long dayTick) {
        this.enterprises = new ArrayList<>(enterprises);
        this.dirty = dirty;
        this.stock = new HashMap<>(stock);
        this.paidUntil = paidUntil;
        this.standings = new ArrayList<>(standings);
        this.days = new ArrayList<>(days);
        this.hours = new ArrayList<>(hours);
        this.hourCosts = new ArrayList<>(hourCosts);
        this.journal = new ArrayList<>(journal);
        this.counter = counter;
        this.report = report;
        this.dayTick = dayTick;
    }

    public boolean isEmpty() {
        return enterprises.isEmpty() && dirty == 0L && stock.isEmpty() && standings.isEmpty() && journal.isEmpty()
                && counter == 0 && days.isEmpty();
    }

    public List<Enterprise> enterprises() {
        return List.copyOf(enterprises);
    }

    public long dirty() {
        return dirty;
    }

    public long stock(String key) {
        return stock.getOrDefault(key, 0L);
    }

    public long paidUntil() {
        return paidUntil;
    }

    public List<Day> days() {
        return List.copyOf(days);
    }

    public List<Long> hours() {
        return List.copyOf(hours);
    }

    public List<Long> hourCosts() {
        return List.copyOf(hourCosts);
    }

    public Report report() {
        return report;
    }

    /** Le journal, du plus recent au plus ancien. */
    public List<String> journal() {
        List<String> out = new ArrayList<>(journal);
        Collections.reverse(out);
        return out;
    }

    /** La relation avec cette organisation ; neutre et tiede tant que rien ne s'est passe. */
    public Standing standing(Syndicate syndicate) {
        for (Standing standing : standings) {
            if (standing.syndicate().equals(syndicate.name())) {
                return standing;
            }
        }
        return new Standing(syndicate.name(), 30.0d, Status.NEUTRE.name(), 0L, 0L);
    }

    void put(Standing standing) {
        for (int index = 0; index < standings.size(); index++) {
            if (standings.get(index).syndicate().equals(standing.syndicate())) {
                standings.set(index, standing);
                return;
            }
        }
        standings.add(standing);
    }

    Enterprise enterprise(String id) {
        for (Enterprise enterprise : enterprises) {
            if (enterprise.id().equals(id)) {
                return enterprise;
            }
        }
        return null;
    }

    void replace(Enterprise enterprise) {
        for (int index = 0; index < enterprises.size(); index++) {
            if (enterprises.get(index).id().equals(enterprise.id())) {
                enterprises.set(index, enterprise);
                return;
            }
        }
    }

    void add(String key, long amount) {
        long next = Math.max(0L, stock.getOrDefault(key, 0L) + amount);
        if (next == 0L) {
            stock.remove(key);
        } else {
            stock.put(key, next);
        }
    }

    void log(long now, String text) {
        journal.add(now + "|" + text);
        while (journal.size() > JOURNAL) {
            journal.removeFirst();
        }
    }

    String nextId() {
        counter++;
        return "e" + counter;
    }

    void hour(long gross, long costs) {
        hours.add(Math.max(0L, gross));
        hourCosts.add(Math.max(0L, costs));
        while (hours.size() > HOURS) {
            hours.removeFirst();
        }
        while (hourCosts.size() > HOURS) {
            hourCosts.removeFirst();
        }
    }

    void book(long day, long clean, long dirtyEarned, long costs, long laundered, long seized) {
        if (!days.isEmpty() && days.getLast().day() == day) {
            days.set(days.size() - 1, days.getLast().plus(clean, dirtyEarned, costs, laundered, seized));
        } else {
            days.add(new Day(day, clean, dirtyEarned, costs, laundered, seized));
        }
        while (days.size() > DAYS) {
            days.removeFirst();
        }
    }

    /** La nation est dissoute : tout s'efface. */
    void clear() {
        enterprises.clear();
        dirty = 0L;
        stock.clear();
        paidUntil = 0L;
        standings.clear();
        days.clear();
        hours.clear();
        hourCosts.clear();
        journal.clear();
        counter = 0;
        report = Report.NONE;
        dayTick = 0L;
    }
}

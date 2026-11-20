package fr.atraxterra.dohy.faction.war;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ce que les nations rivales pensent de toi, et ou en est chaque guerre.
 * Une piece jointe du joueur, gardee a la mort.
 *
 * <p>Une relation par nation, creee a sa premiere reflexion : son humeur
 * envers toi (hostilite, crainte), son tresor et son armee, sa posture
 * (paix, menace, guerre, treve) avec ses echeances, et sa memoire — les griefs
 * qu'elle te garde, les assauts gagnes et perdus, ce qu'elle t'a pris.
 *
 * <p>Depuis le lot 22l-2, aussi : ce qu'elle doit te rendre (l'argent pille,
 * les pays et les mines repris), ce qu'elle a appris de tes defenses d'un
 * assaut a l'autre, et la coalition qu'elle forme contre toi.
 */
public final class WarData {

    /** Ou en est une nation avec toi. */
    public enum Stance {
        PAIX, MENACE, GUERRE, TREVE;

        static Stance of(int index) {
            Stance[] all = values();
            return index >= 0 && index < all.length ? all[index] : PAIX;
        }
    }

    /** Un grief : ce que tu lui as fait, son poids au jour ou c'est arrive. */
    public record Grievance(String kind, double weight, long time) {
        static final Codec<Grievance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("kind").forGetter(Grievance::kind),
                Codec.DOUBLE.fieldOf("weight").forGetter(Grievance::weight),
                Codec.LONG.fieldOf("time").forGetter(Grievance::time)
        ).apply(instance, Grievance::new));
    }

    /**
     * Ce que la nation retient de vos guerres.
     *
     * @param wins   ses assauts reussis (le tresorier est reparti avec l'argent)
     * @param losses ses assauts brises
     * @param looted ce qu'elle t'a pris en tout
     * @param paid   les tributs et cadeaux que tu lui as verses
     * @param killed ses soldats tombes chez toi
     * @param strikes les missiles qu'elle a tires sur ta base (chaque tir monte d'un cran)
     */
    public record Memory(List<Grievance> grievances, int wins, int losses, long looted, long paid, int killed,
                         int strikes) {
        static final Memory EMPTY = new Memory(List.of(), 0, 0, 0L, 0L, 0, 0);
        static final Codec<Memory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Grievance.CODEC.listOf().optionalFieldOf("grievances", List.of()).forGetter(Memory::grievances),
                Codec.INT.optionalFieldOf("wins", 0).forGetter(Memory::wins),
                Codec.INT.optionalFieldOf("losses", 0).forGetter(Memory::losses),
                Codec.LONG.optionalFieldOf("looted", 0L).forGetter(Memory::looted),
                Codec.LONG.optionalFieldOf("paid", 0L).forGetter(Memory::paid),
                Codec.INT.optionalFieldOf("killed", 0).forGetter(Memory::killed),
                Codec.INT.optionalFieldOf("strikes", 0).forGetter(Memory::strikes)
        ).apply(instance, Memory::new));

        Memory withGrievance(Grievance grievance) {
            List<Grievance> next = new ArrayList<>(grievances);
            next.add(grievance);
            while (next.size() > 12) {
                next.removeFirst();
            }
            return new Memory(List.copyOf(next), wins, losses, looted, paid, killed, strikes);
        }

        Memory withGrievances(List<Grievance> next) {
            return new Memory(List.copyOf(next), wins, losses, looted, paid, killed, strikes);
        }

        Memory withWin(long amount) {
            return new Memory(grievances, wins + 1, losses, looted + amount, paid, killed, strikes);
        }

        Memory withLoss() {
            return new Memory(grievances, wins, losses + 1, looted, paid, killed, strikes);
        }

        Memory withPaid(long amount) {
            return new Memory(grievances, wins, losses, looted, paid + amount, killed, strikes);
        }

        Memory withKilled(int count) {
            return new Memory(grievances, wins, losses, looted, paid, killed + count, strikes);
        }

        Memory withStrike() {
            return new Memory(grievances, wins, losses, looted, paid, killed, strikes + 1);
        }
    }

    /**
     * Ce qu'une nation a appris de tes defenses, d'un assaut a l'autre (lot 22l-2).
     *
     * @param assaults  ses assauts contre toi depuis qu'elle apprend : chacun lui devoile un dixieme de plus
     *                  de tes pieges caches, quatre dixiemes au plus
     * @param dimension la ou ses morts ont ete relevees, avec {@code vault} : ton coffre deplace a plus de
     *                  {@value #MOVED} blocs, la carte de ses morts ne vaut plus rien
     * @param deaths    ou ses soldats sont tombes, les {@value #DEATHS} derniers : son prochain plan evite ces
     *                  cases
     * @param causes    de quoi ils sont morts — {@code lame}, {@code fleches}, {@code tourelles},
     *                  {@code pieges}, {@code terrain}, {@code gardiens} —, chaque assaut comptant moitie
     *                  moins au suivant
     */
    public record Lessons(int assaults, String dimension, BlockPos vault, List<BlockPos> deaths,
                          Map<String, Double> causes) {
        static final int DEATHS = 40;
        static final int MOVED = 48;
        static final Lessons EMPTY = new Lessons(0, "", BlockPos.ZERO, List.of(), Map.of());
        static final Codec<Lessons> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("assaults", 0).forGetter(Lessons::assaults),
                Codec.STRING.optionalFieldOf("dimension", "").forGetter(Lessons::dimension),
                BlockPos.CODEC.optionalFieldOf("vault", BlockPos.ZERO).forGetter(Lessons::vault),
                BlockPos.CODEC.listOf().optionalFieldOf("deaths", List.of()).forGetter(Lessons::deaths),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("causes", Map.of())
                        .forGetter(Lessons::causes)
        ).apply(instance, Lessons::new));

        /** Ce que ses espions voient de plus de tes pieges caches, en plus de sa ruse. */
        public double insight() {
            return Math.min(0.4d, 0.1d * assaults);
        }

        /** Ses morts relevees valent-elles encore pour ce coffre ? */
        public boolean remembers(String where, BlockPos at) {
            if (deaths.isEmpty() || !dimension.equals(where)) {
                return false;
            }
            long dx = vault.getX() - at.getX();
            long dz = vault.getZ() - at.getZ();
            return dx * dx + dz * dz <= (long) MOVED * MOVED;
        }

        /**
         * La lecon qui se degage : la cause de la plupart de ses morts (au moins
         * trois, et plus du tiers), ou {@code ""} si rien ne se degage.
         */
        public String lesson() {
            double total = 0.0d;
            String best = "";
            double most = 0.0d;
            for (Map.Entry<String, Double> entry : causes.entrySet()) {
                total += entry.getValue();
                if (entry.getValue() > most) {
                    most = entry.getValue();
                    best = entry.getKey();
                }
            }
            return most >= 3.0d && most >= total * 0.35d ? best : "";
        }

        /** Ses morts de quoi que ce soit, comptees comme les causes (les vieux assauts pesent moins). */
        public double weight() {
            double total = 0.0d;
            for (double value : causes.values()) {
                total += value;
            }
            return total;
        }

        /** Un assaut de plus : ses morts s'ajoutent a la carte (ou la remplacent), ses causes aux anciennes. */
        Lessons after(String where, BlockPos at, List<BlockPos> fallen, Map<String, Integer> killed, boolean counts) {
            List<BlockPos> next = new ArrayList<>(remembers(where, at) ? deaths : List.of());
            next.addAll(fallen);
            while (next.size() > DEATHS) {
                next.removeFirst();
            }
            Map<String, Double> merged = new LinkedHashMap<>();
            for (Map.Entry<String, Double> entry : causes.entrySet()) {
                if (entry.getValue() * 0.5d >= 0.25d) {
                    merged.put(entry.getKey(), entry.getValue() * 0.5d);
                }
            }
            for (Map.Entry<String, Integer> entry : killed.entrySet()) {
                merged.merge(entry.getKey(), (double) entry.getValue(), Double::sum);
            }
            return new Lessons(assaults + (counts ? 1 : 0), where, at.immutable(), List.copyOf(next),
                    Map.copyOf(merged));
        }
    }

    /**
     * Ce qui reste entre une nation et toi au-dela de l'humeur (lot 22l-2), tel
     * qu'il s'enregistre : voir les champs de {@link Relation} du meme nom.
     */
    record Front(long owed, List<Integer> countries, List<String> mines, boolean declared, long defeated, int ally,
                 long pact, Lessons lessons) {
        static final Front EMPTY = new Front(0L, List.of(), List.of(), false, 0L, -1, 0L, Lessons.EMPTY);
        static final Codec<Front> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("owed", 0L).forGetter(Front::owed),
                Codec.INT.listOf().optionalFieldOf("countries", List.of()).forGetter(Front::countries),
                Codec.STRING.listOf().optionalFieldOf("mines", List.of()).forGetter(Front::mines),
                Codec.BOOL.optionalFieldOf("declared", false).forGetter(Front::declared),
                Codec.LONG.optionalFieldOf("defeated", 0L).forGetter(Front::defeated),
                Codec.INT.optionalFieldOf("ally", -1).forGetter(Front::ally),
                Codec.LONG.optionalFieldOf("pact", 0L).forGetter(Front::pact),
                Lessons.CODEC.optionalFieldOf("lessons", Lessons.EMPTY).forGetter(Front::lessons)
        ).apply(instance, Front::new));
    }

    /** Une nation et toi. Modifiee sur place par {@link WarStrategy} et le siege. */
    public static final class Relation {
        final int nation;
        double hostility;
        double fear;
        long treasury;
        int army;
        Stance stance;
        /** Depuis quand elle tient sa posture (ms). */
        long since;
        /** L'ultimatum : sa date limite ; la guerre : la fin de la fenetre ou tu fixes l'assaut (ms). */
        long deadline;
        /** Le tribut exige par l'ultimatum. */
        long tribute;
        /** L'heure de l'assaut, 0 tant qu'elle n'est pas fixee (ms). */
        long slot;
        /** La fin de la treve (ms). */
        long truce;
        /** Un contre-offre a deja ete faite a cet ultimatum. */
        boolean negotiated;
        /** Sa derniere reflexion (ms). */
        long thought;
        /** Ce qu'elle sait de tes defenses : 0 une plaine ouverte, 1 une forteresse. */
        double defense;
        Memory memory;
        /** L'argent qu'elle t'a pille et pas encore rendu (lot 22l-2). */
        long owed;
        /** Les pays et les mines qu'elle t'a repris : ils te reviennent si sa place forte tombe. */
        final List<Integer> retaken = new ArrayList<>();
        final List<String> retakenMines = new ArrayList<>();
        /** Cette guerre, c'est toi qui l'as declaree : ta contre-offensive. */
        boolean declared;
        /** La derniere chute de sa place forte (ms) : vaincue, elle se releve une semaine. */
        long defeated;
        /** Son allie dans la coalition contre toi, -1 sans ; et la fin du pacte (ms). */
        int ally = -1;
        long pact;
        /** Ce qu'elle a appris de tes defenses. */
        Lessons lessons = Lessons.EMPTY;

        static final Codec<Relation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("nation").forGetter(r -> r.nation),
                Codec.DOUBLE.optionalFieldOf("hostility", 30.0d).forGetter(r -> r.hostility),
                Codec.DOUBLE.optionalFieldOf("fear", 20.0d).forGetter(r -> r.fear),
                Codec.LONG.optionalFieldOf("treasury", 0L).forGetter(r -> r.treasury),
                Codec.INT.optionalFieldOf("army", 0).forGetter(r -> r.army),
                Codec.INT.optionalFieldOf("stance", 0).forGetter(r -> r.stance.ordinal()),
                Codec.LONG.optionalFieldOf("since", 0L).forGetter(r -> r.since),
                Codec.LONG.optionalFieldOf("deadline", 0L).forGetter(r -> r.deadline),
                Codec.LONG.optionalFieldOf("tribute", 0L).forGetter(r -> r.tribute),
                Codec.LONG.optionalFieldOf("slot", 0L).forGetter(r -> r.slot),
                Codec.LONG.optionalFieldOf("truce", 0L).forGetter(r -> r.truce),
                Codec.BOOL.optionalFieldOf("negotiated", false).forGetter(r -> r.negotiated),
                Codec.LONG.optionalFieldOf("thought", 0L).forGetter(r -> r.thought),
                Codec.DOUBLE.optionalFieldOf("defense", 0.3d).forGetter(r -> r.defense),
                Memory.CODEC.optionalFieldOf("memory", Memory.EMPTY).forGetter(r -> r.memory),
                Front.CODEC.optionalFieldOf("front").forGetter(r -> Optional.of(r.front()))
        ).apply(instance, Relation::new));

        Relation(int nation, double hostility, double fear, long treasury, int army, int stance, long since,
                 long deadline, long tribute, long slot, long truce, boolean negotiated, long thought, double defense,
                 Memory memory, Optional<Front> front) {
            this.nation = nation;
            this.hostility = hostility;
            this.fear = fear;
            this.treasury = treasury;
            this.army = army;
            this.stance = Stance.of(stance);
            this.since = since;
            this.deadline = deadline;
            this.tribute = tribute;
            this.slot = slot;
            this.truce = truce;
            this.negotiated = negotiated;
            this.thought = thought;
            this.defense = defense;
            this.memory = memory;
            if (front.isPresent()) {
                Front kept = front.get();
                this.owed = kept.owed();
                this.retaken.addAll(kept.countries());
                this.retakenMines.addAll(kept.mines());
                this.declared = kept.declared();
                this.defeated = kept.defeated();
                this.ally = kept.ally();
                this.pact = kept.pact();
                this.lessons = kept.lessons();
            } else {
                // Une relation d'avant le lot 22l-2 : ce qu'elle a pille jusqu'ici, elle le doit.
                this.owed = memory.looted();
            }
        }

        /** Une nation qui ne te connait pas encore : son humeur de depart vient de son caractere. */
        static Relation fresh(int nation, long now) {
            NationTraits traits = NationTraits.of(nation);
            double hostility = 15.0d + 25.0d * traits.aggression() + 10.0d * traits.greed();
            // Les premieres reflexions s'etalent : toutes les nations ne pensent pas a toi la meme minute.
            long first = now - WarStrategy.THINK_MS + (long) (WarStrategy.THINK_MS * (0.15d + 0.07d * nation));
            return new Relation(nation, hostility, 20.0d, traits.treasury(), traits.army(), 0, now, 0L, 0L, 0L, 0L,
                    false, first, 0.3d, Memory.EMPTY, Optional.of(Front.EMPTY));
        }

        Front front() {
            return new Front(owed, List.copyOf(retaken), List.copyOf(retakenMines), declared, defeated, ally, pact,
                    lessons);
        }

        public int nation() {
            return nation;
        }

        public NationTraits traits() {
            return NationTraits.of(nation);
        }

        public double hostility() {
            return hostility;
        }

        public double fear() {
            return fear;
        }

        public long treasury() {
            return treasury;
        }

        public int army() {
            return army;
        }

        public Stance stance() {
            return stance;
        }

        public long since() {
            return since;
        }

        public long deadline() {
            return deadline;
        }

        public long tribute() {
            return tribute;
        }

        public long slot() {
            return slot;
        }

        public long truce() {
            return truce;
        }

        public boolean negotiated() {
            return negotiated;
        }

        public double defense() {
            return defense;
        }

        public Memory memory() {
            return memory;
        }

        public long owed() {
            return owed;
        }

        public List<Integer> retaken() {
            return List.copyOf(retaken);
        }

        public List<String> retakenMines() {
            return List.copyOf(retakenMines);
        }

        /** A-t-elle quelque chose a toi : de l'argent pille, un pays ou une mine repris ? */
        public boolean holds() {
            return owed > 0L || !retaken.isEmpty() || !retakenMines.isEmpty();
        }

        public boolean declared() {
            return declared;
        }

        public long defeated() {
            return defeated;
        }

        /** Sa place forte est tombee il y a moins d'une semaine : elle se releve. */
        public boolean beaten(long now) {
            return defeated > 0L && now - defeated < WarStrategy.BEATEN_MS;
        }

        public int ally() {
            return ally;
        }

        public long pact() {
            return pact;
        }

        public Lessons lessons() {
            return lessons;
        }

        /** Le poids de ses griefs aujourd'hui : chacun s'efface avec le temps, moins vite chez les rancuniers. */
        public double grievance(long now) {
            double days = 1.0d + 3.0d * traits().grudge();
            double total = 0.0d;
            for (Grievance grievance : memory.grievances()) {
                double age = Math.max(0L, now - grievance.time()) / 86_400_000.0d;
                total += grievance.weight() * Math.exp(-age / days);
            }
            return total;
        }

        void stance(Stance next, long now) {
            stance = next;
            since = now;
            negotiated = false;
            if (next != Stance.MENACE) {
                tribute = 0L;
            }
            if (next != Stance.GUERRE) {
                slot = 0L;
                declared = false;
            }
            if (next == Stance.PAIX) {
                deadline = 0L;
            }
        }

        void grieve(String kind, double weight, long now) {
            memory = memory.withGrievance(new Grievance(kind, weight, now));
        }
    }

    /** Le coffre que les nations viennent piller : un point d'un de tes claims. */
    public record Vault(String dimension, BlockPos pos) {
        static final Codec<Vault> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("dimension").forGetter(Vault::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Vault::pos)
        ).apply(instance, Vault::new));
    }

    /** Une ligne de l'histoire de tes guerres. */
    public record Event(long time, int nation, String text) {
        static final Codec<Event> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("time").forGetter(Event::time),
                Codec.INT.fieldOf("nation").forGetter(Event::nation),
                Codec.STRING.fieldOf("text").forGetter(Event::text)
        ).apply(instance, Event::new));
    }

    public static final MapCodec<WarData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Relation.CODEC.listOf().optionalFieldOf("relations", List.of()).forGetter(data -> List.copyOf(data.relations)),
            Vault.CODEC.optionalFieldOf("vault").forGetter(data -> data.vault),
            Event.CODEC.listOf().optionalFieldOf("history", List.of()).forGetter(data -> List.copyOf(data.history)),
            Codec.LONG.optionalFieldOf("seen", 0L).forGetter(data -> data.seen),
            Codec.LONG.optionalFieldOf("refund", 0L).forGetter(data -> data.refund),
            Codec.INT.optionalFieldOf("assaults", 0).forGetter(data -> data.assaults),
            Codec.LONG.listOf().optionalFieldOf("declarations", List.of())
                    .forGetter(data -> List.copyOf(data.declarations))
    ).apply(instance, WarData::new));

    private static final int HISTORY = 24;
    /** Les guerres que tu as declarees comptent deux semaines dans ta puissance. */
    private static final long AGGRESSION_MS = 14L * 86_400_000L;

    final List<Relation> relations;
    Optional<Vault> vault;
    final List<Event> history;
    /** La derniere minute ou le joueur etait en ligne, pour le rattrapage de l'economie des nations. */
    long seen;
    /** De l'argent a rendre a la banque a la prochaine connexion (un siege coupe par un arret du serveur). */
    long refund;
    int assaults;
    /** Quand tu as declare tes guerres (ta contre-offensive), les huit dernieres. */
    final List<Long> declarations;

    WarData(List<Relation> relations, Optional<Vault> vault, List<Event> history, long seen, long refund,
            int assaults, List<Long> declarations) {
        this.relations = new ArrayList<>(relations);
        this.vault = vault;
        this.history = new ArrayList<>(history);
        this.seen = seen;
        this.refund = refund;
        this.assaults = assaults;
        this.declarations = new ArrayList<>(declarations);
    }

    public static WarData empty() {
        return new WarData(List.of(), Optional.empty(), List.of(), 0L, 0L, 0, List.of());
    }

    /** Les guerres que tu as declarees ces deux dernieres semaines. */
    public int aggressions(long now) {
        int count = 0;
        for (long time : declarations) {
            if (now - time < AGGRESSION_MS) {
                count++;
            }
        }
        return count;
    }

    void declared(long now) {
        declarations.add(now);
        while (declarations.size() > 8) {
            declarations.removeFirst();
        }
    }

    public boolean exists() {
        return !relations.isEmpty() || vault.isPresent() || !history.isEmpty() || refund != 0L;
    }

    public List<Relation> relations() {
        return List.copyOf(relations);
    }

    public Optional<Vault> vault() {
        return vault;
    }

    public List<Event> history() {
        return List.copyOf(history);
    }

    public int assaults() {
        return assaults;
    }

    /** La relation avec cette nation, creee si besoin. */
    Relation relation(int nation, long now) {
        for (Relation relation : relations) {
            if (relation.nation == nation) {
                return relation;
            }
        }
        Relation fresh = Relation.fresh(nation, now);
        relations.add(fresh);
        return fresh;
    }

    /** La relation si elle existe deja. */
    public Optional<Relation> find(int nation) {
        for (Relation relation : relations) {
            if (relation.nation == nation) {
                return Optional.of(relation);
            }
        }
        return Optional.empty();
    }

    /** La nation en guerre (ou en menace) contre toi, s'il y en a une. */
    public Optional<Relation> conflict() {
        Relation best = null;
        for (Relation relation : relations) {
            if (relation.stance == Stance.GUERRE) {
                return Optional.of(relation);
            }
            if (relation.stance == Stance.MENACE && best == null) {
                best = relation;
            }
        }
        return Optional.ofNullable(best);
    }

    void log(long now, int nation, String text) {
        history.add(new Event(now, nation, text));
        while (history.size() > HISTORY) {
            history.removeFirst();
        }
    }
}

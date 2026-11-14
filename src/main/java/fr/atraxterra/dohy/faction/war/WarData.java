package fr.atraxterra.dohy.faction.war;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ce que les nations rivales pensent de toi, et ou en est chaque guerre.
 * Une piece jointe du joueur, gardee a la mort.
 *
 * <p>Une relation par nation, creee a sa premiere reflexion : son humeur
 * envers toi (hostilite, crainte), son tresor et son armee, sa posture
 * (paix, menace, guerre, treve) avec ses echeances, et sa memoire — les griefs
 * qu'elle te garde, les assauts gagnes et perdus, ce qu'elle t'a pris.
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
     */
    public record Memory(List<Grievance> grievances, int wins, int losses, long looted, long paid, int killed) {
        static final Memory EMPTY = new Memory(List.of(), 0, 0, 0L, 0L, 0);
        static final Codec<Memory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Grievance.CODEC.listOf().optionalFieldOf("grievances", List.of()).forGetter(Memory::grievances),
                Codec.INT.optionalFieldOf("wins", 0).forGetter(Memory::wins),
                Codec.INT.optionalFieldOf("losses", 0).forGetter(Memory::losses),
                Codec.LONG.optionalFieldOf("looted", 0L).forGetter(Memory::looted),
                Codec.LONG.optionalFieldOf("paid", 0L).forGetter(Memory::paid),
                Codec.INT.optionalFieldOf("killed", 0).forGetter(Memory::killed)
        ).apply(instance, Memory::new));

        Memory withGrievance(Grievance grievance) {
            List<Grievance> next = new ArrayList<>(grievances);
            next.add(grievance);
            while (next.size() > 12) {
                next.removeFirst();
            }
            return new Memory(List.copyOf(next), wins, losses, looted, paid, killed);
        }

        Memory withGrievances(List<Grievance> next) {
            return new Memory(List.copyOf(next), wins, losses, looted, paid, killed);
        }

        Memory withWin(long amount) {
            return new Memory(grievances, wins + 1, losses, looted + amount, paid, killed);
        }

        Memory withLoss() {
            return new Memory(grievances, wins, losses + 1, looted, paid, killed);
        }

        Memory withPaid(long amount) {
            return new Memory(grievances, wins, losses, looted, paid + amount, killed);
        }

        Memory withKilled(int count) {
            return new Memory(grievances, wins, losses, looted, paid, killed + count);
        }
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
                Memory.CODEC.optionalFieldOf("memory", Memory.EMPTY).forGetter(r -> r.memory)
        ).apply(instance, Relation::new));

        Relation(int nation, double hostility, double fear, long treasury, int army, int stance, long since,
                 long deadline, long tribute, long slot, long truce, boolean negotiated, long thought, double defense,
                 Memory memory) {
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
        }

        /** Une nation qui ne te connait pas encore : son humeur de depart vient de son caractere. */
        static Relation fresh(int nation, long now) {
            NationTraits traits = NationTraits.of(nation);
            double hostility = 15.0d + 25.0d * traits.aggression() + 10.0d * traits.greed();
            // Les premieres reflexions s'etalent : toutes les nations ne pensent pas a toi la meme minute.
            long first = now - WarStrategy.THINK_MS + (long) (WarStrategy.THINK_MS * (0.15d + 0.07d * nation));
            return new Relation(nation, hostility, 20.0d, traits.treasury(), traits.army(), 0, now, 0L, 0L, 0L, 0L,
                    false, first, 0.3d, Memory.EMPTY);
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
            Codec.INT.optionalFieldOf("assaults", 0).forGetter(data -> data.assaults)
    ).apply(instance, WarData::new));

    private static final int HISTORY = 24;

    final List<Relation> relations;
    Optional<Vault> vault;
    final List<Event> history;
    /** La derniere minute ou le joueur etait en ligne, pour le rattrapage de l'economie des nations. */
    long seen;
    /** De l'argent a rendre a la banque a la prochaine connexion (un siege coupe par un arret du serveur). */
    long refund;
    int assaults;

    WarData(List<Relation> relations, Optional<Vault> vault, List<Event> history, long seen, long refund,
            int assaults) {
        this.relations = new ArrayList<>(relations);
        this.vault = vault;
        this.history = new ArrayList<>(history);
        this.seen = seen;
        this.refund = refund;
        this.assaults = assaults;
    }

    public static WarData empty() {
        return new WarData(List.of(), Optional.empty(), List.of(), 0L, 0L, 0);
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

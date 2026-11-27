package fr.atraxterra.dohy.faction.marks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Ce qu'un joueur a fait a A vos marques : la manche en cours et son compteur,
 * l'ELO deja gagne aujourd'hui (70 au plus par nation et par jour ; en solo, la
 * nation est celle du joueur) et le nombre de manches reussies. Garde a la mort.
 */
public final class MarksData {

    public static final MapCodec<MarksData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.LONG.optionalFieldOf("round", 0L).forGetter(data -> data.round),
            Codec.LONG.optionalFieldOf("count", 0L).forGetter(data -> data.count),
            Codec.BOOL.optionalFieldOf("done", false).forGetter(data -> data.done),
            Codec.LONG.optionalFieldOf("day", 0L).forGetter(data -> data.day),
            Codec.LONG.optionalFieldOf("elo_today", 0L).forGetter(data -> data.eloToday),
            Codec.INT.optionalFieldOf("wins", 0).forGetter(data -> data.wins)
    ).apply(instance, MarksData::new));

    /** L'identite de la manche que compte {@link #count} ({@link MarksRound#id()}). */
    long round;
    long count;
    boolean done;
    /** Le jour de {@link #eloToday}. */
    long day;
    long eloToday;
    int wins;

    public MarksData() {
        this(0L, 0L, false, 0L, 0L, 0);
    }

    private MarksData(long round, long count, boolean done, long day, long eloToday, int wins) {
        this.round = round;
        this.count = count;
        this.done = done;
        this.day = day;
        this.eloToday = eloToday;
        this.wins = wins;
    }

    boolean isEmpty() {
        return round == 0L && wins == 0 && eloToday == 0L;
    }

    /** Passe a cette manche : une nouvelle manche repart de zero. */
    void enter(MarksRound current) {
        if (round != current.id()) {
            round = current.id();
            count = 0L;
            done = false;
        }
    }

    /** Le compteur de cette manche (zero si le joueur n'y a encore rien fait). */
    public long count(MarksRound current) {
        return current != null && round == current.id() ? count : 0L;
    }

    /** L'objectif de cette manche est-il atteint ? */
    public boolean done(MarksRound current) {
        return current != null && round == current.id() && done;
    }

    /** L'ELO gagne ce jour-la. */
    public long eloOn(long today) {
        return day == today ? eloToday : 0L;
    }

    public int wins() {
        return wins;
    }
}

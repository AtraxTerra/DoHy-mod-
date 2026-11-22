package fr.atraxterra.dohy.faction.org;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * L'organisation interne de la nation (lot 24a), en piece jointe du joueur, gardee a la mort :
 * son regime, ses cadres PNJ et les candidats du jour, ses traites avec les douze nations, ses
 * rivales declarees, la paie de ses cadres et le journal de tout cela.
 */
public final class Organisation {

    public static final MapCodec<Organisation> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("regime", Regime.INDEPENDANT.name()).forGetter(data -> data.regime.name()),
            Codec.LONG.optionalFieldOf("regime_since", 0L).forGetter(data -> data.regimeSince),
            Cadre.CODEC.listOf().optionalFieldOf("cadres", List.of()).forGetter(data -> List.copyOf(data.cadres)),
            Cadre.CODEC.listOf().optionalFieldOf("candidates", List.of())
                    .forGetter(data -> List.copyOf(data.candidates)),
            Codec.LONG.optionalFieldOf("candidates_at", 0L).forGetter(data -> data.candidatesAt),
            Treaty.CODEC.listOf().optionalFieldOf("treaties", List.of()).forGetter(data -> List.copyOf(data.treaties)),
            Codec.INT.listOf().optionalFieldOf("rivals", List.of()).forGetter(data -> List.copyOf(data.rivals)),
            Codec.LONG.optionalFieldOf("paid_until", 0L).forGetter(data -> data.paidUntil),
            Codec.LONG.optionalFieldOf("debt", 0L).forGetter(data -> data.debt),
            Codec.STRING.listOf().optionalFieldOf("journal", List.of()).forGetter(data -> List.copyOf(data.journal)),
            Codec.INT.optionalFieldOf("counter", 0).forGetter(data -> data.counter)
    ).apply(instance, Organisation::new));

    /** Lignes de journal gardees. */
    static final int JOURNAL = 24;

    Regime regime;
    long regimeSince;
    final List<Cadre> cadres;
    final List<Cadre> candidates;
    long candidatesAt;
    final List<Treaty> treaties;
    final List<Integer> rivals;
    /** Les salaires sont regles jusqu'a cette heure (ms). */
    long paidUntil;
    /** Les salaires que la banque n'a pas pu payer. */
    long debt;
    /** « ms|texte », le plus recent a la fin. */
    final List<String> journal;
    int counter;

    public Organisation() {
        this(Regime.INDEPENDANT.name(), 0L, List.of(), List.of(), 0L, List.of(), List.of(), 0L, 0L, List.of(), 0);
    }

    private Organisation(String regime, long regimeSince, List<Cadre> cadres, List<Cadre> candidates,
                         long candidatesAt, List<Treaty> treaties, List<Integer> rivals, long paidUntil, long debt,
                         List<String> journal, int counter) {
        this.regime = Regime.of(regime);
        this.regimeSince = regimeSince;
        this.cadres = new ArrayList<>(cadres);
        this.candidates = new ArrayList<>(candidates);
        this.candidatesAt = candidatesAt;
        this.treaties = new ArrayList<>(treaties);
        this.rivals = new ArrayList<>(rivals);
        this.paidUntil = paidUntil;
        this.debt = debt;
        this.journal = new ArrayList<>(journal);
        this.counter = counter;
    }

    public boolean isEmpty() {
        return regime == Regime.INDEPENDANT && regimeSince == 0L && cadres.isEmpty() && candidates.isEmpty()
                && treaties.isEmpty() && rivals.isEmpty() && journal.isEmpty() && debt == 0L && counter == 0;
    }

    public Regime regime() {
        return regime;
    }

    public long regimeSince() {
        return regimeSince;
    }

    public List<Cadre> cadres() {
        return List.copyOf(cadres);
    }

    public List<Cadre> candidates() {
        return List.copyOf(candidates);
    }

    public List<Treaty> treaties() {
        return List.copyOf(treaties);
    }

    public List<Integer> rivals() {
        return List.copyOf(rivals);
    }

    public long paidUntil() {
        return paidUntil;
    }

    public long debt() {
        return debt;
    }

    /** Le journal, du plus recent au plus ancien. */
    public List<String> journal() {
        List<String> out = new ArrayList<>(journal);
        java.util.Collections.reverse(out);
        return out;
    }

    void log(long now, String text) {
        journal.add(now + "|" + text);
        while (journal.size() > JOURNAL) {
            journal.removeFirst();
        }
    }

    String nextId() {
        counter++;
        return "c" + counter;
    }

    Cadre cadre(String id) {
        for (Cadre cadre : cadres) {
            if (cadre.id().equals(id)) {
                return cadre;
            }
        }
        return null;
    }

    void replace(Cadre cadre) {
        for (int index = 0; index < cadres.size(); index++) {
            if (cadres.get(index).id().equals(cadre.id())) {
                cadres.set(index, cadre);
                return;
            }
        }
    }

    /** Tout repart de zero : la nation est dissoute. */
    void clear() {
        regime = Regime.INDEPENDANT;
        regimeSince = 0L;
        cadres.clear();
        candidates.clear();
        candidatesAt = 0L;
        treaties.clear();
        rivals.clear();
        paidUntil = 0L;
        debt = 0L;
        journal.clear();
    }
}

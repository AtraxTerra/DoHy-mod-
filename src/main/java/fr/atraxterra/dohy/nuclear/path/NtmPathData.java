package fr.atraxterra.dohy.nuclear.path;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Le parcours NTM d'un joueur : les etapes validees, dans l'ordre, ce qu'elles
 * lui ont rapporte, et s'il a deja recu le mot d'accueil.
 *
 * <p>Les etapes sont gardees par identifiant, pas par numero : une etape
 * ajoutee au fichier ne decale rien de ce qui est fait.
 */
public final class NtmPathData {

    public static final MapCodec<NtmPathData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("done", List.of()).forGetter(data -> List.copyOf(data.done)),
            Codec.LONG.optionalFieldOf("earned", 0L).forGetter(data -> data.earned),
            Codec.BOOL.optionalFieldOf("greeted", false).forGetter(data -> data.greeted)
    ).apply(instance, NtmPathData::new));

    private final Set<String> done = new LinkedHashSet<>();
    private long earned;
    private boolean greeted;

    public NtmPathData() {
    }

    private NtmPathData(List<String> done, long earned, boolean greeted) {
        this.done.addAll(done);
        this.earned = earned;
        this.greeted = greeted;
    }

    public boolean isEmpty() {
        return done.isEmpty() && earned == 0L && !greeted;
    }

    public boolean done(String step) {
        return done.contains(step);
    }

    public List<String> doneSteps() {
        return new ArrayList<>(done);
    }

    public long earned() {
        return earned;
    }

    public boolean greeted() {
        return greeted;
    }

    void greet() {
        greeted = true;
    }

    /** Valide une etape ; faux si elle l'etait deja (rien n'est verse deux fois). */
    boolean complete(String step, long money) {
        if (!done.add(step)) {
            return false;
        }
        earned += Math.max(0L, money);
        return true;
    }

    /** Remet le parcours a zero (commande d'administration). */
    void reset() {
        done.clear();
        earned = 0L;
    }
}

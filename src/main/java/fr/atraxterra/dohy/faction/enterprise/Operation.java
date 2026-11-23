package fr.atraxterra.dohy.faction.enterprise;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Une operation de police d'une nation contre les affaires de ta nation dans ses pays (lot 24d).
 * Annoncee, elle frappe a son heure toutes les affaires visees qui sont encore ouvertes, plus
 * durement qu'une descente ordinaire ; d'ici la, on peut l'acheter ou se mettre au vert.
 *
 * @param id        son identifiant (« o3 »)
 * @param nation    la nation qui la mene
 * @param countries ses pays ou ta nation a des affaires que sa loi reprouve
 * @param announced son annonce (ms)
 * @param runs      l'heure ou elle frappe (ms)
 * @param state     son etat ({@link State})
 * @param strength  sa force : 1, 1,25 apres une corruption ratee
 * @param bribed    une corruption a deja ete tentee
 * @param hits      les affaires touchees, une fois menee
 */
public record Operation(String id, int nation, List<Integer> countries, long announced, long runs, String state,
                        double strength, boolean bribed, int hits) {

    static final Codec<Operation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(Operation::id),
            Codec.INT.optionalFieldOf("nation", -1).forGetter(Operation::nation),
            Codec.INT.listOf().optionalFieldOf("countries", List.of()).forGetter(Operation::countries),
            Codec.LONG.optionalFieldOf("announced", 0L).forGetter(Operation::announced),
            Codec.LONG.optionalFieldOf("runs", 0L).forGetter(Operation::runs),
            Codec.STRING.optionalFieldOf("state", State.ANNONCEE.name()).forGetter(Operation::state),
            Codec.DOUBLE.optionalFieldOf("strength", 1.0d).forGetter(Operation::strength),
            Codec.BOOL.optionalFieldOf("bribed", false).forGetter(Operation::bribed),
            Codec.INT.optionalFieldOf("hits", 0).forGetter(Operation::hits)
    ).apply(instance, Operation::new));

    /** Ce qu'il advient d'une operation. */
    public enum State {
        ANNONCEE("Annoncee"), ACHETEE("Achetee"), AU_VERT("Esquivee"), MENEE("Menee");

        private final String label;

        State(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        static State of(String name) {
            for (State state : values()) {
                if (state.name().equals(name)) {
                    return state;
                }
            }
            return MENEE;
        }
    }

    public Operation {
        countries = List.copyOf(countries);
    }

    public State phase() {
        return State.of(state);
    }

    /** Elle n'a pas encore frappe et rien ne l'a arretee. */
    public boolean pending() {
        return phase() == State.ANNONCEE;
    }

    Operation as(State next) {
        return new Operation(id, nation, countries, announced, runs, next.name(), strength, bribed, hits);
    }

    Operation bribeFailed() {
        return new Operation(id, nation, countries, announced, runs, state, 1.25d, true, hits);
    }

    Operation done(int touched) {
        return new Operation(id, nation, countries, announced, runs, State.MENEE.name(), strength, bribed, touched);
    }
}

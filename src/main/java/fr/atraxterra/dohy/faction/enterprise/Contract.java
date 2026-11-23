package fr.atraxterra.dohy.faction.enterprise;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Un contrat entre une organisation criminelle et ta nation (lot 24d) : livrer tant d'unites
 * d'un produit, ou tant de caisses, avant une date, contre de l'argent sale.
 *
 * @param id        son identifiant (« k4 »)
 * @param syndicate l'organisation qui le propose ({@link Syndicate})
 * @param key       le stock a livrer : « drug:LIGNE » ou « crates »
 * @param units     ce qu'il faut livrer
 * @param delivered ce qui l'est deja
 * @param reward    ce qu'il rapporte, en argent sale, une fois rempli
 * @param offered   la proposition (ms) : elle tient deux jours
 * @param deadline  l'echeance, une fois accepte (ms)
 * @param state     son etat ({@link State})
 */
public record Contract(String id, String syndicate, String key, long units, long delivered, long reward,
                       long offered, long deadline, String state) {

    static final Codec<Contract> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(Contract::id),
            Codec.STRING.optionalFieldOf("syndicate", "").forGetter(Contract::syndicate),
            Codec.STRING.optionalFieldOf("key", "").forGetter(Contract::key),
            Codec.LONG.optionalFieldOf("units", 0L).forGetter(Contract::units),
            Codec.LONG.optionalFieldOf("delivered", 0L).forGetter(Contract::delivered),
            Codec.LONG.optionalFieldOf("reward", 0L).forGetter(Contract::reward),
            Codec.LONG.optionalFieldOf("offered", 0L).forGetter(Contract::offered),
            Codec.LONG.optionalFieldOf("deadline", 0L).forGetter(Contract::deadline),
            Codec.STRING.optionalFieldOf("state", State.OFFRE.name()).forGetter(Contract::state)
    ).apply(instance, Contract::new));

    /** Ce qu'il advient d'un contrat. */
    public enum State {
        OFFRE("Propose"), EN_COURS("En cours"), FAIT("Rempli"), ECHOUE("Manque"), EXPIRE("Expire");

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
            return EXPIRE;
        }
    }

    public State phase() {
        return State.of(state);
    }

    public Syndicate from() {
        return Syndicate.of(syndicate);
    }

    /** Propose ou en cours : il compte encore. */
    public boolean open() {
        State phase = phase();
        return phase == State.OFFRE || phase == State.EN_COURS;
    }

    public long missing() {
        return Math.max(0L, units - delivered);
    }

    Contract as(State next) {
        return new Contract(id, syndicate, key, units, delivered, reward, offered, deadline, next.name());
    }

    Contract accepted(long until) {
        return new Contract(id, syndicate, key, units, delivered, reward, offered, until, State.EN_COURS.name());
    }

    Contract plus(long more) {
        long total = Math.min(units, delivered + Math.max(0L, more));
        return new Contract(id, syndicate, key, units, total, reward, offered, deadline,
                total >= units ? State.FAIT.name() : state);
    }
}

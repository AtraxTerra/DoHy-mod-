package fr.atraxterra.dohy.faction.org;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Un cadre de la nation (lot 24a) : un PNJ qui travaille pour toi. Il a un grade, un talent
 * de 1 a 5, un caractere, une loyaute de 0 a 100 et peut tenir un poste.
 *
 * @param id       son identifiant dans la nation (« c12 »)
 * @param name     prenom et nom
 * @param grade    l'indice de son grade ({@link Grade})
 * @param skill    son talent, de 1 a 5
 * @param trait    son caractere ({@link Trait})
 * @param post     le poste qu'il tient ({@link Post}), vide sans
 * @param hired    son embauche (ms)
 * @param promoted son dernier grade gagne (ms) : il faut servir avant de monter
 * @param loyalty  sa loyaute : payee, elle monte ; impayee, elle fond ; a zero, il s'en va
 */
public record Cadre(String id, String name, int grade, int skill, String trait, String post, long hired,
                    long promoted, double loyalty) {

    public static final Codec<Cadre> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(Cadre::id),
            Codec.STRING.fieldOf("name").forGetter(Cadre::name),
            Codec.INT.optionalFieldOf("grade", 0).forGetter(Cadre::grade),
            Codec.INT.optionalFieldOf("skill", 1).forGetter(Cadre::skill),
            Codec.STRING.optionalFieldOf("trait", "").forGetter(Cadre::trait),
            Codec.STRING.optionalFieldOf("post", "").forGetter(Cadre::post),
            Codec.LONG.optionalFieldOf("hired", 0L).forGetter(Cadre::hired),
            Codec.LONG.optionalFieldOf("promoted", 0L).forGetter(Cadre::promoted),
            Codec.DOUBLE.optionalFieldOf("loyalty", 60.0d).forGetter(Cadre::loyalty)
    ).apply(instance, Cadre::new));

    /** Les caracteres : ils changent la facon dont la loyaute bouge. */
    public enum Trait {
        FIDELE("Fidele", "Sa loyaute monte deux fois plus vite"),
        AMBITIEUX("Ambitieux", "Sans promotion depuis deux semaines, il s'impatiente"),
        MERCENAIRE("Mercenaire", "Il ne reste que pour l'argent : un salaire en retard compte double"),
        PRUDENT("Prudent", "Sa loyaute bouge moitie moins, dans un sens comme dans l'autre");

        private final String label;
        private final String hint;

        Trait(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }

        public String label() {
            return label;
        }

        public String hint() {
            return hint;
        }

        public static Trait of(String name) {
            for (Trait trait : values()) {
                if (trait.name().equals(name)) {
                    return trait;
                }
            }
            return PRUDENT;
        }
    }

    public Grade rank() {
        return Grade.of(grade);
    }

    public Trait character() {
        return Trait.of(trait);
    }

    public Post role() {
        return Post.of(post);
    }

    /** L'entreprise qu'il gere (lot 24b : son poste vaut « @id »), vide sinon. */
    public String manages() {
        return post.startsWith("@") ? post.substring(1) : "";
    }

    /** Ni poste ni gerance. */
    public boolean free() {
        return post.isEmpty();
    }

    /** « Ines Morel, Capitaine » */
    public String label() {
        return name + ", " + rank().label();
    }

    Cadre withPost(String next) {
        return new Cadre(id, name, grade, skill, trait, next, hired, promoted, loyalty);
    }

    Cadre withLoyalty(double next) {
        return new Cadre(id, name, grade, skill, trait, post, hired, promoted, Math.max(0.0d, Math.min(100.0d, next)));
    }

    Cadre promotedAt(long now) {
        Grade up = rank().next();
        if (up == null) {
            return this;
        }
        return new Cadre(id, name, up.ordinal(), skill, trait, post, hired, now, Math.min(100.0d, loyalty + 10.0d));
    }

    /** Le jour ou il pourra monter : son dernier grade gagne, plus les jours que ce grade demande. */
    public long promotable() {
        return Math.max(hired, promoted) + rank().days() * 86_400_000L;
    }
}

package fr.atraxterra.dohy.palamod.botanist;

/**
 * Ce qu'une plante en pot reclame ({@code IBotanistNeed} de JobsReborn).
 *
 * <p>Les cinq besoins de ce lot, avec les textes de JobsReborn : arroser
 * (« Remplir l'arrosoir »), jouer un air (« Jouer un air »), secouer
 * (« Secoue-la d'un clic droit, main vide »), percer la boule de spores
 * (« Perce sa boule d'un clic droit, main vide ») et donner un objet
 * (« Donner un objet »). Les nibblers, attrapes au filet, sont du lot suivant.
 *
 * <p>Un besoin se remplit dans les 90 secondes (temps passe pres de la plante) ;
 * rate, il la ralentit d'une minute ({@code BotanistNeedFailure.SLOW}).
 */
public enum Need {
    WATER("water", "de l'eau", "Arrose-la : clic droit avec un arrosoir rempli."),
    MUSIC("music", "un air de musique", "Joue-lui un air : clic droit avec une guitare."),
    SHAKE("shake", "une secousse", "Secoue-la d'un clic droit, main vide."),
    BURST("burst", "qu'on perce sa boule", "Perce sa boule de spores d'un clic droit, main vide."),
    ITEM("item", "un objet", "Donne-le-lui : clic droit avec l'objet en main.");

    /** Le temps pour repondre, en ticks passes pres de la plante. */
    public static final int TIMEOUT = 90 * 20;
    /** Le retard d'un besoin rate, en ticks. */
    public static final int SLOW = 60 * 20;

    private final String id;
    private final String label;
    private final String hint;

    Need(String id, String label, String hint) {
        this.id = id;
        this.label = label;
        this.hint = hint;
    }

    public String id() {
        return id;
    }

    /** Ce que reclame la plante, a la suite de « Reclame : ». */
    public String label() {
        return label;
    }

    /** Comment y repondre. */
    public String hint() {
        return hint;
    }

    /** Les besoins qui se remplissent d'un clic droit main vide. */
    public boolean emptyHand() {
        return this == SHAKE || this == BURST;
    }

    public static Need byId(String id) {
        for (Need need : values()) {
            if (need.id.equals(id)) {
                return need;
            }
        }
        return null;
    }
}

package fr.atraxterra.dohy.palamod.defense;

/**
 * Les amulettes ({@code ItemAmulet.Type}), celles que la faction utilise.
 *
 * <p>Sur Paladium, une amulette se combine a un plastron en paladium ou en
 * endium et agit tant qu'on le porte. Deux etaient desactivees (discretion,
 * combat) et deux visaient les autres joueurs (voyance cache le pseudo a
 * travers les murs) : le solo leur donne un effet face aux monstres.
 */
public enum AmuletType {

    /** Paladium : fait apparaitre une creature de metier. Ici : +25 % d'XP de Chasseur. */
    HUNTER("hunter"),
    /** Paladium : cache le pseudo a travers les murs. Ici : les monstres a 24 blocs brillent. */
    VOYANCE("voyance"),
    /** Desactivee sur Paladium. Ici : les monstres te voient deux fois moins loin. */
    DISCRETION("discretion"),
    /** Desactivee sur Paladium. Ici : +15 % de degats contre les monstres. */
    COMBAT("combat"),
    /** Resistance au feu permanente, comme sur Paladium. */
    MAGMA("magma");

    private final String key;

    AmuletType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static AmuletType byKey(String key) {
        for (AmuletType type : values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        return null;
    }
}

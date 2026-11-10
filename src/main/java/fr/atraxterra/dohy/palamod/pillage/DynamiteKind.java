package fr.atraxterra.dohy.palamod.pillage;

/**
 * Les trois dynamites ({@code BaseItemDynamite} et ses filles).
 *
 * <p>Une dynamite se lance comme une boule de neige, se colle a ce qu'elle
 * touche et saute deux secondes plus tard, une demi-seconde de plus au hasard.
 * L'explosion est celle de la TNT : elle epargne donc l'obsidienne, les portes
 * renforcees, les enclumes et l'eau. C'est la TNT en endium qui perce les murs.
 *
 * @param power    la puissance de l'explosion (la TNT vanilla : 4)
 * @param silent   la ninja dynamite saute sans un bruit
 */
public enum DynamiteKind {
    DYNAMITE("dynamite", 2.5f, false),
    NINJA("ninja_dynamite", 3.0f, true),
    BIG("big_dynamite", 4.5f, false);

    /** {@code DynamiteEntity} : 40 ticks, plus 0 a 9 au hasard. */
    public static final int FUSE = 40;
    public static final int FUSE_SPREAD = 10;

    private final String id;
    private final float power;
    private final boolean silent;

    DynamiteKind(String id, float power, boolean silent) {
        this.id = id;
        this.power = power;
        this.silent = silent;
    }

    public String id() {
        return id;
    }

    public float power() {
        return power;
    }

    public boolean silent() {
        return silent;
    }

    /** La cle de l'arbre de faction qui la debloque, et le compteur qu'elle alimente. */
    public String key() {
        return "use:" + id;
    }
}

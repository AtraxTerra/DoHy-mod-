package fr.atraxterra.dohy.palamod.dungeon;

/**
 * Les trois tiers d'essence du wiki : « Fragment d'essence » repare l'armure
 * basique, « Essence » la renforcee, « Essence concentree » la legendaire —
 * et une essence ne repare que son tier.
 */
public enum EssenceTier {

    FRAGMENT("_fragment", 10),
    ESSENCE("", 40),
    CONCENTRATE("_concentrate", 70);

    private final String suffix;
    private final int from;

    EssenceTier(String suffix, int from) {
        this.suffix = suffix;
        this.from = from;
    }

    public String suffix() {
        return suffix;
    }

    /** Le premier palier ou elle tombe, celui de l'equipement qu'elle repare. */
    public int from() {
        return from;
    }

    public static EssenceTier forPalier(int palier) {
        if (palier >= CONCENTRATE.from) {
            return CONCENTRATE;
        }
        return palier >= ESSENCE.from ? ESSENCE : FRAGMENT;
    }
}

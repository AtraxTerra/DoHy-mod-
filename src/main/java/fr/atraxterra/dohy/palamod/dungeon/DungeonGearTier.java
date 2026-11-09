package fr.atraxterra.dohy.palamod.dungeon;

/**
 * Les trois tiers de l'equipement de donjon (« Basique », « Renforcee »,
 * « Legendaire », ou « tier 1 / 2 / 3 » dans les tables du wiki), et le set
 * antique du palier 100.
 *
 * <p>Le wiki ne donne aucune statistique. Choix du joueur : un equipement
 * fort <b>surtout en donjon</b>. Dehors, le tier vaut a peu pres un metal
 * (basique l'amethyste, renforce le paladium, legendaire l'endium mixte,
 * antique l'endium) ; dedans, chaque piece reduit les degats des monstres et
 * l'epee frappe plus fort, a plein dans son donjon et a moitie ailleurs.
 *
 * @param prefix       le prefixe des identifiants ({@code talikus_reinforced_helmet})
 * @param armorBonus   la part des degats de monstres retiree, par piece portee
 * @param swordBonus   le supplement de degats de l'epee contre les monstres
 * @param from         le palier d'ou le wiki le rend disponible
 */
public enum DungeonGearTier {

    BASIC("", "", "", 0.04f, 0.25f, 10),
    REINFORCED("reinforced_", "renforce", "Reinforced", 0.07f, 0.50f, 40),
    LEGENDARY("legendary_", "legendaire", "Legendary", 0.10f, 0.80f, 70),
    ANTIQUE("antique_", "antique", "Antique", 0.12f, 0.0f, 100);

    private final String prefix;
    private final String labelFr;
    private final String labelEn;
    private final float armorBonus;
    private final float swordBonus;
    private final int from;

    DungeonGearTier(String prefix, String labelFr, String labelEn, float armorBonus, float swordBonus, int from) {
        this.prefix = prefix;
        this.labelFr = labelFr;
        this.labelEn = labelEn;
        this.armorBonus = armorBonus;
        this.swordBonus = swordBonus;
        this.from = from;
    }

    public String prefix() {
        return prefix;
    }

    public String labelFr() {
        return labelFr;
    }

    public String labelEn() {
        return labelEn;
    }

    public float armorBonus() {
        return armorBonus;
    }

    public float swordBonus() {
        return swordBonus;
    }

    public int from() {
        return from;
    }

    /** Le tier d'essence qui le repare : fragment, essence, essence concentree. */
    public EssenceTier essence() {
        return switch (this) {
            case BASIC -> EssenceTier.FRAGMENT;
            case REINFORCED -> EssenceTier.ESSENCE;
            case LEGENDARY, ANTIQUE -> EssenceTier.CONCENTRATE;
        };
    }
}

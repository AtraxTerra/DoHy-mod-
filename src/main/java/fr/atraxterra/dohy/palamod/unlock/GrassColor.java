package fr.atraxterra.dohy.palamod.unlock;

import net.minecraft.world.item.DyeColor;

/**
 * Les dix couleurs des herbes colorees de PalaJobs ({@code BlockColoredGrassSeeds},
 * metadonnees 0 a 9), et le colorant qui fait chaque graine
 * ({@code CraftRegistry} : colorant + graine de ble).
 *
 * <p>Le colorant de 1.7.10 avait une metadonnee : 0 noir, 1 rouge, 2 vert,
 * 3 marron, 4 bleu, 5 violet, 11 jaune, 14 orange, 9 rose, 10 vert clair.
 * La teinte de l'herbe est celle du colorant de 26.2.
 */
public enum GrassColor {
    BLACK("black", DyeColor.BLACK),
    RED("red", DyeColor.RED),
    DARKGREEN("darkgreen", DyeColor.GREEN),
    BROWN("brown", DyeColor.BROWN),
    BLUE("blue", DyeColor.BLUE),
    PURPLE("purple", DyeColor.PURPLE),
    YELLOW("yellow", DyeColor.YELLOW),
    ORANGE("orange", DyeColor.ORANGE),
    PINK("pink", DyeColor.PINK),
    GREEN("green", DyeColor.LIME);

    private final String key;
    private final DyeColor dye;

    GrassColor(String key, DyeColor dye) {
        this.key = key;
        this.dye = dye;
    }

    public String key() {
        return key;
    }

    public DyeColor dye() {
        return dye;
    }

    /** La teinte de l'herbe et des graines, opaque. */
    public int tint() {
        return 0xFF000000 | dye.getTextureDiffuseColor();
    }
}

package fr.atraxterra.dohy.palamod.storage;

/**
 * Les deux coffres de PalaMod : meme taille, meme interface, couleurs du titre.
 *
 * <p>{@code ModdedChestLogic(1, 1)} pour l'amethyste et {@code (1, 2)} pour le
 * titane : 108 cases de stockage chacun, plus une ou deux cases d'anneau de
 * reparation. Les anneaux ({@code BaseItemRing}) ne sont pas portes ; sans eux,
 * ces cases n'auraient rien a accepter, et elles ne sont pas creees. Les deux
 * coffres different donc par leur recette, leur titre et leur texture.
 */
public enum ChestTier {

    /** Titre (183, 30, 255), ombre (125, 0, 184). */
    AMETHYST("amethyst_chest", "Coffre en amethyste", 0xFFB71EFF),
    /** Titre (165, 165, 165), ombre (88, 88, 88). */
    TITANE("titane_chest", "Coffre en titane", 0xFFA5A5A5);

    /** 12 colonnes x 9 rangees : {@code new ItemStack[108 + ringSlots]}. */
    public static final int COLUMNS = 12;
    public static final int ROWS = 9;
    public static final int SIZE = COLUMNS * ROWS;

    private final String key;
    private final String label;
    private final int titleColor;

    ChestTier(String key, String label, int titleColor) {
        this.key = key;
        this.label = label;
        this.titleColor = titleColor;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public int titleColor() {
        return titleColor;
    }
}

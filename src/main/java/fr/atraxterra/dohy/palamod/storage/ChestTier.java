package fr.atraxterra.dohy.palamod.storage;

/**
 * Les coffres de PalaMod, et le coffre en or d'Iron Chests.
 *
 * <p>Ceux de PalaMod ({@code ModdedChestLogic}) ont tous 108 cases, 12 x 9,
 * plus une colonne de cases d'anneau a droite. Les deux nombres du constructeur
 * de l'original reglent la reparation : combien de rangees du haut les anneaux
 * reparent, et combien d'anneaux on peut poser — amethyste {@code (1, 1)},
 * titane {@code (1, 2)}, paladium {@code (2, 4)}, paladium vert {@code (6, 4)},
 * endium {@code (9, 4)}. Le coffre en endium prend les anneaux en endium et ne
 * repare que l'endium ; les autres, les anneaux en paladium et tout le reste
 * (voir {@code RingRepair}).
 *
 * <p>Le coffre en or vient d'Iron Chests ({@code IronChestType.GOLD}) : 81
 * cases, 9 x 9. Il sert a la recette du gestionnaire d'enclos des montures.
 */
public enum ChestTier {

    /** Titre (183, 30, 255), ombre (125, 0, 184). */
    AMETHYST("amethyst_chest", "Coffre en amethyste", 0xFFB71EFF, 12, 1, 1),
    /** Titre (165, 165, 165), ombre (88, 88, 88). */
    TITANE("titane_chest", "Coffre en titane", 0xFFA5A5A5, 12, 1, 2),
    /** Titre (239, 57, 38), ombre (185, 28, 12). */
    PALADIUM("paladium_chest", "Coffre en paladium", 0xFFEF3926, 12, 2, 4),
    /** Titre (37, 226, 81), ombre (52, 146, 12). */
    GREEN_PALADIUM("green_paladium_chest", "Coffre en paladium vert", 0xFF25E251, 12, 6, 4),
    /** Titre (37, 85, 255), ombre (17, 0, 214). */
    ENDIUM("endium_chest", "Coffre en endium", 0xFF2555FF, 12, 9, 4),
    /** Iron Chests : le titre dore, pas d'anneau. */
    GOLD("gold_chest", "Coffre en or", 0xFFF5D33F, 9, 0, 0);

    /** Toujours 9 rangees. */
    public static final int ROWS = 9;
    /** Le plus grand coffre, anneaux compris : la taille du conteneur cote client avant synchronisation. */
    public static final int MAX_SIZE = 12 * ROWS + 4;
    /** La colonne d'anneaux : 6 px d'ecart apres la grille, une case, la marge. */
    public static final int RING_GAP = 6;
    public static final int RING_COLUMN = RING_GAP + 16;

    private final String key;
    private final String label;
    private final int titleColor;
    private final int columns;
    private final int repairRows;
    private final int ringSlots;

    ChestTier(String key, String label, int titleColor, int columns, int repairRows, int ringSlots) {
        this.key = key;
        this.label = label;
        this.titleColor = titleColor;
        this.columns = columns;
        this.repairRows = repairRows;
        this.ringSlots = ringSlots;
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

    public int columns() {
        return columns;
    }

    /** Les cases de la grille, celles que voient les entonnoirs. */
    public int size() {
        return columns * ROWS;
    }

    /** Les rangees du haut que les anneaux reparent ({@code repairableRow}). */
    public int repairRows() {
        return repairRows;
    }

    /** Les cases d'anneau, apres la grille. */
    public int ringSlots() {
        return ringSlots;
    }

    /** Le coffre en endium prend les anneaux en endium et ne repare que l'endium. */
    public boolean endiumRings() {
        return this == ENDIUM;
    }

    /** Grille et anneaux : la taille du conteneur. */
    public int containerSize() {
        return size() + ringSlots;
    }

    /** La grille et 12 px de marge de chaque cote. */
    private int gridWidth() {
        return columns * 18 + 24;
    }

    /** Le panneau : la grille, et la colonne d'anneaux s'il y en a. */
    public int panelWidth() {
        return gridWidth() + (ringSlots > 0 ? RING_COLUMN : 0);
    }

    /** La texture : 256 de large, ou le panneau s'il deborde. */
    public int textureWidth() {
        return Math.max(256, panelWidth());
    }

    /** La colonne d'anneaux, a droite de la grille. */
    public int ringX() {
        return 12 + columns * 18 + RING_GAP;
    }

    /** L'inventaire du joueur, centre sous la grille. */
    public int playerX() {
        return (gridWidth() - 9 * 18) / 2;
    }
}

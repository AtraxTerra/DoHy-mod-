package fr.atraxterra.dohy.palamod.storage;

/**
 * Les coffres de PalaMod, et le coffre en or d'Iron Chests.
 *
 * <p>Ceux de PalaMod ({@code ModdedChestLogic}) ont tous 108 cases, 12 x 9 :
 * amethyste {@code (1, 1)}, titane {@code (1, 2)}, paladium {@code (2, 4)},
 * paladium vert {@code (6, 4)}, endium {@code (9, 4)}. Les deux nombres reglent
 * les cases d'anneau de reparation ({@code BaseItemRing}), non portes : sans
 * anneau, ces cases n'auraient rien a accepter, et elles ne sont pas creees.
 * Les coffres different donc par leur recette, leur titre et leur texture.
 *
 * <p>Le coffre en or vient d'Iron Chests ({@code IronChestType.GOLD}) : 81
 * cases, 9 x 9. Il sert a la recette du gestionnaire d'enclos des montures.
 */
public enum ChestTier {

    /** Titre (183, 30, 255), ombre (125, 0, 184). */
    AMETHYST("amethyst_chest", "Coffre en amethyste", 0xFFB71EFF, 12),
    /** Titre (165, 165, 165), ombre (88, 88, 88). */
    TITANE("titane_chest", "Coffre en titane", 0xFFA5A5A5, 12),
    /** Titre (239, 57, 38), ombre (185, 28, 12). */
    PALADIUM("paladium_chest", "Coffre en paladium", 0xFFEF3926, 12),
    /** Titre (37, 226, 81), ombre (52, 146, 12). */
    GREEN_PALADIUM("green_paladium_chest", "Coffre en paladium vert", 0xFF25E251, 12),
    /** Titre (37, 85, 255), ombre (17, 0, 214). */
    ENDIUM("endium_chest", "Coffre en endium", 0xFF2555FF, 12),
    /** Iron Chests : le titre dore. */
    GOLD("gold_chest", "Coffre en or", 0xFFF5D33F, 9);

    /** Toujours 9 rangees. */
    public static final int ROWS = 9;
    /** Le plus grand coffre : la taille du conteneur cote client avant synchronisation. */
    public static final int MAX_SIZE = 12 * ROWS;

    private final String key;
    private final String label;
    private final int titleColor;
    private final int columns;

    ChestTier(String key, String label, int titleColor, int columns) {
        this.key = key;
        this.label = label;
        this.titleColor = titleColor;
        this.columns = columns;
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

    public int size() {
        return columns * ROWS;
    }

    /** Le panneau : la grille et 12 px de marge de chaque cote. */
    public int panelWidth() {
        return columns * 18 + 24;
    }

    /** L'inventaire du joueur, centre sous la grille. */
    public int playerX() {
        return (panelWidth() - 9 * 18) / 2;
    }
}

package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Les salles d'un palier (« Donjon Talikus » et suivants) : une salle de
 * combat obligatoire, puis, a chaque porte, le choix entre une et trois
 * salles — combat, enigme, boost ou marchande. Mini-boss et boss ferment les
 * paliers en 5 et en 0 ; la sortie, tous les paliers.
 *
 * @param halfWidth  demi-largeur interieure (x et z)
 * @param height     hauteur interieure
 * @param glass      le verre de sa porte, qui dit ce qu'il y a derriere
 */
public enum RoomType {

    COMBAT("Combat", 12, 8, Blocks.RED_STAINED_GLASS, ChatFormatting.RED, true),
    PUZZLE("Enigme", 10, 8, Blocks.BLUE_STAINED_GLASS, ChatFormatting.BLUE, true),
    BOOST("Boost", 7, 6, Blocks.LIME_STAINED_GLASS, ChatFormatting.GREEN, false),
    MERCHANT("Marchande", 7, 6, Blocks.YELLOW_STAINED_GLASS, ChatFormatting.YELLOW, false),
    BOSS("Boss", 16, 14, Blocks.PURPLE_STAINED_GLASS, ChatFormatting.DARK_PURPLE, true),
    EXIT("Sortie", 0, 0, Blocks.WHITE_STAINED_GLASS, ChatFormatting.WHITE, false);

    private final String label;
    private final int halfWidth;
    private final int height;
    private final Block glass;
    private final ChatFormatting color;
    private final boolean loot;

    RoomType(String label, int halfWidth, int height, Block glass, ChatFormatting color, boolean loot) {
        this.label = label;
        this.halfWidth = halfWidth;
        this.height = height;
        this.glass = glass;
        this.color = color;
        this.loot = loot;
    }

    public String label() {
        return label;
    }

    public int halfWidth() {
        return halfWidth;
    }

    public int height() {
        return height;
    }

    public Block glass() {
        return glass;
    }

    public ChatFormatting color() {
        return color;
    }

    /** Une salle qui laisse un coffre une fois finie. */
    public boolean loot() {
        return loot;
    }
}

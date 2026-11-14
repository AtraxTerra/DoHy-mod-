package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Les salles d'un palier (« Donjon Talikus » et suivants) : une salle de
 * combat obligatoire, puis, a chaque porte, le choix entre une et trois
 * salles — combat, enigme, boost ou marchande, et depuis les modes elite,
 * piege et survie (DoHy). Mini-boss et boss ferment les paliers en 5 et en 0 ;
 * la sortie, tous les paliers.
 *
 * @param halfWidth  demi-largeur interieure (x et z)
 * @param height     hauteur interieure
 * @param glass      le verre de sa porte, qui dit ce qu'il y a derriere
 */
public enum RoomType {

    COMBAT("Combat", 12, 8, Blocks.STAINED_GLASS.pick(DyeColor.RED), ChatFormatting.RED, true),
    PUZZLE("Enigme", 10, 8, Blocks.STAINED_GLASS.pick(DyeColor.BLUE), ChatFormatting.BLUE, true),
    BOOST("Boost", 7, 6, Blocks.STAINED_GLASS.pick(DyeColor.LIME), ChatFormatting.GREEN, false),
    MERCHANT("Marchande", 7, 6, Blocks.STAINED_GLASS.pick(DyeColor.YELLOW), ChatFormatting.YELLOW, false),
    BOSS("Boss", 16, 14, Blocks.STAINED_GLASS.pick(DyeColor.PURPLE), ChatFormatting.DARK_PURPLE, true),
    /** Un des elites du donjon ({@link Dungeon#elites()}) : un mini-boss a tout palier. */
    ELITE("Elite", 12, 9, Blocks.STAINED_GLASS.pick(DyeColor.ORANGE), ChatFormatting.GOLD, true),
    /** Un sol piege qui s'embrase par rangees : traverser jusqu'au levier du fond. */
    TRAP("Piege", 9, 7, Blocks.STAINED_GLASS.pick(DyeColor.BLACK), ChatFormatting.DARK_GRAY, true),
    /** Tenir jusqu'au bout du compte a rebours, sous des renforts sans fin. */
    SURVIVAL("Survie", 12, 8, Blocks.STAINED_GLASS.pick(DyeColor.MAGENTA), ChatFormatting.LIGHT_PURPLE, true),
    EXIT("Sortie", 0, 0, Blocks.STAINED_GLASS.pick(DyeColor.WHITE), ChatFormatting.WHITE, false);

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

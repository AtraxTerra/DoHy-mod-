package fr.atraxterra.dohy.palamod.smeltery;

import net.minecraft.world.item.ItemStack;

/**
 * Un outil que le grinder peut ameliorer : le marteau, la broadsword et la
 * fastsword en paladium ({@code SlotUpgradeGrinder}). Les versions en
 * amethyste et en titane ne le sont pas.
 */
public interface ModifiableTool {

    /** Faux pour les versions en amethyste et en titane, que le grinder refuse. */
    boolean modifiable();

    GrinderModifier.ToolKind kind();

    /** {@code modifiersmax} : trois emplacements, quatre pour la broadsword. */
    int baseSlots();

    /** A appeler apres chaque modificateur pose : un dommage change les degats. */
    default void onModified(ItemStack stack, ToolModifiers modifiers) {
    }
}

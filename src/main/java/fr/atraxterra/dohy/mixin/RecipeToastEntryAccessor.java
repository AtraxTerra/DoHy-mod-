package fr.atraxterra.dohy.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Les deux objets d'une entree de la notification des recettes (classe non publique du jeu). */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.RecipeToast$Entry")
public interface RecipeToastEntryAccessor {

    @Accessor("unlockedItem")
    ItemStack dohy$unlocked();

    @Accessor("categoryItem")
    ItemStack dohy$category();
}

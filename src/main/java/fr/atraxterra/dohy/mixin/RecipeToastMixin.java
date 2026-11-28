package fr.atraxterra.dohy.mixin;

import fr.atraxterra.dohy.client.ui.KitToasts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * La notification des recettes debloquees au style du kit ({@link KitToasts}), client seulement.
 * L'entree affichee est celle que le jeu fait tourner ({@code displayedRecipeIndex}) ; ses objets
 * se lisent par {@link RecipeToastEntryAccessor}, la classe de l'entree n'etant pas publique.
 */
@Mixin(RecipeToast.class)
public abstract class RecipeToastMixin {

    @Shadow
    @Final
    private static Component TITLE_TEXT;

    @Shadow
    @Final
    private static Component DESCRIPTION_TEXT;

    @Shadow
    @Final
    private List<?> recipeItems;

    @Shadow
    private int displayedRecipeIndex;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;J)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void dohy$kit(GuiGraphicsExtractor graphics, Font font, long time, CallbackInfo callback) {
        if (recipeItems.isEmpty() || displayedRecipeIndex < 0 || displayedRecipeIndex >= recipeItems.size()
                || !(recipeItems.get(displayedRecipeIndex) instanceof RecipeToastEntryAccessor entry)) {
            return;
        }
        Toast self = (Toast) (Object) this;
        if (KitToasts.recipe(graphics, self.width(), self.height(), TITLE_TEXT, DESCRIPTION_TEXT,
                entry.dohy$unlocked(), entry.dohy$category())) {
            callback.cancel();
        }
    }
}

package fr.atraxterra.dohy.mixin;

import fr.atraxterra.dohy.client.ui.KitToasts;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * La notification d'un progres au style du kit ({@link KitToasts}), client seulement. Crochet
 * facultatif ({@code require = 0}) : s'il ne trouve plus sa methode, la notification garde le
 * dessin du jeu.
 */
@Mixin(AdvancementToast.class)
public abstract class AdvancementToastMixin {

    @Shadow
    @Final
    private AdvancementHolder advancement;

    @Shadow
    @Final
    private ItemStack iconItem;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;J)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void dohy$kit(GuiGraphicsExtractor graphics, Font font, long time, CallbackInfo callback) {
        Toast self = (Toast) (Object) this;
        if (KitToasts.advancement(graphics, self.width(), self.height(),
                advancement.value().display().orElse(null), iconItem)) {
            callback.cancel();
        }
    }
}

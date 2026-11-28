package fr.atraxterra.dohy.mixin;

import fr.atraxterra.dohy.client.ui.KitToasts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Les messages du systeme au style du kit ({@link KitToasts}), client seulement. */
@Mixin(SystemToast.class)
public abstract class SystemToastMixin {

    @Shadow
    @Final
    private SystemToast.SystemToastId id;

    @Shadow
    private List<FormattedCharSequence> titleLines;

    @Shadow
    private List<FormattedCharSequence> messageLines;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;J)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void dohy$kit(GuiGraphicsExtractor graphics, Font font, long time, CallbackInfo callback) {
        Toast self = (Toast) (Object) this;
        if (KitToasts.system(graphics, self.width(), self.height(), id, titleLines, messageLines)) {
            callback.cancel();
        }
    }
}

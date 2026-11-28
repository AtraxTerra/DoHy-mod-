package fr.atraxterra.dohy.mixin;

import fr.atraxterra.dohy.client.ui.KitToasts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Le tutoriel au style du kit ({@link KitToasts}), client seulement. La progression lissee est
 * tenue par le jeu ({@code update}) : on ne fait que la lire.
 */
@Mixin(TutorialToast.class)
public abstract class TutorialToastMixin {

    @Shadow
    @Final
    private TutorialToast.Icons icon;

    @Shadow
    @Final
    private List<FormattedCharSequence> lines;

    @Shadow
    @Final
    private boolean progressable;

    @Shadow
    private float progress;

    @Shadow
    private float smoothedProgress;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;J)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void dohy$kit(GuiGraphicsExtractor graphics, Font font, long time, CallbackInfo callback) {
        Toast self = (Toast) (Object) this;
        if (KitToasts.tutorial(graphics, self.width(), self.height(), icon, lines, progressable, progress,
                smoothedProgress)) {
            callback.cancel();
        }
    }
}

package fr.atraxterra.dohy.mixin;

import fr.atraxterra.dohy.client.ui.KitToasts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.NowPlayingToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * La musique en cours au style du kit ({@link KitToasts}), client seulement. Le jeu dessine
 * cette carte par une methode statique, aussi appelee hors des notifications : la meme carte
 * partout. Sa largeur reste celle du jeu (le titre du morceau dans la police du jeu, plus les
 * marges) : le titre du kit, plus etroit, y tient.
 */
@Mixin(NowPlayingToast.class)
public abstract class NowPlayingToastMixin {

    @Shadow
    @Final
    private static Identifier MUSIC_NOTES_SPRITE;

    @Shadow
    private static int musicNoteColor;

    @Shadow
    private static String getCurrentSongName() {
        throw new AssertionError();
    }

    @Shadow
    private static Component getNowPlayingString(String song) {
        throw new AssertionError();
    }

    @Inject(method = "extractToast(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void dohy$kit(GuiGraphicsExtractor graphics, Font font, CallbackInfo callback) {
        String song = getCurrentSongName();
        if (song == null) {
            return;
        }
        Component title = getNowPlayingString(song);
        int width = 30 + font.width(title) + 7;
        if (KitToasts.nowPlaying(graphics, width, 30, MUSIC_NOTES_SPRITE, musicNoteColor, title.getString())) {
            callback.cancel();
        }
    }
}

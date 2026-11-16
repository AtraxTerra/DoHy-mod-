package fr.atraxterra.dohy.mixin;

import fr.atraxterra.dohy.client.hud.ZephyrChat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Le chat au style Zephyr ({@link ZephyrChat}), client seulement.
 *
 * <p>Le chat se dessine en deux temps : la methode publique choisit son « pinceau »
 * (celui du HUD, ou celui de l'ecran de chat qui gere le survol), puis une methode privee
 * fait tout le travail avec lui. On remplace le pinceau par le notre, qui garde celui du jeu
 * dedans, et on est prevenu a la fin du travail pour poser la carte.
 *
 * <p>Aucun crochet n'est obligatoire ({@code require = 0}) : si une version du jeu change ces
 * methodes, le chat reste simplement celui du jeu au lieu d'empecher le jeu de demarrer.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD"), require = 0)
    private void dohy$begin(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY,
                            ChatComponent.DisplayMode mode, boolean changeCursor, CallbackInfo callback) {
        ZephyrChat.begin(graphics);
    }

    @ModifyArg(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V"),
            index = 0, require = 0)
    private ChatComponent.ChatGraphicsAccess dohy$wrap(ChatComponent.ChatGraphicsAccess access) {
        return ZephyrChat.wrap(access);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At("RETURN"), require = 0)
    private void dohy$finish(ChatComponent.ChatGraphicsAccess access, int height, int ticks,
                             ChatComponent.DisplayMode mode, CallbackInfo callback) {
        ZephyrChat.finish(access);
    }
}

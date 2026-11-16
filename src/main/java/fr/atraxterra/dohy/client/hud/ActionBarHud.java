package fr.atraxterra.dohy.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * La barre d'action (les messages au-dessus de la hotbar : un disque qui joue, un lit
 * occupe, les retours des machines) dans une pastille, au-dessus du nom de l'objet tenu.
 * Le texte et son fondu restent ceux du jeu, l'arc-en-ciel du disque compris.
 */
final class ActionBarHud {

    private ActionBarHud() {
    }

    static boolean draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (HudKit.hidden(minecraft)) {
            return true;
        }
        if (!HudAccess.available()) {
            return false;
        }
        Hud hud = minecraft.gui.hud;
        Component message = HudAccess.overlayMessage(hud);
        int time = HudAccess.overlayTime(hud);
        if (message == null || time <= 0) {
            return true;
        }
        float left = time - delta.getGameTimeDeltaPartialTick(false);
        float alpha = Math.min(1.0f, left * 255.0f / 20.0f / 255.0f);
        if (alpha <= 0.02f) {
            return true;
        }
        int color = HudAccess.overlayAnimated(hud)
                ? Mth.hsvToArgb(left / 50.0f, 0.7f, 0.6f, 255) : 0xFFFFFFFF;
        // Au-dessus du nom de l'objet tenu s'il est affiche, a sa place sinon.
        float bottom = graphics.guiHeight() - ItemNameHud.base(minecraft, hud) - (ItemNameHud.showing(hud) ? 18.0f
                : 3.0f);
        ItemNameHud.pill(graphics, minecraft, message, bottom, alpha, color);
        return true;
    }
}

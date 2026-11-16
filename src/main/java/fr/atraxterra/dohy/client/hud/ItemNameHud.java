package fr.atraxterra.dohy.client.hud;

import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Le nom de l'objet qu'on vient de prendre en main, dans une pastille au-dessus des barres.
 * Le texte reste celui du jeu (la couleur de rarete, l'italique d'un objet renomme, ce que
 * les mods y ajoutent) ; il s'efface comme dans le jeu.
 */
final class ItemNameHud {

    private ItemNameHud() {
    }

    static boolean draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (HudKit.hidden(minecraft)) {
            return true;
        }
        if (!HudAccess.available() || minecraft.gameMode == null
                || minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return false;
        }
        Hud hud = minecraft.gui.hud;
        int timer = HudAccess.highlightTimer(hud);
        ItemStack stack = HudAccess.highlightStack(hud);
        if (timer <= 0 || stack.isEmpty()) {
            return true;
        }
        float alpha = Math.min(1.0f, timer * 256.0f / 10.0f / 255.0f);
        if (alpha <= 0.02f) {
            return true;
        }
        MutableComponent name = Component.empty().append(stack.getHoverName()).withStyle(stack.getRarity()
                .getStyleModifier());
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            name.withStyle(ChatFormatting.ITALIC);
        }
        Component text = stack.getHighlightTip(name);
        float bottom = graphics.guiHeight() - base(minecraft, hud) - 3.0f;
        pill(graphics, minecraft, text, bottom, alpha, 0xFFFFFFFF);
        return true;
    }

    /** Le nom de l'objet est-il a l'ecran (la barre d'action se pose alors au-dessus). */
    static boolean showing(Hud hud) {
        return HudAccess.highlightTimer(hud) > 0 && !HudAccess.highlightStack(hud).isEmpty();
    }

    /** La hauteur deja prise au-dessus du bas : les barres en survie, la hotbar seule sinon. */
    static int base(Minecraft minecraft, Hud hud) {
        return HudKit.survival(minecraft) ? Math.max(hud.leftHeight, hud.rightHeight)
                : HudKit.BOTTOM + HudKit.HOTBAR_HEIGHT + 4;
    }

    /** Une pastille centree dont le bas est a {@code bottom}, avec un texte du jeu. */
    static float pill(GuiGraphicsExtractor graphics, Minecraft minecraft, Component text, float bottom, float alpha,
                      int color) {
        Font font = minecraft.font;
        float unit = HudKit.unit(minecraft);
        Paint paint = new Paint(graphics, unit);
        int width = font.width(text);
        float pillWidth = width + 12.0f;
        float height = 12.0f;
        float x = graphics.guiWidth() / 2.0f - pillWidth / 2.0f;
        float y = bottom - height;
        paint.round(x, y, pillWidth, height, height / 2.0f, Paint.alpha(0xEE101113, alpha));
        paint.outline(x, y, pillWidth, height, height / 2.0f, Paint.alpha(Ui.Theme.LINE2, alpha), 1);
        int argb = (Math.round(alpha * 255.0f) << 24) | (color & 0xFFFFFF);
        graphics.text(font, text, Math.round(graphics.guiWidth() / 2.0f - width / 2.0f), Math.round(y + 2.0f), argb,
                true);
        return y;
    }
}

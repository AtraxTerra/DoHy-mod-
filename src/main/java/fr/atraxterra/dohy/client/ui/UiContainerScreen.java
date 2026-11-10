package fr.atraxterra.dohy.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Un ecran de conteneur dessine avec le kit : machines, coffres, sacs.
 *
 * <p>Contrairement a {@link UiScreen}, pas de mise a l'echelle libre : les cases
 * d'objets sont celles du menu, que le jeu place et teste au pixel de
 * l'interface. Une unite du kit vaut donc ici un pixel d'interface, et le
 * panneau se dessine autour des cases, a leurs positions reelles.
 *
 * <p>Le fond (panneau, cadres, jauges) passe avant les objets ; les textes et
 * bulles du premier plan, apres.
 */
public abstract class UiContainerScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {

    protected UiContainerScreen(M menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title, width, height);
    }

    /** Le panneau, les cadres et les jauges, en coordonnees du panneau. */
    protected abstract void drawPanel(UiGraphics g, int mouseX, int mouseY);

    /** Ce qui passe par-dessus les objets ; rien par defaut. */
    protected void drawForeground(UiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level != null) {
            graphics.fill(0, 0, width, height, 0x70000000);
        }
        UiGraphics g = new UiGraphics(graphics, font, mouseX, mouseY);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(getLeftPos(), getTopPos());
        drawPanel(g, mouseX - getLeftPos(), mouseY - getTopPos());
        g.flushOverlays();
        pose.popMatrix();
    }

    /** Le jeu ecrit ici le titre et « Inventaire » en gris fonce ; le kit dessine les siens. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UiGraphics g = new UiGraphics(graphics, font, mouseX, mouseY);
        drawForeground(g, mouseX - getLeftPos(), mouseY - getTopPos());
        g.flushOverlays();
    }

    // -- Aides -----------------------------------------------------------------

    /** Le cadre d'une case, autour de l'objet (16 x 16) place en {@code x, y}. */
    protected static void frame(UiGraphics g, Slot slot, boolean big) {
        if (big) {
            g.slot(slot.x - 5, slot.y - 5, 26, false);
        } else {
            g.slot(slot.x - 1, slot.y - 1, 18, false);
        }
    }

    /** Les cadres des cases du joueur : celles qui suivent les {@code first} cases du conteneur. */
    protected void playerFrames(UiGraphics g, int first) {
        for (int index = first; index < menu.slots.size(); index++) {
            frame(g, menu.slots.get(index), false);
        }
    }

    /** « INVENTAIRE » au-dessus de la premiere case du joueur. */
    protected void inventoryLabel(UiGraphics g, int first) {
        if (first < menu.slots.size()) {
            Slot slot = menu.slots.get(first);
            g.text(Ui.PIXEL, Component.translatable("container.inventory").getString().toUpperCase(Locale.ROOT),
                    slot.x - 1, slot.y - 9, 4.5f, Ui.MUTED);
        }
    }

    /** La case sous la souris, parmi les {@code count} premieres, ou -1. */
    protected int hoveredSlot(int mouseX, int mouseY, int count) {
        for (int index = 0; index < count && index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (mouseX >= slot.x - 1 && mouseX < slot.x + 17 && mouseY >= slot.y - 1 && mouseY < slot.y + 17) {
                return index;
            }
        }
        return -1;
    }

    /** Une bulle si la souris est dans le rectangle. */
    protected static void tip(UiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height,
                              List<String> lines) {
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height && !lines.isEmpty()) {
            List<Component> components = new ArrayList<>();
            for (int index = 0; index < lines.size(); index++) {
                components.add(index == 0 ? Component.literal(lines.get(index)).withColor(0xFFFF55)
                        : Component.literal(lines.get(index)).withColor(0xAAAAAA));
            }
            g.tooltip(components);
        }
    }

    /** Un titre en police de titre, reduit s'il deborde de {@code maxWidth}. */
    protected static void title(UiGraphics g, String text, int x, int y, float size, float maxWidth, int color,
                                int shade) {
        float fitted = size;
        while (fitted > 7 && g.width(Ui.TITLE, text, fitted) > maxWidth) {
            fitted -= 0.5f;
        }
        g.textShaded(Ui.TITLE, text, x, y + (size - fitted), fitted, color, shade, 1.0f);
    }
}

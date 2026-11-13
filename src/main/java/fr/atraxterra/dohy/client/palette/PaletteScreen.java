package fr.atraxterra.dohy.client.palette;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiContainerScreen;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.palette.PaletteFamily;
import fr.atraxterra.dohy.palette.PaletteMenu;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;

/**
 * Le stock d'un groupe de palettes, au style du kit : une rangee de 16 cases
 * par palette, numerotee a gauche ; le compte par famille dans la bande du
 * haut ; la molette ou les fleches de droite font defiler au-dela de six
 * rangees.
 */
public final class PaletteScreen extends UiContainerScreen<PaletteMenu> {

    private static final int KHAKI = 0xFFC9B87A;
    private static final int KHAKI_SHADE = 0xFF6B5F35;
    private static final int ARROW = 8;

    public PaletteScreen(PaletteMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PaletteMenu.PANEL_WIDTH, menu.panelHeight());
    }

    private int arrowX() {
        return PaletteMenu.GRID_X + PaletteMenu.COLUMNS * 18 + 2;
    }

    private int upY() {
        return PaletteMenu.GRID_Y - 1;
    }

    private int downY() {
        return PaletteMenu.GRID_Y + menu.shownRows() * 18 - ARROW - 1;
    }

    @Override
    protected void drawPanel(UiGraphics g, int mouseX, int mouseY) {
        g.panelPlain(0, 0, imageWidth, imageHeight);

        // La bande du haut : le nom et la taille du groupe, puis le compte par famille.
        float countsRight = imageWidth - 9;
        float x = countsRight;
        PaletteFamily[] families = PaletteFamily.values();
        for (int index = families.length - 1; index >= 0; index--) {
            PaletteFamily family = families[index];
            String text = family.label().toUpperCase(Locale.ROOT) + " " + menu.total(family);
            g.right(Ui.PIXEL, text, x, 8, 5, menu.total(family) > 0 ? family.color() : Ui.DISABLED);
            x -= g.width(Ui.PIXEL, text, 5) + 9;
        }
        String name = getTitle().getString().toUpperCase(Locale.ROOT);
        if (menu.rows() > 1) {
            name += "  " + menu.groupW() + " X " + menu.groupD();
        }
        title(g, name, 8, 5, 9, x - 14, KHAKI, KHAKI_SHADE);

        // Les rangees montrees, numerotees.
        for (int row = 0; row < menu.shownRows(); row++) {
            int number = menu.offset() + row + 1;
            if (number <= menu.rows()) {
                g.centered(Ui.PIXEL, Integer.toString(number), 6, PaletteMenu.GRID_Y + row * 18 + 6, 5, Ui.MUTED);
            }
        }
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (slot.isActive()) {
                frame(g, slot, false);
            }
        }
        inventoryLabel(g, PaletteMenu.WINDOW);

        if (menu.scrollable()) {
            boolean canUp = menu.offset() > 0;
            boolean canDown = menu.offset() < menu.rows() - PaletteMenu.VISIBLE;
            arrow(g, arrowX(), upY(), true, canUp, hovered(mouseX, mouseY, upY()));
            arrow(g, arrowX(), downY(), false, canDown, hovered(mouseX, mouseY, downY()));
            // La glissiere : la part montree et sa place dans le groupe.
            int trackTop = upY() + ARROW + 2;
            int trackHeight = downY() - 2 - trackTop;
            g.rect(arrowX() + 3, trackTop, 2, trackHeight, Ui.SCROLL_TRACK);
            int thumb = Math.max(6, trackHeight * PaletteMenu.VISIBLE / menu.rows());
            int span = Math.max(1, menu.rows() - PaletteMenu.VISIBLE);
            int thumbY = trackTop + (trackHeight - thumb) * menu.offset() / span;
            g.rect(arrowX() + 3, thumbY, 2, thumb, Ui.SCROLL_THUMB);
        }
    }

    private boolean hovered(int mouseX, int mouseY, int y) {
        return mouseX >= arrowX() && mouseX < arrowX() + ARROW && mouseY >= y && mouseY < y + ARROW;
    }

    /** Une fleche dans un petit bouton : un chevron de trois traits. */
    private static void arrow(UiGraphics g, int x, int y, boolean up, boolean enabled, boolean hovered) {
        g.button(x, y, ARROW, ARROW, enabled ? Ui.GRAY_BUTTON : Ui.GRAY_BUTTON_DARK, Ui.OUTER, enabled && hovered);
        int color = enabled ? Ui.WHITE : Ui.DISABLED;
        for (int step = 0; step < 3; step++) {
            int width = 2 + step * 2;
            int rowY = up ? y + 2 + step : y + 4 - step;
            g.rect(x + (ARROW - width) / 2, rowY, width, 1, color);
        }
    }

    // -- Defilement ------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menu.scrollable()) {
            int x = (int) Math.floor(event.x()) - getLeftPos();
            int y = (int) Math.floor(event.y()) - getTopPos();
            if (hovered(x, y, upY())) {
                scroll(PaletteMenu.BUTTON_UP);
                return true;
            }
            if (hovered(x, y, downY())) {
                scroll(PaletteMenu.BUTTON_DOWN);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.scrollable() && scrollY != 0.0d) {
            scroll(scrollY > 0.0d ? PaletteMenu.BUTTON_UP : PaletteMenu.BUTTON_DOWN);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void scroll(int button) {
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        if (menu.clickMenuButton(minecraft.player, button)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }
}

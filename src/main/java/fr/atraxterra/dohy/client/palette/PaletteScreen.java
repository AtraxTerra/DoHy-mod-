package fr.atraxterra.dohy.client.palette;

import fr.atraxterra.dohy.client.ui.ContainerStyle;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiContainerScreen;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.palette.DoHyPalette;
import fr.atraxterra.dohy.palette.PaletteFamily;
import fr.atraxterra.dohy.palette.PaletteMenu;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Le stock d'un groupe de palettes, au style des coffres du kit : la fenetre et son
 * en-tete (la palette, son nom, la croix), le compte par famille et la taille du groupe
 * dans la bande du haut, une rangee de 16 cases par palette, numerotee a gauche ; la
 * molette ou les fleches de droite font defiler au-dela de six rangees.
 */
public final class PaletteScreen extends UiContainerScreen<PaletteMenu> {

    private static final int ARROW = 8;
    private final ItemStack icon = new ItemStack(DoHyPalette.PALETTE_ITEM.get());

    public PaletteScreen(PaletteMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PaletteMenu.PANEL_WIDTH, menu.panelHeight());
    }

    @Override
    protected void init() {
        super.init();
        topPos = ContainerStyle.top(height, imageHeight);
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
        Paint p = g.paint();
        ContainerStyle.window(p, imageWidth, imageHeight);
        ContainerStyle.header(g, p, icon, getTitle().getString(), imageWidth, 22);
        ContainerStyle.close(g, p, imageWidth, ContainerStyle.overClose(mouseX, mouseY, imageWidth));

        // La bande du haut : la taille du groupe a gauche, le compte par famille a droite.
        if (menu.rows() > 1) {
            ContainerStyle.label(g, PaletteMenu.GRID_X - 0.5f, 7.0f, "GROUPE", menu.groupW() + " X " + menu.groupD());
        }
        float x = imageWidth - 9.0f;
        PaletteFamily[] families = PaletteFamily.values();
        for (int index = families.length - 1; index >= 0; index--) {
            PaletteFamily family = families[index];
            int total = menu.total(family);
            String count = Integer.toString(total);
            String label = family.label().toUpperCase(Locale.ROOT);
            g.right(Ui.WIDE, count, x, 7.0f, 4.5f, total > 0 ? family.color() : Ui.Theme.DIM);
            x -= g.width(Ui.WIDE, count, 4.5f) + 2.5f;
            g.right(Ui.PIXEL, label, x, 7.8f, 3.3f, total > 0 ? Ui.Theme.TEXT2 : Ui.Theme.DIM);
            x -= g.width(Ui.PIXEL, label, 3.3f) + 8.0f;
        }

        // Les rangees montrees, numerotees.
        for (int row = 0; row < menu.shownRows(); row++) {
            int number = menu.offset() + row + 1;
            if (number <= menu.rows()) {
                g.centered(Ui.PIXEL, Integer.toString(number), 6, PaletteMenu.GRID_Y + row * 18 + 6.5f, 3.3f,
                        Ui.Theme.MUTED);
            }
        }
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (slot.isActive()) {
                ContainerStyle.slot(p, slot);
            }
        }
        if (PaletteMenu.WINDOW < menu.slots.size()) {
            ContainerStyle.inventoryLabel(g, menu.slots.get(PaletteMenu.WINDOW));
        }

        if (menu.scrollable()) {
            boolean canUp = menu.offset() > 0;
            boolean canDown = menu.offset() < menu.rows() - PaletteMenu.VISIBLE;
            arrow(p, arrowX(), upY(), true, canUp, hovered(mouseX, mouseY, upY()));
            arrow(p, arrowX(), downY(), false, canDown, hovered(mouseX, mouseY, downY()));
            // La glissiere : la part montree et sa place dans le groupe.
            int trackTop = upY() + ARROW + 2;
            int trackHeight = downY() - 2 - trackTop;
            p.round(arrowX() + 2.5f, trackTop, 3.0f, trackHeight, 1.5f, 0xFF16171A);
            int thumb = Math.max(6, trackHeight * PaletteMenu.VISIBLE / menu.rows());
            int span = Math.max(1, menu.rows() - PaletteMenu.VISIBLE);
            int thumbY = trackTop + (trackHeight - thumb) * menu.offset() / span;
            p.round(arrowX() + 2.5f, thumbY, 3.0f, thumb, 1.5f, 0xFF5A5D65);
        }
    }

    private boolean hovered(int mouseX, int mouseY, int y) {
        return mouseX >= arrowX() && mouseX < arrowX() + ARROW && mouseY >= y && mouseY < y + ARROW;
    }

    /** Une fleche dans un petit bouton du kit : un chevron de trois traits. */
    private static void arrow(Paint p, int x, int y, boolean up, boolean enabled, boolean hovered) {
        int fill = !enabled ? 0xFF1B1C1F : hovered ? 0xFF34373D : 0xFF222427;
        int edge = !enabled ? 0xFF26282C : hovered ? 0xFF5A5E66 : 0xFF2E3035;
        p.framed(x, y, ARROW, ARROW, 2.5f, fill, edge, 1);
        int color = enabled ? Ui.Theme.TEXT2 : Ui.Theme.DIM;
        for (int step = 0; step < 3; step++) {
            int width = 2 + step * 2;
            int rowY = up ? y + 2 + step : y + 4 - step;
            p.rect(x + (ARROW - width) / 2.0f, rowY, width, 1, color);
        }
    }

    // -- Defilement ------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && ContainerStyle.overClose(event.x() - leftPos, event.y() - topPos, imageWidth)) {
            onClose();
            return true;
        }
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
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return super.hasClickedOutside(mouseX, mouseY, left, top)
                && !ContainerStyle.inHeader(mouseX, mouseY, left, top, imageWidth);
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

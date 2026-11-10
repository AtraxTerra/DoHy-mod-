package fr.atraxterra.dohy.client.ui.widget;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Une grille d'objets qui defile, avec sa barre a droite.
 *
 * <p>La selection est un indice dans la liste affichee ; la grille ne connait
 * que des piles. Molette, clic sur la barre ou glisser de la barre.
 */
public class ItemGrid extends UiElement {

    private final int columns;
    private final int rows;
    private final int cell;
    private final int pitch;
    private final IntConsumer onSelect;
    private List<ItemStack> stacks = List.of();
    private int selected = -1;
    private int firstRow;
    private boolean draggingBar;

    public ItemGrid(int x, int y, int columns, int rows, int cell, int gap, IntConsumer onSelect) {
        super(x, y, columns * (cell + gap) - gap + 6, rows * (cell + gap) - gap);
        this.columns = columns;
        this.rows = rows;
        this.cell = cell;
        this.pitch = cell + gap;
        this.onSelect = onSelect;
    }

    public void setStacks(List<ItemStack> stacks) {
        this.stacks = stacks;
        firstRow = Math.min(firstRow, maxFirstRow());
    }

    public void setSelected(int selected) {
        this.selected = selected;
    }

    private int totalRows() {
        return (stacks.size() + columns - 1) / columns;
    }

    private int maxFirstRow() {
        return Math.max(0, totalRows() - rows);
    }

    private int barX() {
        return x + columns * pitch + 4;
    }

    @Override
    public void render(UiGraphics graphics, double mouseX, double mouseY) {
        ItemStack hoveredStack = ItemStack.EMPTY;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = (firstRow + row) * columns + column;
                int cellX = x + column * pitch;
                int cellY = y + row * pitch;
                boolean hovered = mouseX >= cellX && mouseX < cellX + cell && mouseY >= cellY && mouseY < cellY + cell;
                if (index >= stacks.size()) {
                    graphics.slot(cellX, cellY, cell, false);
                    continue;
                }
                graphics.slot(cellX, cellY, cell, hovered);
                if (index == selected) {
                    graphics.border(cellX, cellY, cell, cell, 1, Ui.RED);
                }
                float itemSize = cell - 8;
                graphics.item(stacks.get(index), cellX + (cell - itemSize) / 2.0f, cellY + (cell - itemSize) / 2.0f,
                        itemSize, false);
                if (hovered) {
                    hoveredStack = stacks.get(index);
                }
            }
        }
        // La barre.
        int barX = barX();
        int track = rows * pitch - (pitch - cell);
        graphics.rect(barX, y, 3, track, Ui.SCROLL_TRACK);
        int thumb = totalRows() <= rows ? track : Math.max(8, track * rows / totalRows());
        int thumbY = maxFirstRow() == 0 ? y : y + (track - thumb) * firstRow / maxFirstRow();
        graphics.rect(barX, thumbY, 3, thumb, Ui.SCROLL_THUMB);
        if (!hoveredStack.isEmpty()) {
            graphics.tooltip(hoveredStack);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (mouseX >= barX() - 1) {
            draggingBar = true;
            scrollTo(mouseY);
            return true;
        }
        int column = (int) ((mouseX - x) / pitch);
        int row = (int) ((mouseY - y) / pitch);
        if ((mouseX - x) % pitch >= cell || (mouseY - y) % pitch >= cell || column >= columns) {
            return true;
        }
        int index = (firstRow + row) * columns + column;
        if (index < stacks.size()) {
            selected = index;
            UiScreen.click(1.2f);
            onSelect.accept(index);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY) {
        if (draggingBar) {
            scrollTo(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased() {
        draggingBar = false;
    }

    private void scrollTo(double mouseY) {
        double ratio = (mouseY - y) / Math.max(1, height);
        firstRow = Math.max(0, Math.min(maxFirstRow(), (int) Math.round(ratio * maxFirstRow())));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        firstRow = Math.max(0, Math.min(maxFirstRow(), firstRow - (int) Math.signum(amount)));
        return true;
    }
}

package fr.atraxterra.dohy.client.ui.widget;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;

import java.util.List;
import java.util.function.IntConsumer;

/** Une liste deroulante : ALL, puis les categories. */
public class Dropdown extends UiElement {

    private final List<String> options;
    private final IntConsumer onSelect;
    private int selected;
    private boolean open;
    private final int rowHeight;

    public Dropdown(int x, int y, int width, int height, List<String> options, int selected, IntConsumer onSelect) {
        super(x, y, width, height);
        this.options = options;
        this.selected = selected;
        this.onSelect = onSelect;
        this.rowHeight = height;
    }

    public int selected() {
        return selected;
    }

    @Override
    public boolean contains(double mouseX, double mouseY) {
        int fullHeight = open ? height + rowHeight * options.size() : height;
        return visible && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + fullHeight;
    }

    @Override
    public void render(UiGraphics graphics, double mouseX, double mouseY) {
        graphics.rect(x, y, width, height, 0xFF5E5E60);
        graphics.rect(x + 1, y + 1, width - 2, height - 2, Ui.FIELD_DARK);
        float size = 3.6f;
        float textY = y + (height - size) / 2.0f - 0.3f;
        graphics.text(Ui.BODY, options.get(selected).toUpperCase(), x + 4, textY, size, Ui.TEXT);
        // Le triangle.
        int tx = x + width - 8;
        int ty = y + height / 2 - 1;
        graphics.rect(tx, ty, 5, 1, Ui.WHITE);
        graphics.rect(tx + 1, ty + 1, 3, 1, Ui.WHITE);
        graphics.rect(tx + 2, ty + 2, 1, 1, Ui.WHITE);
        if (!open) {
            return;
        }
        graphics.overlay(() -> {
            int top = y + height;
            graphics.rect(x, top, width, rowHeight * options.size() + 1, 0xFF5E5E60);
            for (int index = 0; index < options.size(); index++) {
                int rowY = top + index * rowHeight;
                boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + rowHeight;
                graphics.rect(x + 1, rowY, width - 2, rowHeight, hovered ? Ui.SLOT_HOVER : Ui.FIELD_DARK);
                graphics.text(Ui.BODY, options.get(index).toUpperCase(), x + 4,
                        rowY + (rowHeight - size) / 2.0f - 0.3f, size, index == selected ? Ui.RED : Ui.TEXT);
            }
        });
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            open = false;
            return false;
        }
        if (open && mouseY >= y + height) {
            int index = (int) ((mouseY - y - height) / rowHeight);
            if (index >= 0 && index < options.size()) {
                selected = index;
                onSelect.accept(index);
            }
            open = false;
        } else {
            open = !open;
        }
        UiScreen.click(1.1f);
        return true;
    }
}

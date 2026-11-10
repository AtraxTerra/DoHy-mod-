package fr.atraxterra.dohy.client.ui.widget;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;

/**
 * Une liste de lignes qui defile, avec sa barre a droite.
 *
 * <p>La liste ne sait pas ce qu'elle montre : chaque ligne est dessinee par
 * {@link Painter}, qui recoit l'indice et le rectangle.
 */
public class RowList extends UiElement {

    /** Dessine la ligne {@code index} dans le rectangle donne. */
    @FunctionalInterface
    public interface Painter {
        void paint(UiGraphics graphics, int index, int x, int y, int width, int height, boolean hovered);
    }

    private final int rowHeight;
    private final int gap;
    private final Painter painter;
    private int count;
    private int first;
    private boolean draggingBar;

    public RowList(int x, int y, int width, int height, int rowHeight, int gap, Painter painter) {
        super(x, y, width, height);
        this.rowHeight = rowHeight;
        this.gap = gap;
        this.painter = painter;
    }

    public void setCount(int count) {
        this.count = count;
        first = Math.max(0, Math.min(first, maxFirst()));
    }

    public void reset() {
        first = 0;
    }

    private int visibleRows() {
        return Math.max(1, (height + gap) / (rowHeight + gap));
    }

    private int maxFirst() {
        return Math.max(0, count - visibleRows());
    }

    @Override
    public void render(UiGraphics graphics, double mouseX, double mouseY) {
        int rowWidth = width - 6;
        for (int row = 0; row < visibleRows(); row++) {
            int index = first + row;
            if (index >= count) {
                break;
            }
            int rowY = y + row * (rowHeight + gap);
            boolean hovered = mouseX >= x && mouseX < x + rowWidth && mouseY >= rowY && mouseY < rowY + rowHeight;
            painter.paint(graphics, index, x, rowY, rowWidth, rowHeight, hovered);
        }
        int barX = x + width - 3;
        graphics.rect(barX, y, 3, height, Ui.SCROLL_TRACK);
        int thumb = count <= visibleRows() ? height : Math.max(8, height * visibleRows() / count);
        int thumbY = maxFirst() == 0 ? y : y + (height - thumb) * first / maxFirst();
        graphics.rect(barX, thumbY, 3, thumb, Ui.SCROLL_THUMB);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (mouseX >= x + width - 5) {
            draggingBar = true;
            scrollTo(mouseY);
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
        first = Math.max(0, Math.min(maxFirst(), (int) Math.round(ratio * maxFirst())));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        first = Math.max(0, Math.min(maxFirst(), first - (int) Math.signum(amount)));
        return true;
    }
}

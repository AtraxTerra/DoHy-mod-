package fr.atraxterra.dohy.client.ui.widget;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;

/** La croix rouge du coin. */
public class CloseButton extends UiElement {

    private final Runnable action;

    public CloseButton(int x, int y, Runnable action) {
        super(x, y, 10, 10);
        this.action = action;
    }

    @Override
    public void render(UiGraphics graphics, double mouseX, double mouseY) {
        boolean hovered = contains(mouseX, mouseY);
        graphics.rect(x, y + 1, width, height - 1, Ui.RED_DARK);
        graphics.rect(x, y, width, height - 2, hovered ? UiGraphics.lighten(Ui.RED) : Ui.RED);
        graphics.rect(x + 1, y, width - 2, 1, UiGraphics.lighten(Ui.RED));
        graphics.texture(Ui.CLOSE, x + 2, y + 1, 6, 6, 32, 32);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        UiScreen.click(0.8f);
        action.run();
        return true;
    }
}

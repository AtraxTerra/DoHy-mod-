package fr.atraxterra.dohy.client.ui.widget;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import org.lwjgl.glfw.GLFW;

/** Une quantite : le nombre, puis « + » et « - ». Maj ajoute ou retire 16. */
public class QuantityField extends UiElement {

    private final int min;
    private final int max;
    private int value;

    public QuantityField(int x, int y, int width, int height, int min, int max) {
        super(x, y, width, height);
        this.min = min;
        this.max = max;
        this.value = min;
    }

    public int value() {
        return value;
    }

    public void set(int value) {
        this.value = Math.max(min, Math.min(max, value));
    }

    @Override
    public void render(UiGraphics graphics, double mouseX, double mouseY) {
        graphics.border(x, y, width, height, 1, focused() ? Ui.RED : Ui.WHITE);
        graphics.rect(x + 1, y + 1, width - 2, height - 2, Ui.BG);
        float size = 5.0f;
        float textY = y + (height - size) / 2.0f;
        graphics.text(Ui.WIDE, Integer.toString(value), x + 4, textY, size, Ui.WHITE);
        int plusX = x + width - 15;
        int minusX = x + width - 7;
        int center = y + height / 2;
        int plusColor = hovered(mouseX, mouseY, plusX) ? Ui.RED : Ui.WHITE;
        int minusColor = hovered(mouseX, mouseY, minusX) ? Ui.RED : Ui.WHITE;
        graphics.rect(plusX, center, 5, 1, plusColor);
        graphics.rect(plusX + 2, center - 2, 1, 5, plusColor);
        graphics.rect(minusX, center, 5, 1, minusColor);
    }

    private boolean hovered(double mouseX, double mouseY, int buttonX) {
        return mouseX >= buttonX - 2 && mouseX < buttonX + 7 && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        int step = (UiScreen.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 16 : 1;
        if (hovered(mouseX, mouseY, x + width - 15)) {
            set(value == 1 && step == 16 ? 16 : value + step);
            UiScreen.click(1.3f);
        } else if (hovered(mouseX, mouseY, x + width - 7)) {
            set(value - step);
            UiScreen.click(1.1f);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        set(value + (int) Math.signum(amount));
        return true;
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public boolean keyPressed(int key, int modifiers) {
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            value = value / 10;
            if (value < min) {
                value = min;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP) {
            set(value + 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            set(value - 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(String text) {
        if (text.length() == 1 && Character.isDigit(text.charAt(0))) {
            long next = (long) value * 10 + (text.charAt(0) - '0');
            set((int) Math.min(max, next));
        }
        return true;
    }
}

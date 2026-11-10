package fr.atraxterra.dohy.client.ui.widget;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Un champ de recherche : texte, curseur en fin de ligne, bouton loupe. */
public class TextField extends UiElement {

    private final String placeholder;
    private final Consumer<String> onChange;
    private final int maxLength;
    private String value = "";

    public TextField(int x, int y, int width, int height, String placeholder, int maxLength,
                     Consumer<String> onChange) {
        super(x, y, width, height);
        this.placeholder = placeholder;
        this.maxLength = maxLength;
        this.onChange = onChange;
    }

    public String value() {
        return value;
    }

    @Override
    public void render(UiGraphics graphics, double mouseX, double mouseY) {
        int button = height;
        graphics.rect(x, y, width - button, height, focused() ? 0xFF3A3940 : Ui.FIELD);
        graphics.rect(x + width - button, y, button, height, Ui.RED);
        graphics.rect(x + width - button, y + height - 1, button, 1, Ui.RED_DARK);
        int icon = height - 5;
        graphics.texture(Ui.SEARCH, x + width - button + (button - icon) / 2, y + 2, icon, icon, 32, 32);
        float size = 3.6f;
        float textY = y + (height - size) / 2.0f - 0.3f;
        if (value.isEmpty() && !focused()) {
            graphics.text(Ui.BODY, placeholder, x + 3, textY, size, Ui.MUTED);
            return;
        }
        String shown = value;
        float max = width - button - 6;
        while (!shown.isEmpty() && graphics.width(Ui.BODY, shown, size) > max) {
            shown = shown.substring(1);
        }
        graphics.text(Ui.BODY, shown, x + 3, textY, size, Ui.WHITE);
        if (focused() && (System.currentTimeMillis() / 500) % 2 == 0) {
            float caret = x + 3 + graphics.width(Ui.BODY, shown, size) + 0.5f;
            graphics.rect(Math.round(caret), y + 2, 1, height - 4, Ui.WHITE);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (button == 1) {
            set("");
        }
        return true;
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public boolean keyPressed(int key, int modifiers) {
        if (key == GLFW.GLFW_KEY_BACKSPACE && !value.isEmpty()) {
            set((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 ? "" : value.substring(0, value.length() - 1));
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(String text) {
        if (value.length() + text.length() <= maxLength) {
            set(value + text);
        }
        return true;
    }

    private void set(String next) {
        if (!next.equals(value)) {
            value = next;
            onChange.accept(value);
        }
    }
}

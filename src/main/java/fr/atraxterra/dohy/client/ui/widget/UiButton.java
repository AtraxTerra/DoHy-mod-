package fr.atraxterra.dohy.client.ui.widget;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;

/** Un bouton plein, libelle en police large : ACHETER, VENDRE. */
public class UiButton extends UiElement {

    public enum Style {
        RED(Ui.RED, Ui.RED_DARK),
        GREEN(Ui.GREEN, Ui.GREEN_DARK),
        GRAY(Ui.GRAY_BUTTON, Ui.GRAY_BUTTON_DARK);

        final int face;
        final int shade;

        Style(int face, int shade) {
            this.face = face;
            this.shade = shade;
        }
    }

    private String label;
    private final Style style;
    private final Runnable action;
    public boolean enabled = true;
    public float textSize = 5.0f;

    public UiButton(int x, int y, int width, int height, String label, Style style, Runnable action) {
        super(x, y, width, height);
        this.label = label;
        this.style = style;
        this.action = action;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public void render(UiGraphics graphics, double mouseX, double mouseY) {
        boolean hovered = enabled && contains(mouseX, mouseY);
        if (enabled) {
            graphics.button(x, y, width, height, style.face, style.shade, hovered);
        } else {
            graphics.button(x, y, width, height, Ui.GRAY_BUTTON_DARK, 0xFF1A1A1F, false);
        }
        float textY = y + (height - 2 - textSize) / 2.0f;
        graphics.centered(Ui.WIDE, label, x + width / 2.0f, textY, textSize, enabled ? Ui.WHITE : Ui.MUTED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || !contains(mouseX, mouseY) || button != 0) {
            return false;
        }
        UiScreen.click(1.0f);
        action.run();
        return true;
    }
}

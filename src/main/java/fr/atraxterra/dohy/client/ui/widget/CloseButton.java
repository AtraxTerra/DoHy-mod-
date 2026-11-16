package fr.atraxterra.dohy.client.ui.widget;

import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;

/** La croix du coin : un carre sombre arrondi, qui rougit au survol (le style de la coque). */
public class CloseButton extends UiElement {

    private final Runnable action;
    private final Anim.Value hover = new Anim.Value(0.0f, 16.0f);

    public CloseButton(int x, int y, Runnable action) {
        super(x, y, 10, 10);
        this.action = action;
    }

    @Override
    public void render(UiGraphics graphics, double mouseX, double mouseY) {
        float t = hover.toward(contains(mouseX, mouseY) ? 1.0f : 0.0f);
        Paint p = graphics.paint();
        if (t > 0.01f) {
            p.glow(x + width / 2.0f, y + height / 2.0f, width, Paint.alpha(Ui.Theme.RED, 0.35f * t));
        }
        p.framed(x, y, width, height, 2.7f, Paint.mix(0xFF26282C, Ui.Theme.RED, t),
                Paint.mix(0xFF3A3C42, Ui.Theme.RED_LIGHT, t), 1);
        graphics.texture(Ui.CLOSE, x + 2, y + 2, width - 4, height - 4, 32, 32);
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

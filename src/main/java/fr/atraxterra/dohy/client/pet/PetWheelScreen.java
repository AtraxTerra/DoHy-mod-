package fr.atraxterra.dohy.client.pet;

import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.shell.Shell;
import fr.atraxterra.dohy.palamod.pet.PetWheelView;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * La roue du familier (wiki : la touche tenue) : les actives posees en cercle ;
 * la souris vise, relacher la touche (ou cliquer) lance l'active visee. Une
 * active qui se recharge est grisee, avec le temps qui reste.
 */
public class PetWheelScreen extends UiScreen implements ViewScreen<PetWheelView> {

    private static final float CX = Shell.WIDTH / 2.0f;
    private static final float CY = Shell.HEIGHT / 2.0f;
    private static final float RADIUS = 78.0f;
    private static final float BUBBLE = 24.0f;

    private PetWheelView view;
    private long shownAt;
    private int aimed = -1;
    private boolean sent;

    public PetWheelScreen(PetWheelView view) {
        super(Component.literal("Roue du familier"), Shell.WIDTH, Shell.HEIGHT);
        accept(view);
    }

    @Override
    public void accept(PetWheelView next) {
        this.view = next;
        this.shownAt = System.currentTimeMillis();
    }

    @Override
    public Identifier viewId() {
        return PetWheelView.ID;
    }

    @Override
    protected void build() {
    }

    private float angle(int index) {
        int count = Math.max(1, view.entries().size());
        return -Mth.HALF_PI + index * Mth.TWO_PI / count;
    }

    private long left(PetWheelView.Entry entry) {
        return Math.max(0L, entry.cooldownMs() - (System.currentTimeMillis() - shownAt));
    }

    @Override
    public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float partialTick) {
        graphics.fill(0, 0, width, height, 0x50000000);
    }

    @Override
    protected void drawBackground(UiGraphics g, double mx, double my) {
        Paint p = g.paint();
        double dx = mx - CX;
        double dy = my - CY;
        aimed = -1;
        if (dx * dx + dy * dy > 18.0d * 18.0d && !view.entries().isEmpty()) {
            double best = Double.MAX_VALUE;
            for (int index = 0; index < view.entries().size(); index++) {
                double a = angle(index);
                double ex = Math.cos(a) * RADIUS;
                double ey = Math.sin(a) * RADIUS;
                double distance = (ex - dx) * (ex - dx) + (ey - dy) * (ey - dy);
                if (distance < best) {
                    best = distance;
                    aimed = index;
                }
            }
        }
        p.glow(CX, CY, RADIUS + 46.0f, 0x30000000);
        p.dot(CX, CY, 30.0f, 0xE017181B);
        g.texture(FamiliarModels.portrait(view.familiar()), (int) CX - 24, (int) CY - 26, 48, 48, 96, 96);
        for (int index = 0; index < view.entries().size(); index++) {
            PetWheelView.Entry entry = view.entries().get(index);
            float a = angle(index);
            float x = CX + Mth.cos(a) * RADIUS;
            float y = CY + Mth.sin(a) * RADIUS;
            boolean ready = left(entry) <= 0L;
            boolean hover = index == aimed;
            p.dot(x, y, BUBBLE + (hover ? 3.0f : 0.0f), hover ? 0xF0F3445D : 0xE0222427);
            p.dot(x, y, BUBBLE - 2.0f, ready ? 0xF01C1D21 : 0xF0101113);
            g.item(PetScreen.icon(entry.id()), x - 10.0f, y - 14.0f, 20.0f, false);
            g.centered(Ui.WIDE, g.fit(Ui.WIDE, entry.label().toUpperCase(java.util.Locale.ROOT), 3.6f, 54.0f), x,
                    y + 9.0f, 3.6f, ready ? 0xFFFFFFFF : Ui.Theme.DIM);
            if (!ready) {
                g.centered(Ui.WIDE, PetScreen.duration(left(entry)), x, y + 15.0f, 3.6f, 0xFFEF3926);
            } else if (!entry.value().isEmpty()) {
                g.centered(Ui.BODY, entry.value(), x, y + 15.0f, 3.8f, 0xFF30CB1F);
            }
        }
        g.centered(Ui.BODY, "Vise une competence, puis relache la touche (ou clique)", CX, CY + RADIUS + 36.0f, 4.6f,
                Ui.Theme.TEXT2);
    }

    private void fire() {
        if (sent || aimed < 0 || aimed >= view.entries().size()) {
            return;
        }
        sent = true;
        click(1.2f);
        UiClient.send(PetWheelView.ID, "use", view.entries().get(aimed).id(), 0);
        onClose();
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        KeyMapping key = PetClient.wheelKey();
        if (key != null && event.key() == key.getKey().getValue()) {
            if (aimed >= 0) {
                fire();
            } else {
                onClose();
            }
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    protected boolean backgroundClicked(double mx, double my, int button) {
        if (aimed >= 0) {
            fire();
            return true;
        }
        onClose();
        return true;
    }
}

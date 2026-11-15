package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.palamod.fishing.FishingView;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Le mini-jeu de peche : le curseur fait l'aller-retour sur la barre, la
 * barre espace l'arrete. L'icone de rarete et la touche viennent de PalaJobs.
 */
public class FishingScreen extends UiScreen implements ViewScreen<FishingView> {

    private static final int WIDTH = 240;
    private static final int HEIGHT = 112;
    private static final int BAR_X = 20;
    private static final int BAR_Y = 62;
    private static final int BAR_W = 200;
    private static final int BAR_H = 12;
    private static final Identifier SPACE = Identifier.fromNamespaceAndPath("palamod", "textures/gui/fishing/space.png");
    private static final Identifier SPACE_PRESSED =
            Identifier.fromNamespaceAndPath("palamod", "textures/gui/fishing/space_pressed.png");

    private FishingView view;
    private long shownAt;
    private boolean sent;
    private float stoppedAt;

    public FishingScreen(FishingView view) {
        // A l'echelle des menus : le panneau garde sa taille, en bas de l'ecran, et le
        // bouchon reste visible au-dessus.
        super(Component.literal("Peche"), fr.atraxterra.dohy.client.ui.shell.Shell.WIDTH,
                fr.atraxterra.dohy.client.ui.shell.Shell.HEIGHT);
        accept(view);
    }

    private static final float LEFT = (fr.atraxterra.dohy.client.ui.shell.Shell.WIDTH - WIDTH) / 2.0f;
    private static final float TOP = fr.atraxterra.dohy.client.ui.shell.Shell.HEIGHT - HEIGHT - 24.0f;

    @Override
    public void accept(FishingView next) {
        this.view = next;
        this.shownAt = System.currentTimeMillis();
        this.sent = false;
    }

    @Override
    public Identifier viewId() {
        return FishingView.ID;
    }

    @Override
    protected void build() {
    }

    /** Position du curseur, de 0 a 1 : un aller-retour a vitesse constante. */
    private float cursor() {
        if (sent) {
            return stoppedAt;
        }
        double phase = (System.currentTimeMillis() - shownAt) / 1000.0d * view.speed() % 2.0d;
        return (float) (phase <= 1.0d ? phase : 2.0d - phase);
    }

    private float remaining() {
        return Math.max(0f, 1f - (System.currentTimeMillis() - shownAt) / (float) view.durationMs());
    }

    /** Un voile leger : on regarde aussi le bouchon. */
    @Override
    public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float partialTick) {
        graphics.fill(0, 0, width, height, 0x30000000);
    }

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        org.joml.Matrix3x2fStack pose = g.raw().pose();
        pose.pushMatrix();
        pose.translate(LEFT, TOP);
        drawPanel(g);
        pose.popMatrix();
    }

    private void drawPanel(UiGraphics g) {
        Paint p = g.paint();
        int accent = 0xFF000000 | view.color();
        p.shadow(6, 10, WIDTH - 12, HEIGHT - 8, 18, Paint.alpha(0xFF000000, 0.55f));
        p.framed(0, 0, WIDTH, HEIGHT, 7.3f, 0xFF17181B, 0xFF2C2E33, 1);
        g.scissor(0, 0, WIDTH, HEIGHT);
        p.glow(28, 18, 60, Paint.alpha(accent, 0.18f));
        g.endScissor();
        g.scissor(0, 0, WIDTH, 1);
        p.round(0, 0, WIDTH, HEIGHT, 7.3f, accent);
        g.endScissor();
        g.texture(Identifier.fromNamespaceAndPath("palamod", "textures/gui/fishing/icons/" + view.rarity() + ".png"),
                10, 9, 24, 24, 43, 43);
        g.textShaded(Ui.TITLE, view.label(), 40, 11, 9.0f, accent, Paint.mix(accent, 0xFF000000, 0.55f), 0.8f);
        g.text(Ui.BODY, "Espace pour arreter le curseur", 40, 23.5f, 4.7f, Ui.Theme.TEXT2);

        // La legende des zones, en pastilles.
        String[] labels = {"1 poisson", "2 poissons", "Boost", "Rarete +1"};
        int[] colors = {0xFF30CB1F, 0xFFEF3926, 0xFFB45CFF, 0xFFFAC400};
        float x = BAR_X;
        for (int index = 0; index < labels.length; index++) {
            p.dot(x + 1.6f, 47.6f, 1.6f, colors[index]);
            g.text(Ui.BODY, labels[index], x + 5.0f, 46.0f, 3.8f, Ui.Theme.MUTED);
            x += g.width(Ui.BODY, labels[index], 3.8f) + 12.0f;
        }

        // La barre et ses zones, de la plus large a la plus etroite : les petites restent visibles.
        p.framed(BAR_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2, 3.3f, 0xFF101115, 0xFF2A2C31, 1);
        g.scissor(BAR_X, BAR_Y, BAR_W, BAR_H);
        view.zones().stream().sorted((a, b) -> Float.compare(b.width(), a.width())).forEach(zone ->
                p.round(BAR_X + zone.start() * BAR_W, BAR_Y + 1, Math.max(1.0f, zone.width() * BAR_W), BAR_H - 2,
                        1.5f, 0xFF000000 | zone.color()));
        g.endScissor();
        float cursorX = BAR_X + cursor() * BAR_W;
        p.glow(cursorX, BAR_Y + BAR_H / 2.0f, 9.0f, 0x80FFFFFF);
        p.round(cursorX - 1.0f, BAR_Y - 3, 2.0f, BAR_H + 6, 1.0f, 0xFFFFFFFF);

        // Le temps qui reste.
        float timeY = BAR_Y + BAR_H + 6;
        p.round(BAR_X, timeY, BAR_W, 2.0f, 1.0f, 0xFF2A2C31);
        float left = remaining();
        if (left > 0) {
            p.round(BAR_X, timeY, Math.max(2.0f, BAR_W * left), 2.0f, 1.0f, accent);
        }
        g.texture(sent ? SPACE_PRESSED : SPACE, WIDTH / 2 - 24, HEIGHT - 21, 47, 16, 47, 16);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_SPACE) {
            if (!sent) {
                stoppedAt = cursor();
                sent = true;
                UiClient.send(FishingView.ID, "stop", String.valueOf(view.stage()), Math.round(stoppedAt * 1000f));
            }
            return true;
        }
        return super.keyPressed(event);
    }
}

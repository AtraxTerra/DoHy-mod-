package fr.atraxterra.dohy.client.pet;

import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.shell.Shell;
import fr.atraxterra.dohy.palamod.pet.TamingView;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * L'apprivoisement : comme le mini-jeu de peche, le curseur fait l'aller-retour
 * sur la barre et la barre espace l'arrete. Le noir couvre la barre ; le rouge,
 * le jaune et le vert s'emboitent autour d'un point tire au hasard. Le portrait
 * du familier et les points du palier sont a gauche.
 */
public class TamingScreen extends UiScreen implements ViewScreen<TamingView> {

    private static final int WIDTH = 260;
    private static final int HEIGHT = 120;
    private static final int BAR_X = 78;
    private static final int BAR_Y = 70;
    private static final int BAR_W = 168;
    private static final int BAR_H = 12;
    private static final float LEFT = (Shell.WIDTH - WIDTH) / 2.0f;
    private static final float TOP = Shell.HEIGHT - HEIGHT - 24.0f;
    private static final Identifier SPACE = Identifier.fromNamespaceAndPath("palamod", "textures/gui/fishing/space.png");
    private static final Identifier SPACE_PRESSED =
            Identifier.fromNamespaceAndPath("palamod", "textures/gui/fishing/space_pressed.png");
    private static final int ACCENT = 0xFFF3445D;

    private TamingView view;
    private long shownAt;
    private boolean sent;
    private float stoppedAt;

    public TamingScreen(TamingView view) {
        super(Component.literal("Apprivoisement"), Shell.WIDTH, Shell.HEIGHT);
        accept(view);
    }

    @Override
    public void accept(TamingView next) {
        this.view = next;
        this.shownAt = System.currentTimeMillis();
        this.sent = false;
    }

    @Override
    public Identifier viewId() {
        return TamingView.ID;
    }

    @Override
    protected void build() {
    }

    private float cursor() {
        if (sent) {
            return stoppedAt;
        }
        double phase = (System.currentTimeMillis() - shownAt) / 1000.0d * view.speed() % 2.0d;
        return (float) (phase <= 1.0d ? phase : 2.0d - phase);
    }

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
        p.shadow(6, 10, WIDTH - 12, HEIGHT - 8, 18, Paint.alpha(0xFF000000, 0.55f));
        p.framed(0, 0, WIDTH, HEIGHT, 7.3f, 0xFF17181B, 0xFF2C2E33, 1);
        g.scissor(0, 0, WIDTH, 1);
        p.round(0, 0, WIDTH, HEIGHT, 7.3f, ACCENT);
        g.endScissor();
        // le familier
        p.glow(38, 52, 36, Paint.alpha(ACCENT, 0.16f));
        g.texture(FamiliarModels.portrait(view.familiar()), 6, 18, 64, 64, 96, 96);
        g.centered(Ui.WIDE, "PALIER " + view.stage() + "/3", 38, 88, 5.0f, 0xFFFFFFFF);
        // le titre
        g.textShaded(Ui.TITLE, "Apprivoiser " + view.label(), BAR_X, 11, 9.0f, ACCENT, Paint.mix(ACCENT, 0xFF000000,
                0.55f), 0.8f);
        g.text(Ui.BODY, "Espace pour arreter le curseur", BAR_X, 23.5f, 4.7f, Ui.Theme.TEXT2);
        // les points du palier
        float part = Math.max(0.0f, Math.min(1.0f, view.points() / (float) Math.max(1, view.target())));
        p.round(BAR_X, 34, BAR_W, 5, 2.0f, 0xFF2A2C31);
        if (part > 0.0f) {
            p.round(BAR_X, 34, Math.max(3.0f, BAR_W * part), 5, 2.0f, ACCENT);
        }
        g.text(Ui.BODY, view.points() + " / " + view.target() + " points", BAR_X, 42, 4.6f, 0xFFE9E8EE);
        if (view.last() != 0) {
            int color = view.last() > 0 ? 0xFF30CB1F : 0xFFEF3926;
            g.right(Ui.WIDE, (view.last() > 0 ? "+" : "") + view.last(), BAR_X + BAR_W, 41.5f, 5.5f, color);
        }
        // la legende
        String[] labels = {"+50", "+25", "-50", "-150"};
        int[] colors = {0xFF30CB1F, 0xFFFAC400, 0xFFEF3926, 0xFF3A3C42};
        float x = BAR_X;
        for (int index = 0; index < labels.length; index++) {
            p.dot(x + 1.6f, 58.6f, 1.6f, colors[index]);
            g.text(Ui.BODY, labels[index], x + 5.0f, 57.0f, 3.9f, Ui.Theme.MUTED);
            x += g.width(Ui.BODY, labels[index], 3.9f) + 12.0f;
        }
        // la barre : le noir, puis les zones de la plus large a la plus etroite
        p.framed(BAR_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2, 3.3f, 0xFF050506, 0xFF2A2C31, 1);
        g.scissor(BAR_X, BAR_Y, BAR_W, BAR_H);
        view.zones().stream().sorted((a, b) -> Float.compare(b.width(), a.width())).forEach(zone ->
                p.round(BAR_X + zone.start() * BAR_W, BAR_Y + 1, Math.max(1.0f, zone.width() * BAR_W), BAR_H - 2,
                        1.5f, 0xFF000000 | zone.color()));
        g.endScissor();
        float cursorX = BAR_X + cursor() * BAR_W;
        p.glow(cursorX, BAR_Y + BAR_H / 2.0f, 9.0f, 0x80FFFFFF);
        p.round(cursorX - 1.0f, BAR_Y - 3, 2.0f, BAR_H + 6, 1.0f, 0xFFFFFFFF);
        g.texture(sent ? SPACE_PRESSED : SPACE, BAR_X + BAR_W / 2 - 24, HEIGHT - 21, 47, 16, 47, 16);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_SPACE) {
            if (!sent) {
                stoppedAt = cursor();
                sent = true;
                UiClient.send(TamingView.ID, "stop", String.valueOf(view.round()), Math.round(stoppedAt * 1000f));
            }
            return true;
        }
        return super.keyPressed(event);
    }
}

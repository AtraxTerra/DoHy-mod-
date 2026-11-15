package fr.atraxterra.dohy.client.ui.screen;

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
        super(Component.literal("Peche"), WIDTH, HEIGHT);
        accept(view);
    }

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

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        g.panel(0, 0, WIDTH, HEIGHT);
        g.texture(Identifier.fromNamespaceAndPath("palamod", "textures/gui/fishing/icons/" + view.rarity() + ".png"),
                10, 8, 26, 26, 43, 43);
        g.text(Ui.TITLE, view.label(), 42, 12, 7f, view.color());
        g.text(Ui.BODY, "Arrete le curseur sur une zone avec la barre espace", 42, 24, 3.6f, Ui.TEXT);
        g.text(Ui.BODY, "Vert : 1 poisson   Rouge : 2   Violet : Boost   Jaune : rarete +1", 20, 46, 3.4f, Ui.DISABLED);
        g.rect(BAR_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2, 0xFF101015);
        g.rect(BAR_X, BAR_Y, BAR_W, BAR_H, 0xFF2A2A33);
        // Les zones de la plus large a la plus etroite : les petites restent visibles.
        view.zones().stream().sorted((a, b) -> Float.compare(b.width(), a.width())).forEach(zone ->
                g.rect(BAR_X + Math.round(zone.start() * BAR_W), BAR_Y,
                        Math.max(1, Math.round(zone.width() * BAR_W)), BAR_H, zone.color()));
        int x = BAR_X + Math.round(cursor() * BAR_W);
        g.rect(x - 1, BAR_Y - 3, 2, BAR_H + 6, Ui.WHITE);
        g.gauge(BAR_X, BAR_Y + BAR_H + 6, BAR_W, 4, remaining(), view.color());
        g.texture(sent ? SPACE_PRESSED : SPACE, WIDTH / 2 - 24, HEIGHT - 22, 47, 16, 47, 16);
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

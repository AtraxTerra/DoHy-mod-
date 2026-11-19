package fr.atraxterra.dohy.client.lucky;

import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.shell.Shell;
import fr.atraxterra.dohy.palamod.lucky.OneMillionView;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

/**
 * « 1 000 000 joueurs » ({@code GuiOneMillionPlayer}) : la banniere criarde d'un faux
 * gagnant, avec ses confettis, ses coupes et son bouton « RECUPERER ». Dessinee ici
 * (l'image de PalaMod, faite de photos, n'est pas reprise).
 */
public class OneMillionScreen extends UiScreen implements ViewScreen<OneMillionView> {

    private static final float LEFT = 22.0f;
    private static final float TOP = 14.0f;
    private static final float WIDTH = Shell.WIDTH - 2 * LEFT;
    private static final float HEIGHT = Shell.HEIGHT - 2 * TOP;
    private static final float BUTTON_W = 120.0f;
    private static final float BUTTON_H = 26.0f;
    private static final float BUTTON_X = Shell.WIDTH / 2.0f - BUTTON_W / 2.0f;
    private static final float BUTTON_Y = TOP + HEIGHT - 52.0f;
    private static final int[] CONFETTI = {0xFFFF6FB5, 0xFFFFE14D, 0xFF5CE1FF, 0xFFFFFFFF, 0xFFFF9A3C, 0xFFB98CFF};

    private OneMillionView view;
    private boolean claimed;
    private final double opened = Anim.now();
    private final Anim.Value hover = new Anim.Value(0.0f, 16.0f);

    public OneMillionScreen(OneMillionView view) {
        super(Component.literal("1 000 000 joueurs"), Shell.WIDTH, Shell.HEIGHT);
        this.view = view;
    }

    @Override
    public Identifier viewId() {
        return OneMillionView.ID;
    }

    @Override
    public void accept(OneMillionView next) {
        this.view = next;
    }

    @Override
    protected void build() {
    }

    @Override
    protected float lift() {
        return 6.0f * (1.0f - Anim.easeOut(Anim.progress(opened, 0.25d)));
    }

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        Paint p = g.paint();
        long now = System.currentTimeMillis();
        // Le cadre bleu glace, puis le fond vert vif.
        p.shadow(LEFT + 6, TOP + 10, WIDTH - 12, HEIGHT - 8, 18, Paint.alpha(0xFF000000, 0.6f));
        p.framed(LEFT, TOP, WIDTH, HEIGHT, 6.0f, 0xFF7CC9F0, 0xFF2D6E9E, 2);
        p.round(LEFT + 7, TOP + 7, WIDTH - 14, HEIGHT - 14, 3.0f, 0xFF5EE41D);
        confetti(p, now);
        trophy(p, LEFT + 34, TOP + 26);
        trophy(p, LEFT + WIDTH - 84, TOP + HEIGHT - 116);

        float middle = Shell.WIDTH / 2.0f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 160.0d);
        g.textShaded(Ui.TITLE, "VOUS ETES LE", middle - g.width(Ui.TITLE, "VOUS ETES LE", 26.0f) / 2.0f, TOP + 22,
                26.0f, 0xFFE8231A, 0xFF5A0A06, 1.6f);
        p.glow(middle, TOP + 92, 90.0f, Paint.alpha(0xFFFFF27A, 0.25f + 0.2f * pulse));
        g.textShaded(Ui.TITLE, "1 000 000", middle - g.width(Ui.TITLE, "1 000 000", 40.0f) / 2.0f, TOP + 64,
                40.0f, 0xFFFFD34D, 0xFF7A4A00, 2.2f);
        g.textShaded(Ui.TITLE, "JOUEUR !", middle - g.width(Ui.TITLE, "JOUEUR !", 22.0f) / 2.0f, TOP + 112, 22.0f,
                0xFFE8231A, 0xFF5A0A06, 1.4f);
        g.textShaded(Ui.TITLE, "VOUS AVEZ GAGNE UN FULL ENDIUM !!!!",
                middle - g.width(Ui.TITLE, "VOUS AVEZ GAGNE UN FULL ENDIUM !!!!", 17.0f) / 2.0f, TOP + 150, 17.0f,
                0xFF1C4DFF, 0xFF0A1A66, 1.2f);

        boolean over = mouseX >= BUTTON_X && mouseX < BUTTON_X + BUTTON_W && mouseY >= BUTTON_Y
                && mouseY < BUTTON_Y + BUTTON_H;
        float lifted = hover.toward(over ? 1.0f : 0.0f);
        p.shadow(BUTTON_X + 2, BUTTON_Y + 4, BUTTON_W - 4, BUTTON_H - 2, 6, Paint.alpha(0xFF000000, 0.45f));
        p.framed(BUTTON_X, BUTTON_Y - lifted, BUTTON_W, BUTTON_H, 4.0f, Paint.mix(0xFFF4F4F4, 0xFFFFFFFF, lifted),
                0xFF3A3A3A, 2);
        g.centered(Ui.TITLE, "RECUPERER", middle, BUTTON_Y - lifted + 7.5f, 12.0f, 0xFF1A1A1A);
        cursor(p, BUTTON_X - 26, BUTTON_Y + 8 + 2.0f * pulse);
    }

    /** Des confettis qui tombent doucement, toujours les memes. */
    private static void confetti(Paint p, long now) {
        RandomSource random = RandomSource.create(1000000L);
        float drift = (now % 12000L) / 12000.0f;
        for (int index = 0; index < 170; index++) {
            float x = LEFT + 9 + random.nextFloat() * (WIDTH - 22);
            float y = TOP + 9 + ((random.nextFloat() + drift * (0.4f + random.nextFloat())) % 1.0f) * (HEIGHT - 22);
            boolean tall = random.nextBoolean();
            int color = CONFETTI[random.nextInt(CONFETTI.length)];
            p.rect(x, y, tall ? 1.6f : 4.0f, tall ? 4.0f : 1.6f, Paint.alpha(color, 0.85f));
        }
    }

    /** Une coupe doree : la vasque, les anses, le pied et le socle. */
    private static void trophy(Paint p, float x, float y) {
        int gold = 0xFFF2C53D;
        int dark = 0xFF9C7413;
        p.framed(x - 9, y + 6, 12, 18, 6.0f, 0x00000000, dark, 3);
        p.framed(x + 47, y + 6, 12, 18, 6.0f, 0x00000000, dark, 3);
        p.round(x, y, 50, 40, 10.0f, gold);
        p.round(x + 6, y + 4, 10, 30, 5.0f, Paint.alpha(0xFFFFFFFF, 0.45f));
        p.rect(x + 20, y + 40, 10, 14, dark);
        p.round(x + 8, y + 54, 34, 8, 2.0f, 0xFF2B2B2B);
        p.round(x + 4, y + 62, 42, 10, 2.0f, 0xFF1A1A1A);
    }

    /** La main qui montre le bouton, en pixels. */
    private static void cursor(Paint p, float x, float y) {
        int white = 0xFFFFFFFF;
        int black = 0xFF111111;
        p.round(x - 1, y - 1, 22, 12, 3.0f, black);
        p.round(x, y, 20, 10, 2.5f, white);
        p.rect(x + 16, y - 6, 10, 4, black);
        p.rect(x + 16, y - 5, 9, 2, white);
    }

    private void claim() {
        if (!claimed) {
            claimed = true;
            UiClient.send(OneMillionView.ID, "claim", "", 0);
        }
    }

    /** L'aveu part avant le « close » : apres, le serveur aurait oublie la vue. */
    @Override
    public void removed() {
        claim();
        super.removed();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = designX(event.x());
        double y = designY(event.y());
        if (event.button() == 0 && x >= BUTTON_X && x < BUTTON_X + BUTTON_W && y >= BUTTON_Y
                && y < BUTTON_Y + BUTTON_H) {
            UiScreen.click(1.1f);
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_SPACE || event.key() == GLFW.GLFW_KEY_ENTER) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}

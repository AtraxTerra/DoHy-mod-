package fr.atraxterra.dohy.client.lucky;

import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Buttons;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.shell.Shell;
import fr.atraxterra.dohy.palamod.lucky.LuckyBlockView;
import fr.atraxterra.dohy.palamod.lucky.LuckyType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * L'ouverture d'un lucky block ({@code UILuckyBlock}) : la machine a sous de
 * Paladium.
 *
 * <p>Trois rouleaux d'images d'evenements, derriere le cadre rouge de PalaMod
 * ({@code LuckyBlock/minecraft/foreground.png}). « Ouvrir » demande le tirage au
 * serveur ; les rouleaux partent l'un apres l'autre (0,1 s puis 0,6 s d'ecart),
 * filent cinq secondes et s'arretent avec un leger rebond ({@code Back}) sur le
 * meme evenement. Son nom s'affiche ; l'ecran se ferme cinq secondes plus tard,
 * ou tout de suite au clic. L'evenement se produit a la fermeture.
 * « Passer » saute la roulette.
 */
public class LuckyBlockScreen extends UiScreen implements ViewScreen<LuckyBlockView> {

    static final Identifier FOREGROUND = Identifier.fromNamespaceAndPath("palamod", "textures/gui/lucky/foreground.png");
    static final Identifier UNKNOWN = Identifier.fromNamespaceAndPath("palamod", "textures/gui/lucky/unknown.png");
    static final int FOREGROUND_W = 907;
    static final int FOREGROUND_H = 489;
    static final int UNKNOWN_SIZE = 102;

    /** Le panneau, centre dans la maquette des menus. */
    static final int WIDTH = 300;
    static final int HEIGHT = 238;
    static final int LEFT = (Shell.WIDTH - WIDTH) / 2;
    static final int TOP = (Shell.HEIGHT - HEIGHT) / 2;
    /** La machine : le cadre de PalaMod, a ses proportions (1814 x 978). */
    static final int MACHINE_W = 264;
    static final int MACHINE_H = 142;
    static final int MACHINE_X = LEFT + (WIDTH - MACHINE_W) / 2;
    static final int MACHINE_Y = TOP + 34;
    /** Le centre de chaque rouleau, en part de la largeur du cadre. */
    static final float[] COLUMNS = {0.205f, 0.5f, 0.795f};
    static final int ICON = 32;
    static final int PITCH = 40;
    static final int BUTTON_W = 96;
    static final int BUTTON_H = 14;
    static final int BUTTON_X = LEFT + (WIDTH - BUTTON_W) / 2;
    static final int BUTTON_Y = TOP + HEIGHT - BUTTON_H - 9;

    /** Le depart de chaque rouleau et sa course ({@code Thread.sleep(100)}, puis 600 ms d'ecart, 5 s de tween). */
    private static final long FIRST = 100L;
    private static final long GAP = 600L;
    private static final long SPIN = 5000L;
    /** La fermeture automatique, apres l'arret ({@code Thread.sleep(5000)}). */
    private static final long LINGER = 5000L;

    private LuckyBlockView view;
    private final double opened = Anim.now();
    private final Anim.Value hover = new Anim.Value(0.0f, 16.0f);
    private long start = -1L;
    private long stopped = -1L;
    private boolean skipped;
    private boolean requested;
    private boolean used;
    private final int[] lastCell = {-1, -1, -1};
    private final boolean[] landed = new boolean[3];
    private long lastTick;

    public LuckyBlockScreen(LuckyBlockView view) {
        super(Component.literal("Lucky Block"), Shell.WIDTH, Shell.HEIGHT);
        this.view = view;
    }

    @Override
    public Identifier viewId() {
        return LuckyBlockView.ID;
    }

    @Override
    public void accept(LuckyBlockView next) {
        boolean newSpin = next.drawn() && (!view.drawn() || next.spin() != view.spin());
        this.view = next;
        if (newSpin) {
            start = System.currentTimeMillis();
            stopped = -1L;
            skipped = false;
            java.util.Arrays.fill(lastCell, -1);
            java.util.Arrays.fill(landed, false);
        }
    }

    @Override
    protected void build() {
    }

    @Override
    protected float lift() {
        return 5.0f * (1.0f - Anim.easeOut(Anim.progress(opened, 0.22d)));
    }

    // -- Etat --------------------------------------------------------------------

    private LuckyType type() {
        return LuckyType.of(view.type());
    }

    private boolean spinning() {
        return view.drawn() && stopped < 0L;
    }

    private boolean done() {
        return view.drawn() && stopped >= 0L;
    }

    /** L'avance d'un rouleau, en cases, de 0 a {@code stop} (rebond compris). */
    private float offset(int column, long now) {
        if (!view.drawn()) {
            return 0.0f;
        }
        if (skipped || stopped >= 0L) {
            return view.stop();
        }
        long begin = start + FIRST + GAP * column;
        float t = Math.clamp((now - begin) / (float) SPIN, 0.0f, 1.0f);
        return view.stop() * backOut(t);
    }

    /** {@code Back.OUT} de la Universal Tween Engine : on depasse un peu, et on revient. */
    private static float backOut(float t) {
        float s = 1.70158f;
        float u = t - 1.0f;
        return u * u * ((s + 1.0f) * u + s) + 1.0f;
    }

    /** La bande d'un rouleau : la meme pour les trois, decalee, l'evenement a la case d'arret. */
    private String icon(int column, int index) {
        List<String> reel = view.reel();
        if (index == view.stop()) {
            return view.icon();
        }
        if (reel.isEmpty()) {
            return view.icon();
        }
        return reel.get(Math.floorMod(index + column * 7, reel.size()));
    }

    private void skip() {
        if (spinning()) {
            skipped = true;
        }
    }

    // -- Rendu -------------------------------------------------------------------

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        long now = System.currentTimeMillis();
        tick(now);
        Paint p = g.paint();
        int accent = type().color();

        // Le panneau, et la lueur du type en haut a gauche.
        p.shadow(LEFT + 6, TOP + 10, WIDTH - 12, HEIGHT - 8, 18, Paint.alpha(0xFF000000, 0.55f));
        p.framed(LEFT, TOP, WIDTH, HEIGHT, 7.3f, 0xFF17181B, 0xFF2C2E33, 1);
        g.scissor(LEFT, TOP, WIDTH, HEIGHT);
        p.glow(LEFT + 28, TOP + 18, 70, Paint.alpha(accent, 0.20f));
        g.endScissor();
        g.scissor(LEFT, TOP, WIDTH, 1);
        p.round(LEFT, TOP, WIDTH, HEIGHT, 7.3f, accent);
        g.endScissor();

        // L'en-tete : le bloc, « LUCKY BLOCK », le type.
        int tile = Paint.mix(0xFF1A1B1E, accent, 0.18f);
        p.framed(LEFT + 10, TOP + 9, 18, 18, 4.0f, tile, Paint.mix(tile, accent, 0.45f), 1);
        g.item(type().stack(1), LEFT + 11.5f, TOP + 10.5f, 15, false);
        g.textShaded(Ui.TITLE, "LUCKY BLOCK", LEFT + 34, TOP + 9, 9.0f, Ui.Theme.TEXT, 0xFF000000, 0.8f);
        g.text(Ui.WIDE, type().label(), LEFT + 34, TOP + 21, 4.4f, accent);
        // Un evenement vu pour la premiere fois compte des qu'il s'affiche.
        int seen = view.found() + (done() && view.fresh() ? 1 : 0);
        String found = "DECOUVERTS " + seen + " / " + view.total();
        float pill = g.width(Ui.BODY, found, 4.0f) + 10.0f;
        p.framed(LEFT + WIDTH - 10 - pill, TOP + 12, pill, 10, 5.0f, 0xFF18191C, 0xFF2A2C31, 1);
        g.text(Ui.BODY, found, LEFT + WIDTH - 5 - pill, TOP + 15, 4.0f, Ui.Theme.TEXT2);

        drawMachine(g, p, now);
        drawStatus(g, p, now);

        boolean over = mouseX >= BUTTON_X && mouseX < BUTTON_X + BUTTON_W && mouseY >= BUTTON_Y
                && mouseY < BUTTON_Y + BUTTON_H;
        String label = !view.drawn() ? "OUVRIR" : spinning() ? "PASSER" : "FERMER";
        int kind = !view.drawn() ? Buttons.GREEN : spinning() ? Buttons.GHOST : Buttons.RED;
        Buttons.draw(g, p, BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H, label, kind, hover.toward(over ? 1.0f : 0.0f),
                !requested || view.drawn(), 5.0f);
    }

    private void drawMachine(UiGraphics g, Paint p, long now) {
        int accent = type().color();
        float payline = MACHINE_Y + MACHINE_H / 2.0f;
        if (done()) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 180.0d);
            int color = view.bad() ? 0xFFEF3926 : 0xFF2BD15A;
            p.round(MACHINE_X + 14, payline - PITCH / 2.0f, MACHINE_W - 28, PITCH, 6.0f,
                    Paint.alpha(color, 0.12f + 0.08f * pulse));
        }
        g.scissor(MACHINE_X + 10, MACHINE_Y + 3, MACHINE_W - 20, MACHINE_H - 6);
        for (int column = 0; column < 3; column++) {
            float center = MACHINE_X + MACHINE_W * COLUMNS[column];
            if (!view.drawn()) {
                for (int row = -1; row <= 1; row++) {
                    float y = payline + row * PITCH;
                    int alpha = row == 0 ? 255 : 130;
                    g.texture(UNKNOWN, Math.round(center - ICON / 2.0f), Math.round(y - ICON / 2.0f), ICON, ICON,
                            UNKNOWN_SIZE, UNKNOWN_SIZE, (alpha << 24) | 0xFFFFFF);
                }
                continue;
            }
            float offset = offset(column, now);
            int base = (int) Math.floor(offset);
            for (int index = base - 2; index <= base + 3; index++) {
                if (index < 0) {
                    continue;
                }
                float y = payline + (index - offset) * PITCH;
                float distance = Math.abs(y - payline) / (MACHINE_H / 2.0f);
                if (distance > 1.3f) {
                    continue;
                }
                int alpha = Math.round(255 * Math.clamp(1.0f - distance * 0.65f, 0.15f, 1.0f));
                if (done() && index != view.stop()) {
                    alpha = Math.min(alpha, 90);
                }
                Identifier texture = Identifier.fromNamespaceAndPath("palamod",
                        "textures/gui/lucky/events/" + icon(column, index) + ".png");
                g.texture(texture, Math.round(center - ICON / 2.0f), Math.round(y - ICON / 2.0f), ICON, ICON, 16, 16,
                        (alpha << 24) | 0xFFFFFF);
            }
        }
        g.endScissor();
        g.raw().nextStratum();
        g.texture(FOREGROUND, MACHINE_X, MACHINE_Y, MACHINE_W, MACHINE_H, FOREGROUND_W, FOREGROUND_H);
        if (done()) {
            p.glow(MACHINE_X + MACHINE_W / 2.0f, payline, 40.0f, Paint.alpha(accent, 0.10f));
        }
    }

    private void drawStatus(UiGraphics g, Paint p, long now) {
        float middle = LEFT + WIDTH / 2.0f;
        float top = MACHINE_Y + MACHINE_H + 6;
        if (!view.drawn()) {
            g.centered(Ui.WIDE, "CLIQUE SUR OUVRIR POUR TENTER TA CHANCE", middle, top + 4, 4.6f, Ui.Theme.MUTED);
            return;
        }
        if (spinning()) {
            int dots = (int) ((now / 300L) % 4L);
            g.centered(Ui.WIDE, "OUVERTURE EN COURS" + ".".repeat(dots), middle, top + 4, 4.6f, Ui.Theme.MUTED);
            return;
        }
        int color = view.bad() ? 0xFFEF3926 : 0xFF2BD15A;
        String tag = view.bad() ? "MALUS" : "BONUS";
        float tagWidth = g.width(Ui.PIXEL, tag, 3.6f) + 8.0f;
        float freshWidth = view.fresh() ? g.width(Ui.PIXEL, "NOUVEAU", 3.6f) + 8.0f : 0.0f;
        float pills = tagWidth + (view.fresh() ? freshWidth + 4.0f : 0.0f);
        float x = middle - pills / 2.0f;
        p.round(x, top, tagWidth, 7.3f, 3.65f, color);
        g.centered(Ui.PIXEL, tag, x + tagWidth / 2.0f, top + 1.9f, 3.6f, 0xFFFFFFFF);
        if (view.fresh()) {
            float fx = x + tagWidth + 4.0f;
            p.round(fx, top, freshWidth, 7.3f, 3.65f, 0xFFFAC400);
            g.centered(Ui.PIXEL, "NOUVEAU", fx + freshWidth / 2.0f, top + 1.9f, 3.6f, 0xFF2A1E00);
        }
        g.centered(Ui.BOLD, g.fit(Ui.BOLD, view.name(), 8.0f, WIDTH - 24), middle, top + 10, 8.0f, Ui.Theme.TEXT);
        long left = Math.max(0L, (stopped + LINGER - now + 999L) / 1000L);
        g.centered(Ui.BODY, "Fermeture dans " + left + " s, ou clique sur fermer", middle, top + 22, 4.2f,
                Ui.Theme.DIM);
    }

    /** Les bruits de la roulette, l'arret de chaque rouleau, la fin et la fermeture automatique. */
    private void tick(long now) {
        if (!view.drawn()) {
            return;
        }
        if (stopped >= 0L) {
            return;
        }
        boolean all = true;
        for (int column = 0; column < 3; column++) {
            float offset = offset(column, now);
            int cell = Math.round(offset);
            if (cell != lastCell[column]) {
                if (lastCell[column] >= 0 && now - lastTick > 45L && !skipped) {
                    play(SoundEvents.NOTE_BLOCK_HAT.value(), 0.9f + 0.5f * offset / Math.max(1, view.stop()), 0.3f);
                    lastTick = now;
                }
                lastCell[column] = cell;
            }
            boolean finished = skipped || now >= start + FIRST + GAP * column + SPIN;
            if (finished && !landed[column]) {
                landed[column] = true;
                play(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.8f + 0.1f * column, 0.6f);
            }
            all &= finished;
        }
        if (all) {
            stopped = now;
            if (view.bad()) {
                play(SoundEvents.ANVIL_LAND, 0.8f, 0.5f);
            } else {
                play(SoundEvents.PLAYER_LEVELUP, 1.2f, 0.8f);
                play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
            }
        }
    }

    /** La fermeture automatique, hors du rendu. */
    @Override
    public void tick() {
        super.tick();
        if (stopped >= 0L && System.currentTimeMillis() - stopped > LINGER) {
            onClose();
        }
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // -- Entrees -----------------------------------------------------------------

    private void press() {
        if (!view.drawn()) {
            if (!requested) {
                requested = true;
                UiScreen.click(1.1f);
                UiClient.send(LuckyBlockView.ID, "open", "", view.spin());
            }
        } else if (spinning()) {
            UiScreen.click(0.9f);
            skip();
        } else {
            UiScreen.click(0.9f);
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = designX(event.x());
        double y = designY(event.y());
        if (event.button() == 0 && x >= BUTTON_X && x < BUTTON_X + BUTTON_W && y >= BUTTON_Y
                && y < BUTTON_Y + BUTTON_H) {
            press();
            return true;
        }
        if (event.button() == 0 && spinning() && x >= MACHINE_X && x < MACHINE_X + MACHINE_W && y >= MACHINE_Y
                && y < MACHINE_Y + MACHINE_H) {
            skip();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_SPACE || event.key() == GLFW.GLFW_KEY_ENTER) {
            press();
            return true;
        }
        return super.keyPressed(event);
    }

    /**
     * La fermeture ouvre le bloc pour de bon, des que l'evenement est tire :
     * avant la vue de fermeture, sans quoi le serveur l'aurait deja oubliee.
     */
    @Override
    public void removed() {
        if (view.drawn() && !used) {
            used = true;
            UiClient.send(LuckyBlockView.ID, "use", "", 0);
        }
        super.removed();
    }
}

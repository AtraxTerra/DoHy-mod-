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
import fr.atraxterra.dohy.palamod.lucky.LuckyPassView;
import fr.atraxterra.dohy.palamod.lucky.LuckyType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Le Lucky Pass ({@code UILuckyPass}) : la meme machine a sous, chargee de
 * lucky blocks. Un lancer par jour ; une croix rouge marque une case vide.
 */
public class LuckyPassScreen extends UiScreen implements ViewScreen<LuckyPassView> {

    private static final Identifier EMPTY = Identifier.fromNamespaceAndPath("palamod", "textures/gui/lucky/empty.png");
    private static final int EMPTY_SIZE = 98;
    private static final int STOP = 18;
    private static final int CELL = 28;
    private static final long FIRST = 100L;
    private static final long GAP = 600L;
    private static final long SPIN = 4000L;

    private static final int WIDTH = LuckyBlockScreen.WIDTH;
    private static final int HEIGHT = LuckyBlockScreen.HEIGHT;
    private static final int LEFT = LuckyBlockScreen.LEFT;
    private static final int TOP = LuckyBlockScreen.TOP;
    private static final int MACHINE_X = LuckyBlockScreen.MACHINE_X;
    private static final int MACHINE_Y = LuckyBlockScreen.MACHINE_Y;
    private static final int MACHINE_W = LuckyBlockScreen.MACHINE_W;
    private static final int MACHINE_H = LuckyBlockScreen.MACHINE_H;
    private static final int PITCH = LuckyBlockScreen.PITCH;
    private static final int BUTTON_W = LuckyBlockScreen.BUTTON_W;
    private static final int BUTTON_H = LuckyBlockScreen.BUTTON_H;
    private static final int BUTTON_X = LuckyBlockScreen.BUTTON_X;
    private static final int BUTTON_Y = LuckyBlockScreen.BUTTON_Y;

    private LuckyPassView view;
    private final double opened = Anim.now();
    private final Anim.Value hover = new Anim.Value(0.0f, 16.0f);
    private long start = -1L;
    private long stopped = -1L;
    private boolean skipped;
    private boolean requested;
    private final int[] lastCell = {-1, -1, -1};
    private final boolean[] landed = new boolean[3];
    private long lastTick;

    public LuckyPassScreen(LuckyPassView view) {
        super(Component.literal("Lucky Pass"), Shell.WIDTH, Shell.HEIGHT);
        this.view = view;
        if (view.spin() > 0) {
            start = System.currentTimeMillis();
        }
    }

    @Override
    public Identifier viewId() {
        return LuckyPassView.ID;
    }

    @Override
    public void accept(LuckyPassView next) {
        boolean newSpin = next.spin() > 0 && next.spin() != view.spin();
        this.view = next;
        requested = false;
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

    /** Les rouleaux tournent : un lancer vient d'etre fait et ne s'est pas encore arrete. */
    private boolean spinning() {
        return start >= 0L && stopped < 0L;
    }

    private float offset(int column, long now) {
        if (start < 0L) {
            return view.claimed() ? STOP : 0.0f;
        }
        if (skipped || stopped >= 0L) {
            return STOP;
        }
        float t = Math.clamp((now - start - FIRST - GAP * column) / (float) SPIN, 0.0f, 1.0f);
        float s = 1.70158f;
        float u = t - 1.0f;
        return STOP * (u * u * ((s + 1.0f) * u + s) + 1.0f);
    }

    /** La case d'un rouleau : -1 vide, sinon un type. La case d'arret porte le gain. */
    private int cell(int column, int index) {
        if (index == STOP) {
            List<Integer> wins = view.wins();
            return column < wins.size() ? wins.get(column) : -1;
        }
        RandomSource random = RandomSource.create(view.spin() * 977L + column * 131L + index * 17L);
        int roll = random.nextInt(9);
        return roll < 2 ? -1 : roll < 5 ? LuckyType.PALADIUM.ordinal() : roll < 8 ? LuckyType.FINDIUM.ordinal()
                : LuckyType.ENDIUM.ordinal();
    }

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        long now = System.currentTimeMillis();
        tick(now);
        Paint p = g.paint();
        int accent = 0xFF22E558;
        p.shadow(LEFT + 6, TOP + 10, WIDTH - 12, HEIGHT - 8, 18, Paint.alpha(0xFF000000, 0.55f));
        p.framed(LEFT, TOP, WIDTH, HEIGHT, 7.3f, 0xFF17181B, 0xFF2C2E33, 1);
        g.scissor(LEFT, TOP, WIDTH, HEIGHT);
        p.glow(LEFT + 28, TOP + 18, 70, Paint.alpha(accent, 0.18f));
        g.endScissor();
        g.scissor(LEFT, TOP, WIDTH, 1);
        p.round(LEFT, TOP, WIDTH, HEIGHT, 7.3f, accent);
        g.endScissor();

        int tile = Paint.mix(0xFF1A1B1E, accent, 0.18f);
        p.framed(LEFT + 10, TOP + 9, 18, 18, 4.0f, tile, Paint.mix(tile, accent, 0.45f), 1);
        g.item(LuckyType.PALADIUM.stack(1), LEFT + 11.5f, TOP + 10.5f, 15, false);
        g.textShaded(Ui.TITLE, "LUCKY PASS", LEFT + 34, TOP + 9, 9.0f, Ui.Theme.TEXT, 0xFF000000, 0.8f);
        g.text(Ui.WIDE, "UN LANCER GRATUIT PAR JOUR", LEFT + 34, TOP + 21, 4.4f, accent);
        String count = view.spins() + (view.spins() > 1 ? " LANCERS" : " LANCER");
        float pill = g.width(Ui.BODY, count, 4.0f) + 10.0f;
        p.framed(LEFT + WIDTH - 10 - pill, TOP + 12, pill, 10, 5.0f, 0xFF18191C, 0xFF2A2C31, 1);
        g.text(Ui.BODY, count, LEFT + WIDTH - 5 - pill, TOP + 15, 4.0f, Ui.Theme.TEXT2);

        drawMachine(g, p, now);
        drawStatus(g, p, now);

        boolean over = mouseX >= BUTTON_X && mouseX < BUTTON_X + BUTTON_W && mouseY >= BUTTON_Y
                && mouseY < BUTTON_Y + BUTTON_H;
        String label = spinning() ? "PASSER" : view.claimed() ? "FERMER" : "LANCER";
        int kind = spinning() ? Buttons.GHOST : view.claimed() ? Buttons.RED : Buttons.GREEN;
        Buttons.draw(g, p, BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H, label, kind, hover.toward(over ? 1.0f : 0.0f),
                !requested, 5.0f);
    }

    private void drawMachine(UiGraphics g, Paint p, long now) {
        float payline = MACHINE_Y + MACHINE_H / 2.0f;
        boolean done = view.claimed() && !spinning();
        if (done) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 180.0d);
            p.round(MACHINE_X + 14, payline - PITCH / 2.0f, MACHINE_W - 28, PITCH, 6.0f,
                    Paint.alpha(0xFF22E558, 0.12f + 0.08f * pulse));
        }
        g.scissor(MACHINE_X + 10, MACHINE_Y + 3, MACHINE_W - 20, MACHINE_H - 6);
        for (int column = 0; column < 3; column++) {
            float center = MACHINE_X + MACHINE_W * LuckyBlockScreen.COLUMNS[column];
            if (!view.claimed() && start < 0L) {
                for (int row = -1; row <= 1; row++) {
                    float y = payline + row * PITCH;
                    g.texture(LuckyBlockScreen.UNKNOWN, Math.round(center - 16), Math.round(y - 16), 32, 32,
                            LuckyBlockScreen.UNKNOWN_SIZE, LuckyBlockScreen.UNKNOWN_SIZE,
                            ((row == 0 ? 255 : 130) << 24) | 0xFFFFFF);
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
                if (Math.abs(y - payline) > MACHINE_H * 0.65f) {
                    continue;
                }
                int value = cell(column, index);
                if (value < 0) {
                    int alpha = done && index != STOP ? 90 : 230;
                    g.texture(EMPTY, Math.round(center - 13), Math.round(y - 13), 26, 26, EMPTY_SIZE, EMPTY_SIZE,
                            (alpha << 24) | 0xFFFFFF);
                } else {
                    g.item(LuckyType.of(value).stack(1), center - CELL / 2.0f, y - CELL / 2.0f, CELL, false);
                }
            }
        }
        g.endScissor();
        g.raw().nextStratum();
        g.texture(LuckyBlockScreen.FOREGROUND, MACHINE_X, MACHINE_Y, MACHINE_W, MACHINE_H,
                LuckyBlockScreen.FOREGROUND_W, LuckyBlockScreen.FOREGROUND_H);
    }

    private void drawStatus(UiGraphics g, Paint p, long now) {
        float middle = LEFT + WIDTH / 2.0f;
        float top = MACHINE_Y + MACHINE_H + 6;
        if (spinning()) {
            int dots = (int) ((now / 300L) % 4L);
            g.centered(Ui.WIDE, "TIRAGE EN COURS" + ".".repeat(dots), middle, top + 4, 4.6f, Ui.Theme.MUTED);
            return;
        }
        if (!view.claimed()) {
            g.centered(Ui.WIDE, "1, 2 OU 3 LUCKY BLOCKS A GAGNER", middle, top + 4, 4.6f, Ui.Theme.MUTED);
            return;
        }
        List<String> won = new ArrayList<>();
        int[] counts = new int[LuckyType.values().length];
        for (int win : view.wins()) {
            if (win >= 0) {
                counts[LuckyType.of(win).ordinal()]++;
            }
        }
        for (LuckyType type : LuckyType.values()) {
            if (counts[type.ordinal()] > 0) {
                won.add(counts[type.ordinal()] + " " + type.label());
            }
        }
        g.centered(Ui.BOLD, won.isEmpty() ? "Rien cette fois" : "Gagne : " + String.join(" + ", won), middle, top + 2,
                7.0f, Ui.Theme.TEXT);
        g.centered(Ui.BODY, "Prochain lancer dans " + until(view.minutes()), middle, top + 13, 4.4f, Ui.Theme.MUTED);
    }

    /** « 5 h 12 », « 25 min ». */
    private static String until(long minutes) {
        long hours = minutes / 60L;
        return hours > 0 ? hours + " h " + String.format(java.util.Locale.ROOT, "%02d", minutes % 60L)
                : Math.max(1L, minutes) + " min";
    }

    private void tick(long now) {
        if (!spinning()) {
            return;
        }
        boolean all = true;
        for (int column = 0; column < 3; column++) {
            float offset = offset(column, now);
            int cell = Math.round(offset);
            if (cell != lastCell[column]) {
                if (lastCell[column] >= 0 && now - lastTick > 45L && !skipped) {
                    play(SoundEvents.NOTE_BLOCK_HAT.value(), 0.9f + 0.5f * offset / STOP, 0.3f);
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
            play(SoundEvents.PLAYER_LEVELUP, 1.2f, 0.8f);
            play(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0f, 0.6f);
        }
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private void press() {
        if (spinning()) {
            UiScreen.click(0.9f);
            skipped = true;
        } else if (view.claimed()) {
            UiScreen.click(0.9f);
            onClose();
        } else if (!requested) {
            requested = true;
            UiScreen.click(1.1f);
            UiClient.send(LuckyPassView.ID, "spin", "", 0);
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
            skipped = true;
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
}

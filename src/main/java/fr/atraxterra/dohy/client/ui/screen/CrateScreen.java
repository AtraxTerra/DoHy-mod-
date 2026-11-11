package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.widget.CloseButton;
import fr.atraxterra.dohy.client.ui.widget.UiButton;
import fr.atraxterra.dohy.ui.view.CrateView;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * L'ouverture d'une box : une roulette d'objets qui file, ralentit et
 * s'arrete sous le repere, sur le lot que le serveur a deja tire.
 *
 * <p>Chaque case porte la couleur de sa classe (commun, rare, epique,
 * legendaire). A l'arret, la case gagnante s'illumine, les autres s'eteignent,
 * et un son marque la classe du lot. Un clic sur la roulette, ou Espace, saute
 * l'animation.
 */
public class CrateScreen extends UiScreen implements ViewScreen<CrateView> {

    private static final int WIDTH = 360;
    private static final int HEIGHT = 216;
    private static final int REEL_X = 18;
    private static final int REEL_Y = 58;
    private static final int REEL_W = 324;
    private static final int REEL_H = 58;
    /** Le pas d'une case, et la case elle-meme. */
    private static final int PITCH = 34;
    private static final int CELL_W = 30;
    private static final int CELL_H = 46;
    private static final long SPIN_MS = 5200L;

    /** Commun, rare, epique, legendaire. */
    public static final int[] COLORS = {0xFFB4B3BD, 0xFF3D8BFF, 0xFFB45CFF, 0xFFFAC400};
    private static final String[] NAMES = {"COMMUN", "RARE", "ÉPIQUE", "LÉGENDAIRE"};
    private static final String[] ODDS = {"Commun", "Rare", "Épique", "Légendaire"};

    private CrateView view;
    private long start = -1L;
    private float jitter;
    private int lastCell = -1;
    private long lastTick;
    private boolean revealed;
    private UiButton again;

    public CrateScreen(CrateView view) {
        super(Component.literal("Box"), WIDTH, HEIGHT);
        this.view = view;
        restart();
    }

    @Override
    public Identifier viewId() {
        return CrateView.ID;
    }

    @Override
    public void accept(CrateView next) {
        boolean newSpin = next.spin() != view.spin();
        this.view = next;
        if (newSpin) {
            restart();
        }
        refresh();
    }

    /** Relance la roulette. L'arret tombe au hasard dans la case, pas toujours au milieu. */
    private void restart() {
        start = -1L;
        revealed = false;
        lastCell = -1;
        jitter = (new Random(view.spin() * 7919L + view.win()).nextFloat() - 0.5f) * (CELL_W - 8);
    }

    @Override
    protected void build() {
        add(new CloseButton(WIDTH - 26, 16, this::onClose));
        again = add(new UiButton(WIDTH / 2 - 122, 184, 118, 18, "", UiButton.Style.GREEN,
                () -> UiClient.send(CrateView.ID, "again", view.tier(), 0)));
        add(new UiButton(WIDTH / 2 + 4, 184, 118, 18, "BOUTIQUE", UiButton.Style.GRAY,
                () -> UiClient.send(CrateView.ID, "back", "", 0)));
        refresh();
    }

    private void refresh() {
        if (again == null) {
            return;
        }
        if (view.keys() > 0) {
            again.setLabel("ROUVRIR (" + view.keys() + " CLÉ" + (view.keys() > 1 ? "S" : "") + ")");
            again.enabled = revealed;
        } else if (view.affordable()) {
            again.setLabel("CLÉ À " + MoneyFormat.compact(view.price()).toUpperCase(Locale.ROOT));
            again.enabled = revealed;
        } else {
            again.setLabel("PLUS DE CLÉ");
            again.enabled = false;
        }
    }

    // -- Animation -------------------------------------------------------------

    private float progress(long now) {
        if (start < 0) {
            return 0f;
        }
        return Math.min(1f, (now - start) / (float) SPIN_MS);
    }

    /** Lancee vite, puis un long ralenti : la derniere case se fait attendre. */
    private static float ease(float t) {
        float rest = 1f - t;
        return 1f - rest * rest * rest * rest;
    }

    /** Le decalage de la bande : a la fin, la case gagnante est sous le repere. */
    private float offset(float t) {
        float target = view.win() * PITCH + PITCH / 2f - REEL_W / 2f + jitter;
        return target * ease(t);
    }

    private void skip() {
        if (!revealed && start >= 0) {
            start = System.currentTimeMillis() - SPIN_MS;
        }
    }

    private void reveal() {
        revealed = true;
        refresh();
        switch (view.rarity()) {
            case 3 -> {
                play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.9f);
                play(SoundEvents.PLAYER_LEVELUP, 0.8f, 0.6f);
            }
            case 2 -> {
                play(SoundEvents.PLAYER_LEVELUP, 1.0f, 0.8f);
                play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.0f);
            }
            case 1 -> play(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.6f);
            default -> play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.7f);
        }
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // -- Rendu -----------------------------------------------------------------

    @Override
    protected void drawBackground(UiGraphics g, double mouseX, double mouseY) {
        long now = System.currentTimeMillis();
        if (start < 0) {
            start = now;
        }
        float t = progress(now);
        float offset = offset(t);
        if (!revealed) {
            // Un clic a chaque case qui passe sous le repere, plus aigu en fin de course.
            int cell = (int) Math.floor((offset + REEL_W / 2f) / PITCH);
            if (cell != lastCell) {
                if (lastCell >= 0 && now - lastTick > 40) {
                    play(SoundEvents.NOTE_BLOCK_HAT.value(), 0.9f + 0.7f * t, 0.35f);
                    lastTick = now;
                }
                lastCell = cell;
            }
            if (t >= 1f) {
                reveal();
            }
        }

        g.panel(0, 0, WIDTH, HEIGHT);
        g.textShaded(Ui.TITLE, view.title().toUpperCase(Locale.ROOT), 24, 18, 20, Ui.RED, Ui.RED_SHADE, 1.5f);
        String keys = view.keys() + (view.keys() > 1 ? " CLÉS" : " CLÉ");
        g.right(Ui.WIDE, keys, WIDTH - 34, 18, 5, view.keys() > 0 ? Ui.WHITE : Ui.MUTED);
        g.right(Ui.BODY, "Clé : " + MoneyFormat.exact(view.price()), WIDTH - 34, 27, 4.4f, Ui.MUTED);

        drawReel(g, offset, now);
        drawResult(g, now);
        drawOdds(g);
    }

    private void drawReel(UiGraphics g, float offset, long now) {
        g.rect(REEL_X - 2, REEL_Y - 2, REEL_W + 4, REEL_H + 4, Ui.FRAME);
        g.rect(REEL_X, REEL_Y, REEL_W, REEL_H, Ui.INSET);

        int base = (int) Math.floor(offset);
        float fraction = offset - base;
        int cellY = REEL_Y + (REEL_H - CELL_H) / 2;
        List<CrateView.Slot> reel = view.reel();
        g.scissor(REEL_X, REEL_Y, REEL_W, REEL_H);
        Matrix3x2fStack pose = g.raw().pose();
        pose.pushMatrix();
        // Le reste du decalage en sous-unite : la bande glisse sans a-coups au ralenti.
        pose.translate(-fraction, 0f);
        for (int index = 0; index < reel.size(); index++) {
            int x = cellX(index, base);
            if (visible(x)) {
                drawCell(g, reel.get(index), x, cellY, false);
            }
        }
        // Les objets se dessinent au-dessus des aplats de leur strate : ce qui
        // doit les recouvrir (voile, bords estompes, case gagnante) passe dans
        // la suivante.
        g.raw().nextStratum();
        if (revealed) {
            for (int index = 0; index < reel.size(); index++) {
                int x = cellX(index, base);
                if (index != view.win() && visible(x)) {
                    g.rect(x, cellY, CELL_W, CELL_H, 0xB0101014);
                }
            }
            int x = cellX(view.win(), base);
            int rgb = COLORS[rarity()] & 0xFFFFFF;
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 160.0);
            g.rect(x - 4, cellY - 4, CELL_W + 8, CELL_H + 8, (Math.round(0x28 + 0x38 * pulse) << 24) | rgb);
            g.border(x - 2, cellY - 2, CELL_W + 4, CELL_H + 4, 2, (Math.round(0xA0 + 0x5F * pulse) << 24) | rgb);
            drawCell(g, reel.get(view.win()), x, cellY, true);
        }
        pose.popMatrix();

        // Les bords s'estompent dans le fond.
        for (int step = 0; step < 10; step++) {
            int alpha = Math.round(0xE0 * (1f - step / 10f));
            int color = (alpha << 24) | (Ui.INSET & 0xFFFFFF);
            g.rect(REEL_X + step * 2, REEL_Y, 2, REEL_H, color);
            g.rect(REEL_X + REEL_W - (step + 1) * 2, REEL_Y, 2, REEL_H, color);
        }
        g.endScissor();

        // Le repere : un filet et deux pointes, a la couleur du lot une fois arrete.
        int center = REEL_X + REEL_W / 2;
        int marker = revealed ? COLORS[rarity()] : Ui.WHITE;
        if (!revealed) {
            g.rect(center, REEL_Y, 1, REEL_H, 0x55FFFFFF);
        }
        g.rect(center - 3, REEL_Y - 6, 7, 2, marker);
        g.rect(center - 2, REEL_Y - 4, 5, 2, marker);
        g.rect(center - 1, REEL_Y - 2, 3, 2, marker);
        g.rect(center - 1, REEL_Y + REEL_H, 3, 2, marker);
        g.rect(center - 2, REEL_Y + REEL_H + 2, 5, 2, marker);
        g.rect(center - 3, REEL_Y + REEL_H + 4, 7, 2, marker);
    }

    private static int cellX(int index, int base) {
        return REEL_X + index * PITCH - base + (PITCH - CELL_W) / 2;
    }

    private static boolean visible(int x) {
        return x + CELL_W >= REEL_X - 4 && x <= REEL_X + REEL_W + 4;
    }

    private void drawCell(UiGraphics g, CrateView.Slot slot, int x, int y, boolean winner) {
        int color = COLORS[Math.max(0, Math.min(slot.rarity(), COLORS.length - 1))];
        g.rect(x, y, CELL_W, CELL_H, 0xFF1F1E25);
        g.rect(x, y, CELL_W, CELL_H, ((winner ? 0x50 : 0x24) << 24) | (color & 0xFFFFFF));
        g.rect(x, y + CELL_H - 3, CELL_W, 3, color);
        if (slot.icon().isEmpty()) {
            g.texture(Ui.MONEY, x + 5, y + 11, 20, 12, 56, 34);
        } else {
            g.item(slot.icon(), x + 5, y + 7, 20, false);
        }
        g.centered(Ui.PIXEL, g.fit(Ui.PIXEL, slot.label(), 4.5f, CELL_W - 2), x + CELL_W / 2f, y + 32, 4.5f,
                Ui.TEXT);
    }

    private void drawResult(UiGraphics g, long now) {
        float middle = WIDTH / 2f;
        if (!revealed) {
            int dots = (int) ((now / 300L) % 4L);
            g.centered(Ui.WIDE, "OUVERTURE" + ".".repeat(dots), middle, 132, 6, Ui.MUTED);
            g.centered(Ui.BODY, "Clique sur la roulette pour passer", middle, 145, 4.4f, Ui.DISABLED);
            return;
        }
        int color = COLORS[rarity()];
        g.centered(Ui.WIDE, NAMES[rarity()], middle, 126, 6, color);
        g.centered(Ui.BOLD, g.fit(Ui.BOLD, view.prize(), 8, WIDTH - 60), middle, 136, 8, Ui.WHITE);
        List<ItemStack> contents = view.contents();
        if (contents.isEmpty()) {
            if (!view.detail().isEmpty()) {
                g.centered(Ui.BODY, view.detail(), middle, 150, 4.8f, Ui.MUTED);
            }
            return;
        }
        // Un lot groupe : ses objets en ligne, avec leur nombre.
        int size = 16;
        int gap = 3;
        int total = contents.size() * size + (contents.size() - 1) * gap;
        int x = Math.round(middle - total / 2f);
        for (ItemStack stack : contents) {
            g.item(stack, x, 151, size, true);
            x += size + gap;
        }
    }

    private void drawOdds(UiGraphics g) {
        List<Integer> odds = view.odds();
        float size = 4.2f;
        float gap = 9f;
        String[] parts = new String[Math.min(odds.size(), ODDS.length)];
        float total = 0f;
        for (int index = 0; index < parts.length; index++) {
            parts[index] = ODDS[index] + " " + percent(odds.get(index));
            total += g.width(Ui.BODY, parts[index], size) + (index > 0 ? gap : 0f);
        }
        float x = WIDTH / 2f - total / 2f;
        for (int index = 0; index < parts.length; index++) {
            g.text(Ui.BODY, parts[index], x, 205, size, COLORS[index]);
            x += g.width(Ui.BODY, parts[index], size) + gap;
        }
    }

    private static String percent(int permille) {
        if (permille <= 0) {
            return "0 %";
        }
        if (permille >= 10) {
            return Math.round(permille / 10.0f) + " %";
        }
        return "0," + permille + " %";
    }

    private int rarity() {
        return Math.max(0, Math.min(view.rarity(), COLORS.length - 1));
    }

    /** Hover sur un objet d'un lot groupe : sa bulle. */
    @Override
    protected void drawForeground(UiGraphics g, double mouseX, double mouseY) {
        if (!revealed) {
            return;
        }
        List<ItemStack> contents = view.contents();
        int size = 16;
        int gap = 3;
        int total = contents.size() * size + Math.max(0, contents.size() - 1) * gap;
        int x = Math.round(WIDTH / 2f - total / 2f);
        for (ItemStack stack : contents) {
            if (mouseX >= x && mouseX < x + size && mouseY >= 151 && mouseY < 151 + size) {
                g.tooltip(stack);
                return;
            }
            x += size + gap;
        }
        // La case gagnante : la bulle de l'objet.
        CrateView.Slot slot = view.reel().get(view.win());
        float end = offset(1f);
        int base = (int) Math.floor(end);
        double cellX = cellX(view.win(), base) - (end - base);
        int cellY = REEL_Y + (REEL_H - CELL_H) / 2;
        if (contents.isEmpty() && !slot.icon().isEmpty() && mouseX >= cellX && mouseX < cellX + CELL_W
                && mouseY >= cellY && mouseY < cellY + CELL_H) {
            g.tooltip(slot.icon());
        }
    }

    // -- Entrees ---------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = designX(event.x());
        double y = designY(event.y());
        if (!revealed && x >= REEL_X && x < REEL_X + REEL_W && y >= REEL_Y && y < REEL_Y + REEL_H) {
            skip();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!revealed && (event.key() == GLFW.GLFW_KEY_SPACE || event.key() == GLFW.GLFW_KEY_ENTER)) {
            skip();
            return true;
        }
        return super.keyPressed(event);
    }
}

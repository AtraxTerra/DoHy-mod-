package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Buttons;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiClient;
import fr.atraxterra.dohy.client.ui.UiElement;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.UiScreen;
import fr.atraxterra.dohy.client.ui.ViewScreen;
import fr.atraxterra.dohy.client.ui.shell.Shell;
import fr.atraxterra.dohy.ui.view.CrateView;
import fr.atraxterra.dohy.ui.view.MenuView;
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

    private static final int REEL_W = 324;
    private static final int REEL_H = 58;
    /** Le pas d'une case, et la case elle-meme. */
    private static final int PITCH = 34;
    private static final int CELL_W = 30;
    private static final int CELL_H = 46;
    private static final long SPIN_MS = 5200L;

    /** Commun, rare, epique, legendaire. */
    public static final int[] COLORS = {0xFFB4B3BD, 0xFF3D8BFF, 0xFFB45CFF, 0xFFFAC400};
    private static final String[] NAMES = {"COMMUN", "RARE", "EPIQUE", "LEGENDAIRE"};
    private static final String[] ODDS = {"Commun", "Rare", "Epique", "Legendaire"};

    private CrateView view;
    private final Shell shell = new Shell(CrateView.ID);
    private final double opened = Anim.now();
    private long start = -1L;
    private float jitter;
    private int lastCell = -1;
    private long lastTick;
    private boolean revealed;
    /** La roulette, centree dans la zone de contenu de la coque. */
    private int reelX;
    private int reelY;

    public CrateScreen(CrateView view) {
        super(Component.literal("Box"), Shell.WIDTH, Shell.HEIGHT);
        this.view = view;
        header();
        restart();
    }

    private void header() {
        shell.header(view.title(), "", List.of(
                new MenuView.Stat(view.keys() > 1 ? "CLES" : "CLE", Integer.toString(view.keys()),
                        view.keys() > 0 ? 0xFFFFFFFF : 0xFF8B8E95),
                new MenuView.Stat("PRIX DE LA CLE", MoneyFormat.compact(view.price()).toUpperCase(Locale.ROOT),
                        0xFFFAC400)), List.of(), true);
    }

    @Override
    protected float lift() {
        return 5.0f * (1.0f - Anim.easeOut(Anim.progress(opened, 0.22d)));
    }

    @Override
    public Identifier viewId() {
        return CrateView.ID;
    }

    @Override
    public void accept(CrateView next) {
        boolean newSpin = next.spin() != view.spin();
        this.view = next;
        header();
        if (newSpin) {
            restart();
        }
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
        reelX = Math.round(shell.mainLeft() + (shell.mainRight() - shell.mainLeft() - REEL_W) / 2.0f);
        reelY = Math.round(shell.contentTop() + 26.0f);
        add(new Actions());
    }

    private String againLabel() {
        if (view.keys() > 0) {
            return "ROUVRIR (" + view.keys() + " CLE" + (view.keys() > 1 ? "S" : "") + ")";
        }
        if (view.affordable()) {
            return "CLE A " + MoneyFormat.compact(view.price()).toUpperCase(Locale.ROOT);
        }
        return "PLUS DE CLE";
    }

    private boolean againEnabled() {
        return revealed && (view.keys() > 0 || view.affordable());
    }

    /** Les deux boutons sous le resultat : rouvrir, revenir a la boutique. */
    private final class Actions extends UiElement {
        private final Anim.Value againHover = new Anim.Value(0.0f, 16.0f);
        private final Anim.Value backHover = new Anim.Value(0.0f, 16.0f);

        Actions() {
            super(0, 0, 1, 1);
            width = 244;
            height = 17;
            x = reelX + (REEL_W - width) / 2;
            y = reelY + REEL_H + 76;
        }

        @Override
        public void render(UiGraphics g, double mouseX, double mouseY) {
            // Sous le resultat : plus bas quand un lot groupe aligne ses objets.
            y = Math.round(resultTop() + (view.contents().isEmpty() ? 34 : 46));
            float half = (width - 4) / 2.0f;
            boolean overAgain = mouseX >= x && mouseX < x + half && mouseY >= y && mouseY < y + height;
            boolean overBack = mouseX >= x + half + 4 && mouseX < x + width && mouseY >= y && mouseY < y + height;
            Buttons.draw(g, g.paint(), x, y, half, height, againLabel(), Buttons.GREEN,
                    againHover.toward(overAgain ? 1.0f : 0.0f), againEnabled(), 5.0f);
            Buttons.draw(g, g.paint(), x + half + 4, y, half, height, "BOUTIQUE", Buttons.GHOST,
                    backHover.toward(overBack ? 1.0f : 0.0f), true, 5.0f);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY) || button != 0) {
                return false;
            }
            if (mouseX < x + (width - 4) / 2.0f) {
                if (againEnabled()) {
                    UiScreen.click(1.1f);
                    UiClient.send(CrateView.ID, "again", view.tier(), 0);
                }
            } else {
                UiScreen.click(0.9f);
                UiClient.send(CrateView.ID, "back", "", 0);
            }
            return true;
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

        shell.draw(g, g.paint(), mouseX, mouseY);
        drawReel(g, offset, now);
        drawResult(g, now);
        drawOdds(g);
    }

    private void drawReel(UiGraphics g, float offset, long now) {
        Paint p = g.paint();
        p.shadow(reelX + 4, reelY + 6, REEL_W - 8, REEL_H - 4, 12, Paint.alpha(0xFF000000, 0.45f));
        p.framed(reelX - 3, reelY - 3, REEL_W + 6, REEL_H + 6, 7.0f, 0xFF141518, 0xFF2C2E33, 1);

        int base = (int) Math.floor(offset);
        float fraction = offset - base;
        int cellY = reelY + (REEL_H - CELL_H) / 2;
        List<CrateView.Slot> reel = view.reel();
        g.scissor(reelX, reelY, REEL_W, REEL_H);
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
                    p.round(x, cellY, CELL_W, CELL_H, 4.0f, 0xB0101014);
                }
            }
            int x = cellX(view.win(), base);
            int color = COLORS[rarity()];
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 160.0);
            p.glow(x + CELL_W / 2.0f, cellY + CELL_H / 2.0f, CELL_H * 0.9f, Paint.alpha(color, 0.25f + 0.2f * pulse));
            p.round(x - 2, cellY - 2, CELL_W + 4, CELL_H + 4, 5.3f, Paint.alpha(color, 0.75f + 0.25f * pulse));
            drawCell(g, reel.get(view.win()), x, cellY, true);
        }
        pose.popMatrix();

        // Les bords s'estompent dans le fond.
        p.gradientH(reelX, reelY, 22, REEL_H, 0xF0141518, 0x00141518);
        p.gradientH(reelX + REEL_W - 22, reelY, 22, REEL_H, 0x00141518, 0xF0141518);
        g.endScissor();

        // Le repere : un filet lumineux et deux pastilles, a la couleur du lot une fois arrete.
        float center = reelX + REEL_W / 2.0f;
        int marker = revealed ? COLORS[rarity()] : 0xFFFFFFFF;
        if (!revealed) {
            p.rect(center - 0.34f, reelY, 0.67f, REEL_H, 0x66FFFFFF);
        }
        p.glow(center, reelY - 3.0f, 6.0f, Paint.alpha(marker, 0.6f));
        p.round(center - 4.0f, reelY - 5.0f, 8.0f, 3.3f, 1.65f, marker);
        p.glow(center, reelY + REEL_H + 3.0f, 6.0f, Paint.alpha(marker, 0.6f));
        p.round(center - 4.0f, reelY + REEL_H + 1.7f, 8.0f, 3.3f, 1.65f, marker);
    }

    private int cellX(int index, int base) {
        return reelX + index * PITCH - base + (PITCH - CELL_W) / 2;
    }

    private boolean visible(int x) {
        return x + CELL_W >= reelX - 4 && x <= reelX + REEL_W + 4;
    }

    private void drawCell(UiGraphics g, CrateView.Slot slot, int x, int y, boolean winner) {
        Paint p = g.paint();
        int color = COLORS[Math.max(0, Math.min(slot.rarity(), COLORS.length - 1))];
        int fill = Paint.mix(0xFF1F2024, color, winner ? 0.30f : 0.12f);
        p.framed(x, y, CELL_W, CELL_H, 4.0f, fill, Paint.mix(fill, color, 0.5f), 1);
        p.round(x + 4, y + CELL_H - 3.3f, CELL_W - 8, 1.6f, 0.8f, color);
        if (slot.icon().isEmpty()) {
            g.texture(Ui.MONEY, x + 5, y + 11, 20, 12, 56, 34);
        } else {
            g.item(slot.icon(), x + 5, y + 7, 20, false);
        }
        g.centered(Ui.PIXEL, g.fit(Ui.PIXEL, slot.label(), 3.6f, CELL_W - 2), x + CELL_W / 2f, y + 32, 3.6f,
                Ui.Theme.TEXT);
    }

    private float resultTop() {
        return reelY + REEL_H + 14;
    }

    private void drawResult(UiGraphics g, long now) {
        float middle = reelX + REEL_W / 2f;
        float top = resultTop();
        if (!revealed) {
            int dots = (int) ((now / 300L) % 4L);
            g.centered(Ui.WIDE, "OUVERTURE" + ".".repeat(dots), middle, top + 8, 6, Ui.Theme.MUTED);
            g.centered(Ui.BODY, "Clique sur la roulette pour passer", middle, top + 20, 4.7f, Ui.Theme.DIM);
            return;
        }
        int color = COLORS[rarity()];
        Paint p = g.paint();
        float pill = g.width(Ui.PIXEL, NAMES[rarity()], 3.6f) + 8.0f;
        p.glow(middle, top + 3.5f, pill * 0.7f, Paint.alpha(color, 0.35f));
        p.round(middle - pill / 2.0f, top, pill, 7.3f, 3.65f, color);
        g.centered(Ui.PIXEL, NAMES[rarity()], middle, top + 1.9f, 3.6f, rarity() == 3 ? 0xFF2A1E00 : 0xFFFFFFFF);
        g.centered(Ui.BOLD, g.fit(Ui.BOLD, view.prize(), 8, REEL_W - 20), middle, top + 11, 8, Ui.Theme.TEXT);
        List<ItemStack> contents = view.contents();
        if (contents.isEmpty()) {
            if (!view.detail().isEmpty()) {
                g.centered(Ui.BODY, view.detail(), middle, top + 23, 4.8f, Ui.Theme.MUTED);
            }
            return;
        }
        // Un lot groupe : ses objets en ligne, avec leur nombre.
        int size = 16;
        int gap = 3;
        int total = contents.size() * size + (contents.size() - 1) * gap;
        int x = Math.round(middle - total / 2f);
        for (ItemStack stack : contents) {
            g.item(stack, x, Math.round(top + 24), size, true);
            x += size + gap;
        }
    }

    /** La chance de chaque classe, en pastilles sous les boutons. */
    private void drawOdds(UiGraphics g) {
        Paint p = g.paint();
        List<Integer> odds = view.odds();
        float size = 4.0f;
        float gap = 4f;
        int count = Math.min(odds.size(), ODDS.length);
        String[] parts = new String[count];
        float total = 0f;
        for (int index = 0; index < count; index++) {
            parts[index] = ODDS[index] + " " + percent(odds.get(index));
            total += g.width(Ui.BODY, parts[index], size) + 10.0f + (index > 0 ? gap : 0f);
        }
        float x = reelX + REEL_W / 2f - total / 2f;
        float y = resultTop() + (view.contents().isEmpty() ? 34 : 46) + 25;
        for (int index = 0; index < count; index++) {
            float width = g.width(Ui.BODY, parts[index], size) + 10.0f;
            p.framed(x, y, width, 10.0f, 5.0f, 0xFF18191C, Paint.mix(0xFF2A2C31, COLORS[index], 0.5f), 1);
            p.dot(x + 4.0f, y + 5.0f, 1.3f, COLORS[index]);
            g.text(Ui.BODY, parts[index], x + 7.0f, y + 3.0f, size, Ui.Theme.TEXT2);
            x += width + gap;
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
        int x = Math.round(reelX + REEL_W / 2f - total / 2f);
        int itemsY = Math.round(resultTop() + 24);
        for (ItemStack stack : contents) {
            if (mouseX >= x && mouseX < x + size && mouseY >= itemsY && mouseY < itemsY + size) {
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
        int cellY = reelY + (REEL_H - CELL_H) / 2;
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
        if (!revealed && x >= reelX && x < reelX + REEL_W && y >= reelY && y < reelY + REEL_H) {
            skip();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected boolean backgroundClicked(double mouseX, double mouseY, int button) {
        return button == 0 && shell.click(mouseX, mouseY);
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

package fr.atraxterra.dohy.client.botanist;

import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiContainerScreen;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.palamod.botanist.Catalysts;
import fr.atraxterra.dohy.palamod.botanist.CentrifugeBlockEntity;
import fr.atraxterra.dohy.palamod.botanist.UnknownMutationItem;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * L'ecran de la centrifugeuse ({@code UIBotanistCentrifuge} de JobsReborn), au style des machines du
 * kit : la graine au centre d'un hexagone d'objets de centrifugation, la chance de mutation et le
 * bouton « CENTRIFUGER » au milieu, le resultat a droite. En tete, les onglets « CLASSIQUE » et
 * « BOOST » : la centrifugeuse boost est « Bientot disponible » dans JobsReborn aussi.
 *
 * <p>Les textes sont ceux de JobsReborn, sans accents : « Place une graine de mutation », « Ajoute des
 * objets autour de la graine », « Chaque objet pose ajoute sa chance de reussite. », « Reussite : une
 * mutation inconnue. Echec : un dechet de mutation ou ta graine rendue. », « Centrifugation en
 * cours », « Recupere le resultat »...
 */
public final class CentrifugeScreen extends UiContainerScreen<MachineMenu> {

    public static final int WIDTH = 262;
    public static final int HEIGHT = 236;

    private static final int CORE_X = 122;
    private static final int CORE_Y = 40;
    private static final int CORE_W = 64;
    private static final int CORE_H = 62;
    private static final int START_X = 122;
    private static final int START_Y = 112;
    private static final int START_W = 64;
    private static final int START_H = 18;
    private static final int TAB_Y = 12;
    private static final int TAB_W = 42;
    private static final int TAB_H = 13;
    private static final int TAB_CLASSIC_X = 168;
    private static final int TAB_BOOST_X = 212;
    private static final int TITLE_W = 140;

    /** Les couleurs de JobsReborn : le vert de la reussite, le rouge du kit, le violet du boost. */
    private static final int GREEN = 0xFF20FF75;
    private static final int MUTED = 0xFF8C8CA0;
    private static final int WARN = 0xFFFF5A5A;
    private static final int PURPLE = 0xFFB93CFF;
    private static final Identifier DIAMOND = Identifier.fromNamespaceAndPath("palamod",
            "textures/botanist/need/badge.png");
    private static final Identifier DIAMOND_OUTLINE = Identifier.fromNamespaceAndPath("palamod",
            "textures/botanist/need/badge_outline.png");

    public CentrifugeScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int state() {
        return menu.extra(0);
    }

    /** La chance, de 0 a 1 : celle que calcule la machine (sa valeur libre 1, en pour mille). */
    private float chance() {
        return Math.max(0, Math.min(1000, menu.extra(1))) / 1000.0f;
    }

    // -- Le fond -------------------------------------------------------------------------------

    @Override
    protected void drawPanel(UiGraphics g, int mouseX, int mouseY) {
        g.panel(0, 0, WIDTH, HEIGHT);
        title(g, "CENTRIFUGEUSE", 16, 15, 16, TITLE_W, Ui.RED, Ui.RED_SHADE);
        tabs(g, mouseX, mouseY);
        spokes(g);
        Slot seed = menu.slots.get(CentrifugeBlockEntity.SLOT_SEED);
        int tint = chance() >= 1.0f ? Paint.alpha(PURPLE, 0.9f) : 0xFF2C3A33;
        g.raw().blit(RenderPipelines.GUI_TEXTURED, DIAMOND, seed.x - 8, seed.y - 8, 0.0f, 0.0f, 32, 32, 48, 48, 48, 48,
                tint);
        g.raw().blit(RenderPipelines.GUI_TEXTURED, DIAMOND_OUTLINE, seed.x - 8, seed.y - 8, 0.0f, 0.0f, 32, 32, 48, 48,
                48, 48, chance() >= 1.0f ? PURPLE : 0xFF4E6A5C);
        for (int index = CentrifugeBlockEntity.FIRST_ITEM;
             index < CentrifugeBlockEntity.FIRST_ITEM + CentrifugeBlockEntity.ITEMS; index++) {
            frame(g, menu.slots.get(index), false);
        }
        frame(g, menu.slots.get(CentrifugeBlockEntity.SLOT_RESULT), true);
        playerFrames(g, CentrifugeBlockEntity.SIZE);
        inventoryLabel(g, CentrifugeBlockEntity.SIZE);

        g.section(16, 36, "section_craft", "CENTRIFUGATION", 100);
        g.section(194, 36, "section_resources", "RESULTAT", 54);
        core(g);
        startButton(g, mouseX, mouseY);
        result(g);
    }

    /** Les onglets « CLASSIQUE » (actif) et « BOOST » (bientot disponible). */
    private void tabs(UiGraphics g, int mouseX, int mouseY) {
        Paint p = g.paint();
        p.framed(TAB_CLASSIC_X, TAB_Y, TAB_W, TAB_H, 3.0f, 0xFF1A191E, Ui.RED, 1);
        g.centered(Ui.PIXEL, "CLASSIQUE", TAB_CLASSIC_X + TAB_W / 2.0f, TAB_Y + 4, 4.0f, 0xFFFFFFFF);
        boolean over = inside(mouseX, mouseY, TAB_BOOST_X, TAB_Y, TAB_W, TAB_H);
        p.framed(TAB_BOOST_X, TAB_Y, TAB_W, TAB_H, 3.0f, 0xFF121116, over ? Paint.alpha(PURPLE, 0.6f) : 0xFF2A2930, 1);
        g.centered(Ui.PIXEL, "BOOST", TAB_BOOST_X + TAB_W / 2.0f, TAB_Y + 4, 4.0f, 0xFF5E5D66);
    }

    /** Des rayons de la graine vers chaque objet : vert quand l'objet compte, gris sinon. */
    private void spokes(UiGraphics g) {
        Paint p = g.paint();
        Slot seed = menu.slots.get(CentrifugeBlockEntity.SLOT_SEED);
        float cx = seed.x + 8;
        float cy = seed.y + 8;
        for (int index = CentrifugeBlockEntity.FIRST_ITEM;
             index < CentrifugeBlockEntity.FIRST_ITEM + CentrifugeBlockEntity.ITEMS; index++) {
            Slot slot = menu.slots.get(index);
            boolean counts = Catalysts.accepts(slot.getItem());
            int color = counts ? Paint.alpha(GREEN, 0.55f) : 0xFF34333B;
            float tx = slot.x + 8;
            float ty = slot.y + 8;
            int steps = 10;
            for (int step = 2; step < steps - 1; step++) {
                float t = step / (float) steps;
                p.rect(cx + (tx - cx) * t - 0.5f, cy + (ty - cy) * t - 0.5f, 1.5f, 1.5f, color);
            }
        }
    }

    /** Le coeur : la chance de mutation, l'avancement en anneau, la ligne d'etat. */
    private void core(UiGraphics g) {
        Paint p = g.paint();
        boolean boosted = chance() >= 1.0f;
        int accent = boosted ? PURPLE : GREEN;
        p.framed(CORE_X - 2, CORE_Y - 2, CORE_W + 4, CORE_H + 4, 5.0f, 0xFF101A17, boosted ? 0xFF4A2A63 : 0xFF2D4A3F, 1);
        p.roundGradient(CORE_X, CORE_Y, CORE_W, CORE_H, 4.0f, boosted ? 0xFF1E0533 : 0xFF0C1714,
                boosted ? 0xFF2A0A44 : 0xFF102620);
        g.centered(Ui.PIXEL, "CHANCE DE MUTATION", CORE_X + CORE_W / 2.0f, CORE_Y + 5, 3.4f, MUTED);
        String percent = Math.round(chance() * 100.0f) + " %";
        g.centered(Ui.PIXEL, percent, CORE_X + CORE_W / 2.0f, CORE_Y + 15, 9.0f, chance() > 0.0f ? accent : MUTED);
        // L'anneau : seize points autour du coeur, allumes avec l'avancement (ou tournants en attente).
        float cx = CORE_X + CORE_W / 2.0f;
        float cy = CORE_Y + 40;
        boolean running = state() == CentrifugeBlockEntity.STATE_RUNNING;
        float progress = state() == CentrifugeBlockEntity.STATE_TAKE ? 1.0f : running ? menu.progress() : 0.0f;
        int lit = Math.round(progress * 16);
        long time = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        for (int dot = 0; dot < 16; dot++) {
            double angle = Math.PI * 2.0d * dot / 16.0d - Math.PI / 2.0d;
            float x = cx + (float) Math.cos(angle) * 11.0f;
            float y = cy + (float) Math.sin(angle) * 11.0f;
            boolean on = dot < lit || (running && (time / 2 + dot) % 16 == 0);
            p.round(x - 1.5f, y - 1.5f, 3.0f, 3.0f, 1.5f, on ? accent : 0xFF2A2B30);
        }
        String line;
        int color;
        switch (state()) {
            case CentrifugeBlockEntity.STATE_RUNNING -> {
                line = "EN COURS";
                color = accent;
            }
            case CentrifugeBlockEntity.STATE_TAKE -> {
                line = "TERMINE";
                color = GREEN;
            }
            case CentrifugeBlockEntity.STATE_READY -> {
                line = boosted ? "BOOSTEE" : "PRETE";
                color = accent;
            }
            case CentrifugeBlockEntity.STATE_NO_ITEM -> {
                line = "AJOUTE DES OBJETS";
                color = WARN;
            }
            default -> {
                line = "EN ATTENTE";
                color = MUTED;
            }
        }
        g.centered(Ui.PIXEL, line, cx, CORE_Y + CORE_H - 9, 3.6f, color);
    }

    /** « CENTRIFUGER » : rouge du kit quand tout est pret (violet a 100 %), gris sinon. */
    private void startButton(UiGraphics g, int mouseX, int mouseY) {
        boolean ready = state() == CentrifugeBlockEntity.STATE_READY;
        boolean hovered = ready && inside(mouseX, mouseY, START_X, START_Y, START_W, START_H);
        if (ready) {
            boolean boosted = chance() >= 1.0f;
            g.button(START_X, START_Y, START_W, START_H, boosted ? 0xFF9E0AF3 : Ui.RED, boosted ? 0xFF6A06A3 : 0xFFB91C0C,
                    hovered);
        } else {
            g.button(START_X, START_Y, START_W, START_H, Ui.GRAY_BUTTON, Ui.GRAY_BUTTON_DARK, false);
        }
        String label = state() == CentrifugeBlockEntity.STATE_RUNNING ? "EN COURS..." : "CENTRIFUGER";
        g.centered(Ui.PIXEL, label, START_X + START_W / 2.0f, START_Y + 6, 5.0f, ready ? Ui.WHITE : 0xFFB5B4BD);
    }

    /** Le resultat : une mutation inconnue, un dechet, la graine rendue, ou rien encore. */
    private void result(UiGraphics g) {
        ItemStack output = menu.slots.get(CentrifugeBlockEntity.SLOT_RESULT).getItem();
        float x = 196;
        float y = 104;
        if (output.getItem() instanceof UnknownMutationItem) {
            g.text(Ui.BOLD, "Reussite", x, y, 6.0f, GREEN);
            g.wrapped(Ui.BODY, "Une mutation inconnue, a reveler au scanner.", x, y + 9, 4.6f, 54, 6.2f,
                    Ui.Theme.TEXT2, 3);
        } else if (!output.isEmpty()) {
            boolean waste = output.getItem() == fr.atraxterra.dohy.palamod.botanist.PalaBotanist.MUTATION_WASTE.get();
            g.text(Ui.BOLD, "Echec", x, y, 6.0f, WARN);
            g.wrapped(Ui.BODY, waste ? "Un dechet de mutation." : "Ta graine est rendue.", x, y + 9, 4.6f, 54, 6.2f,
                    Ui.Theme.TEXT2, 3);
        } else {
            g.text(Ui.BODY, "Aucun resultat", x, y, 5.0f, MUTED);
        }
    }

    // -- Le premier plan : les bulles ------------------------------------------------------------

    @Override
    protected void drawForeground(UiGraphics g, int mouseX, int mouseY) {
        int hovered = hoveredSlot(mouseX, mouseY, CentrifugeBlockEntity.SIZE);
        if (hovered >= 0 && !menu.slots.get(hovered).hasItem()) {
            List<String> hint;
            if (hovered == CentrifugeBlockEntity.SLOT_SEED) {
                hint = List.of("Graine a centrifuger", "Place une graine de mutation : une centrifugation",
                        "reussie la change en mutation inconnue.");
            } else if (hovered == CentrifugeBlockEntity.SLOT_RESULT) {
                hint = List.of("Resultat", "Reussite : une mutation inconnue.",
                        "Echec : un dechet de mutation ou ta graine rendue.");
            } else {
                hint = List.of("Objet de centrifugation", "Ajoute des objets autour de la graine :",
                        "chaque objet pose ajoute sa chance de reussite.");
            }
            tip(g, mouseX, mouseY, mouseX, mouseY, 1, 1, hint);
        }
        tip(g, mouseX, mouseY, CORE_X, CORE_Y, CORE_W, CORE_H, accepted());
        if (state() == CentrifugeBlockEntity.STATE_READY) {
            tip(g, mouseX, mouseY, START_X, START_Y, START_W, START_H, List.of("Centrifuger",
                    "Chance de reussite : " + Math.round(chance() * 100.0f) + " %",
                    "Reussite : une mutation inconnue.", "Echec : un dechet de mutation ou ta graine rendue."));
        } else {
            tip(g, mouseX, mouseY, START_X, START_Y, START_W, START_H, List.of(statusLine()));
        }
        tip(g, mouseX, mouseY, TAB_BOOST_X, TAB_Y, TAB_W, TAB_H, List.of("Centrifugeuse boost", "Bientot disponible"));
        tip(g, mouseX, mouseY, TAB_CLASSIC_X, TAB_Y, TAB_W, TAB_H, List.of("Centrifugeuse",
                "La centrifugation classique."));
    }

    /** La bulle « Objets acceptes » : chaque objet et la chance qu'il ajoute. */
    private static List<String> accepted() {
        List<String> lines = new ArrayList<>();
        lines.add("Objets acceptes");
        lines.add("Chaque objet pose ajoute sa chance de reussite.");
        for (Catalysts.Entry entry : Catalysts.all()) {
            String name = new ItemStack(entry.item()).getHoverName().getString();
            String chance = entry.chance() * 100.0d == Math.rint(entry.chance() * 100.0d)
                    ? Long.toString(Math.round(entry.chance() * 100.0d))
                    : String.format(Locale.ROOT, "%.1f", entry.chance() * 100.0d);
            lines.add("+" + chance + " % : " + name);
        }
        lines.add("100 % : centrifugation boostee, reussite sure.");
        return lines;
    }

    private String statusLine() {
        return switch (state()) {
            case CentrifugeBlockEntity.STATE_RUNNING -> "Centrifugation en cours";
            case CentrifugeBlockEntity.STATE_TAKE -> "Recupere le resultat";
            case CentrifugeBlockEntity.STATE_NO_ITEM -> "Ajoute des objets autour de la graine";
            default -> "Place une graine de mutation";
        };
    }

    // -- Les clics -----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x() - getLeftPos();
        double y = event.y() - getTopPos();
        if (event.button() == 0 && minecraft != null && minecraft.gameMode != null
                && state() == CentrifugeBlockEntity.STATE_READY && inside(x, y, START_X, START_Y, START_W, START_H)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CentrifugeBlockEntity.BUTTON_START);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}

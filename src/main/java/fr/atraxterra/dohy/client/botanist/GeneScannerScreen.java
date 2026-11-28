package fr.atraxterra.dohy.client.botanist;

import fr.atraxterra.dohy.client.ui.ContainerStyle;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiContainerScreen;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.palamod.botanist.GeneScannerBlockEntity;
import fr.atraxterra.dohy.palamod.botanist.Mutation;
import fr.atraxterra.dohy.palamod.botanist.MutationSeedItem;
import fr.atraxterra.dohy.palamod.botanist.UnknownMutationItem;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * L'ecran du scanner de Genis ({@code UIBotanistScanner} de JobsReborn), au style des machines du
 * kit : la graine a analyser et le carburant a gauche, la chambre du scan au milieu (le laser,
 * l'avancement, l'etat), le resultat a droite ; en tete, le codex (decouvertes, ce qui reste a
 * decouvrir a son niveau, le prochain deblocage) et son bouton.
 *
 * <p>Les textes sont ceux de JobsReborn, sans accents : « Place une graine a analyser »,
 * « Mutation deja dans ton codex », « Ajoute du carburant », « Recupere le resultat », « Pret a
 * scanner », « Scan en cours », « LANCER LE SCAN », « SCAN TERMINE »...
 */
public final class GeneScannerScreen extends UiContainerScreen<MachineMenu> {

    public static final int WIDTH = 262;
    public static final int HEIGHT = 236;

    private static final int CHAMBER_X = 100;
    private static final int CHAMBER_Y = 40;
    private static final int CHAMBER_W = 66;
    private static final int CHAMBER_H = 64;
    private static final int SCAN_X = 100;
    private static final int SCAN_Y = 118;
    private static final int SCAN_W = 66;
    private static final int SCAN_H = 18;
    private static final int CODEX_X = 186;
    private static final int CODEX_Y = 10;
    private static final int CODEX_W = 60;
    private static final int CODEX_H = 20;
    /** Le titre s'arrete la : l'etat du codex tient entre lui et le bouton. */
    private static final int TITLE_W = 86;

    /** Le vert du laser de JobsReborn ({@code #00FF6A}) et le fond de sa chambre ({@code #0C1714}). */
    private static final int LASER = 0xFF00FF6A;
    private static final int CHAMBER = 0xFF0C1714;
    private static final int GREEN = 0xFF20FF75;
    private static final int AMBER = 0xFFFFB020;
    private static final int WARN = 0xFFFF5A5A;
    private static final Identifier DIAMOND = Identifier.fromNamespaceAndPath("palamod",
            "textures/botanist/need/badge.png");

    public GeneScannerScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int state() {
        return menu.extra(0);
    }

    private boolean discovery() {
        return menu.extra(1) == 1;
    }

    // -- Le fond -------------------------------------------------------------------------------

    @Override
    protected void drawPanel(UiGraphics g, int mouseX, int mouseY) {
        g.panel(0, 0, WIDTH, HEIGHT);
        title(g, "SCANNER", 16, 15, 16, TITLE_W, Ui.RED, Ui.RED_SHADE);
        codexHeader(g, mouseX, mouseY);
        for (int index = 0; index < GeneScannerBlockEntity.SIZE; index++) {
            frame(g, menu.slots.get(index), index != GeneScannerBlockEntity.SLOT_FUEL);
        }
        playerFrames(g, GeneScannerBlockEntity.SIZE);
        inventoryLabel(g, GeneScannerBlockEntity.SIZE);

        g.section(16, 38, "section_resources", "GRAINE", 76);
        g.section(16, 92, "section_fuel", "CARBURANT", 76);
        g.text(Ui.PIXEL, "1 CHARBON", 62, 114, 4.0f, Ui.MUTED);
        g.text(Ui.PIXEL, "PAR SCAN", 62, 120, 4.0f, Ui.MUTED);
        g.section(176, 38, "section_craft", "RESULTAT", 70);

        chamber(g);
        scanButton(g, mouseX, mouseY);
        result(g);
    }

    /** En tete a droite : le codex, ses decouvertes, et le bouton qui l'ouvre. */
    private void codexHeader(UiGraphics g, int mouseX, int mouseY) {
        int found = ClientCodex.count();
        int total = Mutation.values().length;
        boolean hovered = inside(mouseX, mouseY, CODEX_X, CODEX_Y, CODEX_W, CODEX_H);
        g.button(CODEX_X, CODEX_Y, CODEX_W, CODEX_H, 0xFF3C8D2F, 0xFF22561A, hovered);
        g.centered(Ui.PIXEL, "LE CODEX", CODEX_X + CODEX_W / 2.0f, CODEX_Y + 4, 5.0f, Ui.WHITE);
        g.centered(Ui.PIXEL, found + " / " + total, CODEX_X + CODEX_W / 2.0f, CODEX_Y + 11, 4.0f, 0xFFD9F5D0);
        // L'etat du codex (« A decouvrir a ton niveau », « Prochain deblocage » de JobsReborn), en
        // court : il tient entre le titre et le bouton ; la bulle du bouton le dit en entier.
        int left = ClientCodex.toDiscover();
        int next = ClientCodex.nextUnlock();
        String status;
        int color;
        if (left > 0) {
            status = left + " A DECOUVRIR";
            color = GREEN;
        } else if (next > 0) {
            status = "RIEN A DECOUVRIR";
            color = AMBER;
        } else {
            status = "CODEX COMPLET";
            color = GREEN;
        }
        float room = CODEX_X - 6 - (16 + TITLE_W + 6);
        g.right(Ui.PIXEL, g.fit(Ui.PIXEL, status, 4.0f, room), CODEX_X - 6, 13, 4.0f, color);
        if (next > 0) {
            g.right(Ui.PIXEL, g.fit(Ui.PIXEL, "DEBLOCAGE NIV. " + next, 3.6f, room), CODEX_X - 6, 20, 3.6f,
                    Ui.MUTED);
        }
    }

    /** La chambre du scan : le losange, le laser qui balaie pendant le scan, l'avancement, l'etat. */
    private void chamber(UiGraphics g) {
        Paint p = g.paint();
        p.framed(CHAMBER_X - 2, CHAMBER_Y - 2, CHAMBER_W + 4, CHAMBER_H + 4, 5.0f, 0xFF101A17, 0xFF2D4A3F, 1);
        p.roundGradient(CHAMBER_X, CHAMBER_Y, CHAMBER_W, CHAMBER_H, 4.0f, CHAMBER, 0xFF102620);
        int state = state();
        boolean scanning = state == GeneScannerBlockEntity.STATE_SCANNING;
        float progress = menu.progress();
        float cx = CHAMBER_X + CHAMBER_W / 2.0f;
        float cy = CHAMBER_Y + 24.0f;
        // Le losange du kit du botaniste, au centre, et la graine dedans.
        g.raw().blit(RenderPipelines.GUI_TEXTURED, DIAMOND, Math.round(cx - 14), Math.round(cy - 14), 0.0f, 0.0f,
                28, 28, 48, 48, 48, 48, scanning ? Paint.alpha(LASER, 0.9f) : 0xFF3D5A50);
        ItemStack input = menu.slots.get(GeneScannerBlockEntity.SLOT_INPUT).getItem();
        if (!input.isEmpty()) {
            g.item(input, cx - 8, cy - 8, 16, false);
        }
        if (scanning && minecraft != null && minecraft.level != null) {
            // Le laser balaie la chambre de haut en bas, deux fois par seconde.
            float phase = (minecraft.level.getGameTime() % 40L) / 40.0f;
            float sweep = phase < 0.5f ? phase * 2.0f : 2.0f - phase * 2.0f;
            float ly = CHAMBER_Y + 6 + sweep * 36.0f;
            p.glow(cx, ly, 26.0f, Paint.alpha(LASER, 0.18f));
            p.rect(CHAMBER_X + 6, ly, CHAMBER_W - 12, 1, Paint.alpha(LASER, 0.85f));
        }
        String percent = Math.round(progress * 100.0f) + "%";
        String line;
        int color;
        switch (state) {
            case GeneScannerBlockEntity.STATE_SCANNING -> {
                line = discovery() ? "DECOUVERTE..." : "ANALYSE EN COURS";
                color = LASER;
            }
            case GeneScannerBlockEntity.STATE_TAKE -> {
                line = "SCAN TERMINE";
                color = GREEN;
            }
            case GeneScannerBlockEntity.STATE_READY -> {
                line = "PRET A SCANNER";
                color = GREEN;
            }
            case GeneScannerBlockEntity.STATE_NO_FUEL -> {
                line = "AJOUTE DU CARBURANT";
                color = WARN;
            }
            case GeneScannerBlockEntity.STATE_KNOWN -> {
                line = "DEJA DANS TON CODEX";
                color = AMBER;
            }
            default -> {
                line = "EN ATTENTE";
                color = Ui.MUTED;
            }
        }
        g.centered(Ui.PIXEL, scanning ? percent : state == GeneScannerBlockEntity.STATE_TAKE ? "100%" : "-",
                cx, CHAMBER_Y + 44, 7.0f, scanning || state == GeneScannerBlockEntity.STATE_TAKE ? LASER : Ui.MUTED);
        g.centered(Ui.PIXEL, line, cx, CHAMBER_Y + 55, 4.0f, color);
        // La barre d'avancement sous la chambre.
        p.round(CHAMBER_X, CHAMBER_Y + CHAMBER_H + 6, CHAMBER_W, 4, 2.0f, 0xFF2A2B30);
        float fill = state == GeneScannerBlockEntity.STATE_TAKE ? 1.0f : scanning ? progress : 0.0f;
        if (fill > 0.0f) {
            p.round(CHAMBER_X, CHAMBER_Y + CHAMBER_H + 6, Math.max(4.0f, CHAMBER_W * fill), 4, 2.0f, LASER);
        }
    }

    /** « LANCER LE SCAN » : rouge du kit quand tout est pret, gris sinon. */
    private void scanButton(UiGraphics g, int mouseX, int mouseY) {
        boolean ready = state() == GeneScannerBlockEntity.STATE_READY;
        boolean hovered = ready && inside(mouseX, mouseY, SCAN_X, SCAN_Y, SCAN_W, SCAN_H);
        if (ready) {
            g.button(SCAN_X, SCAN_Y, SCAN_W, SCAN_H, Ui.RED, 0xFFB91C0C, hovered);
        } else {
            g.button(SCAN_X, SCAN_Y, SCAN_W, SCAN_H, Ui.GRAY_BUTTON, Ui.GRAY_BUTTON_DARK, false);
        }
        String label = state() == GeneScannerBlockEntity.STATE_SCANNING ? "SCAN EN COURS" : "LANCER LE SCAN";
        g.centered(Ui.PIXEL, label, SCAN_X + SCAN_W / 2.0f, SCAN_Y + 6, 5.0f, ready ? Ui.WHITE : 0xFFB5B4BD);
    }

    /** Le resultat : la plante revelee (sa famille, sa variete), un dechet, ou rien encore. */
    private void result(UiGraphics g) {
        ItemStack output = menu.slots.get(GeneScannerBlockEntity.SLOT_OUTPUT).getItem();
        float x = 176;
        float max = 70;
        if (output.getItem() instanceof MutationSeedItem seed) {
            Mutation mutation = seed.mutation();
            g.text(Ui.BOLD, g.fit(Ui.BOLD, ContainerStyle.plain(mutation.label()), 6.5f, max), x, 92, 6.5f,
                    mutation.rarity().color());
            g.text(Ui.PIXEL, "FAMILLE", x, 102, 4.0f, Ui.MUTED);
            g.text(Ui.BODY, g.fit(Ui.BODY, mutation.sheet().family(), 5.0f, max - 30), x + 30, 101, 5.0f, Ui.TEXT);
            g.text(Ui.PIXEL, "VARIETE", x, 110, 4.0f, Ui.MUTED);
            g.text(Ui.BODY, g.fit(Ui.BODY, mutation.sheet().variety(), 5.0f, max - 30), x + 30, 109, 5.0f, Ui.TEXT);
            g.text(Ui.PIXEL, mutation.rarity().label().toUpperCase(Locale.ROOT), x, 120, 4.0f, mutation.rarity().color());
        } else if (!output.isEmpty()) {
            g.text(Ui.BOLD, "Dechet de mutation", x, 92, 6.0f, AMBER);
            g.wrapped(Ui.BODY, "Rien a decouvrir a ton niveau.", x, 102, 5.0f, max, 7.0f, Ui.MUTED, 2);
        } else if (state() == GeneScannerBlockEntity.STATE_SCANNING) {
            g.text(Ui.BOLD, "Inconnue", x, 92, 6.5f, Ui.TEXT);
            g.text(Ui.BODY, "Analyse en cours...", x, 102, 5.0f, LASER);
        } else {
            g.text(Ui.BODY, "Aucun resultat", x, 92, 5.5f, Ui.MUTED);
        }
    }

    // -- Le premier plan : les bulles ------------------------------------------------------------

    @Override
    protected void drawForeground(UiGraphics g, int mouseX, int mouseY) {
        int hovered = hoveredSlot(mouseX, mouseY, GeneScannerBlockEntity.SIZE);
        if (hovered >= 0 && !menu.slots.get(hovered).hasItem()) {
            List<String> hint = switch (hovered) {
                case GeneScannerBlockEntity.SLOT_INPUT -> List.of("Graine a analyser",
                        "Place une graine de mutation inconnue,", "ou d'une mutation absente de ton codex.");
                case GeneScannerBlockEntity.SLOT_FUEL -> List.of("Carburant", "Chaque scan consomme 1 charbon.");
                default -> List.of("Resultat", "La graine revelee sort ici.");
            };
            tip(g, mouseX, mouseY, mouseX, mouseY, 1, 1, hint);
        }
        if (state() == GeneScannerBlockEntity.STATE_READY) {
            tip(g, mouseX, mouseY, SCAN_X, SCAN_Y, SCAN_W, SCAN_H, List.of("Lancer le scan",
                    "Revele la mutation de la graine", "et l'ajoute a ton codex."));
        } else {
            tip(g, mouseX, mouseY, SCAN_X, SCAN_Y, SCAN_W, SCAN_H, List.of(statusLine()));
        }
        int next = ClientCodex.nextUnlock();
        tip(g, mouseX, mouseY, CODEX_X, CODEX_Y, CODEX_W, CODEX_H, List.of("Le codex",
                "Ouvre le codex des mutations.", "Decouvertes : " + ClientCodex.count() + " / "
                        + Mutation.values().length,
                "A decouvrir a ton niveau : " + ClientCodex.toDiscover(),
                next > 0 ? "Prochain deblocage : niveau " + next : "Toutes les plantes sont a ta portee."));
        ItemStack input = menu.slots.get(GeneScannerBlockEntity.SLOT_INPUT).getItem();
        if (input.getItem() instanceof UnknownMutationItem) {
            tip(g, mouseX, mouseY, CHAMBER_X, CHAMBER_Y, CHAMBER_W, CHAMBER_H, List.of("Mutation inconnue",
                    "Le scan revele la plante qu'elle porte."));
        }
    }

    /** La ligne d'etat de JobsReborn, pour la bulle du bouton grise. */
    private String statusLine() {
        return switch (state()) {
            case GeneScannerBlockEntity.STATE_SCANNING -> "Scan en cours";
            case GeneScannerBlockEntity.STATE_TAKE -> "Recupere le resultat";
            case GeneScannerBlockEntity.STATE_NO_FUEL -> "Ajoute du carburant";
            case GeneScannerBlockEntity.STATE_KNOWN -> "Mutation deja dans ton codex";
            default -> "Place une graine a analyser";
        };
    }

    // -- Les clics -----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x() - getLeftPos();
        double y = event.y() - getTopPos();
        if (event.button() == 0 && minecraft != null && minecraft.gameMode != null) {
            if (state() == GeneScannerBlockEntity.STATE_READY && inside(x, y, SCAN_X, SCAN_Y, SCAN_W, SCAN_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, GeneScannerBlockEntity.BUTTON_SCAN);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                return true;
            }
            if (inside(x, y, CODEX_X, CODEX_Y, CODEX_W, CODEX_H)) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                onClose();
                minecraft.gui.setScreen(new CodexScreen());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

}

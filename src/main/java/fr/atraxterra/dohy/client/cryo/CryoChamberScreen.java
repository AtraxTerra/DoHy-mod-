package fr.atraxterra.dohy.client.cryo;

import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiContainerScreen;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.cryo.CryoChamberBlockEntity;
import fr.atraxterra.dohy.cryo.Dna;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * L'ecran de la chambre cryogenique, au style des machines du kit : l'ADN et la
 * reserve de froid a gauche, la cuve au centre avec son clone qui suit la
 * souris, la production a droite, et la fusion dessous.
 */
public final class CryoChamberScreen extends UiContainerScreen<MachineMenu> {

    public static final int WIDTH = 262;
    public static final int HEIGHT = 236;

    private static final int TUBE_X = 74;
    private static final int TUBE_Y = 50;
    private static final int TUBE_W = 66;
    private static final int TUBE_H = 72;
    private static final int GAUGE_X = 50;
    private static final int GAUGE_Y = 98;
    private static final int GAUGE_W = 7;
    private static final int GAUGE_H = 38;
    private static final int FUSE_X = 177;
    private static final int FUSE_Y = 119;
    private static final int FUSE_W = 40;
    private static final int FUSE_H = 18;

    private static final int CYAN = 0xFF3FD8EC;
    private static final int ICE = 0xFF9FD6F2;

    private static final Map<Integer, List<String>> HINTS = Map.of(
            CryoChamberBlockEntity.SLOT_DNA, List.of("Fiole d'ADN", "Prelevee a la seringue sur une bete paisible"),
            CryoChamberBlockEntity.SLOT_COLD, List.of("Refrigerant",
                    "Neige, glace, glace compactee ou glace bleue"),
            CryoChamberBlockEntity.SLOT_FUSE_IN, List.of("Fusion",
                    Dna.FUSION + " fioles du meme ADN et du meme grade"),
            CryoChamberBlockEntity.SLOT_FUSE_OUT, List.of("Fiole fusionnee", "Un grade de plus"));

    private LivingEntity preview;
    private EntityType<?> previewType;

    public CryoChamberScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private ItemStack dna() {
        return menu.slots.get(CryoChamberBlockEntity.SLOT_DNA).getItem();
    }

    /** Le clone de la cuve, cote ecran : un modele de la bete, refait quand l'ADN change. */
    private LivingEntity preview() {
        Optional<EntityType<?>> type = Dna.type(dna());
        if (type.isEmpty() || minecraft == null || minecraft.level == null) {
            preview = null;
            previewType = null;
            return null;
        }
        if (type.get() != previewType) {
            previewType = type.get();
            preview = previewType.create(minecraft.level, EntitySpawnReason.LOAD) instanceof LivingEntity living
                    ? living : null;
        }
        return preview;
    }

    // -- Le fond -------------------------------------------------------------------------------

    @Override
    protected void drawPanel(UiGraphics g, int mouseX, int mouseY) {
        g.panel(0, 0, WIDTH, HEIGHT);
        title(g, getTitle().getString().toUpperCase(Locale.ROOT), 16, 15, 16, WIDTH - 32, Ui.RED, Ui.RED_SHADE);
        for (int index = 0; index < CryoChamberBlockEntity.SIZE; index++) {
            frame(g, menu.slots.get(index), index == CryoChamberBlockEntity.SLOT_DNA
                    || index == CryoChamberBlockEntity.SLOT_FUSE_OUT);
        }
        playerFrames(g, CryoChamberBlockEntity.SIZE);
        inventoryLabel(g, CryoChamberBlockEntity.SIZE);

        g.section(16, 38, "section_resources", "ADN", 52);
        g.section(74, 38, "section_fuel", "CUVE", 68);
        g.section(152, 38, "section_craft", "PRODUCTION", 94);

        // L'ADN : la bete et son grade.
        ItemStack dna = dna();
        Optional<EntityType<?>> type = Dna.type(dna);
        if (type.isPresent()) {
            int grade = Dna.grade(dna);
            String name = type.get().getDescription().getString();
            g.text(Ui.BODY, g.fit(Ui.BODY, name, 5.5f, 56), 16, 80, 5.5f, Ui.TEXT);
            for (int pip = 0; pip < Dna.MAX_GRADE; pip++) {
                int color = pip < grade ? Dna.gradeColor(grade) : 0xFF3A3940;
                g.rect(16 + pip * 7, 89, 5, 3, color);
            }
        } else {
            g.text(Ui.BODY, "Aucun ADN", 16, 80, 5.5f, Ui.MUTED);
        }

        // Le froid : une jauge qui descend a chaque cycle.
        g.text(Ui.PIXEL, "FROID", 16, 100, 4.5f, Ui.MUTED);
        float cold = menu.tankGoal() <= 0 ? 0.0f : menu.tank() / (float) menu.tankGoal();
        g.gauge(GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H, cold, cold > 0.15f ? CYAN : 0xFFE5484D);

        // La cuve : un fond de verre sombre, l'emetteur au plancher ; le clone vient ensuite.
        tube(g);
        float progress = menu.progress();
        g.rect(TUBE_X, TUBE_Y + TUBE_H + 4, TUBE_W, 4, 0xFF2E2D34);
        g.rect(TUBE_X, TUBE_Y + TUBE_H + 4, Math.round(TUBE_W * progress), 4, CYAN);
        int state = menu.extra(0);
        String line = switch (state) {
            case CryoChamberBlockEntity.STATE_RUNNING -> {
                int left = Math.max(0, menu.duration() - menu.work()) / 20;
                yield String.format(Locale.ROOT, "CLONAGE %d:%02d", left / 60, left % 60);
            }
            case CryoChamberBlockEntity.STATE_NO_COLD -> "PLUS DE FROID";
            case CryoChamberBlockEntity.STATE_FULL -> "SORTIE PLEINE";
            case CryoChamberBlockEntity.STATE_STERILE -> "ADN STERILE";
            default -> "POSE UN ADN";
        };
        int color = state == CryoChamberBlockEntity.STATE_RUNNING ? CYAN
                : state == CryoChamberBlockEntity.STATE_NO_DNA ? Ui.MUTED : 0xFFE5484D;
        g.centered(Ui.PIXEL, line, TUBE_X + TUBE_W / 2.0f, TUBE_Y + TUBE_H + 11, 4.5f, color);

        // La production, et la fusion dessous.
        int rolls = menu.extra(1);
        if (rolls > 0 && menu.duration() > 0) {
            g.text(Ui.PIXEL, rolls + (rolls > 1 ? " TIRAGES / " : " TIRAGE / ") + menu.duration() / 20 + " S", 152,
                    108, 4.0f, Ui.MUTED);
        }
        boolean hovered = mouseX >= FUSE_X && mouseX < FUSE_X + FUSE_W && mouseY >= FUSE_Y
                && mouseY < FUSE_Y + FUSE_H;
        g.button(FUSE_X, FUSE_Y, FUSE_W, FUSE_H, 0xFF2A8FB8, 0xFF175A78, hovered);
        g.centered(Ui.PIXEL, "FUSION", FUSE_X + FUSE_W / 2.0f, FUSE_Y + 5, 5.0f, Ui.WHITE);
        g.centered(Ui.PIXEL, Dna.FUSION + " > 1", FUSE_X + FUSE_W / 2.0f, FUSE_Y + 11, 3.5f, 0xFFCDEBF7);
    }

    /** Le fond de la cuve : un verre bleu nuit, plus clair au centre, l'emetteur qui luit au plancher. */
    private void tube(UiGraphics g) {
        Paint p = g.paint();
        p.framed(TUBE_X - 2, TUBE_Y - 2, TUBE_W + 4, TUBE_H + 4, 5.0f, 0xFF15181D, 0xFF3D4A57, 1);
        p.roundGradient(TUBE_X, TUBE_Y, TUBE_W, TUBE_H, 4.0f, 0xFF0D2232, 0xFF123A52);
        boolean running = menu.extra(0) == CryoChamberBlockEntity.STATE_RUNNING;
        p.glow(TUBE_X + TUBE_W / 2.0f, TUBE_Y + TUBE_H - 6.0f, 34.0f, Paint.alpha(CYAN, running ? 0.45f : 0.18f));
        p.round(TUBE_X + 12, TUBE_Y + TUBE_H - 7, TUBE_W - 24, 4, 2.0f, Paint.alpha(CYAN, running ? 0.9f : 0.35f));
        if (running && minecraft != null && minecraft.level != null) {
            // Des bulles qui montent, a la cadence du temps de jeu.
            long time = minecraft.level.getGameTime();
            for (int k = 0; k < 7; k++) {
                float phase = ((time + k * 23L) % 70L) / 70.0f;
                float bx = TUBE_X + 8 + (k * 37 % (TUBE_W - 16)) + Mth.sin(phase * 6.0f + k) * 2.0f;
                float by = TUBE_Y + TUBE_H - 8 - phase * (TUBE_H - 14);
                p.dot(bx, by, 1.0f + (k % 3) * 0.4f, Paint.alpha(ICE, 0.55f * (1.0f - phase)));
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        LivingEntity mob = preview();
        if (mob == null) {
            return;
        }
        float size = Math.max(0.3f, Math.max(mob.getBbHeight(), mob.getBbWidth() * 1.3f));
        int scale = Mth.clamp(Math.round((TUBE_H - 14) / size), 10, 38);
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, leftPos + TUBE_X + 3, topPos + TUBE_Y + 3,
                leftPos + TUBE_X + TUBE_W - 3, topPos + TUBE_Y + TUBE_H - 6, scale, 0.0625f, mouseX, mouseY, mob);
    }

    // -- Le premier plan -------------------------------------------------------------------

    @Override
    protected void drawForeground(UiGraphics g, int mouseX, int mouseY) {
        // Le verre par-dessus le clone : deux reflets et le givre des coins.
        Paint p = g.paint();
        p.gradientH(TUBE_X + 5, TUBE_Y + 4, 3, TUBE_H - 12, Paint.alpha(0xFFFFFFFF, 0.22f), 0x00FFFFFF);
        p.rect(TUBE_X + TUBE_W - 8, TUBE_Y + 6, 1, TUBE_H - 20, Paint.alpha(0xFFFFFFFF, 0.12f));
        p.gradient(TUBE_X, TUBE_Y, TUBE_W, 7, Paint.alpha(ICE, 0.35f), 0x009FD6F2);
        int hovered = hoveredSlot(mouseX, mouseY, CryoChamberBlockEntity.SIZE);
        if (hovered >= 0 && !menu.slots.get(hovered).hasItem()) {
            List<String> hint = HINTS.get(hovered);
            if (hint == null && hovered >= CryoChamberBlockEntity.FIRST_OUTPUT
                    && hovered < CryoChamberBlockEntity.FIRST_OUTPUT + CryoChamberBlockEntity.OUTPUTS) {
                hint = List.of("Production", "Le butin du clone, a chaque cycle");
            }
            if (hint != null) {
                tip(g, mouseX, mouseY, mouseX, mouseY, 1, 1, hint);
            }
        }
        tip(g, mouseX, mouseY, GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H, List.of("Reserve de froid",
                menu.tank() + " / " + menu.tankGoal(), CryoChamberBlockEntity.COLD_PER_CYCLE + " par cycle",
                "Neige 4 - glace 8 - compactee 72 - bleue 648"));
        ItemStack dna = dna();
        if (Dna.type(dna).isPresent()) {
            int grade = Dna.grade(dna);
            tip(g, mouseX, mouseY, TUBE_X, TUBE_Y, TUBE_W, TUBE_H, List.of(dna.getHoverName().getString(),
                    "Un cycle : " + Dna.cycle(grade) / 20 + " s, " + Dna.rolls(grade) + " tirage(s) de butin",
                    grade < Dna.MAX_GRADE ? "Fusionne " + Dna.FUSION + " fioles pour le grade "
                            + Dna.roman(grade + 1) : "Grade maximal"));
        }
        tip(g, mouseX, mouseY, FUSE_X, FUSE_Y, FUSE_W, FUSE_H, List.of("Fusion",
                Dna.FUSION + " fioles d'une meme bete et d'un meme grade", "donnent une fiole du grade suivant"));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x() - getLeftPos();
        double y = event.y() - getTopPos();
        if (event.button() == 0 && minecraft != null && minecraft.gameMode != null && x >= FUSE_X
                && x < FUSE_X + FUSE_W && y >= FUSE_Y && y < FUSE_Y + FUSE_H) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CryoChamberBlockEntity.BUTTON_FUSE);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}

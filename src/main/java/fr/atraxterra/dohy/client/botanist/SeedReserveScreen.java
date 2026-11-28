package fr.atraxterra.dohy.client.botanist;

import fr.atraxterra.dohy.client.ui.ContainerStyle;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiContainerScreen;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.palamod.botanist.Mutation;
import fr.atraxterra.dohy.palamod.botanist.PalaBotanist;
import fr.atraxterra.dohy.palamod.botanist.SeedReserve;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * L'ecran de la reserve de graines ({@code UIBotanistReserve} de JobsReborn), au style des machines du
 * kit : « RESERVE », « RANGEZ VOS ECHANTILLONS », le nombre de cases et leur capacite (« 10 graines »),
 * puis une case par plante dans le losange du kit du botaniste, givree quand elle est vide ; une
 * rangee par climat. La bulle d'une case reprend son texte : « Depose ici des graines de cette
 * mutation pour les garder en reserve. »
 */
public final class SeedReserveScreen extends UiContainerScreen<MachineMenu> {

    public static final int WIDTH = 262;
    public static final int HEIGHT = 236;

    private static final int GREEN = 0xFF00FF6A;
    private static final int FROST = 0xFF9FD8F0;
    private static final Identifier DIAMOND = Identifier.fromNamespaceAndPath("palamod",
            "textures/botanist/need/badge.png");
    private static final Identifier DIAMOND_OUTLINE = Identifier.fromNamespaceAndPath("palamod",
            "textures/botanist/need/badge_outline.png");
    private static final String[] ROWS = {"NORMAL", "GLACIAL", "DESERTIQUE"};

    public SeedReserveScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private boolean rewarded(int slot) {
        return (menu.extra(0) & (1 << slot)) != 0;
    }

    @Override
    protected void drawPanel(UiGraphics g, int mouseX, int mouseY) {
        g.panel(0, 0, WIDTH, HEIGHT);
        title(g, "RESERVE", 16, 15, 16, 76, Ui.RED, Ui.RED_SHADE);
        g.text(Ui.PIXEL, "RANGEZ VOS ECHANTILLONS", 96, 15, 4.2f, Ui.MUTED);
        int stored = 0;
        for (int index = 0; index < SeedReserve.size(); index++) {
            if (menu.slots.get(index).hasItem()) {
                stored++;
            }
        }
        g.text(Ui.PIXEL, "NOMBRE DE SLOTS", 96, 23, 3.6f, Ui.MUTED);
        g.text(Ui.PIXEL, stored + " / " + SeedReserve.size(), 152, 23, 3.6f, GREEN);
        g.right(Ui.PIXEL, "CAPACITE", 246, 15, 3.6f, Ui.MUTED);
        g.right(Ui.PIXEL, SeedReserve.LIMIT + " GRAINES", 246, 23, 3.6f, 0xFFFFFFFF);

        Mutation[] all = Mutation.values();
        for (int row = 0; row < 3; row++) {
            Slot first = menu.slots.get(row * 5);
            g.text(Ui.PIXEL, ROWS[row], 16, first.y + 6, 3.8f, Ui.MUTED);
        }
        int hovered = hoveredSlot(mouseX, mouseY, SeedReserve.size());
        for (int index = 0; index < SeedReserve.size() && index < all.length; index++) {
            Slot slot = menu.slots.get(index);
            Mutation mutation = all[index];
            boolean filled = slot.hasItem();
            int rarity = mutation.rarity().color();
            int tint = filled ? Paint.mix(0xFF1E1D23, rarity, 0.25f) : 0xFF151A1E;
            g.raw().blit(RenderPipelines.GUI_TEXTURED, DIAMOND, slot.x - 7, slot.y - 7, 0.0f, 0.0f, 30, 30, 48, 48, 48,
                    48, tint);
            int outline = index == hovered ? 0xFFFFFFFF : filled ? rarity : 0xFF33424A;
            g.raw().blit(RenderPipelines.GUI_TEXTURED, DIAMOND_OUTLINE, slot.x - 7, slot.y - 7, 0.0f, 0.0f, 30, 30, 48,
                    48, 48, 48, outline);
            if (!filled) {
                // La graine attendue, givree : son ombre sous le givre de la reserve.
                g.item(new ItemStack(PalaBotanist.MUTATION_SEEDS.get(mutation).get()), slot.x, slot.y, 16, false);
                g.paint().rect(slot.x, slot.y, 16, 16, Paint.alpha(0xFF0C1418, 0.82f));
                g.paint().rect(slot.x + 3, slot.y + 3, 1, 1, Paint.alpha(FROST, 0.7f));
                g.paint().rect(slot.x + 12, slot.y + 10, 1, 1, Paint.alpha(FROST, 0.6f));
                g.paint().rect(slot.x + 7, slot.y + 13, 1, 1, Paint.alpha(FROST, 0.5f));
            } else {
                int count = slot.getItem().getCount();
                g.centered(Ui.PIXEL, count + "/" + SeedReserve.LIMIT, slot.x + 8, slot.y + 19, 3.2f,
                        count >= SeedReserve.LIMIT ? GREEN : 0xFFD9D9D9);
            }
        }
        playerFrames(g, SeedReserve.size());
        inventoryLabel(g, SeedReserve.size());
    }

    @Override
    protected void drawForeground(UiGraphics g, int mouseX, int mouseY) {
        int hovered = hoveredSlot(mouseX, mouseY, SeedReserve.size());
        if (hovered < 0 || hovered >= Mutation.values().length) {
            return;
        }
        Mutation mutation = Mutation.values()[hovered];
        Slot slot = menu.slots.get(hovered);
        if (slot.hasItem()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(ContainerStyle.plain(mutation.label()));
        lines.add(mutation.rarity().label() + " - climat " + Mutation.climateLabel(mutation.climate()));
        lines.add("Depose ici des graines de cette mutation");
        lines.add("pour les garder en reserve (" + SeedReserve.LIMIT + " au plus).");
        lines.add(rewarded(hovered) ? "XP de la premiere graine deja recue."
                : "Premiere graine rangee : +" + SeedReserve.rewardXp(mutation.rarity()) + " XP de Fermier.");
        tip(g, mouseX, mouseY, mouseX, mouseY, 1, 1, lines);
    }
}

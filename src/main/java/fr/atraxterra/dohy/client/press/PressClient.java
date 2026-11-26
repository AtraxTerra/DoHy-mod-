package fr.atraxterra.dohy.client.press;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.screen.MachineKitScreen.Button;
import fr.atraxterra.dohy.client.ui.screen.MachineKitScreen.Design;
import fr.atraxterra.dohy.palamod.press.EnchantedPlateItem;
import fr.atraxterra.dohy.palamod.press.PrintPressBlockEntity;
import fr.atraxterra.dohy.palamod.press.TypeMachineBlockEntity;
import fr.atraxterra.dohy.palamod.press.TypesetCost;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Les interfaces de la presse a imprimer et de la table d'ecriture.
 *
 * <p>PalaMod n'en avait pas : on y deposait les fournitures en cliquant sur le
 * bloc, ce qui marche toujours. L'interface montre ce que le bloc ne dit pas :
 * l'avancement de l'impression, l'usure de la plaque, le prix de la gravure.
 */
public final class PressClient {

    private PressClient() {
    }

    private static void small(UiGraphics g, String text, float x, float y, int color) {
        g.text(Ui.BODY, text, x, y, 5.5f, color);
    }

    private static void tip(UiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height,
                            List<String> lines) {
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            List<Component> components = new ArrayList<>();
            for (int index = 0; index < lines.size(); index++) {
                components.add(Component.literal(lines.get(index)).withColor(index == 0 ? 0xFFFF55 : 0xAAAAAA));
            }
            g.tooltip(components);
        }
    }

    // -- Presse a imprimer ---------------------------------------------------------

    public static final Design PRINT_PRESS = new Design(Set.of(PrintPressBlockEntity.SLOT_OUTPUT),
            (g, menu, mouseX, mouseY) -> {
                g.section(16, 38, "section_fuel", "ENCRE", 62);
                g.centered(Ui.PIXEL, PrintPressBlockEntity.INK_PER_BOOK + " PAR LIVRE", 48, 84, 4.5f, Ui.MUTED);
                g.section(86, 38, "section_resources", "PLAQUE ET LIVRES", 92);
                g.arrow(126, 87, 64, menu.progress());
                if (menu.duration() > 0) {
                    int left = Math.max(0, menu.duration() - menu.work());
                    g.centered(Ui.PIXEL, String.format(Locale.FRANCE, "%.1f S", left / 20.0f), 158, 98, 4.5f,
                            Ui.MUTED);
                }
                g.section(186, 38, "section_craft", "LIVRE", 60);
                boolean plate = menu.extra(1) == 1;
                int left = menu.extra(0);
                small(g, plate ? "Plaque : " + left + " / " + EnchantedPlateItem.PRINTS : "Aucune plaque",
                        120, 66, plate ? (left > 1 ? Ui.TEXT : Ui.RED) : Ui.MUTED);
                small(g, plate ? "impressions" : "a graver", 120, 74, Ui.MUTED);
                small(g, "Livres vierges", 120, 108, Ui.MUTED);
                g.wrapped(Ui.BODY, "La plaque recopie ses enchantements sur chaque livre, puis casse a la "
                        + "troisieme impression.", 16, 124, 5.5f, 230, 8, Ui.MUTED, 2);
                tip(g, mouseX, mouseY, 120, 64, 60, 18, List.of("Plaque d'impression enchantee",
                        "Gravee a la table d'ecriture", "Trois livres par plaque"));
            }, Map.of(
                    PrintPressBlockEntity.SLOT_INK, List.of("Encre de paladium", "Deux par livre imprime"),
                    PrintPressBlockEntity.SLOT_PLATE, List.of("Plaque d'impression enchantee",
                            "Gravee a la table d'ecriture"),
                    PrintPressBlockEntity.SLOT_BOOKS, List.of("Livres", "Un par impression"),
                    PrintPressBlockEntity.SLOT_OUTPUT, List.of("Livre enchante", "Les enchantements de la plaque")));

    // -- Table d'ecriture ----------------------------------------------------------

    private static final int ENGRAVE_X = 100;
    private static final int ENGRAVE_Y = 104;
    private static final int ENGRAVE_W = 84;
    private static final int ENGRAVE_H = 18;

    public static final Design TYPE_MACHINE = new Design(Set.of(TypeMachineBlockEntity.SLOT_PLATE),
            (g, menu, mouseX, mouseY) -> {
                g.section(16, 38, "section_resources", "LIVRE ET PLAQUE", 76);
                g.section(100, 38, "section_upgrade", "GRAVURE", 84);
                g.section(192, 38, "section_craft", "PLAQUE", 54);
                int cost = menu.extra(0);
                Minecraft minecraft = Minecraft.getInstance();
                boolean free = minecraft.player != null && minecraft.player.hasInfiniteMaterials();
                int level = minecraft.player == null ? 0 : minecraft.player.experienceLevel;
                boolean ready = cost > 0 && menu.slots.get(TypeMachineBlockEntity.SLOT_CHASE).hasItem()
                        && !menu.slots.get(TypeMachineBlockEntity.SLOT_PLATE).hasItem();
                boolean affordable = free || level >= cost;
                if (cost > 0) {
                    g.text(Ui.TITLE, cost + " NIV.", 100, 56, 10, affordable ? Ui.GREEN : Ui.RED);
                    small(g, free ? "Creatif : gratuit" : "Tu as " + level + " niveau" + (level > 1 ? "x" : ""),
                            100, 72, Ui.MUTED);
                } else {
                    small(g, "Pose un livre enchante", 100, 58, Ui.MUTED);
                    small(g, "et une plaque d'impression", 100, 66, Ui.MUTED);
                }
                g.arrow(120, 89, 64, ready && affordable ? 1.0f : 0.0f);
                boolean hovered = mouseX >= ENGRAVE_X && mouseX < ENGRAVE_X + ENGRAVE_W && mouseY >= ENGRAVE_Y
                        && mouseY < ENGRAVE_Y + ENGRAVE_H;
                boolean active = ready && affordable;
                g.button(ENGRAVE_X, ENGRAVE_Y, ENGRAVE_W, ENGRAVE_H, active ? 0xFF8E44D8 : 0xFF4A4950,
                        active ? 0xFF5B2696 : 0xFF2E2D33, hovered && active);
                g.centered(Ui.PIXEL, "GRAVER", ENGRAVE_X + ENGRAVE_W / 2.0f, ENGRAVE_Y + 5, 5.5f,
                        active ? Ui.WHITE : Ui.MUTED);
                g.wrapped(Ui.BODY, "Le prix suit la rarete et le niveau des enchantements du livre, "
                        + TypesetCost.MAX + " niveaux au plus.", 16, 128, 5.5f, 230, 8, Ui.MUTED, 1);
                tip(g, mouseX, mouseY, ENGRAVE_X, ENGRAVE_Y, ENGRAVE_W, ENGRAVE_H, List.of("Graver la plaque",
                        "Prend le livre, une plaque et les niveaux", "Accroupi, main vide sur la table : pareil"));
            }, Map.of(
                    TypeMachineBlockEntity.SLOT_BOOK, List.of("Livre enchante", "Ses enchantements seront graves"),
                    TypeMachineBlockEntity.SLOT_CHASE, List.of("Plaque d'impression", "Une par gravure"),
                    TypeMachineBlockEntity.SLOT_PLATE, List.of("Plaque d'impression enchantee",
                            "A poser dans la presse a imprimer")),
            List.of(new Button(TypeMachineBlockEntity.BUTTON_ENGRAVE, ENGRAVE_X, ENGRAVE_Y, ENGRAVE_W, ENGRAVE_H)));
}

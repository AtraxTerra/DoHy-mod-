package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Le dessin du crusher, sur la maquette de Paladium : les recettes et leurs
 * bacs a gauche, le fruit en haut, le charbon en bas, la fleche, le resultat.
 *
 * <p>Separe de l'ecran de conteneur pour pouvoir etre dessine seul (apercu,
 * captures) : les cases d'objets sont celles du menu, dessinees a part.
 */
public final class CrusherPanel {

    public static final int WIDTH = 262;
    public static final int HEIGHT = 236;

    /** Les cases, en coordonnees de panneau (coin de l'objet 16x16). */
    public static final int FRUIT_X = 177;
    public static final int FRUIT_Y = 44;
    public static final int FUEL_X = 177;
    public static final int FUEL_Y = 88;
    public static final int OUTPUT_X = 235;
    public static final int OUTPUT_Y = 66;
    public static final int INVENTORY_X = 51;
    public static final int INVENTORY_Y = 150;
    public static final int HOTBAR_Y = 210;

    private static final String[][] RECIPES = {
            {"eggplant", "gold_mixed_coal", "AUBERGINES"},
            {"chervil", "amethyst_mixed_coal", "CERFEUIL"},
            {"kiwano", "titane_mixed_coal", "KIWANOS"},
            {"orangeblue", "pala_mixed_coal", "ORANGEBLUES"},
    };

    /**
     * L'etat a montrer.
     *
     * @param tanks   fruits broyes par recette
     * @param goals   taille de lot par recette
     * @param current recette en cours, -1 si aucune
     */
    public record State(int[] tanks, int[] goals, int current, float progress) {
    }

    private CrusherPanel() {
    }

    private static ItemStack palamod(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("palamod", path)));
    }

    public static void draw(UiGraphics g, int x, int y, State state, double mouseX, double mouseY) {
        g.panel(x, y, WIDTH, HEIGHT);
        g.textShaded(Ui.TITLE, "CRUSHER", x + 16, y + 15, 16, Ui.RED, Ui.RED_SHADE, 1.0f);

        // Les recettes.
        section(g, x + 16, y + 38, "section_resources", "RECETTES", 122);
        int listX = x + 16;
        int listY = y + 50;
        g.rect(listX, listY, 140, 88, Ui.INSET);
        for (int index = 0; index < RECIPES.length; index++) {
            int rowY = listY + 3 + index * 21;
            boolean active = index == state.current();
            g.rect(listX + 3, rowY, 134, 19, active ? 0xFF2A292F : 0xFF1C1B21);
            if (active) {
                g.rect(listX + 3, rowY, 2, 19, Ui.RED);
            }
            g.item(palamod(RECIPES[index][0]), listX + 8, rowY + 1.5f, 16, false);
            int color = active ? Ui.WHITE : Ui.MUTED;
            g.text(Ui.TITLE, RECIPES[index][2], listX + 28, rowY + 3, 6.5f, color);
            String tank = state.tanks()[index] + "/" + state.goals()[index];
            g.text(Ui.TITLE, tank, listX + 28, rowY + 11, 5, color);
            g.item(palamod(RECIPES[index][1]), listX + 118, rowY + 3.5f, 12, false);
            int fill = Math.min(36, 36 * state.tanks()[index] / Math.max(1, state.goals()[index]));
            g.rect(listX + 76, rowY + 13, 36, 2, 0xFF2E2D34);
            g.rect(listX + 76, rowY + 13, fill, 2, active ? Ui.GREEN : 0xFF5C5B63);
        }

        // Fruit, chaleur, charbon.
        g.texture(Ui.HEAT, x + FUEL_X + 1, y + FUEL_Y - 22, 14, 14, 44, 44);
        g.text(Ui.PIXEL, "Fruit", x + FRUIT_X - 24, y + FRUIT_Y + 5, 5, Ui.MUTED);
        g.text(Ui.PIXEL, "Fuel", x + FUEL_X - 22, y + FUEL_Y + 5, 5, Ui.MUTED);

        // La fleche vers le resultat.
        g.arrow(x + FRUIT_X + 22, y + OUTPUT_Y + 3, 30, state.progress());

        g.text(Ui.PIXEL, "Inventaire".toUpperCase(Locale.ROOT), x + INVENTORY_X, y + INVENTORY_Y - 9, 4.5f,
                Ui.MUTED);
    }

    /** Un intertitre de machine : le losange, le mot, un filet. */
    public static void section(UiGraphics g, int x, int y, String icon, String label, int width) {
        g.texture(Ui.texture(icon), x, y - 2, 10, 10, 56, 56);
        g.text(Ui.PIXEL, label, x + 14, y, 6, Ui.WHITE);
        float end = x + 14 + g.width(Ui.PIXEL, label, 6) + 5;
        g.rect(Math.round(end), y + 3, Math.round(x + width - end), 1, Ui.WHITE);
    }

    /** Les cadres des cases : celles de la machine et l'inventaire. */
    public static void slots(UiGraphics g, int x, int y) {
        g.slot(x + FRUIT_X - 1, y + FRUIT_Y - 1, 18, false);
        g.slot(x + FUEL_X - 1, y + FUEL_Y - 1, 18, false);
        g.slot(x + OUTPUT_X - 5, y + OUTPUT_Y - 5, 26, false);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                g.slot(x + INVENTORY_X + column * 18 - 1, y + INVENTORY_Y + row * 18 - 1, 18, false);
            }
        }
        for (int column = 0; column < 9; column++) {
            g.slot(x + INVENTORY_X + column * 18 - 1, y + HOTBAR_Y - 1, 18, false);
        }
    }
}

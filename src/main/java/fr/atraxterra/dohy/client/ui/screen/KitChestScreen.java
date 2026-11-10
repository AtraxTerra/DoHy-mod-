package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiContainerScreen;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.ui.KitMenus;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

import java.util.Locale;

/**
 * Un inventaire en coffre au style du kit : coffre de vente, guichet du musee,
 * sac de la monture, coffre du golem, coffre de l'ender en paladium.
 *
 * <p>Les cases sont celles du coffre du jeu (176 de large, rangees de 18), donc
 * le panneau garde ses dimensions : le titre tient dans la bande du haut, la
 * consigne de l'usage a sa droite.
 */
public final class KitChestScreen extends UiContainerScreen<ChestMenu> {

    private final KitMenus.Use use;

    public KitChestScreen(ChestMenu menu, Inventory inventory, Component title, KitMenus.Use use) {
        super(menu, inventory, title, 176, 114 + use.rows() * 18);
        this.use = use;
    }

    @Override
    protected void drawPanel(UiGraphics g, int mouseX, int mouseY) {
        g.panelPlain(0, 0, imageWidth, imageHeight);
        float hintWidth = g.width(Ui.BODY, use.hint(), 5);
        g.right(Ui.BODY, use.hint(), imageWidth - 8, 7, 5, Ui.MUTED);
        title(g, getTitle().getString().toUpperCase(Locale.ROOT), 8, 4, 9, imageWidth - 22 - hintWidth, Ui.RED,
                Ui.RED_SHADE);
        int chestSlots = use.rows() * 9;
        for (int index = 0; index < menu.slots.size(); index++) {
            frame(g, menu.slots.get(index), false);
        }
        inventoryLabel(g, chestSlots);
    }
}

package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiContainerScreen;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.palamod.storage.PalaChestMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;

/**
 * L'ecran des coffres de PalaMod, au style du kit : 12 x 9 cases, la colonne
 * d'anneaux, l'inventaire.
 *
 * <p>Les coordonnees sont celles de {@code ModdedChestContainer} et ne laissent
 * aucune place au titre a l'interieur du panneau : la grille commence a 8 px du
 * haut. Le titre est donc ecrit juste au-dessus, a la couleur de son metal.
 */
public class PalaChestScreen extends UiContainerScreen<PalaChestMenu> {

    private static final int PANEL_HEIGHT = 256;

    public PalaChestScreen(PalaChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.tier().panelWidth(), PANEL_HEIGHT);
    }

    @Override
    protected void drawPanel(UiGraphics g, int mouseX, int mouseY) {
        g.panelPlain(0, 0, imageWidth, imageHeight);
        int color = menu.tier().titleColor();
        title(g, getTitle().getString().toUpperCase(Locale.ROOT), 2, -13, 10, imageWidth - 4, color,
                darker(color));
        int chestEnd = menu.tier().containerSize();
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            boolean ring = index >= menu.tier().size() && index < chestEnd;
            frame(g, slot, false);
            if (ring) {
                // Les anneaux : la case teintee du metal du coffre.
                g.rect(slot.x, slot.y, 16, 16, (color & 0x00FFFFFF) | 0x38000000);
            }
        }
    }

    private static int darker(int color) {
        int r = ((color >> 16) & 0xFF) * 6 / 10;
        int gr = ((color >> 8) & 0xFF) * 6 / 10;
        int b = (color & 0xFF) * 6 / 10;
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }
}

package fr.atraxterra.dohy.client.ui.screen;

import fr.atraxterra.dohy.client.ui.ContainerStyle;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.UiContainerScreen;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.ui.KitMenus;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * Un inventaire en coffre au style du kit : coffre de vente, guichet du musee,
 * sac de la monture, coffre du golem, coffre de l'ender en paladium ou agrandi par
 * un grade (jusqu'a sept rangees).
 *
 * <p>Les cases sont celles du coffre du jeu (176 de large, rangees de 18), donc le
 * panneau garde ses dimensions ; l'en-tete (icone, titre, croix) deborde au-dessus,
 * comme pour les coffres du jeu, et la consigne de l'usage suit l'intertitre.
 */
public final class KitChestScreen extends UiContainerScreen<ChestMenu> {

    private final KitMenus.Use use;
    private final ItemStack icon;

    public KitChestScreen(ChestMenu menu, Inventory inventory, Component title, KitMenus.Use use) {
        super(menu, inventory, title, 176, 114 + use.rows() * 18);
        this.use = use;
        this.icon = use == KitMenus.Use.MOUNT_INVENTORY ? new ItemStack(Items.SADDLE)
                : use.ender() ? new ItemStack(Items.ENDER_CHEST)
                : ContainerStyle.targetIcon(new ItemStack(Items.CHEST));
    }

    @Override
    protected void init() {
        super.init();
        topPos = ContainerStyle.top(height, imageHeight);
    }

    @Override
    protected void drawPanel(UiGraphics g, int mouseX, int mouseY) {
        Paint p = g.paint();
        ContainerStyle.window(p, imageWidth, imageHeight);
        ContainerStyle.header(g, p, icon, getTitle().getString(), imageWidth, 22);
        ContainerStyle.close(g, p, imageWidth, ContainerStyle.overClose(mouseX, mouseY, imageWidth));
        for (Slot slot : menu.slots) {
            ContainerStyle.slot(p, slot);
        }
        int chestSlots = use.rows() * 9;
        ContainerStyle.labelAbove(g, menu.slots.get(0), "CONTENU", use.hint().toUpperCase(Locale.ROOT));
        if (chestSlots < menu.slots.size()) {
            ContainerStyle.inventoryLabel(g, menu.slots.get(chestSlots));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && ContainerStyle.overClose(event.x() - leftPos, event.y() - topPos, imageWidth)) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return super.hasClickedOutside(mouseX, mouseY, left, top)
                && !ContainerStyle.inHeader(mouseX, mouseY, left, top, imageWidth);
    }
}

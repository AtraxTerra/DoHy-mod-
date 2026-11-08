package fr.atraxterra.dohy.palamod.golem;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Un coffre vanilla dont les cases demandent au conteneur ce qu'elles
 * acceptent.
 *
 * <p>Les cases de {@link ChestMenu} acceptent tout : elles ne consultent pas
 * {@code canPlaceItem}. Ici, chaque case du conteneur est remplacee par une
 * case qui le fait. Le client garde le coffre vanilla (meme type de menu,
 * memes positions) ; le serveur refuse, et resynchronise.
 */
public class FilteredChestMenu extends ChestMenu {

    public FilteredChestMenu(MenuType<?> type, int containerId, Inventory inventory, Container container, int rows) {
        super(type, containerId, inventory, container, rows);
        for (int index = 0; index < rows * 9; index++) {
            Slot old = slots.get(index);
            Slot filtered = new Slot(container, old.getContainerSlot(), old.x, old.y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return container.canPlaceItem(getContainerSlot(), stack);
                }
            };
            filtered.index = index;
            slots.set(index, filtered);
        }
    }
}

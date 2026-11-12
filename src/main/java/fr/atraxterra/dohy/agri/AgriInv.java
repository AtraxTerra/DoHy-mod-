package fr.atraxterra.dohy.agri;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Compter, prendre et rendre dans l'inventaire porte : les machines de l'agriculture y puisent. */
final class AgriInv {

    private static final int CARRIED_SLOTS = 36;

    private AgriInv() {
    }

    static int count(ServerPlayer player, Item item) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < CARRIED_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    static void take(ServerPlayer player, Item item, int wanted) {
        int left = wanted;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < CARRIED_SLOTS && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                int part = Math.min(left, stack.getCount());
                stack.shrink(part);
                left -= part;
            }
        }
        inventory.setChanged();
    }

    /** Donne, et pose au sol ce qui ne rentre pas : rien de paye ne disparait. */
    static void give(ServerPlayer player, Item item, int count) {
        int left = count;
        int stackSize = new ItemStack(item).getMaxStackSize();
        while (left > 0) {
            int part = Math.min(left, stackSize);
            ItemStack stack = new ItemStack(item, part);
            if (!player.getInventory().add(stack) || !stack.isEmpty()) {
                player.drop(stack, false);
            }
            left -= part;
        }
    }
}

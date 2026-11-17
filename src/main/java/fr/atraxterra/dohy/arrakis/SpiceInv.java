package fr.atraxterra.dohy.arrakis;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Compter, prendre et rendre dans l'inventaire porte : le comptoir et la moissonneuse y puisent. */
final class SpiceInv {

    private static final int CARRIED_SLOTS = 36;

    private SpiceInv() {
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

    /** Retire jusqu'a {@code wanted} exemplaires ; rend ce qui a ete retire. */
    static int take(ServerPlayer player, Item item, int wanted) {
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
        return wanted - left;
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

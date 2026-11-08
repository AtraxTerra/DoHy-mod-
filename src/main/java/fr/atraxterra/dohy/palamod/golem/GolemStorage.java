package fr.atraxterra.dohy.palamod.golem;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Le stockage du golem (Stockeur) : 16 cases, ouvertes dans une interface de
 * coffre a deux rangees. Les deux dernieres cases de la rangee restent
 * fermees.
 */
public class GolemStorage extends SimpleContainer {

    private final GuardianGolem golem;

    public GolemStorage(GuardianGolem golem) {
        super(18);
        this.golem = golem;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot < GuardianGolem.STORAGE_SLOTS;
    }

    @Override
    public ItemStack addItem(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int slot = 0; slot < GuardianGolem.STORAGE_SLOTS && !rest.isEmpty(); slot++) {
            ItemStack here = getItem(slot);
            if (here.isEmpty()) {
                setItem(slot, rest);
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameComponents(here, rest) && here.getCount() < here.getMaxStackSize()) {
                int moved = Math.min(rest.getCount(), here.getMaxStackSize() - here.getCount());
                here.grow(moved);
                rest.shrink(moved);
                setChanged();
            }
        }
        return rest;
    }

    @Override
    public boolean stillValid(Player player) {
        return golem.isAlive() && golem.has(GolemUpgrade.STOCKAGE)
                && player.distanceToSqr(golem) <= GuardianGolem.MENU_RANGE * GuardianGolem.MENU_RANGE;
    }

    public void save(ValueOutput output) {
        NonNullList<ItemStack> items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < getContainerSize(); slot++) {
            items.set(slot, getItem(slot));
        }
        ContainerHelper.saveAllItems(output, items);
    }

    public void load(ValueInput input) {
        NonNullList<ItemStack> items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        for (int slot = 0; slot < getContainerSize(); slot++) {
            setItem(slot, items.get(slot));
        }
    }
}

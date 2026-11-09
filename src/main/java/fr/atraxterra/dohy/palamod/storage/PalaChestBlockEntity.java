package fr.atraxterra.dohy.palamod.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.stream.IntStream;

/**
 * Le contenu d'un coffre en amethyste ou en titane.
 *
 * <p>Accessible par toutes les faces aux entonnoirs, comme
 * {@code ModdedChestLogic} ({@code getAccessibleSlotsFromSide} rend les 108
 * cases quelle que soit la face).
 */
public class PalaChestBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    private final NonNullList<ItemStack> items;
    private final int[] allSlots;
    private final ChestTier tier;

    public PalaChestBlockEntity(BlockPos pos, BlockState state) {
        super(PalaStorage.CHEST.get(), pos, state);
        this.tier = state.getBlock() instanceof PalaChestBlock chest ? chest.tier() : ChestTier.AMETHYST;
        this.items = NonNullList.withSize(tier.size(), ItemStack.EMPTY);
        this.allSlots = IntStream.range(0, tier.size()).toArray();
    }

    public ChestTier tier() {
        return tier;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    public NonNullList<ItemStack> contents() {
        return items;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return allSlots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.palamod." + tier.key());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new PalaChestMenu(PalaStorage.menuOf(tier), tier, containerId, inventory, this);
    }
}

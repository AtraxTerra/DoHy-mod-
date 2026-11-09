package fr.atraxterra.dohy.palamod.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import fr.atraxterra.dohy.palamod.pvp.RingItem;
import fr.atraxterra.dohy.palamod.pvp.RingRepair;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * Le contenu d'un coffre de PalaMod : la grille, puis la colonne d'anneaux.
 *
 * <p>La grille est accessible par toutes les faces aux entonnoirs, comme
 * {@code ModdedChestLogic} ({@code getAccessibleSlotsFromSide} rend les 108
 * cases quelle que soit la face) ; les anneaux, non.
 *
 * <p>A chaque tick ({@code updateEntity}), chaque anneau encore utilisable
 * repare d'un point chaque piece abimee des {@link ChestTier#repairRows()}
 * premieres rangees (voir {@code RingRepair}).
 */
public class PalaChestBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    private final NonNullList<ItemStack> items;
    private final int[] allSlots;
    private final ChestTier tier;

    public PalaChestBlockEntity(BlockPos pos, BlockState state) {
        super(PalaStorage.CHEST.get(), pos, state);
        this.tier = state.getBlock() instanceof PalaChestBlock chest ? chest.tier() : ChestTier.AMETHYST;
        this.items = NonNullList.withSize(tier.containerSize(), ItemStack.EMPTY);
        this.allSlots = IntStream.range(0, tier.size()).toArray();
    }

    public ChestTier tier() {
        return tier;
    }

    /** Les anneaux au travail, cote serveur. */
    public void serverTick(ServerLevel level) {
        if (tier.ringSlots() == 0) {
            return;
        }
        int repaired = Math.min(tier.size(), tier.repairRows() * tier.columns());
        boolean changed = false;
        for (int ring = tier.size(); ring < tier.containerSize(); ring++) {
            ItemStack stack = items.get(ring);
            if (stack.isEmpty()) {
                continue;
            }
            for (int slot = 0; slot < repaired && !stack.isEmpty(); slot++) {
                changed |= RingRepair.repairStored(level, items.get(slot), stack, tier.endiumRings());
            }
        }
        if (changed) {
            setChanged();
        }
    }

    /** Une case d'anneau ne prend qu'un anneau du bon metal ; la grille, tout. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot < tier.size() || acceptsRing(stack);
    }

    public boolean acceptsRing(ItemStack stack) {
        return stack.getItem() instanceof RingItem ring && ring.endium() == tier.endiumRings();
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
        return slot < tier.size();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot < tier.size();
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

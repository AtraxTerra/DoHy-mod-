package fr.atraxterra.dohy.palamod.defense;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * L'inventaire et le travail du pont-levis : un bloc pose ou repris tous les
 * deux ticks. Il ne reprend que ses propres blocs, encore en place ; un bloc
 * casse ou change par quelqu'un d'autre est laisse tel quel.
 */
public class DrawbridgeBlockEntity extends BlockEntity implements Container, MenuProvider {

    /** Longueur du pont, au plus. */
    public static final int MAX_LENGTH = 16;
    private static final int SLOTS = 9;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    /** Les blocs poses, dans l'ordre : le premier touche le pont-levis. */
    private final List<Item> placed = new ArrayList<>();

    public DrawbridgeBlockEntity(BlockPos pos, BlockState state) {
        super(PalaDefense.DRAWBRIDGE_ENTITY.get(), pos, state);
    }

    public int extended() {
        return placed.size();
    }

    void tick(Level level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 2L != 0L) {
            return;
        }
        Direction facing = state.getValue(DrawbridgeBlock.FACING);
        if (level.hasNeighborSignal(pos)) {
            extend(level, pos, facing);
        } else if (!placed.isEmpty()) {
            retract(level, pos, facing);
        }
    }

    private void extend(Level level, BlockPos pos, Direction facing) {
        if (placed.size() >= MAX_LENGTH) {
            return;
        }
        BlockPos target = pos.relative(facing, placed.size() + 1);
        if (!level.getBlockState(target).canBeReplaced()) {
            return;
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.getItem() instanceof BlockItem blockItem && !(blockItem.getBlock() instanceof EntityBlock)) {
                BlockState placedState = blockItem.getBlock().defaultBlockState();
                if (!placedState.canSurvive(level, target)) {
                    return;
                }
                level.setBlock(target, placedState, Block.UPDATE_ALL);
                placed.add(stack.getItem());
                stack.shrink(1);
                level.playSound(null, target, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.4f, 1.2f);
                setChanged();
                return;
            }
        }
    }

    private void retract(Level level, BlockPos pos, Direction facing) {
        int index = placed.size() - 1;
        Item item = placed.remove(index);
        BlockPos target = pos.relative(facing, index + 1);
        if (item instanceof BlockItem blockItem && level.getBlockState(target).is(blockItem.getBlock())) {
            level.setBlock(target, level.getFluidState(target).createLegacyBlock(), Block.UPDATE_ALL);
            give(level, pos, new ItemStack(item));
            level.playSound(null, target, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.4f, 1.2f);
        }
        setChanged();
    }

    /** Range un bloc repris, ou le pose au sol si l'inventaire est plein. */
    private void give(Level level, BlockPos pos, ItemStack stack) {
        for (int slot = 0; slot < SLOTS && !stack.isEmpty(); slot++) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty()) {
                items.set(slot, stack.copy());
                stack.setCount(0);
            } else if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(moved);
                stack.shrink(moved);
            }
        }
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5d, pos.getY() + 1.0d, pos.getZ() + 0.5d, stack);
        }
    }

    /** Le pont-levis est casse : ses blocs poses tombent avec son inventaire. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null || level.isClientSide()) {
            return;
        }
        Direction facing = state.getValue(DrawbridgeBlock.FACING);
        while (!placed.isEmpty()) {
            int index = placed.size() - 1;
            Item item = placed.remove(index);
            BlockPos target = pos.relative(facing, index + 1);
            if (item instanceof BlockItem blockItem && level.getBlockState(target).is(blockItem.getBlock())) {
                level.setBlock(target, level.getFluidState(target).createLegacyBlock(), Block.UPDATE_ALL);
                Containers.dropItemStack(level, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d,
                        new ItemStack(item));
            }
        }
        Containers.dropContents(level, pos, items);
    }

    // -- Sauvegarde ----------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        placed.clear();
        int count = input.getIntOr("placed", 0);
        for (int i = 0; i < count; i++) {
            Identifier id = Identifier.tryParse(input.getStringOr("placed_" + i, ""));
            Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
            if (item != null) {
                placed.add(item);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("placed", placed.size());
        for (int i = 0; i < placed.size(); i++) {
            output.putString("placed_" + i, BuiltInRegistries.ITEM.getKey(placed.get(i)).toString());
        }
    }

    // -- Inventaire ----------------------------------------------------------

    @Override
    public int getContainerSize() {
        return SLOTS;
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
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.palamod.drawbridge");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DispenserMenu(id, inventory, this);
    }
}

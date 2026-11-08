package fr.atraxterra.dohy.palamod.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.stream.IntStream;

/**
 * L'entonnoir en paladium : un entonnoir vanilla qui deplace des piles entieres.
 *
 * <p><b>Ce n'est plus l'original, a la demande du joueur</b> (« c'est cense etre
 * un hopper ameliore »). Le {@code TileEntityPaladiumHopper} de PalaMod n'avait
 * pas d'inventaire et n'aspirait rien au sol : pose seul, il ne faisait rien de
 * visible. Celui-ci fait tout ce que fait l'entonnoir vanilla — cinq cases,
 * l'interface de l'entonnoir, les objets au sol aspires, coupe par la
 * redstone, lu par un comparateur — et garde l'amelioration de PalaMod :
 * <ul>
 *   <li>toutes les 8 ticks ({@code PMiner.HOPPER_RATE}, la cadence vanilla) ;</li>
 *   <li>mais une <b>pile entiere</b> par transfert, la ou l'entonnoir vanilla
 *       deplace un seul objet : 64 fois plus vite.</li>
 * </ul>
 *
 * <p>Chaque cycle pousse d'abord une pile vers l'inventaire vise, puis en prend
 * une dans l'inventaire du dessus — ou, s'il n'y en a pas, ramasse les objets au
 * sol au-dessus de lui. Les faces et restrictions d'insertion des inventaires
 * voisins sont celles du jeu ({@code HopperBlockEntity.addItem}).
 */
public class PaladiumHopperBlockEntity extends BlockEntity implements Hopper, MenuProvider {

    /** {@code PMiner.HOPPER_RATE}, et la cadence de l'entonnoir vanilla. */
    private static final int PERIOD = 8;

    /** Les cinq cases de l'entonnoir : c'est ce qu'attend {@link HopperMenu}. */
    public static final int SIZE = 5;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int ticks;

    public PaladiumHopperBlockEntity(BlockPos pos, BlockState state) {
        super(PalaStorage.PALADIUM_HOPPER.get(), pos, state);
    }

    void serverTick(BlockState state) {
        if (level == null || ++ticks % PERIOD != 0 || !state.getValue(PaladiumHopperBlock.ENABLED)) {
            return;
        }
        boolean changed = push(state.getValue(PaladiumHopperBlock.FACING));
        changed |= pull();
        if (changed) {
            setChanged();
        }
    }

    /** Une pile de l'entonnoir vers l'inventaire vise. */
    private boolean push(Direction facing) {
        if (isEmpty()) {
            return false;
        }
        Container target = HopperBlockEntity.getContainerAt(level, worldPosition.relative(facing));
        if (target == null) {
            return false;
        }
        for (int slot = 0; slot < SIZE; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remainder = HopperBlockEntity.addItem(this, target, stack.copy(), facing.getOpposite());
            int moved = stack.getCount() - remainder.getCount();
            if (moved > 0) {
                stack.shrink(moved);
                target.setChanged();
                return true;
            }
        }
        return false;
    }

    /**
     * Une pile de l'inventaire du dessus ; a defaut, les objets au sol.
     *
     * <p>Comme l'entonnoir vanilla, un cube plein pose dessus bouche l'aspiration
     * au sol, sauf s'il est dans {@code #minecraft:does_not_block_hoppers}.
     */
    private boolean pull() {
        BlockPos above = worldPosition.above();
        Container source = HopperBlockEntity.getContainerAt(level, above);
        if (source != null) {
            for (int slot : extractable(source)) {
                ItemStack stack = source.getItem(slot);
                if (stack.isEmpty() || !source.canTakeItem(this, slot, stack)) {
                    continue;
                }
                ItemStack remainder = HopperBlockEntity.addItem(source, this, stack.copy(), null);
                int moved = stack.getCount() - remainder.getCount();
                if (moved > 0) {
                    source.removeItem(slot, moved);
                    source.setChanged();
                    return true;
                }
            }
            return false;
        }

        BlockState aboveState = level.getBlockState(above);
        if (aboveState.isCollisionShapeFullBlock(level, above) && !aboveState.is(BlockTags.DOES_NOT_BLOCK_HOPPERS)) {
            return false;
        }
        boolean taken = false;
        for (ItemEntity item : HopperBlockEntity.getItemsAtAndAbove(level, this)) {
            taken |= HopperBlockEntity.addItem(this, item);
        }
        return taken;
    }

    /** Les cases que la source laisse prendre par sa face du bas. */
    private static int[] extractable(Container source) {
        if (source instanceof WorldlyContainer worldly) {
            return IntStream.of(worldly.getSlotsForFace(Direction.DOWN))
                    .filter(slot -> worldly.canTakeItemThroughFace(slot, source.getItem(slot), Direction.DOWN))
                    .toArray();
        }
        return IntStream.range(0, source.getContainerSize()).toArray();
    }

    // -- Hopper ---------------------------------------------------------------

    @Override
    public double getLevelX() {
        return worldPosition.getX() + 0.5d;
    }

    @Override
    public double getLevelY() {
        return worldPosition.getY() + 0.5d;
    }

    @Override
    public double getLevelZ() {
        return worldPosition.getZ() + 0.5d;
    }

    @Override
    public boolean isGridAligned() {
        return true;
    }

    // -- Container ------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return SIZE;
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
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
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

    // -- Interface ------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.palamod.paladium_hopper");
    }

    /** L'interface de l'entonnoir vanilla : son ecran est deja enregistre par le jeu. */
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new HopperMenu(containerId, inventory, this);
    }
}

package fr.atraxterra.dohy.agri;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Le stock d'un silo : une case par cereale (le ble, puis celles de DoHy),
 * bien plus profonde qu'une pile.
 *
 * <p><b>Deux etages par cereale.</b> Les entonnoirs, les convoyeurs et les
 * tuyaux d'objets ne connaissent que des piles de 64 : ils lisent la pile
 * d'une case et la modifient sur place. Chaque case montre donc une vraie
 * pile, la <i>vitrine</i>, tenue a mi-hauteur — il y reste toujours de quoi
 * prendre et de la place pour deposer — et le gros du grain dort dans une
 * <i>reserve</i> comptee a part. A chaque changement, la vitrine est remise a
 * niveau depuis la reserve. Le tracteur, lui, verse directement dans la
 * reserve.
 *
 * <p>Les faces suivent la regle des machines de l'agriculture : on entre par
 * le haut et les cotes, on sort par le dessous.
 */
public class SiloBlockEntity extends BlockEntity implements WorldlyContainer {

    /** La hauteur a laquelle une vitrine est tenue : la moitie d'une pile. */
    private static final int WINDOW = 32;

    private final List<String> keys = DoHyAgri.flourKeys();
    private final NonNullList<ItemStack> window = NonNullList.withSize(keys.size(), ItemStack.EMPTY);
    private final int[] reserve = new int[keys.size()];
    private final int[] slots = new int[keys.size()];

    public SiloBlockEntity(BlockPos pos, BlockState state) {
        super(DoHyAgri.SILO_TYPE.get(), pos, state);
        for (int slot = 0; slot < slots.length; slot++) {
            slots[slot] = slot;
        }
    }

    public static int capacity() {
        return AgriConfig.config().farming().siloCapacity();
    }

    // -- Le stock, en cereales ---------------------------------------------------------

    public int kinds() {
        return keys.size();
    }

    /** La cereale d'une case. */
    public Item kind(int slot) {
        return DoHyAgri.grain(keys.get(slot));
    }

    /** La case d'une cereale, ou -1 si le silo ne la stocke pas. */
    public int slotOf(Item item) {
        for (int slot = 0; slot < keys.size(); slot++) {
            if (kind(slot) == item) {
                return slot;
            }
        }
        return -1;
    }

    public int stored(int slot) {
        return reserve[slot] + window.get(slot).getCount();
    }

    public int total() {
        int sum = 0;
        for (int slot = 0; slot < reserve.length; slot++) {
            sum += stored(slot);
        }
        return sum;
    }

    /** Depose jusqu'a {@code count} cereales ; rend ce qui a ete pris. */
    public int deposit(Item item, int count) {
        int slot = slotOf(item);
        int taken = slot < 0 ? 0 : Math.max(0, Math.min(count, capacity() - total()));
        if (taken > 0) {
            reserve[slot] += taken;
            setChanged();
        }
        return taken;
    }

    /** Vide une tremie dans le silo, tant qu'il y a de la place ; rend le nombre de cereales deposees. */
    public int depositAll(Map<Item, Integer> hopper) {
        int moved = 0;
        Iterator<Map.Entry<Item, Integer>> entries = hopper.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Item, Integer> entry = entries.next();
            int taken = deposit(entry.getKey(), entry.getValue());
            moved += taken;
            if (taken >= entry.getValue()) {
                entries.remove();
            } else if (taken > 0) {
                entry.setValue(entry.getValue() - taken);
            }
        }
        return moved;
    }

    /** Retire jusqu'a {@code count} cereales d'une case ; rend ce qui a ete retire. */
    public int withdraw(int slot, int count) {
        if (slot < 0 || slot >= reserve.length) {
            return 0;
        }
        int taken = Math.max(0, Math.min(count, stored(slot)));
        int fromReserve = Math.min(taken, reserve[slot]);
        reserve[slot] -= fromReserve;
        if (taken > fromReserve) {
            window.get(slot).shrink(taken - fromReserve);
        }
        if (taken > 0) {
            setChanged();
        }
        return taken;
    }

    /** Remet chaque vitrine a mi-hauteur : le trop-plein descend en reserve, le manque en remonte. */
    private void settle() {
        for (int slot = 0; slot < reserve.length; slot++) {
            ItemStack shown = window.get(slot);
            if (!shown.isEmpty() && shown.getItem() != kind(slot)) {
                continue;
            }
            int count = shown.getCount();
            if (count > WINDOW) {
                reserve[slot] += count - WINDOW;
                shown.setCount(WINDOW);
            } else if (count < WINDOW && reserve[slot] > 0) {
                int moved = Math.min(WINDOW - count, reserve[slot]);
                reserve[slot] -= moved;
                if (shown.isEmpty()) {
                    window.set(slot, new ItemStack(kind(slot), moved));
                } else {
                    shown.grow(moved);
                }
            }
        }
    }

    @Override
    public void setChanged() {
        settle();
        super.setChanged();
    }

    // -- L'inventaire que voient les entonnoirs et les mods -----------------------------

    @Override
    public int getContainerSize() {
        return window.size();
    }

    @Override
    public boolean isEmpty() {
        return total() == 0;
    }

    @Override
    public ItemStack getItem(int slot) {
        return window.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        // pas de remise a niveau ici : un entonnoir qui echoue remet la pile telle qu'il l'a lue
        return ContainerHelper.removeItem(window, slot, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(window, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        window.set(slot, stack);
        stack.limitSize(getMaxStackSize());
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        window.clear();
        java.util.Arrays.fill(reserve, 0);
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < reserve.length && stack.getItem() == kind(slot) && total() < capacity();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return slots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side != Direction.DOWN && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.DOWN;
    }

    /** Un silo casse rend tout son grain. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        for (int slot = 0; slot < reserve.length; slot++) {
            Item item = kind(slot);
            int max = new ItemStack(item).getMaxStackSize();
            for (int left = stored(slot); left > 0; left -= max) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                        new ItemStack(item, Math.min(left, max)));
            }
            reserve[slot] = 0;
        }
        window.clear();
    }

    // -- Sauvegarde ----------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int slot = 0; slot < reserve.length; slot++) {
            output.putInt("stock_" + keys.get(slot), stored(slot));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        window.clear();
        for (int slot = 0; slot < reserve.length; slot++) {
            reserve[slot] = Math.max(0, input.getIntOr("stock_" + keys.get(slot), 0));
        }
        settle();
    }
}

package fr.atraxterra.dohy.faction;

import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Le coffre de la banque nationale : les minerais que versent les mines de la
 * Terre ({@code earth/mine/EarthMines}), a cote de l'argent de la banque. On les
 * en sort depuis l'onglet Banque. Garde a la mort ; perdu, comme la banque, si
 * la faction est dissoute.
 *
 * @param items les piles du coffre, sans case vide, {@link #SLOTS} au plus
 */
public record FactionVault(List<ItemStack> items) {

    /** Six rangees de neuf, comme un grand coffre. */
    public static final int SLOTS = 54;

    public static final FactionVault EMPTY = new FactionVault(List.of());

    public static final MapCodec<FactionVault> MAP_CODEC = ItemStack.CODEC.listOf().fieldOf("items")
            .xmap(FactionVault::new, FactionVault::items);

    public FactionVault {
        List<ItemStack> copy = new ArrayList<>(items.size());
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                copy.add(stack.copy());
            }
        }
        items = List.copyOf(copy);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int used() {
        return items.size();
    }

    /** Le nombre d'objets du coffre, toutes piles comprises. */
    public int count() {
        int count = 0;
        for (ItemStack stack : items) {
            count += stack.getCount();
        }
        return count;
    }

    public static FactionVault of(ServerPlayer player) {
        return player.getData(Factions.VAULT.get());
    }

    private static List<ItemStack> copy(FactionVault vault) {
        List<ItemStack> slots = new ArrayList<>(vault.items.size());
        for (ItemStack stack : vault.items) {
            slots.add(stack.copy());
        }
        return slots;
    }

    private static void save(ServerPlayer player, List<ItemStack> slots) {
        if (slots.isEmpty()) {
            player.removeData(Factions.VAULT.get());
        } else {
            player.setData(Factions.VAULT.get(), new FactionVault(slots));
        }
    }

    /** Range ces objets dans le coffre, en completant les piles d'abord ; rend combien n'y ont pas tenu. */
    public static int store(ServerPlayer player, List<ItemStack> stacks) {
        List<ItemStack> slots = copy(of(player));
        int lost = 0;
        for (ItemStack stack : stacks) {
            ItemStack left = stack.copy();
            for (ItemStack slot : slots) {
                if (left.isEmpty()) {
                    break;
                }
                if (ItemStack.isSameItemSameComponents(slot, left) && slot.getCount() < slot.getMaxStackSize()) {
                    int moved = Math.min(left.getCount(), slot.getMaxStackSize() - slot.getCount());
                    slot.grow(moved);
                    left.shrink(moved);
                }
            }
            while (!left.isEmpty() && slots.size() < SLOTS) {
                int moved = Math.min(left.getCount(), left.getMaxStackSize());
                slots.add(left.copyWithCount(moved));
                left.shrink(moved);
            }
            lost += left.getCount();
        }
        save(player, slots);
        return lost;
    }

    /** La pile de cette case passe dans l'inventaire ; ce qui n'y tient pas reste. Rend le nombre d'objets sortis. */
    public static int take(ServerPlayer player, int index) {
        List<ItemStack> slots = copy(of(player));
        if (index < 0 || index >= slots.size()) {
            return 0;
        }
        ItemStack stack = slots.get(index);
        int before = stack.getCount();
        player.getInventory().add(stack);
        int moved = before - stack.getCount();
        if (stack.isEmpty()) {
            slots.remove(index);
        }
        save(player, slots);
        return moved;
    }

    /** Tout ce qui tient dans l'inventaire en sort ; rend le nombre d'objets sortis. */
    public static int takeAll(ServerPlayer player) {
        List<ItemStack> slots = copy(of(player));
        int moved = 0;
        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack stack : slots) {
            int before = stack.getCount();
            player.getInventory().add(stack);
            moved += before - stack.getCount();
            if (!stack.isEmpty()) {
                kept.add(stack);
            }
        }
        save(player, kept);
        return moved;
    }

    /**
     * Retire du coffre jusqu'a {@code units} unites de ce qui passe le filtre : {@code weight} dit
     * combien d'unites vaut un objet (0 s'il ne compte pas ; un bloc de fer en vaut neuf). Les objets
     * a une unite partent d'abord, puis les blocs, entiers. Rend les unites retirees (lot 24b :
     * le fer des ateliers d'armes).
     */
    public static int consume(ServerPlayer player, java.util.function.ToIntFunction<ItemStack> weight, int units) {
        if (units <= 0) {
            return 0;
        }
        List<ItemStack> slots = copy(of(player));
        int taken = 0;
        for (int round = 0; round < 2 && taken < units; round++) {
            for (ItemStack slot : slots) {
                if (taken >= units) {
                    break;
                }
                int each = weight.applyAsInt(slot);
                if (each <= 0 || (round == 0) != (each == 1)) {
                    continue;
                }
                int needed = (units - taken + each - 1) / each;
                int used = Math.min(needed, slot.getCount());
                slot.shrink(used);
                taken += used * each;
            }
        }
        slots.removeIf(ItemStack::isEmpty);
        save(player, slots);
        return taken;
    }

    /** La faction est dissoute : le coffre part avec la banque. */
    public static void clear(ServerPlayer player) {
        player.removeData(Factions.VAULT.get());
    }
}

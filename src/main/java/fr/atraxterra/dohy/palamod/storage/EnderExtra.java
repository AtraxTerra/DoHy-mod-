package fr.atraxterra.dohy.palamod.storage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Les rangees du coffre de l'Ender au-dela des 27 cases du jeu, gardees sur le joueur : la
 * rangee du coffre en paladium, puis celles des grades ({@code grade.GradeEnder}), quatre au plus.
 */
public record EnderExtra(NonNullList<ItemStack> items) {

    /** Quatre rangees : 63 cases avec les 27 du jeu, la place de Divinite avec Premium. */
    public static final int MAX_SLOTS = 36;

    public static final MapCodec<EnderExtra> MAP_CODEC = ItemStack.OPTIONAL_CODEC.listOf()
            .optionalFieldOf("items", List.of())
            .xmap(EnderExtra::of, extra -> List.copyOf(extra.items()));

    public static EnderExtra empty() {
        return new EnderExtra(NonNullList.withSize(MAX_SLOTS, ItemStack.EMPTY));
    }

    private static EnderExtra of(List<ItemStack> stacks) {
        NonNullList<ItemStack> items = NonNullList.withSize(MAX_SLOTS, ItemStack.EMPTY);
        for (int slot = 0; slot < items.size() && slot < stacks.size(); slot++) {
            items.set(slot, stacks.get(slot));
        }
        return new EnderExtra(items);
    }

    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }
}

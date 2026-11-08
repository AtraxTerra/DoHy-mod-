package fr.atraxterra.dohy.palamod.storage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** La rangee en plus du coffre de l'End en paladium, gardee sur le joueur. */
public record EnderExtra(NonNullList<ItemStack> items) {

    public static final MapCodec<EnderExtra> MAP_CODEC = ItemStack.OPTIONAL_CODEC.listOf()
            .optionalFieldOf("items", List.of())
            .xmap(EnderExtra::of, extra -> List.copyOf(extra.items()));

    public static EnderExtra empty() {
        return new EnderExtra(NonNullList.withSize(PaladiumEnderChestBlock.EXTRA_SLOTS, ItemStack.EMPTY));
    }

    private static EnderExtra of(List<ItemStack> stacks) {
        NonNullList<ItemStack> items = NonNullList.withSize(PaladiumEnderChestBlock.EXTRA_SLOTS, ItemStack.EMPTY);
        for (int slot = 0; slot < items.size() && slot < stacks.size(); slot++) {
            items.set(slot, stacks.get(slot));
        }
        return new EnderExtra(items);
    }

    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }
}

package fr.atraxterra.dohy.palamod.golem;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * {@code MiniGolemRepair} : la cle, le mini golem, un bloc de paladium, en
 * colonne. Le mini golem redevient une pierre de gardien, usure remise a 144.
 */
public class MiniGolemRepairRecipe extends CustomRecipe {

    /**
     * L'unique instance. Le codec reseau de 26.2 ({@code StreamCodec.unit})
     * n'encode qu'une valeur egale a celle qu'il connait : deux instances
     * distinctes faisaient echouer l'envoi des recettes, et le joueur etait
     * deconnecte avant meme d'entrer dans le monde.
     */
    public static final MiniGolemRepairRecipe INSTANCE = new MiniGolemRepairRecipe();

    @Override
    public boolean equals(Object other) {
        return other instanceof MiniGolemRepairRecipe;
    }

    @Override
    public int hashCode() {
        return MiniGolemRepairRecipe.class.hashCode();
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return input.width() == 1 && input.height() == 3
                && input.getItem(0, 0).is(PalaGolem.WRENCH.get())
                && input.getItem(0, 1).is(PalaGolem.MINI_GOLEM.get())
                && MiniGolemRecipe.block(input.getItem(0, 2));
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        GolemData data = input.getItem(0, 1).getOrDefault(PalaGolem.GOLEM_DATA.get(), GolemData.EMPTY);
        ItemStack result = new ItemStack(PalaGolem.GUARDIAN_STONE.get());
        result.set(PalaGolem.GOLEM_DATA.get(), data.withDurability(GolemData.MINI_GOLEM_DURABILITY));
        return result;
    }

    @Override
    public RecipeSerializer<MiniGolemRepairRecipe> getSerializer() {
        return PalaGolem.MINI_GOLEM_REPAIR_RECIPE.get();
    }
}

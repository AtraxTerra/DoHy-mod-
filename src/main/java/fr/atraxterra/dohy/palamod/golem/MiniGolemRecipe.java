package fr.atraxterra.dohy.palamod.golem;

import fr.atraxterra.dohy.palamod.PalaBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * {@code MiniGolemRecipe} : une pierre de gardien habitee entre deux blocs de
 * paladium, un troisieme dessous. Le mini golem garde tout le golem de la
 * pierre ; une pierre vide ne donne rien.
 *
 * <pre>
 *   P S P
 *   . P .
 * </pre>
 */
public class MiniGolemRecipe extends CustomRecipe {

    /**
     * L'unique instance. Le codec reseau de 26.2 ({@code StreamCodec.unit})
     * n'encode qu'une valeur egale a celle qu'il connait : deux instances
     * distinctes faisaient echouer l'envoi des recettes, et le joueur etait
     * deconnecte avant meme d'entrer dans le monde.
     */
    public static final MiniGolemRecipe INSTANCE = new MiniGolemRecipe();

    @Override
    public boolean equals(Object other) {
        return other instanceof MiniGolemRecipe;
    }

    @Override
    public int hashCode() {
        return MiniGolemRecipe.class.hashCode();
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 2) {
            return false;
        }
        return block(input.getItem(0, 0)) && block(input.getItem(2, 0)) && block(input.getItem(1, 1))
                && input.getItem(1, 0).is(PalaGolem.GUARDIAN_STONE.get())
                && input.getItem(1, 0).has(PalaGolem.GOLEM_DATA.get())
                && input.getItem(0, 1).isEmpty() && input.getItem(2, 1).isEmpty();
    }

    static boolean block(ItemStack stack) {
        return stack.is(PalaBlocks.PALADIUM_BLOCK.get().asItem());
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack result = new ItemStack(PalaGolem.MINI_GOLEM.get());
        result.set(PalaGolem.GOLEM_DATA.get(), input.getItem(1, 0).get(PalaGolem.GOLEM_DATA.get()).withDeath(
                java.util.Optional.empty()));
        return result;
    }

    @Override
    public RecipeSerializer<MiniGolemRecipe> getSerializer() {
        return PalaGolem.MINI_GOLEM_RECIPE.get();
    }
}

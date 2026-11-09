package fr.atraxterra.dohy.palamod.legendary;

import fr.atraxterra.dohy.palamod.PalaItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * {@code EndiumGauntletRecipe} : une pierre legendaire entre deux lingots
 * d'endium, un coeur en paladium dessus et dessous. Le gant nait avec cette
 * pierre, et la recharge qu'elle avait ({@code transferCooldown}).
 *
 * <pre>
 *   . C .
 *   E P E
 *   . C .
 * </pre>
 *
 * <p>Comme l'original, la recette ne prend jamais un gant existant : les
 * pierres suivantes s'ajoutent au grinder.
 */
public class EndiumGauntletRecipe extends CustomRecipe {

    /** L'unique instance : le codec reseau ({@code StreamCodec.unit}) l'exige. */
    public static final EndiumGauntletRecipe INSTANCE = new EndiumGauntletRecipe();

    @Override
    public boolean equals(Object other) {
        return other instanceof EndiumGauntletRecipe;
    }

    @Override
    public int hashCode() {
        return EndiumGauntletRecipe.class.hashCode();
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3) {
            return false;
        }
        return input.getItem(0, 0).isEmpty() && input.getItem(2, 0).isEmpty()
                && input.getItem(0, 2).isEmpty() && input.getItem(2, 2).isEmpty()
                && core(input.getItem(1, 0)) && core(input.getItem(1, 2))
                && ingot(input.getItem(0, 1)) && ingot(input.getItem(2, 1))
                && LegendaryStoneItem.insertable(input.getItem(1, 1));
    }

    private static boolean core(ItemStack stack) {
        return stack.is(PalaItems.PALADIUM_CORE.get());
    }

    private static boolean ingot(ItemStack stack) {
        return stack.is(PalaItems.ENDIUM_INGOT.get());
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack stone = input.getItem(1, 1);
        LegendaryEffect effect = ((LegendaryStoneItem) stone.getItem()).effect();
        ItemStack gauntlet = new ItemStack(PalaLegendary.ENDIUM_GAUNTLET.get());
        EndiumGauntletItem.store(gauntlet, GauntletData.EMPTY.withStone(effect, LegendaryStoneItem.lastUse(stone)));
        return gauntlet;
    }

    @Override
    public RecipeSerializer<EndiumGauntletRecipe> getSerializer() {
        return PalaLegendary.GAUNTLET_RECIPE.get();
    }
}

package fr.atraxterra.dohy.palamod.machine;

import fr.atraxterra.dohy.palamod.PalaItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Le combustible des machines de Paladium : celui du fourneau vanilla, plus les
 * charbons mixes.
 *
 * <p><b>Choix du joueur, pas de PalaMod.</b> Dans le jar, les charbons mixes ne
 * brulent nulle part : ce sont les reactifs du crusher, et la Palafurnace ne
 * connait que les combustibles vanilla ({@code PaladiumFurnaceLogic.getItemBurnTime}).
 * Ils servent ici de carburant a la Palafurnace et a la Palaforge, et serviront a
 * la Palamachine quand elle sera portee. Ils ne brulent pas dans le fourneau
 * vanilla : c'est un combustible de machine.
 *
 * <p>Les durees sont CALIBREES, aucune n'existe dans les sources. Le charbon fait
 * 1 600 ticks (8 cuissons) ; chaque palier fait mieux que le precedent, et le
 * charbon mixe en paladium, une seule unite par fabrication et le plus cher,
 * egale un bloc de charbon.
 */
public final class PalaFuels {

    /** Or : 12 cuissons, comme un baton de blaze. */
    private static final int GOLD = 2_400;
    /** Amethyste : 20 cuissons. */
    private static final int AMETHYST = 4_000;
    /** Titane : 32 cuissons. */
    private static final int TITANE = 6_400;
    /** Paladium : 80 cuissons, un bloc de charbon. */
    private static final int PALADIUM = 16_000;

    private PalaFuels() {
    }

    /** Duree de combustion en ticks, 0 si l'objet ne brule pas. */
    public static int burnTime(Level level, ItemStack stack) {
        if (stack.isEmpty() || level == null) {
            return 0;
        }
        if (stack.is(PalaItems.GOLD_MIXED_COAL.get())) {
            return GOLD;
        }
        if (stack.is(PalaItems.AMETHYST_MIXED_COAL.get())) {
            return AMETHYST;
        }
        if (stack.is(PalaItems.TITANE_MIXED_COAL.get())) {
            return TITANE;
        }
        if (stack.is(PalaItems.PALA_MIXED_COAL.get())) {
            return PALADIUM;
        }
        return stack.getBurnTime(RecipeType.SMELTING, level.fuelValues());
    }

    public static boolean isFuel(Level level, ItemStack stack) {
        return burnTime(level, stack) > 0;
    }
}

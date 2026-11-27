package fr.atraxterra.dohy.palamod.unlock;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Les recettes de la table d'alchimiste ({@code PRegister_Craft.AlchemyCreatorRecipies}).
 *
 * <p>Quatre entrees en position fixe, comparees objet par objet
 * ({@code isSameKey}) : un ingredient, une verrue du Nether, un second
 * ingredient, puis une fiole vide (les potions) ou une fleche (les fleches,
 * seize a la fois). Une unite de chaque entree part a chaque fabrication.
 */
public final class AlchemyCreatorRecipes {

    /** Une recette : quatre objets en position, un resultat. */
    public record Recipe(List<Item> inputs, Item result, int count) {

        boolean matches(List<ItemStack> stacks) {
            for (int index = 0; index < inputs.size(); index++) {
                if (!stacks.get(index).is(inputs.get(index))) {
                    return false;
                }
            }
            return true;
        }

        public ItemStack output() {
            return new ItemStack(result, count);
        }
    }

    private static List<Recipe> potions;
    private static List<Recipe> arrows;

    private AlchemyCreatorRecipes() {
    }

    /** L'onglet des potions ({@code AlchemyCreatorPotionRecipies}). */
    public static List<Recipe> potions() {
        if (potions == null) {
            List<Recipe> list = new ArrayList<>();
            list.add(new Recipe(List.of(Items.SPIDER_EYE, Items.NETHER_WART, Items.POISONOUS_POTATO, Items.GLASS_BOTTLE),
                    PalaUnlocks.POISON_POTION.get(), 1));
            list.add(new Recipe(List.of(Items.BLAZE_POWDER, Items.NETHER_WART, Items.FIRE_CHARGE, Items.GLASS_BOTTLE),
                    PalaUnlocks.FIRE_POTION.get(), 1));
            list.add(new Recipe(List.of(palamod("wither_skull_fragment"), Items.NETHER_WART, Items.BONE,
                    Items.GLASS_BOTTLE), PalaUnlocks.WITHER_POTION.get(), 1));
            list.add(new Recipe(List.of(Items.BLAZE_POWDER, Items.NETHER_WART, Items.SPIDER_EYE, Items.GLASS_BOTTLE),
                    PalaUnlocks.SICKNESS_POTION.get(), 1));
            potions = List.copyOf(list);
        }
        return potions;
    }

    /** L'onglet des fleches ({@code AlchemyCreatorArrowRecipies}) : seize a la fois. */
    public static List<Recipe> arrows() {
        if (arrows == null) {
            List<Recipe> list = new ArrayList<>();
            list.add(new Recipe(List.of(Items.SPIDER_EYE, Items.NETHER_WART, Items.POISONOUS_POTATO, Items.ARROW),
                    PalaUnlocks.POISON_ARROW.get(), 16));
            list.add(new Recipe(List.of(palamod("wither_skull_fragment"), Items.NETHER_WART, Items.BONE, Items.ARROW),
                    PalaUnlocks.WITHER_ARROW.get(), 16));
            list.add(new Recipe(List.of(Items.FERMENTED_SPIDER_EYE, Items.NETHER_WART, Items.CARROT, Items.ARROW),
                    PalaUnlocks.SLOWNESS_ARROW.get(), 16));
            list.add(new Recipe(List.of(Items.ENDER_PEARL, Items.NETHER_WART, Items.ENDER_PEARL, Items.ARROW),
                    palamod("switch_arrow"), 16));
            list.add(new Recipe(List.of(Items.SNOW_BLOCK, Items.NETHER_WART, Items.SLIME_BALL, Items.ARROW),
                    PalaUnlocks.FROST_ARROW.get(), 16));
            arrows = List.copyOf(list);
        }
        return arrows;
    }

    static Recipe find(List<Recipe> recipes, List<ItemStack> inputs) {
        if (inputs.stream().anyMatch(ItemStack::isEmpty)) {
            return null;
        }
        for (Recipe recipe : recipes) {
            if (recipe.matches(inputs)) {
                return recipe;
            }
        }
        return null;
    }

    private static Item palamod(String name) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, name));
        if (item == Items.AIR) {
            throw new IllegalStateException("table d'alchimiste : objet inconnu palamod:" + name);
        }
        return item;
    }
}

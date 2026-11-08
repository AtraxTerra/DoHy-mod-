package fr.atraxterra.dohy.palamod.machine;

import fr.atraxterra.dohy.palamod.PalaBlocks;
import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.equipment.PalaEquipment;
import fr.atraxterra.dohy.palamod.smeltery.PalaSmeltery;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * La forge du grinder ({@code PSRegister_Craft.paladiumGrinderRecipies}) : deux
 * objets, dans cet ordre, et un cout en paladium du reservoir. Un pattern et le
 * socle donnent une tete d'outil (ou un lingot, un bloc) ; une tete et un baton
 * donnent l'outil. Les patterns ne s'usent jamais.
 *
 * <table>
 *   <tr><th>case 1</th><th>case 2</th><th>resultat</th><th>paladium</th></tr>
 *   <tr><td>pattern de broadsword</td><td>socle</td><td>tete de broadsword</td><td>4</td></tr>
 *   <tr><td>pattern de pioche</td><td>socle</td><td>tete de pioche</td><td>3</td></tr>
 *   <tr><td>pattern de fastsword</td><td>socle</td><td>tete de fastsword</td><td>1</td></tr>
 *   <tr><td>pattern d'epee</td><td>socle</td><td>tete d'epee</td><td>2</td></tr>
 *   <tr><td>pattern de marteau</td><td>socle</td><td>tete de marteau</td><td>6</td></tr>
 *   <tr><td>pattern de pelle</td><td>socle</td><td>tete de pelle</td><td>1</td></tr>
 *   <tr><td>pattern de hache</td><td>socle</td><td>tete de hache</td><td>3</td></tr>
 *   <tr><td>pattern de lingot</td><td>socle</td><td>lingot de paladium</td><td>1</td></tr>
 *   <tr><td>pattern de bloc</td><td>socle</td><td>bloc de paladium</td><td>9</td></tr>
 *   <tr><td>tete de broadsword</td><td>baton en paladium / en bois</td><td>broadsword en paladium</td><td>0 / 1</td></tr>
 *   <tr><td>tete de fastsword</td><td>baton en paladium / en bois</td><td>fastsword en paladium</td><td>0 / 1</td></tr>
 *   <tr><td>tete de marteau</td><td>baton en paladium / en bois</td><td>marteau en paladium</td><td>0 / 1</td></tr>
 *   <tr><td>tete de pioche, d'epee, de hache, de pelle</td><td>baton en bois</td><td>l'outil en paladium</td><td>0</td></tr>
 * </table>
 */
public record GrinderRecipes(Item first, Item second, Item result, int cost) {

    private static List<GrinderRecipes> recipes;

    /** Toutes les recettes, construites au premier appel : les objets sont enregistres. */
    static List<GrinderRecipes> all() {
        if (recipes != null) {
            return recipes;
        }
        Item socket = PalaSmeltery.SOCKET_PATTERN.get();
        Item stick = Items.STICK;
        Item paladiumStick = PalaItems.PALADIUM_STICK.get();
        List<GrinderRecipes> built = new ArrayList<>();
        built.add(new GrinderRecipes(PalaSmeltery.BROADSWORD_PATTERN.get(), socket, PalaSmeltery.BROADSWORD_HEAD.get(), 4));
        built.add(new GrinderRecipes(PalaSmeltery.PICKAXE_PATTERN.get(), socket, PalaSmeltery.PICKAXE_HEAD.get(), 3));
        built.add(new GrinderRecipes(PalaSmeltery.FASTSWORD_PATTERN.get(), socket, PalaSmeltery.FASTSWORD_HEAD.get(), 1));
        built.add(new GrinderRecipes(PalaSmeltery.SWORD_PATTERN.get(), socket, PalaSmeltery.SWORD_HEAD.get(), 2));
        built.add(new GrinderRecipes(PalaSmeltery.HAMMER_PATTERN.get(), socket, PalaSmeltery.HAMMER_HEAD.get(), 6));
        built.add(new GrinderRecipes(PalaSmeltery.SHOVEL_PATTERN.get(), socket, PalaSmeltery.SHOVEL_HEAD.get(), 1));
        built.add(new GrinderRecipes(PalaSmeltery.AXE_PATTERN.get(), socket, PalaSmeltery.AXE_HEAD.get(), 3));
        built.add(new GrinderRecipes(PalaSmeltery.INGOT_PATTERN.get(), socket, PalaItems.PALADIUM_INGOT.get(), 1));
        built.add(new GrinderRecipes(PalaSmeltery.BLOCK_PATTERN.get(), socket, PalaBlocks.PALADIUM_BLOCK.get().asItem(), 9));
        built.add(new GrinderRecipes(PalaSmeltery.BROADSWORD_HEAD.get(), paladiumStick, PalaSmeltery.PALADIUM_BROADSWORD.get(), 0));
        built.add(new GrinderRecipes(PalaSmeltery.BROADSWORD_HEAD.get(), stick, PalaSmeltery.PALADIUM_BROADSWORD.get(), 1));
        built.add(new GrinderRecipes(PalaSmeltery.PICKAXE_HEAD.get(), stick, PalaEquipment.PALADIUM_PICKAXE.get(), 0));
        built.add(new GrinderRecipes(PalaSmeltery.SWORD_HEAD.get(), stick, PalaEquipment.PALADIUM_SWORD.get(), 0));
        built.add(new GrinderRecipes(PalaSmeltery.FASTSWORD_HEAD.get(), paladiumStick, PalaSmeltery.PALADIUM_FASTSWORD.get(), 0));
        built.add(new GrinderRecipes(PalaSmeltery.FASTSWORD_HEAD.get(), stick, PalaSmeltery.PALADIUM_FASTSWORD.get(), 1));
        built.add(new GrinderRecipes(PalaSmeltery.HAMMER_HEAD.get(), stick, PalaSmeltery.PALADIUM_HAMMER.get(), 1));
        built.add(new GrinderRecipes(PalaSmeltery.HAMMER_HEAD.get(), paladiumStick, PalaSmeltery.PALADIUM_HAMMER.get(), 0));
        built.add(new GrinderRecipes(PalaSmeltery.AXE_HEAD.get(), stick, PalaEquipment.PALADIUM_AXE.get(), 0));
        built.add(new GrinderRecipes(PalaSmeltery.SHOVEL_HEAD.get(), stick, PalaEquipment.PALADIUM_SHOVEL.get(), 0));
        recipes = List.copyOf(built);
        return recipes;
    }

    /** {@code isSameKey} : le meme objet dans chaque case, dans l'ordre. */
    static GrinderRecipes find(ItemStack first, ItemStack second) {
        for (GrinderRecipes recipe : all()) {
            if (first.is(recipe.first) && second.is(recipe.second)) {
                return recipe;
            }
        }
        return null;
    }

    /** Ce qui peut aller dans la premiere case : patterns (sauf le socle) et tetes. */
    static boolean isFirst(ItemStack stack) {
        return all().stream().anyMatch(recipe -> stack.is(recipe.first));
    }

    /** Ce qui peut aller dans la seconde : le socle et les batons. */
    static boolean isSecond(ItemStack stack) {
        return all().stream().anyMatch(recipe -> stack.is(recipe.second));
    }
}

package fr.atraxterra.dohy.palamod.pvp;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Les recettes de la palamachine ({@code PRegister_Craft.registerPaladiumMachineRecipies}).
 *
 * <p>Cinq entrees en position fixe — la piece centrale, puis les quatre
 * ingredients — et un resultat. La comparaison se fait objet par objet, case
 * par case ({@code isSameKey}) : l'usure de la piece centrale n'entre pas en
 * compte, c'est ce qui permet de reparer un stick, un anneau ou une corne use.
 *
 * <p>Portees : les sticks, les anneaux et les cornes de monture — creation et
 * reparation —, et la recharge de l'extracteur d'alchimiste (l'extracteur
 * entame en ressort a 144 utilisations ; le compteur {@code refill} qu'elle
 * tenait ne servait a rien d'autre). Laissees : les detecteurs de claim et les
 * runes de donjon, non portes.
 *
 * <p>La defense d'elephant ({@code TUSK}), centre des recettes de cornes,
 * n'existe pas ici : un bloc d'os la remplace, comme dans l'ancien substitut
 * a l'etabli qu'elles remplacent.
 *
 * <p>Les noms sont ceux du registre ; la table est construite au premier
 * usage, une fois les objets enregistres.
 */
public final class PalamachineRecipes {

    /** Une recette : cinq objets en position, un resultat. */
    public record Recipe(List<Item> inputs, Item result) {

        boolean matches(List<ItemStack> stacks) {
            for (int index = 0; index < inputs.size(); index++) {
                if (!stacks.get(index).is(inputs.get(index))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static List<Recipe> recipes;

    private PalamachineRecipes() {
    }

    public static Recipe find(List<ItemStack> inputs) {
        for (Recipe recipe : all()) {
            if (recipe.matches(inputs)) {
                return recipe;
            }
        }
        return null;
    }

    public static List<Recipe> all() {
        if (recipes == null) {
            List<Recipe> built = new ArrayList<>();
            // Anneaux en paladium : creation, puis reparation.
            add(built, "small_ring", "paladium_block", "paladium_ingot", "paladium_ingot", "paladium_ingot", "paladium_ingot");
            add(built, "medium_ring", "small_ring", "paladium_block", "paladium_ingot", "paladium_block", "paladium_ingot");
            add(built, "big_ring", "medium_ring", "compressed_paladium", "paladium_ingot", "paladium_ingot", "paladium_ingot");
            // Anneaux en endium.
            add(built, "endium_small_ring", "medium_ring", "endium_fragment", "endium_fragment", "endium_fragment", "endium_fragment");
            add(built, "endium_medium_ring", "endium_small_ring", "paladium_block", "findium_block", "paladium_block", "endium_nugget");
            add(built, "endium_big_ring", "endium_medium_ring", "compressed_paladium", "endium_nugget", "compressed_paladium", "endium_nugget");
            add(built, "small_ring", "small_ring", "paladium_ingot", "paladium_ingot", "paladium_ingot", "paladium_ingot");
            add(built, "medium_ring", "medium_ring", "paladium_particle", "paladium_ingot", "paladium_particle", "paladium_block");
            add(built, "big_ring", "big_ring", "paladium_block", "paladium_block", "paladium_ingot", "paladium_block");
            add(built, "endium_small_ring", "endium_small_ring", "endium_fragment", "compressed_paladium", "endium_fragment", "compressed_paladium");
            add(built, "endium_medium_ring", "endium_medium_ring", "compressed_paladium", "compressed_endium", "compressed_paladium", "compressed_paladium");
            add(built, "endium_big_ring", "endium_big_ring", "compressed_paladium", "compressed_endium", "compressed_paladium", "compressed_endium");
            // Sticks : creation.
            add(built, "speed_stick", "speed_orb", "amethyst_stick", "compressed_amethyst", "titane_stick", "compressed_titane");
            add(built, "strength_stick", "strength_orb", "paladium_stick", "paladium_block", "titane_stick", "paladium_block");
            add(built, "jump_stick", "jump_orb", "titane_stick", "slimepad", "titane_stick", "slimepad");
            add(built, "heal_stick", "heal_orb", "paladium_stick", "paladium_block", "paladium_stick", "paladium_block");
            add(built, "god_stick", "strength_stick", "heal_stick", "paladium_block", "speed_stick", "findium");
            add(built, "hyper_jump_stick", "jump_orb", "jump_stick", "paladium_block", "jump_stick", "paladium_block");
            add(built, "damage_stick", "titane_sword", "amethyst_stick", "paladium_spike", "amethyst_stick", "paladium_spike");
            // Cornes de monture : creation (la defense devient un bloc d'os).
            add(built, "amethyst_horn", Items.BONE_BLOCK, "amethyst_ingot", "amethyst_block", "amethyst_ingot", "amethyst_block");
            add(built, "titane_horn", Items.BONE_BLOCK, "titane_ingot", "titane_block", "titane_ingot", "titane_block");
            add(built, "paladium_horn", Items.BONE_BLOCK, "paladium_ingot", "paladium_block", "paladium_ingot", "paladium_block");
            // Sticks : reparation.
            add(built, "heal_stick", "heal_stick", "paladium_block", "titane_block", "paladium_block", "titane_block");
            add(built, "speed_stick", "speed_stick", "titane_block", "compressed_amethyst", "paladium_ingot", "titane_block");
            add(built, "strength_stick", "strength_stick", "paladium_block", "titane_block", "paladium_block", "titane_block");
            add(built, "jump_stick", "jump_stick", "titane_stick", "slimepad", "titane_stick", "paladium_ingot");
            add(built, "god_stick", "god_stick", "paladium_block", "paladium_block", "paladium_block", "paladium_block");
            add(built, "damage_stick", "damage_stick", "amethyst_block", "amethyst_stick", "amethyst_stick", "paladium_spike");
            add(built, "hyper_jump_stick", "hyper_jump_stick", "slimepad", "paladium_core", "paladium_core", "heal_stick");
            // Cornes de monture : reparation.
            add(built, "amethyst_horn", "amethyst_horn", "amethyst_stick", "amethyst_stick", "amethyst_stick", "amethyst_block");
            add(built, "titane_horn", "titane_horn", "titane_stick", "titane_stick", "titane_stick", "titane_block");
            add(built, "paladium_horn", "paladium_horn", "paladium_stick", "paladium_core", "paladium_stick", "paladium_block");
            // L'extracteur : trois lingots de paladium et un seau d'eau, qui part avec.
            add(built, "extractor", "extractor", "paladium_ingot", "paladium_ingot", Items.WATER_BUCKET, "paladium_ingot");
            recipes = List.copyOf(built);
        }
        return recipes;
    }

    private static void add(List<Recipe> into, String result, Object... inputs) {
        List<Item> items = new ArrayList<>();
        for (Object input : inputs) {
            items.add(input instanceof Item item ? item : item((String) input));
        }
        into.add(new Recipe(List.copyOf(items), item(result)));
    }

    private static Item item(String name) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, name));
        if (item == Items.AIR) {
            throw new IllegalStateException("palamachine : objet inconnu palamod:" + name);
        }
        return item;
    }
}

package fr.atraxterra.dohy.palamod.alchemist;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Les recettes a objets du chaudron ({@code CauldronController.addRecipe} dans
 * {@code PAlchimiste.preInit}), dans leur ordre de priorite :
 *
 * <ol>
 *   <li>potion de foudre + buche d'erable : 2 bois d'erable brillant ;</li>
 *   <li>potion de foudre + buche de jacaranda : 2 bois de jacaranda brillant ;</li>
 *   <li>potion de foudre + buche d'ostrya : 2 bois d'ostrya brillant ;</li>
 *   <li>potion de foudre + buche de judée : 2 bois de judée brillant.</li>
 * </ol>
 *
 * <p><b>Ecart</b> : la cinquieme, poudre lumineuse → 3 pepites d'or, n'est pas
 * portee. Chez Paladium l'or ne valait rien ; ici il se vend, et la poudre
 * s'achete au shop (4 $) comme chez le clerc (une emeraude) : trois pepites
 * a 9 $ font de l'argent infini, sans limite de debit — jusqu'a 32 recettes
 * par tick. Retiree a la demande du joueur (chapitre economique).
 *
 * <p>Une recette se fait des que chacun de ses ingredients est dans le bassin,
 * les autres objets n'y changent rien. <b>Ecart</b> : l'original retirait un
 * exemplaire a <i>chaque</i> tas de l'ingredient ; ici, a un seul.
 */
public record CauldronRecipe(List<Supplier<? extends Item>> inputs, Supplier<ItemStack> output) {

    private static final List<CauldronRecipe> RECIPES = new ArrayList<>();

    static {
        for (Sap sap : new Sap[]{Sap.ERABLE, Sap.JACARANDA, Sap.OSTRYA, Sap.JUDEECERCIS}) {
            RECIPES.add(new CauldronRecipe(
                    List.of(PalaAlchemist.LIGHTNING_POTION, () -> PalaWoods.log(sap).asItem()),
                    () -> new ItemStack(PalaWoods.shiny(sap), 2)));
        }
    }

    public ItemStack result() {
        return output.get();
    }

    /** La premiere recette dont tous les ingredients sont la, ou {@code null}. */
    static CauldronRecipe match(List<ItemEntity> candidates) {
        for (CauldronRecipe recipe : RECIPES) {
            if (recipe.inputs.stream().allMatch(input -> find(candidates, input.get()) != null)) {
                return recipe;
            }
        }
        return null;
    }

    void consume(List<ItemEntity> candidates) {
        for (Supplier<? extends Item> input : inputs) {
            ItemEntity entity = find(candidates, input.get());
            if (entity != null) {
                entity.getItem().shrink(1);
                if (entity.getItem().isEmpty()) {
                    entity.discard();
                }
            }
        }
    }

    private static ItemEntity find(List<ItemEntity> candidates, Item item) {
        for (ItemEntity entity : candidates) {
            if (entity.isAlive() && !entity.getItem().isEmpty() && entity.getItem().is(item)) {
                return entity;
            }
        }
        return null;
    }
}

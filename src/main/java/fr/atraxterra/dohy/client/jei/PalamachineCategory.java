package fr.atraxterra.dohy.client.jei;

import fr.atraxterra.dohy.palamod.pvp.PalaPvp;
import fr.atraxterra.dohy.palamod.pvp.PalamachineRecipes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * La palamachine dans JEI : la piece centrale entouree de ses quatre
 * ingredients, en croix comme dans la machine ; dix secondes de travail.
 */
public class PalamachineCategory extends AbstractRecipeCategory<PalamachineRecipes.Recipe> {

    /** La place des cinq entrees : le centre, puis haut-gauche, bas-gauche, haut-droite, bas-droite. */
    private static final int[][] INPUTS = {{22, 22}, {0, 0}, {0, 44}, {44, 0}, {44, 44}};

    public PalamachineCategory(IGuiHelper gui) {
        super(DoHyJeiPlugin.PALAMACHINE, Component.translatable("block.palamod.palamachine"),
                gui.createDrawableItemLike(PalaPvp.PALAMACHINE.get()), 126, 62);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, PalamachineRecipes.Recipe recipe, IFocusGroup focuses) {
        for (int index = 0; index < recipe.inputs().size() && index < INPUTS.length; index++) {
            builder.addInputSlot(INPUTS[index][0], INPUTS[index][1]).setStandardSlotBackground()
                    .add(recipe.inputs().get(index));
        }
        builder.addOutputSlot(104, 22).setOutputSlotBackground().add(new ItemStack(recipe.result()));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, PalamachineRecipes.Recipe recipe,
                                   IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(200).setPosition(70, 23);
    }
}

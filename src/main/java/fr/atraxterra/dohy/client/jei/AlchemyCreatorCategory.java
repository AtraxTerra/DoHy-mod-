package fr.atraxterra.dohy.client.jei;

import fr.atraxterra.dohy.palamod.unlock.AlchemyCreatorBlockEntity;
import fr.atraxterra.dohy.palamod.unlock.AlchemyCreatorRecipes;
import fr.atraxterra.dohy.palamod.unlock.PalaUnlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;

/**
 * La table d'alchimiste dans JEI : quatre entrees dans l'ordre (ingredient,
 * verrue, second ingredient, fiole ou fleche), le resultat a droite ; dix
 * secondes pour une potion, une seconde pour seize fleches.
 */
public class AlchemyCreatorCategory extends AbstractRecipeCategory<AlchemyCreatorRecipes.Recipe> {

    public AlchemyCreatorCategory(IGuiHelper gui) {
        super(DoHyJeiPlugin.ALCHEMY_CREATOR, Component.translatable("block.palamod.alchemy_creator"),
                gui.createDrawableItemLike(PalaUnlocks.ALCHEMY_CREATOR.get()), 130, 18);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AlchemyCreatorRecipes.Recipe recipe, IFocusGroup focuses) {
        for (int index = 0; index < recipe.inputs().size(); index++) {
            builder.addInputSlot(index * 18, 0).setStandardSlotBackground().add(recipe.inputs().get(index));
        }
        builder.addOutputSlot(112, 0).setOutputSlotBackground().add(recipe.output());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, AlchemyCreatorRecipes.Recipe recipe,
                                   IFocusGroup focuses) {
        int ticks = recipe.count() > 1 ? AlchemyCreatorBlockEntity.ARROW_TIME : AlchemyCreatorBlockEntity.POTION_TIME;
        builder.addAnimatedRecipeArrow(ticks).setPosition(78, 1);
    }
}

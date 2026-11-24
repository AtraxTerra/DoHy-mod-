package fr.atraxterra.dohy.client.jei;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.palamod.pvp.PalaPvp;
import fr.atraxterra.dohy.palamod.pvp.PalamachineRecipes;
import fr.atraxterra.dohy.palamod.unlock.AlchemyCreatorRecipes;
import fr.atraxterra.dohy.palamod.unlock.PalaUnlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Le plugin JEI de DoHy : les recettes des machines qui n'ont pas de recette
 * vanilla, et que JEI ne peut donc pas trouver seul — la palamachine (cinq
 * entrees en position) et la table d'alchimiste (quatre entrees, potions et
 * fleches).
 *
 * <p>L'API de JEI n'est la qu'a la compilation ({@code compileOnly}) : sans JEI
 * dans le jeu, cette classe n'est jamais chargee.
 */
@JeiPlugin
public class DoHyJeiPlugin implements IModPlugin {

    public static final IRecipeType<PalamachineRecipes.Recipe> PALAMACHINE =
            IRecipeType.create(DoHy.MOD_ID, "palamachine", PalamachineRecipes.Recipe.class);
    public static final IRecipeType<AlchemyCreatorRecipes.Recipe> ALCHEMY_CREATOR =
            IRecipeType.create(DoHy.MOD_ID, "alchemy_creator", AlchemyCreatorRecipes.Recipe.class);

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new PalamachineCategory(gui), new AlchemyCreatorCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(PALAMACHINE, PalamachineRecipes.all());
        List<AlchemyCreatorRecipes.Recipe> alchemy = new ArrayList<>(AlchemyCreatorRecipes.potions());
        alchemy.addAll(AlchemyCreatorRecipes.arrows());
        registration.addRecipes(ALCHEMY_CREATOR, alchemy);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(PALAMACHINE, PalaPvp.PALAMACHINE.get());
        registration.addCraftingStation(ALCHEMY_CREATOR, PalaUnlocks.ALCHEMY_CREATOR.get());
    }
}

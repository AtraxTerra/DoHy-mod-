package fr.atraxterra.dohy.palamod.unlock;

import fr.atraxterra.dohy.palamod.machine.MachineBlockEntity;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;

/**
 * La table d'alchimiste ({@code AlchemyCreatorLogic}) : deux ateliers dans un
 * meme bloc, qui tournent chacun de leur cote — les potions (quatre entrees,
 * une sortie, 200 ticks) et les fleches (quatre entrees, une sortie, 20 ticks,
 * seize fleches a la fois) ; neuf cases et deux compteurs dans l'original
 * ({@code workingTime}, {@code timeNeeded}).
 *
 * <p><b>Ecart d'interface.</b> L'original montrait un atelier par onglet ;
 * ici les deux sont l'un sous l'autre, chacun avec sa fleche d'avancement.
 * Comme la palamachine, la table attend quand la sortie ne peut pas recevoir
 * le resultat, au lieu de le perdre.
 */
public class AlchemyCreatorBlockEntity extends MachineBlockEntity implements WorldlyContainer {

    public static final int POTION_FIRST = 0;
    public static final int POTION_OUTPUT = 4;
    public static final int ARROW_FIRST = 5;
    public static final int ARROW_OUTPUT = 9;
    public static final int SLOT_COUNT = 10;

    /** {@code timeNeeded} : 200 ticks pour une potion, 20 pour seize fleches. */
    public static final int POTION_TIME = 200;
    public static final int ARROW_TIME = 20;

    /** Les potions en haut, les fleches en bas ; les entrees dans l'ordre des recettes. */
    public static final MachineLayout LAYOUT = new MachineLayout(List.of(
            new MachineLayout.SlotPos(28, 60, true),
            new MachineLayout.SlotPos(52, 60, true),
            new MachineLayout.SlotPos(76, 60, true),
            new MachineLayout.SlotPos(100, 60, true),
            new MachineLayout.SlotPos(206, 60, false),
            new MachineLayout.SlotPos(28, 112, true),
            new MachineLayout.SlotPos(52, 112, true),
            new MachineLayout.SlotPos(76, 112, true),
            new MachineLayout.SlotPos(100, 112, true),
            new MachineLayout.SlotPos(206, 112, false)));

    private static final int[] NONE = new int[0];

    private int potionWork;
    private int arrowWork;

    public AlchemyCreatorBlockEntity(BlockPos pos, BlockState state) {
        super(PalaUnlocks.ALCHEMY_CREATOR_TYPE.get(), pos, state, SLOT_COUNT,
                Component.translatable("block.palamod.alchemy_creator"));
    }

    @Override
    protected MenuType<MachineMenu> menuType() {
        return PalaUnlocks.ALCHEMY_CREATOR_MENU.get();
    }

    @Override
    protected MachineLayout layout() {
        return LAYOUT;
    }

    @Override
    public int work() {
        return potionWork;
    }

    @Override
    public int cycleDuration() {
        return craftable(AlchemyCreatorRecipes.potions(), POTION_FIRST, POTION_OUTPUT) != null ? POTION_TIME : 0;
    }

    @Override
    public int work2() {
        return arrowWork;
    }

    @Override
    public int cycleDuration2() {
        return craftable(AlchemyCreatorRecipes.arrows(), ARROW_FIRST, ARROW_OUTPUT) != null ? ARROW_TIME : 0;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot != POTION_OUTPUT && slot != ARROW_OUTPUT;
    }

    /** La recette qu'un atelier ferait maintenant, si sa sortie peut la recevoir. */
    private AlchemyCreatorRecipes.Recipe craftable(List<AlchemyCreatorRecipes.Recipe> recipes, int first,
                                                   int output) {
        AlchemyCreatorRecipes.Recipe recipe = AlchemyCreatorRecipes.find(recipes, items.subList(first, output));
        if (recipe == null) {
            return null;
        }
        ItemStack result = recipe.output();
        ItemStack current = items.get(output);
        if (!current.isEmpty() && (!ItemStack.isSameItemSameComponents(current, result)
                || current.getCount() + result.getCount() > current.getMaxStackSize())) {
            return null;
        }
        return recipe;
    }

    @Override
    public void serverTick() {
        int potion = step(AlchemyCreatorRecipes.potions(), POTION_FIRST, POTION_OUTPUT, potionWork, POTION_TIME);
        int arrow = step(AlchemyCreatorRecipes.arrows(), ARROW_FIRST, ARROW_OUTPUT, arrowWork, ARROW_TIME);
        if (potion != potionWork || arrow != arrowWork) {
            potionWork = potion;
            arrowWork = arrow;
            setChanged();
        }
    }

    /** Un tick d'un atelier ; rend son nouvel avancement. */
    private int step(List<AlchemyCreatorRecipes.Recipe> recipes, int first, int output, int work, int goal) {
        AlchemyCreatorRecipes.Recipe recipe = craftable(recipes, first, output);
        if (recipe == null) {
            return 0;
        }
        if (++work < goal) {
            return work;
        }
        ItemStack current = items.get(output);
        if (current.isEmpty()) {
            items.set(output, recipe.output());
        } else {
            current.grow(recipe.count());
        }
        for (int slot = first; slot < output; slot++) {
            items.get(slot).shrink(1);
        }
        return 0;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return NONE;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        potionWork = input.getIntOr("PotionWork", 0);
        arrowWork = input.getIntOr("ArrowWork", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("PotionWork", potionWork);
        output.putInt("ArrowWork", arrowWork);
    }
}

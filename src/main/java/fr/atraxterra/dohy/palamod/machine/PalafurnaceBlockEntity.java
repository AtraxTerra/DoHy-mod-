package fr.atraxterra.dohy.palamod.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Palafurnace : un fourneau qu'on accelere avec des ameliorations.
 *
 * <p>Releve dans {@code PaladiumFurnaceLogic} :
 * <ul>
 *   <li>recettes et combustibles du fourneau vanilla — plus les charbons mixes,
 *       a la demande du joueur ({@link PalaFuels}) ;</li>
 *   <li>{@code n} = le nombre d'ameliorations de four dans la case dediee,
 *       plafonne a 16, et 1 si elle est vide ;</li>
 *   <li>une cuisson dure {@code 200 / n} ticks (division entiere : 200, 100, 66,
 *       50... 12) et le combustible brule {@code n} fois plus vite — un charbon
 *       cuit donc toujours 8 objets, seulement plus vite ;</li>
 *   <li>une seule amelioration ne change rien, puisque {@code n} vaut deja 1 :
 *       elles ne comptent qu'a partir de deux. C'est l'original.</li>
 * </ul>
 *
 * <p>Entonnoirs : le dessus vers l'entree, les cotes vers le combustible, le
 * dessous sort le resultat (et un seau vide). La case d'amelioration n'est
 * accessible par aucune face.
 *
 * <p>Pas d'XP de fourneau au retrait : la case de sortie du menu des machines est
 * une case simple. C'est le seul ecart.
 */
public class PalafurnaceBlockEntity extends MachineBlockEntity implements WorldlyContainer {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_UPGRADE = 3;
    public static final int SLOT_COUNT = 4;

    /** {@code timeNeeded = 200 / n}. */
    private static final int BASE_COOK_TICKS = 200;

    /** {@code min(taille, 16)}. */
    private static final int MAX_UPGRADES = 16;

    private static final int[] TOP = {SLOT_INPUT};
    private static final int[] BOTTOM = {SLOT_OUTPUT, SLOT_FUEL};
    private static final int[] SIDES = {SLOT_FUEL};

    private int burnTime;
    private int burnDuration;
    private int cookTime;

    public PalafurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(PalaMachines.PALAFURNACE.get(), pos, state, SLOT_COUNT,
                Component.translatable("block.palamod.paladium_furnace"));
    }

    @Override
    protected MenuType<MachineMenu> menuType() {
        return PalaMenus.PALAFURNACE.get();
    }

    @Override
    protected MachineLayout layout() {
        return MachineLayout.PALAFURNACE;
    }

    @Override
    public int work() {
        return cookTime;
    }

    @Override
    public int cycleDuration() {
        return BASE_COOK_TICKS / upgrades();
    }

    @Override
    public int tankFill() {
        return burnTime;
    }

    @Override
    public int tankGoal() {
        return burnDuration;
    }

    private int upgrades() {
        ItemStack upgrade = getItem(SLOT_UPGRADE);
        return upgrade.is(PalaMachines.FURNACE_UPGRADE.get())
                ? Math.min(upgrade.getCount(), MAX_UPGRADES) : 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_INPUT -> !stack.is(PalaMachines.FURNACE_UPGRADE.get());
            case SLOT_FUEL -> PalaFuels.isFuel(level, stack);
            case SLOT_UPGRADE -> stack.is(PalaMachines.FURNACE_UPGRADE.get());
            default -> false;
        };
    }

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        int n = upgrades();
        boolean wasBurning = burnTime > 0;
        if (burnTime > 0) {
            burnTime = Math.max(0, burnTime - n);
        }

        ItemStack result = result(server);
        boolean canSmelt = !result.isEmpty() && fits(result);

        if (burnTime <= 0 && canSmelt) {
            ItemStack fuel = getItem(SLOT_FUEL);
            int duration = PalaFuels.burnTime(server, fuel);
            if (duration > 0) {
                burnTime = burnDuration = duration;
                // Le seau de lave rend un seau vide ; tout autre combustible disparait.
                if (fuel.is(Items.LAVA_BUCKET)) {
                    setItem(SLOT_FUEL, new ItemStack(Items.BUCKET));
                } else {
                    fuel.shrink(1);
                }
            }
        }

        if (burnTime > 0 && canSmelt) {
            cookTime++;
            if (cookTime >= BASE_COOK_TICKS / n) {
                cookTime = 0;
                smelt(result);
            }
        } else {
            cookTime = 0;
        }

        boolean burning = burnTime > 0;
        if (burning != wasBurning) {
            server.setBlock(worldPosition, getBlockState().setValue(OrientedMachineBlock.LIT, burning),
                    Block.UPDATE_ALL);
        }
        setChanged();
    }

    private ItemStack result(ServerLevel server) {
        ItemStack input = getItem(SLOT_INPUT);
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        return server.recipeAccess().getRecipeFor(RecipeType.SMELTING, recipeInput, server)
                .map(holder -> holder.value().assemble(recipeInput))
                .orElse(ItemStack.EMPTY);
    }

    private boolean fits(ItemStack result) {
        ItemStack output = getItem(SLOT_OUTPUT);
        if (output.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void smelt(ItemStack result) {
        ItemStack output = getItem(SLOT_OUTPUT);
        if (output.isEmpty()) {
            setItem(SLOT_OUTPUT, result.copy());
        } else {
            output.grow(result.getCount());
        }
        getItem(SLOT_INPUT).shrink(1);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return switch (side) {
            case UP -> TOP;
            case DOWN -> BOTTOM;
            default -> SIDES;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side != Direction.DOWN || slot != SLOT_FUEL || stack.is(Items.BUCKET);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        burnTime = input.getIntOr("BurnTime", 0);
        burnDuration = input.getIntOr("BurnDuration", 0);
        cookTime = input.getIntOr("CookTime", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("BurnTime", burnTime);
        output.putInt("BurnDuration", burnDuration);
        output.putInt("CookTime", cookTime);
    }
}

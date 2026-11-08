package fr.atraxterra.dohy.palamod.golem;

import fr.atraxterra.dohy.palamod.machine.MachineBlockEntity;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Set;

/**
 * {@code TileEntityFoodMachine} : la machine a croquettes.
 *
 * <p>Un seau de lave vaut 2 unites, le reservoir en tient 100 ; une fournee en
 * coute 0,05 — quarante fournees par seau. Trois cases typees (fibres,
 * mineraux, proteines), un ingredient de chaque par fournee, 7 ticks de
 * cuisson :
 * <ul>
 *   <li><b>en-cas</b>, 5 croquettes : deux ingredients sur trois parmi
 *       pasteque, citrouille ou canne a sucre / pierre ou pierre taillee /
 *       chair putrefiee ;</li>
 *   <li><b>gouter</b>, 8 : deux parmi verrue du Nether, pomme de terre ou
 *       carotte / gravier / boeuf, poulet, porc ou oeuf crus, plus un
 *       ingredient d'en-cas ;</li>
 *   <li><b>gros repas</b>, 16 : un parmi pomme ou ble / pierre moussue /
 *       poisson cru, plus un ingredient de gouter.</li>
 * </ul>
 *
 * <p><b>Ecarts</b>, des corrections : PalaMod consommait les ingredients sans
 * lave et pouvait passer les trois paliers dans le meme tick, en consommant
 * trois fois pour une seule fournee ; il versait sa fournee dans une case qui
 * ne contenait pas de croquettes, et la perdait au redemarrage. Ici, une
 * fournee ne part qu'avec de la lave et de la place, consomme une fois, au
 * meilleur palier, et se sauvegarde.
 */
public class FoodMachineBlockEntity extends MachineBlockEntity implements WorldlyContainer {

    public static final int SLOT_FUEL = 0;
    public static final int SLOT_FIBERS = 1;
    public static final int SLOT_MINERALS = 2;
    public static final int SLOT_PROTEIN = 3;
    public static final int FIRST_OUTPUT = 4;
    public static final int SLOT_COUNT = 8;

    /** La lave en centiemes : 10 000 = 100 unites, un seau = 200, une fournee = 5. */
    public static final int MAX_LAVA = 10_000;
    private static final int BUCKET = 200;
    private static final int PER_BATCH = 5;
    private static final int COOK_TICKS = 7;

    private static final int[] BOTTOM = {SLOT_FUEL, 4, 5, 6, 7};
    private static final int[] SIDES = {SLOT_FUEL, SLOT_FIBERS, SLOT_MINERALS, SLOT_PROTEIN};

    private int lava;
    private int fireTick;
    private int pending;

    public FoodMachineBlockEntity(BlockPos pos, BlockState state) {
        super(PalaGolem.FOOD_MACHINE_ENTITY.get(), pos, state, SLOT_COUNT,
                Component.translatable("container.palamod.food_machine"));
    }

    @Override
    protected MenuType<MachineMenu> menuType() {
        return PalaGolem.FOOD_MACHINE_MENU.get();
    }

    @Override
    protected MachineLayout layout() {
        return MachineLayout.FOOD_MACHINE;
    }

    @Override
    public int work() {
        return pending > 0 ? COOK_TICKS - fireTick : 0;
    }

    @Override
    public int cycleDuration() {
        return COOK_TICKS;
    }

    @Override
    public int tankFill() {
        return lava;
    }

    @Override
    public int tankGoal() {
        return MAX_LAVA;
    }

    @Override
    public void serverTick() {
        if (items.get(SLOT_FUEL).is(Items.LAVA_BUCKET) && lava < MAX_LAVA) {
            items.set(SLOT_FUEL, new ItemStack(Items.BUCKET));
            lava = Math.min(MAX_LAVA, lava + BUCKET);
            setChanged();
        }
        if (pending > 0) {
            if (fireTick > 0) {
                fireTick--;
                return;
            }
            if (deliver(pending)) {
                pending = 0;
                lava -= PER_BATCH;
                setChanged();
            }
            return;
        }
        int batch = recipe();
        if (batch > 0 && lava >= PER_BATCH && room(batch) >= 0) {
            items.get(SLOT_FIBERS).shrink(1);
            items.get(SLOT_MINERALS).shrink(1);
            items.get(SLOT_PROTEIN).shrink(1);
            pending = batch;
            fireTick = COOK_TICKS;
            setChanged();
        }
    }

    // -- Recettes ------------------------------------------------------------

    private static final Set<Item> SNACK_FIBERS = Set.of(Items.MELON_SLICE, Items.PUMPKIN, Items.SUGAR_CANE);
    private static final Set<Item> SNACK_MINERALS = Set.of(Items.COBBLESTONE, Items.STONE);
    private static final Set<Item> SNACK_PROTEIN = Set.of(Items.ROTTEN_FLESH);
    private static final Set<Item> TEA_FIBERS = Set.of(Items.NETHER_WART, Items.POTATO, Items.CARROT);
    private static final Set<Item> TEA_MINERALS = Set.of(Items.GRAVEL);
    private static final Set<Item> TEA_PROTEIN = Set.of(Items.BEEF, Items.CHICKEN, Items.PORKCHOP, Items.EGG);
    private static final Set<Item> MEAL_FIBERS = Set.of(Items.APPLE, Items.WHEAT);
    private static final Set<Item> MEAL_MINERALS = Set.of(Items.MOSSY_COBBLESTONE);
    private static final Set<Item> MEAL_PROTEIN = Set.of(Items.COD, Items.SALMON);

    /** {@code checkCrafts} : la taille de la fournee, 0 si les trois cases ne font rien. */
    private int recipe() {
        ItemStack fibers = items.get(SLOT_FIBERS);
        ItemStack minerals = items.get(SLOT_MINERALS);
        ItemStack protein = items.get(SLOT_PROTEIN);
        if (fibers.isEmpty() || minerals.isEmpty() || protein.isEmpty()) {
            return 0;
        }
        int snack = matches(fibers, minerals, protein, SNACK_FIBERS, SNACK_MINERALS, SNACK_PROTEIN);
        int tea = matches(fibers, minerals, protein, TEA_FIBERS, TEA_MINERALS, TEA_PROTEIN);
        int meal = matches(fibers, minerals, protein, MEAL_FIBERS, MEAL_MINERALS, MEAL_PROTEIN);
        if (meal >= 1 && tea >= 1) {
            return 16;
        }
        if (tea >= 2 && snack >= 1) {
            return 8;
        }
        return snack >= 2 ? 5 : 0;
    }

    private static int matches(ItemStack fibers, ItemStack minerals, ItemStack protein,
                               Set<Item> f, Set<Item> m, Set<Item> p) {
        return (f.contains(fibers.getItem()) ? 1 : 0) + (m.contains(minerals.getItem()) ? 1 : 0)
                + (p.contains(protein.getItem()) ? 1 : 0);
    }

    /** {@code FoodTypeManager} : ce que chaque case accepte. */
    public static boolean accepts(int slot, ItemStack stack) {
        Item item = stack.getItem();
        return switch (slot) {
            case SLOT_FUEL -> stack.is(Items.LAVA_BUCKET);
            case SLOT_FIBERS -> SNACK_FIBERS.contains(item) || TEA_FIBERS.contains(item) || MEAL_FIBERS.contains(item);
            case SLOT_MINERALS -> SNACK_MINERALS.contains(item) || TEA_MINERALS.contains(item)
                    || MEAL_MINERALS.contains(item);
            case SLOT_PROTEIN -> SNACK_PROTEIN.contains(item) || TEA_PROTEIN.contains(item)
                    || MEAL_PROTEIN.contains(item);
            default -> false;
        };
    }

    /** La premiere case de sortie qui prend toute la fournee, ou -1. */
    private int room(int batch) {
        for (int slot = FIRST_OUTPUT; slot < SLOT_COUNT; slot++) {
            ItemStack here = items.get(slot);
            if (here.isEmpty() || here.is(PalaGolem.CROQUETTE.get()) && here.getCount() + batch <= here.getMaxStackSize()) {
                return slot;
            }
        }
        return -1;
    }

    private boolean deliver(int batch) {
        int slot = room(batch);
        if (slot < 0) {
            return false;
        }
        ItemStack here = items.get(slot);
        if (here.isEmpty()) {
            items.set(slot, new ItemStack(PalaGolem.CROQUETTE.get(), batch));
        } else {
            here.grow(batch);
        }
        return true;
    }

    // -- Cases -----------------------------------------------------------------

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return accepts(slot, stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? BOTTOM : SIDES;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return accepts(slot, stack);
    }

    /** Par le bas : les croquettes, et le seau vide. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= FIRST_OUTPUT || slot == SLOT_FUEL && stack.is(Items.BUCKET);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        lava = input.getIntOr("Lava", 0);
        fireTick = input.getIntOr("FireTick", 0);
        pending = input.getIntOr("Pending", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Lava", lava);
        output.putInt("FireTick", fireTick);
        output.putInt("Pending", pending);
    }
}

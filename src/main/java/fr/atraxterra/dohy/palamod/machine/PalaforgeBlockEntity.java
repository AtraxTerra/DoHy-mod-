package fr.atraxterra.dohy.palamod.machine;

import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.equipment.PalaEquipment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Palaforge : refond l'equipement use en lingots.
 *
 * <p>Releve dans {@code ForgeLogic} et {@code EnumAllowItemsForge} :
 * <ul>
 *   <li>une piece d'equipement de la table, du combustible vanilla ;</li>
 *   <li>un cycle dure 101 ticks (le travail monte de 0 a 100, la piece est
 *       refondue au tick suivant) ;</li>
 *   <li>on recupere {@code maximum x pct / 100} lingots en division entiere, ou
 *       {@code pct} est la durabilite restante en pourcentage, tronquee. Une
 *       piece neuve rend le maximum ; une pelle a peine usee ne rend rien.</li>
 * </ul>
 *
 * <table>
 *   <tr><th>Metal</th><th>Casque</th><th>Plastron</th><th>Jambieres</th><th>Bottes</th>
 *       <th>Epee</th><th>Hache</th><th>Pioche</th><th>Pelle</th></tr>
 *   <tr><td>fer, or</td><td>4</td><td>7</td><td>6</td><td>3</td><td>2</td><td>2</td><td>2</td><td>1</td></tr>
 *   <tr><td>titane</td><td>5</td><td>8</td><td>7</td><td>4</td><td>2</td><td>3</td><td>3</td><td>1</td></tr>
 *   <tr><td>amethyste</td><td>4</td><td>7</td><td>6</td><td>3</td><td>2</td><td>3</td><td>3</td><td>1</td></tr>
 *   <tr><td>paladium</td><td>4</td><td>7</td><td>6</td><td>3</td><td>2</td><td>2</td><td>2</td><td>1</td></tr>
 *   <tr><td>endium</td><td>4</td><td>7</td><td>6</td><td>3</td><td>2</td><td>2</td><td>2</td><td>—</td></tr>
 * </table>
 *
 * <p><b>Bugs de l'original corriges</b> : une piece qui ne rendait rien restait
 * dans la forge et brulait tout le combustible — ici la forge ne s'allume pas
 * pour elle ; l'etat allume ne basculait presque jamais — ici il suit le feu ; le
 * contenu etait perdu a la casse — ici il tombe, comme pour toute machine.
 */
public class PalaforgeBlockEntity extends MachineBlockEntity implements WorldlyContainer {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_FUEL = 2;
    public static final int SLOT_COUNT = 3;

    /** {@code work == 100} declenche la refonte. */
    private static final int WORK_GOAL = 100;

    private static final int[] NONE = new int[0];

    private record Recycle(Supplier<Item> ingot, int maximum) {
    }

    private static Map<Item, Recycle> table;

    private int work;
    private int burn;
    private int burnMax;

    public PalaforgeBlockEntity(BlockPos pos, BlockState state) {
        super(PalaMachines.PALAFORGE.get(), pos, state, SLOT_COUNT,
                Component.translatable("block.palamod.palaforge"));
    }

    /** Construite au premier usage : les objets n'existent pas avant l'enregistrement. */
    private static Map<Item, Recycle> table() {
        if (table != null) {
            return table;
        }
        Map<Item, Recycle> built = new HashMap<>();
        Supplier<Item> iron = () -> Items.IRON_INGOT;
        Supplier<Item> gold = () -> Items.GOLD_INGOT;
        Supplier<Item> titane = () -> PalaItems.TITANE_INGOT.get();
        Supplier<Item> amethyst = () -> PalaItems.AMETHYST_INGOT.get();
        Supplier<Item> paladium = () -> PalaItems.PALADIUM_INGOT.get();
        Supplier<Item> endium = () -> PalaItems.ENDIUM_INGOT.get();

        put(built, iron, new int[]{4, 7, 6, 3, 2, 2, 2, 1},
                Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
                Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_PICKAXE, Items.IRON_SHOVEL);
        put(built, gold, new int[]{4, 7, 6, 3, 2, 2, 2, 1},
                Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
                Items.GOLDEN_SWORD, Items.GOLDEN_AXE, Items.GOLDEN_PICKAXE, Items.GOLDEN_SHOVEL);
        put(built, titane, new int[]{5, 8, 7, 4, 2, 3, 3, 1},
                PalaEquipment.TITANE_HELMET.get(), PalaEquipment.TITANE_CHESTPLATE.get(),
                PalaEquipment.TITANE_LEGGINGS.get(), PalaEquipment.TITANE_BOOTS.get(),
                PalaEquipment.TITANE_SWORD.get(), PalaEquipment.TITANE_AXE.get(),
                PalaEquipment.TITANE_PICKAXE.get(), PalaEquipment.TITANE_SHOVEL.get());
        put(built, amethyst, new int[]{4, 7, 6, 3, 2, 3, 3, 1},
                PalaEquipment.AMETHYST_HELMET.get(), PalaEquipment.AMETHYST_CHESTPLATE.get(),
                PalaEquipment.AMETHYST_LEGGINGS.get(), PalaEquipment.AMETHYST_BOOTS.get(),
                PalaEquipment.AMETHYST_SWORD.get(), PalaEquipment.AMETHYST_AXE.get(),
                PalaEquipment.AMETHYST_PICKAXE.get(), PalaEquipment.AMETHYST_SHOVEL.get());
        put(built, paladium, new int[]{4, 7, 6, 3, 2, 2, 2, 1},
                PalaEquipment.PALADIUM_HELMET.get(), PalaEquipment.PALADIUM_CHESTPLATE.get(),
                PalaEquipment.PALADIUM_LEGGINGS.get(), PalaEquipment.PALADIUM_BOOTS.get(),
                PalaEquipment.PALADIUM_SWORD.get(), PalaEquipment.PALADIUM_AXE.get(),
                PalaEquipment.PALADIUM_PICKAXE.get(), PalaEquipment.PALADIUM_SHOVEL.get());
        put(built, endium, new int[]{4, 7, 6, 3, 2, 2, 2},
                PalaEquipment.ENDIUM_HELMET.get(), PalaEquipment.ENDIUM_CHESTPLATE.get(),
                PalaEquipment.ENDIUM_LEGGINGS.get(), PalaEquipment.ENDIUM_BOOTS.get(),
                PalaEquipment.ENDIUM_SWORD.get(), PalaEquipment.ENDIUM_AXE.get(),
                PalaEquipment.ENDIUM_PICKAXE.get());
        table = built;
        return table;
    }

    private static void put(Map<Item, Recycle> into, Supplier<Item> ingot, int[] maxima, Item... pieces) {
        for (int index = 0; index < pieces.length; index++) {
            into.put(pieces[index], new Recycle(ingot, maxima[index]));
        }
    }

    @Override
    protected MenuType<MachineMenu> menuType() {
        return PalaMenus.PALAFORGE.get();
    }

    @Override
    protected MachineLayout layout() {
        return MachineLayout.PALAFORGE;
    }

    @Override
    public int work() {
        return work;
    }

    @Override
    public int cycleDuration() {
        return recovered() > 0 ? WORK_GOAL : 0;
    }

    @Override
    public int tankFill() {
        return burn;
    }

    @Override
    public int tankGoal() {
        return burn > 0 ? burnMax : 0;
    }

    /** Lingots que rendrait la piece en entree, dans son etat d'usure. */
    private int recovered() {
        ItemStack piece = getItem(SLOT_INPUT);
        Recycle recycle = piece.isEmpty() ? null : table().get(piece.getItem());
        if (recycle == null) {
            return 0;
        }
        int max = piece.getMaxDamage();
        int percent = max <= 0 ? 100 : (int) ((max - piece.getDamageValue()) * 100 / (double) max);
        return recycle.maximum() * percent / 100;
    }

    private boolean fits(Item ingot, int count) {
        ItemStack output = getItem(SLOT_OUTPUT);
        return output.isEmpty()
                || (output.is(ingot) && output.getCount() + count <= output.getMaxStackSize());
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_INPUT -> table().containsKey(stack.getItem());
            case SLOT_FUEL -> level != null && level.fuelValues().isFuel(stack);
            default -> false;
        };
    }

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        boolean wasBurning = burn > 0;
        ItemStack piece = getItem(SLOT_INPUT);
        Recycle recycle = piece.isEmpty() ? null : table().get(piece.getItem());
        int out = recovered();
        boolean valid = recycle != null && out > 0 && fits(recycle.ingot().get(), out);

        if (burn <= 0) {
            ItemStack fuel = getItem(SLOT_FUEL);
            int duration = !valid || fuel.isEmpty() ? 0
                    : fuel.getBurnTime(RecipeType.SMELTING, server.fuelValues());
            if (duration > 0) {
                burn = burnMax = duration;
                if (fuel.is(Items.LAVA_BUCKET)) {
                    setItem(SLOT_FUEL, new ItemStack(Items.BUCKET));
                } else {
                    fuel.shrink(1);
                }
            } else {
                work = 0;
            }
        } else if (valid) {
            if (work >= WORK_GOAL) {
                ItemStack output = getItem(SLOT_OUTPUT);
                if (output.isEmpty()) {
                    setItem(SLOT_OUTPUT, new ItemStack(recycle.ingot().get(), out));
                } else {
                    output.grow(out);
                }
                piece.shrink(1);
                work = 0;
            } else {
                work++;
            }
        } else {
            work = 0;
        }

        if (burn > 0) {
            burn--;
        }
        boolean burning = burn > 0;
        if (burning != wasBurning) {
            server.setBlock(worldPosition, getBlockState().setValue(OrientedMachineBlock.LIT, burning),
                    Block.UPDATE_ALL);
        }
        setChanged();
    }

    /** Aucune face : {@code getAccessibleSlotsFromSide} rendait un tableau vide. */
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
        work = input.getIntOr("Work", 0);
        burn = input.getIntOr("Burn", 0);
        burnMax = input.getIntOr("BurnMax", 100);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Work", work);
        output.putInt("Burn", burn);
        output.putInt("BurnMax", burnMax);
    }
}

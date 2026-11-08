package fr.atraxterra.dohy.palamod.machine;

import fr.atraxterra.dohy.palamod.PalaBlocks;
import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.equipment.PalaEquipment;
import fr.atraxterra.dohy.palamod.godpickaxe.GodPickaxeItem;
import fr.atraxterra.dohy.palamod.godpickaxe.GodPickaxeLevels;
import fr.atraxterra.dohy.palamod.godpickaxe.PalaGodPickaxe;
import fr.atraxterra.dohy.palamod.smeltery.GrinderModifier;
import fr.atraxterra.dohy.palamod.smeltery.ModifiableTool;
import fr.atraxterra.dohy.palamod.smeltery.PalaSmeltery;
import fr.atraxterra.dohy.palamod.smeltery.ToolModifiers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Map;

/**
 * Le grinder ({@code GrinderLogic}) : un reservoir de paladium et deux ateliers
 * qui travaillent en meme temps.
 *
 * <ul>
 *   <li><b>reservoir</b> (100) : toutes les 21 ticks, la case de paladium s'y
 *       vide — un lingot vaut 1, un bloc 9, une piece d'equipement en paladium
 *       sa valeur au prorata de sa durabilite (pioche 3, epee 2, casque 5,
 *       plastron 8, jambieres 7, bottes 4). Ce qui deborde est perdu ; une hache
 *       ou une pelle n'est pas du carburant ;</li>
 *   <li><b>forge</b> : 100 ticks, {@link GrinderRecipes} ; le reservoir ne doit
 *       pas etre vide, meme pour une recette gratuite ;</li>
 *   <li><b>amelioration</b> : 100 ticks, gratuite, un modificateur sur un outil
 *       qui en prend ({@link ToolModifiers}).</li>
 * </ul>
 * Aucune automatisation : ni entonnoir, ni tuyau. Casse, il rend ses six cases,
 * pas son paladium.
 *
 * <p><b>Ecart</b> : PalaMod ne regardait la structure qu'a l'ouverture et
 * toutes les secondes interface ouverte ; une machine dont on avait casse le
 * cube continuait de travailler. Ici, la structure est revue toutes les
 * secondes et la machine s'arrete si elle est incomplete.
 */
public class GrinderBlockEntity extends MachineBlockEntity implements WorldlyContainer {

    public static final int SLOT_OUTPUT = 0;
    public static final int SLOT_FIRST = 1;
    public static final int SLOT_SECOND = 2;
    public static final int SLOT_MODIFIER = 3;
    public static final int SLOT_TOOL = 4;
    public static final int SLOT_FUEL = 5;
    public static final int SLOT_COUNT = 6;

    /** {@code maxPaladium}. */
    public static final int MAX_PALADIUM = 100;
    /** {@code timeNeededCreation}, {@code timeNeededUpgrade}. */
    private static final int WORK_GOAL = 100;
    /** {@code timeNeededPaladium} : 20, compare par {@code <}, soit 21 ticks. */
    private static final int FUEL_GOAL = 20;
    private static final int CHECK_PERIOD = 20;

    private static final int[] NONE = new int[0];

    private int paladium;
    private int workCreation;
    private int workUpgrade;
    private int workFuel;
    private int check = CHECK_PERIOD;
    private boolean formed;

    public GrinderBlockEntity(BlockPos pos, BlockState state) {
        super(PalaMachines.GRINDER.get(), pos, state, SLOT_COUNT, Component.translatable("container.palamod.grinder"));
    }

    @Override
    protected MenuType<MachineMenu> menuType() {
        return PalaMenus.GRINDER.get();
    }

    @Override
    protected MachineLayout layout() {
        return MachineLayout.GRINDER;
    }

    @Override
    public int work() {
        return workCreation;
    }

    @Override
    public int cycleDuration() {
        return WORK_GOAL;
    }

    @Override
    public int work2() {
        return workUpgrade;
    }

    @Override
    public int cycleDuration2() {
        return WORK_GOAL;
    }

    @Override
    public int tankFill() {
        return paladium;
    }

    @Override
    public int tankGoal() {
        return MAX_PALADIUM;
    }

    @Override
    public boolean canOpen(ServerPlayer player) {
        if (level != null && GrinderStructure.isComplete(level, worldPosition)) {
            return true;
        }
        player.connection.send(new ClientboundSetActionBarTextPacket(
                Component.translatable("message.palamod.grinder.incomplete").withStyle(ChatFormatting.RED)));
        return false;
    }

    /** {@code canInteractWith} : la structure, revue chaque seconde, et la distance. */
    @Override
    public boolean stillValid(Player player) {
        return formed && super.stillValid(player);
    }

    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }
        if (++check >= CHECK_PERIOD) {
            check = 0;
            formed = GrinderStructure.isComplete(level, worldPosition);
            BlockState state = getBlockState();
            if (state.hasProperty(OrientedMachineBlock.LIT) && state.getValue(OrientedMachineBlock.LIT) != formed) {
                level.setBlock(worldPosition, state.setValue(OrientedMachineBlock.LIT, formed), Block.UPDATE_ALL);
            }
        }
        if (!formed) {
            workCreation = 0;
            workUpgrade = 0;
            workFuel = 0;
            return;
        }

        if (!items.get(SLOT_FUEL).isEmpty() && paladium < MAX_PALADIUM) {
            if (workFuel < FUEL_GOAL) {
                workFuel++;
            } else {
                consumeFuel();
                workFuel = 0;
            }
        } else {
            workFuel = 0;
        }

        GrinderRecipes recipe = forgeable();
        if (recipe != null) {
            if (++workCreation >= WORK_GOAL) {
                forge(recipe);
                workCreation = 0;
            }
        } else {
            workCreation = 0;
        }

        GrinderModifier modifier = applicable();
        boolean autoSmelt = modifier == null && autoSmeltApplicable();
        if (modifier != null || autoSmelt) {
            if (++workUpgrade >= WORK_GOAL) {
                if (autoSmelt) {
                    applyAutoSmelt();
                } else {
                    upgrade(modifier);
                }
                workUpgrade = 0;
            }
        } else {
            workUpgrade = 0;
        }
    }

    // -- Reservoir -----------------------------------------------------------

    private static Map<Item, Integer> fuelCosts() {
        return Map.of(
                PalaEquipment.PALADIUM_PICKAXE.get(), 3, PalaEquipment.PALADIUM_SWORD.get(), 2,
                PalaEquipment.PALADIUM_HELMET.get(), 5, PalaEquipment.PALADIUM_CHESTPLATE.get(), 8,
                PalaEquipment.PALADIUM_LEGGINGS.get(), 7, PalaEquipment.PALADIUM_BOOTS.get(), 4);
    }

    /** Ce que vaut un objet dans le reservoir : 1, 9, ou la piece au prorata de son usure. */
    static int fuelValue(ItemStack stack) {
        if (stack.is(PalaItems.PALADIUM_INGOT.get())) {
            return 1;
        }
        if (stack.is(PalaBlocks.PALADIUM_BLOCK.get().asItem())) {
            return 9;
        }
        Integer cost = fuelCosts().get(stack.getItem());
        if (cost == null || stack.getMaxDamage() <= 0) {
            return 0;
        }
        return (stack.getMaxDamage() - stack.getDamageValue()) * cost / stack.getMaxDamage();
    }

    static boolean isFuel(ItemStack stack) {
        return stack.is(PalaItems.PALADIUM_INGOT.get()) || stack.is(PalaBlocks.PALADIUM_BLOCK.get().asItem())
                || fuelCosts().containsKey(stack.getItem());
    }

    /** {@code consumePaladium} : autant d'objets qu'il en faut pour remplir, le reste est perdu. */
    private void consumeFuel() {
        ItemStack fuel = items.get(SLOT_FUEL);
        int value = fuelValue(fuel);
        if (value <= 0) {
            return;
        }
        int needed = (MAX_PALADIUM - paladium + value - 1) / value;
        int used = Math.min(fuel.getCount(), needed);
        fuel.shrink(used);
        paladium = Math.min(MAX_PALADIUM, paladium + value * used);
        setChanged();
    }

    // -- Forge ---------------------------------------------------------------

    /** {@code canSmeltTool} : la recette, un reservoir non vide, la place en sortie. */
    private GrinderRecipes forgeable() {
        ItemStack first = items.get(SLOT_FIRST);
        ItemStack second = items.get(SLOT_SECOND);
        if (first.isEmpty() || second.isEmpty() || paladium == 0) {
            return null;
        }
        GrinderRecipes recipe = GrinderRecipes.find(first, second);
        if (recipe == null || recipe.cost() > paladium) {
            return null;
        }
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) {
            return recipe;
        }
        // Seuls le lingot et le bloc s'empilent en sortie ; une tete demande une case vide.
        boolean stacks = output.is(recipe.result()) && (output.is(PalaItems.PALADIUM_INGOT.get())
                || output.is(PalaBlocks.PALADIUM_BLOCK.get().asItem()));
        return stacks && output.getCount() < output.getMaxStackSize() ? recipe : null;
    }

    private void forge(GrinderRecipes recipe) {
        paladium -= recipe.cost();
        ItemStack first = items.get(SLOT_FIRST);
        ItemStack second = items.get(SLOT_SECOND);
        // Deux patterns : rien ne s'use. Sinon, un de chaque.
        if (!(isPattern(first) && isPattern(second))) {
            first.shrink(1);
            second.shrink(1);
        }
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) {
            items.set(SLOT_OUTPUT, new ItemStack(recipe.result()));
        } else {
            output.grow(1);
        }
        setChanged();
    }

    private static boolean isPattern(ItemStack stack) {
        return PalaSmeltery.isPattern(stack);
    }

    // -- Amelioration --------------------------------------------------------

    /** {@code canSmeltUpgrade} : un modificateur que l'outil peut encore recevoir. */
    private GrinderModifier applicable() {
        ItemStack tool = items.get(SLOT_TOOL);
        GrinderModifier modifier = GrinderModifier.of(items.get(SLOT_MODIFIER).getItem());
        if (modifier == null || !(tool.getItem() instanceof ModifiableTool modifiable) || !modifiable.modifiable()) {
            return null;
        }
        return PalaSmeltery.modifiers(tool).canApply(modifier, modifiable.kind()) ? modifier : null;
    }

    private void upgrade(GrinderModifier modifier) {
        ItemStack tool = items.get(SLOT_TOOL);
        ModifiableTool modifiable = (ModifiableTool) tool.getItem();
        ToolModifiers next = PalaSmeltery.modifiers(tool).with(modifier);
        tool.set(PalaSmeltery.MODIFIERS.get(), next);
        modifiable.onModified(tool, next);
        if (modifier == GrinderModifier.FORTUNE && level != null) {
            // Sans fusion, la fortune de PalaMod est celle du vanilla.
            level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.FORTUNE)
                    .ifPresent(fortune -> tool.enchant(fortune, next.level(GrinderModifier.FORTUNE)));
        }
        items.get(SLOT_MODIFIER).shrink(1);
        setChanged();
    }

    /**
     * La fusion de la pioche des dieux : au niveau 100, une seule fois, sans
     * paladium. <b>Ecart</b> : PalaMod laissait aussi poser l'amelioration sur
     * un marteau ou une epee qui avait un emplacement libre — elle y etait
     * consommee pour rien. Ici, elle ne va que sur la pioche.
     */
    private boolean autoSmeltApplicable() {
        ItemStack tool = items.get(SLOT_TOOL);
        return items.get(SLOT_MODIFIER).is(PalaGodPickaxe.AUTO_SMELT.get())
                && tool.getItem() instanceof GodPickaxeItem
                && GodPickaxeItem.level(tool) >= GodPickaxeLevels.MAX
                && !GodPickaxeItem.data(tool).autoSmelt();
    }

    private void applyAutoSmelt() {
        ItemStack tool = items.get(SLOT_TOOL);
        tool.set(PalaGodPickaxe.DATA.get(), GodPickaxeItem.data(tool).withAutoSmelt());
        items.get(SLOT_MODIFIER).shrink(1);
        setChanged();
    }

    // -- Cases -----------------------------------------------------------------

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_FIRST -> GrinderRecipes.isFirst(stack);
            case SLOT_SECOND -> GrinderRecipes.isSecond(stack);
            case SLOT_MODIFIER -> GrinderModifier.of(stack.getItem()) != null
                    || stack.is(PalaGodPickaxe.AUTO_SMELT.get());
            case SLOT_TOOL -> stack.getItem() instanceof ModifiableTool tool && tool.modifiable()
                    || stack.getItem() instanceof GodPickaxeItem;
            case SLOT_FUEL -> isFuel(stack) && paladium < MAX_PALADIUM;
            default -> false;
        };
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
        paladium = input.getIntOr("Paladium", 0);
        workCreation = input.getIntOr("WorkCreation", 0);
        workUpgrade = input.getIntOr("WorkUpgrade", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Paladium", paladium);
        output.putInt("WorkCreation", workCreation);
        output.putInt("WorkUpgrade", workUpgrade);
    }
}

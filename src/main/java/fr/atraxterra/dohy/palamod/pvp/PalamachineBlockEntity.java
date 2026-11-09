package fr.atraxterra.dohy.palamod.pvp;

import fr.atraxterra.dohy.palamod.machine.MachineBlockEntity;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;

/**
 * La palamachine ({@code PaladiumMachineLogic}) : cinq entrees, une sortie,
 * 200 ticks (10 secondes) par fabrication, sans combustible.
 *
 * <p>Releve dans le code :
 * <ul>
 *   <li>la machine tourne tant que les cinq cases d'entree forment une recette
 *       et que la sortie peut la recevoir ; elle repart de zero sinon ;</li>
 *   <li>a la fin, une unite de chaque entree est consommee ;</li>
 *   <li>le resultat <b>reprend les enchantements de la piece centrale</b>
 *       ({@code ench} recopie) — le wiki dit qu'un anneau repare perd les
 *       siens ; le code dit le contraire, et c'est lui qu'on suit ;</li>
 *   <li>un stick repare compte ses passages ({@code repair}) et la machine
 *       refuse le sixieme ({@code limiteType} : 5).</li>
 * </ul>
 *
 * <p><b>Ecarts.</b> L'original consommait les entrees meme quand la sortie
 * contenait un autre objet (le resultat etait alors perdu) : ici la machine
 * attend. Son compteur de reparations partait a zero au premier passage,
 * puis comptait — le premier etait donc gratuit, et un stick de force passe en
 * stick divin lui leguait le sien ; ici chaque reparation d'un stick par
 * lui-meme compte un, et une creation repart de zero.
 *
 * <p>Aucune face : ni entonnoir ni conduit ({@code getAccessibleSlotsFromSide}
 * rend un tableau vide).
 */
public class PalamachineBlockEntity extends MachineBlockEntity implements WorldlyContainer {

    public static final int SLOT_CENTER = 0;
    public static final int SLOT_OUTPUT = 5;
    public static final int SLOT_COUNT = 6;

    /** {@code timeNeeded}. */
    private static final int WORK_GOAL = 200;

    private static final int[] NONE = new int[0];

    private int work;

    public PalamachineBlockEntity(BlockPos pos, BlockState state) {
        super(PalaPvp.PALAMACHINE_ENTITY.get(), pos, state, SLOT_COUNT,
                Component.translatable("block.palamod.palamachine"));
    }

    @Override
    protected MenuType<MachineMenu> menuType() {
        return PalaPvp.PALAMACHINE_MENU.get();
    }

    @Override
    protected MachineLayout layout() {
        return MachineLayout.PALAMACHINE;
    }

    @Override
    public int work() {
        return work;
    }

    @Override
    public int cycleDuration() {
        return craftable() != null ? WORK_GOAL : 0;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot != SLOT_OUTPUT;
    }

    /** Ce que la machine fabriquerait maintenant, ou {@code null}. */
    private ItemStack craftable() {
        List<ItemStack> inputs = items.subList(SLOT_CENTER, SLOT_OUTPUT);
        if (inputs.stream().anyMatch(ItemStack::isEmpty)) {
            return null;
        }
        PalamachineRecipes.Recipe recipe = PalamachineRecipes.find(inputs);
        if (recipe == null) {
            return null;
        }
        ItemStack center = items.get(SLOT_CENTER);
        ItemStack result = new ItemStack(recipe.result());
        ItemEnchantments enchantments = center.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) {
            result.set(DataComponents.ENCHANTMENTS, enchantments);
        }
        if (center.getItem() instanceof StickItem && center.is(result.getItem())) {
            int repairs = center.getOrDefault(PalaPvp.STICK_REPAIRS.get(), 0);
            if (repairs >= StickType.MAX_REPAIRS) {
                return null;
            }
            result.set(PalaPvp.STICK_REPAIRS.get(), repairs + 1);
        }
        ItemStack output = items.get(SLOT_OUTPUT);
        if (!output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, result)
                || output.getCount() + result.getCount() > output.getMaxStackSize())) {
            return null;
        }
        return result;
    }

    @Override
    public void serverTick() {
        ItemStack result = craftable();
        if (result == null) {
            if (work != 0) {
                work = 0;
                setChanged();
            }
            return;
        }
        work++;
        if (work >= WORK_GOAL) {
            ItemStack output = items.get(SLOT_OUTPUT);
            if (output.isEmpty()) {
                items.set(SLOT_OUTPUT, result);
            } else {
                output.grow(result.getCount());
            }
            for (int slot = SLOT_CENTER; slot < SLOT_OUTPUT; slot++) {
                items.get(slot).shrink(1);
            }
            work = 0;
        }
        setChanged();
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
        work = input.getIntOr("Work", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Work", work);
    }
}

package fr.atraxterra.dohy.palamod.machine;

import fr.atraxterra.dohy.palamod.alchemist.PalaAlchemist;
import fr.atraxterra.dohy.palamod.nature.PalaNature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Le totem fleuri ({@code TileFlowerFarm}) : de la poudre d'os en entree, des
 * fleurs et des graines en sortie, au coeur d'un petit jardin bati.
 *
 * <p><b>Tout est releve au bytecode de {@code TileFlowerFarm}.</b> Un cycle
 * dure 101 ticks : le compteur descend de 100 a 0, puis le totem brule une
 * poudre d'os et rend deux fleurs (coquelicot ou pissenlit, une chance sur
 * deux chacune) et deux graines de ble, avec des etincelles de poudre d'os sur
 * le jardin. Sans poudre, sans jardin complet, ou les neuf cases de sortie
 * pleines, le compteur revient a 100. Ce qui ne tient pas dans les sorties est
 * perdu, comme dans l'original.
 *
 * <p><b>Le jardin</b> ({@code checkFirstFloor}, {@code validateSecondFloorPattern}) :
 * sous le totem, un carre de 3 x 3 en damier — herbe aux coins et au centre,
 * podzol sur les bords ; autour du totem, a sa hauteur, les huit cases portent,
 * dans n'importe quel ordre, un buisson a XP, un coquelicot, un pissenlit, une
 * orchidee bleue, un allium, une tulipe blanche, une fleur minerale et un
 * entonnoir.
 *
 * <p>L'entonnoir sert a l'alimenter : seule la case de poudre d'os accepte un
 * depot, et rien ne sort par un entonnoir — les fleurs se ramassent a la main,
 * comme dans l'original.
 */
public class FlowerTotemBlockEntity extends MachineBlockEntity {

    public static final int SLOT_FUEL = 0;
    public static final int FIRST_OUTPUT = 1;
    public static final int SLOT_COUNT = 10;

    /** Le compteur part de 100 et produit a 0 : 101 ticks par cycle. */
    public static final int CYCLE = 100;

    /** Le jardin n'est relu qu'une fois par seconde, et au moment de produire. */
    private static final int CHECK_PERIOD = 20;

    /**
     * Les huit voisins attendus, dans l'ordre des bits de {@link #ringMask()}.
     * L'ecran les nomme dans le meme ordre.
     */
    public static final List<Supplier<Block>> RING = List.of(
            () -> PalaNature.XP_BUSH.get(),
            () -> Blocks.POPPY,
            () -> Blocks.DANDELION,
            () -> Blocks.BLUE_ORCHID,
            () -> Blocks.ALLIUM,
            () -> Blocks.WHITE_TULIP,
            () -> PalaAlchemist.MINERAL_FLOWER.get(),
            () -> Blocks.HOPPER);

    private int remaining = CYCLE;
    private int floorMask;
    private int ringMask;
    private long nextCheck;

    public FlowerTotemBlockEntity(BlockPos pos, BlockState state) {
        super(PalaMachines.FLOWER_TOTEM.get(), pos, state, SLOT_COUNT,
                Component.translatable("block.palamod.flower_totem"));
    }

    // -- Le jardin ------------------------------------------------------------

    /** Bit {@code i} : la case {@code i} du sol (de -1,-1 a +1,+1, ligne par ligne) est la bonne. */
    public int floorMask() {
        return floorMask;
    }

    /** Bit {@code i} : le voisin {@code RING[i]} est a sa place. */
    public int ringMask() {
        return ringMask;
    }

    public boolean gardenComplete() {
        return floorMask == 0x1FF && ringMask == (1 << RING.size()) - 1;
    }

    private void checkGarden(Level level) {
        BlockPos pos = getBlockPos();
        int floor = 0;
        int index = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                Block expected = (Math.abs(dx) + Math.abs(dz)) % 2 == 1 ? Blocks.PODZOL : Blocks.GRASS_BLOCK;
                if (level.getBlockState(pos.offset(dx, -1, dz)).is(expected)) {
                    floor |= 1 << index;
                }
                index++;
            }
        }
        // Un multi-ensemble : chaque voisin attendu est pris une fois.
        List<Block> around = new ArrayList<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dz != 0) {
                    around.add(level.getBlockState(pos.offset(dx, 0, dz)).getBlock());
                }
            }
        }
        int ring = 0;
        for (int i = 0; i < RING.size(); i++) {
            if (around.remove(RING.get(i).get())) {
                ring |= 1 << i;
            }
        }
        floorMask = floor;
        ringMask = ring;
    }

    // -- Le cycle ---------------------------------------------------------------

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        long now = server.getGameTime();
        if (now >= nextCheck || remaining <= 0) {
            checkGarden(server);
            nextCheck = now + CHECK_PERIOD;
        }
        if (!canRun()) {
            if (remaining != CYCLE) {
                remaining = CYCLE;
                setChanged();
            }
            return;
        }
        if (remaining > 0) {
            remaining--;
            return;
        }
        items.get(SLOT_FUEL).shrink(1);
        remaining = CYCLE;
        for (ItemStack drop : drops(server)) {
            store(drop);
        }
        sparkle(server);
        setChanged();
    }

    private boolean canRun() {
        return items.get(SLOT_FUEL).is(Items.BONE_MEAL) && gardenComplete() && !outputsFull();
    }

    /** Les neuf sorties sont-elles toutes a 64 ? ({@code getStackInSlot(i).stackSize >= 64}) */
    private boolean outputsFull() {
        for (int slot = FIRST_OUTPUT; slot < SLOT_COUNT; slot++) {
            if (items.get(slot).getCount() < 64) {
                return false;
            }
        }
        return true;
    }

    /** Deux fleurs, coquelicot ou pissenlit, et deux graines de ble. */
    private static List<ItemStack> drops(ServerLevel level) {
        List<ItemStack> drops = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            drops.add(new ItemStack(level.getRandom().nextBoolean() ? Items.POPPY : Items.DANDELION));
        }
        drops.add(new ItemStack(Items.WHEAT_SEEDS, 2));
        return drops;
    }

    /** Range dans les sorties, piles existantes d'abord ; le reste est perdu. */
    private void store(ItemStack drop) {
        for (int slot = FIRST_OUTPUT; slot < SLOT_COUNT && !drop.isEmpty(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, drop)) {
                int moved = Math.min(drop.getCount(), stack.getMaxStackSize() - stack.getCount());
                stack.grow(moved);
                drop.shrink(moved);
            }
        }
        for (int slot = FIRST_OUTPUT; slot < SLOT_COUNT && !drop.isEmpty(); slot++) {
            if (items.get(slot).isEmpty()) {
                items.set(slot, drop.copy());
                drop.setCount(0);
            }
        }
    }

    /** Les etincelles de la poudre d'os, sur les neuf cases du jardin. */
    private void sparkle(ServerLevel level) {
        BlockPos pos = getBlockPos();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + dx + 0.5d, pos.getY() + 0.3d,
                        pos.getZ() + dz + 0.5d, 4, 0.3d, 0.2d, 0.3d, 0.0d);
            }
        }
    }

    // -- Inventaire et interface ---------------------------------------------------

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_FUEL && stack.is(Items.BONE_MEAL);
    }

    @Override
    protected boolean hopperMayTake(int slot) {
        return false;
    }

    @Override
    protected MenuType<MachineMenu> menuType() {
        return PalaMenus.FLOWER_TOTEM.get();
    }

    @Override
    protected MachineLayout layout() {
        return MachineLayout.FLOWER_TOTEM;
    }

    @Override
    public int work() {
        return CYCLE - remaining;
    }

    @Override
    public int cycleDuration() {
        return canRun() ? CYCLE : 0;
    }

    /** 0 : le sol ; 1 : les voisins. */
    @Override
    public int extra(int index) {
        return switch (index) {
            case 0 -> floorMask;
            case 1 -> ringMask;
            default -> 0;
        };
    }

    // -- Sauvegarde -------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        remaining = input.getIntOr("Work", CYCLE);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Work", remaining);
    }
}

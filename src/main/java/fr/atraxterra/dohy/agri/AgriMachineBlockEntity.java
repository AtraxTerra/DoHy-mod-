package fr.atraxterra.dohy.agri;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * L'entite des machines de l'agriculture : une reserve d'energie, et pour le
 * biogenerateur une tremie de cereales qu'il brule.
 *
 * <p>L'energie est celle de NeoForge (FE), offerte sur toutes les faces : un
 * cable de NTM NEXT, ou de tout mod qui parle FE, alimente le moulin et la
 * machine a pain. Sans autre mod, le biogenerateur suffit : pose contre elles,
 * il leur pousse ce qu'il produit.
 *
 * <p>Le moulin et la machine a pain ne font que <b>recevoir</b> ; le
 * biogenerateur ne fait que <b>donner</b>. Aucune ne sert de pile.
 */
public class AgriMachineBlockEntity extends BlockEntity {

    private final Energy energy = new Energy();
    private int fuel;
    private int burning;
    private int ticks;
    private boolean dirty;

    public AgriMachineBlockEntity(BlockPos pos, BlockState state) {
        super(DoHyAgri.MACHINE_TYPE.get(), pos, state);
    }

    private AgriMachineBlock.Kind kind() {
        return getBlockState().getBlock() instanceof AgriMachineBlock machine ? machine.kind()
                : AgriMachineBlock.Kind.MILL;
    }

    private boolean generator() {
        return kind() == AgriMachineBlock.Kind.BIOGEN;
    }

    public EnergyHandler energy() {
        return energy;
    }

    public int energyStored() {
        return energy.amount;
    }

    public int capacity() {
        return AgriConfig.config().machines().capacity();
    }

    /** Depense de l'energie. Faux, et rien n'est pris, s'il n'y en a pas assez. */
    public boolean spend(int cost) {
        if (cost > energy.amount) {
            return false;
        }
        energy.amount -= cost;
        dirty = true;
        setChanged();
        return true;
    }

    // -- Le biogenerateur ----------------------------------------------------------

    /** Cereales en attente dans la tremie. */
    public int fuel() {
        return fuel;
    }

    /** Charge des cereales ; rend ce qui a ete pris. */
    public int addFuel(int offered) {
        int taken = Math.max(0, Math.min(offered, AgriConfig.config().machines().biogenHopper() - fuel));
        if (taken > 0) {
            fuel += taken;
            setChanged();
        }
        return taken;
    }

    public String fuelReport() {
        return "Biogenerateur : " + fuel + " cereales en attente  ·  " + energy.amount + " / " + capacity() + " FE";
    }

    void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        AgriConfig.Machines config = AgriConfig.config().machines();
        if (generator()) {
            burn(config);
            push(server, config);
        }
        if (ticks++ % 20 == 0) {
            boolean lit = generator() ? burning > 0 : energy.amount >= lowestCost(config);
            BlockState state = getBlockState();
            if (state.hasProperty(AgriMachineBlock.LIT) && state.getValue(AgriMachineBlock.LIT) != lit) {
                level.setBlock(worldPosition, state.setValue(AgriMachineBlock.LIT, lit), Block.UPDATE_CLIENTS);
            }
            if (dirty) {
                dirty = false;
                setChanged();
            }
        }
    }

    /** L'energie qu'il faut pour que la machine fasse au moins une chose : en dessous, elle est eteinte. */
    private int lowestCost(AgriConfig.Machines config) {
        if (kind() == AgriMachineBlock.Kind.MILL) {
            return Math.max(1, config.millEnergy());
        }
        int lowest = Integer.MAX_VALUE;
        for (AgriConfig.Batch batch : AgriConfig.config().breads()) {
            lowest = Math.min(lowest, batch.energy());
        }
        return Math.max(1, lowest == Integer.MAX_VALUE ? 1 : lowest);
    }

    /** Une cereale brule tant que la reserve a de la place pour ce qu'elle donne. */
    private void burn(AgriConfig.Machines config) {
        if (burning <= 0 && fuel > 0 && energy.amount + config.biogenPerCereal() <= config.capacity()) {
            fuel--;
            burning = config.biogenPerCereal();
            dirty = true;
        }
        if (burning > 0) {
            int produced = Math.min(Math.min(config.biogenPerTick(), burning), config.capacity() - energy.amount);
            if (produced > 0) {
                burning -= produced;
                energy.amount += produced;
                dirty = true;
            }
        }
    }

    /** Pousse l'energie vers ce qui touche le biogenerateur et en accepte. */
    private void push(ServerLevel server, AgriConfig.Machines config) {
        if (energy.amount <= 0 || config.biogenPush() <= 0) {
            return;
        }
        // Le premier voisin servi tourne a chaque tick : a 20 FE par tick, un ordre
        // fixe donnerait tout au meme, et la seconde machine attendrait qu'il soit plein.
        Direction[] sides = Direction.values();
        for (int step = 0; step < sides.length; step++) {
            Direction side = sides[(ticks + step) % sides.length];
            if (energy.amount <= 0) {
                return;
            }
            EnergyHandler neighbor = Capabilities.Energy.BLOCK.getCapability(server, worldPosition.relative(side),
                    null, null, side.getOpposite());
            if (neighbor == null) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int accepted = neighbor.insert(Math.min(config.biogenPush(), energy.amount), transaction);
                if (accepted > 0) {
                    energy.amount -= accepted;
                    dirty = true;
                    transaction.commit();
                }
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("energy", energy.amount);
        output.putInt("fuel", fuel);
        output.putInt("burning", burning);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.amount = Math.max(0, input.getIntOr("energy", 0));
        fuel = Math.max(0, input.getIntOr("fuel", 0));
        burning = Math.max(0, input.getIntOr("burning", 0));
    }

    /**
     * La reserve, vue par les cables. Une insertion faite dans une transaction
     * abandonnee est defaite : c'est ainsi qu'un reseau demande « combien
     * acceptes-tu ? » sans rien donner.
     */
    private final class Energy extends SnapshotJournal<Integer> implements EnergyHandler {
        private int amount;

        @Override
        public long getAmountAsLong() {
            return amount;
        }

        @Override
        public long getCapacityAsLong() {
            return capacity();
        }

        @Override
        public int insert(int offered, TransactionContext transaction) {
            if (generator()) {
                return 0;
            }
            AgriConfig.Machines config = AgriConfig.config().machines();
            int fits = Math.min(Math.min(offered, config.maxInput()), config.capacity() - amount);
            if (fits <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            amount += fits;
            return fits;
        }

        @Override
        public int extract(int wanted, TransactionContext transaction) {
            if (!generator()) {
                return 0;
            }
            int given = Math.min(wanted, amount);
            if (given <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            amount -= given;
            return given;
        }

        @Override
        protected Integer createSnapshot() {
            return amount;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            amount = snapshot;
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            dirty = true;
        }
    }
}

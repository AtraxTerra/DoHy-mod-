package fr.atraxterra.dohy.agri;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L'entite des machines de l'agriculture : une reserve d'energie, un petit
 * inventaire, et pour le biogenerateur une tremie de cereales qu'il brule.
 *
 * <p><b>L'energie</b> est celle de NeoForge (FE), offerte sur toutes les faces :
 * un cable de NTM NEXT, ou de tout mod qui parle FE, alimente le moulin et la
 * machine a pain. Sans autre mod, le biogenerateur suffit : pose contre elles,
 * il leur pousse ce qu'il produit. Le moulin et la machine a pain ne font que
 * <b>recevoir</b> ; le biogenerateur ne fait que <b>donner</b>.
 *
 * <p><b>L'inventaire</b> fait de ces machines des automates : six cases d'entree
 * et trois de sortie. Un entonnoir pose dessus ou sur un cote les remplit, un
 * entonnoir dessous les vide ; tant qu'il y a de quoi faire et de l'energie, la
 * machine travaille seule. Une sorte d'objet n'occupe qu'<b>une</b> case
 * d'entree : un tuyau qui deverse de la farine ne peut pas boucher la place de
 * la creme. Le biogenerateur n'a que des entrees : ses cereales passent dans la
 * tremie.
 *
 * <p>Ce que l'automate ne fait pas : donner de l'XP de Fermier, sortir le pain
 * en plus du metier, cuire une fournee reservee a un niveau. Ces trois-la
 * demandent une main ({@link MillMenu}, {@link BreadMachineMenu}).
 */
public class AgriMachineBlockEntity extends BlockEntity implements WorldlyContainer {

    public static final int INPUTS = 6;
    public static final int OUTPUTS = 3;
    private static final int[] INPUT_SLOTS = {0, 1, 2, 3, 4, 5};
    private static final int[] OUTPUT_SLOTS = {6, 7, 8};
    private static final int[] NO_SLOTS = {};

    private final Energy energy = new Energy();
    private final NonNullList<ItemStack> items = NonNullList.withSize(INPUTS + OUTPUTS, ItemStack.EMPTY);
    private UUID owner;
    private int fuel;
    private int burning;
    private int progress;
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
        switch (kind()) {
            case BIOGEN -> {
                refuel(config);
                burn(config);
                push(server, config);
            }
            case MILL -> work(server, millJob(config), config.millTicks());
            case OVEN -> work(server, bakeJob(), config.bakeTicks());
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

    // -- L'automate ---------------------------------------------------------------------

    /**
     * Un travail que l'automate peut faire maintenant.
     *
     * @param inputs ce qu'il consomme dans les cases d'entree
     * @param output ce qu'il pose dans les cases de sortie
     * @param count  combien
     * @param energy son cout en FE
     */
    private record Job(Map<Item, Integer> inputs, Item output, int count, int energy) {
    }

    /** La premiere farine que le moulin peut moudre avec ce qu'il a en entree, ou {@code null}. */
    private Job millJob(AgriConfig.Machines config) {
        for (String key : DoHyAgri.flourKeys()) {
            Item grain = DoHyAgri.grain(key);
            if (stored(grain) >= config.millRatio()) {
                return new Job(Map.of(grain, config.millRatio()), DoHyAgri.flour(key), 1, config.millEnergy());
            }
        }
        return null;
    }

    /** La premiere fournee que la machine a pain peut cuire seule, ou {@code null}. */
    private Job bakeJob() {
        for (AgriConfig.Batch batch : AgriConfig.config().breads()) {
            Bread bread = Bread.byKey(batch.id());
            if (bread == null || batch.minLevel() > 0) {
                continue;
            }
            Map<Item, Integer> inputs = new LinkedHashMap<>();
            boolean ready = true;
            for (Map.Entry<Identifier, Integer> input : batch.inputs().entrySet()) {
                Item item = BuiltInRegistries.ITEM.getOptional(input.getKey()).orElse(null);
                if (item == null || stored(item) < input.getValue()) {
                    ready = false;
                    break;
                }
                inputs.put(item, input.getValue());
            }
            if (ready) {
                return new Job(inputs, DoHyAgri.bread(bread), batch.count(), batch.energy());
            }
        }
        return null;
    }

    /** Avance le travail en cours ; arrive au bout, consomme, produit et depense. */
    private void work(ServerLevel server, Job job, int duration) {
        if (job == null || energy.amount < job.energy() || !fits(job.output(), job.count())) {
            progress = 0;
            return;
        }
        if (++progress < duration) {
            return;
        }
        progress = 0;
        energy.amount -= job.energy();
        job.inputs().forEach(this::consume);
        deposit(job.output(), job.count());
        dirty = true;
        setChanged();
        if (owner != null && server.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer player) {
            AgriProduction.produced(player, job.output(), job.count());
        }
    }

    /** Le travail en cours, en pour mille ; 0 quand la machine attend. */
    public int progressPermille() {
        AgriConfig.Machines config = AgriConfig.config().machines();
        int duration = kind() == AgriMachineBlock.Kind.MILL ? config.millTicks() : config.bakeTicks();
        return duration <= 0 ? 0 : Math.min(1000, progress * 1000 / duration);
    }

    /** Exemplaires de cet objet dans les cases d'entree. */
    private int stored(Item item) {
        int count = 0;
        for (int slot = 0; slot < INPUTS; slot++) {
            if (items.get(slot).is(item)) {
                count += items.get(slot).getCount();
            }
        }
        return count;
    }

    private void consume(Item item, int wanted) {
        int left = wanted;
        for (int slot = 0; slot < INPUTS && left > 0; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.is(item)) {
                int part = Math.min(left, stack.getCount());
                stack.shrink(part);
                left -= part;
            }
        }
    }

    /** Vrai si les cases de sortie ont la place pour ce produit. */
    private boolean fits(Item item, int count) {
        int room = 0;
        for (int slot = INPUTS; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                room += new ItemStack(item).getMaxStackSize();
            } else if (stack.is(item)) {
                room += stack.getMaxStackSize() - stack.getCount();
            }
        }
        return room >= count;
    }

    private void deposit(Item item, int count) {
        int left = count;
        for (int slot = INPUTS; slot < items.size() && left > 0; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.is(item)) {
                int part = Math.min(left, stack.getMaxStackSize() - stack.getCount());
                stack.grow(part);
                left -= part;
            }
        }
        for (int slot = INPUTS; slot < items.size() && left > 0; slot++) {
            if (items.get(slot).isEmpty()) {
                int part = Math.min(left, new ItemStack(item).getMaxStackSize());
                items.set(slot, new ItemStack(item, part));
                left -= part;
            }
        }
    }

    /** Vrai si la machine prend cet objet en entree. */
    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        switch (kind()) {
            case BIOGEN -> {
                return stack.is(DoHyAgri.CEREALS);
            }
            case MILL -> {
                for (String key : DoHyAgri.flourKeys()) {
                    if (stack.is(DoHyAgri.grain(key))) {
                        return true;
                    }
                }
                return false;
            }
            default -> {
                Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                for (AgriConfig.Batch batch : AgriConfig.config().breads()) {
                    if (batch.minLevel() <= 0 && batch.inputs().containsKey(id)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    /**
     * Depose a la main dans les cases d'entree, sous la meme regle que les
     * entonnoirs. Rend ce qui a ete pris ; la pile donnee est diminuee d'autant.
     */
    public int insert(ItemStack stack) {
        if (!accepts(stack)) {
            return 0;
        }
        int before = stack.getCount();
        for (int slot = 0; slot < INPUTS && !stack.isEmpty(); slot++) {
            ItemStack held = items.get(slot);
            if (ItemStack.isSameItemSameComponents(held, stack)) {
                int part = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
                held.grow(part);
                stack.shrink(part);
            }
        }
        for (int slot = 0; slot < INPUTS && !stack.isEmpty(); slot++) {
            if (items.get(slot).isEmpty() && canPlaceItem(slot, stack)) {
                items.set(slot, stack.copyAndClear());
            }
        }
        int taken = before - stack.getCount();
        if (taken > 0) {
            setChanged();
        }
        return taken;
    }

    /** Les cereales deposees dans le biogenerateur passent dans sa tremie. */
    private void refuel(AgriConfig.Machines config) {
        for (int slot = 0; slot < INPUTS && fuel < config.biogenHopper(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) {
                int part = Math.min(stack.getCount(), config.biogenHopper() - fuel);
                stack.shrink(part);
                fuel += part;
                dirty = true;
            }
        }
    }

    // -- Le proprietaire -------------------------------------------------------------------

    /** Celui qui a pose la machine : ce qu'elle produit seule compte pour ses contrats. */
    public void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        setChanged();
    }

    // -- L'inventaire, vu par les entonnoirs -----------------------------------------------

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    /**
     * Une case d'entree prend ce que la machine accepte, et une sorte d'objet
     * n'occupe qu'une case : sans cela, un tuyau de farine remplirait les six et
     * la creme n'entrerait plus jamais.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot >= INPUTS || !accepts(stack)) {
            return false;
        }
        for (int other = 0; other < INPUTS; other++) {
            if (other != slot && items.get(other).is(stack.getItem())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) {
            return generator() ? NO_SLOTS : OUTPUT_SLOTS;
        }
        return INPUT_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= INPUTS;
    }

    /** Cassee, la machine rend ce qu'elle contenait. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, this);
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

    /**
     * Pousse l'energie vers ce qui touche le biogenerateur et en accepte, a parts
     * egales : a 20 FE par tick, servir les voisins dans un ordre fixe donnerait
     * tout au premier, et la seconde machine attendrait qu'il soit plein.
     */
    private void push(ServerLevel server, AgriConfig.Machines config) {
        if (energy.amount <= 0 || config.biogenPush() <= 0) {
            return;
        }
        List<EnergyHandler> neighbors = new ArrayList<>();
        for (Direction side : Direction.values()) {
            EnergyHandler neighbor = Capabilities.Energy.BLOCK.getCapability(server, worldPosition.relative(side),
                    null, null, side.getOpposite());
            if (neighbor != null) {
                neighbors.add(neighbor);
            }
        }
        // Le premier servi tourne a chaque tick, pour que le reste d'une division ne profite pas toujours au meme.
        for (int step = 0; step < neighbors.size() && energy.amount > 0; step++) {
            EnergyHandler neighbor = neighbors.get((ticks + step) % neighbors.size());
            int left = neighbors.size() - step;
            int share = Math.min(config.biogenPush(), (energy.amount + left - 1) / left);
            try (Transaction transaction = Transaction.openRoot()) {
                int accepted = neighbor.insert(share, transaction);
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
        output.putInt("progress", progress);
        output.putString("owner", owner == null ? "" : owner.toString());
        ContainerHelper.saveAllItems(output, items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.amount = Math.max(0, input.getIntOr("energy", 0));
        fuel = Math.max(0, input.getIntOr("fuel", 0));
        burning = Math.max(0, input.getIntOr("burning", 0));
        progress = Math.max(0, input.getIntOr("progress", 0));
        String raw = input.getStringOr("owner", "");
        try {
            owner = raw.isEmpty() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException malformed) {
            owner = null;
        }
        items.clear();
        ContainerHelper.loadAllItems(input, items);
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

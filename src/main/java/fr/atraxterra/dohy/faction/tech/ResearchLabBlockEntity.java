package fr.atraxterra.dohy.faction.tech;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le laboratoire de recherche (chapitre 23, lot 23c) : de l'energie en entree, des points de
 * recherche en sortie, pour la nation de celui qui l'a pose.
 *
 * <h2>L'energie</h2>
 *
 * <p>Comme le serveur de trading, il expose la capacite d'energie de NeoForge ({@link EnergyHandler},
 * en FE), en reception seule : les cables de NTM NEXT y poussent leur courant (5 HE = 1 FE), et tout
 * mod qui parle FE aussi (le biogenerateur de DoHy pousse vers ses voisins). A defaut, il brule du
 * combustible donne d'un clic droit, a mi-regime.
 *
 * <h2>Les points</h2>
 *
 * <p>Chaque tick de marche ajoute une fraction des points horaires ({@link #fraction} sur 72 000
 * ticks par heure, en entiers). Les points restent dans le laboratoire ({@link #buffer}) et passent
 * aux recherches de la nation chaque seconde, quand son proprietaire est la : sa nation vit sur lui.
 * Sans recherche, le laboratoire garde sa reserve, puis s'arrete.
 *
 * <h2>Le nombre</h2>
 *
 * <p>Le Gouvernement permet un, deux puis trois laboratoires. La nation tient la liste des siens
 * ({@link FactionData#labs()}) ; un laboratoire au-dela du nombre permis attend.
 */
public class ResearchLabBlockEntity extends BlockEntity {

    /** L'etat du laboratoire, du plus bloquant au moins bloquant. */
    public enum Status {
        NO_OWNER("Sans proprietaire", "Repose-le pour te l'attribuer"),
        NO_NATION("Sans nation", "Son proprietaire n'a pas de nation"),
        OVER_CAP("En surnombre", "Le Gouvernement ne permet pas autant de laboratoires"),
        FULL("Reserve pleine", "Lance une recherche : elle prendra ses points"),
        NO_POWER("Hors tension", "Branche-le sur un reseau d'energie, ou donne-lui du combustible"),
        RUNNING("En marche", "Il produit des points de recherche");

        private final String label;
        private final String hint;

        Status(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }

        public String label() {
            return label;
        }

        public String hint() {
            return hint;
        }
    }

    private static final int TICKS_PER_HOUR = 72_000;

    /** Les laboratoires en memoire, par proprietaire : leur cle et leurs points bruts par heure. */
    private static final Map<UUID, Map<String, Double>> LOADED = new ConcurrentHashMap<>();

    private final Energy energy = new Energy();
    private UUID owner;
    private String ownerName = "";
    /** Les points bruts produits, pas encore pris par une recherche. */
    private long buffer;
    /** Le reste de la division des points horaires par 72 000, reporte de tick en tick. */
    private long fraction;
    private long produced;
    /** Le combustible qui reste, en ticks de combustion. */
    private int fuel;
    private int ticks;
    /** Les ticks de marche de la seconde ecoulee, pour le rythme affiche. */
    private int worked;
    private int lastWorked;
    /** Ce que la nation disait a la derniere visite de son proprietaire. */
    private boolean nation = true;
    private boolean allowed = true;
    private int cost = -1;
    private double multiplier = 1.0d;
    private String nationName = "";
    private boolean dirty;

    public ResearchLabBlockEntity(BlockPos pos, BlockState state) {
        super(DoHyResearch.LAB_TYPE.get(), pos, state);
    }

    // -- Ce que l'ecran et le bloc lisent ------------------------------------------------------------

    public EnergyHandler energy() {
        return energy;
    }

    public int energyStored() {
        return energy.amount;
    }

    public long buffer() {
        return buffer;
    }

    public long produced() {
        return produced;
    }

    public int fuel() {
        return fuel;
    }

    public String ownerName() {
        return ownerName;
    }

    public String nationName() {
        return nationName;
    }

    public UUID owner() {
        return owner;
    }

    public boolean isOwner(ServerPlayer player) {
        return owner != null && owner.equals(player.getUUID());
    }

    /** L'energie par tick de marche, bonus de l'Energie compris (lus a la derniere visite du proprietaire). */
    public int cost() {
        return cost > 0 ? cost : ResearchLabConfig.config().energyPerTick();
    }

    /** Ce que valent ses points pour la nation : vitesse de recherche et pays. */
    public double multiplier() {
        return multiplier;
    }

    /** Les points bruts par heure de la seconde ecoulee. */
    public double rawRate() {
        return ResearchLabConfig.config().pointsPerHour() * lastWorked / 20.0d;
    }

    public void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().name();
        FactionData data = FactionApi.data(player);
        nationName = data.name();
        setChanged();
    }

    public Status status() {
        ResearchLabConfig.Config config = ResearchLabConfig.config();
        if (owner == null) {
            return Status.NO_OWNER;
        }
        if (!nation) {
            return Status.NO_NATION;
        }
        if (!allowed) {
            return Status.OVER_CAP;
        }
        if (buffer >= config.bufferPoints()) {
            return Status.FULL;
        }
        if (energy.amount < cost() && fuel <= 0) {
            return Status.NO_POWER;
        }
        return Status.RUNNING;
    }

    /** La cle du laboratoire dans la liste de sa nation : « minecraft:overworld@12,64,-30 ». */
    public String key() {
        return key(level, worldPosition);
    }

    public static String key(Level level, BlockPos pos) {
        return (level == null ? "?" : level.dimension().identifier().toString()) + "@" + pos.getX() + ","
                + pos.getY() + "," + pos.getZ();
    }

    // -- Le tick ----------------------------------------------------------------------------------------

    void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        ResearchLabConfig.Config config = ResearchLabConfig.config();
        if (ticks++ % 20 == 0) {
            lastWorked = worked;
            worked = 0;
            sync(server.getServer());
            boolean lit = status() == Status.RUNNING && lastWorked > 0;
            BlockState state = getBlockState();
            if (state.hasProperty(ResearchLabBlock.LIT) && state.getValue(ResearchLabBlock.LIT) != lit) {
                level.setBlock(worldPosition, state.setValue(ResearchLabBlock.LIT, lit), Block.UPDATE_CLIENTS);
            }
            if (dirty) {
                dirty = false;
                setChanged();
            }
        }
        if (owner == null || !nation || !allowed || buffer >= config.bufferPoints()) {
            return;
        }
        int need = cost();
        if (energy.amount < need && fuel > 0) {
            fuel--;
            energy.amount = Math.min(config.energyCapacity(), energy.amount + config.fuelPerTick());
            dirty = true;
        }
        if (energy.amount < need) {
            return;
        }
        energy.amount -= need;
        worked++;
        fraction += config.pointsPerHour();
        if (fraction >= TICKS_PER_HOUR) {
            long gained = fraction / TICKS_PER_HOUR;
            fraction -= gained * TICKS_PER_HOUR;
            buffer = Math.min(config.bufferPoints(), buffer + gained);
            produced += gained;
        }
        dirty = true;
    }

    /**
     * Chaque seconde : le laboratoire se presente, et si son proprietaire est la, il lit sa nation
     * (le nombre permis, les bonus) et verse ses points a ses recherches.
     */
    private void sync(MinecraftServer server) {
        if (owner == null) {
            return;
        }
        LOADED.computeIfAbsent(owner, ignored -> new ConcurrentHashMap<>()).put(key(), rawRate());
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player == null) {
            return;
        }
        FactionData data = FactionApi.data(player);
        nation = data.exists();
        nationName = data.name();
        if (!nation) {
            return;
        }
        String key = key();
        int cap = TechPerks.of(data).labs();
        List<String> labs = data.labs();
        if (!labs.contains(key) && labs.size() < cap) {
            data.addLab(key);
            labs = data.labs();
        }
        int index = labs.indexOf(key);
        allowed = index >= 0 && index < cap;
        cost = Tech.labEnergy(data);
        multiplier = Tech.multiplier(player);
        if (allowed && buffer > 0L) {
            long used = Tech.feed(player, buffer, multiplier);
            if (used > 0L) {
                buffer -= used;
                dirty = true;
            }
        }
    }

    /** Un clic droit avec du combustible : il brule a la place de l'electricite. */
    public int addFuel(int burnTicks) {
        int room = Math.max(0, ResearchLabConfig.config().fuelMaxTicks() - fuel);
        int added = Math.min(room, Math.max(0, burnTicks));
        fuel += added;
        if (added > 0) {
            setChanged();
        }
        return added;
    }

    public boolean fuelFull() {
        return fuel >= ResearchLabConfig.config().fuelMaxTicks();
    }

    /** Les points bruts par heure des laboratoires charges de ce proprietaire. */
    public static double rawRate(UUID owner) {
        Map<String, Double> mine = LOADED.get(owner);
        if (mine == null) {
            return 0.0d;
        }
        double total = 0.0d;
        for (double rate : mine.values()) {
            total += rate;
        }
        return total;
    }

    /** Vrai si ce laboratoire de la liste est en memoire, et son rythme. */
    public static Double loadedRate(UUID owner, String key) {
        Map<String, Double> mine = LOADED.get(owner);
        return mine == null ? null : mine.get(key);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (owner != null && level != null && !level.isClientSide()) {
            Map<String, Double> mine = LOADED.get(owner);
            if (mine != null) {
                mine.remove(key());
            }
        }
    }

    /** A l'arret du serveur de jeu : un autre monde ne doit pas heriter de la liste. */
    static void forgetAll() {
        LOADED.clear();
    }

    /** Le bloc est casse : ses points vont aux recherches si son proprietaire est la, et il quitte la liste. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (owner != null && level instanceof ServerLevel server
                && server.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer player) {
            FactionData data = FactionApi.data(player);
            if (buffer > 0L && allowed && data.exists()) {
                Tech.feed(player, buffer, Tech.multiplier(player));
            }
            data.removeLab(key(level, pos));
        }
    }

    /** Le laboratoire de cette cle, s'il est en memoire ; {@code null} sinon. */
    public static ResearchLabBlockEntity at(MinecraftServer server, String key) {
        int at = key.indexOf('@');
        if (at < 0) {
            return null;
        }
        Identifier dimension = Identifier.tryParse(key.substring(0, at));
        String[] parts = key.substring(at + 1).split(",");
        if (dimension == null || parts.length != 3) {
            return null;
        }
        try {
            BlockPos pos = new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null || !level.hasChunkAt(pos)) {
                return null;
            }
            return level.getBlockEntity(pos) instanceof ResearchLabBlockEntity lab ? lab : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    /**
     * Vrai si la cle designe un endroit charge ou le laboratoire n'est plus : la nation peut
     * l'oublier. Un laboratoire dans un chunk decharge reste dans la liste.
     */
    public static boolean gone(MinecraftServer server, String key) {
        int at = key.indexOf('@');
        if (at < 0) {
            return true;
        }
        Identifier dimension = Identifier.tryParse(key.substring(0, at));
        String[] parts = key.substring(at + 1).split(",");
        if (dimension == null || parts.length != 3) {
            return true;
        }
        try {
            BlockPos pos = new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null) {
                return true;
            }
            return level.hasChunkAt(pos) && !(level.getBlockEntity(pos) instanceof ResearchLabBlockEntity);
        } catch (NumberFormatException error) {
            return true;
        }
    }

    // -- Sauvegarde ----------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("owner", owner == null ? "" : owner.toString());
        output.putString("owner_name", ownerName);
        output.putString("nation", nationName);
        output.putInt("energy", energy.amount);
        output.putLong("buffer", buffer);
        output.putLong("fraction", fraction);
        output.putLong("produced", produced);
        output.putInt("fuel", fuel);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String raw = input.getStringOr("owner", "");
        try {
            owner = raw.isEmpty() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException malformed) {
            owner = null;
        }
        ownerName = input.getStringOr("owner_name", "");
        nationName = input.getStringOr("nation", "");
        energy.amount = Math.max(0, input.getIntOr("energy", 0));
        buffer = Math.max(0L, input.getLongOr("buffer", 0L));
        fraction = Math.max(0L, input.getLongOr("fraction", 0L));
        produced = Math.max(0L, input.getLongOr("produced", 0L));
        fuel = Math.max(0, input.getIntOr("fuel", 0));
    }

    /**
     * La reserve d'energie : reception seule, dans une transaction de NeoForge (une insertion peut
     * etre simulee puis annulee : le journal remet la valeur d'avant).
     */
    private final class Energy extends SnapshotJournal<Integer> implements EnergyHandler {
        private int amount;

        @Override
        public long getAmountAsLong() {
            return amount;
        }

        @Override
        public long getCapacityAsLong() {
            return ResearchLabConfig.config().energyCapacity();
        }

        @Override
        public int insert(int offered, TransactionContext transaction) {
            ResearchLabConfig.Config config = ResearchLabConfig.config();
            int fits = Math.min(Math.min(offered, config.energyMaxInput()), config.energyCapacity() - amount);
            if (fits <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            amount += fits;
            return fits;
        }

        @Override
        public int extract(int wanted, TransactionContext transaction) {
            return 0;
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

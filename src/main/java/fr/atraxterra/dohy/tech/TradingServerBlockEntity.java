package fr.atraxterra.dohy.tech;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le serveur de trading : de l'energie en entree, de l'argent en sortie.
 *
 * <h2>L'energie</h2>
 *
 * <p>La machine expose la capacite d'energie de NeoForge ({@link EnergyHandler},
 * en FE), en reception seule. C'est ce que les cables de NTM NEXT cherchent sur
 * un bloc voisin qui n'est pas des leurs : ils y poussent leur courant en
 * convertissant 5 HE en 1 FE. Rien de NTM n'est appele ici — la machine marche
 * avec tout mod qui parle FE, et le jeu se lance sans NTM.
 *
 * <h2>L'argent</h2>
 *
 * <p>Chaque tick alimente ajoute une fraction du revenu horaire. Le compte se
 * tient en entiers ({@link #fraction} sur 72 000 ticks par heure) : aucun
 * arrondi ne se perd, quelle que soit la valeur du fichier. L'argent reste dans
 * la machine jusqu'a ce que son proprietaire l'encaisse ; au-dela du tampon,
 * elle s'arrete — un revenu passif qui demande quand meme de passer.
 *
 * <h2>La limite</h2>
 *
 * <p>Seuls les premiers serveurs charges d'un joueur tournent
 * ({@code max_per_player}). Le compte se fait sur les machines en memoire :
 * un serveur dans un chunk decharge ne produit pas et ne prend pas de place.
 */
public class TradingServerBlockEntity extends BlockEntity {

    /** L'etat de la machine, du plus bloquant au moins bloquant. */
    public enum Status {
        NO_OWNER("Sans proprietaire", "Repose-le pour te l'attribuer"),
        LIMIT("En attente", "Trop de serveurs tournent deja"),
        FULL("Caisse pleine", "Encaisse pour relancer la production"),
        NO_POWER("Hors tension", "Branche-le sur un reseau d'energie"),
        RUNNING("En marche", "Il produit");

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

    /** Les serveurs en memoire, par proprietaire, dans l'ordre ou ils se sont presentes. */
    private static final Map<UUID, Set<String>> LOADED = new ConcurrentHashMap<>();

    private final Energy energy = new Energy();
    private UUID owner;
    private String ownerName = "";
    /** L'argent produit, pas encore encaisse. */
    private long pending;
    /** Le reste de la division du revenu horaire par 72 000, reporte de tick en tick. */
    private long fraction;
    private long totalProduced;
    private int ticks;
    private boolean limited;
    private boolean dirty;

    public TradingServerBlockEntity(BlockPos pos, BlockState state) {
        super(DoHyTech.TRADING_SERVER_TYPE.get(), pos, state);
    }

    // -- Ce que l'ecran et le bloc lisent ------------------------------------------

    public EnergyHandler energy() {
        return energy;
    }

    public int energyStored() {
        return energy.amount;
    }

    public long pending() {
        return pending;
    }

    public long totalProduced() {
        return totalProduced;
    }

    public String ownerName() {
        return ownerName;
    }

    public boolean isOwner(ServerPlayer player) {
        return owner != null && owner.equals(player.getUUID());
    }

    public void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().name();
        setChanged();
    }

    /** Le nombre de serveurs de ce proprietaire en memoire. */
    public int loadedCount() {
        Set<String> mine = owner == null ? null : LOADED.get(owner);
        return mine == null ? 0 : mine.size();
    }

    public Status status() {
        TradingServerConfig.Config config = TradingServerConfig.config();
        if (owner == null) {
            return Status.NO_OWNER;
        }
        if (limited) {
            return Status.LIMIT;
        }
        if (pending >= config.bufferMax()) {
            return Status.FULL;
        }
        if (energy.amount < config.energyPerTick()) {
            return Status.NO_POWER;
        }
        return Status.RUNNING;
    }

    // -- Le tick ----------------------------------------------------------------------

    void serverTick() {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        TradingServerConfig.Config config = TradingServerConfig.config();
        if (ticks++ % 20 == 0) {
            limited = rank() >= config.maxPerPlayer();
            boolean lit = status() == Status.RUNNING;
            BlockState state = getBlockState();
            if (state.hasProperty(TradingServerBlock.LIT) && state.getValue(TradingServerBlock.LIT) != lit) {
                level.setBlock(worldPosition, state.setValue(TradingServerBlock.LIT, lit), Block.UPDATE_CLIENTS);
            }
            if (dirty) {
                dirty = false;
                setChanged();
            }
        }
        if (status() != Status.RUNNING) {
            return;
        }
        energy.amount -= config.energyPerTick();
        fraction += config.incomePerHour();
        if (fraction >= TICKS_PER_HOUR) {
            long gained = fraction / TICKS_PER_HOUR;
            fraction -= gained * TICKS_PER_HOUR;
            pending = Math.min(config.bufferMax(), pending + gained);
            totalProduced += gained;
        }
        dirty = true;
    }

    /** Le rang de ce serveur parmi ceux de son proprietaire ; il s'y inscrit au passage. */
    private int rank() {
        if (owner == null || level == null) {
            return 0;
        }
        String key = key();
        Set<String> mine = LOADED.computeIfAbsent(owner, ignored -> new LinkedHashSet<>());
        synchronized (mine) {
            mine.add(key);
            int index = 0;
            for (String other : mine) {
                if (other.equals(key)) {
                    return index;
                }
                index++;
            }
            return index;
        }
    }

    private String key() {
        return level.dimension().identifier() + "@" + worldPosition.asLong();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (owner != null && level != null && !level.isClientSide()) {
            Set<String> mine = LOADED.get(owner);
            if (mine != null) {
                synchronized (mine) {
                    mine.remove(key());
                }
            }
        }
    }

    /** A l'arret du serveur de jeu : un autre monde ne doit pas heriter de la liste. */
    static void forgetAll() {
        LOADED.clear();
    }

    // -- La caisse -----------------------------------------------------------------------

    /**
     * Verse la caisse a son proprietaire.
     *
     * @return le refus a afficher, ou {@code null} si de l'argent a ete verse
     */
    public String collect(ServerPlayer player) {
        if (!isOwner(player)) {
            return owner == null ? "Ce serveur n'a pas de proprietaire." : "Ce serveur appartient a " + ownerName + ".";
        }
        if (pending <= 0L) {
            return "La caisse est vide.";
        }
        long amount = pending;
        pending = 0L;
        setChanged();
        long balance = Economy.credit(player, amount);
        player.level().playSound(null, worldPosition, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS,
                0.6f, 1.2f);
        player.sendSystemMessage(Component.literal("Serveur de trading : +" + MoneyFormat.exact(amount) + " $")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal("  solde " + MoneyFormat.exact(balance) + " $")
                        .withStyle(ChatFormatting.DARK_GRAY)));
        return null;
    }

    /** Le bloc est casse : la caisse revient a son proprietaire s'il est la, sinon elle est perdue. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (pending > 0L && owner != null && level instanceof ServerLevel server
                && server.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer player) {
            collect(player);
        }
    }

    // -- Sauvegarde ------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("owner", owner == null ? "" : owner.toString());
        output.putString("owner_name", ownerName);
        output.putInt("energy", energy.amount);
        output.putLong("pending", pending);
        output.putLong("fraction", fraction);
        output.putLong("total", totalProduced);
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
        energy.amount = Math.max(0, input.getIntOr("energy", 0));
        pending = Math.max(0L, input.getLongOr("pending", 0L));
        fraction = Math.max(0L, input.getLongOr("fraction", 0L));
        totalProduced = Math.max(0L, input.getLongOr("total", 0L));
    }

    /**
     * La reserve d'energie : reception seule, dans une transaction de NeoForge.
     *
     * <p>Une insertion peut etre simulee puis annulee (c'est ainsi qu'un cable
     * demande « combien acceptes-tu ? ») : le journal garde la valeur d'avant
     * et la remet si la transaction est abandonnee.
     */
    private final class Energy extends SnapshotJournal<Integer> implements EnergyHandler {
        private int amount;

        @Override
        public long getAmountAsLong() {
            return amount;
        }

        @Override
        public long getCapacityAsLong() {
            return TradingServerConfig.config().energyCapacity();
        }

        @Override
        public int insert(int offered, TransactionContext transaction) {
            TradingServerConfig.Config config = TradingServerConfig.config();
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

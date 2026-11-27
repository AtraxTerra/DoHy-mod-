package fr.atraxterra.dohy.palamod.pet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

/**
 * L'etat d'une cage : son maitre, l'appat, le familier entre et ou en est son
 * apprivoisement. Tout se compte en temps de jeu, sauvegarde avec la cage.
 */
public class PetCageBlockEntity extends BlockEntity {

    /** L'appat charge la cage cinq minutes. */
    public static final int BAIT_TICKS = 5 * 60 * 20;
    /** Un tirage toutes les deux minutes. */
    public static final int ROLL_TICKS = 2 * 60 * 20;
    /** 5 % de chances par tirage. */
    public static final double CHANCE = 0.05d;
    /** Le maitre a cinq minutes pour venir chercher le familier. */
    public static final int CLAIM_TICKS = 5 * 60 * 20;
    /** Deux minutes entre deux paliers. */
    public static final int STAGE_GAP = 2 * 60 * 20;
    /** Sept minutes pour revenir jouer un palier ouvert, puis pour le finir. */
    public static final int STAGE_WINDOW = 7 * 60 * 20;

    private UUID owner;
    private String ownerName = "";
    /** Fin de l'appat (temps de jeu), 0 sans appat. */
    long baitUntil;
    long nextRoll;
    /** Le familier entre, ou "". */
    String occupant = "";
    /** Le palier a jouer (1 a 3) et ses points. */
    int stage;
    int points;
    /** Le palier s'ouvre a {@code availableAt} ; passe {@code deadline}, le familier s'enfuit. */
    long availableAt;
    long deadline;
    /** Le palier a deja ete ouvert. */
    boolean started;

    public PetCageBlockEntity(BlockPos pos, BlockState state) {
        super(PalaPets.PET_CAGE_TYPE.get(), pos, state);
    }

    // -- Lu par le rendu ------------------------------------------------------------------------

    public String occupant() {
        return occupant;
    }

    /** Cote client, pour le rendu : l'occupant vu au dernier dessin, et quand il est arrive. */
    private String seenOccupant;
    private long seenSince;

    /**
     * Quand le familier vu par le rendu est-il arrive ? La porte de la cage se ferme
     * a son arrivee (animation « closing » de PalaPet). Au premier regard, la porte
     * est deja fermee.
     */
    public long occupantSince(long now) {
        if (seenOccupant == null) {
            seenOccupant = occupant;
            seenSince = now - 20L * 60L;
        } else if (!seenOccupant.equals(occupant)) {
            seenOccupant = occupant;
            seenSince = now;
        }
        return seenSince;
    }

    public boolean baited(long now) {
        return occupant.isEmpty() && baitUntil > now;
    }

    public long baitUntil() {
        return baitUntil;
    }

    public int stage() {
        return stage;
    }

    public long availableAt() {
        return availableAt;
    }

    public long deadline() {
        return deadline;
    }

    public String ownerName() {
        return ownerName;
    }

    // -- Le maitre -------------------------------------------------------------------------------

    void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().name();
        sync();
    }

    boolean isOwner(ServerPlayer player) {
        return owner == null || owner.equals(player.getUUID());
    }

    private long now() {
        return level == null ? 0L : level.getGameTime();
    }

    /** Refuse un autre joueur que le maitre ; rend vrai si c'est le maitre. */
    private boolean checkOwner(ServerPlayer player) {
        if (isOwner(player)) {
            if (owner == null) {
                setOwner(player);
            }
            return true;
        }
        player.sendSystemMessage(Component.literal("[Familier] Cette cage appartient a " + ownerName
                + " : tu ne peux que la casser.").withStyle(ChatFormatting.RED));
        return false;
    }

    // -- Appat et tirages ------------------------------------------------------------------------

    void bait(ServerPlayer player, ItemStack stack) {
        if (!checkOwner(player)) {
            return;
        }
        if (PetApi.has(player)) {
            player.sendSystemMessage(Component.literal("[Familier] Tu as deja un familier : on n'en a qu'un.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!occupant.isEmpty()) {
            use(player);
            return;
        }
        long now = now();
        if (baitUntil > now) {
            player.sendSystemMessage(Component.literal("[Familier] La cage est deja chargee (encore "
                    + clock(baitUntil - now) + ").").withStyle(ChatFormatting.GRAY));
            return;
        }
        stack.consume(1, player);
        baitUntil = now + BAIT_TICKS;
        nextRoll = now + ROLL_TICKS;
        player.sendSystemMessage(Component.literal("[Familier] La cage est chargee pour 5 minutes : un familier a "
                + "5 % de chances d'y entrer toutes les 2 minutes.").withStyle(ChatFormatting.GREEN));
        level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
        sync();
    }

    void use(ServerPlayer player) {
        if (!checkOwner(player)) {
            return;
        }
        long now = now();
        if (occupant.isEmpty()) {
            String text = baitUntil > now
                    ? "La cage est chargee encore " + clock(baitUntil - now) + " ; prochain tirage dans "
                    + clock(Math.max(0L, nextRoll - now)) + "."
                    : "Pose un appat dans la cage (clic droit avec l'appat en main). On en trouve sur les "
                    + "serpents et les escargots.";
            player.sendSystemMessage(Component.literal("[Familier] " + text).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (PetApi.has(player)) {
            flee(false);
            return;
        }
        if (now < availableAt) {
            player.sendSystemMessage(Component.literal("[Familier] Le palier " + stage + " s'ouvre dans "
                    + clock(availableAt - now) + ".").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!started) {
            started = true;
            deadline = now + STAGE_WINDOW;
            sync();
        }
        PetTaming.open(player, this);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server) || server.getGameTime() % 20L != 0L) {
            return;
        }
        long now = server.getGameTime();
        if (occupant.isEmpty()) {
            if (baitUntil <= 0L) {
                return;
            }
            if (now >= nextRoll && nextRoll <= baitUntil) {
                nextRoll += ROLL_TICKS;
                if (server.getRandom().nextDouble() < CHANCE) {
                    capture(server, now);
                    return;
                }
            }
            if (now >= baitUntil) {
                baitUntil = 0L;
                ServerPlayer player = ownerOnline(server);
                if (player != null) {
                    player.sendSystemMessage(Component.literal("[Familier] L'appat de ta cage est epuise, aucun "
                            + "familier n'est venu.").withStyle(ChatFormatting.GRAY));
                }
                sync();
            }
            return;
        }
        if (now > deadline) {
            flee(started || stage > 1);
        }
    }

    private void capture(ServerLevel server, long now) {
        occupant = Familiar.pick(server.getRandom()).id();
        baitUntil = 0L;
        stage = 1;
        points = PetTaming.START_POINTS;
        availableAt = now;
        deadline = now + CLAIM_TICKS;
        started = false;
        server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.4f);
        ServerPlayer player = ownerOnline(server);
        if (player != null) {
            player.sendSystemMessage(Component.literal("[Familier] Ton piege a ete active ! Un "
                    + Familiar.byId(occupant).label() + " t'attend dans la cage (" + worldPosition.getX() + " "
                    + worldPosition.getY() + " " + worldPosition.getZ() + ") : tu as 5 minutes pour venir.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(),
                    SoundSource.PLAYERS, 1.0f, 1.5f);
        }
        sync();
    }

    /** Le familier s'enfuit ; la cage casse si {@code breakCage}. */
    void flee(boolean breakCage) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        PetTaming.cancel(server, worldPosition);
        ServerPlayer player = ownerOnline(server);
        if (player != null) {
            player.sendSystemMessage(Component.literal("[Familier] Le familier s'est enfui !" + (breakCage
                    ? " La cage n'a pas tenu." : "")).withStyle(ChatFormatting.RED));
        }
        occupant = "";
        stage = 0;
        points = 0;
        started = false;
        server.playSound(null, worldPosition, SoundEvents.FOX_SNIFF, SoundSource.BLOCKS, 1.0f, 0.8f);
        if (breakCage) {
            server.destroyBlock(worldPosition, false);
            return;
        }
        sync();
    }

    /** Apres un palier reussi, ou un retour au palier d'avant. */
    void nextStage(int next) {
        stage = next;
        points = PetTaming.START_POINTS;
        availableAt = now() + STAGE_GAP;
        deadline = availableAt + STAGE_WINDOW;
        started = false;
        sync();
    }

    /** Le familier part avec son maitre : la cage redevient vide. */
    void tamed() {
        occupant = "";
        stage = 0;
        points = 0;
        started = false;
        sync();
    }

    private ServerPlayer ownerOnline(ServerLevel server) {
        return owner == null ? null : server.getServer().getPlayerList().getPlayer(owner);
    }

    /** « 4:05 » a partir de ticks. */
    public static String clock(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        return seconds / 60L + ":" + String.format("%02d", seconds % 60L);
    }

    // -- Sauvegarde et envoi ---------------------------------------------------------------------

    void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String raw = input.getStringOr("owner", "");
        try {
            owner = raw.isEmpty() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            owner = null;
        }
        ownerName = input.getStringOr("owner_name", "");
        baitUntil = input.getLongOr("bait_until", 0L);
        nextRoll = input.getLongOr("next_roll", 0L);
        occupant = input.getStringOr("occupant", "");
        stage = input.getIntOr("stage", 0);
        points = input.getIntOr("points", 0);
        availableAt = input.getLongOr("available_at", 0L);
        deadline = input.getLongOr("deadline", 0L);
        started = input.getBooleanOr("started", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("owner", owner == null ? "" : owner.toString());
        output.putString("owner_name", ownerName);
        output.putLong("bait_until", baitUntil);
        output.putLong("next_roll", nextRoll);
        output.putString("occupant", occupant);
        output.putInt("stage", stage);
        output.putInt("points", points);
        output.putLong("available_at", availableAt);
        output.putLong("deadline", deadline);
        output.putBoolean("started", started);
    }
}

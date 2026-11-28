package fr.atraxterra.dohy.palamod.botanist;

import fr.atraxterra.dohy.palamod.machine.ServerTicking;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

/**
 * Le centre de controle pose ({@code TileEntityBotanistControl} de JobsReborn) : son maitre (celui
 * qui l'a pose, dont il gere le laboratoire) et l'etat de son modele. Toutes les secondes : l'alerte
 * si l'un des pots du maitre reclame quelque chose, la construction si une amelioration est en cours,
 * sinon l'etat normal. L'alerte et la construction sonnent en boucle ({@code BotanistMachineSound},
 * volume 0,5), comme la cloche et les aiguilles du modele.
 */
public class LabControlBlockEntity extends BlockEntity implements ServerTicking {

    /** Les sons de l'alerte et de la construction font 1,2 seconde, comme leurs animations. */
    private static final int LOOP_TICKS = 24;

    public enum Anim {
        NORMAL("normal"),
        ALERT("alert"),
        CONSTRUCTION("construction");

        private final String clip;

        Anim(String clip) {
            this.clip = clip;
        }

        public String clip() {
            return clip;
        }

        static Anim byOrdinal(int ordinal) {
            Anim[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NORMAL;
        }
    }

    private UUID owner;
    private String ownerName = "";
    private Anim anim = Anim.NORMAL;
    private long soundAt;

    public LabControlBlockEntity(BlockPos pos, BlockState state) {
        super(PalaBotanist.LAB_CONTROL_TYPE.get(), pos, state);
    }

    public Anim anim() {
        return anim;
    }

    void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().name();
        setChanged();
    }

    /** Le maitre seulement (le laboratoire est le sien) ; un centre sans maitre devient celui du joueur. */
    boolean canOpen(ServerPlayer player) {
        if (owner == null) {
            setOwner(player);
        }
        if (owner.equals(player.getUUID())) {
            return true;
        }
        player.sendSystemMessage(Component.literal("Ce centre de controle appartient a " + ownerName
                + " : il gere son laboratoire.").withStyle(ChatFormatting.RED), true);
        return false;
    }

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        long now = server.getGameTime();
        if (now % 20 == 0) {
            Anim next = resolve(server);
            if (next != anim) {
                anim = next;
                soundAt = now;
                setChanged();
                BlockState state = getBlockState();
                server.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
            }
        }
        if (anim != Anim.NORMAL && now >= soundAt) {
            SoundEvent sound = anim == Anim.ALERT ? PalaBotanist.CONTROL_ALERT.get()
                    : PalaBotanist.CONTROL_CONSTRUCTION.get();
            server.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 0.5f, 1.0f);
            soundAt = now + LOOP_TICKS;
        }
    }

    private Anim resolve(ServerLevel server) {
        ServerPlayer player = owner == null ? null : server.getServer().getPlayerList().getPlayer(owner);
        if (player == null || !BotanistLab.unlocked(player)) {
            return Anim.NORMAL;
        }
        for (BotanistLab.Module module : BotanistLab.Module.values()) {
            if (BotanistLab.needs(BotanistLab.pots(player, module)) > 0) {
                return Anim.ALERT;
            }
        }
        for (BotanistLab.Module module : BotanistLab.Module.values()) {
            if (BotanistLab.building(player, module)) {
                return Anim.CONSTRUCTION;
            }
        }
        return Anim.NORMAL;
    }

    // -- Sauvegarde et envoi -------------------------------------------------------------------

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
        anim = Anim.byOrdinal(input.getIntOr("anim", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("owner", owner == null ? "" : owner.toString());
        output.putString("owner_name", ownerName);
        output.putInt("anim", anim.ordinal());
    }
}

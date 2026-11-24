package fr.atraxterra.dohy.faction.garrison;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

/** La caserne garde son proprietaire ; cassee, elle previent sa garnison. */
public class BarracksBlockEntity extends BlockEntity {

    private UUID owner;
    private String ownerName = "";

    public BarracksBlockEntity(BlockPos pos, BlockState state) {
        super(DoHyGarrison.BARRACKS_TYPE.get(), pos, state);
    }

    void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getName().getString();
        setChanged();
    }

    boolean isOwner(ServerPlayer player) {
        return owner != null && owner.equals(player.getUUID());
    }

    String ownerName() {
        return ownerName;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (owner != null && level instanceof ServerLevel server
                && server.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer player) {
            Garrisons.lostBarracks(player, GlobalPos.of(server.dimension(), pos.immutable()));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("owner", owner == null ? "" : owner.toString());
        output.putString("owner_name", ownerName);
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
    }
}

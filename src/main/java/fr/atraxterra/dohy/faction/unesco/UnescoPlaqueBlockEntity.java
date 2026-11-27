package fr.atraxterra.dohy.faction.unesco;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

/** La plaque UNESCO : a qui elle est, et le nom du monument (celui donne a la plaque sur l'enclume). */
public class UnescoPlaqueBlockEntity extends BlockEntity {

    private UUID owner;
    private String ownerName = "";
    private String name = "";

    public UnescoPlaqueBlockEntity(BlockPos pos, BlockState state) {
        super(DoHyUnesco.PLAQUE_TYPE.get(), pos, state);
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public String name() {
        return name;
    }

    public boolean ownedBy(ServerPlayer player) {
        return owner != null && owner.equals(player.getUUID());
    }

    void setOwner(ServerPlayer player, String monumentName) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().name();
        name = monumentName == null ? "" : monumentName;
        setChanged();
    }

    /** La plaque part : son monument quitte le programme. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server) {
            Unesco.plaqueRemoved(server, pos);
        }
        super.preRemoveSideEffects(pos, state);
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
        name = input.getStringOr("name", "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("owner", owner == null ? "" : owner.toString());
        output.putString("owner_name", ownerName);
        output.putString("name", name);
    }
}

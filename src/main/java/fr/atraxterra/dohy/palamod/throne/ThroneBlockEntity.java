package fr.atraxterra.dohy.palamod.throne;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Le trone pose ({@code TileEntityMinerThrone} et ses freres) : il ne garde que
 * le nom grave, que le client recoit pour l'afficher au-dessus du trone.
 */
public class ThroneBlockEntity extends BlockEntity {

    private String owner = "";

    public ThroneBlockEntity(BlockPos pos, BlockState state) {
        super(PalaThrones.THRONE_ENTITY.get(), pos, state);
    }

    /** Le nom grave, ou une chaine vide. */
    public String owner() {
        return owner;
    }

    void setOwner(String name) {
        owner = name == null ? "" : name;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.getStringOr("Owner", "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!owner.isEmpty()) {
            output.putString("Owner", owner);
        }
    }

    /** Le trone casse garde son nom ({@code onBlockHarvested}). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!owner.isEmpty()) {
            components.set(PalaThrones.OWNER.get(), owner);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        String stored = components.get(PalaThrones.OWNER.get());
        owner = stored == null ? "" : stored;
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("Owner");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

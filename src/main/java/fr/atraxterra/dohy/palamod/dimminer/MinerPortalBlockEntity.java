package fr.atraxterra.dohy.palamod.dimminer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

/** {@code TileEntityMinerPortal} : le proprietaire du portail, le premier a y entrer. */
public class MinerPortalBlockEntity extends BlockEntity {

    private UUID owner;

    public MinerPortalBlockEntity(BlockPos pos, BlockState state) {
        super(PalaDimMiner.MINER_PORTAL_ENTITY.get(), pos, state);
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID value) {
        owner = value;
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (owner != null) {
            output.store("Owner", UUIDUtil.CODEC, owner);
        }
    }
}

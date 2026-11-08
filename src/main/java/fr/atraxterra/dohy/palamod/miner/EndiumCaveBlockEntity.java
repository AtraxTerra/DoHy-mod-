package fr.atraxterra.dohy.palamod.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Marqueur du cave block en endium, sans donnees. */
public class EndiumCaveBlockEntity extends BlockEntity {

    public EndiumCaveBlockEntity(BlockPos pos, BlockState state) {
        super(PalaMiner.ENDIUM_CAVE.get(), pos, state);
    }
}

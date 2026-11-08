package fr.atraxterra.dohy.palamod.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Palaforge ({@code BlockForge}) : voir {@link PalaforgeBlockEntity}. */
public class PalaforgeBlock extends OrientedMachineBlock {

    public static final MapCodec<PalaforgeBlock> CODEC = simpleCodec(PalaforgeBlock::new);

    public PalaforgeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PalaforgeBlockEntity(pos, state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}

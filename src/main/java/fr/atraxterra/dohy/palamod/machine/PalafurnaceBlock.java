package fr.atraxterra.dohy.palamod.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Palafurnace ({@code BlockPaladiumFurnace}) : voir {@link PalafurnaceBlockEntity}. */
public class PalafurnaceBlock extends OrientedMachineBlock {

    public static final MapCodec<PalafurnaceBlock> CODEC = simpleCodec(PalafurnaceBlock::new);

    public PalafurnaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PalafurnaceBlockEntity(pos, state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}

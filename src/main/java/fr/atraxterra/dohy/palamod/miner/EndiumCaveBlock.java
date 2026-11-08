package fr.atraxterra.dohy.palamod.miner;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cave block en endium : un cave block qui montre les conteneurs vus a travers
 * lui ({@link EndiumCaveVision}). Sa block entity ne fait rien ; elle sert a le
 * trouver, comme {@code TileEntityEndiumCaveBlock} dans PalaMod.
 */
public class EndiumCaveBlock extends BaseEntityBlock {

    public static final MapCodec<EndiumCaveBlock> CODEC = simpleCodec(EndiumCaveBlock::new);

    public EndiumCaveBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EndiumCaveBlockEntity(pos, state);
    }
}

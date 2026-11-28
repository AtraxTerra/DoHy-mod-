package fr.atraxterra.dohy.palamod.botanist;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * La centrifugeuse ({@code BlockBotanistCentrifuge}) : une machine du laboratoire de JobsReborn,
 * posee dans le monde sur deux blocs de haut ({@link TallMachineBlock}) ; son modele (83 pixels de
 * haut dans le laboratoire) est reduit par {@code CentrifugeRenderer}.
 */
public class CentrifugeBlock extends TallMachineBlock {

    public static final MapCodec<CentrifugeBlock> CODEC = simpleCodec(CentrifugeBlock::new);

    public CentrifugeBlock(Properties properties) {
        super(properties, Block.box(1.0d, 0.0d, 1.0d, 15.0d, 16.0d, 15.0d), Block.box(1.0d, 0.0d, 1.0d, 15.0d, 15.0d,
                15.0d));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntity machine(BlockPos pos, BlockState state) {
        return new CentrifugeBlockEntity(pos, state);
    }
}

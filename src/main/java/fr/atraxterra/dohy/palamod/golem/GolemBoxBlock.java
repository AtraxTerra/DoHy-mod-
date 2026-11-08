package fr.atraxterra.dohy.palamod.golem;

import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.palamod.machine.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Le bloc de boite a golem, au centre d'une face du cube. */
public class GolemBoxBlock extends MachineBlock {

    public static final MapCodec<GolemBoxBlock> CODEC = simpleCodec(GolemBoxBlock::new);

    public GolemBoxBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GolemBoxBlockEntity(pos, state);
    }
}

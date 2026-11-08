package fr.atraxterra.dohy.palamod.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le grinder ({@code BlockGrinder}) : la face avant regarde le joueur qui le
 * pose ; allume ({@code lit}) quand sa structure est complete, il fume et
 * crache des flammes par l'avant, comme l'original.
 */
public class GrinderBlock extends OrientedMachineBlock {

    public static final MapCodec<GrinderBlock> CODEC = simpleCodec(GrinderBlock::new);

    public GrinderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GrinderBlockEntity(pos, state);
    }

    /** {@code randomDisplayTick} : fumee et flamme sur la face avant, si forme. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double side = random.nextDouble() * 0.6d - 0.3d;
        double x = pos.getX() + 0.5d + facing.getStepX() * 0.52d + (facing.getStepZ() != 0 ? side : 0.0d);
        double y = pos.getY() + random.nextDouble() * 6.0d / 16.0d;
        double z = pos.getZ() + 0.5d + facing.getStepZ() * 0.52d + (facing.getStepX() != 0 ? side : 0.0d);
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0d, 0.0d, 0.0d);
        level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0d, 0.0d, 0.0d);
    }
}

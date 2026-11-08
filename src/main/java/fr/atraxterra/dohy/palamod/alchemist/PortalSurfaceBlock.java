package fr.atraxterra.dohy.palamod.alchemist;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La surface d'un portail ouvert ({@code BlockPortalAlchemist}) : une nappe de
 * deux pixels, incassable, sans objet ni butin. Les objets la traversent et
 * reposent dessus, c'est le controleur qui les absorbe. Une nappe que plus
 * aucun portail ouvert ne tient disparait d'elle-meme.
 */
public class PortalSurfaceBlock extends Block {

    public static final MapCodec<PortalSurfaceBlock> CODEC = simpleCodec(PortalSurfaceBlock::new);

    /** {@code field_149756_F = 0.125} : un huitieme de bloc. */
    private static final VoxelShape SHAPE = box(0.0d, 0.0d, 0.0d, 16.0d, 2.0d, 16.0d);

    public PortalSurfaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!PortalControllerBlockEntity.holds(level, pos)) {
            level.removeBlock(pos, false);
        }
    }
}

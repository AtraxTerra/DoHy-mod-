package fr.atraxterra.dohy.palamod.unlock;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La racine du racinier ({@code BlockInferiorRoot}, et son bout {@code ROOT_LAST}) :
 * une colonne d'un bloc, qui remplace ce qu'elle traverse, sans rien rendre.
 * Cassee, elle s'en va jusqu'a son bout.
 *
 * <p><b>Ecart</b> : on la traverse et on y grimpe comme a une liane, pour que
 * le puits qu'elle creuse serve dans les deux sens.
 */
public class RootColumnBlock extends Block {

    public static final MapCodec<RootColumnBlock> CODEC = simpleCodec(RootColumnBlock::new);

    private static final VoxelShape SHAPE = Block.box(5.0d, 0.0d, 5.0d, 11.0d, 16.0d, 11.0d);

    public RootColumnBlock(Properties properties) {
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
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            RootLeavesBlockEntity.clearColumn(level, pos.below());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}

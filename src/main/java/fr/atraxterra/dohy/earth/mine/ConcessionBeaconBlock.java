package fr.atraxterra.dohy.earth.mine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La balise de concession posee dans une mine de la Terre : tant qu'elle tient
 * et que son poseur reste a cote, la capture avance ({@link EarthMines}). La
 * capture finie, elle s'eteint et disparait ; cassee avant, elle se ramasse et
 * la capture est abandonnee. Un clic droit dit ou en est la capture.
 */
public class ConcessionBeaconBlock extends Block {

    private static final VoxelShape SHAPE = Shapes.or(Block.box(4, 0, 4, 12, 2, 12), Block.box(7, 2, 7, 9, 16, 9));

    public ConcessionBeaconBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (player instanceof ServerPlayer server) {
            EarthMines.status(server, pos);
        }
        return InteractionResult.SUCCESS;
    }
}

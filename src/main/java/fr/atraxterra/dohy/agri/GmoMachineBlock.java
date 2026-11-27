package fr.atraxterra.dohy.agri;

import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.agri.machine.BigMachine;
import fr.atraxterra.dohy.agri.machine.BigMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La machine a OGM : un clic ouvre l'ecran des croisements ({@link GmoMachineMenu}).
 *
 * <p>C'est une grande machine ({@link BigMachine#GMO_MACHINE}) : 3 x 3 x 3, une
 * cuve vitree sous un portique, dont le fouet tourne sans cesse. Son entite de bloc
 * ({@link GmoMachineBlockEntity}) ne garde rien : elle porte le rendu du fouet.
 */
public class GmoMachineBlock extends BaseEntityBlock {

    public static final MapCodec<GmoMachineBlock> CODEC = simpleCodec(GmoMachineBlock::new);

    public GmoMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BigMachines.FACING, Direction.NORTH)
                .setValue(BigMachines.FORMED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BigMachines.FACING, BigMachines.FORMED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GmoMachineBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return BigMachines.placement(BigMachine.GMO_MACHINE, defaultBlockState(), context);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        BigMachines.onPlace(BigMachine.GMO_MACHINE, state, level, pos, oldState);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        BigMachines.onRemove(BigMachine.GMO_MACHINE, state, level, pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BigMachines.shape(BigMachine.GMO_MACHINE, state, Shapes.block());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BigMachines.FACING, rotation.rotate(state.getValue(BigMachines.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(BigMachines.FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        BigMachines.unfold(BigMachine.GMO_MACHINE, state, level, pos, player);
        if (player instanceof ServerPlayer server) {
            GmoMachineMenu.open(server, pos);
        }
        return InteractionResult.SUCCESS;
    }
}

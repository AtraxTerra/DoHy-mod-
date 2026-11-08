package fr.atraxterra.dohy.palamod.storage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Entonnoir en paladium ({@code BlockPaladiumHopper}).
 *
 * <p>Fer, durete 5, n'importe quelle pioche. Il se pose comme l'entonnoir
 * vanilla — pointe a l'oppose de la face cliquee, jamais vers le haut —, un
 * signal de redstone le coupe, il s'ouvre sur l'interface de l'entonnoir et un
 * comparateur lit son contenu. Ce qui change est dans
 * {@link PaladiumHopperBlockEntity} : des piles entieres au lieu d'un objet.
 */
public class PaladiumHopperBlock extends BaseEntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING_HOPPER;
    public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;

    public static final MapCodec<PaladiumHopperBlock> CODEC = simpleCodec(PaladiumHopperBlock::new);

    /**
     * Le bol et l'entonnoir, creux comme l'entonnoir vanilla : un objet jete
     * dessus tombe dans le bol, dans la zone d'aspiration.
     */
    private static final VoxelShape BOWL = Shapes.join(
            Shapes.or(Block.box(0, 10, 0, 16, 16, 16), Block.box(4, 4, 4, 12, 10, 12)),
            Block.box(2, 11, 2, 14, 16, 14), BooleanOp.ONLY_FIRST);
    private static final VoxelShape DOWN = Shapes.or(BOWL, Block.box(6, 0, 6, 10, 4, 10));
    private static final VoxelShape NORTH = Shapes.or(BOWL, Block.box(6, 4, 0, 10, 8, 4));
    private static final VoxelShape SOUTH = Shapes.or(BOWL, Block.box(6, 4, 12, 10, 8, 16));
    private static final VoxelShape WEST = Shapes.or(BOWL, Block.box(0, 4, 6, 4, 8, 10));
    private static final VoxelShape EAST = Shapes.or(BOWL, Block.box(12, 4, 6, 16, 8, 10));

    public PaladiumHopperBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.DOWN)
                .setValue(ENABLED, true));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ENABLED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getClickedFace().getOpposite();
        return defaultBlockState()
                .setValue(FACING, direction.getAxis() == Direction.Axis.Y ? Direction.DOWN : direction)
                .setValue(ENABLED, !context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   Orientation orientation, boolean moving) {
        boolean enabled = !level.hasNeighborSignal(pos);
        if (enabled != state.getValue(ENABLED)) {
            level.setBlock(pos, state.setValue(ENABLED, enabled), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> DOWN;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PaladiumHopperBlockEntity hopper) {
            player.openMenu(hopper);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PaladiumHopperBlockEntity hopper) {
            Containers.dropContents(level, pos, hopper);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PaladiumHopperBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof PaladiumHopperBlockEntity hopper) {
                hopper.serverTick(tickState);
            }
        };
    }
}

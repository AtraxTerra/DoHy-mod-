package fr.atraxterra.dohy.palamod.botanist;

import fr.atraxterra.dohy.palamod.machine.ServerTicking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Une machine du laboratoire de JobsReborn posee dans le monde sur deux blocs de large et deux de
 * haut : la reserve de graines et le centre de controle, dont les modeles sont deux fois plus larges
 * que profonds. Le bloc pose est la partie basse de gauche (vue de face) ; elle porte la machine.
 * Les trois autres parties ne font que renvoyer a elle : un clic sur n'importe laquelle ouvre la
 * meme interface, et casser l'une casse les quatre (la partie principale rend l'objet).
 */
public abstract class WideMachineBlock extends BaseEntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<Side> SIDE = EnumProperty.create("side", Side.class);

    /** La colonne de la partie : celle posee (gauche, vue de face) ou sa voisine (droite). */
    public enum Side implements StringRepresentable {
        LEFT("left"),
        RIGHT("right");

        private final String name;

        Side(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    private final VoxelShape lower;
    private final VoxelShape upper;

    protected WideMachineBlock(Properties properties, VoxelShape lower, VoxelShape upper) {
        super(properties);
        this.lower = lower;
        this.upper = upper;
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER).setValue(SIDE, Side.LEFT));
    }

    /** La machine de la partie principale. */
    protected abstract BlockEntity machine(BlockPos pos, BlockState state);

    /** A la pose, par un joueur. */
    protected void placed(ServerPlayer player, BlockEntity machine) {
    }

    /** Un clic sur l'une des quatre parties : l'interface de la machine. */
    protected abstract void open(ServerPlayer player, BlockEntity machine);

    /** Vers la colonne de droite, vue de face : de la machine tournee vers {@code facing}. */
    public static Direction toRight(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** La partie principale (basse, a gauche) d'une partie. */
    public static BlockPos main(BlockPos pos, BlockState state) {
        BlockPos column = state.getValue(SIDE) == Side.RIGHT ? pos.relative(toRight(state.getValue(FACING)).getOpposite())
                : pos;
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? column.below() : column;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, SIDE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? lower : upper;
    }

    // -- Quatre parties -----------------------------------------------------------------------

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos right = pos.relative(toRight(facing));
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)
                || !level.getBlockState(right).canBeReplaced(context)
                || !level.getBlockState(right.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(SIDE, Side.LEFT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        BlockPos right = pos.relative(toRight(state.getValue(FACING)));
        level.setBlock(right, state.setValue(SIDE, Side.RIGHT), Block.UPDATE_ALL);
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        level.setBlock(right.above(), state.setValue(HALF, DoubleBlockHalf.UPPER).setValue(SIDE, Side.RIGHT),
                Block.UPDATE_ALL);
        BlockEntity machine = level.getBlockEntity(pos);
        if (placer instanceof ServerPlayer player && machine != null) {
            placed(player, machine);
        }
    }

    /** Chaque partie tient a sa voisine du dessus (ou du dessous) et a celle d'a cote. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighbor,
                                     RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        Side side = state.getValue(SIDE);
        Direction facing = state.getValue(FACING);
        Direction vertical = half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
        Direction across = side == Side.LEFT ? toRight(facing) : toRight(facing).getOpposite();
        if (direction == vertical && !(neighbor.is(this) && neighbor.getValue(HALF) != half
                && neighbor.getValue(SIDE) == side && neighbor.getValue(FACING) == facing)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (direction == across && !(neighbor.is(this) && neighbor.getValue(SIDE) != side
                && neighbor.getValue(HALF) == half && neighbor.getValue(FACING) == facing)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
    }

    /**
     * Cassee en creatif, la partie principale part sans rien rendre ; sinon elle rend l'objet (sa
     * table de butin, partie principale seulement) quand ses voisines la font tomber.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()) {
            BlockPos main = main(pos, state);
            BlockState mainState = level.getBlockState(main);
            if (!main.equals(pos) && mainState.is(this)) {
                level.setBlock(main, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(2001, main, Block.getId(mainState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // -- La machine ---------------------------------------------------------------------------

    private static boolean isMain(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER && state.getValue(SIDE) == Side.LEFT;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMain(state) ? machine(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || !isMain(state)) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof ServerTicking machine) {
                machine.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        BlockEntity machine = level.getBlockEntity(main(pos, state));
        if (player instanceof ServerPlayer server && machine != null) {
            open(server, machine);
        }
        return InteractionResult.SUCCESS;
    }
}

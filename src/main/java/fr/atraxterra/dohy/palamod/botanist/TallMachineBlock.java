package fr.atraxterra.dohy.palamod.botanist;

import fr.atraxterra.dohy.palamod.machine.MachineBlockEntity;
import fr.atraxterra.dohy.palamod.machine.ServerTicking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
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
 * Une machine du laboratoire de JobsReborn posee dans le monde sur deux blocs de haut (comme une
 * porte) : le scanner de Genis et la centrifugeuse. Leurs modeles font plus de quatre blocs dans le
 * laboratoire ; leurs rendus les reduisent a ces deux blocs, tournes vers le joueur.
 *
 * <p>Toute la machine (cases, travail, maitre) est dans la moitie basse ; un clic sur la moitie
 * haute ouvre la meme interface ({@link #open}). La moitie basse s'ouvre par
 * {@code PalaMachineEvents}, comme les autres machines.
 */
public abstract class TallMachineBlock extends BaseEntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private final VoxelShape lower;
    private final VoxelShape upper;

    protected TallMachineBlock(Properties properties, VoxelShape lower, VoxelShape upper) {
        super(properties);
        this.lower = lower;
        this.upper = upper;
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    /** La machine de la moitie basse. */
    protected abstract BlockEntity machine(BlockPos pos, BlockState state);

    /** A la pose, par un joueur : le maitre, pour les machines qui en ont un. */
    protected void placed(ServerPlayer player, BlockEntity machine) {
    }

    /** Un clic sur la moitie haute : l'interface de la machine du dessous. */
    protected void open(ServerPlayer player, BlockEntity machine) {
        if (machine instanceof MachineBlockEntity menu && menu.canOpen(player)) {
            player.openMenu(menu);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? lower : upper;
    }

    // -- Deux blocs de haut -------------------------------------------------------------------

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        BlockEntity machine = level.getBlockEntity(pos);
        if (placer instanceof ServerPlayer player && machine != null) {
            placed(player, machine);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighbor,
                                     RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean partner = direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        if (partner && !(neighbor.is(this) && neighbor.getValue(HALF) != half)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
    }

    /**
     * La machine cassee rend son contenu ; cassee par la tete en creatif, sa moitie basse part sans
     * rien rendre, et sinon elle rend l'objet (sa table de butin, moitie basse seulement).
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos base = base(pos, state);
            if (level.getBlockEntity(base) instanceof Container container) {
                Containers.dropContents(level, base, container);
            }
            if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
                BlockState below = level.getBlockState(base);
                if (below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER) {
                    if (player.isCreative()) {
                        level.setBlock(base, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                        level.levelEvent(2001, base, Block.getId(below));
                    } else {
                        level.destroyBlock(base, true, player);
                    }
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    public static BlockPos base(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
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

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? machine(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || state.getValue(HALF) != DoubleBlockHalf.LOWER) {
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
        if (state.getValue(HALF) != DoubleBlockHalf.UPPER) {
            return InteractionResult.PASS;
        }
        BlockEntity machine = level.getBlockEntity(pos.below());
        if (player instanceof ServerPlayer server && machine != null) {
            open(server, machine);
        }
        return InteractionResult.SUCCESS;
    }
}

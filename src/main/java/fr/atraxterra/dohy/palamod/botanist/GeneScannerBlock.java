package fr.atraxterra.dohy.palamod.botanist;

import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.palamod.machine.ServerTicking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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
 * Le scanner de Genis ({@code BlockBotanistScanner}) : une machine du laboratoire de JobsReborn.
 *
 * <p>Son modele (celui du jar, dessine par {@code GeneScannerRenderer}) fait plus de quatre blocs de
 * haut dans le laboratoire ; pose dans le monde, il est reduit a deux blocs : le scanner occupe le
 * bloc pose et celui du dessus (comme une porte), tourne vers le joueur. Toute la machine (cases,
 * scan, maitre) est dans la moitie basse ({@link GeneScannerBlockEntity}) ; un clic sur la moitie
 * haute ouvre la meme interface.
 */
public class GeneScannerBlock extends BaseEntityBlock {

    public static final MapCodec<GeneScannerBlock> CODEC = simpleCodec(GeneScannerBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** Le corps du scanner, puis sa tete jusqu'a 14 pixels dans le bloc du dessus. */
    private static final VoxelShape LOWER = Block.box(1.0d, 0.0d, 1.0d, 15.0d, 16.0d, 15.0d);
    private static final VoxelShape UPPER = Block.box(2.0d, 0.0d, 2.0d, 14.0d, 14.0d, 14.0d);

    public GeneScannerBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
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
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof GeneScannerBlockEntity scanner) {
            scanner.setOwner(player);
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
     * Le scanner casse rend son contenu (comme les machines) ; casse par la tete en creatif, sa
     * moitie basse part sans rien rendre, et sinon elle rend l'objet (sa table de butin).
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos base = base(pos, state);
            if (level.getBlockEntity(base) instanceof GeneScannerBlockEntity scanner) {
                Containers.dropContents(level, base, scanner);
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

    private static BlockPos base(BlockPos pos, BlockState state) {
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
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new GeneScannerBlockEntity(pos, state) : null;
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

    /**
     * La moitie basse s'ouvre par {@code PalaMachineEvents} (comme les autres machines) ; un clic
     * sur la moitie haute, sans machine, ouvre celle du dessous.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (state.getValue(HALF) != DoubleBlockHalf.UPPER) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server
                && level.getBlockEntity(pos.below()) instanceof GeneScannerBlockEntity scanner
                && scanner.canOpen(server)) {
            server.openMenu(scanner);
        }
        return InteractionResult.SUCCESS;
    }
}

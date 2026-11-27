package fr.atraxterra.dohy.palamod.pet;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La cage a familier (wiki « Java | Familiers ») : seul celui qui l'a posee s'en
 * sert, les autres ne peuvent que la casser. Un appat la charge cinq minutes ;
 * toutes les deux minutes, un familier a 5 % de chances d'y entrer. Il attend
 * alors son maitre cinq minutes, puis l'apprivoisement se joue en trois paliers
 * ({@link PetTaming}).
 */
public class PetCageBlock extends BaseEntityBlock {

    public static final MapCodec<PetCageBlock> CODEC = simpleCodec(PetCageBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** La caisse de PalaPet : un bloc plein ; le baton qui tient la porte depasse devant. */
    private static final VoxelShape SHAPE = Block.box(0.0d, 0.0d, 0.0d, 16.0d, 16.0d, 16.0d);

    public PetCageBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PetCageBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof PetCageBlockEntity cage) {
                cage.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof PetCageBlockEntity cage) {
            cage.setOwner(player);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(PalaPets.PET_BAIT.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof PetCageBlockEntity cage) {
            cage.bait(server, stack);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof PetCageBlockEntity cage) {
            cage.use(server);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
                                               BlockPos pos, boolean movedByPiston) {
        PetTaming.cancel(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}

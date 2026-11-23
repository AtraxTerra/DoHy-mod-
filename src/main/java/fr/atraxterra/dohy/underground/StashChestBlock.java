package fr.atraxterra.dohy.underground;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
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

import java.util.Map;

/**
 * La planque securisee (lot 24c) : un coffre de 27 cases qui ne s'ouvre et ne se casse que
 * pour celui qui l'a pose, et qui resiste aux explosions. A la fermeture, il verse aux stocks
 * des entreprises ce qu'il contient d'utile (produits, sachets, colis, matieres, caisses
 * scellees), autant qu'il y a de place ; le reste y demeure.
 *
 * <p>Un tapis de Create ou un convoyeur de NTM NEXT peut la remplir (jamais la vider) : elle
 * verse alors d'elle-meme aux stocks, chaque minute, tant que son proprietaire est en ligne
 * ({@link StashChestBlockEntity#serverTick}).
 */
public class StashChestBlock extends BaseEntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Le coffre sur ses pieds, sa porte en saillie (le cadran et la poignee n'arretent personne). */
    private static final Map<Direction, VoxelShape> SHAPES = TurnedShapes.of(
            new double[]{1, 0, 1, 15, 15, 15},
            new double[]{2, 2, 0.5, 14, 14, 1});

    public StashChestBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(StashChestBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,
            BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof StashChestBlockEntity stash) {
                stash.serverTick();
            }
        };
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
        return new StashChestBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof StashChestBlockEntity stash) {
            stash.claim(player);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof StashChestBlockEntity stash) {
            if (stash.allows(server)) {
                server.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new StashMenu(id, inventory, stash,
                        server), Component.translatable("block.dohy.stash_chest")));
            } else {
                server.sendSystemMessage(Component.literal("Cette planque appartient a " + stash.ownerName() + ".")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Seul son proprietaire la casse. */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof StashChestBlockEntity stash && !stash.allows(player)) {
            return 0.0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }
}

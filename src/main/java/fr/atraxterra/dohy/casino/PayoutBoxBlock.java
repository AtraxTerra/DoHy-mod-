package fr.atraxterra.dohy.casino;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * La caisse des gains : le panneau [Casino2] de NationsGlory s'y accroche. Quand la caisse recoit
 * un signal (le jeu est gagne), chacun de ses panneaux paie sa somme au dernier joueur qui a paye
 * le jeu du meme nom, depuis le coffre du casino.
 */
public class PayoutBoxBlock extends CasinoBlock {

    public static final MapCodec<PayoutBoxBlock> CODEC = simpleCodec(PayoutBoxBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public PayoutBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
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
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   Orientation orientation, boolean moved) {
        if (level instanceof ServerLevel server && !server.getBlockTicks().hasScheduledTick(pos, this)) {
            server.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean input = level.hasNeighborSignal(pos);
        if (input == state.getValue(POWERED)) {
            return;
        }
        level.setBlock(pos, state.setValue(POWERED, input), Block.UPDATE_CLIENTS);
        if (input) {
            Casinos.payout(level, pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof CasinoBlockEntity box) {
            if (box.ownedBy(server)) {
                CasinoMenus.Book.INSTANCE.open(server);
            } else {
                server.sendSystemMessage(Component.literal("[Casino] Caisse des gains de " + owner(box)
                        + " : elle paie le gagnant quand le jeu l'allume.").withStyle(ChatFormatting.GOLD));
            }
        }
        return InteractionResult.SUCCESS;
    }
}

package fr.atraxterra.dohy.casino;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
 * La machine aleatoire de NationsGlory : elle laisse passer un signal de redstone, ou non, selon
 * un pourcentage regle dans son interface.
 *
 * <p>Le signal entre par l'arriere (le cote de celui qui l'a posee) et sort par l'avant, comme
 * d'un repeteur. A chaque front montant, un tirage : reussi, l'avant s'allume tant que l'entree
 * reste allumee ; rate, il reste eteint jusqu'au prochain front. Le dessus montre une fleche vers
 * la sortie.
 */
public class RandomMachineBlock extends CasinoBlock {

    public static final MapCodec<RandomMachineBlock> CODEC = simpleCodec(RandomMachineBlock::new);
    /** La sortie. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** L'entree allumee. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Le signal passe. */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public RandomMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false)
                .setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
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
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /** Le signal ne sort que par l'avant : vers la voisine de devant, qui demande vers l'arriere. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(OPEN) && direction == state.getValue(FACING).getOpposite() ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(state.getBlock()) && level instanceof ServerLevel server) {
            server.scheduleTick(pos, this, 1);
        }
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
        Direction back = state.getValue(FACING).getOpposite();
        boolean input = level.getSignal(pos.relative(back), back) > 0;
        if (input == state.getValue(POWERED)) {
            return;
        }
        boolean open = false;
        if (input && level.getBlockEntity(pos) instanceof CasinoBlockEntity machine) {
            open = machine.roll(random);
            level.playSound(null, pos, open ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
                    SoundSource.BLOCKS, 0.4f, open ? 1.4f : 0.7f);
        }
        level.setBlock(pos, state.setValue(POWERED, input).setValue(OPEN, open), Block.UPDATE_ALL);
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.relative(state.getValue(FACING)), this);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof CasinoBlockEntity machine) {
            if (machine.ownedBy(server)) {
                CasinoMenus.Machine.INSTANCE.openAt(server, pos);
            } else {
                server.sendSystemMessage(Component.literal("[Casino] Machine aleatoire de " + owner(machine)
                        + " : elle laisse passer " + machine.chance() + " % des signaux.")
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        return InteractionResult.SUCCESS;
    }
}

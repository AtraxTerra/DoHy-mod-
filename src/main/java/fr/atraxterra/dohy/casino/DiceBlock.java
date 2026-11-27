package fr.atraxterra.dohy.casino;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Le de de casino : un clic droit ou un front de redstone le lance. Il roule une demi-seconde
 * (ses faces defilent), puis s'arrete sur une face de 1 a 6, montree sur le dessus. Un comparateur
 * lit la face : un signal de 1 a 6, de quoi monter un jeu de des en redstone.
 */
public class DiceBlock extends CasinoBlock {

    public static final MapCodec<DiceBlock> CODEC = simpleCodec(DiceBlock::new);
    public static final IntegerProperty FACE = IntegerProperty.create("face", 1, 6);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Les tours d'un lancer, un toutes les deux ticks. */
    static final int TUMBLE = 5;

    public DiceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, 6).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, POWERED);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return state.getValue(FACE);
    }

    /** Lance le de, s'il ne roule pas deja. */
    static void roll(ServerLevel level, BlockPos pos, Block block) {
        if (level.getBlockEntity(pos) instanceof CasinoBlockEntity dice && dice.tumble <= 0) {
            dice.tumble = TUMBLE;
            if (!level.getBlockTicks().hasScheduledTick(pos, block)) {
                level.scheduleTick(pos, block, 2);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            roll(server, pos, this);
        }
        return InteractionResult.SUCCESS;
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
        if (input != state.getValue(POWERED)) {
            state = state.setValue(POWERED, input);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            if (input) {
                roll(level, pos, this);
            }
        }
        if (!(level.getBlockEntity(pos) instanceof CasinoBlockEntity dice) || dice.tumble <= 0) {
            return;
        }
        dice.tumble--;
        // Pendant qu'il roule, la face change a chaque tour ; la derniere est tiree sur les six, honnetement.
        int face = dice.tumble > 0 ? 1 + random.nextInt(5) : 1 + random.nextInt(6);
        if (dice.tumble > 0 && face >= state.getValue(FACE)) {
            face++;
        }
        level.setBlock(pos, state.setValue(FACE, face), Block.UPDATE_CLIENTS);
        if (dice.tumble > 0) {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.5f,
                    0.8f + random.nextFloat() * 0.6f);
            level.scheduleTick(pos, this, 2);
        } else {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, 0.6f, 1.2f);
            level.updateNeighbourForOutputSignal(pos, this);
        }
    }
}

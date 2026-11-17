package fr.atraxterra.dohy.arrakis;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Le marteleur : un pieu a masselotte qu'on plante dans le sable de l'erg.
 *
 * <p>Pose (ou relance d'un clic), il frappe une fois par seconde pendant une
 * minute : un coup sourd, un jet de sable. Le desert l'entend plus fort que tout
 * ({@link WormNoise}) : un ver qui fonce vers une moissonneuse se detourne vers
 * lui, et un marteleur seul finit par en appeler un. Le ver le broie en jaillissant.
 */
public class ThumperBlock extends Block {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final IntegerProperty THUMPS = IntegerProperty.create("thumps", 0, 60);

    /** Coups par minute de marche. */
    private static final int MAX_THUMPS = 60;
    /** Demi-periode : la masselotte monte, puis retombe. */
    private static final int HALF = 10;
    /** Ce qu'un coup fait entendre au desert. */
    private static final double NOISE = 20.0d;
    private static final VoxelShape SHAPE = Block.box(3.0d, 0.0d, 3.0d, 13.0d, 16.0d, 13.0d);

    public ThumperBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(ACTIVE, false).setValue(DOWN, false)
                .setValue(THUMPS, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(ThumperBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, DOWN, THUMPS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(state.getBlock()) && level instanceof ServerLevel server) {
            start(server, pos, state);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer target) {
            if (state.getValue(ACTIVE)) {
                target.sendSystemMessage(Component.literal("Le marteleur frappe encore "
                        + (MAX_THUMPS - state.getValue(THUMPS)) + " s.").withStyle(ChatFormatting.GOLD), true);
            } else {
                start(server, pos, state);
                target.sendSystemMessage(Component.literal("Le marteleur frappe : une minute de vacarme.")
                        .withStyle(ChatFormatting.GOLD), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private void start(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(ACTIVE, true).setValue(DOWN, false).setValue(THUMPS, 0), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, HALF);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) {
            return;
        }
        boolean down = !state.getValue(DOWN);
        int thumps = state.getValue(THUMPS);
        if (down) {
            thumps++;
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, 3.0f, 0.5f);
            level.playSound(null, pos, SoundEvents.SAND_HIT, SoundSource.BLOCKS, 1.5f, 0.6f);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                    pos.getX() + 0.5d, pos.getY() + 0.1d, pos.getZ() + 0.5d, 16, 0.6d, 0.1d, 0.6d, 0.15d);
            WormNoise.emitAt(level, pos, NOISE, 3 * 20, true);
        }
        if (thumps >= MAX_THUMPS) {
            level.setBlock(pos, state.setValue(ACTIVE, false).setValue(DOWN, false).setValue(THUMPS, 0),
                    Block.UPDATE_ALL);
            return;
        }
        level.setBlock(pos, state.setValue(DOWN, down).setValue(THUMPS, thumps), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, HALF);
    }
}

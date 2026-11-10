package fr.atraxterra.dohy.palamod.defense;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;

/**
 * L'obsidienne a redstone ({@code BlockObsiRedstone}) : « a toutes les
 * proprietes de l'obsidienne mais permet de transporter la redstone ».
 *
 * <p>Un fil en bloc : elle prend le signal de ses voisins (leviers, repeteurs,
 * blocs de redstone, poudre posee contre elle) et le transmet en perdant un
 * cran par bloc, comme la poudre. D'un fil de poudre, elle ne prend que le
 * signal plus fort que le sien : le reste, c'est elle qui le lui donne.
 * Allumee, elle prend sa texture rouge ; eteinte, la sombre.
 */
public class RedstoneObsidianBlock extends DefenseObsidianBlock {

    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public RedstoneObsidianBlock(Properties properties) {
        super(ObsidianEffect.NONE, properties);
        registerDefaultState(getStateDefinition().any().setValue(POWER, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(RedstoneObsidianBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
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
        int power = incoming(level, pos, state.getValue(POWER));
        if (power != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_ALL);
            level.updateNeighborsAt(pos, this);
        }
    }

    /** Le plus fort signal recu : celui des sources, ou celui d'une voisine moins un. */
    private int incoming(Level level, BlockPos pos, int current) {
        int best = 0;
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            BlockState neighbor = level.getBlockState(next);
            int signal;
            if (neighbor.getBlock() instanceof RedstoneObsidianBlock) {
                signal = neighbor.getValue(POWER) - 1;
            } else if (neighbor.getBlock() instanceof RedStoneWireBlock) {
                // Un fil plus faible que nous tient son signal de nous : on
                // l'ignore, sinon les deux se tiendraient allumes l'un l'autre.
                int wire = neighbor.getSignal(level, next, direction);
                signal = wire > current ? wire : 0;
            } else {
                signal = neighbor.getSignal(level, next, direction);
            }
            best = Math.max(best, signal);
            if (best >= 15) {
                break;
            }
        }
        return Math.max(0, Math.min(15, best));
    }
}

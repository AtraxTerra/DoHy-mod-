package fr.atraxterra.dohy.palamod.defense;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * L'eponge a lave ({@code BlockLavaSponge}) : l'eponge vanilla, pour la lave.
 *
 * <p>Posee, ou touchee par la lave, elle boit la lave a six blocs a la ronde,
 * 65 blocs au plus — les memes bornes que l'eponge — et devient l'eponge a lave
 * pleine, qui se vide au four. Le mur de lave d'un assaillant ne tient pas
 * devant elle.
 */
public class LavaSpongeBlock extends Block {

    private static final int MAX_DEPTH = 6;
    private static final int MAX_COUNT = 65;

    public LavaSpongeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(LavaSpongeBlock::new);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(state.getBlock())) {
            tryAbsorb(level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   Orientation orientation, boolean moved) {
        tryAbsorb(level, pos);
    }

    private void tryAbsorb(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || drain(server, pos) == 0) {
            return;
        }
        server.setBlock(pos, PalaDefense.WET_LAVA_SPONGE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        server.playSound(null, pos, SoundEvents.SPONGE_ABSORB, SoundSource.BLOCKS, 1.0f, 0.8f);
    }

    /** Le parcours en largeur de l'eponge vanilla, sur la lave : rend le nombre de blocs bus. */
    private static int drain(ServerLevel level, BlockPos origin) {
        Deque<BlockPos> queue = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(origin);
        depths.add(0);
        seen.add(origin);
        int removed = 0;
        while (!queue.isEmpty() && removed < MAX_COUNT) {
            BlockPos pos = queue.poll();
            int depth = depths.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!seen.add(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (!state.getFluidState().is(FluidTags.LAVA) || !(state.getBlock() instanceof LiquidBlock)) {
                    continue;
                }
                level.setBlock(next, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                removed++;
                if (removed >= MAX_COUNT) {
                    break;
                }
                if (depth + 1 < MAX_DEPTH) {
                    queue.add(next);
                    depths.add(depth + 1);
                }
            }
        }
        return removed;
    }
}

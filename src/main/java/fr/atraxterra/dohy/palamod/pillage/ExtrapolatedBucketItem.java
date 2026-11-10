package fr.atraxterra.dohy.palamod.pillage;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.palamod.defense.DefenseGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Le seau extrapole ({@code ItemExtrapolatedBucket}) : vise un liquide, et
 * toute la nappe disparait — eau, lave, Fake Water, eau angelique — sources et
 * coulants d'un meme liquide relies entre eux, 512 blocs au plus. La douve, le
 * mur de lave ou le bassin de Fake Water d'une base ne tiennent pas.
 *
 * <p>32 utilisations ; l'arbre de faction en debloque l'usage (Pillage, palier
 * 6), et chaque nappe videe alimente son compteur. Les claims des autres ne se
 * vident pas.
 */
public class ExtrapolatedBucketItem extends Item {

    static final int MAX_BLOCKS = 512;
    static final int MAX_DISTANCE = 24;

    public ExtrapolatedBucketItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        }
        BlockPos origin = hit.getBlockPos();
        BlockState target = level.getBlockState(origin);
        if (!(target.getBlock() instanceof LiquidBlock)) {
            origin = origin.relative(hit.getDirection());
            target = level.getBlockState(origin);
            if (!(target.getBlock() instanceof LiquidBlock)) {
                return InteractionResult.PASS;
            }
        }
        if (!(player instanceof ServerPlayer server) || !(level instanceof ServerLevel world)) {
            return InteractionResult.SUCCESS;
        }
        if (!DefenseGate.check(server, DefenseGate.key(this))) {
            return InteractionResult.FAIL;
        }
        int removed = drain(world, origin, target.getFluidState().getType(), server);
        if (removed == 0) {
            return InteractionResult.FAIL;
        }
        player.getItemInHand(hand).hurtAndBreak(1, server, hand);
        FactionApi.stat(server, "use:extrapolated_bucket", 1);
        level.playSound(null, origin, SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1.0f, 0.7f);
        return InteractionResult.SUCCESS;
    }

    /** Le parcours en largeur de la nappe ; rend le nombre de blocs vides. */
    private static int drain(ServerLevel level, BlockPos origin, Fluid fluid, ServerPlayer player) {
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(origin);
        seen.add(origin);
        int removed = 0;
        while (!queue.isEmpty() && removed < MAX_BLOCKS) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof LiquidBlock) || !state.getFluidState().getType().isSame(fluid)
                    || !TntKind.mayAlter(level, pos, player)) {
                continue;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            removed++;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (next.distManhattan(origin) <= MAX_DISTANCE && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return removed;
    }
}

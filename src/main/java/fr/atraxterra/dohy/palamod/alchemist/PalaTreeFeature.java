package fr.atraxterra.dohy.palamod.alchemist;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Un arbre de PalaMod ({@code WorldTree1Gen}, {@code WorldTree2Gen}).
 *
 * <p>Les generateurs de PalaMod sont des patrons figes, sans hasard : une liste
 * de blocs a poser, la meme pour les quatre essences. {@code (int)(Math.random()
 * * 3)} choisit le premier deux fois sur trois (11 de haut), le second une fois
 * sur trois (12 de haut) ; les patrons 3 et 4 ne sont jamais tires. Les
 * coordonnees sont recopiees du bytecode, l'etage {@code dy = 1} etant celui de
 * la pousse — la case (0, 1, 0) n'est jamais posee : sous le tronc, la place de
 * la pousse reste vide, comme dans l'original.
 *
 * <p><b>Ecart</b> : l'original ecrasait tout ce qui se trouvait dans son
 * empreinte, maison du voisin comprise. Ici, l'arbre ne remplace que l'air, les
 * plantes, l'eau et les feuilles.
 */
public class PalaTreeFeature extends Feature<PalaTreeConfiguration> {

    /** WorldTree1Gen : 30 buches, en triplets (dx, dy, dz). */
    private static final int[] TREE1_LOGS = {
            0, 1, 2, 1, 1, -2, 1, 1, 1, 2, 1, 2, 0, 2, 0, 1, 2, -1,
            1, 2, 1, 0, 3, 0, 0, 4, 0, -1, 5, 0, -1, 6, 0, -2, 7, 0,
            -3, 8, -1, -2, 8, 1, -1, 8, -1, -5, 9, -2, -5, 9, 1, -4, 9, -2,
            -4, 9, 0, -4, 9, 1, -3, 9, -3, -3, 9, -1, -2, 9, 0, -2, 9, 2,
            -2, 9, 3, 0, 9, -2, 0, 9, 0, -2, 10, -1, -1, 10, -1, 0, 10, 0,
    };
    private static final int[] TREE1_LEAVES = {
            -6, 7, 1, -4, 7, 1, -3, 7, 2, -2, 7, 1, -1, 7, -1, -1, 7, 3,
            1, 7, -2, 1, 7, 1, -6, 8, -1, -6, 8, 2, -5, 8, -2, -5, 8, 1,
            -4, 8, -4, -4, 8, -1, -4, 8, 0, -4, 8, 1, -4, 8, 3, -3, 8, -2,
            -3, 8, 0, -3, 8, 1, -2, 8, -3, -2, 8, -1, -2, 8, 2, -2, 8, 3,
            -1, 8, -2, -1, 8, 0, -1, 8, 2, 0, 8, -3, 0, 8, -2, 0, 8, -1,
            0, 8, 0, 0, 8, 1, 1, 8, 0, 2, 8, -2, -6, 9, -2, -6, 9, 0,
            -6, 9, 1, -5, 9, -3, -5, 9, -1, -5, 9, 0, -5, 9, 2, -4, 9, -3,
            -4, 9, -1, -4, 9, 2, -3, 9, -4, -3, 9, -2, -3, 9, 0, -3, 9, 2,
            -3, 9, 3, -2, 9, -4, -2, 9, -1, -2, 9, 1, -2, 9, 4, -1, 9, -3,
            -1, 9, 0, -1, 9, 1, -1, 9, 2, -1, 9, 3, 0, 9, -4, 0, 9, -1,
            0, 9, 1, 0, 9, 2, 1, 9, -3, 1, 9, -2, 1, 9, -1, 1, 9, 1,
            2, 9, 0, -5, 10, -2, -5, 10, 1, -4, 10, -2, -4, 10, 0, -4, 10, 1,
            -3, 10, -3, -3, 10, -1, -3, 10, 1, -3, 10, 2, -2, 10, -3, -2, 10, -2,
            -2, 10, 0, -2, 10, 2, -2, 10, 3, -1, 10, -2, -1, 10, 0, -1, 10, 1,
            -1, 10, 2, 0, 10, -3, 0, 10, -2, 0, 10, -1, 0, 10, 1, 1, 10, 0,
            -4, 11, -1, -3, 11, -2, -3, 11, 0, -2, 11, -1, -2, 11, 1, -1, 11, -1,
            0, 11, 0,
    };

    /** WorldTree2Gen : 35 buches, en triplets (dx, dy, dz). */
    private static final int[] TREE2_LOGS = {
            -2, 1, -1, -2, 1, 0, -1, 1, 1, 0, 1, -3, 2, 1, 0, -1, 2, -1,
            -1, 2, 1, 1, 2, -2, 2, 2, 0, 0, 3, 0, 1, 3, -1, 0, 4, 0,
            0, 5, 0, 0, 6, -1, 0, 7, -1, 1, 7, 0, -2, 8, -1, -1, 8, -1,
            0, 8, -2, 1, 8, -1, 1, 8, 1, -3, 9, -2, -1, 9, -4, -1, 9, 0,
            0, 9, -3, 1, 9, 2, 2, 9, -1, 3, 9, -1, -2, 10, 1, 0, 10, 0,
            -1, 11, -1, 0, 11, -3, 0, 11, -2, 0, 11, -1, 1, 11, -1,
    };
    private static final int[] TREE2_LEAVES = {
            -2, 6, 0, -1, 6, -3, -4, 7, -1, -3, 7, -2, -2, 7, -4, -2, 7, -3,
            -2, 7, -1, -2, 7, 0, -1, 7, -2, -1, 7, 1, 0, 7, -4, 0, 7, -2,
            0, 7, 2, 1, 7, -3, 1, 7, -1, 1, 7, 1, 2, 7, 0, 2, 7, 2,
            3, 7, -1, -4, 8, -3, -4, 8, 1, -3, 8, -4, -3, 8, -2, -3, 8, -1,
            -2, 8, -2, -2, 8, 0, -2, 8, 2, -1, 8, -5, -1, 8, -4, -1, 8, -3,
            -1, 8, 0, 0, 8, -4, 0, 8, -3, 0, 8, 1, 0, 8, 3, 1, 8, -5,
            1, 8, -3, 1, 8, -2, 1, 8, 0, 1, 8, 2, 1, 8, 3, 2, 8, -4,
            2, 8, -2, 2, 8, -1, 2, 8, 1, 3, 8, -3, 3, 8, 0, 3, 8, 2,
            -4, 9, -2, -3, 9, -3, -3, 9, -1, -3, 9, 0, -3, 9, 1, -3, 9, 2,
            -2, 9, -5, -2, 9, -4, -2, 9, -1, -2, 9, 0, -2, 9, 1, -1, 9, -6,
            -1, 9, -3, -1, 9, -2, -1, 9, 1, -1, 9, 2, -1, 9, 3, 0, 9, -5,
            0, 9, -4, 0, 9, -2, 0, 9, 0, 0, 9, 2, 0, 9, 3, 1, 9, -4,
            1, 9, -2, 1, 9, -1, 1, 9, 1, 1, 9, 4, 2, 9, -3, 2, 9, -2,
            2, 9, 2, 2, 9, 3, 3, 9, -2, 3, 9, 0, 4, 9, -1, -3, 10, -2,
            -3, 10, 1, -2, 10, -3, -2, 10, -2, -2, 10, 0, -2, 10, 2, -1, 10, -5,
            -1, 10, -4, -1, 10, -2, -1, 10, -1, -1, 10, 0, -1, 10, 1, -1, 10, 2,
            0, 10, -3, 0, 10, -1, 0, 10, 1, 1, 10, -5, 1, 10, -3, 1, 10, 0,
            1, 10, 2, 1, 10, 3, 2, 10, -2, 2, 10, -1, 2, 10, 0, 2, 10, 1,
            3, 10, -3, 3, 10, -1, -2, 11, -1, -2, 11, 1, -1, 11, -3, -1, 11, -2,
            -1, 11, 0, 0, 11, -4, 0, 11, 0, 0, 11, 1, 1, 11, -3, 1, 11, -2,
            1, 11, 0, 2, 11, -4, 2, 11, -1, -1, 12, -1, 0, 12, -3, 0, 12, -2,
            0, 12, -1, 1, 12, -1, 2, 12, -3,
    };

    public PalaTreeFeature() {
        super(PalaTreeConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<PalaTreeConfiguration> context) {
        boolean first = context.random().nextInt(3) <= 1;
        BlockPos origin = context.origin();
        WorldGenLevel level = context.level();
        PalaTreeConfiguration config = context.config();
        Set<Long> logs = place(level, origin, first ? TREE1_LOGS : TREE2_LOGS, config.log());
        placeLeaves(level, origin, first ? TREE1_LEAVES : TREE2_LEAVES, config.leaves(), logs);
        return true;
    }

    /** Pose les cases libres du patron ; rend celles qui portent le bloc. */
    private Set<Long> place(WorldGenLevel level, BlockPos origin, int[] cells, BlockState state) {
        Set<Long> placed = new HashSet<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < cells.length; i += 3) {
            pos.setWithOffset(origin, cells[i], cells[i + 1] - 1, cells[i + 2]);
            if (free(level.getBlockState(pos), state)) {
                setBlock(level, pos, state);
                placed.add(pos.asLong());
            }
        }
        return placed;
    }

    /**
     * Les feuilles, avec leur distance aux buches posees (26 voisins, comme
     * {@link PalaLeavesBlock#distance}) : naturelles, elles tomberont quand on
     * abattra l'arbre.
     */
    private void placeLeaves(WorldGenLevel level, BlockPos origin, int[] cells, BlockState state, Set<Long> logs) {
        Set<Long> free = new HashSet<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < cells.length; i += 3) {
            pos.setWithOffset(origin, cells[i], cells[i + 1] - 1, cells[i + 2]);
            if (!logs.contains(pos.asLong()) && free(level.getBlockState(pos), state)) {
                free.add(pos.asLong());
            }
        }
        Map<Long, Integer> distance = new HashMap<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (long log : logs) {
            distance.put(log, 0);
            queue.add(log);
        }
        while (!queue.isEmpty()) {
            long at = queue.poll();
            int next = distance.get(at) + 1;
            if (next > 6) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long near = BlockPos.offset(at, dx, dy, dz);
                        if (free.contains(near) && !distance.containsKey(near)) {
                            distance.put(near, next);
                            queue.add(near);
                        }
                    }
                }
            }
        }
        for (long cell : free) {
            BlockState leaf = state;
            if (leaf.getBlock() instanceof LeavesBlock) {
                leaf = leaf.setValue(LeavesBlock.PERSISTENT, false)
                        .setValue(LeavesBlock.DISTANCE, distance.getOrDefault(cell, 7));
            }
            setBlock(level, pos.set(cell), leaf);
        }
    }

    private static boolean free(BlockState current, BlockState state) {
        return current.isAir() || current.canBeReplaced() || current.is(BlockTags.LEAVES)
                || current.getBlock() == state.getBlock();
    }
}

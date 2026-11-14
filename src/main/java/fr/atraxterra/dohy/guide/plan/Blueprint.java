package fr.atraxterra.dohy.guide.plan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Le plan d'une structure a plusieurs blocs, tel que l'hologramme le montre.
 *
 * <p>Les cases sont donnees dans un repere de plan : {@code x} de gauche a
 * droite (0 au milieu), {@code y} vers le haut (0 au sol), {@code z} vers
 * l'avant (0 au premier rang, celui qui fait face au joueur). Posee, la
 * structure s'etend devant le joueur, dans la direction de son regard
 * ({@link #place}). Chaque case dit ce qu'elle attend ({@link Expect}).
 *
 * @param id      identifiant (« grinder », « enclos »...)
 * @param name    nom affiche
 * @param entry   la fiche de l'encyclopedie qui en parle
 * @param icon    le bloc qui represente la structure
 * @param variants les tailles ou paliers ; une seule le plus souvent
 */
public record Blueprint(String id, String name, String entry, Block icon, List<Variant> variants) {

    public Variant variant(int index) {
        return variants.get(Math.floorMod(index, variants.size()));
    }

    public ItemStack iconStack() {
        return new ItemStack(icon);
    }

    /** Une taille ou un palier : ses cases, et son encombrement. */
    public record Variant(String label, List<Cell> cells, int width, int depth, int height) {

        /** Les cases qui comptent pour dire la structure complete. */
        public int required() {
            int count = 0;
            for (Cell cell : cells) {
                if (cell.expect().required()) {
                    count++;
                }
            }
            return count;
        }

        /** Le materiel : ce qu'il faut de chaque bloc, dans l'ordre ou il apparait. */
        public Map<Expect, Integer> materials() {
            Map<Expect, Integer> counts = new LinkedHashMap<>();
            for (Cell cell : cells) {
                if (cell.expect().required()) {
                    counts.merge(cell.expect(), 1, Integer::sum);
                }
            }
            return counts;
        }
    }

    /** Une case du plan, dans le repere du plan. */
    public record Cell(int x, int y, int z, Expect expect) {
    }

    /** Ou en est une case posee. */
    public enum Status {
        /** Le bon bloc, dans le bon etat. */
        CORRECT,
        /** Rien encore (air, herbe, liquide qui coule). */
        MISSING,
        /** Le bon bloc, pas encore dans le bon etat (une obsidienne a charger, de l'eau qui coule). */
        PARTIAL,
        /** Un autre bloc a retirer. */
        WRONG,
        /** Une case libre : ni comptee, ni exigee (un coin de portail, le coeur d'une reserve). */
        OPTIONAL
    }

    /**
     * Ce qu'attend une case.
     *
     * @param label    le nom du materiel (pour la liste)
     * @param display  l'etat dessine par l'hologramme
     * @param block    le bloc attendu, ou {@code null} pour un liquide
     * @param fluid    le liquide attendu (une source), ou {@code null}
     * @param ready    pour un bloc : l'etat qu'il doit avoir en plus (charge...), ou {@code null}
     * @param required compte dans la structure ; sinon case libre
     * @param exclusive pour une case libre : ce bloc ou rien, tout autre bloc est a retirer
     */
    public record Expect(String label, BlockState display, Block block, Fluid fluid, Predicate<BlockState> ready,
                         boolean required, boolean exclusive) {

        public static Expect block(String label, Block block) {
            return new Expect(label, block.defaultBlockState(), block, null, null, true, false);
        }

        public static Expect charged(String label, Block block, Predicate<BlockState> ready, BlockState display) {
            return new Expect(label, display, block, null, ready, true, false);
        }

        public static Expect fluid(String label, Fluid fluid) {
            return new Expect(label, fluid.defaultFluidState().createLegacyBlock(), null, fluid, null, true, false);
        }

        /** Une case libre : n'importe quel bloc, ou rien (un coin de portail). */
        public static Expect optional(String label, Block block) {
            return new Expect(label, block.defaultBlockState(), block, null, null, false, false);
        }

        /** Une case libre, mais seulement pour ce bloc : vide, ou ce bloc (le coeur d'une reserve d'or). */
        public static Expect emptyOr(String label, Block block) {
            return new Expect(label, block.defaultBlockState(), block, null, null, false, true);
        }

        public Status status(BlockGetter level, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            if (!required) {
                return !exclusive || state.isAir() || state.is(block) ? Status.OPTIONAL : Status.WRONG;
            }
            if (fluid != null) {
                FluidState liquid = state.getFluidState();
                if (liquid.getType().isSame(fluid)) {
                    return liquid.isSource() ? Status.CORRECT : Status.PARTIAL;
                }
                return state.isAir() || state.canBeReplaced() ? Status.MISSING : Status.WRONG;
            }
            if (state.is(block)) {
                return ready == null || ready.test(state) ? Status.CORRECT : Status.PARTIAL;
            }
            return state.isAir() || state.canBeReplaced() ? Status.MISSING : Status.WRONG;
        }

        // Deux attentes du meme materiel sont la meme ligne de la liste.
        @Override
        public boolean equals(Object other) {
            return other instanceof Expect that && label.equals(that.label) && required == that.required;
        }

        @Override
        public int hashCode() {
            return label.hashCode() * 31 + (required ? 1 : 0);
        }
    }

    // -- Pose ------------------------------------------------------------------------------

    /**
     * La position dans le monde d'une case du plan : {@code anchor} est le
     * milieu du premier rang, au sol ; {@code quarter} la direction du regard
     * du joueur a la pose ({@link Direction#get2DDataValue}).
     */
    public static BlockPos place(BlockPos anchor, int quarter, int x, int y, int z) {
        Direction forward = Direction.from2DDataValue(quarter);
        Direction right = forward.getClockWise();
        return anchor.offset(right.getStepX() * x + forward.getStepX() * z, y,
                right.getStepZ() * x + forward.getStepZ() * z);
    }

    /** Les cases en place, sur celles qui comptent (une case libre occupee a tort n'y entre pas). */
    public static int[] progress(BlockGetter level, Variant variant, BlockPos anchor, int quarter) {
        int done = 0;
        int total = 0;
        for (Cell cell : variant.cells()) {
            if (!cell.expect().required()) {
                continue;
            }
            total++;
            if (cell.expect().status(level, place(anchor, quarter, cell.x(), cell.y(), cell.z())) == Status.CORRECT) {
                done++;
            }
        }
        return new int[]{done, total};
    }

    // -- Construction des plans ------------------------------------------------------------

    /**
     * Ramene des cases donnees autour d'un bloc cle (dx, dy, dz quelconques)
     * dans le repere du plan : le sol a 0, le premier rang a 0, centre en x.
     */
    static Variant normalize(String label, List<int[]> offsets, List<Expect> expects) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] offset : offsets) {
            minX = Math.min(minX, offset[0]);
            maxX = Math.max(maxX, offset[0]);
            minY = Math.min(minY, offset[1]);
            maxY = Math.max(maxY, offset[1]);
            minZ = Math.min(minZ, offset[2]);
            maxZ = Math.max(maxZ, offset[2]);
        }
        int centerX = Math.floorDiv(minX + maxX, 2);
        List<Cell> cells = new ArrayList<>(offsets.size());
        for (int index = 0; index < offsets.size(); index++) {
            int[] offset = offsets.get(index);
            cells.add(new Cell(offset[0] - centerX, offset[1] - minY, offset[2] - minZ, expects.get(index)));
        }
        return new Variant(label, List.copyOf(cells), maxX - minX + 1, maxZ - minZ + 1, maxY - minY + 1);
    }
}

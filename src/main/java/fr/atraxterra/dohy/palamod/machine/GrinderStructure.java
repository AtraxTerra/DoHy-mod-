package fr.atraxterra.dohy.palamod.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

/**
 * La structure du grinder ({@code BlockGrinder.checkNorth/East/South/West},
 * {@code GrinderBluePrint}) : un cube plein de 3 x 3 x 3, le grinder au milieu
 * d'une face, une source de lave au centre, un casing au milieu des cinq autres
 * faces, des frames sur les vingt aretes et coins.
 *
 * <pre>
 *   bas          milieu        haut          (le grinder G devant, au milieu)
 *   F F F        F C F         F F F
 *   F C F        C L C         F C F
 *   F F F        F G F         F F F
 * </pre>
 * Le cube peut s'etendre de n'importe quel cote horizontal du grinder :
 * l'orientation de sa face n'est pas consultee. 126 lingots de paladium,
 * 40 de titane et un seau de lave.
 */
public final class GrinderStructure {

    private GrinderStructure() {
    }

    public static boolean isComplete(Level level, BlockPos grinder) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (isComplete(level, grinder, direction)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isComplete(Level level, BlockPos grinder, Direction inward) {
        BlockPos center = grinder.relative(inward);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos cell = center.offset(dx, dy, dz);
                    if (cell.equals(grinder)) {
                        continue;
                    }
                    int axes = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (axes == 0) {
                        FluidState fluid = level.getFluidState(cell);
                        if (!fluid.is(FluidTags.LAVA) || !fluid.isSource()) {
                            return false;
                        }
                    } else if (axes == 1) {
                        if (!level.getBlockState(cell).is(PalaMachines.GRINDER_CASING.get())) {
                            return false;
                        }
                    } else if (!level.getBlockState(cell).is(PalaMachines.GRINDER_FRAME.get())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}

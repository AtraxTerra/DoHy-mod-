package fr.atraxterra.dohy.palamod.golem;

import fr.atraxterra.dohy.palamod.fluid.PalaFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

/**
 * {@code BlockGuardianBox} : la boite est un cube de 3 x 3 x 3 dont le bloc de
 * boite occupe le centre d'une face laterale. Au centre, une source d'eau
 * angelique ; partout ailleurs, 25 cadres de boite.
 */
public final class GolemBoxStructure {

    private GolemBoxStructure() {
    }

    public static boolean isComplete(Level level, BlockPos box) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (complete(level, box, box.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    private static boolean complete(Level level, BlockPos box, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (pos.equals(box)) {
                        continue;
                    }
                    if (dx == 0 && dy == 0 && dz == 0) {
                        FluidState fluid = level.getFluidState(pos);
                        if (fluid.getType() != PalaFluids.ANGELIC_WATER.get() || !fluid.isSource()) {
                            return false;
                        }
                    } else if (!level.getBlockState(pos).is(PalaGolem.GOLEM_BOX_FRAME.get())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}

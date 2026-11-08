package fr.atraxterra.dohy.palamod.alchemist;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.List;

/**
 * La forme du chaudron ({@code BlockCauldronCore.func_149689_a},
 * {@code CauldronCoreBluePrint}), relative au coeur :
 *
 * <pre>
 *   y+1        y 0
 *   . . . . . . .    C C C C C C C
 *   . C C C C C .    C C C C C C C
 *   . C ~ ~ ~ C .    C C C C C C C
 *   . C ~ ~ ~ C .    C C C H C C C      C = bloc du chaudron, H = coeur
 *   . C ~ ~ ~ C .    C C C C C C C      ~ = bassin d'eau angelique
 *   . C C C C C .    C C C C C C C
 *   . . . . . . .    C C C C C C C
 * </pre>
 *
 * 48 + 16 = 64 blocs du chaudron, le coeur pose en dernier. <b>Ecart</b> : le
 * code ne verifiait que 15 des 16 blocs de l'anneau (le coin (+2, +2) etait
 * oublie) ; les 16 sont verifies ici. Les deux supports de tank (x +-3) ne
 * servaient qu'aux boules de glu, non portees : ce sont de simples blocs.
 */
public final class CauldronStructure {

    /** Le plancher : le carre de 7 x 7 sauf le coeur. */
    static final List<BlockPos> BASE = new ArrayList<>();
    /** L'anneau de 5 x 5 au-dessus. */
    static final List<BlockPos> RING = new ArrayList<>();
    /** Le bassin de 3 x 3 dans l'anneau. */
    static final List<BlockPos> BASIN = new ArrayList<>();

    static {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx != 0 || dz != 0) {
                    BASE.add(new BlockPos(dx, 0, dz));
                }
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring == 2) {
                    RING.add(new BlockPos(dx, 1, dz));
                } else if (ring <= 1) {
                    BASIN.add(new BlockPos(dx, 1, dz));
                }
            }
        }
    }

    private CauldronStructure() {
    }

    /** Le plancher et l'anneau sont-ils complets autour de ce coeur ? */
    public static boolean isComplete(Level level, BlockPos core) {
        for (BlockPos offset : BASE) {
            if (!level.getBlockState(core.offset(offset)).is(PalaAlchemist.CAULDRON.get())) {
                return false;
            }
        }
        for (BlockPos offset : RING) {
            if (!level.getBlockState(core.offset(offset)).is(PalaAlchemist.CAULDRON.get())) {
                return false;
            }
        }
        return true;
    }

    /** Sources d'eau angelique du bassin, et si de l'eau angelique y coule. */
    static int[] basin(Level level, BlockPos core) {
        int sources = 0;
        int any = 0;
        for (BlockPos offset : BASIN) {
            FluidState fluid = level.getFluidState(core.offset(offset));
            if (fluid.getType().isSame(fr.atraxterra.dohy.palamod.fluid.PalaFluids.ANGELIC_WATER.get())) {
                any++;
                if (fluid.isSource()) {
                    sources++;
                }
            }
        }
        return new int[]{sources, any};
    }
}

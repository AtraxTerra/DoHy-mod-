package fr.atraxterra.dohy.palette;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Le regroupement des palettes autour d'une palette posee ou cassee.
 *
 * <p>On rassemble les palettes d'un carre de 5 x 5 autour d'elle, puis, de
 * proche en proche, les groupes entiers auxquels elles appartenaient : on ne
 * refait ainsi que des groupes complets, et aucun groupe voisin ne se retrouve
 * a moitie redecoupe. L'ensemble est ensuite coupe en groupes d'au plus 3 x 3
 * ({@link PalettePlan}). Rien ne bouge dans les stocks : chaque palette garde
 * ses piles, seule la vue du groupe change.
 */
final class PaletteGroups {

    private static final int RADIUS = 2;
    /** Neuf groupes complets au plus par regroupement. */
    private static final int MAX_CELLS = 81;

    private PaletteGroups() {
    }

    static void regroupAround(ServerLevel level, BlockPos origin) {
        Set<Long> cells = new HashSet<>();
        Deque<BlockPos> work = new ArrayDeque<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                BlockPos at = origin.offset(dx, 0, dz);
                if (pallet(level, at) != null && cells.add(PalettePlan.key(at.getX(), at.getZ()))) {
                    work.add(at);
                }
            }
        }
        while (!work.isEmpty() && cells.size() < MAX_CELLS) {
            PaletteBlockEntity pallet = pallet(level, work.poll());
            if (pallet == null) {
                continue;
            }
            BlockPos ctrl = pallet.controllerPos();
            for (int gz = 0; gz < pallet.groupD(); gz++) {
                for (int gx = 0; gx < pallet.groupW(); gx++) {
                    BlockPos member = ctrl.offset(gx, 0, gz);
                    if (pallet(level, member) != null && cells.add(PalettePlan.key(member.getX(), member.getZ()))) {
                        work.add(member);
                    }
                }
            }
        }
        for (PalettePlan.Rect rect : PalettePlan.partition(cells)) {
            apply(level, rect, origin.getY());
        }
    }

    private static PaletteBlockEntity pallet(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PaletteBlockEntity pallet
                && !pallet.isRemoved() ? pallet : null;
    }

    private static void apply(ServerLevel level, PalettePlan.Rect rect, int y) {
        BlockPos ctrl = new BlockPos(rect.x(), y, rect.z());
        PaletteBlockEntity controller = pallet(level, ctrl);
        if (controller == null) {
            return;
        }
        for (int dz = 0; dz < rect.d(); dz++) {
            for (int dx = 0; dx < rect.w(); dx++) {
                PaletteBlockEntity member = pallet(level, ctrl.offset(dx, 0, dz));
                if (member != null) {
                    member.assign(ctrl, rect.w(), rect.d());
                }
            }
        }
        // Les caisses suivent l'axe long du groupe ; pour un groupe carre, celui de la palette posee.
        boolean alongX = rect.w() != rect.d() ? rect.w() > rect.d()
                : controller.getBlockState().getValue(PaletteBlock.FACING).getAxis() == Direction.Axis.Z;
        controller.setAlongX(alongX);
        controller.refresh(true);
    }
}

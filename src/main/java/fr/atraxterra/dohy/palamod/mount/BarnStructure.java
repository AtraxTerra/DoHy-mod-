package fr.atraxterra.dohy.palamod.mount;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * L'enclos ({@code BlockBarnController.checkStructureFromBarn}).
 *
 * <p>Sous le gestionnaire, une botte de foin ; autour d'elle, un carre de
 * foin de 3x3 a 7x7 (on compte les bottes contigues dans les quatre
 * directions, jusqu'a sept) ; tout autour, un anneau de fondations d'enclos,
 * chacune portant une barriere d'enclos. Le nombre de fondations doit valoir
 * {@code (√foin + 1) x 4} : seuls les carres parfaits passent. Un seul
 * gestionnaire sur le foin ; une couveuse posee sur le foin permet les soins.
 * Le gestionnaire peut etre n'importe ou sur le carre.
 *
 * <p>La taille rendue est le cote du carre de foin, de 3 a 7.
 */
public final class BarnStructure {

    /** Bottes comptees au plus dans chaque direction depuis le gestionnaire. */
    private static final int REACH = 7;

    public record Result(int size, boolean incubator, Component error) {
        public boolean valid() {
            return error == null;
        }
    }

    private BarnStructure() {
    }

    public static Result check(Level level, BlockPos controller) {
        BlockPos center = controller.below();
        if (!isHay(level, center)) {
            return fail("structure_hay");
        }
        int east = run(level, center, Direction.EAST);
        int west = run(level, center, Direction.WEST);
        int south = run(level, center, Direction.SOUTH);
        int north = run(level, center, Direction.NORTH);
        int minX = center.getX() - west;
        int maxX = center.getX() + east;
        int minZ = center.getZ() - north;
        int maxZ = center.getZ() + south;
        int y = center.getY();

        int hay = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (isHay(level, new BlockPos(x, y, z))) {
                    hay++;
                }
            }
        }
        if (hay < 9 || hay > 49) {
            return fail("structure_hay");
        }

        int foundations = 0;
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                BlockPos cell = new BlockPos(x, y, z);
                if (level.getBlockState(cell).is(PalaMount.BARN_FOUNDATION.get())) {
                    foundations++;
                    if (!level.getBlockState(cell.above()).is(PalaMount.BARN_FENCE.get())) {
                        return fail("structure_fences");
                    }
                }
            }
        }
        double side = Math.sqrt(hay);
        if (foundations != (side + 1) * 4) {
            return new Result(-1, false, Component.translatable("message.palamod.mount.structure_foundations",
                    foundations, ((int) side + 1) * 4));
        }

        int controllers = 0;
        boolean incubator = false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos cell = new BlockPos(x, controller.getY(), z);
                if (level.getBlockState(cell).is(PalaMount.BARN_CONTROLLER.get())) {
                    controllers++;
                } else if (level.getBlockState(cell).is(PalaMount.BARN_INCUBATOR.get())) {
                    incubator = true;
                }
            }
        }
        if (controllers > 1) {
            return fail("structure_controllers");
        }
        return new Result((int) side, incubator, null);
    }

    private static int run(Level level, BlockPos center, Direction direction) {
        int count = 0;
        for (int i = 1; i <= REACH && isHay(level, center.relative(direction, i)); i++) {
            count++;
        }
        return count;
    }

    private static boolean isHay(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.HAY_BLOCK);
    }

    private static Result fail(String key) {
        return new Result(-1, false, Component.translatable("message.palamod.mount." + key));
    }
}

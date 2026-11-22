package fr.atraxterra.dohy.underground;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Les formes d'un poste en relief tourne vers chacune des quatre faces (lot 24c). On les donne
 * pour le poste tourne vers le nord, en pixels, comme les elements de son modele ; elles tournent
 * comme le modele dans son etat de bloc : un quart de tour vers l'est, un demi vers le sud, trois
 * quarts vers l'ouest, dans le sens des aiguilles d'une montre vu d'en haut.
 */
final class TurnedShapes {

    private TurnedShapes() {
    }

    /** Chaque boite : {x1, y1, z1, x2, y2, z2}, en pixels, le poste tourne vers le nord. */
    static Map<Direction, VoxelShape> of(double[]... boxes) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] box : boxes) {
                shape = Shapes.or(shape, turned(box, quarters(facing)));
            }
            shapes.put(facing, shape.optimize());
        }
        return shapes;
    }

    private static int quarters(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    /** Un quart de tour vu d'en haut : la face nord passe a l'est, soit x' = 16 - z et z' = x. */
    private static VoxelShape turned(double[] box, int quarters) {
        double x1 = box[0];
        double z1 = box[2];
        double x2 = box[3];
        double z2 = box[5];
        for (int turn = 0; turn < quarters; turn++) {
            double nextX1 = 16.0d - z2;
            double nextX2 = 16.0d - z1;
            z1 = x1;
            z2 = x2;
            x1 = nextX1;
            x2 = nextX2;
        }
        return Block.box(x1, box[1], z1, x2, box[4], z2);
    }
}

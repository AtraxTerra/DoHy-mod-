package fr.atraxterra.dohy.agri.machine;

import fr.atraxterra.dohy.agri.DoHyAgri;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Les grandes machines de l'agriculture : six machines de la ferme qui occupent
 * plusieurs cases (le silo 3 x 5 x 3, le moulin et la machine a pain 3 x 4 x 3,
 * le biogenerateur et la machine a OGM 3 x 3 x 3, la pompe 2 x 3 x 2).
 *
 * <h2>Une commande et des parties</h2>
 *
 * <p>La machine reste son bloc : le silo, le moulin... C'est la <b>commande</b>,
 * qui garde l'entite de bloc, l'inventaire et tout ce qu'elle faisait. Une fois
 * posee, elle se deploie ({@code formed=true}) : chaque autre case de la
 * construction recoit un <b>bloc de partie</b> ({@link BigMachinePartBlock}) qui
 * sait ou est sa commande — ses proprietes x, y, z disent sa case dans la machine,
 * facing son orientation. Un clic sur une partie est un clic sur la commande ; un
 * tuyau, un entonnoir ou un cable branche sur une partie atteint la commande.
 * Casser une partie, c'est casser la machine : elle tombe en objet, son contenu
 * avec, et toutes les parties disparaissent.
 *
 * <h2>Le repere</h2>
 *
 * <p>Dans le repere de la machine, x va vers la droite vue de face, y vers le haut,
 * z vers l'arriere ; la face avant regarde le nord, et {@code facing} la tourne
 * comme un bloc : vers l'est, une case (dx, dz) de la commande passe en (-dz, dx).
 * Les cases occupees et leurs boites de collision viennent de
 * {@link BigMachineShapes}, tires des modeles par {@code tools/agri_machine_assets.py}.
 *
 * <h2>La place</h2>
 *
 * <p>Une machine ne se pose que si toutes ses cases sont libres (air, herbe,
 * neige...) ; sinon la pose est refusee et le joueur sait pourquoi. Une machine
 * posee avant cette version (un seul bloc, {@code formed=false}) garde son petit
 * modele et marche comme avant ; un clic la deploie des que la place est libre.
 */
public enum BigMachine {

    SILO("silo", "le silo", BigMachineShapes.SILO_SIZE, BigMachineShapes.SILO_CELLS),
    FLOUR_MILL("flour_mill", "le moulin", BigMachineShapes.FLOUR_MILL_SIZE, BigMachineShapes.FLOUR_MILL_CELLS),
    BREAD_MACHINE("bread_machine", "la machine a pain", BigMachineShapes.BREAD_MACHINE_SIZE,
            BigMachineShapes.BREAD_MACHINE_CELLS),
    BIOGENERATOR("biogenerator", "le biogenerateur", BigMachineShapes.BIOGENERATOR_SIZE,
            BigMachineShapes.BIOGENERATOR_CELLS),
    GMO_MACHINE("gmo_machine", "la machine a OGM", BigMachineShapes.GMO_MACHINE_SIZE,
            BigMachineShapes.GMO_MACHINE_CELLS),
    FUEL_PUMP("fuel_pump", "la pompe a carburant", BigMachineShapes.FUEL_PUMP_SIZE,
            BigMachineShapes.FUEL_PUMP_CELLS);

    /** Une case occupee, dans le repere de la machine, et sa boite de collision (pixels). */
    public record Cell(int x, int y, int z, double[] box) {
    }

    private final String id;
    private final String label;
    private final int width;
    private final int height;
    private final int depth;
    private final int controllerX;
    private final int controllerY;
    private final int controllerZ;
    private final List<Cell> cells;
    private final Map<Direction, Map<Integer, VoxelShape>> shapes = new EnumMap<>(Direction.class);

    BigMachine(String id, String label, int[] size, double[][] cells) {
        this.id = id;
        this.label = label;
        this.width = size[0];
        this.height = size[1];
        this.depth = size[2];
        this.controllerX = size[3];
        this.controllerY = size[4];
        this.controllerZ = size[5];
        List<Cell> list = new ArrayList<>();
        for (double[] row : cells) {
            list.add(new Cell((int) row[0], (int) row[1], (int) row[2],
                    new double[]{row[3], row[4], row[5], row[6], row[7], row[8]}));
        }
        this.cells = Collections.unmodifiableList(list);
    }

    public String id() {
        return id;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    public int controllerX() {
        return controllerX;
    }

    public int controllerY() {
        return controllerY;
    }

    public int controllerZ() {
        return controllerZ;
    }

    public List<Cell> cells() {
        return cells;
    }

    /** Le bloc de commande : la machine elle-meme. */
    public Block controller() {
        return switch (this) {
            case SILO -> DoHyAgri.SILO.get();
            case FLOUR_MILL -> DoHyAgri.FLOUR_MILL.get();
            case BREAD_MACHINE -> DoHyAgri.BREAD_MACHINE.get();
            case BIOGENERATOR -> DoHyAgri.BIOGENERATOR.get();
            case GMO_MACHINE -> DoHyAgri.GMO_MACHINE.get();
            case FUEL_PUMP -> DoHyAgri.FUEL_PUMP.get();
        };
    }

    /** Le bloc de partie de cette machine. */
    public Block part() {
        Supplier<? extends Block> part = DoHyAgri.part(this);
        return part.get();
    }

    /** La machine dont ce bloc est la commande, ou {@code null}. */
    public static BigMachine of(Block controller) {
        for (BigMachine machine : values()) {
            if (machine.controller() == controller) {
                return machine;
            }
        }
        return null;
    }

    /** Une case de la machine, en monde, depuis la position de la commande. */
    public BlockPos toWorld(BlockPos controller, Direction facing, int x, int y, int z) {
        int dx = x - controllerX;
        int dz = z - controllerZ;
        int wx;
        int wz;
        switch (facing) {
            case EAST -> {
                wx = -dz;
                wz = dx;
            }
            case SOUTH -> {
                wx = -dx;
                wz = -dz;
            }
            case WEST -> {
                wx = dz;
                wz = -dx;
            }
            default -> {
                wx = dx;
                wz = dz;
            }
        }
        return controller.offset(wx, y - controllerY, wz);
    }

    /** La commande d'une partie, depuis sa position et sa case. */
    public BlockPos controllerOf(BlockPos part, Direction facing, int x, int y, int z) {
        BlockPos offset = toWorld(BlockPos.ZERO, facing, x, y, z);
        return part.subtract(offset);
    }

    public boolean isController(int x, int y, int z) {
        return x == controllerX && y == controllerY && z == controllerZ;
    }

    /** La case libre pour une partie : remplacable (air, herbe, neige...) et dans le monde. */
    private static boolean free(LevelReader level, BlockPos pos) {
        return level.isInsideBuildHeight(pos.getY()) && level.getBlockState(pos).canBeReplaced();
    }

    /** Toutes les cases, hors commande, sont-elles libres ? */
    public boolean fits(LevelReader level, BlockPos controller, Direction facing) {
        for (Cell cell : cells) {
            if (isController(cell.x(), cell.y(), cell.z())) {
                continue;
            }
            if (!free(level, toWorld(controller, facing, cell.x(), cell.y(), cell.z()))) {
                return false;
            }
        }
        return true;
    }

    /** Ce que dit le refus d'une pose. */
    public Component noRoom() {
        return Component.literal("Pas assez de place pour " + label + " : il lui faut " + width + " x " + depth
                + " au sol et " + height + " de haut, libres.").withStyle(ChatFormatting.RED);
    }

    public void tellNoRoom(Player player) {
        if (player instanceof ServerPlayer server) {
            server.sendSystemMessage(noRoom(), true);
        }
    }

    /** Pose les parties autour d'une commande deployee. */
    public void placeParts(Level level, BlockPos controller, Direction facing) {
        Block part = part();
        for (Cell cell : cells) {
            if (isController(cell.x(), cell.y(), cell.z())) {
                continue;
            }
            BlockPos pos = toWorld(controller, facing, cell.x(), cell.y(), cell.z());
            BlockState state = part.defaultBlockState()
                    .setValue(BigMachinePartBlock.FACING, facing)
                    .setValue(BigMachinePartBlock.X, cell.x())
                    .setValue(BigMachinePartBlock.Y, cell.y())
                    .setValue(BigMachinePartBlock.Z, cell.z());
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
    }

    /** Retire les parties d'une commande, sans rien faire tomber. */
    public void removeParts(Level level, BlockPos controller, Direction facing) {
        Block part = part();
        for (Cell cell : cells) {
            if (isController(cell.x(), cell.y(), cell.z())) {
                continue;
            }
            BlockPos pos = toWorld(controller, facing, cell.x(), cell.y(), cell.z());
            BlockState state = level.getBlockState(pos);
            if (state.is(part) && state.getValue(BigMachinePartBlock.FACING) == facing
                    && state.getValue(BigMachinePartBlock.X) == cell.x()
                    && state.getValue(BigMachinePartBlock.Y) == cell.y()
                    && state.getValue(BigMachinePartBlock.Z) == cell.z()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** La boite de collision d'une case, tournee selon facing. */
    public VoxelShape shape(Direction facing, int x, int y, int z) {
        Map<Integer, VoxelShape> byCell = shapes.computeIfAbsent(facing, f -> new java.util.concurrent.ConcurrentHashMap<>());
        int key = (x * 16 + y) * 16 + z;
        return byCell.computeIfAbsent(key, k -> {
            for (Cell cell : cells) {
                if (cell.x() == x && cell.y() == y && cell.z() == z) {
                    return rotated(cell.box(), facing);
                }
            }
            return Shapes.block();
        });
    }

    /** Une boite en pixels du repere de la machine, tournee autour du centre de sa case. */
    private static VoxelShape rotated(double[] b, Direction facing) {
        double x0 = b[0];
        double z0 = b[2];
        double x1 = b[3];
        double z1 = b[5];
        double rx0;
        double rz0;
        double rx1;
        double rz1;
        switch (facing) {
            case EAST -> {
                rx0 = 16 - z1;
                rx1 = 16 - z0;
                rz0 = x0;
                rz1 = x1;
            }
            case SOUTH -> {
                rx0 = 16 - x1;
                rx1 = 16 - x0;
                rz0 = 16 - z1;
                rz1 = 16 - z0;
            }
            case WEST -> {
                rx0 = z0;
                rx1 = z1;
                rz0 = 16 - x1;
                rz1 = 16 - x0;
            }
            default -> {
                rx0 = x0;
                rx1 = x1;
                rz0 = z0;
                rz1 = z1;
            }
        }
        return Block.box(rx0, b[1], rz0, rx1, b[4], rz1);
    }
}

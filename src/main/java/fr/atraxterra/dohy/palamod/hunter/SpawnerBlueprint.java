package fr.atraxterra.dohy.palamod.hunter;

import fr.atraxterra.dohy.palamod.PalaBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Les quatre structures de spawners ({@code First} a
 * {@code FourthSpawnerBluePrint} de PalaMod), recopiees etage par etage.
 *
 * <p>Legende : {@code P} bloc de paladium, {@code A} bloc de paladium vert (le
 * centre), {@code O} obsidienne, {@code S} sable des ames, {@code X G I E} les
 * obsidiennes infusees des paliers I a IV, {@code Z} le controleur. Une case
 * vide n'est pas regardee : c'est la que vont les spawners et les coffres.
 *
 * <p>Tailles : 7 x 7 x 3, 11 x 11 x 3, 13 x 13 x 4 et 17 x 17 x 5 (le wiki
 * donne 13 x 13 x 4 au palier IV ; le plan dit 17). Chaque structure contient
 * la precedente : elles sont essayees de la plus grande a la plus petite,
 * comme les enregistre PalaMod, sans quoi le palier I gagnerait toujours.
 */
public final class SpawnerBlueprint {

    /** Une case du plan, relative au controleur, avant rotation. */
    private record Cell(int dx, int dy, int dz, char symbol) {
    }

    /** Une structure reconnue : son palier, son centre et le cote du controleur. */
    public record Match(SpawnerTier tier, BlockPos center, Direction outward) {
    }

    /** Ce qui manque a une structure : la case, et le bloc qu'elle attend. */
    public record Missing(SpawnerTier tier, BlockPos pos, Block expected, int placed, int total) {
    }

    public static final List<SpawnerBlueprint> ALL = List.of(
            new SpawnerBlueprint(SpawnerTier.FOUR, new String[][] {
                    {
                            "O     O   O     O",
                            "                 ",
                            "  O O   O   O O  ",
                            "      O   O      ",
                            "  O           O  ",
                            "     O SPS O     ",
                            "O  O  OSOSO  O  O",
                            "     SSOOOSS     ",
                            "  O  POOAOOP  O  ",
                            "     SSOOOSS     ",
                            "O  O  OSOSO  O  O",
                            "     O SPS O     ",
                            "  O           O  ",
                            "      O   O      ",
                            "  O O   O   O O  ",
                            "                 ",
                            "O     O   O     O"
                    },
                    {
                            "O     O   O     O",
                            "                 ",
                            "  O O   O   O O  ",
                            "      O   O      ",
                            "  O           O  ",
                            "     X     X     ",
                            "O  O         O  O",
                            "                 ",
                            "  O           O  ",
                            "                 ",
                            "O  O         O  O",
                            "     X  P  X     ",
                            "  O           O  ",
                            "      O   O      ",
                            "  O O   O   O O  ",
                            "                 ",
                            "O     O   O     O"
                    },
                    {
                            "O     O   O     O",
                            "                 ",
                            "  O O   O   O O  ",
                            "      G   G      ",
                            "  O           O  ",
                            "                 ",
                            "O  G         G  O",
                            "                 ",
                            "  O           O  ",
                            "                 ",
                            "O  G         G  O",
                            "        Z        ",
                            "  O           O  ",
                            "      G   G      ",
                            "  O O   O   O O  ",
                            "                 ",
                            "O     O   O     O"
                    },
                    {
                            "O     O   O     O",
                            "                 ",
                            "  I I   E   I I  ",
                            "                 ",
                            "  I           I  ",
                            "                 ",
                            "O               O",
                            "                 ",
                            "  E           E  ",
                            "                 ",
                            "O               O",
                            "                 ",
                            "  I           I  ",
                            "                 ",
                            "  I I   E   I I  ",
                            "                 ",
                            "O     O   O     O"
                    },
                    {
                            "E     E   E     E",
                            "                 ",
                            "                 ",
                            "                 ",
                            "                 ",
                            "                 ",
                            "E               E",
                            "                 ",
                            "                 ",
                            "                 ",
                            "E               E",
                            "                 ",
                            "                 ",
                            "                 ",
                            "                 ",
                            "                 ",
                            "E     E   E     E"
                    }
            }),
            new SpawnerBlueprint(SpawnerTier.THREE, new String[][] {
                    {
                            "O O       O O",
                            "    O   O    ",
                            "O           O",
                            "   O SPS O   ",
                            " O  OSOSO  O ",
                            "   SSOOOSS   ",
                            "   POOAOOP   ",
                            "   SSOOOSS   ",
                            " O  OSOSO  O ",
                            "   O SPS O   ",
                            "O           O",
                            "    O   O    ",
                            "O O       O O"
                    },
                    {
                            "O O       O O",
                            "    O   O    ",
                            "O           O",
                            "   X     X   ",
                            " O         O ",
                            "             ",
                            "             ",
                            "             ",
                            " O         O ",
                            "   X  P  X   ",
                            "O           O",
                            "    O   O    ",
                            "O O       O O"
                    },
                    {
                            "O O       O O",
                            "    G   G    ",
                            "O           O",
                            "             ",
                            " G         G ",
                            "             ",
                            "             ",
                            "             ",
                            " G         G ",
                            "      Z      ",
                            "O           O",
                            "    G   G    ",
                            "O O       O O"
                    },
                    {
                            "I I       I I",
                            "             ",
                            "I           I",
                            "             ",
                            "             ",
                            "             ",
                            "             ",
                            "             ",
                            "             ",
                            "             ",
                            "I           I",
                            "             ",
                            "I I       I I"
                    }
            }),
            new SpawnerBlueprint(SpawnerTier.TWO, new String[][] {
                    {
                            "   O   O   ",
                            "           ",
                            "  O SPS O  ",
                            "O  OSOSO  O",
                            "  SSOOOSS  ",
                            "  POOAOOP  ",
                            "  SSOOOSS  ",
                            "O  OSOSO  O",
                            "  O SPS O  ",
                            "           ",
                            "   O   O   "
                    },
                    {
                            "   O   O   ",
                            "           ",
                            "  X     X  ",
                            "O         O",
                            "           ",
                            "           ",
                            "           ",
                            "O         O",
                            "  X  P  X  ",
                            "           ",
                            "   O   O   "
                    },
                    {
                            "   G   G   ",
                            "           ",
                            "           ",
                            "G         G",
                            "           ",
                            "           ",
                            "           ",
                            "G         G",
                            "     Z     ",
                            "           ",
                            "   G   G   "
                    }
            }),
            new SpawnerBlueprint(SpawnerTier.ONE, new String[][] {
                    {
                            "O SPS O",
                            " OSOSO ",
                            "SSOOOSS",
                            "POOAOOP",
                            "SSOOOSS",
                            " OSOSO ",
                            "O SPS O"
                    },
                    {
                            "X     X",
                            "       ",
                            "       ",
                            "       ",
                            "       ",
                            "       ",
                            "X  P  X"
                    },
                    {
                            "       ",
                            "       ",
                            "       ",
                            "       ",
                            "       ",
                            "       ",
                            "   Z   "
                    }
            }));

    private final SpawnerTier tier;
    private final List<Cell> cells = new ArrayList<>();
    /** Le centre (le bloc de paladium vert), relatif au controleur. */
    private final int centerX;
    private final int centerY;
    private final int centerZ;

    private SpawnerBlueprint(SpawnerTier tier, String[][] floors) {
        this.tier = tier;
        int zx = 0;
        int zy = 0;
        int zz = 0;
        int ax = 0;
        int az = 0;
        for (int floor = 0; floor < floors.length; floor++) {
            for (int row = 0; row < floors[floor].length; row++) {
                String line = floors[floor][row];
                for (int column = 0; column < line.length(); column++) {
                    char symbol = line.charAt(column);
                    if (symbol == 'Z') {
                        zx = column;
                        zy = floor;
                        zz = row;
                    } else if (symbol == 'A') {
                        ax = column;
                        az = row;
                    }
                }
            }
        }
        for (int floor = 0; floor < floors.length; floor++) {
            for (int row = 0; row < floors[floor].length; row++) {
                String line = floors[floor][row];
                for (int column = 0; column < line.length(); column++) {
                    char symbol = line.charAt(column);
                    if (symbol != ' ' && symbol != 'Z') {
                        cells.add(new Cell(column - zx, floor - zy, row - zz, symbol));
                    }
                }
            }
        }
        this.centerX = ax - zx;
        this.centerY = -zy;
        this.centerZ = az - zz;
    }

    public SpawnerTier tier() {
        return tier;
    }

    /** Une case du plan, pour l'hologramme : relative au controleur, avant rotation, avec son bloc. */
    public record PlanCell(int dx, int dy, int dz, Block block) {
    }

    /** Toutes les cases du plan, le controleur compris (en 0, 0, 0). */
    public List<PlanCell> planCells() {
        List<PlanCell> out = new ArrayList<>(cells.size() + 1);
        out.add(new PlanCell(0, 0, 0, PalaHunter.SPAWNER_CONTROLLER.get()));
        for (Cell cell : cells) {
            out.add(new PlanCell(cell.dx(), cell.dy(), cell.dz(), block(cell.symbol())));
        }
        return out;
    }

    public int blockCount() {
        return cells.size();
    }

    private static Block block(char symbol) {
        return switch (symbol) {
            case 'P' -> PalaBlocks.PALADIUM_BLOCK.get();
            case 'A' -> PalaBlocks.GREEN_PALADIUM_BLOCK.get();
            case 'O' -> Blocks.OBSIDIAN;
            case 'S' -> Blocks.SOUL_SAND;
            case 'X' -> PalaHunter.SPAWNER_STRUCTURES.get(0).get();
            case 'G' -> PalaHunter.SPAWNER_STRUCTURES.get(1).get();
            case 'I' -> PalaHunter.SPAWNER_STRUCTURES.get(2).get();
            case 'E' -> PalaHunter.SPAWNER_STRUCTURES.get(3).get();
            default -> Blocks.AIR;
        };
    }

    /** Un quart de tour par cran : le plan est symetrique, quatre sens suffisent. */
    private static BlockPos turn(BlockPos origin, int dx, int dy, int dz, int quarter) {
        return switch (quarter & 3) {
            case 0 -> origin.offset(dx, dy, dz);
            case 1 -> origin.offset(-dz, dy, dx);
            case 2 -> origin.offset(-dx, dy, -dz);
            default -> origin.offset(dz, dy, -dx);
        };
    }

    private static Direction outward(int quarter) {
        return switch (quarter & 3) {
            case 0 -> Direction.SOUTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    /** Les cases en place dans ce sens, sur le total. */
    private int placed(Level level, BlockPos controller, int quarter) {
        int placed = 0;
        for (Cell cell : cells) {
            if (level.getBlockState(turn(controller, cell.dx(), cell.dy(), cell.dz(), quarter))
                    .is(block(cell.symbol()))) {
                placed++;
            }
        }
        return placed;
    }

    private boolean complete(Level level, BlockPos controller, int quarter) {
        for (Cell cell : cells) {
            if (!level.getBlockState(turn(controller, cell.dx(), cell.dy(), cell.dz(), quarter))
                    .is(block(cell.symbol()))) {
                return false;
            }
        }
        return true;
    }

    private Match match(BlockPos controller, int quarter) {
        return new Match(tier, turn(controller, centerX, centerY, centerZ, quarter), outward(quarter));
    }

    /**
     * La plus grande structure complete autour de ce controleur, ou
     * {@code null}. {@code hint} : celle du passage precedent, revue d'abord —
     * une structure qui tient ne coute alors qu'un seul parcours par seconde.
     */
    public static Match find(Level level, BlockPos controller, Match hint) {
        if (hint != null) {
            for (SpawnerBlueprint blueprint : ALL) {
                if (blueprint.tier != hint.tier()) {
                    continue;
                }
                for (int quarter = 0; quarter < 4; quarter++) {
                    if (outward(quarter) == hint.outward() && blueprint.complete(level, controller, quarter)) {
                        // Une structure agrandie depuis : elle n'est gardee que si
                        // aucune plus grande ne tient.
                        Match larger = larger(level, controller, blueprint.tier);
                        return larger != null ? larger : blueprint.match(controller, quarter);
                    }
                }
            }
        }
        for (SpawnerBlueprint blueprint : ALL) {
            for (int quarter = 0; quarter < 4; quarter++) {
                if (blueprint.complete(level, controller, quarter)) {
                    return blueprint.match(controller, quarter);
                }
            }
        }
        return null;
    }

    private static Match larger(Level level, BlockPos controller, SpawnerTier than) {
        for (SpawnerBlueprint blueprint : ALL) {
            if (blueprint.tier.ordinal() <= than.ordinal()) {
                continue;
            }
            for (int quarter = 0; quarter < 4; quarter++) {
                if (blueprint.complete(level, controller, quarter)) {
                    return blueprint.match(controller, quarter);
                }
            }
        }
        return null;
    }

    /**
     * Ce qui manque a la structure la plus avancee : le sens ou le plus de
     * cases sont en place, et dans ce sens le plus petit plan inacheve. Sert a
     * guider le joueur, un plan de 17 x 17 x 5 ne se devinant pas.
     */
    public static Missing missing(Level level, BlockPos controller) {
        SpawnerBlueprint first = ALL.get(ALL.size() - 1);
        int bestQuarter = 0;
        int best = -1;
        for (int quarter = 0; quarter < 4; quarter++) {
            int placed = first.placed(level, controller, quarter);
            if (placed > best) {
                best = placed;
                bestQuarter = quarter;
            }
        }
        for (int index = ALL.size() - 1; index >= 0; index--) {
            SpawnerBlueprint blueprint = ALL.get(index);
            int placed = blueprint.placed(level, controller, bestQuarter);
            if (placed == blueprint.cells.size()) {
                continue;
            }
            // De bas en haut : on batit dans cet ordre.
            Cell lowest = null;
            for (Cell cell : blueprint.cells) {
                if (level.getBlockState(turn(controller, cell.dx(), cell.dy(), cell.dz(), bestQuarter))
                        .is(block(cell.symbol()))) {
                    continue;
                }
                if (lowest == null || cell.dy() < lowest.dy()) {
                    lowest = cell;
                }
            }
            return new Missing(blueprint.tier, turn(controller, lowest.dx(), lowest.dy(), lowest.dz(),
                    bestQuarter), block(lowest.symbol()), placed, blueprint.cells.size());
        }
        return null;
    }
}

package fr.atraxterra.dohy.farmland;

import fr.atraxterra.dohy.farmland.FarmlandMap.Zone;
import fr.atraxterra.dohy.spawn.DoHySpawn;
import fr.atraxterra.dohy.spawn.SpawnTerminalBlock;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;

import static fr.atraxterra.dohy.farmland.FarmlandMap.G;
import static fr.atraxterra.dohy.farmland.FarmlandMap.rand;
import static fr.atraxterra.dohy.farmland.FarmlandMap.top;
import static fr.atraxterra.dohy.farmland.FarmlandMap.zone;

/**
 * Ce qui se pose sur le sol de la farmland : arbres, champignons geants, hameau,
 * grotte, ruines du Nether et de l'End, piliers d'obsidienne, camp d'arrivee.
 * Calcule une fois, bloc par position ; ces blocs recouvrent ceux du sol.
 */
final class FarmlandDecor {

    /** Une etiquette flottante (le bloc hologramme du spawn). */
    record Label(BlockPos pos, String text, int color, float scale) {
    }

    /** Les decors : un bloc par position, leur index par chunk, les etiquettes, le sol de la grotte. */
    static final class Decor {
        final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
        final Long2ObjectOpenHashMap<LongArrayList> byChunk = new Long2ObjectOpenHashMap<>();
        final List<Label> labels = new ArrayList<>();
        final List<BlockPos> caveFloor = new ArrayList<>();
        /** Les rectangles reserves (x0, z0, x1, z1) : rien d'eparpille n'y pousse. */
        private final List<int[]> reserved = new ArrayList<>();

        void put(int x, int y, int z, BlockState state) {
            blocks.put(BlockPos.asLong(x, y, z), state);
        }

        /** Seulement si rien d'autre n'est deja la (les feuilles n'ecrasent pas un tronc). */
        void soft(int x, int y, int z, BlockState state) {
            blocks.putIfAbsent(BlockPos.asLong(x, y, z), state);
        }

        void reserve(int x0, int z0, int x1, int z1) {
            reserved.add(new int[] {Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)});
        }

        boolean free(int x, int z) {
            for (int[] box : reserved) {
                if (x >= box[0] && x <= box[2] && z >= box[1] && z <= box[3]) {
                    return false;
                }
            }
            return true;
        }

        private void index() {
            for (long key : blocks.keySet()) {
                int x = BlockPos.getX(key);
                int z = BlockPos.getZ(key);
                byChunk.computeIfAbsent(chunkKey(x >> 4, z >> 4), k -> new LongArrayList()).add(key);
            }
        }
    }

    static long chunkKey(int cx, int cz) {
        return (long) cz << 32 | cx & 0xFFFFFFFFL;
    }

    private FarmlandDecor() {
    }

    static Decor build() {
        Decor d = new Decor();
        camp(d);
        hamlet(d);
        mushrooms(d);
        cave(d);
        netherRuin(d);
        endCorner(d);
        scatter(d);
        island(d);
        d.index();
        return d;
    }

    // -- Petits outils ----------------------------------------------------------------------------

    private static BlockState log(String name) {
        return B.with(B.of(name), BlockStateProperties.AXIS, Direction.Axis.Y);
    }

    private static BlockState leaves(String name) {
        return B.with(B.of(name), BlockStateProperties.PERSISTENT, true);
    }

    private static BlockState facing(String name, Direction direction) {
        return B.with(B.of(name), BlockStateProperties.HORIZONTAL_FACING, direction);
    }

    private static BlockState stairs(String name, Direction direction) {
        return B.with(facing(name, direction), BlockStateProperties.HALF, Half.BOTTOM);
    }

    private static BooleanProperty side(Direction direction) {
        return switch (direction) {
            case NORTH -> BlockStateProperties.NORTH;
            case SOUTH -> BlockStateProperties.SOUTH;
            case EAST -> BlockStateProperties.EAST;
            case WEST -> BlockStateProperties.WEST;
            default -> BlockStateProperties.UP;
        };
    }

    /** La hauteur du sol ; {@code Integer.MIN_VALUE} hors de la terre ferme (eau, lave, vide). */
    private static int ground(int x, int z) {
        Zone zone = zone(x, z);
        if (zone == Zone.VOID || zone == Zone.LAKE && !FarmlandMap.island(x, z)
                || zone == Zone.SWAMP && FarmlandMap.puddle(x, z) || zone == Zone.NETHER && FarmlandMap.lavaPool(x, z)) {
            return Integer.MIN_VALUE;
        }
        return top(x, z, zone);
    }

    // -- Le camp ----------------------------------------------------------------------------------

    private static void camp(Decor d) {
        d.reserve(-16, -16, 16, 16);
        BlockState post = B.of("spruce_fence");
        BlockState lantern = B.of("lantern");
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0d * i / 8.0d + Math.PI / 8.0d;
            int x = (int) Math.round(Math.cos(angle) * 10.5d);
            int z = (int) Math.round(Math.sin(angle) * 10.5d);
            d.put(x, G + 1, z, post);
            d.put(x, G + 2, z, post);
            d.put(x, G + 3, z, lantern);
        }
        // la borne RETOUR, face a l'arrivee
        d.put(0, G + 1, -5, DoHySpawn.TERMINAL.get().defaultBlockState()
                .setValue(SpawnTerminalBlock.KIND, SpawnTerminalBlock.Kind.RETURN)
                .setValue(SpawnTerminalBlock.FACING, Direction.SOUTH));
        // deux bancs et des tonneaux
        for (int dz = -1; dz <= 1; dz++) {
            d.put(-7, G + 1, dz, stairs("spruce_stairs", Direction.WEST));
            d.put(7, G + 1, dz, stairs("spruce_stairs", Direction.EAST));
        }
        d.put(-3, G + 1, -6, B.of("barrel"));
        d.put(3, G + 1, -6, B.of("barrel"));
        d.labels.add(new Label(new BlockPos(0, G + 6, 0), "FARMLAND", 0xFAC400, 3.0f));
        d.labels.add(new Label(new BlockPos(0, G + 4, 0), "Les creatures du Chasseur", 0xFFFFFF, 1.2f));
        d.labels.add(new Label(new BlockPos(0, G + 3, -5), "Retour au spawn", 0xFF5A4A, 1.0f));
        d.labels.add(new Label(new BlockPos(-8, G + 3, -8), "LA FORET · monstres", 0x22E558, 1.1f));
        d.labels.add(new Label(new BlockPos(8, G + 3, -8), "LA PRAIRIE · betail, hameau", 0xFAC400, 1.1f));
        d.labels.add(new Label(new BlockPos(12, G + 3, 0), "LES COLLINES · grotte", 0xB8BCC6, 1.1f));
        d.labels.add(new Label(new BlockPos(8, G + 3, 8), "LA JUNGLE · coin de l'End", 0x9B6BFF, 1.1f));
        d.labels.add(new Label(new BlockPos(0, G + 3, 12), "LE LAC · plage", 0x4FB8FF, 1.1f));
        d.labels.add(new Label(new BlockPos(-8, G + 3, 6), "LE MARAIS · coin du Nether", 0xEF3826, 1.1f));
    }

    // -- Le hameau --------------------------------------------------------------------------------

    private static void hamlet(Decor d) {
        d.reserve(32, -56, 60, -28);
        house(d, 36, -52, 7, 6, Direction.SOUTH);
        house(d, 50, -50, 7, 6, Direction.WEST);
        house(d, 41, -36, 7, 6, Direction.NORTH);
        // le puits
        int floor = G + 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = 46 + dx;
                int z = -42 + dz;
                if (dx == 0 && dz == 0) {
                    d.put(x, floor, z, B.of("water"));
                    d.put(x, floor - 1, z, B.of("water"));
                    continue;
                }
                d.put(x, floor, z, B.of("cobblestone"));
                d.put(x, floor + 1, z, B.of("cobblestone_wall"));
                if (dx != 0 && dz != 0) {
                    d.put(x, floor + 2, z, B.of("oak_fence"));
                    d.put(x, floor + 3, z, B.of("oak_fence"));
                }
                d.put(x, floor + 4, z, B.with(B.of("oak_slab"), BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
            }
        }
        d.put(46, floor + 4, -42, B.with(B.of("oak_slab"), BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
        // du foin, un composteur, des tonneaux
        d.put(44, floor + 1, -46, B.of("hay_block"));
        d.put(45, floor + 1, -46, B.of("hay_block"));
        d.put(44, floor + 2, -46, B.of("hay_block"));
        d.put(49, floor + 1, -38, B.of("composter"));
        d.put(48, floor + 1, -45, B.of("barrel"));
        // l'enclos des chevaux, dans la prairie
        paddock(d, 38, -62, 8, 5);
    }

    /**
     * Une maison : sol de planches, murs de rondins et de planches sur une assise de pierre,
     * fenetres, porte, toit a deux pans d'escaliers d'epicea.
     */
    private static void house(Decor d, int x0, int z0, int w, int depth, Direction door) {
        int floor = G + 1;
        BlockState planks = B.of("oak_planks");
        BlockState corner = log("oak_log");
        BlockState cobble = B.of("cobblestone");
        BlockState pane = B.of("glass_pane");
        BlockState air = B.of("air");
        int x1 = x0 + w - 1;
        int z1 = z0 + depth - 1;
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                for (int y = floor + 1; y <= floor + 8; y++) {
                    d.put(x, y, z, air);
                }
                if (x >= x0 && x <= x1 && z >= z0 && z <= z1) {
                    d.put(x, floor, z, planks);
                }
            }
        }
        int doorX = door == Direction.NORTH || door == Direction.SOUTH ? x0 + w / 2 : door == Direction.WEST ? x0 : x1;
        int doorZ = door == Direction.EAST || door == Direction.WEST ? z0 + depth / 2 : door == Direction.NORTH ? z0 : z1;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edgeX = x == x0 || x == x1;
                boolean edgeZ = z == z0 || z == z1;
                if (!edgeX && !edgeZ) {
                    continue;
                }
                boolean isCorner = edgeX && edgeZ;
                for (int y = floor + 1; y <= floor + 3; y++) {
                    BlockState state = isCorner ? corner : y == floor + 1 ? cobble : planks;
                    // une fenetre sur deux blocs, au milieu de chaque mur
                    boolean window = !isCorner && y == floor + 2 && (edgeZ && (x - x0) % 2 == 1
                            || edgeX && (z - z0) % 3 == 2);
                    d.put(x, y, z, window ? pane : state);
                }
            }
        }
        d.put(doorX, floor + 1, doorZ, B.with(facing("oak_door", door), BlockStateProperties.DOUBLE_BLOCK_HALF,
                DoubleBlockHalf.LOWER));
        d.put(doorX, floor + 2, doorZ, B.with(facing("oak_door", door), BlockStateProperties.DOUBLE_BLOCK_HALF,
                DoubleBlockHalf.UPPER));
        // le toit, faitage selon x : deux pans d'escaliers qui montent vers le milieu
        for (int k = 0; ; k++) {
            int north = z0 - 1 + k;
            int south = z1 + 1 - k;
            if (north > south) {
                break;
            }
            int y = floor + 4 + k;
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                if (north == south) {
                    d.put(x, y, north, B.with(B.of("spruce_slab"), BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
                    continue;
                }
                d.put(x, y, north, stairs("spruce_stairs", Direction.SOUTH));
                d.put(x, y, south, stairs("spruce_stairs", Direction.NORTH));
            }
            // les pignons
            for (int z = north + 1; z < south; z++) {
                if (z >= z0 && z <= z1) {
                    d.put(x0, y, z, planks);
                    d.put(x1, y, z, planks);
                }
            }
        }
        // dedans : une lanterne, un tonneau, un etabli
        d.put(x0 + 1, floor + 1, z0 + 1, B.of("lantern"));
        d.put(x1 - 1, floor + 1, z0 + 1, B.of("barrel"));
        d.put(x1 - 1, floor + 1, z1 - 1, B.of("crafting_table"));
    }

    private static void paddock(Decor d, int cx, int cz, int rx, int rz) {
        d.reserve(cx - rx - 1, cz - rz - 1, cx + rx + 1, cz + rz + 1);
        BlockState fence = B.of("oak_fence");
        long gate = Long.MIN_VALUE;
        for (int step = 0; step < 360; step += 2) {
            double angle = Math.toRadians(step);
            int x = (int) Math.round(cx + rx * Math.cos(angle));
            int z = (int) Math.round(cz + rz * Math.sin(angle));
            int y = top(x, z) + 1;
            if (step == 90) {
                gate = BlockPos.asLong(x, y, z);
                d.put(x, y, z, facing("oak_fence_gate", Direction.SOUTH));
            } else if (BlockPos.asLong(x, y, z) != gate) {
                d.soft(x, y, z, fence);
            }
        }
    }

    // -- Les champignons --------------------------------------------------------------------------

    private static void mushrooms(Decor d) {
        d.reserve(10, -84, 30, -64);
        hugeMushroom(d, true, 16, top(16, -78) + 1, -78, 5);
        hugeMushroom(d, false, 24, top(24, -71) + 1, -71, 5);
        hugeMushroom(d, true, 25, top(25, -80) + 1, -80, 4);
    }

    private static void hugeMushroom(Decor d, boolean red, int x, int y, int z, int height) {
        BlockState stem = B.of("mushroom_stem");
        for (int dy = 0; dy < height; dy++) {
            d.put(x, y + dy, z, stem);
        }
        int top = y + height;
        if (red) {
            BlockState cap = B.of("red_mushroom_block");
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    if (!corner) {
                        d.put(x + dx, top, z + dz, cap);
                    }
                    boolean ring = (Math.abs(dx) == 2 || Math.abs(dz) == 2) && !corner;
                    if (ring) {
                        for (int dy = 1; dy <= 3; dy++) {
                            d.put(x + dx, top - dy, z + dz, cap);
                        }
                    }
                }
            }
        } else {
            BlockState cap = B.of("brown_mushroom_block");
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.abs(dx) == 3 && Math.abs(dz) == 3) {
                        continue;
                    }
                    d.put(x + dx, top, z + dz, cap);
                }
            }
        }
    }

    // -- La grotte des collines -------------------------------------------------------------------

    /** Un tunnel ouvert dans le flanc ouest de la plus haute colline, puis une salle sous son sommet. */
    private static void cave(Decor d) {
        d.reserve(36, 0, 62, 12);
        BlockState air = B.of("air");
        for (int x = 41; x <= 50; x++) {
            for (int z = 5; z <= 7; z++) {
                if (top(x, z) < G + 4) {
                    continue;
                }
                for (int y = G + 1; y <= G + 3; y++) {
                    d.put(x, y, z, air);
                }
                if (z == 6 && x >= 44) {
                    d.caveFloor.add(new BlockPos(x, G + 1, z));
                }
            }
        }
        int cx = 55;
        int cz = 6;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double e = (dx / 6.0d) * (dx / 6.0d) + (dz / 5.0d) * (dz / 5.0d);
                if (e > 1.0d) {
                    continue;
                }
                int height = Math.max(2, (int) Math.round(3.6d * Math.sqrt(1.0d - e)));
                for (int y = G + 1; y <= G + height; y++) {
                    d.put(cx + dx, y, cz + dz, air);
                }
                d.put(cx + dx, G, cz + dz, rand(cx + dx, cz + dz, 50) < 0.6d ? B.of("mossy_cobblestone")
                        : B.of("cobblestone"));
                if (e < 0.7d) {
                    d.caveFloor.add(new BlockPos(cx + dx, G + 1, cz + dz));
                }
                if (e > 0.75d && rand(cx + dx, cz + dz, 51) < 0.35d) {
                    d.put(cx + dx, G + Math.max(1, height - 1), cz + dz, B.of("cobweb"));
                }
            }
        }
        d.put(52, G + 1, 4, B.of("lantern"));
        d.put(58, G + 1, 8, B.of("lantern"));
        d.labels.add(new Label(new BlockPos(40, G + 7, 6), "LA GROTTE", 0xB8BCC6, 1.4f));
    }

    // -- Le coin du Nether ------------------------------------------------------------------------

    /** Un morceau de forteresse : murs ebreches, fenetres grillagees, un carre de verrues. */
    private static void netherRuin(Decor d) {
        int x0 = -54;
        int z0 = 48;
        int w = 11;
        int depth = 9;
        d.reserve(x0 - 2, z0 - 2, x0 + w + 1, z0 + depth + 1);
        int floor = G + 3;
        BlockState bricks = B.of("nether_bricks");
        BlockState cracked = B.of("cracked_nether_bricks");
        BlockState chiseled = B.of("chiseled_nether_bricks");
        BlockState fence = B.of("nether_brick_fence");
        BlockState air = B.of("air");
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + depth; z++) {
                for (int y = G + 1; y < floor; y++) {
                    d.put(x, y, z, bricks);
                }
                d.put(x, floor, z, rand(x, z, 52) < 0.2d ? cracked : bricks);
                for (int y = floor + 1; y <= floor + 7; y++) {
                    d.put(x, y, z, air);
                }
                boolean edgeX = x == x0 || x == x0 + w - 1;
                boolean edgeZ = z == z0 || z == z0 + depth - 1;
                if (!edgeX && !edgeZ) {
                    continue;
                }
                if (edgeX && edgeZ) {
                    for (int y = floor + 1; y <= floor + 5; y++) {
                        d.put(x, y, z, chiseled);
                    }
                    continue;
                }
                if (rand(x, z, 53) < 0.12d) {
                    continue;
                }
                int height = 2 + (int) (rand(x, z, 54) * 4.0d);
                for (int y = floor + 1; y <= floor + height; y++) {
                    boolean window = y == floor + 2 && height >= 3 && ((edgeZ ? x : z) % 3 == 0);
                    d.put(x, y, z, window ? fence : rand(x, y + z, 55) < 0.25d ? cracked : bricks);
                }
            }
        }
        // l'entree, a l'ouest, au bout du chemin
        for (int y = floor + 1; y <= floor + 3; y++) {
            for (int dz = 3; dz <= 5; dz++) {
                d.put(x0, y, z0 + dz, air);
            }
        }
        // le carre de verrues sur sable des ames, deux lanternes des ames
        BlockState wart = B.with(B.of("nether_wart"), BlockStateProperties.AGE_3, 3);
        for (int x = x0 + 6; x <= x0 + 8; x++) {
            for (int z = z0 + 2; z <= z0 + 3; z++) {
                d.put(x, floor, z, B.of("soul_sand"));
                d.put(x, floor + 1, z, wart);
            }
        }
        for (int[] at : new int[][] {{x0 + 1, z0 + 1}, {x0 + w - 2, z0 + depth - 2}}) {
            d.put(at[0], floor + 1, at[1], fence);
            d.put(at[0], floor + 2, at[1], B.of("soul_lantern"));
        }
        d.labels.add(new Label(new BlockPos(-58, G + 8, 44), "LE COIN DU NETHER", 0xEF3826, 1.4f));
    }

    // -- Le coin de l'End -------------------------------------------------------------------------

    private static final int[][] PILLARS = {{48, 60, 12}, {54, 70, 16}, {66, 46, 18}, {46, 52, 14}, {70, 54, 20}};

    private static void endCorner(Decor d) {
        for (int[] pillar : PILLARS) {
            d.reserve(pillar[0] - 4, pillar[1] - 4, pillar[0] + 4, pillar[1] + 4);
            obsidianPillar(d, pillar[0], pillar[1], pillar[2]);
        }
        // la ruine de purpur
        int x0 = 58;
        int z0 = 50;
        d.reserve(x0 - 2, z0 - 2, x0 + 8, z0 + 8);
        int floor = G + 5;
        BlockState purpur = B.of("purpur_block");
        BlockState pillar = B.with(B.of("purpur_pillar"), BlockStateProperties.AXIS, Direction.Axis.Y);
        BlockState bricks = B.of("end_stone_bricks");
        BlockState air = B.of("air");
        for (int x = x0; x < x0 + 7; x++) {
            for (int z = z0; z < z0 + 7; z++) {
                for (int y = G + 3; y < floor; y++) {
                    d.put(x, y, z, bricks);
                }
                d.put(x, floor, z, (x + z & 1) == 0 ? purpur : bricks);
                for (int y = floor + 1; y <= floor + 7; y++) {
                    d.put(x, y, z, air);
                }
                boolean edgeX = x == x0 || x == x0 + 6;
                boolean edgeZ = z == z0 || z == z0 + 6;
                if (edgeX && edgeZ) {
                    int height = x == x0 && z == z0 ? 4 : 6;
                    for (int y = floor + 1; y <= floor + height; y++) {
                        d.put(x, y, z, pillar);
                    }
                    d.put(x, floor + height + 1, z, B.of("end_rod"));
                } else if ((edgeX || edgeZ) && rand(x, z, 56) > 0.2d && !(z == z0 + 3 && x == x0)) {
                    int height = 1 + (int) (rand(x, z, 57) * 3.0d);
                    for (int y = floor + 1; y <= floor + height; y++) {
                        d.put(x, y, z, purpur);
                    }
                }
            }
        }
        d.put(x0 + 3, floor + 1, z0 + 3, pillar);
        d.put(x0 + 3, floor + 2, z0 + 3, B.of("dragon_head"));
        d.labels.add(new Label(new BlockPos(50, G + 10, 50), "LE COIN DE L'END", 0x9B6BFF, 1.4f));
    }

    /** Un pilier d'obsidienne, un peu de larmes, de la bedrock et un baton de l'End au sommet. */
    private static void obsidianPillar(Decor d, int x, int z, int height) {
        int base = Math.max(G + 3, top(x, z));
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5) {
                    continue;
                }
                int ground = Math.min(base, top(x + dx, z + dz));
                for (int y = ground; y <= base + height; y++) {
                    d.put(x + dx, y, z + dz, rand(x + dx, y, z + dz) < 0.1d ? B.of("crying_obsidian")
                            : B.of("obsidian"));
                }
            }
        }
        d.put(x, base + height + 1, z, B.of("bedrock"));
        d.put(x, base + height + 2, z, B.of("end_rod"));
    }

    // -- Ce qui s'eparpille -----------------------------------------------------------------------

    private static void scatter(Decor d) {
        int edge = FarmlandMap.PLAY;
        for (int cx = -edge; cx <= edge; cx += 5) {
            for (int cz = -edge; cz <= edge; cz += 5) {
                cell(d, cx, cz, 5, Zone.FOREST, 0.8d);
            }
        }
        for (int cx = -edge; cx <= edge; cx += 16) {
            for (int cz = -edge; cz <= edge; cz += 16) {
                cell(d, cx, cz, 16, Zone.MEADOW, 0.35d);
            }
        }
        for (int cx = -edge; cx <= edge; cx += 9) {
            for (int cz = -edge; cz <= edge; cz += 9) {
                cell(d, cx, cz, 9, Zone.HILLS, 0.4d);
                cell(d, cx, cz, 9, Zone.SWAMP, 0.5d);
                cell(d, cx, cz, 9, Zone.NETHER, 0.6d);
            }
        }
        for (int cx = -edge; cx <= edge; cx += 8) {
            for (int cz = -edge; cz <= edge; cz += 8) {
                cell(d, cx, cz, 8, Zone.JUNGLE, 0.6d);
            }
        }
        for (int cx = -edge; cx <= edge; cx += 11) {
            for (int cz = -edge; cz <= edge; cz += 11) {
                pillarCell(d, cx, cz);
            }
        }
        for (int cx = -edge; cx <= edge; cx += 6) {
            for (int cz = -edge; cz <= edge; cz += 6) {
                cell(d, cx, cz, 6, Zone.END, 0.45d);
            }
        }
    }

    /** Une case de la grille : peut-etre un arbre (ou ce qui en tient lieu) quelque part dedans. */
    private static void cell(Decor d, int cx, int cz, int size, Zone wanted, double chance) {
        if (rand(cx, cz, 60 + wanted.ordinal()) >= chance) {
            return;
        }
        int x = cx + (int) (rand(cx, cz, 61) * (size - 1));
        int z = cz + (int) (rand(cx, cz, 62) * (size - 1));
        if (zone(x, z) != wanted || !d.free(x, z) || FarmlandMap.onPath(x, z) || FarmlandMap.onPath(x + 2, z)
                || FarmlandMap.onPath(x - 2, z) || FarmlandMap.onPath(x, z + 2) || FarmlandMap.onPath(x, z - 2)) {
            return;
        }
        int ground = ground(x, z);
        if (ground == Integer.MIN_VALUE) {
            return;
        }
        int y = ground + 1;
        double kind = rand(x, z, 63);
        switch (wanted) {
            case FOREST -> {
                double grove = FarmlandMap.noise(x / 30.0d, z / 30.0d, 40);
                if (grove > 0.35d) {
                    darkOak(d, x, z);
                } else if (grove < -0.25d || kind < 0.2d) {
                    tree(d, x, y, z, 5 + (int) (kind * 3.0d), "birch_log", "birch_leaves");
                } else {
                    tree(d, x, y, z, 4 + (int) (kind * 3.0d), "oak_log", "oak_leaves");
                }
            }
            case MEADOW -> tree(d, x, y, z, 5, "oak_log", "oak_leaves");
            case HILLS -> {
                if (ground < G + 10 && FarmlandMap.slope(x, z, ground) < 2) {
                    spruce(d, x, y, z, 7 + (int) (kind * 3.0d));
                }
            }
            case SWAMP -> swampOak(d, x, y, z, 4 + (int) (kind * 2.0d));
            case JUNGLE -> {
                if (FarmlandMap.noise(x / 10.0d, z / 10.0d, 31) <= 0.15d) {
                    jungle(d, x, y, z, 7 + (int) (kind * 5.0d));
                }
            }
            case NETHER -> {
                BlockState below = topBlock(x, z);
                if (below == B.of("crimson_nylium")) {
                    fungus(d, x, y, z, true, 5 + (int) (kind * 5.0d));
                } else if (below == B.of("warped_nylium")) {
                    fungus(d, x, y, z, false, 5 + (int) (kind * 5.0d));
                }
            }
            case END -> chorus(d, x, y, z);
            default -> {
            }
        }
    }

    /** Les piliers de basalte du Nether, sur le sable des ames, la pierre noire ou la netherrack. */
    private static void pillarCell(Decor d, int cx, int cz) {
        if (rand(cx, cz, 64) >= 0.45d) {
            return;
        }
        int x = cx + (int) (rand(cx, cz, 65) * 10.0d);
        int z = cz + (int) (rand(cx, cz, 66) * 10.0d);
        if (zone(x, z) != Zone.NETHER || !d.free(x, z) || FarmlandMap.onPath(x, z)) {
            return;
        }
        int ground = ground(x, z);
        BlockState below = topBlock(x, z);
        if (ground == Integer.MIN_VALUE || below == B.of("crimson_nylium") || below == B.of("warped_nylium")) {
            return;
        }
        int height = 6 + (int) (rand(x, z, 67) * 8.0d);
        BlockState basalt = B.with(B.of("basalt"), BlockStateProperties.AXIS, Direction.Axis.Y);
        BlockState polished = B.with(B.of("polished_basalt"), BlockStateProperties.AXIS, Direction.Axis.Y);
        for (int dy = 1; dy <= height; dy++) {
            d.put(x, ground + dy, z, dy % 4 == 0 ? polished : basalt);
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int sx = x + direction.getStepX();
            int sz = z + direction.getStepZ();
            int side = height - 2 - (int) (rand(sx, sz, 68) * 4.0d);
            int sideGround = ground(sx, sz);
            if (sideGround == Integer.MIN_VALUE) {
                continue;
            }
            for (int y = sideGround + 1; y <= ground + side; y++) {
                d.put(sx, y, sz, basalt);
            }
        }
        if (rand(x, z, 69) < 0.45d) {
            for (int i = 0; i < 4; i++) {
                int gx = x + (int) Math.round(rand(x, z, 70 + i) * 2.0d - 1.0d);
                int gz = z + (int) Math.round(rand(x, z, 75 + i) * 2.0d - 1.0d);
                d.put(gx, ground + height + 1 + (i == 0 ? 1 : 0), gz, B.of("glowstone"));
            }
        }
    }

    /** Le bloc du dessus tel que le sol le pose (pour choisir ce qui y pousse). */
    private static BlockState topBlock(int x, int z) {
        BlockState[] found = {B.of("air")};
        int top = top(x, z);
        FarmlandMap.column(x, z, (bx, by, bz, state) -> {
            if (by == top) {
                found[0] = state;
            }
        });
        return found[0];
    }

    private static void island(Decor d) {
        int x = FarmlandMap.ISLAND_C[0];
        int z = FarmlandMap.ISLAND_C[1];
        tree(d, x, top(x, z) + 1, z, 5, "oak_log", "oak_leaves");
    }

    // -- Les arbres -------------------------------------------------------------------------------

    /** Un chene ou un bouleau : tronc droit, deux couches larges, deux etroites. */
    static void tree(Decor d, int x, int y, int z, int height, String logName, String leafName) {
        BlockState trunk = log(logName);
        BlockState leaf = leaves(leafName);
        for (int dy = 0; dy < height; dy++) {
            d.put(x, y + dy, z, trunk);
        }
        int top = y + height - 1;
        for (int dy = -2; dy <= 1; dy++) {
            int radius = dy <= -1 ? 2 : 1;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    boolean corner = Math.abs(dx) == radius && Math.abs(dz) == radius;
                    if (corner && (dy == 1 || rand(x + dx, z + dz, top + dy) < 0.5d)) {
                        continue;
                    }
                    d.soft(x + dx, top + dy, z + dz, leaf);
                }
            }
        }
    }

    /** Un chene noir : tronc de deux sur deux, large couronne. */
    private static void darkOak(Decor d, int x, int z) {
        int y = Integer.MIN_VALUE;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                int ground = ground(x + dx, z + dz);
                if (ground == Integer.MIN_VALUE) {
                    return;
                }
                y = Math.max(y, ground + 1);
            }
        }
        int height = 6 + (int) (rand(x, z, 80) * 3.0d);
        BlockState trunk = log("dark_oak_log");
        BlockState leaf = leaves("dark_oak_leaves");
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                int ground = ground(x + dx, z + dz);
                for (int yy = ground + 1; yy < y + height; yy++) {
                    d.put(x + dx, yy, z + dz, trunk);
                }
            }
        }
        int top = y + height - 1;
        for (int dy = -1; dy <= 1; dy++) {
            int radius = dy == 1 ? 2 : 3;
            for (int dx = -radius; dx <= radius + 1; dx++) {
                for (int dz = -radius; dz <= radius + 1; dz++) {
                    double cx = dx - 0.5d;
                    double cz = dz - 0.5d;
                    if (cx * cx + cz * cz > (radius + 0.5d) * (radius + 0.5d)) {
                        continue;
                    }
                    d.soft(x + dx, top + dy, z + dz, leaf);
                }
            }
        }
    }

    /** Un epicea : un cone de couches alternees. */
    private static void spruce(Decor d, int x, int y, int z, int height) {
        BlockState trunk = log("spruce_log");
        BlockState leaf = leaves("spruce_leaves");
        for (int dy = 0; dy < height; dy++) {
            d.put(x, y + dy, z, trunk);
        }
        int top = y + height - 1;
        for (int dy = 2; dy <= height; dy++) {
            int fromTop = height - dy;
            int radius = fromTop == 0 ? 0 : fromTop % 2 == 1 ? 1 : Math.min(3, 1 + fromTop / 3);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 1 && Math.abs(dx) == radius && Math.abs(dz) == radius) {
                        continue;
                    }
                    d.soft(x + dx, y + dy, z + dz, leaf);
                }
            }
        }
        d.soft(x, top + 1, z, leaf);
    }

    /** Un arbre de la jungle : haut tronc, couronne ronde, lianes sur les bords. */
    private static void jungle(Decor d, int x, int y, int z, int height) {
        BlockState trunk = log("jungle_log");
        BlockState leaf = leaves("jungle_leaves");
        for (int dy = 0; dy < height; dy++) {
            d.put(x, y + dy, z, trunk);
        }
        int top = y + height - 1;
        for (int dy = -1; dy <= 1; dy++) {
            int radius = dy == 1 ? 1 : 3;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius + 1) {
                        continue;
                    }
                    d.soft(x + dx, top + dy, z + dz, leaf);
                }
            }
        }
        vines(d, x, top - 1, z, 3, 3, "vine");
    }

    /** Un chene du marais : tronc court, couronne plate et large, lianes. */
    private static void swampOak(Decor d, int x, int y, int z, int height) {
        BlockState trunk = log("oak_log");
        BlockState leaf = leaves("oak_leaves");
        for (int dy = 0; dy < height; dy++) {
            d.put(x, y + dy, z, trunk);
        }
        int top = y + height - 1;
        for (int dy = -1; dy <= 1; dy++) {
            int radius = dy == 1 ? 1 : dy == 0 ? 2 : 3;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 1 && Math.abs(dx) == radius && Math.abs(dz) == radius) {
                        continue;
                    }
                    d.soft(x + dx, top + dy, z + dz, leaf);
                }
            }
        }
        vines(d, x, top - 1, z, 3, 4, "vine");
    }

    /** Des lianes qui pendent contre les feuilles du bord d'une couronne de rayon {@code radius}. */
    private static void vines(Decor d, int x, int y, int z, int radius, int maxLength, String name) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int offset = -1; offset <= 1; offset++) {
                if (rand(x + offset, z + direction.ordinal(), y + 81) < 0.45d) {
                    continue;
                }
                int vx = x + direction.getStepX() * (radius + 1) + (direction.getStepX() == 0 ? offset : 0);
                int vz = z + direction.getStepZ() * (radius + 1) + (direction.getStepZ() == 0 ? offset : 0);
                BlockState vine = B.with(B.of(name), side(direction.getOpposite()), true);
                int length = 1 + (int) (rand(vx, vz, 82) * maxLength);
                int floor = ground(vx, vz);
                for (int dy = 0; dy < length && y - dy > floor + 1; dy++) {
                    d.soft(vx, y - dy, vz, vine);
                }
            }
        }
    }

    /** Un champignon geant du Nether : pied, chapeau de verrues, quelques champilumes, des lianes. */
    private static void fungus(Decor d, int x, int y, int z, boolean crimson, int height) {
        BlockState stem = log(crimson ? "crimson_stem" : "warped_stem");
        BlockState wart = B.of(crimson ? "nether_wart_block" : "warped_wart_block");
        BlockState light = B.of("shroomlight");
        for (int dy = 0; dy < height; dy++) {
            d.put(x, y + dy, z, stem);
        }
        int top = y + height - 1;
        for (int dy = -2; dy <= 1; dy++) {
            int radius = dy == 1 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    boolean shell = Math.abs(dx) == radius || Math.abs(dz) == radius || dy == 1;
                    if (!shell || Math.abs(dx) == 2 && Math.abs(dz) == 2 && dy < 0) {
                        continue;
                    }
                    double r = rand(x + dx, z + dz, top + dy + 83);
                    d.soft(x + dx, top + dy, z + dz, r < 0.1d ? light : wart);
                }
            }
        }
        if (crimson) {
            BlockState plant = B.of("weeping_vines_plant");
            BlockState tip = B.with(B.of("weeping_vines"), BlockStateProperties.AGE_25, 25);
            for (int i = 0; i < 4; i++) {
                int vx = x + (int) Math.round(rand(x, z, 84 + i) * 4.0d - 2.0d);
                int vz = z + (int) Math.round(rand(x, z, 88 + i) * 4.0d - 2.0d);
                if (vx == x && vz == z) {
                    continue;
                }
                int length = 1 + (int) (rand(vx, vz, 92) * 3.0d);
                int floor = ground(vx, vz);
                while (length > 0 && top - 2 - length <= floor + 1) {
                    length--;
                }
                for (int dy = 1; dy <= length; dy++) {
                    d.soft(vx, top - 2 - dy, vz, dy == length ? tip : plant);
                }
            }
        }
    }

    /** Une plante de chorus fanee (elle ne pousse plus) : une tige, deux ou trois branches fleuries. */
    private static void chorus(Decor d, int x, int y, int z) {
        BlockState plant = B.of("chorus_plant");
        BlockState flower = B.with(B.of("chorus_flower"), BlockStateProperties.AGE_5, 5);
        int stalk = 2 + (int) (rand(x, z, 93) * 3.0d);
        for (int dy = 0; dy < stalk; dy++) {
            d.put(x, y + dy, z, plant);
        }
        d.put(x, y + stalk, z, flower);
        int branches = 1 + (int) (rand(x, z, 94) * 3.0d);
        for (int i = 0; i < branches; i++) {
            Direction direction = Direction.from2DDataValue((int) (rand(x, z, 95 + i) * 4.0d) & 3);
            int by = y + 1 + (int) (rand(x, z, 98 + i) * Math.max(1, stalk - 1));
            int bx = x + direction.getStepX();
            int bz = z + direction.getStepZ();
            d.put(bx, by, bz, plant);
            d.put(bx, by + 1, bz, plant);
            d.put(bx, by + 2, bz, flower);
        }
    }
}

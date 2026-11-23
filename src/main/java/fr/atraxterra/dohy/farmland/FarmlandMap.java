package fr.atraxterra.dohy.farmland;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * La carte de la farmland (lot 24e), colonne par colonne : ses coins, la hauteur
 * et la matiere du sol, l'eau, la lave et les petites plantes. Tout est
 * deterministe : la meme colonne donne toujours les memes blocs, d'une
 * construction a l'autre.
 *
 * <p>Le plan, nord en haut (z negatif) :
 * <pre>
 *            FORET      |   PRAIRIE, HAMEAU, CHAMPIGNONS
 *   ------------------ CAMP ------------------
 *    MARAIS, LAC, PLAGE |   COLLINES (grotte), JUNGLE
 *   coin du Nether      |                 coin de l'End
 * </pre>
 * Une ceinture de falaises ferme le tout ; au-dela, le vide.
 */
public final class FarmlandMap {

    /** Le sol des parties plates : on s'y tient en {@code G + 1}. */
    public static final int G = 64;
    /** Le camp d'arrivee, au centre. */
    public static final int HUB_R = 13;
    /** Ou commencent les falaises de la ceinture. */
    public static final int PLAY = 92;
    /** Le bord exterieur des falaises : au-dela, le vide. */
    public static final int RIM = 104;
    /** Le plus bas d'une colonne ordinaire : la bedrock. */
    static final int BOTTOM = G - 6;

    static final double CORNER_R = 30.0d;
    static final int[] NETHER_C = {-62, 62};
    static final int[] END_C = {62, 62};
    static final int[] HAMLET_C = {46, -42};
    static final int[] MUSHROOM_C = {20, -74};
    static final int[] LAKE_C = {-26, 30};
    static final double LAKE_R = 25.0d;
    /** L'ilot du lac. */
    static final int[] ISLAND_C = {-30, 33};
    static final double ISLAND_R = 4.5d;
    /** Les sommets des collines : x, z, hauteur, rayon. */
    static final int[][] PEAKS = {{54, 6, 15, 20}, {74, 24, 12, 16}, {38, 14, 7, 11}};

    /** Les chemins depuis le camp : des suites de points. */
    static final int[][][] PATHS = {
            {{-8, -8}, {-30, -30}, {-50, -52}},
            {{8, -8}, {26, -26}, {44, -38}},
            {{26, -26}, {22, -62}},
            {{12, 0}, {41, 5}},
            {{8, 8}, {24, 26}, {44, 44}, {52, 52}},
            {{0, 12}, {0, 18}},
            {{-8, 6}, {-14, 2}, {-50, 0}, {-58, 20}, {-58, 46}}};

    /** Les coins de la carte, ce qu'on y voit et ce qui y apparait. */
    public enum Zone {
        HUB("Le camp", "L'arrivee : rien n'y apparait"),
        FOREST("La foret", "Zombies, squelettes, araignees, creepers, sorcieres ; loups et lapins"),
        MEADOW("La prairie", "Vaches, moutons, poules, chevaux, anes, lapins"),
        HAMLET("Le hameau", "Villageois"),
        MUSHROOM("Les champignons", "Champimeuh"),
        HILLS("Les collines", "Chevres ; araignees venimeuses dans la grotte"),
        JUNGLE("La jungle", "Pandas, perroquets, elephants"),
        LAKE("Le lac", "Dauphins"),
        BEACH("La plage", "Tortues"),
        SWAMP("Le marais", "Slimes, sorcieres"),
        NETHER("Le coin du Nether", "Piglins zombifies, cubes de magma, squelettes wither, ghasts"),
        END("Le coin de l'End", "Endermen"),
        RIM("Les falaises", ""),
        VOID("Le vide", "");

        private final String label;
        private final String creatures;

        Zone(String label, String creatures) {
            this.label = label;
            this.creatures = creatures;
        }

        public String label() {
            return label;
        }

        public String creatures() {
            return creatures;
        }
    }

    private FarmlandMap() {
    }

    // -- Les coins --------------------------------------------------------------------------------

    public static double radius(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }

    /** Le coin d'une colonne ; les bords se melent un peu (un bruit les deforme). */
    public static Zone zone(int x, int z) {
        double r = radius(x, z);
        if (r >= RIM) {
            return Zone.VOID;
        }
        if (r >= PLAY) {
            return Zone.RIM;
        }
        if (r < HUB_R) {
            return Zone.HUB;
        }
        double wx = x + 7.0d * noise(x / 21.0d, z / 21.0d, 3);
        double wz = z + 7.0d * noise(x / 21.0d, z / 21.0d, 4);
        if (distance(wx, wz, NETHER_C) < CORNER_R) {
            return Zone.NETHER;
        }
        if (distance(wx, wz, END_C) < CORNER_R) {
            return Zone.END;
        }
        if (wz < 0.0d) {
            if (wx < 0.0d) {
                return Zone.FOREST;
            }
            if (distance(wx, wz, HAMLET_C) < 16.0d) {
                return Zone.HAMLET;
            }
            if (distance(wx, wz, MUSHROOM_C) < 11.0d) {
                return Zone.MUSHROOM;
            }
            return Zone.MEADOW;
        }
        if (wx >= 0.0d) {
            return wz < wx * 0.55d ? Zone.HILLS : Zone.JUNGLE;
        }
        double lake = lakeDistance(x, z);
        if (lake < LAKE_R) {
            return Zone.LAKE;
        }
        if (lake < LAKE_R + 5.0d && wx > -48.0d) {
            return Zone.BEACH;
        }
        return Zone.SWAMP;
    }

    /** Pour la ceinture : le coin qu'elle borde (celui d'un point ramene un peu en dedans). */
    static Zone inner(int x, int z) {
        double r = radius(x, z);
        if (r < PLAY) {
            return zone(x, z);
        }
        double scale = (PLAY - 3.0d) / Math.max(1.0d, r);
        return zone((int) Math.round(x * scale), (int) Math.round(z * scale));
    }

    static double lakeDistance(int x, int z) {
        return distance(x, z, LAKE_C) + 4.0d * noise(x / 14.0d, z / 14.0d, 21);
    }

    static boolean island(int x, int z) {
        return distance(x, z, ISLAND_C) < ISLAND_R;
    }

    // -- Les chemins ------------------------------------------------------------------------------

    /** Vrai si la colonne est sur un chemin (trois blocs de large). */
    static boolean onPath(int x, int z) {
        for (int[][] path : PATHS) {
            for (int i = 0; i + 1 < path.length; i++) {
                if (segment(x, z, path[i], path[i + 1]) < 1.6d) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double segment(double x, double z, int[] a, int[] b) {
        double dx = b[0] - a[0];
        double dz = b[1] - a[1];
        double t = ((x - a[0]) * dx + (z - a[1]) * dz) / (dx * dx + dz * dz);
        t = Math.max(0.0d, Math.min(1.0d, t));
        double px = a[0] + t * dx - x;
        double pz = a[1] + t * dz - z;
        return Math.sqrt(px * px + pz * pz);
    }

    // -- Les hauteurs -----------------------------------------------------------------------------

    /** Le bloc du dessus de la colonne (le fond pour le lac). */
    public static int top(int x, int z) {
        return top(x, z, zone(x, z));
    }

    static int top(int x, int z, Zone zone) {
        return switch (zone) {
            case HUB -> G;
            case FOREST -> G + clamp((int) Math.round(0.8d + 1.4d * fbm(x / 16.0d, z / 16.0d, 5)), 0, 2);
            case MEADOW -> G + clamp((int) Math.round(0.4d + 1.2d * fbm(x / 22.0d, z / 22.0d, 6)), 0, 2);
            case HAMLET, MUSHROOM -> G + 1;
            case HILLS -> G + hills(x, z);
            case JUNGLE -> G + clamp((int) Math.round(0.6d + 1.3d * fbm(x / 14.0d, z / 14.0d, 7)), 0, 2);
            case LAKE -> island(x, z) ? G + 1 + (distance(x, z, ISLAND_C) < 2.5d ? 1 : 0) : G - depth(x, z);
            case BEACH -> G + (lakeDistance(x, z) > LAKE_R + 2.5d ? 1 : 0);
            case SWAMP -> puddle(x, z) ? G - 1 : G;
            case NETHER -> lavaPool(x, z) ? G - 1
                    : G + 1 + clamp((int) Math.round(1.2d + 1.6d * fbm(x / 12.0d, z / 12.0d, 8)), 0, 3);
            case END -> G + 3 + clamp((int) Math.round(1.0d + 1.5d * fbm(x / 13.0d, z / 13.0d, 9)), 0, 3);
            case RIM -> rim(x, z);
            case VOID -> Integer.MIN_VALUE;
        };
    }

    /** La profondeur du lac, de 1 au bord a 8 au milieu. */
    static int depth(int x, int z) {
        return clamp((int) Math.round((LAKE_R - lakeDistance(x, z)) * 0.45d + 1.0d), 1, 8);
    }

    /** Les flaques du marais : un bloc d'eau. */
    static boolean puddle(int x, int z) {
        return noise(x / 7.0d, z / 7.0d, 13) > 0.42d && lakeDistance(x, z) > LAKE_R + 6.0d;
    }

    /**
     * Les bassins de lave du Nether, loin de ses bords : meme deforme, le coin les entoure, et la lave
     * ne touche jamais ni l'eau ni le vide.
     */
    static boolean lavaPool(int x, int z) {
        return noise(x / 9.0d, z / 9.0d, 14) > 0.5d && distance(x, z, NETHER_C) < CORNER_R - 12.0d;
    }

    /** Le rebord de magma des bassins. */
    static boolean lavaRim(int x, int z) {
        return !lavaPool(x, z) && noise(x / 9.0d, z / 9.0d, 14) > 0.4d && distance(x, z, NETHER_C) < CORNER_R - 10.0d;
    }

    /** Les collines : trois sommets, un peu de bruit. */
    static int hills(int x, int z) {
        double height = 0.0d;
        for (int[] peak : PEAKS) {
            double d = distance(x, z, new int[] {peak[0], peak[1]});
            height += peak[2] * smooth(1.0d - d / peak[3]);
        }
        height += 1.5d * noise(x / 8.0d, z / 8.0d, 15) + 0.8d;
        return clamp((int) Math.round(height), 0, 18);
    }

    /** La ceinture : elle monte en falaise sur douze blocs. */
    static int rim(int x, int z) {
        double t = (radius(x, z) - PLAY) / (RIM - PLAY);
        double height = Math.pow(Math.max(0.0d, t), 1.3d) * 24.0d + 3.0d * t * noise(x / 9.0d, z / 9.0d, 10);
        return G + 2 + (int) Math.round(height);
    }

    /** La plus grande difference de hauteur avec les quatre voisines. */
    static int slope(int x, int z, int top) {
        int slope = 0;
        slope = Math.max(slope, Math.abs(top(x + 1, z) - top));
        slope = Math.max(slope, Math.abs(top(x - 1, z) - top));
        slope = Math.max(slope, Math.abs(top(x, z + 1) - top));
        slope = Math.max(slope, Math.abs(top(x, z - 1) - top));
        return slope;
    }

    // -- La colonne -------------------------------------------------------------------------------

    /** Ce qui recoit les blocs d'une colonne. */
    interface Sink {
        void set(int x, int y, int z, BlockState state);
    }

    /** Tous les blocs d'une colonne : sol, eau ou lave, et la petite plante du dessus. */
    static void column(int x, int z, Sink sink) {
        Zone zone = zone(x, z);
        if (zone == Zone.VOID) {
            return;
        }
        int top = top(x, z, zone);
        int bottom = Math.min(BOTTOM, top - 3);
        boolean path = zone != Zone.LAKE && zone != Zone.HUB && onPath(x, z);
        Zone ground = zone == Zone.RIM ? inner(x, z) : zone;
        sink.set(x, bottom, z, B.of("bedrock"));
        for (int y = bottom + 1; y <= top; y++) {
            sink.set(x, y, z, material(x, y, z, zone, ground, top, path));
        }
        if (zone == Zone.LAKE && !island(x, z)) {
            for (int y = top + 1; y <= G; y++) {
                sink.set(x, y, z, B.of("water"));
            }
        } else if (zone == Zone.SWAMP && puddle(x, z)) {
            sink.set(x, G, z, B.of("water"));
        } else if (zone == Zone.NETHER && lavaPool(x, z)) {
            for (int y = top + 1; y <= G + 1; y++) {
                sink.set(x, y, z, B.of("lava"));
            }
        }
        if (zone == Zone.RIM && radius(x, z) > RIM - 1.6d) {
            // la couronne invisible : on ne marche pas jusqu'au vide
            for (int y = top + 1; y <= top + 4; y++) {
                sink.set(x, y, z, B.of("barrier"));
            }
        }
        if (!path) {
            plant(x, z, zone, top, sink);
        }
    }

    private static BlockState material(int x, int y, int z, Zone zone, Zone ground, int top, boolean path) {
        int below = top - y;
        if (ground == Zone.NETHER) {
            if (below == 0) {
                return path ? B.of("polished_blackstone_bricks") : netherTop(x, z);
            }
            return below < 3 && noise(x / 9.0d, z / 9.0d, 17) > 0.45d ? B.of("soul_soil") : B.of("netherrack");
        }
        if (ground == Zone.END) {
            return below == 0 && path ? B.of("end_stone_bricks") : B.of("end_stone");
        }
        switch (zone) {
            case HUB -> {
                return below == 0 ? plaza(x, z) : below < 3 ? B.of("dirt") : B.of("stone");
            }
            case LAKE -> {
                if (island(x, z)) {
                    return below == 0 ? B.of("grass_block") : below < 3 ? B.of("dirt") : B.of("stone");
                }
                if (below == 0) {
                    double n = noise(x / 6.0d, z / 6.0d, 18);
                    return n > 0.45d ? B.of("clay") : n < -0.35d ? B.of("gravel") : B.of("sand");
                }
                return below < 3 ? B.of("sand") : B.of("stone");
            }
            case BEACH -> {
                return below < 3 ? B.of("sand") : below < 4 ? B.of("sandstone") : B.of("stone");
            }
            case MUSHROOM -> {
                return below == 0 ? (path ? B.of("dirt_path") : B.of("mycelium")) : below < 3 ? B.of("dirt")
                        : B.of("stone");
            }
            case HILLS, RIM -> {
                boolean rocky = top >= G + 11 || slope(x, z, top) >= 3;
                if (below == 0) {
                    if (path) {
                        return B.of("dirt_path");
                    }
                    return rocky ? rock(x, y, z) : B.of("grass_block");
                }
                return below < 3 && !rocky ? B.of("dirt") : rock(x, y, z);
            }
            case SWAMP -> {
                if (below == 0) {
                    if (path) {
                        return B.of("dirt_path");
                    }
                    return noise(x / 5.0d, z / 5.0d, 19) > 0.35d ? B.of("mud") : B.of("grass_block");
                }
                return below < 3 ? B.of("dirt") : B.of("stone");
            }
            default -> {
                if (below == 0) {
                    if (path) {
                        return B.of("dirt_path");
                    }
                    double n = noise(x / 7.0d, z / 7.0d, 20);
                    if (zone == Zone.FOREST) {
                        return n > 0.5d ? B.of("podzol") : n < -0.62d ? B.of("moss_block") : B.of("grass_block");
                    }
                    if (zone == Zone.JUNGLE) {
                        return n > 0.4d ? B.of("podzol") : B.of("grass_block");
                    }
                    return B.of("grass_block");
                }
                return below < 3 ? B.of("dirt") : B.of("stone");
            }
        }
    }

    /** La roche des collines et des falaises : pierre, andesite, tuf, un peu de granite. */
    private static BlockState rock(int x, int y, int z) {
        double n = noise(x / 6.0d + y / 9.0d, z / 6.0d - y / 9.0d, 22);
        return n > 0.45d ? B.of("andesite") : n < -0.5d ? B.of("tuff") : n > 0.3d ? B.of("granite") : B.of("stone");
    }

    private static BlockState netherTop(int x, int z) {
        if (lavaRim(x, z)) {
            return B.of("magma_block");
        }
        double crimson = noise(x / 10.0d, z / 10.0d, 23);
        double soul = noise(x / 11.0d, z / 11.0d, 24);
        if (soul > 0.42d) {
            return rand(x, z, 25) < 0.5d ? B.of("soul_sand") : B.of("soul_soil");
        }
        if (crimson > 0.28d) {
            return B.of("crimson_nylium");
        }
        if (crimson < -0.32d) {
            return B.of("warped_nylium");
        }
        return noise(x / 6.0d, z / 6.0d, 26) > 0.5d ? B.of("blackstone") : B.of("netherrack");
    }

    /** Le parvis du camp : andesite polie, cercles de briques, un coeur sculpte. */
    private static BlockState plaza(int x, int z) {
        double r = radius(x, z);
        if (r < 1.6d) {
            return B.of("chiseled_stone_bricks");
        }
        int ring = (int) Math.round(r);
        if (ring == 5 || ring == 12) {
            return B.of("stone_bricks");
        }
        if (ring == 9) {
            return (x + z & 1) == 0 ? B.of("mossy_stone_bricks") : B.of("stone_bricks");
        }
        return B.of("polished_andesite");
    }

    // -- Les plantes ------------------------------------------------------------------------------

    private static void plant(int x, int z, Zone zone, int top, Sink sink) {
        double r = rand(x, z, 30);
        int y = top + 1;
        switch (zone) {
            case FOREST -> {
                if (r < 0.28d) {
                    sink.set(x, y, z, B.of("short_grass"));
                } else if (r < 0.34d) {
                    sink.set(x, y, z, B.of("fern"));
                } else if (r < 0.37d) {
                    sink.set(x, y, z, pick(x, z, "dandelion", "poppy", "lily_of_the_valley", "cornflower"));
                } else if (r < 0.385d) {
                    sink.set(x, y, z, B.of("brown_mushroom"));
                } else if (r < 0.45d) {
                    sink.set(x, y, z, B.of("leaf_litter"));
                }
            }
            case MEADOW -> {
                if (r < 0.32d) {
                    sink.set(x, y, z, B.of("short_grass"));
                } else if (r < 0.40d) {
                    tall(x, y, z, "tall_grass", sink);
                } else if (r < 0.50d) {
                    sink.set(x, y, z, pick(x, z, "dandelion", "poppy", "oxeye_daisy", "azure_bluet", "cornflower",
                            "allium"));
                }
            }
            case HAMLET -> {
                if (r < 0.12d) {
                    sink.set(x, y, z, B.of("short_grass"));
                }
            }
            case MUSHROOM -> {
                if (r < 0.04d) {
                    sink.set(x, y, z, r < 0.02d ? B.of("red_mushroom") : B.of("brown_mushroom"));
                }
            }
            case HILLS, RIM -> {
                if (top < G + 11 && slope(x, z, top) < 3) {
                    if (r < 0.16d) {
                        sink.set(x, y, z, B.of("short_grass"));
                    } else if (r < 0.19d) {
                        sink.set(x, y, z, B.of("fern"));
                    }
                }
            }
            case JUNGLE -> {
                if (noise(x / 10.0d, z / 10.0d, 31) > 0.15d && r < 0.45d) {
                    bamboo(x, y, z, sink);
                } else if (r < 0.22d) {
                    sink.set(x, y, z, B.of("fern"));
                } else if (r < 0.28d) {
                    tall(x, y, z, "large_fern", sink);
                } else if (r < 0.295d) {
                    sink.set(x, y, z, B.of("melon"));
                } else if (r < 0.40d) {
                    sink.set(x, y, z, B.of("short_grass"));
                }
            }
            case BEACH -> {
                if (lakeDistance(x, z) < LAKE_R + 1.2d && r < 0.22d) {
                    int height = 1 + (int) (rand(x, z, 32) * 3.0d);
                    for (int dy = 0; dy < height; dy++) {
                        sink.set(x, y + dy, z, B.of("sugar_cane"));
                    }
                } else if (r < 0.015d) {
                    sink.set(x, y, z, B.of("dead_bush"));
                }
            }
            case SWAMP -> {
                if (puddle(x, z)) {
                    if (r < 0.22d) {
                        sink.set(x, G + 1, z, B.of("lily_pad"));
                    }
                } else if (r < 0.2d) {
                    sink.set(x, y, z, B.of("short_grass"));
                } else if (r < 0.23d) {
                    sink.set(x, y, z, B.of("blue_orchid"));
                } else if (r < 0.245d) {
                    sink.set(x, y, z, B.of("brown_mushroom"));
                }
            }
            case LAKE -> {
                if (island(x, z)) {
                    if (r < 0.3d) {
                        sink.set(x, y, z, B.of("short_grass"));
                    }
                    return;
                }
                int depth = G - top;
                if (depth >= 3 && r < 0.12d) {
                    int height = Math.max(1, depth - 1 - (int) (rand(x, z, 33) * 2.0d));
                    for (int dy = 0; dy < height - 1; dy++) {
                        sink.set(x, y + dy, z, B.of("kelp_plant"));
                    }
                    sink.set(x, y + height - 1, z, B.with(B.of("kelp"), BlockStateProperties.AGE_25, 25));
                } else if (depth >= 2 && r < 0.32d) {
                    sink.set(x, y, z, B.of("seagrass"));
                } else if (depth <= 2 && r > 0.93d) {
                    sink.set(x, G + 1, z, B.of("lily_pad"));
                }
            }
            case NETHER -> {
                if (lavaPool(x, z)) {
                    return;
                }
                BlockState ground = netherTop(x, z);
                if (ground == B.of("crimson_nylium")) {
                    if (r < 0.12d) {
                        sink.set(x, y, z, B.of("crimson_roots"));
                    } else if (r < 0.15d) {
                        sink.set(x, y, z, B.of("crimson_fungus"));
                    }
                } else if (ground == B.of("warped_nylium")) {
                    if (r < 0.12d) {
                        sink.set(x, y, z, B.of("warped_roots"));
                    } else if (r < 0.2d) {
                        sink.set(x, y, z, B.of("nether_sprouts"));
                    } else if (r < 0.23d) {
                        sink.set(x, y, z, B.of("warped_fungus"));
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void tall(int x, int y, int z, String name, Sink sink) {
        BlockState plant = B.of(name);
        sink.set(x, y, z, B.with(plant, BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        sink.set(x, y + 1, z, B.with(plant, BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    /** Une tige de bambou de six a onze blocs, mure (elle ne pousse plus), feuillue en haut. */
    private static void bamboo(int x, int y, int z, Sink sink) {
        int height = 6 + (int) (rand(x, z, 34) * 6.0d);
        BlockState stalk = B.with(B.of("bamboo"), BlockStateProperties.STAGE, 1);
        for (int dy = 0; dy < height; dy++) {
            BlockState state = stalk;
            if (dy >= height - 2) {
                state = B.with(state, BlockStateProperties.BAMBOO_LEAVES,
                        net.minecraft.world.level.block.state.properties.BambooLeaves.LARGE);
            } else if (dy == height - 3) {
                state = B.with(state, BlockStateProperties.BAMBOO_LEAVES,
                        net.minecraft.world.level.block.state.properties.BambooLeaves.SMALL);
            }
            if (dy >= height - 4) {
                state = B.with(state, BlockStateProperties.AGE_1, 1);
            }
            sink.set(x, y + dy, z, state);
        }
    }

    private static BlockState pick(int x, int z, String... names) {
        return B.of(names[(int) (rand(x, z, 35) * names.length) % names.length]);
    }

    /** Une direction horizontale tiree de la colonne. */
    static Direction facing(int x, int z, int salt) {
        return Direction.from2DDataValue((int) (rand(x, z, salt) * 4.0d) & 3);
    }

    // -- Le bruit ---------------------------------------------------------------------------------

    static double distance(double x, double z, int[] point) {
        double dx = x - point[0];
        double dz = z - point[1];
        return Math.sqrt(dx * dx + dz * dz);
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static double smooth(double t) {
        double c = Math.max(0.0d, Math.min(1.0d, t));
        return c * c * (3.0d - 2.0d * c);
    }

    /** Un nombre de [0, 1) propre a une colonne et a un sel. */
    static double rand(int x, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L ^ 0x5DEECE66DL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Un bruit lisse de [-1, 1]. */
    static double noise(double x, double z, int salt) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        double a = rand(x0, z0, salt) * 2.0d - 1.0d;
        double b = rand(x0 + 1, z0, salt) * 2.0d - 1.0d;
        double c = rand(x0, z0 + 1, salt) * 2.0d - 1.0d;
        double d = rand(x0 + 1, z0 + 1, salt) * 2.0d - 1.0d;
        double u = fx * fx * (3.0d - 2.0d * fx);
        double v = fz * fz * (3.0d - 2.0d * fz);
        return (a + (b - a) * u) * (1.0d - v) + (c + (d - c) * u) * v;
    }

    /** Deux octaves de bruit. */
    static double fbm(double x, double z, int salt) {
        return (noise(x, z, salt) * 2.0d + noise(x * 2.0d, z * 2.0d, salt + 100)) / 3.0d;
    }
}

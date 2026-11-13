package fr.atraxterra.dohy.earth;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Le plan d'une ville jouable, chunk par chunk.
 *
 * <p>Une ville est un damier de chunks autour de son centre : chaque chunk est
 * un ilot de 12 x 12 blocs borde de trottoirs et de rues ; ceux du centre (le
 * chunk du milieu, ou les 3 x 3 du milieu des villes moyennes et grandes)
 * forment la place, ou se dresse le monument. Les tours au centre, les
 * immeubles autour, les maisons au bord, des parcs ca et la ; les murs, les
 * toits et les couleurs suivent la region du monde ({@link Style}).
 *
 * <p>Chaque chunk se dessine seul, sans voir ses voisins : la ville se pose a
 * mesure que la carte se charge. Tout se tire d'une graine propre a la ville et
 * au chunk : un chunk redessine donne la meme chose. Le plan ne connait que des
 * noms de blocs ({@link CityBlocks}) ; il se lance aussi hors du jeu.
 */
public final class CityBuilder {

    public static final int OUTSIDE = 0;
    public static final int DISTRICT = 1;
    public static final int BLOCK = 2;
    /** La hauteur d'un etage : quatre blocs d'air et un plancher. */
    public static final int STOREY = 5;

    private CityBuilder() {
    }

    /** Le terrain naturel sous la ville. */
    public interface Ground {
        /** Le dessus du sol ferme, sous l'eau s'il y en a. */
        int floor(int x, int z);

        /** Le plus haut bloc a degager : l'eau, les troncs. */
        int top(int x, int z);

        /** Vrai si la colonne est sous l'eau. */
        boolean water(int x, int z);
    }

    /** Un texte flottant : le nom de la ville sur sa place. */
    public record Label(BlockPos pos, String text, int color, float scale) {
    }

    /** Ce qu'il faut poser, dans l'ordre : les supports avant ce qui s'y accroche. */
    public static final class Plan {
        public final Long2ObjectLinkedOpenHashMap<BlockState> blocks = new Long2ObjectLinkedOpenHashMap<>();
        public final List<Label> labels = new ArrayList<>();

        private int minX = Integer.MIN_VALUE;
        private int maxX = Integer.MAX_VALUE;
        private int minZ = Integer.MIN_VALUE;
        private int maxZ = Integer.MAX_VALUE;

        /** Borne le plan a un chunk : ce qui deborde n'est pas pose (le voisin pose sa moitie). */
        void clip(int minX, int minZ) {
            this.minX = minX;
            this.maxX = minX + 15;
            this.minZ = minZ;
            this.maxZ = minZ + 15;
        }

        public void set(int x, int y, int z, BlockState state) {
            if (x < minX || x > maxX || z < minZ || z > maxZ) {
                return;
            }
            long key = BlockPos.asLong(x, y, z);
            blocks.remove(key);
            blocks.put(key, state);
        }

        public void label(int x, int y, int z, String text, int color, float scale) {
            labels.add(new Label(new BlockPos(x, y, z), text, color, scale));
        }
    }

    /**
     * Une ville : son centre et son rayon en chunks, sa region et son monument.
     *
     * @param chunkX  le chunk du centre
     * @param radius  1 a 4 : 3 x 3 a 9 x 9 chunks, sans les coins
     */
    public record City(String id, String name, String country, int chunkX, int chunkZ, int radius, String style,
                       String monument, long seed, CityLayouts.Layout layout, List<CityLayouts.Site> sites) {

        public City(String id, String name, String country, int chunkX, int chunkZ, int radius, String style,
                    String monument, long seed) {
            this(id, name, country, chunkX, chunkZ, radius, style, monument, seed, CityLayouts.of(id),
                    CityLayouts.of(id) != null ? CityLayouts.of(id).sites()
                            : List.of(new CityLayouts.Site(0, 0, radius >= 2 ? 1 : 0, monument == null ? "palace"
                            : monument, name, true)));
        }

        /** La place qui tient ce chunk, ou -1. */
        public int site(int cx, int cz) {
            for (int i = 0; i < sites.size(); i++) {
                if (sites.get(i).covers(cx - chunkX, cz - chunkZ)) {
                    return i;
                }
            }
            return -1;
        }

        /** Ce qu'est ce chunk pour la ville : dehors, une place, ou un ilot. */
        public int role(int cx, int cz) {
            int dx = cx - chunkX;
            int dz = cz - chunkZ;
            if (dx * dx + dz * dz > radius * radius + radius) {
                return OUTSIDE;
            }
            return site(cx, cz) >= 0 ? DISTRICT : BLOCK;
        }

        public int centerX() {
            return (chunkX << 4) + 8;
        }

        public int centerZ() {
            return (chunkZ << 4) + 8;
        }

        /** Vrai si ce chunk est un parc du plan. */
        boolean park(int cx, int cz) {
            if (layout != null) {
                for (int[] park : layout.parks()) {
                    if (park[0] == cx - chunkX && park[1] == cz - chunkZ) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** La distance de ce point au fleuve, en blocs ; tres grande sans fleuve. */
        double river(int x, int z) {
            return layout == null ? 1.0e9d : distance(layout.river(), x - centerX(), z - centerZ());
        }

        /** Vrai si le fleuve coule plutot d'est en ouest pres de ce point. */
        boolean riverEastWest(int x, int z) {
            int[][] line = layout.river();
            double best = 1.0e9d;
            boolean flat = true;
            for (int i = 0; i + 1 < line.length; i++) {
                double d = distance(new int[][] {line[i], line[i + 1]}, x - centerX(), z - centerZ());
                if (d < best) {
                    best = d;
                    flat = Math.abs(line[i + 1][0] - line[i][0]) >= Math.abs(line[i + 1][1] - line[i][1]);
                }
            }
            return flat;
        }

        /** La distance de ce point a la grande avenue la plus proche. */
        double avenue(int x, int z) {
            double best = 1.0e9d;
            if (layout != null) {
                for (int[][] avenue : layout.avenues()) {
                    best = Math.min(best, distance(avenue, x - centerX(), z - centerZ()));
                }
            }
            return best;
        }

        private static double distance(int[][] line, int x, int z) {
            double best = 1.0e9d;
            for (int i = 0; i + 1 < line.length; i++) {
                double ax = line[i][0];
                double az = line[i][1];
                double bx = line[i + 1][0] - ax;
                double bz = line[i + 1][1] - az;
                double t = Math.max(0.0d, Math.min(1.0d, ((x - ax) * bx + (z - az) * bz) / (bx * bx + bz * bz)));
                best = Math.min(best, Math.hypot(x - ax - t * bx, z - az - t * bz));
            }
            return best;
        }
    }

    // -- Les styles -------------------------------------------------------------------------

    /** Les materiaux d'une region du monde. */
    public record Style(String id, String[] walls, String[] trims, String roof, String roofStairs, String floor,
                        String glass, boolean pitched, String houseWall) {

        static Style of(String id) {
            return switch (id) {
                case "orient" -> new Style(id,
                        new String[] {"smooth_sandstone", "sandstone", "cut_sandstone", "white_terracotta"},
                        new String[] {"cut_sandstone", "chiseled_sandstone"}, "smooth_sandstone", null,
                        "acacia_planks", "glass", false, "smooth_sandstone");
                case "afrique" -> new Style(id,
                        new String[] {"terracotta", "orange_terracotta", "white_terracotta", "mud_bricks",
                                "brown_terracotta"},
                        new String[] {"brown_terracotta", "packed_mud"}, "light_gray_terracotta", null,
                        "jungle_planks", "glass", false, "mud_bricks");
                case "asie" -> new Style(id,
                        new String[] {"white_concrete", "light_gray_concrete", "smooth_stone", "white_terracotta"},
                        new String[] {"gray_concrete", "dark_oak_planks"}, "deepslate_tiles",
                        "deepslate_tile_stairs", "oak_planks", "light_blue_stained_glass", true, "white_terracotta");
                case "latam" -> new Style(id,
                        new String[] {"white_terracotta", "yellow_terracotta", "orange_terracotta",
                                "light_blue_terracotta", "pink_terracotta", "lime_terracotta", "white_concrete"},
                        new String[] {"white_concrete", "smooth_quartz"}, "white_concrete", "brick_stairs",
                        "spruce_planks", "glass", true, "white_terracotta");
                case "ameriques" -> new Style(id,
                        new String[] {"white_concrete", "light_gray_concrete", "bricks", "quartz_block",
                                "smooth_stone"},
                        new String[] {"gray_concrete", "stone_bricks"}, "gray_concrete", "dark_oak_stairs",
                        "birch_planks", "light_blue_stained_glass", true, "birch_planks");
                default -> new Style("europe",
                        new String[] {"stone_bricks", "bricks", "smooth_sandstone", "white_terracotta",
                                "light_gray_terracotta", "polished_diorite"},
                        new String[] {"polished_andesite", "smooth_stone"}, "deepslate_tiles",
                        "deepslate_tile_stairs", "spruce_planks", "glass", true, "white_terracotta");
            };
        }

        String wall(Random random) {
            return walls[random.nextInt(walls.length)];
        }

        String trim(Random random) {
            return trims[random.nextInt(trims.length)];
        }

        /** Combien d'etages pour un immeuble de cette region. */
        int storeys(Random random) {
            return switch (id) {
                case "afrique" -> 2 + random.nextInt(3);
                case "orient" -> 3 + random.nextInt(3);
                case "latam" -> 3 + random.nextInt(4);
                case "asie" -> 6 + random.nextInt(7);
                case "ameriques" -> 4 + random.nextInt(6);
                default -> 5 + random.nextInt(3);
            };
        }
    }

    private static BlockState b(String name) {
        return CityBlocks.get(name);
    }

    private static BlockState with(String name, String... properties) {
        BlockState state = b(name);
        for (int i = 0; i + 1 < properties.length; i += 2) {
            state = CityBlocks.with(state, properties[i], properties[i + 1]);
        }
        return state;
    }

    private static Random random(City city, int cx, int cz) {
        return new Random(city.seed() ^ (cx * 341_873_128_712L) ^ (cz * 132_897_987_541L));
    }

    // -- Un chunk ---------------------------------------------------------------------------

    /**
     * Dessine un chunk de la ville : le terrain mis a plat a la hauteur de la
     * ville, les rues, puis l'ilot ou la place. Un ilot presque tout dans l'eau
     * est laisse a l'eau (un port, un fleuve) ; la place, jamais.
     */
    public static void chunk(Plan plan, City city, int cx, int cz, int baseY, Ground ground) {
        int role = city.role(cx, cz);
        if (role == OUTSIDE) {
            return;
        }
        int minX = cx << 4;
        int minZ = cz << 4;
        if (role == BLOCK) {
            int wet = 0;
            for (int lx = 0; lx < 16; lx += 3) {
                for (int lz = 0; lz < 16; lz += 3) {
                    if (ground.water(minX + lx, minZ + lz)) {
                        wet++;
                    }
                }
            }
            if (wet > 14) {
                return;
            }
        }
        plan.clip(minX, minZ);
        Style style = Style.of(city.style());
        Random random = random(city, cx, cz);
        // les rues bordent l'ilot ; la place n'en a qu'au bord, face aux ilots
        int site = city.site(cx, cz);
        boolean north = role == BLOCK || city.role(cx, cz - 1) == OUTSIDE || city.site(cx, cz - 1) != site;
        boolean south = role == BLOCK || city.role(cx, cz + 1) == OUTSIDE || city.site(cx, cz + 1) != site;
        boolean west = role == BLOCK || city.role(cx - 1, cz) == OUTSIDE || city.site(cx - 1, cz) != site;
        boolean east = role == BLOCK || city.role(cx + 1, cz) == OUTSIDE || city.site(cx + 1, cz) != site;
        // deux ilots reunis : pas de rue entre eux, un seul grand batiment
        int left = pairLeft(city, cx);
        if (role == BLOCK && merged(city, left, cz)) {
            if (cx == left) {
                east = false;
            } else {
                west = false;
            }
        }
        boolean[] rim = {city.role(cx, cz - 1) == OUTSIDE, city.role(cx, cz + 1) == OUTSIDE,
                city.role(cx - 1, cz) == OUTSIDE, city.role(cx + 1, cz) == OUTSIDE};
        terrain(plan, minX, minZ, baseY, ground, rim);
        BlockState asphalt = b("gray_concrete");
        BlockState walk = b("smooth_stone");
        BlockState curb = b("stone_bricks");
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int road = Math.min(Math.min(north ? lz : 99, south ? 15 - lz : 99), Math.min(west ? lx : 99,
                        east ? 15 - lx : 99));
                BlockState top = road <= 1 ? asphalt : road == 2 ? walk : null;
                if (top != null) {
                    plan.set(minX + lx, baseY, minZ + lz, top);
                    plan.set(minX + lx, baseY - 1, minZ + lz, curb);
                }
            }
        }
        if (role == DISTRICT) {
            square(plan, city, cx, cz, baseY, north, south, west, east, random);
        } else {
            double t = Math.hypot(cx - city.chunkX(), cz - city.chunkZ()) / (city.radius() + 0.5d);
            lot(plan, city, style, minX, minZ, baseY, t, random);
            waterways(plan, city, minX, minZ, baseY, rim);
        }
        // reverberes aux coins des trottoirs
        int[][] corners = {{2, 2}, {13, 2}, {2, 13}, {13, 13}};
        for (int[] corner : corners) {
            boolean near = (corner[0] == 2 ? west : east) || (corner[1] == 2 ? north : south);
            if (near) {
                lamp(plan, minX + corner[0], baseY + 1, minZ + corner[1]);
            }
        }
    }

    /** Le terrain mis a la hauteur de la ville : rempli dessous, degage dessus ; un mur de quai au bord. */
    private static void terrain(Plan plan, int minX, int minZ, int baseY, Ground ground, boolean[] rim) {
        BlockState stone = b("stone");
        BlockState dirt = b("dirt");
        BlockState wall = b("stone_bricks");
        BlockState air = b("air");
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = minX + lx;
                int z = minZ + lz;
                boolean edge = (rim[0] && lz == 0) || (rim[1] && lz == 15) || (rim[2] && lx == 0)
                        || (rim[3] && lx == 15);
                int floor = ground.floor(x, z);
                int top = Math.max(floor, ground.top(x, z));
                for (int y = Math.min(floor, baseY - 1) + 1; y < baseY; y++) {
                    plan.set(x, y, z, edge ? wall : y >= baseY - 3 ? dirt : stone);
                }
                plan.set(x, baseY, z, edge ? wall : b("grass_block"));
                int clear = top >= baseY ? top + 8 : baseY + 2;
                for (int y = clear; y > baseY; y--) {
                    plan.set(x, y, z, air);
                }
            }
        }
    }

    private static void lamp(Plan plan, int x, int y, int z) {
        BlockState post = b("andesite_wall");
        plan.set(x, y, z, post);
        plan.set(x, y + 1, z, post);
        plan.set(x, y + 2, z, post);
        plan.set(x, y + 3, z, b("lantern"));
    }

    // -- La place ---------------------------------------------------------------------------

    private static void square(Plan plan, City city, int cx, int cz, int baseY, boolean north, boolean south,
                               boolean west, boolean east, Random random) {
        int minX = cx << 4;
        int minZ = cz << 4;
        BlockState a = b("polished_andesite");
        BlockState c = b("stone_bricks");
        BlockState border = b("smooth_stone");
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int road = Math.min(Math.min(north ? lz : 99, south ? 15 - lz : 99), Math.min(west ? lx : 99,
                        east ? 15 - lx : 99));
                if (road <= 2) {
                    continue;
                }
                int x = minX + lx;
                int z = minZ + lz;
                plan.set(x, baseY, z, road == 3 ? border : ((x >> 1) + (z >> 1) & 1) == 0 ? a : c);
            }
        }
        // jardins aux coins de la grande place
        CityLayouts.Site place = city.sites().get(city.site(cx, cz));
        int dx = cx - city.chunkX() - place.dx();
        int dz = cz - city.chunkZ() - place.dz();
        if (place.half() >= 1 && dx != 0 && dz != 0) {
            int gx = minX + (dx < 0 ? 4 : 3);
            int gz = minZ + (dz < 0 ? 4 : 3);
            for (int x = gx; x < gx + 9; x++) {
                for (int z = gz; z < gz + 9; z++) {
                    boolean rim = x == gx || z == gz || x == gx + 8 || z == gz + 8;
                    plan.set(x, baseY, z, rim ? b("stone_bricks") : b("grass_block"));
                    if (rim) {
                        plan.set(x, baseY + 1, z, b("stone_brick_slab"));
                    } else if (random.nextInt(6) == 0) {
                        plan.set(x, baseY + 1, z, b(random.nextBoolean() ? "poppy" : "cornflower"));
                    }
                }
            }
            tree(plan, gx + 4, baseY + 1, gz + 4, random, "oak");
        }
    }

    // -- Les ilots --------------------------------------------------------------------------

    private static void lot(Plan plan, City city, Style style, int minX, int minZ, int baseY, double t,
                            Random random) {
        int x0 = minX + 3;
        int z0 = minZ + 3;
        // le sol de l'ilot, avant les batiments
        for (int x = x0; x < x0 + 10; x++) {
            for (int z = z0; z < z0 + 10; z++) {
                plan.set(x, baseY, z, b("grass_block"));
            }
        }
        float roll = random.nextFloat();
        int cx = minX >> 4;
        int cz = minZ >> 4;
        int left = pairLeft(city, cx);
        if (merged(city, left, cz)) {
            // un grand batiment a cheval sur deux ilots : chaque chunk en pose sa moitie, tiree de la meme graine
            Random pair = random(city, left, cz);
            pair.nextFloat();
            int fx = (left << 4) + 3;
            int zone = Math.min(zone(city, left, cz), zone(city, left + 1, cz));
            for (int x = fx; x < fx + 26; x++) {
                for (int z = z0; z < z0 + 10; z++) {
                    plan.set(x, baseY, z, b("smooth_stone"));
                }
            }
            if (zone == 0 && modern(style, pair)) {
                glassTower(plan, fx + 1, z0 + 1, 24, 8, baseY, towerStoreys(city, left, cz, pair), style, pair);
            } else {
                int storeys = zone == 0 ? Math.max(6, Math.min(9, towerStoreys(city, left, cz, pair)))
                        : style.storeys(pair);
                building(plan, fx, z0, 26, 10, baseY, storeys, style, pair, false);
            }
            return;
        }
        if (crossed(city, cx, cz)) {
            // le fleuve ou une grande avenue traverse l'ilot : un quai plante, pas de batiment
            pave(plan, x0, z0, 10, 10, baseY);
            for (int i = 0; i < 4; i++) {
                int tx = x0 + 2 + (i % 2) * 5;
                int tz = z0 + 2 + (i / 2) * 5;
                if (city.river(tx, tz) > riverHalf(city) + 3.0d && city.avenue(tx, tz) > 6.0d) {
                    tree(plan, tx, baseY + 1, tz, random, "oak");
                }
            }
        } else if (city.park(cx, cz) || roll < parkChance(t)) {
            park(plan, x0, z0, baseY, random);
        } else if (zone(city, cx, cz) == 0) {
            pave(plan, x0, z0, 10, 10, baseY);
            int storeys = towerStoreys(city, cx, cz, random);
            if (modern(style, random)) {
                glassTower(plan, x0 + 1, z0 + 1, 8, 8, baseY, storeys, style, random);
            } else {
                building(plan, x0, z0, 10, 10, baseY, Math.max(6, Math.min(storeys, 9)), style, random, false);
            }
        } else if (zone(city, cx, cz) == 1) {
            int layout = random.nextInt(3);
            // une petite capitale reste basse
            int cap = city.radius() <= 1 ? 4 : 12;
            pave(plan, x0, z0, 10, 10, baseY);
            if (layout == 0) {
                building(plan, x0, z0, 10, 10, baseY, Math.min(cap, style.storeys(random)), style, random, false);
            } else if (layout == 1) {
                building(plan, x0, z0, 10, 4, baseY, Math.min(cap, style.storeys(random)), style, random, false);
                building(plan, x0, z0 + 6, 10, 4, baseY, Math.min(cap, style.storeys(random)), style, random, false);
            } else {
                for (int i = 0; i < 4; i++) {
                    building(plan, x0 + (i % 2) * 6, z0 + (i / 2) * 6, 4, 4, baseY, Math.min(3, style.storeys(random)),
                            style, random, style.pitched());
                }
            }
        } else {
            houses(plan, x0, z0, baseY, style, random);
        }
    }

    private static float parkChance(double t) {
        return t > 0.7d ? 0.16f : 0.08f;
    }

    private static double ring(City city, int cx, int cz) {
        return Math.hypot(cx - city.chunkX(), cz - city.chunkZ()) / (city.radius() + 0.5d);
    }

    /** 0 : le centre et ses tours ; 1 : les immeubles ; 2 : les maisons du bord. */
    private static int zone(City city, int cx, int cz) {
        double t = ring(city, cx, cz);
        if (city.radius() >= 3 && t < 0.42d) {
            return 0;
        }
        return t < 0.72d || (city.radius() <= 2 && t < 0.9d) ? 1 : 2;
    }

    /** Les villes d'Asie, d'Amerique et d'Orient ont des tours de verre ; les autres, peu. */
    private static boolean modern(Style style, Random random) {
        return switch (style.id()) {
            case "asie", "ameriques", "orient" -> random.nextInt(10) < 7;
            default -> random.nextInt(4) == 0;
        };
    }

    private static int towerStoreys(City city, int cx, int cz, Random random) {
        int max = city.radius() >= 4 ? 22 : 13;
        return 8 + (int) Math.round((max - 8) * (1.0d - ring(city, cx, cz) / 0.42d) * (0.5d + 0.5d * random.nextDouble()));
    }

    private static boolean crossed(City city, int cx, int cz) {
        if (city.layout() == null) {
            return false;
        }
        int x0 = (cx << 4) + 3;
        int z0 = (cz << 4) + 3;
        for (int x = x0; x < x0 + 10; x++) {
            for (int z = z0; z < z0 + 10; z++) {
                if (city.river(x, z) <= riverHalf(city) + 1.0d || city.avenue(x, z) <= 4.0d) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Un ilot ordinaire, a batir : ni parc, ni quai, ni maisons. */
    private static boolean plain(City city, int cx, int cz) {
        return city.role(cx, cz) == BLOCK && !city.park(cx, cz) && zone(city, cx, cz) != 2
                && !crossed(city, cx, cz) && random(city, cx, cz).nextFloat() >= parkChance(ring(city, cx, cz));
    }

    /** Les ilots vont par deux d'ouest en est : le chunk de gauche de la paire de celui-ci. */
    private static int pairLeft(City city, int cx) {
        return Math.floorMod(cx - city.chunkX(), 2) == 0 ? cx : cx - 1;
    }

    /** Vrai si les deux ilots de la paire se reunissent en un grand batiment (trois fois sur quatre). */
    private static boolean merged(City city, int left, int cz) {
        return city.radius() >= 2 && plain(city, left, cz) && plain(city, left + 1, cz)
                && (random(city, left, cz).nextInt() >>> 8 & 3) != 0;
    }

    private static double riverHalf(City city) {
        return city.layout() == null ? 0.0d : city.layout().riverHalf();
    }

    /**
     * Le fleuve et les grandes avenues du plan, par-dessus l'ilot. Le fleuve
     * ne creuse que hors des rues : chaque rue qui le croise fait un pont.
     */
    private static void waterways(Plan plan, City city, int minX, int minZ, int baseY, boolean[] rim) {
        if (city.layout() == null) {
            return;
        }
        double half = riverHalf(city);
        BlockState water = b("water");
        BlockState air = b("air");
        BlockState quay = b("stone_bricks");
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = minX + lx;
                int z = minZ + lz;
                boolean edge = (rim[0] && lz == 0) || (rim[1] && lz == 15) || (rim[2] && lx == 0)
                        || (rim[3] && lx == 15);
                boolean street = lx <= 2 || lx >= 13 || lz <= 2 || lz >= 13;
                double river = city.river(x, z);
                // un pont : une rue sur deux qui croise le fleuve garde sa chaussee, l'eau passe dessous
                boolean flat = half > 0 && river <= half + 1.0d && city.riverEastWest(x, z);
                boolean across = half > 0 && river <= half + 1.0d && (flat
                        ? (lx <= 1 || lx >= 14) && ((x + 2) >> 4 & 1) == 0
                        : (lz <= 1 || lz >= 14) && ((z + 2) >> 4 & 1) == 0);
                if (half > 0 && river <= half && !edge) {
                    if (across) {
                        plan.set(x, baseY - 3, z, quay);
                        plan.set(x, baseY - 2, z, water);
                        plan.set(x, baseY - 1, z, quay);
                        boolean side = flat ? (lx == 1 || lx == 14) : (lz == 1 || lz == 14);
                        if (side) {
                            plan.set(x, baseY + 1, z, b("stone_brick_wall"));
                        }
                        continue;
                    }
                    plan.set(x, baseY - 3, z, quay);
                    plan.set(x, baseY - 2, z, water);
                    plan.set(x, baseY - 1, z, water);
                    for (int y = baseY; y <= baseY + 12; y++) {
                        plan.set(x, y, z, air);
                    }
                } else if (half > 0 && river <= half + 1.0d && !edge && !across) {
                    plan.set(x, baseY, z, quay);
                    plan.set(x, baseY + 1, z, b("stone_brick_wall"));
                } else if (city.avenue(x, z) <= 2.5d && !edge) {
                    plan.set(x, baseY, z, b("gray_concrete"));
                    for (int y = baseY + 1; y <= baseY + 12; y++) {
                        plan.set(x, y, z, air);
                    }
                }
            }
        }
    }

    private static void pave(Plan plan, int x0, int z0, int w, int d, int baseY) {
        BlockState stone = b("smooth_stone");
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + d; z++) {
                plan.set(x, baseY, z, stone);
            }
        }
    }

    private static void park(Plan plan, int x0, int z0, int baseY, Random random) {
        BlockState path = b("dirt_path");
        for (int i = 0; i < 10; i++) {
            plan.set(x0 + i, baseY, z0 + 4, path);
            plan.set(x0 + i, baseY, z0 + 5, path);
            plan.set(x0 + 4, baseY, z0 + i, path);
            plan.set(x0 + 5, baseY, z0 + i, path);
        }
        // une fontaine au croisement
        for (int x = x0 + 3; x <= x0 + 6; x++) {
            for (int z = z0 + 3; z <= z0 + 6; z++) {
                boolean rim = x == x0 + 3 || x == x0 + 6 || z == z0 + 3 || z == z0 + 6;
                plan.set(x, baseY, z, b("stone_bricks"));
                plan.set(x, baseY + 1, z, rim ? b("stone_brick_slab") : b("water"));
            }
        }
        int[][] spots = {{1, 1}, {8, 1}, {1, 8}, {8, 8}};
        for (int[] spot : spots) {
            if (random.nextInt(5) > 0) {
                tree(plan, x0 + spot[0], baseY + 1, z0 + spot[1], random, random.nextBoolean() ? "oak" : "birch");
            }
        }
        for (int i = 0; i < 10; i++) {
            int x = x0 + random.nextInt(10);
            int z = z0 + random.nextInt(10);
            if (!plan.blocks.containsKey(BlockPos.asLong(x, baseY + 1, z))) {
                plan.set(x, baseY + 1, z, b(random.nextBoolean() ? "dandelion" : "short_grass"));
            }
        }
    }

    /** Un arbre rond : un tronc et une boule de feuilles. */
    static void tree(Plan plan, int x, int y, int z, Random random, String wood) {
        int height = 4 + random.nextInt(2);
        BlockState log = b(wood + "_log");
        BlockState leaves = with(wood + "_leaves", "persistent", "true");
        for (int dy = 0; dy < height; dy++) {
            plan.set(x, y + dy, z, log);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = height - 2; dy <= height + 1; dy++) {
                    int reach = dy >= height ? 1 : 2;
                    if (Math.abs(dx) <= reach && Math.abs(dz) <= reach && !(dx == 0 && dz == 0 && dy < height)
                            && !(Math.abs(dx) == 2 && Math.abs(dz) == 2)) {
                        plan.set(x + dx, y + dy, z + dz, leaves);
                    }
                }
            }
        }
    }

    // -- Les batiments ----------------------------------------------------------------------

    /**
     * Un immeuble : murs, fenetres d'un etage sur l'autre, planchers, une
     * echelle jusqu'au toit, une lanterne par etage ; toit plat a parapet.
     */
    static void building(Plan plan, int x0, int z0, int w, int d, int baseY, int storeys, Style style,
                         Random random, boolean pitched) {
        BlockState wall = b(style.wall(random));
        BlockState trim = b(style.trim(random));
        BlockState glass = b(style.glass());
        BlockState floor = b(style.floor());
        BlockState air = b("air");
        String[] signs = {"red_terracotta", "green_terracotta", "blue_terracotta", "yellow_terracotta",
                "black_terracotta"};
        BlockState sign = b(signs[random.nextInt(signs.length)]);
        boolean large = w >= 8 && d >= 8;
        boolean shops = large && storeys >= 3;
        boolean mansard = large && "europe".equals(style.id()) && storeys >= 4;
        boolean balconies = large && storeys >= 4 && !"orient".equals(style.id()) && random.nextInt(3) > 0;
        BlockState slate = b("deepslate_tiles");
        int top = baseY + storeys * STOREY;
        int x1 = x0 + w - 1;
        int z1 = z0 + d - 1;
        for (int y = baseY + 1; y <= top; y++) {
            int level = (y - baseY) % STOREY;
            int storey = (y - baseY - 1) / STOREY + 1;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean side = x == x0 || x == x1 || z == z0 || z == z1;
                    boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                    if (!side) {
                        plan.set(x, y, z, level == 0 ? floor : air);
                        continue;
                    }
                    if (corner || level == 0) {
                        plan.set(x, y, z, trim);
                        continue;
                    }
                    int along = (x == x0 || x == x1) ? z - z0 : x - x0;
                    if (shops && storey == 1) {
                        // les boutiques : des vitrines entre des piliers, une enseigne en bandeau
                        plan.set(x, y, z, level == 4 ? sign : along % 4 == 0 ? trim : level <= 3 ? glass : wall);
                        continue;
                    }
                    boolean window = (level == 2 || level == 3) && along % 2 == 1;
                    boolean attic = mansard && storey == storeys;
                    plan.set(x, y, z, window ? glass : attic ? slate : wall);
                }
            }
        }
        // les balcons : une dalle et un garde-corps, un etage sur deux, sur les grands cotes
        if (balconies) {
            BlockState slab = b("smooth_stone_slab");
            BlockState rail = b("iron_bars");
            for (int storey = 3; storey <= storeys - (mansard ? 1 : 0); storey += 2) {
                int y = baseY + (storey - 1) * STOREY + 1;
                for (int x = x0 + 1; x < x1; x++) {
                    if ((x - x0) % 4 != 0) {
                        plan.set(x, y - 1, z0 - 1, with("smooth_stone_slab", "type", "top"));
                        plan.set(x, y, z0 - 1, rail);
                        plan.set(x, y - 1, z1 + 1, with("smooth_stone_slab", "type", "top"));
                        plan.set(x, y, z1 + 1, rail);
                    }
                }
            }
        }
        // l'entree : au rez-de-chaussee, au milieu du cote sud (ou nord si l'ilot est au nord du chunk)
        int doorX = x0 + w / 2;
        int doorZ = (z0 & 15) < 8 ? z0 : z1;
        for (int dx = 0; dx <= (large ? 1 : 0); dx++) {
            plan.set(doorX + dx, baseY + 1, doorZ, air);
            plan.set(doorX + dx, baseY + 2, doorZ, air);
            if (large) {
                plan.set(doorX + dx, baseY + 3, doorZ, air);
            }
        }
        // l'echelle, contre le mur ouest, et un trou dans chaque plancher
        int ladderX = x0 + 1;
        int ladderZ = z0 + 1;
        if (w >= 4 && d >= 4) {
            for (int y = baseY + 1; y <= top; y++) {
                plan.set(ladderX, y, ladderZ, with("ladder", "facing", "east"));
            }
        }
        // le toit
        if (pitched && style.roofStairs() != null && w <= 7 && d <= 7) {
            hipRoof(plan, x0, z0, w, d, top + 1, style);
        } else {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean side = x == x0 || x == x1 || z == z0 || z == z1;
                    plan.set(x, top, z, (x == ladderX && z == ladderZ) ? with("ladder", "facing", "east")
                            : b(mansard ? "deepslate_tiles" : style.roof()));
                    if (side) {
                        plan.set(x, top + 1, z, mansard ? slate : trim);
                    }
                }
            }
            if (large) {
                // la corniche deborde d'un bloc
                BlockState ledge = with("stone_brick_slab", "type", "top");
                for (int x = x0 - 1; x <= x1 + 1; x++) {
                    plan.set(x, top, z0 - 1, ledge);
                    plan.set(x, top, z1 + 1, ledge);
                }
                for (int z = z0; z <= z1; z++) {
                    plan.set(x0 - 1, top, z, ledge);
                    plan.set(x1 + 1, top, z, ledge);
                }
                // sur le toit : des cheminees en Europe, des blocs techniques ailleurs
                for (int x = x0 + 3; x < x1 - 1; x += 7) {
                    if (mansard) {
                        plan.set(x, top + 1, z0 + 2, b("bricks"));
                        plan.set(x, top + 2, z0 + 2, b("bricks"));
                        plan.set(x, top + 3, z0 + 2, b("flower_pot"));
                    } else {
                        plan.set(x, top + 1, z0 + 3, b("smooth_stone"));
                        plan.set(x + 1, top + 1, z0 + 3, b("smooth_stone"));
                        plan.set(x, top + 2, z0 + 3, b("iron_bars"));
                    }
                }
            }
        }
        // une lanterne sous chaque plafond, tous les huit blocs
        for (int storey = 1; storey <= storeys; storey++) {
            int y = baseY + storey * STOREY - 1;
            for (int x = x0 + Math.min(4, w / 2); x < x1; x += 8) {
                plan.set(x, y, z0 + d / 2, with("lantern", "hanging", "true"));
            }
        }
    }

    /** Un toit a quatre pans, en marches : chaque rang regarde vers l'interieur. */
    private static void hipRoof(Plan plan, int x0, int z0, int w, int d, int y, Style style) {
        String stairs = style.roofStairs();
        BlockState fill = b(style.roof());
        int ax = x0 - 1;
        int az = z0 - 1;
        int bx = x0 + w;
        int bz = z0 + d;
        while (ax <= bx && az <= bz) {
            for (int x = ax; x <= bx; x++) {
                for (int z = az; z <= bz; z++) {
                    boolean n = z == az;
                    boolean s = z == bz;
                    boolean wst = x == ax;
                    boolean est = x == bx;
                    if (!(n || s || wst || est)) {
                        continue;
                    }
                    if (ax == bx || az == bz) {
                        plan.set(x, y, z, fill);
                    } else if (n) {
                        plan.set(x, y, z, with(stairs, "facing", "south"));
                    } else if (s) {
                        plan.set(x, y, z, with(stairs, "facing", "north"));
                    } else if (wst) {
                        plan.set(x, y, z, with(stairs, "facing", "east"));
                    } else {
                        plan.set(x, y, z, with(stairs, "facing", "west"));
                    }
                }
            }
            ax++;
            az++;
            bx--;
            bz--;
            y++;
        }
    }

    /** Une tour de verre : piliers aux angles, bandeaux a chaque etage, couronne et antenne. */
    static void glassTower(Plan plan, int x0, int z0, int w, int d, int baseY, int storeys, Style style,
                           Random random) {
        String[] glasses = {"light_blue_stained_glass", "cyan_stained_glass", "light_gray_stained_glass", "glass",
                "blue_stained_glass"};
        BlockState glass = b(glasses[random.nextInt(glasses.length)]);
        BlockState frame = b(random.nextBoolean() ? "light_gray_concrete" : "white_concrete");
        BlockState floor = b("smooth_stone");
        BlockState air = b("air");
        int top = baseY + storeys * STOREY;
        int x1 = x0 + w - 1;
        int z1 = z0 + d - 1;
        for (int y = baseY + 1; y <= top; y++) {
            int level = (y - baseY) % STOREY;
            boolean crown = y > top - 2 * STOREY && storeys > 6;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    int inset = crown ? 1 : 0;
                    if (x < x0 + inset || x > x1 - inset || z < z0 + inset || z > z1 - inset) {
                        if (crown && y == top - 2 * STOREY + 1) {
                            plan.set(x, y - 1, z, frame);
                        }
                        continue;
                    }
                    boolean side = x == x0 + inset || x == x1 - inset || z == z0 + inset || z == z1 - inset;
                    boolean corner = (x == x0 + inset || x == x1 - inset) && (z == z0 + inset || z == z1 - inset);
                    if (!side) {
                        plan.set(x, y, z, level == 0 ? floor : air);
                    } else if (corner || level == 0) {
                        plan.set(x, y, z, frame);
                    } else {
                        plan.set(x, y, z, glass);
                    }
                }
            }
        }
        plan.set(x0 + w / 2, baseY + 1, (z0 & 15) < 8 ? z0 : z1, air);
        plan.set(x0 + w / 2, baseY + 2, (z0 & 15) < 8 ? z0 : z1, air);
        for (int x = x0 + 1; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                plan.set(x, top, z, frame);
            }
        }
        for (int y = baseY + 1; y <= top; y++) {
            plan.set(x0 + 2, y, z0 + 3, frame);
            plan.set(x0 + 3, y, z0 + 3, with("ladder", "facing", "east"));
        }
        for (int storey = 1; storey <= storeys; storey++) {
            for (int x = x0 + 4; x < x1; x += 8) {
                plan.set(x, baseY + storey * STOREY - 1, z0 + d / 2, with("lantern", "hanging", "true"));
            }
        }
        if (storeys >= 12) {
            for (int y = top + 1; y <= top + 7; y++) {
                plan.set(x0 + w / 2, y, z0 + d / 2, b("iron_bars"));
            }
            plan.set(x0 + w / 2, top + 8, z0 + d / 2, b("lightning_rod"));
        }
    }

    /** Quatre maisons aux coins de l'ilot, des jardins entre elles. */
    private static void houses(Plan plan, int x0, int z0, int baseY, Style style, Random random) {
        BlockState hedge = with("oak_leaves", "persistent", "true");
        for (int i = 0; i < 10; i++) {
            plan.set(x0 + i, baseY + 1, z0 + 4, i == 1 || i == 8 ? b("air") : hedge);
            plan.set(x0 + i, baseY + 1, z0 + 5, i == 1 || i == 8 ? b("air") : hedge);
        }
        for (int i = 0; i < 4; i++) {
            int hx = x0 + (i % 2) * 6;
            int hz = z0 + (i / 2) * 6;
            if (random.nextInt(6) == 0) {
                tree(plan, hx + 2, baseY + 1, hz + 2, random, "oak");
                continue;
            }
            Style house = new Style(style.id(), new String[] {style.houseWall(), style.wall(random)}, style.trims(),
                    style.roof(), style.roofStairs(), style.floor(), "glass", style.pitched(), style.houseWall());
            building(plan, hx, hz, 4, 4, baseY, 1 + random.nextInt(2), house, random, style.pitched());
        }
    }
}

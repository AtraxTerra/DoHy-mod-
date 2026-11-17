package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.spawn.DoHySpawn;
import fr.atraxterra.dohy.spawn.SpawnTerminalBlock;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Le plan de l'astroport d'Arrakeen : la grande aire d'atterrissage au centre
 * de la cuvette, le hall de l'astroport au nord, la Residence derriere lui,
 * deux aires de service (une navette y est posee), les avenues et un anneau de
 * blocs monolithiques entre les avenues, avec leurs pieges a vent.
 *
 * <p>Une architecture de sable et de beton, massive, aux murs en gradins et
 * aux fentes etroites : un dessin propre au mod, pas une copie. Le sol de la
 * cuvette est plat a {@link ArrakisRelief#CITY_GROUND} ; tout s'y pose.
 */
final class PortBuilder {

    /** A changer quand le dessin change : l'astroport est alors repose. */
    static final int VERSION = 2;
    static final int G = ArrakisRelief.CITY_GROUND;
    /** Le centre de l'aire d'arrivee ; on y apparait tourne vers le hall, au nord. */
    static final BlockPos ARRIVAL = new BlockPos(0, G + 1, 6);
    /** La borne de la navette, au bord nord de l'aire, face a l'arrivee. */
    static final BlockPos TERMINAL = new BlockPos(0, G + 1, -18);
    /** Le comptoir de l'epice, a droite de la navette : la Guilde y rachete le melange. */
    static final BlockPos COUNTER = new BlockPos(8, G + 1, -18);

    record Label(BlockPos pos, String text, int color, float scale) {
    }

    record Plan(Long2ObjectMap<BlockState> blocks, List<Label> labels) {
    }

    private final Long2ObjectMap<BlockState> blocks = new Long2ObjectLinkedOpenHashMap<>();
    private final List<Label> labels = new ArrayList<>();
    /** Les emprises deja prises (x0, z0, x1, z1), pour que les blocs de la ville ne se chevauchent pas. */
    private final List<int[]> taken = new ArrayList<>();

    private final BlockState wall = block("smooth_sandstone");
    private final BlockState trim = block("cut_sandstone");
    private final BlockState chiseled = block("chiseled_sandstone");
    private final BlockState shade = block("light_gray_terracotta");
    private final BlockState dark = block("brown_terracotta");
    private final BlockState slit = block("black_stained_glass");
    private final BlockState ledge = slab("smooth_sandstone_slab", SlabType.BOTTOM);
    private final BlockState paving = block("smooth_sandstone");
    private final BlockState kerb = block("cut_sandstone");
    private final BlockState padTop = block("light_gray_concrete");
    private final BlockState padUnder = block("gray_concrete");
    private final BlockState padLine = block("white_concrete");
    private final BlockState padRing = block("yellow_concrete");
    private final BlockState padRim = block("polished_andesite");
    private final BlockState light = block("ochre_froglight");
    private final BlockState lantern = block("lantern");
    private final BlockState post = block("sandstone_wall");
    private final BlockState air = Blocks.AIR.defaultBlockState();

    static Plan plan() {
        PortBuilder builder = new PortBuilder();
        builder.build();
        return new Plan(builder.blocks, builder.labels);
    }

    private static BlockState block(String id) {
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id));
        return block.defaultBlockState();
    }

    private static BlockState slab(String id, SlabType type) {
        BlockState state = block(id);
        return state.hasProperty(SlabBlock.TYPE) ? state.setValue(SlabBlock.TYPE, type) : state;
    }

    private void put(int x, int y, int z, BlockState state) {
        blocks.put(BlockPos.asLong(x, y, z), state);
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    put(x, y, z, state);
                }
            }
        }
    }

    private void build() {
        avenues();
        pad(0, 0, 24, true);
        hall();
        residence();
        pad(-64, 4, 13, false);
        pad(64, 4, 13, false);
        shuttle(64, 4);
        city();
        // la borne de la navette sur l'aire, et ses etiquettes
        put(TERMINAL.getX(), TERMINAL.getY() - 1, TERMINAL.getZ(), trim);
        put(TERMINAL.getX(), TERMINAL.getY(), TERMINAL.getZ(), DoHySpawn.TERMINAL.get().defaultBlockState()
                .setValue(SpawnTerminalBlock.FACING, Direction.SOUTH)
                .setValue(SpawnTerminalBlock.KIND, SpawnTerminalBlock.Kind.SHUTTLE));
        labels.add(new Label(TERMINAL.above(), "NAVETTE", 0xFF9A3C, 1.0f));
        labels.add(new Label(TERMINAL.above(2), "Retour au spawn : gratuit", 0xE8C27A, 0.6f));
        // le comptoir de l'epice, a cote
        put(COUNTER.getX(), COUNTER.getY() - 1, COUNTER.getZ(), trim);
        put(COUNTER.getX(), COUNTER.getY(), COUNTER.getZ(), DoHySpawn.TERMINAL.get().defaultBlockState()
                .setValue(SpawnTerminalBlock.FACING, Direction.SOUTH)
                .setValue(SpawnTerminalBlock.KIND, SpawnTerminalBlock.Kind.SPICE));
        labels.add(new Label(COUNTER.above(), "EPICE", 0xE8742C, 1.0f));
        labels.add(new Label(COUNTER.above(2), "Comptoir de la Guilde", 0xE8C27A, 0.6f));
    }

    // -- L'aire d'atterrissage ------------------------------------------------------------

    private void pad(int cx, int cz, int radius, boolean main) {
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius + 0.5d) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                boolean rim = d > radius - 0.6d;
                put(x, G - 1, z, padUnder);
                BlockState top = padTop;
                if (rim) {
                    top = padRim;
                } else if (d > radius - 4.5d && d <= radius - 3.4d) {
                    top = padRing;
                } else if ((dx == 0 || dz == 0) && d <= radius * 0.45d) {
                    top = padLine;
                } else if (d <= radius - 5.0d && Math.floorMod(dx, 6) == 0 && Math.floorMod(dz, 6) == 0) {
                    top = light;
                }
                put(x, G, z, top);
                if (rim && main && Math.floorMod(dx * 3 + dz * 7, 9) == 0) {
                    put(x, G + 1, z, post);
                    put(x, G + 2, z, lantern);
                }
            }
        }
        // un cercle et un trait, la marque des aires de la ville
        if (main) {
            for (int a = 0; a < 360; a += 2) {
                double r = radius * 0.28d;
                int x = cx + (int) Math.round(Math.cos(Math.toRadians(a)) * r);
                int z = cz + (int) Math.round(Math.sin(Math.toRadians(a)) * r);
                put(x, G, z, padLine);
            }
        }
        taken.add(new int[] {cx - radius - 4, cz - radius - 4, cx + radius + 4, cz + radius + 4});
    }

    // -- Les avenues --------------------------------------------------------------------------

    private void avenues() {
        // la grande avenue vers la passe du sud, et le boulevard est-ouest
        road(-5, 20, 5, 146, true);
        road(-146, -5, 146, 5, false);
        // l'allee de la Residence, derriere le hall, et le parvis entre le hall et l'aire
        road(-4, -82, 4, -58, true);
        road(-4, -34, 4, -24, true);
        taken.add(new int[] {-8, 20, 8, 150});
        taken.add(new int[] {-150, -8, 150, 8});
        taken.add(new int[] {-8, -84, 8, -56});
    }

    private void road(int x0, int z0, int x1, int z1, boolean northSouth) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x * x + z * z > 146 * 146) {
                    continue;
                }
                boolean edge = northSouth ? (x == x0 || x == x1) : (z == z0 || z == z1);
                put(x, G, z, edge ? kerb : (Math.floorMod(northSouth ? z : x, 8) == 0 ? trim : paving));
                int along = northSouth ? z : x;
                if (edge && Math.floorMod(along, 12) == 0 && !onPad(x, z)) {
                    int ox = northSouth ? (x == x0 ? -1 : 1) : 0;
                    int oz = northSouth ? 0 : (z == z0 ? -1 : 1);
                    put(x + ox, G + 1, z + oz, post);
                    put(x + ox, G + 2, z + oz, post);
                    put(x + ox, G + 3, z + oz, lantern);
                }
            }
        }
    }

    /** Vrai sur une aire ou a son bord : on n'y plante pas de reverbere. */
    private static boolean onPad(int x, int z) {
        return x * x + z * z < 29 * 29 || (x - 64) * (x - 64) + (z - 4) * (z - 4) < 17 * 17
                || (x + 64) * (x + 64) + (z - 4) * (z - 4) < 17 * 17;
    }

    // -- Le hall de l'astroport ----------------------------------------------------------

    private void hall() {
        int cx = 0;
        int cz = -46;
        int hw = 22;
        int hd = 12;
        int h = 18;
        monolith(cx, cz, hw, hd, h, 10, Direction.SOUTH, 9, 8);
        door(cx, cz, hw, hd, Direction.NORTH, 5, 6);
        // le sol du hall, et un auvent au-dessus de la grande baie
        fill(cx - hw + 1, G, cz - hd + 1, cx + hw - 1, G, cz + hd - 1, paving);
        for (int x = cx - 8; x <= cx + 8; x++) {
            put(x, G + 9, cz + hd + 1, ledge);
            put(x, G + 9, cz + hd + 2, ledge);
        }
        for (int x = cx - 18; x <= cx + 18; x += 6) {
            fill(x, G + 1, cz - hd + 1, x, G + h - 2, cz - hd + 1, trim);
            put(x, G + 6, cz - hd + 2, light);
        }
        labels.add(new Label(new BlockPos(cx, G + h + 4, cz + hd), "ARRAKEEN", 0xF0D9A8, 3.0f));
        labels.add(new Label(new BlockPos(cx, G + h + 2, cz + hd), "Astroport", 0xE8C27A, 1.2f));
        labels.add(new Label(new BlockPos(cx, G + 4, cz + hd + 3), "Bienvenue sur Arrakis", 0xFFE7B0, 0.8f));
        taken.add(new int[] {cx - hw - 4, cz - hd - 4, cx + hw + 4, cz + hd + 6});
    }

    // -- La Residence -------------------------------------------------------------------------

    private void residence() {
        int cx = 0;
        int cz = -100;
        monolith(cx, cz, 30, 18, 34, 7, Direction.SOUTH, 5, 6);
        // deux tours d'angle, plus hautes, et le parvis de la porte
        for (int side = -1; side <= 1; side += 2) {
            tower(cx + side * 36, cz + 10, 5, 46);
        }
        fill(cx - 9, G, cz + 19, cx + 9, G, cz + 23, trim);
        for (int side = -1; side <= 1; side += 2) {
            fill(cx + side * 8, G + 1, cz + 20, cx + side * 8, G + 7, cz + 20, chiseled);
            put(cx + side * 8, G + 8, cz + 20, light);
        }
        labels.add(new Label(new BlockPos(cx, G + 40, cz + 18), "LA RESIDENCE", 0xF0D9A8, 1.6f));
        taken.add(new int[] {cx - 42, cz - 24, cx + 42, cz + 26});
    }

    /** Une tour carree a degres, faite comme un monolithe mais haute et etroite. */
    private void tower(int cx, int cz, int half, int height) {
        monolith(cx, cz, half, half, height, 15, null, 3, 5);
        for (int y = G + height - 3; y <= G + height; y++) {
            put(cx, y, cz, trim);
        }
        put(cx, G + height + 1, cz, light);
    }

    // -- Les blocs de la ville ---------------------------------------------------------------

    private void city() {
        Random random = new Random(0x4152524BL);
        int placed = 0;
        for (int attempt = 0; attempt < 400 && placed < 22; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0d;
            double radius = 52.0d + random.nextDouble() * 78.0d;
            int cx = (int) Math.round(Math.cos(angle) * radius);
            int cz = (int) Math.round(Math.sin(angle) * radius);
            int hw = 6 + random.nextInt(7);
            int hd = 6 + random.nextInt(6);
            int corner = (int) Math.ceil(Math.sqrt((double) (Math.abs(cx) + hw) * (Math.abs(cx) + hw)
                    + (double) (Math.abs(cz) + hd) * (Math.abs(cz) + hd)));
            if (corner > 140 || overlaps(cx - hw - 3, cz - hd - 3, cx + hw + 3, cz + hd + 3)) {
                continue;
            }
            int height = 10 + random.nextInt(17);
            Direction door = Math.abs(cx) > Math.abs(cz) ? (cx > 0 ? Direction.WEST : Direction.EAST)
                    : (cz > 0 ? Direction.NORTH : Direction.SOUTH);
            monolith(cx, cz, hw, hd, height, 5 + random.nextInt(4), door, 3, 4);
            if (random.nextInt(3) == 0) {
                // un piege a vent sur le toit
                windTrap(cx + (random.nextBoolean() ? 1 : -1) * Math.max(1, hw - 4), cz, G + height + 1,
                        8 + random.nextInt(6));
            }
            taken.add(new int[] {cx - hw - 3, cz - hd - 3, cx + hw + 3, cz + hd + 3});
            placed++;
        }
        // des pieges a vent isoles, plantes au sol entre les blocs
        int traps = 0;
        for (int attempt = 0; attempt < 300 && traps < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0d;
            double radius = 40.0d + random.nextDouble() * 95.0d;
            int cx = (int) Math.round(Math.cos(angle) * radius);
            int cz = (int) Math.round(Math.sin(angle) * radius);
            if (overlaps(cx - 3, cz - 3, cx + 3, cz + 3)) {
                continue;
            }
            fill(cx - 1, G + 1, cz - 1, cx + 1, G + 2, cz + 1, trim);
            windTrap(cx, cz, G + 3, 12 + random.nextInt(8));
            taken.add(new int[] {cx - 3, cz - 3, cx + 3, cz + 3});
            traps++;
        }
    }

    private boolean overlaps(int x0, int z0, int x1, int z1) {
        for (int[] box : taken) {
            if (x0 <= box[2] && x1 >= box[0] && z0 <= box[3] && z1 >= box[1]) {
                return true;
            }
        }
        return false;
    }

    /** Un piege a vent : un mat de gres a ailettes, coiffe d'une lanterne. */
    private void windTrap(int x, int z, int y0, int height) {
        for (int y = y0; y < y0 + height; y++) {
            put(x, y, z, Math.floorMod(y - y0, 4) == 3 ? chiseled : trim);
            if (y >= y0 + 3 && Math.floorMod(y - y0, 2) == 1) {
                put(x + 1, y, z, ledge);
                put(x - 1, y, z, ledge);
                put(x, y, z + 1, ledge);
                put(x, y, z - 1, ledge);
            }
        }
        put(x, y0 + height, z, chiseled);
        put(x, y0 + height + 1, z, lantern);
    }

    /**
     * Un monolithe : un bloc creux aux murs en gradins (le plan recule d'un bloc
     * tous les {@code stepEvery} etages), des fentes verticales sombres, une
     * porte, un toit a parapet.
     */
    private void monolith(int cx, int cz, int halfW, int halfD, int height, int stepEvery, Direction door,
                          int doorWidth, int doorHeight) {
        int inset = 0;
        int top = G + height;
        for (int dy = 1; dy <= height; dy++) {
            int y = G + dy;
            int next = (dy - 1) / stepEvery;
            if (next != inset) {
                // le replat entre deux gradins
                int ow = halfW - inset;
                int od = halfD - inset;
                for (int x = cx - ow; x <= cx + ow; x++) {
                    for (int z = cz - od; z <= cz + od; z++) {
                        boolean inner = Math.abs(x - cx) < halfW - next && Math.abs(z - cz) < halfD - next;
                        if (!inner) {
                            put(x, y, z, ledge);
                        }
                    }
                }
                inset = next;
            }
            int hw = halfW - inset;
            int hd = halfD - inset;
            if (hw < 2 || hd < 2) {
                top = y - 1;
                break;
            }
            for (int x = cx - hw; x <= cx + hw; x++) {
                for (int z = cz - hd; z <= cz + hd; z++) {
                    boolean edgeX = Math.abs(x - cx) == hw;
                    boolean edgeZ = Math.abs(z - cz) == hd;
                    if (!edgeX && !edgeZ) {
                        if (dy == 1) {
                            put(x, G, z, paving);
                        }
                        continue;
                    }
                    BlockState state;
                    if (edgeX && edgeZ) {
                        state = trim;
                    } else if (dy <= 2) {
                        state = shade;
                    } else {
                        int along = edgeX ? z - cz : x - cx;
                        int row = Math.floorMod(dy, 7);
                        boolean slitColumn = Math.floorMod(along, 4) == 0 && Math.abs(along) < (edgeX ? hd : hw) - 1;
                        state = slitColumn && row >= 3 && row <= 5 ? slit : (row == 0 ? trim : wall);
                    }
                    put(x, y, z, state);
                }
            }
            top = y;
        }
        // le toit, et son parapet
        int hw = halfW - inset;
        int hd = halfD - inset;
        fill(cx - hw, top, cz - hd, cx + hw, top, cz + hd, wall);
        for (int x = cx - hw; x <= cx + hw; x++) {
            for (int z = cz - hd; z <= cz + hd; z++) {
                if (Math.abs(x - cx) == hw || Math.abs(z - cz) == hd) {
                    put(x, top + 1, z, Math.floorMod(x + z, 2) == 0 ? trim : ledge);
                }
            }
        }
        if (door != null) {
            door(cx, cz, halfW, halfD, door, doorWidth, doorHeight);
        }
    }

    /** Perce la porte dans le mur du rez-de-chaussee, encadree de gres taille. */
    private void door(int cx, int cz, int halfW, int halfD, Direction side, int width, int height) {
        int half = width / 2;
        for (int a = -half - 1; a <= half + 1; a++) {
            for (int dy = 1; dy <= height + 1; dy++) {
                int x = side.getAxis() == Direction.Axis.Z ? cx + a : cx + side.getStepX() * halfW;
                int z = side.getAxis() == Direction.Axis.Z ? cz + side.getStepZ() * halfD : cz + a;
                boolean frame = Math.abs(a) == half + 1 || dy == height + 1;
                put(x, G + dy, z, frame ? chiseled : air);
            }
        }
        // une marche de dalle devant la porte, et la lumiere au-dessus
        int ox = side.getStepX();
        int oz = side.getStepZ();
        int x = side.getAxis() == Direction.Axis.Z ? cx : cx + ox * (halfW + 1);
        int z = side.getAxis() == Direction.Axis.Z ? cz + oz * (halfD + 1) : cz;
        put(x, G, z, trim);
        put(x - (side.getAxis() == Direction.Axis.Z ? 0 : ox), G + height + 2,
                z - (side.getAxis() == Direction.Axis.Z ? oz : 0), light);
    }

    // -- La navette posee sur l'aire de l'est ---------------------------------------------

    /** Une navette de ligne, posee sur ses jambes : un dessin propre au mod. */
    private void shuttle(int cx, int cz) {
        BlockState hull = block("white_concrete");
        BlockState belly = block("light_gray_concrete");
        BlockState stripe = block("orange_concrete");
        BlockState engine = block("iron_block");
        BlockState nozzle = block("blackstone");
        BlockState glass = block("light_blue_stained_glass");
        BlockState leg = block("iron_bars");
        // les jambes
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                fill(cx + sx * 5, G + 1, cz + sz * 3, cx + sx * 5, G + 2, cz + sz * 3, leg);
            }
        }
        // la coque, de la tuyere (-x) au nez (+x)
        for (int x = -8; x <= 7; x++) {
            int width = x >= 5 ? 1 : 2;
            int topY = x >= 6 ? G + 5 : G + 6;
            for (int z = -width; z <= width; z++) {
                for (int y = G + 3; y <= topY; y++) {
                    BlockState state = y == G + 3 ? belly : (y == G + 4 && Math.abs(z) == width ? stripe : hull);
                    put(cx + x, y, cz + z, state);
                }
            }
        }
        // le nez et la verriere
        fill(cx + 8, G + 4, cz - 1, cx + 8, G + 4, cz + 1, belly);
        fill(cx + 6, G + 5, cz - 1, cx + 7, G + 5, cz + 1, glass);
        // les ailes et les moteurs
        for (int side = -1; side <= 1; side += 2) {
            for (int x = -6; x <= 1; x++) {
                int reach = 3 + Math.max(0, 2 - Math.abs(x + 3));
                for (int z = 3; z <= reach; z++) {
                    put(cx + x, G + 4, cz + side * z, slab("smooth_quartz_slab", SlabType.BOTTOM));
                }
            }
            fill(cx - 9, G + 4, cz + side * 2, cx - 8, G + 5, cz + side * 2, engine);
        }
        fill(cx - 9, G + 4, cz - 1, cx - 9, G + 5, cz + 1, engine);
        fill(cx - 10, G + 4, cz - 2, cx - 10, G + 5, cz + 2, nozzle);
        put(cx - 2, G + 7, cz, block("lightning_rod"));
        labels.add(new Label(new BlockPos(cx, G + 11, cz), "Navette du spawn", 0xFFE7B0, 0.9f));
    }
}

package fr.atraxterra.dohy.arrakis;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Le plan d'un sietch, creuse dans la falaise a partir de son pied.
 *
 * <p>Dans le repere du sietch, {@code u} s'enfonce dans la roche, {@code v} va vers la droite
 * de qui entre, {@code y} monte depuis le sol (les pieds a 0, le dallage a -1). Une fissure
 * etroite s'ouvre au pied de la paroi, un couloir qui tourne (de dehors, on ne voit pas le
 * dedans), une porte de cuivre ; le vestibule et ses bancs ; la grande salle voutee, sa
 * citerne au milieu, ses piliers, quatre alcoves ou se tiennent ceux qui echangent ; au fond,
 * le dortoir et la reserve. Sur la roche, au-dessus de la salle, un piege a vent : de loin,
 * c'est tout ce qu'on voit du sietch.
 *
 * <p>Les murs restent la roche d'Arrakis, taillee ; seuls le sol, les piliers, la citerne et
 * le mobilier sont poses. Un dessin propre au mod.
 */
final class SietchBuilder {

    /** Ou se tient un habitant, et vers ou il regarde. */
    record Spot(SietchDweller.Role role, BlockPos pos, float yaw) {
    }

    record Label(BlockPos pos, String text, int color, float scale) {
    }

    record Plan(Long2ObjectMap<BlockState> blocks, List<Spot> spots, List<Label> labels, BoundingBox box) {
    }

    private final Sietches.Site site;
    private final int floor;
    private final int summit;
    private final Direction f;
    private final Direction r;
    private final Long2ObjectMap<BlockState> blocks = new Long2ObjectLinkedOpenHashMap<>();
    private final List<Spot> spots = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private int minX = Integer.MAX_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;
    private int maxZ = Integer.MIN_VALUE;

    private final BlockState air = Blocks.AIR.defaultBlockState();
    private final BlockState tile = block("smooth_sandstone");
    private final BlockState cut = block("cut_sandstone");
    private final BlockState chiseled = block("chiseled_sandstone");
    private final BlockState sandstone = block("sandstone");
    private final BlockState packed = block("packed_mud");
    private final BlockState basin = block("cyan_terracotta");
    private final BlockState water = Blocks.WATER.defaultBlockState();
    private final BlockState glow = block("ochre_froglight");
    private final BlockState bone = block("bone_block");

    private SietchBuilder(Sietches.Site site, int floor, int summit) {
        this.site = site;
        this.floor = floor;
        this.summit = summit;
        this.f = site.forward();
        this.r = f.getClockWise();
    }

    /** Le plan de ce sietch, son sol a {@code floor} ; {@code summit} : le haut de la roche au-dessus de la salle. */
    static Plan plan(Sietches.Site site, int floor, int summit) {
        SietchBuilder builder = new SietchBuilder(site, floor, summit);
        builder.build();
        return new Plan(builder.blocks, builder.spots, builder.labels,
                new BoundingBox(builder.minX, builder.minY, builder.minZ, builder.maxX, builder.maxY, builder.maxZ));
    }

    /** Le volume du sietch (sans le piege a vent du sommet), pour la protection. */
    static BoundingBox bounds(Sietches.Site site, int floor) {
        return plan(site, floor, 0).box();
    }

    // -- Les blocs -----------------------------------------------------------------------------

    private static BlockState block(String id) {
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id));
        return block.defaultBlockState();
    }

    /** Le premier identifiant qui existe (un bloc renomme d'une version a l'autre). */
    private static BlockState first(String... ids) {
        for (String id : ids) {
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id));
            if (block != Blocks.AIR) {
                return block.defaultBlockState();
            }
        }
        return Blocks.AIR.defaultBlockState();
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, T value) {
        return state.hasProperty(property) ? state.setValue(property, value) : state;
    }

    private BlockPos at(int u, int y, int v) {
        BlockPos face = site.face();
        return new BlockPos(face.getX() + f.getStepX() * u + r.getStepX() * v, floor + y,
                face.getZ() + f.getStepZ() * u + r.getStepZ() * v);
    }

    private void put(int u, int y, int v, BlockState state) {
        BlockPos pos = at(u, y, v);
        blocks.put(pos.asLong(), state);
        if (state != air || y >= -1) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
    }

    private void fill(int u0, int y0, int v0, int u1, int y1, int v1, BlockState state) {
        for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int v = Math.min(v0, v1); v <= Math.max(v0, v1); v++) {
                    put(u, y, v, state);
                }
            }
        }
    }

    /** Une piece creusee : de l'air, un dallage dessous. */
    private void room(int u0, int v0, int u1, int v1, int height, BlockState ground) {
        fill(u0, 0, v0, u1, height - 1, v1, air);
        fill(u0, -1, v0, u1, -1, v1, ground);
    }

    /** Une direction locale : avant (dans la roche), arriere (vers l'erg), droite, gauche. */
    private Direction dir(char d) {
        return switch (d) {
            case 'F' -> f;
            case 'B' -> f.getOpposite();
            case 'R' -> r;
            default -> r.getOpposite();
        };
    }

    private BlockState facing(BlockState state, char d) {
        return with(state, BlockStateProperties.HORIZONTAL_FACING, dir(d));
    }

    private float yaw(char d) {
        return dir(d).toYRot();
    }

    // -- Le dessin -----------------------------------------------------------------------------

    private void build() {
        entrance();
        vestibule();
        hall();
        cistern();
        alcoves();
        back();
        if (summit > floor + 10) {
            BlockPos hall = site.hall(floor);
            int top = summit;
            BlockPos trap = new BlockPos(hall.getX(), top, hall.getZ());
            blocks.put(trap.asLong(), SietchContent.windtrap());
            for (Direction d : Direction.Plane.HORIZONTAL) {
                blocks.put(trap.relative(d).below().asLong(), cut);
                blocks.put(trap.relative(d).relative(d.getClockWise()).below().asLong(), sandstone);
            }
        }
    }

    /** La fissure au pied de la paroi, puis le couloir qui tourne, la porte de cuivre. */
    private void entrance() {
        // la fissure : deux de large, trois de haut, une fente plus haute au milieu
        fill(-3, 0, -1, 6, 2, 0, air);
        fill(-3, 3, 0, 2, 3, 0, air);
        fill(-1, 4, 0, 0, 4, 0, air);
        fill(2, -1, -1, 6, -1, 0, sandstone);
        // le coude : on ne voit pas le dedans depuis l'erg
        room(6, -1, 9, 4, 3, sandstone);
        room(8, 3, 14, 4, 3, sandstone);
        put(7, 2, 4, lantern(true));
        put(10, 2, 4, lantern(true));
        put(13, 2, 3, lantern(true));
        put(3, 2, -1, lantern(true));
        // deux cairns sur le sable, de part et d'autre : on les voit si l'on sait les chercher
        for (int v : new int[] {-4, 3}) {
            put(-5, -1, v, sandstone);
            put(-5, 0, v, sandstone);
            put(-5, 1, v, block("sandstone_wall"));
        }
        // la porte : deux battants de cuivre, un linteau taille
        BlockState door = first("waxed_weathered_copper_door", "waxed_copper_door", "spruce_door");
        door = facing(door, 'F');
        for (int v = 3; v <= 4; v++) {
            DoorHingeSide hinge = v == 3 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
            BlockState half = with(with(door, BlockStateProperties.DOOR_HINGE, hinge), BlockStateProperties.OPEN, false);
            put(14, 0, v, with(half, BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
            put(14, 1, v, with(half, BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
            put(14, 2, v, cut);
        }
        put(14, -1, 3, tile);
        put(14, -1, 4, tile);
        for (int y = 0; y <= 2; y++) {
            put(14, y, 2, chiseled);
            put(14, y, 5, chiseled);
        }
    }

    private void vestibule() {
        room(15, 0, 19, 7, 4, tile);
        // des bancs tailles contre les murs, des jarres
        BlockState bench = with(block("smooth_sandstone_stairs"), BlockStateProperties.HALF, Half.BOTTOM);
        for (int u = 16; u <= 18; u++) {
            put(u, 0, 0, facing(bench, 'L'));
            put(u, 0, 7, facing(bench, 'R'));
        }
        put(15, 0, 0, block("decorated_pot"));
        put(15, 0, 7, block("decorated_pot"));
        put(17, 3, 1, lantern(true));
        put(17, 3, 6, lantern(true));
        for (int v = 1; v <= 6; v++) {
            put(17, 0, v, v == 3 || v == 4 ? block("orange_carpet") : air);
        }
        labels.add(new Label(at(15, 3, 3), "SIETCH " + site.name().toUpperCase(java.util.Locale.ROOT), 0xE8C27A, 0.9f));
    }

    /** La grande salle : seize de large, voutee en gradins, des piliers, des tapis. */
    private void hall() {
        room(20, -4, 36, 11, 6, tile);
        fill(22, 6, -2, 34, 6, 9, air);
        fill(24, 7, 0, 32, 7, 7, air);
        // les piliers
        int[][] pillars = {{23, -1}, {23, 8}, {33, -1}, {33, 8}};
        for (int[] p : pillars) {
            for (int y = 0; y <= 5; y++) {
                put(p[0], y, p[1], y == 2 ? chiseled : y == 5 ? tile : cut);
            }
        }
        // des lumieres dans la voute et dans les murs
        int[][] vault = {{26, 1}, {26, 6}, {30, 1}, {30, 6}, {28, 3}, {28, 4}};
        for (int[] l : vault) {
            put(l[0], 8, l[1], glow);
        }
        for (int u = 21; u <= 35; u += 4) {
            put(u, 3, -5, glow);
            put(u, 3, 12, glow);
        }
        // des lanternes pendues a des chaines
        BlockState chain = first("iron_chain", "chain");
        for (int[] l : new int[][] {{24, 3}, {24, 4}, {33, 3}, {33, 4}}) {
            if (l[0] <= 32) {
                put(l[0], 7, l[1], chain);
            }
            put(l[0], 6, l[1], chain);
            put(l[0], 5, l[1], lantern(true));
        }
        // l'allee de tapis jusqu'a la citerne, et les nattes devant les alcoves
        for (int u = 20; u <= 24; u++) {
            put(u, 0, 3, block("orange_carpet"));
            put(u, 0, 4, block("orange_carpet"));
            put(u, 0, 2, block("brown_carpet"));
            put(u, 0, 5, block("brown_carpet"));
        }
        for (int u = 21; u <= 25; u++) {
            put(u, 0, -4, block("red_carpet"));
            put(u, 0, 11, block("red_carpet"));
        }
        for (int u = 29; u <= 33; u++) {
            put(u, 0, -4, block("red_carpet"));
            put(u, 0, 11, block("red_carpet"));
        }
        labels.add(new Label(at(27, 4, 3), "LA CITERNE", 0x4FB8FF, 0.6f));
    }

    /** La citerne : un bassin creuse dans le dallage, borde de dalles, l'eau a deux blocs de fond. */
    private void cistern() {
        fill(25, -1, 1, 31, -1, 6, chiseled);
        fill(26, -3, 2, 30, -3, 5, basin);
        fill(26, -2, 2, 30, -1, 5, water);
        BlockState rim = with(block("smooth_sandstone_slab"), BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
        for (int u = 25; u <= 31; u++) {
            put(u, 0, 1, rim);
            put(u, 0, 6, rim);
        }
        for (int v = 2; v <= 5; v++) {
            put(25, 0, v, rim);
            put(31, 0, v, rim);
        }
        for (int[] c : new int[][] {{25, 1}, {25, 6}, {31, 1}, {31, 6}}) {
            put(c[0], 0, c[1], cut);
            put(c[0], 1, c[1], lantern(false));
        }
        // le porteur d'eau garde la citerne, cote droit
        spots.add(new Spot(SietchDweller.Role.WATER, at(28, 0, 8), yaw('L')));
        put(27, 0, 8, cauldron());
        put(29, 0, 8, block("barrel"));
    }

    /** Les quatre alcoves : l'artisan, l'ancienne a gauche ; l'equipeur, la reserve d'eau a droite. */
    private void alcoves() {
        alcove(21, -9, -5, 'L', SietchDweller.Role.ARTISAN, "L'ARTISAN", 0xD9A35C);
        alcove(29, -9, -5, 'L', SietchDweller.Role.ELDER, "L'ANCIENNE", 0xC88BE8);
        alcove(21, 12, 16, 'R', SietchDweller.Role.OUTFITTER, "L'EQUIPEUR", 0xC8A878);
        alcove(29, 12, 16, 'R', null, "LA RESERVE D'EAU", 0x4FB8FF);
        // l'artisan : meule, table de forge, os de ver
        put(22, 0, -9, facing(with(block("grindstone"), BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR), 'R'));
        put(24, 0, -9, block("smithing_table"));
        put(25, 0, -9, bone);
        put(25, 1, -9, bone);
        put(21, 0, -9, block("stonecutter"));
        // l'ancienne : bougies, pupitre, coussins
        put(30, 0, -9, facing(block("lectern"), 'R'));
        put(29, 0, -9, candles(4));
        put(33, 0, -9, candles(3));
        put(32, 0, -9, block("brown_wool"));
        put(32, 1, -9, candles(2));
        // l'equipeur : metier a tisser, balles de laine, tonneaux
        put(22, 0, 16, facing(block("loom"), 'L'));
        put(24, 0, 16, block("orange_wool"));
        put(24, 1, 16, block("brown_wool"));
        put(25, 0, 16, with(block("barrel"), BlockStateProperties.FACING, Direction.UP));
        // la reserve d'eau : chaudrons pleins, jarres
        for (int u = 29; u <= 33; u++) {
            put(u, 0, 16, u % 2 == 1 ? cauldron() : block("decorated_pot"));
        }
        put(31, 0, 15, cauldron());
    }

    private void alcove(int u0, int v0, int v1, char side, SietchDweller.Role role, String title, int color) {
        int vIn = side == 'L' ? v0 : v1;
        int vOut = side == 'L' ? v1 : v0;
        int step = side == 'L' ? 1 : -1;
        room(u0, v0, u0 + 4, v1, 4, packed);
        put(u0 + 2, 3, vIn + step, lantern(true));
        // le comptoir : des dalles entre l'habitant et la salle, un passage d'un cote
        BlockState counter = with(block("smooth_sandstone_slab"), BlockStateProperties.SLAB_TYPE, SlabType.TOP);
        if (role != null) {
            for (int u = u0 + 1; u <= u0 + 3; u++) {
                put(u, 0, vOut - step, counter);
            }
            spots.add(new Spot(role, at(u0 + 2, 0, vOut - 2 * step), yaw(side == 'L' ? 'R' : 'L')));
        }
        labels.add(new Label(at(u0 + 2, 3, vOut), title, color, 0.6f));
        // une tenture tissee dans la paroi du fond : rayures de laine, un motif au milieu
        int wall = vIn + (side == 'L' ? -1 : 1);
        BlockState[] stripes = {block("red_wool"), block("orange_wool"), block("brown_wool")};
        for (int u = u0 + 1; u <= u0 + 3; u++) {
            put(u, 2, wall, stripes[(u - u0) % 3]);
            put(u, 1, wall, u == u0 + 2 ? motif(color) : block("brown_wool"));
        }
    }

    /** Le motif d'une tenture, de la couleur du comptoir. */
    private BlockState motif(int color) {
        return switch (color) {
            case 0x4FB8FF -> block("light_blue_wool");
            case 0xC88BE8 -> block("purple_wool");
            case 0xD9A35C -> block("yellow_wool");
            default -> block("white_wool");
        };
    }

    /** Au fond : le couloir, le dortoir (des nattes en rangs), la reserve (tonneaux, jarres). */
    private void back() {
        room(37, 3, 39, 4, 3, tile);
        room(40, -3, 46, 10, 4, packed);
        BlockState mat = block("brown_carpet");
        BlockState pillow = block("white_carpet");
        for (int u = 41; u <= 45; u += 2) {
            for (int v = -2; v <= 1; v++) {
                put(u, 0, v, v == -2 ? pillow : mat);
            }
        }
        BlockState barrel = with(block("barrel"), BlockStateProperties.FACING, Direction.UP);
        for (int u = 41; u <= 45; u++) {
            put(u, 0, 10, barrel);
            if (u % 2 == 1) {
                put(u, 1, 10, barrel);
            }
        }
        put(46, 0, 8, block("decorated_pot"));
        put(46, 0, 6, block("decorated_pot"));
        put(43, 3, 4, lantern(true));
        put(41, 3, -1, lantern(true));
        put(45, 3, -1, lantern(true));
        put(43, 3, 8, lantern(true));
        put(46, 0, -3, candles(3));
        put(40, 0, -3, candles(2));
        for (int v = -2; v <= 2; v++) {
            put(47, 2, v, v % 2 == 0 ? block("orange_wool") : block("red_wool"));
            put(47, 1, v, block("brown_wool"));
        }
        // deux habitants qui vont et viennent dans la salle, un troisieme au dortoir
        spots.add(new Spot(SietchDweller.Role.DWELLER, at(22, 0, 1), yaw('F')));
        spots.add(new Spot(SietchDweller.Role.DWELLER, at(34, 0, 6), yaw('B')));
        spots.add(new Spot(SietchDweller.Role.DWELLER, at(43, 0, 5), yaw('L')));
    }

    private BlockState cauldron() {
        return with(block("water_cauldron"), BlockStateProperties.LEVEL_CAULDRON, 3);
    }

    private BlockState lantern(boolean hanging) {
        return with(block("lantern"), BlockStateProperties.HANGING, hanging);
    }

    private BlockState candles(int count) {
        return with(with(block("candle"), BlockStateProperties.CANDLES, count), BlockStateProperties.LIT, true);
    }
}

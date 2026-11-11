package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Une salle de donjon, construite par le mod dans la dimension des donjons.
 *
 * <p>Une boite fermee, decoree au theme du donjon ({@link RoomTheme}) : sol
 * a medaillon, murs a assise et couronnement scandes de piliers, fenetres
 * hautes ou filons, plafond a caissons d'ou pendent des lanternes, et de
 * quoi meubler — obstacles, plantes, rails. On entre par le nord (z minimal) ;
 * les portes s'ouvrent dans le mur sud une fois la salle finie, chacune vitree de
 * la couleur de ce qu'elle cache et surmontee d'un panneau.
 *
 * <p>Rien n'est conserve : la salle est effacee quand le palier se termine.
 */
final class DungeonRoom {

    /** Une porte : ce qu'elle ouvre, et la zone ou s'avancer pour la prendre. */
    record Door(RoomType type, AABB zone) {
    }

    private static final int FLAGS = Block.UPDATE_CLIENTS;

    final RoomType type;
    final BlockPos center;
    final int half;
    final int height;
    final AABB interior;
    final List<BlockPos> buttons = new ArrayList<>();
    private final List<int[]> forced = new ArrayList<>();

    private DungeonRoom(RoomType type, BlockPos center, int half, int height) {
        this.type = type;
        this.center = center;
        this.half = half;
        this.height = height;
        this.interior = new AABB(center.getX() - half, center.getY(), center.getZ() - half,
                center.getX() + half + 1, center.getY() + height, center.getZ() + half + 1);
    }

    /** Ou l'on apparait : au nord, face au sud. */
    Vec3 entrance() {
        return new Vec3(center.getX() + 0.5d, center.getY(), center.getZ() - half + 1.5d);
    }

    BlockPos chestPos() {
        return center;
    }

    int farWall() {
        return center.getZ() + half + 1;
    }

    /** Construit la salle, centre du sol en {@code center}. */
    static DungeonRoom build(ServerLevel level, Dungeon dungeon, RoomType type, BlockPos center, RandomSource random) {
        DungeonRoom room = new DungeonRoom(type, center, type.halfWidth(), type.height());
        room.place(level, RoomTheme.of(dungeon), random);
        return room;
    }

    /** Ce qui depasse des murs : les niches des fenetres. La salle l'efface avec le reste. */
    private static final int MARGIN = 2;

    /** Un pilier tous les six blocs le long des murs, une poutre au plafond dans son axe. */
    private static final int BAY = 6;

    private boolean onLine(int offset) {
        return Math.floorMod(offset, BAY) == 0;
    }

    private void place(ServerLevel level, RoomTheme theme, RandomSource random) {
        int cx = center.getX();
        int cz = center.getZ();
        int x0 = cx - half - 1;
        int x1 = cx + half + 1;
        int z0 = cz - half - 1;
        int z1 = cz + half + 1;
        int ground = center.getY();
        int floor = ground - 1;
        int ceiling = ground + height;
        // La salle tient ses chunks charges tant qu'elle existe : ses monstres
        // restent comptables et effacables quoi que fassent les joueurs.
        for (int chunkX = (x0 - MARGIN) >> 4; chunkX <= (x1 + MARGIN) >> 4; chunkX++) {
            for (int chunkZ = (z0 - MARGIN) >> 4; chunkZ <= (z1 + MARGIN) >> 4; chunkZ++) {
                level.setChunkForced(chunkX, chunkZ, true);
                forced.add(new int[]{chunkX, chunkZ});
                level.getChunk(chunkX, chunkZ);
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int dx = x - cx;
                int dz = z - cz;
                boolean edgeX = x == x0 || x == x1;
                boolean edgeZ = z == z0 || z == z1;
                set(level, pos.set(x, floor, z), floorBlock(theme, random, dx, dz));
                // Le plafond : une lumiere encastree au milieu de chaque caisson.
                boolean lamp = !edgeX && !edgeZ && Math.floorMod(dx, BAY) == BAY / 2
                        && Math.floorMod(dz, BAY) == BAY / 2;
                set(level, pos.set(x, ceiling, z), lamp ? theme.light() : theme.ceiling());
                if (!edgeX && !edgeZ) {
                    continue;
                }
                // Les murs : une assise, un couronnement, un pilier aux angles
                // et tous les six blocs avec une lumiere a mi-hauteur.
                boolean corner = edgeX && edgeZ;
                boolean pillar = corner || onLine(edgeX ? dz : dx);
                for (int y = ground; y < ceiling; y++) {
                    int h = y - ground;
                    Block block;
                    if (h == 0) {
                        block = theme.wallBase();
                    } else if (h == height - 1) {
                        block = theme.trim();
                    } else if (pillar) {
                        block = h == 3 && !corner ? theme.light() : theme.pillar();
                    } else {
                        int roll = random.nextInt(100);
                        block = roll < 18 ? theme.wallAlt()
                                : roll < 22 && !theme.veins().isEmpty() ? pick(theme.veins(), random) : theme.wall();
                    }
                    set(level, pos.set(x, y, z), block);
                }
            }
        }
        if (type == RoomType.COMBAT || type == RoomType.BOSS) {
            // Quatre piliers pour se mettre a couvert, et des obstacles du theme.
            int q = half / 2;
            for (int[] at : new int[][]{{q, q}, {-q, q}, {q, -q}, {-q, -q}}) {
                for (int y = ground; y < ceiling; y++) {
                    int h = y - ground;
                    set(level, pos.set(cx + at[0], y, cz + at[1]),
                            h == 0 ? theme.wallBase() : h == height - 1 ? theme.trim() : theme.pillar());
                }
            }
        }
        placeBeams(level, theme, cx, cz, ground, ceiling);
        placeWindows(level, theme, random, x0, x1, z0, ground);
        if (type == RoomType.COMBAT || type == RoomType.BOSS || type == RoomType.PUZZLE) {
            placeObstacles(level, theme, random, type == RoomType.BOSS ? 6 + random.nextInt(6)
                    : type == RoomType.PUZZLE ? 3 + random.nextInt(3) : 4 + random.nextInt(5));
        }
        placeGround(level, theme, random);
        placeHanging(level, theme, random, ceiling);
        if (type == RoomType.BOOST) {
            // L'autel, entre quatre lanternes.
            set(level, center, Blocks.ENCHANTING_TABLE);
            for (int[] at : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
                set(level, pos.set(cx + at[0], ground, cz + at[1]), theme.wallBase());
                set(level, pos.set(cx + at[0], ground + 1, cz + at[1]), theme.lantern());
            }
        }
        if (type == RoomType.MERCHANT) {
            // L'etal du marchand, qui se tient deux blocs au sud du centre : deux
            // poteaux, un auvent, un comptoir ouvert en son milieu.
            for (int side = -1; side <= 1; side += 2) {
                for (int h = 0; h < 3; h++) {
                    set(level, pos.set(cx + 2 * side, ground + h, cz + 3), theme.pillar());
                }
                set(level, pos.set(cx + side, ground, cz + 1), theme.trim());
                set(level, pos.set(cx + 2 * side, ground, cz + 1), theme.trim());
                set(level, pos.set(cx + 2 * side, ground + 1, cz + 1), theme.lantern());
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = 2; dz <= 3; dz++) {
                    level.setBlock(pos.set(cx + dx, ground + 3, cz + dz), axis(theme.beam(), Direction.Axis.X), FLAGS);
                }
            }
        }
    }

    /**
     * Le sol : une bordure, un medaillon au centre (un disque et un anneau),
     * et un appareil irregulier ou affleure parfois une lumiere.
     */
    private Block floorBlock(RoomTheme theme, RandomSource random, int dx, int dz) {
        if (Math.max(Math.abs(dx), Math.abs(dz)) >= half) {
            return theme.floorEdge();
        }
        int distance = dx * dx + dz * dz;
        if (distance <= 2) {
            return theme.medallion();
        }
        if (distance >= 9 && distance <= 13) {
            return theme.floorEdge();
        }
        int roll = random.nextInt(100);
        if (roll < 22) {
            return theme.floorAlt();
        }
        if (roll < 28) {
            return theme.floorAccent();
        }
        return roll < 30 && theme.floorLight() != null ? theme.floorLight() : theme.floor();
    }

    /**
     * Le plafond a caissons : une poutre sous le plafond dans l'axe de chaque
     * pilier, et une lanterne pendue a chaque croisement, au bout d'une
     * chaine d'autant plus longue que la salle est haute.
     */
    private void placeBeams(ServerLevel level, RoomTheme theme, int cx, int cz, int ground, int ceiling) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int chain = height >= 12 ? 3 : height >= 8 ? 1 : 0;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                boolean alongX = onLine(dz);
                boolean alongZ = onLine(dx);
                if (!alongX && !alongZ) {
                    continue;
                }
                pos.set(cx + dx, ceiling - 1, cz + dz);
                if (!level.getBlockState(pos).isAir()) {
                    continue;
                }
                if (alongX && alongZ) {
                    set(level, pos, theme.trim());
                    for (int link = 1; link <= chain; link++) {
                        set(level, pos.set(cx + dx, ceiling - 1 - link, cz + dz), Blocks.IRON_CHAIN);
                    }
                    level.setBlock(pos.set(cx + dx, ceiling - 2 - chain, cz + dz), RoomTheme.with(
                            theme.lantern().defaultBlockState(), BlockStateProperties.HANGING, true), FLAGS);
                } else {
                    level.setBlock(pos, axis(theme.beam(), alongX ? Direction.Axis.X : Direction.Axis.Z), FLAGS);
                }
            }
        }
    }

    private static BlockState axis(Block block, Direction.Axis axis) {
        return RoomTheme.with(block.defaultBlockState(), BlockStateProperties.AXIS, axis);
    }

    /**
     * Les fenetres hautes des murs nord, est et ouest, une par travee : trois
     * blocs de vitrage sur deux, une niche derriere et une lumiere au fond.
     * Un theme sans vitrage a des plaques de filon a la place. Le mur sud, ou
     * s'ouvrent les portes, reste nu.
     */
    private void placeWindows(ServerLevel level, RoomTheme theme, RandomSource random, int x0, int x1, int z0,
                              int ground) {
        if (theme.window() == null && theme.veins().isEmpty()) {
            return;
        }
        int sill = ground + (height >= 8 ? 3 : 2);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int offset = -half + BAY / 2; offset <= half - BAY / 2; offset++) {
            if (Math.floorMod(offset, BAY) != BAY / 2) {
                continue;
            }
            // Chaque mur : son plan, et le pas qui s'en eloigne vers l'exterieur.
            int[][] walls = {
                    {center.getX() + offset, z0, 1, 0, 0, -1},
                    {x0, center.getZ() + offset, 0, 1, -1, 0},
                    {x1, center.getZ() + offset, 0, 1, 1, 0},
            };
            for (int[] wall : walls) {
                for (int along = -2; along <= 2; along++) {
                    for (int up = -1; up <= 2; up++) {
                        int x = wall[0] + wall[2] * along;
                        int z = wall[1] + wall[3] * along;
                        boolean opening = Math.abs(along) <= 1 && up >= 0 && up <= 1;
                        if (theme.window() == null) {
                            if (opening) {
                                set(level, pos.set(x, sill + up, z), pick(theme.veins(), random));
                            }
                            continue;
                        }
                        if (opening) {
                            set(level, pos.set(x, sill + up, z), theme.window());
                        }
                        // La niche : fermee de toutes parts, garnie au milieu.
                        BlockState inside = theme.wallBase().defaultBlockState();
                        if (opening) {
                            List<BlockState> behind = theme.behind();
                            inside = up == 0 && along != 0 && behind.size() > 1
                                    ? behind.get(1 + random.nextInt(behind.size() - 1)) : behind.get(0);
                        }
                        level.setBlock(pos.set(x + wall[4], sill + up, z + wall[5]), inside, FLAGS);
                        set(level, pos.set(x + 2 * wall[4], sill + up, z + 2 * wall[5]),
                                along == 0 && up == 0 ? theme.light() : theme.wallBase());
                    }
                }
            }
        }
    }

    /** Des obstacles du theme, parfois empiles, parfois coiffes. */
    private void placeObstacles(ServerLevel level, RoomTheme theme, RandomSource random, int count) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < count; i++) {
            int x = center.getX() - half + 1 + random.nextInt(2 * half - 1);
            int z = center.getZ() - half + 4 + random.nextInt(2 * half - 4);
            if (Math.abs(x - center.getX()) <= 3 && Math.abs(z - center.getZ()) <= 3) {
                continue;
            }
            if (!level.getBlockState(pos.set(x, center.getY(), z)).isAir()) {
                continue;
            }
            Block block = pick(theme.obstacles(), random);
            int stack = random.nextInt(3) == 0 ? 2 : 1;
            for (int h = 0; h < stack; h++) {
                set(level, pos.set(x, center.getY() + h, z), block);
            }
            if (theme.obstacleTop() != null && random.nextInt(3) == 0) {
                level.setBlock(pos.set(x, center.getY() + stack, z), theme.obstacleTop(), FLAGS);
            }
        }
    }

    /** Au sol : des plantes sur leur terre, de la mousse, une voie ferree. */
    private void placeGround(ServerLevel level, RoomTheme theme, RandomSource random) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        if (theme.soil() != null) {
            for (int dx = -half + 1; dx <= half - 1; dx++) {
                for (int dz = -half + 1; dz <= half - 1; dz++) {
                    if (dx * dx + dz * dz <= 16 || !level.getBlockState(
                            pos.set(center.getX() + dx, center.getY(), center.getZ() + dz)).isAir()) {
                        continue;
                    }
                    int roll = random.nextInt(100);
                    if (roll < 7 && !theme.plants().isEmpty()) {
                        Block plant = pick(theme.plants(), random);
                        set(level, pos.set(center.getX() + dx, center.getY() - 1, center.getZ() + dz), theme.soil());
                        set(level, pos.set(center.getX() + dx, center.getY(), center.getZ() + dz), plant);
                    } else if (roll < 12 && theme.carpet() != null) {
                        set(level, pos, theme.carpet());
                    }
                }
            }
        }
        if (theme.rails() && half >= 8) {
            int x = center.getX() + BAY / 2;
            for (int dz = -half; dz <= half; dz++) {
                if (level.getBlockState(pos.set(x, center.getY(), center.getZ() + dz)).isAir()) {
                    set(level, pos, Blocks.RAIL);
                }
            }
        }
    }

    /** Ce qui pend du plafond, entre les poutres. */
    private void placeHanging(ServerLevel level, RoomTheme theme, RandomSource random, int ceiling) {
        if (theme.hanging().isEmpty()) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                if (onLine(dx) || onLine(dz) || random.nextInt(100) >= 4) {
                    continue;
                }
                if (level.getBlockState(pos.set(center.getX() + dx, ceiling - 1, center.getZ() + dz)).isAir()) {
                    level.setBlock(pos, theme.hanging().get(random.nextInt(theme.hanging().size())), FLAGS);
                }
            }
        }
    }

    private static Block pick(List<Block> blocks, RandomSource random) {
        return blocks.get(random.nextInt(blocks.size()));
    }

    /**
     * Des emplacements libres pour des jarres : contre les murs nord, est et
     * ouest, loin de l'entree et les unes des autres.
     */
    List<BlockPos> jarSpots(ServerLevel level, RandomSource random, int count) {
        List<BlockPos> spots = new ArrayList<>();
        for (int attempt = 0; attempt < 80 && spots.size() < count; attempt++) {
            int along = -half + 2 + random.nextInt(2 * half - 3);
            int side = random.nextInt(3);
            if (side == 2 && Math.abs(along) <= 3) {
                continue;
            }
            BlockPos spot = switch (side) {
                case 0 -> new BlockPos(center.getX() - half + 1, center.getY(), center.getZ() + along);
                case 1 -> new BlockPos(center.getX() + half - 1, center.getY(), center.getZ() + along);
                default -> new BlockPos(center.getX() + along, center.getY(), center.getZ() - half + 1);
            };
            if (!level.getBlockState(spot).isAir() || !level.getBlockState(spot.above()).isAir()) {
                continue;
            }
            boolean crowded = false;
            for (BlockPos other : spots) {
                crowded |= other.distManhattan(spot) < 3;
            }
            if (!crowded) {
                spots.add(spot);
            }
        }
        return spots;
    }

    /** Les boutons de l'enigme, sur la face interieure des murs est, ouest et sud. */
    void placeButtons(ServerLevel level, Dungeon.Palette palette, RandomSource random, int count) {
        int attempts = 0;
        while (buttons.size() < count && attempts++ < 200) {
            int side = random.nextInt(3);
            int along = -half + 1 + random.nextInt(2 * half - 1);
            int y = center.getY() + random.nextInt(3);
            BlockPos button;
            Direction facing;
            switch (side) {
                case 0 -> {
                    button = new BlockPos(center.getX() + half, y, center.getZ() + along);
                    facing = Direction.WEST;
                }
                case 1 -> {
                    button = new BlockPos(center.getX() - half, y, center.getZ() + along);
                    facing = Direction.EAST;
                }
                default -> {
                    button = new BlockPos(center.getX() + along, y, center.getZ() + half);
                    facing = Direction.NORTH;
                }
            }
            if (!level.getBlockState(button).isAir() || buttons.contains(button)) {
                continue;
            }
            BlockState state = palette.button().defaultBlockState()
                    .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            level.setBlock(button, state, FLAGS);
            buttons.add(button);
        }
    }

    /**
     * Ouvre les portes dans le mur sud : une a trois, reparties sur sa
     * largeur, chacune de 3 x 4 vitree de la couleur de sa salle, avec un
     * panneau au-dessus.
     */
    List<Door> openDoors(ServerLevel level, List<RoomType> options) {
        List<Door> doors = new ArrayList<>();
        int wall = farWall();
        int spacing = Math.min(7, Math.max(4, half - 2));
        for (int index = 0; index < options.size(); index++) {
            RoomType option = options.get(index);
            int offset = (int) Math.round((index - (options.size() - 1) / 2.0d) * spacing);
            int x = center.getX() + offset;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy < 4; dy++) {
                    set(level, new BlockPos(x + dx, center.getY() + dy, wall), option.glass());
                }
            }
            BlockPos signPos = new BlockPos(x, center.getY() + 4, wall - 1);
            level.setBlock(signPos, Blocks.OAK_WALL_SIGN.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), Block.UPDATE_ALL);
            if (level.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
                SignText text = new SignText()
                        .setMessage(1, Component.literal(option.label()).withStyle(option.color(), ChatFormatting.BOLD))
                        .setHasGlowingText(true);
                sign.setText(text, true);
                sign.setWaxed(true);
                sign.setChanged();
                level.sendBlockUpdated(signPos, sign.getBlockState(), sign.getBlockState(), Block.UPDATE_ALL);
            }
            doors.add(new Door(option, new AABB(x - 1, center.getY(), wall - 1.4d, x + 2, center.getY() + 3, wall)));
        }
        return doors;
    }

    /** Un point du sol libre pour faire apparaitre un monstre, loin de l'entree. */
    BlockPos spawnPoint(ServerLevel level, RandomSource random) {
        for (int attempt = 0; attempt < 30; attempt++) {
            int x = center.getX() - half + 1 + random.nextInt(2 * half - 1);
            int z = center.getZ() - half + 6 + random.nextInt(Math.max(1, 2 * half - 6));
            BlockPos pos = new BlockPos(x, center.getY(), z);
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                    && level.getBlockState(pos.above(2)).isAir()) {
                return pos;
            }
        }
        return center.offset(0, 0, half / 2);
    }

    /** Efface la salle : tout ce qui n'est pas de l'air dans sa boite exterieure. */
    void erase(ServerLevel level) {
        if (forced.isEmpty()) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int reach = half + 1 + MARGIN;
        for (int x = center.getX() - reach; x <= center.getX() + reach; x++) {
            for (int z = center.getZ() - reach; z <= center.getZ() + reach; z++) {
                for (int y = center.getY() - 1; y <= center.getY() + height; y++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.removeBlockEntity(pos);
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                }
            }
        }
        for (int[] chunk : forced) {
            level.setChunkForced(chunk[0], chunk[1], false);
        }
        forced.clear();
    }

    AABB outer() {
        return interior.inflate(1.0d);
    }

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), FLAGS);
    }
}

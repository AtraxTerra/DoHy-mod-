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
 * <p>Une boite fermee aux blocs du theme : sol en damier, murs scandes de
 * piliers et de lumieres, plafond eclaire. On entre par le nord (z minimal) ;
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
        room.place(level, dungeon.palette(), random);
        return room;
    }

    private void place(ServerLevel level, Dungeon.Palette palette, RandomSource random) {
        int x0 = center.getX() - half - 1;
        int x1 = center.getX() + half + 1;
        int z0 = center.getZ() - half - 1;
        int z1 = center.getZ() + half + 1;
        int floor = center.getY() - 1;
        int ceiling = center.getY() + height;
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edgeX = x == x0 || x == x1;
                boolean edgeZ = z == z0 || z == z1;
                // Sol en damier, plafond eclaire tous les cinq blocs.
                set(level, pos.set(x, floor, z), (x + z & 1) == 0 ? palette.floor() : palette.floorAlt());
                boolean lamp = !edgeX && !edgeZ && Math.floorMod(x - center.getX(), 5) == 0
                        && Math.floorMod(z - center.getZ(), 5) == 0;
                set(level, pos.set(x, ceiling, z), lamp ? palette.light() : palette.ceiling());
                if (!edgeX && !edgeZ) {
                    continue;
                }
                // Murs : un pilier aux angles et tous les six blocs, une lumiere a mi-hauteur.
                boolean pillar = (edgeX && edgeZ) || Math.floorMod(edgeX ? z - center.getZ() : x - center.getX(), 6) == 0;
                for (int y = center.getY(); y < ceiling; y++) {
                    Block block = pillar ? palette.pillar() : palette.wall();
                    if (pillar && y == center.getY() + 3 && !(edgeX && edgeZ)) {
                        block = palette.light();
                    }
                    set(level, pos.set(x, y, z), block);
                }
            }
        }
        if (type == RoomType.COMBAT || type == RoomType.BOSS) {
            // Quatre piliers pour se mettre a couvert, et quelques obstacles du theme.
            int q = half / 2;
            for (int[] corner : new int[][]{{q, q}, {-q, q}, {q, -q}, {-q, -q}}) {
                for (int y = center.getY(); y < ceiling; y++) {
                    set(level, pos.set(center.getX() + corner[0], y, center.getZ() + corner[1]), palette.pillar());
                }
            }
            int obstacles = 4 + random.nextInt(5);
            for (int i = 0; i < obstacles; i++) {
                int x = center.getX() - half + 1 + random.nextInt(2 * half - 1);
                int z = center.getZ() - half + 4 + random.nextInt(2 * half - 4);
                if (Math.abs(x - center.getX()) <= 1 && Math.abs(z - center.getZ()) <= 1) {
                    continue;
                }
                set(level, pos.set(x, center.getY(), z), palette.decor());
            }
        }
        if (type == RoomType.BOOST) {
            set(level, center, Blocks.ENCHANTING_TABLE);
        }
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
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - half - 1; x <= center.getX() + half + 1; x++) {
            for (int z = center.getZ() - half - 1; z <= center.getZ() + half + 1; z++) {
                for (int y = center.getY() - 1; y <= center.getY() + height; y++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.removeBlockEntity(pos);
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                }
            }
        }
    }

    AABB outer() {
        return interior.inflate(1.0d);
    }

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), FLAGS);
    }
}

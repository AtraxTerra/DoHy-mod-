package fr.atraxterra.dohy.faction.event;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * La Warzone : la dimension des events de faction (lot 22f).
 *
 * <p>Sur Paladium, on gagne l'arene de sa ligue par le portail de la Warzone. Ici,
 * {@code palamod:warzone} est une dimension vide (biome {@code the_void} : aucun
 * monstre n'y apparait). Chaque event a son arene, batie en dur la premiere fois
 * qu'il se lance, puis reutilisee ; un bloc temoin sous le centre dit qu'elle
 * existe deja.
 *
 * <ul>
 *   <li><b>Boss</b> (0, 0) : un disque de 34 blocs de rayon en briques de pierre
 *       noire, rempart crenele, huit piliers de lanternes, portes grillagees.</li>
 *   <li><b>KOTH</b> (1 000, 0) : la meme arene en 26 blocs, avec au centre une
 *       colline a trois gradins ; la zone de capture est son sommet.</li>
 *   <li><b>Egg Hunt</b> (0, 1 000) : l'ile volcanique de Volcania, 40 blocs de
 *       rayon, bordee d'un muret ; au centre l'autel d'obsidienne ou l'oeuf
 *       apparait, quatre bassins de lave autour.</li>
 *   <li><b>Totem</b> (-1 000, 0) : une grotte en dome de 24 blocs de rayon, que
 *       l'on gagne par un long escalier depuis la surface — le deroule du wiki.
 *       Au centre, le totem de douze blocs, en bedrock hors event ; au nord, la
 *       zone de depot.</li>
 * </ul>
 */
public final class EventArena {

    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "warzone"));

    public static final BlockPos BOSS_CENTER = new BlockPos(0, 100, 0);
    public static final int BOSS_RADIUS = 34;

    public static final BlockPos KOTH_CENTER = new BlockPos(1000, 100, 0);
    public static final int KOTH_RADIUS = 26;
    /** La zone de capture : le sommet de la colline, a trois blocs du sol. */
    public static final BlockPos KOTH_ZONE = KOTH_CENTER.above(3);
    public static final double KOTH_ZONE_RADIUS = 3.5d;

    public static final BlockPos TOTEM_CENTER = new BlockPos(-1000, 100, 0);
    public static final int TOTEM_RADIUS = 24;
    public static final int TOTEM_HEIGHT = 12;
    public static final BlockPos TOTEM_DEPOSIT = TOTEM_CENTER.offset(0, 0, -15);
    public static final double TOTEM_DEPOSIT_RADIUS = 3.5d;
    /** Le haut de l'escalier, ou l'on arrive. */
    private static final int STAIR_TOP = 21;
    private static final int STAIR_START_Z = 36;

    public static final BlockPos EGG_CENTER = new BlockPos(0, 100, 1000);
    public static final int EGG_RADIUS = 40;
    /** Ou l'oeuf attend : au sommet de l'autel d'obsidienne. */
    public static final BlockPos EGG_ALTAR = EGG_CENTER.above(4);

    private static final BlockState MARKER = Blocks.LODESTONE.defaultBlockState();
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private EventArena() {
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(DIMENSION);
    }

    /**
     * Le rayon du ticket de chargement. Un ticket de rayon r ne fait vivre les
     * entites (niveau 31) qu'a r - 2 chunks du centre : avec 3, Volcania se
     * figeait des qu'il s'eloignait d'un chunk de l'autel (verifie en jeu : il
     * avait 181 ticks d'age quand le serveur en comptait 811). Avec 7, tout vit
     * a 5 chunks (80 blocs) : arenes, ile, et le vol du dragon.
     */
    private static final int HOLD_RADIUS = 7;

    /** Garde l'arene vivante pendant l'event : les rivaux s'y battent meme en ton absence. */
    public static void hold(ServerLevel level, BlockPos center, boolean on) {
        ChunkPos chunk = ChunkPos.containing(center);
        if (on) {
            level.getChunkSource().addTicketWithRadius(TicketType.FORCED, chunk, HOLD_RADIUS);
        } else {
            level.getChunkSource().removeTicketWithRadius(TicketType.FORCED, chunk, HOLD_RADIUS);
        }
    }

    // -- Arenes rondes (Boss, KOTH) ---------------------------------------------

    /** Le parvis sud d'une arene ronde. */
    public static Vec3 ringEntry(BlockPos center, int radius) {
        return Vec3.atBottomCenterOf(center.offset(0, 1, radius - 4));
    }

    /** Les trois portes des factions rivales : est, ouest, nord. */
    public static Vec3 ringGate(BlockPos center, int radius, int index) {
        int r = radius - 4;
        BlockPos pos = switch (index % 3) {
            case 0 -> center.offset(r, 1, 0);
            case 1 -> center.offset(-r, 1, 0);
            default -> center.offset(0, 1, -r);
        };
        return Vec3.atBottomCenterOf(pos);
    }

    public static void buildBossArena(ServerLevel level) {
        buildRing(level, BOSS_CENTER, BOSS_RADIUS);
    }

    /** L'arene du KOTH et sa colline : trois gradins, le sommet dore. */
    public static void buildKothArena(ServerLevel level) {
        if (level.getBlockState(KOTH_CENTER.below(2)).is(MARKER.getBlock())) {
            return;
        }
        buildRing(level, KOTH_CENTER, KOTH_RADIUS);
        BlockState step = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState edge = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int height = d <= KOTH_ZONE_RADIUS ? 3 : d <= 5.5d ? 2 : d <= 7.5d ? 1 : 0;
                for (int y = 1; y <= height; y++) {
                    BlockState state = y == height && d > KOTH_ZONE_RADIUS - 0.6d && d <= KOTH_ZONE_RADIUS
                            ? Blocks.GOLD_BLOCK.defaultBlockState() : y == height ? edge : step;
                    if (height == 3 && y == 3 && d <= KOTH_ZONE_RADIUS - 0.6d) {
                        state = Blocks.RAW_GOLD_BLOCK.defaultBlockState();
                    }
                    level.setBlock(KOTH_CENTER.offset(dx, y, dz), state, FLAGS);
                }
            }
        }
        // Un phare au centre du sommet : on voit la zone de loin.
        level.setBlock(KOTH_ZONE, Blocks.BEACON.defaultBlockState(), FLAGS);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(KOTH_ZONE.offset(dx, -1, dz), Blocks.GOLD_BLOCK.defaultBlockState(), FLAGS);
            }
        }
    }

    /**
     * Une arene ronde : disque de briques de pierre noire, cercles dores, rempart
     * crenele de six blocs, huit piliers de lanternes, quatre portes grillagees.
     * Une seule fois par monde : quelques milliers de blocs dans des chunks
     * vides, sans mise a jour des voisins.
     */
    private static void buildRing(ServerLevel level, BlockPos c, int r) {
        if (level.getBlockState(c.below(2)).is(MARKER.getBlock())) {
            return;
        }
        BlockState floor = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState under = Blocks.BLACKSTONE.defaultBlockState();
        BlockState gold = Blocks.GILDED_BLACKSTONE.defaultBlockState();
        BlockState chiseled = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        BlockState light = Blocks.SHROOMLIGHT.defaultBlockState();
        for (int dx = -r - 2; dx <= r + 2; dx++) {
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 2.5d) {
                    continue;
                }
                BlockPos at = c.offset(dx, 0, dz);
                level.setBlock(at.below(), under, FLAGS);
                if (d <= r + 0.5d) {
                    BlockState top = floor;
                    int ring = (int) Math.round(d);
                    if (ring == 8 || ring == r - 14) {
                        top = gold;
                    } else if (ring == 0 || (ring % 6 == 3 && (dx == 0 || dz == 0))) {
                        top = chiseled;
                    } else if (ring % 7 == 5 && ((dx + dz) & 3) == 0) {
                        top = light;
                    }
                    level.setBlock(at, top, FLAGS);
                    continue;
                }
                level.setBlock(at, floor, FLAGS);
                int height = ((dx + dz) & 1) == 0 ? 7 : 6;
                for (int y = 1; y <= height; y++) {
                    level.setBlock(at.above(y), (dx * 7 + dz * 3 + y) % 5 == 0 ? cracked : wall, FLAGS);
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0d * i / 8.0d + Math.PI / 8.0d;
            BlockPos base = c.offset((int) Math.round(Math.cos(angle) * (r - 4)), 0,
                    (int) Math.round(Math.sin(angle) * (r - 4)));
            for (int y = 1; y <= 5; y++) {
                level.setBlock(base.above(y), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), FLAGS);
            }
            level.setBlock(base.above(6), light, FLAGS);
            level.setBlock(base.above(7), Blocks.LANTERN.defaultBlockState(), FLAGS);
        }
        // Les quatre portes, grillagees : au-dela du rempart, c'est le vide.
        for (int i = 0; i < 4; i++) {
            int sx = i == 0 ? 1 : i == 1 ? -1 : 0;
            int sz = i == 2 ? -1 : i == 3 ? 1 : 0;
            BlockState bars = Blocks.IRON_BARS.defaultBlockState()
                    .setValue(BlockStateProperties.NORTH, sx != 0).setValue(BlockStateProperties.SOUTH, sx != 0)
                    .setValue(BlockStateProperties.EAST, sz != 0).setValue(BlockStateProperties.WEST, sz != 0);
            for (int w = -2; w <= 2; w++) {
                for (int d = r + 1; d <= r + 2; d++) {
                    BlockPos at = c.offset(sx * d + (sz != 0 ? w : 0), 0, sz * d + (sx != 0 ? w : 0));
                    for (int y = 1; y <= 4; y++) {
                        level.setBlock(at.above(y), bars, FLAGS);
                    }
                }
            }
        }
        level.setBlock(c.below(2), MARKER, FLAGS);
    }

    // -- L'ile de Volcania (Egg Hunt) -------------------------------------------

    /** Le bord sud de l'ile, face a l'autel. */
    public static Vec3 eggEntry() {
        return Vec3.atBottomCenterOf(EGG_CENTER.offset(0, 1, EGG_RADIUS - 6));
    }

    /** Les rivaux debarquent a l'est, a l'ouest et au nord. */
    public static Vec3 eggGate(int index) {
        int r = EGG_RADIUS - 7;
        BlockPos pos = switch (index % 3) {
            case 0 -> EGG_CENTER.offset(r, 1, 0);
            case 1 -> EGG_CENTER.offset(-r, 1, 0);
            default -> EGG_CENTER.offset(0, 1, -r);
        };
        return Vec3.atBottomCenterOf(pos);
    }

    /** Le rayon de l'ile dans une direction : un bord ondule. */
    private static double eggEdge(double angle) {
        return EGG_RADIUS + 2.5d * Math.sin(angle * 5.0d) + 1.5d * Math.cos(angle * 3.0d);
    }

    /** Vrai si la position est sur l'ile (en dedans du muret). */
    public static boolean onEggIsland(BlockPos pos) {
        double dx = pos.getX() - EGG_CENTER.getX();
        double dz = pos.getZ() - EGG_CENTER.getZ();
        return Math.sqrt(dx * dx + dz * dz) < eggEdge(Math.atan2(dz, dx)) - 1.5d
                && pos.getY() >= EGG_CENTER.getY() - 2 && pos.getY() <= EGG_CENTER.getY() + 40;
    }

    public static void buildEggArena(ServerLevel level) {
        BlockPos c = EGG_CENTER;
        if (level.getBlockState(c.below(2)).is(MARKER.getBlock())) {
            return;
        }
        BlockState blackstone = Blocks.BLACKSTONE.defaultBlockState();
        BlockState basalt = Blocks.BASALT.defaultBlockState();
        BlockState smooth = Blocks.SMOOTH_BASALT.defaultBlockState();
        BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
        BlockState light = Blocks.SHROOMLIGHT.defaultBlockState();
        int reach = EGG_RADIUS + 5;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                double edge = eggEdge(Math.atan2(dz, dx));
                if (d > edge + 0.5d) {
                    continue;
                }
                int noise = Math.floorMod(dx * 73 + dz * 37 + dx * dz, 11);
                // Le dessous : un cone renverse de basalte, veine de magma.
                int depth = 2 + (int) Math.round((edge - d) * 0.7d);
                for (int y = 1; y <= depth; y++) {
                    level.setBlock(c.offset(dx, -y, dz), noise == 3 && y > 2 ? magma : y % 3 == 0 ? basalt
                            : blackstone, FLAGS);
                }
                BlockPos top = c.offset(dx, 0, dz);
                if (d > edge - 1.5d) {
                    // Le muret du bord : deux blocs, on ne tombe pas par megarde.
                    level.setBlock(top, blackstone, FLAGS);
                    level.setBlock(top.above(), basalt, FLAGS);
                    level.setBlock(top.above(2), noise < 4 ? basalt : Blocks.BLACKSTONE_SLAB.defaultBlockState(),
                            FLAGS);
                    continue;
                }
                boolean lamp = Math.floorMod(dx + 2, 7) == 0 && Math.floorMod(dz + 2, 7) == 0 && d > 6.0d;
                level.setBlock(top, lamp ? light : noise < 3 ? smooth : noise < 5 ? basalt : blackstone, FLAGS);
            }
        }
        // L'autel : trois gradins d'obsidienne, l'oeuf au sommet.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(c.offset(dx, 1, dz), Blocks.OBSIDIAN.defaultBlockState(), FLAGS);
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    level.setBlock(c.offset(dx, 2, dz), Blocks.CRYING_OBSIDIAN.defaultBlockState(), FLAGS);
                }
            }
        }
        level.setBlock(c.above(3), Blocks.OBSIDIAN.defaultBlockState(), FLAGS);
        // Quatre bassins de lave autour de l'autel, sertis d'un rebord.
        for (int i = 0; i < 4; i++) {
            BlockPos basin = c.offset(i % 2 == 0 ? 10 : -10, 0, i < 2 ? 10 : -10);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    BlockPos at = basin.offset(dx, 0, dz);
                    level.setBlock(at.below(), magma, FLAGS);
                    level.setBlock(at, rim ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                            : Blocks.LAVA.defaultBlockState(), FLAGS);
                    if (rim) {
                        level.setBlock(at.above(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), FLAGS);
                    }
                }
            }
        }
        level.setBlock(c.below(2), MARKER, FLAGS);
    }

    // -- La grotte du Totem -----------------------------------------------------

    /** En haut de l'escalier, face au nord. */
    public static Vec3 totemEntry() {
        return Vec3.atBottomCenterOf(TOTEM_CENTER.offset(0, STAIR_TOP + 1, STAIR_START_Z + 2));
    }

    /** Les rivaux entrent par les flancs est, ouest, et le fond nord-ouest. */
    public static Vec3 totemGate(int index) {
        BlockPos pos = switch (index % 3) {
            case 0 -> TOTEM_CENTER.offset(19, 1, 3);
            case 1 -> TOTEM_CENTER.offset(-19, 1, 3);
            default -> TOTEM_CENTER.offset(-12, 1, -16);
        };
        return Vec3.atBottomCenterOf(pos);
    }

    /** La hauteur du plafond de la grotte a une distance du centre. */
    private static int ceiling(double d) {
        double ratio = Math.min(1.0d, d / TOTEM_RADIUS);
        return Math.max(5, (int) Math.round(18.0d * Math.sqrt(1.0d - ratio * ratio)));
    }

    public static void buildTotemArena(ServerLevel level) {
        BlockPos c = TOTEM_CENTER;
        if (level.getBlockState(c.below(2)).is(MARKER.getBlock())) {
            return;
        }
        int r = TOTEM_RADIUS;
        BlockState rock = Blocks.DEEPSLATE.defaultBlockState();
        BlockState cobbled = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        BlockState tuff = Blocks.TUFF.defaultBlockState();
        BlockState floor = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState cracked = Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
        BlockState light = Blocks.SHROOMLIGHT.defaultBlockState();
        for (int dx = -r - 2; dx <= r + 2; dx++) {
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 2.5d) {
                    continue;
                }
                BlockPos at = c.offset(dx, 0, dz);
                int noise = Math.floorMod(dx * 31 + dz * 17, 7);
                BlockState stone = noise == 0 ? tuff : noise < 3 ? cobbled : rock;
                level.setBlock(at.below(), rock, FLAGS);
                // Le sol s'eclaire d'une grille de grenouilleres, hors totem, zone de depot et escalier.
                boolean floorLight = Math.floorMod(dx + 3, 6) == 0 && Math.floorMod(dz + 3, 6) == 0 && d > 3.0d
                        && d < r - 3.0d && TOTEM_DEPOSIT.distSqr(at) > 36.0d && !(Math.abs(dx) <= 3 && dz > 10);
                level.setBlock(at, floorLight ? Blocks.OCHRE_FROGLIGHT.defaultBlockState()
                        : noise == 4 ? cracked : floor, FLAGS);
                if (d > r - 1.5d) {
                    for (int y = 1; y <= ceiling(r - 1.5d) + 2; y++) {
                        level.setBlock(at.above(y), stone, FLAGS);
                    }
                    continue;
                }
                int top = ceiling(d);
                boolean ceilingLight = Math.floorMod(dx, 4) == 0 && Math.floorMod(dz, 4) == 0;
                level.setBlock(at.above(top), ceilingLight ? light : stone, FLAGS);
                level.setBlock(at.above(top + 1), rock, FLAGS);
            }
        }
        // Le socle du totem, et quatre lanternes aux angles.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(c.offset(dx, 0, dz), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), FLAGS);
            }
        }
        for (int i = 0; i < 4; i++) {
            BlockPos post = c.offset(i % 2 == 0 ? 3 : -3, 0, i < 2 ? 3 : -3);
            level.setBlock(post.above(), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), FLAGS);
            level.setBlock(post.above(2), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), FLAGS);
            level.setBlock(post.above(3), Blocks.LANTERN.defaultBlockState(), FLAGS);
        }
        // La zone de depot : un disque d'or brut cercle d'or, quatre lanternes.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= TOTEM_DEPOSIT_RADIUS) {
                    level.setBlock(TOTEM_DEPOSIT.offset(dx, 0, dz), d > TOTEM_DEPOSIT_RADIUS - 1.0d
                            ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.RAW_GOLD_BLOCK.defaultBlockState(), FLAGS);
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            BlockPos post = TOTEM_DEPOSIT.offset(i < 2 ? (i == 0 ? 4 : -4) : 0, 0, i >= 2 ? (i == 2 ? 4 : -4) : 0);
            level.setBlock(post.above(), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), FLAGS);
            level.setBlock(post.above(2), Blocks.LANTERN.defaultBlockState(), FLAGS);
        }
        // Le long escalier : de la surface, plein sud, jusqu'au sol de la grotte.
        BlockState stair = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.NORTH);
        for (int step = 0; step <= STAIR_TOP; step++) {
            int y = STAIR_TOP - step;
            int z = STAIR_START_Z - step;
            double d = Math.abs(z);
            boolean inside = d < r - 1.5d && y + 5 < ceiling(d);
            for (int dx = -2; dx <= 2; dx++) {
                BlockPos at = c.offset(dx, y, z);
                if (Math.abs(dx) == 2) {
                    if (!inside) {
                        for (int h = 0; h <= 5; h++) {
                            level.setBlock(at.above(h), rock, FLAGS);
                        }
                    } else {
                        level.setBlock(at.above(), Blocks.DEEPSLATE_TILE_WALL.defaultBlockState(), FLAGS);
                    }
                    continue;
                }
                level.setBlock(at, y == 0 ? floor : stair, FLAGS);
                if (y > 0) {
                    level.setBlock(at.below(), rock, FLAGS);
                }
                for (int h = 1; h <= 4; h++) {
                    level.setBlock(at.above(h), Blocks.AIR.defaultBlockState(), FLAGS);
                }
                if (!inside) {
                    level.setBlock(at.above(5), step % 3 == 0 ? light : rock, FLAGS);
                }
            }
        }
        // Le palier d'arrivee, en haut, et sa lanterne.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = 1; dz <= 4; dz++) {
                BlockPos at = c.offset(dx, STAIR_TOP, STAIR_START_Z + dz);
                level.setBlock(at, Math.abs(dx) == 2 || dz == 4 ? rock : floor, FLAGS);
                for (int h = 1; h <= 4; h++) {
                    level.setBlock(at.above(h), Math.abs(dx) == 2 || dz == 4 ? rock : Blocks.AIR.defaultBlockState(),
                            FLAGS);
                }
                level.setBlock(at.above(5), dx == 0 && dz == 2 ? light : rock, FLAGS);
            }
        }
        setTotem(level, 0, false);
        level.setBlock(c.below(2), MARKER, FLAGS);
    }

    /** Le bloc {@code index} du totem (1 = le plus bas) : or et pierre noire sculptee en alternance. */
    public static BlockState totemBlock(int index) {
        return index % 2 == 0 ? Blocks.GOLD_BLOCK.defaultBlockState()
                : Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
    }

    public static BlockPos totemBlockPos(int index) {
        return TOTEM_CENTER.above(index);
    }

    /**
     * Dresse le totem : {@code height} blocs d'or et de pierre noire, le reste
     * vide ; hors event ({@code active} faux), douze blocs de bedrock, comme sur
     * Paladium.
     */
    public static void setTotem(ServerLevel level, int height, boolean active) {
        for (int i = 1; i <= TOTEM_HEIGHT; i++) {
            BlockState state = !active ? Blocks.BEDROCK.defaultBlockState()
                    : i <= height ? totemBlock(i) : Blocks.AIR.defaultBlockState();
            level.setBlock(totemBlockPos(i), state, Block.UPDATE_ALL);
        }
    }
}

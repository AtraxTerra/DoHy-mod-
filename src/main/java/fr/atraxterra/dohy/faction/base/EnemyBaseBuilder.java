package fr.atraxterra.dohy.faction.base;

import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.defense.PalaDefense;
import fr.atraxterra.dohy.palamod.pillage.PalaPillage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Bati une base ennemie (lot 22g) dans des chunks deja charges, par etapes :
 * le chantier tourne quelques millisecondes par tick ({@link #work(long)}), une
 * forteresse de 97 x 97 ne fige donc pas le serveur.
 *
 * <p>Le plan, a la facon des bases de Paladium, de l'exterieur vers le centre :
 * <ul>
 *   <li>une <b>douve</b> (eau, puis Fake Water) et un pont devant la herse ;</li>
 *   <li>une <b>enceinte</b> a couches — dont de l'eau prise entre deux murs :
 *       une TNT qui la perce explose ensuite dans l'eau et ne casse plus rien,
 *       il faut la vider (seau extrapole, TNT eponge) —, ses creneaux, quatre
 *       <b>tours</b> d'angle et une <b>porterie</b> a herse ;</li>
 *   <li>une <b>cour</b> pavee : spikes, casernes a spawner, tranchee de Fake
 *       Water ou de lave ;</li>
 *   <li>un <b>donjon</b> en cube a couches sur ses six faces, dessous compris
 *       (renforcee, eau, enclumes, obsidiennes a wither et piegees), coiffe de
 *       creneaux, de tourelles et d'une balise visible de loin ; au coeur, la
 *       <b>salle des coffres</b>.</li>
 * </ul>
 * Les gardes sont surtout des illageois (pillards aux arbaletes sur les murs
 * et les tours, vindicateurs, sorcieres, ravageurs, un evocateur), qui ne
 * brulent pas au soleil.
 */
final class EnemyBaseBuilder {

    record Result(BlockPos center, BlockPos coreMin, BlockPos coreMax) {
    }

    /** Une couche de mur : un bloc, ou un liquide pris entre deux couches pleines. */
    private record Layer(Supplier<BlockState> block, boolean fluid) {
        static Layer solid(Supplier<BlockState> block) {
            return new Layer(block, false);
        }

        static Layer fluid(Supplier<BlockState> block) {
            return new Layer(block, true);
        }
    }

    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private final ServerLevel level;
    private final RandomSource random;
    private final EnemyBaseTier tier;
    private final String name;
    private final int cx;
    private final int cz;
    private int y0;
    private int coreRadius;
    private int coreHeight;
    private final ArrayDeque<Runnable> steps = new ArrayDeque<>();
    private final List<BlockPos> shaped = new ArrayList<>();

    EnemyBaseBuilder(ServerLevel level, RandomSource random, EnemyBaseTier tier, String name, int cx, int cz) {
        this.level = level;
        this.random = random;
        this.tier = tier;
        this.name = name;
        this.cx = cx;
        this.cz = cz;
    }

    /** Le proprietaire fictif des claims : le meme pour une meme base. */
    static UUID owner(String name, int x, int z) {
        return UUID.nameUUIDFromBytes(("dohy:enemy_base:" + name + ":" + x + ":" + z).getBytes());
    }

    /** Un claim deja pose (un joueur, une autre base) dans l'emprise ? */
    static boolean overlapsClaim(ServerLevel level, int chunkX, int chunkZ, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunk(chunkX + dx, chunkZ + dz);
                if (chunk.getData(Factions.CLAIM.get()).claimed()) {
                    return true;
                }
            }
        }
        return false;
    }

    static ResourceKey<LootTable> table(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, path));
    }

    // -- Chantier -------------------------------------------------------------------

    /** Choisit le sol et prepare toutes les etapes. */
    void plan() {
        y0 = ground();
        for (int x = -tier.half(); x <= tier.half(); x++) {
            int column = x;
            steps.add(() -> flatten(column));
        }
        switch (tier) {
            case OUTPOST -> outpost();
            case BASE -> base();
            default -> fortress();
        }
        steps.add(() -> {
            for (BlockPos pos : shaped) {
                level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), FLAGS);
            }
        });
        steps.add(this::claim);
    }

    /** Avance le chantier pendant {@code budget} nanosecondes au plus ; vrai quand tout est pose. */
    boolean work(long budget) {
        long end = System.nanoTime() + budget;
        while (!steps.isEmpty() && System.nanoTime() < end) {
            steps.poll().run();
        }
        return steps.isEmpty();
    }

    Result result() {
        return new Result(new BlockPos(cx, y0, cz), new BlockPos(cx - coreRadius, y0, cz - coreRadius),
                new BlockPos(cx + coreRadius, y0 + coreHeight - 1, cz + coreRadius));
    }

    // -- Les trois plans --------------------------------------------------------------

    /** 41 x 41 : douve d'eau, enceinte pierre-eau-pierre, 4 tours, donjon pierre-eau-obsidienne. */
    private void outpost() {
        moat(18, 19, 2, () -> Blocks.WATER.defaultBlockState());
        curtain(13, 7, List.of(Layer.solid(this::stoneBricks), Layer.fluid(() -> Blocks.WATER.defaultBlockState()),
                Layer.solid(this::stoneBricks)), null, this::stoneBricks);
        towers(12, 3, 11, this::stoneBricks, () -> Blocks.COBBLESTONE.defaultBlockState(), false);
        gate(13, 19, 7, this::stoneBricks);
        keep(6, 10, List.of(Layer.solid(this::stoneBricks), Layer.fluid(() -> Blocks.WATER.defaultBlockState()),
                Layer.solid(() -> Blocks.OBSIDIAN.defaultBlockState())), null, 4, false);
        steps.add(() -> {
            lanternPosts(8, 8, 1);
            chests(List.of(new int[]{-3, -3}, new int[]{3, -3}, new int[]{-3, 3}, new int[]{3, 3}), null);
            lantern(0, y0 + coreHeight - 1, 0);
        });
        steps.add(() -> {
            for (int[] t : corners(12)) {
                guard(EntityTypes.PILLAGER, t[0], y0 + 12, t[1], false);
            }
            guard(EntityTypes.PILLAGER, 0, y0 + 7, 11, false);
            guard(EntityTypes.PILLAGER, 11, y0 + 7, 0, false);
            guard(EntityTypes.VINDICATOR, -8, y0, -8, false);
            guard(EntityTypes.VINDICATOR, 8, y0, 8, false);
            guard(EntityTypes.VINDICATOR, -1, y0, 0, false);
            guard(EntityTypes.ZOMBIE, 1, y0, 1, true);
        });
    }

    /** 65 x 65 : douve de Fake Water, enceinte a 4 couches, spikes, casernes, donjon a 5 couches. */
    private void base() {
        moat(30, 31, 2, () -> PalaDefense.FAKE_WATER_BLOCK.get().defaultBlockState());
        curtain(24, 10, List.of(Layer.solid(() -> Blocks.OBSIDIAN.defaultBlockState()),
                Layer.fluid(() -> Blocks.WATER.defaultBlockState()), Layer.solid(this::trappedObsidian),
                Layer.solid(this::hardened)), () -> Blocks.CRYING_OBSIDIAN.defaultBlockState(), this::stoneBricks);
        towers(23, 4, 15, () -> Blocks.OBSIDIAN.defaultBlockState(), () -> Blocks.OBSIDIAN.defaultBlockState(), true);
        gate(24, 31, 10, () -> Blocks.OBSIDIAN.defaultBlockState());
        steps.add(() -> {
            ring(19, y0, () -> PalaDefense.DIAMOND_SPIKE.get().defaultBlockState());
            trench(11, 11, 1, () -> PalaDefense.FAKE_WATER_BLOCK.get().defaultBlockState());
            lanternPosts(12, 18, 6);
        });
        steps.add(() -> barracks(-15, 0, Direction.EAST, EntityTypes.HUSK, this::stoneBricks));
        steps.add(() -> barracks(15, 0, Direction.WEST, EntityTypes.SPIDER, this::stoneBricks));
        keep(9, 14, List.of(Layer.solid(this::hardened), Layer.fluid(() -> Blocks.WATER.defaultBlockState()),
                Layer.solid(() -> Blocks.OBSIDIAN.defaultBlockState()), Layer.solid(this::boomObsidian),
                Layer.solid(this::stoneBricks)), () -> Blocks.CRYING_OBSIDIAN.defaultBlockState(), 5, true);
        steps.add(() -> {
            chests(List.of(new int[]{-4, -4}, new int[]{4, -4}, new int[]{-4, 4}, new int[]{4, 4},
                    new int[]{0, -4}, new int[]{0, 4}), null);
            lantern(-2, y0 + coreHeight - 1, 0);
            lantern(2, y0 + coreHeight - 1, 0);
        });
        steps.add(() -> {
            for (int[] t : corners(23)) {
                guard(EntityTypes.PILLAGER, t[0] - 1, y0 + 16, t[1], false);
                guard(EntityTypes.PILLAGER, t[0] + 1, y0 + 16, t[1], false);
            }
            for (int[] w : new int[][]{{0, 22}, {22, 0}, {-22, 0}, {12, -22}}) {
                guard(EntityTypes.PILLAGER, w[0], y0 + 10, w[1], false);
            }
            for (int[] c : new int[][]{{-14, -14}, {14, -14}, {-14, 14}, {14, 14}}) {
                guard(EntityTypes.VINDICATOR, c[0], y0, c[1], false);
            }
            guard(EntityTypes.WITCH, 0, y0, 16, false);
            guard(EntityTypes.VINDICATOR, -2, y0, -2, false);
            guard(EntityTypes.VINDICATOR, 2, y0, 2, false);
        });
    }

    /** 97 x 97 : la place forte — six couches d'enceinte, lave, quatre casernes, donjon a sept couches. */
    private void fortress() {
        moat(45, 47, 3, () -> PalaDefense.FAKE_WATER_BLOCK.get().defaultBlockState());
        curtain(40, 14, List.of(Layer.solid(this::hardened), Layer.fluid(() -> Blocks.WATER.defaultBlockState()),
                Layer.solid(this::wallTrap), Layer.solid(() -> Blocks.OBSIDIAN.defaultBlockState()),
                Layer.fluid(() -> Blocks.WATER.defaultBlockState()), Layer.solid(this::hardened)),
                () -> Blocks.CRYING_OBSIDIAN.defaultBlockState(), () -> Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        towers(39, 5, 21, this::hardened, () -> Blocks.OBSIDIAN.defaultBlockState(), true);
        gate(40, 47, 14, this::hardened);
        steps.add(() -> {
            ring(31, y0, () -> PalaDefense.DIAMOND_SPIKE.get().defaultBlockState());
            ring(30, y0, () -> PalaDefense.TITANE_SPIKE.get().defaultBlockState());
            trench(17, 18, 2, () -> Blocks.LAVA.defaultBlockState());
            lanternPosts(20, 28, 8);
        });
        steps.add(() -> barracks(-25, 0, Direction.EAST, EntityTypes.WITHER_SKELETON,
                () -> Blocks.DEEPSLATE_BRICKS.defaultBlockState()));
        steps.add(() -> barracks(25, 0, Direction.WEST, EntityTypes.WITHER_SKELETON,
                () -> Blocks.DEEPSLATE_BRICKS.defaultBlockState()));
        steps.add(() -> barracks(-17, 25, Direction.NORTH, EntityTypes.HUSK,
                () -> Blocks.DEEPSLATE_BRICKS.defaultBlockState()));
        steps.add(() -> barracks(17, 25, Direction.NORTH, EntityTypes.CAVE_SPIDER,
                () -> Blocks.DEEPSLATE_BRICKS.defaultBlockState()));
        keep(14, 22, List.of(Layer.solid(this::hardened), Layer.fluid(() -> Blocks.WATER.defaultBlockState()),
                Layer.solid(this::anvilLayer), Layer.solid(this::witherLayer),
                Layer.fluid(() -> Blocks.WATER.defaultBlockState()), Layer.solid(this::megaBoomObsidian),
                Layer.solid(() -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState())),
                () -> Blocks.CRYING_OBSIDIAN.defaultBlockState(), 6, true);
        steps.add(() -> {
            List<int[]> spots = new ArrayList<>();
            for (int i = -6; i <= 6; i += 3) {
                spots.add(new int[]{i, -7});
                spots.add(new int[]{i, 7});
            }
            chests(spots, new int[]{0, 0});
            level.setBlock(new BlockPos(cx + 7, y0, cz), PalaPillage.ONLINE_DETECTOR.get().defaultBlockState(), FLAGS);
            for (int[] l : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
                lantern(l[0], y0 + coreHeight - 1, l[1]);
            }
        });
        steps.add(() -> {
            for (int[] t : corners(39)) {
                guard(EntityTypes.PILLAGER, t[0] - 2, y0 + 22, t[1], false);
                guard(EntityTypes.PILLAGER, t[0] + 2, y0 + 22, t[1], false);
            }
            for (int[] w : new int[][]{{-15, 36}, {15, 36}, {36, -15}, {36, 15}, {-36, -15}, {-36, 15},
                    {-15, -36}, {15, -36}}) {
                guard(EntityTypes.PILLAGER, w[0], y0 + 14, w[1], false);
            }
            for (int[] c : new int[][]{{-22, -22}, {22, -22}, {-22, 22}, {22, 22}, {0, -24}, {0, 24}}) {
                guard(EntityTypes.VINDICATOR, c[0], y0, c[1], false);
            }
            guard(EntityTypes.RAVAGER, -26, y0, -12, false);
            guard(EntityTypes.RAVAGER, 26, y0, 12, false);
            guard(EntityTypes.WITCH, -10, y0, 26, false);
            guard(EntityTypes.WITCH, 10, y0, 26, false);
            guard(EntityTypes.EVOKER, 0, y0, 3, false);
            guard(EntityTypes.VINDICATOR, -3, y0, -2, false);
            guard(EntityTypes.VINDICATOR, 3, y0, -2, false);
            guard(EntityTypes.WITHER_SKELETON, 0, y0, -3, false);
        });
    }

    // -- Terrain ------------------------------------------------------------------

    /** Le sol : la mediane des hauteurs de l'emprise, au-dessus du niveau de la mer. */
    private int ground() {
        int half = tier.half();
        List<Integer> heights = new ArrayList<>();
        for (int x = cx - half; x <= cx + half; x += 4) {
            for (int z = cz - half; z <= cz + half; z += 4) {
                heights.add(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
            }
        }
        heights.sort(Integer::compare);
        return Math.max(heights.get(heights.size() / 2), level.getSeaLevel() + 1);
    }

    /** Une colonne de l'emprise : degagee, pavee, posee sur des fondations de pierre. */
    private void flatten(int dx) {
        int half = tier.half();
        int x = cx + dx;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dz = -half; dz <= half; dz++) {
            int z = cz + dz;
            int top = Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), y0 + tier.clearHeight());
            for (int y = y0; y <= top; y++) {
                pos.set(x, y, z);
                if (!level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
            level.setBlock(pos.set(x, y0 - 1, z), floor(dx, dz), FLAGS);
            for (int y = y0 - 2; y > y0 - 16; y--) {
                pos.set(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (!state.canBeReplaced() && state.getFluidState().isEmpty()) {
                    break;
                }
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), FLAGS);
            }
        }
    }

    /** Dehors, l'herbe ; dans l'enceinte, une cour pavee et une croix d'allees. */
    private BlockState floor(int dx, int dz) {
        int inside = switch (tier) {
            case OUTPOST -> 11;
            case BASE -> 21;
            default -> 35;
        };
        if (Math.max(Math.abs(dx), Math.abs(dz)) > inside) {
            return random.nextInt(8) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) {
            return Blocks.POLISHED_ANDESITE.defaultBlockState();
        }
        int roll = random.nextInt(10);
        return roll < 4 ? Blocks.COBBLESTONE.defaultBlockState() : roll < 6 ? Blocks.GRAVEL.defaultBlockState()
                : roll < 8 ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
    }

    // -- Ouvrages -------------------------------------------------------------------

    /** La douve : un anneau de liquide de {@code from} a {@code to}, profond de {@code depth}. */
    private void moat(int from, int to, int depth, Supplier<BlockState> fluid) {
        steps.add(() -> trench(from, to, depth, fluid));
    }

    private void trench(int from, int to, int depth, Supplier<BlockState> fluid) {
        for (int r = from; r <= to; r++) {
            for (int y = y0 - depth; y <= y0 - 1; y++) {
                ring(r, y, fluid);
            }
            for (int y = y0 - depth - 2; y < y0 - depth; y++) {
                ring(r, y, () -> Blocks.STONE.defaultBlockState());
            }
        }
    }

    /**
     * L'enceinte : une couche par anneau, de l'exterieur (rayon {@code r}) vers
     * l'interieur. Les liquides vont du sol a l'avant-derniere rangee, coiffes
     * par la couche exterieure ; sous l'enceinte, deux rangees pleines.
     */
    private void curtain(int r, int height, List<Layer> layers, Supplier<BlockState> band, Supplier<BlockState> merlon) {
        for (int y = y0 - 2; y <= y0 + height; y++) {
            int row = y;
            steps.add(() -> {
                if (row == y0 + height) {
                    ringMerlons(r, row, merlon);
                    return;
                }
                for (int i = 0; i < layers.size(); i++) {
                    Layer layer = layers.get(i);
                    Supplier<BlockState> block = layer.fluid() && (row < y0 || row == y0 + height - 1)
                            ? layers.get(0).block() : layer.block();
                    if (i == 0 && band != null && row >= y0 && (row - y0) % 4 == 3) {
                        block = band;
                    }
                    ring(r - i, row, block);
                }
            });
        }
    }

    /** Quatre tours d'angle, pleines, a creneaux, lanterne et banniere noire. */
    private void towers(int at, int radius, int height, Supplier<BlockState> outer, Supplier<BlockState> inner,
                        boolean banner) {
        for (int[] corner : corners(at)) {
            steps.add(() -> {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        boolean edge = Math.max(Math.abs(x), Math.abs(z)) == radius;
                        for (int y = y0 - 2; y <= y0 + height; y++) {
                            set(corner[0] + x, y, corner[1] + z, edge || y == y0 + height ? outer.get() : inner.get());
                        }
                        if (edge && Math.floorMod(x + z, 2) == 0) {
                            set(corner[0] + x, y0 + height + 1, corner[1] + z, outer.get());
                        }
                    }
                }
                lantern(corner[0] - radius + 1, y0 + height + 1, corner[1] + radius - 1, false);
                if (banner) {
                    set(corner[0] + radius - 1, y0 + height + 1, corner[1] - radius + 1,
                            Blocks.BANNER.pick(DyeColor.BLACK).defaultBlockState());
                }
            });
        }
    }

    /** La porterie, au nord : deux tourelles, une herse devant le mur plein, un pont sur la douve. */
    private void gate(int wall, int moatOuter, int height, Supplier<BlockState> stone) {
        steps.add(() -> {
            for (int side = -1; side <= 1; side += 2) {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        for (int y = y0 - 2; y <= y0 + height + 4; y++) {
                            set(side * 6 + x, y, -wall + z, stone.get());
                        }
                        if ((Math.abs(x) == 2 || Math.abs(z) == 2) && Math.floorMod(x + z, 2) == 0) {
                            set(side * 6 + x, y0 + height + 5, -wall + z, stone.get());
                        }
                    }
                }
            }
            for (int x = -3; x <= 3; x++) {
                for (int y = y0; y <= y0 + 6; y++) {
                    BlockPos pos = new BlockPos(cx + x, y, cz - wall - 1);
                    level.setBlock(pos, y == y0 + 6 ? stone.get() : Blocks.IRON_BARS.defaultBlockState(), FLAGS);
                    shaped.add(pos);
                }
            }
            for (int x = -2; x <= 2; x++) {
                for (int z = wall + 2; z <= moatOuter + 1; z++) {
                    set(x, y0 - 1, -z, Blocks.STONE_BRICKS.defaultBlockState());
                }
            }
        });
    }

    /**
     * Le donjon : un cube a couches sur ses six faces — la couche {@code d}
     * est a {@code d} blocs de la face la plus proche, dessous compris —, la
     * salle des coffres au centre, au niveau du sol ; au-dessus, creneaux,
     * tourelles et, pour les grandes bases, une balise.
     */
    private void keep(int radius, int height, List<Layer> layers, Supplier<BlockState> band, int core, boolean beacon) {
        int depth = layers.size();
        coreRadius = radius - depth;
        coreHeight = core;
        int top = y0 + height - 1;
        int bottom = y0 - 1 - depth;
        for (int y = bottom; y <= top; y++) {
            int row = y;
            steps.add(() -> {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        int side = radius - Math.max(Math.abs(x), Math.abs(z));
                        int d = Math.min(side, Math.min(top - row, row - bottom));
                        BlockState state;
                        if (d < depth) {
                            state = d == 0 && side == 0 && band != null && row >= y0 && (row - y0) % 5 == 4
                                    ? band.get() : layers.get(d).block().get();
                        } else if (row >= y0 && row < y0 + core) {
                            state = Blocks.AIR.defaultBlockState();
                        } else {
                            state = layers.get(depth - 1).block().get();
                        }
                        set(x, row, z, state);
                    }
                }
            });
        }
        steps.add(() -> {
            ringMerlons(radius, top + 1, layers.get(0).block());
            for (int[] corner : corners(radius - 1)) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        for (int y = top + 1; y <= top + 4; y++) {
                            set(corner[0] + x, y, corner[1] + z, layers.get(0).block().get());
                        }
                    }
                }
                set(corner[0], top + 5, corner[1], Blocks.BANNER.pick(DyeColor.BLACK).defaultBlockState());
            }
            if (beacon) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        set(x, top, z, Blocks.IRON_BLOCK.defaultBlockState());
                    }
                }
                set(0, top + 1, 0, Blocks.BEACON.defaultBlockState());
            } else {
                set(0, top + 1, 0, Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
            }
        });
    }

    /** Une caserne : une halle sombre, une porte vers le donjon, un spawner au fond. */
    private void barracks(int dx, int dz, Direction door, EntityType<?> mob, Supplier<BlockState> wall) {
        boolean alongX = door.getAxis() == Direction.Axis.X;
        int hx = alongX ? 3 : 4;
        int hz = alongX ? 4 : 3;
        for (int x = -hx; x <= hx; x++) {
            for (int z = -hz; z <= hz; z++) {
                boolean edge = Math.abs(x) == hx || Math.abs(z) == hz;
                for (int y = y0; y <= y0 + 4; y++) {
                    set(dx + x, y, dz + z, edge || y == y0 + 4 ? wall.get() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        int ox = door.getStepX() * hx;
        int oz = door.getStepZ() * hz;
        set(dx + ox, y0, dz + oz, Blocks.AIR.defaultBlockState());
        set(dx + ox, y0 + 1, dz + oz, Blocks.AIR.defaultBlockState());
        BlockPos pos = new BlockPos(cx + dx - door.getStepX() * (hx - 1), y0, cz + dz - door.getStepZ() * (hz - 1));
        level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), FLAGS);
        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
            spawner.setEntityId(mob, random);
        }
    }

    /** Des lampadaires de pierre le long de l'allee nord-sud, de {@code from} a {@code reach}. */
    private void lanternPosts(int from, int reach, int step) {
        for (int d = from; d <= reach; d += step) {
            for (int[] p : new int[][]{{2, d}, {-2, d}, {2, -d}, {-2, -d}}) {
                set(p[0], y0, p[1], Blocks.STONE_BRICK_WALL.defaultBlockState());
                set(p[0], y0 + 1, p[1], Blocks.STONE_BRICK_WALL.defaultBlockState());
                lantern(p[0], y0 + 2, p[1], false);
            }
        }
    }

    // -- Palettes -------------------------------------------------------------------

    private BlockState stoneBricks() {
        int roll = random.nextInt(20);
        return roll < 12 ? Blocks.STONE_BRICKS.defaultBlockState()
                : roll < 15 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                : roll < 18 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                : Blocks.COBBLESTONE.defaultBlockState();
    }

    private BlockState hardened() {
        return PalaDefense.HARDENED_OBSIDIAN.get().defaultBlockState();
    }

    /** Une obsidienne sur quatre piegee : poison, boom, knockback, gel. */
    private BlockState trappedObsidian() {
        return switch (random.nextInt(16)) {
            case 0 -> PalaDefense.POISON_OBSIDIAN.get().defaultBlockState();
            case 1 -> PalaDefense.BOOM_OBSIDIAN.get().defaultBlockState();
            case 2 -> PalaDefense.KNOCKBACK_OBSIDIAN.get().defaultBlockState();
            case 3 -> PalaDefense.FROZEN_OBSIDIAN.get().defaultBlockState();
            default -> Blocks.OBSIDIAN.defaultBlockState();
        };
    }

    /** La couche piegee de la place forte : une sur trois, slime contre les withers compris. */
    private BlockState wallTrap() {
        return switch (random.nextInt(9)) {
            case 0 -> PalaDefense.POISON_OBSIDIAN.get().defaultBlockState();
            case 1 -> PalaDefense.MEGA_BOOM_OBSIDIAN.get().defaultBlockState();
            case 2 -> PalaDefense.SLIME_OBSIDIAN.get().defaultBlockState();
            default -> Blocks.OBSIDIAN.defaultBlockState();
        };
    }

    private BlockState boomObsidian() {
        return random.nextInt(6) == 0 ? PalaDefense.BOOM_OBSIDIAN.get().defaultBlockState()
                : Blocks.OBSIDIAN.defaultBlockState();
    }

    private BlockState megaBoomObsidian() {
        return random.nextInt(6) == 0 ? PalaDefense.MEGA_BOOM_OBSIDIAN.get().defaultBlockState()
                : Blocks.OBSIDIAN.defaultBlockState();
    }

    /** Les enclumes en paladium encaissent deux coups de wither ; l'anti-aggro leur fait lacher prise. */
    private BlockState anvilLayer() {
        return random.nextBoolean() ? PalaDefense.PALADIUM_ANVIL.get().defaultBlockState()
                : PalaDefense.ANTI_AGGRO_OBSIDIAN.get().defaultBlockState();
    }

    /** L'obsidienne a wither retient les withers ; la compacte revient. */
    private BlockState witherLayer() {
        return random.nextInt(3) == 0 ? PalaDefense.BIG_OBSIDIAN.get().defaultBlockState()
                : PalaDefense.WITHER_OBSIDIAN.get().defaultBlockState();
    }

    // -- Primitives -----------------------------------------------------------------

    private void set(int dx, int y, int dz, BlockState state) {
        level.setBlock(new BlockPos(cx + dx, y, cz + dz), state, FLAGS);
    }

    private static int[][] corners(int at) {
        return new int[][]{{-at, -at}, {at, -at}, {-at, at}, {at, at}};
    }

    /** Le pourtour du carre de demi-cote {@code r} a la hauteur {@code y}. */
    private void ring(int r, int y, Supplier<BlockState> state) {
        for (int i = -r; i <= r; i++) {
            set(i, y, -r, state.get());
            set(i, y, r, state.get());
            if (i != -r && i != r) {
                set(-r, y, i, state.get());
                set(r, y, i, state.get());
            }
        }
    }

    /** Des creneaux : un bloc sur deux du pourtour. */
    private void ringMerlons(int r, int y, Supplier<BlockState> state) {
        for (int i = -r; i <= r; i++) {
            if (Math.floorMod(i, 2) == 0) {
                set(i, y, -r, state.get());
                set(i, y, r, state.get());
                set(-r, y, i, state.get());
                set(r, y, i, state.get());
            }
        }
    }

    private void lantern(int dx, int y, int dz) {
        lantern(dx, y, dz, true);
    }

    private void lantern(int dx, int y, int dz, boolean hanging) {
        set(dx, y, dz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, hanging));
    }

    /** Les coffres de la salle, et le coffre fort au centre s'il y en a un. */
    private void chests(List<int[]> spots, int[] vault) {
        for (int[] spot : spots) {
            chest(spot[0], spot[1], spot[1] < 0 ? Direction.SOUTH : Direction.NORTH, tier.lootTable());
        }
        if (vault != null) {
            chest(vault[0], vault[1], Direction.NORTH, "chests/enemy_coffre_fort");
        }
    }

    private void chest(int dx, int dz, Direction facing, String table) {
        BlockPos pos = new BlockPos(cx + dx, y0, cz + dz);
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), FLAGS);
        if (level.getBlockEntity(pos) instanceof RandomizableContainer container) {
            container.setLootTable(table(table), random.nextLong());
        }
    }

    /** Un garde de la faction : persistant, nomme. Le zombie a un casque et une epee. */
    private void guard(EntityType<? extends Mob> type, int dx, int y, int dz, boolean armed) {
        Mob mob = type.create(level, EntitySpawnReason.STRUCTURE);
        if (mob == null) {
            return;
        }
        BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
        mob.snapTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, random.nextFloat() * 360.0f, 0.0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
        if (mob instanceof Zombie zombie) {
            zombie.setBaby(false);
        }
        if (armed) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        }
        mob.setPersistenceRequired();
        mob.addTag(EnemyBases.GUARD_TAG);
        mob.setCustomName(Component.literal("Garde des " + name));
        level.addFreshEntity(mob);
    }

    /** Les claims de la faction ennemie, sur tout le carre de chunks. */
    private void claim() {
        int chunkX = cx >> 4;
        int chunkZ = cz >> 4;
        int radius = tier.claimRadius();
        ClaimOwner owner = new ClaimOwner(Optional.of(owner(name, cx, cz)), name, true, true);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunk(chunkX + dx, chunkZ + dz);
                chunk.setData(Factions.CLAIM.get(), owner);
                chunk.markUnsaved();
            }
        }
    }
}

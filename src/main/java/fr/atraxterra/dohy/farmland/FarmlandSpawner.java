package fr.atraxterra.dohy.farmland;

import fr.atraxterra.dohy.farmland.FarmlandMap.Zone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les creatures de la farmland : seulement celles du Chasseur, chacune dans son
 * coin, a leur plafond. Le jeu n'en fait apparaitre aucune autre ici
 * ({@link FarmlandEvents}).
 *
 * <p>Toutes les deux secondes, pres de chaque joueur de la farmland, quelques
 * creatures arrivent a 14-72 blocs, la ou un coin en manque. Les animaux (et les
 * villageois) vivent dix minutes si personne ne les attaque ; les monstres
 * disparaissent comme ailleurs, loin des joueurs. Les plafonds sont pour toute la
 * carte.
 */
public final class FarmlandSpawner {

    /** Les marques posees sur les creatures de la farmland. */
    public static final String TAG = "dohy_farmland";
    static final String HIT = "dohy_farmland_hit";
    static final String BORN = "dohy_farmland_born";
    /** Dix minutes de vie pour un animal que personne n'attaque. */
    public static final long LIFETIME = 12_000L;
    private static final int PERIOD = 40;
    private static final int SWEEP = 200;
    private static final int PER_PLAYER = 3;
    private static final int MIN_DISTANCE = 14;
    private static final int MAX_DISTANCE = 72;
    /** Au-dela, plus rien n'arrive. */
    private static final int TOTAL_CAP = 110;

    enum Placement { GROUND, WATER, AIR, CAVE }

    /** Une espece et son poids dans le tirage de sa reserve. */
    record Entry(String id, int weight) {
    }

    /** Une reserve : un coin, un plafond, des especes. */
    public record Pool(String name, Zone zone, int cap, boolean passive, Placement placement, List<Entry> entries) {
        String tag() {
            return TAG + "_" + name;
        }

        int weight() {
            int total = 0;
            for (Entry entry : entries) {
                total += entry.weight();
            }
            return total;
        }
    }

    public static final List<Pool> POOLS = List.of(
            new Pool("foret", Zone.FOREST, 14, false, Placement.GROUND, List.of(new Entry("zombie", 30),
                    new Entry("skeleton", 25), new Entry("spider", 20), new Entry("creeper", 15),
                    new Entry("witch", 6))),
            new Pool("foret_betes", Zone.FOREST, 6, true, Placement.GROUND, List.of(new Entry("wolf", 3),
                    new Entry("rabbit", 4))),
            new Pool("prairie", Zone.MEADOW, 16, true, Placement.GROUND, List.of(new Entry("cow", 10),
                    new Entry("sheep", 10), new Entry("chicken", 8), new Entry("horse", 4), new Entry("donkey", 2),
                    new Entry("rabbit", 3))),
            new Pool("hameau", Zone.HAMLET, 4, true, Placement.GROUND, List.of(new Entry("villager", 1))),
            new Pool("champignons", Zone.MUSHROOM, 4, true, Placement.GROUND, List.of(new Entry("mooshroom", 1))),
            new Pool("collines", Zone.HILLS, 4, true, Placement.GROUND, List.of(new Entry("goat", 1))),
            new Pool("grotte", Zone.HILLS, 5, false, Placement.CAVE, List.of(new Entry("cave_spider", 1))),
            new Pool("jungle", Zone.JUNGLE, 7, true, Placement.GROUND, List.of(new Entry("panda", 3),
                    new Entry("parrot", 4), new Entry("palamod:elephant", 2))),
            new Pool("lac", Zone.LAKE, 3, true, Placement.WATER, List.of(new Entry("dolphin", 1))),
            new Pool("plage", Zone.BEACH, 4, true, Placement.GROUND, List.of(new Entry("turtle", 1))),
            new Pool("marais", Zone.SWAMP, 6, false, Placement.GROUND, List.of(new Entry("slime", 4),
                    new Entry("witch", 2))),
            new Pool("nether", Zone.NETHER, 11, false, Placement.GROUND, List.of(new Entry("zombified_piglin", 6),
                    new Entry("magma_cube", 4), new Entry("wither_skeleton", 3))),
            new Pool("nether_ciel", Zone.NETHER, 2, false, Placement.AIR, List.of(new Entry("ghast", 1))),
            new Pool("end", Zone.END, 6, false, Placement.GROUND, List.of(new Entry("enderman", 1))),
            // les montures sauvages de Paladium vivaient dans sa Farmland : une de chaque, a capturer
            new Pool("monture_prairie", Zone.MEADOW, 1, true, Placement.GROUND,
                    List.of(new Entry("palamod:dancarok", 1))),
            new Pool("monture_collines", Zone.HILLS, 1, true, Placement.GROUND,
                    List.of(new Entry("palamod:ravirok", 1))),
            new Pool("monture_foret", Zone.FOREST, 1, true, Placement.GROUND,
                    List.of(new Entry("palamod:tedarok", 1))));

    /** Les colonnes de chaque coin ou faire apparaitre (une sur deux), calculees une fois. */
    private static final Map<Zone, List<int[]>> COLUMNS = new EnumMap<>(Zone.class);
    private static final List<int[]> DEEP_WATER = new ArrayList<>();
    private static List<BlockPos> cave = List.of();
    /** Les effectifs du dernier passage, par reserve (pour le menu). */
    private static final Map<String, Integer> COUNTS = new LinkedHashMap<>();

    private FarmlandSpawner() {
    }

    static void caveFloor(List<BlockPos> floor) {
        cave = List.copyOf(floor);
    }

    /** Ce que le dernier passage a compte dans cette reserve. */
    public static int count(Pool pool) {
        return COUNTS.getOrDefault(pool.name(), 0);
    }

    static void reset() {
        COUNTS.clear();
    }

    private static synchronized void columns() {
        if (!COLUMNS.isEmpty()) {
            return;
        }
        for (Zone zone : Zone.values()) {
            COLUMNS.put(zone, new ArrayList<>());
        }
        for (int x = -FarmlandMap.PLAY; x <= FarmlandMap.PLAY; x += 2) {
            for (int z = -FarmlandMap.PLAY; z <= FarmlandMap.PLAY; z += 2) {
                Zone zone = FarmlandMap.zone(x, z);
                COLUMNS.get(zone).add(new int[] {x, z});
                if (zone == Zone.LAKE && !FarmlandMap.island(x, z) && FarmlandMap.depth(x, z) >= 3) {
                    DEEP_WATER.add(new int[] {x, z});
                }
            }
        }
    }

    // -- Chaque tick de la dimension --------------------------------------------------------------

    static void tick(ServerLevel level) {
        long time = level.getGameTime();
        if (time % SWEEP == 7) {
            sweep(level);
        }
        if (time % PERIOD != 0 || !Farmland.ready(level) || !level.getGameRules().get(GameRules.SPAWN_MOBS)) {
            return;
        }
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                players.add(player);
            }
        }
        if (players.isEmpty()) {
            return;
        }
        columns();
        Map<String, Integer> counts = census(level);
        int total = 0;
        for (int value : counts.values()) {
            total += value;
        }
        RandomSource random = level.getRandom();
        for (ServerPlayer player : players) {
            for (int attempt = 0; attempt < PER_PLAYER && total < TOTAL_CAP; attempt++) {
                Pool pool = pick(level, counts, random);
                if (pool == null) {
                    return;
                }
                if (spawn(level, pool, player, random) != null) {
                    counts.merge(pool.name(), 1, Integer::sum);
                    total++;
                }
            }
        }
        COUNTS.clear();
        COUNTS.putAll(counts);
    }

    /** Les creatures de la farmland encore la, par reserve. */
    public static Map<String, Integer> census(ServerLevel level) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Pool pool : POOLS) {
            counts.put(pool.name(), 0);
        }
        for (Entity entity : level.getAllEntities()) {
            if (!entity.isAlive() || !entity.entityTags().contains(TAG)) {
                continue;
            }
            for (Pool pool : POOLS) {
                if (entity.entityTags().contains(pool.tag())) {
                    counts.merge(pool.name(), 1, Integer::sum);
                    break;
                }
            }
        }
        return counts;
    }

    /** Une reserve qui manque de monde, tiree d'autant plus qu'il lui en manque. */
    private static Pool pick(ServerLevel level, Map<String, Integer> counts, RandomSource random) {
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        int total = 0;
        for (Pool pool : POOLS) {
            if (!peaceful || pool.passive()) {
                total += Math.max(0, pool.cap() - counts.getOrDefault(pool.name(), 0));
            }
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (Pool pool : POOLS) {
            if (peaceful && !pool.passive()) {
                continue;
            }
            roll -= Math.max(0, pool.cap() - counts.getOrDefault(pool.name(), 0));
            if (roll < 0) {
                return pool;
            }
        }
        return null;
    }

    /** Une creature de cette reserve pres de ce joueur ; {@code null} si aucune place n'a convenu. */
    public static Mob spawn(ServerLevel level, Pool pool, ServerPlayer player, RandomSource random) {
        columns();
        EntityType<?> type = type(pool, random);
        if (type == null) {
            return null;
        }
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos pos = place(level, pool, player, random);
            if (pos == null) {
                continue;
            }
            double x = pos.getX() + 0.5d;
            double z = pos.getZ() + 0.5d;
            if (!level.noCollision(type.getSpawnAABB(x, pos.getY(), z))
                    || level.getNearestPlayer(x, pos.getY(), z, 10.0d, false) != null) {
                continue;
            }
            Entity entity = type.create(level, EntitySpawnReason.NATURAL);
            if (!(entity instanceof Mob mob)) {
                return null;
            }
            mob.snapTo(x, pos.getY(), z, random.nextFloat() * 360.0f, 0.0f);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
            mob.addTag(TAG);
            mob.addTag(pool.tag());
            if (pool.passive()) {
                mob.getPersistentData().putLong(BORN, level.getGameTime());
            }
            level.addFreshEntity(mob);
            return mob;
        }
        return null;
    }

    private static EntityType<?> type(Pool pool, RandomSource random) {
        int roll = random.nextInt(Math.max(1, pool.weight()));
        for (Entry entry : pool.entries()) {
            roll -= entry.weight();
            if (roll < 0) {
                int colon = entry.id().indexOf(':');
                Identifier id = colon < 0 ? Identifier.withDefaultNamespace(entry.id())
                        : Identifier.fromNamespaceAndPath(entry.id().substring(0, colon), entry.id().substring(colon + 1));
                return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            }
        }
        return null;
    }

    /** Une place pour une creature de cette reserve, dans un chunk charge, assez loin et pas trop. */
    private static BlockPos place(ServerLevel level, Pool pool, ServerPlayer player, RandomSource random) {
        if (pool.placement() == Placement.CAVE) {
            if (cave.isEmpty()) {
                return null;
            }
            BlockPos pos = cave.get(random.nextInt(cave.size()));
            double distance = Math.sqrt(player.distanceToSqr(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d));
            return distance <= MAX_DISTANCE + 24 && level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                    && level.getBlockState(pos).isAir() ? pos : null;
        }
        List<int[]> columns = pool.placement() == Placement.WATER ? DEEP_WATER : COLUMNS.get(pool.zone());
        if (columns == null || columns.isEmpty()) {
            return null;
        }
        int[] column = columns.get(random.nextInt(columns.size()));
        int x = column[0] + random.nextInt(2);
        int z = column[1] + random.nextInt(2);
        double dx = x + 0.5d - player.getX();
        double dz = z + 0.5d - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE || !level.hasChunk(x >> 4, z >> 4)
                || FarmlandMap.zone(x, z) != pool.zone()) {
            return null;
        }
        switch (pool.placement()) {
            case WATER -> {
                BlockPos pos = new BlockPos(x, FarmlandMap.G - 1, z);
                return level.getBlockState(pos).is(Blocks.WATER) && level.getBlockState(pos.below()).is(Blocks.WATER)
                        ? pos : null;
            }
            case AIR -> {
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                return new BlockPos(x, ground + 7 + random.nextInt(6), z);
            }
            default -> {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockState below = level.getBlockState(new BlockPos(x, y - 1, z));
                if (y < FarmlandMap.G - 2 || y > FarmlandMap.G + 30 || below.isAir()
                        || !below.getFluidState().isEmpty() || below.is(Blocks.BARRIER)) {
                    return null;
                }
                return new BlockPos(x, y, z);
            }
        }
    }

    // -- Le menage --------------------------------------------------------------------------------

    /**
     * Les animaux de plus de dix minutes que personne n'a attaques s'en vont, comme ce qui a quitte
     * la carte. Une bete nommee reste, comme celle qu'un joueur a touchee, nourrie, apprivoisee ou
     * prise en laisse ({@link FarmlandEvents} la marque).
     */
    static void sweep(ServerLevel level) {
        long now = level.getGameTime();
        List<Entity> gone = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!entity.isAlive() || !entity.entityTags().contains(TAG)) {
                continue;
            }
            if (Farmland.outOfBounds(entity)) {
                gone.add(entity);
                continue;
            }
            if (expired(entity, now)) {
                gone.add(entity);
            }
        }
        for (Entity entity : gone) {
            entity.discard();
        }
    }

    /** Un animal de la farmland qui a fait son temps. */
    static boolean expired(Entity entity, long now) {
        if (!entity.getPersistentData().contains(BORN) || entity.entityTags().contains(HIT)
                || entity.hasCustomName()) {
            return false;
        }
        return now - entity.getPersistentData().getLongOr(BORN, now) > LIFETIME;
    }

    /** Avant une reconstruction : plus aucune creature de la farmland. */
    static void discardAll(ServerLevel level) {
        List<Entity> all = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity.entityTags().contains(TAG)) {
                all.add(entity);
            }
        }
        all.forEach(Entity::discard);
    }
}

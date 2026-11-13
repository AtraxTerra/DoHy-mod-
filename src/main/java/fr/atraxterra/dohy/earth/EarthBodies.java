package fr.atraxterra.dohy.earth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Les gisements geants de la Terre : de vraies mines (Kiruna, Escondida, le
 * Witwatersrand...), des cheminees de kimberlite, des nappes de petrole sous
 * les vrais champs (Ghawar, Samotlor, le bassin permien...) et les sables
 * bitumineux de l'Athabasca ; et le nettoyage des minerais que la carte avait
 * semes partout.
 *
 * <p>Un gisement couvre plusieurs chunks. Sa forme ne depend que de son nom :
 * chaque chunk qui arrive en pose sa part, et le gisement se recolle sans
 * couture quel que soit l'ordre d'arrivee. Rien n'est lu hors du chunk servi
 * (ni bloc ni hauteur) : poser un gisement ne charge jamais un voisin.
 *
 * <p>La table est la partie {@code bodies} de {@code data/dohy/earth/deposits.json}.
 */
public final class EarthBodies {

    public enum Kind { ORE, PIPE, POOL, SAND }

    public record Extra(BlockState ore, BlockState deepslate, double chance) {
    }

    public record Geodes(BlockState shell, BlockState budding, double buddingChance, int radius, List<int[]> centers) {
    }

    /** Un gisement geant pret a poser ; {@code centerX}/{@code centerZ} en blocs, l'angle en cosinus et sinus. */
    public record Body(String id, String name, String resource, Kind kind, String country, int tier, double centerX,
                double centerZ, double rx, double rz, int ry, double cos, double sin, int y, int surface,
                BlockState ore, BlockState deepslate, double density, BlockState rich, double richChance,
                List<Extra> extras, BlockState filler, Geodes geodes, double oil, ImprovedNoise noise, long salt) {

        /** La demi-largeur de la boite du gisement, bosses comprises. */
        double reach() {
            return Math.max(rx, rz) * BUMP + (geodes == null ? 0 : geodes.radius() + 1);
        }

        boolean touches(int minX, int minZ) {
            double reach = reach();
            return centerX + reach >= minX && centerX - reach <= minX + 15
                    && centerZ + reach >= minZ && centerZ - reach <= minZ + 15;
        }

        /** La distance au centre, dans le plan, en rayons (1 = le bord de l'ellipse). */
        double flat(double x, double z) {
            double dx = x - centerX;
            double dz = z - centerZ;
            double u = (dx * cos + dz * sin) / rx;
            double v = (-dx * sin + dz * cos) / rz;
            return u * u + v * v;
        }
    }

    /** Les bosses : un gisement deborde jusqu'a 35 % de son rayon. */
    private static final double BUMP = 1.35d;

    private static volatile List<Body> bodies = List.of();
    private static volatile Set<Block> clear = Set.of();

    private EarthBodies() {
    }

    public static List<Body> all() {
        return bodies;
    }

    static boolean clears() {
        return !clear.isEmpty();
    }

    static void unload() {
        bodies = List.of();
        clear = Set.of();
    }

    static void load(JsonObject root, EarthData earth) {
        Set<Block> toClear = new HashSet<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(root, "clear", new JsonArray())) {
            BuiltInRegistries.BLOCK.getOptional(Identifier.parse(element.getAsString())).ifPresent(toClear::add);
        }
        List<Body> list = new ArrayList<>();
        int skipped = 0;
        if (earth != null) {
            for (JsonElement element : GsonHelper.getAsJsonArray(root, "bodies", new JsonArray())) {
                try {
                    Body body = body(element.getAsJsonObject(), earth);
                    if (body == null) {
                        skipped++;
                    } else {
                        list.add(body);
                    }
                } catch (RuntimeException error) {
                    DoHy.LOGGER.error("Gisement geant illisible : {}", element, error);
                    skipped++;
                }
            }
        }
        clear = Set.copyOf(toClear);
        bodies = List.copyOf(list);
        DoHy.LOGGER.info("Gisements geants de la Terre : {} en service, {} ignores ; {} minerais nettoyes",
                list.size(), skipped, toClear.size());
    }

    private static BlockState block(JsonObject json, String key) {
        if (!json.has(key)) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getOptional(Identifier.parse(GsonHelper.getAsString(json, key)))
                .map(Block::defaultBlockState).orElse(null);
    }

    /** Un gisement de la table ; {@code null} si son minerai n'est pas dans le jeu. */
    private static Body body(JsonObject json, EarthData earth) {
        String id = GsonHelper.getAsString(json, "id");
        BlockState ore = block(json, "ore");
        if (ore == null) {
            return null;
        }
        BlockState deepslate = block(json, "deepslate");
        Kind kind = Kind.valueOf(GsonHelper.getAsString(json, "kind", "ore").toUpperCase(java.util.Locale.ROOT));
        double angle = Math.toRadians(GsonHelper.getAsDouble(json, "angle", 0.0d));
        long salt = id.hashCode() * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
        BlockState rich = null;
        double richChance = 0.0d;
        if (json.has("rich")) {
            JsonObject object = GsonHelper.getAsJsonObject(json, "rich");
            rich = block(object, "block");
            richChance = GsonHelper.getAsDouble(object, "chance", 0.0d);
        }
        List<Extra> extras = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "extras", new JsonArray())) {
            JsonObject object = element.getAsJsonObject();
            BlockState extra = block(object, "ore");
            if (extra != null) {
                BlockState deep = block(object, "deepslate");
                extras.add(new Extra(extra, deep == null ? extra : deep, GsonHelper.getAsDouble(object, "chance")));
            }
        }
        double rx = GsonHelper.getAsDouble(json, "rx");
        double rz = GsonHelper.getAsDouble(json, "rz");
        int ry = GsonHelper.getAsInt(json, "ry", 0);
        int y = GsonHelper.getAsInt(json, "y", 0);
        Geodes geodes = null;
        if (json.has("geodes")) {
            JsonObject object = GsonHelper.getAsJsonObject(json, "geodes");
            BlockState shell = block(object, "shell");
            BlockState budding = block(object, "budding");
            if (shell != null && budding != null) {
                int radius = GsonHelper.getAsInt(object, "radius", 4);
                RandomSource random = RandomSource.create(salt ^ 0x5DEECE66DL);
                List<int[]> centers = new ArrayList<>();
                for (int count = GsonHelper.getAsInt(object, "count", 0); count > 0; count--) {
                    double a = random.nextDouble() * Math.PI * 2.0d;
                    double r = Math.sqrt(random.nextDouble()) * 0.7d;
                    double u = Math.cos(a) * r * rx;
                    double v = Math.sin(a) * r * rz;
                    centers.add(new int[]{(int) Math.round(u * Math.cos(angle) - v * Math.sin(angle)),
                            Math.min(40, y + random.nextInt(Math.max(1, ry)) - ry / 2),
                            (int) Math.round(u * Math.sin(angle) + v * Math.cos(angle))});
                }
                geodes = new Geodes(shell, budding, GsonHelper.getAsDouble(object, "budding_chance", 0.12d), radius,
                        List.copyOf(centers));
            }
        }
        return new Body(id, GsonHelper.getAsString(json, "name", id), GsonHelper.getAsString(json, "resource", ""),
                kind, GsonHelper.getAsString(json, "country", "").replace("~", ""),
                GsonHelper.getAsInt(json, "tier", 2),
                earth.x(GsonHelper.getAsDouble(json, "longitude")), earth.z(GsonHelper.getAsDouble(json, "latitude")),
                rx, rz, ry, Math.cos(angle), Math.sin(angle), y, GsonHelper.getAsInt(json, "surface", 0),
                ore, deepslate == null ? ore : deepslate, GsonHelper.getAsDouble(json, "density", 0.3d),
                rich, richChance, List.copyOf(extras), block(json, "filler"), geodes,
                GsonHelper.getAsDouble(json, "oil", 0.0d), new ImprovedNoise(RandomSource.create(salt)), salt);
    }

    // -- Le nettoyage --------------------------------------------------------------------------

    /** Retire du chunk les minerais de la liste ; chacun prend la roche d'un voisin du meme chunk. */
    public static int clean(ServerLevel level, LevelChunk chunk) {
        Set<Block> targets = clear;
        if (targets.isEmpty()) {
            return 0;
        }
        int removed = 0;
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(state -> targets.contains(state.getBlock()))) {
                continue;
            }
            int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
            for (int ly = 0; ly < 16; ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        if (!targets.contains(section.getBlockState(lx, ly, lz).getBlock())) {
                            continue;
                        }
                        pos.set(minX + lx, baseY + ly, minZ + lz);
                        put(level, chunk, pos, rock(chunk, pos, targets));
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    /** La roche qui remplace un minerai : celle d'un voisin dans le chunk, sinon la pierre ou l'ardoise. */
    private static BlockState rock(LevelChunk chunk, BlockPos pos, Set<Block> targets) {
        ChunkPos at = chunk.getPos();
        for (Direction direction : NEIGHBOURS) {
            BlockPos near = pos.relative(direction);
            if ((near.getX() >> 4) != (at.getMinBlockX() >> 4) || (near.getZ() >> 4) != (at.getMinBlockZ() >> 4)
                    || near.getY() <= chunk.getMinY() || near.getY() >= chunk.getMaxY()) {
                continue;
            }
            BlockState state = chunk.getBlockState(near);
            if (!targets.contains(state.getBlock()) && natural(state)) {
                return state.getBlock().defaultBlockState();
            }
        }
        return (pos.getY() < 0 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
    }

    private static final Direction[] NEIGHBOURS = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST,
            Direction.EAST, Direction.UP};

    /** De la roche ou de la terre en place : ce qu'un gisement ou un nettoyage peut prendre. */
    private static boolean natural(BlockState state) {
        return state.is(BlockTags.STONE_ORE_REPLACEABLES) || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
                || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.SANDSTONE) || state.is(Blocks.CLAY)
                || state.is(BlockTags.TERRACOTTA);
    }

    private static boolean rock(BlockState state) {
        return state.is(BlockTags.STONE_ORE_REPLACEABLES) || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
    }

    private static boolean deep(BlockState state) {
        return state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
    }

    // -- La pose -------------------------------------------------------------------------------

    /**
     * Ecrit un bloc dans la section du chunk, sans passer par {@code Level.setBlock} : un minerai qui
     * remplace de la roche ne change ni la lumiere ni les hauteurs, et le chemin complet coutait un
     * dixieme de milliseconde par bloc. Le client est prevenu comme pour tout changement de bloc ; la
     * lumiere n'est revue que si de l'air apparait ou disparait (les geodes). Le chunk est a marquer
     * modifie ensuite ({@link #done}).
     */
    static void put(ServerLevel level, LevelChunk chunk, BlockPos pos, BlockState state) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
        BlockState before = section.setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state, false);
        if (before != state) {
            level.getChunkSource().blockChanged(pos);
            if (before.isAir() != state.isAir()) {
                level.getChunkSource().getLightEngine().checkBlock(pos);
            }
        }
    }

    /** Le chunk a change : a sauvegarder. */
    static void done(LevelChunk chunk) {
        chunk.markUnsaved();
    }

    /** Pose dans ce chunk la part de chaque gisement qui le touche ; rend le nombre de blocs poses. */
    public static int place(ServerLevel level, LevelChunk chunk) {
        int placed = 0;
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (Body body : bodies) {
            if (body.touches(minX, minZ)) {
                placed += switch (body.kind()) {
                    case ORE, POOL -> lens(level, chunk, body);
                    case PIPE -> pipe(level, chunk, body);
                    case SAND -> sand(level, chunk, body);
                };
                if (body.geodes() != null) {
                    placed += geodes(level, chunk, body);
                }
            }
        }
        return placed;
    }

    /** Le sol d'une colonne du chunk (le dernier bloc plein, fonds marins compris). */
    private static int floor(LevelChunk chunk, int x, int z) {
        return chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15);
    }

    private static double hash(long salt, int x, int y, int z) {
        long h = salt ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** La bosse du gisement en ce point : de 0,65 a 1,35. */
    private static double bump(Body body, int x, int y, int z) {
        return 1.0d + 0.35d * body.noise().noise(x * 0.07d, y * 0.09d, z * 0.07d);
    }

    /** Un corps de minerai ou une nappe : un ellipsoide bossele, sous la surface. */
    private static int lens(ServerLevel level, LevelChunk chunk, Body body) {
        int placed = 0;
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int ry = Math.max(1, body.ry());
        int span = (int) Math.ceil(ry * BUMP);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                double flat = body.flat(x + 0.5d, z + 0.5d);
                if (flat > BUMP * BUMP) {
                    continue;
                }
                int floor = floor(chunk, x, z);
                int center = body.surface() > 0 ? floor - body.surface()
                        : Math.min(body.y(), floor - ry - (body.kind() == Kind.POOL ? 6 : 4));
                int roof = body.surface() > 0 ? floor - 2 : floor - 3;
                for (int y = Math.max(chunk.getMinY() + 1, center - span); y <= Math.min(roof, center + span); y++) {
                    double dy = (double) (y - center) / ry;
                    double bump = bump(body, x, y, z);
                    if (flat + dy * dy > bump * bump) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState host = chunk.getBlockState(pos);
                    if (!natural(host)) {
                        continue;
                    }
                    BlockState put = pick(body, host, hash(body.salt(), x, y, z), flat + dy * dy < 0.25d);
                    if (put != null) {
                        put(level, chunk, pos, put);
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    /** Le bloc d'un point du gisement : le minerai, sa forme riche au coeur, un minerai associe, ou rien. */
    private static BlockState pick(Body body, BlockState host, double roll, boolean core) {
        boolean deep = deep(host);
        if (core && body.rich() != null && roll < body.richChance()) {
            return body.rich();
        }
        double density = core ? Math.min(0.95d, body.density() * 1.3d) : body.density();
        if (roll < density) {
            return deep ? body.deepslate() : body.ore();
        }
        roll -= density;
        for (Extra extra : body.extras()) {
            if (roll < extra.chance()) {
                return deep ? extra.deepslate() : extra.ore();
            }
            roll -= extra.chance();
        }
        return null;
    }

    /** Une cheminee de kimberlite : du tuf et du diamant, plus large en haut, du fond jusqu'a six blocs du sol. */
    private static int pipe(ServerLevel level, LevelChunk chunk, Body body) {
        int placed = 0;
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                double flat = body.flat(x + 0.5d, z + 0.5d);
                if (flat > BUMP * BUMP) {
                    continue;
                }
                int bottom = Math.max(chunk.getMinY() + 1, body.y());
                int top = floor(chunk, x, z) - 6;
                for (int y = bottom; y <= top; y++) {
                    double widen = 0.6d + 0.4d * (y - bottom) / Math.max(1.0d, top - bottom);
                    double bump = bump(body, x, y, z) * widen;
                    if (flat > bump * bump) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState host = chunk.getBlockState(pos);
                    if (!natural(host)) {
                        continue;
                    }
                    BlockState put = hash(body.salt(), x, y, z) < body.density()
                            ? (deep(host) ? body.deepslate() : body.ore())
                            : body.filler();
                    if (put != null) {
                        put(level, chunk, pos, put);
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    /** Une couche de sables bitumineux, de 2 a 2 + 2 x ry blocs sous la surface. */
    private static int sand(ServerLevel level, LevelChunk chunk, Body body) {
        int placed = 0;
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                double flat = body.flat(x + 0.5d, z + 0.5d);
                int floor = floor(chunk, x, z);
                if (flat > Math.pow(bump(body, x, floor, z), 2)) {
                    continue;
                }
                for (int y = floor - 2; y >= floor - 2 - 2 * body.ry(); y--) {
                    pos.set(x, y, z);
                    if (natural(chunk.getBlockState(pos)) && hash(body.salt(), x, y, z) < body.density()) {
                        put(level, chunk, pos, body.ore());
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    /** Les geodes de quartz : une coque de quartz certus semee de quartz bourgeonnant, du vide dedans. */
    private static int geodes(ServerLevel level, LevelChunk chunk, Body body) {
        Geodes geodes = body.geodes();
        int placed = 0;
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int r = geodes.radius();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int[] center : geodes.centers()) {
            int cx = (int) Math.round(body.centerX()) + center[0];
            int cy = center[1];
            int cz = (int) Math.round(body.centerZ()) + center[2];
            if (cx + r < minX || cx - r > minX + 15 || cz + r < minZ || cz - r > minZ + 15) {
                continue;
            }
            for (int x = Math.max(minX, cx - r); x <= Math.min(minX + 15, cx + r); x++) {
                for (int z = Math.max(minZ, cz - r); z <= Math.min(minZ + 15, cz + r); z++) {
                    for (int y = cy - r; y <= cy + r; y++) {
                        double distance = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz));
                        if (distance > r) {
                            continue;
                        }
                        pos.set(x, y, z);
                        BlockState host = chunk.getBlockState(pos);
                        if (!rock(host) && !host.is(body.ore().getBlock()) && !host.is(body.deepslate().getBlock())) {
                            continue;
                        }
                        BlockState put = distance < r - 1.2d ? Blocks.AIR.defaultBlockState()
                                : hash(body.salt() ^ 0x1234L, x, y, z) < geodes.buddingChance() ? geodes.budding()
                                : geodes.shell();
                        put(level, chunk, pos, put);
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    // -- Create Diesel Generators ----------------------------------------------------------------

    private static Method setOil;
    private static boolean oilTried;

    /**
     * Les chunks d'un champ ont du petrole pour la pompe du Create Diesel Generators :
     * 4 a 9 millions de mB par point de richesse, du bord au centre ; au-dela de
     * 10 millions, le mod rend le chunk inepuisable. Ailleurs sur la carte, le
     * datapack lui interdit le petrole ({@code deny_oil_biomes}).
     */
    public static void oil(ServerLevel level, ChunkPos chunk) {
        int best = 0;
        double x = chunk.getMinBlockX() + 8.0d;
        double z = chunk.getMinBlockZ() + 8.0d;
        for (Body body : bodies) {
            if (body.oil() <= 0.0d) {
                continue;
            }
            double flat = body.flat(x, z);
            if (flat < 1.0d) {
                best = Math.max(best, (int) Math.min(Integer.MAX_VALUE - 1L,
                        Math.round(body.oil() * (4_000_000.0d + 5_000_000.0d * (1.0d - Math.sqrt(flat))))));
            }
        }
        if (best <= 0) {
            return;
        }
        if (!oilTried) {
            oilTried = true;
            if (ModList.get().isLoaded("createdieselgenerators")) {
                try {
                    setOil = Class.forName("com.jesz.createdieselgenerators.world.OilChunksSavedData")
                            .getMethod("setChunkOilAmount", ServerLevel.class, ChunkPos.class, int.class);
                } catch (ReflectiveOperationException | LinkageError error) {
                    DoHy.LOGGER.warn("Create Diesel Generators : petrole des chunks inaccessible ({})", error.toString());
                }
            }
        }
        if (setOil != null) {
            try {
                setOil.invoke(null, level, chunk, best);
            } catch (ReflectiveOperationException error) {
                DoHy.LOGGER.warn("Create Diesel Generators : petrole du chunk {} non pose", chunk, error);
                setOil = null;
            }
        }
    }

    // -- Pour le joueur --------------------------------------------------------------------------

    /** Les gisements geants d'un pays, « Kiruna (fer), Aitik (cuivre) ». */
    public static String inCountry(String code) {
        StringBuilder text = new StringBuilder();
        for (Body body : bodies) {
            if (body.country().equals(code)) {
                if (!text.isEmpty()) {
                    text.append(", ");
                }
                text.append(body.name()).append(" (").append(body.resource()).append(')');
            }
        }
        return text.toString();
    }
}

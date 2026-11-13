package fr.atraxterra.dohy.earth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.spawn.DoHySpawn;
import fr.atraxterra.dohy.spawn.HologramBlockEntity;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.BufferedReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les capitales de la Terre : une ville jouable a la place de chaque vraie
 * capitale ({@code data/dohy/earth/capitals.json}).
 *
 * <p>La ville se pose a mesure que la carte se charge, chunk par chunk : un
 * chunk qui touche une ville est mis en file, son plan est dresse
 * ({@link CityBuilder}), puis pose quelques milliers de blocs par tick pour ne
 * pas faire ramer le serveur. La hauteur de la ville se mesure une fois, au
 * milieu de sa place, et se garde sur le monde. Le monument
 * ({@link CityMonuments}) attend que toute la place soit posee.
 */
public final class EarthCities {

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "earth/capitals.json");
    /** Blocs poses par tick, toutes villes comprises. */
    private static final int BUDGET = 4000;

    // -- Les donnees ---------------------------------------------------------------------------

    /** Une capitale, telle que l'ecrit {@code tools/earth_capitals.py}. */
    public record Entry(String id, String name, String country, double latitude, double longitude, int population,
                        int radius, String style, Optional<String> monument) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Entry::id),
                Codec.STRING.fieldOf("name").forGetter(Entry::name),
                Codec.STRING.fieldOf("country").forGetter(Entry::country),
                Codec.DOUBLE.fieldOf("latitude").forGetter(Entry::latitude),
                Codec.DOUBLE.fieldOf("longitude").forGetter(Entry::longitude),
                Codec.INT.optionalFieldOf("population", 0).forGetter(Entry::population),
                Codec.intRange(1, 4).fieldOf("radius").forGetter(Entry::radius),
                Codec.STRING.optionalFieldOf("style", "europe").forGetter(Entry::style),
                Codec.STRING.optionalFieldOf("monument").forGetter(Entry::monument)
        ).apply(instance, Entry::new));
    }

    public record Config(int version, List<Entry> cities) {
        static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("version").forGetter(Config::version),
                Entry.CODEC.listOf().fieldOf("cities").forGetter(Config::cities)
        ).apply(instance, Config::new));
    }

    /** Une ville placee sur la carte de ce monde. */
    public record Placed(Entry entry, CityBuilder.City city) {
    }

    /** Ce que le monde retient d'une ville : sa hauteur, les chunks de sa place deja poses, son monument. */
    public record State(String id, int baseY, int square, boolean monument) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(State::id),
                Codec.INT.fieldOf("base_y").forGetter(State::baseY),
                Codec.INT.optionalFieldOf("square", 0).forGetter(State::square),
                Codec.BOOL.optionalFieldOf("monument", false).forGetter(State::monument)
        ).apply(instance, State::new));
    }

    public record States(List<State> cities) {
        public static final States EMPTY = new States(List.of());
        public static final MapCodec<States> MAP_CODEC = State.CODEC.listOf().fieldOf("cities")
                .xmap(States::new, States::cities);

        public boolean exists() {
            return !cities.isEmpty();
        }

        State of(String id) {
            for (State state : cities) {
                if (state.id().equals(id)) {
                    return state;
                }
            }
            return null;
        }

        States with(State state) {
            List<State> next = new ArrayList<>(cities);
            next.removeIf(other -> other.id().equals(state.id()));
            next.add(state);
            return new States(List.copyOf(next));
        }
    }

    private static volatile Config config = new Config(1, List.of());
    private static volatile List<Placed> placed = List.of();
    private static volatile EarthData placedFor;
    private static final Long2IntOpenHashMap INDEX = new Long2IntOpenHashMap();
    private static final LongLinkedOpenHashSet QUEUE = new LongLinkedOpenHashSet();
    private static final Deque<Job> JOBS = new ArrayDeque<>();
    private static final Map<UUID, String> INSIDE = new ConcurrentHashMap<>();

    private EarthCities() {
    }

    public static boolean load(MinecraftServer server) {
        clear();
        config = new Config(1, List.of());
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            return true;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            var result = Config.CODEC.parse(JsonOps.INSTANCE, GsonHelper.parse(reader));
            if (result.isError()) {
                DoHy.LOGGER.error("Capitales de la Terre illisibles : {}",
                        result.error().map(Object::toString).orElse("erreur inconnue"));
                return false;
            }
            config = result.getOrThrow();
            DoHy.LOGGER.info("Capitales de la Terre : {} villes", config.cities().size());
            return true;
        } catch (Exception error) {
            DoHy.LOGGER.error("Echec du chargement des capitales de la Terre", error);
            return false;
        }
    }

    static void clear() {
        QUEUE.clear();
        JOBS.clear();
        INDEX.clear();
        INSIDE.clear();
        placed = List.of();
        placedFor = null;
    }

    private static long key(int cx, int cz) {
        return (cx & 0xFFFFFFFFL) | ((long) cz << 32);
    }

    /** Les villes placees sur la carte de ce monde ; vide sans carte. */
    public static List<Placed> placed() {
        EarthData earth = EarthData.get();
        if (earth == null) {
            return List.of();
        }
        if (placedFor != earth) {
            List<Placed> list = new ArrayList<>();
            INDEX.clear();
            INDEX.defaultReturnValue(-1);
            for (Entry entry : config.cities()) {
                int x = (int) Math.round(earth.x(entry.longitude()));
                int z = (int) Math.round(earth.z(entry.latitude()));
                CityBuilder.City city = new CityBuilder.City(entry.id(), entry.name(), entry.country(), x >> 4, z >> 4,
                        entry.radius(), entry.style(), entry.monument().orElse(null),
                        entry.id().hashCode() * 0x9E37_79B9_7F4A_7C15L);
                int index = list.size();
                list.add(new Placed(entry, city));
                for (int dx = -entry.radius(); dx <= entry.radius(); dx++) {
                    for (int dz = -entry.radius(); dz <= entry.radius(); dz++) {
                        if (city.role(city.chunkX() + dx, city.chunkZ() + dz) != CityBuilder.OUTSIDE) {
                            INDEX.putIfAbsent(key(city.chunkX() + dx, city.chunkZ() + dz), index);
                        }
                    }
                }
            }
            placed = List.copyOf(list);
            placedFor = earth;
        }
        return placed;
    }

    /** La ville de ce point, ou {@code null}. */
    public static Placed at(double x, double z) {
        List<Placed> all = placed();
        int index = INDEX.get(key((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4));
        return index < 0 || index >= all.size() ? null : all.get(index);
    }

    public static Placed byId(String id) {
        for (Placed city : placed()) {
            if (city.entry().id().equals(id)) {
                return city;
            }
        }
        return null;
    }

    private static States states(ServerLevel level) {
        return level.getData(DoHyEarth.CITY_STATES.get());
    }

    // -- La pose ----------------------------------------------------------------------------

    /** Un chunk de la carte vient d'arriver : en file s'il touche une ville et ne l'a pas recue. */
    static void arrived(LevelChunk chunk) {
        if (config.cities().isEmpty() || chunk.getData(DoHyEarth.CITY.get()) >= config.version()) {
            return;
        }
        placed();
        long key = key(chunk.getPos().x, chunk.getPos().z);
        if (INDEX.get(key) >= 0) {
            QUEUE.add(key);
        }
    }

    /** Ce qui reste a poser : un chunk de ville, ou le monument d'une place. */
    private static final class Job {
        final Placed city;
        final long chunk;
        final boolean monument;
        final long[] positions;
        final BlockState[] states;
        final List<CityBuilder.Label> labels;
        int cursor;

        Job(Placed city, long chunk, boolean monument, CityBuilder.Plan plan) {
            this.city = city;
            this.chunk = chunk;
            this.monument = monument;
            this.positions = new long[plan.blocks.size()];
            this.states = new BlockState[plan.blocks.size()];
            int i = 0;
            for (var entry : plan.blocks.long2ObjectEntrySet()) {
                positions[i] = entry.getLongKey();
                states[i] = entry.getValue();
                i++;
            }
            this.labels = plan.labels;
        }
    }

    static void tick(ServerLevel level) {
        int budget = BUDGET;
        while (budget > 0) {
            if (JOBS.isEmpty() && !next(level)) {
                return;
            }
            Job job = JOBS.peekFirst();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            while (job.cursor < job.positions.length && budget > 0) {
                pos.set(job.positions[job.cursor]);
                BlockState state = job.states[job.cursor];
                if (!level.getBlockState(pos).equals(state)) {
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    budget--;
                }
                job.cursor++;
                if ((job.cursor & 255) == 0) {
                    budget--;
                }
            }
            if (job.cursor >= job.positions.length) {
                JOBS.pollFirst();
                finish(level, job);
            }
        }
    }

    /** Dresse le plan du prochain chunk de la file ; faux s'il n'y a plus rien. */
    private static boolean next(ServerLevel level) {
        while (!QUEUE.isEmpty()) {
            long key = QUEUE.removeFirstLong();
            int cx = (int) key;
            int cz = (int) (key >> 32);
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null || chunk.getData(DoHyEarth.CITY.get()) >= config.version()) {
                continue;
            }
            List<Placed> all = placed();
            int index = INDEX.get(key);
            if (index < 0 || index >= all.size()) {
                continue;
            }
            Placed city = all.get(index);
            int baseY = baseY(level, city);
            CityBuilder.Plan plan = new CityBuilder.Plan();
            CityBuilder.chunk(plan, city.city(), cx, cz, baseY, ground(level));
            JOBS.addLast(new Job(city, key, false, plan));
            return true;
        }
        return false;
    }

    /** Le terrain naturel, lu dans les chunks du monde. */
    private static CityBuilder.Ground ground(ServerLevel level) {
        return new CityBuilder.Ground() {
            @Override
            public int floor(int x, int z) {
                return level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15);
            }

            @Override
            public int top(int x, int z) {
                return level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15,
                        z & 15);
            }

            @Override
            public boolean water(int x, int z) {
                return top(x, z) > floor(x, z);
            }
        };
    }

    /**
     * La hauteur de la ville : la mediane du sol au milieu de sa place, mesuree
     * une fois sur le terrain d'origine, jamais sous le niveau de la mer.
     */
    private static int baseY(ServerLevel level, Placed city) {
        States states = states(level);
        State state = states.of(city.entry().id());
        if (state != null) {
            return state.baseY();
        }
        CityBuilder.City c = city.city();
        CityBuilder.Ground ground = ground(level);
        int[] heights = new int[81];
        int i = 0;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                heights[i++] = ground.floor(c.centerX() + dx * 4, c.centerZ() + dz * 4);
            }
        }
        Arrays.sort(heights);
        int baseY = Math.max(level.getSeaLevel() + 1, heights[40] + 1);
        level.setData(DoHyEarth.CITY_STATES.get(), states.with(new State(city.entry().id(), baseY, 0, false)));
        DoHy.LOGGER.info("Villes : {} se pose a y = {}", city.entry().name(), baseY);
        return baseY;
    }

    /** Un chunk ou un monument fini : la marque du chunk, les noms, et le monument quand la place est complete. */
    private static void finish(ServerLevel level, Job job) {
        for (CityBuilder.Label label : job.labels) {
            level.setBlock(label.pos(), DoHySpawn.HOLOGRAM.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(label.pos()) instanceof HologramBlockEntity hologram) {
                hologram.set(label.text(), label.color(), label.scale());
            }
        }
        CityBuilder.City city = job.city.city();
        States states = states(level);
        State state = states.of(city.id());
        if (state == null) {
            return;
        }
        if (job.monument) {
            level.setData(DoHyEarth.CITY_STATES.get(), states.with(new State(state.id(), state.baseY(),
                    state.square(), true)));
            DoHy.LOGGER.info("Villes : monument de {} pose", city.name());
            return;
        }
        int cx = (int) job.chunk;
        int cz = (int) (job.chunk >> 32);
        level.getChunk(cx, cz).setData(DoHyEarth.CITY.get(), config.version());
        if (city.role(cx, cz) != CityBuilder.DISTRICT) {
            return;
        }
        int bit = 1 << ((cx - city.chunkX() + 1) * 3 + (cz - city.chunkZ() + 1));
        int square = state.square() | bit;
        int full = city.radius() >= 2 ? 0x1FF : 1 << 4;
        level.setData(DoHyEarth.CITY_STATES.get(), states.with(new State(state.id(), state.baseY(), square,
                state.monument())));
        if ((square & full) == full && !state.monument()) {
            CityBuilder.Plan plan = new CityBuilder.Plan();
            String country = countryName(city.country());
            CityMonuments.build(plan, city, state.baseY(), country);
            JOBS.addLast(new Job(job.city, job.chunk, true, plan));
        }
    }

    static String countryName(String code) {
        EarthData earth = EarthData.get();
        EarthData.Country country = earth == null ? null : earth.byCode(code);
        return country == null ? code : country.name();
    }

    // -- Les joueurs ------------------------------------------------------------------------

    /** Une fois par seconde : le joueur entre-t-il dans une capitale ? */
    static void track(ServerPlayer player) {
        Placed city = at(player.getX(), player.getZ());
        String now = city == null ? "" : city.entry().id();
        String before = INSIDE.put(player.getUUID(), now);
        if (before == null || before.equals(now) || city == null) {
            return;
        }
        String country = countryName(city.entry().country());
        EarthData earth = EarthData.get();
        EarthData.Country found = earth == null ? null : earth.byCode(city.entry().country());
        String holder = found == null ? null : EarthNations.holderName(player, found.id());
        String detail = "Capitale · " + country + (holder == null ? "" : ", aux mains " + EarthNations.de(holder));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 60, 16));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(city.entry().name())
                .withStyle(ChatFormatting.WHITE)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(detail)
                .withStyle(ChatFormatting.GOLD)));
        player.sendSystemMessage(Component.literal("Bienvenue a " + city.entry().name() + ", capitale : " + country
                + (holder == null ? "" : " (aux mains " + EarthNations.de(holder) + ")") + ".")
                .withStyle(ChatFormatting.GOLD));
    }

    static void forget(UUID player) {
        INSIDE.remove(player);
    }

    /** Pour les essais : combien de chunks et de monuments restent a poser. */
    public static String pending() {
        int blocks = 0;
        for (Job job : JOBS) {
            blocks += job.positions.length - job.cursor;
        }
        return QUEUE.size() + " chunk(s) en file, " + JOBS.size() + " plan(s) en cours, " + blocks + " bloc(s)";
    }
}

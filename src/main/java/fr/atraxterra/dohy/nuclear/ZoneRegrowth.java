package fr.atraxterra.dohy.nuclear;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.earth.EarthSites;
import fr.atraxterra.dohy.faction.base.NtmDefenses;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Les minerais des zones irradiees repoussent : toutes les deux heures, les
 * filons de NTM NEXT de Tchernobyl et de Fukushima se reforment dans la roche.
 *
 * <p>Les zones recoivent leurs minerais une seule fois, a la premiere visite
 * ({@code EarthDeposits}). Ici, a chaque cycle de deux heures, chaque chunk
 * d'une zone est remis a niveau : pour chaque minerai, des filons neufs sont
 * tires comme au premier jour, tant que le chunk en a moins que son plein (ce
 * qu'on n'a pas mine reste, et ne s'accumule pas). Un chunk charge est servi
 * dans la minute, les autres a leur prochaine visite : pour le joueur qui
 * revient, la zone a toujours repousse.
 *
 * <p>Ce qui repousse : l'uranium des zones (minerai de Sellafield), du thorium,
 * des gemmes radioactives, et un peu de schrabidium, deux fois moins qu'au
 * premier jour. Le diamant et l'emeraude des zones ne repoussent pas.
 *
 * <p>Seulement avec une carte de la Terre et NTM NEXT. Le cycle servi de
 * chaque chunk est garde dans {@code <monde>/dohy/zones-irradiees.json}.
 */
@EventBusSubscriber
public final class ZoneRegrowth {

    static final long CYCLE_MS = 2L * 3_600_000L;

    /** Un minerai qui repousse : ses filons par chunk et sa taille, comme au premier jour (sauf le schrabidium). */
    private record Rule(String ore, String deepslate, int minY, int maxY, double veins, int size) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("ore_sellafield_uranium_scorched", "", -56, 56, 2.0d, 5),
            new Rule("ore_thorium", "ore_thorium_deepslate", -56, 48, 1.0d, 4),
            new Rule("ore_sellafield_radgem", "", -56, 48, 0.8d, 3),
            new Rule("ore_sellafield_schrabidium", "", -56, 40, 0.15d, 3));

    private record Ready(Rule rule, BlockState ore, BlockState deepslate) {
        boolean matches(BlockState state) {
            return state.is(ore.getBlock()) || state.is(deepslate.getBlock());
        }
    }

    private static List<Ready> ready;
    private static final Map<Long, Long> SERVED = new HashMap<>();
    private static final LongLinkedOpenHashSet QUEUE = new LongLinkedOpenHashSet();
    private static long announced = -1L;
    private static boolean dirty;
    private static boolean loaded;

    private ZoneRegrowth() {
    }

    static long cycle(long now) {
        return Math.floorDiv(now, CYCLE_MS);
    }

    /** L'heure de la prochaine repousse (ms). */
    static long nextAt(long now) {
        return (cycle(now) + 1L) * CYCLE_MS;
    }

    /** Les zones irradiees de la carte ; vide sans carte. */
    static List<EarthSites.Placed> zones() {
        List<EarthSites.Placed> out = new ArrayList<>();
        for (EarthSites.Placed placed : EarthSites.placed()) {
            if (placed.site().irradiated()) {
                out.add(placed);
            }
        }
        return out;
    }

    /** Vrai si les zones repoussent dans cette partie : une carte de la Terre, ses zones, et NTM NEXT. */
    static boolean active() {
        return EarthData.get() != null && NtmDefenses.present() && !zones().isEmpty();
    }

    /** « Tchernobyl et Fukushima ». */
    static String names() {
        List<String> names = new ArrayList<>();
        for (EarthSites.Placed zone : zones()) {
            names.add(zone.site().name());
        }
        if (names.size() <= 1) {
            return names.isEmpty() ? "" : names.getFirst();
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " et " + names.getLast();
    }

    private static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    /** La zone dont ce chunk fait partie (son centre y est), ou {@code null}. */
    private static EarthSites.Placed zoneOf(int chunkX, int chunkZ) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        for (EarthSites.Placed zone : zones()) {
            if (zone.contains(x, z)) {
                return zone;
            }
        }
        return null;
    }

    private static List<Ready> ready() {
        if (ready == null) {
            List<Ready> list = new ArrayList<>();
            for (Rule rule : RULES) {
                Block ore = block(rule.ore());
                if (ore == null) {
                    continue;
                }
                Block deep = rule.deepslate().isEmpty() ? ore : block(rule.deepslate());
                list.add(new Ready(rule, ore.defaultBlockState(), (deep == null ? ore : deep).defaultBlockState()));
            }
            ready = List.copyOf(list);
        }
        return ready;
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath("hbm", id)).orElse(null);
    }

    // -- L'arrivee des chunks et le cycle -----------------------------------------------------------

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        EarthData earth = EarthData.get();
        if (!loaded || earth == null || !(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != earth.dimension() || !(event.getChunk() instanceof LevelChunk chunk)
                || !NtmDefenses.present()) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        if (zoneOf(pos.x(), pos.z()) != null) {
            due(key(pos.x(), pos.z()), cycle(System.currentTimeMillis()));
        }
    }

    /** Un chunk de zone vu : neuf, il compte comme servi ; en retard d'un cycle, il attend sa repousse. */
    private static void due(long key, long current) {
        Long served = SERVED.get(key);
        if (served == null) {
            // ses minerais du premier jour viennent d'arriver (ou arrivent) : la repousse attend le cycle suivant
            SERVED.put(key, current);
            dirty = true;
        } else if (served < current) {
            QUEUE.add(key);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        EarthData earth = EarthData.get();
        if (!loaded || earth == null || !NtmDefenses.present()) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.getLevel(earth.dimension());
        if (level == null) {
            return;
        }
        int tick = server.getTickCount();
        long now = System.currentTimeMillis();
        long current = cycle(now);
        if (tick % 100 == 0) {
            boolean fresh = current > announced;
            if (fresh) {
                boolean first = announced < 0L;
                announced = current;
                dirty = true;
                if (!first && server.getPlayerList().getPlayerCount() > 0 && !zones().isEmpty()) {
                    server.getPlayerList().broadcastSystemMessage(NuclearDrops.prefix().append(Component.literal(
                            "Les minerais irradies ont repousse a " + names() + " : uranium, thorium, gemmes "
                                    + "radioactives et un peu de schrabidium.").withStyle(ChatFormatting.YELLOW)),
                            false);
                }
            }
            if (fresh || tick % 1200 == 0) {
                scan(level, current);
            }
        }
        for (int count = 0; count < 2 && !QUEUE.isEmpty(); count++) {
            long key = QUEUE.removeFirstLong();
            LevelChunk chunk = level.getChunkSource().getChunkNow((int) key, (int) (key >> 32));
            Long served = SERVED.get(key);
            if (chunk == null || served != null && served >= current) {
                // decharge : il sera servi a sa prochaine visite
                continue;
            }
            int placed = regrow(level, chunk, level.getRandom());
            SERVED.put(key, current);
            dirty = true;
            if (placed > 0) {
                DoHy.LOGGER.debug("Zones irradiees : {} blocs de minerai repousses en {}", placed, chunk.getPos());
            }
        }
        if (dirty && tick % 1200 == 600) {
            save(server);
        }
    }

    /** Les chunks charges des zones : ceux qui ont un cycle de retard attendent leur repousse. */
    private static void scan(ServerLevel level, long current) {
        for (EarthSites.Placed zone : zones()) {
            int reach = zone.site().radius() + 16;
            for (int cx = (zone.x() - reach) >> 4; cx <= (zone.x() + reach) >> 4; cx++) {
                for (int cz = (zone.z() - reach) >> 4; cz <= (zone.z() + reach) >> 4; cz++) {
                    if (zone.contains((cx << 4) + 8, (cz << 4) + 8)
                            && level.getChunkSource().getChunkNow(cx, cz) != null) {
                        due(key(cx, cz), current);
                    }
                }
            }
        }
    }

    // -- La repousse -------------------------------------------------------------------------------

    /**
     * Remet a niveau les minerais d'un chunk de zone : pour chacun, des filons
     * tires comme au premier jour, tant que le chunk n'a pas son plein. Rend le
     * nombre de blocs poses.
     */
    static int regrow(ServerLevel level, LevelChunk chunk, RandomSource random) {
        List<Ready> rules = ready();
        if (rules.isEmpty()) {
            return 0;
        }
        int[] count = count(level, chunk, rules);
        int placed = 0;
        for (int index = 0; index < rules.size(); index++) {
            Rule rule = rules.get(index).rule();
            int cap = (int) Math.ceil(rule.veins()) * rule.size();
            int veins = (int) rule.veins() + (random.nextDouble() < rule.veins() - (int) rule.veins() ? 1 : 0);
            // un filon de plus seulement s'il tient sous le plein : le chunk n'en a jamais plus qu'au premier jour
            for (int vein = 0; vein < veins && count[index] + rule.size() <= cap; vein++) {
                int laid = vein(level, chunk, rules.get(index), random);
                count[index] += laid;
                placed += laid;
            }
        }
        return placed;
    }

    /** Les blocs de chaque minerai deja dans le chunk, dans sa tranche de hauteur. */
    private static int[] count(ServerLevel level, LevelChunk chunk, List<Ready> rules) {
        int[] count = new int[rules.size()];
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Ready ready : rules) {
            minY = Math.min(minY, ready.rule().minY());
            maxY = Math.max(maxY, ready.rule().maxY());
        }
        minY = Math.max(minY, level.getMinY());
        maxY = Math.min(maxY, level.getMaxY());
        for (int index = chunk.getSectionIndex(minY); index <= chunk.getSectionIndex(maxY); index++) {
            if (index < 0 || index >= chunk.getSectionsCount()) {
                continue;
            }
            LevelChunkSection section = chunk.getSection(index);
            if (section.hasOnlyAir() || !section.maybeHas(state -> {
                for (Ready ready : rules) {
                    if (ready.matches(state)) {
                        return true;
                    }
                }
                return false;
            })) {
                continue;
            }
            int y0 = chunk.getSectionYFromSectionIndex(index) << 4;
            for (int y = 0; y < 16; y++) {
                int worldY = y0 + y;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        for (int rule = 0; rule < rules.size(); rule++) {
                            Ready ready = rules.get(rule);
                            if (worldY >= ready.rule().minY() && worldY <= ready.rule().maxY()
                                    && ready.matches(state)) {
                                count[rule]++;
                            }
                        }
                    }
                }
            }
        }
        return count;
    }

    /** Un filon : une marche au hasard qui reste dans son chunk et ne remplace que la roche ; rend ses blocs poses. */
    private static int vein(ServerLevel level, LevelChunk chunk, Ready ready, RandomSource random) {
        ChunkPos pos = chunk.getPos();
        Rule rule = ready.rule();
        int x = pos.getMinBlockX() + 1 + random.nextInt(14);
        int z = pos.getMinBlockZ() + 1 + random.nextInt(14);
        int floor = Math.max(rule.minY(), level.getMinY() + 1);
        int roof = Math.min(rule.maxY(), chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15) - 4);
        if (roof < floor) {
            return 0;
        }
        int y = floor + random.nextInt(roof - floor + 1);
        int placed = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int block = 0; block < rule.size(); block++) {
            at.set(x, y, z);
            BlockState state = chunk.getBlockState(at);
            if (state.is(BlockTags.STONE_ORE_REPLACEABLES)) {
                level.setBlock(at, ready.ore(), Block.UPDATE_CLIENTS);
                placed++;
            } else if (state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
                level.setBlock(at, ready.deepslate(), Block.UPDATE_CLIENTS);
                placed++;
            }
            x = Math.max(pos.getMinBlockX(), Math.min(pos.getMaxBlockX(), x + random.nextInt(3) - 1));
            y = Math.max(floor, Math.min(roof, y + random.nextInt(3) - 1));
            z = Math.max(pos.getMinBlockZ(), Math.min(pos.getMaxBlockZ(), z + random.nextInt(3) - 1));
        }
        return placed;
    }

    /**
     * Toutes les zones repoussent maintenant (essais) : les chunks charges
     * dans la minute, les autres a leur visite. Rend le nombre de chunks de zone.
     */
    static int force(MinecraftServer server) {
        int chunks = 0;
        for (EarthSites.Placed zone : zones()) {
            int reach = zone.site().radius() + 16;
            for (int cx = (zone.x() - reach) >> 4; cx <= (zone.x() + reach) >> 4; cx++) {
                for (int cz = (zone.z() - reach) >> 4; cz <= (zone.z() + reach) >> 4; cz++) {
                    if (zone.contains((cx << 4) + 8, (cz << 4) + 8)) {
                        SERVED.put(key(cx, cz), -1L);
                        chunks++;
                    }
                }
            }
        }
        EarthData earth = EarthData.get();
        ServerLevel level = earth == null ? null : server.getLevel(earth.dimension());
        if (level != null) {
            scan(level, cycle(System.currentTimeMillis()));
        }
        dirty = true;
        return chunks;
    }

    // -- Le disque ---------------------------------------------------------------------------------

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("dohy").resolve("zones-irradiees.json");
    }

    private static void save(MinecraftServer server) {
        if (!loaded) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("announced", announced);
        JsonObject served = new JsonObject();
        for (Map.Entry<Long, Long> entry : SERVED.entrySet()) {
            served.addProperty(Long.toString(entry.getKey()), entry.getValue());
        }
        root.add("served", served);
        Path file = file(server);
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling("zones-irradiees.json.tmp");
            Files.writeString(temporary, root.toString());
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException error) {
            DoHy.LOGGER.warn("Zones irradiees : repousses non ecrites", error);
        }
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        SERVED.clear();
        QUEUE.clear();
        ready = null;
        announced = -1L;
        dirty = false;
        loaded = true;
        Path file = file(event.getServer());
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            announced = root.get("announced").getAsLong();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject("served").entrySet()) {
                SERVED.put(Long.parseLong(entry.getKey()), entry.getValue().getAsLong());
            }
        } catch (IOException | RuntimeException error) {
            DoHy.LOGGER.warn("Zones irradiees : repousses illisibles", error);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        save(event.getServer());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        SERVED.clear();
        QUEUE.clear();
        ready = null;
        loaded = false;
    }
}

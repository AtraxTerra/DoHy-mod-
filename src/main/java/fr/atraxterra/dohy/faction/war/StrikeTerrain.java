package fr.atraxterra.dohy.faction.war;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import fr.atraxterra.dohy.DoHy;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Le terrain d'une base bombardee revient, cinq heures apres l'impact.
 *
 * <p><b>Le releve.</b> Avant le tir, chaque chunk de la zone (le rayon de
 * l'explosion, et une marge) est releve : une tranche de hauteur qui va du
 * fond du cratere possible a 40 blocs au-dessus de son point le plus haut. Les
 * claims sont releves comme le reste : c'est ta base qu'on bombarde.
 *
 * <p><b>Le retour.</b> Les releves sont ecrits dans
 * {@code <monde>/dohy/frappes/} avant le tir ; a l'echeance (serveur eteint
 * compris), les blocs qui ont change sont reposes tranche par tranche (8 ms
 * par tick au plus), sans voisinage. Restent tels quels :
 *
 * <ul>
 *   <li>ce qu'un joueur a pose ou casse depuis l'impact (il garde son
 *       travail, et un minerai qu'il a mine ne repousse pas) ;</li>
 *   <li>les conteneurs, les machines et les multiblocs : jamais touches par
 *       le tir, jamais reposes par le retour (rien ne se duplique).</li>
 * </ul>
 *
 * <p>Un chunk deja releve par une frappe qui n'a pas fini de revenir n'est pas
 * releve une seconde fois : son terrain d'origine reviendra a la plus tardive
 * des deux echeances.
 *
 * <p><b>L'abri.</b> Une charge nucleaire ne connait pas les blocs a epargner :
 * juste avant l'impact, les conteneurs, les machines et les multiblocs du
 * cratere sont mis a l'abri (ecrits sur le disque, avec leur contenu, puis
 * retires sans rien vider), et reposes quand l'explosion est finie — au
 * redemarrage si le serveur tombe entre-temps.
 */
final class StrikeTerrain {

    /** Poser sans voisinage, sans {@code onPlace} et sans vider de conteneur. */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SKIP_ON_PLACE;
    /** Le delai du retour, apres l'impact. */
    static final long RETURN_MS = 5L * 3_600_000L;

    private static final int MAGIC = 0x44535431;
    private static final int BELOW = 28;
    private static final int ABOVE = 40;
    private static final int SLAB = 16;
    private static final long BUDGET = 8_000_000L;
    private static final int TIMEOUT_TICKS = 180 * 20;

    private StrikeTerrain() {
    }

    static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    static int keyX(long key) {
        return (int) key;
    }

    static int keyZ(long key) {
        return (int) (key >> 32);
    }

    private static Path folder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("dohy").resolve("frappes");
    }

    // -- Les releves ------------------------------------------------------------------------

    /** Le releve d'un chunk : sa tranche de hauteur, une palette et un indice par bloc. */
    static final class Capture {
        final int chunkX;
        final int chunkZ;
        final int minY;
        final int height;
        final List<BlockState> palette;
        final short[] cells;

        Capture(int chunkX, int chunkZ, int minY, int height, List<BlockState> palette, short[] cells) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.minY = minY;
            this.height = height;
            this.palette = palette;
            this.cells = cells;
        }

        static Capture of(ServerLevel level, LevelChunk chunk, int floor) {
            int lowest = Integer.MAX_VALUE;
            int highest = Integer.MIN_VALUE;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    lowest = Math.min(lowest, chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z));
                    highest = Math.max(highest, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
                }
            }
            int minY = Math.max(level.getMinY(), Math.min(lowest - BELOW, floor));
            int maxY = Math.min(level.getMaxY(), highest + ABOVE);
            int height = Math.max(1, maxY - minY + 1);
            List<BlockState> palette = new ArrayList<>();
            Map<BlockState, Short> indices = new IdentityHashMap<>();
            short[] cells = new short[height << 8];
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int x0 = chunk.getPos().getMinBlockX();
            int z0 = chunk.getPos().getMinBlockZ();
            BlockState last = null;
            short lastIndex = 0;
            int at = 0;
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = chunk.getBlockState(pos.set(x0 + x, minY + y, z0 + z));
                        if (state != last) {
                            Short known = indices.get(state);
                            if (known == null) {
                                known = (short) palette.size();
                                palette.add(state);
                                indices.put(state, known);
                            }
                            last = state;
                            lastIndex = known;
                        }
                        cells[at++] = lastIndex;
                    }
                }
            }
            return new Capture(chunk.getPos().x(), chunk.getPos().z(), minY, height, palette, cells);
        }
    }

    /** Les releves d'une frappe, avant son impact. */
    static final class Ledger {
        final String key;
        final String label;
        final ResourceKey<Level> dimension;
        final UUID owner;
        final int x;
        final int z;
        final int radius;
        /** Le fond du cratere possible : chaque tranche descend au moins jusque-la. */
        final int floor;
        final Long2ObjectOpenHashMap<Capture> chunks = new Long2ObjectOpenHashMap<>();
        final LongOpenHashSet refused = new LongOpenHashSet();
        /** Les frappes qui tenaient deja une partie de la zone : leur retour attendra le notre. */
        final Set<String> linked = new LinkedHashSet<>();
        /** Ce que les joueurs ont pose ou casse depuis le releve : ils le garderont. */
        final LongOpenHashSet touched = new LongOpenHashSet();

        Ledger(String key, String label, ResourceKey<Level> dimension, UUID owner, int x, int z, int radius,
               int floor) {
            this.key = key;
            this.label = label;
            this.dimension = dimension;
            this.owner = owner;
            this.x = x;
            this.z = z;
            this.radius = radius;
            this.floor = floor;
        }

        boolean covers(int chunkX, int chunkZ) {
            int nearX = Math.max(chunkX << 4, Math.min(x, (chunkX << 4) + 15));
            int nearZ = Math.max(chunkZ << 4, Math.min(z, (chunkZ << 4) + 15));
            long dx = nearX - x;
            long dz = nearZ - z;
            return dx * dx + dz * dz <= (long) radius * radius;
        }

        int fromX() {
            return (x - radius) >> 4;
        }

        int toX() {
            return (x + radius) >> 4;
        }

        int fromZ() {
            return (z - radius) >> 4;
        }

        int toZ() {
            return (z + radius) >> 4;
        }
    }

    /** Les frappes en preparation ou en vol, par cle. */
    private static final Map<String, Ledger> OPEN = new LinkedHashMap<>();
    /** Chaque chunk releve, et la frappe qui le tient (avant l'impact ou en attente de retour), par dimension. */
    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<String>> OWNER = new HashMap<>();
    /** Les retours en attente, par cle. */
    private static final Map<String, Pending> PENDING = new LinkedHashMap<>();
    private static final Map<String, CompletableFuture<Void>> WRITES = new LinkedHashMap<>();
    /** Les abris a reposer au demarrage : la frappe a ete coupee par un arret. */
    private static final List<Shelter> ORPHANS = new ArrayList<>();
    private static Restore restore;

    private static Long2ObjectOpenHashMap<String> owners(ResourceKey<Level> dimension) {
        return OWNER.computeIfAbsent(dimension, ignored -> new Long2ObjectOpenHashMap<>());
    }

    static Ledger open(String key, String label, ServerLevel level, UUID owner, int x, int z, int radius,
                       int floor) {
        Ledger ledger = new Ledger(key, label, level.dimension(), owner, x, z, radius, floor);
        OPEN.put(key, ledger);
        return ledger;
    }

    /**
     * Releve la zone, quelques chunks par appel (8 ms) ; vrai quand c'est fait.
     * Un chunk pas encore charge fait attendre, sauf au dernier appel
     * ({@code last}) : il est alors laisse, le tir n'y changera rien de
     * durable.
     */
    static boolean prepare(ServerLevel level, Ledger ledger, boolean last) {
        long deadline = System.nanoTime() + BUDGET;
        Long2ObjectOpenHashMap<String> owners = owners(ledger.dimension);
        boolean done = true;
        for (int cx = ledger.fromX(); cx <= ledger.toX(); cx++) {
            for (int cz = ledger.fromZ(); cz <= ledger.toZ(); cz++) {
                long key = key(cx, cz);
                if (ledger.chunks.containsKey(key) || ledger.refused.contains(key) || !ledger.covers(cx, cz)) {
                    continue;
                }
                String owner = owners.get(key);
                if (owner != null && !owner.equals(ledger.key)) {
                    ledger.refused.add(key);
                    ledger.linked.add(owner);
                    continue;
                }
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    if (last) {
                        ledger.refused.add(key);
                    } else {
                        done = false;
                    }
                    continue;
                }
                if (System.nanoTime() > deadline) {
                    return false;
                }
                ledger.chunks.put(key, Capture.of(level, chunk, ledger.floor));
                owners.put(key, ledger.key);
            }
        }
        return done;
    }

    /** Les releves sont faits : ils partent sur le disque pendant que le missile vole. */
    static void arm(MinecraftServer server, Ledger ledger) {
        List<Capture> captures = new ArrayList<>(ledger.chunks.values());
        Path terrain = folder(server).resolve(ledger.key + ".terrain");
        Path due = folder(server).resolve(ledger.key + ".due");
        Pending draft = Pending.of(ledger, 0L);
        try {
            writeDue(due, draft);
        } catch (IOException error) {
            DoHy.LOGGER.error("Guerre : echeance de la frappe {} non ecrite", ledger.label, error);
        }
        WRITES.put(ledger.key, CompletableFuture.runAsync(() -> {
            try {
                write(terrain, captures);
            } catch (IOException | RuntimeException error) {
                DoHy.LOGGER.error("Guerre : releve du terrain de la frappe {} non ecrit : il restera tel quel",
                        ledger.label, error);
            }
        }));
    }

    /**
     * L'impact : le terrain reviendra a {@code due}. Les frappes liees (qui
     * tenaient deja une partie de la zone) attendent au moins jusque-la.
     */
    static void strike(MinecraftServer server, Ledger ledger, long due) {
        OPEN.remove(ledger.key);
        for (String link : ledger.linked) {
            Pending older = PENDING.get(link);
            if (older != null && older.due < due) {
                older.due = due;
                older.warned = false;
                older.dirty = true;
            }
        }
        if (ledger.chunks.isEmpty()) {
            forgetFiles(server, ledger.key);
            return;
        }
        Pending pending = Pending.of(ledger, due);
        PENDING.put(ledger.key, pending);
        try {
            writeDue(folder(server).resolve(ledger.key + ".due"), pending);
        } catch (IOException error) {
            DoHy.LOGGER.error("Guerre : echeance de la frappe {} non ecrite", ledger.label, error);
        }
    }

    /** La frappe n'aura pas lieu : ses releves sont oublies. */
    static void discard(MinecraftServer server, Ledger ledger) {
        OPEN.remove(ledger.key);
        Long2ObjectOpenHashMap<String> owners = owners(ledger.dimension);
        for (long chunk : ledger.chunks.keySet().toLongArray()) {
            if (ledger.key.equals(owners.get(chunk))) {
                owners.remove(chunk);
            }
        }
        forgetFiles(server, ledger.key);
    }

    private static void forgetFiles(MinecraftServer server, String key) {
        Path folder = folder(server);
        CompletableFuture<Void> write = WRITES.remove(key);
        Runnable delete = () -> {
            try {
                Files.deleteIfExists(folder.resolve(key + ".due"));
                Files.deleteIfExists(folder.resolve(key + ".terrain"));
            } catch (IOException error) {
                DoHy.LOGGER.warn("Guerre : releve de la frappe {} non efface", key, error);
            }
        };
        if (write == null) {
            delete.run();
        } else {
            write.whenComplete((ignored, error) -> delete.run());
        }
    }

    // -- Le travail des joueurs -------------------------------------------------------------------

    /**
     * Un joueur pose ou casse un bloc : s'il est dans une zone relevee (avant
     * l'impact comme apres), le retour du terrain n'y touchera pas.
     */
    static void touch(Level level, BlockPos pos) {
        if (PENDING.isEmpty() && OPEN.isEmpty()) {
            return;
        }
        Long2ObjectOpenHashMap<String> owners = OWNER.get(level.dimension());
        if (owners == null) {
            return;
        }
        String owner = owners.get(key(pos.getX() >> 4, pos.getZ() >> 4));
        if (owner == null) {
            return;
        }
        Pending pending = PENDING.get(owner);
        if (pending != null) {
            if (pending.touched.add(pos.asLong())) {
                pending.dirty = true;
            }
            return;
        }
        Ledger ledger = OPEN.get(owner);
        if (ledger != null) {
            ledger.touched.add(pos.asLong());
        }
    }

    // -- Les retours en attente --------------------------------------------------------------------

    private static final class Pending {
        final String key;
        final String label;
        final ResourceKey<Level> dimension;
        final UUID owner;
        long due;
        final long[] chunks;
        final int x;
        final int z;
        final int radius;
        final LongOpenHashSet touched = new LongOpenHashSet();
        boolean warned;
        boolean dirty;

        Pending(String key, String label, ResourceKey<Level> dimension, UUID owner, long due, long[] chunks, int x,
                int z, int radius) {
            this.key = key;
            this.label = label;
            this.dimension = dimension;
            this.owner = owner;
            this.due = due;
            this.chunks = chunks;
            this.x = x;
            this.z = z;
            this.radius = radius;
        }

        static Pending of(Ledger ledger, long due) {
            Pending pending = new Pending(ledger.key, ledger.label, ledger.dimension, ledger.owner, due,
                    ledger.chunks.keySet().toLongArray(), ledger.x, ledger.z, ledger.radius);
            pending.touched.addAll(ledger.touched);
            return pending;
        }
    }

    /** Le nombre de terrains en attente de retour. */
    static int pending() {
        return PENDING.size() + (restore != null ? 1 : 0);
    }

    /** L'echeance du retour de ce joueur (millisecondes du temps reel), ou 0. */
    static long dueFor(UUID owner) {
        long best = 0L;
        for (Pending pending : PENDING.values()) {
            if (owner.equals(pending.owner) && (best == 0L || pending.due < best)) {
                best = pending.due;
            }
        }
        return best;
    }

    /** Fait revenir tous les terrains tout de suite (commande d'administration). */
    static int hurry() {
        int count = 0;
        for (Pending pending : PENDING.values()) {
            pending.due = 0L;
            pending.warned = true;
            count++;
        }
        return count;
    }

    private static void writeDue(Path file, Pending pending) throws IOException {
        Files.createDirectories(file.getParent());
        StringBuilder chunks = new StringBuilder();
        for (long key : pending.chunks) {
            if (!chunks.isEmpty()) {
                chunks.append(' ');
            }
            chunks.append(keyX(key)).append(',').append(keyZ(key));
        }
        StringBuilder touched = new StringBuilder();
        for (long pos : pending.touched.toLongArray()) {
            if (!touched.isEmpty()) {
                touched.append(' ');
            }
            touched.append(Long.toHexString(pos));
        }
        String text = "due=" + pending.due + "\n"
                + "label=" + pending.label + "\n"
                + "dimension=" + pending.dimension.identifier() + "\n"
                + "area=" + pending.x + " " + pending.z + " " + pending.radius + "\n"
                + "owner=" + (pending.owner == null ? "" : pending.owner) + "\n"
                + "chunks=" + chunks + "\n"
                + "touched=" + touched + "\n";
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, text);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private static Pending readDue(String key, Path file) throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(file)) {
            int at = line.indexOf('=');
            if (at > 0) {
                values.put(line.substring(0, at), line.substring(at + 1));
            }
        }
        long due = Long.parseLong(values.getOrDefault("due", "0").trim());
        String[] area = values.getOrDefault("area", "0 0 0").trim().split(" ");
        List<Long> chunks = new ArrayList<>();
        String list = values.getOrDefault("chunks", "").trim();
        if (!list.isEmpty()) {
            for (String pair : list.split(" ")) {
                String[] xz = pair.split(",");
                chunks.add(key(Integer.parseInt(xz[0]), Integer.parseInt(xz[1])));
            }
        }
        String owner = values.getOrDefault("owner", "").trim();
        Pending pending = new Pending(key, values.getOrDefault("label", key),
                ResourceKey.create(Registries.DIMENSION, Identifier.parse(values.getOrDefault("dimension",
                        "minecraft:overworld").trim())), owner.isEmpty() ? null : UUID.fromString(owner), due,
                chunks.stream().mapToLong(Long::longValue).toArray(), Integer.parseInt(area[0]),
                Integer.parseInt(area[1]), Integer.parseInt(area[2]));
        String touched = values.getOrDefault("touched", "").trim();
        if (!touched.isEmpty()) {
            for (String pos : touched.split(" ")) {
                pending.touched.add(Long.parseUnsignedLong(pos, 16));
            }
        }
        return pending;
    }

    private static void write(Path file, List<Capture> captures) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(Files.newOutputStream(temporary)), 1 << 16))) {
            out.writeInt(MAGIC);
            out.writeInt(captures.size());
            for (Capture capture : captures) {
                out.writeInt(capture.chunkX);
                out.writeInt(capture.chunkZ);
                out.writeInt(capture.minY);
                out.writeInt(capture.height);
                out.writeInt(capture.palette.size());
                for (BlockState state : capture.palette) {
                    out.writeUTF(BlockState.CODEC.encodeStart(JsonOps.INSTANCE, state).result()
                            .map(JsonElement::toString).orElse(""));
                }
                for (short cell : capture.cells) {
                    out.writeShort(cell);
                }
            }
        }
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private static List<Capture> read(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(file)), 1 << 16))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("releve illisible : " + file);
            }
            int count = in.readInt();
            List<Capture> captures = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int chunkX = in.readInt();
                int chunkZ = in.readInt();
                int minY = in.readInt();
                int height = in.readInt();
                int size = in.readInt();
                List<BlockState> palette = new ArrayList<>(size);
                for (int p = 0; p < size; p++) {
                    String json = in.readUTF();
                    BlockState state = Blocks.AIR.defaultBlockState();
                    if (!json.isEmpty()) {
                        state = BlockState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result()
                                .orElse(state);
                    }
                    palette.add(state);
                }
                short[] cells = new short[height << 8];
                for (int c = 0; c < cells.length; c++) {
                    cells[c] = in.readShort();
                }
                captures.add(new Capture(chunkX, chunkZ, minY, height, palette, cells));
            }
            return captures;
        }
    }

    // -- L'abri des conteneurs ---------------------------------------------------------------------

    /** Ce qu'une charge nucleaire ne doit pas toucher : mis de cote le temps de l'explosion. */
    static final class Shelter {
        record Held(BlockPos pos, BlockState state, CompoundTag data) {
        }

        final String key;
        final ResourceKey<Level> dimension;
        final List<Held> held;
        final long since;

        Shelter(String key, ResourceKey<Level> dimension, List<Held> held, long since) {
            this.key = key;
            this.dimension = dimension;
            this.held = held;
            this.since = since;
        }

        int size() {
            return held.size();
        }

        /**
         * Met a l'abri ce qu'il faut epargner dans cette sphere : ecrit sur le
         * disque d'abord, retire ensuite (sans vider les conteneurs, sans
         * prevenir les voisins : un multibloc de NTM ne se demolit pas).
         */
        static Shelter lift(ServerLevel level, String key, BlockPos center, int radius) {
            List<Held> held = new ArrayList<>();
            long reach = (long) radius * radius;
            int minY = Math.max(level.getMinY(), center.getY() - radius);
            int maxY = Math.min(level.getMaxY(), center.getY() + radius);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
                for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity entity : List.copyOf(chunk.getBlockEntities().values())) {
                        BlockPos at = entity.getBlockPos();
                        if (at.distSqr(center) <= reach) {
                            held.add(new Held(at.immutable(), entity.getBlockState(),
                                    entity.saveWithFullMetadata(level.registryAccess())));
                        }
                    }
                    for (int index = chunk.getSectionIndex(minY); index <= chunk.getSectionIndex(maxY); index++) {
                        if (index < 0 || index >= chunk.getSectionsCount()) {
                            continue;
                        }
                        LevelChunkSection section = chunk.getSection(index);
                        if (section.hasOnlyAir() || !section.maybeHas(state -> !state.hasBlockEntity()
                                && NtmArsenal.spared(state))) {
                            continue;
                        }
                        int y0 = chunk.getSectionYFromSectionIndex(index) << 4;
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                for (int x = 0; x < 16; x++) {
                                    BlockState state = section.getBlockState(x, y, z);
                                    if (state.hasBlockEntity() || !NtmArsenal.spared(state)) {
                                        continue;
                                    }
                                    pos.set((cx << 4) + x, y0 + y, (cz << 4) + z);
                                    if (pos.distSqr(center) <= reach) {
                                        held.add(new Held(pos.immutable(), state, null));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Shelter shelter = new Shelter(key, level.dimension(), held, System.currentTimeMillis());
            if (held.isEmpty()) {
                return shelter;
            }
            try {
                shelter.save(level.getServer(), level);
            } catch (IOException | RuntimeException error) {
                DoHy.LOGGER.error("Guerre : abri de la frappe {} non ecrit : rien n'est retire", key, error);
                return new Shelter(key, level.dimension(), List.of(), shelter.since);
            }
            for (Held item : held) {
                level.setBlock(item.pos(), Blocks.AIR.defaultBlockState(), FLAGS);
            }
            DoHy.LOGGER.info("Guerre : {} blocs mis a l'abri avant l'impact ({})", held.size(), key);
            return shelter;
        }

        /** Repose tout, contenu compris, puis oublie l'abri. Rend le nombre de blocs reposes. */
        int putBack(MinecraftServer server) {
            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                return 0;
            }
            int count = 0;
            for (Held item : held) {
                level.setBlock(item.pos(), item.state(), FLAGS);
                if (item.data() != null) {
                    BlockEntity entity = level.getBlockEntity(item.pos());
                    if (entity != null) {
                        try {
                            entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
                                    level.registryAccess(), item.data()));
                            entity.setChanged();
                        } catch (RuntimeException error) {
                            DoHy.LOGGER.error("Guerre : contenu de {} non repose", item.pos(), error);
                        }
                    }
                }
                level.sendBlockUpdated(item.pos(), item.state(), item.state(), Block.UPDATE_CLIENTS);
                count++;
            }
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && !level.noCollision(player)) {
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
                    player.teleportTo(player.getX(), y, player.getZ());
                    player.resetFallDistance();
                }
            }
            try {
                Files.deleteIfExists(file(server, key));
            } catch (IOException error) {
                DoHy.LOGGER.warn("Guerre : abri {} non efface", key, error);
            }
            return count;
        }

        private static Path file(MinecraftServer server, String key) {
            return folder(server).resolve(key + ".abri");
        }

        private void save(MinecraftServer server, ServerLevel level) throws IOException {
            CompoundTag root = new CompoundTag();
            root.putString("dimension", dimension.identifier().toString());
            root.putLong("since", since);
            ListTag list = new ListTag();
            for (Held item : held) {
                CompoundTag entry = new CompoundTag();
                entry.putLong("pos", item.pos().asLong());
                BlockState.CODEC.encodeStart(NbtOps.INSTANCE, item.state()).result()
                        .ifPresent(tag -> entry.put("state", tag));
                if (item.data() != null) {
                    entry.put("data", item.data());
                }
                list.add(entry);
            }
            root.put("held", list);
            Path file = file(server, key);
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            NbtIo.writeCompressed(root, temporary);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }

        static Shelter load(String key, Path file) throws IOException {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                    Identifier.parse(root.getStringOr("dimension", "minecraft:overworld")));
            List<Held> held = new ArrayList<>();
            ListTag list = root.getListOrEmpty("held");
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompoundOrEmpty(i);
                Tag stateTag = entry.get("state");
                if (stateTag == null) {
                    continue;
                }
                BlockState state = BlockState.CODEC.parse(NbtOps.INSTANCE, stateTag).result().orElse(null);
                if (state == null) {
                    continue;
                }
                CompoundTag data = entry.getCompound("data").orElse(null);
                held.add(new Held(BlockPos.of(entry.getLongOr("pos", 0L)), state, data));
            }
            return new Shelter(key, dimension, held, root.getLongOr("since", 0L));
        }
    }

    /** Les abris laisses par un arret du serveur, a reposer : rend ceux de cette dimension. */
    static List<Shelter> orphans() {
        return List.copyOf(ORPHANS);
    }

    static void adopted(Shelter shelter) {
        ORPHANS.remove(shelter);
    }

    // -- Le cycle du serveur ----------------------------------------------------------------

    static void start(MinecraftServer server) {
        OPEN.clear();
        OWNER.clear();
        PENDING.clear();
        WRITES.clear();
        ORPHANS.clear();
        restore = null;
        Path folder = folder(server);
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".abri")) {
                    try {
                        ORPHANS.add(Shelter.load(name.substring(0, name.length() - 5), file));
                    } catch (IOException | RuntimeException error) {
                        DoHy.LOGGER.error("Guerre : abri illisible : {}", file, error);
                    }
                    continue;
                }
                if (!name.endsWith(".due")) {
                    continue;
                }
                String key = name.substring(0, name.length() - 4);
                try {
                    Pending pending = readDue(key, file);
                    if (pending.due <= 0L || !Files.exists(folder.resolve(key + ".terrain"))) {
                        // Une frappe coupee avant son impact : il n'y a rien a faire revenir.
                        Files.deleteIfExists(file);
                        Files.deleteIfExists(folder.resolve(key + ".terrain"));
                        continue;
                    }
                    PENDING.put(key, pending);
                    Long2ObjectOpenHashMap<String> owners = owners(pending.dimension);
                    for (long chunk : pending.chunks) {
                        owners.put(chunk, key);
                    }
                } catch (IOException | RuntimeException malformed) {
                    DoHy.LOGGER.warn("Guerre : echeance de frappe illisible : {}", file);
                }
            }
        } catch (IOException error) {
            DoHy.LOGGER.warn("Guerre : echeances des frappes non lues", error);
        }
        if (!PENDING.isEmpty() || !ORPHANS.isEmpty()) {
            DoHy.LOGGER.info("Guerre : {} terrains bombardes attendent leur retour, {} abris a reposer",
                    PENDING.size(), ORPHANS.size());
        }
    }

    /** Ecrit le travail des joueurs dans les zones en attente, attend les ecritures. */
    static void stop(MinecraftServer server) {
        flush(server, true);
        for (CompletableFuture<Void> write : WRITES.values()) {
            write.join();
        }
        WRITES.clear();
        OPEN.clear();
        OWNER.clear();
        PENDING.clear();
        ORPHANS.clear();
        restore = null;
    }

    private static void flush(MinecraftServer server, boolean all) {
        for (Pending pending : PENDING.values()) {
            if (pending.dirty || all) {
                pending.dirty = false;
                try {
                    writeDue(folder(server).resolve(pending.key + ".due"), pending);
                } catch (IOException error) {
                    DoHy.LOGGER.warn("Guerre : echeance de la frappe {} non ecrite", pending.label, error);
                }
            }
        }
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 1200 == 0) {
            flush(server, false);
        }
        long now = System.currentTimeMillis();
        if (!PENDING.isEmpty()) {
            Iterator<Pending> iterator = PENDING.values().iterator();
            while (iterator.hasNext()) {
                Pending pending = iterator.next();
                if (!pending.warned && pending.due - now <= 60_000L) {
                    pending.warned = true;
                    tell(server, pending, "Le terrain bombarde revient dans une minute.", ChatFormatting.YELLOW);
                }
                if (pending.due <= now && restore == null) {
                    iterator.remove();
                    restore = new Restore(pending);
                }
            }
        }
        if (restore != null && restore.advance(server)) {
            restore = null;
        }
    }

    /** Previent les joueurs qui sont dans la zone. */
    private static void tell(MinecraftServer server, Pending pending, String message, ChatFormatting color) {
        ServerLevel level = server.getLevel(pending.dimension);
        if (level == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (Math.hypot(player.getX() - pending.x, player.getZ() - pending.z) <= pending.radius + 32) {
                player.sendSystemMessage(Component.literal(message).withStyle(color));
            }
        }
    }

    private static final class Restore {
        final Pending pending;
        CompletableFuture<List<Capture>> loading;
        List<Capture> captures;
        boolean forced;
        boolean announced;
        int waited;
        int pass = 1;
        int index;
        int slab;
        int changed;
        int kept;
        long nanos;
        int ticks;

        Restore(Pending pending) {
            this.pending = pending;
        }

        boolean advance(MinecraftServer server) {
            ServerLevel level = server.getLevel(pending.dimension);
            Path folder = folder(server);
            if (level == null) {
                forget(server, folder);
                return true;
            }
            if (loading == null) {
                CompletableFuture<Void> write = WRITES.remove(pending.key);
                if (write != null) {
                    write.join();
                }
                Path file = folder.resolve(pending.key + ".terrain");
                loading = CompletableFuture.supplyAsync(() -> {
                    try {
                        return read(file);
                    } catch (IOException | RuntimeException error) {
                        DoHy.LOGGER.error("Guerre : releve du terrain {} illisible : il reste tel quel", pending.key,
                                error);
                        return null;
                    }
                });
                return false;
            }
            if (captures == null) {
                if (!loading.isDone()) {
                    return false;
                }
                captures = loading.join();
                if (captures == null) {
                    forget(server, folder);
                    return true;
                }
            }
            if (!forced) {
                for (Capture capture : captures) {
                    level.getChunkSource().addTicketWithRadius(TicketType.FORCED,
                            new ChunkPos(capture.chunkX, capture.chunkZ), 0);
                }
                forced = true;
            }
            if (!announced) {
                boolean ready = true;
                for (Capture capture : captures) {
                    ready &= level.getChunkSource().getChunkNow(capture.chunkX, capture.chunkZ) != null;
                }
                if (!ready) {
                    if (++waited > TIMEOUT_TICKS) {
                        release(level);
                        pending.due = System.currentTimeMillis() + 60_000L;
                        PENDING.put(pending.key, pending);
                        return true;
                    }
                    return false;
                }
                announced = true;
            }
            long start = System.nanoTime();
            long end = start + BUDGET;
            ticks++;
            while (index < captures.size() && System.nanoTime() < end) {
                step(level);
            }
            nanos += System.nanoTime() - start;
            if (index < captures.size()) {
                return false;
            }
            if (pass == 1) {
                pass = 2;
                index = 0;
                slab = 0;
                return false;
            }
            release(level);
            forget(server, folder);
            DoHy.LOGGER.info("Guerre : terrain de la frappe {} revenu : {} blocs reposes, {} laisses aux joueurs, "
                    + "en {} ms sur {} ticks", pending.label, changed, kept, nanos / 1_000_000L, ticks);
            tell(server, pending, "Le terrain bombarde est revenu.", ChatFormatting.GRAY);
            ServerPlayer owner = pending.owner == null ? null : server.getPlayerList().getPlayer(pending.owner);
            if (owner != null) {
                owner.sendSystemMessage(fr.atraxterra.dohy.faction.FactionApi.prefix().append(Component.literal(
                        "Le terrain de ta base est revenu : " + changed + " blocs reposes.")
                        .withStyle(ChatFormatting.GREEN)));
            }
            fr.atraxterra.dohy.faction.absence.AbsenceJournal.post(server,
                    fr.atraxterra.dohy.faction.absence.AbsenceJournal.WORLD, "Le terrain bombarde est revenu : "
                            + pending.label + " (" + changed + " blocs)", net.minecraft.world.item.Items.GRASS_BLOCK);
            return true;
        }

        private void step(ServerLevel level) {
            Capture capture = captures.get(index);
            LevelChunk chunk = level.getChunk(capture.chunkX, capture.chunkZ);
            int x0 = chunk.getPos().getMinBlockX();
            int z0 = chunk.getPos().getMinBlockZ();
            int top = Math.min(capture.height, slab + SLAB);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = slab; y < top; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState wanted = capture.palette.get(capture.cells[(y << 8) | (z << 4) | x] & 0xFFFF);
                        pos.set(x0 + x, capture.minY + y, z0 + z);
                        BlockState current = chunk.getBlockState(pos);
                        if (current == wanted) {
                            continue;
                        }
                        if (NtmArsenal.spared(wanted) || NtmArsenal.spared(current)) {
                            continue;
                        }
                        if (pending.touched.contains(pos.asLong())) {
                            if (pass == 1) {
                                kept++;
                            }
                            continue;
                        }
                        level.setBlock(pos, wanted, FLAGS);
                        changed++;
                    }
                }
            }
            slab = top;
            if (slab >= capture.height) {
                lift(level, chunk);
                index++;
                slab = 0;
            }
        }

        private void release(ServerLevel level) {
            if (forced) {
                for (Capture capture : captures) {
                    level.getChunkSource().removeTicketWithRadius(TicketType.FORCED,
                            new ChunkPos(capture.chunkX, capture.chunkZ), 0);
                }
                forced = false;
            }
        }

        private void forget(MinecraftServer server, Path folder) {
            Long2ObjectOpenHashMap<String> owners = owners(pending.dimension);
            for (long key : pending.chunks) {
                if (pending.key.equals(owners.get(key))) {
                    owners.remove(key);
                }
            }
            try {
                Files.deleteIfExists(folder.resolve(pending.key + ".due"));
                Files.deleteIfExists(folder.resolve(pending.key + ".terrain"));
            } catch (IOException error) {
                DoHy.LOGGER.warn("Guerre : releve du terrain {} non efface", pending.key, error);
            }
        }

        /** Un joueur pris dans le terrain revenu remonte a la surface. */
        private void lift(ServerLevel level, LevelChunk chunk) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || player.chunkPosition().x() != chunk.getPos().x()
                        || player.chunkPosition().z() != chunk.getPos().z()) {
                    continue;
                }
                if (!level.noCollision(player)) {
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
                    player.teleportTo(player.getX(), y, player.getZ());
                    player.resetFallDistance();
                }
            }
        }
    }
}

package fr.atraxterra.dohy.earth.disaster;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.world.PlacedBlocks;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Le terrain revient apres une catastrophe.
 *
 * <p><b>Le releve.</b> Avant qu'une catastrophe ne touche un chunk pour la
 * premiere fois, le chunk est releve : une tranche de hauteur qui va de 28
 * blocs sous son point le plus bas a 40 blocs au-dessus de son point le plus
 * haut. Un seisme et une eruption relevent d'avance toute leur zone (ce qui
 * tombe, coule ou roule y reste compris) ; un ouragan et une mousson relevent
 * chunk par chunk, juste avant leur premier degat.
 *
 * <p><b>Ce qui n'est jamais touche</b> : un chunk revendique (ta faction, une
 * base ennemie), un bloc pose par un joueur, un bloc qui porte un conteneur ou
 * une machine, et un chunk deja releve par une autre catastrophe qui n'a pas
 * fini de revenir.
 *
 * <p><b>Le retour.</b> Quand la catastrophe est finie, ses releves sont ecrits
 * dans {@code <monde>/dohy/catastrophes/} et le terrain revient a l'echeance
 * (trois heures de temps reel par defaut, serveur eteint compris) : les chunks
 * sont charges, les blocs qui ont change sont reposes tranche par tranche (10
 * ms par tick au plus), sans voisinage. Un chunk revendique entre-temps et un
 * bloc pose par un joueur entre-temps sont laisses tels quels ; un joueur pris
 * dans la roche remonte a la surface.
 */
final class DisasterTerrain {

    /** Poser sans voisinage, sans {@code onPlace} (l'eau et la lave restent ou on les met) et sans vider de conteneur. */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SKIP_ON_PLACE;

    private static final int MAGIC = 0x44435431;
    private static final int BELOW = 28;
    private static final int ABOVE = 40;
    private static final int SLAB = 16;
    private static final long BUDGET = 10_000_000L;
    private static final int TIMEOUT_TICKS = 180 * 20;

    private DisasterTerrain() {
    }

    /** Un chunk en un seul nombre : x dans les 32 bits bas, z dans les 32 bits hauts. */
    static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    static int keyX(long key) {
        return (int) key;
    }

    static int keyZ(long key) {
        return (int) (key >> 32);
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

        boolean covers(int y) {
            return y >= minY && y < minY + height;
        }

        /** L'etat releve en ce point du chunk (coordonnees locales x et z, y du monde). */
        BlockState at(int localX, int y, int localZ) {
            return palette.get(cells[((y - minY) << 8) | (localZ << 4) | localX] & 0xFFFF);
        }

        static Capture of(ServerLevel level, LevelChunk chunk) {
            int lowest = Integer.MAX_VALUE;
            int highest = Integer.MIN_VALUE;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    lowest = Math.min(lowest, chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z));
                    highest = Math.max(highest, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
                }
            }
            int minY = Math.max(level.getMinY(), lowest - BELOW);
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

    /** Les releves d'une catastrophe. */
    static final class Ledger {
        final String key;
        final String label;
        final Long2ObjectOpenHashMap<Capture> chunks = new Long2ObjectOpenHashMap<>();
        /** Les chunks qu'elle n'a pas le droit de toucher : revendiques, ou a une autre catastrophe. */
        final LongOpenHashSet refused = new LongOpenHashSet();
        int changed;

        Ledger(String key, String label) {
            this.key = key;
            this.label = label;
        }

        Capture capture(int chunkX, int chunkZ) {
            return chunks.get(key(chunkX, chunkZ));
        }
    }

    /** Les catastrophes en cours qui ont releve du terrain. */
    private static final Map<String, Ledger> OPEN = new LinkedHashMap<>();
    /** Chaque chunk releve, et la catastrophe qui le tient (en cours ou en attente de retour). */
    private static final Long2ObjectOpenHashMap<String> OWNER = new Long2ObjectOpenHashMap<>();

    static Ledger open(String key, String label) {
        Ledger ledger = new Ledger(key, label);
        OPEN.put(key, ledger);
        return ledger;
    }

    /** Releve ce chunk s'il ne l'est pas ; vrai s'il est releve pour cette catastrophe. */
    static boolean take(ServerLevel level, Ledger ledger, int chunkX, int chunkZ) {
        long key = key(chunkX, chunkZ);
        if (ledger.chunks.containsKey(key)) {
            return true;
        }
        if (ledger.refused.contains(key)) {
            return false;
        }
        String owner = OWNER.get(key);
        if (owner != null && !owner.equals(ledger.key)) {
            ledger.refused.add(key);
            return false;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return false;
        }
        ClaimOwner claim = chunk.getData(Factions.CLAIM.get());
        if (claim.claimed()) {
            ledger.refused.add(key);
            return false;
        }
        ledger.chunks.put(key, Capture.of(level, chunk));
        OWNER.put(key, ledger.key);
        return true;
    }

    /**
     * Releve d'avance les chunks d'un disque, quelques-uns par appel (10 ms) ; vrai quand c'est fait. Les
     * chunks absents de la memoire sont laisses : la catastrophe n'y touchera pas.
     */
    static boolean prepare(ServerLevel level, Ledger ledger, double centerX, double centerZ, double radius) {
        long deadline = System.nanoTime() + BUDGET;
        int fromX = (int) Math.floor(centerX - radius) >> 4;
        int toX = (int) Math.floor(centerX + radius) >> 4;
        int fromZ = (int) Math.floor(centerZ - radius) >> 4;
        int toZ = (int) Math.floor(centerZ + radius) >> 4;
        for (int cx = fromX; cx <= toX; cx++) {
            for (int cz = fromZ; cz <= toZ; cz++) {
                long key = key(cx, cz);
                if (ledger.chunks.containsKey(key) || ledger.refused.contains(key)) {
                    continue;
                }
                if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                    ledger.refused.add(key);
                    continue;
                }
                if (System.nanoTime() > deadline) {
                    return false;
                }
                take(level, ledger, cx, cz);
            }
        }
        return true;
    }

    /** Vrai si ce chunk et ses huit voisins sont releves : ce qui tombe ou coule depuis lui reste compris. */
    static boolean surrounded(Ledger ledger, int x, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!ledger.chunks.containsKey(key(cx + dx, cz + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Vrai si la catastrophe peut changer ce bloc (le chunk releve au besoin). */
    static boolean editable(ServerLevel level, Ledger ledger, BlockPos pos) {
        if (!take(level, ledger, pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }
        Capture capture = ledger.capture(pos.getX() >> 4, pos.getZ() >> 4);
        if (capture == null || !capture.covers(pos.getY())) {
            return false;
        }
        if (level.getBlockEntity(pos) != null) {
            return false;
        }
        return !PlacedBlocks.isPlayerPlaced(level, pos);
    }

    /** Change un bloc s'il le peut ; vrai si c'est fait. */
    static boolean set(ServerLevel level, Ledger ledger, BlockPos pos, BlockState state, int flags) {
        if (!editable(level, ledger, pos)) {
            return false;
        }
        if (level.getBlockState(pos) == state) {
            return true;
        }
        if (level.setBlock(pos, state, flags)) {
            ledger.changed++;
            return true;
        }
        return false;
    }

    /** L'etat releve en ce point, ou {@code null} s'il ne l'est pas. */
    static BlockState original(Ledger ledger, BlockPos pos) {
        Capture capture = ledger.capture(pos.getX() >> 4, pos.getZ() >> 4);
        if (capture == null || !capture.covers(pos.getY())) {
            return null;
        }
        return capture.at(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
    }

    // -- La fin d'une catastrophe ------------------------------------------------------------

    private static Path folder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("dohy").resolve("catastrophes");
    }

    /** Les retours en attente, par cle. */
    private static final Map<String, Pending> PENDING = new LinkedHashMap<>();
    private static final Map<String, CompletableFuture<Void>> WRITES = new LinkedHashMap<>();
    private static Restore restore;

    private static final class Pending {
        final String key;
        final String label;
        long due;
        final long[] chunks;
        /** Le centre de la zone et son rayon, pour prevenir les joueurs qui s'y trouvent. */
        final int x;
        final int z;
        final int radius;
        boolean warned;

        Pending(String key, String label, long due, long[] chunks, int x, int z, int radius) {
            this.key = key;
            this.label = label;
            this.due = due;
            this.chunks = chunks;
            this.x = x;
            this.z = z;
            this.radius = radius;
        }
    }

    /** La catastrophe est finie : son terrain reviendra a {@code due} (millisecondes du temps reel). */
    static void close(MinecraftServer server, Ledger ledger, long due) {
        OPEN.remove(ledger.key);
        if (ledger.chunks.isEmpty()) {
            return;
        }
        long[] keys = ledger.chunks.keySet().toLongArray();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long key : keys) {
            minX = Math.min(minX, keyX(key));
            maxX = Math.max(maxX, keyX(key));
            minZ = Math.min(minZ, keyZ(key));
            maxZ = Math.max(maxZ, keyZ(key));
        }
        int x = ((minX + maxX + 1) << 4) / 2;
        int z = ((minZ + maxZ + 1) << 4) / 2;
        int radius = Math.max(maxX - minX + 1, maxZ - minZ + 1) * 8 + 16;
        Pending pending = new Pending(ledger.key, ledger.label, due, keys, x, z, radius);
        PENDING.put(ledger.key, pending);
        List<Capture> captures = new ArrayList<>(ledger.chunks.values());
        Path terrain = folder(server).resolve(ledger.key + ".terrain");
        Path dueFile = folder(server).resolve(ledger.key + ".due");
        WRITES.put(ledger.key, CompletableFuture.runAsync(() -> {
            try {
                write(terrain, captures);
                writeDue(dueFile, pending);
            } catch (IOException | RuntimeException error) {
                DoHy.LOGGER.error("Releve du terrain de la catastrophe {} non ecrit : il restera tel quel",
                        ledger.label, error);
            }
        }));
    }

    /** Le nombre de terrains en attente de retour. */
    static int pending() {
        return PENDING.size() + (restore != null ? 1 : 0);
    }

    /** La prochaine echeance (millisecondes du temps reel), ou 0. */
    static long nextDue() {
        long best = 0L;
        for (Pending pending : PENDING.values()) {
            if (best == 0L || pending.due < best) {
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
        Files.writeString(file, pending.due + "\n" + pending.label + "\n" + pending.x + " " + pending.z + " "
                + pending.radius + "\n" + chunks + "\n");
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
        Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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

    // -- Le cycle du serveur ----------------------------------------------------------------

    static void start(MinecraftServer server) {
        OPEN.clear();
        OWNER.clear();
        PENDING.clear();
        WRITES.clear();
        restore = null;
        Path folder = folder(server);
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".due")).toList()) {
                String name = file.getFileName().toString();
                String key = name.substring(0, name.length() - 4);
                if (!Files.exists(folder.resolve(key + ".terrain"))) {
                    Files.deleteIfExists(file);
                    continue;
                }
                try {
                    List<String> lines = Files.readAllLines(file);
                    long due = Long.parseLong(lines.get(0).trim());
                    String label = lines.size() > 1 ? lines.get(1) : key;
                    String[] area = lines.size() > 2 ? lines.get(2).trim().split(" ") : new String[0];
                    List<Long> chunks = new ArrayList<>();
                    if (lines.size() > 3 && !lines.get(3).isBlank()) {
                        for (String pair : lines.get(3).trim().split(" ")) {
                            String[] xz = pair.split(",");
                            chunks.add(key(Integer.parseInt(xz[0]), Integer.parseInt(xz[1])));
                        }
                    }
                    long[] keys = chunks.stream().mapToLong(Long::longValue).toArray();
                    Pending pending = new Pending(key, label, due, keys,
                            area.length > 0 ? Integer.parseInt(area[0]) : 0,
                            area.length > 1 ? Integer.parseInt(area[1]) : 0,
                            area.length > 2 ? Integer.parseInt(area[2]) : 0);
                    PENDING.put(key, pending);
                    for (long chunk : keys) {
                        OWNER.put(chunk, key);
                    }
                } catch (RuntimeException malformed) {
                    DoHy.LOGGER.warn("Echeance de catastrophe illisible : {}", file);
                }
            }
        } catch (IOException error) {
            DoHy.LOGGER.warn("Echeances des catastrophes non lues", error);
        }
        if (!PENDING.isEmpty()) {
            DoHy.LOGGER.info("Catastrophes : {} terrains attendent leur retour", PENDING.size());
        }
    }

    static void stop() {
        for (CompletableFuture<Void> write : WRITES.values()) {
            write.join();
        }
        WRITES.clear();
        OPEN.clear();
        OWNER.clear();
        PENDING.clear();
        restore = null;
    }

    static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (!PENDING.isEmpty()) {
            Iterator<Pending> iterator = PENDING.values().iterator();
            while (iterator.hasNext()) {
                Pending pending = iterator.next();
                if (!pending.warned && pending.due - now <= 60_000L) {
                    pending.warned = true;
                    tell(server, pending, "Le terrain revient ici dans une minute (" + pending.label + ").",
                            ChatFormatting.YELLOW);
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

    /** Previent les joueurs de la carte qui sont dans la zone. */
    private static void tell(MinecraftServer server, Pending pending, String message, ChatFormatting color) {
        EarthData earth = EarthData.get();
        if (earth == null || pending.radius <= 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (earth.covers(player.level())
                    && Math.hypot(player.getX() - pending.x, player.getZ() - pending.z) <= pending.radius + 32) {
                player.sendSystemMessage(Disasters.prefix().append(Component.literal(message).withStyle(color)));
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
            EarthData earth = EarthData.get();
            ServerLevel level = earth == null ? null : server.getLevel(earth.dimension());
            if (level == null) {
                return false;
            }
            Path folder = folder(server);
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
                        DoHy.LOGGER.error("Releve du terrain {} illisible : il reste tel quel", pending.key, error);
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
                    forget(folder);
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
            forget(folder);
            DoHy.LOGGER.info("Terrain de la catastrophe {} revenu : {} blocs reposes, {} chunks revendiques laisses, "
                    + "en {} ms sur {} ticks", pending.label, changed, kept, nanos / 1_000_000L, ticks);
            tell(server, pending, "Le terrain est revenu (" + pending.label + ").", ChatFormatting.GRAY);
            fr.atraxterra.dohy.faction.absence.AbsenceJournal.post(server,
                    fr.atraxterra.dohy.faction.absence.AbsenceJournal.WORLD, "Le terrain est revenu : "
                            + pending.label + " (" + changed + " blocs)", net.minecraft.world.item.Items.GRASS_BLOCK);
            return true;
        }

        private void step(ServerLevel level) {
            Capture capture = captures.get(index);
            LevelChunk chunk = level.getChunk(capture.chunkX, capture.chunkZ);
            ClaimOwner claim = chunk.getData(Factions.CLAIM.get());
            if (claim.claimed()) {
                if (pass == 1) {
                    kept++;
                }
                index++;
                slab = 0;
                return;
            }
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
                        if (current != wanted && !PlacedBlocks.isPlayerPlaced(level, pos)) {
                            level.setBlock(pos, wanted, FLAGS);
                            changed++;
                        }
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

        private void forget(Path folder) {
            for (long key : pending.chunks) {
                if (pending.key.equals(OWNER.get(key))) {
                    OWNER.remove(key);
                }
            }
            try {
                Files.deleteIfExists(folder.resolve(pending.key + ".due"));
                Files.deleteIfExists(folder.resolve(pending.key + ".terrain"));
            } catch (IOException error) {
                DoHy.LOGGER.warn("Releve du terrain {} non efface", pending.key, error);
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

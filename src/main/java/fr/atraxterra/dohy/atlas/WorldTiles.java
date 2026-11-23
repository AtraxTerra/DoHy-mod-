package fr.atraxterra.dohy.atlas;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * L'atlas du serveur : le terrain du monde entier, lu dans ses fichiers de region, pour que la
 * grande carte du client soit revelee d'avance, et pas seulement la ou il est passe.
 *
 * <p>Le client demande les dalles qu'il voit (comme les siennes : 256 pixels de cote, a 1, 4 ou 16
 * blocs par pixel) ; un fil a part rend les regions qu'elles couvrent (un pixel par bloc, comme le
 * releve du client : {@link ChunkColors}), en tire les dalles moyennes et d'ensemble, qu'il garde sur
 * le disque du monde ({@code <monde>/dohy/atlas/}), et renvoie la dalle, compressee, a quelques-unes
 * par seconde. Une region dont le fichier a change est relue, pas plus d'une fois en dix minutes ; les
 * dalles fines ne se gardent qu'en memoire, le temps de servir leurs voisines.
 *
 * <p>Une dalle d'ensemble couvre 64 regions (4 096 blocs de cote) : la premiere fois, elle se remplit
 * en plusieurs envois, au fil des regions lues.
 *
 * <p>Le passage de fond : quand personne ne demande rien, le fil rend une a une toutes les regions du
 * monde (l'overworld) qui n'ont pas encore de rendu, en commencant pres du joueur connecte, puis, toutes
 * les dix minutes, celles dont le fichier a change. La carte d'ensemble est ainsi prete avant d'etre
 * regardee ; il va moitie moins vite quand un joueur est la.
 */
@EventBusSubscriber
public final class WorldTiles {

    public static final int FINE = 0;
    public static final int MIDDLE = 1;
    public static final int OVERVIEW = 2;
    /** Pixels d'une dalle, de cote. */
    public static final int SIZE = 256;
    /** Blocs par pixel de chaque niveau (les dalles du client : 1, 4, 16). */
    private static final int[] SCALE = {1, 4, 16};
    private static final int REGION = 512;
    /** Les demandes en attente d'un joueur, au plus : au-dela, il redemandera. */
    private static final int MAX_PENDING = 96;
    /** Une dalle envoyee a chaque joueur tous les cinq ticks : quatre par seconde. */
    private static final int SEND_EVERY = 5;
    /** Un fichier modifie depuis son rendu se relit, mais pas plus d'une fois en dix minutes. */
    private static final long STALE_MS = 10L * 60_000L;
    /** Les dalles modifiees se sauvent toutes les trente secondes. */
    private static final long SAVE_MS = 30_000L;
    /** Au-dela de 30 millions de blocs, rien n'existe. */
    private static final long WORLD_LIMIT = 30_000_000L;
    /** Le passage de fond reprend, pour les regions changees, au plus toutes les dix minutes. */
    private static final long SWEEP_EVERY_MS = 10L * 60_000L;
    /** Un passage de fond plus court ne se raconte pas dans la console (sauf le premier). */
    private static final int SWEEP_LOG_MIN = 64;
    /** Le passage de fond repart du joueur quand il s'est eloigne de plus de deux regions. */
    private static final int REFOCUS_BLOCKS = 1024;
    /** La position du joueur connecte (blocs x et z tasses) et sa presence, relues toutes les cinq secondes. */
    private static final int WATCH_EVERY = 100;

    /** La demande d'une dalle de la carte. */
    public record TileRequest(String dimension, int level, int tx, int tz) implements CustomPacketPayload {
        public static final Type<TileRequest> TYPE = new Type<>(id("atlas_tile_request"));
        public static final StreamCodec<FriendlyByteBuf, TileRequest> STREAM_CODEC = StreamCodec.of(
                (buffer, request) -> {
                    buffer.writeUtf(request.dimension(), 128);
                    buffer.writeVarInt(request.level());
                    buffer.writeInt(request.tx());
                    buffer.writeInt(request.tz());
                },
                buffer -> new TileRequest(buffer.readUtf(128), buffer.readVarInt(), buffer.readInt(),
                        buffer.readInt()));

        @Override
        public Type<TileRequest> type() {
            return TYPE;
        }
    }

    /** Une dalle : 256 x 256 couleurs ARGB compressees ; vide si rien n'existe la. */
    public record TileData(String dimension, int level, int tx, int tz, byte[] data) implements CustomPacketPayload {
        public static final Type<TileData> TYPE = new Type<>(id("atlas_tile"));
        public static final StreamCodec<FriendlyByteBuf, TileData> STREAM_CODEC = StreamCodec.of(
                (buffer, tile) -> {
                    buffer.writeUtf(tile.dimension());
                    buffer.writeVarInt(tile.level());
                    buffer.writeInt(tile.tx());
                    buffer.writeInt(tile.tz());
                    buffer.writeByteArray(tile.data());
                },
                buffer -> new TileData(buffer.readUtf(), buffer.readVarInt(), buffer.readInt(), buffer.readInt(),
                        buffer.readByteArray()));

        @Override
        public Type<TileData> type() {
            return TYPE;
        }

        /** Les couleurs, decompressees ; {@code null} si la dalle est vide ou illisible. */
        public int[] pixels() {
            return data.length == 0 ? null : inflate(data);
        }
    }

    private record Job(String dimension, int level, int tx, int tz) {
    }

    /** Une dalle a rendre : les fines d'abord, puis les moyennes, les d'ensemble ; la plus recente en tete. */
    private record Queued(Job job, long order) implements Comparable<Queued> {
        @Override
        public int compareTo(Queued other) {
            int byLevel = Integer.compare(job.level(), other.job.level());
            return byLevel != 0 ? byLevel : Long.compare(other.order, order);
        }
    }

    private static final Map<String, Store> STORES = new ConcurrentHashMap<>();
    private static final Map<Job, Set<UUID>> WAITING = new ConcurrentHashMap<>();
    private static final PriorityBlockingQueue<Queued> QUEUE = new PriorityBlockingQueue<>();
    private static final AtomicLong ORDER = new AtomicLong();
    /** Les dalles pretes, par joueur, a envoyer (fil du serveur). */
    private static final Map<UUID, ArrayDeque<TileData>> OUTBOX = new HashMap<>();
    private static volatile MinecraftServer server;
    private static volatile boolean running;
    private static Thread worker;
    /** D'ou part le passage de fond : un joueur de l'overworld ({@code Long.MIN_VALUE} : personne encore). */
    private static volatile long focus = Long.MIN_VALUE;
    /** Vrai quand un joueur est connecte : le passage de fond va moins vite. */
    private static volatile boolean playing;
    /** Les chunks lus depuis le demarrage (fil de l'atlas). */
    private static long chunksRead;

    private WorldTiles() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(DoHy.MOD_ID, path);
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(TileData.TYPE, TileData.STREAM_CODEC);
        registrar.playToServer(TileRequest.TYPE, TileRequest.STREAM_CODEC, (request, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                request(player, request);
            }
        });
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        server = event.getServer();
        // Le passage de fond commence tout de suite : la carte du monde sera prete quand on la regardera.
        store(event.getServer(), event.getServer().overworld());
        start();
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        running = false;
        Thread current = worker;
        worker = null;
        if (current != null) {
            current.interrupt();
            try {
                current.join(10_000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        for (Store store : STORES.values()) {
            store.save();
        }
        STORES.clear();
        WAITING.clear();
        QUEUE.clear();
        OUTBOX.clear();
        server = null;
        focus = Long.MIN_VALUE;
        playing = false;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        OUTBOX.remove(id);
        // Une dalle que plus personne n'attend ne se rend pas.
        WAITING.entrySet().removeIf(entry -> {
            entry.getValue().remove(id);
            return entry.getValue().isEmpty();
        });
    }

    /** Une dalle par joueur tous les cinq ticks ; ou est le joueur, pour le passage de fond. */
    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % WATCH_EVERY == 0) {
            List<ServerPlayer> players = event.getServer().getPlayerList().getPlayers();
            playing = !players.isEmpty();
            for (ServerPlayer player : players) {
                if (player.level().dimension() == Level.OVERWORLD) {
                    focus = key(player.getBlockX(), player.getBlockZ());
                    break;
                }
            }
        }
        if (OUTBOX.isEmpty() || event.getServer().getTickCount() % SEND_EVERY != 0) {
            return;
        }
        Iterator<Map.Entry<UUID, ArrayDeque<TileData>>> entries = OUTBOX.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, ArrayDeque<TileData>> entry = entries.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            TileData next = entry.getValue().poll();
            if (player != null && next != null) {
                PacketDistributor.sendToPlayer(player, next);
            }
            if (player == null || entry.getValue().isEmpty()) {
                entries.remove();
            }
        }
    }

    // -- Les demandes --------------------------------------------------------------------------------

    private static void request(ServerPlayer player, TileRequest request) {
        MinecraftServer current = player.level().getServer();
        if (request.level() < FINE || request.level() > OVERVIEW || current == null) {
            return;
        }
        long span = (long) SIZE * SCALE[request.level()];
        if (Math.abs(request.tx() * span) > WORLD_LIMIT || Math.abs(request.tz() * span) > WORLD_LIMIT) {
            return;
        }
        Identifier dimensionId = Identifier.tryParse(request.dimension());
        ServerLevel level = dimensionId == null ? null
                : current.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (level == null) {
            return;
        }
        int pending = 0;
        for (Set<UUID> players : WAITING.values()) {
            pending += players.contains(player.getUUID()) ? 1 : 0;
        }
        if (pending >= MAX_PENDING) {
            return;
        }
        server = current;
        Store store = store(current, level);
        Job job = new Job(store.name, request.level(), request.tx(), request.tz());
        boolean[] created = new boolean[1];
        WAITING.computeIfAbsent(job, key -> {
            created[0] = true;
            return ConcurrentHashMap.newKeySet();
        }).add(player.getUUID());
        if (created[0]) {
            QUEUE.add(new Queued(job, ORDER.incrementAndGet()));
        }
        start();
    }

    /** Les dalles d'une dimension, sous son identifiant ({@code minecraft:overworld}). */
    private static Store store(MinecraftServer current, ServerLevel level) {
        Identifier id = level.dimension().identifier();
        return STORES.computeIfAbsent(id.toString(), name -> new Store(name, regionFolder(current, level),
                current.getWorldPath(LevelResource.ROOT).resolve("dohy").resolve("atlas")
                        .resolve(id.getNamespace()).resolve(id.getPath()),
                level.dimensionType().hasCeiling()));
    }

    /** Le dossier des regions d'une dimension : celui du jeu, ou celui d'un monde range par dimension. */
    private static Path regionFolder(MinecraftServer server, ServerLevel level) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        Identifier id = level.dimension().identifier();
        List<Path> candidates = new ArrayList<>();
        candidates.add(root.resolve("dimensions").resolve(id.getNamespace()).resolve(id.getPath()).resolve("region"));
        if (level.dimension() == Level.OVERWORLD) {
            candidates.add(root.resolve("region"));
        } else if (level.dimension() == Level.NETHER) {
            candidates.add(root.resolve("DIM-1").resolve("region"));
        } else if (level.dimension() == Level.END) {
            candidates.add(root.resolve("DIM1").resolve("region"));
        }
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private static synchronized void start() {
        if (worker != null && worker.isAlive()) {
            return;
        }
        running = true;
        worker = new Thread(WorldTiles::work, "DoHy atlas");
        worker.setDaemon(true);
        worker.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2));
        worker.start();
    }

    // -- Le fil de l'atlas ---------------------------------------------------------------------------

    private static void work() {
        while (running) {
            for (Store store : STORES.values()) {
                store.saveIfDue();
            }
            Queued next = QUEUE.poll();
            if (next == null) {
                // Personne n'attend rien : une region du passage de fond, puis on revient aux demandes.
                Store world = STORES.get(Level.OVERWORLD.identifier().toString());
                try {
                    if (world != null && world.sweepStep()) {
                        continue;
                    }
                } catch (RuntimeException | LinkageError error) {
                    // Il reprendra au prochain passage, dans dix minutes.
                    world.abandon();
                    DoHy.LOGGER.warn("Atlas du serveur : passage de fond interrompu ({})", error.toString());
                }
                try {
                    next = QUEUE.poll(2, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    break;
                }
                if (next == null) {
                    continue;
                }
            }
            Job job = next.job();
            Store store = STORES.get(job.dimension());
            int[] pixels = null;
            try {
                if (store != null && WAITING.containsKey(job)) {
                    pixels = switch (job.level()) {
                        case FINE -> store.fine(job.tx(), job.tz());
                        case MIDDLE -> store.middle(job.tx(), job.tz());
                        default -> store.overview(job.tx(), job.tz(), partial -> deliver(job, partial, false));
                    };
                }
            } catch (RuntimeException | LinkageError error) {
                DoHy.LOGGER.warn("Atlas du serveur : dalle {} illisible ({})", job, error.toString());
            }
            deliver(job, pixels, true);
        }
    }

    /** Remet la dalle aux joueurs qui l'attendent (le dernier envoi les libere). */
    private static void deliver(Job job, int[] pixels, boolean last) {
        Set<UUID> players = last ? WAITING.remove(job) : WAITING.get(job);
        MinecraftServer current = server;
        if (players == null || players.isEmpty() || current == null) {
            return;
        }
        TileData tile = new TileData(job.dimension(), job.level(), job.tx(), job.tz(),
                pixels == null ? new byte[0] : deflate(pixels));
        List<UUID> targets = List.copyOf(players);
        current.execute(() -> {
            for (UUID id : targets) {
                OUTBOX.computeIfAbsent(id, key -> new ArrayDeque<>()).add(tile);
            }
        });
    }

    // -- Les dalles d'une dimension (fil de l'atlas seulement) -----------------------------------------

    private static final class Store {
        final String name;
        final Path regions;
        final Path cache;
        final boolean ceiling;
        final Map<Long, Long> stamps = new HashMap<>();
        /** Le passage de fond : les regions qui restent a voir, la plus proche du joueur en tete. */
        final ArrayDeque<Long> sweep = new ArrayDeque<>();
        long sweptAt = Long.MIN_VALUE / 2;
        long sortedFor = Long.MIN_VALUE;
        boolean firstSweep = true;
        int sweepTotal;
        int sweepDone;
        long sweepStarted;
        long sweepBusy;
        long sweepChunks;
        final LinkedHashMap<Long, int[]> rendered = new LinkedHashMap<>(16, 0.75f, true);
        final LinkedHashMap<Long, int[]> middle = new LinkedHashMap<>(32, 0.75f, true);
        final LinkedHashMap<Long, int[]> overview = new LinkedHashMap<>(32, 0.75f, true);
        final Set<Long> dirtyMiddle = new HashSet<>();
        final Set<Long> dirtyOverview = new HashSet<>();
        boolean loaded;
        boolean stampsDirty;
        long savedAt = System.currentTimeMillis();

        Store(String name, Path regions, Path cache, boolean ceiling) {
            this.name = name;
            this.regions = regions;
            this.cache = cache;
            this.ceiling = ceiling;
        }

        private void load() {
            if (loaded) {
                return;
            }
            loaded = true;
            Path file = cache.resolve("regions.bin");
            if (!Files.isRegularFile(file)) {
                return;
            }
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                int count = in.readInt();
                for (int index = 0; index < count; index++) {
                    stamps.put(in.readLong(), in.readLong());
                }
            } catch (IOException error) {
                DoHy.LOGGER.warn("Atlas du serveur : {} illisible ({})", file, error.toString());
            }
        }

        int[] fine(int tx, int tz) {
            int[] region = region(Math.floorDiv(tx, 2), Math.floorDiv(tz, 2), true, false);
            if (region == null) {
                return null;
            }
            int[] out = new int[SIZE * SIZE];
            int ox = Math.floorMod(tx, 2) * SIZE;
            int oz = Math.floorMod(tz, 2) * SIZE;
            for (int z = 0; z < SIZE; z++) {
                System.arraycopy(region, (oz + z) * REGION + ox, out, z * SIZE, SIZE);
            }
            return out;
        }

        int[] middle(int tx, int tz) {
            boolean any = false;
            for (int dz = 0; dz < 2 && running; dz++) {
                for (int dx = 0; dx < 2 && running; dx++) {
                    any |= ensure(tx * 2 + dx, tz * 2 + dz);
                }
            }
            int[] tile = any ? tile(middle, dirtyMiddle, "m", tx, tz, false) : null;
            return tile == null ? null : tile.clone();
        }

        int[] overview(int tx, int tz, Consumer<int[]> partial) {
            boolean any = false;
            int rendered = 0;
            for (int dz = 0; dz < 8 && running; dz++) {
                for (int dx = 0; dx < 8 && running; dx++) {
                    boolean exists = Files.isRegularFile(file(tx * 8 + dx, tz * 8 + dz));
                    boolean fresh = exists && fresh(tx * 8 + dx, tz * 8 + dz);
                    any |= ensure(tx * 8 + dx, tz * 8 + dz);
                    if (exists && !fresh && ++rendered % 16 == 0) {
                        int[] tile = tile(overview, dirtyOverview, "o", tx, tz, false);
                        if (tile != null) {
                            partial.accept(tile.clone());
                        }
                    }
                }
            }
            int[] tile = any ? tile(overview, dirtyOverview, "o", tx, tz, false) : null;
            return tile == null ? null : tile.clone();
        }

        private Path file(int rx, int rz) {
            return regions.resolve("r." + rx + "." + rz + ".mca");
        }

        /** Le rendu de la region est-il a jour (ou trop recent pour etre refait) ? */
        private boolean fresh(int rx, int rz) {
            load();
            Long stamp = stamps.get(key(rx, rz));
            if (stamp == null) {
                return false;
            }
            try {
                long modified = Files.getLastModifiedTime(file(rx, rz)).toMillis();
                return modified <= stamp || System.currentTimeMillis() - stamp < STALE_MS;
            } catch (IOException error) {
                return true;
            }
        }

        /** Rend la region si son rendu manque ou a vieilli ; vrai si elle existe. */
        private boolean ensure(int rx, int rz) {
            if (!Files.isRegularFile(file(rx, rz))) {
                return false;
            }
            if (!fresh(rx, rz)) {
                region(rx, rz, false, false);
            }
            return true;
        }

        /**
         * Les couleurs de la region, un pixel par bloc ; {@code null} si elle n'existe pas. Celles du
         * passage de fond ({@code background}) ne se gardent pas en memoire et laissent plus de repos.
         */
        private int[] region(int rx, int rz, boolean pixels, boolean background) {
            Path path = file(rx, rz);
            if (!Files.isRegularFile(path)) {
                return null;
            }
            long key = key(rx, rz);
            int[] known = rendered.get(key);
            if (known != null && fresh(rx, rz)) {
                return known;
            }
            if (pixels || !fresh(rx, rz)) {
                long started = System.nanoTime();
                long chunksBefore = chunksRead;
                int[] colors = render(path, rx, rz, ceiling);
                if (!background) {
                    rendered.put(key, colors);
                    while (rendered.size() > 6) {
                        Iterator<Long> eldest = rendered.keySet().iterator();
                        eldest.next();
                        eldest.remove();
                    }
                }
                apply(rx, rz, colors);
                stamps.put(key, System.currentTimeMillis());
                stampsDirty = true;
                long elapsed = System.nanoTime() - started;
                if (background) {
                    sweepBusy += elapsed;
                    sweepChunks += chunksRead - chunksBefore;
                }
                rest(elapsed, background);
                return colors;
            }
            return null;
        }

        // -- Le passage de fond (fil de l'atlas) ---------------------------------------------------------

        /** Une region du passage de fond ; faux s'il n'y a rien a rendre pour l'instant. */
        boolean sweepStep() {
            if (sweep.isEmpty()) {
                long now = System.currentTimeMillis();
                if (now - sweptAt < SWEEP_EVERY_MS) {
                    return false;
                }
                sweptAt = now;
                plan();
                if (sweep.isEmpty()) {
                    firstSweep = false;
                    return false;
                }
            }
            long wanted = focus;
            if (wanted != Long.MIN_VALUE && (sortedFor == Long.MIN_VALUE || distance(wanted, sortedFor) > REFOCUS_BLOCKS)) {
                List<Long> left = new ArrayList<>(sweep);
                sweep.clear();
                sweep.addAll(sorted(left, wanted));
                sortedFor = wanted;
            }
            while (!sweep.isEmpty() && running) {
                long key = sweep.poll();
                int rx = (int) (key >> 32);
                int rz = (int) key;
                if (Files.isRegularFile(file(rx, rz)) && !fresh(rx, rz)) {
                    region(rx, rz, false, true);
                    sweepDone++;
                    if (sweepTotal >= SWEEP_LOG_MIN && sweepDone % 500 == 0 && !sweep.isEmpty()) {
                        DoHy.LOGGER.info("Atlas du serveur : carte de {}, {} / {} regions", name, sweepDone, sweepTotal);
                    }
                    if (sweep.isEmpty()) {
                        finish();
                    }
                    return true;
                }
            }
            finish();
            return false;
        }

        /** Les regions du monde qui n'ont pas de rendu a jour, de la plus proche du joueur a la plus loin. */
        private void plan() {
            List<Long> todo = new ArrayList<>();
            try (DirectoryStream<Path> files = Files.newDirectoryStream(regions, "r.*.mca")) {
                for (Path path : files) {
                    String[] parts = path.getFileName().toString().split("\\.");
                    if (parts.length != 4) {
                        continue;
                    }
                    int rx;
                    int rz;
                    try {
                        rx = Integer.parseInt(parts[1]);
                        rz = Integer.parseInt(parts[2]);
                    } catch (NumberFormatException wrong) {
                        continue;
                    }
                    if (!fresh(rx, rz)) {
                        todo.add(key(rx, rz));
                    }
                }
            } catch (IOException | DirectoryIteratorException error) {
                return;
            }
            if (todo.isEmpty()) {
                return;
            }
            long from = focus;
            sortedFor = from;
            sweep.addAll(sorted(todo, from == Long.MIN_VALUE ? 0L : from));
            sweepTotal = todo.size();
            sweepDone = 0;
            sweepBusy = 0L;
            sweepChunks = 0L;
            sweepStarted = System.currentTimeMillis();
            if (firstSweep || sweepTotal >= SWEEP_LOG_MIN) {
                DoHy.LOGGER.info("Atlas du serveur : {} regions a rendre pour la carte de {}, en tache de fond",
                        sweepTotal, name);
            }
        }

        /** Le passage s'arrete sur une erreur : le suivant repartira dans dix minutes. */
        void abandon() {
            sweep.clear();
            sweptAt = System.currentTimeMillis();
            firstSweep = false;
            sweepTotal = 0;
            sweepDone = 0;
        }

        /** Le passage est fini : il se raconte s'il etait long (ou le premier). */
        private void finish() {
            if (sweepTotal > 0 && (firstSweep || sweepTotal >= SWEEP_LOG_MIN)) {
                long minutes = Math.round((System.currentTimeMillis() - sweepStarted) / 60_000.0d);
                long busy = Math.round(sweepBusy / 60_000_000_000.0d);
                DoHy.LOGGER.info("Atlas du serveur : carte de {} a jour, {} regions ({} chunks) en {} min, dont {} de"
                                + " rendu ({} ms par region)", name, sweepDone, sweepChunks, minutes, busy,
                        sweepDone == 0 ? 0 : sweepBusy / 1_000_000L / sweepDone);
            }
            firstSweep = false;
            sweepTotal = 0;
            sweepDone = 0;
        }

        private static List<Long> sorted(List<Long> keys, long from) {
            List<Long> copy = new ArrayList<>(keys);
            copy.sort(Comparator.comparingLong(key -> {
                long dx = (long) (int) (key >> 32) * REGION + REGION / 2 - (int) (from >> 32);
                long dz = (long) (int) (long) key * REGION + REGION / 2 - (int) (long) from;
                return dx * dx + dz * dz;
            }));
            return copy;
        }

        private static long distance(long a, long b) {
            return Math.max(Math.abs((long) (int) (a >> 32) - (int) (b >> 32)), Math.abs((long) (int) a - (int) b));
        }

        /** Le rendu d'une region passe dans ses dalles moyenne et d'ensemble. */
        private void apply(int rx, int rz, int[] colors) {
            int[] mid = tile(middle, dirtyMiddle, "m", Math.floorDiv(rx, 2), Math.floorDiv(rz, 2), true);
            shrink(colors, 4, mid, Math.floorMod(rx, 2) * (REGION / 4), Math.floorMod(rz, 2) * (REGION / 4));
            dirtyMiddle.add(key(Math.floorDiv(rx, 2), Math.floorDiv(rz, 2)));
            int[] all = tile(overview, dirtyOverview, "o", Math.floorDiv(rx, 8), Math.floorDiv(rz, 8), true);
            shrink(colors, 16, all, Math.floorMod(rx, 8) * (REGION / 16), Math.floorMod(rz, 8) * (REGION / 16));
            dirtyOverview.add(key(Math.floorDiv(rx, 8), Math.floorDiv(rz, 8)));
        }

        /** Une dalle moyenne ou d'ensemble : en memoire, sur le disque, ou neuve si {@code create}. */
        private int[] tile(LinkedHashMap<Long, int[]> tiles, Set<Long> dirty, String prefix, int tx, int tz,
                           boolean create) {
            long key = key(tx, tz);
            int[] tile = tiles.get(key);
            if (tile == null) {
                Path path = cache.resolve(prefix + "." + tx + "." + tz + ".bin");
                if (Files.isRegularFile(path)) {
                    try {
                        tile = inflate(Files.readAllBytes(path));
                    } catch (IOException error) {
                        tile = null;
                    }
                }
                if (tile == null && create) {
                    tile = new int[SIZE * SIZE];
                }
                if (tile == null) {
                    return null;
                }
                tiles.put(key, tile);
                while (tiles.size() > 24) {
                    Iterator<Map.Entry<Long, int[]>> eldest = tiles.entrySet().iterator();
                    Map.Entry<Long, int[]> entry = eldest.next();
                    if (dirty.remove(entry.getKey())) {
                        write(prefix, entry.getKey(), entry.getValue());
                    }
                    eldest.remove();
                }
            }
            return tile;
        }

        void saveIfDue() {
            if (System.currentTimeMillis() - savedAt >= SAVE_MS) {
                save();
            }
        }

        void save() {
            savedAt = System.currentTimeMillis();
            for (Long key : List.copyOf(dirtyMiddle)) {
                int[] tile = middle.get(key);
                if (tile != null) {
                    write("m", key, tile);
                }
            }
            dirtyMiddle.clear();
            for (Long key : List.copyOf(dirtyOverview)) {
                int[] tile = overview.get(key);
                if (tile != null) {
                    write("o", key, tile);
                }
            }
            dirtyOverview.clear();
            if (!stampsDirty) {
                return;
            }
            stampsDirty = false;
            try {
                Files.createDirectories(cache);
                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                        Files.newOutputStream(cache.resolve("regions.bin"))))) {
                    out.writeInt(stamps.size());
                    for (Map.Entry<Long, Long> entry : stamps.entrySet()) {
                        out.writeLong(entry.getKey());
                        out.writeLong(entry.getValue());
                    }
                }
            } catch (IOException error) {
                DoHy.LOGGER.warn("Atlas du serveur : rendus non sauves ({})", error.toString());
            }
        }

        private void write(String prefix, long key, int[] tile) {
            int tx = (int) (key >> 32);
            int tz = (int) key;
            try {
                Files.createDirectories(cache);
                Files.write(cache.resolve(prefix + "." + tx + "." + tz + ".bin"), deflate(tile));
            } catch (IOException error) {
                DoHy.LOGGER.warn("Atlas du serveur : dalle {}.{}.{} non sauvee ({})", prefix, tx, tz,
                        error.toString());
            }
        }
    }

    /**
     * Laisse respirer le serveur : une pause d'un tiers du temps de rendu ; pour le passage de fond, quand
     * un joueur est la, une pause aussi longue que le rendu.
     */
    private static void rest(long nanos, boolean background) {
        long millis = background && playing ? Math.min(1_000L, nanos / 1_000_000L) : Math.min(400L, nanos / 3_000_000L);
        if (millis <= 0L || !running) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    // -- Le rendu d'une region -------------------------------------------------------------------------

    /** Le terrain d'une region, un pixel par bloc (512 x 512), transparent la ou il n'y a rien. */
    static int[] render(Path path, int rx, int rz, boolean ceiling) {
        int[] colors = new int[REGION * REGION];
        int[] heights = new int[REGION * REGION];
        Arrays.fill(heights, Integer.MIN_VALUE);
        int[] chunkColors = new int[256];
        int[] chunkHeights = new int[256];
        RegionReader reader;
        try {
            reader = new RegionReader(path, rx, rz);
        } catch (IOException error) {
            return colors;
        }
        for (int lz = 0; lz < 32; lz++) {
            for (int lx = 0; lx < 32; lx++) {
                if (!reader.has(lx, lz)) {
                    continue;
                }
                List<ChunkNbt.Section> sections = reader.sections(lx, lz);
                if (sections == null || !ChunkColors.decode(sections, ceiling, chunkColors, chunkHeights)) {
                    continue;
                }
                chunksRead++;
                for (int z = 0; z < 16; z++) {
                    int row = (lz * 16 + z) * REGION + lx * 16;
                    System.arraycopy(chunkColors, z * 16, colors, row, 16);
                    System.arraycopy(chunkHeights, z * 16, heights, row, 16);
                }
            }
        }
        // La pente, avec le voisin du nord, comme le releve du client.
        for (int z = REGION - 1; z >= 0; z--) {
            for (int x = 0; x < REGION; x++) {
                int index = z * REGION + x;
                if (colors[index] != 0) {
                    int north = z == 0 ? Integer.MIN_VALUE : heights[index - REGION];
                    colors[index] = MapShade.shaded(colors[index], MapShade.slope(heights[index], north));
                }
            }
        }
        return colors;
    }

    /** Reduit un rendu de region : chaque pixel est la moyenne des blocs connus de sa case. */
    private static void shrink(int[] colors, int factor, int[] tile, int ox, int oz) {
        int size = REGION / factor;
        for (int pz = 0; pz < size; pz++) {
            for (int px = 0; px < size; px++) {
                long r = 0;
                long g = 0;
                long b = 0;
                int count = 0;
                for (int dz = 0; dz < factor; dz++) {
                    int row = (pz * factor + dz) * REGION + px * factor;
                    for (int dx = 0; dx < factor; dx++) {
                        int color = colors[row + dx];
                        if ((color >>> 24) != 0) {
                            r += (color >> 16) & 0xFF;
                            g += (color >> 8) & 0xFF;
                            b += color & 0xFF;
                            count++;
                        }
                    }
                }
                tile[(oz + pz) * SIZE + ox + px] = count == 0 ? 0
                        : 0xFF000000 | (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
            }
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** Les couleurs d'une dalle, compressees d'un coup (zlib, entiers gros-boutistes). */
    static byte[] deflate(int[] pixels) {
        ByteBuffer raw = ByteBuffer.allocate(pixels.length * 4);
        raw.asIntBuffer().put(pixels);
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(raw.array());
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            byte[] buffer = new byte[32 * 1024];
            while (!deflater.finished()) {
                int written = deflater.deflate(buffer);
                out.write(buffer, 0, written);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /** Les couleurs d'une dalle ; {@code null} si les donnees sont illisibles. */
    static int[] inflate(byte[] data) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            byte[] raw = new byte[SIZE * SIZE * 4];
            int done = 0;
            while (done < raw.length && !inflater.finished()) {
                int read = inflater.inflate(raw, done, raw.length - done);
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    return null;
                }
                done += read;
            }
            if (done < raw.length) {
                return null;
            }
            int[] pixels = new int[SIZE * SIZE];
            ByteBuffer.wrap(raw).asIntBuffer().get(pixels);
            return pixels;
        } catch (DataFormatException error) {
            return null;
        } finally {
            inflater.end();
        }
    }
}

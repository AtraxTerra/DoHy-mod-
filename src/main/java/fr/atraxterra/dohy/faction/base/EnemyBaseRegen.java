package fr.atraxterra.dohy.faction.base;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.Factions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Le terrain reprend ses droits : cinq minutes apres la fin d'un raid (la base
 * est pillee, ou abandonnee), la base s'efface et le terrain revient tel qu'il
 * etait avant elle — collines, arbres, rivieres, crateres rebouches.
 *
 * <p><b>Le releve.</b> Avant de poser le premier bloc, le chantier releve tous
 * les blocs du carre de claims (l'emprise et son glacis), de 48 blocs sous le
 * sol a 24 blocs au-dessus du plus haut point. Il est ecrit dans
 * {@code <monde>/dohy/bases/<x>_<z>.terrain}, compresse : il survit a un
 * redemarrage, et la base peut attendre des jours.
 *
 * <p><b>Le retour.</b> A l'echeance, les chunks sont charges en tache de fond et
 * relus tranche par tranche, 15 ms par tick au plus : seuls les blocs qui ont
 * change sont reposes, de bas en haut, sans mise a jour de voisinage (rien ne
 * tombe, rien ne coule) et sans vider les coffres au sol. Une seconde passe
 * rattrape ce qui a bouge pendant la premiere. Les gardes et les
 * betes de la base disparaissent, ses claims tombent. Un chunk que le joueur a
 * revendique entre-temps est laisse tel quel. Un joueur pris dans la roche est
 * remonte a la surface ; la chute lente couvre celui qui etait sur un mur.
 *
 * <p><b>Limites.</b> Le contenu d'un coffre naturel detruit par la base ou par
 * le raid ne revient pas (le bloc, si), et une base batie avant ce systeme n'a
 * pas de releve : elle reste en place.
 */
@EventBusSubscriber
public final class EnemyBaseRegen {

    /** Le delai entre la fin du raid et le retour du terrain : cinq minutes de jeu. */
    public static final int DELAY_TICKS = 5 * 60 * 20;

    static final String BEAST_TAG = "dohy_enemy_beast";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAGIC = 0x44425431;
    private static final int BELOW = 48;
    private static final int ABOVE = 24;
    private static final int SLAB = 16;
    private static final long BUDGET = 15_000_000L;
    private static final int TIMEOUT_TICKS = 180 * 20;
    /** Poser sans voisinage, sans {@code onPlace} et sans vider les conteneurs. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SKIP_ON_PLACE;

    private EnemyBaseRegen() {
    }

    // -- Le releve --------------------------------------------------------------------

    /** Les blocs du carre de claims, chunk par chunk : une palette et un indice par bloc. */
    static final class Snapshot {
        final String name;
        final int cx;
        final int cz;
        final int radius;
        final int minY;
        final int height;
        final List<List<BlockState>> palettes = new ArrayList<>();
        final short[][] cells;

        Snapshot(String name, int cx, int cz, int radius, int minY, int height) {
            this.name = name;
            this.cx = cx;
            this.cz = cz;
            this.radius = radius;
            this.minY = minY;
            this.height = height;
            int count = chunks();
            this.cells = new short[count][];
            for (int i = 0; i < count; i++) {
                palettes.add(null);
            }
        }

        /** Le releve a faire autour d'une base dont le sol est en {@code y0}. */
        static Snapshot begin(ServerLevel level, String name, int cx, int cz, int radius, int y0, int clear) {
            int top = y0 + clear;
            int x0 = ((cx >> 4) - radius) << 4;
            int z0 = ((cz >> 4) - radius) << 4;
            int side = (2 * radius + 1) << 4;
            for (int x = x0; x < x0 + side; x++) {
                for (int z = z0; z < z0 + side; z++) {
                    top = Math.max(top, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
                }
            }
            int minY = Math.max(level.getMinY(), y0 - BELOW);
            int maxY = Math.min(level.getMaxY(), top + ABOVE);
            return new Snapshot(name, cx, cz, radius, minY, maxY - minY + 1);
        }

        int chunks() {
            return (2 * radius + 1) * (2 * radius + 1);
        }

        int chunkX(int index) {
            return (cx >> 4) - radius + index / (2 * radius + 1);
        }

        int chunkZ(int index) {
            return (cz >> 4) - radius + index % (2 * radius + 1);
        }

        String key() {
            return EnemyBaseRegen.key(cx, cz);
        }

        /** Releve un chunk (deja charge). */
        void capture(ServerLevel level, int index) {
            LevelChunk chunk = level.getChunk(chunkX(index), chunkZ(index));
            int x0 = chunk.getPos().getMinBlockX();
            int z0 = chunk.getPos().getMinBlockZ();
            List<BlockState> palette = new ArrayList<>();
            Map<BlockState, Short> indices = new IdentityHashMap<>();
            short[] out = new short[height << 8];
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
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
                        out[at++] = lastIndex;
                    }
                }
            }
            palettes.set(index, palette);
            cells[index] = out;
        }

        BlockState at(int index, int x, int y, int z) {
            return palettes.get(index).get(cells[index][(y << 8) | (z << 4) | x] & 0xFFFF);
        }

        void write(Path file) throws IOException {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(temporary)), 1 << 16))) {
                out.writeInt(MAGIC);
                out.writeUTF(name);
                out.writeInt(cx);
                out.writeInt(cz);
                out.writeInt(radius);
                out.writeInt(minY);
                out.writeInt(height);
                for (int i = 0; i < chunks(); i++) {
                    List<BlockState> palette = palettes.get(i);
                    out.writeInt(palette.size());
                    for (BlockState state : palette) {
                        out.writeUTF(BlockState.CODEC.encodeStart(JsonOps.INSTANCE, state).result()
                                .map(JsonElement::toString).orElse(""));
                    }
                    for (short cell : cells[i]) {
                        out.writeShort(cell);
                    }
                }
            }
            Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        static Snapshot read(Path file) throws IOException {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                    new GZIPInputStream(Files.newInputStream(file)), 1 << 16))) {
                if (in.readInt() != MAGIC) {
                    throw new IOException("releve illisible : " + file);
                }
                Snapshot snapshot = new Snapshot(in.readUTF(), in.readInt(), in.readInt(), in.readInt(),
                        in.readInt(), in.readInt());
                for (int i = 0; i < snapshot.chunks(); i++) {
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
                    short[] cells = new short[snapshot.height << 8];
                    for (int c = 0; c < cells.length; c++) {
                        cells[c] = in.readShort();
                    }
                    snapshot.palettes.set(i, palette);
                    snapshot.cells[i] = cells;
                }
                return snapshot;
            }
        }
    }

    // -- Les fichiers -----------------------------------------------------------------

    private static String key(int cx, int cz) {
        return cx + "_" + cz;
    }

    private static Path folder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("dohy").resolve("bases");
    }

    private static Path terrain(MinecraftServer server, String key) {
        return folder(server).resolve(key + ".terrain");
    }

    private static Path due(MinecraftServer server, String key) {
        return folder(server).resolve(key + ".due");
    }

    /** Les ecritures en cours : une lecture attend la sienne. */
    private static final Map<String, CompletableFuture<Void>> WRITES = new LinkedHashMap<>();

    /** Garde le releve de la base : appele par le chantier, une fois la base posee. */
    static void store(ServerLevel level, Snapshot snapshot) {
        Path file = terrain(level.getServer(), snapshot.key());
        WRITES.put(snapshot.key(), CompletableFuture.runAsync(() -> {
            try {
                snapshot.write(file);
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Releve du terrain de la base des {} non ecrit : elle restera en place", snapshot.name,
                        e);
            }
        }));
    }

    private static void settle(String key) {
        CompletableFuture<Void> write = WRITES.remove(key);
        if (write != null) {
            write.join();
        }
    }

    private static void writeDue(MinecraftServer server, Pending pending) {
        try {
            Files.createDirectories(folder(server));
            Files.writeString(due(server, pending.key), Math.max(0, pending.ticks) + "\n" + pending.name + "\n"
                    + (pending.player == null ? "" : pending.player.toString()) + "\n");
        } catch (IOException e) {
            LOGGER.warn("Echeance du terrain {} non ecrite", pending.key, e);
        }
    }

    // -- L'echeance -------------------------------------------------------------------

    private static final class Pending {
        final String key;
        final String name;
        final UUID player;
        int ticks;

        Pending(String key, String name, UUID player, int ticks) {
            this.key = key;
            this.name = name;
            this.player = player;
            this.ticks = ticks;
        }
    }

    private static final Map<String, Pending> PENDING = new LinkedHashMap<>();
    private static Restore restore;

    /**
     * Le raid est fini : le terrain reviendra dans {@link #DELAY_TICKS}. Faux si
     * la base n'a pas de releve (batie avant ce systeme) : elle reste en place.
     */
    static boolean schedule(MinecraftServer server, ServerPlayer player, EnemyBase base) {
        return schedule(server, player, base, DELAY_TICKS);
    }

    static boolean schedule(MinecraftServer server, ServerPlayer player, EnemyBase base, int ticks) {
        String key = key(base.center().getX(), base.center().getZ());
        if (restore != null && restore.pending.key.equals(key)) {
            return true;
        }
        if (!WRITES.containsKey(key) && !Files.exists(terrain(server, key))) {
            return false;
        }
        Pending pending = new Pending(key, base.name(), player.getUUID(), ticks);
        PENDING.put(key, pending);
        writeDue(server, pending);
        return true;
    }

    /** Le terrain de cette base attend-il de revenir, ou revient-il ? */
    public static boolean regenerating(EnemyBase base) {
        String key = key(base.center().getX(), base.center().getZ());
        return PENDING.containsKey(key) || restore != null && restore.pending.key.equals(key);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        PENDING.clear();
        WRITES.clear();
        restore = null;
        MinecraftServer server = event.getServer();
        Path folder = folder(server);
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".due")).toList()) {
                String name = file.getFileName().toString();
                String key = name.substring(0, name.length() - 4);
                if (!Files.exists(terrain(server, key))) {
                    Files.deleteIfExists(file);
                    continue;
                }
                List<String> lines = Files.readAllLines(file);
                int ticks = DELAY_TICKS;
                UUID player = null;
                try {
                    ticks = Integer.parseInt(lines.get(0).trim());
                    player = lines.size() > 2 && !lines.get(2).isBlank() ? UUID.fromString(lines.get(2).trim()) : null;
                } catch (RuntimeException malformed) {
                    LOGGER.warn("Echeance illisible : {}", file);
                }
                // Un retour interrompu par l'arret reprend au debut, apres un court repit.
                PENDING.put(key, new Pending(key, lines.size() > 1 ? lines.get(1) : "", player, Math.max(ticks, 200)));
            }
        } catch (IOException e) {
            LOGGER.warn("Echeances des terrains non lues", e);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        for (Pending pending : PENDING.values()) {
            writeDue(server, pending);
        }
        for (CompletableFuture<Void> write : WRITES.values()) {
            write.join();
        }
        WRITES.clear();
        PENDING.clear();
        restore = null;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!PENDING.isEmpty()) {
            Iterator<Pending> iterator = PENDING.values().iterator();
            while (iterator.hasNext()) {
                Pending pending = iterator.next();
                pending.ticks--;
                if (pending.ticks == 60 * 20) {
                    tell(server, pending, "La base des " + pending.name + " s'efface dans 1 minute.",
                            ChatFormatting.YELLOW);
                } else if (pending.ticks == 10 * 20) {
                    tell(server, pending, "La base des " + pending.name + " s'efface dans 10 secondes !",
                            ChatFormatting.RED);
                }
                if (pending.ticks <= 0 && restore == null) {
                    iterator.remove();
                    restore = new Restore(pending);
                }
            }
        }
        if (restore != null && restore.advance(server)) {
            restore = null;
        }
    }

    private static void tell(MinecraftServer server, Pending pending, String message, ChatFormatting color) {
        ServerPlayer player = pending.player == null ? null : server.getPlayerList().getPlayer(pending.player);
        if (player != null) {
            FactionApi.tell(player, message, color);
        }
    }

    // -- Le retour --------------------------------------------------------------------

    private static final class Restore {
        final Pending pending;
        CompletableFuture<Snapshot> loading;
        Snapshot snapshot;
        boolean forced;
        boolean cleared;
        int waited;
        int pass = 1;
        int chunk;
        int slab;
        int changed;
        int again;
        int kept;
        long nanos;
        int ticks;

        Restore(Pending pending) {
            this.pending = pending;
        }

        /** Vrai quand tout est fini (ou abandonne). */
        boolean advance(MinecraftServer server) {
            ServerLevel level = server.overworld();
            if (loading == null) {
                settle(pending.key);
                Path file = terrain(server, pending.key);
                loading = CompletableFuture.supplyAsync(() -> {
                    try {
                        return Snapshot.read(file);
                    } catch (IOException | RuntimeException e) {
                        LOGGER.error("Releve du terrain {} illisible : la base reste en place", pending.key, e);
                        return null;
                    }
                });
                return false;
            }
            if (snapshot == null) {
                if (!loading.isDone()) {
                    return false;
                }
                snapshot = loading.join();
                if (snapshot == null) {
                    forget(server);
                    return true;
                }
            }
            if (!forced) {
                forEachChunk((x, z) -> level.getChunkSource().addTicketWithRadius(TicketType.FORCED,
                        new ChunkPos(x, z), 0));
                forced = true;
            }
            if (!cleared) {
                boolean[] ready = {true};
                forEachChunk((x, z) -> ready[0] &= level.getChunkSource().getChunkNow(x, z) != null);
                if (!ready[0]) {
                    if (++waited > TIMEOUT_TICKS) {
                        // Le monde ne suit pas : on reessaiera dans une minute.
                        release(level);
                        pending.ticks = 60 * 20;
                        PENDING.put(pending.key, pending);
                        return true;
                    }
                    return false;
                }
                clear(level);
                cleared = true;
                tell(server, pending, "La base des " + pending.name + " s'efface : le terrain reprend ses droits.",
                        ChatFormatting.GOLD);
            }
            long start = System.nanoTime();
            long end = start + BUDGET;
            ticks++;
            while (chunk < snapshot.chunks() && System.nanoTime() < end) {
                step(level);
            }
            nanos += System.nanoTime() - start;
            if (chunk < snapshot.chunks()) {
                return false;
            }
            if (pass == 1) {
                // Une seconde passe : l'eau d'une douve pas encore effacee a pu couler dans un chunk deja revenu.
                pass = 2;
                chunk = 0;
                slab = 0;
                return false;
            }
            release(level);
            forget(server);
            LOGGER.info("Terrain de la base des {} ({}) revenu : {} blocs reposes, {} a la seconde passe, {} chunks "
                            + "revendiques laisses, en {} ms sur {} ticks", pending.name, pending.key, changed, again,
                    kept, nanos / 1_000_000L, ticks);
            fr.atraxterra.dohy.faction.absence.AbsenceJournal.post(server,
                    fr.atraxterra.dohy.faction.absence.AbsenceJournal.WORLD, "La base des " + pending.name
                            + " s'est effacee : le terrain a repris ses droits",
                    net.minecraft.world.item.Items.MOSSY_COBBLESTONE);
            ServerPlayer player = pending.player == null ? null : server.getPlayerList().getPlayer(pending.player);
            if (player != null) {
                EnemyBase base = EnemyBases.of(player);
                if (base.exists() && !base.active() && key(base.center().getX(), base.center().getZ())
                        .equals(pending.key)) {
                    player.setData(Factions.ENEMY_BASE.get(), EnemyBase.EMPTY);
                }
                FactionApi.tell(player, "Il ne reste rien de la base des " + pending.name + ".",
                        ChatFormatting.GRAY);
            }
            return true;
        }

        private void forEachChunk(ChunkAction action) {
            for (int i = 0; i < snapshot.chunks(); i++) {
                action.apply(snapshot.chunkX(i), snapshot.chunkZ(i));
            }
        }

        private void release(ServerLevel level) {
            if (forced) {
                forEachChunk((x, z) -> level.getChunkSource().removeTicketWithRadius(TicketType.FORCED,
                        new ChunkPos(x, z), 0));
                forced = false;
            }
        }

        private void forget(MinecraftServer server) {
            try {
                Files.deleteIfExists(due(server, pending.key));
                Files.deleteIfExists(terrain(server, pending.key));
            } catch (IOException e) {
                LOGGER.warn("Releve du terrain {} non efface", pending.key, e);
            }
        }

        private AABB area() {
            int x0 = snapshot.chunkX(0) << 4;
            int z0 = snapshot.chunkZ(0) << 4;
            int side = (2 * snapshot.radius + 1) << 4;
            return new AABB(x0, snapshot.minY, z0, x0 + side, snapshot.minY + snapshot.height, z0 + side);
        }

        /** Avant le premier bloc : plus de gardes ni de betes, et personne ne se tue en tombant d'un mur. */
        private void clear(ServerLevel level) {
            AABB area = area();
            for (Entity entity : level.getEntities((Entity) null, area, found -> found.entityTags()
                    .contains(EnemyBases.GUARD_TAG) || found.entityTags().contains(BEAST_TAG))) {
                entity.discard();
            }
            for (ServerPlayer player : level.players()) {
                if (area.contains(player.position())) {
                    player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 45 * 20, 0));
                }
            }
        }

        /** Une tranche de seize couches d'un chunk, de bas en haut. */
        private void step(ServerLevel level) {
            LevelChunk target = level.getChunk(snapshot.chunkX(chunk), snapshot.chunkZ(chunk));
            ClaimOwner owner = target.getData(Factions.CLAIM.get());
            boolean enemy = owner.raidable() && owner.faction().equals(snapshot.name);
            if (owner.claimed() && !enemy) {
                // Revendique par le joueur depuis : c'est chez lui, on n'y touche pas.
                if (pass == 1) {
                    kept++;
                }
                chunk++;
                slab = 0;
                return;
            }
            if (slab == 0 && enemy) {
                target.setData(Factions.CLAIM.get(), ClaimOwner.NONE);
                target.markUnsaved();
            }
            int x0 = target.getPos().getMinBlockX();
            int z0 = target.getPos().getMinBlockZ();
            int top = Math.min(snapshot.height, slab + SLAB);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = slab; y < top; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState wanted = snapshot.at(chunk, x, y, z);
                        pos.set(x0 + x, snapshot.minY + y, z0 + z);
                        if (target.getBlockState(pos) != wanted) {
                            level.setBlock(pos, wanted, FLAGS);
                            if (pass == 1) {
                                changed++;
                            } else {
                                again++;
                            }
                        }
                    }
                }
            }
            slab = top;
            if (slab >= snapshot.height) {
                lift(level, target);
                chunk++;
                slab = 0;
            }
        }

        /** Un joueur pris dans le terrain revenu remonte a la surface. */
        private void lift(ServerLevel level, LevelChunk target) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || player.chunkPosition().x() != target.getPos().x()
                        || player.chunkPosition().z() != target.getPos().z()) {
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

    private interface ChunkAction {
        void apply(int x, int z);
    }
}

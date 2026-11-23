package fr.atraxterra.dohy.faction;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.faction.base.EnemyBase;
import fr.atraxterra.dohy.faction.war.Strongholds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les claims sur la grande carte de l'atlas : le client y voit les chunks de sa nation et ceux des
 * autres qu'il connait, et en revendique ou en rend d'un clic, sans s'y tenir.
 *
 * <p>Le serveur envoie l'etat a la connexion puis chaque fois qu'il change (releve chaque
 * seconde : un claim pris a la commande, une technologie qui en ajoute, un pays conquis). Une
 * demande de la carte suit les regles de {@code /f claim} ({@link FactionApi#claimChunk}), avec
 * deux bornes : la dimension ou se tient le joueur, a {@value #RADIUS} chunks de lui au plus,
 * et {@value #BATCH} chunks par demande — un chunk revendique de loin se charge pour noter son
 * proprietaire, et une selection ne doit pas figer le serveur.
 */
@EventBusSubscriber
public final class ClaimMap {

    /** La portee de la carte, en chunks autour de celui du joueur : 1 024 blocs. */
    public static final int RADIUS = 64;
    /** Les chunks d'une demande au plus (une zone de 8 sur 8). */
    public static final int BATCH = 64;

    /** Les zones des autres : une base ennemie, une place forte, la nation d'un autre joueur. */
    public static final int ENEMY_BASE = 0;
    public static final int STRONGHOLD = 1;
    public static final int OTHER_NATION = 2;

    /** Une demande toutes les cinq ticks au plus. */
    private static final long COOLDOWN_TICKS = 5L;
    private static final String OVERWORLD = "minecraft:overworld";

    private static final Map<UUID, Integer> SENT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    /** Un claim de la nation : sa dimension (rang dans la liste de l'etat) et son chunk. */
    public record Mine(int dimension, int x, int z) {
    }

    /** Des chunks revendiques par d'autres, bornes compris. */
    public record Zone(String dimension, int minX, int minZ, int maxX, int maxZ, String label, int kind) {

        public boolean contains(String where, int x, int z) {
            return dimension.equals(where) && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    /** Ce que la carte du client sait des claims. */
    public record State(boolean nation, String name, int count, int cap, int radius, List<String> dimensions,
                        List<Mine> mine, List<Zone> zones) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(id("claim_map"));
        public static final StreamCodec<FriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(
                (buffer, state) -> {
                    buffer.writeBoolean(state.nation());
                    buffer.writeUtf(state.name());
                    buffer.writeVarInt(state.count());
                    buffer.writeVarInt(state.cap());
                    buffer.writeVarInt(state.radius());
                    buffer.writeVarInt(state.dimensions().size());
                    for (String dimension : state.dimensions()) {
                        buffer.writeUtf(dimension);
                    }
                    buffer.writeVarInt(state.mine().size());
                    for (Mine claim : state.mine()) {
                        buffer.writeVarInt(claim.dimension());
                        buffer.writeInt(claim.x());
                        buffer.writeInt(claim.z());
                    }
                    buffer.writeVarInt(state.zones().size());
                    for (Zone zone : state.zones()) {
                        buffer.writeUtf(zone.dimension());
                        buffer.writeInt(zone.minX());
                        buffer.writeInt(zone.minZ());
                        buffer.writeInt(zone.maxX());
                        buffer.writeInt(zone.maxZ());
                        buffer.writeUtf(zone.label());
                        buffer.writeVarInt(zone.kind());
                    }
                },
                buffer -> {
                    boolean nation = buffer.readBoolean();
                    String name = buffer.readUtf();
                    int count = buffer.readVarInt();
                    int cap = buffer.readVarInt();
                    int radius = buffer.readVarInt();
                    int dimensionCount = buffer.readVarInt();
                    List<String> dimensions = new ArrayList<>(dimensionCount);
                    for (int index = 0; index < dimensionCount; index++) {
                        dimensions.add(buffer.readUtf());
                    }
                    int mineCount = buffer.readVarInt();
                    List<Mine> mine = new ArrayList<>(mineCount);
                    for (int index = 0; index < mineCount; index++) {
                        mine.add(new Mine(buffer.readVarInt(), buffer.readInt(), buffer.readInt()));
                    }
                    int zoneCount = buffer.readVarInt();
                    List<Zone> zones = new ArrayList<>(zoneCount);
                    for (int index = 0; index < zoneCount; index++) {
                        zones.add(new Zone(buffer.readUtf(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                                buffer.readInt(), buffer.readUtf(), buffer.readVarInt()));
                    }
                    return new State(nation, name, count, cap, radius, dimensions, mine, zones);
                });

        @Override
        public Type<State> type() {
            return TYPE;
        }
    }

    /** Revendiquer (ou rendre) les chunks d'un rectangle, bornes comprises. */
    public record Request(String dimension, int x1, int z1, int x2, int z2, boolean claim)
            implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(id("claim_map_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
                (buffer, request) -> {
                    buffer.writeUtf(request.dimension(), 128);
                    buffer.writeInt(request.x1());
                    buffer.writeInt(request.z1());
                    buffer.writeInt(request.x2());
                    buffer.writeInt(request.z2());
                    buffer.writeBoolean(request.claim());
                },
                buffer -> new Request(buffer.readUtf(128), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                        buffer.readInt(), buffer.readBoolean()));

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /** La reponse a une demande : ce qui a ete fait, ou pourquoi rien. */
    public record Result(String text, boolean ok) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(id("claim_map_result"));
        public static final StreamCodec<FriendlyByteBuf, Result> STREAM_CODEC = StreamCodec.of(
                (buffer, result) -> {
                    buffer.writeUtf(result.text());
                    buffer.writeBoolean(result.ok());
                },
                buffer -> new Result(buffer.readUtf(), buffer.readBoolean()));

        @Override
        public Type<Result> type() {
            return TYPE;
        }
    }

    private ClaimMap() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(DoHy.MOD_ID, path);
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(State.TYPE, State.STREAM_CODEC);
        registrar.playToClient(Result.TYPE, Result.STREAM_CODEC);
        registrar.playToServer(Request.TYPE, Request.STREAM_CODEC, (request, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                handle(player, request);
            }
        });
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SENT.remove(player.getUUID());
            send(player, true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
        LAST.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 11) {
            send(player, false);
        }
    }

    /** Envoie l'etat s'il a change depuis le dernier envoi (ou toujours, si {@code force}). */
    public static void send(ServerPlayer player, boolean force) {
        State state = state(player);
        int hash = state.hashCode();
        Integer before = SENT.put(player.getUUID(), hash);
        if (force || before == null || before != hash) {
            PacketDistributor.sendToPlayer(player, state);
        }
    }

    /** Les claims de la nation, sa limite, et les zones des autres que le serveur connait. */
    static State state(ServerPlayer player) {
        FactionData data = FactionApi.data(player);
        List<String> dimensions = new ArrayList<>();
        List<Mine> mine = new ArrayList<>();
        for (FactionData.Claim claim : data.claims) {
            int index = dimensions.indexOf(claim.dimension());
            if (index < 0) {
                dimensions.add(claim.dimension());
                index = dimensions.size() - 1;
            }
            mine.add(new Mine(index, claim.x(), claim.z()));
        }
        List<Zone> zones = new ArrayList<>();
        EnemyBase base = player.getData(Factions.ENEMY_BASE.get());
        if (base.state() == EnemyBase.ACTIVE) {
            zones.add(square(base.center().getX() >> 4, base.center().getZ() >> 4, base.kind().claimRadius(),
                    "Base ennemie des " + base.name(), ENEMY_BASE));
        }
        Strongholds.Holding fort = Strongholds.holding(player.getUUID());
        if (fort != null) {
            zones.add(square(fort.center().getX() >> 4, fort.center().getZ() >> 4, fort.radius(), fort.name(),
                    STRONGHOLD));
        }
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            FactionData theirs = other == player ? null : FactionApi.data(other);
            if (theirs == null || !theirs.exists()) {
                continue;
            }
            for (FactionData.Claim claim : theirs.claims) {
                zones.add(new Zone(claim.dimension(), claim.x(), claim.z(), claim.x(), claim.z(),
                        "La nation " + theirs.name(), OTHER_NATION));
            }
        }
        return new State(data.exists(), data.exists() ? data.name() : "", data.claims.size(),
                data.exists() ? FactionApi.claimCap(player) : 0, RADIUS, List.copyOf(dimensions), List.copyOf(mine),
                List.copyOf(zones));
    }

    private static Zone square(int chunkX, int chunkZ, int radius, String label, int kind) {
        return new Zone(OVERWORLD, chunkX - radius, chunkZ - radius, chunkX + radius, chunkZ + radius, label, kind);
    }

    // -- Les demandes de la carte ------------------------------------------------------------------

    static void handle(ServerPlayer player, Request request) {
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS && now >= last) {
            return;
        }
        LAST.put(player.getUUID(), now);
        String refusal = refusal(player, level, request);
        if (refusal != null) {
            reply(player, refusal, false);
            return;
        }
        reply(player, apply(player, level, request), true);
        send(player, true);
    }

    private static String refusal(ServerPlayer player, ServerLevel level, Request request) {
        FactionData data = FactionApi.data(player);
        if (!data.exists()) {
            return "Fonde d'abord ta nation (/f create)";
        }
        if (!FactionApi.dimension(level).equals(request.dimension())) {
            return "La carte ne revendique que dans la dimension ou tu te trouves";
        }
        if (!FactionApi.claimable(level)) {
            return "On ne revendique pas dans cette dimension";
        }
        long width = Math.abs((long) request.x2() - request.x1()) + 1L;
        long depth = Math.abs((long) request.z2() - request.z1()) + 1L;
        if (width * depth > BATCH) {
            return "Une zone de " + BATCH + " chunks au plus (8 sur 8) : celle-ci en compte " + width * depth;
        }
        return null;
    }

    /** Revendique ou rend chaque chunk du rectangle, et dit ce qui a ete fait. */
    private static String apply(ServerPlayer player, ServerLevel level, Request request) {
        FactionData data = FactionApi.data(player);
        String dimension = request.dimension();
        ChunkPos at = player.chunkPosition();
        int minX = Math.min(request.x1(), request.x2());
        int maxX = Math.max(request.x1(), request.x2());
        int minZ = Math.min(request.z1(), request.z2());
        int maxZ = Math.max(request.z1(), request.z2());
        int done = 0;
        int refused = 0;
        int far = 0;
        String reason = null;
        boolean full = false;
        for (int z = minZ; z <= maxZ && !full; z++) {
            for (int x = minX; x <= maxX && !full; x++) {
                boolean mine = owned(data, dimension, x, z);
                if (request.claim() == mine) {
                    continue;
                }
                if (Math.max(Math.abs(x - at.x()), Math.abs(z - at.z())) > RADIUS) {
                    far++;
                    continue;
                }
                String refusal = request.claim() ? FactionApi.claimChunk(player, level, x, z)
                        : FactionApi.unclaim(player, dimension, x, z);
                if (refusal == null) {
                    done++;
                } else {
                    refused++;
                    reason = reason == null ? refusal : reason;
                    full = request.claim() && data.claims.size() >= FactionApi.claimCap(player);
                }
            }
        }
        int cap = FactionApi.claimCap(player);
        StringBuilder text = new StringBuilder();
        if (done > 0) {
            String verb = request.claim() ? "revendique" : "rendu";
            text.append(done).append(done == 1 ? " chunk " : " chunks ").append(verb).append(done == 1 ? "" : "s")
                    .append(" (").append(data.claims.size()).append(" / ").append(cap).append(")");
        } else if (refused == 0 && far == 0) {
            text.append(request.claim() ? "Ces chunks sont deja a toi" : "Aucun de ces chunks n'est a toi");
        }
        if (refused > 0) {
            text.append(text.isEmpty() ? "" : " ; ").append(reason);
        }
        if (far > 0) {
            text.append(text.isEmpty() ? "" : " ; ").append(far).append(far == 1 ? " chunk" : " chunks")
                    .append(" hors de portee (").append(RADIUS * 16).append(" blocs de toi au plus)");
        }
        return text.toString();
    }

    private static boolean owned(FactionData data, String dimension, int x, int z) {
        for (FactionData.Claim claim : data.claims) {
            if (claim.is(dimension, x, z)) {
                return true;
            }
        }
        return false;
    }

    private static void reply(ServerPlayer player, String text, boolean ok) {
        PacketDistributor.sendToPlayer(player, new Result(text, ok));
    }
}

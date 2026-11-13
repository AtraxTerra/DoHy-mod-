package fr.atraxterra.dohy.earth;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.zip.Deflater;

/**
 * Ce que l'atlas du client doit savoir de la Terre : la grille des pays (le
 * globe, visible d'avance), les gisements geants et les lieux, et qui tient
 * chaque pays. Rien n'est envoye hors d'un monde dont un datapack decrit la
 * carte.
 *
 * <p>Le globe et les lieux partent a la connexion ; les pays tenus, a la
 * connexion puis des qu'ils changent (releve toutes les cinq secondes).
 */
@EventBusSubscriber
public final class EarthAtlas {

    /** Un pays de la carte : son numero dans la grille, son nom, sa teinte de carte politique (0-15). */
    public record Country(int id, String code, String name, int color) {
    }

    /** Le globe : la grille des pays, compressee (deux octets par case, ligne par ligne du nord au sud). */
    public record Globe(int minX, int minZ, int cell, int width, int height, byte[] grid, List<Country> countries)
            implements CustomPacketPayload {
        public static final Type<Globe> TYPE = new Type<>(id("atlas_globe"));
        public static final StreamCodec<FriendlyByteBuf, Globe> STREAM_CODEC = StreamCodec.of(
                (buffer, globe) -> {
                    buffer.writeVarInt(globe.minX());
                    buffer.writeVarInt(globe.minZ());
                    buffer.writeVarInt(globe.cell());
                    buffer.writeVarInt(globe.width());
                    buffer.writeVarInt(globe.height());
                    buffer.writeByteArray(globe.grid());
                    buffer.writeVarInt(globe.countries().size());
                    for (Country country : globe.countries()) {
                        buffer.writeVarInt(country.id());
                        buffer.writeUtf(country.code());
                        buffer.writeUtf(country.name());
                        buffer.writeVarInt(country.color());
                    }
                },
                buffer -> {
                    int minX = buffer.readVarInt();
                    int minZ = buffer.readVarInt();
                    int cell = buffer.readVarInt();
                    int width = buffer.readVarInt();
                    int height = buffer.readVarInt();
                    byte[] grid = buffer.readByteArray();
                    int count = buffer.readVarInt();
                    List<Country> countries = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        countries.add(new Country(buffer.readVarInt(), buffer.readUtf(), buffer.readUtf(),
                                buffer.readVarInt()));
                    }
                    return new Globe(minX, minZ, cell, width, height, grid, countries);
                });

        @Override
        public Type<Globe> type() {
            return TYPE;
        }
    }

    /**
     * Un lieu de l'atlas : un gisement geant ou un lieu de la Terre.
     *
     * @param kind 0 mine, 1 cheminee de diamant, 2 nappe de petrole ou de gaz, 3 sables bitumineux,
     *             10 zone irradiee, 11 base de lancement
     */
    public record Mark(int kind, String name, String resource, int x, int z, float rx, float rz, float angle) {
    }

    public record Layers(List<Mark> marks) implements CustomPacketPayload {
        public static final Type<Layers> TYPE = new Type<>(id("atlas_layers"));
        public static final StreamCodec<FriendlyByteBuf, Layers> STREAM_CODEC = StreamCodec.of(
                (buffer, layers) -> {
                    buffer.writeVarInt(layers.marks().size());
                    for (Mark mark : layers.marks()) {
                        buffer.writeVarInt(mark.kind());
                        buffer.writeUtf(mark.name());
                        buffer.writeUtf(mark.resource());
                        buffer.writeVarInt(mark.x());
                        buffer.writeVarInt(mark.z());
                        buffer.writeFloat(mark.rx());
                        buffer.writeFloat(mark.rz());
                        buffer.writeFloat(mark.angle());
                    }
                },
                buffer -> {
                    int count = buffer.readVarInt();
                    List<Mark> marks = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        marks.add(new Mark(buffer.readVarInt(), buffer.readUtf(), buffer.readUtf(), buffer.readVarInt(),
                                buffer.readVarInt(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat()));
                    }
                    return new Layers(marks);
                });

        @Override
        public Type<Layers> type() {
            return TYPE;
        }
    }

    /**
     * Qui tient un pays.
     *
     * @param kind 1 ta faction, 2 la faction d'un autre joueur, 3 une nation rivale ({@code nation} : son rang)
     */
    public record Owner(int country, int kind, int nation, String holder) {
    }

    /** Les pays tenus, ta capitale (0 : aucune) et ta cible (0 : aucune). */
    public record Nations(List<Owner> owners, int capital, int capitalX, int capitalZ, int target)
            implements CustomPacketPayload {
        public static final Type<Nations> TYPE = new Type<>(id("atlas_nations"));
        public static final StreamCodec<FriendlyByteBuf, Nations> STREAM_CODEC = StreamCodec.of(
                (buffer, nations) -> {
                    buffer.writeVarInt(nations.owners().size());
                    for (Owner owner : nations.owners()) {
                        buffer.writeVarInt(owner.country());
                        buffer.writeVarInt(owner.kind());
                        buffer.writeVarInt(owner.nation());
                        buffer.writeUtf(owner.holder());
                    }
                    buffer.writeVarInt(nations.capital());
                    buffer.writeVarInt(nations.capitalX());
                    buffer.writeVarInt(nations.capitalZ());
                    buffer.writeVarInt(nations.target());
                },
                buffer -> {
                    int count = buffer.readVarInt();
                    List<Owner> owners = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        owners.add(new Owner(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                                buffer.readUtf()));
                    }
                    return new Nations(owners, buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                            buffer.readVarInt());
                });

        @Override
        public Type<Nations> type() {
            return TYPE;
        }
    }

    private static final Map<UUID, Integer> SENT = new ConcurrentHashMap<>();

    private EarthAtlas() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(DoHy.MOD_ID, path);
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(Globe.TYPE, Globe.STREAM_CODEC);
        registrar.playToClient(Layers.TYPE, Layers.STREAM_CODEC);
        registrar.playToClient(Nations.TYPE, Nations.STREAM_CODEC);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SENT.remove(player.getUUID());
        EarthData earth = EarthData.get();
        if (earth == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, globe(earth));
        PacketDistributor.sendToPlayer(player, layers());
        sendNations(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 100 == 37
                && EarthData.get() != null) {
            sendNations(player);
        }
    }

    /** Envoie les pays tenus s'ils ont change depuis le dernier envoi. */
    private static void sendNations(ServerPlayer player) {
        Nations nations = nations(player);
        int hash = nations.hashCode();
        Integer before = SENT.put(player.getUUID(), hash);
        if (before == null || before != hash) {
            PacketDistributor.sendToPlayer(player, nations);
        }
    }

    private static Globe globe(EarthData earth) {
        EarthData.Grid grid = earth.config().grid();
        short[] cells = earth.cells();
        byte[] raw = new byte[cells.length * 2];
        for (int index = 0; index < cells.length; index++) {
            raw[index * 2] = (byte) (cells[index] >> 8);
            raw[index * 2 + 1] = (byte) cells[index];
        }
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(raw.length / 16);
        byte[] chunk = new byte[1 << 16];
        while (!deflater.finished()) {
            out.write(chunk, 0, deflater.deflate(chunk));
        }
        deflater.end();
        List<Country> countries = new ArrayList<>();
        for (EarthData.Country country : earth.config().countries()) {
            countries.add(new Country(country.id(), country.code(), country.name(), country.color()));
        }
        return new Globe(grid.minX(), grid.minZ(), grid.cell(), grid.width(), grid.height(), out.toByteArray(),
                countries);
    }

    private static Layers layers() {
        List<Mark> marks = new ArrayList<>();
        for (EarthBodies.Body body : EarthBodies.all()) {
            int kind = switch (body.kind()) {
                case ORE -> 0;
                case PIPE -> 1;
                case POOL -> 2;
                case SAND -> 3;
            };
            marks.add(new Mark(kind, body.name(), body.resource(), (int) Math.round(body.centerX()),
                    (int) Math.round(body.centerZ()), (float) body.rx(), (float) body.rz(),
                    (float) Math.atan2(body.sin(), body.cos())));
        }
        for (EarthSites.Placed placed : EarthSites.placed()) {
            boolean zone = EarthSites.IRRADIATED.equals(placed.site().kind());
            marks.add(new Mark(zone ? 10 : 11, placed.site().name(), zone ? "zone irradiee" : "base de lancement",
                    placed.x(), placed.z(), placed.site().radius(), placed.site().radius(), 0.0f));
        }
        return new Layers(marks);
    }

    private static Nations nations(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        EarthData earth = EarthData.get();
        List<Owner> owners = new ArrayList<>();
        if (earth != null && EarthNations.active()) {
            for (EarthData.Country country : earth.config().countries()) {
                EarthNations.Holder holder = EarthNations.holder(server, country.id());
                if (holder.ownedBy(player.getUUID())) {
                    owners.add(new Owner(country.id(), 1, 0, "ta faction"));
                } else if (holder.player()) {
                    owners.add(new Owner(country.id(), 2, 0, holder.name()));
                } else if (holder.rival() >= 0) {
                    owners.add(new Owner(country.id(), 3, holder.rival(), holder.name()));
                }
            }
        }
        EarthNations.Nation nation = EarthNations.nation(player);
        return new Nations(owners, nation.capital(), nation.capitalX(), nation.capitalZ(), nation.target());
    }
}

package fr.atraxterra.dohy.ui;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ce que la liste des joueurs (touche Tab) affiche en plus du jeu : la faction de
 * chaque joueur et son niveau global.
 *
 * <p>Le client ne connait ni l'une ni l'autre : le serveur envoie la liste a tous,
 * a chaque connexion et toutes les cinq secondes. Une ligne par joueur en ligne,
 * quelques octets : rien a optimiser.
 */
@EventBusSubscriber
public record TabInfo(List<Entry> entries) implements CustomPacketPayload {

    /** Une ligne : la faction ({@code ""} sans faction), sa couleur, le niveau global. */
    public record Entry(UUID id, String faction, int color, int level) {
    }

    public static final Type<TabInfo> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "tab_info"));

    public static final StreamCodec<FriendlyByteBuf, TabInfo> STREAM_CODEC = StreamCodec.of(
            (buffer, info) -> {
                buffer.writeVarInt(info.entries().size());
                for (Entry entry : info.entries()) {
                    buffer.writeUUID(entry.id());
                    buffer.writeUtf(entry.faction(), 32);
                    buffer.writeInt(entry.color());
                    buffer.writeVarInt(entry.level());
                }
            },
            buffer -> {
                int count = Math.min(buffer.readVarInt(), 1024);
                List<Entry> entries = new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    entries.add(new Entry(buffer.readUUID(), buffer.readUtf(32), buffer.readInt(), buffer.readVarInt()));
                }
                return new TabInfo(entries);
            });

    /** Les couleurs des factions, choisies par leur nom : une faction garde la sienne. */
    private static final int[] COLORS = {0xFFFAC400, 0xFF2F5BEA, 0xFF22E558, 0xFFEC4FB5, 0xFF2BC4C4, 0xFFFF6F00,
            0xFFF3445D, 0xFFB45CFF};

    /** La couleur d'une faction, choisie par son nom ; gris sans faction. */
    public static int color(String faction) {
        if (faction == null || faction.isEmpty()) {
            return 0xFF5D6068;
        }
        return COLORS[Math.floorMod(faction.toLowerCase(java.util.Locale.ROOT).hashCode(), COLORS.length)];
    }

    private static final int PERIOD = 100;
    private static int ticks;

    @Override
    public Type<TabInfo> type() {
        return TYPE;
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(TYPE, STREAM_CODEC);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % PERIOD == 0) {
            broadcast(event.getServer());
        }
    }

    /** A la connexion : le nouveau venu recoit la liste, et les autres apprennent sa ligne. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().getServer() != null) {
            broadcast(player.level().getServer());
        }
    }

    private static void broadcast(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        List<Entry> entries = new ArrayList<>(players.size());
        for (ServerPlayer player : players) {
            entries.add(entry(player));
        }
        PacketDistributor.sendToAllPlayers(new TabInfo(entries));
    }

    private static Entry entry(ServerPlayer player) {
        String faction = "";
        int color = 0xFF5D6068;
        try {
            if (FactionApi.has(player)) {
                FactionData data = FactionApi.data(player);
                faction = data.name() == null ? "" : data.name();
                if (faction.length() > 16) {
                    faction = faction.substring(0, 16);
                }
                color = color(faction);
            }
        } catch (RuntimeException ignored) {
            // Une faction illisible ne prive pas la liste de la ligne du joueur.
        }
        int level = 0;
        try {
            level = ContractApi.globalLevel(player);
        } catch (RuntimeException ignored) {
            // Idem pour le niveau.
        }
        return new Entry(player.getUUID(), faction, color, level);
    }
}

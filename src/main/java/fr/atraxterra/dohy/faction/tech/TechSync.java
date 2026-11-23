package fr.atraxterra.dohy.faction.tech;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ce que le client doit savoir pour l'infobulle des machines (lot 23d) : les paliers, leurs motifs
 * de machines, et ceux que la nation du joueur a. Le client ne connait pas les donnees du serveur
 * (un serveur dedie garde son datapack) ; il recoit ici de quoi dire, pour chaque objet, le palier
 * qui le deverrouille.
 *
 * <p>Le serveur regarde toutes les deux secondes et n'envoie que si quelque chose a change : les
 * donnees (un {@code /dohy reload}) ou un palier de la nation.
 *
 * @param entries un palier par ligne, dans l'ordre des domaines
 * @param free    les motifs des machines laissees libres
 */
@EventBusSubscriber
public record TechSync(List<Entry> entries, List<String> free) implements CustomPacketPayload {

    /**
     * Un palier.
     *
     * @param domain  le nom du domaine (« Energie »)
     * @param tier    son numero
     * @param name    son nom (« Vapeur »)
     * @param reached vrai si la nation du joueur l'a (ou d'office)
     * @param patterns les machines qu'il deverrouille (motifs, {@code *} accepte)
     */
    public record Entry(String domain, int tier, String name, boolean reached, List<String> patterns) {
    }

    public static final Type<TechSync> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "tech_sync"));

    public static final StreamCodec<FriendlyByteBuf, TechSync> STREAM_CODEC = StreamCodec.of(
            (buffer, sync) -> {
                buffer.writeVarInt(sync.entries().size());
                for (Entry entry : sync.entries()) {
                    buffer.writeUtf(entry.domain(), 64);
                    buffer.writeVarInt(entry.tier());
                    buffer.writeUtf(entry.name(), 96);
                    buffer.writeBoolean(entry.reached());
                    buffer.writeVarInt(entry.patterns().size());
                    for (String pattern : entry.patterns()) {
                        buffer.writeUtf(pattern, 160);
                    }
                }
                buffer.writeVarInt(sync.free().size());
                for (String pattern : sync.free()) {
                    buffer.writeUtf(pattern, 160);
                }
            },
            buffer -> {
                int count = buffer.readVarInt();
                List<Entry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    String domain = buffer.readUtf(64);
                    int tier = buffer.readVarInt();
                    String name = buffer.readUtf(96);
                    boolean reached = buffer.readBoolean();
                    int patterns = buffer.readVarInt();
                    List<String> list = new ArrayList<>(patterns);
                    for (int p = 0; p < patterns; p++) {
                        list.add(buffer.readUtf(160));
                    }
                    entries.add(new Entry(domain, tier, name, reached, List.copyOf(list)));
                }
                int frees = buffer.readVarInt();
                List<String> free = new ArrayList<>(frees);
                for (int i = 0; i < frees; i++) {
                    free.add(buffer.readUtf(160));
                }
                return new TechSync(List.copyOf(entries), List.copyOf(free));
            });

    private static final int PERIOD = 40;

    /** Ce qui a ete envoye a chaque joueur : l'empreinte des donnees et de ses paliers. */
    private static final Map<UUID, Integer> SENT = new HashMap<>();
    private static int ticks;

    @Override
    public Type<TechSync> type() {
        return TYPE;
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        event.registrar("2").optional().playToClient(TYPE, STREAM_CODEC);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % PERIOD != 0 || TechConfig.domains().isEmpty()) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            int print = fingerprint(player);
            Integer sent = SENT.get(player.getUUID());
            if (sent != null && sent == print) {
                continue;
            }
            SENT.put(player.getUUID(), print);
            PacketDistributor.sendToPlayer(player, of(player));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    /** L'empreinte de ce que le joueur doit voir : l'instance des donnees, et les paliers de sa nation. */
    private static int fingerprint(ServerPlayer player) {
        FactionData data = FactionApi.data(player);
        int print = System.identityHashCode(TechConfig.domains()) * 31 + (data.exists() ? 1 : 0);
        for (TechConfig.Domain domain : TechConfig.domains()) {
            print = print * 31 + Tech.level(data, domain);
            for (TechConfig.Tier tier : domain.tiers()) {
                print = print * 31 + (Tech.reached(data, domain, tier.tier()) ? 1 : 0);
            }
        }
        return print;
    }

    public static TechSync of(ServerPlayer player) {
        FactionData data = FactionApi.data(player);
        List<Entry> entries = new ArrayList<>();
        for (TechConfig.Domain domain : TechConfig.domains()) {
            for (TechConfig.Tier tier : domain.tiers()) {
                if (!tier.unlocks().isEmpty()) {
                    entries.add(new Entry(domain.name(), tier.tier(), tier.name(),
                            Tech.reached(data, domain, tier.tier()), tier.unlocks()));
                }
            }
        }
        return new TechSync(List.copyOf(entries), TechConfig.free());
    }
}

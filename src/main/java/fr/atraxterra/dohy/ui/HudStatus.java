package fr.atraxterra.dohy.ui;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.faction.war.Sieges;
import fr.atraxterra.dohy.faction.war.WarData;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ce que la carte du HUD affiche en haut a droite et que le client ne connait pas : la
 * faction du joueur (nom, couleur, ligue, ELO, claims) et ses alertes de guerre (la nation
 * qui le menace ou l'attaque, l'echeance, le siege en cours).
 *
 * <p>Le serveur regarde toutes les deux secondes et n'envoie que ce qui a change : une
 * echeance est une date, pas un compte a rebours, et le client decompte lui-meme. Une fois
 * par minute il renvoie quand meme, pour recaler l'horloge du client.
 *
 * @param faction  le nom de la faction, {@code ""} sans faction
 * @param color    la couleur de la faction (celle de la liste des joueurs)
 * @param league   l'indice de la ligue ({@code FactionLeague})
 * @param elo      l'ELO de la faction
 * @param claims   les claims poses
 * @param claimCap les claims permis
 * @param stance   0 paix, 1 menace, 2 guerre ({@code WarData.Stance})
 * @param nation   la nation en conflit, {@code ""} sans conflit
 * @param deadline la fin de l'ultimatum ou l'heure de l'assaut (millisecondes du serveur), 0 sans date
 * @param siege    la ligne du siege en cours, {@code ""} sans siege
 * @param now      l'heure du serveur a l'envoi, pour recaler {@code deadline}
 */
@EventBusSubscriber
public record HudStatus(String faction, int color, int league, long elo, int claims, int claimCap, int stance,
                        String nation, long deadline, String siege, long now) implements CustomPacketPayload {

    public static final Type<HudStatus> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "hud_status"));

    public static final StreamCodec<FriendlyByteBuf, HudStatus> STREAM_CODEC = StreamCodec.of(
            (buffer, status) -> {
                buffer.writeUtf(status.faction(), 32);
                buffer.writeInt(status.color());
                buffer.writeVarInt(status.league());
                buffer.writeVarLong(Math.max(0L, status.elo()));
                buffer.writeVarInt(status.claims());
                buffer.writeVarInt(status.claimCap());
                buffer.writeVarInt(status.stance());
                buffer.writeUtf(status.nation(), 64);
                buffer.writeLong(status.deadline());
                buffer.writeUtf(status.siege(), 160);
                buffer.writeLong(status.now());
            },
            buffer -> new HudStatus(buffer.readUtf(32), buffer.readInt(), buffer.readVarInt(), buffer.readVarLong(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(64),
                    buffer.readLong(), buffer.readUtf(160), buffer.readLong()));

    private static final int PERIOD = 40;
    private static final long RESYNC = 60_000L;

    /** Le dernier etat envoye a chaque joueur (sans l'heure d'envoi) et quand. */
    private static final Map<UUID, HudStatus> SENT = new HashMap<>();
    private static final Map<UUID, Long> SENT_AT = new HashMap<>();
    private static int ticks;

    @Override
    public Type<HudStatus> type() {
        return TYPE;
    }

    public boolean hasFaction() {
        return !faction.isEmpty();
    }

    private HudStatus timeless() {
        return new HudStatus(faction, color, league, elo, claims, claimCap, stance, nation, deadline, siege, 0L);
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(TYPE, STREAM_CODEC);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % PERIOD != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            HudStatus status = of(player, now);
            HudStatus key = status.timeless();
            Long at = SENT_AT.get(player.getUUID());
            if (key.equals(SENT.get(player.getUUID())) && at != null && now - at < RESYNC) {
                continue;
            }
            SENT.put(player.getUUID(), key);
            SENT_AT.put(player.getUUID(), now);
            PacketDistributor.sendToPlayer(player, status);
        }
    }

    /** A la connexion, la carte se remplit au prochain passage, sans attendre un changement. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        SENT.remove(event.getEntity().getUUID());
        SENT_AT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
        SENT_AT.remove(event.getEntity().getUUID());
    }

    static HudStatus of(ServerPlayer player, long now) {
        String faction = "";
        int color = 0xFF5D6068;
        int league = 0;
        long elo = 0L;
        int claims = 0;
        int claimCap = 0;
        try {
            if (FactionApi.has(player)) {
                FactionData data = FactionApi.data(player);
                faction = data.name() == null ? "" : data.name();
                if (faction.length() > 16) {
                    faction = faction.substring(0, 16);
                }
                color = TabInfo.color(faction);
                league = data.ranking().league();
                elo = data.ranking().elo();
                claims = data.claims().size();
                claimCap = FactionApi.claimCap(player);
            }
        } catch (RuntimeException ignored) {
            // Une faction illisible laisse la carte sans faction plutot que sans carte.
        }
        int stance = 0;
        String nation = "";
        long deadline = 0L;
        String siege = "";
        try {
            WarData war = player.getData(Factions.WAR.get());
            Optional<WarData.Relation> conflict = war.conflict();
            if (conflict.isPresent()) {
                WarData.Relation relation = conflict.get();
                stance = relation.stance() == WarData.Stance.GUERRE ? 2 : 1;
                nation = relation.traits().name();
                deadline = relation.stance() == WarData.Stance.GUERRE ? relation.slot() : relation.deadline();
            }
            String status = Sieges.status(player.getUUID());
            if (status != null) {
                siege = status.length() > 150 ? status.substring(0, 150) : status;
            }
        } catch (RuntimeException ignored) {
            // Idem pour la guerre.
        }
        if (nation.length() > 60) {
            nation = nation.substring(0, 60);
        }
        return new HudStatus(faction, color, league, elo, claims, claimCap, stance, nation, deadline, siege, now);
    }
}

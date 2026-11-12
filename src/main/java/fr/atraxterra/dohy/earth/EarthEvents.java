package fr.atraxterra.dohy.earth;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ce que la carte de la Terre change au jeu : elle se charge avant les mondes
 * (les gisements en ont besoin des le premier chunk), annonce les frontieres
 * qu'on passe, sert les gisements et amortit la reception d'un saut.
 */
@EventBusSubscriber
public final class EarthEvents {

    /** Le pays ou chaque joueur a ete vu ; 0 pour la haute mer. */
    private static final Map<UUID, Integer> LAST = new ConcurrentHashMap<>();
    /** Un pays ne s'annonce qu'apres deux releves de suite : longer une frontiere ne fait pas clignoter l'ecran. */
    private static final Map<UUID, Integer> PENDING = new ConcurrentHashMap<>();
    private static final int SEA = 0;

    private EarthEvents() {
    }

    @SubscribeEvent
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        EarthData.load(event.getServer());
        EarthDeposits.load(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        EarthData.unload();
        EarthDeposits.clear();
        EarthJump.clear();
        LAST.clear();
        PENDING.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST.remove(id);
        PENDING.remove(id);
        EarthJump.forget(id);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        EarthData earth = EarthData.get();
        if (earth != null && event.getLevel() instanceof ServerLevel level && earth.covers(level)
                && event.getChunk() instanceof LevelChunk chunk) {
            EarthDeposits.arrived(chunk);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        EarthData earth = EarthData.get();
        if (earth == null) {
            return;
        }
        ServerLevel level = event.getServer().getLevel(earth.dimension());
        if (level != null) {
            EarthDeposits.tick(level, earth);
        }
    }

    /** Une fois par seconde : dans quel pays est le joueur ? */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.onGround() || player.isInWater()) {
            EarthJump.landed(player);
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        EarthData earth = EarthData.get();
        if (earth == null || !earth.covers(player.level())) {
            LAST.remove(player.getUUID());
            PENDING.remove(player.getUUID());
            return;
        }
        EarthData.Country country = earth.countryAt(player.getBlockX(), player.getBlockZ());
        int now = country == null ? SEA : country.id();
        Integer before = LAST.get(player.getUUID());
        if (before != null && before == now) {
            PENDING.remove(player.getUUID());
            return;
        }
        Integer pending = PENDING.put(player.getUUID(), now);
        if (before != null && (pending == null || pending != now)) {
            return;
        }
        PENDING.remove(player.getUUID());
        LAST.put(player.getUUID(), now);
        announce(player, earth, before, country);
    }

    /** Le joueur vient d'etre pose dans ce pays par un saut : il le sait deja. */
    static void entered(ServerPlayer player, EarthData.Country country) {
        LAST.put(player.getUUID(), country == null ? SEA : country.id());
        PENDING.remove(player.getUUID());
    }

    private static void announce(ServerPlayer player, EarthData earth, Integer before, EarthData.Country country) {
        String name = country == null ? "Eaux internationales" : country.name();
        String left = null;
        if (before != null && before != SEA) {
            for (EarthData.Country other : earth.config().countries()) {
                if (other.id() == before) {
                    left = other.name();
                    break;
                }
            }
        }
        String detail = left == null ? earth.coordinates(player.getX(), player.getZ()) : "Tu quittes : " + left;
        player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 16));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(name)
                .withStyle(country == null ? ChatFormatting.AQUA : ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal(detail).withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(Component.literal(country == null ? "Tu entres dans les eaux internationales."
                : "Tu entres en territoire : " + name + ".").withStyle(ChatFormatting.GRAY));
    }

    /** La reception d'un saut ne blesse pas, meme si le parachute s'est eteint avant le sol. */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getSource().is(DamageTypeTags.IS_FALL)
                && EarthJump.protects(player)) {
            event.setCanceled(true);
        }
    }
}

package fr.atraxterra.dohy.earth;

import fr.atraxterra.dohy.spawn.SpawnApi;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.level.BlockEvent;
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
import net.neoforged.neoforge.event.server.ServerStartedEvent;
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
    /** Le lieu ou chaque joueur a ete vu ; la chaine vide hors de tout lieu. */
    private static final Map<UUID, String> SITE = new ConcurrentHashMap<>();

    private EarthEvents() {
    }

    @SubscribeEvent
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        EarthData.load(event.getServer());
        EarthDeposits.load(event.getServer());
        EarthSites.load(event.getServer());
        EarthCities.load(event.getServer());
    }

    /** Le partage du monde entre les nations rivales se calcule une fois les mondes ouverts. */
    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (EarthData.get() != null) {
            EarthNations.atlas(event.getServer());
            SpawnApi.paintNations(event.getServer());
        }
    }

    /** Une capitale se dessine sur la carte du spawn quand sa faction est en ligne. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (EarthData.get() != null && event.getEntity() instanceof ServerPlayer player
                && EarthNations.nation(player).capital() != 0) {
            SpawnApi.paintNations(player.level().getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        EarthNations.clear();
        EarthData.unload();
        EarthDeposits.clear();
        EarthSites.clear();
        EarthCities.clear();
        EarthJump.clear();
        LAST.clear();
        PENDING.clear();
        SITE.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST.remove(id);
        PENDING.remove(id);
        SITE.remove(id);
        EarthCities.forget(id);
        EarthJump.forget(id);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        EarthData earth = EarthData.get();
        if (earth != null && event.getLevel() instanceof ServerLevel level && earth.covers(level)
                && event.getChunk() instanceof LevelChunk chunk) {
            EarthDeposits.arrived(chunk);
            EarthSites.arrived(chunk);
            EarthCities.arrived(chunk);
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
            // les lieux d'abord : leurs gisements comptent sur des biomes et une surface deja poses
            EarthSites.tick(level);
            EarthDeposits.tick(level, earth);
            EarthCities.tick(level);
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
            SITE.remove(player.getUUID());
            return;
        }
        site(player);
        EarthCities.track(player);
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

    /** Les lieux : une zone irradiee ou une base de lancement s'annoncent a l'entree, comme un pays. */
    private static void site(ServerPlayer player) {
        EarthSites.Placed site = EarthSites.at(player.getX(), player.getZ());
        String now = site == null ? "" : site.site().id();
        String before = SITE.put(player.getUUID(), now);
        if (before == null || before.equals(now)) {
            return;
        }
        if (site == null) {
            player.sendSystemMessage(Component.literal("Tu quittes le lieu.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        boolean radiation = site.site().irradiated();
        // un titre court : un nom long deborde de l'ecran
        String detail = radiation
                ? (ModList.get().isLoaded("hbm") ? "Zone d'exclusion : radiation, combinaison conseillee"
                : "Zone d'exclusion")
                : "Base de lancement : les fusees decollent d'ici";
        player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 60, 16));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(site.site().name())
                .withStyle(radiation ? ChatFormatting.RED : ChatFormatting.YELLOW)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal(detail).withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(Component.literal(site.site().name() + " - " + detail + ".")
                .withStyle(radiation ? ChatFormatting.RED : ChatFormatting.YELLOW));
        // le titre du lieu passe devant celui du pays de la meme seconde
        PENDING.remove(player.getUUID());
        EarthData earth = EarthData.get();
        EarthData.Country country = earth == null ? null : earth.countryAt(player.getBlockX(), player.getBlockZ());
        LAST.put(player.getUUID(), country == null ? SEA : country.id());
    }

    /**
     * Les pas de tir des fusees ne se posent que sur une base de lancement.
     * Ailleurs sur la carte, la pose est refusee — l'objet reste au joueur —
     * et il apprend ou est la base la plus proche. Hors de la carte (la Lune,
     * Mars…), rien ne change.
     */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        EarthData earth = EarthData.get();
        if (earth == null || !(event.getLevel() instanceof ServerLevel level) || !earth.covers(level)
                || !EarthSites.isLaunchBlock(event.getPlacedBlock().getBlock())) {
            return;
        }
        BlockPos pos = event.getPos();
        EarthSites.Placed here = EarthSites.at(pos.getX() + 0.5d, pos.getZ() + 0.5d);
        if (here != null && here.site().launch()) {
            return;
        }
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            EarthSites.Placed base = EarthSites.nearestBase(pos.getX(), pos.getZ());
            String where = base == null ? "Aucune base de lancement sur cette carte."
                    : "La plus proche : " + base.site().name() + ", a " + (int) base.distance(pos.getX(), pos.getZ())
                    + " blocs " + EarthSites.direction(pos.getX(), pos.getZ(), base) + ".";
            player.sendSystemMessage(Component.literal("Les fusees ne decollent que d'une base de lancement. " + where)
                    .withStyle(ChatFormatting.RED));
        }
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
        // Les pays a prendre : qui tient celui-ci, et ou en est la conquete s'il est vise.
        String holder = country == null ? null : EarthNations.holderName(player, country.id());
        ChatFormatting tone = ChatFormatting.GRAY;
        if (holder != null) {
            boolean mine = holder.equals("ta faction");
            detail = "Aux mains " + EarthNations.de(holder);
            tone = mine ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            EarthNations.Nation nation = EarthNations.nation(player);
            if (nation.target() == country.id()) {
                detail += " · ta cible (" + MoneyFormat.exact(nation.pointsOn(country.id())) + " / "
                        + MoneyFormat.exact(EarthNations.cost(player.level().getServer(), country.id())) + ")";
                tone = ChatFormatting.YELLOW;
            }
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 16));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(name)
                .withStyle(country == null ? ChatFormatting.AQUA : ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal(detail).withStyle(tone)));
        String line = country == null ? "Tu entres dans les eaux internationales."
                : "Tu entres en territoire : " + name + (holder == null ? "" : ", aux mains " + EarthNations.de(holder)) + "."
                + (left == null ? "" : " Tu quittes : " + left + ".");
        player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY));
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

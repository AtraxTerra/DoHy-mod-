package fr.atraxterra.dohy.earth.mine;

import fr.atraxterra.dohy.earth.EarthBodies;
import fr.atraxterra.dohy.earth.EarthData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ce qui fait vivre les mines : les captures toutes les dix ticks, le versement de
 * l'heure (et celui du retour), et l'annonce a l'entree d'une mine.
 */
@EventBusSubscriber
public final class MineEvents {

    /** La mine ou se tient chaque joueur, pour ne l'annoncer qu'a l'entree. */
    private static final Map<UUID, String> HERE = new HashMap<>();

    private static int ticks;

    private MineEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(MineCommand.build());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ticks++;
        if (ticks % 10 == 0) {
            EarthMines.tick(server);
        }
        if (ticks % 1200 == 0 && EarthMines.active()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                EarthMines.payout(player, false);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EarthMines.payout(player, true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EarthMines.loggedOut(player);
            HERE.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        EarthMines.forgetAll();
        HERE.clear();
        ticks = 0;
    }

    /** Une fois par seconde : entrer dans une mine l'annonce, avec qui la tient. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 7) {
            return;
        }
        EarthData earth = EarthData.get();
        if (earth == null || !earth.covers(player.level()) || EarthBodies.all().isEmpty()) {
            HERE.remove(player.getUUID());
            return;
        }
        EarthBodies.Body body = EarthMines.at(player.getX(), player.getZ());
        String now = body == null ? "" : body.id();
        String before = HERE.put(player.getUUID(), now);
        if (body == null || now.equals(before)) {
            return;
        }
        String holder = EarthMines.holderName(player, body);
        boolean mine = EarthMines.ownedBy(player, body);
        player.sendSystemMessage(Component.literal("Mine de " + body.name() + " (" + EarthMines.label(body) + ") · "
                + (mine ? "a ta faction" : "aux mains " + fr.atraxterra.dohy.earth.EarthNations.de(holder)
                + " · une balise de concession la capture"))
                .withStyle(mine ? ChatFormatting.GREEN : ChatFormatting.GOLD), true);
    }
}

package fr.atraxterra.dohy.faction.enterprise;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Ce qui fait tourner les entreprises (lot 24b) : chaque minute, les heures pleines echues pour
 * les joueurs en ligne ; a la connexion, tout ce qui s'est passe pendant l'absence.
 */
@EventBusSubscriber
public final class EnterpriseEvents {

    private static int ticks;

    private EnterpriseEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ticks++;
        if (ticks % 1200 != 600) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Enterprises.tick(player, false);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Enterprises.tick(player, true);
        }
    }
}

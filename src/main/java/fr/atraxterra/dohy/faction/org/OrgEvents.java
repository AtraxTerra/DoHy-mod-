package fr.atraxterra.dohy.faction.org;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Ce qui fait vivre l'organisation (lot 24a) : la paie des cadres chaque minute pour les heures
 * pleines echues, et celle du retour, qui dit ce qui s'est passe pendant l'absence.
 */
@EventBusSubscriber
public final class OrgEvents {

    private static int ticks;

    private OrgEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ticks++;
        if (ticks % 1200 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Organisations.payroll(player, false);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Organisations.payroll(player, true);
        }
    }
}

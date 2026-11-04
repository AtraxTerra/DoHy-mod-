package fr.atraxterra.dohy.boost;

import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Rappels de booster et passages d'autosell, une fois par minute de jeu. */
@EventBusSubscriber
public final class BoostEvents {

    private static final int STEP_TICKS = 20 * 60;

    /** Derniere minute restante annoncee, pour ne pas repeter le meme rappel. */
    private static final Map<UUID, Long> lastWarned = new HashMap<>();

    private static int tickCounter;

    private BoostEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < STEP_TICKS) {
            return;
        }
        tickCounter = 0;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            warnBooster(player);
            Autosell.tick(player);
        }
    }

    /**
     * Previent a cinq minutes puis a l'expiration.
     *
     * <p>Un booster de vingt minutes paye des millions ne doit pas expirer sans
     * que le joueur s'en apercoive : il aurait vendu son stock pour rien.
     */
    private static void warnBooster(ServerPlayer player) {
        long remaining = Boosters.remaining(player);
        Long previous = lastWarned.get(player.getUUID());

        if (remaining == 0L) {
            if (previous != null && previous > 0L) {
                player.sendSystemMessage(Component.literal("Booster termine.")
                        .withStyle(ChatFormatting.GRAY));
            }
            lastWarned.put(player.getUUID(), 0L);
            return;
        }

        if (remaining == 5L && (previous == null || previous != 5L)) {
            player.sendSystemMessage(Component.literal("Booster x"
                            + MoneyFormat.exact((long) Boosters.active(player))
                            + " : 5 minutes restantes.")
                    .withStyle(ChatFormatting.YELLOW));
        }
        lastWarned.put(player.getUUID(), remaining);
    }
}

package fr.atraxterra.dohy.economy;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Versement des interets, par minute de temps de jeu.
 *
 * <p>Comme l'oubli du marche, le compteur est en temps de jeu : une epargne ne
 * doit pas grossir pendant que le monde est ferme, sinon attendre devient plus
 * rentable que jouer.
 */
@EventBusSubscriber
public final class BankInterest {

    private static final int STEP_TICKS = 20 * 60;

    private static int tickCounter;

    private BankInterest() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < STEP_TICKS) {
            return;
        }
        tickCounter = 0;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            Bank.accrue(player, 1);
        }
    }
}

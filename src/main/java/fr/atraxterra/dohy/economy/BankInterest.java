package fr.atraxterra.dohy.economy;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Versement des interets, par minute reelle passee en jeu.
 *
 * <p>Ce pas-ci ne couvre que le temps en ligne ; le temps hors ligne est
 * rattrape a la connexion par {@link OfflineCatchUp}, qui appelle le meme
 * {@link Bank#accrue}. Un seul calcul d'interet, deux sources de minutes.
 *
 * <p>Le taux est donc exprime par <b>jour reel</b> et calibre en consequence :
 * 0,25 % par jour plutot que 2 %, pour que le rendement d'une soiree reste
 * celui d'avant le passage au temps reel. Sans ce recalibrage, attendre serait
 * devenu douze fois plus rentable que jouer — exactement ce qu'on cherchait a
 * eviter en comptant en temps de jeu.
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

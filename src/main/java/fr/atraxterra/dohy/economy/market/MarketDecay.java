package fr.atraxterra.dohy.economy.market;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * L'oubli du marche : la pression de vente s'efface avec le temps de jeu.
 *
 * <h2>Pourquoi le temps de jeu et non le temps reel</h2>
 *
 * <p>C'est la decision la plus importante de ce fichier. Le joueur vise deux
 * heures en semaine et beaucoup le week-end. Avec une decroissance en temps
 * reel, un week-end de grind ecraserait les prix, puis les cinq jours ouvres
 * les feraient remonter sans que le joueur y soit pour rien — et ses soirees de
 * semaine, elles, se derouleraient sur des prix deja au plancher sans jamais
 * pouvoir les reconstituer par le jeu.
 *
 * <p>En temps de jeu, l'equation est la meme pour tout le monde : vingt minutes
 * passees en jeu rendent 10 % du prix, que ce soit un mardi soir ou un dimanche
 * apres-midi. Une soiree de semaine suffit alors a faire nettement remonter un
 * prix ecrase le week-end precedent.
 *
 * <p>La decroissance est appliquee chaque minute de jeu plutot que par paliers
 * de vingt minutes, avec le facteur equivalent : les prix remontent de facon
 * continue au lieu de sauter d'un coup toutes les vingt minutes.
 */
@EventBusSubscriber
public final class MarketDecay {

    /** Un pas par minute de jeu. */
    private static final int STEP_TICKS = 20 * 60;

    /** 10 % oublies par tranche de vingt minutes. */
    private static final double DECAY_PER_20_MIN = 0.10d;

    /** Facteur conserve a chaque pas : 0.9 ^ (1/20) par minute. */
    private static final double KEEP_PER_STEP = Math.pow(1.0d - DECAY_PER_20_MIN, 1.0d / 20.0d);

    private static int tickCounter;

    private MarketDecay() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < STEP_TICKS) {
            return;
        }
        tickCounter = 0;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PlayerMarket market = DynamicPrice.market(player);
            if (!market.isEmpty()) {
                market.decay(KEEP_PER_STEP);
            }
        }
    }
}

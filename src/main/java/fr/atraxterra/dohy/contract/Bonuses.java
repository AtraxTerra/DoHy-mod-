package fr.atraxterra.dohy.contract;

import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Les multiplicateurs de paiement : demande du jour et premiere vente.
 *
 * <p>Ils s'appliquent <b>apres</b> le prix dynamique, et la pression enregistree
 * reste celle de la quantite reellement vendue. Un bonus ne doit pas accelerer
 * la chute du prix, sinon il s'annulerait lui-meme — c'est la meme regle que
 * pour les boosters.
 */
public final class Bonuses {

    private Bonuses() {
    }

    /** Multiplicateur applicable, sans rien consommer. Pour les apercus. */
    public static double preview(ServerPlayer player, Identifier id) {
        return demand(player, id) * firstSale(player, false);
    }

    /** Multiplicateur applicable, en consommant le bonus de premiere vente. */
    public static double consume(ServerPlayer player, Identifier id) {
        return demand(player, id) * firstSale(player, true);
    }

    private static double demand(ServerPlayer player, Identifier id) {
        Identifier wanted = ContractApi.data(player).demandItem();
        if (wanted == null || !wanted.equals(id)) {
            return 1.0d;
        }
        return Math.max(1.0d, ContractConfig.config().demand().multiplier());
    }

    private static double firstSale(ServerPlayer player, boolean consume) {
        ContractConfig.FirstSale config = ContractConfig.config().firstSale();
        long resetMinutes = config.resetHours() * 60L;
        ContractData data = ContractApi.data(player);

        if (!data.firstSaleAvailable(resetMinutes)) {
            return 1.0d;
        }
        if (consume) {
            data.consumeFirstSale();
        }
        return Math.max(1.0d, config.multiplier());
    }

    /** Annonce au joueur le detail d'un bonus applique. */
    public static void report(ServerPlayer player, double multiplier, long payout) {
        if (multiplier <= 1.0001d) {
            return;
        }
        player.sendSystemMessage(Component.literal("Bonus x"
                        + String.format("%.2f", multiplier) + " applique — "
                        + MoneyFormat.exact(payout))
                .withStyle(ChatFormatting.AQUA));
    }
}

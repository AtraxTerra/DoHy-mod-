package fr.atraxterra.dohy.economy;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.contract.ContractData;
import fr.atraxterra.dohy.economy.market.DynamicPrice;
import fr.atraxterra.dohy.economy.market.MarketDecay;
import fr.atraxterra.dohy.economy.market.PlayerMarket;
import fr.atraxterra.dohy.util.MoneyFormat;
import fr.atraxterra.dohy.util.RealClock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Le rattrapage du temps passe hors ligne.
 *
 * <h2>Ce qui rattrape, et ce qui ne rattrape pas</h2>
 *
 * <p>Deux systemes doivent avancer monde ferme, parce qu'ils modelisent le monde
 * et non l'activite du joueur : l'<b>epargne</b> produit des interets, et la
 * <b>pression sur les prix</b> s'oublie. Sans ca, revenir apres une semaine
 * d'absence retrouve les prix exactement la ou un gros week-end les avait
 * laisses, et il faut regrinder la remontee.
 *
 * <p>Rien d'autre ne rattrape. Un booster ne fond pas hors ligne — c'est un
 * achat actif, pas un phenomene — et l'autosell ne vide pas un coffre en
 * l'absence du joueur. Ces deux-la restent en temps de jeu.
 *
 * <h2>Le plafond de rattrapage</h2>
 *
 * <p>Le rattrapage est plafonne a trente jours. Pas pour l'equilibrage —
 * trente jours d'interets plafonnes restent modestes — mais pour l'horloge : un
 * changement de date systeme, un fuseau mal regle ou une sauvegarde recopiee
 * d'une machine a l'autre peuvent donner un ecart absurde, et un ecart absurde
 * ne doit pas se traduire en credit absurde.
 */
@EventBusSubscriber
public final class OfflineCatchUp {

    /** Ecart maximal rattrape en une fois, en minutes. */
    private static final long MAX_CATCH_UP_MINUTES = 30L * 24L * 60L;

    /** En deca, le rattrapage ne vaut pas un message. */
    private static final long WORTH_REPORTING_MINUTES = 30L;

    private OfflineCatchUp() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            catchUp(player);
        }
    }

    private static void catchUp(ServerPlayer player) {
        ContractData data = ContractApi.data(player);
        long now = RealClock.epochMinutes();
        long last = data.lastSeenMinute();
        data.setLastSeenMinute(now);

        if (last < 0) {
            // Premiere connexion : il n'y a pas de passe a rattraper.
            return;
        }
        if (now < last) {
            // L'horloge a recule. On ne credite rien et on repart de maintenant.
            DoHy.LOGGER.warn("Horloge en arriere de {} min pour {} : rattrapage ignore.",
                    last - now, player.getGameProfile().name());
            return;
        }

        long elapsed = Math.min(now - last, MAX_CATCH_UP_MINUTES);
        if (elapsed <= 0) {
            return;
        }

        long interest = Bank.accrue(player, elapsed);
        int restored = forget(player, elapsed);

        // revenu apres plus d'une demi-heure avec un releve de depart : le rapport d'absence s'en charge
        if (fr.atraxterra.dohy.faction.absence.AbsenceReport.catchUp(player, interest, restored)) {
            return;
        }
        if (elapsed < WORTH_REPORTING_MINUTES || (interest <= 0 && restored == 0)) {
            return;
        }
        report(player, elapsed, interest, restored);
    }

    /**
     * Applique l'oubli du marche sur la duree ecoulee.
     *
     * <p>Un seul calcul pour toute la periode : le facteur de conservation est
     * multiplicatif, donc l'elever a la puissance du nombre de minutes donne
     * exactement le meme resultat que la boucle minute par minute, sans la
     * boucle.
     *
     * @return le nombre d'items dont la pression est entierement retombee
     */
    private static int forget(ServerPlayer player, long minutes) {
        PlayerMarket market = DynamicPrice.market(player);
        if (market.isEmpty()) {
            return 0;
        }
        int before = market.trackedItems();
        double keep = Math.pow(MarketDecay.keepPerMinute(), minutes);
        return before - market.decay(keep);
    }

    private static void report(ServerPlayer player, long elapsed, long interest, int restored) {
        long hours = elapsed / 60L;
        String absence = hours >= 24
                ? (hours / 24L) + " j " + (hours % 24L) + " h"
                : hours + " h " + (elapsed % 60L) + " min";

        player.sendSystemMessage(Component.literal("Pendant ton absence (" + absence + ")")
                .withStyle(ChatFormatting.DARK_AQUA));
        if (interest > 0) {
            player.sendSystemMessage(Component.literal("  interets d'epargne  +"
                            + MoneyFormat.exact(interest))
                    .withStyle(ChatFormatting.GREEN));
        }
        if (restored > 0) {
            player.sendSystemMessage(Component.literal("  prix revenus au plein tarif sur "
                            + restored + " item(s)")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}

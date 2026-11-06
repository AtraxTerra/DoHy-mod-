package fr.atraxterra.dohy.boost;

import fr.atraxterra.dohy.collection.CollectionApi;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.upgrade.UpgradeApi;
import fr.atraxterra.dohy.economy.market.DynamicPrice;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vente automatique du contenu d'un coffre lie.
 *
 * <h2>Pourquoi un coffre vanilla et non un bloc du mod</h2>
 *
 * <p>Un bloc dedie demanderait un enregistrement, un modele, une texture et une
 * table de butin, pour une mecanique qui n'a besoin d'aucun des quatre : il
 * suffit de retenir une position. Le joueur lie n'importe quel coffre, et le
 * mod en vide le contenu vendable a intervalle regulier.
 *
 * <p>Deblocage tres tardif, a 500 M : c'est l'objectif terminal de la partie.
 * Son role est de transformer les fermes construites le week-end en revenu
 * passif de semaine — exactement ce que le rythme de jeu vise demande.
 */
public final class Autosell {

    /** Prix du deblocage. Objectif terminal de {@code BALANCE.md}. */
    public static final long UNLOCK_PRICE = 500_000_000L;

    /** Intervalle entre deux passages, en minutes de temps de jeu. */
    public static final long INTERVAL_MINUTES = 5L;

    /**
     * L'intervalle effectif, raccourci par la branche Cadence.
     *
     * <p>Plancher d'une minute garde en dur : en dessous, l'autosell ne serait
     * plus un ramassage periodique mais un tuyau continu, et le coffre de vente
     * n'aurait plus de raison d'exister.
     */
    public static long intervalFor(ServerPlayer player) {
        long cut = UpgradeApi.bonusInt(player, UpgradeApi.AUTOSELL_INTERVAL_CUT);
        return Math.max(1L, INTERVAL_MINUTES - cut);
    }

    /** Distance maximale entre le joueur et son coffre lie, pour le lien. */
    private static final int LINK_RANGE = 16;

    private Autosell() {
    }

    /** Achete le deblocage. Retourne la raison du refus, ou {@code null}. */
    public static String unlock(ServerPlayer player) {
        BoostData data = Boosters.data(player);
        if (data.autosellUnlocked()) {
            return "Deja debloque.";
        }
        if (!Economy.tryDebit(player, UNLOCK_PRICE)) {
            return "Il te faut " + MoneyFormat.exact(UNLOCK_PRICE) + " en liquide.";
        }
        data.unlockAutosell();
        return null;
    }

    /** Lie un coffre. Retourne la raison du refus, ou {@code null}. */
    public static String link(ServerPlayer player, BlockPos pos) {
        BoostData data = Boosters.data(player);
        if (!data.autosellUnlocked()) {
            return "Autosell non debloque (" + MoneyFormat.exact(UNLOCK_PRICE)
                    + ", /autosell unlock).";
        }
        if (!player.blockPosition().closerThan(pos, LINK_RANGE)) {
            return "Trop loin : place-toi a moins de " + LINK_RANGE + " blocs du coffre.";
        }
        if (!(player.level().getBlockEntity(pos) instanceof Container)) {
            return "Il n'y a pas de conteneur a cette position.";
        }
        data.setAutosellPos(pos.immutable());
        return null;
    }

    /**
     * Vide le coffre lie de son contenu vendable, si l'intervalle est ecoule.
     *
     * <p>Ne charge jamais le chunk : si le coffre n'est pas charge, le passage
     * est simplement saute. Forcer le chargement ferait tourner une ferme a
     * distance en permanence, ce qui n'est pas le marche propose.
     */
    public static void tick(ServerPlayer player) {
        BoostData data = Boosters.data(player);
        if (!data.autosellUnlocked() || data.autosellPos() == null) {
            return;
        }

        long now = ContractApi.data(player).playtimeMinutes();
        if (data.autosellLast() >= 0 && now - data.autosellLast() < intervalFor(player)) {
            return;
        }
        data.markAutosell(now);

        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = data.autosellPos();
        if (!level.isLoaded(pos)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            return;
        }

        // Agregation par item, comme partout : le prix dynamique est une
        // integrale, et tarifer pile par pile partirait du prix d'avant-vente.
        Map<Item, Integer> batch = new LinkedHashMap<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            PriceEntry entry = Prices.book().entry(stack).orElse(null);
            if (entry == null || !entry.sellable()) {
                continue;
            }
            if (!CollectionApi.isUnlocked(player,
                    BuiltInRegistries.ITEM.getKey(stack.getItem()), entry)) {
                continue;
            }
            batch.merge(stack.getItem(), stack.getCount(), Integer::sum);
            container.setItem(slot, ItemStack.EMPTY);
        }

        if (batch.isEmpty()) {
            return;
        }

        long payout = 0L;
        int count = 0;
        for (Map.Entry<Item, Integer> line : batch.entrySet()) {
            PriceEntry entry = Prices.book().entry(line.getKey()).orElse(null);
            if (entry == null) {
                continue;
            }
            payout += DynamicPrice.sell(player,
                    BuiltInRegistries.ITEM.getKey(line.getKey()), entry, line.getValue());
            count += line.getValue();
        }

        container.setChanged();
        long balance = Economy.credit(player, payout);

        int finalCount = count;
        long finalPayout = payout;
        player.sendSystemMessage(Component.literal("Autosell : " + finalCount + " items pour ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(MoneyFormat.exact(finalPayout))
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" · solde " + MoneyFormat.shortForm(balance))
                        .withStyle(ChatFormatting.DARK_GRAY)));
    }
}

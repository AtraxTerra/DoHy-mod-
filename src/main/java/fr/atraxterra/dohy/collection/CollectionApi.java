package fr.atraxterra.dohy.collection;

import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Les collections : seul point d'entree.
 *
 * <p>Le premier palier debloque la vente de l'item. C'est le meilleur levier de
 * rythme du mod : il empeche de tout vendre des la premiere minute, et il donne
 * une raison de miner autre chose que l'optimum du moment.
 */
public final class CollectionApi {

    private CollectionApi() {
    }

    public static CollectionData data(ServerPlayer player) {
        return player.getData(DoHyAttachments.COLLECTIONS);
    }

    public static long countOf(ServerPlayer player, Identifier id) {
        return data(player).countOf(id);
    }

    /**
     * Enregistre une collecte et annonce les paliers franchis.
     *
     * <p>Appele depuis {@link CollectionEvents}, jamais depuis un achat : un
     * item achete au shop ne doit pas faire progresser la collection, sinon il
     * suffirait d'acheter pour debloquer la vente du meme item.
     */
    public static void record(ServerPlayer player, Identifier id, int quantity) {
        if (quantity <= 0) {
            return;
        }
        PriceEntry entry = Prices.book().entry(id).orElse(null);
        if (entry == null) {
            // Item hors catalogue : aucune collection a suivre, et surtout pas
            // de table qui grossit avec tous les items du jeu.
            return;
        }

        List<Long> tiers = CollectionTiers.config().tiersFor(id, entry.tier());
        if (tiers.isEmpty()) {
            return;
        }

        long before = data(player).countOf(id);
        long after = data(player).add(id, quantity);

        for (int index = 0; index < tiers.size(); index++) {
            long threshold = tiers.get(index);
            if (before < threshold && after >= threshold) {
                announce(player, id, entry, index, threshold, tiers.size());
            }
        }
    }

    /**
     * Vrai si le joueur a atteint le premier palier, donc si l'item est
     * vendable pour lui.
     *
     * <p>Un item sans courbe de paliers est vendable d'emblee : c'est le cas des
     * items hors catalogue de collection, et il vaut mieux laisser vendre que
     * bloquer sur une donnee manquante.
     */
    public static boolean isUnlocked(ServerPlayer player, Identifier id, PriceEntry entry) {
        List<Long> tiers = CollectionTiers.config().tiersFor(id, entry.tier());
        if (tiers.isEmpty()) {
            return true;
        }
        return data(player).countOf(id) >= tiers.get(0);
    }

    /** Quantite encore necessaire pour debloquer la vente. Zero si debloquee. */
    public static long missingToUnlock(ServerPlayer player, Identifier id, PriceEntry entry) {
        List<Long> tiers = CollectionTiers.config().tiersFor(id, entry.tier());
        if (tiers.isEmpty()) {
            return 0L;
        }
        return Math.max(0L, tiers.get(0) - data(player).countOf(id));
    }

    /** Index du dernier palier franchi, ou -1. */
    public static int tierIndex(ServerPlayer player, Identifier id, PriceEntry entry) {
        List<Long> tiers = CollectionTiers.config().tiersFor(id, entry.tier());
        long count = data(player).countOf(id);
        int index = -1;
        for (int i = 0; i < tiers.size(); i++) {
            if (count >= tiers.get(i)) {
                index = i;
            }
        }
        return index;
    }

    /**
     * Bonus de plancher acquis par les collections, a ajouter au plancher du
     * tier. Un item `FARM` a fond passe ainsi de 15 % a 40 % de plancher.
     */
    public static double floorBonus(ServerPlayer player, Identifier id, PriceEntry entry) {
        if (!CollectionTiers.config().floorBonusAllowed(id)) {
            return 0.0d;
        }
        int index = tierIndex(player, id, entry);
        if (index < 0) {
            return 0.0d;
        }
        return CollectionTiers.config().reward(index).floorBonusPct();
    }

    /** Prochain palier a atteindre, ou -1 si tout est franchi. */
    public static long nextThreshold(ServerPlayer player, Identifier id, PriceEntry entry) {
        List<Long> tiers = CollectionTiers.config().tiersFor(id, entry.tier());
        long count = data(player).countOf(id);
        for (long threshold : tiers) {
            if (count < threshold) {
                return threshold;
            }
        }
        return -1L;
    }

    private static void announce(ServerPlayer player, Identifier id, PriceEntry entry,
                                 int index, long threshold, int total) {
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        // Item.getName exige une pile en 26.2 : on en fabrique une jetable.
        Component name = item == null
                ? Component.literal(id.toString())
                : new ItemStack(item).getHoverName();

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.5f, 1.6f);

        player.sendSystemMessage(Component.literal("Collection ")
                .withStyle(ChatFormatting.GRAY)
                .append(name.copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" — palier " + (index + 1) + "/" + total
                                + " (" + MoneyFormat.exact(threshold) + ")")
                        .withStyle(ChatFormatting.GOLD)));

        if (index == 0) {
            player.sendSystemMessage(Component.literal("  Vente debloquee au shop.")
                    .withStyle(ChatFormatting.GREEN));
        }

        double bonus = CollectionTiers.config().reward(index).floorBonusPct();
        if (bonus > 0 && CollectionTiers.config().floorBonusAllowed(id)) {
            player.sendSystemMessage(Component.literal("  Prix plancher +"
                            + Math.round(bonus * 100) + " %.")
                    .withStyle(ChatFormatting.GREEN));
        }
    }
}

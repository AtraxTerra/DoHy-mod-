package fr.atraxterra.dohy.collection;

import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.contract.ContractType;
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
 * <p>Chaque palier franchi releve le <b>prix plancher</b> de l'item : plus la
 * collection avance, plus l'item resiste a la vente en masse. C'est ce qui donne
 * une raison de miner autre chose que l'optimum du moment — un item travaille de
 * longue date rapporte plus qu'un item decouvert hier, a quantite egale.
 *
 * <p>Le premier palier verrouillait aussi la vente, a une epoque. Ce verrou est
 * retire : il retardait la decouverte du shop sans rien apporter au rythme, et
 * {@code sell_requires_collection} le restaure si besoin.
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
                ContractApi.grantGlobalXp(player,
                        CollectionTiers.config().reward(index).globalXp(), "palier de collection");
                ContractApi.progress(player, ContractType.COLLECTION_TIER, target -> true, 1L);
            }
        }
    }

    /**
     * Vrai si l'item est vendable pour ce joueur.
     *
     * <p>Par defaut toujours vrai : le verrou d'origine — il fallait avoir
     * recolte dix ou cent exemplaires avant de pouvoir vendre — retardait la
     * decouverte du shop sans rien apporter. Un joueur qui vient de miner son
     * premier diamant doit pouvoir le vendre. Les collections gardent tout leur
     * role par le bonus de plancher et le prestige.
     *
     * <p>{@code sell_requires_collection} dans {@code collections.json} restaure
     * le verrou sans toucher au code.
     *
     * <p>Un item sans courbe de paliers est vendable d'emblee dans tous les cas :
     * mieux vaut laisser vendre que bloquer sur une donnee manquante.
     */
    public static boolean isUnlocked(ServerPlayer player, Identifier id, PriceEntry entry) {
        if (!CollectionTiers.config().sellRequiresCollection()) {
            return true;
        }
        List<Long> tiers = CollectionTiers.config().tiersFor(id, entry.tier());
        if (tiers.isEmpty()) {
            return true;
        }
        return data(player).countOf(id) >= tiers.get(0);
    }

    /**
     * Quantite encore necessaire pour debloquer la vente. Zero si debloquee,
     * donc zero partout tant que le verrou reste desactive.
     */
    public static long missingToUnlock(ServerPlayer player, Identifier id, PriceEntry entry) {
        if (!CollectionTiers.config().sellRequiresCollection()) {
            return 0L;
        }
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

    public static int prestigeLevel(ServerPlayer player, Identifier id) {
        return data(player).prestigeOf(id);
    }

    /**
     * Vrai si la collection est complete et qu'il reste un prestige disponible.
     */
    public static boolean canPrestige(ServerPlayer player, Identifier id, PriceEntry entry) {
        CollectionTiers.Prestige rules = CollectionTiers.config().prestige();
        if (prestigeLevel(player, id) >= rules.maxLevel()) {
            return false;
        }
        List<Long> tiers = CollectionTiers.config().tiersFor(id, entry.tier());
        if (tiers.isEmpty()) {
            return false;
        }
        return data(player).countOf(id) >= tiers.get(tiers.size() - 1);
    }

    /**
     * Passe un prestige. Retourne la raison du refus, ou {@code null}.
     *
     * <p>Le gain est permanent et porte sur le {@code S0} — la quantite
     * ecoulable avant que le prix tombe — et non sur le prix de base. Augmenter
     * le prix de base serait inflationniste sans fin ; augmenter le S0 est
     * auto-limite, puisque le plafond reste le prix de base. On devient
     * industriellement efficace sur un item, on ne le rend pas plus cher.
     */
    public static String prestige(ServerPlayer player, Identifier id, PriceEntry entry) {
        CollectionTiers.Prestige rules = CollectionTiers.config().prestige();
        if (prestigeLevel(player, id) >= rules.maxLevel()) {
            return "Prestige maximal atteint (" + rules.maxLevel() + ").";
        }
        if (!canPrestige(player, id, entry)) {
            long last = lastThreshold(id, entry);
            return "Collection incomplete : il faut " + MoneyFormat.exact(last)
                    + " (tu en as " + MoneyFormat.exact(data(player).countOf(id)) + ").";
        }

        int level = data(player).addPrestige(id);

        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        Component name = item == null
                ? Component.literal(id.toString())
                : new ItemStack(item).getHoverName();

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.8f, 0.6f);
        player.sendSystemMessage(Component.literal("Prestige " + level + " — ")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(name.copy().withStyle(ChatFormatting.WHITE)));
        player.sendSystemMessage(Component.literal("  tu ecoules desormais "
                        + Math.round((1 + level * rules.s0BonusPerLevel()) * 100)
                        + " % de la quantite d'origine au meme prix")
                .withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.literal("  collection repartie a zero")
                .withStyle(ChatFormatting.GRAY));

        ContractApi.grantGlobalXp(player, rules.globalXp(), "prestige de collection");
        return null;
    }

    /** Multiplicateur de S0 acquis par le prestige. */
    public static double s0Multiplier(ServerPlayer player, Identifier id) {
        return 1.0d + prestigeLevel(player, id)
                * CollectionTiers.config().prestige().s0BonusPerLevel();
    }

    /**
     * Bonus de plancher acquis par le prestige.
     *
     * <p>Un item sans bonus de plancher ({@code floor_bonus_disabled}) n'en a
     * pas non plus par le prestige : l'emeraude et les blocs de masse ont ce
     * drapeau pour que leur plancher reste bas, et 300 000 blocs se cassent en
     * quelques heures a la corne du Dancarok — le prestige rendait ce que les
     * paliers retenaient.
     */
    public static double prestigeFloorBonus(ServerPlayer player, Identifier id) {
        if (!CollectionTiers.config().floorBonusAllowed(id)) {
            return 0.0d;
        }
        return prestigeLevel(player, id)
                * CollectionTiers.config().prestige().floorBonusPerLevel();
    }

    private static long lastThreshold(Identifier id, PriceEntry entry) {
        List<Long> tiers = CollectionTiers.config().tiersFor(id, entry.tier());
        return tiers.isEmpty() ? 0L : tiers.get(tiers.size() - 1);
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

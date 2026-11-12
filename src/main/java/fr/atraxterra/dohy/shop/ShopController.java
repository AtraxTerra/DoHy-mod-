package fr.atraxterra.dohy.shop;

import fr.atraxterra.dohy.collection.CollectionApi;
import fr.atraxterra.dohy.economy.market.DynamicPrice;
import fr.atraxterra.dohy.economy.market.HourlyCourse;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.museum.MuseumApi;
import fr.atraxterra.dohy.ui.view.ShopView;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.upgrade.UpgradeApi;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Le shop cote serveur : la vue envoyee a l'ecran, et l'achat et la vente.
 *
 * <p>Les regles sont celles de l'ancien shop en coffre ({@code ShopMenu}) :
 * remise d'achat (musee + negoce, plafonnee a 25 %), prix de vente dynamique,
 * vente verrouillee tant que la collection n'est pas assez avancee.
 */
public final class ShopController {

    /** Plafond d'une transaction : 36 piles de 64. */
    private static final int MAX_AMOUNT = 2304;

    private ShopController() {
    }

    public static void open(ServerPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        UiNet.show(player, view(player));
    }

    static ShopView view(ServerPlayer player) {
        var book = Prices.book();
        List<Identifier> ids = new ArrayList<>();
        book.items().forEach((id, entry) -> {
            if (ShopCategory.byKey(entry.category()) != null && BuiltInRegistries.ITEM.getOptional(id).isPresent()) {
                ids.add(id);
            }
        });
        ids.sort(Comparator
                .<Identifier>comparingInt(id -> {
                    ShopCategory category = ShopCategory.byKey(book.entry(id).map(PriceEntry::category).orElse(""));
                    return category == null ? 99 : category.ordinal();
                })
                .thenComparingLong(id -> book.entry(id).map(PriceEntry::basePrice).orElse(0L))
                .thenComparing(Identifier::toString));
        double discount = discount(player);
        List<ShopView.Offer> offers = new ArrayList<>();
        for (Identifier id : ids) {
            PriceEntry entry = book.entry(id).orElseThrow();
            Item item = BuiltInRegistries.ITEM.getValue(id);
            double course = HourlyCourse.factor(id, entry);
            long buy = entry.buyPrice() > 0
                    ? Math.max(1L, Math.round(entry.buyPrice() * course * (1.0d - discount))) : 0L;
            long sell = 0;
            int percent = 100;
            long missing = 0;
            if (entry.sellable()) {
                sell = Math.max(1L, DynamicPrice.unitPrice(player, id, entry));
                percent = (int) Math.round(100.0 * sell / Math.max(1L, entry.basePrice()));
                if (!CollectionApi.isUnlocked(player, id, entry)) {
                    missing = Math.max(1L, CollectionApi.missingToUnlock(player, id, entry));
                }
            }
            offers.add(new ShopView.Offer(new ItemStack(item), entry.category(), buy,
                    entry.buyPrice() > 0 ? (int) Math.round(discount * 100) : 0, sell, percent, missing,
                    countOwned(player, item), CollectionApi.countOf(player, id),
                    Math.max(0L, CollectionApi.nextThreshold(player, id, entry)),
                    HourlyCourse.percent(id, entry)));
        }
        return new ShopView(Economy.balance(player), offers, HourlyCourse.minutesLeft());
    }

    public static void handle(ServerPlayer player, UiNet.Action action) {
        Identifier id = Identifier.tryParse(action.argument());
        if (id == null) {
            return;
        }
        PriceEntry entry = Prices.book().entry(id).orElse(null);
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        int amount = Math.max(1, Math.min(MAX_AMOUNT, action.amount()));
        if (entry == null || item == null) {
            return;
        }
        switch (action.verb()) {
            case "buy" -> buy(player, id, item, entry, amount);
            case "sell" -> sell(player, id, item, entry, amount);
            default -> {
                return;
            }
        }
        UiNet.show(player, view(player));
    }

    private static void sell(ServerPlayer player, Identifier id, Item item, PriceEntry entry, int wanted) {
        if (!entry.sellable()) {
            refuse(player, "Cet item est en achat seul.");
            return;
        }
        if (!CollectionApi.isUnlocked(player, id, entry)) {
            refuse(player, "Vente verrouillee : il t'en manque "
                    + MoneyFormat.exact(CollectionApi.missingToUnlock(player, id, entry)) + " a collecter.");
            return;
        }
        NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
        int removed = 0;
        for (int slot = 0; slot < items.size() && removed < wanted; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty() || !stack.is(item)) {
                continue;
            }
            int take = Math.min(stack.getCount(), wanted - removed);
            stack.shrink(take);
            removed += take;
        }
        if (removed == 0) {
            refuse(player, "Tu n'en as pas.");
            return;
        }
        long payout = DynamicPrice.sell(player, id, entry, removed);
        long balance = Economy.credit(player, payout);
        fr.atraxterra.dohy.palamod.achievement.Achievements.sold(player, payout);
        play(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4f);
        player.sendSystemMessage(Component.literal("Vendu " + removed + "x pour ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(payout)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" · solde " + MoneyFormat.shortForm(balance))
                        .withStyle(ChatFormatting.DARK_GRAY)));
    }

    private static void buy(ServerPlayer player, Identifier id, Item item, PriceEntry entry, int wanted) {
        if (entry.buyPrice() <= 0L) {
            refuse(player, "Cet item n'est pas en vente.");
            return;
        }
        // Le cours de l'heure touche l'achat comme la vente : voir HourlyCourse.
        long unitPrice = Math.max(1L, Math.round(entry.buyPrice() * HourlyCourse.factor(id, entry)
                * (1.0d - discount(player))));
        long cost = unitPrice * wanted;
        if (!Economy.tryDebit(player, cost)) {
            play(player, SoundEvents.VILLAGER_NO, 1.0f);
            player.sendSystemMessage(Component.literal("Il te manque "
                    + MoneyFormat.exact(cost - Economy.balance(player)) + ".").withStyle(ChatFormatting.RED));
            return;
        }
        fr.atraxterra.dohy.palamod.achievement.Achievements.count(player, fr.atraxterra.dohy.palamod.achievement.Achievements.PURCHASES, 1);
        int left = wanted;
        int stackSize = new ItemStack(item).getMaxStackSize();
        while (left > 0) {
            int count = Math.min(left, stackSize);
            ItemStack bought = new ItemStack(item, count);
            if (!player.getInventory().add(bought)) {
                // Inventaire plein : l'item tombe au sol plutot que de disparaitre
                // apres avoir ete paye.
                player.drop(bought, false);
            }
            left -= count;
        }
        play(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4f);
        player.sendSystemMessage(Component.literal("Achete " + wanted + "x pour ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(cost)).withStyle(ChatFormatting.GOLD)));
    }

    private static void refuse(ServerPlayer player, String message) {
        play(player, SoundEvents.VILLAGER_NO, 1.0f);
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GRAY));
    }

    private static void play(ServerPlayer player, SoundEvent sound, float pitch) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.MASTER, 0.4f, pitch);
    }

    /**
     * La remise d'achat : aile Composants du musee plus branche Negoce.
     *
     * <p>Bornee a 25 %. Au-dela, les prix NTM cesseraient d'etre le mur qu'ils
     * doivent etre — c'est toute la raison pour laquelle ils sont enormes.
     */
    static double discount(ServerPlayer player) {
        return Math.min(0.25d, MuseumApi.buyDiscount(player)
                + UpgradeApi.bonus(player, UpgradeApi.SHOP_DISCOUNT));
    }

    private static int countOwned(ServerPlayer player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}

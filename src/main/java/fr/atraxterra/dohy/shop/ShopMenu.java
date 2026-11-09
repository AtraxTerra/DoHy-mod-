package fr.atraxterra.dohy.shop;

import fr.atraxterra.dohy.collection.CollectionApi;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.market.DynamicPrice;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.museum.MuseumApi;
import fr.atraxterra.dohy.ui.DisplayMenu;
import fr.atraxterra.dohy.ui.Gui;
import fr.atraxterra.dohy.ui.Hub;
import fr.atraxterra.dohy.upgrade.UpgradeApi;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Le shop, presente comme un coffre.
 *
 * <p>Le conteneur ne contient que des copies d'affichage : aucun clic n'est
 * delegue a {@code super}, donc aucun item ne peut etre deplace, duplique ou
 * perdu. Chaque clic est traduit en operation economique, puis la page est
 * redessinee. C'est le socle {@link DisplayMenu} qui le garantit, pour tous les
 * ecrans du mod a la fois.
 *
 * <p>Contrepartie assumee : le client predit localement le deplacement d'items
 * avant d'etre corrige par le serveur, ce qui provoque un bref scintillement au
 * clic. C'est le comportement de tous les shops en coffre, et le prix a payer
 * pour n'avoir aucun code de rendu.
 */
public final class ShopMenu extends DisplayMenu {

    /** Emplacements de marchandise : cinq rangees. */
    private static final int ITEM_SLOTS = 45;

    private static final int SLOT_PREVIOUS = 45;
    /** Six onglets, 46 a 51. La boutique, qui tenait la case 51, est au menu principal. */
    private static final int SLOT_FIRST_TAB = 46;
    private static final int SLOT_BALANCE = 52;
    private static final int SLOT_NEXT = 53;

    private ShopCategory category = ShopCategory.FARM;
    private int page;
    private List<Identifier> currentItems = List.of();

    public ShopMenu(int containerId, Inventory inventory, ServerPlayer player) {
        super(containerId, inventory, player, 6);
        refresh();
    }

    @Override
    protected void onClick(int slot, int button, boolean shift) {
        if (slot >= ITEM_SLOTS) {
            handleControl(slot, button);
            return;
        }
        int index = page * ITEM_SLOTS + slot;
        if (index < currentItems.size()) {
            handleTrade(currentItems.get(index), button, shift);
        }
    }

    private void handleControl(int slot, int button) {
        if (slot == SLOT_PREVIOUS && page > 0) {
            page--;
            click();
        } else if (slot == SLOT_NEXT && (page + 1) * ITEM_SLOTS < currentItems.size()) {
            page++;
            click();
        } else if (slot == SLOT_BALANCE) {
            click(0.7f);
            Hub.open(player);
        } else if (slot >= SLOT_FIRST_TAB
                && slot < SLOT_FIRST_TAB + ShopCategory.values().length) {
            ShopCategory target = ShopCategory.values()[slot - SLOT_FIRST_TAB];
            if (target != category) {
                category = target;
                page = 0;
                click();
            }
        }
    }

    private void handleTrade(Identifier id, int button, boolean shift) {
        PriceEntry entry = Prices.book().entry(id).orElse(null);
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (entry == null || item == null) {
            return;
        }

        if (button == 1) {
            buy(item, entry, shift ? 16 : 1);
        } else if (entry.sellable() && !CollectionApi.isUnlocked(player, id, entry)) {
            failure();
            long missing = CollectionApi.missingToUnlock(player, id, entry);
            player.sendSystemMessage(Component.literal(
                            "Vente verrouillee : il t'en manque " + MoneyFormat.exact(missing)
                                    + " a collecter.")
                    .withStyle(ChatFormatting.GRAY));
        } else if (entry.sellable()) {
            sell(item, entry, shift ? Integer.MAX_VALUE : 1);
        } else {
            failure();
            player.sendSystemMessage(Component.literal("Cet item est en achat seul.")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private void sell(Item item, PriceEntry entry, int wanted) {
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
            failure();
            player.sendSystemMessage(Component.literal("Tu n'en as pas.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        long payout = DynamicPrice.sell(player,
                BuiltInRegistries.ITEM.getKey(item), entry, removed);
        long balance = Economy.credit(player, payout);
        success();
        player.sendSystemMessage(Component.literal("Vendu " + removed + "x pour ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(payout)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" · solde " + MoneyFormat.shortForm(balance))
                        .withStyle(ChatFormatting.DARK_GRAY)));
    }

    private void buy(Item item, PriceEntry entry, int wanted) {
        long unitPrice = Math.max(1L, Math.round(entry.buyPrice() * (1.0d - discount(player))));
        if (entry.buyPrice() <= 0L) {
            failure();
            player.sendSystemMessage(Component.literal("Cet item n'est pas en vente.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        long cost = unitPrice * wanted;
        if (!Economy.tryDebit(player, cost)) {
            failure();
            player.sendSystemMessage(Component.literal("Il te manque "
                            + MoneyFormat.exact(cost - Economy.balance(player)) + ".")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        ItemStack bought = new ItemStack(item, wanted);
        if (!player.getInventory().add(bought)) {
            // Inventaire plein : l'item tombe au sol plutot que de disparaitre
            // apres avoir ete paye.
            player.drop(bought, false);
        }
        success();
        player.sendSystemMessage(Component.literal("Achete " + wanted + "x pour ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(cost)).withStyle(ChatFormatting.GOLD)));
    }

    @Override
    protected void build() {
        currentItems = itemsOf(category);

        for (int slot = 0; slot < ITEM_SLOTS; slot++) {
            int index = page * ITEM_SLOTS + slot;
            if (index < currentItems.size()) {
                set(slot, renderOffer(currentItems.get(index)));
            }
        }

        int pages = Math.max(1, (currentItems.size() + ITEM_SLOTS - 1) / ITEM_SLOTS);

        footer();
        if (page > 0) {
            set(SLOT_PREVIOUS, Gui.previousPage(page, pages));
        }
        if (page + 1 < pages) {
            set(SLOT_NEXT, Gui.nextPage(page + 2, pages));
        }

        for (ShopCategory tab : ShopCategory.values()) {
            boolean active = tab == category;
            set(SLOT_FIRST_TAB + tab.ordinal(), Gui.button(tab.icon(),
                    Component.literal((active ? "> " : "") + tab.display())
                            .withStyle(active ? ChatFormatting.GOLD : ChatFormatting.YELLOW),
                    Gui.lore(Gui.hint(itemsOf(tab).size() + " item(s)"))));
        }

        set(SLOT_BALANCE, Gui.button(Items.GOLD_INGOT,
                "Solde : " + MoneyFormat.exact(Economy.balance(player)), Gui.lore(
                        Gui.hint("page " + (page + 1) + " / " + pages),
                        Gui.blank(),
                        Gui.hint("clic gauche : vendre 1"),
                        Gui.hint("shift + gauche : tout vendre"),
                        Gui.hint("clic droit : acheter 1"),
                        Gui.hint("shift + droit : acheter 16"),
                        Gui.blank(),
                        Gui.hint("la boutique est au menu"),
                        Gui.action("clic ici : revenir au menu"))));
    }

    private List<Identifier> itemsOf(ShopCategory target) {
        var book = Prices.book();
        List<Identifier> ids = new ArrayList<>();
        book.items().forEach((id, entry) -> {
            if (target.key().equalsIgnoreCase(entry.category())) {
                ids.add(id);
            }
        });
        // Tri par prix croissant : la progression du joueur se lit dans la page,
        // et l'ordre reste stable d'une ouverture a l'autre.
        ids.sort(Comparator
                .<Identifier>comparingLong(id -> book.entry(id).map(PriceEntry::basePrice).orElse(0L))
                .thenComparing(Identifier::toString));
        return ids;
    }

    /** La vignette d'une offre : l'item reel, avec son tarif en description. */
    private ItemStack renderOffer(Identifier id) {
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        PriceEntry entry = Prices.book().entry(id).orElse(null);
        if (item == null || entry == null) {
            return ItemStack.EMPTY;
        }

        int owned = countOwned(item);
        List<Component> lore = new ArrayList<>();
        if (entry.sellable() && !CollectionApi.isUnlocked(player, id, entry)) {
            // Verrouille : on montre l'objectif plutot qu'un prix inaccessible.
            lore.add(Gui.bad("Vente verrouillee"));
            lore.add(Gui.hint("  collecte " + MoneyFormat.exact(
                    CollectionApi.missingToUnlock(player, id, entry)) + " de plus"));
        } else if (entry.sellable()) {
            long current = DynamicPrice.unitPrice(player, id, entry);
            int percent = (int) Math.round(100.0 * current / Math.max(1L, entry.basePrice()));
            lore.add(Gui.good("Vente : " + MoneyFormat.exact(current)));
            if (percent < 100) {
                // Le prix a baisse sous la pression des ventes passees : le dire,
                // sinon le joueur croit a un bug du catalogue.
                lore.add(Gui.hint("  " + percent
                        + " % du prix de base · remonte avec le temps de jeu"));
            }
        } else {
            lore.add(Gui.hint("Achat seul"));
        }
        if (entry.buyPrice() > 0L) {
            double discount = discount(player);
            long shown = Math.max(1L, Math.round(entry.buyPrice() * (1.0d - discount)));
            lore.add(Component.literal("Achat : " + MoneyFormat.exact(shown)
                            + (discount > 0 ? "  (-" + Math.round(discount * 100) + " %)" : ""))
                    .withStyle(ChatFormatting.RED));
        }
        lore.add(Component.literal("Tu en as : " + owned).withStyle(ChatFormatting.GRAY));

        long collected = CollectionApi.countOf(player, id);
        long next = CollectionApi.nextThreshold(player, id, entry);
        if (next > 0) {
            lore.add(Component.literal("Collection : " + MoneyFormat.exact(collected)
                            + " / " + MoneyFormat.exact(next))
                    .withStyle(ChatFormatting.DARK_AQUA));
        } else if (collected > 0) {
            lore.add(Component.literal("Collection complete").withStyle(ChatFormatting.AQUA));
        }

        int prestigeLevel = CollectionApi.prestigeLevel(player, id);
        if (prestigeLevel > 0) {
            lore.add(Component.literal("Prestige " + prestigeLevel + " · ecoulable x"
                            + String.format("%.1f", CollectionApi.s0Multiplier(player, id)))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (CollectionApi.canPrestige(player, id, entry)) {
            lore.add(Component.literal("Prestige disponible").withStyle(ChatFormatting.GOLD));
        }

        ItemStack stack = new ItemStack(item, Math.max(1, Math.min(64, owned)));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    /**
     * La remise d'achat : aile Composants du musee plus branche Negoce.
     *
     * <p>Bornee a 25 %. Au-dela, les prix NTM cesseraient d'etre le mur qu'ils
     * doivent etre — c'est toute la raison pour laquelle ils sont enormes.
     */
    private static double discount(ServerPlayer player) {
        return Math.min(0.25d, MuseumApi.buyDiscount(player)
                + UpgradeApi.bonus(player, UpgradeApi.SHOP_DISCOUNT));
    }

    private int countOwned(Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}

package fr.atraxterra.dohy.shop;

import fr.atraxterra.dohy.auction.AuctionController;
import fr.atraxterra.dohy.auction.AuctionHouse;
import fr.atraxterra.dohy.auction.AuctionSales;
import fr.atraxterra.dohy.boost.Boosters;
import fr.atraxterra.dohy.boost.Crates;
import fr.atraxterra.dohy.collection.CollectionApi;
import fr.atraxterra.dohy.economy.Bank;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.palamod.box.BoxKind;
import fr.atraxterra.dohy.palamod.box.PalaBoxes;
import fr.atraxterra.dohy.ui.menu.BoutiqueController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.ShopView;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Le commerce : un seul ecran pour tout ce qui s'achete et se vend, en quatre
 * onglets.
 *
 * <ul>
 *   <li><b>Acheter</b> (le shop, {@link ShopController}) : blocs, minerais,
 *       nourriture, pieces, a prix fixe ;</li>
 *   <li><b>Vendre</b> ({@link SellController}) : ce que le shop rachete, ce que
 *       l'hotel des ventes prend ;</li>
 *   <li><b>Boutique</b> ({@link BoutiqueController}) : boosters, cles de box,
 *       coffres a poser, potions d'XP, services ;</li>
 *   <li><b>Hotel des ventes</b> ({@link AuctionController}) : les raretes des
 *       vendeurs fictifs, et ce que le joueur met en vente.</li>
 * </ul>
 *
 * <p>Chaque onglet a son controleur et sa vue ; ils partagent le titre, la
 * rangee d'onglets et une phrase qui dit ce qu'on y trouve. C'etait le reproche
 * fait aux quatre entrees d'avant : on ne savait pas laquelle vendait quoi.
 */
public final class Commerce {

    public static final String TITLE = "Commerce";

    /** Les onglets, dans l'ordre de la rangee. */
    public enum Tab {
        BUY("shop", "Acheter", ""),
        SELL("sell", "Vendre", ""),
        BOUTIQUE("boutique", "Boutique", ""),
        AUCTION("auction", "Hotel des ventes", "");

        private final String path;
        private final String label;
        private final String subtitle;

        Tab(String path, String label, String subtitle) {
            this.path = path;
            this.label = label;
            this.subtitle = subtitle;
        }

        /** Le chemin de la vue de l'onglet ({@code dohy:shop}...). */
        public String path() {
            return path;
        }

        public String label() {
            return label;
        }

        /** La phrase sous le titre : ce qu'on trouve dans cet onglet. */
        public String subtitle() {
            return subtitle;
        }

        public String action() {
            return "tab:" + path;
        }

        public static Tab byPath(String path) {
            for (Tab tab : values()) {
                if (tab.path.equals(path)) {
                    return tab;
                }
            }
            return null;
        }
    }

    private Commerce() {
    }

    /** La rangee d'onglets, l'onglet courant allume ; les compteurs signalent ce qui attend le joueur. */
    public static List<MenuView.Tab> tabs(ServerPlayer player, Tab active) {
        List<MenuView.Tab> tabs = new ArrayList<>();
        for (Tab tab : Tab.values()) {
            String count = "";
            if (tab == Tab.SELL) {
                int kinds = sellableKinds(player);
                count = kinds > 0 ? String.valueOf(kinds) : "";
            } else if (tab == Tab.AUCTION) {
                int waiting = AuctionSales.unsold(player);
                count = waiting > 0 ? waiting + " A RECUPERER" : "";
            }
            tabs.add(new MenuView.Tab(tab.label(), tab.action(), tab == active, count));
        }
        return tabs;
    }

    /** Ouvre un onglet. */
    public static void open(ServerPlayer player, Tab tab) {
        switch (tab) {
            case BUY -> ShopController.open(player);
            case SELL -> SellController.INSTANCE.open(player);
            case BOUTIQUE -> BoutiqueController.INSTANCE.open(player);
            case AUCTION -> AuctionController.INSTANCE.open(player);
        }
    }

    /**
     * Un clic sur un onglet ({@code tab:chemin}) venu d'un ecran du commerce. Rend
     * {@code false} si ce n'est pas un onglet du commerce.
     */
    public static boolean openTab(ServerPlayer player, String action) {
        if (action == null || !action.startsWith("tab:")) {
            return false;
        }
        Tab tab = Tab.byPath(action.substring(4));
        if (tab == null) {
            return false;
        }
        open(player, tab);
        return true;
    }

    /** Combien de sortes d'objets du sac le shop rachete, vente ouverte. */
    static int sellableKinds(ServerPlayer player) {
        Set<Item> kinds = new HashSet<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty() || kinds.contains(stack.getItem())) {
                continue;
            }
            PriceEntry entry = Prices.book().entry(stack).orElse(null);
            if (entry != null && entry.sellable()
                    && CollectionApi.isUnlocked(player, BuiltInRegistries.ITEM.getKey(stack.getItem()), entry)) {
                kinds.add(stack.getItem());
            }
        }
        return kinds.size();
    }

    /**
     * Ce qui s'achete ailleurs qu'au shop, pour la recherche de l'onglet Acheter :
     * les annonces du jour de l'hotel des ventes et ce que vend la boutique. Une
     * recherche qui ne trouve rien au shop dit ainsi ou aller.
     */
    static List<ShopView.Elsewhere> elsewhere(ServerPlayer player) {
        List<ShopView.Elsewhere> out = new ArrayList<>();
        try {
            for (AuctionHouse.Listing listing : AuctionHouse.listings(player)) {
                if (AuctionHouse.remaining(player, listing) > 0) {
                    out.add(new ShopView.Elsewhere(listing.icon(), "", Tab.AUCTION.path(), Tab.AUCTION.label(),
                            money(listing.price()) + (listing.lot() > 1 ? " les " + listing.lot() : "")));
                }
            }
        } catch (RuntimeException ignored) {
            // La reserve n'est pas chargee : pas d'annonces a montrer.
        }
        for (Boosters.Tier tier : Boosters.TIERS) {
            out.add(new ShopView.Elsewhere(new ItemStack(BoutiqueController.boosterIcon(tier)), "Booster " + tier.key(),
                    Tab.BOUTIQUE.path(), Tab.BOUTIQUE.label(), money(tier.price())));
        }
        for (String tier : Crates.ordered()) {
            Crates.Crate crate = Crates.config().crates().get(tier);
            BoxKind kind = BoxKind.byId(tier);
            if (crate == null) {
                continue;
            }
            ItemStack key = kind != null ? new ItemStack(PalaBoxes.key(kind)) : new ItemStack(Items.TRIPWIRE_HOOK);
            out.add(new ShopView.Elsewhere(key, kind != null ? "" : "Cle " + Crates.title(tier), Tab.BOUTIQUE.path(),
                    Tab.BOUTIQUE.label(), money(crate.price())));
            if (kind != null) {
                out.add(new ShopView.Elsewhere(new ItemStack(PalaBoxes.item(kind)), "", Tab.BOUTIQUE.path(),
                        Tab.BOUTIQUE.label(), money(PalaBoxes.PRICE)));
            }
        }
        for (fr.atraxterra.dohy.palamod.boost.PalaXpPotions.Offer offer
                : fr.atraxterra.dohy.palamod.boost.PalaXpPotions.SHOP) {
            out.add(new ShopView.Elsewhere(fr.atraxterra.dohy.palamod.boost.PalaXpPotions.stack(offer.multiplier(),
                    offer.minutes()), "", Tab.BOUTIQUE.path(), Tab.BOUTIQUE.label(), money(offer.price())));
        }
        out.add(new ShopView.Elsewhere(new ItemStack(Items.TOTEM_OF_UNDYING), "Assurance", Tab.BOUTIQUE.path(),
                Tab.BOUTIQUE.label(), money(Bank.insurancePrice())));
        for (fr.atraxterra.dohy.grade.PlayerGrade grade : fr.atraxterra.dohy.grade.PlayerGrade.buyable()) {
            out.add(new ShopView.Elsewhere(fr.atraxterra.dohy.grade.DoHyGrades.badge(grade), "Grade " + grade.label(),
                    Tab.BOUTIQUE.path(), Tab.BOUTIQUE.label(), money(grade.price())));
        }
        out.add(new ShopView.Elsewhere(fr.atraxterra.dohy.grade.DoHyGrades.premiumBadge(), "Premium 30 jours",
                Tab.BOUTIQUE.path(), Tab.BOUTIQUE.label(), money(fr.atraxterra.dohy.grade.Premium.PRICE)));
        out.add(new ShopView.Elsewhere(new ItemStack(fr.atraxterra.dohy.farmbot.DoHyFarmBots.FARM_BOT_ITEM.get()),
                "Bot de farming", Tab.BOUTIQUE.path(), Tab.BOUTIQUE.label(),
                money(fr.atraxterra.dohy.farmbot.DoHyFarmBots.BOT_PRICE)));
        out.add(new ShopView.Elsewhere(new ItemStack(fr.atraxterra.dohy.farmbot.DoHyFarmBots.BOT_REMOTE.get()),
                "Telecommande de bot", Tab.BOUTIQUE.path(), Tab.BOUTIQUE.label(),
                money(fr.atraxterra.dohy.farmbot.DoHyFarmBots.REMOTE_PRICE)));
        return out;
    }

    static String money(long amount) {
        return MoneyFormat.exact(amount) + " $";
    }
}

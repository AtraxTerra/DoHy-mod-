package fr.atraxterra.dohy.auction;

import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.shop.Commerce;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'onglet Hotel des ventes du commerce : ce que le joueur a mis en vente, les
 * annonces du jour des vendeurs fictifs, et la mise en vente en deux etapes
 * (l'objet, puis le prix).
 */
public final class AuctionController extends MenuController {

    public static final AuctionController INSTANCE = new AuctionController();

    private static final int DEAL = 0xFF22E558;
    private static final int FAIR = 0xFF2F5BEA;
    private static final int GREEDY = 0xFFEF3826;
    private static final int GOLD = 0xFFFAC400;
    private static final int GREY = 0xFF55575D;

    private static final int MAIN = 0;
    private static final int PICK = 1;
    private static final int PRICE = 2;

    /** L'etape de chaque joueur : la page principale, le choix de l'objet, le choix du prix. */
    private record Page(int step, int slot, Item item) {
        static final Page HOME = new Page(MAIN, -1, null);
    }

    private static final Map<UUID, Page> PAGES = new ConcurrentHashMap<>();

    private AuctionController() {
        super("auction");
    }

    /** Ouvre la page principale. */
    @Override
    public void open(ServerPlayer player) {
        PAGES.remove(player.getUUID());
        super.open(player);
    }

    /**
     * Ouvre directement le choix du prix pour un objet du sac (depuis l'onglet
     * Vendre) : la pile la plus grosse de cet objet.
     */
    public void openPrice(ServerPlayer player, Item item) {
        NonNullList<ItemStack> bag = AuctionSales.bag(player);
        int best = -1;
        for (int slot = 0; slot < bag.size(); slot++) {
            ItemStack stack = bag.get(slot);
            if (stack.is(item) && AuctionSales.accepts(stack)
                    && (best < 0 || stack.getCount() > bag.get(best).getCount())) {
                best = slot;
            }
        }
        PAGES.put(player.getUUID(), best < 0 ? new Page(PICK, -1, null) : new Page(PRICE, best, item));
        super.open(player);
    }

    private static Page page(ServerPlayer player) {
        return PAGES.getOrDefault(player.getUUID(), Page.HOME);
    }

    @Override
    protected void back(ServerPlayer player) {
        if (page(player).step() == MAIN) {
            super.back(player);
            return;
        }
        PAGES.remove(player.getUUID());
        UiNet.show(player, view(player));
    }

    @Override
    public MenuView view(ServerPlayer player) {
        AuctionSales.settle(player, false);
        Page page = page(player);
        if (page.step() == PRICE && !samePile(player, page)) {
            // La pile a bouge ou disparu entre-temps : on repart du choix de l'objet.
            page = new Page(PICK, -1, null);
            PAGES.put(player.getUUID(), page);
        }
        long balance = Economy.balance(player);
        int slots = AuctionSales.slots(player);
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("TON ARGENT", money(balance), 0xFFFFFFFF),
                new MenuView.Stat("TES ANNONCES", AuctionSales.used(player) + " / " + slots, GOLD));
        List<MenuView.Section> sections;
        String subtitle;
        switch (page.step()) {
            case PICK -> {
                sections = pickSections(player);
                subtitle = "Choisis l'objet a vendre : les acheteurs cherchent les memes raretes que celles qui sont "
                        + "en vente ici.";
            }
            case PRICE -> {
                sections = priceSections(player, page);
                subtitle = "Choisis ton prix : moins c'est cher, plus ca part vite. Invendu au bout de "
                        + Math.round(AuctionSales.buyers().hours()) + " h, l'objet te revient.";
            }
            default -> {
                sections = mainSections(player, balance);
                subtitle = Commerce.Tab.AUCTION.subtitle();
            }
        }
        return new MenuView(id(), Commerce.TITLE, subtitle, stats, sections, page.step() != MAIN)
                .withTabs(Commerce.tabs(player, Commerce.Tab.AUCTION));
    }

    // -- La page principale ---------------------------------------------------------------

    private static List<MenuView.Section> mainSections(ServerPlayer player, long balance) {
        List<MenuView.Section> sections = new ArrayList<>();

        // Tes ventes : les annonces du joueur, puis la carte pour en poser une.
        List<Card> mine = new ArrayList<>();
        for (AuctionData.Sale sale : AuctionSales.sales(player)) {
            mine.add(saleCard(sale));
        }
        int free = AuctionSales.free(player);
        int sellable = sellableStacks(player).size();
        Card list = Card.of(new ItemStack(Items.NAME_TAG), "Mettre en vente", List.of(
                        free > 0 ? free + (free > 1 ? " emplacements libres" : " emplacement libre")
                                : "Tous tes emplacements sont pris",
                        sellable > 0 ? sellable + (sellable > 1 ? " objets vendables dans ton sac"
                                : " objet vendable dans ton sac") : "Rien a vendre ici dans ton sac"))
                .accent(DEAL)
                .tooltip(List.of("Les acheteurs prennent les memes raretes que celles en vente ici :",
                        "runes, essences, equipement de donjon, pierres legendaires, cles...",
                        "Ce que le shop rachete se vend dans l'onglet Vendre"));
        mine.add(free > 0 && sellable > 0 ? list.action("pick|choisir un objet") : list.state(MenuView.DISABLED));
        sections.add(new MenuView.Section("TES VENTES", "upgrade", MenuView.GRID, mine));

        // Les annonces du jour, en une seule section.
        List<Card> cards = new ArrayList<>();
        for (AuctionHouse.Listing listing : AuctionHouse.listings(player)) {
            cards.add(listingCard(player, listing, balance));
        }
        long minutes = ContractApi.minutesUntilDailyReset();
        sections.add(new MenuView.Section("ANNONCES DU JOUR|nouvelles dans " + minutes / 60 + " h "
                + String.format("%02d", minutes % 60), "resources", MenuView.GRID, cards));

        long reroll = AuctionHouse.rerollPrice(player);
        Card refresh = Card.of(new ItemStack(Items.BELL), "Nouvelles annonces", List.of(
                        reroll < 0L ? "Plus rien de neuf aujourd'hui" : money(reroll),
                        "Remplace toutes les annonces du jour"))
                .accent(GOLD)
                .tooltip(List.of("D'autres vendeurs, d'autres prix",
                        "Le prix double a chaque fois dans la journee",
                        "Les annonces changent toutes seules chaque jour",
                        reroll > balance ? "Il te manque " + money(reroll - balance) : ""));
        refresh = reroll < 0L || reroll > balance ? refresh.state(MenuView.DISABLED)
                : refresh.action("reroll|payer");
        sections.add(new MenuView.Section("PLUS D'ANNONCES", "craft", MenuView.GRID, List.of(refresh)));
        return sections;
    }

    private static Card saleCard(AuctionData.Sale sale) {
        ItemStack icon = sale.stack().isEmpty() ? new ItemStack(Items.BARRIER) : sale.stack().copy();
        String title = sale.stack().isEmpty() ? "Objet disparu" : label(sale.stack());
        AuctionConfig.Preset preset = AuctionSales.buyers().preset(sale.preset());
        long now = System.currentTimeMillis();
        return switch (sale.state()) {
            case AuctionData.Sale.SOLD -> Card.of(icon, title, List.of("Vendu " + money(sale.price()),
                            "L'argent est sur ton compte"))
                    .state(MenuView.DONE).badge("VENDU")
                    .tooltip(List.of("Clic : effacer de la liste", "Elle s'efface toute seule apres une journee"))
                    .action("clear:" + sale.id() + "|effacer");
            case AuctionData.Sale.UNSOLD -> Card.of(icon, title, List.of("Personne n'en a voulu",
                            "Clic : le recuperer"))
                    .accent(GOLD).badge("A RECUPERER")
                    .tooltip(List.of("Il attend ici, rien ne se perd",
                            "Remets-le en vente moins cher : il partira plus vite"))
                    .action("claim:" + sale.id() + "|recuperer");
            default -> {
                long hours = Math.max(0L, (now - sale.listedAt()) / 3_600_000L);
                long left = Math.max(0L, Math.round(AuctionSales.buyers().hours()) - hours);
                yield Card.of(icon, title, List.of("En vente : " + money(sale.price()),
                                preset == null ? "En attente d'un acheteur" : AuctionSales.estimate(preset)))
                        .accent(FAIR).badge("EN VENTE")
                        .tooltip(List.of("Mise en vente il y a " + (hours > 0 ? hours + " h" : "moins d'une heure"),
                                "Invendue dans " + left + " h, elle te revient",
                                "Clic : la retirer, l'objet revient dans ton sac"))
                        .action("cancel:" + sale.id() + "|retirer")
                        .confirm("Retirer cette annonce ?|L'objet revient dans ton sac.");
            }
        };
    }

    private static Card listingCard(ServerPlayer player, AuctionHouse.Listing listing, long balance) {
        ItemStack icon = listing.icon();
        int left = AuctionHouse.remaining(player, listing);
        String title = MenuView.text(new ItemStack(listing.item()).getHoverName());
        int percent = (int) Math.round((listing.factor() - 1.0d) * 100.0d);
        boolean deal = percent <= -8;
        boolean greedy = percent >= 25;
        String second = left <= 0 ? "Plus en stock" : deal ? "Bonne affaire"
                : left > 1 ? left + " lots en stock" : "Dernier lot";
        List<String> tooltip = new ArrayList<>();
        tooltip.add("Vendu par " + listing.seller());
        if (listing.lot() > 1) {
            tooltip.add(listing.lot() + " objets par lot, " + money(listing.price() / listing.lot()) + " l'unite");
        }
        tooltip.add(deal ? "Bonne affaire : " + (-percent) + " % sous le prix habituel"
                : greedy ? "Vendeur gourmand : " + percent + " % au-dessus du prix habituel"
                : percent == 0 ? "Au prix habituel"
                : percent > 0 ? percent + " % au-dessus du prix habituel" : (-percent) + " % sous le prix habituel");
        if (left > 0 && listing.price() > balance) {
            tooltip.add("Il te manque " + money(listing.price() - balance));
        }
        Card card = Card.of(icon, title, List.of(
                        money(listing.price()) + (listing.lot() > 1 ? " les " + listing.lot() : ""), second))
                .accent(deal ? DEAL : greedy ? GREEDY : FAIR)
                .badge(left <= 0 ? "VENDU" : deal ? "-" + (-percent) + " %" : "")
                .tooltip(tooltip);
        if (left <= 0) {
            return card.state(MenuView.DONE);
        }
        if (listing.price() > balance) {
            return card.state(MenuView.DISABLED);
        }
        return card.action("buy:" + listing.index() + "|acheter");
    }

    // -- Le choix de l'objet --------------------------------------------------------------

    /** Les emplacements du sac que les acheteurs prennent, les plus chers d'abord. */
    private static List<Integer> sellableStacks(ServerPlayer player) {
        NonNullList<ItemStack> bag = AuctionSales.bag(player);
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < bag.size(); slot++) {
            if (AuctionSales.accepts(bag.get(slot))) {
                slots.add(slot);
            }
        }
        slots.sort(Comparator.comparingLong(slot -> -AuctionSales.recommended(bag.get(slot))));
        return slots;
    }

    private static List<MenuView.Section> pickSections(ServerPlayer player) {
        NonNullList<ItemStack> bag = AuctionSales.bag(player);
        AuctionConfig.Preset fair = AuctionSales.buyers().preset("fair");
        List<Card> cards = new ArrayList<>();
        for (int slot : sellableStacks(player)) {
            ItemStack stack = bag.get(slot);
            cards.add(Card.of(stack.copy(), label(stack), List.of("Prix conseille : " + money(AuctionSales.recommended(stack)),
                            fair == null ? "" : AuctionSales.estimate(fair)))
                    .accent(FAIR)
                    .tooltip(List.of("Clic : choisir le prix", stack.getCount() > 1
                            ? "Toute la pile part dans la meme annonce" : ""))
                    .action("pick:" + slot + "|choisir"));
        }
        if (cards.isEmpty()) {
            cards.add(Card.of(new ItemStack(Items.BARRIER), "Rien a vendre ici", List.of(
                    "Aucune rarete dans ton sac", "Ce que le shop rachete : onglet Vendre")).state(MenuView.DISABLED));
        }
        cards.add(cancel());
        return List.of(new MenuView.Section("QUEL OBJET ?", "resources", MenuView.GRID, cards));
    }

    // -- Le choix du prix -----------------------------------------------------------------

    private static boolean samePile(ServerPlayer player, Page page) {
        NonNullList<ItemStack> bag = AuctionSales.bag(player);
        return page.slot() >= 0 && page.slot() < bag.size() && page.item() != null
                && bag.get(page.slot()).is(page.item()) && AuctionSales.accepts(bag.get(page.slot()));
    }

    private static List<MenuView.Section> priceSections(ServerPlayer player, Page page) {
        ItemStack stack = AuctionSales.bag(player).get(page.slot());
        List<Card> cards = new ArrayList<>();
        for (AuctionConfig.Preset preset : AuctionSales.buyers().presets()) {
            long price = AuctionSales.price(stack, preset);
            boolean fair = preset.factor() == 1.0d;
            int accent = preset.factor() < 1.0d ? DEAL : fair ? FAIR : GREEDY;
            cards.add(Card.of(stack.copy(), preset.label(), List.of(money(price), AuctionSales.estimate(preset)))
                    .accent(accent).badge(fair ? "CONSEILLE" : "")
                    .tooltip(List.of("Tu recois " + money(price) + " quand il part",
                            "Invendu au bout de " + Math.round(AuctionSales.buyers().hours())
                                    + " h : il te revient",
                            "Tu peux retirer l'annonce a tout moment"))
                    .action("sell:" + page.slot() + ":" + preset.id() + "|mettre en vente"));
        }
        cards.add(cancel());
        // L'objet en tete, pour rappeler ce qu'on vend : le nom se traduit chez le client.
        cards.addFirst(Card.of(stack.copy(), label(stack), List.of("Prix conseille : "
                        + money(AuctionSales.recommended(stack)), "Choisis un prix a droite"))
                .accent(GREY).tooltip(List.of("Toute la pile part dans la meme annonce")));
        return List.of(new MenuView.Section("A QUEL PRIX ?", "upgrade", MenuView.GRID, cards));
    }

    private static Card cancel() {
        return Card.of(new ItemStack(Items.ARROW), "Retour", List.of("Revenir a l'hotel des ventes", ""))
                .accent(GREY).action("home|revenir");
    }

    // -- Les clics ------------------------------------------------------------------------

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (Commerce.openTab(player, key)) {
            return;
        }
        UUID uuid = player.getUUID();
        if (key.equals("home")) {
            PAGES.remove(uuid);
        } else if (key.equals("pick")) {
            PAGES.put(uuid, new Page(PICK, -1, null));
        } else if (key.startsWith("pick:")) {
            int slot = number(key.substring(5));
            NonNullList<ItemStack> bag = AuctionSales.bag(player);
            if (slot >= 0 && slot < bag.size() && AuctionSales.accepts(bag.get(slot))) {
                PAGES.put(uuid, new Page(PRICE, slot, bag.get(slot).getItem()));
            }
        } else if (key.startsWith("sell:")) {
            String[] parts = key.split(":");
            Page page = page(player);
            int slot = parts.length == 3 ? number(parts[1]) : -1;
            if (page.step() != PRICE || slot != page.slot() || !samePile(player, page)) {
                report(player, "L'objet a change de place : choisis-le a nouveau.", "");
                PAGES.put(uuid, new Page(PICK, -1, null));
                return;
            }
            ItemStack stack = AuctionSales.bag(player).get(slot);
            Component name = Component.empty().append(stack.getHoverName())
                    .append(stack.getCount() > 1 ? " x" + stack.getCount() : "");
            String refusal = AuctionSales.list(player, slot, parts[2]);
            if (refusal != null) {
                report(player, refusal, "");
                return;
            }
            success(player);
            player.sendSystemMessage(Component.literal("Mis en vente : ").append(name).withStyle(ChatFormatting.GREEN));
            PAGES.remove(uuid);
        } else if (key.startsWith("cancel:")) {
            report(player, AuctionSales.cancel(player, saleId(key.substring(7))), "Annonce retiree : l'objet est "
                    + "dans ton sac.");
        } else if (key.startsWith("claim:")) {
            report(player, AuctionSales.claim(player, saleId(key.substring(6))), "Objet recupere.");
        } else if (key.startsWith("clear:")) {
            AuctionSales.clear(player, saleId(key.substring(6)));
        } else if (key.startsWith("buy:")) {
            int index = number(key.substring(4));
            List<AuctionHouse.Listing> listings = AuctionHouse.listings(player);
            Component name = index >= 0 && index < listings.size() ? name(listings.get(index)) : Component.empty();
            String refusal = AuctionHouse.buy(player, index);
            if (refusal != null) {
                report(player, refusal, "");
            } else {
                success(player);
                player.sendSystemMessage(Component.literal("Achete a l'hotel des ventes : ").append(name)
                        .withStyle(ChatFormatting.GREEN));
            }
        } else if (key.equals("reroll")) {
            report(player, AuctionHouse.reroll(player), "De nouveaux vendeurs sont passes.");
        }
    }

    /** « Fragment de rune commune x4 », pour le tchat : le nom se traduit chez le client. */
    private static Component name(AuctionHouse.Listing listing) {
        Component name = new ItemStack(listing.item()).getHoverName();
        return listing.lot() > 1 ? Component.empty().append(name).append(" x" + listing.lot()) : name;
    }

    /** Le nom d'une pile et sa quantite, traduits chez le client. */
    static String label(ItemStack stack) {
        Component name = stack.getHoverName();
        return MenuView.text(stack.getCount() > 1 ? Component.empty().append(name).append(" x" + stack.getCount())
                : name);
    }

    private static long saleId(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException error) {
            return -1L;
        }
    }

    private static int number(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    private static String money(long amount) {
        return MoneyFormat.exact(amount) + " $";
    }
}

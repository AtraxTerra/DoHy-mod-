package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Les ecrans des donjons, dans le kit d'interface : le choix du palier,
 * l'autel de boost, la marchande et le sac.
 */
public final class DungeonMenus {

    private DungeonMenus() {
    }

    public static void openPaliers(ServerPlayer player, Dungeon dungeon, InteractionHand hand) {
        Paliers.INSTANCE.context.put(player.getUUID(), new PaliersContext(dungeon, hand));
        Paliers.INSTANCE.open(player);
    }

    static void openBoost(ServerPlayer player, DungeonRun run) {
        BoostAltar.INSTANCE.runs.put(player.getUUID(), run);
        BoostAltar.INSTANCE.open(player);
    }

    static void openMerchant(ServerPlayer player, DungeonRun run) {
        Merchant.INSTANCE.runs.put(player.getUUID(), run);
        Merchant.INSTANCE.open(player);
    }

    static void openBag(ServerPlayer player, DungeonRun run) {
        Bag.INSTANCE.runs.put(player.getUUID(), run);
        Bag.INSTANCE.open(player);
    }

    private static int color(ChatFormatting format) {
        return MenuView.color(format);
    }

    // -- Choix du palier --------------------------------------------------------

    private record PaliersContext(Dungeon dungeon, InteractionHand hand) {
    }

    /**
     * Les cent paliers en vignettes. Vert : reussi, rejouable. Jaune : le
     * suivant. Gris : verrouille. Les paliers en 5 et en 0 portent un crane.
     */
    public static final class Paliers extends MenuController {
        public static final Paliers INSTANCE = new Paliers();
        final Map<UUID, PaliersContext> context = new ConcurrentHashMap<>();

        private Paliers() {
            super("dungeon_paliers");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            PaliersContext here = context.get(player.getUUID());
            Dungeon dungeon = here == null ? Dungeon.values()[0] : here.dungeon();
            int cleared = DungeonManager.cleared(player, dungeon);
            List<ServerPlayer> party = DungeonManager.party(player);
            List<String> members = new ArrayList<>();
            for (ServerPlayer member : party) {
                members.add("- " + member.getDisplayName().getString());
            }
            members.add("Les joueurs a 6 blocs de vous entrent avec vous.");
            List<Card> info = List.of(
                    Card.of(new ItemStack(Items.PLAYER_HEAD), "Groupe", List.of(
                                    party.size() + " / " + DungeonManager.MAX_PARTY + " joueur(s)", "A 6 blocs de vous"))
                            .accent(color(dungeon.color())).tooltip(members),
                    Card.of(new ItemStack(Items.BOOK), "Regles", List.of("Mort ou sortie : palier echoue",
                                    "La cle est consommee a l'entree"))
                            .accent(color(dungeon.color()))
                            .tooltip(List.of("Le sac est perdu, l'inventaire est garde.")));
            List<Card> tiles = new ArrayList<>();
            for (int palier = 1; palier <= DungeonManager.MAX_PALIER; palier++) {
                boolean done = palier <= cleared;
                boolean next = palier == cleared + 1;
                BossSpec boss = dungeon.bossFor(palier);
                ItemStack icon = boss != null
                        ? new ItemStack(palier % 10 == 0 ? Items.WITHER_SKELETON_SKULL : Items.SKELETON_SKULL)
                        : new ItemStack(Items.STAINED_GLASS_PANE.pick(done ? DyeColor.LIME
                                : next ? DyeColor.YELLOW : DyeColor.GRAY));
                List<String> lines = new ArrayList<>();
                lines.add("Difficulte : " + Band.of(palier).label());
                if (boss != null) {
                    lines.add((palier % 10 == 0 ? "Boss : " : "Mini-boss : ") + boss.name());
                }
                Card card = Card.of(icon, "Palier " + palier, lines).badge(String.valueOf(palier));
                if (done) {
                    card = card.state(MenuView.DONE).action("palier:" + palier + "|rejouer");
                } else if (next) {
                    card = card.accent(0xFFFAC400).action("palier:" + palier + "|lancer le palier");
                } else {
                    card = card.state(MenuView.DISABLED)
                            .tooltip(List.of("Verrouille : reussissez le palier " + (cleared + 1)));
                }
                tiles.add(card);
            }
            List<MenuView.Stat> stats = List.of(
                    new MenuView.Stat("METIER", dungeon.job().display().toUpperCase(), 0xFFFFFFFF),
                    new MenuView.Stat("DERNIER REUSSI", cleared == 0 ? "-" : String.valueOf(cleared), 0xFF22E558));
            return new MenuView(id(), "Donjon " + dungeon.display(), "Choisis ton palier", stats, List.of(
                    new MenuView.Section("AVANT D'ENTRER", "resources", MenuView.GRID, info),
                    new MenuView.Section("PALIERS", "upgrade", MenuView.TILES, tiles)), false);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            PaliersContext here = context.get(player.getUUID());
            if (here == null || !key.startsWith("palier:")) {
                return;
            }
            int palier;
            try {
                palier = Integer.parseInt(key.substring(7));
            } catch (NumberFormatException malformed) {
                return;
            }
            if (palier < 1 || palier > DungeonManager.MAX_PALIER
                    || palier > DungeonManager.cleared(player, here.dungeon()) + 1) {
                failure(player);
                return;
            }
            UiNet.close(player);
            if (DungeonManager.start(player, here.dungeon(), palier, here.hand())) {
                success(player);
            } else {
                failure(player);
            }
        }
    }

    // -- Autel de boost -----------------------------------------------------------

    public static final class BoostAltar extends MenuController {
        public static final BoostAltar INSTANCE = new BoostAltar();
        final Map<UUID, DungeonRun> runs = new ConcurrentHashMap<>();

        private BoostAltar() {
            super("dungeon_boost");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            DungeonRun run = runs.get(player.getUUID());
            List<Card> cards = new ArrayList<>();
            if (run != null) {
                boolean taken = run.boosted.contains(player.getUUID());
                for (int index = 0; index < run.boostOptions.size(); index++) {
                    Boost boost = run.boostOptions.get(index);
                    Card card = Card.of(new ItemStack(boost.icon()), boost.label(), List.of(
                            "Pendant 10 minutes", "Jusqu'a la fin du palier")).accent(0xFF22E558);
                    cards.add(taken ? card.state(MenuView.DISABLED).tooltip(List.of("Bonus deja choisi"))
                            : card.action("boost:" + index + "|choisir"));
                }
            }
            return new MenuView(id(), "Autel de boost", "Un seul bonus par joueur", List.of(),
                    List.of(new MenuView.Section("BONUS", "upgrade", MenuView.GRID, cards)), false);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            DungeonRun run = runs.get(player.getUUID());
            if (run == null || !key.startsWith("boost:")) {
                return;
            }
            int index = Integer.parseInt(key.substring(6));
            if (index < 0 || index >= run.boostOptions.size()) {
                return;
            }
            Boost boost = run.boostOptions.get(index);
            if (run.takeBoost(player, boost)) {
                success(player);
                DungeonManager.message(player, ChatFormatting.GREEN, "boost_taken", boost.label());
                UiNet.close(player);
            } else {
                failure(player);
                DungeonManager.message(player, ChatFormatting.RED, "boost_already");
            }
        }
    }

    // -- Marchande ----------------------------------------------------------------

    /**
     * Les offres de la marchande. Le wiki ne donne ni monnaie ni stock : ici,
     * l'argent de DoHy, et de quoi tenir jusqu'au bout du palier. Les prix
     * croissent de {@code palier / 50}.
     */
    private enum Offer {
        GOLDEN_APPLE("Pomme doree", 1_500, () -> new ItemStack(Items.GOLDEN_APPLE)),
        STEAK("Steak x16", 300, () -> new ItemStack(Items.COOKED_BEEF, 16)),
        HEALING("Potion de soin II jetable", 800,
                () -> PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_HEALING)),
        REGENERATION("Potion de regeneration", 600,
                () -> PotionContents.createItemStack(Items.POTION, Potions.REGENERATION)),
        ARROWS("Fleches x32", 200, () -> new ItemStack(Items.ARROW, 32)),
        PEARLS("Perles de l'Ender x4", 400, () -> new ItemStack(Items.ENDER_PEARL, 4)),
        TOTEM("Totem d'immortalite", 20_000, () -> new ItemStack(Items.TOTEM_OF_UNDYING));

        private final String label;
        private final long price;
        private final Supplier<ItemStack> stack;

        Offer(String label, long price, Supplier<ItemStack> stack) {
            this.label = label;
            this.price = price;
            this.stack = stack;
        }

        long price(int palier) {
            return Math.round(price * (1.0d + palier / 50.0d));
        }
    }

    public static final class Merchant extends MenuController {
        public static final Merchant INSTANCE = new Merchant();
        final Map<UUID, DungeonRun> runs = new ConcurrentHashMap<>();

        private Merchant() {
            super("dungeon_merchant");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            DungeonRun run = runs.get(player.getUUID());
            int palier = run == null ? 1 : run.palier;
            long balance = Economy.balance(player);
            List<Card> cards = new ArrayList<>();
            for (Offer offer : Offer.values()) {
                long price = offer.price(palier);
                Card card = Card.of(offer.stack.get(), offer.label, List.of(MoneyFormat.exact(price) + " $",
                        balance >= price ? "Disponible" : "Pas assez d'argent")).accent(0xFFFAC400);
                cards.add(balance >= price ? card.action("buy:" + offer.name() + "|acheter")
                        : card.state(MenuView.DISABLED));
            }
            return new MenuView(id(), "Marchande", "Les prix montent avec le palier",
                    List.of(new MenuView.Stat("VOTRE ARGENT", MoneyFormat.shortForm(balance), 0xFFFFFFFF)),
                    List.of(new MenuView.Section("OFFRES", "resources", MenuView.GRID, cards)), false);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
            DungeonRun run = runs.get(player.getUUID());
            if (run == null || !key.startsWith("buy:")) {
                return;
            }
            Offer offer;
            try {
                offer = Offer.valueOf(key.substring(4));
            } catch (IllegalArgumentException unknown) {
                return;
            }
            long price = offer.price(run.palier);
            if (!Economy.tryDebit(player, price)) {
                failure(player);
                DungeonManager.message(player, ChatFormatting.RED, "poor", MoneyFormat.exact(price) + " $");
                return;
            }
            ItemStack bought = offer.stack.get();
            if (!player.getInventory().add(bought) && !bought.isEmpty()) {
                player.drop(bought, false);
            }
            success(player);
            DungeonManager.message(player, ChatFormatting.GREEN, "bought", offer.label, MoneyFormat.exact(price) + " $");
        }
    }

    // -- Sac ------------------------------------------------------------------------

    public static final class Bag extends MenuController {
        public static final Bag INSTANCE = new Bag();
        final Map<UUID, DungeonRun> runs = new ConcurrentHashMap<>();

        private Bag() {
            super("dungeon_bag");
        }

        @Override
        public MenuView view(ServerPlayer player) {
            DungeonRun run = runs.get(player.getUUID());
            List<ItemStack> bag = run == null ? List.of() : run.bags.getOrDefault(player.getUUID(), List.of());
            int capacity = run == null ? 0 : run.capacity(player.getUUID());
            List<Card> tiles = new ArrayList<>();
            for (int slot = 0; slot < 36; slot++) {
                if (slot < bag.size()) {
                    ItemStack stack = bag.get(slot).copy();
                    tiles.add(Card.of(stack, MenuView.text(stack.getHoverName()), List.of("x" + stack.getCount()))
                            .badge(stack.getCount() > 1 ? String.valueOf(stack.getCount()) : ""));
                } else if (slot >= capacity) {
                    tiles.add(Card.of(new ItemStack(Items.STAINED_GLASS_PANE.pick(DyeColor.BLACK)),
                            "Emplacement ferme", List.of("Un meilleur sac de donjon l'ouvre"))
                            .state(MenuView.DISABLED));
                } else {
                    tiles.add(Card.of(ItemStack.EMPTY, "Emplacement libre", List.of()));
                }
            }
            return new MenuView(id(), "Sac de donjon", "Livre a la fin du palier reussi, perdu sinon",
                    List.of(new MenuView.Stat("PILES", bag.size() + " / " + capacity, 0xFFFFFFFF)),
                    List.of(new MenuView.Section("", "", MenuView.TILES, tiles)), false);
        }

        @Override
        protected void card(ServerPlayer player, String key, int button) {
        }
    }
}

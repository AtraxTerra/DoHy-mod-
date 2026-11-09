package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.DisplayMenu;
import fr.atraxterra.dohy.ui.Gui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Les ecrans des donjons, en coffres comme le reste de DoHy : le choix du
 * palier, l'autel de boost, la marchande et le sac.
 */
public final class DungeonMenus {

    private DungeonMenus() {
    }

    public static void openPaliers(ServerPlayer player, Dungeon dungeon, InteractionHand hand) {
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new Paliers(id, inventory, player, dungeon,
                hand), Component.literal("Donjon " + dungeon.display())));
    }

    static void openBoost(ServerPlayer player, DungeonRun run) {
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new BoostMenu(id, inventory, player, run),
                Component.literal("Autel de boost")));
    }

    static void openMerchant(ServerPlayer player, DungeonRun run) {
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new Merchant(id, inventory, player, run),
                Component.literal("Marchande")));
    }

    static void openBag(ServerPlayer player, DungeonRun run) {
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new Bag(id, inventory, player, run),
                Component.literal("Sac de donjon")));
    }

    // -- Choix du palier --------------------------------------------------------

    /**
     * Les cent paliers, quarante-cinq par page. Vert : reussi, rejouable.
     * Jaune : le suivant. Gris : verrouille. Les paliers en 5 et en 0
     * portent un crane — mini-boss et boss.
     */
    private static final class Paliers extends DisplayMenu {

        private static final int PER_PAGE = 45;
        private static final int PAGES = (DungeonManager.MAX_PALIER + PER_PAGE - 1) / PER_PAGE;
        private static final int INFO = 45;

        private final Dungeon dungeon;
        private final InteractionHand hand;
        private int page;

        Paliers(int containerId, Inventory inventory, ServerPlayer player, Dungeon dungeon, InteractionHand hand) {
            super(containerId, inventory, player, 6);
            this.dungeon = dungeon;
            this.hand = hand;
            int next = Math.min(DungeonManager.MAX_PALIER, DungeonManager.cleared(player, dungeon) + 1);
            this.page = (next - 1) / PER_PAGE;
            refresh();
        }

        @Override
        protected void build() {
            int cleared = DungeonManager.cleared(player, dungeon);
            for (int slot = 0; slot < PER_PAGE; slot++) {
                int palier = page * PER_PAGE + slot + 1;
                if (palier > DungeonManager.MAX_PALIER) {
                    break;
                }
                set(slot, palierIcon(palier, cleared));
            }
            footer(INFO, Gui.FOOTER_PREVIOUS, Gui.FOOTER_CLOSE, Gui.FOOTER_NEXT);
            List<ServerPlayer> party = DungeonManager.party(player);
            List<Component> lore = new ArrayList<>();
            lore.add(Gui.field("Metier", dungeon.job().display()));
            lore.add(Gui.field("Dernier palier reussi", cleared == 0 ? "aucun" : String.valueOf(cleared)));
            lore.add(Gui.blank());
            lore.add(Gui.hint("Groupe (" + party.size() + "/" + DungeonManager.MAX_PARTY + ") :"));
            for (ServerPlayer member : party) {
                lore.add(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(member.getDisplayName().copy().withStyle(ChatFormatting.WHITE)));
            }
            lore.add(Gui.hint("Les joueurs a 6 blocs de vous entrent avec vous."));
            lore.add(Gui.blank());
            lore.add(Gui.hint("Mort ou sortie : le palier est echoue,"));
            lore.add(Gui.hint("le sac est perdu, l'inventaire est garde."));
            lore.add(Gui.hint("La cle est consommee a l'entree."));
            set(INFO, Gui.button(Items.BOOK, Component.literal("Donjon " + dungeon.display())
                    .withStyle(dungeon.color(), ChatFormatting.BOLD), lore));
            if (page > 0) {
                set(Gui.FOOTER_PREVIOUS, Gui.previousPage(page, PAGES));
            }
            set(Gui.FOOTER_CLOSE, Gui.close());
            if (page < PAGES - 1) {
                set(Gui.FOOTER_NEXT, Gui.nextPage(page + 2, PAGES));
            }
        }

        private ItemStack palierIcon(int palier, int cleared) {
            boolean done = palier <= cleared;
            boolean next = palier == cleared + 1;
            BossSpec boss = dungeon.bossFor(palier);
            Band band = Band.of(palier);
            Item item;
            if (boss != null) {
                item = palier % 10 == 0 ? Items.WITHER_SKELETON_SKULL : Items.SKELETON_SKULL;
            } else if (done) {
                item = Items.LIME_STAINED_GLASS_PANE;
            } else if (next) {
                item = Items.YELLOW_STAINED_GLASS_PANE;
            } else {
                item = Items.GRAY_STAINED_GLASS_PANE;
            }
            ChatFormatting color = done ? ChatFormatting.GREEN : next ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
            List<Component> lore = new ArrayList<>();
            lore.add(Gui.field("Difficulte", band.label()));
            if (boss != null) {
                lore.add(Gui.field(palier % 10 == 0 ? "Boss" : "Mini-boss", boss.name()));
            }
            lore.add(Gui.blank());
            if (done) {
                lore.add(Gui.good("Reussi"));
                lore.add(Gui.action("Clic : rejouer"));
            } else if (next) {
                lore.add(Gui.action("Clic : lancer le palier"));
            } else {
                lore.add(Gui.bad("Verrouille : reussissez le palier " + (cleared + 1)));
            }
            return Gui.button(item, Component.literal("Palier " + palier).withStyle(color, ChatFormatting.BOLD), lore);
        }

        @Override
        protected void onClick(int slot, int button, boolean shift) {
            if (slot == Gui.FOOTER_CLOSE) {
                player.closeContainer();
                return;
            }
            if (slot == Gui.FOOTER_PREVIOUS && page > 0) {
                page--;
                click();
                return;
            }
            if (slot == Gui.FOOTER_NEXT && page < PAGES - 1) {
                page++;
                click();
                return;
            }
            if (slot < 0 || slot >= PER_PAGE) {
                return;
            }
            int palier = page * PER_PAGE + slot + 1;
            if (palier > DungeonManager.MAX_PALIER) {
                return;
            }
            if (palier > DungeonManager.cleared(player, dungeon) + 1) {
                failure();
                return;
            }
            if (DungeonManager.start(player, dungeon, palier, hand)) {
                success();
            } else {
                failure();
                player.closeContainer();
            }
        }
    }

    // -- Autel de boost -----------------------------------------------------------

    private static final class BoostMenu extends DisplayMenu {

        private static final int[] SLOTS = {11, 13, 15};

        private final DungeonRun run;

        BoostMenu(int containerId, Inventory inventory, ServerPlayer player, DungeonRun run) {
            super(containerId, inventory, player, 3);
            this.run = run;
            refresh();
        }

        @Override
        protected void build() {
            frame();
            boolean taken = run.boosted.contains(player.getUUID());
            for (int i = 0; i < SLOTS.length && i < run.boostOptions.size(); i++) {
                Boost boost = run.boostOptions.get(i);
                set(SLOTS[i], Gui.button(boost.icon(), Component.literal(boost.label())
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), Gui.lore(
                        Gui.hint("Pendant 10 minutes, jusqu'a la fin du palier."),
                        Gui.blank(),
                        taken ? Gui.bad("Vous avez deja choisi votre bonus.") : Gui.action("Clic : choisir"))));
            }
        }

        @Override
        protected void onClick(int slot, int button, boolean shift) {
            for (int i = 0; i < SLOTS.length && i < run.boostOptions.size(); i++) {
                if (SLOTS[i] != slot) {
                    continue;
                }
                Boost boost = run.boostOptions.get(i);
                if (run.takeBoost(player, boost)) {
                    success();
                    DungeonManager.message(player, ChatFormatting.GREEN, "boost_taken", boost.label());
                    player.closeContainer();
                } else {
                    failure();
                    DungeonManager.message(player, ChatFormatting.RED, "boost_already");
                }
                return;
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

    private static final class Merchant extends DisplayMenu {

        private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};
        private static final int BALANCE = 22;

        private final DungeonRun run;

        Merchant(int containerId, Inventory inventory, ServerPlayer player, DungeonRun run) {
            super(containerId, inventory, player, 3);
            this.run = run;
            refresh();
        }

        @Override
        protected void build() {
            frame();
            long balance = Economy.balance(player);
            Offer[] offers = Offer.values();
            for (int i = 0; i < offers.length && i < SLOTS.length; i++) {
                Offer offer = offers[i];
                long price = offer.price(run.palier);
                ItemStack icon = offer.stack.get();
                icon.set(DataComponents.CUSTOM_NAME, Component.literal(offer.label)
                        .withStyle(ChatFormatting.YELLOW));
                icon.set(DataComponents.LORE, new ItemLore(Gui.lore(
                        Gui.field("Prix", money(price)),
                        Gui.blank(),
                        balance >= price ? Gui.action("Clic : acheter") : Gui.bad("Pas assez d'argent"))));
                set(SLOTS[i], icon);
            }
            set(BALANCE, Gui.button(Items.GOLD_NUGGET, "Votre argent", Gui.lore(Gui.field("Solde", money(balance)))));
        }

        @Override
        protected void onClick(int slot, int button, boolean shift) {
            Offer[] offers = Offer.values();
            for (int i = 0; i < offers.length && i < SLOTS.length; i++) {
                if (SLOTS[i] != slot) {
                    continue;
                }
                Offer offer = offers[i];
                long price = offer.price(run.palier);
                if (!Economy.tryDebit(player, price)) {
                    failure();
                    DungeonManager.message(player, ChatFormatting.RED, "poor", money(price));
                    return;
                }
                ItemStack bought = offer.stack.get();
                if (!player.getInventory().add(bought) && !bought.isEmpty()) {
                    player.drop(bought, false);
                }
                success();
                DungeonManager.message(player, ChatFormatting.GREEN, "bought", offer.label, money(price));
                return;
            }
        }

        private static String money(long amount) {
            return String.format("%,d $", amount).replace(',', ' ');
        }
    }

    // -- Sac ------------------------------------------------------------------------

    private static final class Bag extends DisplayMenu {

        private static final int INFO = 40;

        private final DungeonRun run;

        Bag(int containerId, Inventory inventory, ServerPlayer player, DungeonRun run) {
            super(containerId, inventory, player, 5);
            this.run = run;
            refresh();
        }

        @Override
        protected void build() {
            List<ItemStack> bag = run.bags.getOrDefault(player.getUUID(), List.of());
            int capacity = run.capacity(player.getUUID());
            for (int slot = 0; slot < 36; slot++) {
                if (slot < bag.size()) {
                    set(slot, bag.get(slot).copy());
                } else if (slot >= capacity) {
                    set(slot, Gui.button(Items.BLACK_STAINED_GLASS_PANE, Component.literal("Emplacement ferme")
                            .withStyle(ChatFormatting.DARK_GRAY), Gui.lore(
                            Gui.hint("Un meilleur sac de donjon l'ouvre."))));
                }
            }
            footer(INFO);
            set(INFO, Gui.button(Items.BUNDLE, "Sac de donjon", Gui.lore(
                    Gui.field("Piles", bag.size() + " / " + capacity),
                    Gui.blank(),
                    Gui.hint("Livre a la fin du palier reussi."),
                    Gui.hint("Perdu si vous mourez ou quittez le donjon."))));
        }

        @Override
        protected void onClick(int slot, int button, boolean shift) {
        }
    }
}

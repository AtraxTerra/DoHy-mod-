package fr.atraxterra.dohy.ui.menu;

import fr.atraxterra.dohy.boost.Autosell;
import fr.atraxterra.dohy.boost.BoostData;
import fr.atraxterra.dohy.boost.Boosters;
import fr.atraxterra.dohy.boost.Crates;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.economy.Bank;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.Gui;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** La boutique : boosters, caisses, assurance et autosell. */
public final class BoutiqueController extends MenuController {

    public static final BoutiqueController INSTANCE = new BoutiqueController();

    private static final List<String> CRATE_ORDER = List.of("common", "rare", "legendary");

    private BoutiqueController() {
        super("boutique");
    }

    @Override
    public MenuView view(ServerPlayer player) {
        double active = Boosters.active(player);
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("LIQUIDE", MoneyFormat.shortForm(Economy.balance(player)), 0xFFFFFFFF),
                new MenuView.Stat("EPARGNE", MoneyFormat.shortForm(Bank.banked(player)), 0xFFFAC400),
                new MenuView.Stat("BOOSTER", active > 1 ? "x" + (long) active : "-", 0xFF22E558));

        List<Card> boosters = new ArrayList<>();
        for (Boosters.Tier tier : Boosters.TIERS) {
            boolean busy = active > 1.0d;
            long missing = tier.price() - Economy.balance(player);
            Card card = Card.of(new ItemStack(boosterIcon(tier)), "Booster " + tier.key(), List.of(
                            MoneyFormat.exact(tier.price()), Boosters.DURATION_MINUTES + " min de temps de jeu"))
                    .accent(0xFFFAC400).badge(tier.key())
                    .tooltip(List.of("Multiplie l'argent des ventes",
                            "Le montant booste ne fait pas baisser les prix",
                            "Prepare un stock avant de le declencher",
                            busy ? "Un booster est deja actif : ils ne se cumulent pas"
                                    : missing > 0 ? "Il te manque " + MoneyFormat.exact(missing) : ""));
            if (busy || missing > 0) {
                card = card.state(MenuView.DISABLED);
            } else {
                card = card.action("booster:" + tier.key() + "|declencher");
            }
            boosters.add(card);
        }

        List<Card> crates = new ArrayList<>();
        for (String tier : orderedCrates()) {
            Crates.Crate crate = Crates.config().crates().get(tier);
            if (crate == null) {
                continue;
            }
            int keys = Boosters.data(player).keys(tier);
            List<String> tooltip = new ArrayList<>();
            tooltip.add("Contenu possible :");
            int weights = crate.entries().stream().mapToInt(Crates.Entry::weight).sum();
            crate.entries().stream()
                    .sorted((left, right) -> Integer.compare(right.weight(), left.weight()))
                    .limit(6)
                    .forEach(entry -> tooltip.add("  " + describe(entry, weights)));
            crates.add(Card.of(new ItemStack(keys > 0 ? Items.ENDER_CHEST : Items.TRAPPED_CHEST),
                            "Caisse " + Gui.prettify(tier), List.of("Cle : " + MoneyFormat.exact(crate.price()),
                                    keys + " cle(s) en poche"))
                    .accent(0xFFEF3826).badge(keys > 0 ? keys + " CLE" : "")
                    .action("key:" + tier + "|acheter une cle")
                    .right(keys > 0 ? "crate:" + tier + "|ouvrir une caisse" : "")
                    .tooltip(tooltip));
        }

        int charges = Economy.of(player).insurance();
        BoostData data = Boosters.data(player);
        List<Card> services = new ArrayList<>();
        services.add(Card.of(new ItemStack(Items.TOTEM_OF_UNDYING), "Assurance", List.of(
                        MoneyFormat.exact(Bank.insurancePrice()) + " la charge", charges + " charge(s)"))
                .accent(0xFF2F5BEA).badge(String.valueOf(charges)).action("insurance|acheter une charge")
                .tooltip(List.of("Divise par deux la perte a la mort", "Une charge est consommee par mort")));
        if (!data.autosellUnlocked()) {
            long missing = Autosell.UNLOCK_PRICE - Economy.balance(player);
            services.add(Card.of(new ItemStack(Items.HOPPER_MINECART), "Autosell", List.of(
                            MoneyFormat.exact(Autosell.UNLOCK_PRICE),
                            "Vide un coffre lie toutes les " + Autosell.INTERVAL_MINUTES + " min"))
                    .accent(0xFF2F5BEA).action("autosell|debloquer")
                    .state(missing > 0 ? MenuView.DISABLED : MenuView.NORMAL)
                    .tooltip(List.of("Ne charge jamais le chunk : un coffre non charge est saute",
                            missing > 0 ? "Il te manque " + MoneyFormat.exact(missing) : "")));
        } else {
            BlockPos linked = data.autosellPos();
            long since = Math.max(0L, ContractApi.data(player).playtimeMinutes() - data.autosellLast());
            services.add(Card.of(new ItemStack(Items.HOPPER), "Autosell", List.of(
                            linked == null ? "Aucun coffre lie"
                                    : "Coffre " + linked.getX() + " " + linked.getY() + " " + linked.getZ(),
                            "Dernier passage il y a " + since + " min"))
                    .state(MenuView.DONE).tooltip(List.of("Place-toi devant un coffre et fais /autosell link")));
        }
        services.add(Card.of(new ItemStack(Items.CHEST_MINECART), "Banque", List.of("Deposer, retirer",
                "Assurance")).accent(0xFFFAC400).action("bank"));
        services.add(Card.of(new ItemStack(Items.NETHER_STAR), "Ameliorations", List.of("Achats definitifs",
                "Rien ne se consomme")).accent(0xFFEC4FB5).action("upgrades"));

        return new MenuView(id(), "Boutique", "Ce qui s'achete ici ne se trouve pas en jeu", stats, List.of(
                new MenuView.Section("BOOSTERS", "upgrade", MenuView.GRID, boosters),
                new MenuView.Section("CAISSES", "resources", MenuView.GRID, crates),
                new MenuView.Section("SERVICES", "craft", MenuView.GRID, services)), true);
    }

    private static Item boosterIcon(Boosters.Tier tier) {
        return switch (tier.key()) {
            case "x2" -> Items.SUGAR;
            case "x3" -> Items.GLOWSTONE_DUST;
            default -> Items.BLAZE_POWDER;
        };
    }

    private static String describe(Crates.Entry entry, int weights) {
        int percent = weights <= 0 ? 0 : Math.round(100.0f * entry.weight() / weights);
        if (entry.item().isPresent()) {
            Item item = BuiltInRegistries.ITEM.getOptional(entry.item().get()).orElse(null);
            String name = item == null ? Gui.prettify(entry.item().get().getPath())
                    : new ItemStack(item).getHoverName().getString();
            return percent + " %  " + name + " x" + Math.max(1, entry.count());
        }
        long low = Math.min(entry.moneyMin(), entry.moneyMax());
        long high = Math.max(entry.moneyMin(), entry.moneyMax());
        return percent + " %  " + (high > low ? MoneyFormat.shortForm(low) + " a " + MoneyFormat.shortForm(high)
                : MoneyFormat.exact(low));
    }

    private static List<String> orderedCrates() {
        var available = Crates.config().crates().keySet();
        List<String> ordered = new ArrayList<>();
        for (String tier : CRATE_ORDER) {
            if (available.contains(tier)) {
                ordered.add(tier);
            }
        }
        for (String tier : new TreeSet<>(available)) {
            if (!ordered.contains(tier)) {
                ordered.add(tier);
            }
        }
        return ordered;
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (key.startsWith("booster:")) {
            Boosters.Tier tier = Boosters.tier(key.substring(8));
            if (tier != null) {
                report(player, Boosters.buy(player, tier), "Booster x" + (long) tier.multiplier() + " actif pour "
                        + Boosters.DURATION_MINUTES + " min de jeu.");
            }
        } else if (key.startsWith("key:")) {
            String tier = key.substring(4);
            report(player, Crates.buyKey(player, tier), "Cle " + tier + " achetee.");
        } else if (key.startsWith("crate:")) {
            String refusal = Crates.open(player, key.substring(6));
            if (refusal == null) {
                success(player);
            } else {
                failure(player);
                player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
            }
        } else {
            switch (key) {
                case "insurance" -> {
                    if (Bank.buyInsurance(player)) {
                        success(player);
                        player.sendSystemMessage(Component.literal("Assurance achetee — "
                                + Bank.insuranceCharges(player) + " charge(s).").withStyle(ChatFormatting.GREEN));
                    } else {
                        failure(player);
                        player.sendSystemMessage(Component.literal("Il te faut "
                                + MoneyFormat.exact(Bank.insurancePrice()) + " en liquide.")
                                .withStyle(ChatFormatting.RED));
                    }
                }
                case "autosell" -> report(player, Autosell.unlock(player),
                        "Autosell debloque — /autosell link devant un coffre.");
                case "bank" -> BankController.INSTANCE.open(player);
                case "upgrades" -> UpgradesController.INSTANCE.open(player);
                default -> {
                }
            }
        }
    }
}

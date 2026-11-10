package fr.atraxterra.dohy.ui.menu;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.upgrade.UpgradeApi;
import fr.atraxterra.dohy.upgrade.UpgradeConfig;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Les ameliorations : des achats definitifs, chaque palier quatre fois le precedent. */
public final class UpgradesController extends MenuController {

    public static final UpgradesController INSTANCE = new UpgradesController();

    private UpgradesController() {
        super("upgrades");
    }

    @Override
    public MenuView view(ServerPlayer player) {
        int total = UpgradeConfig.config().totalLevels();
        int acquired = UpgradeApi.data(player).total();
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("LIQUIDE", MoneyFormat.shortForm(Economy.balance(player)), 0xFFFFFFFF),
                new MenuView.Stat("PALIERS", acquired + " / " + total, 0xFFEC4FB5));
        List<Card> cards = new ArrayList<>();
        for (String id : UpgradeConfig.config().ordered()) {
            UpgradeConfig.Upgrade upgrade = UpgradeConfig.config().get(id).orElse(null);
            if (upgrade == null) {
                continue;
            }
            int level = UpgradeApi.levelOf(player, id);
            boolean maxed = level >= upgrade.maxLevel();
            long price = upgrade.priceOf(level).orElse(0L);
            List<String> lines = new ArrayList<>();
            lines.add(format(upgrade.effect(), upgrade.amountAt(level)));
            lines.add(maxed ? "Au maximum" : "Suivant : " + format(upgrade.effect(), upgrade.amountAt(level + 1)));
            List<String> tooltip = new ArrayList<>();
            if (!upgrade.description().isEmpty()) {
                tooltip.add(upgrade.description());
            }
            Card card = Card.of(new ItemStack(icon(upgrade.icon())), upgrade.label(), lines)
                    .accent(0xFFEC4FB5).badge(level + "/" + upgrade.maxLevel())
                    .progress(level, upgrade.maxLevel());
            if (maxed) {
                card = card.state(MenuView.DONE);
            } else {
                long missing = price - Economy.balance(player);
                tooltip.add("Prix : " + MoneyFormat.exact(price));
                if (missing > 0) {
                    tooltip.add("Il te manque " + MoneyFormat.exact(missing));
                }
                card = card.action(id + "|acheter pour " + MoneyFormat.shortForm(price))
                        .state(missing > 0 ? MenuView.DISABLED : MenuView.NORMAL);
            }
            cards.add(card.tooltip(tooltip));
        }
        return new MenuView(id(), "Ameliorations", "Achats definitifs : rien ne se consomme", stats,
                List.of(new MenuView.Section("", "", MenuView.GRID, cards)), true);
    }

    /** Un effet en mots, dans la bonne unite : « -15 % a l'achat », pas « +0,15 ». */
    static String format(String effect, double amount) {
        if (amount <= 0) {
            return "Aucun effet";
        }
        return switch (effect) {
            case UpgradeApi.SHOP_DISCOUNT -> "-" + Math.round(amount * 100) + " % a l'achat";
            case UpgradeApi.AUTOSELL_INTERVAL_CUT -> "Autosell toutes les " + Math.max(1, 5 - (int) amount) + " min";
            case UpgradeApi.BANK_CAP_MULTIPLIER -> "Plafond bancaire x" + (long) amount;
            case UpgradeApi.DAILY_CONTRACT_SLOTS -> "+" + (int) amount + " contrat(s) par jour";
            case UpgradeApi.BOOSTER_MINUTES -> "+" + (int) amount + " min de booster";
            case UpgradeApi.FLOOR_BONUS -> "+" + Math.round(amount * 100) + " points de plancher";
            case UpgradeApi.JOB_XP_BONUS -> "+" + Math.round(amount * 100) + " % d'XP de metier";
            case UpgradeApi.DEATH_PENALTY_CUT -> "-" + Math.round(amount * 100) + " % a la mort";
            default -> String.format("%+.2f", amount);
        };
    }

    private static Item icon(String raw) {
        try {
            int colon = raw.indexOf(':');
            Identifier id = colon < 0
                    ? Identifier.fromNamespaceAndPath("minecraft", raw)
                    : Identifier.fromNamespaceAndPath(raw.substring(0, colon), raw.substring(colon + 1));
            return BuiltInRegistries.ITEM.getOptional(id).orElse(Items.PAPER);
        } catch (Exception malformed) {
            return Items.PAPER;
        }
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        String refusal = UpgradeApi.buy(player, key);
        UpgradeConfig.Upgrade upgrade = UpgradeConfig.config().get(key).orElse(null);
        report(player, refusal, (upgrade == null ? key : upgrade.label()) + " niveau "
                + UpgradeApi.levelOf(player, key) + " — achat definitif.");
    }
}

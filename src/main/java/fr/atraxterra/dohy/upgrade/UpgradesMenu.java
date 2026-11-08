package fr.atraxterra.dohy.upgrade;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.DisplayMenu;
import fr.atraxterra.dohy.ui.Gui;
import fr.atraxterra.dohy.ui.Hub;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * L'ecran des ameliorations permanentes.
 *
 * <p>Les branches sont rangees du moins cher au plus cher, donc la lecture de
 * gauche a droite est l'ordre d'achat naturel. La pile de chaque vignette porte
 * le niveau atteint, lisible sans ouvrir la description.
 */
public final class UpgradesMenu extends DisplayMenu {

    private static final int SLOT_HEADER = 4;
    private static final int[] SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    private static final int SLOT_BACK = 45;
    private static final int SLOT_CLOSE = 49;

    private List<String> ids = List.of();

    public UpgradesMenu(int containerId, Inventory inventory, ServerPlayer player) {
        super(containerId, inventory, player, 6);
        refresh();
    }

    @Override
    protected void onClick(int slot, int button, boolean shift) {
        if (slot == SLOT_CLOSE) {
            click(0.6f);
            player.closeContainer();
            return;
        }
        if (slot == SLOT_BACK) {
            click(0.7f);
            Hub.open(player);
            return;
        }
        for (int index = 0; index < SLOTS.length && index < ids.size(); index++) {
            if (SLOTS[index] == slot) {
                buy(ids.get(index));
                return;
            }
        }
    }

    private void buy(String id) {
        String refusal = UpgradeApi.buy(player, id);
        if (refusal == null) {
            success();
            UpgradeConfig.Upgrade upgrade = UpgradeConfig.config().get(id).orElse(null);
            int level = UpgradeApi.levelOf(player, id);
            player.sendSystemMessage(Component.literal(
                            (upgrade == null ? id : upgrade.label()) + " niveau " + level
                                    + " — achat definitif.")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        failure();
        player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
    }

    @Override
    protected void build() {
        frame();
        ids = UpgradeConfig.config().ordered();

        for (int index = 0; index < SLOTS.length && index < ids.size(); index++) {
            set(SLOTS[index], tile(ids.get(index)));
        }
        set(SLOT_HEADER, header());
        set(SLOT_BACK, Gui.back("le menu"));
        set(SLOT_CLOSE, Gui.close());
    }

    private ItemStack header() {
        UpgradeData data = UpgradeApi.data(player);
        return Gui.button(Items.NETHER_STAR, "Ameliorations", Gui.lore(
                Gui.field("liquide", MoneyFormat.exact(Economy.balance(player))),
                Gui.field("paliers acquis",
                        data.total() + " / " + UpgradeConfig.config().totalLevels()),
                Gui.bar(data.total(), UpgradeConfig.config().totalLevels(), 20),
                Gui.blank(),
                Gui.hint("un achat ici est definitif"),
                Gui.hint("contrairement aux boosters et aux caisses,"),
                Gui.hint("rien ne se consomme"),
                Gui.blank(),
                Gui.hint("chaque palier coute quatre fois le precedent :"),
                Gui.hint("c'est l'objectif qui reste quand tout est fait")));
    }

    private ItemStack tile(String id) {
        UpgradeConfig.Upgrade upgrade = UpgradeConfig.config().get(id).orElse(null);
        if (upgrade == null) {
            return ItemStack.EMPTY;
        }
        int level = UpgradeApi.levelOf(player, id);
        boolean maxed = level >= upgrade.maxLevel();
        var price = upgrade.priceOf(level);

        List<Component> lore = new ArrayList<>();
        if (!upgrade.description().isEmpty()) {
            lore.add(Gui.hint(upgrade.description()));
            lore.add(Gui.blank());
        }
        lore.add(Gui.field("palier", level + " / " + upgrade.maxLevel()));
        lore.add(Gui.bar(level, upgrade.maxLevel(), 20));
        lore.add(Gui.blank());
        lore.add(Gui.field("effet actuel", format(upgrade.effect(), upgrade.amountAt(level))));

        if (maxed) {
            lore.add(Gui.blank());
            lore.add(Gui.good("au maximum"));
        } else {
            lore.add(Gui.field("au palier suivant",
                    format(upgrade.effect(), upgrade.amountAt(level + 1))));
            lore.add(Gui.blank());
            lore.add(Gui.field("prix", MoneyFormat.exact(price.orElse(0L))));
            long missing = price.orElse(0L) - Economy.balance(player);
            lore.add(missing > 0
                    ? Gui.bad("il te manque " + MoneyFormat.exact(missing))
                    : Gui.action("clic pour acheter"));
        }

        Component name = Component.literal(upgrade.label() + (level > 0 ? "  " + level : ""))
                .withStyle(maxed ? ChatFormatting.GOLD
                        : level > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        return Gui.button(icon(upgrade.icon()), name, lore,
                Math.max(1, Math.min(64, level)));
    }

    /**
     * Un effet en mots, et dans la bonne unite.
     *
     * <p>Un pourcentage, des minutes et un multiplicateur ne se lisent pas de la
     * meme facon : « +0,15 » ne veut rien dire, « +15 % » si.
     */
    private static String format(String effect, double amount) {
        if (amount <= 0) {
            return "aucun";
        }
        return switch (effect) {
            case UpgradeApi.SHOP_DISCOUNT -> "-" + Math.round(amount * 100) + " % a l'achat";
            case UpgradeApi.AUTOSELL_INTERVAL_CUT ->
                    "toutes les " + Math.max(1, 5 - (int) amount) + " min au lieu de 5";
            case UpgradeApi.BANK_CAP_MULTIPLIER -> "plafond x" + (long) amount;
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
                    : Identifier.fromNamespaceAndPath(raw.substring(0, colon),
                            raw.substring(colon + 1));
            return BuiltInRegistries.ITEM.getOptional(id).orElse(Items.PAPER);
        } catch (Exception malformed) {
            // Une icone mal ecrite dans le datapack ne doit pas empecher
            // l'ecran de s'ouvrir : du papier fait l'affaire.
            return Items.PAPER;
        }
    }
}

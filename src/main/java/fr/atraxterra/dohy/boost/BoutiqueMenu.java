package fr.atraxterra.dohy.boost;

import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.economy.Bank;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.shop.BankMenu;
import fr.atraxterra.dohy.ui.DisplayMenu;
import fr.atraxterra.dohy.ui.Gui;
import fr.atraxterra.dohy.ui.Hub;
import fr.atraxterra.dohy.upgrade.UpgradeApi;
import fr.atraxterra.dohy.upgrade.UpgradeConfig;
import fr.atraxterra.dohy.upgrade.UpgradesMenu;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * La boutique : ce qui s'achete avec de l'argent et ne se trouve pas en jeu.
 *
 * <p>Elle prend la place de l'onglet « Composants » du shop, qui n'a jamais eu
 * d'items — la categorie existait dans le catalogue mais aucune ligne de
 * {@code rates.csv} ne la portait, et un onglet vide est un bug visible. Les
 * sinks d'argent y sont mieux a leur place qu'eparpilles dans des commandes :
 * un joueur qui vient de vendre pour vingt millions doit voir d'un coup d'oeil
 * ce qu'il peut en faire.
 */
public final class BoutiqueMenu extends DisplayMenu {

    private static final int SLOT_HEADER = 4;
    private static final int[] SLOTS_BOOSTERS = {19, 21, 23};
    private static final int[] SLOTS_CRATES = {29, 31, 33};
    // Quatre vignettes reparties sur l'interieur de la rangee, 37 a 43.
    private static final int SLOT_INSURANCE = 37;
    private static final int SLOT_BANK = 39;
    private static final int SLOT_AUTOSELL = 41;
    private static final int SLOT_UPGRADES = 43;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_CLOSE = 49;

    /** Ordre d'affichage des caisses ; les paliers inconnus suivent, tries. */
    private static final List<String> CRATE_ORDER = List.of("common", "rare", "legendary");

    private List<String> crates = List.of();

    public BoutiqueMenu(int containerId, Inventory inventory, ServerPlayer player) {
        super(containerId, inventory, player, 6);
        refresh();
    }

    @Override
    protected void onClick(int slot, int button, boolean shift) {
        switch (slot) {
            case SLOT_CLOSE -> {
                click(0.6f);
                player.closeContainer();
                return;
            }
            case SLOT_BACK -> {
                click(0.7f);
                Hub.open(player);
                return;
            }
            case SLOT_BANK -> {
                click(0.9f);
                open("Banque", (containerId, inventory) ->
                        new BankMenu(containerId, inventory, player));
                return;
            }
            case SLOT_UPGRADES -> {
                click(0.9f);
                open("Ameliorations", (containerId, inventory) ->
                        new UpgradesMenu(containerId, inventory, player));
                return;
            }
            case SLOT_INSURANCE -> {
                buyInsurance();
                return;
            }
            case SLOT_AUTOSELL -> {
                unlockAutosell();
                return;
            }
            default -> {
                // Les boosters et les caisses sont traites ci-dessous.
            }
        }

        for (int index = 0; index < SLOTS_BOOSTERS.length
                && index < Boosters.TIERS.length; index++) {
            if (SLOTS_BOOSTERS[index] == slot) {
                buyBooster(Boosters.TIERS[index]);
                return;
            }
        }
        for (int index = 0; index < SLOTS_CRATES.length && index < crates.size(); index++) {
            if (SLOTS_CRATES[index] == slot) {
                if (button == 1) {
                    openCrate(crates.get(index));
                } else {
                    buyKey(crates.get(index));
                }
                return;
            }
        }
    }

    private void buyBooster(Boosters.Tier tier) {
        String refusal = Boosters.buy(player, tier);
        report(refusal, "Booster x" + (long) tier.multiplier() + " actif pour "
                + Boosters.DURATION_MINUTES + " min de jeu.");
    }

    private void buyKey(String tier) {
        String refusal = Crates.buyKey(player, tier);
        report(refusal, "Cle " + tier + " achetee.");
    }

    private void openCrate(String tier) {
        String refusal = Crates.open(player, tier);
        if (refusal == null) {
            success();
        } else {
            failure();
            player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
        }
    }

    private void buyInsurance() {
        if (Bank.buyInsurance(player)) {
            success();
            player.sendSystemMessage(Component.literal("Assurance achetee — "
                            + Bank.insuranceCharges(player) + " charge(s).")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        failure();
        player.sendSystemMessage(Component.literal("Il te faut "
                        + MoneyFormat.exact(Bank.insurancePrice()) + " en liquide.")
                .withStyle(ChatFormatting.RED));
    }

    private void unlockAutosell() {
        if (Boosters.data(player).autosellUnlocked()) {
            click();
            player.sendSystemMessage(Component.literal(
                            "Autosell deja debloque. Place-toi devant un coffre et fais /autosell link.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        report(Autosell.unlock(player), "Autosell debloque — /autosell link devant un coffre.");
    }

    /** Un refus se dit, une reussite se joue. Une seule facon de le faire. */
    private void report(String refusal, String message) {
        if (refusal == null) {
            success();
            player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GREEN));
        } else {
            failure();
            player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
        }
    }

    @Override
    protected void build() {
        frame();
        crates = orderedCrates();

        set(SLOT_HEADER, header());

        for (int index = 0; index < SLOTS_BOOSTERS.length
                && index < Boosters.TIERS.length; index++) {
            set(SLOTS_BOOSTERS[index], booster(Boosters.TIERS[index]));
        }
        for (int index = 0; index < SLOTS_CRATES.length && index < crates.size(); index++) {
            set(SLOTS_CRATES[index], crate(crates.get(index)));
        }

        set(SLOT_INSURANCE, insurance());
        set(SLOT_UPGRADES, Gui.button(Items.NETHER_STAR, "Ameliorations", Gui.lore(
                Gui.field("paliers acquis", UpgradeApi.data(player).total() + " / "
                        + UpgradeConfig.config().totalLevels()),
                Gui.hint("achats definitifs, rien ne se consomme"),
                Gui.blank(),
                Gui.action("clic pour ouvrir"))));
        set(SLOT_AUTOSELL, autosell());

        set(SLOT_BACK, Gui.back("le menu"));
        set(SLOT_CLOSE, Gui.close());
    }

    private ItemStack header() {
        double active = Boosters.active(player);
        return Gui.button(Items.GOLD_INGOT, "Boutique", Gui.lore(
                Gui.field("liquide", MoneyFormat.exact(Economy.balance(player))),
                Gui.field("epargne", MoneyFormat.exact(Bank.banked(player))),
                Gui.blank(),
                active > 1.0d
                        ? Gui.good("booster x" + (long) active + " · "
                                + Boosters.remaining(player) + " min de jeu restantes")
                        : Gui.hint("aucun booster actif"),
                Gui.blank(),
                Gui.hint("tout ce qui s'achete ici ne se trouve pas en jeu"),
                Gui.hint("c'est la sortie d'argent du mod")));
    }

    /**
     * Un booster.
     *
     * <p>La duree est volontairement courte et le prix eleve : l'interet n'est
     * pas de gagner plus, c'est de parier sur un stock constitue d'avance. La
     * description le dit, sinon le joueur l'achete au mauvais moment et croit
     * s'etre fait avoir.
     */
    private ItemStack booster(Boosters.Tier tier) {
        boolean busy = Boosters.active(player) > 1.0d;
        boolean affordable = Economy.balance(player) >= tier.price();

        List<Component> lore = new ArrayList<>();
        lore.add(Gui.field("prix", MoneyFormat.exact(tier.price())));
        lore.add(Gui.field("duree", Boosters.DURATION_MINUTES + " min de temps de jeu"));
        lore.add(Gui.blank());
        lore.add(Gui.hint("multiplie l'argent des ventes"));
        lore.add(Gui.hint("le montant booste ne fait pas baisser les prix"));
        lore.add(Gui.blank());
        lore.add(Gui.hint("prepare un stock avant de le declencher :"));
        lore.add(Gui.hint("vingt minutes passent vite"));
        lore.add(Gui.blank());
        if (busy) {
            lore.add(Gui.bad("un booster est deja actif"));
            lore.add(Gui.hint("  ils ne se cumulent pas"));
        } else if (!affordable) {
            lore.add(Gui.bad("il te manque " + MoneyFormat.exact(
                    tier.price() - Economy.balance(player))));
        } else {
            lore.add(Gui.action("clic pour declencher"));
        }

        return Gui.button(iconOf(tier),
                Component.literal("Booster " + tier.key())
                        .withStyle(busy ? ChatFormatting.DARK_GRAY : ChatFormatting.YELLOW),
                lore);
    }

    private static Item iconOf(Boosters.Tier tier) {
        return switch (tier.key()) {
            case "x2" -> Items.SUGAR;
            case "x3" -> Items.GLOWSTONE_DUST;
            default -> Items.BLAZE_POWDER;
        };
    }

    private ItemStack crate(String tier) {
        Crates.Crate crate = Crates.config().crates().get(tier);
        if (crate == null) {
            return ItemStack.EMPTY;
        }
        int keys = Boosters.data(player).keys(tier);

        List<Component> lore = new ArrayList<>();
        lore.add(Gui.field("prix de la cle", MoneyFormat.exact(crate.price())));
        lore.add(Gui.field("cles en poche", String.valueOf(keys)));
        lore.add(Gui.blank());
        lore.add(Gui.hint("contenu possible :"));
        crate.entries().stream()
                .sorted((left, right) -> Integer.compare(right.weight(), left.weight()))
                .limit(6)
                .forEach(entry -> lore.add(describe(entry, crate)));
        lore.add(Gui.blank());
        lore.add(Gui.action("clic gauche : acheter une cle"));
        lore.add(keys > 0
                ? Gui.action("clic droit : ouvrir une caisse")
                : Gui.hint("clic droit : ouvrir (aucune cle)"));

        return Gui.button(keys > 0 ? Items.ENDER_CHEST : Items.TRAPPED_CHEST,
                Component.literal("Caisse " + Gui.prettify(tier))
                        .withStyle(ChatFormatting.YELLOW),
                lore);
    }

    /** Une ligne de table de butin, avec sa probabilite. */
    private Component describe(Crates.Entry entry, Crates.Crate crate) {
        int total = crate.entries().stream().mapToInt(Crates.Entry::weight).sum();
        int percent = total <= 0 ? 0 : Math.round(100.0f * entry.weight() / total);

        StringBuilder text = new StringBuilder("  " + percent + " %  ");
        if (entry.item().isPresent()) {
            Item item = BuiltInRegistries.ITEM.getOptional(entry.item().get()).orElse(null);
            text.append(item == null
                            ? Gui.prettify(entry.item().get().getPath())
                            : new ItemStack(item).getHoverName().getString())
                    .append(" x").append(Math.max(1, entry.count()));
        } else {
            long low = Math.min(entry.moneyMin(), entry.moneyMax());
            long high = Math.max(entry.moneyMin(), entry.moneyMax());
            text.append(high > low
                    ? MoneyFormat.shortForm(low) + " a " + MoneyFormat.shortForm(high)
                    : MoneyFormat.exact(low));
        }
        return Component.literal(text.toString()).withStyle(ChatFormatting.DARK_GRAY);
    }

    private ItemStack insurance() {
        int charges = Economy.of(player).insurance();
        return Gui.button(Items.TOTEM_OF_UNDYING,
                "Assurance — " + MoneyFormat.exact(Bank.insurancePrice()), Gui.lore(
                        Gui.field("charges", String.valueOf(charges)),
                        Gui.blank(),
                        Gui.hint("divise par deux la perte a la mort"),
                        Gui.hint("une charge est consommee par mort"),
                        Gui.blank(),
                        Gui.action("clic pour acheter une charge")));
    }

    private ItemStack bank() {
        return Gui.button(Items.CHEST_MINECART, "Banque", Gui.lore(
                Gui.field("epargne", MoneyFormat.exact(Bank.banked(player))),
                Gui.field("interets", MoneyFormat.exact(
                        Bank.perDay(player)) + " / jour"),
                Gui.blank(),
                Gui.hint("l'epargne est protegee de la mort"),
                Gui.blank(),
                Gui.action("clic pour ouvrir la banque")));
    }

    private ItemStack autosell() {
        BoostData data = Boosters.data(player);
        boolean unlocked = data.autosellUnlocked();
        BlockPos linked = data.autosellPos();

        List<Component> lore = new ArrayList<>();
        if (!unlocked) {
            lore.add(Gui.field("prix", MoneyFormat.exact(Autosell.UNLOCK_PRICE)));
            lore.add(Gui.blank());
            lore.add(Gui.hint("vide un coffre lie de ses items vendables"));
            lore.add(Gui.hint("toutes les " + Autosell.INTERVAL_MINUTES + " min de jeu"));
            lore.add(Gui.blank());
            lore.add(Gui.hint("ne charge jamais le chunk : un coffre"));
            lore.add(Gui.hint("non charge est simplement saute"));
            lore.add(Gui.blank());
            long missing = Autosell.UNLOCK_PRICE - Economy.balance(player);
            lore.add(missing > 0
                    ? Gui.bad("il te manque " + MoneyFormat.exact(missing))
                    : Gui.action("clic pour debloquer"));
        } else {
            lore.add(Gui.good("debloque"));
            lore.add(linked == null
                    ? Gui.bad("aucun coffre lie")
                    : Gui.field("coffre lie", linked.getX() + " " + linked.getY()
                            + " " + linked.getZ()));
            lore.add(Gui.blank());
            lore.add(Gui.hint("place-toi devant un coffre"));
            lore.add(Gui.hint("et fais /autosell link"));
            long since = ContractApi.data(player).playtimeMinutes() - data.autosellLast();
            lore.add(Gui.hint("dernier passage il y a " + Math.max(0L, since) + " min"));
        }

        return Gui.button(unlocked ? Items.HOPPER : Items.HOPPER_MINECART,
                "Autosell", lore);
    }

    /** Paliers connus d'abord, puis les autres par ordre alphabetique. */
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
}

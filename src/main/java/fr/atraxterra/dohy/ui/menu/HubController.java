package fr.atraxterra.dohy.ui.menu;

import fr.atraxterra.dohy.boost.Boosters;
import fr.atraxterra.dohy.contract.ActiveContract;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.contract.ContractData;
import fr.atraxterra.dohy.economy.Bank;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.job.JobConfig;
import fr.atraxterra.dohy.job.JobGate;
import fr.atraxterra.dohy.job.JobsController;
import fr.atraxterra.dohy.museum.MuseumApi;
import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.achievement.AchievementMenu;
import fr.atraxterra.dohy.palamod.achievement.Achievements;
import fr.atraxterra.dohy.palamod.dungeon.Rpg;
import fr.atraxterra.dohy.palamod.dungeon.RpgMenu;
import fr.atraxterra.dohy.palamod.trixium.TrixiumContest;
import fr.atraxterra.dohy.palamod.trixium.TrixiumData;
import fr.atraxterra.dohy.palamod.trixium.TrixiumMenu;
import fr.atraxterra.dohy.palamod.trixium.TrixiumRivals;
import fr.atraxterra.dohy.shop.SellChestMenu;
import fr.atraxterra.dohy.shop.ShopController;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.upgrade.UpgradeApi;
import fr.atraxterra.dohy.upgrade.UpgradeConfig;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Le menu principal : une carte par systeme, avec le chiffre qui dit s'il faut
 * y aller (contrats restants, epargne, pieces manquantes au musee).
 */
public final class HubController extends MenuController {

    public static final HubController INSTANCE = new HubController();

    private HubController() {
        super("hub");
    }

    @Override
    public MenuView view(ServerPlayer player) {
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("LIQUIDE", MoneyFormat.shortForm(Economy.balance(player)), 0xFFFFFFFF),
                new MenuView.Stat("PATRIMOINE", MoneyFormat.shortForm(Economy.of(player).netWorth()), 0xFFFFFFFF),
                new MenuView.Stat("NIVEAU GLOBAL", String.valueOf(ContractApi.globalLevel(player)), 0xFF22E558));

        double booster = Boosters.active(player);
        List<Card> economy = List.of(
                Card.of(new ItemStack(Items.EMERALD), "Shop", List.of("Acheter et vendre", "Prix dynamiques"))
                        .accent(0xFFEF3826).action("shop"),
                Card.of(new ItemStack(Items.CHEST), "Coffre de vente", List.of("Depose, ferme : vendu",
                        "Le reste te revient")).accent(0xFFEF3826).action("sell"),
                Card.of(new ItemStack(Items.GOLD_BLOCK), "Boutique", List.of("Boosters, boxes, autosell",
                        booster > 1 ? "Booster x" + (long) booster + " actif" : "Aucun booster actif"))
                        .accent(0xFFFAC400).action("boutique"),
                Card.of(new ItemStack(Items.BELL), "Marche", List.of("Les annonces du jour",
                        "Stocks limites, prix variables")).accent(0xFFFAC400).action("market"),
                Card.of(new ItemStack(Items.CHEST_MINECART), "Banque", List.of(
                        "Epargne " + MoneyFormat.shortForm(Bank.banked(player)),
                        MoneyFormat.shortForm(Bank.perDay(player)) + " d'interets / jour"))
                        .accent(0xFFFAC400).action("bank"),
                clickerCard(player),
                Card.of(new ItemStack(Items.BEACON), "Spawn", List.of("Le hall des box", "et les bornes"))
                        .accent(0xFFEF3826).action("spawn"));

        ContractData contracts = ContractApi.data(player);
        int left = 0;
        for (ActiveContract contract : contracts.daily()) {
            if (!contract.done()) {
                left++;
            }
        }
        int unlocked = 0;
        int total = 0;
        for (Job job : Job.values()) {
            for (JobConfig.Unlock unlock : JobGate.unlocksOf(job)) {
                total++;
                if (JobGate.allows(player, unlock)) {
                    unlocked++;
                }
            }
        }
        int expected = 0;
        int donated = 0;
        for (String wing : MuseumApi.wings()) {
            expected += MuseumApi.expected(wing).size();
            donated += MuseumApi.donatedIn(player, wing);
        }
        List<Card> progress = List.of(
                Card.of(new ItemStack(Items.WRITABLE_BOOK), "Contrats", List.of(
                        left == 0 ? "Quotidiens termines" : left + " quotidien(s) restant(s)",
                        "Niveau global " + ContractApi.globalLevel(player)))
                        .accent(0xFF2F5BEA).action("contracts").badge(left == 0 ? "OK" : String.valueOf(left))
                        .state(left == 0 ? MenuView.DONE : MenuView.NORMAL),
                Card.of(new ItemStack(Items.IRON_PICKAXE), "Metiers", List.of(
                        "Niveaux cumules " + JobApi.totalLevels(player),
                        "Deblocages " + unlocked + " / " + total))
                        .accent(0xFF2F5BEA).action("jobs").progress(unlocked, total),
                Card.of(new ItemStack(Items.BOOKSHELF), "Collections", List.of("Recolter releve le plancher",
                        "Prestige : plus d'ecoulement")).accent(0xFF2F5BEA).action("collections"),
                Card.of(new ItemStack(Items.ENCHANTING_TABLE), "Musee", List.of(
                        "Pieces exposees " + donated + " / " + expected, "Ailes completes : bonus"))
                        .accent(0xFF2F5BEA).action("museum").progress(donated, expected));

        int rpgLevel = Rpg.level(Rpg.data(player).xp());
        int points = Rpg.available(player);
        int upgrades = UpgradeApi.data(player).total();
        int upgradesTotal = UpgradeConfig.config().totalLevels();
        List<Card> endgame = List.of(
                Card.of(new ItemStack(Items.NETHER_STAR), "Ameliorations", List.of(
                        "Paliers " + upgrades + " / " + upgradesTotal, "Achats definitifs"))
                        .accent(0xFFEC4FB5).action("upgrades").progress(upgrades, upgradesTotal),
                Card.of(new ItemStack(Items.TOTEM_OF_UNDYING), "Profil RPG", List.of("Niveau RPG " + rpgLevel,
                        points > 0 ? points + " point(s) a depenser" : "Arbre des donjons"))
                        .accent(0xFFEC4FB5).action("rpg").badge(points > 0 ? "+" + points : ""),
                factionCard(player),
                trixiumCard(player),
                achievementCard(player));

        return new MenuView(id(), "DoHy", "Le menu de ton aventure", stats, List.of(
                new MenuView.Section("ECONOMIE", "resources", MenuView.GRID, economy),
                new MenuView.Section("PROGRESSION", "upgrade", MenuView.GRID, progress),
                new MenuView.Section("FIN DE PARTIE", "craft", MenuView.GRID, endgame)), false);
    }

    /** Le clicker : ses ClicCoins et sa production, s'il a deja servi. */
    private static Card clickerCard(ServerPlayer player) {
        var data = player.getExistingDataOrNull(fr.atraxterra.dohy.DoHyAttachments.CLICKER.get());
        boolean started = data != null && !data.isEmpty();
        return Card.of(new ItemStack(Items.COOKIE), "Clicker", List.of(
                        started ? fr.atraxterra.dohy.palamod.clicker.ClickerFormat.compact(data.points())
                                + " ClicCoins" : "Nourris les namuus",
                        started ? fr.atraxterra.dohy.palamod.clicker.ClickerFormat.rate(data.rps()) + " par seconde"
                                : "Batiments, ameliorations, Clic Shop"))
                .accent(0xFFFAC400).action("clicker");
    }

    /** Les succes : combien sont valides. */
    private static Card achievementCard(ServerPlayer player) {
        int done = Achievements.data(player).doneCount();
        int total = Achievements.total();
        return Card.of(new ItemStack(Items.GOLDEN_APPLE), "Succes", List.of(done + " / " + total + " valides",
                        "Une prime par succes"))
                .accent(0xFFFAC400).action("achievements").progress(done, total);
    }

    /** Le concours Trixium : le rang du mois, et les paliers a recuperer. */
    private static Card trixiumCard(ServerPlayer player) {
        TrixiumContest.catchUp(player);
        TrixiumData data = TrixiumContest.data(player);
        int claimable = TrixiumContest.claimable(player);
        return Card.of(new ItemStack(PalaItems.TRIXIUM.get()), "Concours Trixium", List.of(
                        data.deposited() > 0 ? "Rang " + TrixiumContest.rank(player) + " / "
                                + (TrixiumRivals.COUNT + 1) : "Pas encore de depot",
                        MoneyFormat.shortForm(data.deposited()) + " depose(s) ce mois"))
                .accent(0xFF2BC4C4).action("trixium").badge(claimable > 0 ? "+" + claimable : "");
    }

    private static Card factionCard(ServerPlayer player) {
        fr.atraxterra.dohy.faction.FactionData faction = fr.atraxterra.dohy.faction.FactionApi.data(player);
        if (!faction.exists()) {
            return Card.of(new ItemStack(Items.BANNER.pick(net.minecraft.world.item.DyeColor.WHITE)), "Faction", List.of("Fonde la tienne",
                    fr.atraxterra.dohy.faction.FactionApi.CREATION_COST + " lingots de paladium"))
                    .accent(0xFFEC4FB5).action("faction");
        }
        fr.atraxterra.dohy.faction.FactionLeague league =
                fr.atraxterra.dohy.faction.FactionLeague.of(faction.ranking().league());
        return Card.of(fr.atraxterra.dohy.faction.FactionMenus.banner(faction), faction.name(), List.of(
                        "Ligue " + league.label(), MoneyFormat.shortForm(faction.ranking().elo()) + " ELO"))
                .accent(league.color()).action("faction");
    }

    /** Les menus que les boutons de l'inventaire peuvent ouvrir. */
    private static final java.util.Set<String> INVENTORY_TARGETS =
            java.util.Set.of("shop", "contracts", "bank", "faction", "jobs", "trixium", "clicker");

    /**
     * Ouvre le menu demande par un bouton de l'inventaire ({@code InventoryNav}).
     *
     * <p>{@code hub} ouvre ce menu-ci ; les autres passent par les memes cartes
     * que le menu principal, donc par les memes regles. Un nom hors de la liste
     * ne fait rien : il vient du client.
     */
    public static void openTarget(ServerPlayer player, String target) {
        if ("hub".equals(target)) {
            INSTANCE.open(player);
        } else if (INVENTORY_TARGETS.contains(target)) {
            INSTANCE.card(player, target, 0);
        }
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        switch (key) {
            case "faction" -> fr.atraxterra.dohy.faction.FactionMenus.Overview.INSTANCE.open(player);
            case "trixium" -> TrixiumMenu.INSTANCE.open(player);
            case "achievements" -> AchievementMenu.INSTANCE.open(player);
            case "spawn" -> {
                UiNet.close(player);
                fr.atraxterra.dohy.spawn.SpawnApi.toggle(player);
            }
            case "shop" -> ShopController.open(player);
            case "sell" -> {
                UiNet.forget(player);
                player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, viewer) -> new SellChestMenu(containerId, inventory, player),
                        Component.literal("Coffre de vente")));
            }
            case "boutique" -> BoutiqueController.INSTANCE.open(player);
            case "market" -> fr.atraxterra.dohy.auction.AuctionController.INSTANCE.open(player);
            case "bank" -> BankController.INSTANCE.open(player);
            case "clicker" -> fr.atraxterra.dohy.palamod.clicker.Clicker.open(player);
            case "contracts" -> ContractsController.INSTANCE.open(player);
            case "jobs" -> JobsController.open(player);
            case "collections" -> CollectionsController.INSTANCE.open(player);
            case "museum" -> MuseumController.INSTANCE.open(player);
            case "upgrades" -> UpgradesController.INSTANCE.open(player);
            case "rpg" -> {
                UiNet.forget(player);
                RpgMenu.INSTANCE.open(player);
            }
            default -> {
            }
        }
    }
}

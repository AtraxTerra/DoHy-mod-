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
import fr.atraxterra.dohy.palamod.dungeon.Rpg;
import fr.atraxterra.dohy.palamod.dungeon.RpgMenu;
import fr.atraxterra.dohy.shop.SellChestMenu;
import fr.atraxterra.dohy.shop.ShopController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
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
                Card.of(new ItemStack(Items.GOLD_BLOCK), "Boutique", List.of("Boosters, caisses, autosell",
                        booster > 1 ? "Booster x" + (long) booster + " actif" : "Aucun booster actif"))
                        .accent(0xFFFAC400).action("boutique"),
                Card.of(new ItemStack(Items.CHEST_MINECART), "Banque", List.of(
                        "Epargne " + MoneyFormat.shortForm(Bank.banked(player)),
                        MoneyFormat.shortForm(Bank.perDay(player)) + " d'interets / jour"))
                        .accent(0xFFFAC400).action("bank"));

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
        for (String wing : MuseumApi.categories()) {
            for (var id : MuseumApi.expected(wing)) {
                expected++;
                if (MuseumApi.data(player).has(id)) {
                    donated++;
                }
            }
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
                factionCard(player));

        return new MenuView(id(), "DoHy", "Le menu de ton aventure", stats, List.of(
                new MenuView.Section("ECONOMIE", "resources", MenuView.GRID, economy),
                new MenuView.Section("PROGRESSION", "upgrade", MenuView.GRID, progress),
                new MenuView.Section("FIN DE PARTIE", "craft", MenuView.GRID, endgame)), false);
    }

    private static Card factionCard(ServerPlayer player) {
        fr.atraxterra.dohy.faction.FactionData faction = fr.atraxterra.dohy.faction.FactionApi.data(player);
        if (!faction.exists()) {
            return Card.of(new ItemStack(Items.WHITE_BANNER), "Faction", List.of("Fonde la tienne",
                    fr.atraxterra.dohy.faction.FactionApi.CREATION_COST + " lingots de paladium"))
                    .accent(0xFFEC4FB5).action("faction");
        }
        fr.atraxterra.dohy.faction.FactionLeague league =
                fr.atraxterra.dohy.faction.FactionLeague.of(faction.ranking().league());
        return Card.of(fr.atraxterra.dohy.faction.FactionMenus.banner(faction.banner()), faction.name(), List.of(
                        "Ligue " + league.label(), MoneyFormat.shortForm(faction.ranking().elo()) + " ELO"))
                .accent(league.color()).action("faction");
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        switch (key) {
            case "faction" -> fr.atraxterra.dohy.faction.FactionMenus.Overview.INSTANCE.open(player);
            case "shop" -> ShopController.open(player);
            case "sell" -> {
                UiNet.forget(player);
                player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, viewer) -> new SellChestMenu(containerId, inventory, player),
                        Component.literal("Coffre de vente")));
            }
            case "boutique" -> BoutiqueController.INSTANCE.open(player);
            case "bank" -> BankController.INSTANCE.open(player);
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

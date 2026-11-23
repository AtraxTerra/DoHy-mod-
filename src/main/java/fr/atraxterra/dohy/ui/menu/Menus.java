package fr.atraxterra.dohy.ui.menu;

import fr.atraxterra.dohy.faction.FactionMenus;
import fr.atraxterra.dohy.faction.FactionTreeMenus;
import fr.atraxterra.dohy.faction.base.EnemyBaseMenu;
import fr.atraxterra.dohy.faction.event.FactionEventMenu;
import fr.atraxterra.dohy.faction.reserve.ReserveMenu;
import fr.atraxterra.dohy.palamod.achievement.AchievementMenu;
import fr.atraxterra.dohy.palamod.alchemist.MysticEnchantMenu;
import fr.atraxterra.dohy.palamod.dimminer.DimMinerMenus;
import fr.atraxterra.dohy.palamod.dungeon.DungeonMenus;
import fr.atraxterra.dohy.palamod.dungeon.RpgMenu;
import fr.atraxterra.dohy.palamod.golem.GolemMenus;
import fr.atraxterra.dohy.palamod.mount.MountMenus;
import fr.atraxterra.dohy.palamod.pillage.ChestExplorerMenu;
import fr.atraxterra.dohy.palamod.trixium.TrixiumMenu;

import java.util.List;

/** Tous les menus generiques, pour l'enregistrement de leurs vues. */
public final class Menus {

    private Menus() {
    }

    public static List<MenuController> all() {
        return List.of(HubController.INSTANCE, BankController.INSTANCE, ContractsController.INSTANCE,
                UpgradesController.INSTANCE, BoutiqueController.INSTANCE, CollectionsController.INSTANCE,
                MuseumController.INSTANCE,
                DungeonMenus.Paliers.INSTANCE, DungeonMenus.BoostAltar.INSTANCE, DungeonMenus.Merchant.INSTANCE,
                DungeonMenus.Bag.INSTANCE, RpgMenu.INSTANCE,
                MountMenus.Home.INSTANCE, MountMenus.Barn.INSTANCE, MountMenus.Heal.INSTANCE,
                GolemMenus.Main.INSTANCE, GolemMenus.Tree.INSTANCE, GolemMenus.Choices.INSTANCE,
                DimMinerMenus.Entry.INSTANCE, DimMinerMenus.Summary.INSTANCE, MysticEnchantMenu.INSTANCE,
                FactionMenus.Overview.INSTANCE, FactionMenus.Bank.INSTANCE, FactionMenus.Claims.INSTANCE,
                FactionMenus.Ranking.INSTANCE, FactionMenus.Notation.INSTANCE,
                FactionMenus.Settings.INSTANCE,
                FactionTreeMenus.Tree.INSTANCE, FactionTreeMenus.Branch.INSTANCE, ChestExplorerMenu.INSTANCE,
                EnemyBaseMenu.INSTANCE, ReserveMenu.INSTANCE, FactionEventMenu.INSTANCE,
                fr.atraxterra.dohy.earth.NationMenu.INSTANCE,
                fr.atraxterra.dohy.faction.war.WarMenu.INSTANCE,
                fr.atraxterra.dohy.faction.org.OrgMenus.Org.INSTANCE,
                fr.atraxterra.dohy.faction.org.OrgMenus.CadrePage.INSTANCE,
                fr.atraxterra.dohy.faction.org.DiplomacyMenus.Hub.INSTANCE,
                fr.atraxterra.dohy.faction.org.DiplomacyMenus.NationPage.INSTANCE,
                fr.atraxterra.dohy.faction.enterprise.EnterpriseMenus.Hub.INSTANCE,
                fr.atraxterra.dohy.faction.enterprise.EnterpriseMenus.Catalog.INSTANCE,
                fr.atraxterra.dohy.faction.enterprise.EnterpriseMenus.Detail.INSTANCE,
                fr.atraxterra.dohy.faction.enterprise.EnterpriseMenus.Finance.INSTANCE,
                fr.atraxterra.dohy.faction.enterprise.EnterpriseMenus.Territory.INSTANCE,
                fr.atraxterra.dohy.faction.enterprise.EnterpriseMenus.SyndicatePage.INSTANCE,
                fr.atraxterra.dohy.faction.enterprise.EnterpriseMenus.Laws.INSTANCE,
                fr.atraxterra.dohy.underground.WorkstationMenus.Lab.INSTANCE,
                fr.atraxterra.dohy.underground.WorkstationMenus.Packaging.INSTANCE,
                fr.atraxterra.dohy.underground.WorkstationMenus.Arms.INSTANCE,
                fr.atraxterra.dohy.faction.market.WorldMarketMenu.INSTANCE,
                TrixiumMenu.INSTANCE, AchievementMenu.INSTANCE,
                fr.atraxterra.dohy.tech.TradingServerMenu.INSTANCE,
                fr.atraxterra.dohy.faction.tech.ResearchLabMenu.INSTANCE,
                fr.atraxterra.dohy.agri.GmoMachineMenu.INSTANCE,
                fr.atraxterra.dohy.agri.MillMenu.INSTANCE,
                fr.atraxterra.dohy.agri.TractorMenu.INSTANCE,
                fr.atraxterra.dohy.agri.SiloMenu.INSTANCE,
                fr.atraxterra.dohy.agri.BreadMachineMenu.INSTANCE,
                fr.atraxterra.dohy.auction.AuctionController.INSTANCE,
                fr.atraxterra.dohy.shop.SellController.INSTANCE,
                fr.atraxterra.dohy.spawn.SpawnDaily.INSTANCE,
                fr.atraxterra.dohy.spawn.SpawnPortals.INSTANCE,
                fr.atraxterra.dohy.farmland.FarmlandMenu.INSTANCE,
                fr.atraxterra.dohy.faction.absence.AbsenceMenu.INSTANCE,
                fr.atraxterra.dohy.guide.EncyclopediaMenu.INSTANCE,
                fr.atraxterra.dohy.pass.PassMenu.INSTANCE, fr.atraxterra.dohy.blackmarket.BlackMarketMenu.INSTANCE,
                fr.atraxterra.dohy.guide.plan.PlanMenu.INSTANCE, fr.atraxterra.dohy.jobboss.JobBossMenu.INSTANCE,
                fr.atraxterra.dohy.job.JobsMenu.INSTANCE, fr.atraxterra.dohy.arrakis.ShuttleMenu.INSTANCE,
                fr.atraxterra.dohy.arrakis.SpiceCounterMenu.INSTANCE, fr.atraxterra.dohy.arrakis.HarvesterMenu.INSTANCE,
                fr.atraxterra.dohy.arrakis.WaterMenu.INSTANCE, fr.atraxterra.dohy.arrakis.SietchMenu.INSTANCE,
                fr.atraxterra.dohy.nuclear.path.NtmPathMenu.INSTANCE,
                fr.atraxterra.dohy.grade.GradesController.INSTANCE,
                fr.atraxterra.dohy.farmbot.FarmBotMenu.INSTANCE,
                fr.atraxterra.dohy.earth.mine.MineMenu.INSTANCE);
    }
}

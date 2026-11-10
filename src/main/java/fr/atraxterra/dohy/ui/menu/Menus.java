package fr.atraxterra.dohy.ui.menu;

import fr.atraxterra.dohy.faction.FactionMenus;
import fr.atraxterra.dohy.faction.FactionTreeMenus;
import fr.atraxterra.dohy.faction.base.EnemyBaseMenu;
import fr.atraxterra.dohy.faction.reserve.ReserveMenu;
import fr.atraxterra.dohy.palamod.alchemist.MysticEnchantMenu;
import fr.atraxterra.dohy.palamod.dimminer.DimMinerMenus;
import fr.atraxterra.dohy.palamod.dungeon.DungeonMenus;
import fr.atraxterra.dohy.palamod.dungeon.RpgMenu;
import fr.atraxterra.dohy.palamod.golem.GolemMenus;
import fr.atraxterra.dohy.palamod.mount.MountMenus;
import fr.atraxterra.dohy.palamod.pillage.ChestExplorerMenu;

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
                FactionMenus.Ranking.INSTANCE, FactionMenus.Settings.INSTANCE,
                FactionTreeMenus.Tree.INSTANCE, FactionTreeMenus.Branch.INSTANCE, ChestExplorerMenu.INSTANCE,
                EnemyBaseMenu.INSTANCE, ReserveMenu.INSTANCE);
    }
}

package fr.atraxterra.dohy.ui.menu;

import java.util.List;

/** Tous les menus generiques, pour l'enregistrement de leurs vues. */
public final class Menus {

    private Menus() {
    }

    public static List<MenuController> all() {
        return List.of(HubController.INSTANCE, BankController.INSTANCE, ContractsController.INSTANCE,
                UpgradesController.INSTANCE, BoutiqueController.INSTANCE, CollectionsController.INSTANCE,
                MuseumController.INSTANCE);
    }
}

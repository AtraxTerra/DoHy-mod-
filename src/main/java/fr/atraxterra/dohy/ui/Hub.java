package fr.atraxterra.dohy.ui;

import fr.atraxterra.dohy.ui.menu.HubController;
import net.minecraft.server.level.ServerPlayer;

/**
 * Le menu principal.
 *
 * <p>Depuis le chapitre 21, c'est un ecran du kit d'interface
 * ({@link HubController}) ; la classe garde le point d'entree que les retours
 * des autres menus appellent.
 */
public final class Hub {

    private Hub() {
    }

    /** Ouvre le menu. Point d'entree de {@code /menu} et du bouton « Retour ». */
    public static void open(ServerPlayer player) {
        HubController.INSTANCE.open(player);
    }
}

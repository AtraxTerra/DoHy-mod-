package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * La borne NAVETTE : au spawn, le billet pour Arrakis ; sur l'aire de
 * l'astroport, le retour gratuit.
 */
public final class ShuttleMenu extends MenuController {

    public static final ShuttleMenu INSTANCE = new ShuttleMenu();
    private static final int SAND = 0xFFE8C27A;

    private ShuttleMenu() {
        super("arrakis_shuttle");
    }

    @Override
    public MenuView view(ServerPlayer player) {
        long balance = Economy.balance(player);
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("SOLDE", MoneyFormat.exact(balance) + " $", 0xFFFFFFFF),
                new MenuView.Stat("BILLET", MoneyFormat.exact(ArrakisPort.TICKET) + " $", SAND),
                new MenuView.Stat("RETOUR", "GRATUIT", 0xFF22E558));
        Card trip;
        if (!DoHyArrakis.available()) {
            trip = Card.of(new ItemStack(Items.SAND), "Arrakis", List.of("Indisponible sur ce serveur",
                    "NTM NEXT Space n'y est pas installe")).accent(SAND).state(MenuView.DISABLED);
        } else if (DoHyArrakis.onArrakis(player)) {
            trip = Card.of(new ItemStack(Items.COMPASS), "Retour au spawn", List.of("Gratuit",
                    "Retour : le passeur RETOUR du spawn"))
                    .accent(0xFF22E558).action("home");
        } else {
            trip = Card.of(new ItemStack(Items.FIREWORK_ROCKET), "Arrakis", List.of("Astroport d'Arrakeen",
                    "Billet : " + MoneyFormat.exact(ArrakisPort.TICKET) + " $", "Retour gratuit"))
                    .accent(SAND).badge("ALLER");
            trip = balance >= ArrakisPort.TICKET ? trip.action("go")
                    : trip.state(MenuView.DISABLED).tooltip(List.of("Il te manque "
                            + MoneyFormat.exact(ArrakisPort.TICKET - balance) + " $."));
        }
        return new MenuView(id(), "Navette", "Spawn — Arrakis", stats, List.of(
                new MenuView.Section("DEPART", "resources", MenuView.GRID, List.of(trip))), false);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if ("go".equals(key)) {
            ArrakisPort.depart(player);
        } else if ("home".equals(key)) {
            ArrakisPort.home(player);
        }
    }
}

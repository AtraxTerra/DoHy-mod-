package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.agri.TractorFuel;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'ecran de la moissonneuse : son reservoir, sa bouche, sa tremie. Il s'ouvre
 * accroupi + clic sur la machine, ou par la touche d'inventaire quand on la conduit.
 */
public final class HarvesterMenu extends MenuController {

    public static final HarvesterMenu INSTANCE = new HarvesterMenu();

    private static final double REACH = 8.0d;
    private static final int STACK = 64;
    private static final int GREEN = 0xFF22E558;
    private static final int GOLD = 0xFFFAC400;
    private static final int RED = 0xFFE5484D;
    private static final int SPICE = 0xFFE8742C;

    private final Map<UUID, UUID> open = new ConcurrentHashMap<>();

    private HarvesterMenu() {
        super("spice_harvester");
    }

    public void openFor(ServerPlayer player, SpiceHarvester harvester) {
        open.put(player.getUUID(), harvester.getUUID());
        open(player);
    }

    /** La moissonneuse ouverte par ce joueur, ou {@code null} s'il s'en est eloigne ou qu'elle n'est plus la. */
    private SpiceHarvester harvester(ServerPlayer player) {
        UUID id = open.get(player.getUUID());
        if (id == null || !(player.level() instanceof ServerLevel level)
                || !(level.getEntity(id) instanceof SpiceHarvester harvester) || !harvester.isAlive()
                || harvester.distanceToSqr(player) > REACH * REACH || !harvester.mayUse(player)) {
            return null;
        }
        return harvester;
    }

    @Override
    public MenuView view(ServerPlayer player) {
        SpiceHarvester harvester = harvester(player);
        int fuel = harvester == null ? 0 : harvester.fuel();
        int cargo = harvester == null ? 0 : harvester.cargo();
        boolean working = harvester != null && harvester.working();
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("CARBURANT", fuel + " / " + SpiceHarvester.TANK, fuel > 0 ? GREEN : RED),
                new MenuView.Stat("BOUCHE", working ? "OUVERTE" : "FERMEE", working ? GREEN : GOLD),
                new MenuView.Stat("TREMIE", cargo + " / " + SpiceHarvester.CAPACITY,
                        cargo >= SpiceHarvester.CAPACITY ? RED : SPICE));
        List<MenuView.Section> sections = new ArrayList<>();
        if (harvester != null) {
            sections.add(machine(player, harvester));
            sections.add(hopper(player, harvester));
            sections.add(new MenuView.Section("MODE D'EMPLOI", "", MenuView.TEXT, List.of(Card.of(ItemStack.EMPTY,
                    "Recolter l'epice", List.of(
                            "Roule sur le sable d'epice, la bouche ouverte (touche de saut).",
                            "- Elle avale trois blocs de large, jusqu'a deux sous le sol",
                            "- " + SpiceHarvester.YIELD + " melange par bloc, du sable ordinaire a la place",
                            "- Les champs d'epice : des taches rouille dans l'erg, et les eruptions",
                            "- Garee pres du comptoir d'Arrakeen, la cargaison s'y vend seule",
                            "! Le bruit de la machine porte loin dans le desert.")))));
        }
        String owner = harvester == null || harvester.ownerName().isEmpty() ? "" : " — " + harvester.ownerName();
        return new MenuView(id(), "Moissonneuse d'epice", "Saut : ouvrir ou fermer la bouche" + owner, stats,
                sections, true);
    }

    private static MenuView.Section machine(ServerPlayer player, SpiceHarvester harvester) {
        List<Card> cards = new ArrayList<>();
        boolean fuelInHand = TractorFuel.isFuel(player, InteractionHand.MAIN_HAND);
        Card fuel = Card.of(new ItemStack(Items.COAL_BLOCK), "Reservoir", List.of(
                        harvester.fuel() + " / " + SpiceHarvester.TANK + " unites",
                        fuelInHand ? "Clic : verser ce que tu tiens" : "Tiens un carburant en main"))
                .accent(harvester.fuel() > 0 ? GREEN : RED)
                .tooltip(List.of("Deux unites par bloc parcouru,", "quatre bouche ouverte,",
                        "une de plus par bloc d'epice avale", "Bloc de charbon, bidon de carburant,",
                        "seau de lave : clic sur la machine, l'objet en main"));
        cards.add(fuelInHand && harvester.fuel() < SpiceHarvester.TANK ? fuel.action("fuel|faire le plein")
                : fuel.state(MenuView.DISABLED));
        Card mouth = Card.of(new ItemStack(ArrakisContent.SPICE_SAND_ITEM.get()), "Bouche d'aspiration",
                List.of(harvester.working() ? "Ouverte : elle avale l'epice" : "Fermee : elle roule sans recolter",
                        "Clic : " + (harvester.working() ? "fermer" : "ouvrir")))
                .accent(harvester.working() ? GREEN : GOLD);
        cards.add(harvester.fuel() > 0 || harvester.working()
                ? mouth.action("work|" + (harvester.working() ? "fermer la bouche" : "ouvrir la bouche"))
                : mouth.state(MenuView.DISABLED));
        return new MenuView.Section("MACHINE", "resources", MenuView.GRID, cards);
    }

    private static MenuView.Section hopper(ServerPlayer player, SpiceHarvester harvester) {
        List<Card> cards = new ArrayList<>();
        int cargo = harvester.cargo();
        if (cargo <= 0) {
            cards.add(Card.of(new ItemStack(ArrakisContent.MELANGE.get()), "Tremie vide", List.of(
                    "La bouche la remplit", "Elle se vide seule pres du comptoir d'Arrakeen"))
                    .state(MenuView.DISABLED));
        } else {
            cards.add(Card.of(new ItemStack(ArrakisContent.MELANGE.get()), "Melange", List.of(
                            "Clic : prendre une pile", "Clic droit : tout prendre",
                            "Valeur au comptoir : ~" + MoneyFormat.exact(SpiceMarket.quote(player, cargo)) + " $"))
                    .accent(SPICE).badge("x" + cargo)
                    .action("take|prendre une pile").right("take_all|tout prendre"));
        }
        return new MenuView.Section("TREMIE", "resources", MenuView.GRID, cards);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        SpiceHarvester harvester = harvester(player);
        if (harvester == null) {
            UiNet.close(player);
            return;
        }
        switch (key) {
            case "fuel" -> {
                int gained = TractorFuel.drain(player, InteractionHand.MAIN_HAND,
                        SpiceHarvester.TANK - harvester.fuel());
                harvester.setFuel(harvester.fuel() + gained);
                if (gained > 0) {
                    play(player, SoundEvents.BUCKET_EMPTY, 1.0f);
                } else {
                    failure(player);
                }
            }
            case "work" -> {
                if (!harvester.working() && harvester.fuel() <= 0) {
                    failure(player);
                } else {
                    harvester.setWorking(!harvester.working());
                    success(player);
                }
            }
            case "take", "take_all" -> {
                int wanted = "take_all".equals(key) || button == 1 ? harvester.cargo() : STACK;
                int taken = harvester.takeCargo(wanted);
                if (taken > 0) {
                    SpiceInv.give(player, ArrakisContent.MELANGE.get(), taken);
                    success(player);
                } else {
                    failure(player);
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected void back(ServerPlayer player) {
        open.remove(player.getUUID());
        UiNet.close(player);
    }
}

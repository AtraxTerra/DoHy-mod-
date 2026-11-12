package fr.atraxterra.dohy.earth;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.FactionEdit;
import fr.atraxterra.dohy.faction.FactionMenus;
import fr.atraxterra.dohy.faction.event.EventRun;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'onglet « Pays » de la faction, sur une carte de la Terre : la capitale,
 * le pays vise et ses points, les voisins a prendre, et ce que tient chaque
 * nation rivale.
 */
public final class NationMenu extends MenuController {

    public static final NationMenu INSTANCE = new NationMenu();

    /** Combien de voisins a l'ecran : les moins chers. */
    private static final int SHOWN = 12;

    /** Le pays a rendre, arme par joueur jusqu'a cette date (deux clics). */
    private static final Map<UUID, long[]> ARMED = new ConcurrentHashMap<>();

    private NationMenu() {
        super("faction_nations");
    }

    @Override
    public void open(ServerPlayer player) {
        if (!FactionApi.has(player)) {
            FactionEdit.open(player);
            return;
        }
        super.open(player);
    }

    @Override
    protected void back(ServerPlayer player) {
        FactionMenus.Overview.INSTANCE.open(player);
    }

    private static boolean armed(ServerPlayer player, int country) {
        long[] armed = ARMED.get(player.getUUID());
        return armed != null && armed[0] == country && armed[1] > System.currentTimeMillis();
    }

    private static ItemStack banner(int rival) {
        if (rival < 0) {
            return new ItemStack(Items.BANNER.pick(net.minecraft.world.item.DyeColor.WHITE));
        }
        return new ItemStack(Items.BANNER.pick(EventRun.rivalPool().get(rival).color()));
    }

    private static int color(int rival) {
        return rival < 0 ? 0xFF55545C : 0xFF000000 | EventRun.rivalPool().get(rival).color().getTextColor();
    }

    @Override
    public MenuView view(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        FactionData data = FactionApi.data(player);
        if (!EarthNations.active()) {
            return new MenuView(id(), "Pays", "Il faut une carte de la Terre", List.of(), List.of(
                    new MenuView.Section("PAYS A PRENDRE", "resources", MenuView.GRID, List.of(
                            Card.of(new ItemStack(Items.MAP), "Pas de carte", List.of("Ce monde n'a pas de carte",
                                    "de la Terre (datapack)")).state(MenuView.DISABLED)))), true);
        }
        EarthNations.Nation nation = EarthNations.nation(player);
        List<Integer> mine = EarthNations.heldBy(player);

        // -- Ta nation
        List<Card> own = new ArrayList<>();
        if (nation.capital() == 0) {
            own.add(Card.of(FactionMenus.banner(data), "Capitale", List.of("Aucune",
                            "Pose-la dans un de tes claims"))
                    .accent(0xFFFAC400).badge("A POSER")
                    .tooltip(List.of("Tiens-toi dans un de tes claims, sur la carte de la Terre.",
                            "Son pays sera ta premiere cible.", "Clic : la poser ici"))
                    .action("capital|poser ici"));
        } else {
            own.add(Card.of(FactionMenus.banner(data), "Capitale", List.of(EarthNations.countryName(nation.capital()),
                            nation.capitalX() + ", " + nation.capitalZ()))
                    .accent(0xFFEF3826)
                    .tooltip(List.of("En rouge sur la carte du monde du spawn.",
                            mine.isEmpty() ? "Tant que tu n'as aucun pays, elle se deplace ou tu veux"
                                    : "Elle se deplace dans un pays que tu tiens",
                            "Clic : la poser ici"))
                    .action("capital|deplacer ici"));
        }
        List<String> held = new ArrayList<>();
        for (int id : mine) {
            held.add(EarthNations.countryName(id));
        }
        own.add(Card.of(new ItemStack(Items.FILLED_MAP), mine.size() + " pays", List.of(
                        mine.isEmpty() ? "Aucun pour l'instant" : NationCommand.names(mine, 2),
                        "+" + mine.size() + " claim(s)"))
                .accent(0xFFFAC400).tooltip(held.isEmpty() ? List.of("Chaque pays tenu ajoute un claim",
                        "et compte pour quatre claims dans la notation") : held.subList(0, Math.min(16, held.size()))));
        int target = nation.target();
        if (target == 0) {
            own.add(Card.of(new ItemStack(Items.IRON_SWORD), "Aucune cible", List.of(
                    nation.capital() == 0 ? "Pose d'abord ta capitale" : "Vise un voisin ci-dessous",
                    "Un pays a la fois")).state(MenuView.DISABLED));
        } else {
            EarthNations.Holder holder = EarthNations.holder(server, target);
            long cost = EarthNations.cost(server, target);
            long points = nation.pointsOn(target);
            own.add(Card.of(new ItemStack(Items.IRON_SWORD), EarthNations.countryName(target), List.of(
                            "Aux mains de " + holder.name(),
                            MoneyFormat.exact(points) + " / " + MoneyFormat.exact(cost) + " points"))
                    .accent(color(holder.rival())).badge("CIBLE").progress(points, cost)
                    .tooltip(List.of("Aux events de faction, " + holder.name() + " est de la partie.",
                            "Finis devant eux : l'ELO gagne compte en points.",
                            "Le pays tombe quand ses points sont reunis.",
                            "Clic : renoncer (les points restent acquis)"))
                    .action("drop|renoncer"));
        }

        // -- Ici
        List<Card> here = new ArrayList<>();
        EarthData earth = EarthData.get();
        EarthData.Country under = earth.covers(player.level()) ? earth.countryAt(player.getBlockX(), player.getBlockZ())
                : null;
        if (under == null) {
            here.add(Card.of(new ItemStack(Items.COMPASS), earth.covers(player.level()) ? "En mer" : "Hors de la carte",
                    List.of("Aucun pays sous tes pieds", "")).state(MenuView.DISABLED));
        } else {
            EarthNations.Holder holder = EarthNations.holder(server, under.id());
            Card card = Card.of(holder.player() ? FactionMenus.banner(data) : banner(holder.rival()), under.name(),
                    List.of("Aux mains de " + EarthNations.holderName(player, under.id()),
                            MoneyFormat.exact(EarthNations.cost(server, under.id())) + " points"))
                    .accent(holder.player() ? 0xFF22E558 : color(holder.rival()));
            if (holder.ownedBy(player.getUUID())) {
                boolean armed = armed(player, under.id());
                card = card.badge("A TOI").tooltip(List.of("Deux clics : le rendre a la nation de sa region"))
                        .action("abandon:" + under.id() + "|" + (armed ? "confirmer" : "rendre"));
                if (armed) {
                    card = card.badge("RENDRE ?");
                }
            } else if (target == under.id()) {
                card = card.badge("CIBLE");
            } else if (!holder.player() && EarthNations.reachable(player, under.id())) {
                card = card.action("target:" + under.id() + "|viser");
            } else {
                card = card.state(MenuView.DISABLED).tooltip(List.of(holder.player() ? "La faction d'un joueur"
                        : mine.isEmpty() ? "Ton premier pays est celui de ta capitale"
                        : "Pas voisin de tes terres"));
            }
            here.add(card);
        }

        // -- Voisins a prendre
        List<Card> next = new ArrayList<>();
        List<Integer> targets = EarthNations.targets(player);
        for (int id : targets.subList(0, Math.min(SHOWN, targets.size()))) {
            EarthNations.Holder holder = EarthNations.holder(server, id);
            long cost = EarthNations.cost(server, id);
            long points = nation.pointsOn(id);
            Card card = Card.of(banner(holder.rival()), EarthNations.countryName(id), List.of(holder.name(),
                            MoneyFormat.exact(cost) + " points"))
                    .accent(color(holder.rival()));
            if (points > 0) {
                card = card.progress(points, cost);
            }
            next.add(id == target ? card.badge("CIBLE") : card.action("target:" + id + "|viser"));
        }
        if (next.isEmpty()) {
            next.add(Card.of(new ItemStack(Items.MAP), "Rien a viser", List.of(
                    nation.capital() == 0 ? "Pose d'abord ta capitale" : "Tous tes voisins sont pris", ""))
                    .state(MenuView.DISABLED));
        }

        // -- Nations rivales
        List<Card> rivals = new ArrayList<>();
        List<List<Integer>> holdings = EarthNations.rivalHoldings(server);
        List<EventRun.Rival> pool = EventRun.rivalPool();
        for (int i = 0; i < pool.size(); i++) {
            List<Integer> countries = holdings.get(i);
            List<String> tip = new ArrayList<>();
            for (int id : countries.subList(0, Math.min(12, countries.size()))) {
                tip.add(EarthNations.countryName(id));
            }
            if (countries.size() > 12) {
                tip.add("et " + (countries.size() - 12) + " autres");
            }
            rivals.add(Card.of(banner(i), pool.get(i).name(), List.of(countries.size() + " pays",
                            countries.isEmpty() ? "Plus rien" : EarthNations.countryName(countries.get(0))))
                    .accent(color(i)).tooltip(tip)
                    .state(countries.isEmpty() ? MenuView.DONE : MenuView.NORMAL));
        }

        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("PAYS", String.valueOf(mine.size()), 0xFFFAC400),
                new MenuView.Stat("CIBLE", target == 0 ? "AUCUNE" : EarthNations.countryName(target).toUpperCase(),
                        target == 0 ? 0xFFFFFFFF : 0xFFEF3826),
                new MenuView.Stat("POINTS", target == 0 ? "-" : MoneyFormat.shortForm(nation.pointsOn(target)) + " / "
                        + MoneyFormat.shortForm(EarthNations.cost(server, target)), 0xFF22E558));
        return new MenuView(id(), "Pays a prendre", "Prends les pays de la Terre aux nations rivales", stats, List.of(
                new MenuView.Section("TA NATION", "resources", MenuView.GRID, own),
                new MenuView.Section("ICI", "craft", MenuView.GRID, here),
                new MenuView.Section("VOISINS A PRENDRE", "upgrade", MenuView.GRID, next),
                new MenuView.Section("NATIONS RIVALES", "resources", MenuView.GRID, rivals)), true);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if ("capital".equals(key)) {
            report(player, EarthNations.setCapital(player), "Capitale posee ici.");
        } else if ("drop".equals(key)) {
            report(player, EarthNations.dropTarget(player), "Plus de cible : les points restent acquis.");
        } else if (key.startsWith("target:")) {
            int id = Integer.parseInt(key.substring(7));
            String refusal = EarthNations.target(player, id);
            report(player, refusal, refusal == null ? "Cible : " + EarthNations.countryName(id) + ", aux mains de "
                    + EarthNations.holderName(player, id) + "." : "");
        } else if (key.startsWith("abandon:")) {
            int id = Integer.parseInt(key.substring(8));
            if (armed(player, id)) {
                ARMED.remove(player.getUUID());
                report(player, EarthNations.abandon(player, id), EarthNations.countryName(id) + " rendu.");
            } else {
                ARMED.put(player.getUUID(), new long[] {id, System.currentTimeMillis() + 5000L});
            }
        }
    }
}

package fr.atraxterra.dohy.faction.reserve;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.FactionEdit;
import fr.atraxterra.dohy.faction.FactionMenus;
import fr.atraxterra.dohy.faction.FactionPerks;
import fr.atraxterra.dohy.palamod.reserve.PalaReserve;
import fr.atraxterra.dohy.ui.menu.MenuController;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'onglet « Reserve d'or » de la faction (lot 22e) : le cube et ce qu'il
 * permet de placer, les plans des trois paliers, le montant puis la duree d'un
 * placement, et les placements en cours (recolter, ou retirer avant terme).
 */
public final class ReserveMenu extends MenuController {

    public static final ReserveMenu INSTANCE = new ReserveMenu();

    /** Les montants proposes ; 0 : le plus possible. */
    private static final long[] AMOUNTS = {100_000L, 1_000_000L, 10_000_000L, 0L};
    /** Le montant choisi par joueur (indice dans {@link #AMOUNTS}). */
    private static final Map<UUID, Integer> CHOSEN = new ConcurrentHashMap<>();
    /** Le retrait avant terme, arme pour un placement jusqu'a une date. */
    private static final Map<UUID, long[]> ARMED = new ConcurrentHashMap<>();

    private ReserveMenu() {
        super("faction_reserve");
    }

    @Override
    public void open(ServerPlayer player) {
        if (!FactionApi.has(player)) {
            FactionEdit.open(player);
            return;
        }
        GoldReserves.refresh(player);
        super.open(player);
    }

    @Override
    protected void back(ServerPlayer player) {
        FactionMenus.Overview.INSTANCE.open(player);
    }

    private static int chosen(ServerPlayer player) {
        return CHOSEN.getOrDefault(player.getUUID(), AMOUNTS.length - 1);
    }

    private static boolean armed(ServerPlayer player, int index) {
        long[] armed = ARMED.get(player.getUUID());
        return armed != null && armed[0] == index && armed[1] > System.currentTimeMillis();
    }

    /** Les blocs d'un cube de cote n : coffres-forts, vitres, or liquide au plus. */
    private static int[] plan(int n) {
        int core = (n - 2) * (n - 2) * (n - 2);
        int windows = 4 * (n - 2) * (n - 2);
        return new int[]{n * n * n - core - windows, windows, core};
    }

    @Override
    public MenuView view(ServerPlayer player) {
        FactionData data = FactionApi.data(player);
        FactionPerks perks = FactionApi.perks(player);
        GoldReserve reserve = GoldReserves.of(player);
        long capacity = GoldReserves.capacity(player, reserve);
        long now = System.currentTimeMillis();

        List<Card> mine = new ArrayList<>();
        if (reserve.exists()) {
            int[] plan = plan(reserve.size());
            mine.add(Card.of(new ItemStack(PalaReserve.LIQUID_GOLD.get()), "Reserve de palier " + reserve.tier(),
                            List.of(reserve.gold() + " / " + plan[2] + " or liquide",
                                    MoneyFormat.exact(capacity) + " $ a placer"))
                    .accent(0xFFFAC400).badge(reserve.size() + "^3")
                    .progress(reserve.invested(), Math.max(1L, capacity))
                    .tooltip(List.of("En " + reserve.origin().toShortString() + " (" + reserve.dimension() + ")",
                            MoneyFormat.exact(perks.goldValue()) + " $ par or liquide",
                            "Interets x" + Math.max(1, perks.interest()),
                            "Seul un wither la detruit : l'argent place serait perdu.")));
        } else {
            mine.add(Card.of(new ItemStack(PalaReserve.FACTION_SAFE.get()), "Aucune reserve", List.of(
                            "Bats un cube ci-dessous,", "puis clic droit mains nues"))
                    .state(MenuView.DISABLED)
                    .tooltip(List.of("Aretes, dessus et dessous : coffres-forts de faction.",
                            "Centre des quatre flancs : vitres de coffre-fort.",
                            "Coeur : or liquide (ou vide). Le tout dans tes claims.",
                            "Clic droit mains nues sur un coffre-fort pour l'ouvrir.")));
        }

        List<Card> plans = new ArrayList<>();
        for (int n = 5; n <= 7; n++) {
            int tier = n - 4;
            int[] plan = plan(n);
            Card card = Card.of(new ItemStack(tier == 1 ? PalaReserve.FACTION_SAFE.get()
                            : tier == 2 ? PalaReserve.VAULT_GLASS.get() : PalaReserve.LIQUID_GOLD.get()),
                    "Palier " + tier + " : " + n + " x " + n + " x " + n, List.of(
                            plan[0] + " coffres-forts, " + plan[1] + " vitres",
                            "Jusqu'a " + plan[2] + " or liquide : " + MoneyFormat.shortForm(plan[2] * perks.goldValue())
                                    + " $"));
            plans.add(tier <= perks.reserveTier() ? card.accent(0xFFFAC400)
                    .badge(reserve.tier() == tier ? "BATIE" : "")
                    : card.state(MenuView.DISABLED).tooltip(List.of("Branche Economie de l'arbre de faction")));
        }

        List<Card> amounts = new ArrayList<>();
        int chosen = chosen(player);
        for (int i = 0; i < AMOUNTS.length; i++) {
            long amount = AMOUNTS[i];
            String title = amount == 0 ? "Le plus possible" : MoneyFormat.exact(amount) + " $";
            amounts.add(Card.of(new ItemStack(amount == 0 ? Items.GOLD_BLOCK : amount >= 10_000_000L
                            ? Items.GOLD_INGOT : Items.GOLD_NUGGET), title, List.of(amount == 0
                            ? "Place et banque permettant" : "Depuis la banque de faction",
                            "Banque : " + MoneyFormat.shortForm(data.bank()) + " $"))
                    .accent(i == chosen ? 0xFF22E558 : 0xFF55545C).badge(i == chosen ? "CHOISI" : "")
                    .action("amount:" + i + "|choisir"));
        }

        List<Card> terms = new ArrayList<>();
        int multiplier = Math.max(1, perks.interest());
        for (GoldReserves.Term term : GoldReserves.Term.values()) {
            int permille = term.permille() * multiplier;
            Card card = Card.of(new ItemStack(Items.CLOCK), "Placer " + term.label(), List.of(
                            "+" + permille / 10 + (permille % 10 == 0 ? "" : "," + permille % 10) + " % a l'echeance",
                            multiplier > 1 ? "Interets x" + multiplier : "Plus c'est long, plus ca rapporte"))
                    .tooltip(List.of("Montant choisi : " + (AMOUNTS[chosen] == 0 ? "le plus possible"
                                    : MoneyFormat.exact(AMOUNTS[chosen]) + " $"),
                            "Retrait avant l'echeance : la mise moins " + GoldReserves.PENALTY_PERCENT + " %."));
            terms.add(reserve.exists() ? card.accent(0xFFFAC400).action("invest:" + term.name() + "|placer")
                    : card.state(MenuView.DISABLED));
        }

        List<Card> running = new ArrayList<>();
        List<GoldReserve.Investment> investments = reserve.investments();
        for (int i = 0; i < investments.size(); i++) {
            GoldReserve.Investment investment = investments.get(i);
            boolean ripe = investment.ripe(now);
            long left = Math.max(0L, investment.end() - now);
            Card card = Card.of(new ItemStack(ripe ? Items.GOLD_BLOCK : Items.GOLD_INGOT),
                    MoneyFormat.exact(investment.amount()) + " $", List.of(
                            investment.days() + " j · +" + MoneyFormat.exact(investment.payout() - investment.amount())
                                    + " $",
                            ripe ? "A recolter !" : "Encore " + duration(left)))
                    .progress(now - investment.start(), investment.end() - investment.start());
            if (ripe) {
                card = card.accent(0xFF22E558).badge("PRET").action("harvest:" + i + "|recolter");
            } else {
                boolean armed = armed(player, i);
                card = card.accent(armed ? 0xFFEF3826 : 0xFFFAC400)
                        .right("withdraw:" + i + "|" + (armed ? "confirmer le retrait" : "retirer (-"
                                + GoldReserves.PENALTY_PERCENT + " %)"));
            }
            running.add(card);
        }
        if (running.isEmpty()) {
            running.add(Card.of(new ItemStack(Items.BARRIER), "Aucun placement", List.of(
                    "Choisis un montant,", "puis une duree")).state(MenuView.DISABLED));
        }

        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("PALIER", reserve.exists() ? String.valueOf(reserve.tier()) : "-", 0xFFFAC400),
                new MenuView.Stat("PLACE", MoneyFormat.shortForm(reserve.invested()) + " / "
                        + MoneyFormat.shortForm(capacity), 0xFFFFFFFF),
                new MenuView.Stat("BANQUE", MoneyFormat.shortForm(data.bank()), 0xFF22E558));
        return new MenuView(id(), "Reserve d'or", "Places l'argent de la faction, recolte les interets", stats,
                List.of(new MenuView.Section("TA RESERVE", "resources", MenuView.GRID, mine),
                        new MenuView.Section("PLACEMENTS", "upgrade", MenuView.GRID, running),
                        new MenuView.Section("MONTANT", "resources", MenuView.GRID, amounts),
                        new MenuView.Section("DUREE", "upgrade", MenuView.GRID, terms),
                        new MenuView.Section("PLANS", "resources", MenuView.GRID, plans)), true);
    }

    private static String duration(long millis) {
        long minutes = millis / 60_000L;
        long days = minutes / 1440L;
        long hours = minutes / 60L % 24L;
        if (days > 0) {
            return days + " j " + hours + " h";
        }
        return hours > 0 ? hours + " h " + minutes % 60L + " min" : Math.max(1L, minutes) + " min";
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if (key.startsWith("amount:")) {
            CHOSEN.put(player.getUUID(), Integer.parseInt(key.substring(7)));
            success(player);
        } else if (key.startsWith("invest:")) {
            GoldReserves.Term term = GoldReserves.Term.valueOf(key.substring(7));
            report(player, GoldReserves.invest(player, AMOUNTS[chosen(player)], term),
                    "Placement fait pour " + term.label() + ".");
        } else if (key.startsWith("harvest:")) {
            report(player, GoldReserves.harvest(player, Integer.parseInt(key.substring(8))), "Placement recolte.");
        } else if (key.startsWith("withdraw:")) {
            int index = Integer.parseInt(key.substring(9));
            if (armed(player, index)) {
                ARMED.remove(player.getUUID());
                report(player, GoldReserves.withdraw(player, index), "Placement retire, moins "
                        + GoldReserves.PENALTY_PERCENT + " %.");
            } else {
                ARMED.put(player.getUUID(), new long[]{index, System.currentTimeMillis() + 5000L});
            }
        }
    }
}

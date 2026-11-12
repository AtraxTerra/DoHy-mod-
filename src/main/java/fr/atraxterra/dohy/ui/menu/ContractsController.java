package fr.atraxterra.dohy.ui.menu;

import fr.atraxterra.dohy.boost.Boosters;
import fr.atraxterra.dohy.contract.ActiveContract;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.contract.ContractConfig;
import fr.atraxterra.dohy.contract.ContractData;
import fr.atraxterra.dohy.contract.ContractDisplay;
import fr.atraxterra.dohy.contract.ContractType;
import fr.atraxterra.dohy.contract.TrixiumOrder;
import fr.atraxterra.dohy.ui.Gui;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Les contrats : ceux du jour, la commande de trixium, celui de la semaine, la demande et la premiere vente. */
public final class ContractsController extends MenuController {

    public static final ContractsController INSTANCE = new ContractsController();

    private ContractsController() {
        super("contracts");
    }

    /** Un delai en minutes, rendu lisible : « 2 j 5 h », « 5 h 12 min ». */
    public static String delay(long minutes) {
        long hours = minutes / 60L;
        if (hours >= 24) {
            return (hours / 24L) + " j " + (hours % 24L) + " h";
        }
        return hours > 0 ? hours + " h " + (minutes % 60L) + " min" : minutes + " min";
    }

    @Override
    public MenuView view(ServerPlayer player) {
        ContractApi.rollIfDue(player);
        ContractData data = ContractApi.data(player);
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("NIVEAU GLOBAL", String.valueOf(ContractApi.globalLevel(player)), 0xFF22E558),
                new MenuView.Stat("NOUVEAUX DANS", delay(ContractApi.minutesUntilDailyReset()), 0xFFFFFFFF),
                new MenuView.Stat("HEBDO DANS", delay(ContractApi.minutesUntilWeeklyReset()), 0xFFFFFFFF));

        List<Card> daily = new ArrayList<>();
        for (ActiveContract contract : data.daily()) {
            daily.add(card(player, contract));
        }
        if (daily.isEmpty()) {
            daily.add(Card.of(new ItemStack(Items.PAPER), "Aucun contrat", List.of("Le tirage a lieu a la connexion"))
                    .state(MenuView.DISABLED));
        }
        List<Card> weekly = new ArrayList<>();
        for (ActiveContract contract : data.weekly()) {
            weekly.add(card(player, contract));
        }
        if (weekly.isEmpty()) {
            weekly.add(Card.of(new ItemStack(Items.PAPER), "Aucun contrat hebdomadaire",
                    List.of("Le tirage a lieu a la connexion")).state(MenuView.DISABLED));
        }

        List<Card> order = List.of(order(player));

        List<Card> bonus = new ArrayList<>();
        Identifier demand = ContractApi.demandItem(player);
        ContractConfig.Config config = ContractConfig.config();
        bonus.add(demand == null
                ? Card.of(new ItemStack(Items.BELL), "Demande du jour", List.of("Aucune demande en cours",
                        "Tiree a la connexion")).state(MenuView.DISABLED)
                : Card.of(new ItemStack(Items.BELL), "Demande du jour", List.of(
                        MenuView.text(Gui.displayName(demand)), "x" + config.demand().multiplier() + " a la vente"))
                .accent(0xFFFAC400).tooltip(List.of("Change dans " + delay(ContractApi.minutesUntilDailyReset()),
                        "Se cumule avec le booster")));
        boolean firstSale = ContractApi.firstSaleAvailable(player);
        bonus.add(Card.of(new ItemStack(Items.EMERALD), "Premiere vente", List.of(
                        firstSale ? "Disponible · x" + config.firstSale().multiplier() : "Deja utilisee aujourd'hui",
                        "S'applique a ta premiere vente"))
                .accent(0xFF22E558).state(firstSale ? MenuView.NORMAL : MenuView.DISABLED));
        double booster = Boosters.active(player);
        long hours = data.playtimeMinutes() / 60L;
        bonus.add(Card.of(new ItemStack(Items.EXPERIENCE_BOTTLE), "Progression", List.of(
                        "Temps de jeu " + hours + " h " + data.playtimeMinutes() % 60L + " min",
                        booster > 1 ? "Booster x" + (long) booster + " · " + Boosters.remaining(player) + " min"
                                : "Aucun booster actif"))
                .accent(0xFF2F5BEA)
                .progress(ContractApi.globalXpIntoLevel(player), ContractApi.globalXpPerLevel()));

        return new MenuView(id(), "Contrats", "Ils se terminent tout seuls ; seule la commande se livre", stats,
                List.of(
                new MenuView.Section("CONTRATS DU JOUR", "resources", MenuView.ROWS, daily),
                new MenuView.Section("COMMANDE DE TRIXIUM", "resources", MenuView.ROWS, order),
                new MenuView.Section("CONTRAT DE LA SEMAINE", "upgrade", MenuView.ROWS, weekly),
                new MenuView.Section("BONUS", "craft", MenuView.GRID, bonus)), true);
    }

    private static Card card(ServerPlayer player, ActiveContract contract) {
        String progress = contract.kind() == ContractType.SELL_VALUE
                ? MoneyFormat.shortForm(contract.progress()) + " / " + MoneyFormat.shortForm(contract.amount()) + " $"
                : MoneyFormat.exact(contract.progress()) + " / " + MoneyFormat.exact(contract.amount());
        long reward = ContractApi.rewardFor(player, contract);
        Card card = Card.of(new ItemStack(ContractDisplay.icon(contract)), ContractDisplay.label(contract),
                        List.of(progress, contract.done() ? "Termine, recompense versee" : "Recompense "
                                + MoneyFormat.exact(reward)))
                .accent(contract.weekly() ? 0xFFEC4FB5 : 0xFF2F5BEA)
                .badge(MoneyFormat.shortForm(reward) + " $")
                .progress(contract.progress(), contract.amount());
        return contract.done() ? card.state(MenuView.DONE) : card;
    }

    /**
     * La commande de trixium : l'objet du jour, ce qui est livre, ce qu'elle paie.
     *
     * <p>C'est la seule carte de l'ecran qui se clique : elle prend dans le sac
     * ce que le joueur porte de l'objet demande.
     */
    private static Card order(ServerPlayer player) {
        TrixiumOrder.refresh(player);
        ContractConfig.OrderEntry entry = TrixiumOrder.today();
        if (entry == null) {
            return Card.of(new ItemStack(Items.PAPER), "Aucune commande", List.of("Le fichier n'en propose aucune"))
                    .state(MenuView.DISABLED);
        }
        ContractConfig.TrixiumOrderConfig config = ContractConfig.config().trixiumOrder();
        Item item = BuiltInRegistries.ITEM.getOptional(entry.item()).orElse(Items.PAPER);
        // Un nom d'objet est traduit par le client : la phrase entiere doit etre un composant,
        // un nom colle a du texte brut arriverait en JSON.
        Component rewardName = Gui.displayName(config.rewardItem());
        String reward = MenuView.text(Component.literal(config.reward() + " ").append(rewardName));
        int goal = TrixiumOrder.streakGoal();
        int streak = TrixiumOrder.streak(player);
        String change = "Change dans " + delay(TrixiumOrder.minutesUntilChange());
        List<String> tooltip = new ArrayList<>();
        tooltip.add("L'objet demande change chaque jour a " + config.resetHour() + " h");
        tooltip.add("Une livraison partielle compte");
        if (goal > 0) {
            tooltip.add(goal + " commandes de suite : le God Villager apparait");
            tooltip.add("Un jour manque remet la serie a zero, rien d'autre");
        }
        String series = goal > 0 ? "  ·  serie " + streak + " / " + goal : "";
        String title = MenuView.text(Component.literal("Livrer " + entry.amount() + " ")
                .append(Gui.displayName(entry.item())));

        if (TrixiumOrder.done(player)) {
            return Card.of(new ItemStack(item), title, List.of(
                            MenuView.text(Component.literal(entry.amount() + " / " + entry.amount()
                                    + "  ·  livree, " + config.reward() + " ").append(rewardName).append(" verses")),
                            change + series))
                    .accent(0xFF27C4E8).badge(reward).progress(entry.amount(), entry.amount())
                    .state(MenuView.DONE).tooltip(tooltip);
        }
        int delivered = TrixiumOrder.delivered(player);
        int carried = TrixiumOrder.carried(player, entry.item());
        Card card = Card.of(new ItemStack(item), title, List.of(
                        delivered + " / " + entry.amount() + "  ·  tu en portes " + carried,
                        change + series))
                .accent(0xFF27C4E8).badge(reward).progress(delivered, entry.amount()).tooltip(tooltip);
        return carried > 0 ? card.action("order|livrer ce que tu portes") : card;
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        if ("order".equals(key)) {
            String refusal = TrixiumOrder.deliver(player);
            if (refusal == null) {
                success(player);
            } else {
                report(player, refusal, "");
            }
        }
    }
}

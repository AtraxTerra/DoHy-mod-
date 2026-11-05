package fr.atraxterra.dohy.contract;

import fr.atraxterra.dohy.boost.Boosters;
import fr.atraxterra.dohy.ui.DisplayMenu;
import fr.atraxterra.dohy.ui.Gui;
import fr.atraxterra.dohy.ui.Hub;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Les contrats, la demande du jour et le niveau global, sur un ecran.
 *
 * <p>Ecran de lecture : un contrat se termine tout seul des que sa jauge est
 * pleine, il n'y a donc rien a reclamer. Ce que le joueur vient chercher ici,
 * c'est « que me reste-t-il a faire ce soir » — d'ou les trois quotidiens au
 * centre, l'hebdo en dessous, et les deux bonus de rythme encadres.
 */
public final class ContractsMenu extends DisplayMenu {

    private static final int SLOT_HEADER = 4;
    private static final int SLOT_DEMAND = 13;
    private static final int[] SLOTS_DAILY = {20, 22, 24};
    private static final int SLOT_WEEKLY = 31;
    private static final int SLOT_FIRST_SALE = 40;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_CLOSE = 49;

    public ContractsMenu(int containerId, Inventory inventory, ServerPlayer player) {
        super(containerId, inventory, player, 6);
        refresh();
    }

    @Override
    protected void onClick(int slot, int button, boolean shift) {
        if (slot == SLOT_BACK) {
            click(0.7f);
            Hub.open(player);
        } else if (slot == SLOT_CLOSE) {
            click(0.6f);
            player.closeContainer();
        }
    }

    @Override
    protected void build() {
        frame();

        ContractData data = ContractApi.data(player);
        set(SLOT_HEADER, header(data));
        set(SLOT_DEMAND, demand(data));
        set(SLOT_FIRST_SALE, firstSale(data));

        List<ActiveContract> daily = data.daily();
        for (int index = 0; index < SLOTS_DAILY.length; index++) {
            set(SLOTS_DAILY[index], index < daily.size()
                    ? card(daily.get(index), "Contrat du jour " + (index + 1))
                    : Gui.button(Items.PAPER, "Aucun contrat", Gui.lore(
                            Gui.hint("le tirage a lieu a la connexion"))));
        }

        List<ActiveContract> weekly = data.weekly();
        set(SLOT_WEEKLY, weekly.isEmpty()
                ? Gui.button(Items.PAPER, "Aucun contrat hebdomadaire", Gui.lore(
                        Gui.hint("le tirage a lieu a la connexion")))
                : card(weekly.get(0), "Contrat de la semaine"));

        set(SLOT_BACK, Gui.back("le menu"));
        set(SLOT_CLOSE, Gui.close());
    }

    /**
     * La vignette d'un contrat.
     *
     * <p>Jamais d'identifiant : l'intitule vient du datapack, l'icone de l'item
     * vise. C'est tout l'objet de {@link ContractDisplay}.
     */
    private ItemStack card(ActiveContract contract, String kind) {
        boolean done = contract.done();

        List<Component> lore = new ArrayList<>();
        lore.add(Gui.hint(kind));
        lore.add(Gui.blank());
        lore.add(ContractDisplay.progress(contract));
        lore.add(Gui.blank());
        lore.add(Gui.field("recompense",
                MoneyFormat.exact(ContractApi.rewardFor(player, contract))));
        int level = ContractApi.globalLevel(player);
        if (level > 0) {
            lore.add(Gui.hint("  niveau global " + level + " compris"));
        }
        lore.add(Gui.blank());
        lore.add(done
                ? Gui.good("Termine — recompense versee")
                : Gui.hint("se termine tout seul, rien a reclamer"));

        Component name = Component.literal(ContractDisplay.label(contract))
                .withStyle(done ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        return Gui.button(ContractDisplay.icon(contract), name, lore);
    }

    private ItemStack header(ContractData data) {
        long hours = data.playtimeMinutes() / 60L;
        long minutes = data.playtimeMinutes() % 60L;
        double booster = Boosters.active(player);

        return Gui.button(Items.EXPERIENCE_BOTTLE, "Progression", Gui.lore(
                Gui.field("niveau global", String.valueOf(ContractApi.globalLevel(player))),
                Gui.bar(ContractApi.globalXpIntoLevel(player),
                        ContractApi.globalXpPerLevel(), 20),
                Gui.blank(),
                Gui.field("nouveaux contrats dans",
                        delay(ContractApi.minutesUntilDailyReset())),
                Gui.field("nouvel hebdo dans",
                        delay(ContractApi.minutesUntilWeeklyReset())),
                Gui.hint("en temps reel : le compte tourne monde ferme"),
                Gui.blank(),
                Gui.field("temps de jeu", hours + " h " + minutes + " min"),
                Gui.blank(),
                booster > 1.0d
                        ? Gui.good("booster x" + (long) booster + " · "
                                + Boosters.remaining(player) + " min de jeu restantes")
                        : Gui.hint("aucun booster actif")));
    }

    /** Un delai en minutes, rendu lisible : « 2 j 5 h », « 5 h 12 min ». */
    static String delay(long minutes) {
        long hours = minutes / 60L;
        if (hours >= 24) {
            return (hours / 24L) + " j " + (hours % 24L) + " h";
        }
        return hours > 0
                ? hours + " h " + (minutes % 60L) + " min"
                : minutes + " min";
    }

    private ItemStack demand(ContractData data) {
        ContractConfig.Demand config = ContractConfig.config().demand();
        Identifier item = ContractApi.demandItem(player);
        if (item == null) {
            return Gui.button(Items.BELL, "Demande du jour", Gui.lore(
                    Gui.hint("aucune demande en cours"),
                    Gui.hint("la prochaine est tiree a la connexion")));
        }

        return Gui.button(Items.BELL, "Demande du jour", Gui.lore(
                Gui.displayName(item).copy().withStyle(ChatFormatting.AQUA),
                Gui.blank(),
                Gui.good("x" + config.multiplier() + " a la vente"),
                Gui.hint("change dans " + delay(ContractApi.minutesUntilDailyReset())),
                Gui.blank(),
                Gui.hint("se cumule avec le booster")));
    }

    private ItemStack firstSale(ContractData data) {
        ContractConfig.FirstSale config = ContractConfig.config().firstSale();
        boolean available = ContractApi.firstSaleAvailable(player);
        return Gui.button(Items.EMERALD, "Bonus de premiere vente", Gui.lore(
                available
                        ? Gui.good("disponible · x" + config.multiplier())
                        : Gui.hint("deja utilise aujourd'hui"),
                Gui.blank(),
                Gui.hint("s'applique a ta premiere vente"),
                available
                        ? Gui.hint("remis a zero chaque jour")
                        : Gui.hint("de retour dans "
                                + delay(ContractApi.minutesUntilDailyReset())),
                Gui.blank(),
                Gui.hint("pas de serie a entretenir : rater un soir"),
                Gui.hint("ne coute rien")));
    }
}

package fr.atraxterra.dohy.ui.menu;

import fr.atraxterra.dohy.economy.Bank;
import fr.atraxterra.dohy.economy.DeathPenalty;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.PlayerEconomy;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.MenuView.Card;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * La banque : deposer ou retirer une part du solde, l'assurance, et le
 * rendement a ce solde. Memes regles que l'ancien ecran en coffre.
 */
public final class BankController extends MenuController {

    public static final BankController INSTANCE = new BankController();

    private BankController() {
        super("bank");
    }

    /** Arrondi vers le bas, avec un minimum de 1 des que le solde le permet. */
    private static long fraction(long total, double part) {
        return total <= 0 ? 0L : Math.max(1L, (long) Math.floor(total * part));
    }

    @Override
    public MenuView view(ServerPlayer player) {
        PlayerEconomy data = Economy.of(player);
        List<MenuView.Stat> stats = List.of(
                new MenuView.Stat("LIQUIDE", MoneyFormat.shortForm(data.balance()), 0xFFFFFFFF),
                new MenuView.Stat("EPARGNE", MoneyFormat.shortForm(data.banked()), 0xFFFAC400),
                new MenuView.Stat("PATRIMOINE", MoneyFormat.shortForm(data.netWorth()), 0xFFFFFFFF));

        int fee = (int) Math.round(Bank.depositFee() * 100);
        List<Card> deposit = new ArrayList<>();
        deposit.add(depositCard(Items.IRON_NUGGET, "Deposer 10 %", fraction(data.balance(), 0.10), fee, "deposit10"));
        deposit.add(depositCard(Items.IRON_INGOT, "Deposer 50 %", fraction(data.balance(), 0.50), fee, "deposit50"));
        deposit.add(depositCard(Items.IRON_BLOCK, "Deposer tout", data.balance(), fee, "depositAll"));
        List<Card> withdraw = new ArrayList<>();
        withdraw.add(withdrawCard(Items.GOLD_NUGGET, "Retirer 10 %", fraction(data.banked(), 0.10), "withdraw10"));
        withdraw.add(withdrawCard(Items.GOLD_INGOT, "Retirer 50 %", fraction(data.banked(), 0.50), "withdraw50"));
        withdraw.add(withdrawCard(Items.GOLD_BLOCK, "Retirer tout", data.banked(), "withdrawAll"));

        long perDay = Bank.perDay(player);
        long loss = DeathPenalty.lossFor(data);
        boolean insured = data.insurance() > 0;
        int freeLeft = Math.max(0, DeathPenalty.freeDeaths() - data.deaths());
        List<Card> account = List.of(
                Card.of(new ItemStack(Items.CLOCK), "Interets", List.of(MoneyFormat.exact(perDay) + " par jour",
                        MoneyFormat.exact(perDay * 7) + " par semaine"))
                        .accent(0xFF22E558)
                        .tooltip(data.interestTotal() > 0
                                ? List.of("Percus a ce jour : " + MoneyFormat.exact(data.interestTotal()),
                                "Ils courent aussi monde ferme")
                                : List.of("Ils courent aussi monde ferme")),
                Card.of(new ItemStack(Items.SKELETON_SKULL), "A la mort", List.of(
                        freeLeft > 0 ? freeLeft + " mort(s) sans penalite"
                                : "Perte : " + MoneyFormat.exact(insured ? loss / 2 : loss),
                        "L'epargne est protegee"))
                        .accent(freeLeft > 0 ? 0xFF22E558 : 0xFFEF3826),
                Card.of(new ItemStack(Items.TOTEM_OF_UNDYING), "Assurance", List.of(
                        MoneyFormat.exact(Bank.insurancePrice()) + " la charge",
                        data.insurance() + " charge(s)"))
                        .accent(0xFFFAC400).badge(String.valueOf(data.insurance()))
                        .action("insurance|acheter une charge")
                        .tooltip(List.of("Divise par deux la perte a la mort",
                                "Une charge est consommee par mort")));

        return new MenuView(id(), "Banque", "Ton epargne, protegee de la mort", stats, List.of(
                new MenuView.Section("DEPOSER", "resources", MenuView.GRID, deposit),
                new MenuView.Section("RETIRER", "upgrade", MenuView.GRID, withdraw),
                new MenuView.Section("TON COMPTE", "craft", MenuView.GRID, account)), true);
    }

    private static Card depositCard(Item icon, String title, long amount, int fee, String key) {
        return Card.of(new ItemStack(icon), title, List.of(MoneyFormat.exact(amount),
                        "Frais " + MoneyFormat.exact(Bank.feeFor(amount)) + " (" + fee + " %)"))
                .accent(0xFFEF3826).action(key + "|deposer")
                .state(amount <= 0 ? MenuView.DISABLED : MenuView.NORMAL);
    }

    private static Card withdrawCard(Item icon, String title, long amount, String key) {
        return Card.of(new ItemStack(icon), title, List.of(MoneyFormat.exact(amount), "Sans frais"))
                .accent(0xFF22E558).action(key + "|retirer")
                .state(amount <= 0 ? MenuView.DISABLED : MenuView.NORMAL);
    }

    @Override
    protected void card(ServerPlayer player, String key, int button) {
        PlayerEconomy data = Economy.of(player);
        switch (key) {
            case "deposit10" -> deposit(player, fraction(data.balance(), 0.10));
            case "deposit50" -> deposit(player, fraction(data.balance(), 0.50));
            case "depositAll" -> deposit(player, data.balance());
            case "withdraw10" -> withdraw(player, fraction(data.banked(), 0.10));
            case "withdraw50" -> withdraw(player, fraction(data.banked(), 0.50));
            case "withdrawAll" -> withdraw(player, data.banked());
            case "insurance" -> {
                if (Bank.buyInsurance(player)) {
                    success(player);
                    player.sendSystemMessage(Component.literal("Assurance achetee — "
                            + Bank.insuranceCharges(player) + " charge(s).").withStyle(ChatFormatting.GREEN));
                } else {
                    failure(player);
                    player.sendSystemMessage(Component.literal("Il te faut "
                            + MoneyFormat.exact(Bank.insurancePrice()) + " en liquide.").withStyle(ChatFormatting.RED));
                }
            }
            default -> {
            }
        }
    }

    private static void deposit(ServerPlayer player, long amount) {
        if (amount <= 0) {
            failure(player);
            return;
        }
        long fee = Bank.feeFor(amount);
        long credited = Bank.deposit(player, amount);
        if (credited < 0) {
            failure(player);
            return;
        }
        play(player, SoundEvents.NOTE_BLOCK_BELL.value(), 1.2f);
        player.sendSystemMessage(Component.literal("Depose ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(credited)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" (frais " + MoneyFormat.exact(fee) + ")").withStyle(ChatFormatting.DARK_GRAY)));
    }

    private static void withdraw(ServerPlayer player, long amount) {
        if (amount <= 0 || Bank.withdraw(player, amount) < 0) {
            failure(player);
            return;
        }
        play(player, SoundEvents.NOTE_BLOCK_BELL.value(), 0.8f);
        player.sendSystemMessage(Component.literal("Retire ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(amount)).withStyle(ChatFormatting.GOLD)));
    }
}

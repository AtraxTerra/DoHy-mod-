package fr.atraxterra.dohy.shop;

import fr.atraxterra.dohy.economy.Bank;
import fr.atraxterra.dohy.economy.DeathPenalty;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.PlayerEconomy;
import fr.atraxterra.dohy.ui.DisplayMenu;
import fr.atraxterra.dohy.ui.Gui;
import fr.atraxterra.dohy.ui.Hub;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * La banque, en coffre de trois rangees.
 *
 * <p>Les montants sont proposes en parts du solde plutot que saisis au
 * clavier — c'est tout l'interet d'une interface ici, ne plus avoir a taper un
 * nombre pour deposer.
 *
 * <p>La vignette centrale annonce le rendement <b>a ce solde</b> et par soiree
 * de deux heures, pas un taux abstrait : c'est le chiffre qui fait decider de
 * deposer.
 */
public final class BankMenu extends DisplayMenu {

    private static final int SLOT_INFO = 4;
    private static final int SLOT_DEPOSIT_10 = 10;
    private static final int SLOT_DEPOSIT_50 = 11;
    private static final int SLOT_DEPOSIT_ALL = 12;
    private static final int SLOT_WITHDRAW_ALL = 14;
    private static final int SLOT_WITHDRAW_50 = 15;
    private static final int SLOT_WITHDRAW_10 = 16;
    private static final int SLOT_BACK = 18;
    private static final int SLOT_INSURANCE = 22;
    private static final int SLOT_CLOSE = 26;

    public BankMenu(int containerId, Inventory inventory, ServerPlayer player) {
        super(containerId, inventory, player, 3);
        refresh();
    }

    @Override
    protected void onClick(int slot, int button, boolean shift) {
        PlayerEconomy data = Economy.of(player);
        switch (slot) {
            case SLOT_DEPOSIT_10 -> deposit(fraction(data.balance(), 0.10d));
            case SLOT_DEPOSIT_50 -> deposit(fraction(data.balance(), 0.50d));
            case SLOT_DEPOSIT_ALL -> deposit(data.balance());
            case SLOT_WITHDRAW_10 -> withdraw(fraction(data.banked(), 0.10d));
            case SLOT_WITHDRAW_50 -> withdraw(fraction(data.banked(), 0.50d));
            case SLOT_WITHDRAW_ALL -> withdraw(data.banked());
            case SLOT_INSURANCE -> insurance();
            case SLOT_BACK -> {
                click(0.7f);
                Hub.open(player);
            }
            case SLOT_CLOSE -> {
                click(0.6f);
                player.closeContainer();
            }
            default -> {
                // Emplacement decoratif : rien a faire.
            }
        }
    }

    /** Arrondi vers le bas, avec un minimum de 1 des que le solde le permet. */
    private static long fraction(long total, double part) {
        if (total <= 0) {
            return 0L;
        }
        return Math.max(1L, (long) Math.floor(total * part));
    }

    private void deposit(long amount) {
        if (amount <= 0) {
            failure();
            return;
        }
        long fee = Bank.feeFor(amount);
        long credited = Bank.deposit(player, amount);
        if (credited < 0) {
            failure();
            return;
        }
        play(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2f);
        player.sendSystemMessage(Component.literal("Depose ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(credited)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" (frais " + MoneyFormat.exact(fee) + ")")
                        .withStyle(ChatFormatting.DARK_GRAY)));
    }

    private void withdraw(long amount) {
        if (amount <= 0 || Bank.withdraw(player, amount) < 0) {
            failure();
            return;
        }
        play(SoundEvents.NOTE_BLOCK_BELL.value(), 0.8f);
        player.sendSystemMessage(Component.literal("Retire ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(amount)).withStyle(ChatFormatting.GOLD)));
    }

    private void insurance() {
        if (!Bank.buyInsurance(player)) {
            failure();
            player.sendSystemMessage(Component.literal("Il te faut "
                            + MoneyFormat.exact(Bank.insurancePrice()) + " en liquide.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        success();
        player.sendSystemMessage(Component.literal("Assurance achetee — "
                        + Bank.insuranceCharges(player) + " charge(s).")
                .withStyle(ChatFormatting.GREEN));
    }

    @Override
    protected void build() {
        PlayerEconomy data = Economy.of(player);
        long loss = DeathPenalty.lossFor(data);
        boolean insured = data.insurance() > 0;
        long effective = insured ? loss / 2 : loss;
        int freeLeft = Math.max(0, DeathPenalty.freeDeaths() - data.deaths());

        List<Component> info = new ArrayList<>();
        info.add(Gui.field("liquide", MoneyFormat.exact(data.balance())));
        info.add(Gui.field("epargne", MoneyFormat.exact(data.banked())));
        info.add(Gui.field("patrimoine", MoneyFormat.exact(data.netWorth())));
        info.add(Gui.blank());

        long perDay = Bank.perDay(player);
        info.add(Gui.good("interets : " + MoneyFormat.exact(perDay) + " / jour"));
        info.add(Gui.hint("  soit " + MoneyFormat.exact(perDay * 7) + " par semaine"));
        info.add(Gui.hint("  les interets courent aussi monde ferme"));
        if (data.interestTotal() > 0) {
            info.add(Gui.hint("percus a ce jour : " + MoneyFormat.exact(data.interestTotal())));
        }
        info.add(Gui.blank());
        info.add(freeLeft > 0
                ? Gui.good(freeLeft + " mort(s) sans penalite")
                : Gui.bad("une mort couterait " + MoneyFormat.exact(effective)));
        info.add(Gui.hint("l'epargne est protegee de la mort"));
        set(SLOT_INFO, Gui.button(Items.GOLD_INGOT, "Ton compte", info));

        set(SLOT_DEPOSIT_10, depositTile(Items.IRON_NUGGET, "Deposer 10 %",
                fraction(data.balance(), 0.10d)));
        set(SLOT_DEPOSIT_50, depositTile(Items.IRON_INGOT, "Deposer 50 %",
                fraction(data.balance(), 0.50d)));
        set(SLOT_DEPOSIT_ALL, depositTile(Items.IRON_BLOCK, "Deposer tout",
                data.balance()));

        set(SLOT_WITHDRAW_10, withdrawalTile(Items.GOLD_NUGGET, "Retirer 10 %",
                fraction(data.banked(), 0.10d)));
        set(SLOT_WITHDRAW_50, withdrawalTile(Items.GOLD_INGOT, "Retirer 50 %",
                fraction(data.banked(), 0.50d)));
        set(SLOT_WITHDRAW_ALL, withdrawalTile(Items.GOLD_BLOCK, "Retirer tout",
                data.banked()));

        set(SLOT_INSURANCE, Gui.button(Items.TOTEM_OF_UNDYING,
                "Assurance — " + MoneyFormat.exact(Bank.insurancePrice()), Gui.lore(
                        Gui.field("charges", String.valueOf(data.insurance())),
                        Gui.hint("divise par deux la perte a la mort"),
                        Gui.blank(),
                        Gui.action("clic pour acheter une charge"))));

        set(SLOT_BACK, Gui.back("le menu"));
        set(SLOT_CLOSE, Gui.close());
        footer(SLOT_BACK, SLOT_INSURANCE, SLOT_CLOSE);
    }

    private ItemStack depositTile(Item icon, String name, long amount) {
        return Gui.button(icon, name, Gui.lore(
                Component.literal(MoneyFormat.exact(amount)).withStyle(ChatFormatting.GOLD),
                Gui.hint("frais " + MoneyFormat.exact(Bank.feeFor(amount)) + " ("
                        + Math.round(Bank.depositFee() * 100) + " %)")));
    }

    private ItemStack withdrawalTile(Item icon, String name, long amount) {
        return Gui.button(icon, name, Gui.lore(
                Component.literal(MoneyFormat.exact(amount)).withStyle(ChatFormatting.GOLD),
                Gui.hint("sans frais")));
    }
}

package fr.atraxterra.dohy.shop;

import fr.atraxterra.dohy.economy.Bank;
import fr.atraxterra.dohy.economy.DeathPenalty;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.PlayerEconomy;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * La banque, en coffre de trois rangees.
 *
 * <p>Meme principe que {@link ShopMenu} : conteneur d'affichage, clics jamais
 * delegues a {@code super}, donc aucun item ne peut bouger. Les montants sont
 * proposes en parts du solde plutot que saisis au clavier — c'est tout l'interet
 * d'une interface ici, ne plus avoir a taper un nombre pour deposer.
 */
public final class BankMenu extends ChestMenu {

    private static final int SLOT_INFO = 4;
    private static final int SLOT_DEPOSIT_10 = 10;
    private static final int SLOT_DEPOSIT_50 = 11;
    private static final int SLOT_DEPOSIT_ALL = 12;
    private static final int SLOT_WITHDRAW_ALL = 14;
    private static final int SLOT_WITHDRAW_50 = 15;
    private static final int SLOT_WITHDRAW_10 = 16;
    private static final int SLOT_INSURANCE = 22;

    private final ServerPlayer player;
    private final Container display;

    public BankMenu(int containerId, Inventory inventory, ServerPlayer player) {
        this(containerId, inventory, player, new SimpleContainer(27));
    }

    private BankMenu(int containerId, Inventory inventory, ServerPlayer player, Container display) {
        super(MenuType.GENERIC_9x3, containerId, inventory, display, 3);
        this.player = player;
        this.display = display;
        refresh();
    }

    @Override
    public boolean stillValid(Player candidate) {
        return candidate == player;
    }

    @Override
    public ItemStack quickMoveStack(Player candidate, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player candidate) {
        if (!(candidate instanceof ServerPlayer serverPlayer) || serverPlayer != player) {
            return;
        }
        if (slotId < 0 || slotId >= 27) {
            refresh();
            return;
        }

        PlayerEconomy data = Economy.of(player);
        switch (slotId) {
            case SLOT_DEPOSIT_10 -> deposit(fraction(data.balance(), 0.10d));
            case SLOT_DEPOSIT_50 -> deposit(fraction(data.balance(), 0.50d));
            case SLOT_DEPOSIT_ALL -> deposit(data.balance());
            case SLOT_WITHDRAW_10 -> withdraw(fraction(data.banked(), 0.10d));
            case SLOT_WITHDRAW_50 -> withdraw(fraction(data.banked(), 0.50d));
            case SLOT_WITHDRAW_ALL -> withdraw(data.banked());
            case SLOT_INSURANCE -> insurance();
            default -> {
                // Emplacement decoratif : rien a faire.
            }
        }
        refresh();
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
            play(SoundEvents.VILLAGER_NO, 1.0f);
            return;
        }
        long fee = Bank.feeFor(amount);
        long credited = Bank.deposit(player, amount);
        if (credited < 0) {
            play(SoundEvents.VILLAGER_NO, 1.0f);
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
            play(SoundEvents.VILLAGER_NO, 1.0f);
            return;
        }
        play(SoundEvents.NOTE_BLOCK_BELL.value(), 0.8f);
        player.sendSystemMessage(Component.literal("Retire ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(amount)).withStyle(ChatFormatting.GOLD)));
    }

    private void insurance() {
        if (!Bank.buyInsurance(player)) {
            play(SoundEvents.VILLAGER_NO, 1.0f);
            player.sendSystemMessage(Component.literal("Il te faut "
                            + MoneyFormat.exact(Bank.insurancePrice()) + " en liquide.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4f);
        player.sendSystemMessage(Component.literal("Assurance achetee — "
                        + Bank.insuranceCharges(player) + " charge(s).")
                .withStyle(ChatFormatting.GREEN));
    }

    private void refresh() {
        PlayerEconomy data = Economy.of(player);
        long loss = DeathPenalty.lossFor(data);
        boolean insured = data.insurance() > 0;
        long effective = insured ? loss / 2 : loss;
        int freeLeft = Math.max(0, DeathPenalty.freeDeaths() - data.deaths());

        for (int slot = 0; slot < 27; slot++) {
            display.setItem(slot, ItemStack.EMPTY);
        }

        List<Component> info = new ArrayList<>();
        info.add(value("liquide", data.balance()));
        info.add(value("epargne", data.banked()));
        info.add(value("total", data.netWorth()));
        info.add(Component.literal("").withStyle(ChatFormatting.DARK_GRAY));

        // Le rendement a CE solde, et par soiree de deux heures : c'est le
        // chiffre qui fait decider de deposer, pas un taux abstrait.
        long perDay = Bank.perPlayDay(data.banked());
        info.add(Component.literal("interets : " + MoneyFormat.exact(perDay) + " / jour de jeu")
                .withStyle(ChatFormatting.GREEN));
        info.add(Component.literal("  soit " + MoneyFormat.exact(perDay / 12) + " par soiree de 2 h")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (data.interestTotal() > 0) {
            info.add(Component.literal("percus a ce jour : "
                            + MoneyFormat.exact(data.interestTotal()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        info.add(Component.literal("").withStyle(ChatFormatting.DARK_GRAY));
        if (freeLeft > 0) {
            info.add(Component.literal(freeLeft + " mort(s) sans penalite")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            info.add(Component.literal("une mort couterait " + MoneyFormat.exact(effective))
                    .withStyle(ChatFormatting.RED));
        }
        info.add(Component.literal("l'epargne est protegee de la mort")
                .withStyle(ChatFormatting.DARK_GRAY));
        display.setItem(SLOT_INFO, labelled(Items.GOLD_INGOT, "Ton compte", info));

        display.setItem(SLOT_DEPOSIT_10, deposit(Items.IRON_NUGGET, "Deposer 10 %",
                fraction(data.balance(), 0.10d)));
        display.setItem(SLOT_DEPOSIT_50, deposit(Items.IRON_INGOT, "Deposer 50 %",
                fraction(data.balance(), 0.50d)));
        display.setItem(SLOT_DEPOSIT_ALL, deposit(Items.IRON_BLOCK, "Deposer tout",
                data.balance()));

        display.setItem(SLOT_WITHDRAW_10, withdrawal(Items.GOLD_NUGGET, "Retirer 10 %",
                fraction(data.banked(), 0.10d)));
        display.setItem(SLOT_WITHDRAW_50, withdrawal(Items.GOLD_INGOT, "Retirer 50 %",
                fraction(data.banked(), 0.50d)));
        display.setItem(SLOT_WITHDRAW_ALL, withdrawal(Items.GOLD_BLOCK, "Retirer tout",
                data.banked()));

        display.setItem(SLOT_INSURANCE, labelled(Items.TOTEM_OF_UNDYING,
                "Assurance — " + MoneyFormat.exact(Bank.insurancePrice()),
                List.of(
                        Component.literal("charges : " + data.insurance())
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal("divise par deux la perte a la mort")
                                .withStyle(ChatFormatting.DARK_GRAY))));

        broadcastChanges();
    }

    private ItemStack deposit(Item icon, String name, long amount) {
        long fee = Bank.feeFor(amount);
        return labelled(icon, name, List.of(
                Component.literal(MoneyFormat.exact(amount)).withStyle(ChatFormatting.GOLD),
                Component.literal("frais " + MoneyFormat.exact(fee) + " ("
                                + Math.round(Bank.depositFee() * 100) + " %)")
                        .withStyle(ChatFormatting.DARK_GRAY)));
    }

    private ItemStack withdrawal(Item icon, String name, long amount) {
        return labelled(icon, name, List.of(
                Component.literal(MoneyFormat.exact(amount)).withStyle(ChatFormatting.GOLD),
                Component.literal("sans frais").withStyle(ChatFormatting.DARK_GRAY)));
    }

    private Component value(String label, long amount) {
        return Component.literal(label + " : ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(amount)).withStyle(ChatFormatting.GOLD));
    }

    private ItemStack labelled(Item item, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(name).withStyle(ChatFormatting.YELLOW));
        if (!lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        return stack;
    }

    private void play(SoundEvent sound, float pitch) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.MASTER, 0.5f, pitch);
    }
}

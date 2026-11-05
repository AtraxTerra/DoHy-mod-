package fr.atraxterra.dohy.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atraxterra.dohy.economy.Bank;
import fr.atraxterra.dohy.shop.BankMenu;
import fr.atraxterra.dohy.economy.DeathPenalty;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.PlayerEconomy;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /bank} — epargne, interets, assurance, et ce que couterait une mort.
 *
 * <p>Afficher la perte encourue est le point central de l'interface : c'est ce
 * chiffre qui transforme la penalite de mort en decision avant de sortir, au
 * lieu d'une surprise au retour.
 */
@EventBusSubscriber
public final class BankCommand {

    private BankCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(build());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("bank")
                // Sans argument : l'interface. Les sous-commandes restent pour
                // les montants precis, que des parts predefinies ne couvrent pas.
                .executes(ctx -> open(ctx.getSource()))
                .then(Commands.literal("info")
                        .executes(ctx -> show(ctx.getSource())))
                .then(Commands.literal("deposit")
                        .then(Commands.argument("montant", LongArgumentType.longArg(1))
                                .executes(ctx -> deposit(ctx.getSource(),
                                        LongArgumentType.getLong(ctx, "montant"))))
                        .then(Commands.literal("all")
                                .executes(ctx -> depositAll(ctx.getSource()))))
                .then(Commands.literal("withdraw")
                        .then(Commands.argument("montant", LongArgumentType.longArg(1))
                                .executes(ctx -> withdraw(ctx.getSource(),
                                        LongArgumentType.getLong(ctx, "montant"))))
                        .then(Commands.literal("all")
                                .executes(ctx -> withdrawAll(ctx.getSource()))))
                .then(Commands.literal("insurance")
                        .executes(ctx -> insurance(ctx.getSource())));
    }

    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) -> new BankMenu(containerId, inventory, player),
                Component.literal("Banque")));
        return 1;
    }

    private static int show(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEconomy data = Economy.of(player);
        long loss = DeathPenalty.lossFor(data);
        boolean insured = data.insurance() > 0;
        long effective = insured ? loss / 2 : loss;

        source.sendSuccess(() -> Component.literal("Banque").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> line("liquide", MoneyFormat.exact(data.balance())), false);
        source.sendSuccess(() -> line("epargne", MoneyFormat.exact(data.banked())), false);
        source.sendSuccess(() -> line("total", MoneyFormat.exact(data.netWorth())), false);

        // On annonce le rendement a CE solde, pas le taux abstrait : c'est le
        // chiffre sur lequel le joueur decide de deposer ou non.
        long perDay = Bank.perDay(data.banked());
        source.sendSuccess(() -> line("interets", MoneyFormat.exact(perDay)
                + " / jour  (" + MoneyFormat.exact(perDay * 7) + " par semaine)"), false);
        if (data.interestTotal() > 0) {
            source.sendSuccess(() -> line("interets percus", MoneyFormat.exact(data.interestTotal())), false);
        }
        source.sendSuccess(() -> Component.literal("  frais de depot : "
                        + Math.round(Bank.depositFee() * 100) + " %")
                .withStyle(ChatFormatting.DARK_GRAY), false);

        int remaining = Math.max(0, DeathPenalty.freeDeaths() - data.deaths());
        if (remaining > 0) {
            source.sendSuccess(() -> Component.literal("  " + remaining
                            + " mort(s) encore sans penalite")
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.literal("  une mort te couterait ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(MoneyFormat.exact(effective))
                            .withStyle(ChatFormatting.RED))
                    .append(Component.literal(insured
                                    ? " (assurance : " + data.insurance() + ")"
                                    : " · assurance a " + MoneyFormat.exact(Bank.insurancePrice())
                                            + " : /bank insurance")
                            .withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        return 1;
    }

    private static int deposit(CommandSourceStack source, long amount) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        long fee = Bank.feeFor(amount);
        long credited = Bank.deposit(player, amount);
        if (credited < 0) {
            source.sendFailure(Component.literal("Solde liquide insuffisant."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Depose ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(credited)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" (frais " + MoneyFormat.exact(fee) + ")")
                        .withStyle(ChatFormatting.DARK_GRAY)), false);
        return 1;
    }

    private static int depositAll(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        long balance = Economy.balance(player);
        if (balance <= 0) {
            source.sendFailure(Component.literal("Rien a deposer."));
            return 0;
        }
        return deposit(source, balance);
    }

    private static int withdraw(CommandSourceStack source, long amount) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (Bank.withdraw(player, amount) < 0) {
            source.sendFailure(Component.literal("Epargne insuffisante."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Retire ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(amount)).withStyle(ChatFormatting.GOLD)), false);
        return 1;
    }

    private static int withdrawAll(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        long banked = Bank.banked(player);
        if (banked <= 0) {
            source.sendFailure(Component.literal("Epargne vide."));
            return 0;
        }
        return withdraw(source, banked);
    }

    private static int insurance(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!Bank.buyInsurance(player)) {
            source.sendFailure(Component.literal("Il te faut "
                    + MoneyFormat.exact(Bank.insurancePrice()) + " en liquide."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Assurance achetee — "
                        + Bank.insuranceCharges(player)
                        + " charge(s). La prochaine mort coutera moitie moins.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static Component line(String label, String value) {
        return Component.literal("  " + label + " : ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.GOLD));
    }
}

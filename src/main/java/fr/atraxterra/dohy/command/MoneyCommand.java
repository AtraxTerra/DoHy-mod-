package fr.atraxterra.dohy.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.PlayerEconomy;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /money} — consulter son solde, masquer le HUD, et les sous-commandes
 * d'administration pour tester l'equilibrage sans grind.
 */
@EventBusSubscriber
public final class MoneyCommand {

    private MoneyCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(build());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("money")
                .executes(ctx -> show(ctx.getSource()))
                .then(Commands.literal("hide")
                        .executes(ctx -> toggleHud(ctx.getSource())))
                // Administration : indispensable pour tester un palier de prix
                // sans devoir le gagner. En 26.x, hasPermission(int) a disparu :
                // les niveaux passent par Commands.hasPermission(PermissionCheck),
                // LEVEL_GAMEMASTERS correspondant a l'ancien niveau 2.
                .then(Commands.literal("give")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("montant", LongArgumentType.longArg(1))
                                .executes(ctx -> give(ctx.getSource(),
                                        LongArgumentType.getLong(ctx, "montant")))))
                .then(Commands.literal("set")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("montant", LongArgumentType.longArg(0))
                                .executes(ctx -> set(ctx.getSource(),
                                        LongArgumentType.getLong(ctx, "montant")))));
    }

    private static int show(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEconomy data = Economy.of(player);

        source.sendSuccess(() -> Component.literal("Solde : ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(data.balance()))
                        .withStyle(ChatFormatting.GOLD)), false);
        source.sendSuccess(() -> Component.literal("Gains cumules : ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(MoneyFormat.exact(data.lifetimeEarned()))
                        .withStyle(ChatFormatting.GRAY)), false);

        // Le solde exact tient dans un long : on le renvoie tel quel au
        // systeme de resultat de commande, borne a un int.
        return (int) Math.min(Integer.MAX_VALUE, data.balance());
    }

    private static int toggleHud(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean hidden = Economy.toggleHud(player);
        source.sendSuccess(() -> Component.literal(hidden
                        ? "HUD du solde masque."
                        : "HUD du solde affiche.")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int give(CommandSourceStack source, long amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        long balance = Economy.credit(player, amount);
        source.sendSuccess(() -> Component.literal("Credite de " + MoneyFormat.exact(amount)
                + ", solde " + MoneyFormat.shortForm(balance)), true);
        return 1;
    }

    private static int set(CommandSourceStack source, long amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        long balance = Economy.set(player, amount);
        source.sendSuccess(() -> Component.literal("Solde fixe a " + MoneyFormat.exact(balance)
                + " (gains cumules inchanges)"), true);
        return 1;
    }
}

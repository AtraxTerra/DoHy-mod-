package fr.atraxterra.dohy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /sell} — vendre l'item en main.
 *
 * <p>Les prix sont ceux du catalogue, sans dynamique : la pression a la vente
 * arrive en phase 4. Vendre a prix fixe permet de verifier que le catalogue, la
 * monnaie et le HUD s'enchainent correctement avant d'ajouter une formule
 * par-dessus.
 *
 * <p>La vente de l'inventaire entier attend le shop : la disposition interne de
 * l'inventaire a change dans la ligne 26.x, et supprimer des piles par le
 * mauvais index reviendrait a detruire l'equipement du joueur. Lire est sans
 * risque, ecrire merite d'etre verifie.
 */
@EventBusSubscriber
public final class SellCommand {

    private SellCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(build());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("sell")
                .executes(ctx -> sellHand(ctx.getSource(), false))
                .then(Commands.literal("hand")
                        .executes(ctx -> sellHand(ctx.getSource(), false)))
                .then(Commands.literal("stack")
                        .executes(ctx -> sellHand(ctx.getSource(), true)));
    }

    private static int sellHand(CommandSourceStack source, boolean wholeStack) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack held = player.getMainHandItem();

        if (held.isEmpty()) {
            source.sendFailure(Component.literal("Rien en main a vendre."));
            return 0;
        }

        var maybeEntry = Prices.book().entry(held);
        if (maybeEntry.isEmpty() || !maybeEntry.get().sellable()) {
            source.sendFailure(held.getHoverName().copy()
                    .append(Component.literal(" ne se vend pas.")));
            return 0;
        }

        PriceEntry entry = maybeEntry.get();
        int amount = wholeStack ? held.getCount() : 1;
        long payout = entry.basePrice() * amount;

        held.shrink(amount);
        long balance = Economy.credit(player, payout);

        int sold = amount;
        source.sendSuccess(() -> Component.literal("Vendu ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(sold + "x ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("pour ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(MoneyFormat.exact(payout)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" · solde " + MoneyFormat.shortForm(balance))
                        .withStyle(ChatFormatting.DARK_GRAY)), false);

        return sold;
    }
}

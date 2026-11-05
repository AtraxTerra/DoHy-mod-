package fr.atraxterra.dohy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atraxterra.dohy.collection.CollectionApi;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.market.DynamicPrice;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.shop.SellChestMenu;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /sell} — ouvre le coffre de vente ; {@code /sell hand} et
 * {@code /sell stack} vendent directement ce qu'on tient, au prix dynamique.
 *
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
                // Sans argument : le coffre de vente. C'est le geste courant —
                // on depose une fournee entiere plutot qu'un item a la fois.
                .executes(ctx -> openSellChest(ctx.getSource()))
                .then(Commands.literal("hand")
                        .executes(ctx -> sellHand(ctx.getSource(), false)))
                .then(Commands.literal("stack")
                        .executes(ctx -> sellHand(ctx.getSource(), true)));
    }

    private static int openSellChest(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (Prices.book().size() == 0) {
            source.sendFailure(Component.literal(
                    "Catalogue de prix vide. Essaie /dohy reload et regarde les logs."));
            return 0;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) -> new SellChestMenu(containerId, inventory, player),
                Component.literal("Vente — ferme pour encaisser")));
        return 1;
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
        Identifier id = BuiltInRegistries.ITEM.getKey(held.getItem());

        if (!CollectionApi.isUnlocked(player, id, entry)) {
            source.sendFailure(Component.literal("Vente verrouillee : il t'en manque "
                    + MoneyFormat.exact(CollectionApi.missingToUnlock(player, id, entry))
                    + " a collecter."));
            return 0;
        }

        int amount = wholeStack ? held.getCount() : 1;
        long payout = DynamicPrice.sell(player, id, entry, amount);

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

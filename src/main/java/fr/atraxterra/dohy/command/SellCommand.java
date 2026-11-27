package fr.atraxterra.dohy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atraxterra.dohy.collection.CollectionApi;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.market.DynamicPrice;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.shop.DrawerSale;
import fr.atraxterra.dohy.shop.SellChestMenu;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /sell} — ouvre le coffre de vente ; {@code /sell hand} et
 * {@code /sell stack} vendent directement ce qu'on tient, au prix dynamique.
 * Un tiroir de Storage Drawers en main : son contenu vendable, et le tiroir
 * reste en main ({@link DrawerSale}).
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
                // Sans argument : l'onglet Vendre du commerce, qui montre ce que le sac
                // rapporte et vend tout d'un clic. Le coffre de vente a sa propre forme.
                .executes(ctx -> openSellTab(ctx.getSource()))
                .then(Commands.literal("coffre")
                        .executes(ctx -> openSellChest(ctx.getSource())))
                .then(Commands.literal("hand")
                        .executes(ctx -> sellHand(ctx.getSource(), false)))
                .then(Commands.literal("stack")
                        .executes(ctx -> sellHand(ctx.getSource(), true)));
    }

    private static int openSellTab(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (Prices.book().size() == 0) {
            source.sendFailure(Component.literal(
                    "Catalogue de prix vide. Essaie /dohy reload et regarde les logs."));
            return 0;
        }
        fr.atraxterra.dohy.shop.SellController.INSTANCE.open(player);
        return 1;
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
                Component.literal("Coffre de vente")));
        return 1;
    }

    private static int sellHand(CommandSourceStack source, boolean wholeStack) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack held = player.getMainHandItem();

        if (held.isEmpty()) {
            source.sendFailure(Component.literal("Rien en main a vendre."));
            return 0;
        }
        if (DrawerSale.isDrawer(held)) {
            return sellDrawer(source, player, held);
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
        fr.atraxterra.dohy.palamod.achievement.Achievements.sold(player, payout);

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

    /** Le contenu vendable du tiroir tenu ; le tiroir reste en main, vide ou avec le reste. */
    private static int sellDrawer(CommandSourceStack source, ServerPlayer player, ItemStack held) {
        DrawerSale.Plan plan = DrawerSale.plan(player, held).orElse(null);
        if (plan == null) {
            source.sendFailure(Component.literal("Ce tiroir ne se lit pas."));
            return 0;
        }
        switch (plan.refusal()) {
            case OWNER -> {
                source.sendFailure(Component.literal("Ce tiroir est ferme a cle par un autre joueur."));
                return 0;
            }
            case VENDING -> {
                source.sendFailure(Component.literal("Un tiroir de distribution creative ne se vend pas."));
                return 0;
            }
            default -> {
            }
        }
        if (plan.sold().isEmpty()) {
            source.sendFailure(Component.literal(plan.leftover()
                    ? "Rien dans ce tiroir ne se vend." : "Ce tiroir est vide."));
            return 0;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, plan.apply());
        long payout = 0L;
        for (var line : plan.sold().entrySet()) {
            Identifier id = BuiltInRegistries.ITEM.getKey(line.getKey());
            PriceEntry entry = Prices.book().entry(id).orElse(null);
            if (entry != null) {
                payout += DynamicPrice.sell(player, id, entry, line.getValue());
            }
        }
        long balance = Economy.credit(player, payout);
        fr.atraxterra.dohy.palamod.achievement.Achievements.sold(player, payout);
        int count = plan.count();
        int kinds = plan.sold().size();
        long paid = payout;
        source.sendSuccess(() -> Component.literal("Tiroir : vendu ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(count + "x ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal((kinds > 1 ? "(" + kinds + " sortes) " : "") + "pour ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(MoneyFormat.exact(paid)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" · solde " + MoneyFormat.shortForm(balance))
                        .withStyle(ChatFormatting.DARK_GRAY)), false);
        if (plan.leftover()) {
            source.sendSuccess(() -> Component.literal("Le reste ne se vend pas : il reste dans le tiroir.")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return count;
    }
}

package fr.atraxterra.dohy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.shop.ShopCategory;
import fr.atraxterra.dohy.shop.ShopMenu;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /shop} pour ouvrir le shop, et {@code /sellall} pour vider son
 * inventaire d'une categorie entiere.
 */
@EventBusSubscriber
public final class ShopCommand {

    private ShopCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(shop());
        event.getDispatcher().register(sellAll());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shop() {
        return Commands.literal("shop")
                .executes(ctx -> open(ctx.getSource()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sellAll() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("sellall")
                .executes(ctx -> sell(ctx.getSource(), null));
        for (ShopCategory category : ShopCategory.values()) {
            root = root.then(Commands.literal(category.key().toLowerCase())
                    .executes(ctx -> sell(ctx.getSource(), category)));
        }
        return root;
    }

    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (Prices.book().size() == 0) {
            source.sendFailure(Component.literal(
                    "Catalogue de prix vide. Essaie /dohy reload et regarde les logs."));
            return 0;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) -> new ShopMenu(containerId, inventory, player),
                Component.literal("DoHy — Shop")));
        return 1;
    }

    /**
     * Vend tout le vendable de l'inventaire, eventuellement restreint a une
     * categorie.
     *
     * <p>Passe par {@code getNonEquipmentItems()} : cette liste exclut
     * l'equipement porte, donc une armure ou un item en seconde main ne peut
     * pas partir a la vente par accident. C'est la raison pour laquelle cette
     * commande attendait une verification de l'API — la disposition interne de
     * l'inventaire a change dans la ligne 26.x, et supprimer des piles par le
     * mauvais index aurait detruit l'equipement du joueur.
     */
    private static int sell(CommandSourceStack source, ShopCategory only) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();

        long payout = 0L;
        int sold = 0;
        int kinds = 0;

        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            PriceEntry entry = Prices.book().entry(stack).orElse(null);
            if (entry == null || !entry.sellable()) {
                continue;
            }
            if (only != null && !only.key().equalsIgnoreCase(entry.category())) {
                continue;
            }

            int count = stack.getCount();
            payout += entry.basePrice() * count;
            sold += count;
            kinds++;
            stack.setCount(0);
        }

        if (sold == 0) {
            source.sendSuccess(() -> Component.literal(only == null
                            ? "Rien de vendable dans ton inventaire."
                            : "Rien de vendable en " + only.display() + ".")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        long balance = Economy.credit(player, payout);
        long finalPayout = payout;
        int finalSold = sold;
        int finalKinds = kinds;
        source.sendSuccess(() -> Component.literal("Vendu " + finalSold + " items ("
                        + finalKinds + " types) pour ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyFormat.exact(finalPayout)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" · solde " + MoneyFormat.shortForm(balance))
                        .withStyle(ChatFormatting.DARK_GRAY)), false);
        return finalSold;
    }
}

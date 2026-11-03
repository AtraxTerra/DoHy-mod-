package fr.atraxterra.dohy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
 * {@code /worth} — ce que vaut l'item en main, a l'unite, au stack, et en
 * totalite dans l'inventaire.
 *
 * <p>C'est la commande la plus utilisee d'un mod d'economie : elle evite
 * d'ouvrir le shop pour une question a deux secondes.
 */
@EventBusSubscriber
public final class WorthCommand {

    private WorthCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(build());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("worth")
                .executes(ctx -> worth(ctx.getSource()));
    }

    private static int worth(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack held = player.getMainHandItem();

        if (held.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Rien en main.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        var maybeEntry = Prices.book().entry(held);
        if (maybeEntry.isEmpty()) {
            source.sendSuccess(() -> held.getHoverName().copy()
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(" ne se vend pas.").withStyle(ChatFormatting.GRAY)), false);
            return 0;
        }

        PriceEntry entry = maybeEntry.get();
        if (!entry.sellable()) {
            source.sendSuccess(() -> held.getHoverName().copy()
                    .append(Component.literal(" est en achat seul.").withStyle(ChatFormatting.GRAY)), false);
            return 0;
        }

        long unit = entry.basePrice();
        long stack = unit * 64L;
        int inInventory = countInInventory(player, held);
        long total = unit * inInventory;

        source.sendSuccess(() -> held.getHoverName().copy().withStyle(ChatFormatting.WHITE)
                .append(Component.literal("  [" + entry.category() + "]").withStyle(ChatFormatting.DARK_GRAY)), false);
        source.sendSuccess(() -> line("a l'unite", MoneyFormat.exact(unit)), false);
        source.sendSuccess(() -> line("le stack (64)", MoneyFormat.exact(stack)), false);
        source.sendSuccess(() -> line("ton inventaire (" + inInventory + ")", MoneyFormat.exact(total)), false);
        source.sendSuccess(() -> Component.literal("plancher " + MoneyFormat.exact(entry.floorPrice())
                        + " · plafond " + MoneyFormat.exact(entry.ceilingPrice()))
                .withStyle(ChatFormatting.DARK_GRAY), false);

        return (int) Math.min(Integer.MAX_VALUE, unit);
    }

    private static Component line(String label, String value) {
        return Component.literal("  " + label + " : ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.GOLD));
    }

    /**
     * Compte les exemplaires de cet item dans l'inventaire.
     *
     * <p>Passe par l'interface {@code Container} plutot que par les champs de
     * l'inventaire : la disposition interne de l'inventaire a change dans la
     * ligne 26.x, l'interface est stable. Le comptage est en lecture seule,
     * donc inclure quelques emplacements d'equipement ne peut rien casser.
     */
    private static int countInInventory(ServerPlayer player, ItemStack reference) {
        int count = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!candidate.isEmpty() && candidate.is(reference.getItem())) {
                count += candidate.getCount();
            }
        }
        return count;
    }
}

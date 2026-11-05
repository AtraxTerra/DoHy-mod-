package fr.atraxterra.dohy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atraxterra.dohy.contract.ActiveContract;
import fr.atraxterra.dohy.contract.ContractApi;
import fr.atraxterra.dohy.contract.ContractConfig;
import fr.atraxterra.dohy.contract.ContractData;
import fr.atraxterra.dohy.contract.ContractsMenu;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/** {@code /contracts} — contrats en cours, demande du jour, niveau global. */
@EventBusSubscriber
public final class ContractsCommand {

    private ContractsCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(build());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("contracts")
                // Sans argument : l'interface. Le detail en texte reste sous
                // « list », plus pratique pour copier un chiffre.
                .executes(ctx -> open(ctx.getSource()))
                .then(Commands.literal("list")
                        .executes(ctx -> show(ctx.getSource())));
    }

    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) ->
                        new ContractsMenu(containerId, inventory, player),
                Component.literal("Contrats")));
        return 1;
    }

    private static int show(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ContractData data = ContractApi.data(player);

        source.sendSuccess(() -> Component.literal("Niveau global "
                        + ContractApi.globalLevel(player))
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("  ·  " + MoneyFormat.exact(data.playtimeMinutes())
                                + " min de jeu")
                        .withStyle(ChatFormatting.DARK_GRAY)), false);

        Identifier demand = data.demandItem();
        if (demand != null) {
            Item item = BuiltInRegistries.ITEM.getOptional(demand).orElse(null);
            Component name = item == null
                    ? Component.literal(demand.getPath())
                    : new ItemStack(item).getHoverName();
            long left = Math.max(0, data.demandUntil() - data.playtimeMinutes());
            source.sendSuccess(() -> Component.literal("Demande du jour : ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(name.copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  x"
                                    + ContractConfig.config().demand().multiplier()
                                    + " · " + left + " min restantes")
                            .withStyle(ChatFormatting.DARK_GRAY)), false);
        }

        long reset = ContractConfig.config().firstSale().resetHours() * 60L;
        if (data.firstSaleAvailable(reset)) {
            source.sendSuccess(() -> Component.literal("Premiere vente du jour : x"
                            + ContractConfig.config().firstSale().multiplier() + " disponible")
                    .withStyle(ChatFormatting.GREEN), false);
        }

        print(source, "Contrats du jour", data.daily());
        print(source, "Contrat de la semaine", data.weekly());

        if (data.daily().isEmpty() && data.weekly().isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                            "Aucun contrat. Ils arrivent a la prochaine minute de jeu.")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static void print(CommandSourceStack source, String title, List<ActiveContract> contracts) {
        if (contracts.isEmpty()) {
            return;
        }
        source.sendSuccess(() -> Component.literal(title).withStyle(ChatFormatting.GOLD), false);
        for (ActiveContract contract : contracts) {
            ChatFormatting colour = contract.done() ? ChatFormatting.DARK_GREEN : ChatFormatting.GRAY;
            source.sendSuccess(() -> Component.literal("  "
                            + (contract.done() ? "✔ " : "· ")
                            + ContractApi.describe(contract))
                    .withStyle(colour), false);
        }
    }
}

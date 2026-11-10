package fr.atraxterra.dohy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atraxterra.dohy.museum.MuseumApi;
import fr.atraxterra.dohy.museum.MuseumConfig;
import fr.atraxterra.dohy.museum.MuseumMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/** {@code /museum} — guichet de dons, et avancement des ailes. */
@EventBusSubscriber
public final class MuseumCommand {

    private MuseumCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(build());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("museum")
                // Sans argument : la vitrine. Le guichet reste accessible depuis
                // l'interface et par /museum donate.
                .executes(ctx -> browse(ctx.getSource()))
                .then(Commands.literal("donate")
                        .executes(ctx -> open(ctx.getSource())))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())));
    }

    private static int browse(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        fr.atraxterra.dohy.ui.menu.MuseumController.INSTANCE.open(player);
        return 1;
    }

    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) -> new MuseumMenu(containerId, inventory, player),
                Component.literal("Guichet du musee")));
        return 1;
    }

    private static int list(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        source.sendSuccess(() -> Component.literal("Musee — "
                        + MuseumApi.data(player).count() + " piece(s) exposee(s)")
                .withStyle(ChatFormatting.GOLD), false);

        for (String category : MuseumApi.categories()) {
            List<?> expected = MuseumApi.expected(category);
            int done = MuseumApi.donatedIn(player, category);
            boolean complete = MuseumApi.isComplete(player, category);
            MuseumConfig.Section section = MuseumConfig.config().section(category);
            String display = section.display().isEmpty() ? category : section.display();

            source.sendSuccess(() -> Component.literal("  " + (complete ? "✔ " : "· ")
                            + display + " " + done + " / " + expected.size())
                    .withStyle(complete ? ChatFormatting.AQUA : ChatFormatting.GRAY), false);

            if (!complete && !section.rewards().isEmpty()) {
                section.rewards().forEach(reward -> source.sendSuccess(() ->
                        Component.literal("      a la cle : " + MuseumApi.describe(reward))
                                .withStyle(ChatFormatting.DARK_GRAY), false));
            }
        }

        List<?> patron = MuseumApi.patronItems();
        if (!patron.isEmpty()) {
            boolean complete = MuseumApi.isPatronComplete(player);
            source.sendSuccess(() -> Component.literal("  " + (complete ? "✔ " : "· ")
                            + MuseumConfig.config().patron().display()
                            + " (optionnel, items uniques) " + patron.size() + " piece(s)")
                    .withStyle(complete ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY), false);
        }
        return 1;
    }
}

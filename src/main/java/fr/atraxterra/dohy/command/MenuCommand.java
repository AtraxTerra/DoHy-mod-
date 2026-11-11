package fr.atraxterra.dohy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atraxterra.dohy.ui.Hub;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /menu} — le menu central, et {@code /boutique}.
 *
 * <p>Une seule commande a retenir pour atteindre les huit systemes. Les
 * commandes specialisees restent en place : elles sont plus rapides quand on
 * sait ou on va, et indispensables pour celles qui prennent un argument.
 */
@EventBusSubscriber
public final class MenuCommand {

    private MenuCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(menu());
        event.getDispatcher().register(boutique());
        // Le clicker : le raccourci « clicker » de Paladium.
        event.getDispatcher().register(Commands.literal("clicker").executes(ctx -> {
            fr.atraxterra.dohy.palamod.clicker.Clicker.open(ctx.getSource().getPlayerOrException());
            return 1;
        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> menu() {
        return Commands.literal("menu")
                .executes(ctx -> open(ctx.getSource()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> boutique() {
        return Commands.literal("boutique")
                .executes(ctx -> openBoutique(ctx.getSource()));
    }

    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        Hub.open(source.getPlayerOrException());
        return 1;
    }

    private static int openBoutique(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        fr.atraxterra.dohy.ui.menu.BoutiqueController.INSTANCE.open(player);
        return 1;
    }
}

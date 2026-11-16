package fr.atraxterra.dohy.arrakis;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /arrakis} pour les operateurs : aller a l'astroport sans billet,
 * reconstruire l'astroport, lire l'oxygene de l'atmosphere.
 */
@EventBusSubscriber(modid = DoHy.MOD_ID)
public final class ArrakisCommand {

    private ArrakisCommand() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(command());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("arrakis")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("tp").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ServerLevel level = ArrakisPort.level(context.getSource().getServer());
                    if (level == null) {
                        context.getSource().sendFailure(Component.literal("Arrakis n'est pas chargee."));
                        return 0;
                    }
                    if (!ArrakisPort.prepare(level)) {
                        context.getSource().sendSuccess(() -> Component.literal(
                                "L'astroport se construit : relance la commande dans quelques secondes."), false);
                        return 0;
                    }
                    ArrakisPort.arrive(player, level);
                    return 1;
                }))
                .then(Commands.literal("rebatir").executes(context -> {
                    ServerLevel level = ArrakisPort.level(context.getSource().getServer());
                    if (level == null) {
                        context.getSource().sendFailure(Component.literal("Arrakis n'est pas chargee."));
                        return 0;
                    }
                    level.setData(DoHyArrakis.PORT_BUILT.get(), 0);
                    ArrakisPort.prepare(level);
                    context.getSource().sendSuccess(() -> Component.literal("Astroport en reconstruction."), true);
                    return 1;
                }))
                .then(Commands.literal("air").executes(context -> {
                    double oxygen = ArrakisPlanet.oxygen(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.literal(
                            "Oxygene sur Arrakis : " + String.format(java.util.Locale.ROOT, "%.2f", oxygen)), false);
                    return 1;
                }));
    }
}

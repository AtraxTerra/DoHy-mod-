package fr.atraxterra.dohy.faction.absence;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Temporaire : une base d'essai pour le serveur de la sonde. */
@EventBusSubscriber
public final class AbsenceTestCommand {

    private AbsenceTestCommand() {
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dohytest").then(Commands.literal("garder")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer()).executes(context -> {
                            KeptBases.testKeep(context.getSource().getServer(), "minecraft:overworld",
                                    IntegerArgumentType.getInteger(context, "x"),
                                    IntegerArgumentType.getInteger(context, "z"));
                            context.getSource().sendSuccess(() -> Component.literal("base d'essai tenue : "
                                    + KeptBases.any()), false);
                            return 1;
                        })))));
    }
}

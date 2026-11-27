package fr.atraxterra.dohy.palamod.pet;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /pet} ouvre le menu du familier (wiki). Pour les operateurs :
 * {@code /pet admin tame <familier>}, {@code xp <quantite>},
 * {@code happiness <valeur>}, {@code reset} — pour tester, ou rendre un familier
 * perdu.
 */
@EventBusSubscriber
public final class PetCommand {

    private PetCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pet")
                .executes(ctx -> open(ctx.getSource()))
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("tame")
                                .then(Commands.argument("familiar", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (Familiar familiar : Familiar.values()) {
                                                builder.suggest(familiar.id());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> tame(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "familiar")))))
                        .then(Commands.literal("xp")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> xp(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(Commands.literal("happiness")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, PetApi.MAX_HAPPINESS))
                                        .executes(ctx -> happiness(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("reset")
                                .executes(ctx -> reset(ctx.getSource())))));
    }

    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        PetMenu.open(source.getPlayerOrException());
        return 1;
    }

    private static int tame(CommandSourceStack source, String id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Familiar familiar = Familiar.byId(id);
        if (familiar == null) {
            source.sendFailure(Component.literal("Familier inconnu : " + id));
            return 0;
        }
        PetApi.tame(player, familiar);
        source.sendSuccess(() -> Component.literal("[Familier] " + familiar.label() + " est a toi."), false);
        return 1;
    }

    private static int xp(CommandSourceStack source, int amount) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!PetApi.has(player)) {
            source.sendFailure(Component.literal("Pas de familier."));
            return 0;
        }
        PetApi.grantXp(player, amount);
        return 1;
    }

    private static int happiness(CommandSourceStack source, int value) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!PetApi.has(player)) {
            source.sendFailure(Component.literal("Pas de familier."));
            return 0;
        }
        PetApi.addHappiness(player, value - PetApi.data(player).happiness());
        return 1;
    }

    private static int reset(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.setData(PalaPets.DATA.get(), new PetData());
        PetApi.syncLook(player);
        source.sendSuccess(() -> Component.literal("[Familier] Familier retire."), false);
        return 1;
    }
}

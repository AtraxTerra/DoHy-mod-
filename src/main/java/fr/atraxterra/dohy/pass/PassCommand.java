package fr.atraxterra.dohy.pass;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.time.Duration;

/**
 * {@code /pass} : l'ecran du pass. L'administration permet d'essayer un mois
 * sans l'attendre : de l'XP, des quetes finies, une horloge avancee.
 */
@EventBusSubscriber
public final class PassCommand {

    private PassCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pass")
                .executes(context -> {
                    PassMenu.INSTANCE.open(context.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("xp").then(Commands.argument("xp", LongArgumentType.longArg(1L))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    PassApi.rollIfDue(player);
                                    PassApi.grantXp(player, LongArgumentType.getLong(context, "xp"));
                                    return done(context, "XP du pass : " + PassApi.data(player).xp());
                                })))
                        .then(Commands.literal("quetes").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            PassApi.rollIfDue(player);
                            PassData data = PassApi.data(player);
                            long xp = 0L;
                            for (int i = 0; i < data.daily().size(); i++) {
                                if (!data.daily().get(i).done()) {
                                    xp += data.daily().get(i).xp();
                                    data.replace(false, i, data.daily().get(i).finished());
                                }
                            }
                            for (int i = 0; i < data.monthly().size(); i++) {
                                if (!data.monthly().get(i).done()) {
                                    xp += data.monthly().get(i).xp();
                                    data.replace(true, i, data.monthly().get(i).finished());
                                }
                            }
                            PassApi.grantXp(player, xp);
                            return done(context, "Quetes finies : +" + xp + " XP");
                        }))
                        .then(Commands.literal("avancer").then(Commands.argument("jours", IntegerArgumentType.integer(1, 400))
                                .executes(context -> {
                                    PassSeason.advance(Duration.ofDays(IntegerArgumentType.getInteger(context, "jours")));
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    PassApi.rollIfDue(player);
                                    return done(context, "Horloge du pass : " + PassSeason.now().toLocalDateTime()
                                            .withNano(0));
                                })))
                        .then(Commands.literal("horloge").executes(context -> {
                            PassSeason.resetClock();
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            PassApi.rollIfDue(player);
                            return done(context, "Horloge du pass remise a l'heure");
                        }))
                        .then(Commands.literal("recharger").executes(context -> {
                            boolean ok = PassConfig.load(context.getSource().getServer());
                            return done(context, ok ? "Pass recharge" : "Echec du rechargement : voir le journal");
                        }))));
    }

    private static int done(CommandContext<CommandSourceStack> context, String message) throws CommandSyntaxException {
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }
}

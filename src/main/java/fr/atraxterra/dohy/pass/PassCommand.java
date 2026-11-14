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
                                .executes(context -> advance(context, false))
                                .then(Commands.literal("confirmer").executes(context -> advance(context, true)))))
                        .then(Commands.literal("horloge").executes(context -> {
                            PassSeason.resetClock();
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            PassApi.rollIfDue(player);
                            return done(context, "Horloge du pass remise a l'heure : pass " + PassSeason.ofMonth(
                                    PassSeason.current()));
                        }))
                        .then(Commands.literal("recharger").executes(context -> {
                            boolean ok = PassConfig.load(context.getSource().getServer());
                            return done(context, ok ? "Pass recharge" : "Echec du rechargement : voir le journal");
                        }))));
    }

    /**
     * Avance l'horloge du pass pour essayer une bascule. La bascule est vraie : le
     * mois en cours se clot (ce qui est atteint est donne) et sa progression ne
     * revient pas avec {@code horloge}. Sur l'horloge reelle, avec de la
     * progression en jeu, il faut le confirmer.
     */
    private static int advance(CommandContext<CommandSourceStack> context, boolean confirmed)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int days = IntegerArgumentType.getInteger(context, "jours");
        PassApi.rollIfDue(player);
        PassData data = PassApi.data(player);
        if (!confirmed && !PassSeason.shifted() && (data.xp() > 0L || !data.claimedLevels().isEmpty())) {
            context.getSource().sendFailure(Component.literal("Essai d'une vraie bascule : le pass "
                    + PassSeason.ofMonth(data.season()) + " sera clos (ce qui est atteint est donne) et sa progression"
                    + " ne reviendra pas avec /pass admin horloge. Pour continuer : /pass admin avancer " + days
                    + " confirmer"));
            return 0;
        }
        PassSeason.advance(Duration.ofDays(days));
        PassApi.rollIfDue(player);
        return done(context, "Horloge du pass : " + PassSeason.now().toLocalDateTime().withNano(0)
                + " (jusqu'a /pass admin horloge ou un redemarrage)");
    }

    private static int done(CommandContext<CommandSourceStack> context, String message) throws CommandSyntaxException {
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return 1;
    }
}

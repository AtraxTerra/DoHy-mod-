package fr.atraxterra.dohy.palamod.trixium;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.Duration;

/**
 * Le concours Trixium cote serveur : la saison se ferme a la connexion ou, pour
 * les joueurs presents, dans la minute qui suit la bascule ; et la commande
 * {@code /trixium} du wiki.
 */
@EventBusSubscriber
public final class TrixiumEvents {

    private static int ticks;

    private TrixiumEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TrixiumContest.catchUp(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % 1200 != 0) {
            return;
        }
        catchUpAll(event.getServer());
    }

    private static void catchUpAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TrixiumContest.catchUp(player);
        }
    }

    // -- /trixium -------------------------------------------------------------

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("trixium")
                .executes(ctx -> menu(ctx.getSource()))
                .then(Commands.literal("deposer").executes(ctx -> deposit(ctx.getSource())))
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("avancer")
                                .then(Commands.argument("jours", IntegerArgumentType.integer(1, 400))
                                        .executes(ctx -> advance(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "jours")))))
                        .then(Commands.literal("horloge").executes(ctx -> resetClock(ctx.getSource())))));
    }

    private static int menu(CommandSourceStack source) throws CommandSyntaxException {
        TrixiumMenu.INSTANCE.open(source.getPlayerOrException());
        return 1;
    }

    private static int deposit(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        long deposited = TrixiumContest.deposit(player);
        if (deposited <= 0) {
            source.sendFailure(Component.literal("Tu n'as pas de trixium sur toi."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(MoneyFormat.exact(deposited) + " trixium deposes ; total "
                + MoneyFormat.exact(TrixiumContest.data(player).deposited()) + ", rang "
                + TrixiumContest.rank(player) + "."), false);
        return 1;
    }

    /** Avance l'horloge du concours, pour essayer une fin de saison ; rien n'est sauvegarde. */
    private static int advance(CommandSourceStack source, int days) {
        TrixiumSeason.advance(Duration.ofDays(days));
        catchUpAll(source.getServer());
        source.sendSuccess(() -> Component.literal("Horloge du concours avancee de " + days + " j : "
                + TrixiumSeason.now().toLocalDate() + "."), true);
        return 1;
    }

    private static int resetClock(CommandSourceStack source) {
        TrixiumSeason.resetClock();
        source.sendSuccess(() -> Component.literal("Horloge du concours remise a l'heure."), true);
        return 1;
    }
}

package fr.atraxterra.dohy.jobboss;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import fr.atraxterra.dohy.job.Job;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Arrays;

/**
 * Les boss de metier hors de l'ecran : les annonces (toutes les vingt
 * secondes, et a la connexion) et la commande {@code /autel}.
 */
@EventBusSubscriber
public final class JobBossEvents {

    private static int ticks;

    private JobBossEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % 400 == 7) {
            JobBossApi.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JobBossApi.remind(player);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("autel")
                .executes(context -> {
                    JobBossMenu.INSTANCE.openFromAfar(context.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("combattre").then(Commands.argument("metier", StringArgumentType.word())
                        .suggests((context, builder) -> jobs(builder))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            Job job = Job.byKey(StringArgumentType.getString(context, "metier"));
                            if (job == null) {
                                return fail(context, "Metier inconnu.");
                            }
                            String refusal = JobBossApi.fight(player, job);
                            return refusal == null ? 1 : fail(context, refusal);
                        })))
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("pret").then(Commands.argument("metier", StringArgumentType.word())
                                .suggests((context, builder) -> jobs(builder))
                                .executes(context -> admin(context, "pret", 0))))
                        .then(Commands.literal("offrande").then(Commands.argument("metier", StringArgumentType.word())
                                .suggests((context, builder) -> jobs(builder))
                                .executes(context -> admin(context, "offrande", 0))))
                        .then(Commands.literal("niveau").then(Commands.argument("metier", StringArgumentType.word())
                                .suggests((context, builder) -> jobs(builder))
                                .then(Commands.argument("niveau", IntegerArgumentType.integer(1, 10))
                                        .executes(context -> admin(context, "niveau",
                                                IntegerArgumentType.getInteger(context, "niveau"))))))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> jobs(
            SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Arrays.stream(Job.values()).map(Job::key), builder);
    }

    private static int admin(CommandContext<CommandSourceStack> context, String what, int level)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Job job = Job.byKey(StringArgumentType.getString(context, "metier"));
        if (job == null) {
            return fail(context, "Metier inconnu.");
        }
        switch (what) {
            case "pret" -> JobBossApi.makeReady(player, job);
            case "offrande" -> JobBossApi.fill(player, job);
            default -> JobBossApi.setLevel(player, job, level);
        }
        context.getSource().sendSuccess(() -> Component.literal("Autel : " + what + " " + job.key() + " (niveau "
                + JobBossApi.data(player).guardian(job).level() + ")"), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendFailure(Component.literal(message).withStyle(ChatFormatting.RED));
        return 0;
    }
}

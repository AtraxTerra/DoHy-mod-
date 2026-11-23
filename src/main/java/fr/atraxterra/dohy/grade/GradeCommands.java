package fr.atraxterra.dohy.grade;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;

/**
 * Les commandes des grades. Toutes sont ouvertes a tous : celles qui demandent un grade disent
 * lequel et menent a la page des grades.
 *
 * <ul>
 *   <li>pour tous : {@code /grade}, {@code /sethome}, {@code /home}, {@code /delhome},
 *       {@code /homes}, {@code /rtp}, {@code /tpa}, {@code /tpahere}, {@code /tpaccept},
 *       {@code /tpdeny} ;</li>
 *   <li>par grade : celles de {@link GradeCommand} ;</li>
 *   <li>administration : {@code /dohy grade <joueur> <grade>} et
 *       {@code /dohy premium <joueur> <jours>} (voir {@link #admin()}).</li>
 * </ul>
 */
@EventBusSubscriber
public final class GradeCommands {

    private GradeCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("grade").executes(context -> open(context)));
        dispatcher.register(Commands.literal("grades").executes(context -> open(context)));

        dispatcher.register(Commands.literal("sethome")
                .executes(context -> run(context, player -> GradeTools.setHome(player, null)))
                .then(Commands.argument("nom", StringArgumentType.word())
                        .executes(context -> run(context, player -> GradeTools.setHome(player,
                                StringArgumentType.getString(context, "nom"))))));
        dispatcher.register(Commands.literal("home")
                .executes(context -> run(context, player -> GradeTools.home(player, null)))
                .then(Commands.argument("nom", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                context.getSource().getPlayer() == null ? java.util.List.<String>of()
                                        : Grades.data(context.getSource().getPlayer()).sortedHomes().keySet(), builder))
                        .executes(context -> run(context, player -> GradeTools.home(player,
                                StringArgumentType.getString(context, "nom"))))));
        dispatcher.register(Commands.literal("delhome")
                .then(Commands.argument("nom", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                context.getSource().getPlayer() == null ? java.util.List.<String>of()
                                        : Grades.data(context.getSource().getPlayer()).sortedHomes().keySet(), builder))
                        .executes(context -> run(context, player -> GradeTools.delHome(player,
                                StringArgumentType.getString(context, "nom"))))));
        dispatcher.register(Commands.literal("homes").executes(context -> run(context, GradeTools::homes)));
        dispatcher.register(Commands.literal("rtp").executes(context -> run(context, GradeTools::rtp)));

        dispatcher.register(Commands.literal("tpa")
                .then(Commands.argument("joueur", EntityArgument.player())
                        .executes(context -> request(context, false))));
        dispatcher.register(Commands.literal("tpahere")
                .then(Commands.argument("joueur", EntityArgument.player())
                        .executes(context -> request(context, true))));
        dispatcher.register(Commands.literal("tpaccept")
                .executes(context -> run(context, player -> GradeTeleports.accept(player, null)))
                .then(Commands.argument("joueur", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer from = EntityArgument.getPlayer(context, "joueur");
                            return run(context, player -> GradeTeleports.accept(player, from));
                        })));
        dispatcher.register(Commands.literal("tpdeny")
                .executes(context -> run(context, player -> GradeTeleports.deny(player, null)))
                .then(Commands.argument("joueur", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer from = EntityArgument.getPlayer(context, "joueur");
                            return run(context, player -> GradeTeleports.deny(player, from));
                        })));

        // Une commande par avantage de grade, chacune enregistree a part.
        dispatcher.register(Commands.literal("craft").executes(context -> grade(context, GradeCommand.CRAFT)));
        dispatcher.register(Commands.literal("minage").executes(context -> grade(context, GradeCommand.MINAGE)));
        dispatcher.register(Commands.literal("farmland").executes(context -> grade(context, GradeCommand.FARMLAND)));
        dispatcher.register(Commands.literal("feed").executes(context -> grade(context, GradeCommand.FEED)));
        dispatcher.register(Commands.literal("furnace").executes(context -> grade(context, GradeCommand.FURNACE)));
        dispatcher.register(Commands.literal("rangement").executes(context -> grade(context, GradeCommand.RANGEMENT)));
        dispatcher.register(Commands.literal("tptoggle").executes(context -> grade(context, GradeCommand.TPTOGGLE)));
        dispatcher.register(Commands.literal("condense").executes(context -> grade(context, GradeCommand.CONDENSE)));
        dispatcher.register(Commands.literal("hat").executes(context -> grade(context, GradeCommand.HAT)));
        dispatcher.register(Commands.literal("deathlocate")
                .executes(context -> grade(context, GradeCommand.DEATHLOCATE)));
        dispatcher.register(Commands.literal("ec").executes(context -> grade(context, GradeCommand.EC)));
        dispatcher.register(Commands.literal("fly").executes(context -> grade(context, GradeCommand.FLY)));
        dispatcher.register(Commands.literal("nv").executes(context -> grade(context, GradeCommand.NV)));
    }

    /**
     * {@code /dohy grade <joueur> <aucun|heros|legende|divinite>} et
     * {@code /dohy premium <joueur> <jours>} : pour essayer, ou offrir un grade sur un serveur.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("grade")
                .then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.argument("grade", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.asList("aucun", "heros", "legende", "divinite"), builder))
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "joueur");
                                    String id = StringArgumentType.getString(context, "grade");
                                    PlayerGrade grade = PlayerGrade.byId("aucun".equals(id) ? "" : id);
                                    if (grade == PlayerGrade.NONE && !"aucun".equals(id)) {
                                        context.getSource().sendFailure(Component.literal("Grade inconnu : " + id));
                                        return 0;
                                    }
                                    Grades.set(target, grade);
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            target.getName().getString() + " : " + grade.label() + "."), true);
                                    return 1;
                                })));
    }

    /** {@code /dohy premium <joueur> <jours>} ; 0 jour l'enleve. */
    public static LiteralArgumentBuilder<CommandSourceStack> adminPremium() {
        return Commands.literal("premium")
                .then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.argument("jours", IntegerArgumentType.integer(0, Premium.MAX_DAYS))
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "joueur");
                                    int days = IntegerArgumentType.getInteger(context, "jours");
                                    Grades.setPremiumDays(target, days);
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            target.getName().getString() + " : " + (days > 0 ? "Premium pour "
                                                    + days + " jours." : "plus de Premium.")), true);
                                    return 1;
                                })));
    }

    private static int open(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        GradesController.INSTANCE.open(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int grade(CommandContext<CommandSourceStack> context, GradeCommand command)
            throws CommandSyntaxException {
        GradeTools.run(context.getSource().getPlayerOrException(), command);
        return 1;
    }

    private static int request(CommandContext<CommandSourceStack> context, boolean here)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "joueur");
        GradeTeleports.request(context.getSource().getPlayerOrException(), target, here);
        return 1;
    }

    private interface PlayerAction {
        void accept(ServerPlayer player);
    }

    private static int run(CommandContext<CommandSourceStack> context, PlayerAction action)
            throws CommandSyntaxException {
        action.accept(context.getSource().getPlayerOrException());
        return 1;
    }
}

package fr.atraxterra.dohy.faction.war;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /guerre} : l'ecran de la guerre, et la diplomatie au clavier.
 *
 * <ul>
 *   <li>{@code /guerre} ouvre l'ecran ;</li>
 *   <li>{@code payer}, {@code negocier}, {@code refuser} repondent a un ultimatum ;</li>
 *   <li>{@code heure 21:00}, {@code heure demain 18h} fixent l'assaut ;</li>
 *   <li>{@code paix} achete la paix, {@code cadeau <nation> <montant>} adoucit une nation ;</li>
 *   <li>{@code coffre} designe ton coffre, la ou tu te tiens ;</li>
 *   <li>{@code admin} (operateurs) : menacer, declarer, lancer un assaut, l'arreter.</li>
 * </ul>
 */
@EventBusSubscriber
public final class WarCommand {

    private WarCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(command("guerre"));
        event.getDispatcher().register(command("war"));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> nationArgument() {
        return Commands.argument("nation", StringArgumentType.word())
                .suggests((context, builder) -> {
                    List<String> slugs = new ArrayList<>();
                    for (int i = 0; i < NationTraits.count(); i++) {
                        slugs.add(NationTraits.of(i).slug());
                    }
                    return SharedSuggestionProvider.suggest(slugs, builder);
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return Commands.literal(name)
                .executes(context -> {
                    WarMenu.INSTANCE.open(context.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("payer").executes(context -> answer(context, WarStrategy.pay(
                        context.getSource().getPlayerOrException()), "Tribut paye.")))
                .then(Commands.literal("refuser").executes(context -> answer(context, WarStrategy.refuse(
                        context.getSource().getPlayerOrException()), null)))
                .then(Commands.literal("negocier").executes(context -> answer(context, WarStrategy.negotiate(
                        context.getSource().getPlayerOrException()), null)))
                .then(Commands.literal("paix").executes(context -> answer(context, WarStrategy.peace(
                        context.getSource().getPlayerOrException()), null)))
                .then(Commands.literal("coffre").executes(context -> answer(context, WarStrategy.setVault(
                        context.getSource().getPlayerOrException()), "Ton coffre est ici : c'est lui que les "
                        + "nations viendront prendre. Defends-le.")))
                .then(Commands.literal("heure").then(Commands.argument("quand", StringArgumentType.greedyString())
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            long at = WarStrategy.parse(StringArgumentType.getString(context, "quand"),
                                    System.currentTimeMillis());
                            if (at < 0L) {
                                return answer(context, "Ecris une heure : 21:00, 18h, demain 20:30", null);
                            }
                            return answer(context, WarStrategy.choose(player, at), null);
                        })))
                .then(Commands.literal("cadeau").then(nationArgument()
                        .then(Commands.argument("montant", LongArgumentType.longArg(100L))
                                .executes(context -> {
                                    int nation = NationTraits.find(StringArgumentType.getString(context, "nation"));
                                    if (nation < 0) {
                                        return answer(context, "Nation inconnue", null);
                                    }
                                    return answer(context, WarStrategy.gift(context.getSource().getPlayerOrException(),
                                            nation, LongArgumentType.getLong(context, "montant")), null);
                                }))))
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("menace").then(nationArgument().executes(context -> admin(context,
                                "menace"))))
                        .then(Commands.literal("declarer").then(nationArgument().executes(context -> admin(context,
                                "declarer"))))
                        .then(Commands.literal("assaut").then(nationArgument().executes(context -> admin(context,
                                "assaut"))))
                        .then(Commands.literal("hostilite").then(nationArgument()
                                .then(Commands.argument("valeur", DoubleArgumentType.doubleArg(0.0d, 100.0d))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            int nation = NationTraits.find(StringArgumentType.getString(context,
                                                    "nation"));
                                            if (nation < 0) {
                                                return answer(context, "Nation inconnue", null);
                                            }
                                            WarData.Relation relation = WarStrategy.data(player).relation(nation,
                                                    System.currentTimeMillis());
                                            relation.hostility = DoubleArgumentType.getDouble(context, "valeur");
                                            return answer(context, null, "Hostilite fixee.");
                                        }))))
                        .then(Commands.literal("fin").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            Siege siege = Sieges.of(player.getUUID());
                            if (siege == null) {
                                return answer(context, "Aucun siege en cours", null);
                            }
                            siege.suspend(false);
                            return answer(context, null, "Siege arrete : le terrain revient.");
                        }))
                        .then(Commands.literal("etat").executes(WarCommand::state)));
    }

    private static int answer(CommandContext<CommandSourceStack> context, String refusal, String success)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (refusal != null) {
            FactionApi.tell(player, refusal, ChatFormatting.RED);
            return 0;
        }
        if (success != null) {
            FactionApi.tell(player, success, ChatFormatting.GREEN);
        }
        return 1;
    }

    private static int admin(CommandContext<CommandSourceStack> context, String what) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int nation = NationTraits.find(StringArgumentType.getString(context, "nation"));
        if (nation < 0) {
            return answer(context, "Nation inconnue", null);
        }
        if (!FactionApi.has(player)) {
            return answer(context, "Il faut une faction", null);
        }
        long now = System.currentTimeMillis();
        WarData data = WarStrategy.data(player);
        WarData.Relation relation = data.relation(nation, now);
        for (WarData.Relation other : data.relations()) {
            if (other != relation && (other.stance == WarData.Stance.MENACE || other.stance == WarData.Stance.GUERRE)) {
                other.stance(WarData.Stance.PAIX, now);
            }
        }
        switch (what) {
            case "menace" -> WarStrategy.ultimatum(player, data, relation, now);
            case "declarer" -> WarStrategy.declare(player, data, relation, now, true);
            default -> {
                if (Sieges.of(player.getUUID()) != null) {
                    return answer(context, "Un siege est deja en cours", null);
                }
                if (relation.stance != WarData.Stance.GUERRE) {
                    relation.stance(WarData.Stance.GUERRE, now);
                    relation.deadline = now + WarStrategy.WINDOW_MS;
                }
                relation.army = Math.max(relation.army, 20);
                WarStrategy.launch(player, data, relation, now);
            }
        }
        return 1;
    }

    private static int state(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        long now = System.currentTimeMillis();
        WarData data = WarStrategy.data(player);
        for (WarData.Relation relation : data.relations()) {
            NationTraits traits = relation.traits();
            player.sendSystemMessage(Component.literal(traits.name() + " : " + relation.stance().name().toLowerCase()
                    + ", hostilite " + Math.round(relation.hostility()) + " (vise "
                    + Math.round(WarStrategy.mood(player, data, relation, now)) + "), crainte "
                    + Math.round(relation.fear()) + ", armee " + relation.army() + ", tresor "
                    + MoneyFormat.compact(relation.treasury()) + ", griefs " + Math.round(relation.grievance(now)))
                    .withStyle(ChatFormatting.GRAY));
        }
        Siege siege = Sieges.of(player.getUUID());
        if (siege != null) {
            player.sendSystemMessage(Component.literal("Siege : " + siege.phase.name().toLowerCase() + ", "
                    + siege.status() + ", moral " + Math.round(siege.morale) + ", " + siege.alive().size()
                    + " soldats, reserve " + siege.reserveCount() + ", plan "
                    + (siege.plan == null ? "-" : siege.plan.summary())).withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }
}

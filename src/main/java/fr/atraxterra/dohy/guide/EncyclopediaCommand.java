package fr.atraxterra.dohy.guide;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atraxterra.dohy.guide.plan.Blueprint;
import fr.atraxterra.dohy.guide.plan.Blueprints;
import fr.atraxterra.dohy.guide.plan.PlanItem;
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
 * {@code /encyclopedie [fiche]} (et {@code /wiki}) : le sommaire, ou une fiche.
 * {@code /plan <structure>} donne un plan de construction. Operateurs :
 * {@code /encyclopedie admin recharger} relit les fiches du datapack.
 */
@EventBusSubscriber
public final class EncyclopediaCommand {

    private EncyclopediaCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(command("encyclopedie"));
        event.getDispatcher().register(command("wiki"));
        event.getDispatcher().register(Commands.literal("plan")
                .executes(context -> {
                    EncyclopediaMenu.openEntry(context.getSource().getPlayerOrException(), "plans");
                    return 1;
                })
                .then(Commands.argument("structure", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            List<String> ids = new ArrayList<>();
                            for (Blueprint blueprint : Blueprints.all()) {
                                ids.add(blueprint.id());
                            }
                            return SharedSuggestionProvider.suggest(ids, builder);
                        })
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String id = StringArgumentType.getString(context, "structure");
                            Blueprint blueprint = Blueprints.get(id);
                            if (blueprint == null) {
                                player.sendSystemMessage(Component.literal("Plan inconnu : " + id)
                                        .withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            if (!player.getInventory().add(PlanItem.of(id, 0))) {
                                player.drop(PlanItem.of(id, 0), false);
                            }
                            player.sendSystemMessage(Component.literal("Plan recu : " + blueprint.name()
                                    + ". Clic droit sur un bloc pour le poser.").withStyle(ChatFormatting.GREEN));
                            return 1;
                        })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return Commands.literal(name)
                .executes(context -> {
                    EncyclopediaMenu.openIndex(context.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("recharger").executes(context -> {
                            boolean ok = Encyclopedia.load(context.getSource().getServer());
                            context.getSource().sendSuccess(() -> Component.literal(ok
                                    ? "Encyclopedie rechargee : " + Encyclopedia.entries().size() + " fiches."
                                    : "Echec du rechargement : voir le journal."), true);
                            return ok ? 1 : 0;
                        })))
                .then(Commands.argument("fiche", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            List<String> ids = new ArrayList<>();
                            for (Encyclopedia.Entry entry : Encyclopedia.entries()) {
                                ids.add(entry.id());
                            }
                            return SharedSuggestionProvider.suggest(ids, builder);
                        })
                        .executes(context -> {
                            EncyclopediaMenu.openEntry(context.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(context, "fiche"));
                            return 1;
                        }));
    }
}

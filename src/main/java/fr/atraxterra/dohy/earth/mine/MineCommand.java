package fr.atraxterra.dohy.earth.mine;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.atraxterra.dohy.earth.EarthBodies;
import fr.atraxterra.dohy.faction.FactionApi;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mines} (et {@code /f mines}) : l'ecran des mines de la Terre ; rendre une mine ; pour
 * les operateurs, en donner une et faire passer des heures de production.
 */
public final class MineCommand {

    private static final SuggestionProvider<CommandSourceStack> MINES = (ctx, builder) -> {
        List<String> names = new ArrayList<>();
        for (EarthBodies.Body body : EarthBodies.all()) {
            names.add(body.id());
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private MineCommand() {
    }

    private interface Action {
        String run(ServerPlayer player) throws CommandSyntaxException;
    }

    private static int act(CommandSourceStack source, Action action, String success) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String refusal = action.run(player);
        if (refusal != null) {
            FactionApi.tell(player, refusal, ChatFormatting.RED);
            return 0;
        }
        if (!success.isEmpty()) {
            FactionApi.tell(player, success, ChatFormatting.GREEN);
        }
        return 1;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("mines")
                .executes(ctx -> {
                    MineMenu.INSTANCE.open(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("abandonner")
                        .then(Commands.argument("mine", StringArgumentType.greedyString()).suggests(MINES)
                                .executes(ctx -> act(ctx.getSource(), player -> {
                                    EarthBodies.Body body = EarthMines.find(StringArgumentType.getString(ctx, "mine"));
                                    return body == null ? "Mine inconnue" : EarthMines.abandon(player, body.id());
                                }, ""))))
                // Pour les operateurs : une mine tout de suite, sans balise ni attente.
                .then(Commands.literal("donner")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("mine", StringArgumentType.greedyString()).suggests(MINES)
                                .executes(ctx -> act(ctx.getSource(), player -> EarthMines.give(player,
                                        StringArgumentType.getString(ctx, "mine")), "Mine donnee a ta faction."))))
                // Pour les operateurs : comme si des heures avaient passe, et le versement qui suit.
                .then(Commands.literal("avancer")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("heures", IntegerArgumentType.integer(1, 24 * 30))
                                .executes(ctx -> act(ctx.getSource(), player -> EarthMines.advance(player,
                                        IntegerArgumentType.getInteger(ctx, "heures")), ""))));
    }
}

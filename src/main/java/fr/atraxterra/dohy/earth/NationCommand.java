package fr.atraxterra.dohy.earth;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.event.EventRun;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /f pays} : l'ecran des pays a prendre, et ses gestes sans l'ouvrir —
 * poser sa capitale, viser un pays, y renoncer, en rendre un.
 */
public final class NationCommand {

    private static final SuggestionProvider<CommandSourceStack> COUNTRIES = (ctx, builder) -> {
        EarthData earth = EarthData.get();
        List<String> names = new ArrayList<>();
        if (earth != null) {
            for (EarthData.Country country : earth.config().countries()) {
                names.add(country.name());
            }
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private NationCommand() {
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

    private static int country(CommandContext<CommandSourceStack> ctx) {
        EarthData.Country found = EarthNations.lookup(StringArgumentType.getString(ctx, "pays"));
        return found == null ? 0 : found.id();
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("pays")
                .executes(ctx -> {
                    NationMenu.INSTANCE.open(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("capitale")
                        .executes(ctx -> act(ctx.getSource(), EarthNations::setCapital, "Capitale posee ici.")))
                .then(Commands.literal("viser")
                        .executes(ctx -> act(ctx.getSource(), player -> describe(player, EarthNations.targetHere(player)),
                                ""))
                        .then(Commands.argument("pays", StringArgumentType.greedyString()).suggests(COUNTRIES)
                                .executes(ctx -> act(ctx.getSource(), player -> {
                                    int id = country(ctx);
                                    return id == 0 ? "Pays inconnu" : describe(player, EarthNations.target(player, id));
                                }, ""))))
                .then(Commands.literal("renoncer")
                        .executes(ctx -> act(ctx.getSource(), EarthNations::dropTarget,
                                "Plus de cible : les points deja reunis restent acquis.")))
                .then(Commands.literal("abandonner")
                        .then(Commands.argument("pays", StringArgumentType.greedyString()).suggests(COUNTRIES)
                                .executes(ctx -> act(ctx.getSource(), player -> {
                                    int id = country(ctx);
                                    return id == 0 ? "Pays inconnu" : EarthNations.abandon(player, id);
                                }, "Pays rendu a la nation de sa region."))))
                .then(Commands.literal("liste")
                        .executes(ctx -> list(ctx.getSource().getPlayerOrException())))
                // Pour les operateurs : donner un pays tout de suite, comme s'il venait de tomber.
                .then(Commands.literal("donner")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("pays", StringArgumentType.greedyString()).suggests(COUNTRIES)
                                .executes(ctx -> act(ctx.getSource(), player -> {
                                    int id = country(ctx);
                                    if (id == 0) {
                                        return "Pays inconnu";
                                    }
                                    if (!FactionApi.has(player)) {
                                        return "Fonde d'abord ta faction";
                                    }
                                    EarthNations.Holder holder = EarthNations.holder(player.level().getServer(), id);
                                    if (holder.player()) {
                                        return "Ce pays est deja aux mains de " + holder.name();
                                    }
                                    EarthNations.conquer(player, id, holder);
                                    return null;
                                }, ""))));
    }

    /** Apres une cible posee : contre qui, et combien. */
    private static String describe(ServerPlayer player, String refusal) {
        if (refusal != null) {
            return refusal;
        }
        MinecraftServer server = player.level().getServer();
        int target = EarthNations.nation(player).target();
        FactionApi.tell(player, "Cible : " + EarthNations.countryName(target) + ", aux mains de "
                + EarthNations.holderName(player, target) + ". Finis devant eux aux events de faction : "
                + MoneyFormat.exact(EarthNations.nation(player).pointsOn(target)) + " / "
                + MoneyFormat.exact(EarthNations.cost(server, target)) + " points de conquete.", ChatFormatting.GREEN);
        return null;
    }

    /** Tes pays, puis ce que tient chaque nation rivale. */
    private static int list(ServerPlayer player) {
        if (!EarthNations.active()) {
            FactionApi.tell(player, "Ce monde n'a pas de carte de la Terre.", ChatFormatting.RED);
            return 0;
        }
        MinecraftServer server = player.level().getServer();
        List<Integer> mine = EarthNations.heldBy(player);
        player.sendSystemMessage(FactionApi.prefix().append(Component.literal("Ta nation : " + mine.size() + " pays"
                + (mine.isEmpty() ? "" : " (" + names(mine, 8) + ")")).withStyle(ChatFormatting.WHITE)));
        List<List<Integer>> rivals = EarthNations.rivalHoldings(server);
        List<EventRun.Rival> pool = EventRun.rivalPool();
        for (int i = 0; i < rivals.size(); i++) {
            List<Integer> held = rivals.get(i);
            player.sendSystemMessage(Component.literal(" " + pool.get(i).name() + " : " + held.size() + " pays"
                    + (held.isEmpty() ? "" : " (" + names(held, 4) + ")"))
                    .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(pool.get(i).color().getTextColor())));
        }
        return 1;
    }

    static String names(List<Integer> countries, int max) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(max, countries.size()); i++) {
            out.add(EarthNations.countryName(countries.get(i)));
        }
        if (countries.size() > max) {
            out.add("...");
        }
        return String.join(", ", out);
    }
}

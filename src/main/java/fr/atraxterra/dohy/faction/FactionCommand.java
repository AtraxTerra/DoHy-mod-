package fr.atraxterra.dohy.faction;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * {@code /f} (ou {@code /faction}) : l'ecran, et les gestes du quotidien sans
 * l'ouvrir — revendiquer, rentrer au home, deposer.
 */
@EventBusSubscriber
public final class FactionCommand {

    private FactionCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(build("f"));
        event.getDispatcher().register(build("faction"));
    }

    private interface Action {
        int run(ServerPlayer player) throws CommandSyntaxException;
    }

    private static int as(CommandSourceStack source, Action action) throws CommandSyntaxException {
        return action.run(source.getPlayerOrException());
    }

    private static int result(ServerPlayer player, String refusal, String success) {
        if (refusal != null) {
            FactionApi.tell(player, refusal, ChatFormatting.RED);
            return 0;
        }
        if (!success.isEmpty()) {
            FactionApi.tell(player, success, ChatFormatting.GREEN);
        }
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String root) {
        return Commands.literal(root)
                .executes(ctx -> as(ctx.getSource(), player -> {
                    FactionMenus.Overview.INSTANCE.open(player);
                    return 1;
                }))
                .then(Commands.literal("create")
                        .executes(ctx -> as(ctx.getSource(), player -> {
                            FactionEdit.open(player);
                            return 1;
                        }))
                        .then(Commands.argument("nom", StringArgumentType.greedyString())
                                .executes(ctx -> as(ctx.getSource(), player -> result(player,
                                        FactionApi.create(player, StringArgumentType.getString(ctx, "nom"), "",
                                                List.of(), 14), "")))))
                .then(Commands.literal("claim")
                        .executes(ctx -> as(ctx.getSource(), player -> result(player, FactionApi.claim(player),
                                "Chunk revendique."))))
                .then(Commands.literal("unclaim")
                        .executes(ctx -> as(ctx.getSource(), player -> result(player, FactionApi.unclaimHere(player),
                                "Chunk rendu."))))
                .then(Commands.literal("unclaimall")
                        .then(Commands.literal("confirm")
                                .executes(ctx -> as(ctx.getSource(), player -> result(player, null,
                                        FactionApi.unclaimAll(player) + " claim(s) rendu(s).")))))
                .then(Commands.literal("home")
                        .executes(ctx -> as(ctx.getSource(), player -> result(player, FactionHomes.go(player, 0), "")))
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1, 3))
                                .executes(ctx -> as(ctx.getSource(), player -> result(player,
                                        FactionHomes.go(player, IntegerArgumentType.getInteger(ctx, "numero") - 1),
                                        "")))))
                .then(Commands.literal("sethome")
                        .executes(ctx -> as(ctx.getSource(), player -> result(player, FactionHomes.set(player, 0),
                                "Home pose ici.")))
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1, 3))
                                .executes(ctx -> as(ctx.getSource(), player -> result(player,
                                        FactionHomes.set(player, IntegerArgumentType.getInteger(ctx, "numero") - 1),
                                        "Home pose ici.")))))
                .then(Commands.literal("bank")
                        .executes(ctx -> as(ctx.getSource(), player -> {
                            FactionMenus.Bank.INSTANCE.open(player);
                            return 1;
                        }))
                        .then(Commands.literal("deposit")
                                .then(Commands.argument("montant", LongArgumentType.longArg(1))
                                        .executes(ctx -> as(ctx.getSource(), player -> result(player,
                                                FactionApi.deposit(player, LongArgumentType.getLong(ctx, "montant")),
                                                "Depot fait.")))))
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument("montant", LongArgumentType.longArg(1))
                                        .executes(ctx -> as(ctx.getSource(), player -> result(player,
                                                FactionApi.withdraw(player, LongArgumentType.getLong(ctx, "montant")),
                                                "Retrait fait."))))))
                .then(Commands.literal("info")
                        .executes(ctx -> as(ctx.getSource(), FactionCommand::info)))
                .then(Commands.literal("map")
                        .executes(ctx -> as(ctx.getSource(), FactionCommand::map)))
                .then(Commands.literal("disband")
                        .then(Commands.literal("confirm")
                                .executes(ctx -> as(ctx.getSource(), player -> {
                                    FactionApi.disband(player);
                                    return 1;
                                }))))
                .then(Commands.literal("elo")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("montant", LongArgumentType.longArg(1))
                                .executes(ctx -> as(ctx.getSource(), player -> {
                                    FactionApi.addElo(player, LongArgumentType.getLong(ctx, "montant"), "commande");
                                    return 1;
                                }))));
    }

    private static int info(ServerPlayer player) {
        FactionData data = FactionApi.data(player);
        if (!data.exists()) {
            return result(player, "Tu n'as pas de faction : /f create", "");
        }
        FactionPerks perks = FactionPerks.of(data);
        FactionLeague league = FactionLeague.of(data.ranking().league());
        player.sendSystemMessage(FactionApi.prefix().append(Component.literal(data.name()).withStyle(ChatFormatting.WHITE)));
        player.sendSystemMessage(Component.literal(" Ligue " + league.label() + " · "
                + MoneyFormat.exact(data.ranking().elo()) + " ELO").withStyle(league.format()));
        player.sendSystemMessage(Component.literal(" Claims " + data.claims().size() + "/" + perks.claims()
                + " · banque " + MoneyFormat.exact(data.bank()) + " $").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    /** Les chunks autour du joueur : toi en vert, les autres en rouge, le libre en gris. */
    private static int map(ServerPlayer player) {
        ChunkPos center = player.chunkPosition();
        int radius = 4;
        player.sendSystemMessage(FactionApi.prefix().append(Component.literal("Carte des claims (+ : toi)")
                .withStyle(ChatFormatting.GRAY)));
        for (int dz = -radius; dz <= radius; dz++) {
            MutableComponent line = Component.literal(" ");
            for (int dx = -radius; dx <= radius; dx++) {
                int x = center.x() + dx;
                int z = center.z() + dz;
                ClaimOwner owner = FactionApi.claimAt(player.level(), new BlockPos(x << 4, player.getBlockY(), z << 4));
                String symbol = dx == 0 && dz == 0 ? "+" : "■";
                ChatFormatting color = owner.ownedBy(player.getUUID()) ? ChatFormatting.GREEN
                        : owner.claimed() ? ChatFormatting.RED : ChatFormatting.DARK_GRAY;
                line.append(Component.literal(symbol + " ").withStyle(color));
            }
            player.sendSystemMessage(line);
        }
        return 1;
    }
}

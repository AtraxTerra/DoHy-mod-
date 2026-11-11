package fr.atraxterra.dohy.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atraxterra.dohy.boost.Autosell;
import fr.atraxterra.dohy.boost.BoostData;
import fr.atraxterra.dohy.boost.Boosters;
import fr.atraxterra.dohy.boost.Crates;
import fr.atraxterra.dohy.palamod.box.CrateKeys;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /booster}, {@code /crate} et {@code /autosell}. */
@EventBusSubscriber
public final class BoostCommand {

    private BoostCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(booster());
        event.getDispatcher().register(crate());
        event.getDispatcher().register(autosell());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> booster() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("booster")
                .executes(ctx -> boosterStatus(ctx.getSource()));
        for (Boosters.Tier tier : Boosters.TIERS) {
            root = root.then(Commands.literal(tier.key())
                    .executes(ctx -> buyBooster(ctx.getSource(), tier)));
        }
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> crate() {
        return Commands.literal("crate")
                .executes(ctx -> crateStatus(ctx.getSource()))
                .then(Commands.literal("buy")
                        .then(Commands.argument("box", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    Crates.ordered().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> buyKey(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "box")))))
                .then(Commands.literal("open")
                        .then(Commands.argument("box", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    Crates.ordered().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> openCrate(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "box")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> autosell() {
        return Commands.literal("autosell")
                .executes(ctx -> autosellStatus(ctx.getSource()))
                .then(Commands.literal("unlock")
                        .executes(ctx -> unlockAutosell(ctx.getSource())))
                .then(Commands.literal("link")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> linkAutosell(ctx.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos")))));
    }

    private static int boosterStatus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        double active = Boosters.active(player);

        if (active > 1.0d) {
            source.sendSuccess(() -> Component.literal("Booster x" + (long) active + " actif — "
                            + Boosters.remaining(player) + " min de jeu restantes")
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.literal("Aucun booster actif")
                    .withStyle(ChatFormatting.GRAY), false);
        }

        source.sendSuccess(() -> Component.literal("  Duree : "
                        + Boosters.DURATION_MINUTES + " min de jeu, non cumulables.")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        for (Boosters.Tier tier : Boosters.TIERS) {
            source.sendSuccess(() -> Component.literal("  /booster " + tier.key() + " — ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(MoneyFormat.exact(tier.price()))
                            .withStyle(ChatFormatting.GOLD)), false);
        }
        return 1;
    }

    private static int buyBooster(CommandSourceStack source, Boosters.Tier tier) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String refusal = Boosters.buy(player, tier);
        if (refusal != null) {
            source.sendFailure(Component.literal(refusal));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Booster x" + (long) tier.multiplier()
                        + " actif pour " + Boosters.DURATION_MINUTES + " min de jeu. Vends maintenant.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int crateStatus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        source.sendSuccess(() -> Component.literal("Boxes").withStyle(ChatFormatting.GOLD), false);
        for (String tier : Crates.ordered()) {
            long price = Crates.keyPrice(tier);
            source.sendSuccess(() -> Component.literal("  " + tier + " (" + Crates.title(tier) + ") — ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(MoneyFormat.exact(price)).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("  cles : " + CrateKeys.count(player, tier))
                            .withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        source.sendSuccess(() -> Component.literal("  /crate buy <box> puis /crate open <box>")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static int buyKey(CommandSourceStack source, String tier) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String refusal = Crates.buyKey(player, tier);
        if (refusal != null) {
            source.sendFailure(Component.literal(refusal));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Une " + Crates.keyName(tier) + " achetee — /crate open "
                        + Crates.canonical(tier))
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int openCrate(CommandSourceStack source, String tier) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String refusal = Crates.open(player, tier);
        if (refusal != null) {
            source.sendFailure(Component.literal(refusal));
            return 0;
        }
        return 1;
    }

    private static int autosellStatus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BoostData data = Boosters.data(player);

        if (!data.autosellUnlocked()) {
            source.sendSuccess(() -> Component.literal("Autosell verrouille — "
                            + MoneyFormat.exact(Autosell.UNLOCK_PRICE) + " (/autosell unlock)")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        BlockPos pos = data.autosellPos();
        source.sendSuccess(() -> Component.literal(pos == null
                        ? "Autosell debloque, aucun coffre lie. Place-toi pres d'un coffre et fais /autosell link ~ ~ ~"
                        : "Coffre lie en " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                                + " — passage toutes les " + Autosell.INTERVAL_MINUTES + " min de jeu")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int unlockAutosell(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String refusal = Autosell.unlock(player);
        if (refusal != null) {
            source.sendFailure(Component.literal(refusal));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                        "Autosell debloque. Lie un coffre avec /autosell link ~ ~ ~")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int linkAutosell(CommandSourceStack source, BlockPos pos) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String refusal = Autosell.link(player, pos);
        if (refusal != null) {
            source.sendFailure(Component.literal(refusal));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Coffre lie. Son contenu vendable partira toutes les "
                        + Autosell.INTERVAL_MINUTES + " min de jeu.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}

package fr.atraxterra.dohy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atraxterra.dohy.collection.CollectionTiers;
import fr.atraxterra.dohy.skill.SkillConfig;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.world.PlacedBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Commandes de diagnostic.
 *
 * <p>Le suivi des blocs poses est invisible par nature : rien dans le jeu ne
 * montre si une position est marquee. Sans moyen de l'interroger, une
 * regression passerait inapercue jusqu'a ce qu'un skill distribue de l'XP sur
 * des blocs reposes — c'est-a-dire trop tard. D'ou ces commandes.
 */
@EventBusSubscriber
public final class DoHyCommand {

    private DoHyCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(build());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("dohy")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("placed")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> placed(ctx.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
                .then(Commands.literal("reload")
                        .executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("chunk")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> chunk(ctx.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos")))));
    }

    private static int placed(CommandSourceStack source, BlockPos pos) {
        boolean playerPlaced = PlacedBlocks.isPlayerPlaced(source.getLevel(), pos);
        source.sendSuccess(() -> Component.literal(playerPlaced
                        ? "Pose par un joueur — ne rapportera rien a la casse."
                        : "Naturel — rapportera normalement.")
                .withStyle(playerPlaced ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        return playerPlaced ? 1 : 0;
    }

    /**
     * Recharge le catalogue de prix sans relancer le jeu.
     *
     * <p>Indispensable a l'equilibrage : si regler un prix coute un
     * redemarrage, on ne le regle pas.
     */
    private static int reload(CommandSourceStack source) {
        boolean prices = Prices.load(source.getServer());
        boolean tiers = CollectionTiers.load(source.getServer());
        boolean skills = SkillConfig.load(source.getServer());

        if (prices && tiers && skills) {
            source.sendSuccess(() -> Component.literal(
                            "Recharge : " + Prices.book().size() + " items, paliers de collection a jour.")
                    .withStyle(ChatFormatting.GREEN), true);
            return Prices.book().size();
        }
        source.sendFailure(Component.literal("Rechargement partiel ou echoue ("
                + (prices ? "prix ok" : "prix en echec") + ", "
                + (tiers ? "paliers ok" : "paliers en echec") + ", "
                + (skills ? "skills ok" : "skills en echec")
                + "). Les anciennes donnees restent en service. Voir les logs."));
        return 0;
    }

    private static int chunk(CommandSourceStack source, BlockPos pos) {
        int tracked = PlacedBlocks.trackedInChunk(source.getLevel(), pos);
        source.sendSuccess(() -> Component.literal(tracked + " position(s) suivie(s) dans ce chunk.")
                .withStyle(ChatFormatting.GRAY), false);
        return tracked;
    }
}

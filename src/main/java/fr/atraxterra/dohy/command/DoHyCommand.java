package fr.atraxterra.dohy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.boost.Crates;
import fr.atraxterra.dohy.collection.CollectionTiers;
import fr.atraxterra.dohy.economy.EconomyConfig;
import fr.atraxterra.dohy.job.JobConfig;
import fr.atraxterra.dohy.job.JobProgression;
import fr.atraxterra.dohy.museum.MuseumConfig;
import fr.atraxterra.dohy.contract.ContractConfig;
import fr.atraxterra.dohy.upgrade.UpgradeConfig;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.world.PlacedBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Commandes de diagnostic.
 *
 * <p>Le suivi des blocs poses est invisible par nature : rien dans le jeu ne
 * montre si une position est marquee. Sans moyen de l'interroger, une
 * regression passerait inapercue jusqu'a ce qu'un metier distribue de l'XP sur
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
                .then(Commands.literal("scan")
                        .executes(ctx -> scan(ctx.getSource(), null))
                        .then(Commands.argument("namespace",
                                        com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(ctx -> scan(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType
                                                .getString(ctx, "namespace")))))
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
        boolean progression = JobProgression.load(source.getServer());
        boolean contracts = ContractConfig.load(source.getServer());
        boolean crates = Crates.load(source.getServer());
        boolean economy = EconomyConfig.load(source.getServer());
        boolean museum = MuseumConfig.load(source.getServer());
        boolean notations = fr.atraxterra.dohy.faction.NotationConfig.load(source.getServer());
        boolean server = fr.atraxterra.dohy.tech.TradingServerConfig.load(source.getServer());
        boolean agriculture = fr.atraxterra.dohy.agri.AgriConfig.load(source.getServer());
        boolean earth = fr.atraxterra.dohy.earth.EarthData.load(source.getServer())
                && fr.atraxterra.dohy.earth.EarthDeposits.load(source.getServer());
        boolean upgrades = UpgradeConfig.load(source.getServer());
        boolean jobs = JobConfig.load(source.getServer());
        boolean auction = fr.atraxterra.dohy.auction.AuctionConfig.load(source.getServer());

        if (prices && tiers && progression && contracts && crates && economy && museum && upgrades && jobs
                && auction && notations && server && agriculture && earth) {
            source.sendSuccess(() -> Component.literal(
                            "Recharge : " + Prices.book().size() + " items, paliers de collection a jour.")
                    .withStyle(ChatFormatting.GREEN), true);
            return Prices.book().size();
        }
        source.sendFailure(Component.literal("Rechargement partiel ou echoue ("
                + (prices ? "prix ok" : "prix en echec") + ", "
                + (tiers ? "paliers ok" : "paliers en echec") + ", "
                + (progression ? "progression ok" : "progression en echec") + ", "
                + (contracts ? "contrats ok" : "contrats en echec") + ", "
                + (crates ? "caisses ok" : "caisses en echec") + ", "
                + (economy ? "economie ok" : "economie en echec") + ", "
                + (museum ? "musee ok" : "musee en echec") + ", "
                + (upgrades ? "ameliorations ok" : "ameliorations en echec") + ", "
                + (jobs ? "metiers ok" : "metiers en echec") + ", "
                + (notations ? "notations ok" : "notations en echec") + ", "
                + (server ? "serveur de trading ok" : "serveur de trading en echec") + ", "
                + (agriculture ? "agriculture ok" : "agriculture en echec") + ", "
                + (earth ? "terre ok" : "terre en echec")
                + "). Les anciennes donnees restent en service. Voir les logs."));
        return 0;
    }

    /**
     * Liste les items enregistres qui n'ont aucun prix, groupes par espace de
     * noms.
     *
     * <p>C'est l'outil pour integrer un mod : il dit exactement ce qui echappe
     * encore au catalogue, au lieu de le deviner. Le detail complet part dans
     * les logs, le chat n'en recoit qu'un resume lisible.
     */
    private static int scan(CommandSourceStack source, String namespace) {
        Map<String, Integer> missing = new TreeMap<>();
        List<String> examples = new ArrayList<>();

        for (Holder.Reference<Item> holder : BuiltInRegistries.ITEM.listElements().toList()) {
            Identifier id = BuiltInRegistries.ITEM.getKey(holder.value());
            if (namespace != null && !id.getNamespace().equals(namespace)) {
                continue;
            }
            if (Prices.book().entry(id).isPresent()) {
                continue;
            }
            missing.merge(id.getNamespace(), 1, Integer::sum);
            if (examples.size() < 40) {
                examples.add(id.toString());
            }
        }

        if (missing.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Tous les items sont tarifes.")
                    .withStyle(ChatFormatting.GREEN), false);
            return 0;
        }

        int total = missing.values().stream().mapToInt(Integer::intValue).sum();
        source.sendSuccess(() -> Component.literal(total + " item(s) sans prix")
                .withStyle(ChatFormatting.GOLD), false);
        missing.forEach((ns, count) -> source.sendSuccess(() ->
                Component.literal("  " + ns + " : " + count).withStyle(ChatFormatting.GRAY), false));

        DoHy.LOGGER.info("Items sans prix ({} au total) — exemples : {}", total, examples);
        source.sendSuccess(() -> Component.literal("  detail dans les logs du serveur")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return total;
    }

    private static int chunk(CommandSourceStack source, BlockPos pos) {
        int tracked = PlacedBlocks.trackedInChunk(source.getLevel(), pos);
        source.sendSuccess(() -> Component.literal(tracked + " position(s) suivie(s) dans ce chunk.")
                .withStyle(ChatFormatting.GRAY), false);
        return tracked;
    }
}

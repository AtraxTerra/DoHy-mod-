package fr.atraxterra.dohy.arrakis;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.List;

/**
 * {@code /arrakis} pour les operateurs : aller a l'astroport sans billet,
 * reconstruire l'astroport, provoquer une eruption d'epice, appeler un ver,
 * se donner une moissonneuse pleine, lire ou fixer son eau, lire l'oxygene de
 * l'atmosphere, lister les sietchs et aller devant l'un d'eux.
 */
@EventBusSubscriber(modid = DoHy.MOD_ID)
public final class ArrakisCommand {

    private ArrakisCommand() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(command());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("arrakis")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("tp").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ServerLevel level = ArrakisPort.level(context.getSource().getServer());
                    if (level == null) {
                        context.getSource().sendFailure(Component.literal("Arrakis n'est pas chargee."));
                        return 0;
                    }
                    if (!ArrakisPort.prepare(level)) {
                        context.getSource().sendSuccess(() -> Component.literal(
                                "L'astroport se construit : relance la commande dans quelques secondes."), false);
                        return 0;
                    }
                    ArrakisPort.arrive(player, level);
                    return 1;
                }))
                .then(Commands.literal("rebatir").executes(context -> {
                    ServerLevel level = ArrakisPort.level(context.getSource().getServer());
                    if (level == null) {
                        context.getSource().sendFailure(Component.literal("Arrakis n'est pas chargee."));
                        return 0;
                    }
                    level.setData(DoHyArrakis.PORT_BUILT.get(), 0);
                    ArrakisPort.prepare(level);
                    context.getSource().sendSuccess(() -> Component.literal("Astroport en reconstruction."), true);
                    return 1;
                }))
                .then(Commands.literal("epice").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (!SpiceBlows.trigger(player)) {
                        context.getSource().sendFailure(Component.literal(
                                "Pas d'eruption ici : il faut etre sur Arrakis, hors de la cuvette, pres du sable."));
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.literal("Eruption d'epice en approche."), false);
                    return 1;
                }))
                .then(Commands.literal("ver").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (!(player.level() instanceof ServerLevel level) || !DoHyArrakis.onArrakis(player)
                            || !SandWorm.dispatch(level, WormNoise.summon(player))) {
                        context.getSource().sendFailure(Component.literal(
                                "Pas de ver ici : il faut etre sur Arrakis, dans l'erg, loin de la roche."));
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.literal("Un ver vient vers toi."), false);
                    return 1;
                }))
                .then(Commands.literal("moissonneuse").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ItemStack item = new ItemStack(ArrakisContent.HARVESTER_ITEM.get());
                    item.set(fr.atraxterra.dohy.agri.DoHyAgri.FUEL.get(), SpiceHarvester.TANK);
                    if (!player.getInventory().add(item) || !item.isEmpty()) {
                        player.drop(item, false);
                    }
                    context.getSource().sendSuccess(() -> Component.literal("Moissonneuse donnee, plein fait."),
                            false);
                    return 1;
                }))
                .then(Commands.literal("eau")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            float water = Hydration.water(player);
                            context.getSource().sendSuccess(() -> Component.literal(String.format(
                                    java.util.Locale.ROOT, "Eau : %.1f / %.0f  -  perte ici : %.3f par seconde"
                                            + " (distille : %.0f %%)", water, Hydration.MAX, Hydration.loss(player),
                                    Hydration.saved(player) * 100.0f)), false);
                            return 1;
                        })
                        .then(Commands.argument("valeur", FloatArgumentType.floatArg(0.0f, Hydration.MAX))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    float value = FloatArgumentType.getFloat(context, "valeur");
                                    Hydration.set(player, value);
                                    context.getSource().sendSuccess(() -> Component.literal(String.format(
                                            java.util.Locale.ROOT, "Eau mise a %.1f.", value)), false);
                                    return 1;
                                })))
                .then(Commands.literal("sietchs").executes(context -> {
                    ServerLevel level = ArrakisPort.level(context.getSource().getServer());
                    List<Sietches.Site> sites = Sietches.sites();
                    context.getSource().sendSuccess(() -> Component.literal(sites.size() + " sietchs sur Arrakis :"),
                            false);
                    for (Sietches.Site site : sites) {
                        BlockPos face = site.face();
                        boolean built = level != null && Sietches.built(level, site);
                        context.getSource().sendSuccess(() -> Component.literal("  " + (site.index() + 1) + ". "
                                + site.name() + " : " + face.getX() + " " + face.getY() + " " + face.getZ()
                                + (built ? " (creuse)" : " (pas encore creuse)")), false);
                    }
                    return sites.size();
                }))
                .then(Commands.literal("sietch")
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1, Sietches.NAMES.length))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ServerLevel level = ArrakisPort.level(context.getSource().getServer());
                                    int index = IntegerArgumentType.getInteger(context, "numero") - 1;
                                    Sietches.Site site = null;
                                    for (Sietches.Site s : Sietches.sites()) {
                                        if (s.index() == index) {
                                            site = s;
                                        }
                                    }
                                    if (level == null || site == null) {
                                        context.getSource().sendFailure(Component.literal("Pas de sietch " + (index + 1)
                                                + " sur Arrakis."));
                                        return 0;
                                    }
                                    BlockPos front = site.face().relative(site.forward().getOpposite(), 4);
                                    float yaw = site.forward().toYRot();
                                    player.teleport(new TeleportTransition(level, new Vec3(front.getX() + 0.5d,
                                            Sietches.floor(level, site), front.getZ() + 0.5d), Vec3.ZERO, yaw, 0.0f,
                                            TeleportTransition.DO_NOTHING));
                                    String name = site.name();
                                    context.getSource().sendSuccess(() -> Component.literal("Devant le sietch "
                                            + name + "."), false);
                                    return 1;
                                })))
                .then(Commands.literal("air").executes(context -> {
                    double oxygen = ArrakisPlanet.oxygen(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.literal(
                            "Oxygene sur Arrakis : " + String.format(java.util.Locale.ROOT, "%.2f", oxygen)), false);
                    return 1;
                }));
    }
}

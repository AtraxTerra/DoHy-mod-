package fr.atraxterra.dohy.nuclear;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atraxterra.dohy.earth.EarthSites;
import fr.atraxterra.dohy.faction.base.NtmDefenses;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /largage} : le prochain largage nucleaire, les caisses au sol, la
 * repousse des zones irradiees ; {@code /largage boussole} en donne une qui
 * pointe vers le largage.
 *
 * <p>{@code admin} (operateurs) : {@code lancer [delai] [zone]} annonce un
 * largage tout de suite, {@code fin} fait disparaitre les caisses au sol,
 * {@code repousse} fait repousser les minerais des zones.
 */
@EventBusSubscriber
public final class NuclearCommand {

    private NuclearCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(command("largage"));
        event.getDispatcher().register(command("largages"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return Commands.literal(name)
                .executes(NuclearCommand::status)
                .then(Commands.literal("boussole").executes(context -> answer(context,
                        NuclearDrops.compass(context.getSource().getPlayerOrException()),
                        "Une boussole pointe vers le largage.")))
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("lancer")
                                .executes(context -> launch(context, 20, ""))
                                .then(Commands.argument("delai", IntegerArgumentType.integer(5, 600))
                                        .executes(context -> launch(context,
                                                IntegerArgumentType.getInteger(context, "delai"), ""))
                                        .then(Commands.argument("zone", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    List<String> ids = new ArrayList<>();
                                                    for (EarthSites.Placed zone : ZoneRegrowth.zones()) {
                                                        ids.add(zone.site().id());
                                                    }
                                                    return SharedSuggestionProvider.suggest(ids, builder);
                                                })
                                                .executes(context -> launch(context,
                                                        IntegerArgumentType.getInteger(context, "delai"),
                                                        StringArgumentType.getString(context, "zone"))))))
                        .then(Commands.literal("fin").executes(context -> answer(context, null,
                                NuclearDrops.expireAll(context.getSource().getServer())
                                        + " caisse(s) au sol disparaissent maintenant.")))
                        .then(Commands.literal("repousse").executes(context -> {
                            if (!ZoneRegrowth.active()) {
                                return answer(context, "Pas de zone irradiee ici (carte de la Terre et NTM NEXT)",
                                        null);
                            }
                            int chunks = ZoneRegrowth.force(context.getSource().getServer());
                            return answer(context, null, "Repousse demandee sur " + chunks
                                    + " chunks de zone (les charges dans la minute, les autres a leur visite).");
                        })));
    }

    private static int launch(CommandContext<CommandSourceStack> context, int seconds, String zone)
            throws CommandSyntaxException {
        return answer(context, NuclearDrops.announce(context.getSource().getServer(), zone, seconds * 1000L),
                null);
    }

    private static int answer(CommandContext<CommandSourceStack> context, String refusal, String success)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (refusal != null) {
            player.sendSystemMessage(NuclearDrops.prefix().append(Component.literal(refusal)
                    .withStyle(ChatFormatting.RED)));
            return 0;
        }
        if (success != null) {
            player.sendSystemMessage(NuclearDrops.prefix().append(Component.literal(success)
                    .withStyle(ChatFormatting.GREEN)));
        }
        return 1;
    }

    private static void line(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(NuclearDrops.prefix().append(Component.literal(text).withStyle(color)));
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();
        long now = System.currentTimeMillis();
        if (!NtmDefenses.present()) {
            line(player, "NTM NEXT n'est pas installe : pas de largage nucleaire.", ChatFormatting.GRAY);
            return 1;
        }
        NuclearDrops.Drop drop = NuclearDrops.pending();
        boolean active = drop != null || !NuclearDrops.crates().isEmpty();
        if (drop != null) {
            line(player, "Largage en approche : " + drop.where() + ", arrivee " + NuclearDrops.in(drop.land - now)
                    + ".", ChatFormatting.YELLOW);
        } else {
            line(player, "Prochain largage " + NuclearDrops.in(NuclearDrops.next() - now) + " (annonce "
                    + NuclearDrops.WARNING_MS / 60_000L + " min avant, toutes les 2 h environ).",
                    ChatFormatting.YELLOW);
        }
        for (NuclearDrops.Crate crate : NuclearDrops.crates()) {
            line(player, "Au sol" + (crate.place.isEmpty() ? "" : " a " + crate.place) + " : " + crate.pos.getX() + " "
                    + crate.pos.getY() + " " + crate.pos.getZ() + ", disparait " + NuclearDrops.in(crate.expires - now)
                    + (crate.opened ? " (deja ouvert)" : "") + ".", ChatFormatting.GOLD);
        }
        if (ZoneRegrowth.active()) {
            line(player, "Zones irradiees (" + ZoneRegrowth.names() + ") : les minerais repoussent toutes les 2 h, "
                    + "prochaine repousse " + NuclearDrops.in(ZoneRegrowth.nextAt(now) - now) + ".",
                    ChatFormatting.GRAY);
        }
        if (active) {
            MutableComponent button = Component.literal("   [Boussole]").withStyle(Style.EMPTY
                    .withColor(ChatFormatting.GOLD).withClickEvent(new ClickEvent.RunCommand("/largage boussole")));
            player.sendSystemMessage(button);
        }
        return 1;
    }
}

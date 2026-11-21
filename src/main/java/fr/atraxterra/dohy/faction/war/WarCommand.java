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
 *   <li>{@code declarer <nation>} : ta contre-offensive, la guerre que tu declares (lot 22l-2) ;</li>
 *   <li>{@code coffre} designe ton coffre, la ou tu te tiens ;</li>
 *   <li>{@code placeforte} donne une boussole vers la place forte de la nation en guerre ;</li>
 *   <li>{@code admin} (operateurs) : menacer, declarer, lancer un assaut, l'arreter, tirer un
 *       missile sur sa propre base ({@code missile <nation> [charge]}), faire revenir tout de suite
 *       le terrain bombarde ({@code retour}), commander la place forte d'une nation
 *       ({@code placeforte <nation> [distance]}), la raser ({@code rase}) ou la recompter
 *       ({@code compte}) ; et pour la riposte du lot 22l-2 : un pillage sans bataille
 *       ({@code pillage <nation> <montant>}), une coalition ({@code coalition <nation> <nation>}),
 *       une lecon ({@code lecon <nation> <cause> <morts>}).</li>
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
                .then(Commands.literal("placeforte").executes(context -> answer(context, Strongholds.compass(
                        context.getSource().getPlayerOrException()), "Une boussole pointe vers leur place forte.")))
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
                // Ta contre-offensive (lot 22l-2) : declarer la guerre a une nation.
                .then(Commands.literal("declarer").then(nationArgument().executes(context -> {
                    int nation = NationTraits.find(StringArgumentType.getString(context, "nation"));
                    if (nation < 0) {
                        return answer(context, "Nation inconnue", null);
                    }
                    return answer(context, WarStrategy.counter(context.getSource().getPlayerOrException(), nation),
                            null);
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
                        // Le resultat d'un assaut reussi sur la carte, sans livrer bataille : pour essayer la reconquete.
                        .then(Commands.literal("reprise").then(nationArgument().executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            int nation = NationTraits.find(StringArgumentType.getString(context, "nation"));
                            if (nation < 0) {
                                return answer(context, "Nation inconnue", null);
                            }
                            if (!FactionApi.has(player)) {
                                return answer(context, "Il faut une nation", null);
                            }
                            WarData.Relation relation = WarStrategy.data(player).relation(nation,
                                    System.currentTimeMillis());
                            String retaken = WarStrategy.reconquer(player, relation);
                            return retaken.isEmpty()
                                    ? answer(context, "Rien a reprendre : ni pays (hors capitale) ni mine de sa region",
                                    null)
                                    : answer(context, null, "Reprise : " + retaken + ".");
                        })))
                        // Un assaut reussi sans bataille : pour essayer la restitution (lot 22l-2).
                        .then(Commands.literal("pillage").then(nationArgument()
                                .then(Commands.argument("montant", LongArgumentType.longArg(0L))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            int nation = NationTraits.find(StringArgumentType.getString(context,
                                                    "nation"));
                                            if (nation < 0 || !FactionApi.has(player)) {
                                                return answer(context, "Nation inconnue, ou tu n'as pas de nation", null);
                                            }
                                            String line = WarStrategy.simulatePillage(player, nation,
                                                    LongArgumentType.getLong(context, "montant"));
                                            return line.startsWith("!") ? answer(context, line.substring(1), null)
                                                    : answer(context, null, line + ".");
                                        }))))
                        // Une coalition entre deux nations, tout de suite.
                        .then(Commands.literal("coalition").then(Commands.argument("premiere",
                                        StringArgumentType.word())
                                .then(nationArgument().executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    int first = NationTraits.find(StringArgumentType.getString(context, "premiere"));
                                    int second = NationTraits.find(StringArgumentType.getString(context, "nation"));
                                    if (first < 0 || second < 0 || !FactionApi.has(player)) {
                                        return answer(context, "Nation inconnue, ou tu n'as pas de nation", null);
                                    }
                                    return answer(context, WarStrategy.forcePact(player, first, second), null);
                                }))))
                        // Une lecon apprise sans assaut : pour essayer l'armee qui s'adapte.
                        .then(Commands.literal("lecon").then(nationArgument()
                                .then(Commands.argument("cause", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of(
                                                "lame", "fleches", "tourelles", "pieges", "terrain", "gardiens"),
                                                builder))
                                        .then(Commands.argument("morts", com.mojang.brigadier.arguments
                                                        .IntegerArgumentType.integer(1, 50))
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    int nation = NationTraits.find(StringArgumentType.getString(
                                                            context, "nation"));
                                                    if (nation < 0) {
                                                        return answer(context, "Nation inconnue", null);
                                                    }
                                                    return answer(context, WarStrategy.teach(player, nation,
                                                            StringArgumentType.getString(context, "cause"),
                                                            com.mojang.brigadier.arguments.IntegerArgumentType
                                                                    .getInteger(context, "morts")),
                                                            "Lecon ajoutee.");
                                                })))))
                        // Une frappe de missile sur sa propre base, sans siege : pour essayer les missiles de NTM.
                        .then(Commands.literal("missile").then(nationArgument()
                                .executes(context -> missile(context, null))
                                .then(Commands.argument("charge", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(NtmArsenal.Warhead.values())
                                                        .map(w -> w.name().toLowerCase(java.util.Locale.ROOT))
                                                        .toList(), builder))
                                        .executes(context -> missile(context,
                                                StringArgumentType.getString(context, "charge"))))))
                        // La place forte de la nation, plus pres que d'ordinaire : pour essayer la riposte.
                        .then(Commands.literal("placeforte").then(nationArgument()
                                .executes(context -> stronghold(context, 0))
                                .then(Commands.argument("distance", com.mojang.brigadier.arguments.IntegerArgumentType
                                                .integer(64, 4_000))
                                        .executes(context -> stronghold(context, com.mojang.brigadier.arguments
                                                .IntegerArgumentType.getInteger(context, "distance"))))))
                        .then(Commands.literal("rase").executes(context -> answer(context, Strongholds.forceRaze(
                                context.getSource().getPlayerOrException()), null)))
                        .then(Commands.literal("compte").executes(context -> answer(context, Strongholds.recount(
                                context.getSource().getPlayerOrException()), "Comptes de la place forte en cours.")))
                        .then(Commands.literal("retour").executes(context -> answer(context, null,
                                StrikeTerrain.hurry() + " terrain(s) bombarde(s) reviennent maintenant.")))
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
            return answer(context, "Il faut une nation", null);
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

    /** La guerre avec cette nation (declaree s'il le faut), et sa place forte commandee. */
    private static int stronghold(CommandContext<CommandSourceStack> context, int distance)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int nation = NationTraits.find(StringArgumentType.getString(context, "nation"));
        if (nation < 0) {
            return answer(context, "Nation inconnue", null);
        }
        if (!FactionApi.has(player)) {
            return answer(context, "Il faut une nation", null);
        }
        long now = System.currentTimeMillis();
        WarData data = WarStrategy.data(player);
        WarData.Relation relation = data.relation(nation, now);
        for (WarData.Relation other : data.relations()) {
            if (other != relation && (other.stance == WarData.Stance.MENACE || other.stance == WarData.Stance.GUERRE)) {
                other.stance(WarData.Stance.PAIX, now);
            }
        }
        if (relation.stance != WarData.Stance.GUERRE) {
            WarStrategy.declare(player, data, relation, now, true);
        }
        return answer(context, Strongholds.order(player, relation, distance), null);
    }

    private static int missile(CommandContext<CommandSourceStack> context, String charge)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int nation = NationTraits.find(StringArgumentType.getString(context, "nation"));
        if (nation < 0) {
            return answer(context, "Nation inconnue", null);
        }
        if (!FactionApi.has(player)) {
            return answer(context, "Il faut une nation", null);
        }
        if (!NtmArsenal.ready()) {
            return answer(context, "NTM NEXT n'est pas la (ou ne repond pas) : pas de missiles", null);
        }
        if (Strikes.striking(player.getUUID()) || Sieges.of(player.getUUID()) != null) {
            return answer(context, "Un siege ou une frappe est deja en cours", null);
        }
        long now = System.currentTimeMillis();
        WarData data = WarStrategy.data(player);
        WarData.Relation relation = data.relation(nation, now);
        NtmArsenal.Warhead warhead = charge == null ? Strikes.choose(relation, true) : NtmArsenal.Warhead.find(charge);
        if (warhead == null || !NtmArsenal.available(warhead)) {
            return answer(context, "Charge inconnue ou indisponible (le nucleaire peut etre coupe dans NTM)", null);
        }
        java.util.Optional<java.util.Map.Entry<net.minecraft.server.level.ServerLevel, net.minecraft.core.BlockPos>>
                vault = WarStrategy.vault(player, data);
        if (vault.isEmpty()) {
            return answer(context, "Aucun claim a viser", null);
        }
        relation.memory = relation.memory.withStrike();
        relation.stance(WarData.Stance.TREVE, now);
        relation.truce = now + WarStrategy.DAY_MS;
        Strikes.start(player, nation, vault.get().getKey(), vault.get().getValue(), warhead, 0L);
        WarStrategy.say(player, relation, WarStrategy.bombard(relation.traits(), warhead), ChatFormatting.DARK_RED);
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
                    + MoneyFormat.compact(relation.treasury()) + ", griefs " + Math.round(relation.grievance(now))
                    + (relation.holds() ? ", doit " + WarStrategy.owedLine(player, relation) : "")
                    + (relation.ally() >= 0 ? ", alliee " + fr.atraxterra.dohy.earth.EarthNations.a(
                    NationTraits.of(relation.ally()).name()) : "")
                    + (relation.lessons().assaults() > 0 ? ", " + relation.lessons().assaults() + " assaut(s) appris"
                    + (relation.lessons().lesson().isEmpty() ? "" : " (" + relation.lessons().lesson() + ")") : ""))
                    .withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(Component.literal("Puissance " + Math.round(WarStrategy.threat(player).total())
                + " : " + String.join(", ", WarStrategy.threat(player).parts())).withStyle(ChatFormatting.GRAY));
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

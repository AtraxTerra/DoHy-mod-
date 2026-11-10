package fr.atraxterra.dohy.faction;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atraxterra.dohy.faction.base.EnemyBase;
import fr.atraxterra.dohy.faction.base.EnemyBaseTier;
import fr.atraxterra.dohy.faction.base.EnemyBases;
import fr.atraxterra.dohy.faction.event.FactionEventKind;
import fr.atraxterra.dohy.faction.event.FactionEventMenu;
import fr.atraxterra.dohy.faction.event.FactionEvents;
import fr.atraxterra.dohy.faction.reserve.ReserveMenu;
import fr.atraxterra.dohy.palamod.boss.BossKind;
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
                .then(Commands.literal("arbre")
                        .executes(ctx -> as(ctx.getSource(), player -> {
                            FactionTreeMenus.Tree.INSTANCE.open(player);
                            return 1;
                        }))
                        .then(Commands.literal("finir")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> as(ctx.getSource(), player -> {
                                    FactionTree.complete(player, true);
                                    return 1;
                                })))
                        .then(Commands.literal("palier")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("branche", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (FactionBranch branch : FactionBranch.values()) {
                                                builder.suggest(branch.key());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("palier", IntegerArgumentType.integer(0, 12))
                                                .executes(ctx -> as(ctx.getSource(), player -> {
                                                    FactionBranch branch = FactionBranch.byKey(
                                                            StringArgumentType.getString(ctx, "branche"));
                                                    if (branch == null) {
                                                        return result(player, "Branche inconnue", "");
                                                    }
                                                    FactionTree.setTier(player, branch,
                                                            IntegerArgumentType.getInteger(ctx, "palier"));
                                                    return result(player, null, "Palier fixe.");
                                                }))))))
                .then(Commands.literal("reserve")
                        .executes(ctx -> as(ctx.getSource(), player -> {
                            ReserveMenu.INSTANCE.open(player);
                            return 1;
                        })))
                .then(Commands.literal("event")
                        .executes(ctx -> as(ctx.getSource(), player -> {
                            FactionEventMenu.INSTANCE.open(player);
                            return 1;
                        }))
                        .then(Commands.literal("rejoindre")
                                .executes(ctx -> as(ctx.getSource(), player -> result(player,
                                        FactionEvents.join(player), "Tu entres dans l'arene."))))
                        .then(Commands.literal("quitter")
                                .executes(ctx -> as(ctx.getSource(), player -> result(player,
                                        FactionEvents.leave(player), "Tu quittes l'arene."))))
                        .then(Commands.literal("lacher")
                                .executes(ctx -> as(ctx.getSource(), player -> result(player,
                                        FactionEvents.dropEgg(player), "Tu laches l'oeuf."))))
                        .then(Commands.literal("arreter")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> as(ctx.getSource(), player -> result(player,
                                        FactionEvents.stop(ctx.getSource().getServer()), "Event arrete."))))
                        .then(Commands.literal("lancer")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("event", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (FactionEventKind kind : FactionEventKind.values()) {
                                                builder.suggest(kind.id());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> as(ctx.getSource(), player -> startEvent(player,
                                                StringArgumentType.getString(ctx, "event"), null)))
                                        .then(Commands.argument("boss", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (BossKind kind : BossKind.values()) {
                                                        builder.suggest(kind.id());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> as(ctx.getSource(), player -> startEvent(player,
                                                        StringArgumentType.getString(ctx, "event"),
                                                        StringArgumentType.getString(ctx, "boss"))))))))
                .then(Commands.literal("base")
                        .executes(ctx -> as(ctx.getSource(), FactionCommand::enemyBase))
                        .then(Commands.literal("generer")
                                .executes(ctx -> as(ctx.getSource(), player -> result(player,
                                        EnemyBases.request(player, EnemyBaseTier.OUTPOST), "")))
                                .then(Commands.argument("palier", IntegerArgumentType.integer(1, 3))
                                        .executes(ctx -> as(ctx.getSource(), player -> result(player,
                                                EnemyBases.request(player, EnemyBaseTier.byLevel(
                                                        IntegerArgumentType.getInteger(ctx, "palier"))), "")))))
                        .then(Commands.literal("boussole")
                                .executes(ctx -> as(ctx.getSource(), player -> result(player,
                                        EnemyBases.compass(player), "Boussole recue."))))
                        .then(Commands.literal("abandonner")
                                .executes(ctx -> as(ctx.getSource(), player -> result(player,
                                        EnemyBases.abandon(player), "Base abandonnee.")))))
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

    /** {@code /f base} : la cible en cours, ou comment en demander une. */
    /** {@code /f event lancer <event> [boss]} : pour tester sans attendre l'heure. */
    private static int startEvent(ServerPlayer player, String event, String boss) {
        FactionEventKind kind = FactionEventKind.byId(event);
        if (kind == null) {
            return result(player, "Event inconnu", "");
        }
        BossKind chosen = boss == null ? null : BossKind.byId(boss);
        if (boss != null && chosen == null) {
            return result(player, "Boss inconnu", "");
        }
        return result(player, FactionEvents.start(player.level().getServer(), kind, chosen), "Event lance.");
    }

    private static int enemyBase(ServerPlayer player) {
        EnemyBase base = EnemyBases.of(player);
        if (EnemyBases.pending(player)) {
            return result(player, null, "Tes eclaireurs cherchent encore la base.");
        }
        if (!base.active()) {
            return result(player, null, "Aucune base a piller. /f base generer [1-3] : avant-poste, base, forteresse.");
        }
        return result(player, null, "Cible : les " + base.name() + " (" + base.kind().label() + ") en "
                + base.center().getX() + ", " + base.center().getZ() + " — " + EnemyBases.where(player, base) + ".");
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

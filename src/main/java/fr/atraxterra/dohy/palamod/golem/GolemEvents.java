package fr.atraxterra.dohy.palamod.golem;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Les withers et le golem, et les commandes :
 * <ul>
 *   <li>{@code /whitelistgolem add|remove <joueur>} : la liste blanche tenue en
 *       main ({@code CommandWhitelistGolem}) ;</li>
 *   <li>{@code /guardiangolem <niveau>} (operateurs) : un golem a ce niveau,
 *       au joueur qui tape la commande.</li>
 * </ul>
 */
@EventBusSubscriber
public final class GolemEvents {

    /** {@code [A-Za-zÀ-ÖØ-öø-ÿ0-9\s.;,_]*}, 16 caracteres au plus. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u00FF0-9.;,_]{1,16}");

    private GolemEvents() {
    }

    /** {@code EventHandler.onWitherAgro} : Wither hunter, choix 2. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof WitherBoss
                && event.getNewAboutToBeSetTarget() instanceof GuardianGolem golem && golem.ignoredByWithers()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("whitelistgolem")
                .then(Commands.literal("add").then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> whitelist(ctx.getSource(), StringArgumentType.getString(ctx, "player"), true))))
                .then(Commands.literal("remove").then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> whitelist(ctx.getSource(), StringArgumentType.getString(ctx, "player"), false)))));
        event.getDispatcher().register(Commands.literal("guardiangolem")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("level", IntegerArgumentType.integer(0, GuardianGolem.MAX_LEVEL))
                        .executes(ctx -> spawn(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "level")))));
    }

    private static int whitelist(CommandSourceStack source, String name, boolean add) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (!held.is(PalaGolem.WHITELIST.get())) {
            source.sendFailure(Component.translatable("message.palamod.whitelist.not_held"));
            return 0;
        }
        List<String> names = new ArrayList<>(WhitelistItem.names(held));
        boolean present = names.stream().anyMatch(name::equalsIgnoreCase);
        if (add) {
            if (!NAME.matcher(name).matches()) {
                source.sendFailure(Component.translatable("message.palamod.whitelist.invalid"));
                return 0;
            }
            if (present) {
                source.sendFailure(Component.translatable("message.palamod.whitelist.present", name));
                return 0;
            }
            if (names.size() >= WhitelistItem.MAX_NAMES) {
                source.sendFailure(Component.translatable("message.palamod.whitelist.full", WhitelistItem.MAX_NAMES));
                return 0;
            }
            names.add(name);
        } else {
            if (!present) {
                source.sendFailure(Component.translatable("message.palamod.whitelist.absent", name));
                return 0;
            }
            names.removeIf(name::equalsIgnoreCase);
        }
        held.set(PalaGolem.WHITELIST_NAMES.get(), List.copyOf(names));
        source.sendSuccess(() -> Component.translatable(add
                ? "message.palamod.whitelist.added" : "message.palamod.whitelist.removed", name), false);
        return 1;
    }

    private static int spawn(CommandSourceStack source, int level) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel world = source.getLevel();
        GuardianGolem golem = PalaGolem.GUARDIAN_GOLEM.get().create(world, EntitySpawnReason.COMMAND);
        if (golem == null) {
            return 0;
        }
        golem.setOwner(player);
        int xp = 0;
        for (int step = 1; step <= level; step++) {
            xp += GuardianGolem.xpForLevel(step);
        }
        golem.addXp(xp);
        golem.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot() + 180.0f, 0.0f);
        golem.setHealth(golem.getMaxHealth());
        golem.startLoading();
        world.addFreshEntity(golem);
        source.sendSuccess(() -> Component.translatable("message.palamod.golem.spawned", level), false);
        return 1;
    }
}

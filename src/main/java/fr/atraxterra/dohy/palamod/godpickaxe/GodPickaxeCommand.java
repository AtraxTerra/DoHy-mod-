package fr.atraxterra.dohy.palamod.godpickaxe;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code CommandGodPickaxe}, reserve aux operateurs :
 * <ul>
 *   <li>{@code /godpickaxe create <niveau>} donne une pioche a ce niveau ;</li>
 *   <li>{@code /godpickaxe add auto_smelt} pose la fusion sur celle tenue en
 *       main, a tout niveau.</li>
 * </ul>
 */
@EventBusSubscriber
public final class GodPickaxeCommand {

    private GodPickaxeCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("godpickaxe")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("create")
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, GodPickaxeLevels.MAX + 1))
                                .executes(ctx -> create(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "level")))))
                .then(Commands.literal("add")
                        .then(Commands.literal("auto_smelt")
                                .executes(ctx -> autoSmelt(ctx.getSource())))));
    }

    private static int create(CommandSourceStack source, int shown) throws
            com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = new ItemStack(PalaGodPickaxe.GOD_PICKAXE.get());
        int level = shown - 1;
        stack.set(PalaGodPickaxe.DATA.get(),
                new GodPickaxeData(level == 0 ? 0.0d : GodPickaxeLevels.xpFor(level) + 1.0d, false));
        GodPickaxeItem.applyLevel(stack, source.registryAccess());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        source.sendSuccess(() -> Component.translatable("message.palamod.god_pickaxe.given", shown), false);
        return 1;
    }

    private static int autoSmelt(CommandSourceStack source) throws
            com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GodPickaxeItem)) {
            source.sendFailure(Component.translatable("message.palamod.god_pickaxe.not_held"));
            return 0;
        }
        stack.set(PalaGodPickaxe.DATA.get(), GodPickaxeItem.data(stack).withAutoSmelt());
        source.sendSuccess(() -> Component.translatable("message.palamod.god_pickaxe.upgraded"), false);
        return 1;
    }
}

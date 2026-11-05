package fr.atraxterra.dohy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.atraxterra.dohy.skill.Skill;
import fr.atraxterra.dohy.skill.SkillApi;
import fr.atraxterra.dohy.skill.SkillConfig;
import fr.atraxterra.dohy.skill.SkillsMenu;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /skills} — niveaux, progression et prochaine recompense. */
@EventBusSubscriber
public final class SkillsCommand {

    private static final int BAR_WIDTH = 20;

    private SkillsCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(build());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("skills")
                // Sans argument : l'interface, qui montre les perks. Le resume
                // en texte reste sous « list ».
                .executes(ctx -> open(ctx.getSource()))
                .then(Commands.literal("list")
                        .executes(ctx -> show(ctx.getSource())));
    }

    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, viewer) ->
                        new SkillsMenu(containerId, inventory, player),
                Component.literal("Skills")));
        return 1;
    }

    private static int show(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int maxLevel = SkillConfig.config().curve().maxLevel();

        int total = 0;
        for (Skill skill : Skill.values()) {
            total += SkillApi.levelOf(player, skill);
        }

        int finalTotal = total;
        source.sendSuccess(() -> Component.literal("Skills — " + finalTotal + " niveaux cumules")
                .withStyle(ChatFormatting.GOLD), false);

        for (Skill skill : Skill.values()) {
            int level = SkillApi.levelOf(player, skill);
            double progress = SkillApi.progress(player, skill);
            long xp = SkillApi.data(player).xpOf(skill);

            StringBuilder bar = new StringBuilder();
            int filled = (int) Math.round(progress * BAR_WIDTH);
            bar.append("▏".repeat(Math.max(0, filled)));
            bar.append("·".repeat(Math.max(0, BAR_WIDTH - filled)));

            ChatFormatting colour = level >= maxLevel ? ChatFormatting.AQUA
                    : level > 0 ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY;

            source.sendSuccess(() -> Component.literal("  " + pad(skill.display()) + " ")
                    .withStyle(colour)
                    .append(Component.literal(String.format("%2d", level)).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" " + bar + " ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(MoneyFormat.exact(xp) + " xp")
                            .withStyle(ChatFormatting.DARK_GRAY)), false);
        }

        source.sendSuccess(() -> Component.literal(
                        "  Perk tous les " + SkillConfig.config().curve().perkEvery() + " niveaux.")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return total;
    }

    private static String pad(String text) {
        return text.length() >= 14 ? text : text + " ".repeat(14 - text.length());
    }
}

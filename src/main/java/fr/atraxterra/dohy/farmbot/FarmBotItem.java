package fr.atraxterra.dohy.farmbot;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.function.Consumer;

/**
 * Le bot de farming en objet : un clic sur le sol le pose, a son nom. Range, il garde sa zone et ses
 * coffres, qu'il retrouve si on le repose pres d'eux.
 */
public class FarmBotItem extends Item {

    public FarmBotItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        int placed = FarmBots.of(player).size();
        if (placed >= BotRegistry.MAX_PER_PLAYER) {
            player.sendSystemMessage(Component.literal("Tu as deja " + placed + " bots poses, le maximum. Range-en un"
                    + " (son menu, Ranger le bot) pour poser celui-ci. /bots dit ou ils sont.")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        FarmBot bot = DoHyFarmBots.FARM_BOT.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (bot == null) {
            return InteractionResult.FAIL;
        }
        bot.snapTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, player.getYRot() + 180.0f, 0.0f);
        if (!level.noCollision(bot)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        bot.setOwner(player);
        bot.apply(stack.getOrDefault(DoHyFarmBots.SETUP.get(), BotSetup.EMPTY));
        level.addFreshEntity(bot);
        FarmBots.update(level, bot);
        boolean piloted = BotRemoteItem.adopt(player, bot);
        level.playSound(null, pos, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 0.6f, 1.4f);
        String text;
        if (bot.zone() != null) {
            text = "Bot pose : il reprend sa zone (" + bot.zone().label() + ") et ses coffres.";
        } else if (piloted) {
            text = "Bot pose, ta telecommande le pilote : clic droit sur deux coins de ton champ, puis accroupi"
                    + " + clic droit sur un coffre.";
        } else {
            text = "Bot pose. Telecommande en main : clic droit sur lui, puis sur deux coins de ton champ, puis"
                    + " accroupi + clic droit sur un coffre.";
        }
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GREEN));
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.dohy.farm_bot.1").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.dohy.farm_bot.2").withStyle(ChatFormatting.GRAY));
        BotSetup setup = stack.get(DoHyFarmBots.SETUP.get());
        if (setup != null && setup.zone().isPresent()) {
            tooltip.accept(Component.translatable("tooltip.dohy.farm_bot.setup", setup.zone().get().label(),
                    setup.chests().size()).withStyle(ChatFormatting.DARK_GREEN));
        }
    }
}

package fr.atraxterra.dohy.farmbot;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * La telecommande des bots de farming.
 *
 * <ul>
 *   <li>clic droit sur un de ses bots : elle le pilote ;</li>
 *   <li>clic droit sur un bloc : un coin de la zone, puis le coin oppose ; la zone est faite ;</li>
 *   <li>accroupi + clic droit sur un coffre (ou tout ce qui garde des objets) : le lier, ou le
 *       delier s'il l'etait ;</li>
 *   <li>clic droit en l'air : ou en est le bot ; accroupi, annule un coin en cours.</li>
 * </ul>
 *
 * <p>Tant qu'on la tient, le contour de la zone et les coffres lies scintillent
 * ({@link FarmBotEvents}).
 */
public class BotRemoteItem extends Item {

    public BotRemoteItem(Properties properties) {
        super(properties);
    }

    /** Fait piloter ce bot par la telecommande. */
    static void select(ServerPlayer player, ItemStack remote, FarmBot bot) {
        remote.set(DoHyFarmBots.REMOTE_BOT.get(), bot.getUUID());
        remote.remove(DoHyFarmBots.REMOTE_CORNER.get());
        player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(),
                SoundSource.PLAYERS, 0.6f, 1.6f);
        player.sendSystemMessage(Component.literal("Telecommande reliee a ce bot. Clic droit sur deux coins pour sa"
                + " zone, accroupi + clic droit sur un coffre pour le lier.").withStyle(ChatFormatting.GREEN));
    }

    /** Le bot que pilote cette telecommande, s'il est la et a ce joueur. */
    static FarmBot bot(ServerPlayer player, ItemStack remote) {
        UUID id = remote.get(DoHyFarmBots.REMOTE_BOT.get());
        if (id == null || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getEntity(id) instanceof FarmBot bot && bot.isAlive() && bot.ownedBy(player) ? bot : null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack remote = context.getItemInHand();
        FarmBot bot = bot(player, remote);
        if (bot == null) {
            tell(player, remote.has(DoHyFarmBots.REMOTE_BOT.get())
                    ? "Ton bot n'est pas a portee (trop loin, ou dans un autre monde)."
                    : "Clic droit d'abord sur ton bot avec la telecommande, pour la relier a lui.", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        BlockPos pos = context.getClickedPos().immutable();
        if (context.isSecondaryUseActive()) {
            return link(level, player, bot, pos);
        }
        BlockPos corner = remote.get(DoHyFarmBots.REMOTE_CORNER.get());
        if (corner == null) {
            remote.set(DoHyFarmBots.REMOTE_CORNER.get(), pos);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
            tell(player, "Premier coin : " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                    + ". Clic droit sur le coin oppose du champ.", ChatFormatting.YELLOW);
            return InteractionResult.SUCCESS;
        }
        BotZone zone = BotZone.of(corner, pos);
        if (zone.tooBig()) {
            tell(player, "Trop grand : " + zone.label() + " (" + BotZone.MAX_SIDE + " x " + BotZone.MAX_SIDE
                    + " au plus, et les deux coins a moins de " + (BotZone.MAX_HEIGHT - BotZone.BELOW - BotZone.ABOVE)
                    + " blocs de haut). Choisis un autre second coin.", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if (zone.distance(bot.getX(), bot.getZ()) > FarmBot.ZONE_RANGE) {
            tell(player, "Cette zone est a plus de " + FarmBot.ZONE_RANGE + " blocs du bot : pose-le plus pres.",
                    ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        bot.setZone(zone);
        remote.remove(DoHyFarmBots.REMOTE_CORNER.get());
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.7f, 1.8f);
        tell(player, "Zone du bot : " + zone.label() + ". Il s'y met tout de suite."
                + (bot.chests().isEmpty() ? " Lie-lui un coffre : accroupi + clic droit dessus." : ""),
                ChatFormatting.GREEN);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult link(ServerLevel level, ServerPlayer player, FarmBot bot, BlockPos pos) {
        boolean storage;
        try {
            storage = level.getCapability(Capabilities.Item.BLOCK, pos, null) != null;
        } catch (RuntimeException error) {
            storage = false;
        }
        boolean linked = bot.chests().contains(pos);
        if (!storage && !linked) {
            tell(player, "Ce bloc ne garde pas d'objets : choisis un coffre, un baril, un tiroir...",
                    ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if (!linked) {
            BotZone zone = bot.zone();
            double away = zone != null ? zone.distance(pos.getX() + 0.5d, pos.getZ() + 0.5d)
                    : Math.sqrt(bot.distanceToSqr(pos.getX() + 0.5d, bot.getY(), pos.getZ() + 0.5d));
            if (away > FarmBot.CHEST_RANGE) {
                tell(player, "Ce coffre est trop loin " + (zone != null ? "de la zone" : "du bot") + " ("
                        + FarmBot.CHEST_RANGE + " blocs au plus).", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            if (bot.chests().size() >= FarmBot.MAX_CHESTS) {
                tell(player, "Le bot a deja " + FarmBot.MAX_CHESTS + " coffres : delie-en un d'abord (accroupi + clic"
                        + " droit dessus).", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
        }
        boolean now = bot.toggleChest(pos);
        level.playSound(null, pos, now ? SoundEvents.CHEST_LOCKED : SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.6f,
                now ? 1.4f : 0.8f);
        tell(player, now ? "Coffre lie (" + bot.chests().size() + " / " + FarmBot.MAX_CHESTS
                + ") : le bot y vide sa recolte." : "Coffre delie.", now ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack remote = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && remote.has(DoHyFarmBots.REMOTE_CORNER.get())) {
            remote.remove(DoHyFarmBots.REMOTE_CORNER.get());
            tell(server, "Coin annule.", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        FarmBot bot = bot(server, remote);
        if (bot == null) {
            tell(server, remote.has(DoHyFarmBots.REMOTE_BOT.get())
                    ? "Ton bot n'est pas a portee. /bots dit ou sont les tiens."
                    : "Clic droit sur ton bot avec la telecommande pour la relier a lui.", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        BotZone zone = bot.zone();
        tell(server, "Bot : " + bot.status().label().toLowerCase(java.util.Locale.ROOT) + ". Zone : "
                + (zone == null ? "aucune" : zone.label()) + ", coffres : " + bot.chests().size() + ", recoltes : "
                + bot.harvested() + ".", ChatFormatting.GREEN);
        return InteractionResult.SUCCESS;
    }

    private static void tell(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.dohy.bot_remote.1").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.dohy.bot_remote.2").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.dohy.bot_remote.3").withStyle(ChatFormatting.GRAY));
        if (stack.has(DoHyFarmBots.REMOTE_CORNER.get())) {
            tooltip.accept(Component.translatable("tooltip.dohy.bot_remote.corner").withStyle(ChatFormatting.YELLOW));
        }
    }
}

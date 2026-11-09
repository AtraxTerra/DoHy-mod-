package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Une bouteille d'XP de metier (« Bottle Farmer 1000xp » et suivantes, dans
 * le butin des boss).
 *
 * <p>Clic droit : l'XP d'une bouteille ; accroupi, de toute la pile. L'XP
 * passe par {@link JobApi#grant}, donc avec les bonus de metier, comme toute
 * autre source.
 */
public class JobBottleItem extends Item {

    public static final int DEFAULT_XP = 1000;

    private final Job job;

    public JobBottleItem(Job job, Properties properties) {
        super(properties);
        this.job = job;
    }

    public Job job() {
        return job;
    }

    public static int xp(ItemStack stack) {
        return stack.getOrDefault(PalaDungeon.JOB_XP.get(), DEFAULT_XP);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        int count = player.isShiftKeyDown() ? stack.getCount() : 1;
        long total = (long) xp(stack) * count;
        JobApi.grant(serverPlayer, job, total);
        if (!player.isCreative()) {
            stack.shrink(count);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                0.6f, 0.8f);
        serverPlayer.sendSystemMessage(Component.translatable("message.palamod.job_bottle", total, job.display())
                .withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.job_bottle", xp(stack), job.display())
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.job_bottle.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}

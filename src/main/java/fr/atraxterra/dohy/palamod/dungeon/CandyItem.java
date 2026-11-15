package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.util.MoneyFormat;
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

import java.util.List;
import java.util.function.Consumer;

/**
 * Un bonbon de metier : de l'XP tout de suite, d'apres le wiki Java (« 5
 * bonbons permettant de gagner instantanement 50 000 xp »). Le vert pour le
 * Fermier, le jaune pour le Mineur, le bleu pour le Chasseur, le mauve pour
 * l'Alchimiste, le multicolore pour les quatre.
 *
 * <p><b>Echelle</b> : 50 000 XP de PalaJobs valent 17 850 XP de DoHy, au
 * rapport 0,357 qui traduit les gains d'XP de Paladium (PALAMOD.md, § 1).
 * L'XP passe par {@link JobApi#grant}, avec les bonus de metier.
 *
 * <p>Clic droit : un bonbon ; accroupi, toute la pile.
 */
public class CandyItem extends Item {

    /** 50 000 XP de PalaJobs, en XP de DoHy. */
    public static final long XP = 17_850L;

    private final List<Job> jobs;

    public CandyItem(List<Job> jobs, Properties properties) {
        super(properties);
        this.jobs = List.copyOf(jobs);
    }

    public List<Job> jobs() {
        return jobs;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        int count = player.isShiftKeyDown() ? stack.getCount() : 1;
        long total = XP * count;
        for (Job job : jobs) {
            JobApi.grant(serverPlayer, job, total);
            serverPlayer.sendSystemMessage(Component.translatable("message.palamod.candy",
                    MoneyFormat.exact(total), job.display()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (!player.isCreative()) {
            stack.shrink(count);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.7f,
                1.3f);
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f,
                0.9f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        if (jobs.size() == 1) {
            tooltip.accept(Component.translatable("tooltip.palamod.candy", MoneyFormat.exact(XP),
                    jobs.get(0).display()).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.accept(Component.translatable("tooltip.palamod.candy.all", MoneyFormat.exact(XP))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.translatable("tooltip.palamod.candy.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}

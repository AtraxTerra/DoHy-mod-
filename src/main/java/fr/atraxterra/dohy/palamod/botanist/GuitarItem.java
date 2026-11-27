package fr.atraxterra.dohy.palamod.botanist;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
 * La guitare ({@code ItemBotanistGuitar}) : elle joue un air a une plante qui
 * reclame de la musique (32 airs avant de casser, « Guitare cassee »). En l'air,
 * elle gratte quelques notes pour le plaisir, sans s'user.
 */
public class GuitarItem extends Item {

    /** Les intervalles d'un accord majeur, en demi-tons. */
    private static final int[] CHORD = {0, 4, 7};

    public GuitarItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            strum(server, player.blockPosition(), server.getRandom());
        }
        player.getCooldowns().addCooldown(stack, 10);
        return InteractionResult.SUCCESS;
    }

    /** Un accord pris au hasard sur le manche, et ses notes en l'air. */
    static void strum(ServerLevel level, BlockPos pos, RandomSource random) {
        int root = random.nextInt(12);
        for (int interval : CHORD) {
            float pitch = (float) Math.pow(2.0d, (root + interval - 12) / 12.0d);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.PLAYERS, 0.9f,
                    Math.max(0.5f, Math.min(2.0f, pitch * 1.5f)));
        }
        level.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5d, pos.getY() + 1.4d, pos.getZ() + 0.5d, 3, 0.4d,
                0.2d, 0.4d, 0.5d);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.guitar.use").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.botanist.tool.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}

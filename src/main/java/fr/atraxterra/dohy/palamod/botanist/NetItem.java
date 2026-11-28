package fr.atraxterra.dohy.palamod.botanist;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Le filet ({@code ItemBotanistNet}, un {@code ABotanistChargedTool} de huit charges) : un clic
 * droit sur un nibbler l'attrape (« Clic droit sur un nibbler pour l'attraper »), un clic droit
 * sur une plante qui en reclame les relache (« Clic droit sur une plante qui le reclame »).
 * Chaque relache use le filet (32 utilisations, sans reparation, comme l'arrosoir et la guitare).
 *
 * <p>Tenu plein, il gigote : de temps en temps, les nibblers qu'il porte se font entendre
 * ({@code botanist.net.nibblers}).
 */
public class NetItem extends Item {

    public static final int CAPACITY = 8;
    /** Les nibblers se font entendre en moyenne toutes les quatre secondes, filet en main. */
    private static final int CHATTER_EVERY = 40;

    public NetItem(Properties properties) {
        super(properties);
    }

    public static int nibblers(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(PalaBotanist.NIBBLERS.get(), 0), 0, CAPACITY);
    }

    public static void setNibblers(ItemStack stack, int nibblers) {
        stack.set(PalaBotanist.NIBBLERS.get(), Mth.clamp(nibblers, 0, CAPACITY));
    }

    public static boolean full(ItemStack stack) {
        return nibblers(stack) >= CAPACITY;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof LivingEntity holder) || nibblers(stack) <= 0
                || (holder.getMainHandItem() != stack && holder.getOffhandItem() != stack)) {
            return;
        }
        if ((level.getGameTime() + entity.getId()) % CHATTER_EVERY == 0 && level.getRandom().nextBoolean()) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), PalaBotanist.NET_NIBBLERS.get(),
                    SoundSource.PLAYERS, 0.5f, 0.9f + level.getRandom().nextFloat() * 0.2f);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        int nibblers = nibblers(stack);
        tooltip.accept(Component.translatable("tooltip.palamod.net.nibblers",
                Component.literal(String.valueOf(nibblers))
                        .withStyle(nibblers > 0 ? ChatFormatting.GREEN : ChatFormatting.RED),
                Component.literal(String.valueOf(CAPACITY)).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.net.catch").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.botanist.tool.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}

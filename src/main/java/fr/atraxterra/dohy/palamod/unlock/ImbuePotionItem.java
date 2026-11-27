package fr.atraxterra.dohy.palamod.unlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Une potion d'impregnation ({@code ItemPotion} : {@code potion.witherimbue},
 * {@code fireimbue}, {@code poisonimbue}). Bue, elle donne son effet pour
 * 2 000 ticks (1 min 40) : tant qu'il dure, chaque coup porte passe a la cible
 * du wither, du feu ou du poison ({@code PotionHandler.imbueHandler}). La
 * fiole vide est rendue.
 */
public class ImbuePotionItem extends Item {

    /** {@code new PotionEffect(..., 2000, 1)}. */
    public static final int DURATION = 2000;
    public static final int AMPLIFIER = 1;

    private final Supplier<? extends Holder<MobEffect>> effect;
    private final String hint;

    public ImbuePotionItem(Supplier<? extends Holder<MobEffect>> effect, String hint, Properties properties) {
        super(properties);
        this.effect = effect;
        this.hint = hint;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) {
            entity.addEffect(new MobEffectInstance(effect.get(), DURATION, AMPLIFIER));
        }
        ItemStack rest = super.finishUsingItem(stack, level, entity);
        if (entity instanceof Player player && !player.getAbilities().instabuild) {
            if (rest.isEmpty()) {
                return new ItemStack(Items.GLASS_BOTTLE);
            }
            if (!player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
                player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
            }
        }
        return rest;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(hint).withStyle(ChatFormatting.BLUE));
        tooltip.accept(Component.translatable("item.palamod.imbue.duration").withStyle(ChatFormatting.GRAY));
    }
}

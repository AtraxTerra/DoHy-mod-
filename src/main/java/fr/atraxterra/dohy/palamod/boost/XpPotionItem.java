package fr.atraxterra.dohy.palamod.boost;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Une potion d'XP de metier ({@code ItemBoostDoubleXp.onEaten}).
 *
 * <p>La boisson passe par le composant {@code consumable} pose a
 * l'enregistrement ; l'effet est ajoute ici, cote serveur. Un effet deja actif
 * s'allonge de la duree de la potion, comme {@code getBoostDoubleXp() + duree}.
 */
public class XpPotionItem extends Item {

    private static final int TICKS_PER_MINUTE = 20 * 60;

    private final int multiplier;

    public XpPotionItem(int multiplier, Properties properties) {
        super(properties);
        this.multiplier = multiplier;
    }

    public int multiplier() {
        return multiplier;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) {
            int added = PalaXpPotions.minutes(stack) * TICKS_PER_MINUTE;
            MobEffectInstance active = entity.getEffect(PalaXpPotions.effect(multiplier));
            int total = (active == null ? 0 : active.getDuration()) + added;
            entity.addEffect(PalaXpPotions.instance(multiplier, total));
            if (entity instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable(
                        "message.palamod.xp_potion", "x" + multiplier,
                        PalaXpPotions.label(total / TICKS_PER_MINUTE)).withStyle(ChatFormatting.GREEN)));
            }
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.palamod.xp_potion.named", super.getName(stack),
                PalaXpPotions.label(PalaXpPotions.minutes(stack)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.xp_potion", "x" + multiplier,
                PalaXpPotions.label(PalaXpPotions.minutes(stack))).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.palamod.xp_potion.stack").withStyle(ChatFormatting.DARK_GRAY));
    }
}

package fr.atraxterra.dohy.palamod.pet;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Une potion d'XP de familier ({@code pet_xp_small / medium / big}) : bue, elle
 * donne son XP au familier (1 000, 5 000 ou 20 000). Sans familier, on ne la boit
 * pas.
 */
public class PetXpPotionItem extends Item {

    private final long xp;

    public PetXpPotionItem(long xp, Properties properties) {
        super(properties);
        this.xp = xp;
    }

    public long xp() {
        return xp;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.getData(PalaPets.LOOK.get()).familiar() == null) {
            if (!level.isClientSide()) {
                player.sendSystemMessage(Component.literal("[Familier] Il te faut un familier pour boire cette potion.")
                        .withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        return super.use(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player && PetApi.has(player)) {
            player.sendSystemMessage(Component.literal("[Familier] +" + xp + " XP pour "
                    + PetApi.data(player).familiar().label() + ".").withStyle(ChatFormatting.LIGHT_PURPLE));
            PetApi.grantXp(player, xp);
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.pet_xp", xp).withStyle(ChatFormatting.GRAY));
    }
}

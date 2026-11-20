package fr.atraxterra.dohy.grade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** L'insigne d'un grade : une icone, rien de plus ; le grade lui-meme s'achete a la Boutique. */
public class GradeBadgeItem extends Item {

    public GradeBadgeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.dohy.grade_badge").withStyle(ChatFormatting.GRAY));
    }
}

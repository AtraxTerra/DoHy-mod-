package fr.atraxterra.dohy.palamod.pet;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * L'appat a familier ({@code PetItemRegistry.BAIT}) : tue un serpent (75 %) ou un
 * escargot (25 %) pour en avoir un, puis clic droit sur sa cage.
 */
public class PetBaitItem extends Item {

    public PetBaitItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.pet_bait").withStyle(ChatFormatting.GRAY));
    }
}

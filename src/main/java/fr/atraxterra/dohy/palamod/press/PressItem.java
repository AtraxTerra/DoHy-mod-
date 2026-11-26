package fr.atraxterra.dohy.palamod.press;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Une fourniture d'imprimerie : une ligne d'infobulle dit ou elle sert. */
public class PressItem extends Item {

    private final String usage;

    public PressItem(String usage, Properties properties) {
        super(properties);
        this.usage = usage;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(usage).withStyle(ChatFormatting.GRAY));
    }
}

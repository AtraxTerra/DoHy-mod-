package fr.atraxterra.dohy.palamod.botanist;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Un objet du Botaniste : une ligne d'infobulle dit a quoi il sert. */
public class BotanistItem extends Item {

    private final String usage;

    public BotanistItem(String usage, Properties properties) {
        super(properties);
        this.usage = usage;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(usage).withStyle(ChatFormatting.GRAY));
    }
}

package fr.atraxterra.dohy.palamod.botanist;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.function.Consumer;

/** Un bloc du Botaniste : son infobulle dit a quoi il sert (le scanner de Genis). */
public class BotanistBlockItem extends BlockItem {

    private final List<String> lines;

    public BotanistBlockItem(Block block, Properties properties, String... lines) {
        super(block, properties);
        this.lines = List.of(lines);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        for (int index = 0; index < lines.size(); index++) {
            tooltip.accept(Component.translatable(lines.get(index))
                    .withStyle(index == 0 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        }
    }
}

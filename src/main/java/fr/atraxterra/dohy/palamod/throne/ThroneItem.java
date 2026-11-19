package fr.atraxterra.dohy.palamod.throne;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/** L'objet trone : son infobulle dit a qui il appartient. */
public class ThroneItem extends BlockItem {

    public ThroneItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        String owner = stack.get(PalaThrones.OWNER.get());
        if (owner != null && !owner.isEmpty()) {
            tooltip.accept(Component.translatable("tooltip.palamod.throne.owner", owner)
                    .withStyle(ChatFormatting.RED));
        } else {
            tooltip.accept(Component.translatable("tooltip.palamod.throne.blank").withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.translatable("tooltip.palamod.throne.sit").withStyle(ChatFormatting.DARK_GRAY));
    }
}

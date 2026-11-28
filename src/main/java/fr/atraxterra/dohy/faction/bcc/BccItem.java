package fr.atraxterra.dohy.faction.bcc;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/** L'objet du Base Claim Controller, et les conditions de sa pose. */
public class BccItem extends BlockItem {

    public BccItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.dohy.base_claim_controller").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.dohy.base_claim_controller.place")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.dohy.base_claim_controller.warn")
                .withStyle(ChatFormatting.RED));
    }
}

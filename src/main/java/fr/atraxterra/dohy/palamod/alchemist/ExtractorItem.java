package fr.atraxterra.dohy.palamod.alchemist;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/** L'extracteur en objet ({@code ItemBlockExtractor}) : « Utilisations restantes : x/144 ». */
public class ExtractorItem extends BlockItem {

    public ExtractorItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        int uses = stack.getOrDefault(PalaAlchemist.EXTRACTOR_USES.get(), ExtractorBlockEntity.DEFAULT_USES);
        tooltip.accept(Component.translatable("item.palamod.extractor.uses", uses, ExtractorBlockEntity.DEFAULT_USES)
                .withStyle(ChatFormatting.DARK_RED));
    }
}

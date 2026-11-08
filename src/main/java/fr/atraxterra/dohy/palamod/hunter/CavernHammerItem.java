package fr.atraxterra.dohy.palamod.hunter;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Marteau des cavernes ({@code ItemCavernHammer extends ItemPickaxe}) : une
 * pioche de diamant a 256 utilisations, qui recolte l'ame d'un spawner de
 * donjon en le cassant ({@link SpawnerEvents}). Une ame par spawner, une usure
 * par spawner.
 */
public class CavernHammerItem extends Item {

    public CavernHammerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.palamod.cavern_hammer.tooltip").withStyle(ChatFormatting.GRAY));
    }
}

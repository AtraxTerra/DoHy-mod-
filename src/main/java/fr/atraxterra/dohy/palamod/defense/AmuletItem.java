package fr.atraxterra.dohy.palamod.defense;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Une amulette : par une, elle se fond dans un plastron a l'enclume ({@link Amulets}). */
public class AmuletItem extends Item {

    private final AmuletType type;

    public AmuletItem(AmuletType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public AmuletType type() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.amulet.combine").withStyle(ChatFormatting.YELLOW));
        tooltip.accept(Component.translatable("tooltip.palamod.amulet." + type.key()).withStyle(ChatFormatting.GRAY));
    }
}

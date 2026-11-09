package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Une essence de donjon (wiki : « Fragment d'essence », « Essence »,
 * « Essence concentree »). Elle repare a l'enclume l'equipement de son
 * donjon et de son tier ; la concentree repare aussi l'antique.
 */
public class EssenceItem extends Item {

    private final Dungeon dungeon;
    private final EssenceTier tier;

    public EssenceItem(Dungeon dungeon, EssenceTier tier, Properties properties) {
        super(properties);
        this.dungeon = dungeon;
        this.tier = tier;
    }

    public Dungeon dungeon() {
        return dungeon;
    }

    public EssenceTier tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.essence." + tier.name().toLowerCase(java.util.Locale.ROOT),
                dungeon.display()).withStyle(dungeon.color()));
        tooltip.accept(Component.translatable("tooltip.palamod.essence.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}

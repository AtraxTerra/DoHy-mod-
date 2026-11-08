package fr.atraxterra.dohy.palamod.alchemist;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Cle de portail ({@code ItemAlchemistKey}) : elle ouvre le portail de son
 * metal et garde la seve qu'on y a versee ({@code totalSeve}), meme retiree.
 */
public class PortalKeyItem extends Item {

    private final PortalTier tier;

    public PortalKeyItem(PortalTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public PortalTier tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        int sap = stack.getOrDefault(PalaAlchemist.KEY_SAP.get(), 0);
        tooltip.accept(sap > 0
                ? Component.translatable("item.palamod.portal_key.sap", sap).withStyle(ChatFormatting.GOLD)
                : Component.translatable("item.palamod.portal_key.no_sap").withStyle(ChatFormatting.GOLD));
    }
}

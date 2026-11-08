package fr.atraxterra.dohy.palamod.alchemist;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/** Le tank en objet ({@code ItemBlockTank}) : contenu, niveau, niveau maximal. */
public class TankItem extends BlockItem {

    public TankItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        TankSap sap = stack.get(PalaAlchemist.TANK_SAP.get());
        int capacity = getBlock() instanceof TankBlock tank ? tank.tier().capacity() : 0;
        Component content = sap == null ? Component.translatable("item.palamod.tank.empty") : sap.type().displayName();
        tooltip.accept(Component.translatable("item.palamod.tank.content", content).withStyle(ChatFormatting.DARK_RED));
        tooltip.accept(Component.translatable("item.palamod.tank.level", sap == null ? 0 : sap.amount(), capacity)
                .withStyle(ChatFormatting.RED));
    }
}

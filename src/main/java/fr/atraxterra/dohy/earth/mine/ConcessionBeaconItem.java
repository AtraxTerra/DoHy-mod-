package fr.atraxterra.dohy.earth.mine;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * La balise en objet : elle ne se pose que dans une mine de la Terre que la
 * faction du joueur peut capturer ; posee, elle lance la capture.
 */
public class ConcessionBeaconItem extends BlockItem {

    public ConcessionBeaconItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (context.getLevel().isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) {
            return super.place(context);
        }
        String refusal = EarthMines.refusal(player, context.getClickedPos());
        if (refusal != null) {
            player.sendSystemMessage(Component.literal(refusal + ".").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        InteractionResult result = super.place(context);
        if (result.consumesAction()) {
            EarthMines.start(player, context.getClickedPos());
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.dohy.concession_beacon.1").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.dohy.concession_beacon.2").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.dohy.concession_beacon.3").withStyle(ChatFormatting.GRAY));
    }
}

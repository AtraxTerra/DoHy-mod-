package fr.atraxterra.dohy.underground;

import fr.atraxterra.dohy.faction.enterprise.Line;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * Une graine des affaires illegales (lot 24c) : elle ne se plante qu'a la latitude de sa
 * drogue, et dit pourquoi quand elle refuse.
 */
public class DrugSeedItem extends BlockItem {

    private final Line line;

    public DrugSeedItem(Block block, Properties properties, Line line) {
        super(block, properties);
        this.line = line;
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!Latitudes.grows(line, context.getLevel(), context.getClickedPos())) {
            if (context.getPlayer() instanceof ServerPlayer player) {
                long latitude = Math.round(Latitudes.at(context.getLevel(), context.getClickedPos()));
                player.sendSystemMessage(Component.literal(line.label() + " : " + line.climate().toLowerCase()
                        + " (ici : " + Math.abs(latitude) + " degres).").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }
}

package fr.atraxterra.dohy.agri;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * Une semence de cereale : elle ne se plante que dans le climat de sa cereale,
 * et dit pourquoi quand elle refuse.
 */
public class SeedItem extends BlockItem {

    private final Cereal cereal;

    public SeedItem(Block block, Properties properties, Cereal cereal) {
        super(block, properties);
        this.cereal = cereal;
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!cereal.growsAt(context.getLevel(), context.getClickedPos())) {
            if (context.getPlayer() instanceof ServerPlayer player) {
                Climate here = Climate.at(context.getLevel(), context.getClickedPos());
                player.sendSystemMessage(Component.literal(cereal.label() + " : ne pousse qu'en climat "
                        + cereal.climate().label() + " (ici : " + here.label() + ").")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }
}

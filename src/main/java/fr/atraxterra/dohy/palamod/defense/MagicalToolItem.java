package fr.atraxterra.dohy.palamod.defense;

import fr.atraxterra.dohy.faction.FactionApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * L'outil magique ({@code ItemMagicalTool}) : 10 000 utilisations, par un.
 *
 * <p>Clic droit sur une obsidienne de defense, une enclume de PalaMod ou un
 * bloc renforce (tag {@code palamod:magical_tool_removable}) : le bloc part en
 * objet, sans declencher son piege. C'est la seule facon de demonter ce qu'on
 * ne mine pas. Retirer une obsidienne renforcee compte pour l'arbre de faction
 * (branche Defense, palier 3).
 */
public class MagicalToolItem extends Item {

    public static final String HARDENED_STAT = "magical:hardened";

    public MagicalToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(PalaDefense.MAGICAL_REMOVABLE)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player != null && !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())) {
            return InteractionResult.FAIL;
        }
        ItemStack drop = new ItemStack(state.getBlock().asItem());
        if (state.hasProperty(SlabBlock.TYPE) && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
            drop.setCount(2);
        }
        server.removeBlock(pos, false);
        Block.popResource(server, pos, drop);
        server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.0f, 1.2f);
        if (player instanceof ServerPlayer serverPlayer) {
            context.getItemInHand().hurtAndBreak(1, serverPlayer, context.getHand());
            if (state.is(PalaDefense.HARDENED_OBSIDIAN.get())) {
                FactionApi.stat(serverPlayer, HARDENED_STAT, 1);
            }
        }
        return InteractionResult.SUCCESS;
    }
}

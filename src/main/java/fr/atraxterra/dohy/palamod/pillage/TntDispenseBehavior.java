package fr.atraxterra.dohy.palamod.pillage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Un distributeur allume les TNT de PalaMod comme la TNT vanilla : devant
 * lui, une TNT amorcee de son genre, sans allumeur. C'est le coeur d'un canon
 * a TNT, seul moyen d'en envoyer dans les claims d'une base ennemie (lot 22g),
 * ou rien ne se pose.
 */
final class TntDispenseBehavior extends DefaultDispenseItemBehavior {

    @Override
    protected ItemStack execute(BlockSource source, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof PalaTntBlock block)) {
            return super.execute(source, stack);
        }
        ServerLevel level = source.level();
        BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
        PalaPrimedTnt tnt = new PalaPrimedTnt(level, pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, null,
                block.defaultBlockState());
        level.addFreshEntity(tnt);
        level.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0f,
                1.0f);
        level.gameEvent(null, GameEvent.ENTITY_PLACE, pos);
        stack.shrink(1);
        return stack;
    }
}

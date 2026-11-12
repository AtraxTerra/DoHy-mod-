package fr.atraxterra.dohy.agri;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Le tracteur en objet : un clic sur le sol le pose, face au regard du joueur.
 * Il garde le carburant qu'il avait quand on l'a demonte.
 */
public class TractorItem extends Item {

    public TractorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (context.getPlayer() instanceof ServerPlayer player && !Tractor.allowed(player)) {
            return InteractionResult.FAIL;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        Tractor tractor = DoHyAgri.TRACTOR.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (tractor == null) {
            return InteractionResult.FAIL;
        }
        float yaw = context.getPlayer() == null ? 0.0f : context.getPlayer().getYRot();
        tractor.snapTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, yaw, 0.0f);
        if (!level.noCollision(tractor)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        tractor.setFuel(stack.getOrDefault(DoHyAgri.FUEL.get(), 0));
        level.addFreshEntity(tractor);
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.4f, 0.8f);
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}

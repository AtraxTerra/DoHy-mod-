package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.agri.DoHyAgri;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * La moissonneuse en objet : un clic sur le sol la pose, face au regard du
 * joueur, qui en devient le proprietaire. Elle garde le carburant qu'elle avait
 * quand on l'a demontee.
 */
public class HarvesterItem extends Item {

    public HarvesterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        SpiceHarvester harvester = ArrakisContent.HARVESTER.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (harvester == null) {
            return InteractionResult.FAIL;
        }
        float yaw = context.getPlayer() == null ? 0.0f : context.getPlayer().getYRot();
        harvester.snapTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, yaw, 0.0f);
        if (!level.noCollision(harvester)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        harvester.setFuel(stack.getOrDefault(DoHyAgri.FUEL.get(), 0));
        if (context.getPlayer() != null) {
            harvester.setOwner(context.getPlayer());
        }
        level.addFreshEntity(harvester);
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.5f, 0.6f);
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}

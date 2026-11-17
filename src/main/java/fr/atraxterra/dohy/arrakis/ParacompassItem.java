package fr.atraxterra.dohy.arrakis;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;

import java.util.Optional;

/**
 * Le paracompas : sur Arrakis, son aiguille montre l'entree du sietch le plus proche ; ailleurs,
 * elle tourne sans fin. Il suit son porteur : la cible change quand un autre sietch devient le
 * plus proche.
 */
public class ParacompassItem extends Item {

    public ParacompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        if (level.getGameTime() % 20L != 0L) {
            return;
        }
        LodestoneTracker current = stack.get(DataComponents.LODESTONE_TRACKER);
        if (level.dimension() != DoHyArrakis.DIMENSION) {
            if (current != null) {
                stack.remove(DataComponents.LODESTONE_TRACKER);
            }
            return;
        }
        Sietches.Site site = Sietches.nearest(entity.getX(), entity.getZ());
        if (site == null) {
            return;
        }
        GlobalPos target = GlobalPos.of(level.dimension(), site.face());
        if (current == null || current.target().isEmpty() || !current.target().get().equals(target)) {
            stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(target), false));
        }
    }
}

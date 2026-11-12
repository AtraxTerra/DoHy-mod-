package fr.atraxterra.dohy.agri;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Iterator;

/** Ce que le tracteur change aux evenements du jeu. */
@EventBusSubscriber
public final class TractorEvents {

    private TractorEvents() {
    }

    /**
     * La moisson va a la tremie. En dernier : l'experience de fermier, les
     * contrats et les collections ont lu la recolte avant qu'elle ne quitte
     * le sol. Ce qui ne tient pas dans le tracteur tombe par terre, comme
     * d'habitude.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockDrops(BlockDropsEvent event) {
        Tractor tractor = Tractor.harvesting;
        if (tractor == null || event.isCanceled()) {
            return;
        }
        Iterator<ItemEntity> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack stack = drops.next().getItem();
            int left = tractor.collect(stack);
            if (left <= 0) {
                drops.remove();
            } else {
                stack.setCount(left);
            }
        }
    }

    /** Ses roues ne retournent pas le champ qu'il vient de labourer. */
    @SubscribeEvent
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof Tractor || event.getEntity().getVehicle() instanceof Tractor) {
            event.setCanceled(true);
        }
    }
}

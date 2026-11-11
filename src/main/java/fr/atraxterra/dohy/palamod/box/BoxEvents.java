package fr.atraxterra.dohy.palamod.box;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Le coffre d'une box et le lot qu'elle montre ne sont que de l'affichage : on
 * ne les frappe pas, on ne les nourrit pas, et ils ne reviennent pas du disque
 * (leur block entity les refait).
 */
@EventBusSubscriber
public final class BoxEvents {

    private BoxEvents() {
    }

    private static boolean isBox(Entity entity) {
        return entity.entityTags().contains(BoxBlockEntity.TAG);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (isBox(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (isBox(event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.PASS);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && isBox(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}

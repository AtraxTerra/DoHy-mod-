package fr.atraxterra.dohy.palamod.hunter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * La creature du mode manuel d'un controleur ({@code EntitySpawner}).
 *
 * <p>PalaSpawner en faisait une entite a part, dessinee comme la creature
 * choisie. Ici c'est la creature elle-meme, sans IA, muette et invulnerable,
 * marquee d'une etiquette : un coup en compte une de moins, un clic droit passe
 * a la suivante. Elle n'est jamais relue du disque — le controleur la repose a
 * son prochain passage — ce qui ne laisse aucune creature orpheline.
 */
@EventBusSubscriber
public final class SpawnerControllerEvents {

    private SpawnerControllerEvents() {
    }

    private static boolean isHologram(Entity entity) {
        return entity.entityTags().contains(SpawnerControllerBlockEntity.HOLOGRAM_TAG);
    }

    /** Le controleur sous la creature, s'il la reconnait ; sinon elle disparait. */
    private static SpawnerControllerBlockEntity controller(Entity hologram) {
        BlockPos below = BlockPos.containing(hologram.getX(), hologram.getY() - 0.5d, hologram.getZ());
        if (hologram.level().getBlockEntity(below) instanceof SpawnerControllerBlockEntity controller
                && controller.owns(hologram)) {
            return controller;
        }
        hologram.discard();
        return null;
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Entity target = event.getTarget();
        if (!isHologram(target)) {
            return;
        }
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            SpawnerControllerBlockEntity controller = controller(target);
            if (controller != null) {
                controller.hit(player);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!isHologram(target)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer && event.getHand() == InteractionHand.MAIN_HAND) {
            SpawnerControllerBlockEntity controller = controller(target);
            if (controller != null) {
                controller.cycle();
            }
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && isHologram(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}

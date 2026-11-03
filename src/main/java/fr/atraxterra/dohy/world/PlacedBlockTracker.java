package fr.atraxterra.dohy.world;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Tient a jour le suivi des blocs poses.
 *
 * <p>Deux evenements suffisent :
 * <ul>
 *   <li>la pose par un joueur ajoute la marque ;</li>
 *   <li>la casse la retire, pour que la position redevienne vierge si le monde
 *       y regenere quelque chose et pour que l'ensemble ne grossisse pas
 *       indefiniment.</li>
 * </ul>
 *
 * <p>La casse passe par {@code BlockDropsEvent} et non par un evenement de
 * rupture : {@code BlockEvent.BreakEvent} n'existe plus en 26.2. C'est d'ailleurs
 * le bon endroit pour la suite, puisque les systemes d'XP et de collection ont
 * besoin de connaitre les drops en meme temps que la position.
 */
@EventBusSubscriber
public final class PlacedBlockTracker {

    private PlacedBlockTracker() {
    }

    /**
     * Pose par une entite.
     *
     * <p>{@code EntityMultiPlaceEvent} derive de cet evenement et arrive donc
     * ici aussi, mais seule la position principale est marquee : la seconde
     * moitie d'une porte ou d'un lit reste non marquee. L'exploit theorique est
     * de casser cette moitie pour gagner de l'XP ; il coute un bloc pose pour
     * un bloc casse, donc il n'enrichit personne. A traiter si une recompense
     * future le rend rentable.
     */
    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        PlacedBlocks.mark(event.getLevel(), event.getPos());
    }

    /**
     * Casse d'un bloc : la marque disparait.
     *
     * <p>Passe par {@link PlacedBlocks#wasPlayerPlaced}, qui memoise sa reponse
     * par evenement. Ce gestionnaire garantit donc le nettoyage meme si aucun
     * systeme de recompense n'a encore demande le verdict, sans jamais priver
     * les suivants de la bonne reponse.
     */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        PlacedBlocks.wasPlayerPlaced(event);
    }
}

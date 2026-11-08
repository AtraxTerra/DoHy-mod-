package fr.atraxterra.dohy.palamod;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * La houe sur la terre fertilisee.
 *
 * <p>Le PalaMod passait par {@code UseHoeEvent}, qui n'existe plus. Son
 * remplacant en 26.2 est {@code BlockToolModificationEvent}, verifie a la
 * sonde : il porte l'{@code ItemAbility} demandee et un etat final a renseigner.
 * Le labour vanilla ne connait que la terre et l'herbe ; sans ce crochet, la
 * houe ne ferait rien sur notre bloc et le joueur n'aurait aucun moyen
 * d'obtenir la version labouree a partir de la version brute.
 *
 * <p>L'operation est reversible dans les deux sens : casser la version
 * labouree rend de la terre fertilisee brute, et non un bloc perdu.
 */
@EventBusSubscriber
public final class PalaSoilEvents {

    private PalaSoilEvents() {
    }

    @SubscribeEvent
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.getItemAbility() != ItemAbilities.HOE_TILL) {
            return;
        }
        if (!event.getState().is(PalaBlocks.FERTILIZED_DIRT.get())) {
            return;
        }
        event.setFinalState(PalaBlocks.FERTILIZED_FARMLAND.get().defaultBlockState());
    }
}

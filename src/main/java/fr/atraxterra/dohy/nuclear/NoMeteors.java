package fr.atraxterra.dohy.nuclear;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Plus de meteorites : celles de NTM ({@code hbm:entity_meteor}) n'entrent plus dans le
 * monde, qu'elles viennent d'une chute au hasard, d'une pluie d'etoiles filantes ou de la
 * telecommande a meteorite. Une meteorite deja en vol dans une sauvegarde disparait a son
 * chargement.
 *
 * <p>On bloque l'entite plutot que de toucher aux reglages de NTM : ses reglages du monde
 * viennent d'un registre de donnees, et une erreur la-dedans empecherait le monde de se
 * charger. Sans NTM, l'identifiant n'existe pas et rien ne change.
 */
@EventBusSubscriber
public final class NoMeteors {

    private static final Identifier METEOR = Identifier.fromNamespaceAndPath("hbm", "entity_meteor");

    private NoMeteors() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && METEOR.equals(BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()))) {
            event.setCanceled(true);
        }
    }
}

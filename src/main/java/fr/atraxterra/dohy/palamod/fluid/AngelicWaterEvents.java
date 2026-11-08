package fr.atraxterra.dohy.palamod.fluid;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Le soin de l'eau angelique ({@code BlockAngelicWater.onEntityCollidedWithBlock}).
 *
 * <p>Un compteur monte a chaque tick passe dans le fluide ; a 40, si le joueur
 * est blesse, il regagne un point de vie — un demi-coeur toutes les deux
 * secondes, soit le rythme d'une regeneration I. <b>Ecart</b> : l'original
 * gardait ce compteur sur le bloc, partage par tous les joueurs (plus on etait
 * nombreux, plus chacun guerissait vite). Ici il est par joueur.
 */
@EventBusSubscriber
public final class AngelicWaterEvents {

    /** {@code if (i >= 40)}. */
    private static final int PERIOD = 40;

    /** {@code player.heal(1.0F)}. */
    private static final float HEAL = 1.0f;

    private AngelicWaterEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % PERIOD != 0) {
            return;
        }
        if (player.getHealth() < player.getMaxHealth()
                && player.isInFluidType(PalaFluids.ANGELIC_WATER_TYPE.get())) {
            player.heal(HEAL);
        }
    }
}

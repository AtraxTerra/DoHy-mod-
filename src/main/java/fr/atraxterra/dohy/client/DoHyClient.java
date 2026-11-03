package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Enregistrements cote client.
 *
 * <p>En 26.2, {@code @EventBusSubscriber} n'a plus ni {@code modid} ni
 * {@code bus} : le bus est deduit du type de l'evenement (un
 * {@code IModBusEvent} part sur le bus du mod), et seule la distribution reste
 * a preciser.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class DoHyClient {

    private DoHyClient() {
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "money"),
                new MoneyHudLayer());
    }
}

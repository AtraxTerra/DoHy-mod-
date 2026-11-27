package fr.atraxterra.dohy.client.botanist;

import fr.atraxterra.dohy.palamod.botanist.PalaBotanist;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Le cote client du laboratoire du botaniste : le rendu du pot. */
@EventBusSubscriber(Dist.CLIENT)
public final class BotanistClient {

    private BotanistClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PalaBotanist.POT_TYPE.get(), BotanistPotRenderer::new);
    }
}

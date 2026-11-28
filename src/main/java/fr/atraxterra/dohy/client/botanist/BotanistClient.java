package fr.atraxterra.dohy.client.botanist;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.botanist.PalaBotanist;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Le cote client du laboratoire du botaniste : le rendu du pot et de sa plante. */
@EventBusSubscriber(Dist.CLIENT)
public final class BotanistClient {

    private BotanistClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PalaBotanist.POT_TYPE.get(), BotanistPotRenderer::new);
    }

    /** Les modeles de JobsReborn se relisent avec les ressources (F3 + T, pack de ressources). */
    @SubscribeEvent
    public static void onReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "botanist_models"),
                (ResourceManagerReloadListener) manager -> BotanistModels.clear());
    }
}

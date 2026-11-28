package fr.atraxterra.dohy.client.botanist;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.botanist.BotanistCodex;
import fr.atraxterra.dohy.palamod.botanist.PalaBotanist;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Le cote client du laboratoire du botaniste : le rendu du pot et de sa plante, du scanner de
 * Genis, des machines du lot 2c et des nibblers, les ecrans du scanner, de la centrifugeuse et de la
 * reserve, et le codex recu du serveur.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class BotanistClient {

    private BotanistClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PalaBotanist.POT_TYPE.get(), BotanistPotRenderer::new);
        event.registerBlockEntityRenderer(PalaBotanist.GENE_SCANNER_TYPE.get(), GeneScannerRenderer::new);
        event.registerBlockEntityRenderer(PalaBotanist.CENTRIFUGE_TYPE.get(), CentrifugeRenderer::new);
        event.registerBlockEntityRenderer(PalaBotanist.SEED_RESERVE_TYPE.get(), SeedReserveRenderer::new);
        event.registerBlockEntityRenderer(PalaBotanist.LAB_CONTROL_TYPE.get(), LabControlRenderer::new);
        event.registerEntityRenderer(PalaBotanist.NIBBLER.get(), NibblerRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.<MachineMenu, GeneScannerScreen>register(PalaBotanist.GENE_SCANNER_MENU.get(), GeneScannerScreen::new);
        event.<MachineMenu, CentrifugeScreen>register(PalaBotanist.CENTRIFUGE_MENU.get(), CentrifugeScreen::new);
        event.<MachineMenu, SeedReserveScreen>register(PalaBotanist.SEED_RESERVE_MENU.get(), SeedReserveScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(BotanistCodex.Sync.TYPE, (payload, context) -> ClientCodex.accept(payload));
    }

    /** Les modeles de JobsReborn se relisent avec les ressources (F3 + T, pack de ressources). */
    @SubscribeEvent
    public static void onReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "botanist_models"),
                (ResourceManagerReloadListener) manager -> BotanistModels.clear());
    }
}

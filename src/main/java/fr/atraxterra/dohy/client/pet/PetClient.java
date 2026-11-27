package fr.atraxterra.dohy.client.pet;

import com.mojang.blaze3d.platform.InputConstants;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.pet.PalaPets;
import fr.atraxterra.dohy.palamod.pet.PetNet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Le cote client des familiers : le serpent et l'escargot, la cage, le familier
 * sur l'epaule des joueurs, et la touche de la roue (R par defaut : le wiki dit
 * MAJ, qui fait deja s'accroupir ; a changer dans les commandes du jeu).
 */
@EventBusSubscriber(Dist.CLIENT)
public final class PetClient {

    private static KeyMapping wheelKey;
    private static boolean wheelHeld;

    private PetClient() {
    }

    public static KeyMapping wheelKey() {
        return wheelKey;
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        wheelKey = new KeyMapping("key.dohy.pet_wheel", InputConstants.KEY_R, KeyMapping.Category.MISC);
        event.register(wheelKey);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PalaPets.SNAKE.get(), SnakeRenderer::new);
        event.registerEntityRenderer(PalaPets.SNAIL.get(), SnailRenderer::new);
        event.registerBlockEntityRenderer(PalaPets.PET_CAGE_TYPE.get(), PetCageRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SnakeModel.LAYER, SnakeGeometry::createBodyLayer);
        event.registerLayerDefinition(SnailModel.LAYER, SnailGeometry::createBodyLayer);
    }

    /** Les modeles de PalaPet (familiers, cage) se relisent avec les ressources. */
    @SubscribeEvent
    public static void onReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "pet_models"),
                (ResourceManagerReloadListener) manager -> {
                    FamiliarModels.clear();
                    PetCageRenderer.clear();
                });
    }

    /** Le familier sur l'epaule, pour chaque silhouette de joueur. */
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        FamiliarModels models = new FamiliarModels();
        for (var skin : event.getSkins()) {
            var renderer = event.getPlayerRenderer(skin);
            if (renderer != null) {
                renderer.addLayer(new PetShoulderLayer(renderer, models));
            }
        }
    }

    /** La touche enfoncee ouvre la roue ; la roue part quand on la relache (PetWheelScreen). */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (wheelKey == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean down = wheelKey.isDown();
        if (down && !wheelHeld && minecraft.player != null && minecraft.gui.screen() == null) {
            ClientPacketDistributor.sendToServer(PetNet.OpenWheel.INSTANCE);
        }
        wheelHeld = down;
        while (wheelKey.consumeClick()) {
            // L'appui est lu par isDown : on vide la file des clics.
        }
    }
}

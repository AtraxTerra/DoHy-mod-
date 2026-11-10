package fr.atraxterra.dohy.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.atraxterra.dohy.faction.FactionPayloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * La touche de faction. Sur Paladium c'est F ; ici, F echange deja les mains en
 * vanilla, et un appui ferait les deux. Par defaut G, a changer dans les
 * commandes du jeu.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class FactionClient {

    private static KeyMapping factionKey;

    private FactionClient() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        factionKey = new KeyMapping("key.dohy.faction", InputConstants.KEY_G, KeyMapping.Category.MISC);
        event.register(factionKey);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (factionKey == null) {
            return;
        }
        while (factionKey.consumeClick()) {
            if (Minecraft.getInstance().player != null) {
                ClientPacketDistributor.sendToServer(FactionPayloads.Open.INSTANCE);
            }
        }
    }
}

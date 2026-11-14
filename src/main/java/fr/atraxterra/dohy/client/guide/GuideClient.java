package fr.atraxterra.dohy.client.guide;

import com.mojang.blaze3d.platform.InputConstants;
import fr.atraxterra.dohy.ui.InventoryNav;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** La touche de l'encyclopedie : J par defaut, a changer dans les commandes du jeu. */
@EventBusSubscriber(Dist.CLIENT)
public final class GuideClient {

    private static KeyMapping encyclopediaKey;

    private GuideClient() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        encyclopediaKey = new KeyMapping("key.dohy.encyclopedia", InputConstants.KEY_J, KeyMapping.Category.MISC);
        event.register(encyclopediaKey);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (encyclopediaKey == null) {
            return;
        }
        while (encyclopediaKey.consumeClick()) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null) {
                ClientPacketDistributor.sendToServer(new InventoryNav.Open("encyclopedia"));
            }
        }
    }
}

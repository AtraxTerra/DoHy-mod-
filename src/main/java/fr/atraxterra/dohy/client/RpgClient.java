package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.palamod.dungeon.RpgPayloads;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Le bouton « Profil RPG » de l'inventaire (wiki : « depuis l'inventaire,
 * bouton Profil RPG »), au-dessus du coin droit de l'inventaire. Le profil
 * lui-meme est un ecran de coffre du serveur ; {@code /profil} l'ouvre aussi.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class RpgClient {

    private RpgClient() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) {
            return;
        }
        int x = screen.getGuiLeft() + 176 - 64;
        int y = screen.getGuiTop() - 22;
        event.addListener(Button.builder(Component.translatable("button.palamod.rpg_profile"),
                        button -> ClientPacketDistributor.sendToServer(RpgPayloads.OpenProfile.INSTANCE))
                .bounds(x, y, 64, 20).build());
    }
}

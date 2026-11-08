package fr.atraxterra.dohy.client.mount;

import com.mojang.blaze3d.platform.InputConstants;
import fr.atraxterra.dohy.palamod.mount.Dancarok;
import fr.atraxterra.dohy.palamod.mount.MountPayloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * La touche K ({@code KeysRegister}, « Monture GUI ») et les clics en selle.
 *
 * <p>K ouvre le menu de la monture ; elle n'invoque pas directement, comme
 * dans l'original. En selle d'un Dancarok, attaquer et utiliser ne font plus
 * rien ; le clic droit maintenu donne les coups de corne. Mount remplacait les
 * touches par des touches muettes ; on annule ici les clics.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class MountClient {

    private static KeyMapping mountKey;

    private MountClient() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        mountKey = new KeyMapping("key.palamod.mount", InputConstants.KEY_K, KeyMapping.Category.MISC);
        event.register(mountKey);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (mountKey == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // Une touche ne compte de clic qu'en jeu, aucun ecran ouvert.
        while (mountKey.consumeClick()) {
            if (minecraft.player != null) {
                ClientPacketDistributor.sendToServer(MountPayloads.OpenMenu.INSTANCE);
            }
        }
        // Clic droit maintenu en selle : un coup tous les deux ticks, le serveur
        // n'en garde qu'un toutes les 500 ms (CSMountMovement envoyait a chaque
        // tick).
        LocalPlayer player = minecraft.player;
        if (player != null && minecraft.options.keyUse.isDown() && riding(player)
                && player.tickCount % 2 == 0) {
            ClientPacketDistributor.sendToServer(MountPayloads.HornStrike.INSTANCE);
        }
    }

    private static boolean riding(LocalPlayer player) {
        return player.getVehicle() instanceof Dancarok mount && mount.getControllingPassenger() == player;
    }

    /** En selle, ni attaque ni utilisation d'objet. */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && riding(player)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
}

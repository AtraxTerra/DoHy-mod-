package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.client.ui.screen.KitInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Remplace l'inventaire du jeu par celui du kit ({@link KitInventoryScreen}).
 *
 * <p>Seulement hors du mode creatif : l'ecran du jeu y bascule de lui-meme sur
 * l'inventaire creatif, qu'on garde tel quel. Le remplacement se fait a
 * l'ouverture, donc pour toutes les facons d'ouvrir l'inventaire (touche,
 * avancee, autre mod).
 */
@EventBusSubscriber(Dist.CLIENT)
public final class InventoryClient {

    private InventoryClient() {
    }

    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof InventoryScreen)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.isCreative()) {
            return;
        }
        event.setNewScreen(new KitInventoryScreen(minecraft.player));
    }
}

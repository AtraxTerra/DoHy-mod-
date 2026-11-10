package fr.atraxterra.dohy.client.ui;

import fr.atraxterra.dohy.client.ui.screen.JobDetailScreen;
import fr.atraxterra.dohy.client.ui.screen.JobsScreen;
import fr.atraxterra.dohy.client.ui.screen.MenuScreen;
import fr.atraxterra.dohy.client.ui.screen.ShopScreen;
import fr.atraxterra.dohy.ui.view.JobDetailView;
import fr.atraxterra.dohy.ui.view.JobsView;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.ShopView;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.ui.view.UiView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Le cote client du kit : ouvrir l'ecran d'une vue, envoyer une action. */
@EventBusSubscriber(value = Dist.CLIENT)
public final class UiClient {

    private static final Map<Identifier, Function<UiView, Screen>> SCREENS = new HashMap<>();

    static {
        SCREENS.put(ShopView.ID, view -> new ShopScreen((ShopView) view));
        SCREENS.put(JobsView.ID, view -> new JobsScreen((JobsView) view));
        SCREENS.put(JobDetailView.ID, view -> new JobDetailScreen((JobDetailView) view));
    }

    private UiClient() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientPayloadHandlersEvent event) {
        event.register(UiNet.Show.TYPE, (payload, context) -> show(payload.view()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void show(UiView view) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen current = minecraft.gui.screen();
        if (current instanceof ViewScreen screen && current instanceof UiScreen ui && ui.viewId().equals(view.viewId())) {
            screen.accept(view);
            return;
        }
        if (view instanceof MenuView menu) {
            minecraft.gui.setScreen(new MenuScreen(menu));
            return;
        }
        Function<UiView, Screen> factory = SCREENS.get(view.viewId());
        if (factory != null) {
            minecraft.gui.setScreen(factory.apply(view));
        }
    }

    /** Envoie une action au serveur ; sans connexion (captures d'ecran), ne fait rien. */
    public static void send(Identifier view, String verb, String argument, int amount) {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(new UiNet.Action(view, verb, argument, amount));
        }
    }
}

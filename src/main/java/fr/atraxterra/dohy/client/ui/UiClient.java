package fr.atraxterra.dohy.client.ui;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import fr.atraxterra.dohy.client.ui.screen.CrateScreen;
import fr.atraxterra.dohy.client.ui.screen.JobDetailScreen;
import fr.atraxterra.dohy.client.ui.screen.JobsScreen;
import fr.atraxterra.dohy.client.ui.screen.MenuScreen;
import fr.atraxterra.dohy.client.ui.screen.ShopScreen;
import fr.atraxterra.dohy.ui.view.CrateView;
import fr.atraxterra.dohy.ui.view.JobDetailView;
import fr.atraxterra.dohy.ui.view.JobsView;
import fr.atraxterra.dohy.ui.view.MenuView;
import fr.atraxterra.dohy.ui.view.ShopView;
import fr.atraxterra.dohy.ui.view.UiNet;
import fr.atraxterra.dohy.ui.view.UiView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
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
        SCREENS.put(CrateView.ID, view -> new CrateScreen((CrateView) view));
        SCREENS.put(fr.atraxterra.dohy.palamod.clicker.ClickerView.ID,
                view -> new fr.atraxterra.dohy.client.ui.screen.ClickerScreen(
                        (fr.atraxterra.dohy.palamod.clicker.ClickerView) view));
        SCREENS.put(fr.atraxterra.dohy.faction.FactionEditView.ID,
                view -> new fr.atraxterra.dohy.client.ui.screen.FactionEditScreen(
                        (fr.atraxterra.dohy.faction.FactionEditView) view));
    }

    private UiClient() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientPayloadHandlersEvent event) {
        event.register(UiNet.Show.TYPE, (payload, context) -> show(payload.view()));
        event.register(UiNet.Close.TYPE, (payload, context) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() instanceof UiScreen) {
                minecraft.gui.setScreen(null);
            }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void show(UiView received) {
        Minecraft minecraft = Minecraft.getInstance();
        UiView view = received instanceof MenuView menu ? menu.map(UiClient::translate)
                : received instanceof JobDetailView detail ? detail.map(UiClient::translate)
                : received instanceof CrateView crate ? crate.map(UiClient::translate) : received;
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

    /** Un texte marque par {@code MenuView.text}, traduit dans la langue du joueur. */
    static String translate(String text) {
        if (text.isEmpty() || text.charAt(0) != MenuView.TRANSLATED) {
            return text;
        }
        try {
            return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(text.substring(1)))
                    .result().map(Component::getString).orElse("");
        } catch (RuntimeException malformed) {
            return "";
        }
    }

    /** Envoie une action au serveur ; sans connexion (captures d'ecran), ne fait rien. */
    public static void send(Identifier view, String verb, String argument, int amount) {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(new UiNet.Action(view, verb, argument, amount));
        }
    }
}

package fr.atraxterra.dohy.client.bcc;

import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.client.ui.screen.MachineKitScreen;
import fr.atraxterra.dohy.faction.bcc.BccBlockEntity;
import fr.atraxterra.dohy.faction.bcc.DoHyBcc;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * L'interface du Base Claim Controller : son coffre de 55 cases, et ce que le
 * bloc surveille (les intrus a 200 blocs), les jours tenus, la gloire et l'ELO.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class BccClient {

    public static final MachineKitScreen.Design DESIGN = new MachineKitScreen.Design(Set.of(),
            (g, menu, mouseX, mouseY) -> paint(g, menu, mouseX, mouseY), Map.of());

    private BccClient() {
    }

    private static void paint(UiGraphics g, MachineMenu menu, int mouseX, int mouseY) {
        int intruders = menu.extra(0);
        g.text(Ui.BODY, "Jour " + menu.extra(1) + "  ·  gloire " + menu.extra(2) + "  ·  +" + menu.extra(3)
                + " ELO demain", 32, 35, 5.0f, Ui.TEXT);
        g.right(Ui.BODY, intruders == 0 ? "Personne a " + BccBlockEntity.WATCH + " blocs"
                        : intruders + " intrus a " + BccBlockEntity.WATCH + " blocs",
                230, 35, 5.0f, intruders == 0 ? Ui.GREEN : Ui.RED);
        if (mouseX >= 150 && mouseX < 232 && mouseY >= 32 && mouseY < 44) {
            g.tooltip(List.of(Component.literal("Les intrus : autres joueurs, soldats et pillards rivaux."),
                    Component.literal("Pille en deux minutes de clic droit sans etre frappe.")));
        }
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.<MachineMenu, MachineKitScreen>register(DoHyBcc.BCC_MENU.get(),
                (menu, inventory, title) -> new MachineKitScreen(menu, inventory, title, DESIGN));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(DoHyBcc.BCC_TYPE.get(), BccRenderer::new);
    }
}

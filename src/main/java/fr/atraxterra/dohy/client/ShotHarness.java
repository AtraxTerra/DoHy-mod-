package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Temporaire (branche ci/shots) : ouvre des ecrans l'un apres l'autre et en
 * prend une capture, puis quitte le jeu. Rien de cela ne part dans le mod.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class ShotHarness {
    private static final Logger LOG = LogUtils.getLogger();

    public record Shot(String name, Supplier<Screen> screen) {
    }

    public static final List<Shot> SHOTS = new ArrayList<>();

    private static int index = -1;
    private static int wait = 0;
    private static int startDelay = 200;
    private static int ticks;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (++ticks % 100 == 0) {
            LOG.info("dohy-shots: etat ecran={} overlay={} joueur={} monde={}",
                    minecraft.gui.screen() == null ? "aucun" : minecraft.gui.screen().getClass().getName(),
                    minecraft.gui.overlay(), minecraft.player != null, minecraft.level != null);
        }
        if (minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.BackupConfirmScreen backup && ticks % 40 == 0) {
            try {
                java.lang.reflect.Field field = net.minecraft.client.gui.screens.BackupConfirmScreen.class
                        .getDeclaredField("onProceed");
                field.setAccessible(true);
                ((net.minecraft.client.gui.screens.BackupConfirmScreen.Listener) field.get(backup)).proceed(false, false);
                LOG.info("dohy-shots: sauvegarde passee");
            } catch (ReflectiveOperationException e) {
                LOG.error("dohy-shots: sauvegarde", e);
            }
        }
        if (ticks > 6000) {
            LOG.info("dohy-shots: abandon");
            minecraft.stop();
            return;
        }
        if (minecraft.gui.overlay() != null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (startDelay > 0) {
            startDelay--;
            return;
        }
        if (index == -1) {
            ShotSamples.register();
            SHOTS.add(0, new Shot("00_world", () -> null));
            LOG.info("dohy-shots: {} captures", SHOTS.size());
            index = 0;
            open(minecraft);
            return;
        }
        if (--wait > 0) {
            return;
        }
        if (wait == 0) {
            Shot shot = SHOTS.get(index);
            Screenshot.grab(minecraft.gameDirectory, shot.name() + ".png", minecraft.gameRenderer.mainRenderTarget(), 1,
                    message -> LOG.info("dohy-shots: {} -> {}", shot.name(), message.getString()));
            return;
        }
        if (wait < -10) {
            index++;
            if (index >= SHOTS.size()) {
                LOG.info("dohy-shots: fin");
                minecraft.stop();
                return;
            }
            open(minecraft);
        }
    }

    private static void open(Minecraft minecraft) {
        Shot shot = SHOTS.get(index);
        try {
            Screen screen = shot.screen().get();
            if (screen != null && screen != minecraft.gui.screen()) {
                minecraft.gui.setScreen(screen);
            }
            LOG.info("dohy-shots: ouvert {}", shot.name());
        } catch (Throwable t) {
            LOG.error("dohy-shots: ECHEC " + shot.name(), t);
        }
        wait = 30;
    }
}

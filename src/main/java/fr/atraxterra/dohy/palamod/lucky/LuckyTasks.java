package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Les evenements qui durent : une pluie de minerais, une tour qui monte, un
 * cochon qui explose dix secondes plus tard.
 *
 * <p>PalaMod lancait un fil par evenement ({@code new Thread} et
 * {@code Thread.sleep}), qui touchait au monde hors du fil du serveur. Ici,
 * chaque etape tourne au tic du serveur. Une tache s'arrete quand son joueur
 * quitte le jeu ou meurt : la pluie ne tombe pas sur un joueur absent.
 */
@EventBusSubscriber
public final class LuckyTasks {

    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> ADDED = new ArrayList<>();

    private LuckyTasks() {
    }

    /**
     * Repete {@code step} {@code times} fois : la premiere apres {@code delay}
     * tics, puis tous les {@code period} tics. {@code step} recoit le numero de
     * l'etape, a partir de 0.
     */
    public static void repeat(ServerPlayer owner, int delay, int period, int times, IntConsumer step) {
        synchronized (ADDED) {
            ADDED.add(new Task(owner, Math.max(0, delay), Math.max(1, period), Math.max(1, times), step));
        }
    }

    /** Une seule etape, apres {@code delay} tics. */
    public static void later(ServerPlayer owner, int delay, Runnable action) {
        repeat(owner, delay, 1, 1, index -> action.run());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        synchronized (ADDED) {
            TASKS.addAll(ADDED);
            ADDED.clear();
        }
        if (TASKS.isEmpty()) {
            return;
        }
        TASKS.removeIf(task -> !task.tick());
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        synchronized (ADDED) {
            ADDED.clear();
        }
        TASKS.clear();
    }

    private static final class Task {
        private final ServerPlayer owner;
        private final int period;
        private final int times;
        private final IntConsumer step;
        private int wait;
        private int done;

        Task(ServerPlayer owner, int delay, int period, int times, IntConsumer step) {
            this.owner = owner;
            this.wait = delay;
            this.period = period;
            this.times = times;
            this.step = step;
        }

        /** Faux quand la tache est finie. */
        boolean tick() {
            if (owner != null && (owner.isRemoved() || owner.hasDisconnected())) {
                return false;
            }
            if (wait > 0) {
                wait--;
                return true;
            }
            try {
                step.accept(done);
            } catch (RuntimeException failure) {
                LOGGER.warn("[dohy/lucky] etape d'evenement en echec", failure);
                return false;
            }
            done++;
            wait = period - 1;
            return done < times;
        }
    }
}

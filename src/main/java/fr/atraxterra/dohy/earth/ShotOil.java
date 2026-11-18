package fr.atraxterra.dohy.earth;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/**
 * Temporaire, pour l'essai de migration : {@code /dohyshots load|unload|log}
 * charge de force trois champs de petrole (le serveur les sert sans joueur),
 * puis note ce que le Diesel Generators en place lit pour chacun de leurs chunks.
 */
@EventBusSubscriber
final class ShotOil {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String[] FIELDS = {"ghawar", "permian", "samotlor"};
    private static final int RADIUS = 2;
    private static final String CLASS = "com.jesz.createdieselgenerators.world.OilChunksSavedData";

    private ShotOil() {
    }

    @SubscribeEvent
    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dohyshots")
                .then(Commands.literal("load").executes(context -> force(context.getSource().getServer(), true)))
                .then(Commands.literal("unload").executes(context -> force(context.getSource().getServer(), false)))
                .then(Commands.literal("log").executes(context -> log(context.getSource().getServer()))));
    }

    private static EarthBodies.Body body(String id) {
        for (EarthBodies.Body body : EarthBodies.all()) {
            if (body.id().equals(id)) {
                return body;
            }
        }
        return null;
    }

    private static int force(MinecraftServer server, boolean on) {
        ServerLevel level = server.overworld();
        for (String id : FIELDS) {
            EarthBodies.Body body = body(id);
            if (body == null) {
                LOG.info("dohy-shots: [champ] {} absent", id);
                continue;
            }
            int cx = (int) Math.floor(body.centerX()) >> 4;
            int cz = (int) Math.floor(body.centerZ()) >> 4;
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    level.setChunkForced(cx + dx, cz + dz, on);
                }
            }
            LOG.info("dohy-shots: [champ] {} ({}) centre {} {}, chunk {} {}, oil {} : {}", id, body.name(),
                    (int) body.centerX(), (int) body.centerZ(), cx, cz, body.oil(), on ? "charge" : "relache");
        }
        return 1;
    }

    private static int log(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Method get = null;
        try {
            Class<?> type = Class.forName(CLASS);
            LOG.info("dohy-shots: [cdg] methodes : {}", Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> Modifier.isPublic(method.getModifiers()))
                    .map(method -> (Modifier.isStatic(method.getModifiers()) ? "static " : "") + method.getName()
                            + Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName)
                            .collect(Collectors.joining(",", "(", ")")))
                    .sorted().collect(Collectors.joining(" ; ")));
            get = type.getMethod("getChunkOilAmount", ServerLevel.class, ChunkPos.class);
        } catch (Throwable error) {
            LOG.info("dohy-shots: [cdg] lecture statique absente : {}", error.toString());
        }
        for (String id : FIELDS) {
            EarthBodies.Body body = body(id);
            if (body == null) {
                continue;
            }
            int cx = (int) Math.floor(body.centerX()) >> 4;
            int cz = (int) Math.floor(body.centerZ()) >> 4;
            int served = 0;
            int loaded = 0;
            int oily = 0;
            long min = Long.MAX_VALUE;
            long max = 0L;
            StringBuilder row = new StringBuilder();
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                    if (chunk != null) {
                        loaded++;
                        if (chunk.getData(DoHyEarth.GROUND.get()) > 0) {
                            served++;
                        }
                    }
                    int amount = -1;
                    if (get != null) {
                        try {
                            amount = (int) get.invoke(null, level, new ChunkPos(cx + dx, cz + dz));
                        } catch (Throwable error) {
                            LOG.info("dohy-shots: [cdg] lecture en echec : {}", error.toString());
                        }
                    }
                    if (amount > 0) {
                        oily++;
                    }
                    min = Math.min(min, amount);
                    max = Math.max(max, amount);
                    row.append(amount < 0 ? "?" : amount / 1000).append(dx == RADIUS ? " | " : " ");
                }
            }
            LOG.info("dohy-shots: [champ] {} : {} charges, {} servis ; petrole {} chunks sur 25, de {} a {} mB ;"
                    + " en milliers de mB par ligne nord-sud : {}", id, loaded, served, oily, min, max, row);
        }
        return 1;
    }
}

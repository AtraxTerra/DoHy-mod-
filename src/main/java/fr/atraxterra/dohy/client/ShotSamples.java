package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.mine.MineService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

/** Temporaire : les salles a spawner de la Mine, comptees autour de l'arrivee puis visitees. */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();

    /** 29 x 29 chunks autour de l'arrivee. */
    private static final int RADIUS = 14;

    private static final String[] TYPES = {"zombie", "skeleton", "spider", "cave_spider", "creeper", "witch",
            "magma_cube", "blaze", "cow", "pig", "sheep", "chicken", "rabbit"};

    private static final Map<String, BlockPos> ROOMS = new TreeMap<>();
    private static volatile boolean scanned;
    private static BlockPos last;
    private static String lastType;

    private ShotSamples() {
    }

    private static ShotHarness.Shot server(String name, int extra, boolean waitScan, Consumer<ServerPlayer> action) {
        return new ShotHarness.Shot(name, () -> {
            ShotHarness.extraWait = extra;
            if (waitScan) {
                ShotHarness.until = () -> scanned;
            }
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                try {
                    action.accept(server.getPlayerList().getPlayers().get(0));
                } catch (Throwable t) {
                    LOG.error("dohy-shots: ECHEC serveur " + name, t);
                    scanned = true;
                }
            });
            return null;
        });
    }

    private static void command(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    /** Une commande tapee par le joueur lui-meme. */
    private static void as(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    private static String typeOf(ServerLevel level, SpawnerBlockEntity spawner) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        spawner.getSpawner().save(output);
        CompoundTag data = output.buildResult();
        return data.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity").getStringOr("id", "?");
    }

    private static void scan(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        LOG.info("dohy-shots: scan dans {} autour de {}", level.dimension(), player.blockPosition().toShortString());
        ChunkPos origin = player.chunkPosition();
        Map<String, Integer> counts = new TreeMap<>();
        int chunks = 0;
        int near = 0;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        long start = System.currentTimeMillis();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                LevelChunk chunk = level.getChunk(origin.x() + dx, origin.z() + dz);
                chunks++;
                for (BlockEntity entity : List.copyOf(chunk.getBlockEntities().values())) {
                    if (!(entity instanceof SpawnerBlockEntity spawner)) {
                        continue;
                    }
                    BlockPos pos = entity.getBlockPos().immutable();
                    String type = typeOf(level, spawner);
                    counts.merge(type, 1, Integer::sum);
                    ROOMS.putIfAbsent(type, pos);
                    minY = Math.min(minY, pos.getY());
                    maxY = Math.max(maxY, pos.getY());
                    if (Math.abs(pos.getX() - player.getBlockX()) <= 24 && Math.abs(pos.getZ() - player.getBlockZ()) <= 24) {
                        near++;
                    }
                    LOG.info("dohy-shots: salle {} a {}", type, pos.toShortString());
                }
            }
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        LOG.info("dohy-shots: BILAN {} chunks en {} ms : {} salles, une pour {} chunks, y {} a {}, {} pres de l'arrivee ; {}",
                chunks, System.currentTimeMillis() - start, total,
                total == 0 ? "-" : String.format(Locale.ROOT, "%.2f", (double) chunks / total), minY, maxY, near, counts);
        scanned = true;
    }

    private static void visit(ServerPlayer player, String type) {
        report(player);
        BlockPos pos = ROOMS.get(type);
        if (pos == null) {
            LOG.info("dohy-shots: aucune salle {}", type);
            return;
        }
        // Au bord nord de la salle, face au spawner et au fond.
        command(player, String.format(Locale.ROOT, "execute in dohy:mine run tp @a %.1f %d %.1f 0 28", pos.getX() + 0.5d,
                pos.getY(), pos.getZ() - 2.5d));
        last = pos;
        lastType = type;
    }

    /** Les creatures de la salle visitee juste avant. */
    private static void report(ServerPlayer player) {
        if (last == null) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        List<Entity> inside = level.getEntities((Entity) null, new AABB(last).inflate(4.0d),
                entity -> !(entity instanceof ServerPlayer));
        LOG.info("dohy-shots: salle {} : {} creature(s) {}", lastType, inside.size(),
                inside.stream().map(entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath() + "/"
                        + (entity instanceof net.minecraft.world.entity.Mob mob ? mob.getSpawnType() : "-")
                        + (entity.getTags().contains("dohy.spawner") ? "+marque" : "")).toList());
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_mine", 200, false, player -> {
            command(player, "difficulty normal");
            player.setGameMode(GameType.CREATIVE);
            MineService.enter(player);
        }));
        ShotHarness.SHOTS.add(server("a1_bilan", 60, true, player -> {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1_000_000, 0, false, false));
            scan(player);
        }));
        for (String type : TYPES) {
            ShotHarness.SHOTS.add(server("b_" + type, 240, false, player -> visit(player, "minecraft:" + type)));
        }
        ShotHarness.SHOTS.add(server("z_fin", 20, false, ShotSamples::report));
    }
}

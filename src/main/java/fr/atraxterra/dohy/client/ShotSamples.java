package fr.atraxterra.dohy.client;

import com.mojang.logging.LogUtils;
import fr.atraxterra.dohy.earth.DoHyEarth;
import fr.atraxterra.dohy.earth.EarthBodies;
import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.earth.EarthDeposits;
import fr.atraxterra.dohy.mine.MineService;
import fr.atraxterra.dohy.spawn.SpawnApi;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/** Temporaire : Berskar avec Create Fly et le port de Diesel Generators, sur la grille de la Terre. */
final class ShotSamples {
    private static final Logger LOG = LogUtils.getLogger();
    private static EarthBodies.Body current;
    private static Method oilGet;

    private ShotSamples() {
    }

    private static ShotHarness.Shot server(String name, int extra, Consumer<ServerPlayer> action) {
        return new ShotHarness.Shot(name, () -> {
            ShotHarness.extraWait = extra;
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                try {
                    action.accept(server.getPlayerList().getPlayers().get(0));
                } catch (Throwable t) {
                    LOG.error("dohy-shots: ECHEC serveur " + name, t);
                }
            });
            return null;
        });
    }

    private static void command(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "execute in minecraft:overworld run " + command);
    }

    private static EarthBodies.Body body(String id) {
        for (EarthBodies.Body body : EarthBodies.all()) {
            if (body.id().equals(id)) {
                return body;
            }
        }
        return null;
    }

    /** Le premier de ces gisements dont le centre est a terre dans le monde de test. */
    private static EarthBodies.Body onLand(ServerLevel level, String... ids) {
        for (String id : ids) {
            EarthBodies.Body body = body(id);
            if (body == null) {
                continue;
            }
            int x = (int) body.centerX();
            int z = (int) body.centerZ();
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            boolean wet = !level.getFluidState(new BlockPos(x, y - 1, z)).isEmpty();
            LOG.info("dohy-shots: [gisement] {} en {} {} {} : {}", id, x, y, z, wet ? "sous l'eau" : "a terre");
            if (!wet) {
                return body;
            }
        }
        return body(ids[0]);
    }

    private static void go(ServerPlayer player, EarthBodies.Body body) {
        current = body;
        command(player, "tp " + player.getScoreboardName() + " " + (int) body.centerX() + " 200 "
                + (int) body.centerZ());
    }

    private static int floor(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
    }

    private static boolean wanted(String name) {
        return name.contains("ore") || name.contains("raw_") || name.startsWith("create:");
    }

    /** Ce qu'il y a autour du centre d'un gisement, par bloc (minerais et pierres de Create). */
    private static String census(ServerLevel level, EarthBodies.Body body, int radius) {
        Map<String, Integer> counts = new TreeMap<>();
        int cx = (int) body.centerX();
        int cz = (int) body.centerZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                int top = floor(level, x, z);
                for (int y = level.getMinY(); y <= top; y++) {
                    String name = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.set(x, y, z)).getBlock())
                            .toString();
                    if (wanted(name)) {
                        counts.merge(name, 1, Integer::sum);
                    }
                }
            }
        }
        return counts.toString();
    }

    /** Les chunks d'un carre de (2r+1)^2 autour d'un chunk : minerais, pierres de Create, petrole, service. */
    private static String chunks(ServerLevel level, int centerX, int centerZ, int r, boolean oil) {
        Map<String, Integer> counts = new TreeMap<>();
        int served = 0;
        int chunks = 0;
        long oilMin = Long.MAX_VALUE;
        long oilMax = 0L;
        int oily = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = centerX - r; cx <= centerX + r; cx++) {
            for (int cz = centerZ - r; cz <= centerZ + r; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                chunks++;
                if (chunk.getData(DoHyEarth.GROUND.get()) > 0) {
                    served++;
                }
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int top = Math.min(level.getMaxY(), floor(level, (cx << 4) + lx, (cz << 4) + lz));
                        for (int y = level.getMinY(); y <= top; y++) {
                            BlockState state = chunk.getBlockState(pos.set((cx << 4) + lx, y, (cz << 4) + lz));
                            String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                            if (wanted(name)) {
                                counts.merge(name, 1, Integer::sum);
                            }
                        }
                    }
                }
                if (oil) {
                    int amount = oil(level, new ChunkPos(cx, cz));
                    oilMin = Math.min(oilMin, amount);
                    oilMax = Math.max(oilMax, amount);
                    if (amount > 0) {
                        oily++;
                    }
                }
            }
        }
        String text = chunks + " chunks, " + served + " servis ; " + counts;
        return oil ? text + " ; petrole : " + oily + " chunks, de " + oilMin + " a " + oilMax + " mB" : text;
    }

    /** Le petrole d'un chunk pour la pompe du Diesel Generators, lu dans le mod lui-meme. */
    private static int oil(ServerLevel level, ChunkPos chunk) {
        try {
            if (oilGet == null) {
                oilGet = Class.forName("com.jesz.createdieselgenerators.world.OilChunksSavedData")
                        .getMethod("getChunkOilAmount", ServerLevel.class, ChunkPos.class);
            }
            return (int) oilGet.invoke(null, level, chunk);
        } catch (Throwable t) {
            LOG.error("dohy-shots: petrole illisible", t);
            return -1;
        }
    }

    /** Ouvre la moitie nord du gisement jusqu'au ciel, et place la camera dans la fosse, face a la coupe. */
    private static void cut(ServerPlayer player, EarthBodies.Body body, int half, int bottom) {
        ServerLevel level = player.level().getServer().overworld();
        int cx = (int) body.centerX();
        int cz = (int) body.centerZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = cz - half; z < cz; z++) {
                int roof = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 2;
                for (int y = bottom; y <= roof; y++) {
                    level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        int eye = bottom + Math.max(6, half / 3);
        command(player, "tp " + player.getScoreboardName() + " " + cx + " " + eye + " " + (cz - half + 1)
                + " facing " + cx + " " + (bottom + 4) + " " + cz);
    }

    private static int centerY(ServerLevel level, EarthBodies.Body body) {
        int top = floor(level, (int) body.centerX(), (int) body.centerZ());
        return body.surface() > 0 ? top - body.surface() : Math.min(body.y(), top - body.ry() - 4);
    }

    private static void say(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GOLD));
        LOG.info("dohy-shots: {}", text);
    }

    static void register() {
        ShotHarness.SHOTS.add(server("a0_depart", 100, player -> {
            ShotHarness.until = () -> !SpawnApi.building();
            command(player, "time set noon");
            command(player, "gamerule advance_time false");
            command(player, "gamerule advance_weather false");
            command(player, "gamemode spectator @a");
            for (String id : new String[]{"create", "createdieselgenerators", "hbm", "hbmspace", "utilitydrawers",
                    "dohy"}) {
                LOG.info("dohy-shots: [mod] {} {}", id, ModList.get().getModContainerById(id)
                        .map(container -> container.getModInfo().getVersion().toString()).orElse("ABSENT"));
            }
            for (String id : new String[]{"create:zinc_ore", "create:deepslate_zinc_ore", "create:raw_zinc",
                    "create:zinc_ingot", "create:asurine", "create:crimsite", "createdieselgenerators:pumpjack_head"}) {
                LOG.info("dohy-shots: [id] {} {}", id, BuiltInRegistries.ITEM.containsKey(
                        net.minecraft.resources.Identifier.parse(id)));
            }
            // les champs servis par Create: Adapted en phase A, relus par le port avant toute visite
            command(player, "dohyshots log");
            EarthData earth = EarthData.get();
            LOG.info("dohy-shots: [terre] carte {} ; {} gisements geants", earth != null, EarthBodies.all().size());
            for (String code : new String[]{"USA", "IND", "POL", "TCD"}) {
                LOG.info("dohy-shots: [terre] {} : {}", code, EarthDeposits.summary(earth.byCode(code)));
            }
        }));
        ShotHarness.SHOTS.add(server("o1_petrole", 500, player -> {
            ServerLevel level = player.level().getServer().overworld();
            // un champ que la phase A n'a pas servi : son petrole est pose par le port
            go(player, onLand(level, "hassi_messaoud", "daqing", "burgan", "rumaila", "orinoco",
                    "east_texas", "bakken"));
        }));
        ShotHarness.SHOTS.add(server("o2_petrole_chunks", 60, player -> {
            ServerLevel level = player.level().getServer().overworld();
            EarthBodies.Body body = current;
            int cx = (int) body.centerX() >> 4;
            int cz = (int) body.centerZ() >> 4;
            int center = oil(level, new ChunkPos(cx, cz));
            say(player, "Petrole de " + body.name() + " (Diesel Generators) : " + (center == Integer.MAX_VALUE
                    ? "inepuisable" : center + " mB") + " au centre");
            LOG.info("dohy-shots: [petrole] {} : {}", body.name(), chunks(level, cx, cz, 3, true));
        }));
        ShotHarness.SHOTS.add(server("z1_zinc", 500, player -> {
            ServerLevel level = player.level().getServer().overworld();
            go(player, onLand(level, "red_dog", "rampura_agucha", "olkusz", "kidd_creek", "penasquito", "skorpion",
                    "navan"));
        }));
        ShotHarness.SHOTS.add(server("z2_zinc_coupe", 200, player -> {
            ServerLevel level = player.level().getServer().overworld();
            EarthBodies.Body body = current;
            LOG.info("dohy-shots: [zinc] {} : {}", body.name(), census(level, body, 30));
            say(player, "Gisement de zinc : " + body.name());
            int cy = centerY(level, body);
            cut(player, body, 26, cy - body.ry() - 2);
        }));
        ShotHarness.SHOTS.add(server("t1_temoin", 400, player -> {
            EarthData earth = EarthData.get();
            int x = (int) earth.x(18.5d);
            int z = (int) earth.z(15.0d);
            LOG.info("dohy-shots: [temoin] {} en {} {}", earth.countryAt(x, z) == null ? "?" : earth.countryAt(x, z)
                    .code(), x, z);
            command(player, "tp " + player.getScoreboardName() + " " + x + " 200 " + z);
        }));
        ShotHarness.SHOTS.add(server("t2_temoin_compte", 40, player -> {
            ServerLevel level = player.level().getServer().overworld();
            EarthData earth = EarthData.get();
            int x = (int) earth.x(18.5d);
            int z = (int) earth.z(15.0d);
            String text = chunks(level, x >> 4, z >> 4, 2, true);
            LOG.info("dohy-shots: [temoin] Tchad : {}", text);
            say(player, "Tchad, 25 chunks : " + text);
        }));
        // Les deux moteurs de rendu a la fois : celui embarque par Create Fly (arbres, roue dentee
        // qui tournent) et CrankShaft (le coffre, la cloche, le wagonnet, que les deux savent dessiner).
        ShotHarness.SHOTS.add(server("v1_rendu", 200, player -> {
            EarthData earth = EarthData.get();
            int x = (int) earth.x(18.5d) + 24;
            int z = (int) earth.z(15.0d);
            int y = 210;
            command(player, "fill " + (x - 2) + " " + (y - 1) + " " + (z - 1) + " " + (x + 8) + " " + (y - 1) + " "
                    + (z + 4) + " minecraft:smooth_stone");
            command(player, "setblock " + x + " " + y + " " + z + " create:creative_motor[facing=east]");
            for (int i = 1; i <= 3; i++) {
                command(player, "setblock " + (x + i) + " " + y + " " + z + " create:shaft[axis=x]");
            }
            command(player, "setblock " + (x + 4) + " " + y + " " + z + " create:cogwheel[axis=x]");
            command(player, "setblock " + (x + 5) + " " + y + " " + z + " create:shaft[axis=x]");
            command(player, "setblock " + (x + 6) + " " + y + " " + z + " create:large_cogwheel[axis=x]");
            command(player, "setblock " + (x + 1) + " " + y + " " + (z + 2) + " minecraft:chest[facing=north]");
            command(player, "setblock " + (x + 3) + " " + y + " " + (z + 2) + " minecraft:bell[attachment=floor,"
                    + "facing=north]");
            command(player, "setblock " + (x + 5) + " " + y + " " + (z + 2) + " minecraft:rail");
            command(player, "summon minecraft:minecart " + (x + 5) + " " + y + " " + (z + 2));
            command(player, "setblock " + (x + 7) + " " + y + " " + (z + 2) + " minecraft:red_shulker_box[facing=up]");
            command(player, "tp " + player.getScoreboardName() + " " + (x + 3) + " " + (y + 2) + " " + (z - 5)
                    + " facing " + (x + 3) + " " + y + " " + (z + 1));
            say(player, "Create Fly et CrankShaft ensemble");
        }));
        ShotHarness.SHOTS.add(server("m1_mine", 400, MineService::enter));
        ShotHarness.SHOTS.add(server("m2_mine_compte", 40, player -> {
            ServerLevel level = (ServerLevel) player.level();
            BlockPos at = player.blockPosition();
            Map<String, Integer> counts = new TreeMap<>();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int cx = (at.getX() >> 4) - 2; cx <= (at.getX() >> 4) + 2; cx++) {
                for (int cz = (at.getZ() >> 4) - 2; cz <= (at.getZ() >> 4) + 2; cz++) {
                    LevelChunk chunk = level.getChunk(cx, cz);
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                                String name = BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(
                                        pos.set((cx << 4) + lx, y, (cz << 4) + lz)).getBlock()).toString();
                                if (name.startsWith("create:")) {
                                    counts.merge(name, 1, Integer::sum);
                                }
                            }
                        }
                    }
                }
            }
            LOG.info("dohy-shots: [mine] {} en {} : Create {}", level.dimension(), at.toShortString(), counts);
            say(player, "La Mine, 25 chunks, blocs de Create : " + counts);
        }));
    }
}

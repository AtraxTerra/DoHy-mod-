package fr.atraxterra.dohy.client.atlas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthAtlas;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * L'atlas de DoHy : une minimap, une grande carte (touche M), des points de
 * passage, et sur la carte de la Terre le globe d'avance et les calques de
 * DoHy (pays tenus, gisements geants, petrole, capitale).
 *
 * <p>Tout se passe chez le client : le terrain explore se garde dans
 * {@code dohy_atlas/} du dossier du jeu, monde par monde et dimension par
 * dimension. Le serveur n'envoie que ce que le client ne peut pas voir (le
 * globe, les gisements, qui tient chaque pays).
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class AtlasClient {

    /** Les reglages de l'atlas, gardes entre deux parties. */
    public static final class Options {
        public boolean minimap = true;
        public double minimapScale = 1.0d;
        /** Le cote de la minimap, en pixels d'interface (voir {@link #SIZES}). */
        public int minimapSize = 72;
        public boolean holders = true;
        public boolean marks = true;
        public boolean waypoints = true;
        public boolean markers = true;
    }

    /** Les tailles de minimap proposees par le bouton TAILLE de la grande carte. */
    public static final int[] SIZES = {56, 72, 96, 112};

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static KeyMapping atlasKey;
    private static KeyMapping minimapKey;
    private static KeyMapping zoomInKey;
    private static KeyMapping zoomOutKey;
    private static Options options = new Options();
    private static final AtlasScanner SCANNER = new AtlasScanner();
    private static AtlasMap map;
    private static String world;
    private static boolean dead;
    private static int saveCountdown = 600;

    private AtlasClient() {
    }

    public static Options options() {
        return options;
    }

    static AtlasMap map() {
        return map;
    }

    static KeyMapping atlasKey() {
        return atlasKey;
    }

    static Path root() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("dohy_atlas");
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        atlasKey = new KeyMapping("key.dohy.atlas", InputConstants.KEY_M, KeyMapping.Category.MISC);
        minimapKey = new KeyMapping("key.dohy.minimap", InputConstants.KEY_N, KeyMapping.Category.MISC);
        zoomInKey = new KeyMapping("key.dohy.minimap_zoom_in", InputConstants.KEY_EQUALS, KeyMapping.Category.MISC);
        zoomOutKey = new KeyMapping("key.dohy.minimap_zoom_out", InputConstants.KEY_MINUS, KeyMapping.Category.MISC);
        event.register(atlasKey);
        event.register(minimapKey);
        event.register(zoomInKey);
        event.register(zoomOutKey);
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "atlas"), new AtlasHud());
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(EarthAtlas.Globe.TYPE, (payload, context) -> AtlasData.accept(payload));
        event.register(EarthAtlas.Layers.TYPE, (payload, context) -> AtlasData.accept(payload));
        event.register(EarthAtlas.Nations.TYPE, (payload, context) -> AtlasData.accept(payload));
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        loadOptions();
        world = worldName();
        Waypoints.open(root().resolve(world));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        closeMap();
        Waypoints.close();
        AtlasData.clear();
        AtlasGlobe.release();
        world = null;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || world == null) {
            return;
        }
        String dimension = level.dimension().identifier().toString();
        if (map == null || !map.dimension.equals(safe(dimension))) {
            closeMap();
            map = new AtlasMap(root(), world, safe(dimension));
            SCANNER.clear();
        }
        SCANNER.tick(level, player.getBlockX() >> 4, player.getBlockZ() >> 4,
                Math.min(16, minecraft.options.getEffectiveRenderDistance()), map);
        if (--saveCountdown <= 0) {
            saveCountdown = 600;
            map.save(-1);
        }
        while (atlasKey.consumeClick()) {
            minecraft.gui.setScreen(new AtlasScreen());
        }
        while (minimapKey.consumeClick()) {
            options.minimap = !options.minimap;
            saveOptions();
        }
        while (zoomInKey.consumeClick()) {
            options.minimapScale = Math.max(0.25d, options.minimapScale / 2.0d);
            saveOptions();
        }
        while (zoomOutKey.consumeClick()) {
            options.minimapScale = Math.min(16.0d, options.minimapScale * 2.0d);
            saveOptions();
        }
        if (player.isDeadOrDying()) {
            if (!dead) {
                dead = true;
                Waypoints.death(player.getBlockX(), player.getBlockY(), player.getBlockZ(), dimension);
            }
        } else {
            dead = false;
        }
    }

    private static void closeMap() {
        if (map != null) {
            map.close();
            map = null;
        }
    }

    /** Le dossier de la partie en solo, l'adresse du serveur sinon. */
    private static String worldName() {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            Path path = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            return safe("solo_" + path.getFileName());
        }
        ServerData data = minecraft.getCurrentServer();
        return safe("serveur_" + (data == null ? "inconnu" : data.ip));
    }

    static String safe(String text) {
        return text.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static Path optionsFile() {
        return root().resolve("options.json");
    }

    private static void loadOptions() {
        Path file = optionsFile();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Options loaded = GSON.fromJson(reader, Options.class);
            if (loaded != null) {
                options = loaded;
            }
        } catch (IOException | RuntimeException error) {
            DoHy.LOGGER.warn("Atlas : reglages illisibles ({})", error.toString());
        }
    }

    static void saveOptions() {
        try {
            Files.createDirectories(root());
            try (Writer writer = Files.newBufferedWriter(optionsFile(), StandardCharsets.UTF_8)) {
                GSON.toJson(options, writer);
            }
        } catch (IOException | RuntimeException error) {
            DoHy.LOGGER.warn("Atlas : reglages non sauves ({})", error.toString());
        }
    }
}

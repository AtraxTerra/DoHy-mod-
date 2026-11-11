package fr.atraxterra.dohy.palamod.box;

import com.google.gson.JsonObject;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Les reperes des ouvertures, en ticks : la duree de l'animation, le moment
 * ou la box montre son lot ({@code open}) et celui ou elle se referme
 * ({@code close}). Ils viennent des fichiers d'animation de PalaShop, par
 * {@code tools/rpg_entities.py} ({@code data/palarpg/boxes.json}) : le
 * serveur n'a pas les animations, seulement ces reperes.
 */
@EventBusSubscriber
public final class BoxTimings {

    record Clip(int length, int open, int close) {
    }

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath("palarpg", "boxes.json");
    /** Si le fichier manque : l'ouverture commune de PalaShop. */
    private static final Clip FALLBACK = new Clip(140, 18, 125);

    private static Map<String, Clip> clips;

    private BoxTimings() {
    }

    /** Un autre monde, un autre jeu de donnees : on relit. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        clips = null;
    }

    static Clip of(MinecraftServer server, BoxKind kind, String rarity) {
        if (clips == null) {
            clips = read(server);
        }
        return clips.getOrDefault(kind.getSerializedName() + "/" + rarity, FALLBACK);
    }

    private static Map<String, Clip> read(MinecraftServer server) {
        Map<String, Clip> found = new HashMap<>();
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            DoHy.LOGGER.warn("data/palarpg/boxes.json est absent : ouvertures de box par defaut");
            return found;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            JsonObject boxes = GsonHelper.getAsJsonObject(GsonHelper.parse(reader), "boxes");
            for (String box : boxes.keySet()) {
                JsonObject rarities = boxes.getAsJsonObject(box);
                for (String rarity : rarities.keySet()) {
                    JsonObject clip = rarities.getAsJsonObject(rarity);
                    found.put(box + "/" + rarity, new Clip(ticks(clip, "length"), ticks(clip, "open"),
                            ticks(clip, "close")));
                }
            }
        } catch (Exception error) {
            DoHy.LOGGER.error("Lecture de data/palarpg/boxes.json", error);
        }
        return found;
    }

    private static int ticks(JsonObject clip, String key) {
        return Math.round(GsonHelper.getAsFloat(clip, key) * 20.0f);
    }
}

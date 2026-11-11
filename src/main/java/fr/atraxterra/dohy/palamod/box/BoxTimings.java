package fr.atraxterra.dohy.palamod.box;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.atraxterra.dohy.DoHy;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Les reperes des ouvertures, en ticks : la duree de l'animation, le moment
 * ou la box montre son lot ({@code open}) et celui ou elle se referme
 * ({@code close}). Ils viennent des fichiers d'animation de PalaShop, par
 * {@code tools/rpg_entities.py} ({@code data/palarpg/boxes.json}) : le
 * serveur n'a pas les animations, seulement ces reperes.
 */
final class BoxTimings {

    record Clip(int length, int open, int close) {
    }

    /** Si le fichier manque : l'ouverture commune de PalaShop. */
    private static final Clip FALLBACK = new Clip(140, 18, 125);

    private static Map<String, Clip> clips;

    private BoxTimings() {
    }

    static Clip of(BoxKind kind, String rarity) {
        if (clips == null) {
            clips = read();
        }
        return clips.getOrDefault(kind.getSerializedName() + "/" + rarity, FALLBACK);
    }

    private static Map<String, Clip> read() {
        Map<String, Clip> found = new HashMap<>();
        try (InputStream stream = BoxTimings.class.getResourceAsStream("/data/palarpg/boxes.json")) {
            if (stream == null) {
                DoHy.LOGGER.warn("data/palarpg/boxes.json est absent : ouvertures de box par defaut");
                return found;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject boxes = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("boxes");
                for (String box : boxes.keySet()) {
                    JsonObject rarities = boxes.getAsJsonObject(box);
                    for (String rarity : rarities.keySet()) {
                        JsonObject clip = rarities.getAsJsonObject(rarity);
                        found.put(box + "/" + rarity, new Clip(ticks(clip, "length"), ticks(clip, "open"),
                                ticks(clip, "close")));
                    }
                }
            }
        } catch (Exception error) {
            DoHy.LOGGER.error("Lecture de data/palarpg/boxes.json", error);
        }
        return found;
    }

    private static int ticks(JsonObject clip, String key) {
        return Math.round(clip.get(key).getAsFloat() * 20.0f);
    }
}

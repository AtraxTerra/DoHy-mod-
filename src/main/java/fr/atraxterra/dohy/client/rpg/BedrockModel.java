package fr.atraxterra.dohy.client.rpg;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Un modele Bedrock ({@code minecraft:geometry}) et ses animations, pour les
 * blocs et les objets : le moteur des monstres de donjon ({@link RpgGeometry},
 * {@link RpgEntityModel}, les animations normalisees par
 * {@code tools/rpg_entities.py}), ouvert au reste du mod.
 *
 * <p>Le modele est partage par tous ceux qui l'affichent : sa pose se refait a
 * chaque dessin, dans le rappel de {@code submitCustomGeometry} —
 * {@link #resetPose()}, puis {@link #animate}, {@link #setVisible}, puis
 * {@link #draw}.
 */
public final class BedrockModel {

    /**
     * L'encombrement des boites du fichier, en pixels, dans le repere Bedrock (y vers le
     * haut, sol a 0) ; sans les rotations ni le gonflement : de quoi mettre le modele a
     * l'echelle d'une place, pas une mesure exacte.
     */
    public record Bounds(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
        public float width() {
            return Math.max(1.0f, maxX - minX);
        }

        public float height() {
            return Math.max(1.0f, maxY - minY);
        }

        public float length() {
            return Math.max(1.0f, maxZ - minZ);
        }

        public float centerX() {
            return (minX + maxX) / 2.0f;
        }

        public float centerZ() {
            return (minZ + maxZ) / 2.0f;
        }
    }

    private final RpgEntityModel model;
    private final RpgModels.Named clips;
    private final Map<String, ModelPart> parts;
    private final Bounds bounds;

    private BedrockModel(RpgEntityModel model, RpgModels.Named clips, Map<String, ModelPart> parts, Bounds bounds) {
        this.model = model;
        this.clips = clips;
        this.parts = parts;
        this.bounds = bounds;
    }

    /**
     * Lit la geometrie, et ses animations si {@code animations} n'est pas nul.
     * Rend {@code null} (et le note) si le fichier manque ou ne se lit pas.
     */
    public static BedrockModel load(Identifier geometry, Identifier animations) {
        try {
            JsonObject file = RpgModels.read(geometry);
            if (file == null) {
                DoHy.LOGGER.warn("Modele Bedrock absent : {}", geometry);
                return null;
            }
            RpgGeometry.Built built = RpgGeometry.build(file, Set.of());
            JsonObject clipsFile = animations == null ? null : RpgModels.read(animations);
            return new BedrockModel(new RpgEntityModel(built),
                    RpgModels.named(clipsFile == null ? new JsonObject() : clipsFile, built), index(file, built),
                    bounds(file));
        } catch (Exception error) {
            DoHy.LOGGER.error("Modele Bedrock illisible : {}", geometry, error);
            return null;
        }
    }

    /** Chaque os par son nom, en suivant la chaine de ses parents depuis la racine. */
    private static Map<String, ModelPart> index(JsonObject file, RpgGeometry.Built built) {
        JsonObject geometry = GsonHelper.getAsJsonArray(file, "minecraft:geometry").get(0).getAsJsonObject();
        Map<String, String> parents = new HashMap<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(geometry, "bones", new JsonArray())) {
            JsonObject bone = element.getAsJsonObject();
            parents.put(GsonHelper.getAsString(bone, "name"), GsonHelper.getAsString(bone, "parent", ""));
        }
        Map<String, ModelPart> parts = new HashMap<>();
        for (String name : parents.keySet()) {
            Deque<String> chain = new ArrayDeque<>();
            String current = name;
            while (current != null && !current.isEmpty() && parents.containsKey(current) && chain.size() <= parents.size()) {
                chain.push(current);
                current = parents.get(current);
            }
            ModelPart part = built.root();
            try {
                for (String link : chain) {
                    part = part.getChild(link);
                }
                parts.put(name, part);
            } catch (NoSuchElementException missing) {
                // Un os sans boite ni enfant peut manquer : rien a montrer ni a cacher.
            }
        }
        return parts;
    }

    private static Bounds bounds(JsonObject file) {
        JsonObject geometry = GsonHelper.getAsJsonArray(file, "minecraft:geometry").get(0).getAsJsonObject();
        float[] low = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] high = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (JsonElement element : GsonHelper.getAsJsonArray(geometry, "bones", new JsonArray())) {
            for (JsonElement cube : GsonHelper.getAsJsonArray(element.getAsJsonObject(), "cubes", new JsonArray())) {
                JsonArray origin = GsonHelper.getAsJsonArray(cube.getAsJsonObject(), "origin", new JsonArray());
                JsonArray size = GsonHelper.getAsJsonArray(cube.getAsJsonObject(), "size", new JsonArray());
                if (origin.size() < 3 || size.size() < 3) {
                    continue;
                }
                for (int axis = 0; axis < 3; axis++) {
                    float from = origin.get(axis).getAsFloat();
                    float to = from + size.get(axis).getAsFloat();
                    low[axis] = Math.min(low[axis], Math.min(from, to));
                    high[axis] = Math.max(high[axis], Math.max(from, to));
                }
            }
        }
        if (low[0] > high[0]) {
            return new Bounds(-8.0f, 8.0f, 0.0f, 16.0f, -8.0f, 8.0f);
        }
        return new Bounds(low[0], high[0], low[1], high[1], low[2], high[2]);
    }

    public Bounds bounds() {
        return bounds;
    }

    public RenderType renderType(Identifier texture) {
        return model.renderType(texture);
    }

    /** Remet la pose de repos ; a faire avant chaque dessin. */
    public void resetPose() {
        model.resetPose();
    }

    /**
     * Pose le modele a {@code seconds} dans l'animation {@code name} (bouclee ou
     * tenue sur sa derniere image, comme le dit son fichier). Faux si le modele
     * ne l'a pas.
     */
    public boolean animate(String name, float seconds) {
        return animate(name, seconds, 1.0f);
    }

    /**
     * Comme {@link #animate(String, float)}, au poids {@code weight} : les animations de
     * Paladium posent le corps entier, on en melange plusieurs dont les poids font 1.
     */
    public boolean animate(String name, float seconds, float weight) {
        RpgModels.Clip clip = clips.get(name);
        if (clip == null) {
            return false;
        }
        clip.animation().apply((long) (Math.max(0.0f, seconds) * 1000.0f), weight);
        return true;
    }

    /** La duree de l'animation, en secondes ; 0 si le modele ne l'a pas. */
    public float length(String name) {
        RpgModels.Clip clip = clips.get(name);
        return clip == null ? 0.0f : clip.length();
    }

    /** Montre ou cache un os et ses enfants ; sans effet si le modele ne l'a pas. */
    public void setVisible(String bone, boolean visible) {
        ModelPart part = parts.get(bone);
        if (part != null) {
            part.visible = visible;
        }
    }

    /** Cache ou montre tous les os dont le nom commence par {@code prefix}. */
    public void setVisibleByPrefix(String prefix, boolean visible) {
        for (Map.Entry<String, ModelPart> entry : parts.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                entry.getValue().visible = visible;
            }
        }
    }

    public void draw(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay) {
        model.draw(pose, buffer, light, overlay);
    }
}

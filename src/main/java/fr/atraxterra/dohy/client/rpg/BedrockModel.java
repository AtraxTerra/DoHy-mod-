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

    private final RpgEntityModel model;
    private final RpgModels.Named clips;
    private final Map<String, ModelPart> parts;

    private BedrockModel(RpgEntityModel model, RpgModels.Named clips, Map<String, ModelPart> parts) {
        this.model = model;
        this.clips = clips;
        this.parts = parts;
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
                    RpgModels.named(clipsFile == null ? new JsonObject() : clipsFile, built), index(file, built));
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
        RpgModels.Clip clip = clips.get(name);
        if (clip == null) {
            return false;
        }
        clip.animation().apply((long) (Math.max(0.0f, seconds) * 1000.0f), 1.0f);
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

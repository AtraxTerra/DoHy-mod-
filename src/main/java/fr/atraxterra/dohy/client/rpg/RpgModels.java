package fr.atraxterra.dohy.client.rpg;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les modeles des monstres de donjon ({@code assets/palarpg/entity}), lus a la
 * premiere apparition de chacun et gardes jusqu'au prochain rechargement des
 * ressources. Un modele illisible est note et n'est plus retente : le monstre
 * garde alors son apparence vanilla.
 */
public final class RpgModels {

    private static final String NAMESPACE = "palarpg";
    private static final float RADIANS = (float) Math.PI / 180.0f;

    /** Une animation, sa duree en secondes, et si elle boucle. */
    public record Clip(KeyframeAnimation animation, float length, boolean loop) {
    }

    /** Une texture : une image, ou les images d'une texture animee. */
    public record Texture(Identifier[] frames, int frametime) {
        public Identifier at(float ageInTicks) {
            return frames.length == 1 ? frames[0]
                    : frames[Math.floorMod((int) (ageInTicks / frametime), frames.length)];
        }
    }

    /** Un modele pret a dessiner. Une animation absente est {@code null}. */
    public record Entry(RpgEntityModel model, float height, Map<String, Texture> textures,
                        Clip idle, Clip walk, List<Clip> attacks, Clip hurt, Clip death, Clip spawn,
                        Named named) {
        public Texture texture(String variant) {
            Texture texture = textures.get(variant);
            return texture != null ? texture : textures.get("default");
        }
    }

    /** Les animations du modele par leur nom, cuites a la demande : les poses des objets de decor. */
    public static final class Named {
        private final JsonObject animations;
        private final RpgGeometry.Built built;
        private final Map<String, Clip> clips = new HashMap<>();

        private Named(JsonObject animations, RpgGeometry.Built built) {
            this.animations = animations;
            this.built = built;
        }

        /** L'animation, bouclee ou non comme le dit son fichier ; {@code null} si le modele ne l'a pas. */
        public Clip get(String name) {
            if (!clips.containsKey(name)) {
                boolean loop = animations.has(name)
                        && GsonHelper.getAsBoolean(animations.getAsJsonObject(name), "loop", false);
                clips.put(name, clip(animations, name, built, loop));
            }
            return clips.get(name);
        }
    }

    private static final Map<String, Entry> CACHE = new HashMap<>();
    private static JsonObject index;

    private RpgModels() {
    }

    /** Au rechargement des ressources. */
    public static void clear() {
        CACHE.clear();
        index = null;
    }

    /** Le modele, ou {@code null} s'il n'existe pas ou ne se lit pas. */
    public static Entry get(String model) {
        if (CACHE.containsKey(model)) {
            return CACHE.get(model);
        }
        Entry entry = null;
        try {
            entry = load(model);
        } catch (Exception error) {
            DoHy.LOGGER.error("Modele de monstre illisible : {}", model, error);
        }
        CACHE.put(model, entry);
        return entry;
    }

    private static JsonObject read(String path) throws Exception {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        var resource = manager.getResource(Identifier.fromNamespaceAndPath(NAMESPACE, path));
        if (resource.isEmpty()) {
            return null;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            return GsonHelper.parse(reader);
        }
    }

    private static Entry load(String model) throws Exception {
        if (index == null) {
            index = read("entity/index.json");
            if (index == null) {
                index = new JsonObject();
            }
        }
        JsonObject info = index.getAsJsonObject(model);
        JsonObject geometry = read("entity/" + model + "/model.json");
        if (info == null || geometry == null) {
            return null;
        }
        RpgGeometry.Built built = RpgGeometry.build(geometry);

        Map<String, Texture> textures = new HashMap<>();
        for (var texture : GsonHelper.getAsJsonObject(info, "textures").entrySet()) {
            JsonObject body = texture.getValue().getAsJsonObject();
            int frames = GsonHelper.getAsInt(body, "frames", 1);
            Identifier[] files = new Identifier[frames];
            for (int i = 0; i < frames; i++) {
                files[i] = Identifier.fromNamespaceAndPath(NAMESPACE, "textures/entity/" + model + "/"
                        + texture.getKey() + (frames == 1 ? "" : "_" + i) + ".png");
            }
            textures.put(texture.getKey(), new Texture(files, Math.max(1, GsonHelper.getAsInt(body, "frametime", 1))));
        }
        if (!textures.containsKey("default")) {
            return null;
        }

        JsonObject animations = read("entity/" + model + "/animation.json");
        if (animations == null) {
            animations = new JsonObject();
        }
        JsonObject roles = GsonHelper.getAsJsonObject(info, "roles");
        List<Clip> attacks = new ArrayList<>();
        for (JsonElement name : GsonHelper.getAsJsonArray(roles, "attacks", new JsonArray())) {
            Clip clip = clip(animations, name.getAsString(), built, false);
            if (clip != null) {
                attacks.add(clip);
            }
        }
        return new Entry(new RpgEntityModel(built), built.height(), textures,
                clip(animations, role(roles, "idle"), built, true),
                clip(animations, role(roles, "walk"), built, true),
                attacks,
                clip(animations, role(roles, "hurt"), built, false),
                clip(animations, role(roles, "death"), built, false),
                clip(animations, role(roles, "spawn"), built, false),
                new Named(animations, built));
    }

    private static String role(JsonObject roles, String key) {
        JsonElement name = roles.get(key);
        return name == null || name.isJsonNull() ? null : name.getAsString();
    }

    /**
     * Une animation du fichier, cuite sur le modele. Les os qu'elle anime et
     * que le modele n'a pas sont ignores ({@code bake} les refuserait).
     */
    private static Clip clip(JsonObject animations, String name, RpgGeometry.Built built, boolean loop) {
        if (name == null || !animations.has(name)) {
            return null;
        }
        JsonObject json = animations.getAsJsonObject(name);
        float length = Math.max(0.05f, GsonHelper.getAsFloat(json, "length", 1.0f));
        AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(length);
        if (loop) {
            builder.looping();
        }
        Set<String> bones = built.bones();
        for (var bone : GsonHelper.getAsJsonObject(json, "bones").entrySet()) {
            if (!bones.contains(bone.getKey())) {
                continue;
            }
            for (var channel : bone.getValue().getAsJsonObject().entrySet()) {
                AnimationChannel.Target target = switch (channel.getKey()) {
                    case "rotation" -> AnimationChannel.Targets.ROTATION;
                    case "position" -> AnimationChannel.Targets.POSITION;
                    case "scale" -> AnimationChannel.Targets.SCALE;
                    default -> null;
                };
                JsonArray frames = channel.getValue().getAsJsonArray();
                if (target == null || frames.isEmpty()) {
                    continue;
                }
                Keyframe[] keyframes = new Keyframe[frames.size()];
                for (int i = 0; i < keyframes.length; i++) {
                    JsonArray frame = frames.get(i).getAsJsonArray();
                    float x = frame.get(1).getAsFloat();
                    float y = frame.get(2).getAsFloat();
                    float z = frame.get(3).getAsFloat();
                    Vector3f value = switch (channel.getKey()) {
                        case "rotation" -> new Vector3f(x * RADIANS, y * RADIANS, z * RADIANS);
                        // L'axe y de Bedrock monte, celui du modele descend.
                        case "position" -> new Vector3f(x, -y, z);
                        // L'echelle d'une animation vanilla s'ajoute a 1.
                        default -> new Vector3f(x - 1.0f, y - 1.0f, z - 1.0f);
                    };
                    boolean smooth = frame.size() > 4 && "catmullrom".equals(frame.get(4).getAsString());
                    keyframes[i] = new Keyframe(frame.get(0).getAsFloat(), value, smooth
                            ? AnimationChannel.Interpolations.CATMULLROM : AnimationChannel.Interpolations.LINEAR);
                }
                builder.addAnimation(bone.getKey(), new AnimationChannel(target, keyframes));
            }
        }
        return new Clip(builder.build().bake(built.root()), length, loop);
    }
}

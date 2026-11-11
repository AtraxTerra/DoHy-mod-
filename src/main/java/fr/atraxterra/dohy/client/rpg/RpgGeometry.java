package fr.atraxterra.dohy.client.rpg;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import net.minecraft.util.GsonHelper;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Une geometrie Bedrock ({@code minecraft:geometry}) montee en {@link ModelPart}
 * vanilla, a la lecture du fichier.
 *
 * <p>Les conventions sont celles de {@code tools/bedrock_model.py}, verifiees
 * sur le dancarok : l'axe y de Bedrock monte, celui d'un modele vanilla
 * descend, et le sol est a 24 pixels. Un os racine se pose donc en
 * {@code (px, 24 - py, pz)}, un os enfant en {@code (p - parent)} avec y
 * inverse, une boite en {@code (ox - px, -(oy + sy - py), oz - pz)} ; les
 * rotations gardent leur signe. Une boite qui tourne sur elle-meme devient
 * une piece enfant a son propre pivot.
 *
 * <p>Les UV par face : Bedrock nomme les faces dans son repere, ou x et y
 * sont inverses par rapport au modele vanilla. La face {@code DOWN} vanilla
 * porte donc l'UV « up », {@code WEST} l'UV « east », et inversement ;
 * nord et sud ne changent pas.
 */
final class RpgGeometry {

    private static final Set<Direction> ALL = EnumSet.allOf(Direction.class);
    private static final float RADIANS = (float) Math.PI / 180.0f;

    /**
     * Une piece et ce qu'elle porte, gardes a part : {@link ModelPart} ne rend
     * ni ses boites ni ses enfants, et {@link RpgEntityModel} les dessine
     * lui-meme.
     */
    record Node(ModelPart part, ModelPart.Cube[] cubes, Node[] children) {
    }

    /** Le modele monte : sa racine, son arbre, le nom de ses os, sa hauteur au repos en blocs. */
    record Built(ModelPart root, Node tree, Set<String> bones, float height) {
    }

    private final float textureWidth;
    private final float textureHeight;
    private final Map<String, List<JsonObject>> childrenOf = new HashMap<>();
    private final Set<String> bones = new HashSet<>();
    private float top;

    private RpgGeometry(float textureWidth, float textureHeight) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    static Built build(JsonObject file) {
        JsonObject geometry = GsonHelper.getAsJsonArray(file, "minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = GsonHelper.getAsJsonObject(geometry, "description");
        RpgGeometry builder = new RpgGeometry(GsonHelper.getAsFloat(description, "texture_width", 64.0f),
                GsonHelper.getAsFloat(description, "texture_height", 64.0f));
        JsonArray list = GsonHelper.getAsJsonArray(geometry, "bones", new JsonArray());
        for (JsonElement element : list) {
            builder.bones.add(GsonHelper.getAsString(element.getAsJsonObject(), "name"));
        }
        List<JsonObject> roots = new ArrayList<>();
        for (JsonElement element : list) {
            JsonObject bone = element.getAsJsonObject();
            String parent = GsonHelper.getAsString(bone, "parent", "");
            if (builder.bones.contains(parent)) {
                builder.childrenOf.computeIfAbsent(parent, key -> new ArrayList<>()).add(bone);
            } else {
                roots.add(bone);
            }
        }
        Map<String, ModelPart> children = new LinkedHashMap<>();
        List<Node> nodes = new ArrayList<>();
        for (JsonObject bone : roots) {
            Node node = builder.bone(bone, null);
            nodes.add(node);
            children.put(GsonHelper.getAsString(bone, "name"), node.part());
        }
        ModelPart root = new ModelPart(List.of(), children);
        return new Built(root, new Node(root, new ModelPart.Cube[0], nodes.toArray(Node[]::new)), builder.bones,
                builder.top / 16.0f);
    }

    private Node bone(JsonObject bone, float[] parentPivot) {
        String name = GsonHelper.getAsString(bone, "name");
        float[] pivot = vector(bone, "pivot");
        float[] rotation = vector(bone, "rotation");
        List<ModelPart.Cube> cubes = new ArrayList<>();
        Map<String, ModelPart> children = new LinkedHashMap<>();
        List<Node> nodes = new ArrayList<>();
        int index = 0;
        for (JsonElement element : GsonHelper.getAsJsonArray(bone, "cubes", new JsonArray())) {
            JsonObject json = element.getAsJsonObject();
            float[] turn = vector(json, "rotation");
            if (turn[0] == 0.0f && turn[1] == 0.0f && turn[2] == 0.0f) {
                ModelPart.Cube cube = cube(json, pivot);
                if (cube != null) {
                    cubes.add(cube);
                }
            } else {
                float[] center = json.has("pivot") ? vector(json, "pivot") : pivot;
                ModelPart.Cube cube = cube(json, center);
                if (cube != null) {
                    ModelPart part = new ModelPart(List.of(cube), Map.of());
                    pose(part, PartPose.offsetAndRotation(center[0] - pivot[0], -(center[1] - pivot[1]),
                            center[2] - pivot[2], turn[0] * RADIANS, turn[1] * RADIANS, turn[2] * RADIANS));
                    children.put(name + "#" + index, part);
                    nodes.add(new Node(part, new ModelPart.Cube[] {cube}, new Node[0]));
                }
            }
            index++;
        }
        for (JsonObject child : childrenOf.getOrDefault(name, List.of())) {
            Node node = bone(child, pivot);
            nodes.add(node);
            children.put(GsonHelper.getAsString(child, "name"), node.part());
        }
        ModelPart part = new ModelPart(cubes, children);
        float x = parentPivot == null ? pivot[0] : pivot[0] - parentPivot[0];
        float y = parentPivot == null ? 24.0f - pivot[1] : -(pivot[1] - parentPivot[1]);
        float z = parentPivot == null ? pivot[2] : pivot[2] - parentPivot[2];
        pose(part, PartPose.offsetAndRotation(x, y, z,
                rotation[0] * RADIANS, rotation[1] * RADIANS, rotation[2] * RADIANS));
        return new Node(part, cubes.toArray(ModelPart.Cube[]::new), nodes.toArray(Node[]::new));
    }

    private static void pose(ModelPart part, PartPose pose) {
        part.setInitialPose(pose);
        part.loadPose(pose);
    }

    private ModelPart.Cube cube(JsonObject json, float[] pivot) {
        float[] origin = vector(json, "origin");
        float[] size = vector(json, "size");
        float inflate = GsonHelper.getAsFloat(json, "inflate", 0.0f);
        float x = origin[0] - pivot[0];
        float y = -(origin[1] + size[1] - pivot[1]);
        float z = origin[2] - pivot[2];
        top = Math.max(top, origin[1] + size[1]);
        JsonElement uv = json.get("uv");
        if (uv == null) {
            return null;
        }
        if (uv.isJsonArray()) {
            JsonArray offset = uv.getAsJsonArray();
            return new ModelPart.Cube((int) offset.get(0).getAsFloat(), (int) offset.get(1).getAsFloat(),
                    x, y, z, size[0], size[1], size[2], inflate, inflate, inflate,
                    GsonHelper.getAsBoolean(json, "mirror", false), textureWidth, textureHeight, ALL);
        }
        JsonObject faces = uv.getAsJsonObject();
        Set<Direction> visible = EnumSet.noneOf(Direction.class);
        for (Direction direction : Direction.values()) {
            if (faces.has(face(direction))) {
                visible.add(direction);
            }
        }
        if (visible.isEmpty()) {
            return null;
        }
        ModelPart.Cube cube = new ModelPart.Cube(0, 0, x, y, z, size[0], size[1], size[2],
                inflate, inflate, inflate, false, textureWidth, textureHeight, visible);
        for (int i = 0; i < cube.polygons.length; i++) {
            ModelPart.Polygon polygon = cube.polygons[i];
            if (polygon == null) {
                continue;
            }
            Direction direction = direction(polygon.normal());
            JsonObject face = faces.getAsJsonObject(face(direction));
            if (face == null) {
                continue;
            }
            float[] at = vector(face, "uv");
            float[] extent = vector(face, "uv_size");
            ModelPart.Polygon mapped = new ModelPart.Polygon(polygon.vertices().clone(), at[0], at[1],
                    at[0] + extent[0], at[1] + extent[1], textureWidth, textureHeight, false, direction);
            int turns = Math.floorMod(Math.round(GsonHelper.getAsFloat(face, "uv_rotation", 0.0f) / 90.0f), 4);
            if (turns != 0) {
                ModelPart.Vertex[] from = mapped.vertices();
                ModelPart.Vertex[] turned = new ModelPart.Vertex[from.length];
                for (int v = 0; v < from.length; v++) {
                    ModelPart.Vertex source = from[(v + turns) % from.length];
                    turned[v] = from[v].remap(source.u(), source.v());
                }
                mapped = new ModelPart.Polygon(turned, mapped.normal());
            }
            cube.polygons[i] = mapped;
        }
        return cube;
    }

    /** Le nom Bedrock de la face qu'une face vanilla affiche. */
    private static String face(Direction direction) {
        return switch (direction) {
            case DOWN -> "up";
            case UP -> "down";
            case WEST -> "east";
            case EAST -> "west";
            case NORTH -> "north";
            case SOUTH -> "south";
        };
    }

    private static Direction direction(Vector3fc normal) {
        float ax = Math.abs(normal.x());
        float ay = Math.abs(normal.y());
        float az = Math.abs(normal.z());
        if (ay >= ax && ay >= az) {
            return normal.y() < 0.0f ? Direction.DOWN : Direction.UP;
        }
        if (ax >= az) {
            return normal.x() < 0.0f ? Direction.WEST : Direction.EAST;
        }
        return normal.z() < 0.0f ? Direction.NORTH : Direction.SOUTH;
    }

    private static float[] vector(JsonObject json, String key) {
        float[] out = new float[3];
        JsonElement element = json.get(key);
        if (element != null && element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < Math.min(3, array.size()); i++) {
                out[i] = array.get(i).getAsFloat();
            }
        }
        return out;
    }
}

package fr.atraxterra.dohy.earth;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/**
 * Temporaire, pour l'essai : {@code /dohyshots recettes <phase>} ecrit dans
 * {@code recettes-<phase>.json} chaque recette du mod qui passe par Create ou par
 * NTM NEXT : ses entrees, ses sorties, et ce qui ne se resout pas (objet inconnu,
 * tag vide).
 */
@EventBusSubscriber
final class ShotRecipes {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Set<String> SPACES = Set.of("create", "hbm");
    private static final Set<String> OUTPUT_KEYS = Set.of("result", "results", "output", "outputs", "fluid_result",
            "fluid_results");
    private static final Pattern ID = Pattern.compile("#?[a-z0-9_.-]+:[a-z0-9_./-]+");

    private ShotRecipes() {
    }

    @SubscribeEvent
    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dohyshots")
                .then(Commands.literal("recettes").then(Commands.argument("phase", StringArgumentType.word())
                        .executes(context -> audit(context.getSource().getServer(),
                                StringArgumentType.getString(context, "phase"))))));
    }

    private static boolean ours(String id) {
        int colon = id.indexOf(':');
        return colon > 0 && SPACES.contains(id.substring(id.startsWith("#") ? 1 : 0, colon));
    }

    /** Parcourt le JSON d'une recette : identifiants vus en entree ou en sortie. */
    private static void walk(JsonElement element, boolean output, String key, Set<String> inputs, Set<String> outputs) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals("type")) {
                    continue;
                }
                walk(entry.getValue(), output || OUTPUT_KEYS.contains(entry.getKey()), entry.getKey(), inputs,
                        outputs);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                walk(item, output, key, inputs, outputs);
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String text = element.getAsString();
            if (!ID.matcher(text).matches()) {
                return;
            }
            if ((key.equals("tag") || key.equals("fluid_tag")) && !text.startsWith("#")) {
                text = "#" + text;
            }
            (output ? outputs : inputs).add(text);
        }
    }

    private static int audit(MinecraftServer server, String phase) {
        long start = System.nanoTime();
        DynamicOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonArray recipes = new JsonArray();
        Set<String> tags = new LinkedHashSet<>();
        Map<String, Integer> perType = new TreeMap<>();
        int total = 0;
        int failed = 0;
        List<JsonObject> pending = new ArrayList<>();
        for (RecipeHolder<?> holder : server.overworld().recipeAccess().getRecipes()) {
            total++;
            Recipe<?> recipe = holder.value();
            String id = holder.id().toString();
            int slash = id.lastIndexOf(" / ");
            if (slash >= 0) {
                id = id.substring(slash + 3, id.length() - 1);
            }
            String serializer = String.valueOf(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()));
            String type = String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()));
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id);
            entry.addProperty("type", type);
            entry.addProperty("serializer", serializer);
            Set<String> inputs = new LinkedHashSet<>();
            Set<String> outputs = new LinkedHashSet<>();
            try {
                Object serial = recipe.getSerializer();
                @SuppressWarnings("unchecked")
                MapCodec<Recipe<?>> codec = (MapCodec<Recipe<?>>) serial.getClass().getMethod("codec").invoke(serial);
                var result = codec.codec().encodeStart(ops, recipe);
                if (result.result().isPresent()) {
                    walk(result.result().get(), false, "", inputs, outputs);
                } else {
                    failed++;
                    entry.addProperty("error", result.error().map(Object::toString).orElse("?"));
                }
            } catch (Throwable error) {
                failed++;
                entry.addProperty("error", error.toString());
            }
            boolean relevant = id.startsWith("dohy:") && (ours(type) || ours(serializer));
            if (!relevant) {
                continue;
            }
            perType.merge(type, 1, Integer::sum);
            JsonArray in = new JsonArray();
            inputs.forEach(in::add);
            JsonArray out = new JsonArray();
            outputs.forEach(out::add);
            entry.add("inputs", in);
            entry.add("outputs", out);
            for (String value : inputs) {
                if (value.startsWith("#")) {
                    tags.add(value);
                }
            }
            pending.add(entry);
        }
        // Les tags cites : combien d'objets et de fluides chacun contient.
        Map<String, int[]> tagSizes = new HashMap<>();
        List<Holder.Reference<Item>> items = BuiltInRegistries.ITEM.listElements().toList();
        List<Holder.Reference<Fluid>> fluids = BuiltInRegistries.FLUID.listElements().toList();
        for (String tag : tags) {
            int[] size = new int[2];
            try {
                Identifier name = Identifier.parse(tag.substring(1));
                TagKey<Item> itemTag = TagKey.create(Registries.ITEM, name);
                TagKey<Fluid> fluidTag = TagKey.create(Registries.FLUID, name);
                for (Holder.Reference<Item> item : items) {
                    if (item.is(itemTag)) {
                        size[0]++;
                    }
                }
                for (Holder.Reference<Fluid> fluid : fluids) {
                    if (fluid.is(fluidTag)) {
                        size[1]++;
                    }
                }
            } catch (Throwable error) {
                size[0] = -1;
            }
            tagSizes.put(tag, size);
        }
        for (JsonObject entry : pending) {
            JsonArray unknown = new JsonArray();
            JsonArray empty = new JsonArray();
            for (JsonElement value : entry.getAsJsonArray("inputs")) {
                String text = value.getAsString();
                if (text.startsWith("#")) {
                    int[] size = tagSizes.get(text);
                    if (size != null && size[0] == 0 && size[1] == 0) {
                        empty.add(text);
                    }
                } else if (!known(text)) {
                    unknown.add(text);
                }
            }
            for (JsonElement value : entry.getAsJsonArray("outputs")) {
                if (!known(value.getAsString())) {
                    unknown.add(value.getAsString());
                }
            }
            if (!unknown.isEmpty()) {
                entry.add("unknown", unknown);
            }
            if (!empty.isEmpty()) {
                entry.add("empty_tags", empty);
            }
            recipes.add(entry);
        }
        JsonObject report = new JsonObject();
        report.addProperty("phase", phase);
        JsonObject mods = new JsonObject();
        for (String mod : List.of("create", "createdieselgenerators", "jei", "hbm", "dohy")) {
            mods.addProperty(mod, ModList.get().getModContainerById(mod)
                    .map(container -> container.getModInfo().getVersion().toString()).orElse("ABSENT"));
        }
        report.add("mods", mods);
        report.addProperty("recipes_total", total);
        report.addProperty("encode_failures", failed);
        JsonArray ourItems = new JsonArray();
        for (Holder.Reference<Item> item : items) {
            String name = String.valueOf(BuiltInRegistries.ITEM.getKey(item.value()));
            if (ours(name)) {
                ourItems.add(name);
            }
        }
        report.add("items", ourItems);
        JsonArray ourFluids = new JsonArray();
        for (Holder.Reference<Fluid> fluid : fluids) {
            String name = String.valueOf(BuiltInRegistries.FLUID.getKey(fluid.value()));
            if (ours(name)) {
                ourFluids.add(name);
            }
        }
        report.add("fluids", ourFluids);
        JsonObject sizes = new JsonObject();
        tagSizes.forEach((tag, size) -> {
            JsonArray pair = new JsonArray();
            pair.add(new JsonPrimitive(size[0]));
            pair.add(new JsonPrimitive(size[1]));
            sizes.add(tag, pair);
        });
        report.add("tags", sizes);
        report.add("recipes", recipes);
        Path file = Path.of("recettes-" + phase + ".json");
        try {
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report),
                    StandardCharsets.UTF_8);
        } catch (IOException error) {
            LOG.error("dohy-shots: [recettes] ecriture impossible", error);
        }
        LOG.info("dohy-shots: [recettes] phase {} : {} recettes, {} du mod pour Create et NTM, {} non encodees, "
                        + "{} objets, {} fluides, {} tags ; {} en {} ms ; par type {}", phase, total, recipes.size(),
                failed, ourItems.size(), ourFluids.size(), tagSizes.size(), file.toAbsolutePath(),
                (System.nanoTime() - start) / 1_000_000L, perType);
        return 1;
    }

    private static boolean known(String id) {
        try {
            Identifier name = Identifier.parse(id);
            return BuiltInRegistries.ITEM.containsKey(name) || BuiltInRegistries.FLUID.containsKey(name)
                    || BuiltInRegistries.BLOCK.containsKey(name);
        } catch (Throwable error) {
            return false;
        }
    }
}

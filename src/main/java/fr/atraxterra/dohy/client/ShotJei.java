package fr.atraxterra.dohy.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Temporaire, pour l'essai : JEI par reflexion (Berskar n'en depend pas). Ses
 * categories, le nombre de recettes de chacune, les objets de Create et de
 * Diesel Generators qui n'ont aucune recette dans JEI, et l'ecran d'une categorie.
 */
final class ShotJei {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String API = "mezz.jei.api.";

    private ShotJei() {
    }

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, false, ShotJei.class.getClassLoader());
    }

    /** Les interfaces publiques d'une classe, parentes comprises. */
    private static List<Class<?>> interfaces(Class<?> start) {
        List<Class<?>> found = new ArrayList<>();
        List<Class<?>> queue = new ArrayList<>();
        for (Class<?> current = start; current != null; current = current.getSuperclass()) {
            queue.addAll(Arrays.asList(current.getInterfaces()));
        }
        while (!queue.isEmpty()) {
            Class<?> next = queue.remove(0);
            if (!found.contains(next)) {
                if (Modifier.isPublic(next.getModifiers())) {
                    found.add(next);
                }
                queue.addAll(Arrays.asList(next.getInterfaces()));
            }
        }
        return found;
    }

    private static boolean fits(Class<?>[] parameters, Object[] args) {
        for (int i = 0; i < parameters.length; i++) {
            Class<?> parameter = parameters[i];
            if (parameter.isPrimitive()) {
                return false;
            }
            if (args[i] != null && !parameter.isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    /** Une methode publique d'une interface de l'API (ou d'une interface de l'objet), par nom et arguments. */
    private static Object call(Object target, String iface, String name, Object... args) throws Exception {
        List<Class<?>> owners = new ArrayList<>();
        if (iface != null) {
            owners.add(type(API + iface));
        }
        owners.addAll(interfaces(target.getClass()));
        for (Class<?> owner : owners) {
            for (Method method : owner.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == args.length
                        && fits(method.getParameterTypes(), args)) {
                    return method.invoke(target, args);
                }
            }
        }
        throw new NoSuchMethodException(name + "/" + args.length + " sur " + target.getClass().getName() + " ; "
                + owners.stream().map(owner -> owner.getSimpleName() + Arrays.stream(owner.getMethods())
                        .map(Method::getName).distinct().sorted().collect(Collectors.joining(",", "[", "]")))
                .collect(Collectors.joining(" ")));
    }

    /** Le runtime de JEI, ou null tant qu'il n'est pas pret. */
    static Object runtime() {
        try {
            Class<?> internal = type("mezz.jei.common.Internal");
            Class<?> runtime = type(API + "runtime.IJeiRuntime");
            for (Method method : internal.getMethods()) {
                if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0
                        && runtime.isAssignableFrom(method.getReturnType())) {
                    try {
                        return method.invoke(null);
                    } catch (Throwable notYet) {
                        return null;
                    }
                }
            }
        } catch (Throwable error) {
            return null;
        }
        return null;
    }

    static String uid(Object recipeType) {
        for (String name : List.of("getUid", "uid", "getId")) {
            try {
                return String.valueOf(call(recipeType, "recipe.types.IRecipeType", name));
            } catch (Throwable ignored) {
                // nom suivant
            }
        }
        return String.valueOf(recipeType);
    }

    @SuppressWarnings("unchecked")
    static List<Object> categories(Object runtime) throws Exception {
        Object manager = call(runtime, "runtime.IJeiRuntime", "getRecipeManager");
        Object lookup = call(manager, "recipe.IRecipeManager", "createRecipeCategoryLookup");
        Stream<Object> stream = (Stream<Object>) call(lookup, "recipe.IRecipeCategoriesLookup", "get");
        return stream.toList();
    }

    static Object recipeType(Object category) throws Exception {
        return call(category, "recipe.category.IRecipeCategory", "getRecipeType");
    }

    /** Liste les categories et leurs recettes, et les objets de nos mods sans recette dans JEI ; ecrit jei.json. */
    @SuppressWarnings("unchecked")
    static List<Object> dump(Object runtime) throws Exception {
        Object manager = call(runtime, "runtime.IJeiRuntime", "getRecipeManager");
        List<Object> ours = new ArrayList<>();
        JsonObject report = new JsonObject();
        JsonArray list = new JsonArray();
        Map<String, Integer> counts = new TreeMap<>();
        for (Object category : categories(runtime)) {
            Object recipeType = recipeType(category);
            String uid = uid(recipeType);
            Object lookup = call(manager, "recipe.IRecipeManager", "createRecipeLookup", recipeType);
            List<Object> recipes = ((Stream<Object>) call(lookup, "recipe.IRecipeLookup", "get")).toList();
            JsonObject entry = new JsonObject();
            entry.addProperty("uid", uid);
            String title = "?";
            try {
                title = ((net.minecraft.network.chat.Component) call(category, "recipe.category.IRecipeCategory",
                        "getTitle")).getString();
            } catch (Throwable ignored) {
                // sans titre
            }
            entry.addProperty("title", title);
            entry.addProperty("count", recipes.size());
            JsonArray ids = new JsonArray();
            for (Object recipe : recipes) {
                if (recipe instanceof net.minecraft.world.item.crafting.RecipeHolder<?> holder) {
                    String id = holder.id().toString();
                    int slash = id.lastIndexOf(" / ");
                    ids.add(slash >= 0 ? id.substring(slash + 3, id.length() - 1) : id);
                }
            }
            entry.add("ids", ids);
            list.add(entry);
            counts.put(uid, recipes.size());
            if (uid.startsWith("create:") || uid.startsWith("createdieselgenerators:")) {
                ours.add(recipeType);
            }
        }
        report.add("categories", list);
        // Chaque objet de nos mods : JEI lui connait-il au moins une recette qui le produit ?
        Object helpers = call(runtime, "runtime.IJeiRuntime", "getJeiHelpers");
        Object focuses = call(helpers, "helpers.IJeiHelpers", "getFocusFactory");
        Object output = Enum.valueOf((Class) type(API + "recipe.RecipeIngredientRole"), "OUTPUT");
        Object itemType = null;
        for (Field field : type(API + "constants.VanillaTypes").getFields()) {
            if (field.getName().equals("ITEM_STACK")) {
                itemType = field.get(null);
            }
        }
        JsonArray missing = new JsonArray();
        JsonObject made = new JsonObject();
        int checked = 0;
        for (Holder.Reference<Item> holder : BuiltInRegistries.ITEM.listElements().toList()) {
            String name = String.valueOf(BuiltInRegistries.ITEM.getKey(holder.value()));
            if (!name.startsWith("create:") && !name.startsWith("createdieselgenerators:")) {
                continue;
            }
            checked++;
            Object focus = call(focuses, "recipe.IFocusFactory", "createFocus", output, itemType,
                    new ItemStack(holder.value()));
            Object lookup = call(manager, "recipe.IRecipeManager", "createRecipeCategoryLookup");
            lookup = call(lookup, "recipe.IRecipeCategoriesLookup", "limitFocus", List.of(focus));
            List<Object> found = ((Stream<Object>) call(lookup, "recipe.IRecipeCategoriesLookup", "get")).toList();
            if (found.isEmpty()) {
                missing.add(name);
            } else {
                JsonArray where = new JsonArray();
                for (Object category : found) {
                    where.add(uid(recipeType(category)));
                }
                made.add(name, where);
            }
        }
        report.addProperty("items_checked", checked);
        report.add("items_without_recipe", missing);
        report.add("items_made_by", made);
        // Les fluides de nos mods : leur type NeoForge et le nom que JEI en affiche.
        JsonObject fluids = new JsonObject();
        int broken = 0;
        for (net.minecraft.world.level.material.Fluid fluid : BuiltInRegistries.FLUID) {
            String name = String.valueOf(BuiltInRegistries.FLUID.getKey(fluid));
            if (!name.startsWith("create:") && !name.startsWith("createdieselgenerators:")) {
                continue;
            }
            try {
                Object fluidType = fluid.getFluidType();
                String shown = new net.neoforged.neoforge.fluids.FluidStack(fluid, 1000).getHoverName().getString();
                fluids.addProperty(name, shown + " (" + net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES
                        .getKey((net.neoforged.neoforge.fluids.FluidType) fluidType) + ")");
            } catch (Throwable error) {
                broken++;
                fluids.addProperty(name, "ERREUR " + error);
            }
        }
        report.add("fluids", fluids);
        LOG.info("dohy-shots: [jei] fluides de Create et Diesel Generators : {} dont {} sans type", fluids.size(), broken);
        Files.writeString(Path.of("jei.json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                .toJson(report), StandardCharsets.UTF_8);
        LOG.info("dohy-shots: [jei] {} categories ; {} objets de Create et Diesel Generators, {} sans recette dans JEI : "
                + "{}", counts.size(), checked, missing.size(), missing);
        LOG.info("dohy-shots: [jei] recettes par categorie : {}", counts);
        return ours;
    }

    static void show(Object runtime, Object recipeType) throws Exception {
        Object gui = call(runtime, "runtime.IJeiRuntime", "getRecipesGui");
        call(gui, "runtime.IRecipesGui", "showTypes", List.of(recipeType));
    }

    static void showOutput(Object runtime, Item item) throws Exception {
        Object helpers = call(runtime, "runtime.IJeiRuntime", "getJeiHelpers");
        Object focuses = call(helpers, "helpers.IJeiHelpers", "getFocusFactory");
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object output = Enum.valueOf((Class) type(API + "recipe.RecipeIngredientRole"), "OUTPUT");
        Object itemType = type(API + "constants.VanillaTypes").getField("ITEM_STACK").get(null);
        Object focus = call(focuses, "recipe.IFocusFactory", "createFocus", output, itemType, new ItemStack(item));
        Object gui = call(runtime, "runtime.IJeiRuntime", "getRecipesGui");
        try {
            call(gui, "runtime.IRecipesGui", "show", List.of(focus));
        } catch (Throwable error) {
            call(gui, "runtime.IRecipesGui", "show", focus);
        }
    }

    /** Les recettes d'une categorie qui produisent cet objet, dans la page de cette categorie. */
    @SuppressWarnings("unchecked")
    static void showOutputIn(Object runtime, Item item, String categoryUid) throws Exception {
        Object helpers = call(runtime, "runtime.IJeiRuntime", "getJeiHelpers");
        Object focuses = call(helpers, "helpers.IJeiHelpers", "getFocusFactory");
        @SuppressWarnings({"rawtypes"})
        Object output = Enum.valueOf((Class) type(API + "recipe.RecipeIngredientRole"), "OUTPUT");
        Object itemType = type(API + "constants.VanillaTypes").getField("ITEM_STACK").get(null);
        Object focus = call(focuses, "recipe.IFocusFactory", "createFocus", output, itemType, new ItemStack(item));
        Object manager = call(runtime, "runtime.IJeiRuntime", "getRecipeManager");
        for (Object category : categories(runtime)) {
            Object recipeType = recipeType(category);
            if (!uid(recipeType).equals(categoryUid)) {
                continue;
            }
            Object lookup = call(manager, "recipe.IRecipeManager", "createRecipeLookup", recipeType);
            lookup = call(lookup, "recipe.IRecipeLookup", "limitFocus", List.of(focus));
            List<Object> recipes = ((Stream<Object>) call(lookup, "recipe.IRecipeLookup", "get")).toList();
            LOG.info("dohy-shots: [jei] {} : {} recette(s) de {}", categoryUid, recipes.size(),
                    BuiltInRegistries.ITEM.getKey(item));
            Object gui = call(runtime, "runtime.IJeiRuntime", "getRecipesGui");
            call(gui, "runtime.IRecipesGui", "showRecipes", category, recipes, List.of(focus));
            return;
        }
        LOG.info("dohy-shots: [jei] categorie {} absente", categoryUid);
    }

    static void close() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setScreen(null);
    }

    static String describe(Collection<?> types) {
        return types.stream().map(ShotJei::uid).collect(Collectors.joining(", "));
    }
}

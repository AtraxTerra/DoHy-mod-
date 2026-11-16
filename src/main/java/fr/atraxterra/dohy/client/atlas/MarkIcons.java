package fr.atraxterra.dohy.client.atlas;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * L'objet qui represente chaque gisement et chaque lieu sur la carte : un lingot pour
 * un metal, le minerai brut ou la gemme sinon, un bidon de petrole, une piece
 * radioactive pour une zone irradiee, une fusee pour une base de lancement.
 *
 * <p>Chaque ressource a une liste d'essais : les objets de NTM d'abord, ceux du jeu
 * ensuite. Sans aucun, la carte garde une pastille de la couleur de la ressource.
 */
final class MarkIcons {

    /** {@code objet} ou {@code objet@fluide} (un recipient de NTM rempli de ce fluide). */
    private static final Map<String, List<String>> CHOICES = Map.ofEntries(
            Map.entry("fer", List.of("minecraft:iron_ingot")),
            Map.entry("cuivre", List.of("minecraft:copper_ingot")),
            Map.entry("or", List.of("minecraft:gold_ingot")),
            Map.entry("diamant", List.of("minecraft:diamond")),
            Map.entry("emeraude", List.of("minecraft:emerald")),
            Map.entry("lapis", List.of("minecraft:lapis_lazuli")),
            Map.entry("redstone", List.of("minecraft:redstone")),
            Map.entry("charbon", List.of("minecraft:coal")),
            Map.entry("quartz", List.of("minecraft:quartz")),
            Map.entry("lignite", List.of("hbm:lignite", "minecraft:charcoal")),
            Map.entry("uranium", List.of("hbm:ingot_uranium", "hbm:raw_uranium")),
            Map.entry("thorium", List.of("hbm:ingot_th232", "hbm:raw_thorium")),
            Map.entry("terres rares", List.of("hbm:chunk_ore_rare", "hbm:powder_neodymium")),
            Map.entry("lithium", List.of("hbm:lithium", "hbm:raw_lithium")),
            Map.entry("cobalt", List.of("hbm:ingot_cobalt", "hbm:raw_cobalt")),
            Map.entry("coltan", List.of("hbm:fragment_coltan", "hbm:gem_tantalium")),
            Map.entry("bauxite", List.of("hbm:ingot_aluminium", "hbm:raw_aluminium")),
            Map.entry("titane", List.of("hbm:ingot_titanium", "hbm:raw_titanium")),
            Map.entry("zinc", List.of("create:zinc_ingot", "create:raw_zinc")),
            Map.entry("plomb", List.of("hbm:ingot_lead", "hbm:raw_lead")),
            Map.entry("tungstene", List.of("hbm:ingot_tungsten", "hbm:raw_tungsten")),
            Map.entry("fluorine", List.of("hbm:fluorite")),
            Map.entry("soufre", List.of("hbm:sulfur")),
            Map.entry("salpetre", List.of("hbm:niter")),
            Map.entry("beryllium", List.of("hbm:ingot_beryllium", "hbm:raw_beryllium")),
            Map.entry("amiante", List.of("hbm:ingot_asbestos", "hbm:powder_asbestos")),
            Map.entry("petrole", List.of("hbm:canister@hbm:oil", "hbm:fluid_barrel@hbm:oil", "hbm:oil_tar_crude")),
            Map.entry("gaz", List.of("hbm:gas_tank@hbm:gas", "hbm:canister@hbm:gas", "hbm:gas_empty")),
            Map.entry("sables bitumineux", List.of("hbm:ore_oil_sand", "hbm:oil_tar_crude", "minecraft:sand")),
            Map.entry("zone irradiee", List.of("hbm:coin_radiation", "hbm:geiger_counter", "minecraft:lime_dye")),
            Map.entry("base de lancement", List.of("hbm:missile_soyuz_0", "minecraft:firework_rocket")),
            Map.entry("volcan", List.of("minecraft:lava_bucket")));

    private static final Map<String, ItemStack> CACHE = new HashMap<>();

    private MarkIcons() {
    }

    /** L'objet de la ressource ; vide s'il n'y en a aucun dans ce jeu. */
    static ItemStack icon(String resource) {
        return CACHE.computeIfAbsent(resource == null ? "" : resource, MarkIcons::resolve);
    }

    /** Oublie les objets trouves (nouvelle partie, autres mods). */
    static void clear() {
        CACHE.clear();
    }

    private static ItemStack resolve(String resource) {
        for (String choice : CHOICES.getOrDefault(resource, List.of())) {
            ItemStack stack = stack(choice);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack stack(String choice) {
        int at = choice.indexOf('@');
        Identifier itemId = parse(at < 0 ? choice : choice.substring(0, at));
        if (itemId == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.AIR);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        if (at < 0) {
            return new ItemStack(item);
        }
        Identifier fluidId = parse(choice.substring(at + 1));
        Fluid fluid = fluidId == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.getOptional(fluidId).orElse(Fluids.EMPTY);
        if (fluid == Fluids.EMPTY) {
            return ItemStack.EMPTY;
        }
        return filled(item, fluid);
    }

    private static Identifier parse(String text) {
        try {
            return Identifier.parse(text);
        } catch (RuntimeException error) {
            return null;
        }
    }

    /**
     * Un recipient de NTM rempli : par sa fabrique {@code make(Fluid, int)}, sinon par
     * {@code setContent} avec une pile de fluide de NTM. Rien si NTM a change.
     */
    private static ItemStack filled(Item item, Fluid fluid) {
        try {
            Method make = item.getClass().getMethod("make", Fluid.class, int.class);
            Object made = make.invoke(item, fluid, 1000);
            if (made instanceof ItemStack stack && !stack.isEmpty()) {
                return stack;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // essai suivant
        }
        try {
            Class<?> type = Class.forName("com.hbm.inventory.fluid.FluidStackNTM", false,
                    item.getClass().getClassLoader());
            Constructor<?> constructor = type.getConstructor(Fluid.class, long.class);
            Object content = constructor.newInstance(fluid, 1000L);
            ItemStack stack = new ItemStack(item);
            item.getClass().getMethod("setContent", ItemStack.class, type).invoke(item, stack, content);
            return stack;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            DoHy.LOGGER.debug("Atlas : recipient {} non rempli ({})", item, error.toString());
            return ItemStack.EMPTY;
        }
    }
}

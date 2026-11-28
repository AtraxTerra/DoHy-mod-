package fr.atraxterra.dohy.palamod.botanist;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les objets de centrifugation ({@code BotanistCentrifugeItemConfig} de JobsReborn : un objet et sa
 * chance, au-dessus de 0 et au plus 1). Chaque objet pose autour de la graine ajoute sa chance de
 * reussite ; une case compte une fois, quelle que soit la taille de sa pile, et la centrifugation
 * en prend un. La liste et les chances sont dans la configuration du serveur de Paladium : celles-ci
 * sont calibrees ({@code docs/BALANCE.md}).
 *
 * <p>Du dechet de mutation a l'endium organique des graines minerales : ce que le botaniste produit
 * lui-meme sert aussi de catalyseur.
 */
public final class Catalysts {

    /** Identifiant de l'objet -> chance de reussite qu'il ajoute (0 a 1). */
    private static final Map<String, Double> CHANCES = new LinkedHashMap<>();

    static {
        // Une case ne compte qu'une fois : les objets bon marche plafonnent a 6 %, le paladium a
        // 30 %, et seul l'endium organique (cinq pepites) atteint les 100 % de la graine boostee.
        CHANCES.put("palamod:mutation_waste", 0.01d);
        CHANCES.put("minecraft:bone_meal", 0.01d);
        CHANCES.put("minecraft:redstone", 0.01d);
        CHANCES.put("minecraft:glowstone_dust", 0.01d);
        CHANCES.put("minecraft:lapis_lazuli", 0.01d);
        CHANCES.put("minecraft:emerald", 0.01d);
        CHANCES.put("minecraft:amethyst_shard", 0.02d);
        CHANCES.put("palamod:amethyst_ingot", 0.02d);
        CHANCES.put("palamod:titane_ingot", 0.03d);
        CHANCES.put("palamod:paladium_ingot", 0.05d);
        CHANCES.put("minecraft:diamond", 0.08d);
        CHANCES.put("palamod:green_paladium_ingot", 0.10d);
        CHANCES.put("palamod:organic_endium_nugget", 0.20d);
    }

    private Catalysts() {
    }

    /** La chance qu'ajoute cet objet, ou 0 s'il ne sert pas a la centrifugeuse. */
    public static double chance(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0d;
        }
        return CHANCES.getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), 0.0d);
    }

    public static boolean accepts(ItemStack stack) {
        return chance(stack) > 0.0d;
    }

    /** Les objets acceptes et leur chance, dans l'ordre de la liste (la bulle « Objets acceptes »). */
    public static List<Entry> all() {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, Double> entry : CHANCES.entrySet()) {
            Item item = Mutation.item(entry.getKey());
            if (item != Items.AIR) {
                entries.add(new Entry(item, entry.getValue()));
            }
        }
        return entries;
    }

    /** Un objet accepte et sa chance. */
    public record Entry(Item item, double chance) {
    }
}

package fr.atraxterra.dohy.palette;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Les trois familles qu'une palette accepte, chacune sa caisse.
 *
 * <p>Ce qu'une famille contient est un tag d'objets ({@code dohy:palette/obus},
 * {@code dohy:palette/munitions}, {@code dohy:palette/missiles}) : les obus, les
 * munitions et les missiles de NTM NEXT y sont inscrits (entrees facultatives,
 * sans effet si le mod manque), plus les fleches et les fusees du jeu. Un pack
 * de donnees du serveur peut en ajouter.
 *
 * <p>L'ordre suit les indices de {@link CrateLayout}.
 */
public enum PaletteFamily {

    OBUS("obus", "Obus", 0xFF9DAA5C),
    MUNITIONS("munitions", "Munitions", 0xFFE0B23A),
    MISSILES("missiles", "Missiles", 0xFFE4DCC8);

    private final TagKey<Item> tag;
    private final String label;
    private final int color;

    PaletteFamily(String key, String label, int color) {
        this.tag = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "palette/" + key));
        this.label = label;
        this.color = color;
    }

    public TagKey<Item> tag() {
        return tag;
    }

    public String label() {
        return label;
    }

    /** La couleur de la famille dans l'ecran (ARGB). */
    public int color() {
        return color;
    }

    /** La famille d'un objet, ou {@code null} si aucune palette ne le prend. */
    public static PaletteFamily of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (PaletteFamily family : values()) {
            if (stack.is(family.tag)) {
                return family;
            }
        }
        return null;
    }
}

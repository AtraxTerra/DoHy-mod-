package fr.atraxterra.dohy.palamod.equipment;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Les tags de reparation de l'equipement.
 *
 * <p>Un materiau 26.2 ne nomme pas son objet de reparation, il nomme un
 * <b>tag</b> : {@code ToolMaterial} et {@code ArmorMaterial} prennent tous deux
 * un {@code TagKey<Item>}. Le contenu de chaque tag est genere dans
 * {@code data/palamod/tags/item/repairs/}.
 *
 * <p><b>Il y en a un de plus que de metaux</b>, et c'est la seule exception de
 * tout l'equipement : l'armure en paladium se repare au <b>titane</b>, pas au
 * paladium. Releve au constructeur de chacune des 43 pieces dans
 * {@code PRegister_Items} — c'est la seule qui ne se repare pas avec son propre
 * metal. Porte tel quel ; un tag a part plutot qu'un cas particulier dans le
 * code, pour que l'exception se lise dans les donnees.
 */
public final class PalaTags {

    public static final TagKey<Item> REPAIRS_AMETHYST = repairs("amethyst");
    public static final TagKey<Item> REPAIRS_TITANE = repairs("titane");
    public static final TagKey<Item> REPAIRS_PALADIUM = repairs("paladium");
    public static final TagKey<Item> REPAIRS_PALADIUM_ARMOR = repairs("paladium_armor");
    public static final TagKey<Item> REPAIRS_GREEN_PALADIUM = repairs("green_paladium");
    public static final TagKey<Item> REPAIRS_ENDIUM = repairs("endium");
    public static final TagKey<Item> REPAIRS_MIXED_ENDIUM = repairs("mixed_endium");

    private PalaTags() {
    }

    private static TagKey<Item> repairs(String name) {
        return TagKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "repairs/" + name));
    }
}

package fr.atraxterra.dohy.palamod.equipment;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.EnumMap;
import java.util.Map;

/**
 * Les materiaux d'armure : les six sets de PalaMod, et la cagoule.
 *
 * <p><b>Les valeurs d'origine sont ecrites ici, et la conversion aussi</b> : on
 * lit dans ce fichier ce que {@code PArmorMaterial} declarait et ce que le jeu
 * en fait, sans aller chercher l'un ou l'autre ailleurs. Chaque ligne d'origine
 * est {@code EnumHelper.addArmorMaterial(nom, facteur, reductions[4], enchant)}.
 *
 * <p>Le facteur de durabilite et l'enchantabilite se portent tels quels :
 * {@link ArmorType#getDurability(int)} multiplie par les memes bases
 * {11, 16, 15, 13} qu'en 1.7.10.
 *
 * <p><b>La defense, elle, ne peut pas se porter telle quelle</b>, et c'est la
 * seule valeur calibree de tout l'equipement. Les totaux d'origine vont de 36 a
 * 46 points ; en 1.7.10, Forge plafonnait l'absorption a 100 % des 25 points,
 * donc chacun de ces sets rendait invulnerable aux coups — ce qui n'est pas le
 * jeu reel. Le vrai calcul de degats de Paladium vit dans un plugin serveur
 * absent de tous les jars fournis (verifie). En 26.2, l'attribut plafonne a 30 et
 * la reduction a 80 %.
 *
 * <p>D'ou {@link #defense(int, int)} : une droite <b>par emplacement</b>, ancree
 * sur le diamant (3 / 8 / 6 / 3, identique dans les deux versions) et qui envoie
 * les 46 points de l'endium sur les 30 du plafond. Par emplacement et non par
 * set : une fonction croissante appliquee piece par piece ne peut pas rendre un
 * palier superieur moins bon qu'un inferieur. La projection par set, essayee
 * d'abord, rendait le casque en titane moins bon que celui en amethyste alors que
 * les deux valent 9.
 *
 * <p>Pour ajuster apres test : {@link #SPREAD} ecarte ou resserre la defense, et
 * {@link #toughness(int)} est le levier qui separe les sets contre les gros
 * coups. Le tableau des resultats est dans {@code docs/PALAMOD.md}.
 */
public final class PalaArmorMaterials {

    /** Defense du diamant par emplacement : casque, plastron, jambieres, bottes. */
    private static final int[] DIAMOND = {3, 8, 6, 3};

    /** Total du diamant, et total de l'endium — l'armure la plus forte. */
    private static final int DIAMOND_TOTAL = 20;
    private static final int ENDIUM_TOTAL = 46;

    /** 26.2 plafonne l'attribut d'armure a 30 : l'endium y est envoye. */
    private static final double SPREAD = (30.0 - DIAMOND_TOTAL) / (ENDIUM_TOTAL - DIAMOND_TOTAL);

    public static final ArmorMaterial AMETHYST = material("amethyst",
            170, new int[]{9, 10, 9, 8}, 44, SoundEvents.ARMOR_EQUIP_DIAMOND,
            PalaTags.REPAIRS_AMETHYST);

    public static final ArmorMaterial TITANE = material("titane",
            200, new int[]{9, 11, 10, 8}, 35, SoundEvents.ARMOR_EQUIP_DIAMOND,
            PalaTags.REPAIRS_TITANE);

    /** La seule armure qui ne se repare pas avec son propre metal : au titane. */
    public static final ArmorMaterial PALADIUM = material("paladium",
            260, new int[]{10, 13, 11, 9}, 15, SoundEvents.ARMOR_EQUIP_DIAMOND,
            PalaTags.REPAIRS_PALADIUM_ARMOR);

    public static final ArmorMaterial GREEN_PALADIUM = material("green_paladium",
            300, new int[]{10, 13, 11, 9}, 15, SoundEvents.ARMOR_EQUIP_DIAMOND,
            PalaTags.REPAIRS_GREEN_PALADIUM);

    public static final ArmorMaterial MIXED_ENDIUM = material("mixed_endium",
            350, new int[]{10, 14, 11, 9}, 15, SoundEvents.ARMOR_EQUIP_NETHERITE,
            PalaTags.REPAIRS_MIXED_ENDIUM);

    public static final ArmorMaterial ENDIUM = material("endium",
            400, new int[]{11, 14, 11, 10}, 15, SoundEvents.ARMOR_EQUIP_NETHERITE,
            PalaTags.REPAIRS_ENDIUM);

    /**
     * La cagoule ({@code ItemArmorTravel}, chapitre 22) : en 1.7.10, le materiau
     * fer vanilla et sa reparation au fer. Elle reprend donc le casque en fer de
     * 26.2 tel quel, sans la calibration des sets de PalaMod — qui releverait sa
     * defense au-dessus du fer —, avec le son du cuir.
     */
    public static final ArmorMaterial HOOD = material("hood", ArmorMaterials.IRON, SoundEvents.ARMOR_EQUIP_LEATHER);

    /**
     * Le reste de l'armure de voyage ({@code ItemArmorTravel}) : le meme fer
     * vanilla que la cagoule, un materiau par piece parce que chacune a sa
     * couche portee ({@code getArmorTexture}).
     */
    public static final ArmorMaterial TRAVEL_BOOTS = material("travel_boots", ArmorMaterials.IRON,
            SoundEvents.ARMOR_EQUIP_IRON);
    public static final ArmorMaterial TRAVEL_LEGGINGS = material("travel_leggings", ArmorMaterials.IRON,
            SoundEvents.ARMOR_EQUIP_IRON);
    public static final ArmorMaterial TRAVEL_JUMP = material("travel_jump", ArmorMaterials.IRON,
            SoundEvents.ARMOR_EQUIP_IRON);
    public static final ArmorMaterial TRAVEL_SLIMY = material("travel_slimy", ArmorMaterials.IRON,
            SoundEvents.ARMOR_EQUIP_IRON);
    public static final ArmorMaterial TRAVEL_SCUBA = material("travel_scuba", ArmorMaterials.IRON,
            SoundEvents.ARMOR_EQUIP_IRON);

    private PalaArmorMaterials() {
    }

    /** Un materiau vanilla repris sous un autre nom : ses couches portees sont celles de PalaMod. */
    public static ArmorMaterial material(String name, ArmorMaterial base, Holder<SoundEvent> sound) {
        return new ArmorMaterial(base.durability(), base.defense(), base.enchantmentValue(), sound,
                base.toughness(), base.knockbackResistance(), base.repairIngredient(), asset(name));
    }

    private static ResourceKey<EquipmentAsset> asset(String name) {
        return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, name));
    }

    /**
     * Construit un materiau depuis ses valeurs d'origine.
     *
     * <p>Le nom sert aussi d'identifiant d'equipement : c'est lui qui designe
     * {@code assets/palamod/equipment/<nom>.json}, donc les couches portees. Un
     * nom ici sans fichier la-bas donne un joueur sans armure visible, ce que
     * {@code tools/check_assets.py} verifie.
     */
    public static ArmorMaterial material(String name, int durabilityFactor, int[] original,
                                         int enchantability, Holder<SoundEvent> sound,
                                         TagKey<Item> repair) {
        int total = 0;
        for (int value : original) {
            total += value;
        }

        Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
        defense.put(ArmorType.HELMET, defense(0, original[0]));
        defense.put(ArmorType.CHESTPLATE, defense(1, original[1]));
        defense.put(ArmorType.LEGGINGS, defense(2, original[2]));
        defense.put(ArmorType.BOOTS, defense(3, original[3]));
        // BODY n'est porte par aucune de ces armures ; il figure parce que les
        // materiaux vanilla le declarent tous, et qu'une cle absente ferait
        // planter quiconque demanderait les attributs de cet emplacement.
        defense.put(ArmorType.BODY, defense(1, original[1]));

        ResourceKey<EquipmentAsset> asset = asset(name);

        return new ArmorMaterial(durabilityFactor, defense, enchantability, sound,
                toughness(total), 0.0f, repair, asset);
    }

    /**
     * La defense 26.2 d'une piece, depuis sa valeur 1.7.10.
     *
     * <p>{@code diamant + (origine - diamant) * SPREAD}, arrondi. Croissante, donc
     * l'ordre des paliers est conserve emplacement par emplacement.
     */
    private static int defense(int slot, int original) {
        return (int) Math.round(DIAMOND[slot] + (original - DIAMOND[slot]) * SPREAD);
    }

    /**
     * La robustesse : 2 pour le diamant, 4 pour l'endium, lineaire entre les deux.
     *
     * <p>1.7.10 n'avait pas de robustesse. Elle est ajoutee parce que c'est le
     * mecanisme de 26.2 qui distingue les armures contre les coups lourds — et
     * les coups de Paladium sont lourds : une epee en paladium tranchant V en
     * critique depasse 20. Sans elle, tous les sets seraient identiques des
     * qu'ils atteignent le plafond.
     */
    private static float toughness(int originalTotal) {
        double value = 2.0 + (originalTotal - DIAMOND_TOTAL) * 2.0 / (ENDIUM_TOTAL - DIAMOND_TOTAL);
        return (float) (Math.round(value * 10.0) / 10.0);
    }
}

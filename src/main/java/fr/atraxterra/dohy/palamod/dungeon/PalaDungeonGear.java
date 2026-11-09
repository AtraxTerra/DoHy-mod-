package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.equipment.PalaArmorMaterials;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * L'equipement de donjon : trois armures et trois epees par donjon (wiki :
 * « Basique », « Renforcee », « Legendaire »), le set antique du palier 100
 * et les essences qui les reparent.
 *
 * <p>Statistiques hors donjon (choix du joueur : forts <b>surtout en
 * donjon</b>) : chaque tier reprend les valeurs d'origine d'un metal de
 * {@link PalaArmorMaterials}, converties de la meme facon — basique
 * l'amethyste, renforce le paladium, legendaire l'endium mixte, antique
 * l'endium. Les epees : basique l'amethyste, renforcee le paladium,
 * legendaire entre le paladium et l'endium. Le bonus de donjon est dans
 * {@link DungeonGearItem}.
 *
 * <p>Reparation (wiki) : a l'enclume, avec l'essence de son tier ; ici, de
 * son donjon aussi (une essence de foret pour Talikus). L'antique prend
 * n'importe quelle essence concentree. Textures : Palamod Recreated
 * (SILWOX / Solea Pixel), au choix du joueur ; l'antique, sans texture
 * d'origine, est le legendaire de Talikus passe au bronze.
 *
 * <p>Les noms sont ecrits en toutes lettres pour {@code check_assets.py}.
 */
public final class PalaDungeonGear {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);

    /** Valeurs d'origine (casque, plastron, jambieres, bottes) des metaux repris. */
    private static final int[] BASIC_DEFENSE = {9, 10, 9, 8};
    private static final int[] REINFORCED_DEFENSE = {10, 13, 11, 9};
    private static final int[] LEGENDARY_DEFENSE = {10, 14, 11, 9};
    private static final int[] ANTIQUE_DEFENSE = {11, 14, 11, 10};

    // -- Materiaux ---------------------------------------------------------------

    public static final ArmorMaterial TALIKUS = PalaArmorMaterials.material("talikus",
            170, BASIC_DEFENSE, 20, SoundEvents.ARMOR_EQUIP_DIAMOND, repairs("talikus"));
    public static final ArmorMaterial TALIKUS_REINFORCED = PalaArmorMaterials.material("talikus_reinforced",
            260, REINFORCED_DEFENSE, 15, SoundEvents.ARMOR_EQUIP_DIAMOND, repairs("talikus_reinforced"));
    public static final ArmorMaterial TALIKUS_LEGENDARY = PalaArmorMaterials.material("talikus_legendary",
            350, LEGENDARY_DEFENSE, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, repairs("talikus_legendary"));
    public static final ArmorMaterial NIMBRIA = PalaArmorMaterials.material("nimbria",
            170, BASIC_DEFENSE, 20, SoundEvents.ARMOR_EQUIP_DIAMOND, repairs("nimbria"));
    public static final ArmorMaterial NIMBRIA_REINFORCED = PalaArmorMaterials.material("nimbria_reinforced",
            260, REINFORCED_DEFENSE, 15, SoundEvents.ARMOR_EQUIP_DIAMOND, repairs("nimbria_reinforced"));
    public static final ArmorMaterial NIMBRIA_LEGENDARY = PalaArmorMaterials.material("nimbria_legendary",
            350, LEGENDARY_DEFENSE, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, repairs("nimbria_legendary"));
    public static final ArmorMaterial VITALYS = PalaArmorMaterials.material("vitalys",
            170, BASIC_DEFENSE, 20, SoundEvents.ARMOR_EQUIP_DIAMOND, repairs("vitalys"));
    public static final ArmorMaterial VITALYS_REINFORCED = PalaArmorMaterials.material("vitalys_reinforced",
            260, REINFORCED_DEFENSE, 15, SoundEvents.ARMOR_EQUIP_DIAMOND, repairs("vitalys_reinforced"));
    public static final ArmorMaterial VITALYS_LEGENDARY = PalaArmorMaterials.material("vitalys_legendary",
            350, LEGENDARY_DEFENSE, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, repairs("vitalys_legendary"));
    public static final ArmorMaterial MANELIOS = PalaArmorMaterials.material("manelios",
            170, BASIC_DEFENSE, 20, SoundEvents.ARMOR_EQUIP_DIAMOND, repairs("manelios"));
    public static final ArmorMaterial MANELIOS_REINFORCED = PalaArmorMaterials.material("manelios_reinforced",
            260, REINFORCED_DEFENSE, 15, SoundEvents.ARMOR_EQUIP_DIAMOND, repairs("manelios_reinforced"));
    public static final ArmorMaterial MANELIOS_LEGENDARY = PalaArmorMaterials.material("manelios_legendary",
            350, LEGENDARY_DEFENSE, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, repairs("manelios_legendary"));
    public static final ArmorMaterial ANTIQUE = PalaArmorMaterials.material("antique",
            400, ANTIQUE_DEFENSE, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, repairs("antique"));

    private static final ToolMaterial TALIKUS_TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1999, 20.0f, 3.0f, 20, repairs("talikus"));
    private static final ToolMaterial TALIKUS_REINFORCED_TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 4999, 28.0f, 6.0f, 15, repairs("talikus_reinforced"));
    private static final ToolMaterial TALIKUS_LEGENDARY_TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 4999, 30.0f, 6.6f, 15, repairs("talikus_legendary"));
    private static final ToolMaterial NIMBRIA_TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1999, 20.0f, 3.0f, 20, repairs("nimbria"));
    private static final ToolMaterial NIMBRIA_REINFORCED_TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 4999, 28.0f, 6.0f, 15, repairs("nimbria_reinforced"));
    private static final ToolMaterial NIMBRIA_LEGENDARY_TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 4999, 30.0f, 6.6f, 15, repairs("nimbria_legendary"));
    private static final ToolMaterial VITALYS_TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1999, 20.0f, 3.0f, 20, repairs("vitalys"));
    private static final ToolMaterial VITALYS_REINFORCED_TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 4999, 28.0f, 6.0f, 15, repairs("vitalys_reinforced"));
    private static final ToolMaterial VITALYS_LEGENDARY_TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 4999, 30.0f, 6.6f, 15, repairs("vitalys_legendary"));
    private static final ToolMaterial MANELIOS_TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1999, 20.0f, 3.0f, 20, repairs("manelios"));
    private static final ToolMaterial MANELIOS_REINFORCED_TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 4999, 28.0f, 6.0f, 15, repairs("manelios_reinforced"));
    private static final ToolMaterial MANELIOS_LEGENDARY_TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 4999, 30.0f, 6.6f, 15, repairs("manelios_legendary"));

    // -- Armures et epees ----------------------------------------------------------

    public static final DeferredItem<DungeonGearItem> TALIKUS_HELMET = armor("talikus_helmet",
            Dungeon.TALIKUS, DungeonGearTier.BASIC, TALIKUS, ArmorType.HELMET);
    public static final DeferredItem<DungeonGearItem> TALIKUS_CHESTPLATE = armor("talikus_chestplate",
            Dungeon.TALIKUS, DungeonGearTier.BASIC, TALIKUS, ArmorType.CHESTPLATE);
    public static final DeferredItem<DungeonGearItem> TALIKUS_LEGGINGS = armor("talikus_leggings",
            Dungeon.TALIKUS, DungeonGearTier.BASIC, TALIKUS, ArmorType.LEGGINGS);
    public static final DeferredItem<DungeonGearItem> TALIKUS_BOOTS = armor("talikus_boots",
            Dungeon.TALIKUS, DungeonGearTier.BASIC, TALIKUS, ArmorType.BOOTS);
    public static final DeferredItem<DungeonGearItem> TALIKUS_SWORD = sword("talikus_sword",
            Dungeon.TALIKUS, DungeonGearTier.BASIC, TALIKUS_TOOL);
    public static final DeferredItem<DungeonGearItem> TALIKUS_REINFORCED_HELMET = armor("talikus_reinforced_helmet",
            Dungeon.TALIKUS, DungeonGearTier.REINFORCED, TALIKUS_REINFORCED, ArmorType.HELMET);
    public static final DeferredItem<DungeonGearItem> TALIKUS_REINFORCED_CHESTPLATE = armor("talikus_reinforced_chestplate",
            Dungeon.TALIKUS, DungeonGearTier.REINFORCED, TALIKUS_REINFORCED, ArmorType.CHESTPLATE);
    public static final DeferredItem<DungeonGearItem> TALIKUS_REINFORCED_LEGGINGS = armor("talikus_reinforced_leggings",
            Dungeon.TALIKUS, DungeonGearTier.REINFORCED, TALIKUS_REINFORCED, ArmorType.LEGGINGS);
    public static final DeferredItem<DungeonGearItem> TALIKUS_REINFORCED_BOOTS = armor("talikus_reinforced_boots",
            Dungeon.TALIKUS, DungeonGearTier.REINFORCED, TALIKUS_REINFORCED, ArmorType.BOOTS);
    public static final DeferredItem<DungeonGearItem> TALIKUS_REINFORCED_SWORD = sword("talikus_reinforced_sword",
            Dungeon.TALIKUS, DungeonGearTier.REINFORCED, TALIKUS_REINFORCED_TOOL);
    public static final DeferredItem<DungeonGearItem> TALIKUS_LEGENDARY_HELMET = armor("talikus_legendary_helmet",
            Dungeon.TALIKUS, DungeonGearTier.LEGENDARY, TALIKUS_LEGENDARY, ArmorType.HELMET);
    public static final DeferredItem<DungeonGearItem> TALIKUS_LEGENDARY_CHESTPLATE = armor("talikus_legendary_chestplate",
            Dungeon.TALIKUS, DungeonGearTier.LEGENDARY, TALIKUS_LEGENDARY, ArmorType.CHESTPLATE);
    public static final DeferredItem<DungeonGearItem> TALIKUS_LEGENDARY_LEGGINGS = armor("talikus_legendary_leggings",
            Dungeon.TALIKUS, DungeonGearTier.LEGENDARY, TALIKUS_LEGENDARY, ArmorType.LEGGINGS);
    public static final DeferredItem<DungeonGearItem> TALIKUS_LEGENDARY_BOOTS = armor("talikus_legendary_boots",
            Dungeon.TALIKUS, DungeonGearTier.LEGENDARY, TALIKUS_LEGENDARY, ArmorType.BOOTS);
    public static final DeferredItem<DungeonGearItem> TALIKUS_LEGENDARY_SWORD = sword("talikus_legendary_sword",
            Dungeon.TALIKUS, DungeonGearTier.LEGENDARY, TALIKUS_LEGENDARY_TOOL);
    public static final DeferredItem<DungeonGearItem> NIMBRIA_HELMET = armor("nimbria_helmet",
            Dungeon.NIMBRIA, DungeonGearTier.BASIC, NIMBRIA, ArmorType.HELMET);
    public static final DeferredItem<DungeonGearItem> NIMBRIA_CHESTPLATE = armor("nimbria_chestplate",
            Dungeon.NIMBRIA, DungeonGearTier.BASIC, NIMBRIA, ArmorType.CHESTPLATE);
    public static final DeferredItem<DungeonGearItem> NIMBRIA_LEGGINGS = armor("nimbria_leggings",
            Dungeon.NIMBRIA, DungeonGearTier.BASIC, NIMBRIA, ArmorType.LEGGINGS);
    public static final DeferredItem<DungeonGearItem> NIMBRIA_BOOTS = armor("nimbria_boots",
            Dungeon.NIMBRIA, DungeonGearTier.BASIC, NIMBRIA, ArmorType.BOOTS);
    public static final DeferredItem<DungeonGearItem> NIMBRIA_SWORD = sword("nimbria_sword",
            Dungeon.NIMBRIA, DungeonGearTier.BASIC, NIMBRIA_TOOL);
    public static final DeferredItem<DungeonGearItem> NIMBRIA_REINFORCED_HELMET = armor("nimbria_reinforced_helmet",
            Dungeon.NIMBRIA, DungeonGearTier.REINFORCED, NIMBRIA_REINFORCED, ArmorType.HELMET);
    public static final DeferredItem<DungeonGearItem> NIMBRIA_REINFORCED_CHESTPLATE = armor("nimbria_reinforced_chestplate",
            Dungeon.NIMBRIA, DungeonGearTier.REINFORCED, NIMBRIA_REINFORCED, ArmorType.CHESTPLATE);
    public static final DeferredItem<DungeonGearItem> NIMBRIA_REINFORCED_LEGGINGS = armor("nimbria_reinforced_leggings",
            Dungeon.NIMBRIA, DungeonGearTier.REINFORCED, NIMBRIA_REINFORCED, ArmorType.LEGGINGS);
    public static final DeferredItem<DungeonGearItem> NIMBRIA_REINFORCED_BOOTS = armor("nimbria_reinforced_boots",
            Dungeon.NIMBRIA, DungeonGearTier.REINFORCED, NIMBRIA_REINFORCED, ArmorType.BOOTS);
    public static final DeferredItem<DungeonGearItem> NIMBRIA_REINFORCED_SWORD = sword("nimbria_reinforced_sword",
            Dungeon.NIMBRIA, DungeonGearTier.REINFORCED, NIMBRIA_REINFORCED_TOOL);
    public static final DeferredItem<DungeonGearItem> NIMBRIA_LEGENDARY_HELMET = armor("nimbria_legendary_helmet",
            Dungeon.NIMBRIA, DungeonGearTier.LEGENDARY, NIMBRIA_LEGENDARY, ArmorType.HELMET);
    public static final DeferredItem<DungeonGearItem> NIMBRIA_LEGENDARY_CHESTPLATE = armor("nimbria_legendary_chestplate",
            Dungeon.NIMBRIA, DungeonGearTier.LEGENDARY, NIMBRIA_LEGENDARY, ArmorType.CHESTPLATE);
    public static final DeferredItem<DungeonGearItem> NIMBRIA_LEGENDARY_LEGGINGS = armor("nimbria_legendary_leggings",
            Dungeon.NIMBRIA, DungeonGearTier.LEGENDARY, NIMBRIA_LEGENDARY, ArmorType.LEGGINGS);
    public static final DeferredItem<DungeonGearItem> NIMBRIA_LEGENDARY_BOOTS = armor("nimbria_legendary_boots",
            Dungeon.NIMBRIA, DungeonGearTier.LEGENDARY, NIMBRIA_LEGENDARY, ArmorType.BOOTS);
    public static final DeferredItem<DungeonGearItem> NIMBRIA_LEGENDARY_SWORD = sword("nimbria_legendary_sword",
            Dungeon.NIMBRIA, DungeonGearTier.LEGENDARY, NIMBRIA_LEGENDARY_TOOL);
    public static final DeferredItem<DungeonGearItem> VITALYS_HELMET = armor("vitalys_helmet",
            Dungeon.VITALYS, DungeonGearTier.BASIC, VITALYS, ArmorType.HELMET);
    public static final DeferredItem<DungeonGearItem> VITALYS_CHESTPLATE = armor("vitalys_chestplate",
            Dungeon.VITALYS, DungeonGearTier.BASIC, VITALYS, ArmorType.CHESTPLATE);
    public static final DeferredItem<DungeonGearItem> VITALYS_LEGGINGS = armor("vitalys_leggings",
            Dungeon.VITALYS, DungeonGearTier.BASIC, VITALYS, ArmorType.LEGGINGS);
    public static final DeferredItem<DungeonGearItem> VITALYS_BOOTS = armor("vitalys_boots",
            Dungeon.VITALYS, DungeonGearTier.BASIC, VITALYS, ArmorType.BOOTS);
    public static final DeferredItem<DungeonGearItem> VITALYS_SWORD = sword("vitalys_sword",
            Dungeon.VITALYS, DungeonGearTier.BASIC, VITALYS_TOOL);
    public static final DeferredItem<DungeonGearItem> VITALYS_REINFORCED_HELMET = armor("vitalys_reinforced_helmet",
            Dungeon.VITALYS, DungeonGearTier.REINFORCED, VITALYS_REINFORCED, ArmorType.HELMET);
    public static final DeferredItem<DungeonGearItem> VITALYS_REINFORCED_CHESTPLATE = armor("vitalys_reinforced_chestplate",
            Dungeon.VITALYS, DungeonGearTier.REINFORCED, VITALYS_REINFORCED, ArmorType.CHESTPLATE);
    public static final DeferredItem<DungeonGearItem> VITALYS_REINFORCED_LEGGINGS = armor("vitalys_reinforced_leggings",
            Dungeon.VITALYS, DungeonGearTier.REINFORCED, VITALYS_REINFORCED, ArmorType.LEGGINGS);
    public static final DeferredItem<DungeonGearItem> VITALYS_REINFORCED_BOOTS = armor("vitalys_reinforced_boots",
            Dungeon.VITALYS, DungeonGearTier.REINFORCED, VITALYS_REINFORCED, ArmorType.BOOTS);
    public static final DeferredItem<DungeonGearItem> VITALYS_REINFORCED_SWORD = sword("vitalys_reinforced_sword",
            Dungeon.VITALYS, DungeonGearTier.REINFORCED, VITALYS_REINFORCED_TOOL);
    public static final DeferredItem<DungeonGearItem> VITALYS_LEGENDARY_HELMET = armor("vitalys_legendary_helmet",
            Dungeon.VITALYS, DungeonGearTier.LEGENDARY, VITALYS_LEGENDARY, ArmorType.HELMET);
    public static final DeferredItem<DungeonGearItem> VITALYS_LEGENDARY_CHESTPLATE = armor("vitalys_legendary_chestplate",
            Dungeon.VITALYS, DungeonGearTier.LEGENDARY, VITALYS_LEGENDARY, ArmorType.CHESTPLATE);
    public static final DeferredItem<DungeonGearItem> VITALYS_LEGENDARY_LEGGINGS = armor("vitalys_legendary_leggings",
            Dungeon.VITALYS, DungeonGearTier.LEGENDARY, VITALYS_LEGENDARY, ArmorType.LEGGINGS);
    public static final DeferredItem<DungeonGearItem> VITALYS_LEGENDARY_BOOTS = armor("vitalys_legendary_boots",
            Dungeon.VITALYS, DungeonGearTier.LEGENDARY, VITALYS_LEGENDARY, ArmorType.BOOTS);
    public static final DeferredItem<DungeonGearItem> VITALYS_LEGENDARY_SWORD = sword("vitalys_legendary_sword",
            Dungeon.VITALYS, DungeonGearTier.LEGENDARY, VITALYS_LEGENDARY_TOOL);
    public static final DeferredItem<DungeonGearItem> MANELIOS_HELMET = armor("manelios_helmet",
            Dungeon.MANELIOS, DungeonGearTier.BASIC, MANELIOS, ArmorType.HELMET);
    public static final DeferredItem<DungeonGearItem> MANELIOS_CHESTPLATE = armor("manelios_chestplate",
            Dungeon.MANELIOS, DungeonGearTier.BASIC, MANELIOS, ArmorType.CHESTPLATE);
    public static final DeferredItem<DungeonGearItem> MANELIOS_LEGGINGS = armor("manelios_leggings",
            Dungeon.MANELIOS, DungeonGearTier.BASIC, MANELIOS, ArmorType.LEGGINGS);
    public static final DeferredItem<DungeonGearItem> MANELIOS_BOOTS = armor("manelios_boots",
            Dungeon.MANELIOS, DungeonGearTier.BASIC, MANELIOS, ArmorType.BOOTS);
    public static final DeferredItem<DungeonGearItem> MANELIOS_SWORD = sword("manelios_sword",
            Dungeon.MANELIOS, DungeonGearTier.BASIC, MANELIOS_TOOL);
    public static final DeferredItem<DungeonGearItem> MANELIOS_REINFORCED_HELMET = armor("manelios_reinforced_helmet",
            Dungeon.MANELIOS, DungeonGearTier.REINFORCED, MANELIOS_REINFORCED, ArmorType.HELMET);
    public static final DeferredItem<DungeonGearItem> MANELIOS_REINFORCED_CHESTPLATE = armor("manelios_reinforced_chestplate",
            Dungeon.MANELIOS, DungeonGearTier.REINFORCED, MANELIOS_REINFORCED, ArmorType.CHESTPLATE);
    public static final DeferredItem<DungeonGearItem> MANELIOS_REINFORCED_LEGGINGS = armor("manelios_reinforced_leggings",
            Dungeon.MANELIOS, DungeonGearTier.REINFORCED, MANELIOS_REINFORCED, ArmorType.LEGGINGS);
    public static final DeferredItem<DungeonGearItem> MANELIOS_REINFORCED_BOOTS = armor("manelios_reinforced_boots",
            Dungeon.MANELIOS, DungeonGearTier.REINFORCED, MANELIOS_REINFORCED, ArmorType.BOOTS);
    public static final DeferredItem<DungeonGearItem> MANELIOS_REINFORCED_SWORD = sword("manelios_reinforced_sword",
            Dungeon.MANELIOS, DungeonGearTier.REINFORCED, MANELIOS_REINFORCED_TOOL);
    public static final DeferredItem<DungeonGearItem> MANELIOS_LEGENDARY_HELMET = armor("manelios_legendary_helmet",
            Dungeon.MANELIOS, DungeonGearTier.LEGENDARY, MANELIOS_LEGENDARY, ArmorType.HELMET);
    public static final DeferredItem<DungeonGearItem> MANELIOS_LEGENDARY_CHESTPLATE = armor("manelios_legendary_chestplate",
            Dungeon.MANELIOS, DungeonGearTier.LEGENDARY, MANELIOS_LEGENDARY, ArmorType.CHESTPLATE);
    public static final DeferredItem<DungeonGearItem> MANELIOS_LEGENDARY_LEGGINGS = armor("manelios_legendary_leggings",
            Dungeon.MANELIOS, DungeonGearTier.LEGENDARY, MANELIOS_LEGENDARY, ArmorType.LEGGINGS);
    public static final DeferredItem<DungeonGearItem> MANELIOS_LEGENDARY_BOOTS = armor("manelios_legendary_boots",
            Dungeon.MANELIOS, DungeonGearTier.LEGENDARY, MANELIOS_LEGENDARY, ArmorType.BOOTS);
    public static final DeferredItem<DungeonGearItem> MANELIOS_LEGENDARY_SWORD = sword("manelios_legendary_sword",
            Dungeon.MANELIOS, DungeonGearTier.LEGENDARY, MANELIOS_LEGENDARY_TOOL);
    public static final DeferredItem<DungeonGearItem> ANTIQUE_HELMET = armor("antique_helmet",
            null, DungeonGearTier.ANTIQUE, ANTIQUE, ArmorType.HELMET);
    public static final DeferredItem<DungeonGearItem> ANTIQUE_CHESTPLATE = armor("antique_chestplate",
            null, DungeonGearTier.ANTIQUE, ANTIQUE, ArmorType.CHESTPLATE);
    public static final DeferredItem<DungeonGearItem> ANTIQUE_LEGGINGS = armor("antique_leggings",
            null, DungeonGearTier.ANTIQUE, ANTIQUE, ArmorType.LEGGINGS);
    public static final DeferredItem<DungeonGearItem> ANTIQUE_BOOTS = armor("antique_boots",
            null, DungeonGearTier.ANTIQUE, ANTIQUE, ArmorType.BOOTS);

    // -- Essences -------------------------------------------------------------------

    public static final DeferredItem<EssenceItem> FOREST_ESSENCE_FRAGMENT = ITEMS.registerItem("forest_essence_fragment",
            properties -> new EssenceItem(Dungeon.TALIKUS, EssenceTier.FRAGMENT, properties));
    public static final DeferredItem<EssenceItem> FOREST_ESSENCE = ITEMS.registerItem("forest_essence",
            properties -> new EssenceItem(Dungeon.TALIKUS, EssenceTier.ESSENCE, properties));
    public static final DeferredItem<EssenceItem> FOREST_ESSENCE_CONCENTRATE = ITEMS.registerItem("forest_essence_concentrate",
            properties -> new EssenceItem(Dungeon.TALIKUS, EssenceTier.CONCENTRATE, properties));
    public static final DeferredItem<EssenceItem> CAVERN_ESSENCE_FRAGMENT = ITEMS.registerItem("cavern_essence_fragment",
            properties -> new EssenceItem(Dungeon.NIMBRIA, EssenceTier.FRAGMENT, properties));
    public static final DeferredItem<EssenceItem> CAVERN_ESSENCE = ITEMS.registerItem("cavern_essence",
            properties -> new EssenceItem(Dungeon.NIMBRIA, EssenceTier.ESSENCE, properties));
    public static final DeferredItem<EssenceItem> CAVERN_ESSENCE_CONCENTRATE = ITEMS.registerItem("cavern_essence_concentrate",
            properties -> new EssenceItem(Dungeon.NIMBRIA, EssenceTier.CONCENTRATE, properties));
    public static final DeferredItem<EssenceItem> FLAME_ESSENCE_FRAGMENT = ITEMS.registerItem("flame_essence_fragment",
            properties -> new EssenceItem(Dungeon.VITALYS, EssenceTier.FRAGMENT, properties));
    public static final DeferredItem<EssenceItem> FLAME_ESSENCE = ITEMS.registerItem("flame_essence",
            properties -> new EssenceItem(Dungeon.VITALYS, EssenceTier.ESSENCE, properties));
    public static final DeferredItem<EssenceItem> FLAME_ESSENCE_CONCENTRATE = ITEMS.registerItem("flame_essence_concentrate",
            properties -> new EssenceItem(Dungeon.VITALYS, EssenceTier.CONCENTRATE, properties));
    public static final DeferredItem<EssenceItem> AQUATIC_ESSENCE_FRAGMENT = ITEMS.registerItem("aquatic_essence_fragment",
            properties -> new EssenceItem(Dungeon.MANELIOS, EssenceTier.FRAGMENT, properties));
    public static final DeferredItem<EssenceItem> AQUATIC_ESSENCE = ITEMS.registerItem("aquatic_essence",
            properties -> new EssenceItem(Dungeon.MANELIOS, EssenceTier.ESSENCE, properties));
    public static final DeferredItem<EssenceItem> AQUATIC_ESSENCE_CONCENTRATE = ITEMS.registerItem("aquatic_essence_concentrate",
            properties -> new EssenceItem(Dungeon.MANELIOS, EssenceTier.CONCENTRATE, properties));

    public static final List<DeferredItem<? extends Item>> ORDERED = List.of(
            TALIKUS_HELMET,
            TALIKUS_CHESTPLATE,
            TALIKUS_LEGGINGS,
            TALIKUS_BOOTS,
            TALIKUS_SWORD,
            TALIKUS_REINFORCED_HELMET,
            TALIKUS_REINFORCED_CHESTPLATE,
            TALIKUS_REINFORCED_LEGGINGS,
            TALIKUS_REINFORCED_BOOTS,
            TALIKUS_REINFORCED_SWORD,
            TALIKUS_LEGENDARY_HELMET,
            TALIKUS_LEGENDARY_CHESTPLATE,
            TALIKUS_LEGENDARY_LEGGINGS,
            TALIKUS_LEGENDARY_BOOTS,
            TALIKUS_LEGENDARY_SWORD,
            NIMBRIA_HELMET,
            NIMBRIA_CHESTPLATE,
            NIMBRIA_LEGGINGS,
            NIMBRIA_BOOTS,
            NIMBRIA_SWORD,
            NIMBRIA_REINFORCED_HELMET,
            NIMBRIA_REINFORCED_CHESTPLATE,
            NIMBRIA_REINFORCED_LEGGINGS,
            NIMBRIA_REINFORCED_BOOTS,
            NIMBRIA_REINFORCED_SWORD,
            NIMBRIA_LEGENDARY_HELMET,
            NIMBRIA_LEGENDARY_CHESTPLATE,
            NIMBRIA_LEGENDARY_LEGGINGS,
            NIMBRIA_LEGENDARY_BOOTS,
            NIMBRIA_LEGENDARY_SWORD,
            VITALYS_HELMET,
            VITALYS_CHESTPLATE,
            VITALYS_LEGGINGS,
            VITALYS_BOOTS,
            VITALYS_SWORD,
            VITALYS_REINFORCED_HELMET,
            VITALYS_REINFORCED_CHESTPLATE,
            VITALYS_REINFORCED_LEGGINGS,
            VITALYS_REINFORCED_BOOTS,
            VITALYS_REINFORCED_SWORD,
            VITALYS_LEGENDARY_HELMET,
            VITALYS_LEGENDARY_CHESTPLATE,
            VITALYS_LEGENDARY_LEGGINGS,
            VITALYS_LEGENDARY_BOOTS,
            VITALYS_LEGENDARY_SWORD,
            MANELIOS_HELMET,
            MANELIOS_CHESTPLATE,
            MANELIOS_LEGGINGS,
            MANELIOS_BOOTS,
            MANELIOS_SWORD,
            MANELIOS_REINFORCED_HELMET,
            MANELIOS_REINFORCED_CHESTPLATE,
            MANELIOS_REINFORCED_LEGGINGS,
            MANELIOS_REINFORCED_BOOTS,
            MANELIOS_REINFORCED_SWORD,
            MANELIOS_LEGENDARY_HELMET,
            MANELIOS_LEGENDARY_CHESTPLATE,
            MANELIOS_LEGENDARY_LEGGINGS,
            MANELIOS_LEGENDARY_BOOTS,
            MANELIOS_LEGENDARY_SWORD,
            ANTIQUE_HELMET,
            ANTIQUE_CHESTPLATE,
            ANTIQUE_LEGGINGS,
            ANTIQUE_BOOTS,
            FOREST_ESSENCE_FRAGMENT,
            FOREST_ESSENCE,
            FOREST_ESSENCE_CONCENTRATE,
            CAVERN_ESSENCE_FRAGMENT,
            CAVERN_ESSENCE,
            CAVERN_ESSENCE_CONCENTRATE,
            FLAME_ESSENCE_FRAGMENT,
            FLAME_ESSENCE,
            FLAME_ESSENCE_CONCENTRATE,
            AQUATIC_ESSENCE_FRAGMENT,
            AQUATIC_ESSENCE,
            AQUATIC_ESSENCE_CONCENTRATE);

    private PalaDungeonGear() {
    }

    private static TagKey<Item> repairs(String name) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "repairs/" + name));
    }

    private static DeferredItem<DungeonGearItem> armor(String name, Dungeon dungeon, DungeonGearTier tier,
                                                       ArmorMaterial material, ArmorType type) {
        return ITEMS.registerItem(name, properties -> new DungeonGearItem(dungeon, tier, true,
                properties.humanoidArmor(material, type)));
    }

    private static DeferredItem<DungeonGearItem> sword(String name, Dungeon dungeon, DungeonGearTier tier,
                                                       ToolMaterial material) {
        return ITEMS.registerItem(name, properties -> new DungeonGearItem(dungeon, tier, false,
                properties.sword(material, 3.0f, -2.4f)));
    }

    /** L'essence d'un donjon et d'un tier. */
    public static Item essence(Dungeon dungeon, EssenceTier tier) {
        return ESSENCES.get(dungeon.ordinal() * 3 + tier.ordinal()).get();
    }

    private static final List<DeferredItem<EssenceItem>> ESSENCES = List.of(
            FOREST_ESSENCE_FRAGMENT,
            FOREST_ESSENCE,
            FOREST_ESSENCE_CONCENTRATE,
            CAVERN_ESSENCE_FRAGMENT,
            CAVERN_ESSENCE,
            CAVERN_ESSENCE_CONCENTRATE,
            FLAME_ESSENCE_FRAGMENT,
            FLAME_ESSENCE,
            FLAME_ESSENCE_CONCENTRATE,
            AQUATIC_ESSENCE_FRAGMENT,
            AQUATIC_ESSENCE,
            AQUATIC_ESSENCE_CONCENTRATE);

    /** Une piece d'armure d'un donjon et d'un tier, 0 a 3 : casque, plastron, jambieres, bottes. */
    public static Item armorPiece(Dungeon dungeon, DungeonGearTier tier, int piece) {
        return GEAR.get((dungeon.ordinal() * 3 + tier.ordinal()) * 5 + piece).get();
    }

    public static Item sword(Dungeon dungeon, DungeonGearTier tier) {
        return GEAR.get((dungeon.ordinal() * 3 + tier.ordinal()) * 5 + 4).get();
    }

    /** La piece antique d'un donjon (wiki, palier 100). */
    public static Item antique(Dungeon dungeon) {
        return switch (dungeon) {
            case TALIKUS -> ANTIQUE_CHESTPLATE.get();
            case NIMBRIA -> ANTIQUE_BOOTS.get();
            case VITALYS -> ANTIQUE_HELMET.get();
            case MANELIOS -> ANTIQUE_LEGGINGS.get();
        };
    }

    private static final List<DeferredItem<DungeonGearItem>> GEAR = List.of(
            TALIKUS_HELMET,
            TALIKUS_CHESTPLATE,
            TALIKUS_LEGGINGS,
            TALIKUS_BOOTS,
            TALIKUS_SWORD,
            TALIKUS_REINFORCED_HELMET,
            TALIKUS_REINFORCED_CHESTPLATE,
            TALIKUS_REINFORCED_LEGGINGS,
            TALIKUS_REINFORCED_BOOTS,
            TALIKUS_REINFORCED_SWORD,
            TALIKUS_LEGENDARY_HELMET,
            TALIKUS_LEGENDARY_CHESTPLATE,
            TALIKUS_LEGENDARY_LEGGINGS,
            TALIKUS_LEGENDARY_BOOTS,
            TALIKUS_LEGENDARY_SWORD,
            NIMBRIA_HELMET,
            NIMBRIA_CHESTPLATE,
            NIMBRIA_LEGGINGS,
            NIMBRIA_BOOTS,
            NIMBRIA_SWORD,
            NIMBRIA_REINFORCED_HELMET,
            NIMBRIA_REINFORCED_CHESTPLATE,
            NIMBRIA_REINFORCED_LEGGINGS,
            NIMBRIA_REINFORCED_BOOTS,
            NIMBRIA_REINFORCED_SWORD,
            NIMBRIA_LEGENDARY_HELMET,
            NIMBRIA_LEGENDARY_CHESTPLATE,
            NIMBRIA_LEGENDARY_LEGGINGS,
            NIMBRIA_LEGENDARY_BOOTS,
            NIMBRIA_LEGENDARY_SWORD,
            VITALYS_HELMET,
            VITALYS_CHESTPLATE,
            VITALYS_LEGGINGS,
            VITALYS_BOOTS,
            VITALYS_SWORD,
            VITALYS_REINFORCED_HELMET,
            VITALYS_REINFORCED_CHESTPLATE,
            VITALYS_REINFORCED_LEGGINGS,
            VITALYS_REINFORCED_BOOTS,
            VITALYS_REINFORCED_SWORD,
            VITALYS_LEGENDARY_HELMET,
            VITALYS_LEGENDARY_CHESTPLATE,
            VITALYS_LEGENDARY_LEGGINGS,
            VITALYS_LEGENDARY_BOOTS,
            VITALYS_LEGENDARY_SWORD,
            MANELIOS_HELMET,
            MANELIOS_CHESTPLATE,
            MANELIOS_LEGGINGS,
            MANELIOS_BOOTS,
            MANELIOS_SWORD,
            MANELIOS_REINFORCED_HELMET,
            MANELIOS_REINFORCED_CHESTPLATE,
            MANELIOS_REINFORCED_LEGGINGS,
            MANELIOS_REINFORCED_BOOTS,
            MANELIOS_REINFORCED_SWORD,
            MANELIOS_LEGENDARY_HELMET,
            MANELIOS_LEGENDARY_CHESTPLATE,
            MANELIOS_LEGENDARY_LEGGINGS,
            MANELIOS_LEGENDARY_BOOTS,
            MANELIOS_LEGENDARY_SWORD);

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}

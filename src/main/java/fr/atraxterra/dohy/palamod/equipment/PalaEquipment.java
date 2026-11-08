package fr.atraxterra.dohy.palamod.equipment;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Les outils, les armes et les armures.
 *
 * <p>Quarante-huit objets : quatre outils par metal (l'endium n'a pas de pelle
 * dans PalaMod), cinq houes a rayon, et six armures de quatre pieces. Les
 * identifiants suivent {@code <metal>_<piece>}, la convention que les verrous du
 * chapitre 1 attendent deja pour l'endium — un ecart d'un caractere laisserait
 * le verrou inerte sans la moindre erreur.
 *
 * <p><b>Les noms sont ecrits en toutes lettres</b>, et c'est une contrainte :
 * {@code tools/check_assets.py} trouve les objets enregistres en lisant les
 * noms litteraux du source. Construits par concatenation, ces 48 objets lui
 * seraient invisibles, et il signalerait tous leurs assets comme morts.
 *
 * <p><b>Ce que les verrous disent de l'endium</b>, et qu'il faut avoir en tete
 * avant de toucher a {@code data/jobs.json} : chaque metier debloque une partie
 * de la panoplie au niveau 100 — Mineur la pioche, Fermier la hache et le
 * plastron, Chasseur le casque et l'epee, Alchimiste les bottes et les
 * jambieres. La panoplie complete, donc l'invisibilite de {@link ArmorEffects},
 * est la recompense des quatre metiers montes au maximum.
 *
 * <p><b>Les decalages d'attaque.</b> L'epee prend {@code 3.0, -2.4} : 26.2
 * calcule {@code 1 + bonus + 3}, 1.7.10 calculait {@code 4 + bonus}, donc les
 * degats sont exactement ceux d'origine. Pioche, hache et pelle prennent ceux des
 * outils en diamant de 26.2 : un outil Paladium est a l'outil vanilla moderne ce
 * qu'il etait a celui de 1.7.10, le meme bonus sur la meme base. La hache frappe
 * donc plus fort que l'epee et plus lentement — c'est le dessin de 26.2, pas
 * celui de 1.7.10 qui n'avait pas de temps de recharge.
 */
public final class PalaEquipment {

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(PalaMod.NAMESPACE);

    private static final List<DeferredItem<? extends Item>> ORDER = new ArrayList<>();

    // -- Amethyste -----------------------------------------------------------
    public static final DeferredItem<Item> AMETHYST_SWORD = sword("amethyst_sword", PalaToolMaterials.AMETHYST);
    public static final DeferredItem<Item> AMETHYST_PICKAXE = pickaxe("amethyst_pickaxe", PalaToolMaterials.AMETHYST);
    public static final DeferredItem<AxeItem> AMETHYST_AXE = axe("amethyst_axe", PalaToolMaterials.AMETHYST);
    public static final DeferredItem<ShovelItem> AMETHYST_SHOVEL = shovel("amethyst_shovel", PalaToolMaterials.AMETHYST);
    public static final DeferredItem<RadiusHoeItem> AMETHYST_RADIUS_HOE = hoe("amethyst_radius_hoe", PalaToolMaterials.AMETHYST, 2);
    public static final DeferredItem<Item> AMETHYST_HELMET = armor("amethyst_helmet", PalaArmorMaterials.AMETHYST, ArmorType.HELMET);
    public static final DeferredItem<Item> AMETHYST_CHESTPLATE = armor("amethyst_chestplate", PalaArmorMaterials.AMETHYST, ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> AMETHYST_LEGGINGS = armor("amethyst_leggings", PalaArmorMaterials.AMETHYST, ArmorType.LEGGINGS);
    public static final DeferredItem<Item> AMETHYST_BOOTS = armor("amethyst_boots", PalaArmorMaterials.AMETHYST, ArmorType.BOOTS);

    // -- Titane --------------------------------------------------------------
    public static final DeferredItem<Item> TITANE_SWORD = sword("titane_sword", PalaToolMaterials.TITANE);
    public static final DeferredItem<Item> TITANE_PICKAXE = pickaxe("titane_pickaxe", PalaToolMaterials.TITANE);
    public static final DeferredItem<AxeItem> TITANE_AXE = axe("titane_axe", PalaToolMaterials.TITANE);
    public static final DeferredItem<ShovelItem> TITANE_SHOVEL = shovel("titane_shovel", PalaToolMaterials.TITANE);
    public static final DeferredItem<RadiusHoeItem> TITANE_RADIUS_HOE = hoe("titane_radius_hoe", PalaToolMaterials.TITANE, 4);
    public static final DeferredItem<Item> TITANE_HELMET = armor("titane_helmet", PalaArmorMaterials.TITANE, ArmorType.HELMET);
    public static final DeferredItem<Item> TITANE_CHESTPLATE = armor("titane_chestplate", PalaArmorMaterials.TITANE, ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> TITANE_LEGGINGS = armor("titane_leggings", PalaArmorMaterials.TITANE, ArmorType.LEGGINGS);
    public static final DeferredItem<Item> TITANE_BOOTS = armor("titane_boots", PalaArmorMaterials.TITANE, ArmorType.BOOTS);

    // -- Paladium ------------------------------------------------------------
    public static final DeferredItem<Item> PALADIUM_SWORD = sword("paladium_sword", PalaToolMaterials.PALADIUM);
    public static final DeferredItem<Item> PALADIUM_PICKAXE = pickaxe("paladium_pickaxe", PalaToolMaterials.PALADIUM);
    public static final DeferredItem<AxeItem> PALADIUM_AXE = axe("paladium_axe", PalaToolMaterials.PALADIUM);
    public static final DeferredItem<ShovelItem> PALADIUM_SHOVEL = shovel("paladium_shovel", PalaToolMaterials.PALADIUM);
    public static final DeferredItem<RadiusHoeItem> PALADIUM_RADIUS_HOE = hoe("paladium_radius_hoe", PalaToolMaterials.PALADIUM, 6);
    public static final DeferredItem<Item> PALADIUM_HELMET = armor("paladium_helmet", PalaArmorMaterials.PALADIUM, ArmorType.HELMET);
    public static final DeferredItem<Item> PALADIUM_CHESTPLATE = armor("paladium_chestplate", PalaArmorMaterials.PALADIUM, ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> PALADIUM_LEGGINGS = armor("paladium_leggings", PalaArmorMaterials.PALADIUM, ArmorType.LEGGINGS);
    public static final DeferredItem<Item> PALADIUM_BOOTS = armor("paladium_boots", PalaArmorMaterials.PALADIUM, ArmorType.BOOTS);

    // -- Paladium vert -------------------------------------------------------
    //
    // L'epee a sa propre classe dans PalaMod (ItemPaladiumGreenSword), mais
    // seulement pour une icone alternative : aucun effet de combat. Elle se
    // porte comme une epee ordinaire.
    public static final DeferredItem<Item> GREEN_PALADIUM_SWORD = sword("green_paladium_sword", PalaToolMaterials.GREEN_PALADIUM);
    public static final DeferredItem<Item> GREEN_PALADIUM_PICKAXE = pickaxe("green_paladium_pickaxe", PalaToolMaterials.GREEN_PALADIUM);
    public static final DeferredItem<AxeItem> GREEN_PALADIUM_AXE = axe("green_paladium_axe", PalaToolMaterials.GREEN_PALADIUM);
    public static final DeferredItem<ShovelItem> GREEN_PALADIUM_SHOVEL = shovel("green_paladium_shovel", PalaToolMaterials.GREEN_PALADIUM);
    public static final DeferredItem<RadiusHoeItem> GREEN_PALADIUM_RADIUS_HOE = hoe("green_paladium_radius_hoe", PalaToolMaterials.GREEN_PALADIUM, 8);
    public static final DeferredItem<Item> GREEN_PALADIUM_HELMET = armor("green_paladium_helmet", PalaArmorMaterials.GREEN_PALADIUM, ArmorType.HELMET);
    public static final DeferredItem<Item> GREEN_PALADIUM_CHESTPLATE = armor("green_paladium_chestplate", PalaArmorMaterials.GREEN_PALADIUM, ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> GREEN_PALADIUM_LEGGINGS = armor("green_paladium_leggings", PalaArmorMaterials.GREEN_PALADIUM, ArmorType.LEGGINGS);
    public static final DeferredItem<Item> GREEN_PALADIUM_BOOTS = armor("green_paladium_boots", PalaArmorMaterials.GREEN_PALADIUM, ArmorType.BOOTS);

    // -- Endium mixe : armure seule ------------------------------------------
    public static final DeferredItem<Item> MIXED_ENDIUM_HELMET = armor("mixed_endium_helmet", PalaArmorMaterials.MIXED_ENDIUM, ArmorType.HELMET);
    public static final DeferredItem<Item> MIXED_ENDIUM_CHESTPLATE = armor("mixed_endium_chestplate", PalaArmorMaterials.MIXED_ENDIUM, ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> MIXED_ENDIUM_LEGGINGS = armor("mixed_endium_leggings", PalaArmorMaterials.MIXED_ENDIUM, ArmorType.LEGGINGS);
    public static final DeferredItem<Item> MIXED_ENDIUM_BOOTS = armor("mixed_endium_boots", PalaArmorMaterials.MIXED_ENDIUM, ArmorType.BOOTS);

    // -- Endium : pas de pelle dans PalaMod ----------------------------------
    public static final DeferredItem<Item> ENDIUM_SWORD = sword("endium_sword", PalaToolMaterials.ENDIUM);
    public static final DeferredItem<Item> ENDIUM_PICKAXE = pickaxe("endium_pickaxe", PalaToolMaterials.ENDIUM);
    public static final DeferredItem<AxeItem> ENDIUM_AXE = axe("endium_axe", PalaToolMaterials.ENDIUM);
    public static final DeferredItem<RadiusHoeItem> ENDIUM_RADIUS_HOE = hoe("endium_radius_hoe", PalaToolMaterials.ENDIUM, 12);
    public static final DeferredItem<Item> ENDIUM_HELMET = armor("endium_helmet", PalaArmorMaterials.ENDIUM, ArmorType.HELMET);
    public static final DeferredItem<Item> ENDIUM_CHESTPLATE = armor("endium_chestplate", PalaArmorMaterials.ENDIUM, ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> ENDIUM_LEGGINGS = armor("endium_leggings", PalaArmorMaterials.ENDIUM, ArmorType.LEGGINGS);
    public static final DeferredItem<Item> ENDIUM_BOOTS = armor("endium_boots", PalaArmorMaterials.ENDIUM, ArmorType.BOOTS);

    /**
     * L'ordre de l'onglet creatif : metal par metal, outils puis armure.
     *
     * <p>Rempli par les fabriques ci-dessus au fil de l'initialisation : c'est
     * {@code ORDER}, declaree avant tous les champs, qui le permet. La declarer
     * apres reproduirait exactement le plantage d'initialisation statique du
     * chapitre 4 — que {@code tools/check_static_init.py} attraperait.
     */
    public static List<DeferredItem<? extends Item>> ordered() {
        return List.copyOf(ORDER);
    }

    private PalaEquipment() {
    }

    private static DeferredItem<Item> sword(String name, ToolMaterial material) {
        return track(ITEMS.registerItem(name,
                properties -> new Item(properties.sword(material, 3.0f, -2.4f))));
    }

    private static DeferredItem<Item> pickaxe(String name, ToolMaterial material) {
        return track(ITEMS.registerItem(name,
                properties -> new Item(properties.pickaxe(material, 1.0f, -2.8f))));
    }

    private static DeferredItem<AxeItem> axe(String name, ToolMaterial material) {
        return track(ITEMS.registerItem(name,
                properties -> new AxeItem(material, 5.0f, -3.0f, properties)));
    }

    private static DeferredItem<ShovelItem> shovel(String name, ToolMaterial material) {
        return track(ITEMS.registerItem(name,
                properties -> new ShovelItem(material, 1.5f, -3.0f, properties)));
    }

    private static DeferredItem<RadiusHoeItem> hoe(String name, ToolMaterial material, int radius) {
        return track(ITEMS.registerItem(name,
                properties -> new RadiusHoeItem(material, radius, properties)));
    }

    private static DeferredItem<Item> armor(String name, ArmorMaterial material, ArmorType type) {
        return track(ITEMS.registerItem(name,
                properties -> new Item(properties.humanoidArmor(material, type))));
    }

    private static <T extends Item> DeferredItem<T> track(DeferredItem<T> item) {
        ORDER.add(item);
        return item;
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}

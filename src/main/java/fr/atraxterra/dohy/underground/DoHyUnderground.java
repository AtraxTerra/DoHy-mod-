package fr.atraxterra.dohy.underground;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.faction.enterprise.Line;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Les objets et les blocs de l'economie souterraine (lot 24c), sous l'espace de noms
 * {@code dohy} et dans leur propre onglet. Chacun sert : les graines poussent selon la latitude,
 * les feuilles sechent ou passent au laboratoire, les produits se rangent en sachets puis en
 * colis a la table de conditionnement (balance en main), les caisses scellees se montent a
 * l'etabli, et une planque securisee verse le tout aux stocks des entreprises (lot 24b).
 *
 * <p>Tout reste abstrait : des etapes de jeu, aucun procede reel. Textures originales.
 */
public final class DoHyUnderground {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DoHy.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DoHy.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DoHy.MOD_ID);

    // -- Les plantes ------------------------------------------------------------------------------

    public static final DeferredBlock<DrugCropBlock> CANNABIS_CROP = crop("cannabis_crop", Line.CANNABIS);
    public static final DeferredBlock<DrugCropBlock> COCA_CROP = crop("coca_crop", Line.COCAINE);
    public static final DeferredBlock<DrugCropBlock> POPPY_CROP = crop("poppy_crop", Line.HEROINE);

    public static final DeferredItem<DrugSeedItem> CANNABIS_SEEDS = seeds("cannabis_seeds", CANNABIS_CROP,
            Line.CANNABIS);
    public static final DeferredItem<DrugSeedItem> COCA_SEEDS = seeds("coca_seeds", COCA_CROP, Line.COCAINE);
    public static final DeferredItem<DrugSeedItem> POPPY_SEEDS = seeds("poppy_seeds", POPPY_CROP, Line.HEROINE);

    // -- Les matieres premieres -------------------------------------------------------------------

    public static final DeferredItem<Item> CANNABIS_LEAF = simple("cannabis_leaf");
    public static final DeferredItem<Item> COCA_LEAF = simple("coca_leaf");
    public static final DeferredItem<Item> POPPY_POD = simple("poppy_pod");
    public static final DeferredItem<Item> PRECURSOR_CAN = ITEMS.registerItem("precursor_can", Item::new,
            properties -> properties.stacksTo(16).component(DataComponents.LORE, lore("tooltip.dohy.precursor_can")));

    // -- Les produits finis -----------------------------------------------------------------------

    public static final DeferredItem<Item> DRIED_HERB = product("dried_herb");
    public static final DeferredItem<Item> COCAINE_POWDER = product("cocaine_powder");
    public static final DeferredItem<Item> HEROIN = product("heroin");
    public static final DeferredItem<Item> SYNTHETIC_PILLS = product("synthetic_pills");
    public static final DeferredItem<Item> SYNTHETIC_CRYSTALS = ITEMS.registerItem("synthetic_crystals", Item::new,
            properties -> properties.rarity(Rarity.UNCOMMON)
                    .component(DataComponents.LORE, lore("tooltip.dohy.synthetic_crystals")));

    // -- Le conditionnement -----------------------------------------------------------------------

    public static final DeferredItem<Item> EMPTY_BAG = simple("empty_bag");
    public static final DeferredItem<Item> HERB_BAG = bag("herb_bag");
    public static final DeferredItem<Item> POWDER_BAG = bag("powder_bag");
    public static final DeferredItem<Item> HEROIN_BAG = bag("heroin_bag");
    public static final DeferredItem<Item> PILL_BAG = bag("pill_bag");
    public static final DeferredItem<Item> CRYSTAL_BAG = ITEMS.registerItem("crystal_bag", Item::new,
            properties -> properties.rarity(Rarity.UNCOMMON)
                    .component(DataComponents.LORE, lore("tooltip.dohy.crystal_bag")));
    public static final DeferredItem<Item> HERB_PARCEL = parcel("herb_parcel");
    public static final DeferredItem<Item> POWDER_PARCEL = parcel("powder_parcel");
    public static final DeferredItem<Item> HEROIN_PARCEL = parcel("heroin_parcel");
    public static final DeferredItem<Item> SYNTHETIC_PARCEL = parcel("synthetic_parcel");

    // -- Le materiel ------------------------------------------------------------------------------

    /** La balance de precision : chaque sachet pese l'use d'un cran. */
    public static final DeferredItem<Item> PRECISION_SCALE = ITEMS.registerItem("precision_scale", Item::new,
            properties -> properties.durability(256)
                    .component(DataComponents.LORE, lore("tooltip.dohy.precision_scale")));
    public static final DeferredItem<Item> LAB_GLASSWARE = ITEMS.registerItem("lab_glassware", Item::new,
            properties -> properties.stacksTo(16).component(DataComponents.LORE, lore("tooltip.dohy.lab_glassware")));

    /** Les protections : peu de defense, celle du cuir, mais les vapeurs du laboratoire ne passent plus. */
    public static final ArmorMaterial PROTECTION = new ArmorMaterial(ArmorMaterials.LEATHER.durability() * 2,
            ArmorMaterials.LEATHER.defense(), ArmorMaterials.LEATHER.enchantmentValue(),
            SoundEvents.ARMOR_EQUIP_LEATHER, 0.0f, 0.0f, ArmorMaterials.LEATHER.repairIngredient(),
            ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "protection")));
    public static final DeferredItem<Item> GAS_MASK = ITEMS.registerItem("gas_mask",
            properties -> new Item(properties.humanoidArmor(PROTECTION, ArmorType.HELMET)
                    .component(DataComponents.LORE, lore("tooltip.dohy.protection"))));
    public static final DeferredItem<Item> HAZMAT_SUIT = ITEMS.registerItem("hazmat_suit",
            properties -> new Item(properties.humanoidArmor(PROTECTION, ArmorType.CHESTPLATE)
                    .component(DataComponents.LORE, lore("tooltip.dohy.protection"))));

    // -- Les armes, scellees ----------------------------------------------------------------------

    public static final DeferredItem<Item> MACHINED_PARTS = simple("machined_parts");
    public static final DeferredItem<Item> SEALED_CRATE = ITEMS.registerItem("sealed_crate", Item::new,
            properties -> properties.stacksTo(16).component(DataComponents.LORE, lore("tooltip.dohy.sealed_crate")));
    /** La caisse en cours de montage sur la chaine d'assemblage de Create (ses recettes n'existent qu'avec Create). */
    public static final DeferredItem<Item> INCOMPLETE_SEALED_CRATE = ITEMS.registerItem("incomplete_sealed_crate",
            Item::new, properties -> properties.stacksTo(1)
                    .component(DataComponents.LORE, lore("tooltip.dohy.incomplete_sealed_crate")));

    // -- Les blocs --------------------------------------------------------------------------------

    public static final DeferredBlock<DryingRackBlock> DRYING_RACK = BLOCKS.registerBlock("drying_rack",
            DryingRackBlock::new, properties -> properties.mapColor(MapColor.WOOD).strength(1.0f)
                    .sound(SoundType.WOOD).noOcclusion().randomTicks());
    public static final DeferredBlock<WorkstationBlock> CLANDESTINE_LAB = BLOCKS.registerBlock("clandestine_lab",
            properties -> new WorkstationBlock(properties, WorkstationBlock.Kind.LAB),
            properties -> properties.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL)
                    .noOcclusion());
    public static final DeferredBlock<WorkstationBlock> PACKAGING_TABLE = BLOCKS.registerBlock("packaging_table",
            properties -> new WorkstationBlock(properties, WorkstationBlock.Kind.PACKAGING),
            properties -> properties.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<WorkstationBlock> ARMS_WORKBENCH = BLOCKS.registerBlock("arms_workbench",
            properties -> new WorkstationBlock(properties, WorkstationBlock.Kind.ARMS),
            properties -> properties.mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL)
                    .noOcclusion());
    public static final DeferredBlock<StashChestBlock> STASH_CHEST = BLOCKS.registerBlock("stash_chest",
            StashChestBlock::new, properties -> properties.mapColor(MapColor.METAL).strength(5.0f, 1200.0f)
                    .sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());

    public static final DeferredItem<BlockItem> DRYING_RACK_ITEM = ITEMS.registerSimpleBlockItem(DRYING_RACK,
            properties -> properties.component(DataComponents.LORE, lore("tooltip.dohy.drying_rack")));
    public static final DeferredItem<BlockItem> CLANDESTINE_LAB_ITEM = ITEMS.registerSimpleBlockItem(CLANDESTINE_LAB,
            properties -> properties.rarity(Rarity.UNCOMMON)
                    .component(DataComponents.LORE, lore("tooltip.dohy.clandestine_lab")));
    public static final DeferredItem<BlockItem> PACKAGING_TABLE_ITEM = ITEMS.registerSimpleBlockItem(PACKAGING_TABLE,
            properties -> properties.component(DataComponents.LORE, lore("tooltip.dohy.packaging_table")));
    public static final DeferredItem<BlockItem> ARMS_WORKBENCH_ITEM = ITEMS.registerSimpleBlockItem(ARMS_WORKBENCH,
            properties -> properties.rarity(Rarity.UNCOMMON)
                    .component(DataComponents.LORE, lore("tooltip.dohy.arms_workbench")));
    public static final DeferredItem<BlockItem> STASH_CHEST_ITEM = ITEMS.registerSimpleBlockItem(STASH_CHEST,
            properties -> properties.rarity(Rarity.UNCOMMON)
                    .component(DataComponents.LORE, lore("tooltip.dohy.stash_chest")));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StashChestBlockEntity>> STASH_TYPE =
            TYPES.register("stash_chest", () -> new BlockEntityType<>(StashChestBlockEntity::new,
                    Set.of(STASH_CHEST.get())));

    // -- L'onglet ---------------------------------------------------------------------------------

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("underground",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + DoHy.MOD_ID + ".underground"))
                    .icon(() -> new ItemStack(STASH_CHEST_ITEM.get()))
                    .displayItems((parameters, output) -> ordered().forEach(item -> output.accept(item.get())))
                    .build());

    private DoHyUnderground() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        TABS.register(modBus);
        modBus.addListener(DoHyUnderground::onCapabilities);
    }

    /**
     * Les tapis et entonnoirs de Create, les convoyeurs de NTM NEXT remplissent la planque par ses
     * faces, et ne la vident pas ({@link StashChestBlockEntity#canTakeItemThroughFace}).
     */
    private static void onCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, STASH_TYPE.get(),
                (stash, side) -> new WorldlyContainerWrapper(stash, side == null ? Direction.UP : side));
    }

    /** L'onglet, dans l'ordre des filieres. */
    private static List<Supplier<? extends Item>> ordered() {
        List<Supplier<? extends Item>> list = new ArrayList<>(List.of(
                CANNABIS_SEEDS, COCA_SEEDS, POPPY_SEEDS, CANNABIS_LEAF, COCA_LEAF, POPPY_POD, PRECURSOR_CAN,
                DRIED_HERB, COCAINE_POWDER, HEROIN, SYNTHETIC_PILLS, SYNTHETIC_CRYSTALS,
                EMPTY_BAG, HERB_BAG, POWDER_BAG, HEROIN_BAG, PILL_BAG, CRYSTAL_BAG,
                HERB_PARCEL, POWDER_PARCEL, HEROIN_PARCEL, SYNTHETIC_PARCEL,
                PRECISION_SCALE, LAB_GLASSWARE, GAS_MASK, HAZMAT_SUIT, MACHINED_PARTS, SEALED_CRATE,
                DRYING_RACK_ITEM, CLANDESTINE_LAB_ITEM, PACKAGING_TABLE_ITEM, ARMS_WORKBENCH_ITEM, STASH_CHEST_ITEM));
        return list;
    }

    // -- Les fabriques ----------------------------------------------------------------------------

    private static DeferredBlock<DrugCropBlock> crop(String name, Line line) {
        return BLOCKS.registerBlock(name, properties -> new DrugCropBlock(properties, line),
                properties -> properties.mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak()
                        .sound(SoundType.CROP).pushReaction(PushReaction.DESTROY));
    }

    private static DeferredItem<DrugSeedItem> seeds(String name, DeferredBlock<DrugCropBlock> crop, Line line) {
        return ITEMS.registerItem(name, properties -> new DrugSeedItem(crop.get(), properties, line),
                properties -> properties.component(DataComponents.LORE, lore("tooltip.dohy." + name)));
    }

    private static DeferredItem<Item> simple(String name) {
        return ITEMS.registerItem(name, Item::new);
    }

    private static DeferredItem<Item> product(String name) {
        return ITEMS.registerItem(name, Item::new,
                properties -> properties.component(DataComponents.LORE, lore("tooltip.dohy.product")));
    }

    private static DeferredItem<Item> bag(String name) {
        return ITEMS.registerItem(name, Item::new,
                properties -> properties.stacksTo(32).component(DataComponents.LORE, lore("tooltip.dohy.bag")));
    }

    private static DeferredItem<Item> parcel(String name) {
        return ITEMS.registerItem(name, Item::new, properties -> properties.stacksTo(16).rarity(Rarity.UNCOMMON)
                .component(DataComponents.LORE, lore("tooltip.dohy.parcel")));
    }

    /** Une infobulle d'une ligne, grise et droite. */
    private static ItemLore lore(String... keys) {
        List<Component> lines = new ArrayList<>();
        for (String key : keys) {
            lines.add(Component.translatable(key)
                    .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)));
        }
        return new ItemLore(lines);
    }
}

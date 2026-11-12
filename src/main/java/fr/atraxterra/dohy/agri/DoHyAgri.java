package fr.atraxterra.dohy.agri;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * L'agriculture de DoHy, sous l'espace de noms {@code dohy} et dans son propre
 * onglet — pas dans celui de PalaMod : rien ici ne vient de Paladium.
 *
 * <p>La filiere reprend celle de NationsGlory : dix cereales (neuf ici, plus
 * le ble du jeu), dont six liees a un climat et trois OGM a chercher ; un
 * moulin et une machine a pain electriques ; dix pains ; un elevage a nourrir
 * dont les produits entrent dans les meilleurs pains.
 */
public final class DoHyAgri {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DoHy.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DoHy.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DoHy.MOD_ID);

    /** Les farines et les pains, pour les contrats de production. */
    public static final TagKey<Item> FLOURS =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "flours"));
    public static final TagKey<Item> BREADS_TAG =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "breads"));

    /** Tout ce qu'une mangeoire et un biogenerateur acceptent : les grains d'ici et le ble. */
    public static final TagKey<Item> CEREALS =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "cereals"));

    /** La cle du ble de Minecraft dans les tables de farines. */
    public static final String WHEAT = "wheat";

    // -- Les cereales : une culture, une semence, un grain, une farine ---------------

    private static final Map<Cereal, DeferredBlock<AgriCropBlock>> CROPS = new EnumMap<>(Cereal.class);
    private static final Map<Cereal, DeferredItem<SeedItem>> SEEDS = new EnumMap<>(Cereal.class);
    private static final Map<Cereal, DeferredItem<Item>> GRAINS = new EnumMap<>(Cereal.class);
    private static final Map<String, DeferredItem<Item>> FLOURS = new LinkedHashMap<>();
    private static final Map<Bread, DeferredItem<BreadItem>> BREADS = new EnumMap<>(Bread.class);

    static {
        FLOURS.put(WHEAT, ITEMS.registerItem(WHEAT + "_flour", Item::new));
        for (Cereal cereal : Cereal.values()) {
            DeferredBlock<AgriCropBlock> crop = BLOCKS.registerBlock(cereal.key() + "_crop",
                    properties -> new AgriCropBlock(properties, cereal),
                    properties -> properties.mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak()
                            .sound(SoundType.CROP).pushReaction(PushReaction.DESTROY));
            CROPS.put(cereal, crop);
            SEEDS.put(cereal, ITEMS.registerItem(cereal.key() + "_seeds",
                    properties -> new SeedItem(crop.get(), properties, cereal),
                    properties -> (cereal.gmo() ? properties.rarity(Rarity.UNCOMMON) : properties)
                            .component(DataComponents.LORE, lore("tooltip.dohy.climate."
                                    + (cereal.gmo() ? "gmo" : cereal.climate().key())))));
            GRAINS.put(cereal, ITEMS.registerItem(cereal.key(), Item::new,
                    properties -> cereal.gmo() ? properties.rarity(Rarity.UNCOMMON) : properties));
            FLOURS.put(cereal.key(), ITEMS.registerItem(cereal.key() + "_flour", Item::new,
                    properties -> cereal.gmo() ? properties.rarity(Rarity.UNCOMMON) : properties));
        }
        for (Bread bread : Bread.values()) {
            BREADS.put(bread, ITEMS.registerItem(bread.key(), properties -> bread(bread, properties)));
        }
    }

    // -- L'elevage ---------------------------------------------------------------------

    public static final DeferredItem<Item> CREAM = ITEMS.registerItem("cream", Item::new);
    public static final DeferredItem<Item> LARD = ITEMS.registerItem("lard", Item::new);

    public static final DeferredBlock<FeederBlock> TROUGH = BLOCKS.registerBlock("trough",
            properties -> new FeederBlock(properties, FeederBlock.Kind.TROUGH),
            properties -> properties.mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<FeederBlock> WATER_DISPENSER = BLOCKS.registerBlock("water_dispenser",
            properties -> new FeederBlock(properties, FeederBlock.Kind.WATER),
            properties -> properties.mapColor(MapColor.STONE).strength(2.0f).sound(SoundType.STONE).noOcclusion());
    public static final DeferredItem<BlockItem> TROUGH_ITEM =
            ITEMS.registerSimpleBlockItem(TROUGH, properties -> properties);
    public static final DeferredItem<BlockItem> WATER_DISPENSER_ITEM =
            ITEMS.registerSimpleBlockItem(WATER_DISPENSER, properties -> properties);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FeederBlockEntity>> FEEDER_TYPE =
            TYPES.register("feeder", () -> new BlockEntityType<>(FeederBlockEntity::new,
                    Set.of(TROUGH.get(), WATER_DISPENSER.get())));

    // -- Les machines --------------------------------------------------------------------

    public static final DeferredBlock<GmoMachineBlock> GMO_MACHINE = BLOCKS.registerBlock("gmo_machine",
            GmoMachineBlock::new,
            properties -> properties.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> GMO_MACHINE_ITEM =
            ITEMS.registerSimpleBlockItem(GMO_MACHINE, properties -> properties.rarity(Rarity.UNCOMMON));

    public static final DeferredBlock<AgriMachineBlock> FLOUR_MILL = machine("flour_mill", AgriMachineBlock.Kind.MILL);
    public static final DeferredBlock<AgriMachineBlock> BREAD_MACHINE =
            machine("bread_machine", AgriMachineBlock.Kind.OVEN);
    public static final DeferredBlock<AgriMachineBlock> BIOGENERATOR =
            machine("biogenerator", AgriMachineBlock.Kind.BIOGEN);
    public static final DeferredItem<BlockItem> FLOUR_MILL_ITEM =
            ITEMS.registerSimpleBlockItem(FLOUR_MILL, properties -> properties);
    public static final DeferredItem<BlockItem> BREAD_MACHINE_ITEM =
            ITEMS.registerSimpleBlockItem(BREAD_MACHINE, properties -> properties);
    public static final DeferredItem<BlockItem> BIOGENERATOR_ITEM =
            ITEMS.registerSimpleBlockItem(BIOGENERATOR, properties -> properties);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AgriMachineBlockEntity>> MACHINE_TYPE =
            TYPES.register("agri_machine", () -> new BlockEntityType<>(AgriMachineBlockEntity::new,
                    Set.of(FLOUR_MILL.get(), BREAD_MACHINE.get(), BIOGENERATOR.get())));

    private static DeferredBlock<AgriMachineBlock> machine(String name, AgriMachineBlock.Kind kind) {
        return BLOCKS.registerBlock(name, properties -> new AgriMachineBlock(properties, kind),
                properties -> properties.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL)
                        .lightLevel(state -> state.getValue(AgriMachineBlock.LIT) ? 7 : 0));
    }

    // -- L'onglet ------------------------------------------------------------------------

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("agriculture",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + DoHy.MOD_ID + ".agriculture"))
                    .icon(() -> new ItemStack(GRAINS.get(Cereal.CORN).get()))
                    .displayItems((parameters, output) -> ordered().forEach(item -> output.accept(item.get())))
                    .build());

    private DoHyAgri() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        TABS.register(modBus);
        AgriEffects.register(modBus);
        modBus.addListener(DoHyAgri::onCapabilities);
    }

    /**
     * Ce que les trois machines offrent aux autres mods, sur toutes leurs faces.
     *
     * <p>L'energie : c'est par la que les cables de NTM NEXT — et de tout mod
     * qui parle FE — les trouvent. L'inventaire : les entonnoirs le lisent
     * directement, mais les tuyaux et convoyeurs des mods passent par cette
     * capacite, avec les memes regles de faces (dessous pour sortir, ailleurs
     * pour entrer).
     */
    private static void onCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MACHINE_TYPE.get(), (machine, side) -> machine.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, MACHINE_TYPE.get(), (machine, side) ->
                side == null ? VanillaContainerWrapper.of(machine) : new WorldlyContainerWrapper(machine, side));
    }

    /** L'onglet, dans l'ordre de la filiere : semences, grains, farines, pains, elevage, machines. */
    private static List<Supplier<? extends Item>> ordered() {
        List<Supplier<? extends Item>> list = new ArrayList<>();
        for (Cereal cereal : Cereal.values()) {
            list.add(SEEDS.get(cereal));
        }
        for (Cereal cereal : Cereal.values()) {
            list.add(GRAINS.get(cereal));
        }
        list.addAll(FLOURS.values());
        for (Bread bread : Bread.values()) {
            list.add(BREADS.get(bread));
        }
        list.add(CREAM);
        list.add(LARD);
        list.add(TROUGH_ITEM);
        list.add(WATER_DISPENSER_ITEM);
        list.add(GMO_MACHINE_ITEM);
        list.add(FLOUR_MILL_ITEM);
        list.add(BREAD_MACHINE_ITEM);
        list.add(BIOGENERATOR_ITEM);
        return list;
    }

    public static AgriCropBlock crop(Cereal cereal) {
        return CROPS.get(cereal).get();
    }

    public static Item grain(Cereal cereal) {
        return GRAINS.get(cereal).get();
    }

    public static Item seeds(Cereal cereal) {
        return SEEDS.get(cereal).get();
    }

    public static Item bread(Bread bread) {
        return BREADS.get(bread).get();
    }

    /** Les cles des farines, ble en tete : {@code wheat}, puis une par cereale. */
    public static List<String> flourKeys() {
        return List.copyOf(FLOURS.keySet());
    }

    /** Le grain d'une cle de farine ; {@code wheat} rend le ble de Minecraft. */
    public static Item grain(String key) {
        Cereal cereal = Cereal.byKey(key);
        return cereal == null ? Items.WHEAT : grain(cereal);
    }

    public static Item flour(String key) {
        return FLOURS.getOrDefault(key, FLOURS.get(WHEAT)).get();
    }

    /** Les semences que l'herbe d'un climat peut lacher : celles des cereales qui y poussent. */
    public static List<Item> seedsOf(Climate climate) {
        List<Item> list = new ArrayList<>();
        for (Cereal cereal : Cereal.values()) {
            if (cereal.climate() == climate) {
                list.add(SEEDS.get(cereal).get());
            }
        }
        return list;
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

    /**
     * Un pain. Les trois pains simples se mangent quand on a faim ; les autres
     * a tout moment, puisqu'on les mange pour leur effet.
     */
    private static BreadItem bread(Bread bread, Item.Properties properties) {
        FoodProperties.Builder food = new FoodProperties.Builder().nutrition(bread.nutrition())
                .saturationModifier(0.6f);
        if (!bread.plain()) {
            food.alwaysEdible();
        }
        String[] keys = new String[bread.loreLines()];
        for (int line = 0; line < keys.length; line++) {
            keys[line] = "tooltip.dohy." + bread.key() + "." + (line + 1);
        }
        return new BreadItem(properties.rarity(bread.rarity()).food(food.build())
                .component(DataComponents.LORE, lore(keys)), bread);
    }
}

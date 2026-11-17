package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * L'epice d'Arrakis : le sable qui la porte, le melange qu'on en tire, la
 * moissonneuse qui le recolte, et ce qui rode dessous : le ver des sables, le
 * marteleur qui l'appelle, la dent qu'il perd. Et l'eau, rare : la gourde, le
 * distille qui garde celle du corps, le piege a vent qui la tire de l'air.
 * Tout existe meme sans NTM Space (les objets se gardent d'un serveur a
 * l'autre), mais seule Arrakis a des champs d'epice, des vers et la soif.
 */
public final class ArrakisContent {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DoHy.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, DoHy.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DoHy.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, DoHy.MOD_ID);

    /** Le sable d'epice : il coule comme du sable, et sa poussiere est rouille. */
    public static final DeferredBlock<ColoredFallingBlock> SPICE_SAND = BLOCKS.registerBlock("spice_sand",
            properties -> new ColoredFallingBlock(new ColorRGBA(0xFFC4662E), properties),
            properties -> properties.mapColor(MapColor.COLOR_ORANGE).strength(0.5f).sound(SoundType.SAND)
                    .instrument(NoteBlockInstrument.SNARE));
    public static final DeferredItem<BlockItem> SPICE_SAND_ITEM =
            ITEMS.registerSimpleBlockItem(SPICE_SAND, properties -> properties
                    .component(DataComponents.LORE, lore("tooltip.dohy.spice_sand.1")));

    /**
     * Le melange : ce que le comptoir d'Arrakeen rachete. Mange, il aiguise les
     * sens un moment — on voit la nuit, on va plus vite, on creuse plus vite.
     */
    public static final DeferredItem<Item> MELANGE = ITEMS.registerItem("melange", properties -> new Item(properties
            .rarity(Rarity.UNCOMMON)
            .food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3f).alwaysEdible().build(),
                    Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
                            new MobEffectInstance(MobEffects.NIGHT_VISION, 2400, 0),
                            new MobEffectInstance(MobEffects.SPEED, 1200, 0),
                            new MobEffectInstance(MobEffects.HASTE, 1200, 0)))).build())
            .component(DataComponents.LORE, lore("tooltip.dohy.melange.1", "tooltip.dohy.melange.2"))));

    /** Une machine, pas une bete : hors des categories d'apparition, comme le tracteur. */
    public static final DeferredHolder<EntityType<?>, EntityType<SpiceHarvester>> HARVESTER = ENTITIES.register(
            "spice_harvester", key -> EntityType.Builder.<SpiceHarvester>of(SpiceHarvester::new, MobCategory.MISC)
                    .sized(2.7f, 2.4f)
                    .passengerAttachments(new Vec3(0.0d, 0.95d, -1.3d))
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    public static final DeferredItem<HarvesterItem> HARVESTER_ITEM = ITEMS.registerItem("spice_harvester",
            HarvesterItem::new, properties -> properties.stacksTo(1).rarity(Rarity.RARE)
                    .component(DataComponents.LORE, lore("tooltip.dohy.spice_harvester.1",
                            "tooltip.dohy.spice_harvester.2")));

    /** Le ver des sables : une creature, mais rien qui apparaisse seul ni qu'on puisse tuer. */
    public static final DeferredHolder<EntityType<?>, EntityType<SandWorm>> SANDWORM = ENTITIES.register(
            "sandworm", key -> EntityType.Builder.<SandWorm>of(SandWorm::new, MobCategory.MISC)
                    .sized(2.0f, 2.0f)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    /**
     * Le marteleur : un pieu a masselotte qu'on plante dans le sable. Il frappe une
     * minute, et ses coups appellent les vers — loin de la moissonneuse.
     */
    public static final DeferredBlock<ThumperBlock> THUMPER = BLOCKS.registerBlock("thumper", ThumperBlock::new,
            properties -> properties.mapColor(MapColor.METAL).strength(1.5f, 3.0f).sound(SoundType.METAL)
                    .noOcclusion());
    public static final DeferredItem<BlockItem> THUMPER_ITEM =
            ITEMS.registerSimpleBlockItem(THUMPER, properties -> properties
                    .component(DataComponents.LORE, lore("tooltip.dohy.thumper.1", "tooltip.dohy.thumper.2")));

    /** La dent d'un ver : elle tombe parfois quand il referme sa gueule. Le comptoir la rachete cher. */
    public static final DeferredItem<Item> WORM_TOOTH = ITEMS.registerItem("worm_tooth", properties -> new Item(
            properties.rarity(Rarity.EPIC).stacksTo(16)
                    .component(DataComponents.LORE, lore("tooltip.dohy.worm_tooth.1"))));

    /** Les degats d'un ver : « englouti ». */
    public static final ResourceKey<DamageType> WORM_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "sandworm"));

    // -- L'eau --------------------------------------------------------------------------

    /** L'eau d'une gourde, en gorgees. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WATER =
            COMPONENTS.registerComponentType("canteen_water", builder -> builder
                    .persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** La gourde : huit gorgees, qu'on remplit a un piege a vent (ou a l'eau, hors d'Arrakis). */
    public static final DeferredItem<CanteenItem> CANTEEN = ITEMS.registerItem("canteen", CanteenItem::new,
            properties -> properties.stacksTo(1).rarity(Rarity.UNCOMMON)
                    .component(DataComponents.LORE, lore("tooltip.dohy.canteen.1", "tooltip.dohy.canteen.2")));

    /**
     * Le distille : une combinaison qui garde l'eau du corps (la sueur, le souffle)
     * et la rend a boire. Peu de defense, celle du cuir ; chaque piece portee
     * ralentit la soif, les quatre ensemble de pres de sept fois.
     */
    public static final ArmorMaterial STILLSUIT_MATERIAL = new ArmorMaterial(ArmorMaterials.LEATHER.durability() * 3,
            ArmorMaterials.LEATHER.defense(), ArmorMaterials.LEATHER.enchantmentValue(), SoundEvents.ARMOR_EQUIP_LEATHER,
            0.0f, 0.0f, ArmorMaterials.LEATHER.repairIngredient(), ResourceKey.create(EquipmentAssets.ROOT_ID,
                    Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "stillsuit")));
    public static final DeferredItem<Item> STILLSUIT_MASK = stillsuit("stillsuit_mask", ArmorType.HELMET);
    public static final DeferredItem<Item> STILLSUIT_SUIT = stillsuit("stillsuit_suit", ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> STILLSUIT_LEGGINGS = stillsuit("stillsuit_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<Item> STILLSUIT_BOOTS = stillsuit("stillsuit_boots", ArmorType.BOOTS);

    /** Le piege a vent : il condense l'humidite de l'air, la nuit surtout, tant qu'il voit le ciel. */
    public static final DeferredBlock<WindtrapBlock> WINDTRAP = BLOCKS.registerBlock("windtrap", WindtrapBlock::new,
            properties -> properties.mapColor(MapColor.SAND).strength(2.0f, 6.0f).sound(SoundType.METAL)
                    .noOcclusion().randomTicks());
    public static final DeferredItem<BlockItem> WINDTRAP_ITEM =
            ITEMS.registerSimpleBlockItem(WINDTRAP, properties -> properties.rarity(Rarity.UNCOMMON)
                    .component(DataComponents.LORE, lore("tooltip.dohy.windtrap.1", "tooltip.dohy.windtrap.2")));

    /** La soif : « desseche ». */
    public static final ResourceKey<DamageType> DEHYDRATION = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "dehydration"));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("arrakis",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + DoHy.MOD_ID + ".arrakis"))
                    .icon(() -> new ItemStack(MELANGE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(SPICE_SAND_ITEM.get());
                        output.accept(MELANGE.get());
                        output.accept(HARVESTER_ITEM.get());
                        output.accept(THUMPER_ITEM.get());
                        output.accept(WORM_TOOTH.get());
                        output.accept(CanteenItem.filled(CANTEEN.get()));
                        output.accept(STILLSUIT_MASK.get());
                        output.accept(STILLSUIT_SUIT.get());
                        output.accept(STILLSUIT_LEGGINGS.get());
                        output.accept(STILLSUIT_BOOTS.get());
                        output.accept(WINDTRAP_ITEM.get());
                    })
                    .build());

    private ArrakisContent() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        TABS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(ArrakisContent::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(HARVESTER.get(), SpiceHarvester.createAttributes().build());
        event.put(SANDWORM.get(), SandWorm.createAttributes().build());
    }

    public static boolean isSpice(BlockState state) {
        return state.is(SPICE_SAND.get());
    }

    private static DeferredItem<Item> stillsuit(String name, ArmorType type) {
        return ITEMS.registerItem(name, properties -> new Item(properties.humanoidArmor(STILLSUIT_MATERIAL, type)
                .rarity(Rarity.UNCOMMON)
                .component(DataComponents.LORE, lore("tooltip.dohy.stillsuit.1", "tooltip.dohy." + name))));
    }

    private static ItemLore lore(String... keys) {
        List<Component> lines = new ArrayList<>();
        for (String key : keys) {
            lines.add(Component.translatable(key)
                    .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)));
        }
        return new ItemLore(lines);
    }
}

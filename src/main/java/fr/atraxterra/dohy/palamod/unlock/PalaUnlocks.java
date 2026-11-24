package fr.atraxterra.dohy.palamod.unlock;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Les objets des deblocages de metier qui manquaient (lot du 2026-11-24) :
 * douze verrous de {@code data/jobs.json} visaient des objets que le mod
 * n'avait pas, et ne faisaient rien.
 *
 * <ul>
 *   <li>Fermier : graines d'herbe coloree (45), graine de racinier (50),
 *       hautes herbes colorees (55) ;</li>
 *   <li>Chasseur : bateau en bambou (10), infernal knocker (45), etiquette en
 *       endium (90), et le rituel d'endium (100) — totem, autels, poudre —
 *       avec la boussole de chasseur ;</li>
 *   <li>Alchimiste : la table d'alchimiste (15), ses potions et ses fleches.</li>
 * </ul>
 *
 * <p>La cage a familier (Chasseur 15) vient avec les familiers, au lot suivant.
 */
public final class PalaUnlocks {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, PalaMod.NAMESPACE);
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PalaMod.NAMESPACE);

    // -- Donnees ----------------------------------------------------------------------

    /** La proie suivie par une boussole de chasseur. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Identifier>> PREY =
            COMPONENTS.registerComponentType("hunter_prey", builder -> builder
                    .persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC));

    /** Les creatures nommees a l'etiquette en endium, par joueur. */
    public static final Supplier<AttachmentType<EndiumNametagItem.Named>> NAMED = ATTACHMENTS.register(
            "endium_nametag", () -> AttachmentType.builder(() -> EndiumNametagItem.Named.EMPTY)
                    .serialize(EndiumNametagItem.Named.MAP_CODEC)
                    .copyOnDeath()
                    .build());

    // -- Effets -----------------------------------------------------------------------

    public static final DeferredHolder<MobEffect, UnlockEffect> IMBUE_WITHER = EFFECTS.register(
            "wither_imbue", () -> new UnlockEffect(MobEffectCategory.BENEFICIAL, 0x3A2F2B));
    public static final DeferredHolder<MobEffect, UnlockEffect> IMBUE_FIRE = EFFECTS.register(
            "fire_imbue", () -> new UnlockEffect(MobEffectCategory.BENEFICIAL, 0xE8641C));
    public static final DeferredHolder<MobEffect, UnlockEffect> IMBUE_POISON = EFFECTS.register(
            "poison_imbue", () -> new UnlockEffect(MobEffectCategory.BENEFICIAL, 0x4E9331));
    /** Le givre des fleches glacantes : plus de rebond sur un slimepad ({@code PotionFrost}). */
    public static final DeferredHolder<MobEffect, UnlockEffect> FROST = EFFECTS.register(
            "frost", () -> new UnlockEffect(MobEffectCategory.HARMFUL, 0x9FD8F5));

    // -- Chasseur : le rituel d'endium ---------------------------------------------------

    public static final DeferredBlock<EndiumTotemBlock> ENDIUM_TOTEM = BLOCKS.registerBlock(
            "endium_totem", EndiumTotemBlock::new, properties -> properties
                    .strength(5.0f, 30.0f).sound(SoundType.STONE).mapColor(MapColor.COLOR_BLUE)
                    .requiresCorrectToolForDrops());
    /** {@code BlockSacrificeHotel} : durete 5. */
    public static final DeferredBlock<SacrificeAltarBlock> SACRIFICE_ALTAR = BLOCKS.registerBlock(
            "sacrifice_hotel", SacrificeAltarBlock::new, properties -> properties
                    .strength(5.0f, 30.0f).sound(SoundType.STONE).mapColor(MapColor.COLOR_RED)
                    .requiresCorrectToolForDrops());
    public static final DeferredBlock<PerlinpinpinPowderBlock> PERLINPINPIN_POWDER = BLOCKS.registerBlock(
            "perlinpinpin_power", PerlinpinpinPowderBlock::new, properties -> properties
                    .instabreak().noCollision().sound(SoundType.SAND).mapColor(MapColor.SNOW)
                    .pushReaction(PushReaction.DESTROY));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EndiumTotemBlockEntity>> ENDIUM_TOTEM_TYPE =
            TYPES.register("endium_totem",
                    () -> new BlockEntityType<>(EndiumTotemBlockEntity::new, Set.<Block>of(ENDIUM_TOTEM.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SacrificeAltarBlockEntity>>
            SACRIFICE_ALTAR_TYPE = TYPES.register("sacrifice_hotel",
                    () -> new BlockEntityType<>(SacrificeAltarBlockEntity::new, Set.<Block>of(SACRIFICE_ALTAR.get())));

    public static final DeferredItem<BlockItem> ENDIUM_TOTEM_ITEM =
            ITEMS.registerSimpleBlockItem(ENDIUM_TOTEM, properties -> properties.stacksTo(16));
    public static final DeferredItem<BlockItem> SACRIFICE_ALTAR_ITEM =
            ITEMS.registerSimpleBlockItem(SACRIFICE_ALTAR, properties -> properties.stacksTo(16));
    public static final DeferredItem<BlockItem> PERLINPINPIN_POWDER_ITEM =
            ITEMS.registerSimpleBlockItem(PERLINPINPIN_POWDER, properties -> properties);

    // -- Chasseur : objets --------------------------------------------------------------

    public static final DeferredItem<EndiumNametagItem> ENDIUM_NAMETAG = ITEMS.registerItem(
            "endium_nametag", EndiumNametagItem::new);
    /** {@code ItemInfernalKnocker} : une epee de bois (4 de degats, 59 coups), sans enchantement. */
    public static final DeferredItem<InfernalKnockerItem> INFERNAL_KNOCKER = ITEMS.registerItem(
            "infernal_knocker", properties -> new InfernalKnockerItem(properties
                    .sword(ToolMaterial.WOOD, 3.0f, -2.4f)
                    .component(DataComponents.ENCHANTABLE, null)));

    public static final DeferredHolder<EntityType<?>, EntityType<BambooBoat>> BAMBOO_BOAT = ENTITIES.register(
            "bamboo_boat", key -> EntityType.Builder.<BambooBoat>of(
                            (type, level) -> new BambooBoat(type, level, PalaUnlocks::bambooBoatItem), MobCategory.MISC)
                    .sized(1.375f, 0.5625f)
                    .eyeHeight(0.5625f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));
    public static final DeferredItem<BoatItem> BAMBOO_BOAT_ITEM = ITEMS.registerItem(
            "bamboo_boat", properties -> new BoatItem(BAMBOO_BOAT.get(), properties.stacksTo(1)));

    public static final List<DeferredItem<HunterCompassItem>> HUNTER_COMPASSES = compasses();

    // -- Fermier : herbes colorees -------------------------------------------------------

    public static final Map<GrassColor, DeferredBlock<Block>> COLORED_GRASS = new EnumMap<>(GrassColor.class);
    public static final Map<GrassColor, DeferredBlock<ColoredGrassSeedBlock>> COLORED_GRASS_SEEDS =
            new EnumMap<>(GrassColor.class);
    public static final Map<GrassColor, DeferredBlock<ColoredTallGrassBlock>> COLORED_TALL_GRASS =
            new EnumMap<>(GrassColor.class);
    public static final List<DeferredItem<BlockItem>> GRASS_ITEMS = new ArrayList<>();

    static {
        for (GrassColor color : GrassColor.values()) {
            DeferredBlock<Block> grass = BLOCKS.registerBlock("colored_grass_" + color.key(), Block::new,
                    properties -> properties.strength(0.6f).sound(SoundType.GRASS).mapColor(color.dye()));
            COLORED_GRASS.put(color, grass);
            COLORED_GRASS_SEEDS.put(color, BLOCKS.registerBlock("colored_grass_seeds_" + color.key(),
                    properties -> new ColoredGrassSeedBlock(color, grass::get, properties), properties -> properties
                            .noCollision().instabreak().sound(SoundType.CROP).mapColor(color.dye())
                            .pushReaction(PushReaction.DESTROY)));
            COLORED_TALL_GRASS.put(color, BLOCKS.registerBlock("colored_tall_grass_" + color.key(),
                    ColoredTallGrassBlock::new, properties -> properties
                            .noCollision().instabreak().sound(SoundType.GRASS).mapColor(color.dye())
                            .replaceable().offsetType(net.minecraft.world.level.block.state.BlockBehaviour.OffsetType.XYZ)
                            .pushReaction(PushReaction.DESTROY)));
        }
        for (GrassColor color : GrassColor.values()) {
            GRASS_ITEMS.add(ITEMS.registerSimpleBlockItem(COLORED_GRASS_SEEDS.get(color), properties -> properties));
        }
        for (GrassColor color : GrassColor.values()) {
            GRASS_ITEMS.add(ITEMS.registerSimpleBlockItem(COLORED_TALL_GRASS.get(color), properties -> properties));
        }
        for (GrassColor color : GrassColor.values()) {
            GRASS_ITEMS.add(ITEMS.registerSimpleBlockItem(COLORED_GRASS.get(color), properties -> properties));
        }
    }

    // -- Fermier : le racinier ------------------------------------------------------------

    public static final DeferredBlock<RootSeedsBlock> ROOT_SEEDS = BLOCKS.registerBlock(
            "root_seeds_block", RootSeedsBlock::new, properties -> properties
                    .noCollision().instabreak().sound(SoundType.CROP).mapColor(MapColor.DIRT)
                    .pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<RootLeavesBlock> ROOT_LEAVES = BLOCKS.registerBlock(
            "root_leaves", RootLeavesBlock::new, properties -> properties
                    .strength(0.2f).sound(SoundType.GRASS).mapColor(MapColor.PLANT).noOcclusion().noLootTable());
    public static final DeferredBlock<RotatedPillarBlock> ROOT_LOG = BLOCKS.registerBlock(
            "root_log", RotatedPillarBlock::new, properties -> properties
                    .strength(2.0f).sound(SoundType.WOOD).mapColor(MapColor.WOOD));
    public static final DeferredBlock<Block> ROOT_PLANKS = BLOCKS.registerBlock(
            "root_planks", Block::new, properties -> properties
                    .strength(2.0f, 3.0f).sound(SoundType.WOOD).mapColor(MapColor.WOOD));
    public static final DeferredBlock<RootColumnBlock> ROOT_INFERIOR = BLOCKS.registerBlock(
            "root_inferior", RootColumnBlock::new, properties -> properties
                    .strength(0.2f).sound(SoundType.ROOTS).mapColor(MapColor.DIRT).noOcclusion().noLootTable()
                    .noCollision());
    public static final DeferredBlock<RootColumnBlock> ROOT_LAST = BLOCKS.registerBlock(
            "root_last", RootColumnBlock::new, properties -> properties
                    .strength(0.2f).sound(SoundType.ROOTS).mapColor(MapColor.DIRT).noOcclusion().noLootTable()
                    .noCollision());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RootLeavesBlockEntity>> ROOT_LEAVES_TYPE =
            TYPES.register("root_leaves",
                    () -> new BlockEntityType<>(RootLeavesBlockEntity::new, Set.<Block>of(ROOT_LEAVES.get())));

    public static final DeferredItem<BlockItem> ROOT_SEEDS_ITEM =
            ITEMS.registerSimpleBlockItem(ROOT_SEEDS, properties -> properties);
    public static final DeferredItem<BlockItem> ROOT_LOG_ITEM =
            ITEMS.registerSimpleBlockItem(ROOT_LOG, properties -> properties);
    public static final DeferredItem<BlockItem> ROOT_PLANKS_ITEM =
            ITEMS.registerSimpleBlockItem(ROOT_PLANKS, properties -> properties);

    // -- Alchimiste : la table, ses potions et ses fleches ----------------------------------

    public static final DeferredBlock<AlchemyCreatorBlock> ALCHEMY_CREATOR = BLOCKS.registerBlock(
            "alchemy_creator", AlchemyCreatorBlock::new, properties -> properties
                    .strength(3.0f, 6.0f).sound(SoundType.WOOD).mapColor(MapColor.WOOD).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlchemyCreatorBlockEntity>>
            ALCHEMY_CREATOR_TYPE = TYPES.register("alchemy_creator",
                    () -> new BlockEntityType<>(AlchemyCreatorBlockEntity::new, Set.<Block>of(ALCHEMY_CREATOR.get())));
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> ALCHEMY_CREATOR_MENU =
            MENUS.register("alchemy_creator", () -> new MenuType<>(
                    (containerId, inventory) -> new MachineMenu(PalaUnlocks.ALCHEMY_CREATOR_MENU.get(), containerId,
                            inventory, AlchemyCreatorBlockEntity.LAYOUT),
                    FeatureFlags.VANILLA_SET));
    public static final DeferredItem<BlockItem> ALCHEMY_CREATOR_ITEM =
            ITEMS.registerSimpleBlockItem(ALCHEMY_CREATOR, properties -> properties);

    public static final DeferredItem<ImbuePotionItem> WITHER_POTION = ITEMS.registerItem(
            "wither_imbue", properties -> new ImbuePotionItem(() -> IMBUE_WITHER, "item.palamod.wither_imbue.effect",
                    drink(properties)));
    public static final DeferredItem<ImbuePotionItem> FIRE_POTION = ITEMS.registerItem(
            "fire_imbue", properties -> new ImbuePotionItem(() -> IMBUE_FIRE, "item.palamod.fire_imbue.effect",
                    drink(properties)));
    public static final DeferredItem<ImbuePotionItem> POISON_POTION = ITEMS.registerItem(
            "poison_imbue", properties -> new ImbuePotionItem(() -> IMBUE_POISON, "item.palamod.poison_imbue.effect",
                    drink(properties)));
    /** {@code ItemSplashPotion} : une potion jetable de nausee II, 10 secondes. */
    public static final DeferredItem<SplashPotionItem> SICKNESS_POTION = ITEMS.registerItem(
            "sickness_potion", properties -> new SplashPotionItem(properties.stacksTo(1)
                    .component(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(),
                            Optional.of(0x6B8E23), List.of(new MobEffectInstance(MobEffects.NAUSEA, 200, 1)),
                            Optional.empty()))));

    public static final DeferredItem<PalaArrowItem> POISON_ARROW = ITEMS.registerItem(
            "poison_arrow", properties -> new PalaArrowItem(PalaArrowItem.Kind.POISON, properties));
    public static final DeferredItem<PalaArrowItem> WITHER_ARROW = ITEMS.registerItem(
            "wither_arrow", properties -> new PalaArrowItem(PalaArrowItem.Kind.WITHER, properties));
    public static final DeferredItem<PalaArrowItem> SLOWNESS_ARROW = ITEMS.registerItem(
            "slowness_arrow", properties -> new PalaArrowItem(PalaArrowItem.Kind.SLOWNESS, properties));
    public static final DeferredItem<PalaArrowItem> FROST_ARROW = ITEMS.registerItem(
            "frost_arrow", properties -> new PalaArrowItem(PalaArrowItem.Kind.FROST, properties));

    /** L'ordre de l'onglet. */
    public static final List<DeferredItem<? extends Item>> ORDERED = ordered();

    private PalaUnlocks() {
    }

    private static Item bambooBoatItem() {
        return BAMBOO_BOAT_ITEM.get();
    }

    private static List<DeferredItem<HunterCompassItem>> compasses() {
        List<DeferredItem<HunterCompassItem>> list = new ArrayList<>();
        for (HunterCompassItem.Tier tier : HunterCompassItem.Tier.values()) {
            String name = "hunter_compass_" + (tier == HunterCompassItem.Tier.LEGENDARY ? "legendary"
                    : tier.texture());
            list.add(ITEMS.registerItem(name, properties -> new HunterCompassItem(tier, properties.stacksTo(1))));
        }
        return List.copyOf(list);
    }

    /** Bue en 1,6 seconde, comme une potion ({@code getMaxItemUseDuration} : 32). */
    private static Item.Properties drink(Item.Properties properties) {
        return properties.stacksTo(1).component(DataComponents.CONSUMABLE, Consumable.builder()
                .consumeSeconds(1.6f)
                .animation(ItemUseAnimation.DRINK)
                .sound(SoundEvents.GENERIC_DRINK)
                .hasConsumeParticles(false)
                .build());
    }

    private static List<DeferredItem<? extends Item>> ordered() {
        List<DeferredItem<? extends Item>> list = new ArrayList<>(List.of(
                BAMBOO_BOAT_ITEM, INFERNAL_KNOCKER, ENDIUM_NAMETAG));
        list.addAll(HUNTER_COMPASSES);
        list.addAll(List.of(ENDIUM_TOTEM_ITEM, SACRIFICE_ALTAR_ITEM, PERLINPINPIN_POWDER_ITEM,
                ALCHEMY_CREATOR_ITEM, WITHER_POTION, FIRE_POTION, POISON_POTION, SICKNESS_POTION,
                POISON_ARROW, WITHER_ARROW, SLOWNESS_ARROW, FROST_ARROW,
                ROOT_SEEDS_ITEM, ROOT_LOG_ITEM, ROOT_PLANKS_ITEM));
        list.addAll(GRASS_ITEMS);
        return List.copyOf(list);
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        EFFECTS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        ENTITIES.register(modBus);
        MENUS.register(modBus);
    }
}

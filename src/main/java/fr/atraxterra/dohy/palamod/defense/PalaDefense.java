package fr.atraxterra.dohy.palamod.defense;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.pvp.PalaPvp;
import fr.atraxterra.dohy.palamod.pvp.SpikeBlock;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Les objets de defense de la faction (chapitre 22, lot 22c) : obsidiennes
 * renforcee et piegees, spikes et obsidiennes a spike, blocs d'obsidienne,
 * Fake Water, eponges, brouilleur, enclumes, amulettes, outil magique,
 * pistolet a liquide, home remover et pierre d'aggro.
 *
 * <p>Releve dans {@code PRegister_Blocks}, {@code PPRegisterer},
 * {@code ObsiEffect} et ses effets, {@code BlockAnvil}, {@code BlockSpike},
 * {@code ItemAmulet} et {@code PRegister_Craft}. Les noms sont ecrits en toutes
 * lettres pour {@code check_assets.py}. Les verrous de l'arbre de faction sont
 * dans {@link DefenseGate}.
 */
public final class PalaDefense {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, PalaMod.NAMESPACE);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PalaMod.NAMESPACE);

    /** Ce que l'outil magique demonte : obsidiennes de defense, enclumes, blocs renforces. */
    public static final TagKey<Block> MAGICAL_REMOVABLE = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "magical_tool_removable"));

    /**
     * La porte et la trappe en obsidienne s'ouvrent a la main, avec les sons du
     * fer. Un jeu de blocs propre plutot que celui du cuivre : l'obsidienne ne
     * sonne pas comme une porte en cuivre.
     */
    public static final BlockSetType OBSIDIAN_SET = BlockSetType.register(new BlockSetType("palamod_obsidian",
            true, false, false, BlockSetType.PressurePlateSensitivity.MOBS, SoundType.STONE,
            SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE,
            SoundEvents.IRON_TRAPDOOR_OPEN, SoundEvents.STONE_PRESSURE_PLATE_CLICK_OFF,
            SoundEvents.STONE_PRESSURE_PLATE_CLICK_ON, SoundEvents.STONE_BUTTON_CLICK_OFF,
            SoundEvents.STONE_BUTTON_CLICK_ON));

    // -- Composants et pieces jointes -------------------------------------------

    /** Les amulettes fondues dans un plastron ({@code amulets} de 1.7.10). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> AMULETS =
            COMPONENTS.registerComponentType("amulets", builder -> builder
                    .persistent(Codec.STRING.listOf())
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PistolLoad>> PISTOL_LOAD =
            COMPONENTS.registerComponentType("pistol_load", builder -> builder
                    .persistent(PistolLoad.CODEC).networkSynchronized(PistolLoad.STREAM_CODEC));

    /** Sur un chunk : jusqu'a quand le home remover y empeche les monstres de naitre (temps de jeu). */
    public static final Supplier<AttachmentType<Long>> SPAWN_WARD = ATTACHMENTS.register("spawn_ward",
            () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG.fieldOf("until"), until -> until > 0L)
                    .build());

    // -- Obsidiennes ------------------------------------------------------------

    /** {@code BlockHardenedObsidian} et {@code ObsiEffect} : durete -1, resistance 100 (soit 60). */
    private static BlockBehaviour.Properties defense(BlockBehaviour.Properties properties) {
        return properties.strength(-1.0f, 60.0f).sound(SoundType.STONE).mapColor(MapColor.COLOR_BLACK)
                .pushReaction(PushReaction.BLOCK);
    }

    /** Les blocs d'obsidienne decoratifs : ceux de l'obsidienne vanilla. */
    private static BlockBehaviour.Properties obsidian(BlockBehaviour.Properties properties) {
        return properties.strength(50.0f, 1200.0f).sound(SoundType.STONE).mapColor(MapColor.COLOR_BLACK)
                .requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<DefenseObsidianBlock> HARDENED_OBSIDIAN = BLOCKS.registerBlock(
            "hardened_obsidian", properties -> new DefenseObsidianBlock(ObsidianEffect.NONE, properties),
            PalaDefense::defense);
    public static final DeferredBlock<StairBlock> HARDENED_OBSIDIAN_STAIRS = BLOCKS.registerBlock(
            "hardened_obsidian_stairs", properties -> new StairBlock(Blocks.OBSIDIAN.defaultBlockState(), properties),
            PalaDefense::defense);
    public static final DeferredBlock<SlabBlock> HARDENED_OBSIDIAN_SLAB = BLOCKS.registerBlock(
            "hardened_obsidian_slab", SlabBlock::new, PalaDefense::defense);
    public static final DeferredBlock<StairBlock> OBSIDIAN_STAIRS = BLOCKS.registerBlock(
            "obsidian_stairs", properties -> new StairBlock(Blocks.OBSIDIAN.defaultBlockState(), properties),
            PalaDefense::obsidian);
    public static final DeferredBlock<SlabBlock> OBSIDIAN_SLAB = BLOCKS.registerBlock(
            "obsidian_slab", SlabBlock::new, PalaDefense::obsidian);
    public static final DeferredBlock<ObsidianDoorBlock> OBSIDIAN_DOOR = BLOCKS.registerBlock(
            "obsidian_door", properties -> new ObsidianDoorBlock(OBSIDIAN_SET, properties),
            properties -> obsidian(properties).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<TrapDoorBlock> OBSIDIAN_TRAPDOOR = BLOCKS.registerBlock(
            "obsidian_trapdoor", properties -> new TrapDoorBlock(OBSIDIAN_SET, properties),
            properties -> obsidian(properties).noOcclusion());

    public static final DeferredBlock<DefenseObsidianBlock> POISON_OBSIDIAN = effect("poison_obsidian", ObsidianEffect.POISON);
    public static final DeferredBlock<DefenseObsidianBlock> BOOM_OBSIDIAN = effect("boom_obsidian", ObsidianEffect.BOOM);
    public static final DeferredBlock<DefenseObsidianBlock> MEGA_BOOM_OBSIDIAN = effect("mega_boom_obsidian", ObsidianEffect.MEGA_BOOM);
    public static final DeferredBlock<DefenseObsidianBlock> LAVA_OBSIDIAN = effect("lava_obsidian", ObsidianEffect.LAVA);
    public static final DeferredBlock<DefenseObsidianBlock> SULFURIC_OBSIDIAN = effect("sulfuric_obsidian", ObsidianEffect.SULFURIC);
    public static final DeferredBlock<DefenseObsidianBlock> KNOCKBACK_OBSIDIAN = effect("knockback_obsidian", ObsidianEffect.KNOCKBACK);
    public static final DeferredBlock<DefenseObsidianBlock> FROZEN_OBSIDIAN = effect("frozen_obsidian", ObsidianEffect.FROZEN);
    public static final DeferredBlock<DefenseObsidianBlock> ANTI_AGGRO_OBSIDIAN = effect("anti_aggro_obsidian", ObsidianEffect.ANTI_AGGRO);
    public static final DeferredBlock<DefenseObsidianBlock> BIG_OBSIDIAN = effect("big_obsidian", ObsidianEffect.BIG);
    public static final DeferredBlock<DefenseObsidianBlock> WITHER_OBSIDIAN = effect("wither_obsidian", ObsidianEffect.WITHER);
    public static final DeferredBlock<DirectionalObsidianBlock> SLIME_OBSIDIAN = BLOCKS.registerBlock(
            "slime_obsidian", properties -> new DirectionalObsidianBlock(ObsidianEffect.SLIME, properties),
            PalaDefense::defense);
    public static final DeferredBlock<RedstoneObsidianBlock> REDSTONE_OBSIDIAN = BLOCKS.registerBlock(
            "redstone_obsidian", RedstoneObsidianBlock::new, PalaDefense::defense);
    public static final DeferredBlock<ObserverBlock> OBSERVER_OBSIDIAN = BLOCKS.registerBlock(
            "observer_obsidian", ObserverBlock::new, PalaDefense::defense);

    private static DeferredBlock<DefenseObsidianBlock> effect(String name, ObsidianEffect effect) {
        return BLOCKS.registerBlock(name, properties -> new DefenseObsidianBlock(effect, properties),
                PalaDefense::defense);
    }

    // -- Spikes -----------------------------------------------------------------

    /**
     * {@code BlockSpike} : les degats de chaque metal (2, 5, 7, 10, 10, 12 ; le
     * paladium, 15, est dans {@link PalaPvp}).
     */
    public static final DeferredBlock<SpikeBlock> WOOD_SPIKE = BLOCKS.registerBlock(
            "wood_spike", properties -> new SpikeBlock(2.0f, properties), properties -> properties
                    .strength(2.0f, 3.0f).sound(SoundType.WOOD).mapColor(MapColor.WOOD).noOcclusion());
    public static final DeferredBlock<SpikeBlock> IRON_SPIKE = BLOCKS.registerBlock(
            "iron_spike", properties -> new SpikeBlock(5.0f, properties), PalaDefense::metalSpike);
    public static final DeferredBlock<SpikeBlock> GOLD_SPIKE = BLOCKS.registerBlock(
            "gold_spike", properties -> new SpikeBlock(7.0f, properties), PalaDefense::metalSpike);
    public static final DeferredBlock<SpikeBlock> DIAMOND_SPIKE = BLOCKS.registerBlock(
            "diamond_spike", properties -> new SpikeBlock(10.0f, properties), PalaDefense::metalSpike);
    public static final DeferredBlock<SpikeBlock> AMETHYST_SPIKE = BLOCKS.registerBlock(
            "amethyst_spike", properties -> new SpikeBlock(10.0f, properties), PalaDefense::metalSpike);
    public static final DeferredBlock<SpikeBlock> TITANE_SPIKE = BLOCKS.registerBlock(
            "titane_spike", properties -> new SpikeBlock(12.0f, properties), PalaDefense::metalSpike);

    private static BlockBehaviour.Properties metalSpike(BlockBehaviour.Properties properties) {
        return properties.strength(8.0f, 60.0f).sound(SoundType.METAL).mapColor(MapColor.METAL)
                .requiresCorrectToolForDrops().noOcclusion();
    }

    /** {@code SpikeObsi} : une obsidienne piegee par metal, le spike apparait quand elle tombe. */
    public static final DeferredBlock<SpikeObsidianBlock> WOOD_SPIKE_OBSIDIAN = spikeObsidian("wood_spike_obsidian", WOOD_SPIKE);
    public static final DeferredBlock<SpikeObsidianBlock> IRON_SPIKE_OBSIDIAN = spikeObsidian("iron_spike_obsidian", IRON_SPIKE);
    public static final DeferredBlock<SpikeObsidianBlock> GOLD_SPIKE_OBSIDIAN = spikeObsidian("gold_spike_obsidian", GOLD_SPIKE);
    public static final DeferredBlock<SpikeObsidianBlock> DIAMOND_SPIKE_OBSIDIAN = spikeObsidian("diamond_spike_obsidian", DIAMOND_SPIKE);
    public static final DeferredBlock<SpikeObsidianBlock> AMETHYST_SPIKE_OBSIDIAN = spikeObsidian("amethyst_spike_obsidian", AMETHYST_SPIKE);
    public static final DeferredBlock<SpikeObsidianBlock> TITANE_SPIKE_OBSIDIAN = spikeObsidian("titane_spike_obsidian", TITANE_SPIKE);
    public static final DeferredBlock<SpikeObsidianBlock> PALADIUM_SPIKE_OBSIDIAN = spikeObsidian("paladium_spike_obsidian", PalaPvp.PALADIUM_SPIKE);

    private static DeferredBlock<SpikeObsidianBlock> spikeObsidian(String name, Supplier<? extends Block> spike) {
        return BLOCKS.registerBlock(name, properties -> new SpikeObsidianBlock(spike, properties), PalaDefense::defense);
    }

    // -- Enclumes, eponges, brouilleur ------------------------------------------

    /** {@code BlockAnvil} : durete -1, et 0, 1 ou 2 coups de wither encaisses. */
    public static final DeferredBlock<DefenseAnvilBlock> AMETHYST_ANVIL = anvil("amethyst_anvil", 0);
    public static final DeferredBlock<DefenseAnvilBlock> TITANE_ANVIL = anvil("titane_anvil", 1);
    public static final DeferredBlock<DefenseAnvilBlock> PALADIUM_ANVIL = anvil("paladium_anvil", 2);

    private static DeferredBlock<DefenseAnvilBlock> anvil(String name, int resistance) {
        return BLOCKS.registerBlock(name, properties -> new DefenseAnvilBlock(resistance, properties),
                properties -> properties.strength(-1.0f, 1200.0f).sound(SoundType.ANVIL).mapColor(MapColor.METAL)
                        .pushReaction(PushReaction.BLOCK).noOcclusion());
    }

    public static final DeferredBlock<LavaSpongeBlock> LAVA_SPONGE = BLOCKS.registerBlock(
            "lava_sponge", LavaSpongeBlock::new, properties -> properties
                    .strength(0.6f).sound(SoundType.SPONGE).mapColor(MapColor.COLOR_ORANGE));
    /** Pleine de lave, elle eclaire un peu, et se vide au four. */
    public static final DeferredBlock<Block> WET_LAVA_SPONGE = BLOCKS.registerBlock(
            "wet_lava_sponge", Block::new, properties -> properties
                    .strength(0.6f).sound(SoundType.WET_SPONGE).mapColor(MapColor.COLOR_ORANGE)
                    .lightLevel(state -> 10));
    /** Un ingredient : les TNT du pillage (lot 22d) la demandent. */
    public static final DeferredBlock<Block> COMPRESSED_SPONGE = BLOCKS.registerBlock(
            "compressed_sponge", Block::new, properties -> properties
                    .strength(1.2f).sound(SoundType.SPONGE).mapColor(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<CoordinateJammerBlock> COORDINATE_JAMMER = BLOCKS.registerBlock(
            "coordinate_jammer", CoordinateJammerBlock::new, properties -> properties
                    .strength(5.0f, 6.0f).sound(SoundType.METAL).mapColor(MapColor.COLOR_RED)
                    .requiresCorrectToolForDrops());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoordinateJammerBlockEntity>>
            COORDINATE_JAMMER_ENTITY = BLOCK_ENTITIES.register("coordinate_jammer",
                    () -> new BlockEntityType<>(CoordinateJammerBlockEntity::new, Set.<Block>of(COORDINATE_JAMMER.get())));

    // -- Fake Water -------------------------------------------------------------

    /** L'eau sulfurique : la physique de l'eau, sans source infinie. */
    public static final DeferredHolder<FluidType, FluidType> FAKE_WATER_TYPE = FLUID_TYPES.register(
            "fake_water", () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("block.palamod.fake_water")
                    .isWaterLike(true)
                    .canSwim(true)
                    .canDrown(true)
                    .canExtinguish(true)
                    .canHydrate(false)
                    .supportsBoating(true)
                    .canConvertToSource(false)
                    .density(1000)
                    .viscosity(1000)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    // Comme l'eau angelique : les proprietes citent des champs declares plus
    // bas, elles sont donc construites dans les fournisseurs.
    public static final DeferredHolder<Fluid, FlowingFluid> FAKE_WATER = FLUIDS.register(
            "fake_water", () -> new BaseFlowingFluid.Source(new BaseFlowingFluid.Properties(
                    PalaDefense.FAKE_WATER_TYPE, PalaDefense.FAKE_WATER, PalaDefense.FLOWING_FAKE_WATER)
                    .bucket(PalaDefense.FAKE_WATER_BUCKET).block(PalaDefense.FAKE_WATER_BLOCK)));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_FAKE_WATER = FLUIDS.register(
            "flowing_fake_water", () -> new BaseFlowingFluid.Flowing(new BaseFlowingFluid.Properties(
                    PalaDefense.FAKE_WATER_TYPE, PalaDefense.FAKE_WATER, PalaDefense.FLOWING_FAKE_WATER)
                    .bucket(PalaDefense.FAKE_WATER_BUCKET).block(PalaDefense.FAKE_WATER_BLOCK)));
    public static final DeferredBlock<FakeWaterBlock> FAKE_WATER_BLOCK = BLOCKS.registerBlock(
            "fake_water", properties -> new FakeWaterBlock(FAKE_WATER.get(), properties), properties -> properties
                    .mapColor(MapColor.WATER).replaceable().noCollision().strength(100.0f)
                    .pushReaction(PushReaction.DESTROY).noLootTable().liquid());
    public static final DeferredItem<BucketItem> FAKE_WATER_BUCKET = ITEMS.registerItem(
            "fake_water_bucket", properties -> new BucketItem(FAKE_WATER.get(), properties.stacksTo(1)));

    // -- Objets -----------------------------------------------------------------

    public static final DeferredItem<MagicalToolItem> MAGICAL_TOOL = ITEMS.registerItem(
            "magical_tool", properties -> new MagicalToolItem(properties.durability(10_000)));
    public static final DeferredItem<PistolTankItem> PISTOL_TANK = ITEMS.registerItem(
            "pistol_tank", properties -> new PistolTankItem(properties.stacksTo(1)));
    public static final DeferredItem<HomeRemoverItem> HOME_REMOVER = ITEMS.registerItem(
            "home_remover", properties -> new HomeRemoverItem(properties.stacksTo(16)));
    public static final DeferredItem<AggroStoneItem> AGGRO_STONE = ITEMS.registerItem(
            "aggro_stone", properties -> new AggroStoneItem(properties.durability(4)));
    /** L'ingredient de la pierre d'aggro, que les withers du pillage (lot 22d) demanderont aussi. */
    public static final DeferredItem<Item> WITHER_SKULL_FRAGMENT = ITEMS.registerItem("wither_skull_fragment", Item::new);

    public static final DeferredItem<AmuletItem> VOYANCE_AMULET = amulet("voyance_amulet", AmuletType.VOYANCE);
    public static final DeferredItem<AmuletItem> DISCRETION_AMULET = amulet("discretion_amulet", AmuletType.DISCRETION);
    public static final DeferredItem<AmuletItem> COMBAT_AMULET = amulet("combat_amulet", AmuletType.COMBAT);
    public static final DeferredItem<AmuletItem> MAGMA_AMULET = amulet("magma_amulet", AmuletType.MAGMA);
    public static final DeferredItem<AmuletItem> HUNTER_AMULET = amulet("hunter_amulet", AmuletType.HUNTER);

    private static DeferredItem<AmuletItem> amulet(String name, AmuletType type) {
        return ITEMS.registerItem(name, properties -> new AmuletItem(type, properties.stacksTo(1)));
    }

    // -- Onglet -----------------------------------------------------------------

    /** Les blocs a objet simple, dans l'ordre de l'onglet. La porte a le sien, sur deux blocs. */
    public static final List<DeferredBlock<? extends Block>> ORDERED_BLOCKS = List.of(
            HARDENED_OBSIDIAN, HARDENED_OBSIDIAN_STAIRS, HARDENED_OBSIDIAN_SLAB,
            OBSIDIAN_STAIRS, OBSIDIAN_SLAB, OBSIDIAN_TRAPDOOR,
            POISON_OBSIDIAN, BOOM_OBSIDIAN, MEGA_BOOM_OBSIDIAN, LAVA_OBSIDIAN, SULFURIC_OBSIDIAN,
            KNOCKBACK_OBSIDIAN, FROZEN_OBSIDIAN, ANTI_AGGRO_OBSIDIAN, BIG_OBSIDIAN, SLIME_OBSIDIAN,
            WITHER_OBSIDIAN, REDSTONE_OBSIDIAN, OBSERVER_OBSIDIAN,
            WOOD_SPIKE, IRON_SPIKE, GOLD_SPIKE, DIAMOND_SPIKE, AMETHYST_SPIKE, TITANE_SPIKE,
            WOOD_SPIKE_OBSIDIAN, IRON_SPIKE_OBSIDIAN, GOLD_SPIKE_OBSIDIAN, DIAMOND_SPIKE_OBSIDIAN,
            AMETHYST_SPIKE_OBSIDIAN, TITANE_SPIKE_OBSIDIAN, PALADIUM_SPIKE_OBSIDIAN,
            AMETHYST_ANVIL, TITANE_ANVIL, PALADIUM_ANVIL,
            LAVA_SPONGE, WET_LAVA_SPONGE, COMPRESSED_SPONGE, COORDINATE_JAMMER);
    private static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = ORDERED_BLOCKS.stream()
            .map(block -> ITEMS.registerSimpleBlockItem(block, properties -> properties)).toList();
    public static final DeferredItem<DoubleHighBlockItem> OBSIDIAN_DOOR_ITEM = ITEMS.registerItem(
            "obsidian_door", properties -> new DoubleHighBlockItem(OBSIDIAN_DOOR.get(),
                    properties.useBlockDescriptionPrefix()));

    public static final List<DeferredItem<? extends Item>> ORDERED_ITEMS = List.of(
            OBSIDIAN_DOOR_ITEM, FAKE_WATER_BUCKET, MAGICAL_TOOL, PISTOL_TANK, HOME_REMOVER, AGGRO_STONE,
            WITHER_SKULL_FRAGMENT, VOYANCE_AMULET, DISCRETION_AMULET, COMBAT_AMULET, MAGMA_AMULET, HUNTER_AMULET);

    private PalaDefense() {
    }

    /** Tout l'onglet : les blocs, puis les objets. */
    public static List<Supplier<? extends Item>> tab() {
        List<Supplier<? extends Item>> all = new ArrayList<>(BLOCK_ITEMS);
        all.addAll(ORDERED_ITEMS);
        return all;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}

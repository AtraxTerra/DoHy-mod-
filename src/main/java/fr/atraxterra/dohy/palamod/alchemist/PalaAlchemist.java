package fr.atraxterra.dohy.palamod.alchemist;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Set;

/**
 * Les objets du metier d'Alchimiste : la seve et ses contenants, le chaudron,
 * les fleurs et leurs pollens, les portails et leurs cles, la bibliotheque
 * mystique. Les arbres sont dans {@link PalaWoods}.
 *
 * <p>Identifiants de {@code data/jobs.json} : {@code gold_tank} (10),
 * {@code cauldron} et {@code cauldron_core} (20), {@code amethyst_portal_block}
 * (30)... Les noms sont ecrits en toutes lettres pour {@code check_assets.py}.
 */
public final class PalaAlchemist {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);

    // -- Composants ------------------------------------------------------------
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FlaskSap>> FLASK_SAP =
            COMPONENTS.registerComponentType("flask_sap", builder -> builder
                    .persistent(FlaskSap.CODEC).networkSynchronized(FlaskSap.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TankSap>> TANK_SAP =
            COMPONENTS.registerComponentType("tank_sap", builder -> builder
                    .persistent(TankSap.CODEC).networkSynchronized(TankSap.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> EXTRACTOR_USES =
            COMPONENTS.registerComponentType("extractor_uses", builder -> builder
                    .persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** {@code totalSeve} de la cle. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> KEY_SAP =
            COMPONENTS.registerComponentType("key_sap", builder -> builder
                    .persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    // -- Seve -----------------------------------------------------------------
    public static final DeferredItem<FlaskItem> FLASK = ITEMS.registerItem(
            "flask", properties -> new FlaskItem(properties.stacksTo(16)));

    public static final DeferredBlock<ExtractorBlock> EXTRACTOR_BLOCK = BLOCKS.registerBlock(
            "extractor", ExtractorBlock::new, properties -> properties
                    .strength(2.0f, 1.8f).sound(SoundType.STONE).noOcclusion().mapColor(MapColor.STONE));
    public static final DeferredItem<ExtractorItem> EXTRACTOR_ITEM = ITEMS.registerItem(
            "extractor", properties -> new ExtractorItem(EXTRACTOR_BLOCK.get(), properties.useBlockDescriptionPrefix()));

    // BlockTank : fer, durete 4, setResistance(2).
    public static final DeferredBlock<TankBlock> GOLD_TANK = BLOCKS.registerBlock(
            "gold_tank", properties -> new TankBlock(TankTier.GOLD, properties), PalaAlchemist::tank);
    public static final DeferredBlock<TankBlock> AMETHYST_TANK = BLOCKS.registerBlock(
            "amethyst_tank", properties -> new TankBlock(TankTier.AMETHYST, properties), PalaAlchemist::tank);
    public static final DeferredBlock<TankBlock> TITANE_TANK = BLOCKS.registerBlock(
            "titane_tank", properties -> new TankBlock(TankTier.TITANE, properties), PalaAlchemist::tank);
    public static final DeferredBlock<TankBlock> PALADIUM_TANK = BLOCKS.registerBlock(
            "paladium_tank", properties -> new TankBlock(TankTier.PALADIUM, properties), PalaAlchemist::tank);

    public static final DeferredItem<TankItem> GOLD_TANK_ITEM = ITEMS.registerItem(
            "gold_tank", properties -> new TankItem(GOLD_TANK.get(), properties.useBlockDescriptionPrefix()));
    public static final DeferredItem<TankItem> AMETHYST_TANK_ITEM = ITEMS.registerItem(
            "amethyst_tank", properties -> new TankItem(AMETHYST_TANK.get(), properties.useBlockDescriptionPrefix()));
    public static final DeferredItem<TankItem> TITANE_TANK_ITEM = ITEMS.registerItem(
            "titane_tank", properties -> new TankItem(TITANE_TANK.get(), properties.useBlockDescriptionPrefix()));
    public static final DeferredItem<TankItem> PALADIUM_TANK_ITEM = ITEMS.registerItem(
            "paladium_tank", properties -> new TankItem(PALADIUM_TANK.get(), properties.useBlockDescriptionPrefix()));

    // -- Chaudron --------------------------------------------------------------
    /** {@code ItemLightningPotion} : par un, sans effet a boire. */
    public static final DeferredItem<Item> LIGHTNING_POTION = ITEMS.registerItem(
            "lightning_potion", properties -> new Item(properties.stacksTo(1)));

    // BlockBase : roche, durete 8, setResistance(2).
    public static final DeferredBlock<Block> CAULDRON = BLOCKS.registerBlock(
            "cauldron", Block::new, properties -> properties
                    .strength(8.0f, 1.2f).sound(SoundType.STONE).mapColor(MapColor.COLOR_GRAY)
                    .requiresCorrectToolForDrops());
    public static final DeferredBlock<CauldronCoreBlock> CAULDRON_CORE = BLOCKS.registerBlock(
            "cauldron_core", CauldronCoreBlock::new, properties -> properties
                    .strength(8.0f, 1.2f).sound(SoundType.STONE).mapColor(MapColor.COLOR_PURPLE)
                    .requiresCorrectToolForDrops());

    // -- Fleurs : ce que le chaudron tire des fleurs vanilla ------------------
    public static final DeferredBlock<FlowerBlock> HARPAGOPHYTUM_FLOWER = BLOCKS.registerBlock(
            "harpagophytum_flower", PalaAlchemist::flower, PalaAlchemist::flowerProperties);
    public static final DeferredBlock<FlowerBlock> SAUGE_FLOWER = BLOCKS.registerBlock(
            "sauge_flower", PalaAlchemist::flower, PalaAlchemist::flowerProperties);
    public static final DeferredBlock<FlowerBlock> CLATHRUSARCHERI_FLOWER = BLOCKS.registerBlock(
            "clathrusarcheri_flower", PalaAlchemist::flower, PalaAlchemist::flowerProperties);
    public static final DeferredBlock<FlowerBlock> ENDIUM_FLOWER = BLOCKS.registerBlock(
            "endium_flower", PalaAlchemist::flower, PalaAlchemist::flowerProperties);
    public static final DeferredBlock<FlowerBlock> PALADIUM_FLOWER = BLOCKS.registerBlock(
            "paladium_flower", PalaAlchemist::flower, PalaAlchemist::flowerProperties);
    public static final DeferredBlock<FlowerBlock> MINERAL_FLOWER = BLOCKS.registerBlock(
            "mineral_flower", PalaAlchemist::flower, PalaAlchemist::flowerProperties);

    public static final DeferredItem<BlockItem> HARPAGOPHYTUM_FLOWER_ITEM = ITEMS.registerSimpleBlockItem(HARPAGOPHYTUM_FLOWER);
    public static final DeferredItem<BlockItem> SAUGE_FLOWER_ITEM = ITEMS.registerSimpleBlockItem(SAUGE_FLOWER);
    public static final DeferredItem<BlockItem> CLATHRUSARCHERI_FLOWER_ITEM = ITEMS.registerSimpleBlockItem(CLATHRUSARCHERI_FLOWER);
    public static final DeferredItem<BlockItem> ENDIUM_FLOWER_ITEM = ITEMS.registerSimpleBlockItem(ENDIUM_FLOWER);
    public static final DeferredItem<BlockItem> PALADIUM_FLOWER_ITEM = ITEMS.registerSimpleBlockItem(PALADIUM_FLOWER);
    public static final DeferredItem<BlockItem> MINERAL_FLOWER_ITEM = ITEMS.registerSimpleBlockItem(MINERAL_FLOWER);

    public static final DeferredItem<Item> AMETHYST_POLLEN = ITEMS.registerItem("amethyst_pollen", Item::new);
    public static final DeferredItem<Item> TITANE_POLLEN = ITEMS.registerItem("titane_pollen", Item::new);
    public static final DeferredItem<Item> PALADIUM_POLLEN = ITEMS.registerItem("paladium_pollen", Item::new);
    public static final DeferredItem<Item> ENDIUM_POLLEN = ITEMS.registerItem("endium_pollen", Item::new);

    // -- Portails --------------------------------------------------------------
    public static final DeferredBlock<PortalControllerBlock> AMETHYST_PORTAL_BLOCK = BLOCKS.registerBlock(
            "amethyst_portal_block", properties -> new PortalControllerBlock(PortalTier.AMETHYST, properties),
            PalaAlchemist::rock);
    public static final DeferredBlock<PortalControllerBlock> TITANE_PORTAL_BLOCK = BLOCKS.registerBlock(
            "titane_portal_block", properties -> new PortalControllerBlock(PortalTier.TITANE, properties),
            PalaAlchemist::rock);
    public static final DeferredBlock<PortalControllerBlock> PALADIUM_PORTAL_BLOCK = BLOCKS.registerBlock(
            "paladium_portal_block", properties -> new PortalControllerBlock(PortalTier.PALADIUM, properties),
            PalaAlchemist::rock);
    public static final DeferredBlock<PortalControllerBlock> ENDIUM_PORTAL_BLOCK = BLOCKS.registerBlock(
            "endium_portal_block", properties -> new PortalControllerBlock(PortalTier.ENDIUM, properties),
            PalaAlchemist::rock);
    public static final DeferredBlock<Block> ENDIUM_ANGLE_PORTAL_BLOCK = BLOCKS.registerBlock(
            "endium_angle_portal_block", Block::new, PalaAlchemist::rock);
    /** La surface du portail ouvert : incassable, sans objet ni butin. */
    public static final DeferredBlock<PortalSurfaceBlock> ALCHEMIST_PORTAL = BLOCKS.registerBlock(
            "alchemist_portal", PortalSurfaceBlock::new, properties -> properties
                    .strength(-1.0f, 3_600_000.0f).noCollision().randomTicks()
                    .sound(SoundType.GLASS).mapColor(MapColor.COLOR_PURPLE).pushReaction(PushReaction.BLOCK));

    public static final DeferredItem<PortalKeyItem> AMETHYST_PORTAL_KEY = ITEMS.registerItem(
            "amethyst_portal_key", properties -> new PortalKeyItem(PortalTier.AMETHYST, properties));
    public static final DeferredItem<PortalKeyItem> TITANE_PORTAL_KEY = ITEMS.registerItem(
            "titane_portal_key", properties -> new PortalKeyItem(PortalTier.TITANE, properties));
    public static final DeferredItem<PortalKeyItem> PALADIUM_PORTAL_KEY = ITEMS.registerItem(
            "paladium_portal_key", properties -> new PortalKeyItem(PortalTier.PALADIUM, properties));
    public static final DeferredItem<PortalKeyItem> ENDIUM_PORTAL_KEY = ITEMS.registerItem(
            "endium_portal_key", properties -> new PortalKeyItem(PortalTier.ENDIUM, properties));
    public static final DeferredItem<Item> ENDIUM_HEART = ITEMS.registerItem("endium_heart", Item::new);

    // -- Enchantement ----------------------------------------------------------
    public static final DeferredBlock<MysticalBookshelfBlock> MYSTICAL_BOOKSHELF = BLOCKS.registerBlock(
            "mystical_bookshelf", MysticalBookshelfBlock::new, PalaAlchemist::rock);

    // -- Objets des blocs --------------------------------------------------------
    public static final DeferredItem<BlockItem> CAULDRON_ITEM = ITEMS.registerSimpleBlockItem(CAULDRON);
    public static final DeferredItem<BlockItem> CAULDRON_CORE_ITEM = ITEMS.registerSimpleBlockItem(CAULDRON_CORE);
    public static final DeferredItem<BlockItem> AMETHYST_PORTAL_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(AMETHYST_PORTAL_BLOCK);
    public static final DeferredItem<BlockItem> TITANE_PORTAL_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(TITANE_PORTAL_BLOCK);
    public static final DeferredItem<BlockItem> PALADIUM_PORTAL_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(PALADIUM_PORTAL_BLOCK);
    public static final DeferredItem<BlockItem> ENDIUM_PORTAL_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(ENDIUM_PORTAL_BLOCK);
    public static final DeferredItem<BlockItem> ENDIUM_ANGLE_PORTAL_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(ENDIUM_ANGLE_PORTAL_BLOCK);
    public static final DeferredItem<BlockItem> MYSTICAL_BOOKSHELF_ITEM = ITEMS.registerSimpleBlockItem(MYSTICAL_BOOKSHELF);

    // -- Block entities --------------------------------------------------------
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExtractorBlockEntity>> EXTRACTOR =
            BLOCK_ENTITIES.register("extractor",
                    () -> new BlockEntityType<>(ExtractorBlockEntity::new, Set.of(EXTRACTOR_BLOCK.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TankBlockEntity>> TANK =
            BLOCK_ENTITIES.register("tank", () -> new BlockEntityType<>(TankBlockEntity::new,
                    Set.of(GOLD_TANK.get(), AMETHYST_TANK.get(), TITANE_TANK.get(), PALADIUM_TANK.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CauldronCoreBlockEntity>> CAULDRON_CORE_ENTITY =
            BLOCK_ENTITIES.register("cauldron_core",
                    () -> new BlockEntityType<>(CauldronCoreBlockEntity::new, Set.of(CAULDRON_CORE.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PortalControllerBlockEntity>> PORTAL_CONTROLLER =
            BLOCK_ENTITIES.register("portal_controller", () -> new BlockEntityType<>(PortalControllerBlockEntity::new,
                    Set.of(AMETHYST_PORTAL_BLOCK.get(), TITANE_PORTAL_BLOCK.get(), PALADIUM_PORTAL_BLOCK.get(),
                            ENDIUM_PORTAL_BLOCK.get())));

    /** L'ordre de l'onglet : la chaine de l'Alchimiste, de la seve aux portails. */
    public static final List<DeferredItem<? extends Item>> ORDERED = List.of(
            FLASK, EXTRACTOR_ITEM, GOLD_TANK_ITEM, AMETHYST_TANK_ITEM, TITANE_TANK_ITEM, PALADIUM_TANK_ITEM,
            LIGHTNING_POTION, CAULDRON_ITEM, CAULDRON_CORE_ITEM,
            HARPAGOPHYTUM_FLOWER_ITEM, SAUGE_FLOWER_ITEM, CLATHRUSARCHERI_FLOWER_ITEM, ENDIUM_FLOWER_ITEM,
            PALADIUM_FLOWER_ITEM, MINERAL_FLOWER_ITEM,
            AMETHYST_POLLEN, TITANE_POLLEN, PALADIUM_POLLEN, ENDIUM_POLLEN,
            AMETHYST_PORTAL_BLOCK_ITEM, AMETHYST_PORTAL_KEY, TITANE_PORTAL_BLOCK_ITEM, TITANE_PORTAL_KEY,
            PALADIUM_PORTAL_BLOCK_ITEM, PALADIUM_PORTAL_KEY, ENDIUM_HEART, ENDIUM_ANGLE_PORTAL_BLOCK_ITEM,
            ENDIUM_PORTAL_BLOCK_ITEM, ENDIUM_PORTAL_KEY, MYSTICAL_BOOKSHELF_ITEM);

    private PalaAlchemist() {
    }

    private static BlockBehaviour.Properties tank(BlockBehaviour.Properties properties) {
        return properties.strength(4.0f, 1.2f).sound(SoundType.METAL).noOcclusion().mapColor(MapColor.METAL)
                .requiresCorrectToolForDrops();
    }

    /** {@code BlockBase} : roche, durete 8, setResistance(2), pioche. */
    private static BlockBehaviour.Properties rock(BlockBehaviour.Properties properties) {
        return properties.strength(8.0f, 1.2f).sound(SoundType.STONE).requiresCorrectToolForDrops();
    }

    private static FlowerBlock flower(BlockBehaviour.Properties properties) {
        return new FlowerBlock(new SuspiciousStewEffects(List.of()), properties);
    }

    private static BlockBehaviour.Properties flowerProperties(BlockBehaviour.Properties properties) {
        return properties.noCollision().instabreak().sound(SoundType.GRASS)
                .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY)
                .mapColor(MapColor.PLANT);
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        PalaWoods.register(modBus);
    }
}

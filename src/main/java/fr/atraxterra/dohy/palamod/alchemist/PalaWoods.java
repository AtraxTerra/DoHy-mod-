package fr.atraxterra.dohy.palamod.alchemist;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Optional;

/**
 * Les quatre arbres de PalaMod ({@code PWorld}), le bois mort, les buches
 * entamees par l'extracteur et les bois brillants du chaudron.
 *
 * <p>Releve dans {@code BaseBlockLog}, {@code BlockLeaveBase},
 * {@code BaseSaplingCustom} et {@code WorldTree1Gen} / {@code 2Gen} :
 * <ul>
 *   <li>buches et planches : durete 3, plus lentes que le bois vanilla ;
 *       <b>ni inflammables, ni dans les tags vanilla</b> — une planche de
 *       jacaranda ne fait ni batons ni etabli, comme dans PalaMod ;</li>
 *   <li>feuilles : un bloc simple, <b>qui ne pourrit pas</b> ; 1,01 % de pousse,
 *       rien d'autre ; la feuille elle-meme a la cisaille ou au toucher de soie ;</li>
 *   <li>pousse : la croissance vanilla (lumiere 9, une chance sur 7, deux
 *       etapes, poudre d'os a 45 %), et l'arbre pousse toujours.</li>
 * </ul>
 *
 * <p>Les identifiants sont ceux que {@code data/jobs.json} attend
 * ({@code jacaranda_log}, {@code erable_leaves}, {@code shiny_jacaranda_wood}...).
 */
public final class PalaWoods {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, PalaMod.NAMESPACE);

    /** Les deux formes d'arbre de PalaMod, communes aux quatre essences. */
    public static final DeferredHolder<Feature<?>, PalaTreeFeature> TREE =
            FEATURES.register("tree", PalaTreeFeature::new);

    // -- Buches --------------------------------------------------------------
    public static final DeferredBlock<RotatedPillarBlock> JACARANDA_LOG = BLOCKS.registerBlock(
            "jacaranda_log", RotatedPillarBlock::new, properties -> properties
                    .strength(3.0f).sound(SoundType.WOOD).mapColor(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<RotatedPillarBlock> JUDEECERCIS_LOG = BLOCKS.registerBlock(
            "judeecercis_log", RotatedPillarBlock::new, properties -> properties
                    .strength(3.0f).sound(SoundType.WOOD).mapColor(MapColor.COLOR_RED));
    public static final DeferredBlock<RotatedPillarBlock> ERABLE_LOG = BLOCKS.registerBlock(
            "erable_log", RotatedPillarBlock::new, properties -> properties
                    .strength(3.0f).sound(SoundType.WOOD).mapColor(MapColor.COLOR_BROWN));
    public static final DeferredBlock<RotatedPillarBlock> OSTRYA_LOG = BLOCKS.registerBlock(
            "ostrya_log", RotatedPillarBlock::new, properties -> properties
                    .strength(3.0f).sound(SoundType.WOOD).mapColor(MapColor.COLOR_GREEN));
    /** Ce que laisse une buche videe de sa seve. Jamais genere. */
    public static final DeferredBlock<RotatedPillarBlock> DEAD_WOOD = BLOCKS.registerBlock(
            "dead_wood", RotatedPillarBlock::new, properties -> properties
                    .strength(3.0f).sound(SoundType.WOOD).mapColor(MapColor.COLOR_GRAY));

    // -- Planches ------------------------------------------------------------
    public static final DeferredBlock<Block> JACARANDA_PLANKS = BLOCKS.registerBlock(
            "jacaranda_planks", Block::new, properties -> properties
                    .strength(3.0f).sound(SoundType.WOOD).mapColor(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<Block> JUDEECERCIS_PLANKS = BLOCKS.registerBlock(
            "judeecercis_planks", Block::new, properties -> properties
                    .strength(3.0f).sound(SoundType.WOOD).mapColor(MapColor.COLOR_RED));
    public static final DeferredBlock<Block> ERABLE_PLANKS = BLOCKS.registerBlock(
            "erable_planks", Block::new, properties -> properties
                    .strength(3.0f).sound(SoundType.WOOD).mapColor(MapColor.COLOR_BROWN));
    public static final DeferredBlock<Block> OSTRYA_PLANKS = BLOCKS.registerBlock(
            "ostrya_planks", Block::new, properties -> properties
                    .strength(3.0f).sound(SoundType.WOOD).mapColor(MapColor.COLOR_GREEN));
    public static final DeferredBlock<Block> DEAD_WOOD_PLANKS = BLOCKS.registerBlock(
            "dead_wood_planks", Block::new, properties -> properties
                    .strength(3.0f).sound(SoundType.WOOD).mapColor(MapColor.COLOR_GRAY));

    // -- Feuilles : un bloc simple, sans pourrissement -----------------------
    public static final DeferredBlock<Block> JACARANDA_LEAVES = BLOCKS.registerBlock(
            "jacaranda_leaves", Block::new, properties -> leaves(properties, MapColor.COLOR_PURPLE));
    public static final DeferredBlock<Block> JUDEECERCIS_LEAVES = BLOCKS.registerBlock(
            "judeecercis_leaves", Block::new, properties -> leaves(properties, MapColor.COLOR_PINK));
    public static final DeferredBlock<Block> ERABLE_LEAVES = BLOCKS.registerBlock(
            "erable_leaves", Block::new, properties -> leaves(properties, MapColor.COLOR_RED));
    public static final DeferredBlock<Block> OSTRYA_LEAVES = BLOCKS.registerBlock(
            "ostrya_leaves", Block::new, properties -> leaves(properties, MapColor.PLANT));

    // -- Pousses ---------------------------------------------------------------
    public static final DeferredBlock<SaplingBlock> JACARANDA_SAPLING = BLOCKS.registerBlock(
            "jacaranda_sapling", properties -> new SaplingBlock(grower("jacaranda"), properties),
            PalaWoods::sapling);
    public static final DeferredBlock<SaplingBlock> JUDEECERCIS_SAPLING = BLOCKS.registerBlock(
            "judeecercis_sapling", properties -> new SaplingBlock(grower("judeecercis"), properties),
            PalaWoods::sapling);
    public static final DeferredBlock<SaplingBlock> ERABLE_SAPLING = BLOCKS.registerBlock(
            "erable_sapling", properties -> new SaplingBlock(grower("erable"), properties),
            PalaWoods::sapling);
    public static final DeferredBlock<SaplingBlock> OSTRYA_SAPLING = BLOCKS.registerBlock(
            "ostrya_sapling", properties -> new SaplingBlock(grower("ostrya"), properties),
            PalaWoods::sapling);

    // -- Buches entamees (sans objet : cassees, elles donnent du bois mort) ----
    // BlockWood : durete 2, resistance 3 (x 0,6 pour les unites d'ici).
    public static final DeferredBlock<TappedLogBlock> TAPPED_JACARANDA_LOG = BLOCKS.registerBlock(
            "tapped_jacaranda_log", properties -> new TappedLogBlock(Sap.JACARANDA, properties),
            properties -> properties.strength(2.0f, 1.8f).sound(SoundType.WOOD).mapColor(MapColor.COLOR_GRAY));
    public static final DeferredBlock<TappedLogBlock> TAPPED_JUDEECERCIS_LOG = BLOCKS.registerBlock(
            "tapped_judeecercis_log", properties -> new TappedLogBlock(Sap.JUDEECERCIS, properties),
            properties -> properties.strength(2.0f, 1.8f).sound(SoundType.WOOD).mapColor(MapColor.COLOR_GRAY));
    public static final DeferredBlock<TappedLogBlock> TAPPED_ERABLE_LOG = BLOCKS.registerBlock(
            "tapped_erable_log", properties -> new TappedLogBlock(Sap.ERABLE, properties),
            properties -> properties.strength(2.0f, 1.8f).sound(SoundType.WOOD).mapColor(MapColor.COLOR_GRAY));
    public static final DeferredBlock<TappedLogBlock> TAPPED_OSTRYA_LOG = BLOCKS.registerBlock(
            "tapped_ostrya_log", properties -> new TappedLogBlock(Sap.OSTRYA, properties),
            properties -> properties.strength(2.0f, 1.8f).sound(SoundType.WOOD).mapColor(MapColor.COLOR_GRAY));

    // -- Bois brillants : chaudron, lumiere 15, roche (pioche) -----------------
    public static final DeferredBlock<Block> SHINY_JACARANDA_WOOD = BLOCKS.registerBlock(
            "shiny_jacaranda_wood", Block::new, PalaWoods::shinyProperties);
    public static final DeferredBlock<Block> SHINY_JUDEECERCIS_WOOD = BLOCKS.registerBlock(
            "shiny_judeecercis_wood", Block::new, PalaWoods::shinyProperties);
    public static final DeferredBlock<Block> SHINY_ERABLE_WOOD = BLOCKS.registerBlock(
            "shiny_erable_wood", Block::new, PalaWoods::shinyProperties);
    public static final DeferredBlock<Block> SHINY_OSTRYA_WOOD = BLOCKS.registerBlock(
            "shiny_ostrya_wood", Block::new, PalaWoods::shinyProperties);

    // -- Objets --------------------------------------------------------------
    public static final List<DeferredItem<BlockItem>> ORDERED = List.of(
            ITEMS.registerSimpleBlockItem(JACARANDA_LOG), ITEMS.registerSimpleBlockItem(JACARANDA_PLANKS),
            ITEMS.registerSimpleBlockItem(JACARANDA_LEAVES), ITEMS.registerSimpleBlockItem(JACARANDA_SAPLING),
            ITEMS.registerSimpleBlockItem(JUDEECERCIS_LOG), ITEMS.registerSimpleBlockItem(JUDEECERCIS_PLANKS),
            ITEMS.registerSimpleBlockItem(JUDEECERCIS_LEAVES), ITEMS.registerSimpleBlockItem(JUDEECERCIS_SAPLING),
            ITEMS.registerSimpleBlockItem(ERABLE_LOG), ITEMS.registerSimpleBlockItem(ERABLE_PLANKS),
            ITEMS.registerSimpleBlockItem(ERABLE_LEAVES), ITEMS.registerSimpleBlockItem(ERABLE_SAPLING),
            ITEMS.registerSimpleBlockItem(OSTRYA_LOG), ITEMS.registerSimpleBlockItem(OSTRYA_PLANKS),
            ITEMS.registerSimpleBlockItem(OSTRYA_LEAVES), ITEMS.registerSimpleBlockItem(OSTRYA_SAPLING),
            ITEMS.registerSimpleBlockItem(DEAD_WOOD), ITEMS.registerSimpleBlockItem(DEAD_WOOD_PLANKS),
            ITEMS.registerSimpleBlockItem(SHINY_JACARANDA_WOOD), ITEMS.registerSimpleBlockItem(SHINY_JUDEECERCIS_WOOD),
            ITEMS.registerSimpleBlockItem(SHINY_ERABLE_WOOD), ITEMS.registerSimpleBlockItem(SHINY_OSTRYA_WOOD));

    private PalaWoods() {
    }

    private static BlockBehaviour.Properties leaves(BlockBehaviour.Properties properties, MapColor color) {
        return properties.strength(0.2f).sound(SoundType.GRASS).mapColor(color).noOcclusion()
                .isValidSpawn((state, level, pos, entity) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties sapling(BlockBehaviour.Properties properties) {
        return properties.noCollision().randomTicks().instabreak().sound(SoundType.GRASS)
                .pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties shinyProperties(BlockBehaviour.Properties properties) {
        // BlockBase : roche, durete 8, setResistance(2) ; setLightLevel(1F) = 15.
        return properties.strength(8.0f, 1.2f).lightLevel(state -> 15).sound(SoundType.STONE)
                .requiresCorrectToolForDrops();
    }

    /** La pousse fait pousser « &lt;essence&gt;_tree », l'une des deux formes au hasard. */
    private static TreeGrower grower(String wood) {
        return new TreeGrower(PalaMod.NAMESPACE + "_" + wood, Optional.empty(),
                Optional.of(ResourceKey.create(Registries.CONFIGURED_FEATURE,
                        Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, wood + "_tree"))),
                Optional.empty());
    }

    public static Block log(Sap sap) {
        return switch (sap) {
            case JACARANDA -> JACARANDA_LOG.get();
            case JUDEECERCIS -> JUDEECERCIS_LOG.get();
            case ERABLE -> ERABLE_LOG.get();
            case OSTRYA -> OSTRYA_LOG.get();
        };
    }

    public static Block shiny(Sap sap) {
        return switch (sap) {
            case JACARANDA -> SHINY_JACARANDA_WOOD.get();
            case JUDEECERCIS -> SHINY_JUDEECERCIS_WOOD.get();
            case ERABLE -> SHINY_ERABLE_WOOD.get();
            case OSTRYA -> SHINY_OSTRYA_WOOD.get();
        };
    }

    public static BlockState tapped(Sap sap) {
        TappedLogBlock block = switch (sap) {
            case JACARANDA -> TAPPED_JACARANDA_LOG.get();
            case JUDEECERCIS -> TAPPED_JUDEECERCIS_LOG.get();
            case ERABLE -> TAPPED_ERABLE_LOG.get();
            case OSTRYA -> TAPPED_OSTRYA_LOG.get();
        };
        return block.defaultBlockState();
    }

    public static BlockState deadWood() {
        return DEAD_WOOD.get().defaultBlockState();
    }

    /** La seve d'une buche entiere ou entamee, ou {@code null}. */
    public static Sap sapOf(BlockState state) {
        if (state.getBlock() instanceof TappedLogBlock tapped) {
            return tapped.type();
        }
        for (Sap sap : Sap.values()) {
            if (state.is(log(sap))) {
                return sap;
            }
        }
        return null;
    }

    public static Item logItem(Sap sap) {
        return log(sap).asItem();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FEATURES.register(modBus);
    }
}

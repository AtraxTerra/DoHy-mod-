package fr.atraxterra.dohy.palamod.pillage;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.equipment.PalaArmorMaterials;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Le pillage de la faction (chapitre 22, lot 22d) : dynamites, TNT, detecteurs,
 * explorateur de coffre, detecteur de presence, seau extrapole, arc en paladium
 * et ses modificateurs, fleche de Switch, cagoule.
 *
 * <p>Releve dans {@code ItemsRegister}, {@code PPRegisterer},
 * {@code pillage.common.effects.blocks.tnt} et {@code PRegister_Craft}. Les
 * noms sont ecrits en toutes lettres pour {@code check_assets.py}. Les verrous
 * de l'arbre de faction sont dans {@code DefenseGate}, avec ceux de la defense.
 */
public final class PalaPillage {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);

    /** Ce que la TNT en endium perce : l'obsidienne et ses blocs, les obsidiennes de defense. */
    public static final TagKey<Block> ENDIUM_TNT_BREAKABLE = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "endium_tnt_breakable"));

    /** Les modificateurs poses sur un arc en paladium ({@link BowModifier#key()}). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> BOW_MODIFIERS =
            COMPONENTS.registerComponentType("bow_modifiers", builder -> builder
                    .persistent(Codec.STRING.listOf())
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())));

    // -- Entites ----------------------------------------------------------------

    public static final DeferredHolder<EntityType<?>, EntityType<DynamiteEntity>> DYNAMITE_ENTITY = ENTITIES.register(
            "dynamite", key -> EntityType.Builder.<DynamiteEntity>of(DynamiteEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    /** Les cinq TNT allumees : le genre se lit dans l'etat de bloc porte. */
    public static final DeferredHolder<EntityType<?>, EntityType<PalaPrimedTnt>> PRIMED_TNT = ENTITIES.register(
            "primed_tnt", key -> EntityType.Builder.<PalaPrimedTnt>of(PalaPrimedTnt::new, MobCategory.MISC)
                    .fireImmune()
                    .sized(0.98f, 0.98f)
                    .eyeHeight(0.15f)
                    .clientTrackingRange(10)
                    .updateInterval(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    // -- Blocs ------------------------------------------------------------------

    /** Les proprietes de la TNT vanilla. */
    private static BlockBehaviour.Properties tnt(BlockBehaviour.Properties properties) {
        return properties.mapColor(MapColor.FIRE).instabreak().sound(SoundType.GRASS).ignitedByLava()
                .isRedstoneConductor((state, level, pos) -> false);
    }

    public static final DeferredBlock<PalaTntBlock> BIG_TNT = BLOCKS.registerBlock(
            "big_tnt", properties -> new PalaTntBlock(TntKind.BIG, properties), PalaPillage::tnt);
    public static final DeferredBlock<PalaTntBlock> ENDIUM_TNT = BLOCKS.registerBlock(
            "endium_tnt", properties -> new PalaTntBlock(TntKind.ENDIUM, properties), PalaPillage::tnt);
    public static final DeferredBlock<PalaTntBlock> SPONGE_TNT = BLOCKS.registerBlock(
            "sponge_tnt", properties -> new PalaTntBlock(TntKind.SPONGE, properties), PalaPillage::tnt);
    public static final DeferredBlock<PalaTntBlock> EFFECT_TNT = BLOCKS.registerBlock(
            "effect_tnt", properties -> new PalaTntBlock(TntKind.EFFECT, properties), PalaPillage::tnt);
    public static final DeferredBlock<PalaTntBlock> WITHER_TNT = BLOCKS.registerBlock(
            "wither_tnt", properties -> new PalaTntBlock(TntKind.WITHER, properties), PalaPillage::tnt);

    public static final DeferredBlock<OnlineDetectorBlock> ONLINE_DETECTOR = BLOCKS.registerBlock(
            "online_detector", OnlineDetectorBlock::new, properties -> properties
                    .strength(3.5f, 6.0f).sound(SoundType.METAL).mapColor(MapColor.COLOR_GRAY)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(OnlineDetectorBlock.LIT) ? 7 : 0));

    // -- Objets -----------------------------------------------------------------

    public static final DeferredItem<DynamiteItem> DYNAMITE = ITEMS.registerItem(
            "dynamite", properties -> new DynamiteItem(DynamiteKind.DYNAMITE, properties.stacksTo(16)));
    public static final DeferredItem<DynamiteItem> NINJA_DYNAMITE = ITEMS.registerItem(
            "ninja_dynamite", properties -> new DynamiteItem(DynamiteKind.NINJA, properties.stacksTo(16)));
    public static final DeferredItem<DynamiteItem> BIG_DYNAMITE = ITEMS.registerItem(
            "big_dynamite", properties -> new DynamiteItem(DynamiteKind.BIG, properties.stacksTo(16)));

    public static final DeferredItem<FinderItem> UNCLAIM_FINDER = ITEMS.registerItem(
            "unclaim_finder", properties -> new FinderItem(FinderType.GREEN, properties.durability(FinderType.DURABILITY)));
    public static final DeferredItem<FinderItem> UNCLAIM_FINDER_ORANGE = ITEMS.registerItem(
            "unclaim_finder_orange", properties -> new FinderItem(FinderType.ORANGE,
                    properties.durability(FinderType.DURABILITY)));
    public static final DeferredItem<FinderItem> UNCLAIM_FINDER_RED = ITEMS.registerItem(
            "unclaim_finder_red", properties -> new FinderItem(FinderType.RED,
                    properties.durability(FinderType.DURABILITY)));
    public static final DeferredItem<FinderItem> UNCLAIM_FINDER_BLUE = ITEMS.registerItem(
            "unclaim_finder_blue", properties -> new FinderItem(FinderType.BLUE,
                    properties.durability(FinderType.DURABILITY)));
    public static final DeferredItem<FinderItem> SPAWNER_FINDER = ITEMS.registerItem(
            "spawner_finder", properties -> new FinderItem(FinderType.SPAWNER,
                    properties.durability(FinderType.DURABILITY)));

    public static final DeferredItem<ChestExplorerItem> CHEST_EXPLORER = ITEMS.registerItem(
            "chest_explorer", properties -> new ChestExplorerItem(properties.stacksTo(1)));
    public static final DeferredItem<ExtrapolatedBucketItem> EXTRAPOLATED_BUCKET = ITEMS.registerItem(
            "extrapolated_bucket", properties -> new ExtrapolatedBucketItem(properties.durability(32)));

    public static final DeferredItem<PaladiumBowItem> PALADIUM_BOW = ITEMS.registerItem(
            "paladium_bow", properties -> new PaladiumBowItem(properties.durability(900).enchantable(1)));
    public static final DeferredItem<SwitchArrowItem> SWITCH_ARROW = ITEMS.registerItem(
            "switch_arrow", SwitchArrowItem::new);
    public static final DeferredItem<Item> EMPTY_BOW_MODIFIER = ITEMS.registerItem(
            "empty_bow_modifier", properties -> new Item(properties.stacksTo(16)));
    public static final DeferredItem<Item> RANGE_BOW_MODIFIER = ITEMS.registerItem(
            "range_bow_modifier", properties -> new Item(properties.stacksTo(16)));
    public static final DeferredItem<Item> SPEED_BOW_MODIFIER = ITEMS.registerItem(
            "speed_bow_modifier", properties -> new Item(properties.stacksTo(16)));

    /** La cagoule : les monstres te voient de moins loin ({@code PillageEvents.onVisibility}). */
    public static final DeferredItem<Item> HOOD_HELMET = ITEMS.registerItem(
            "hood_helmet", properties -> new Item(properties.humanoidArmor(PalaArmorMaterials.HOOD, ArmorType.HELMET)));
    /**
     * L'armure de voyage ({@code ItemArmorTravel}) : chaque piece a son effet
     * ({@code TravelArmorEvents}) — monter d'un bloc sans sauter, vitesse, saut,
     * pas de degats de chute, respiration sous l'eau.
     */
    public static final DeferredItem<Item> TRAVEL_BOOTS = ITEMS.registerItem("travel_boots",
            properties -> new Item(properties.humanoidArmor(PalaArmorMaterials.TRAVEL_BOOTS, ArmorType.BOOTS)));
    public static final DeferredItem<Item> TRAVEL_LEGGINGS = ITEMS.registerItem("travel_leggings",
            properties -> new Item(properties.humanoidArmor(PalaArmorMaterials.TRAVEL_LEGGINGS, ArmorType.LEGGINGS)));
    public static final DeferredItem<Item> TRAVEL_JUMPCHEST = ITEMS.registerItem("travel_jumpchest",
            properties -> new Item(properties.humanoidArmor(PalaArmorMaterials.TRAVEL_JUMP, ArmorType.CHESTPLATE)));
    public static final DeferredItem<Item> TRAVEL_SLIMYHELMET = ITEMS.registerItem("travel_slimyhelmet",
            properties -> new Item(properties.humanoidArmor(PalaArmorMaterials.TRAVEL_SLIMY, ArmorType.HELMET)));
    public static final DeferredItem<Item> TRAVEL_SCUBAHELMET = ITEMS.registerItem("travel_scubahelmet",
            properties -> new Item(properties.humanoidArmor(PalaArmorMaterials.TRAVEL_SCUBA, ArmorType.HELMET)));
    /** La ficelle de diamant : l'arc, la dynamite, l'amulette du chasseur. */
    public static final DeferredItem<Item> DIAMOND_STRING = ITEMS.registerItem("diamond_string", Item::new);

    // -- Onglet -----------------------------------------------------------------

    public static final List<DeferredBlock<? extends Block>> ORDERED_BLOCKS = List.of(
            BIG_TNT, ENDIUM_TNT, SPONGE_TNT, EFFECT_TNT, WITHER_TNT, ONLINE_DETECTOR);
    private static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = ORDERED_BLOCKS.stream()
            .map(block -> ITEMS.registerSimpleBlockItem(block, properties -> properties)).toList();

    public static final List<DeferredItem<? extends Item>> ORDERED_ITEMS = List.of(
            DYNAMITE, NINJA_DYNAMITE, BIG_DYNAMITE,
            UNCLAIM_FINDER, UNCLAIM_FINDER_ORANGE, UNCLAIM_FINDER_RED, UNCLAIM_FINDER_BLUE, SPAWNER_FINDER,
            CHEST_EXPLORER, EXTRAPOLATED_BUCKET,
            PALADIUM_BOW, SWITCH_ARROW, EMPTY_BOW_MODIFIER, RANGE_BOW_MODIFIER, SPEED_BOW_MODIFIER,
            HOOD_HELMET, TRAVEL_SLIMYHELMET, TRAVEL_SCUBAHELMET, TRAVEL_JUMPCHEST, TRAVEL_LEGGINGS, TRAVEL_BOOTS,
            DIAMOND_STRING);

    private PalaPillage() {
    }

    /** Tout l'onglet : les objets, puis les blocs. */
    public static List<Supplier<? extends Item>> tab() {
        List<Supplier<? extends Item>> all = new ArrayList<>(ORDERED_ITEMS);
        all.addAll(BLOCK_ITEMS);
        return all;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(PalaPillage::onSetup);
    }

    /** Les distributeurs tirent les dynamites et allument les TNT : les canons a TNT marchent. */
    private static void onSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (DeferredItem<? extends Item> dynamite : List.of(DYNAMITE, NINJA_DYNAMITE, BIG_DYNAMITE)) {
                DispenserBlock.registerProjectileBehavior(dynamite.get());
            }
            TntDispenseBehavior behavior = new TntDispenseBehavior();
            for (DeferredBlock<? extends Block> tnt : List.of(BIG_TNT, ENDIUM_TNT, SPONGE_TNT, EFFECT_TNT, WITHER_TNT)) {
                DispenserBlock.registerBehavior(tnt.get(), behavior);
            }
        });
    }
}

package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.equipment.PalaArmorMaterials;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Les objets et les blocs des lucky blocks du cinquieme lot : lampes, plantes, TNT,
 * gadgets (grappin, lasso, jetpack...), coffres-forts, pieges. Les valeurs viennent de
 * {@code luckyblock/init/Items} et {@code luckyblock/init/Blocks} ; le comportement est dans
 * {@link GadgetItems} et {@link GadgetBlocks}.
 */
public final class LuckyGadgets {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);

    /** L'ordre de l'onglet PalaMod. */
    private static final List<DeferredItem<? extends Item>> ORDER = new ArrayList<>();

    // -- Blocs ------------------------------------------------------------------------------------

    public static final DeferredBlock<GadgetBlocks.LampBlock> COLORED_LAMP = gadgetBlock("colored_lamp",
            GadgetBlocks.LampBlock::new, properties -> properties.strength(0.3f).sound(SoundType.GLASS)
                    .lightLevel(state -> state.getValue(GadgetBlocks.LampBlock.LIT) ? 14 : 0));
    public static final DeferredBlock<Block> FISH_BOWL = BLOCKS.registerBlock("fish_bowl", Block::new,
            properties -> properties.strength(0.3f).sound(SoundType.GLASS).noOcclusion());
    public static final DeferredBlock<GadgetBlocks.HunterFlowerBlock> HUNTER_FLOWER = gadgetBlock("hunter_flower",
            GadgetBlocks.HunterFlowerBlock::new, properties -> properties.instabreak().noCollision()
                    .sound(SoundType.GRASS).mapColor(MapColor.PLANT));
    public static final DeferredBlock<GadgetBlocks.LuckyTntBlock> KAWAII_TNT = gadgetBlock("kawaii_tnt",
            properties -> new GadgetBlocks.LuckyTntBlock(0.0f, properties),
            properties -> properties.instabreak().sound(SoundType.GRASS));
    public static final DeferredBlock<GadgetBlocks.LuckyTntBlock> LESS_KAWAII_TNT = gadgetBlock("less_kawaii_tnt",
            properties -> new GadgetBlocks.LuckyTntBlock(5.0f, properties),
            properties -> properties.instabreak().sound(SoundType.GRASS));
    public static final DeferredBlock<GadgetBlocks.LuckyTntBlock> FAKE_TNT = gadgetBlock("fake_tnt",
            properties -> new GadgetBlocks.LuckyTntBlock(0.0f, properties),
            properties -> properties.instabreak().sound(SoundType.GRASS));
    public static final DeferredBlock<Block> FLASHY_BLOCK = gadgetBlock("flashy_block", Block::new,
            properties -> properties.strength(0.3f).sound(SoundType.GLASS).lightLevel(state -> 15));
    public static final DeferredBlock<Block> BANANA_LOG = gadgetBlock("banana_log", Block::new,
            properties -> properties.strength(2.0f).sound(SoundType.WOOD).mapColor(MapColor.WOOD));
    public static final DeferredBlock<Block> BANANA_LEAVES = gadgetBlock("banana_leaves", Block::new,
            properties -> properties.strength(0.2f).sound(SoundType.GRASS).noOcclusion().mapColor(MapColor.PLANT));
    /** « Pas la » : le faux minerai de paladium ne rend rien. */
    public static final DeferredBlock<Block> FAKE_PALADIUM_ORE = BLOCKS.registerBlock("fake_paladium_ore", Block::new,
            properties -> properties.strength(3.0f, 3.0f).sound(SoundType.STONE));
    public static final DeferredBlock<GadgetBlocks.WirelessLeverBlock> WIRELESS_LEVER = BLOCKS.registerBlock(
            "wireless_lever", GadgetBlocks.WirelessLeverBlock::new,
            properties -> properties.strength(0.5f).sound(SoundType.STONE).noOcclusion());
    public static final DeferredBlock<GadgetBlocks.DyeingMachineBlock> DYEING_MACHINE = gadgetBlock("dyeing_machine",
            GadgetBlocks.DyeingMachineBlock::new, properties -> properties.strength(5.0f).sound(SoundType.STONE));
    public static final DeferredBlock<GadgetBlocks.ShapedBlock> BASKETBALL = gadgetBlock("basketball",
            properties -> new GadgetBlocks.ShapedBlock(Block.box(4, 0, 4, 12, 8, 12), properties),
            properties -> properties.strength(0.5f).sound(SoundType.WOOL).noOcclusion());
    public static final DeferredBlock<GadgetBlocks.ShapedBlock> BASKET_HOOP = gadgetBlock("basket_hoop",
            properties -> new GadgetBlocks.ShapedBlock(Block.box(0, 0, 14, 16, 16, 16), properties),
            properties -> properties.strength(1.0f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<GadgetBlocks.AlarmBlock> ALARM = gadgetBlock("alarm", GadgetBlocks.AlarmBlock::new,
            properties -> properties.strength(1.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<GadgetBlocks.FakePortalBlock> FAKE_PORTAL = BLOCKS.registerBlock("fake_portal",
            GadgetBlocks.FakePortalBlock::new, properties -> properties.strength(0.5f).sound(SoundType.GLASS)
                    .noCollision().noOcclusion().lightLevel(state -> 11));
    public static final DeferredBlock<GadgetBlocks.JawsTrapBlock> JAWS_TRAP = gadgetBlock("jaws_trap",
            GadgetBlocks.JawsTrapBlock::new, properties -> properties.strength(2.0f).sound(SoundType.METAL)
                    .noCollision().noOcclusion());
    public static final DeferredBlock<GadgetBlocks.PetRockBlock> PET_ROCK = gadgetBlock("pet_rock",
            GadgetBlocks.PetRockBlock::new, properties -> properties.strength(0.5f).sound(SoundType.STONE).noOcclusion());
    public static final DeferredBlock<GadgetBlocks.SleepingBagBlock> SLEEPING_BAG = gadgetBlock("sleeping_bag",
            GadgetBlocks.SleepingBagBlock::new, properties -> properties.strength(0.2f).sound(SoundType.WOOL)
                    .noOcclusion());
    public static final DeferredBlock<GadgetBlocks.FakeLavaBlock> FAKE_LAVA = BLOCKS.registerBlock("fake_lava",
            GadgetBlocks.FakeLavaBlock::new, properties -> properties.strength(0.5f).noCollision()
                    .lightLevel(state -> 15).mapColor(MapColor.FIRE));
    public static final DeferredBlock<GadgetBlocks.CatDetectorBlock> CAT_DETECTOR = gadgetBlock("cat_detector",
            GadgetBlocks.CatDetectorBlock::new, properties -> properties.strength(4.0f).sound(SoundType.STONE)
                    .lightLevel(state -> 4));
    public static final DeferredBlock<GadgetBlocks.ChatDetectorBlock> CHAT_DETECTOR = gadgetBlock("chat_detector",
            GadgetBlocks.ChatDetectorBlock::new, properties -> properties.strength(4.0f).sound(SoundType.STONE)
                    .lightLevel(state -> 4));
    public static final DeferredBlock<GadgetBlocks.SafeChestBlock> SAFE_CHEST = BLOCKS.registerBlock("safe_chest",
            properties -> new GadgetBlocks.SafeChestBlock(false, properties),
            properties -> properties.strength(12.0f, 3_600_000.0f).sound(SoundType.METAL));
    public static final DeferredBlock<GadgetBlocks.SafeChestBlock> MEGA_SAFE_CHEST = BLOCKS.registerBlock(
            "mega_safe_chest", properties -> new GadgetBlocks.SafeChestBlock(true, properties),
            properties -> properties.strength(12.0f, 3_600_000.0f).sound(SoundType.METAL));
    public static final DeferredBlock<GadgetBlocks.SafeChestBlock> ULTRA_SAFE_CHEST = BLOCKS.registerBlock(
            "ultra_safe_chest", properties -> new GadgetBlocks.SafeChestBlock(true, properties),
            properties -> properties.strength(12.0f, 3_600_000.0f).sound(SoundType.METAL));
    public static final DeferredBlock<GadgetBlocks.StringTrapBlock> STRING_TRAP = gadgetBlock("string_trap",
            GadgetBlocks.StringTrapBlock::new, properties -> properties.strength(0.3f).sound(SoundType.WOOL)
                    .noCollision().noOcclusion());
    public static final DeferredBlock<GadgetBlocks.FakeCommandBlock> FAKE_COMMAND_BLOCK = gadgetBlock("fake_command_block",
            GadgetBlocks.FakeCommandBlock::new, properties -> properties.strength(5.0f).sound(SoundType.METAL));
    public static final DeferredBlock<GadgetBlocks.BruteforcerBlock> BRUTEFORCER = gadgetBlock("bruteforcer",
            GadgetBlocks.BruteforcerBlock::new, properties -> properties.strength(12.0f).sound(SoundType.METAL));
    public static final DeferredBlock<GadgetBlocks.LuckyFlowerBlock> LUCKY_FLOWER = gadgetBlock("lucky_flower",
            GadgetBlocks.LuckyFlowerBlock::new, properties -> properties.instabreak().noCollision()
                    .sound(SoundType.GRASS).mapColor(MapColor.PLANT));
    /** La tombe de « Ton heure a sonne » : sans objet, elle ne nait que d'une mort. */
    public static final DeferredBlock<GadgetBlocks.TombBlock> TOMB = BLOCKS.registerBlock("tomb",
            GadgetBlocks.TombBlock::new, properties -> properties.strength(-1.0f, 3_600_000.0f)
                    .sound(SoundType.STONE).noOcclusion());

    // -- Objets des blocs particuliers -------------------------------------------------------------

    /** Le bocal a poisson se porte sur la tete ({@code setCurrentItemOrArmor(4, ...)}). */
    public static final DeferredItem<BlockItem> FISH_BOWL_ITEM = track(ITEMS.registerItem("fish_bowl",
            properties -> new BlockItem(FISH_BOWL.get(), properties.useBlockDescriptionPrefix()
                    .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
                            .setEquipSound(SoundEvents.ARMOR_EQUIP_GENERIC).build()))));
    public static final DeferredItem<BlockItem> FAKE_PALADIUM_ORE_ITEM = track(ITEMS.registerSimpleBlockItem(
            FAKE_PALADIUM_ORE, properties -> properties));
    public static final DeferredItem<GadgetItems.WirelessLeverItem> WIRELESS_LEVER_ITEM = track(ITEMS.registerItem(
            "wireless_lever", properties -> new GadgetItems.WirelessLeverItem(WIRELESS_LEVER.get(),
                    properties.useBlockDescriptionPrefix())));
    public static final DeferredItem<BlockItem> FAKE_PORTAL_ITEM = track(ITEMS.registerSimpleBlockItem(
            FAKE_PORTAL, properties -> properties));
    public static final DeferredItem<BlockItem> FAKE_LAVA_ITEM = track(ITEMS.registerSimpleBlockItem(
            FAKE_LAVA, properties -> properties));
    public static final DeferredItem<BlockItem> SAFE_CHEST_ITEM = track(ITEMS.registerSimpleBlockItem(
            SAFE_CHEST, properties -> properties.stacksTo(1)));
    public static final DeferredItem<BlockItem> MEGA_SAFE_CHEST_ITEM = track(ITEMS.registerSimpleBlockItem(
            MEGA_SAFE_CHEST, properties -> properties.stacksTo(1)));
    public static final DeferredItem<BlockItem> ULTRA_SAFE_CHEST_ITEM = track(ITEMS.registerSimpleBlockItem(
            ULTRA_SAFE_CHEST, properties -> properties.stacksTo(1)));

    // -- Objets ------------------------------------------------------------------------------------

    public static final DeferredItem<GadgetItems.GrapplingHookItem> GRAPPIN = gadgetItem("grappin",
            GadgetItems.GrapplingHookItem::new, properties -> properties.durability(64));
    public static final DeferredItem<Item> GRAPPIN_HOOK = gadgetItem("grappin_hook", Item::new, properties -> properties);
    public static final DeferredItem<Item> JETPACK = gadgetItem("jetpack", Item::new,
            properties -> properties.humanoidArmor(PalaArmorMaterials.JETPACK, ArmorType.CHESTPLATE));
    public static final DeferredItem<GadgetItems.LassoItem> LASSO = gadgetItem("lasso", GadgetItems.LassoItem::new,
            properties -> properties.stacksTo(1));
    public static final DeferredItem<GadgetItems.MagicPotionItem> MAGIC_POTION = gadgetItem("magic_potion",
            GadgetItems.MagicPotionItem::new, properties -> properties.durability(3)
                    .component(DataComponents.CONSUMABLE, drink()));
    /** Pyrobarbare : un briquet plus solide ({@code ItemBlazeAndSteel}), « Attention ca chauffe ». */
    public static final DeferredItem<FlintAndSteelItem> BLAZE_AND_STEEL = gadgetItem("blaze_and_steel",
            FlintAndSteelItem::new, properties -> properties.durability(256));
    public static final DeferredItem<GadgetItems.ChunkAnalyserItem> CHUNK_ANALYSER = gadgetItem("chunk_analyser",
            GadgetItems.ChunkAnalyserItem::new, properties -> properties.durability(32));
    public static final DeferredItem<GadgetItems.BoatFurnaceItem> BOAT_FURNACE = gadgetItem("boat_furnace",
            GadgetItems.BoatFurnaceItem::new, properties -> properties.stacksTo(1));
    public static final DeferredItem<GadgetItems.NewYearFireworkItem> NEW_YEAR_FIREWORK = gadgetItem("new_year_firework",
            GadgetItems.NewYearFireworkItem::new, properties -> properties.stacksTo(16));
    public static final DeferredItem<GadgetItems.GiantMushroomItem> GIANT_MUSHROOM = gadgetItem("giant_mushroom",
            GadgetItems.GiantMushroomItem::new, properties -> properties.food(new FoodProperties.Builder()
                    .nutrition(4).saturationModifier(0.6f).alwaysEdible().build()));
    public static final DeferredItem<GadgetItems.SpaceFoodItem> SPACE_FOOD = gadgetItem("space_food",
            GadgetItems.SpaceFoodItem::new, properties -> properties.food(new FoodProperties.Builder()
                    .nutrition(6).saturationModifier(0.6f).alwaysEdible().build()));
    public static final DeferredItem<GadgetItems.StatsStoneItem> STATS_STONE = gadgetItem("stats_stone",
            GadgetItems.StatsStoneItem::new, properties -> properties.stacksTo(1));
    public static final DeferredItem<GadgetItems.SodaItem> SODA = gadgetItem("soda", GadgetItems.SodaItem::new,
            properties -> properties.stacksTo(16).component(DataComponents.CONSUMABLE, drink()));
    public static final DeferredItem<GadgetItems.MeterItem> METER = gadgetItem("meter", GadgetItems.MeterItem::new,
            properties -> properties.stacksTo(1));
    public static final DeferredItem<GadgetItems.BiomePainterItem> BIOME_PAINTER = gadgetItem("biome_painter",
            GadgetItems.BiomePainterItem::new, properties -> properties.durability(64));
    public static final DeferredItem<GadgetItems.FollowCompassItem> FOLLOW_COMPASS = gadgetItem("follow_compass",
            GadgetItems.FollowCompassItem::new, properties -> properties.stacksTo(1));
    public static final DeferredItem<GadgetItems.PoulpoGunItem> POULPOGUN = gadgetItem("poulpogun",
            GadgetItems.PoulpoGunItem::new, properties -> properties.stacksTo(1));
    public static final DeferredItem<GadgetItems.SmokeBombItem> SMOKE_BOMB = gadgetItem("smoke_bomb",
            GadgetItems.SmokeBombItem::new, properties -> properties.stacksTo(16));
    public static final DeferredItem<Item> PARTY_MASK = gadgetItem("party_mask", Item::new,
            properties -> properties.humanoidArmor(PalaArmorMaterials.PARTY_MASK, ArmorType.HELMET));
    public static final DeferredItem<Item> BANANA = gadgetItem("banana", Item::new, properties -> properties.food(
            new FoodProperties.Builder().nutrition(4).saturationModifier(0.3f).build()));

    // Les trophees du chasseur de « Travail mache » ({@code ItemsRegister.SNAKE_VENOM}...).
    public static final DeferredItem<Item> SNAKE_VENOM = gadgetItem("snake_venom", Item::new, properties -> properties);
    public static final DeferredItem<Item> PARROT_FEATHER = gadgetItem("parrot_feather", Item::new, properties -> properties);
    public static final DeferredItem<Item> TURTLE_SCALES = gadgetItem("turtle_scales", Item::new, properties -> properties);
    public static final DeferredItem<Item> GOAT_SHOE = gadgetItem("goat_shoe", Item::new, properties -> properties);
    public static final DeferredItem<Item> MEDUSE_HOOK = gadgetItem("meduse_hook", Item::new, properties -> properties);
    public static final DeferredItem<Item> PANDA_DROOL = gadgetItem("panda_drool", Item::new, properties -> properties);
    public static final DeferredItem<Item> SNAIL_SHELL = gadgetItem("snail_shell", Item::new, properties -> properties);

    // -- Entites de bloc ---------------------------------------------------------------------------

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GadgetBlocks.LinkBlockEntity>> LINK_ENTITY =
            BLOCK_ENTITIES.register("lucky_link", () -> new BlockEntityType<>(GadgetBlocks.LinkBlockEntity::new,
                    Set.of(WIRELESS_LEVER.get(), STRING_TRAP.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GadgetBlocks.SafeChestBlockEntity>> SAFE_ENTITY =
            BLOCK_ENTITIES.register("safe_chest", () -> new BlockEntityType<>(GadgetBlocks.SafeChestBlockEntity::new,
                    Set.of(SAFE_CHEST.get(), MEGA_SAFE_CHEST.get(), ULTRA_SAFE_CHEST.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GadgetBlocks.TombBlockEntity>> TOMB_ENTITY =
            BLOCK_ENTITIES.register("tomb", () -> new BlockEntityType<>(GadgetBlocks.TombBlockEntity::new,
                    Set.of(TOMB.get())));

    private LuckyGadgets() {
    }

    /** Un bloc et son objet, a la suite dans l'onglet. */
    private static <B extends Block> DeferredBlock<B> gadgetBlock(String name,
            Function<BlockBehaviour.Properties, B> factory, UnaryOperator<BlockBehaviour.Properties> properties) {
        DeferredBlock<B> block = BLOCKS.registerBlock(name, factory, properties);
        track(ITEMS.registerSimpleBlockItem(block, item -> item));
        return block;
    }

    private static <I extends Item> DeferredItem<I> gadgetItem(String name,
            Function<Item.Properties, I> factory, UnaryOperator<Item.Properties> properties) {
        return track(ITEMS.registerItem(name, factory, properties));
    }

    private static <I extends Item> DeferredItem<I> track(DeferredItem<I> item) {
        ORDER.add(item);
        return item;
    }

    private static Consumable drink() {
        return Consumable.builder().consumeSeconds(1.6f).animation(ItemUseAnimation.DRINK)
                .sound(SoundEvents.GENERIC_DRINK).hasConsumeParticles(false).build();
    }

    /** L'onglet PalaMod : les objets du lot, dans l'ordre d'inscription. */
    public static List<DeferredItem<? extends Item>> ordered() {
        return List.copyOf(ORDER);
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}

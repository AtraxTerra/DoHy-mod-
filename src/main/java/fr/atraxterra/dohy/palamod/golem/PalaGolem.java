package fr.atraxterra.dohy.palamod.golem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.Set;

/**
 * Le golem gardien et ce qui l'entoure (mod GuardianGolem 6.1.0, ponte de
 * PalaMod) : la pierre, le bloc, le gardien, le marteau, la liste blanche, le
 * mini golem, la cle, les croquettes, leur machine et la boite a golem.
 *
 * <p>Tout passe sous l'espace {@code palamod}, comme le reste du portage.
 */
@EventBusSubscriber
public final class PalaGolem {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PalaMod.NAMESPACE);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, PalaMod.NAMESPACE);

    // -- Composants et pieces jointes ------------------------------------------

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GolemData>> GOLEM_DATA =
            COMPONENTS.registerComponentType("golem", builder -> builder
                    .persistent(GolemData.CODEC).networkSynchronized(GolemData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> WHITELIST_NAMES =
            COMPONENTS.registerComponentType("whitelist", builder -> builder
                    .persistent(Codec.STRING.listOf())
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())));

    public static final java.util.function.Supplier<AttachmentType<GolemKeeper.Data>> KEEPER = ATTACHMENTS.register(
            "guardian_keeper", () -> AttachmentType.builder(GolemKeeper.Data::new)
                    .serialize(GolemKeeper.Data.MAP_CODEC, data -> !data.isEmpty())
                    .build());

    // -- Le golem --------------------------------------------------------------

    /** {@code EntityGolem} : 1,5 x 2,75. */
    public static final DeferredHolder<EntityType<?>, EntityType<GuardianGolem>> GUARDIAN_GOLEM = ENTITIES.register(
            "guardian_golem", key -> EntityType.Builder.<GuardianGolem>of(GuardianGolem::new, MobCategory.MISC)
                    .sized(1.5f, 2.75f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    // -- Blocs -----------------------------------------------------------------

    /** Materiau roche, durete 3,5 : a la pioche. */
    public static final DeferredBlock<Block> GUARDIAN_BLOCK = BLOCKS.registerBlock(
            "guardian_block", GuardianBlock::new, properties -> properties
                    .strength(3.5f, 17.5f).requiresCorrectToolForDrops().sound(SoundType.STONE));
    public static final DeferredBlock<Block> GUARDIAN_KEEPER = BLOCKS.registerBlock(
            "guardian_keeper", GuardianKeeperBlock::new, properties -> properties
                    .strength(3.5f, 17.5f).requiresCorrectToolForDrops().sound(SoundType.STONE));
    public static final DeferredBlock<Block> GOLEM_BOX = BLOCKS.registerBlock(
            "guardian_box_block", GolemBoxBlock::new, properties -> properties
                    .strength(5.0f, 25.0f).requiresCorrectToolForDrops().sound(SoundType.STONE));
    public static final DeferredBlock<Block> GOLEM_BOX_FRAME = BLOCKS.registerBlock(
            "guardian_box_frame", Block::new, properties -> properties
                    .strength(5.0f, 25.0f).requiresCorrectToolForDrops().sound(SoundType.STONE));
    public static final DeferredBlock<Block> FOOD_MACHINE = BLOCKS.registerBlock(
            "food_machine", FoodMachineBlock::new, properties -> properties
                    .strength(5.0f, 25.0f).requiresCorrectToolForDrops().sound(SoundType.STONE).noOcclusion());

    public static final List<DeferredBlock<Block>> ORDERED_BLOCKS =
            List.of(GUARDIAN_BLOCK, GUARDIAN_KEEPER, GOLEM_BOX, GOLEM_BOX_FRAME, FOOD_MACHINE);
    private static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = blockItems(ORDERED_BLOCKS);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FoodMachineBlockEntity>> FOOD_MACHINE_ENTITY =
            BLOCK_ENTITIES.register("food_machine",
                    () -> new BlockEntityType<>(FoodMachineBlockEntity::new, Set.of(FOOD_MACHINE.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GolemBoxBlockEntity>> GOLEM_BOX_ENTITY =
            BLOCK_ENTITIES.register("guardian_box_block",
                    () -> new BlockEntityType<>(GolemBoxBlockEntity::new, Set.of(GOLEM_BOX.get())));

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> FOOD_MACHINE_MENU =
            MENUS.register("food_machine", () -> new MenuType<>(
                    (containerId, inventory) -> new MachineMenu(PalaGolem.FOOD_MACHINE_MENU.get(), containerId,
                            inventory, MachineLayout.FOOD_MACHINE),
                    FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> GOLEM_BOX_MENU =
            MENUS.register("golem_box", () -> new MenuType<>(
                    (containerId, inventory) -> new MachineMenu(PalaGolem.GOLEM_BOX_MENU.get(), containerId,
                            inventory, MachineLayout.GOLEM_BOX),
                    FeatureFlags.VANILLA_SET));

    // -- Objets ----------------------------------------------------------------

    public static final DeferredItem<GuardianStoneItem> GUARDIAN_STONE = ITEMS.registerItem(
            "guardian_stone", properties -> new GuardianStoneItem(properties.stacksTo(1)));
    public static final DeferredItem<MiniGolemItem> MINI_GOLEM = ITEMS.registerItem(
            "mini_golem", properties -> new MiniGolemItem(properties.stacksTo(1)));
    /** {@code setMaxDamage(2)} : trois coups. */
    public static final DeferredItem<Item> GUARDIAN_HAMMER = ITEMS.registerItem(
            "guardian_hammer", properties -> new Item(properties.durability(3)));
    public static final DeferredItem<WhitelistItem> WHITELIST = ITEMS.registerItem(
            "whitelist", properties -> new WhitelistItem(properties.stacksTo(1)));
    public static final DeferredItem<Item> WRENCH = ITEMS.registerItem("wrench", Item::new);
    /** {@code guardiangolem:food} : rien d'autre qu'une monnaie pour les createurs. */
    public static final DeferredItem<Item> CROQUETTE = ITEMS.registerItem("croquette", Item::new);

    /**
     * {@code PALADIUM_APPLE} ({@code BaseItemApple(100, 0.6, ...)}) : la pomme
     * en paladium, ingredient de la machine a croquettes. Toujours mangeable,
     * par 16 ; Regeneration III 5 s et Resistance au feu 75 s.
     */
    public static final DeferredItem<Item> PALADIUM_APPLE = ITEMS.registerItem(
            "paladium_apple", properties -> new Item(properties
                    .stacksTo(16)
                    .food(new FoodProperties.Builder().nutrition(100).saturationModifier(0.6f).alwaysEdible().build(),
                            Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
                                    new MobEffectInstance(MobEffects.REGENERATION, 100, 2),
                                    new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1500, 0)))).build())
                    .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

    public static final List<DeferredItem<? extends Item>> ORDERED = List.of(
            GUARDIAN_STONE, MINI_GOLEM, GUARDIAN_HAMMER, WHITELIST, WRENCH, CROQUETTE, PALADIUM_APPLE);

    // -- Recettes speciales --------------------------------------------------

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MiniGolemRecipe>> MINI_GOLEM_RECIPE =
            SERIALIZERS.register("mini_golem", () -> new RecipeSerializer<>(
                    MapCodec.unit(MiniGolemRecipe.INSTANCE), StreamCodec.unit(MiniGolemRecipe.INSTANCE)));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MiniGolemRepairRecipe>>
            MINI_GOLEM_REPAIR_RECIPE = SERIALIZERS.register("mini_golem_repair", () -> new RecipeSerializer<>(
                    MapCodec.unit(MiniGolemRepairRecipe.INSTANCE), StreamCodec.unit(MiniGolemRepairRecipe.INSTANCE)));

    private PalaGolem() {
    }

    private static List<DeferredItem<BlockItem>> blockItems(List<DeferredBlock<Block>> blocks) {
        return blocks.stream().map(block -> ITEMS.registerSimpleBlockItem(block, properties -> properties)).toList();
    }

    public static List<DeferredItem<BlockItem>> blockItems() {
        return BLOCK_ITEMS;
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(GUARDIAN_GOLEM.get(), GuardianGolem.createAttributes().build());
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        ENTITIES.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        SERIALIZERS.register(modBus);
    }
}

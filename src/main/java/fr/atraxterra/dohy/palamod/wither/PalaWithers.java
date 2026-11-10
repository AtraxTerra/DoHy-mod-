package fr.atraxterra.dohy.palamod.wither;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Les withers du pillage (chapitre 22, lot 22d-bis) : le receptacle, les sept
 * withers et leurs gemmes, les tetes, les ameliorations, le laser de ciblage,
 * la selle et la canne a peche de wither, la fleche de mi-vie, la poudre de
 * wither et le quartz electrifie.
 *
 * <p>Le module PalaWither n'est pas dans les jars fournis : seules ses
 * recettes le sont ({@code PPalaWither.init}). Les comportements viennent du
 * wiki ; les noms sont ecrits en toutes lettres pour {@code check_assets.py}.
 */
@EventBusSubscriber
public final class PalaWithers {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PalaMod.NAMESPACE);
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, PalaMod.NAMESPACE);

    /** Le point verrouille par un laser de ciblage. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> LASER_TARGET =
            COMPONENTS.registerComponentType("laser_target", builder -> builder
                    .persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC));

    /** Sur un wither : l'identifiant de son invocateur, vide pour personne. */
    public static final Supplier<AttachmentType<String>> SUMMONER = ATTACHMENTS.register("wither_summoner",
            () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING.fieldOf("summoner"), summoner -> !summoner.isEmpty())
                    .build());

    public static final DeferredHolder<SoundEvent, SoundEvent> FUNNY_WITHER_LOOP = sound("funny_wither.loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> FUNNY_WITHER_SPAWN = sound("funny_wither.spawn");
    public static final DeferredHolder<SoundEvent, SoundEvent> FUNNY_WITHER_INVOKE = sound("funny_wither.invoke");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, name)));
    }

    // -- Withers -----------------------------------------------------------------

    public static final DeferredHolder<EntityType<?>, EntityType<PalaWither>> PALADIUM_WITHER = wither("paladium_wither");
    public static final DeferredHolder<EntityType<?>, EntityType<PalaWither>> FUNNY_WITHER = wither("funny_wither");
    public static final DeferredHolder<EntityType<?>, EntityType<PalaWither>> BABY_WITHER = wither("baby_wither");
    public static final DeferredHolder<EntityType<?>, EntityType<PalaWither>> PASSIVE_WITHER = wither("passive_wither");
    public static final DeferredHolder<EntityType<?>, EntityType<PalaWither>> THIRSTY_WITHER = wither("thirsty_wither");
    public static final DeferredHolder<EntityType<?>, EntityType<PalaWither>> PREDATOR_WITHER = wither("predator_wither");
    public static final DeferredHolder<EntityType<?>, EntityType<PalaWither>> SUPREME_WITHER = wither("supreme_wither");

    /** Les proportions du wither vanilla ; la taille de chacun vient de son attribut d'echelle. */
    private static DeferredHolder<EntityType<?>, EntityType<PalaWither>> wither(String name) {
        return ENTITIES.register(name, key -> EntityType.Builder.<PalaWither>of(PalaWither::new, MobCategory.MONSTER)
                .fireImmune()
                .sized(0.9f, 3.5f)
                .clientTrackingRange(10)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));
    }

    private static Map<WitherKind, DeferredHolder<EntityType<?>, EntityType<PalaWither>>> types;

    public static synchronized Supplier<? extends EntityType<PalaWither>> type(WitherKind kind) {
        if (types == null) {
            Map<WitherKind, DeferredHolder<EntityType<?>, EntityType<PalaWither>>> built = new EnumMap<>(WitherKind.class);
            built.put(WitherKind.BASE, PALADIUM_WITHER);
            built.put(WitherKind.FUNNY, FUNNY_WITHER);
            built.put(WitherKind.BABY, BABY_WITHER);
            built.put(WitherKind.PASSIVE, PASSIVE_WITHER);
            built.put(WitherKind.THIRSTY, THIRSTY_WITHER);
            built.put(WitherKind.PREDATOR, PREDATOR_WITHER);
            built.put(WitherKind.SUPREME, SUPREME_WITHER);
            types = built;
        }
        return types.get(kind);
    }

    // -- Blocs ---------------------------------------------------------------------

    /**
     * Le receptacle : une machine (pierre, 3,5), que les explosions n'entament
     * qu'a travers ses points de vie.
     */
    public static final DeferredBlock<WitherReceptacleBlock> WITHER_RECEPTACLE = BLOCKS.registerBlock(
            "wither_receptacle", WitherReceptacleBlock::new, properties -> properties
                    .strength(3.5f, 3_600_000.0f).sound(SoundType.STONE).mapColor(MapColor.COLOR_BLACK)
                    .requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK).noOcclusion()
                    .lightLevel(state -> 6));
    /** Neuf blocs de quartz ; le premier des cinq paliers de PalaMod, le seul qui serve ici. */
    public static final DeferredBlock<Block> COMPRESSED_QUARTZ = BLOCKS.registerBlock(
            "compressed_quartz", Block::new, properties -> properties
                    .strength(1.6f).sound(SoundType.STONE).mapColor(MapColor.QUARTZ).requiresCorrectToolForDrops());
    /** Le quartz electrifie ({@code EMPOWERED_QUARTZ}) : receptacle, tete assoiffee, laser. */
    public static final DeferredBlock<Block> EMPOWERED_QUARTZ = BLOCKS.registerBlock(
            "empowered_quartz", Block::new, properties -> properties
                    .strength(0.8f).sound(SoundType.AMETHYST).mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .requiresCorrectToolForDrops().lightLevel(state -> 7));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WitherReceptacleBlockEntity>>
            RECEPTACLE_ENTITY = BLOCK_ENTITIES.register("wither_receptacle",
                    () -> new BlockEntityType<>(WitherReceptacleBlockEntity::new, Set.<Block>of(WITHER_RECEPTACLE.get())));

    // -- Objets --------------------------------------------------------------------

    public static final DeferredItem<WitherGemItem> WITHER_GEM = ITEMS.registerItem(
            "wither_gem", properties -> new WitherGemItem(WitherKind.BASE, properties.stacksTo(16)));
    public static final DeferredItem<WitherGemItem> FUNNY_WITHER_GEM = ITEMS.registerItem(
            "funny_wither_gem", properties -> new WitherGemItem(WitherKind.FUNNY, properties.stacksTo(16)));
    public static final DeferredItem<WitherGemItem> BABY_WITHER_GEM = ITEMS.registerItem(
            "baby_wither_gem", properties -> new WitherGemItem(WitherKind.BABY, properties.stacksTo(16)));
    public static final DeferredItem<WitherGemItem> PASSIVE_WITHER_GEM = ITEMS.registerItem(
            "passive_wither_gem", properties -> new WitherGemItem(WitherKind.PASSIVE, properties.stacksTo(16)));
    public static final DeferredItem<WitherGemItem> THIRSTY_WITHER_GEM = ITEMS.registerItem(
            "thirsty_wither_gem", properties -> new WitherGemItem(WitherKind.THIRSTY, properties.stacksTo(16)));
    public static final DeferredItem<WitherGemItem> PREDATOR_WITHER_GEM = ITEMS.registerItem(
            "predator_wither_gem", properties -> new WitherGemItem(WitherKind.PREDATOR, properties.stacksTo(16)));
    public static final DeferredItem<WitherGemItem> SUPREME_WITHER_GEM = ITEMS.registerItem(
            "supreme_wither_gem", properties -> new WitherGemItem(WitherKind.SUPREME, properties.stacksTo(16)));

    public static final DeferredItem<Item> PASSIVE_WITHER_HEAD = ITEMS.registerItem("passive_wither_head", Item::new);
    public static final DeferredItem<Item> THIRSTY_WITHER_HEAD = ITEMS.registerItem("thirsty_wither_head", Item::new);
    public static final DeferredItem<Item> PREDATOR_WITHER_HEAD = ITEMS.registerItem("predator_wither_head", Item::new);
    public static final DeferredItem<Item> SUPREME_WITHER_HEAD = ITEMS.registerItem("supreme_wither_head", Item::new);

    public static final DeferredItem<WitherUpgradeItem> STEALTH_UPGRADE = ITEMS.registerItem(
            "stealth_upgrade", properties -> new WitherUpgradeItem(WitherUpgrade.STEALTH, properties.stacksTo(16)));
    public static final DeferredItem<WitherUpgradeItem> WITHER_RESISTANCE_UPGRADE = ITEMS.registerItem(
            "wither_resistance_upgrade", properties -> new WitherUpgradeItem(WitherUpgrade.RESISTANCE,
                    properties.stacksTo(16)));
    public static final DeferredItem<WitherUpgradeItem> WITHER_SLIME_UPGRADE = ITEMS.registerItem(
            "wither_slime_upgrade", properties -> new WitherUpgradeItem(WitherUpgrade.SLIME, properties.stacksTo(16)));

    public static final DeferredItem<WitherLaserItem> WITHER_TARGET_LASER = ITEMS.registerItem(
            "wither_target_laser", properties -> new WitherLaserItem(properties.durability(16)));
    public static final DeferredItem<WitherSaddleItem> WITHER_SADDLE = ITEMS.registerItem(
            "wither_saddle", properties -> new WitherSaddleItem(properties.stacksTo(1)));
    public static final DeferredItem<Item> WITHER_FISHING_ROD = ITEMS.registerItem(
            "wither_fishing_rod", properties -> new Item(properties.stacksTo(1)));
    public static final DeferredItem<MidLifeArrowItem> MID_LIFE_ARROW = ITEMS.registerItem(
            "mid_life_arrow", MidLifeArrowItem::new);
    /** Lachee par les sorcieres, des le niveau 20 de Chasseur. */
    public static final DeferredItem<Item> WITHER_DUST = ITEMS.registerItem("wither_dust", Item::new);

    // -- Onglet --------------------------------------------------------------------

    public static final List<DeferredBlock<? extends Block>> ORDERED_BLOCKS = List.of(
            WITHER_RECEPTACLE, COMPRESSED_QUARTZ, EMPOWERED_QUARTZ);
    private static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = ORDERED_BLOCKS.stream()
            .map(block -> ITEMS.registerSimpleBlockItem(block, properties -> properties)).toList();

    public static final List<DeferredItem<? extends Item>> ORDERED_ITEMS = List.of(
            WITHER_GEM, FUNNY_WITHER_GEM, BABY_WITHER_GEM, PASSIVE_WITHER_GEM, THIRSTY_WITHER_GEM,
            PREDATOR_WITHER_GEM, SUPREME_WITHER_GEM,
            PASSIVE_WITHER_HEAD, THIRSTY_WITHER_HEAD, PREDATOR_WITHER_HEAD, SUPREME_WITHER_HEAD,
            STEALTH_UPGRADE, WITHER_RESISTANCE_UPGRADE, WITHER_SLIME_UPGRADE,
            WITHER_TARGET_LASER, WITHER_SADDLE, WITHER_FISHING_ROD, MID_LIFE_ARROW, WITHER_DUST);

    private PalaWithers() {
    }

    /** Tout l'onglet : les blocs, puis les objets. */
    public static List<Supplier<? extends Item>> tab() {
        List<Supplier<? extends Item>> all = new ArrayList<>(BLOCK_ITEMS);
        all.addAll(ORDERED_ITEMS);
        return all;
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (WitherKind kind : WitherKind.values()) {
            event.put(type(kind).get(), PalaWither.attributes(kind).build());
        }
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        SOUNDS.register(modBus);
    }
}

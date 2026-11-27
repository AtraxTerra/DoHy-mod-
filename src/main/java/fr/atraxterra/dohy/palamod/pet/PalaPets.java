package fr.atraxterra.dohy.palamod.pet;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Les familiers : la cage, l'appat (le venin de serpent, la coquille
 * d'escargot), le serpent et l'escargot de PalaMod, la friandise et les potions
 * d'XP de familier, et ce que le joueur garde de son familier.
 */
public final class PalaPets {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, PalaMod.NAMESPACE);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PalaMod.NAMESPACE);

    // -- Ce que le joueur garde ----------------------------------------------------------------

    /** Le familier, garde a la mort ; il ne quitte pas le serveur. */
    public static final Supplier<AttachmentType<PetData>> DATA = ATTACHMENTS.register("pet",
            () -> AttachmentType.builder(PetData::new)
                    .serialize(PetData.MAP_CODEC, PetData::has)
                    .copyOnDeath()
                    .build());

    /** Son espece et sa visibilite, envoyees a ceux qui voient le joueur. */
    public static final Supplier<AttachmentType<PetLook>> LOOK = ATTACHMENTS.register("pet_look",
            () -> AttachmentType.builder(() -> PetLook.NONE)
                    .serialize(PetLook.MAP_CODEC, look -> !look.kind().isEmpty())
                    .sync(PetLook.STREAM_CODEC)
                    .copyOnDeath()
                    .build());

    // -- La cage --------------------------------------------------------------------------------

    public static final DeferredBlock<PetCageBlock> PET_CAGE = BLOCKS.registerBlock("pet_cage", PetCageBlock::new,
            properties -> properties.strength(3.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .mapColor(MapColor.METAL).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PetCageBlockEntity>> PET_CAGE_TYPE =
            TYPES.register("pet_cage",
                    () -> new BlockEntityType<>(PetCageBlockEntity::new, Set.<Block>of(PET_CAGE.get())));
    public static final DeferredItem<BlockItem> PET_CAGE_ITEM =
            ITEMS.registerSimpleBlockItem(PET_CAGE, properties -> properties);

    /** L'appat : tue un serpent (75 %) ou un escargot (25 %) pour en avoir un. */
    public static final DeferredItem<PetBaitItem> PET_BAIT = ITEMS.registerItem("pet_bait", PetBaitItem::new);

    // -- Le serpent et l'escargot ------------------------------------------------------------------

    /** {@code EntitySnake} : {@code setSize(4F, 1F)}, un serpent de cinq blocs, venimeux. */
    public static final DeferredHolder<EntityType<?>, EntityType<Snake>> SNAKE = ENTITIES.register("snake",
            key -> EntityType.Builder.<Snake>of(Snake::new, MobCategory.MONSTER)
                    .sized(4.0f, 1.0f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));
    /** {@code EntitySnail} : 1,1 x 0,9, il fuit le joueur. */
    public static final DeferredHolder<EntityType<?>, EntityType<Snail>> SNAIL = ENTITIES.register("snail",
            key -> EntityType.Builder.<Snail>of(Snail::new, MobCategory.CREATURE)
                    .sized(1.1f, 0.9f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_LIVING = sound("snake.living");
    public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_HURT = sound("snake.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_DEATH = sound("snake.death");

    /** Les oeufs de PalaMod : vert pour le serpent, rose pour l'escargot ({@code registerGlobalEntityID}). */
    public static final DeferredItem<SpawnEggItem> SNAKE_SPAWN_EGG = ITEMS.registerItem("snake_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(SNAKE.get())));
    public static final DeferredItem<SpawnEggItem> SNAIL_SPAWN_EGG = ITEMS.registerItem("snail_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(SNAIL.get())));

    // -- Friandise et potions ----------------------------------------------------------------------

    /** {@code pet_snack} : la quete « nourrir », et du bonheur. */
    public static final DeferredItem<PetSnackItem> PET_SNACK = ITEMS.registerItem("pet_snack",
            properties -> new PetSnackItem(properties.stacksTo(16)));
    /** {@code pet_xp_small / medium / big} : de l'XP de familier. */
    public static final DeferredItem<PetXpPotionItem> PET_XP_SMALL = ITEMS.registerItem("pet_xp_small",
            properties -> new PetXpPotionItem(1_000L, potion(properties)));
    public static final DeferredItem<PetXpPotionItem> PET_XP_MEDIUM = ITEMS.registerItem("pet_xp_medium",
            properties -> new PetXpPotionItem(5_000L, potion(properties)));
    public static final DeferredItem<PetXpPotionItem> PET_XP_BIG = ITEMS.registerItem("pet_xp_big",
            properties -> new PetXpPotionItem(20_000L, potion(properties)));

    public static final List<DeferredItem<? extends Item>> ORDERED = List.of(
            PET_CAGE_ITEM, PET_BAIT, PET_SNACK, PET_XP_SMALL, PET_XP_MEDIUM, PET_XP_BIG, SNAKE_SPAWN_EGG, SNAIL_SPAWN_EGG);

    private PalaPets() {
    }

    /** Bue en 1,6 s, comme les potions d'XP de metier, sans fiole rendue. */
    private static Item.Properties potion(Item.Properties properties) {
        return properties.stacksTo(16)
                .component(DataComponents.CONSUMABLE, Consumable.builder()
                        .consumeSeconds(1.6f)
                        .animation(ItemUseAnimation.DRINK)
                        .sound(SoundEvents.GENERIC_DRINK)
                        .hasConsumeParticles(false)
                        .build());
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, name)));
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(PetEntities::onAttributes);
        modBus.addListener(PetEntities::onSpawnPlacements);
    }
}

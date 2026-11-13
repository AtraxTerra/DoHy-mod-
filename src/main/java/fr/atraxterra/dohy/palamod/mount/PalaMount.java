package fr.atraxterra.dohy.palamod.mount;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Les montures (mod Mount 6.1.0 de Paladium) : le Dancarok, sa gemme, ses
 * cornes, le bandage, l'orbe de regeneration qui le fabrique, et l'enclos —
 * gestionnaire, couveuse, fondation, barriere.
 *
 * <p>Tout passe sous l'espace {@code palamod}, comme le reste du portage.
 * Le Ravirok et le Tedarok, les deux autres especes du mod, ne sont pas
 * portes.
 */
@EventBusSubscriber
public final class PalaMount {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
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

    // -- Donnees ---------------------------------------------------------------

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MountGem>> MOUNT_GEM =
            COMPONENTS.registerComponentType("mount_gem", builder -> builder
                    .persistent(MountGem.CODEC).networkSynchronized(MountGem.STREAM_CODEC));

    /**
     * La monture equipee. Copiee a la mort, comme {@code PlayerEvent.Clone}
     * le faisait (et seulement a la mort : l'original perdait tout au retour de
     * l'End ; NeoForge garde les pieces jointes dans ce cas de toute facon).
     */
    public static final Supplier<AttachmentType<MountData>> MOUNT = ATTACHMENTS.register(
            "mount", () -> AttachmentType.builder(MountData::new)
                    .serialize(MountData.MAP_CODEC, data -> !data.isBlank())
                    .copyOnDeath()
                    .build());

    // -- Sons (assets/mount/sounds.json, categorie neutral) ----------------------

    public static final DeferredHolder<SoundEvent, SoundEvent> DANCAROK_WALK = sound("dancarok.walk");
    public static final DeferredHolder<SoundEvent, SoundEvent> DANCAROK_IDLE = sound("dancarok.idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> DANCAROK_JUMP = sound("dancarok.jump");
    public static final DeferredHolder<SoundEvent, SoundEvent> DANCAROK_DESTROY = sound("dancarok.destroy");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOUNT_SUMMON = sound("mount.summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOUNT_UNSUMMON = sound("mount.unsummon");

    // -- Le Dancarok -------------------------------------------------------------

    /**
     * {@code EntityDancarokMount} : 2,5 x 1,9. Le cavalier est assis a
     * {@code getMountedYOffset} = 0,9 x hauteur ; 1,81 ici remet ses pieds a la
     * meme hauteur qu'en 1.7.10, ou la position du joueur etait celle de ses
     * yeux.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<Dancarok>> DANCAROK = ENTITIES.register(
            "dancarok", key -> EntityType.Builder.<Dancarok>of(Dancarok::new, MobCategory.CREATURE)
                    .sized(2.5f, 1.9f)
                    .passengerAttachments(1.81f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    // -- Blocs : bois, durete 3, setResistance(8) ---------------------------------

    public static final DeferredBlock<Block> BARN_CONTROLLER = BLOCKS.registerBlock(
            "barn_controller", BarnControllerBlock::new, properties -> properties
                    .strength(3.0f, 4.8f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<Block> BARN_INCUBATOR = BLOCKS.registerBlock(
            "barn_incubator", Block::new, properties -> properties.strength(3.0f, 4.8f).sound(SoundType.WOOD));
    public static final DeferredBlock<Block> BARN_FOUNDATION = BLOCKS.registerBlock(
            "barn_foundation", Block::new, properties -> properties.strength(3.0f, 4.8f).sound(SoundType.WOOD));
    public static final DeferredBlock<Block> BARN_FENCE = BLOCKS.registerBlock(
            "barn_fence", BarnFenceBlock::new, properties -> properties
                    .strength(3.0f, 4.8f).sound(SoundType.WOOD).forceSolidOn());

    public static final List<DeferredBlock<Block>> ORDERED_BLOCKS =
            List.of(BARN_CONTROLLER, BARN_INCUBATOR, BARN_FOUNDATION, BARN_FENCE);
    private static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = ORDERED_BLOCKS.stream()
            .map(block -> ITEMS.registerSimpleBlockItem(block, properties -> properties)).toList();

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BarnControllerBlockEntity>> BARN_ENTITY =
            BLOCK_ENTITIES.register("barn_controller",
                    () -> new BlockEntityType<>(BarnControllerBlockEntity::new, Set.of(BARN_CONTROLLER.get())));

    // -- Objets --------------------------------------------------------------------

    public static final DeferredItem<MountGemstoneItem> MOUNT_GEMSTONE = ITEMS.registerItem(
            "mount_gemstone", properties -> new MountGemstoneItem(properties.stacksTo(1)));
    /** {@code ItemBandage} : par un, comme l'original. */
    public static final DeferredItem<Item> BANDAGE = ITEMS.registerItem(
            "bandage", properties -> new Item(properties.stacksTo(1)));
    public static final DeferredItem<MountHornItem> AMETHYST_HORN = ITEMS.registerItem(
            "amethyst_horn", properties -> new MountHornItem(MountHornItem.Tier.AMETHYST, properties));
    public static final DeferredItem<MountHornItem> TITANE_HORN = ITEMS.registerItem(
            "titane_horn", properties -> new MountHornItem(MountHornItem.Tier.TITANE, properties));
    public static final DeferredItem<MountHornItem> PALADIUM_HORN = ITEMS.registerItem(
            "paladium_horn", properties -> new MountHornItem(MountHornItem.Tier.PALADIUM, properties));
    /** {@code ORB_HEAL} : un {@code BaseItem} nu, ingredient du bandage et de la couveuse. */
    public static final DeferredItem<Item> HEAL_ORB = ITEMS.registerItem("heal_orb", Item::new);

    public static final List<DeferredItem<? extends Item>> ORDERED =
            List.of(MOUNT_GEMSTONE, BANDAGE, AMETHYST_HORN, TITANE_HORN, PALADIUM_HORN, HEAL_ORB);

    private PalaMount() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, name)));
    }

    public static List<DeferredItem<BlockItem>> blockItems() {
        return BLOCK_ITEMS;
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(DANCAROK.get(), Dancarok.createAttributes().build());
    }

    /**
     * Aucun code d'apparition dans les jars de Paladium : les sauvages y etaient
     * poses a la main sur le serveur « Farmland ». Ici, une apparition rare dans
     * les plaines et les savanes (tag palamod:dancarok_spawns, regle vanilla de
     * biome_modifier/dancarok.json), sur un sol ou un animal pourrait
     * apparaitre — et {@link DancarokSpawner}, qui en pose aussi sur une carte
     * deja generee.
     */
    @SubscribeEvent
    public static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(DANCAROK.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Dancarok::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        ENTITIES.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        SOUNDS.register(modBus);
    }
}

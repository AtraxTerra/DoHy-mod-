package fr.atraxterra.dohy.palamod.hunter;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
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
 * Le Chasseur (chapitre 10) : canne a peche en paladium, capture de creatures,
 * marteau des cavernes et spawners customises. L'epee en endium est dans
 * {@code PalaEquipment}, avec les autres pieces d'endium.
 */
public final class PalaHunter {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, PalaMod.NAMESPACE);

    // -- Donnees ---------------------------------------------------------------

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CapturedEntity>> CAPTURED =
            COMPONENTS.registerComponentType("captured_entity", builder -> builder
                    .persistent(CapturedEntity.CODEC)
                    .networkSynchronized(CapturedEntity.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpawnerSouls>> SOULS =
            COMPONENTS.registerComponentType("spawner_souls", builder -> builder
                    .persistent(SpawnerSouls.CODEC)
                    .networkSynchronized(SpawnerSouls.STREAM_CODEC));

    /** Les ames d'un spawner customise pose, sur la block entity du spawner vanilla. */
    public static final Supplier<AttachmentType<SpawnerSouls>> SPAWNER_SOULS = ATTACHMENTS.register(
            "spawner_souls", () -> AttachmentType.builder(() -> SpawnerSouls.EMPTY)
                    .serialize(SpawnerSouls.MAP_CODEC)
                    .build());

    /**
     * Le controleur qui compte ce spawner customise (sa position), ou
     * {@link CustomSpawnerBlock#NO_LINK}.
     */
    public static final Supplier<AttachmentType<Long>> SPAWNER_LINK = ATTACHMENTS.register(
            "spawner_link", () -> AttachmentType.builder(() -> CustomSpawnerBlock.NO_LINK)
                    .serialize(Codec.LONG.fieldOf("controller"))
                    .build());

    /** Une pile de creatures ; sauvegardee avec la creature. */
    public static final Supplier<AttachmentType<MobStack>> MOB_STACK = ATTACHMENTS.register(
            "mob_stack", () -> AttachmentType.builder(() -> new MobStack(1, net.minecraft.core.BlockPos.ZERO))
                    .serialize(MobStack.MAP_CODEC)
                    .build());

    // -- Canne a peche ---------------------------------------------------------

    /**
     * Canne a peche en paladium ({@code palajobs:paladium_rod}) : 128
     * utilisations au lieu de 64, enchantabilite 1.
     *
     * <p>Elle ne peche pas plus vite (le wiki se trompe) : dans PalaJobs, elle
     * changeait les chances du mini-jeu de peche — prise rare de 7 a 27 %,
     * legendaire de 0 a 3 %. Ce mini-jeu et ses poissons ne sont pas portes ;
     * l'avantage passe par la chance, +3 en main, ce que fait Chance de la mer
     * III : le tresor passe d'environ 5 a 11 % des prises.
     */
    public static final DeferredItem<FishingRodItem> PALADIUM_FISHING_ROD = ITEMS.registerItem(
            "paladium_fishing_rod", properties -> new FishingRodItem(properties
                    .durability(128)
                    .enchantable(1)
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.LUCK, new AttributeModifier(
                                    Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "paladium_fishing_rod_luck"),
                                    3.0d, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HAND)
                            .build())));

    // -- Capture -----------------------------------------------------------------

    /** {@code ItemCaptureSword} : materiau diamant, 32 utilisations. */
    public static final DeferredItem<Item> CAPTURE_SWORD = ITEMS.registerItem(
            "capture_sword", properties -> new Item(properties
                    .sword(ToolMaterial.DIAMOND, 3.0f, -2.4f)
                    .durability(32)));

    /** {@code ItemCaptureStone} : par un, vide ou pleine. */
    public static final DeferredItem<CaptureStoneItem> CAPTURE_STONE = ITEMS.registerItem(
            "capture_stone", properties -> new CaptureStoneItem(properties.stacksTo(1)));

    // -- Spawners ----------------------------------------------------------------

    /** {@code ItemCavernHammer} : pioche de diamant, vitesse 8, 256 utilisations. */
    public static final DeferredItem<CavernHammerItem> CAVERN_HAMMER = ITEMS.registerItem(
            "cavern_hammer", properties -> new CavernHammerItem(properties
                    .pickaxe(ToolMaterial.DIAMOND, 1.0f, -2.8f)
                    .durability(256)));

    /** {@code empty_broken_mob_spawner} : ne se pose pas, par 64. */
    public static final DeferredItem<Item> BROKEN_SPAWNER = ITEMS.registerItem(
            "broken_spawner", Item::new);

    /**
     * {@code BlockEmptySpawner} : pierre, 5 / 6, comme le spawner vanilla. Sans
     * outil requis : il se rend meme casse a la main, comme dans l'original.
     */
    public static final DeferredBlock<CustomSpawnerBlock> CUSTOM_SPAWNER = BLOCKS.registerBlock(
            "empty_mob_spawner", CustomSpawnerBlock::new, properties -> properties
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.SPAWNER)
                    .noOcclusion());

    public static final DeferredItem<CustomSpawnerItem> CUSTOM_SPAWNER_ITEM = ITEMS.registerItem(
            "empty_mob_spawner", properties -> new CustomSpawnerItem(CUSTOM_SPAWNER.get(),
                    properties.stacksTo(1).useBlockDescriptionPrefix()));

    // -- Controleur de spawners ----------------------------------------------------

    /**
     * {@code BlockSpawnerTier} : les obsidiennes infusees des paliers I a IV
     * (diamant, amethyste, titane, paladium). Durete 50, resistance 2000 de
     * 1.7.10 : celles de l'obsidienne.
     */
    public static final List<DeferredBlock<Block>> SPAWNER_STRUCTURES = List.of(
            BLOCKS.registerBlock("spawner_structure_one", Block::new, PalaHunter::infusedObsidian),
            BLOCKS.registerBlock("spawner_structure_two", Block::new, PalaHunter::infusedObsidian),
            BLOCKS.registerBlock("spawner_structure_three", Block::new, PalaHunter::infusedObsidian),
            BLOCKS.registerBlock("spawner_structure_four", Block::new, PalaHunter::infusedObsidian));

    private static net.minecraft.world.level.block.state.BlockBehaviour.Properties infusedObsidian(
            net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
        return properties.strength(50.0f, 1200.0f).requiresCorrectToolForDrops().sound(SoundType.STONE);
    }

    /** {@code BlockSpawnerController} : roche, durete 2, resistance 10 de 1.7.10. */
    public static final DeferredBlock<SpawnerControllerBlock> SPAWNER_CONTROLLER = BLOCKS.registerBlock(
            "spawner_controller", SpawnerControllerBlock::new, properties -> properties
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.STONE));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpawnerControllerBlockEntity>>
            SPAWNER_CONTROLLER_TYPE = TYPES.register("spawner_controller",
            () -> new BlockEntityType<>(SpawnerControllerBlockEntity::new, Set.of(SPAWNER_CONTROLLER.get())));

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> SPAWNER_CONTROLLER_MENU =
            MENUS.register("spawner_controller", () -> new MenuType<>(
                    (containerId, inventory) -> new MachineMenu(
                            PalaHunter.SPAWNER_CONTROLLER_MENU.get(), containerId, inventory,
                            MachineLayout.SPAWNER_CONTROLLER),
                    FeatureFlags.VANILLA_SET));

    public static final List<DeferredItem<BlockItem>> SPAWNER_STRUCTURE_ITEMS = SPAWNER_STRUCTURES.stream()
            .map(block -> ITEMS.registerSimpleBlockItem(block, properties -> properties)).toList();

    public static final DeferredItem<BlockItem> SPAWNER_CONTROLLER_ITEM =
            ITEMS.registerSimpleBlockItem(SPAWNER_CONTROLLER, properties -> properties);

    /** {@code AItemSpawnerUpgrade} : vitesse, capacite, butin, slime. Par 64, une par case. */
    public static final DeferredItem<SpawnerUpgradeItem> UPGRADE_SPEED = ITEMS.registerItem(
            "upgrade_speed", properties -> new SpawnerUpgradeItem(SpawnerUpgrade.SPEED, properties));
    public static final DeferredItem<SpawnerUpgradeItem> UPGRADE_MORE = ITEMS.registerItem(
            "upgrade_more", properties -> new SpawnerUpgradeItem(SpawnerUpgrade.MORE, properties));
    public static final DeferredItem<SpawnerUpgradeItem> UPGRADE_LOOTING = ITEMS.registerItem(
            "upgrade_looting", properties -> new SpawnerUpgradeItem(SpawnerUpgrade.LOOTING, properties));
    public static final DeferredItem<SpawnerUpgradeItem> UPGRADE_SLIME = ITEMS.registerItem(
            "upgrade_slime", properties -> new SpawnerUpgradeItem(SpawnerUpgrade.SLIME, properties));

    public static final List<DeferredItem<? extends Item>> ORDERED = ordered();

    private static List<DeferredItem<? extends Item>> ordered() {
        List<DeferredItem<? extends Item>> list = new ArrayList<>(List.of(
                PALADIUM_FISHING_ROD, CAPTURE_SWORD, CAPTURE_STONE,
                CAVERN_HAMMER, BROKEN_SPAWNER, CUSTOM_SPAWNER_ITEM, SPAWNER_CONTROLLER_ITEM));
        list.addAll(SPAWNER_STRUCTURE_ITEMS);
        list.addAll(List.of(UPGRADE_SPEED, UPGRADE_LOOTING, UPGRADE_SLIME, UPGRADE_MORE));
        return List.copyOf(list);
    }

    private PalaHunter() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        MENUS.register(modBus);
    }
}

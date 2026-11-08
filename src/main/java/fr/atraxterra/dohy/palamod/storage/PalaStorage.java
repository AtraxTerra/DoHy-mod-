package fr.atraxterra.dohy.palamod.storage;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.Set;

/**
 * Coffres et entonnoir de PalaMod.
 *
 * <p>Les coffres ne sont pas des objets du Mineur, mais deux de ses recettes les
 * demandent : la Voidstone minage prend un coffre en titane, l'entonnoir en
 * paladium aussi, et le coffre en titane se fait avec un coffre en amethyste.
 */
public final class PalaStorage {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, PalaMod.NAMESPACE);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PalaMod.NAMESPACE);

    /**
     * Les coffres, un par palier. Ceux de PalaMod : fer, durete 12,
     * setResistance(8) soit 4,8, pioche en fer. Le coffre en or d'Iron Chests :
     * durete 3, a la pioche.
     */
    public static final Map<ChestTier, DeferredBlock<PalaChestBlock>> CHESTS = chests();

    // Fer, durete 5 sans setResistance ; n'importe quelle pioche.
    public static final DeferredBlock<PaladiumHopperBlock> PALADIUM_HOPPER_BLOCK = BLOCKS.registerBlock(
            "paladium_hopper", PaladiumHopperBlock::new,
            properties -> properties.strength(5.0f).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion());

    /** Fer, durete 12, setResistance(8) soit 4,8 ; a la pioche. */
    public static final DeferredBlock<PaladiumEnderChestBlock> PALADIUM_ENDER_CHEST = BLOCKS.registerBlock(
            "paladium_ender_chest", PaladiumEnderChestBlock::new,
            properties -> properties.strength(12.0f, 4.8f).sound(SoundType.STONE).requiresCorrectToolForDrops()
                    .noOcclusion());

    public static final Map<ChestTier, DeferredItem<BlockItem>> CHEST_ITEMS = chestItems(CHESTS);
    public static final DeferredItem<BlockItem> PALADIUM_ENDER_CHEST_ITEM =
            ITEMS.registerSimpleBlockItem(PALADIUM_ENDER_CHEST, properties -> properties);

    public static final java.util.function.Supplier<AttachmentType<EnderExtra>> ENDER_EXTRA = ATTACHMENTS.register(
            "paladium_ender_chest", () -> AttachmentType.builder(EnderExtra::empty)
                    .serialize(EnderExtra.MAP_CODEC, extra -> !extra.isEmpty())
                    .copyOnDeath()
                    .build());
    public static final DeferredItem<BlockItem> PALADIUM_HOPPER_ITEM =
            ITEMS.registerSimpleBlockItem(PALADIUM_HOPPER_BLOCK, properties -> properties);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PalaChestBlockEntity>> CHEST =
            BLOCK_ENTITIES.register("chest", () -> new BlockEntityType<>(PalaChestBlockEntity::new,
                    CHESTS.values().stream().map(DeferredBlock::get).collect(Collectors.toSet())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PaladiumHopperBlockEntity>> PALADIUM_HOPPER =
            BLOCK_ENTITIES.register("paladium_hopper", () -> new BlockEntityType<>(PaladiumHopperBlockEntity::new,
                    Set.of(PALADIUM_HOPPER_BLOCK.get())));

    /** Un type de menu par coffre : le client en tire la taille de la grille. */
    public static final Map<ChestTier, DeferredHolder<MenuType<?>, MenuType<PalaChestMenu>>> CHEST_MENUS = menus();

    /** L'ordre de l'onglet creatif. */
    public static final List<DeferredItem<? extends Item>> ORDERED =
            ordered(CHEST_ITEMS, PALADIUM_ENDER_CHEST_ITEM, PALADIUM_HOPPER_ITEM);

    private PalaStorage() {
    }

    private static Map<ChestTier, DeferredBlock<PalaChestBlock>> chests() {
        Map<ChestTier, DeferredBlock<PalaChestBlock>> map = new EnumMap<>(ChestTier.class);
        for (ChestTier tier : ChestTier.values()) {
            map.put(tier, BLOCKS.registerBlock(tier.key(), properties -> new PalaChestBlock(tier, properties),
                    properties -> (tier == ChestTier.GOLD ? properties.strength(3.0f) : properties.strength(12.0f, 4.8f))
                            .sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
        }
        return map;
    }

    private static Map<ChestTier, DeferredItem<BlockItem>> chestItems(Map<ChestTier, DeferredBlock<PalaChestBlock>> blocks) {
        Map<ChestTier, DeferredItem<BlockItem>> map = new EnumMap<>(ChestTier.class);
        blocks.forEach((tier, block) -> map.put(tier, ITEMS.registerSimpleBlockItem(block, properties -> properties)));
        return map;
    }

    private static Map<ChestTier, DeferredHolder<MenuType<?>, MenuType<PalaChestMenu>>> menus() {
        Map<ChestTier, DeferredHolder<MenuType<?>, MenuType<PalaChestMenu>>> map = new EnumMap<>(ChestTier.class);
        for (ChestTier tier : ChestTier.values()) {
            map.put(tier, MENUS.register(tier.key(), () -> new MenuType<>(
                    (containerId, inventory) -> new PalaChestMenu(PalaStorage.CHEST_MENUS.get(tier).get(),
                            tier, containerId, inventory),
                    FeatureFlags.VANILLA_SET)));
        }
        return map;
    }

    private static List<DeferredItem<? extends Item>> ordered(Map<ChestTier, DeferredItem<BlockItem>> chests,
                                                             DeferredItem<BlockItem> ender,
                                                             DeferredItem<BlockItem> hopper) {
        List<DeferredItem<? extends Item>> list = new ArrayList<>(chests.values());
        list.add(ender);
        list.add(hopper);
        return List.copyOf(list);
    }

    public static MenuType<PalaChestMenu> menuOf(ChestTier tier) {
        return CHEST_MENUS.get(tier).get();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}

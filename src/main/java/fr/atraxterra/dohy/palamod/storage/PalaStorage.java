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

    // Fer, durete 12, setResistance(8) soit 4,8 ; pioche en fer.
    public static final DeferredBlock<PalaChestBlock> AMETHYST_CHEST = BLOCKS.registerBlock(
            "amethyst_chest", properties -> new PalaChestBlock(ChestTier.AMETHYST, properties),
            properties -> properties.strength(12.0f, 4.8f).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredBlock<PalaChestBlock> TITANE_CHEST = BLOCKS.registerBlock(
            "titane_chest", properties -> new PalaChestBlock(ChestTier.TITANE, properties),
            properties -> properties.strength(12.0f, 4.8f).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion());

    // Fer, durete 5 sans setResistance ; n'importe quelle pioche.
    public static final DeferredBlock<PaladiumHopperBlock> PALADIUM_HOPPER_BLOCK = BLOCKS.registerBlock(
            "paladium_hopper", PaladiumHopperBlock::new,
            properties -> properties.strength(5.0f).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion());

    public static final DeferredItem<BlockItem> AMETHYST_CHEST_ITEM =
            ITEMS.registerSimpleBlockItem(AMETHYST_CHEST, properties -> properties);
    public static final DeferredItem<BlockItem> TITANE_CHEST_ITEM =
            ITEMS.registerSimpleBlockItem(TITANE_CHEST, properties -> properties);
    public static final DeferredItem<BlockItem> PALADIUM_HOPPER_ITEM =
            ITEMS.registerSimpleBlockItem(PALADIUM_HOPPER_BLOCK, properties -> properties);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PalaChestBlockEntity>> CHEST =
            BLOCK_ENTITIES.register("chest", () -> new BlockEntityType<>(PalaChestBlockEntity::new,
                    Set.of(AMETHYST_CHEST.get(), TITANE_CHEST.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PaladiumHopperBlockEntity>> PALADIUM_HOPPER =
            BLOCK_ENTITIES.register("paladium_hopper", () -> new BlockEntityType<>(PaladiumHopperBlockEntity::new,
                    Set.of(PALADIUM_HOPPER_BLOCK.get())));

    public static final DeferredHolder<MenuType<?>, MenuType<PalaChestMenu>> AMETHYST_CHEST_MENU =
            MENUS.register("amethyst_chest", () -> new MenuType<>(
                    (containerId, inventory) -> new PalaChestMenu(PalaStorage.AMETHYST_CHEST_MENU.get(),
                            ChestTier.AMETHYST, containerId, inventory),
                    FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<PalaChestMenu>> TITANE_CHEST_MENU =
            MENUS.register("titane_chest", () -> new MenuType<>(
                    (containerId, inventory) -> new PalaChestMenu(PalaStorage.TITANE_CHEST_MENU.get(),
                            ChestTier.TITANE, containerId, inventory),
                    FeatureFlags.VANILLA_SET));

    /** L'ordre de l'onglet creatif. */
    public static final List<DeferredItem<? extends Item>> ORDERED =
            List.of(AMETHYST_CHEST_ITEM, TITANE_CHEST_ITEM, PALADIUM_HOPPER_ITEM);

    private PalaStorage() {
    }

    public static MenuType<PalaChestMenu> menuOf(ChestTier tier) {
        return tier == ChestTier.AMETHYST ? AMETHYST_CHEST_MENU.get() : TITANE_CHEST_MENU.get();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
    }
}

package fr.atraxterra.dohy.palamod.press;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Set;

/**
 * L'imprimerie de PalaMod : la presse a imprimer ({@code print_press}), la table
 * d'ecriture ({@code type_machine}), leurs fournitures et la dalle en paladium
 * qu'elles demandent.
 *
 * <p>Le circuit d'origine : un livre enchante et une plaque d'impression
 * ({@code chase}) passent sur la table, qui grave contre des niveaux une plaque
 * d'impression enchantee ({@code echanted_plate} dans PalaMod, la faute de
 * frappe n'est pas reprise) ; la presse, avec de l'encre de paladium et des
 * livres, en tire trois copies du livre avant que la plaque ne s'use.
 */
public final class PalaPress {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, PalaMod.NAMESPACE);

    /** {@code Material.iron}, durete 3 : une machine en fer, a la pioche. */
    public static final DeferredBlock<PrintPressBlock> PRINT_PRESS = BLOCKS.registerBlock(
            "print_press", PrintPressBlock::new, properties -> properties
                    .strength(3.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .mapColor(MapColor.METAL).noOcclusion());
    /** {@code Material.wood}, durete 3 ; la boite de selection monte a 0,92 bloc. */
    public static final DeferredBlock<TypeMachineBlock> TYPE_MACHINE = BLOCKS.registerBlock(
            "type_machine", TypeMachineBlock::new, properties -> properties
                    .strength(3.0f).sound(SoundType.WOOD).mapColor(MapColor.WOOD).noOcclusion());
    /** La dalle de PalaMod ({@code paladiumBlockSlab}), celle du bloc de paladium. */
    public static final DeferredBlock<SlabBlock> PALADIUM_SLAB = BLOCKS.registerBlock(
            "paladium_slab", SlabBlock::new, properties -> properties
                    .strength(5.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.METAL));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PrintPressBlockEntity>> PRINT_PRESS_TYPE =
            TYPES.register("print_press",
                    () -> new BlockEntityType<>(PrintPressBlockEntity::new, Set.<Block>of(PRINT_PRESS.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TypeMachineBlockEntity>> TYPE_MACHINE_TYPE =
            TYPES.register("type_machine",
                    () -> new BlockEntityType<>(TypeMachineBlockEntity::new, Set.<Block>of(TYPE_MACHINE.get())));

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> PRINT_PRESS_MENU =
            MENUS.register("print_press", () -> new MenuType<>(
                    (containerId, inventory) -> new MachineMenu(PalaPress.PRINT_PRESS_MENU.get(), containerId,
                            inventory, PrintPressBlockEntity.LAYOUT),
                    FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> TYPE_MACHINE_MENU =
            MENUS.register("type_machine", () -> new MenuType<>(
                    (containerId, inventory) -> new MachineMenu(PalaPress.TYPE_MACHINE_MENU.get(), containerId,
                            inventory, TypeMachineBlockEntity.LAYOUT),
                    FeatureFlags.VANILLA_SET));

    public static final DeferredItem<BlockItem> PRINT_PRESS_ITEM =
            ITEMS.registerSimpleBlockItem(PRINT_PRESS, properties -> properties);
    public static final DeferredItem<BlockItem> TYPE_MACHINE_ITEM =
            ITEMS.registerSimpleBlockItem(TYPE_MACHINE, properties -> properties);
    public static final DeferredItem<BlockItem> PALADIUM_SLAB_ITEM =
            ITEMS.registerSimpleBlockItem(PALADIUM_SLAB, properties -> properties);

    /**
     * La plaque d'impression ({@code ItemChase}). PalaMod la limitait a une par case
     * mais la table en gardait 64 empilees ; ici elle s'empile partout, ce qui
     * revient au meme dans la table et evite les plaques eparpillees dans le sac.
     */
    public static final DeferredItem<PressItem> CHASE = ITEMS.registerItem(
            "chase", properties -> new PressItem("tooltip.palamod.chase.use", properties));
    /** L'encre de paladium ({@code ItemPaladiumInk}) : deux par livre imprime. */
    public static final DeferredItem<PressItem> PALADIUM_INK = ITEMS.registerItem(
            "paladium_ink", properties -> new PressItem("tooltip.palamod.paladium_ink.use", properties));
    /**
     * La plaque gravee ({@code ItemEnchantedPlate}) : les enchantements du livre
     * ({@code StoredEnchantments}), un reflet permanent, trois impressions.
     */
    public static final DeferredItem<EnchantedPlateItem> ENCHANTED_PLATE = ITEMS.registerItem(
            "enchanted_plate", properties -> new EnchantedPlateItem(properties
                    .durability(EnchantedPlateItem.PRINTS)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

    public static final List<DeferredItem<? extends Item>> ORDERED = List.of(
            PRINT_PRESS_ITEM, TYPE_MACHINE_ITEM, CHASE, PALADIUM_INK, ENCHANTED_PLATE, PALADIUM_SLAB_ITEM);

    private PalaPress() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        MENUS.register(modBus);
    }
}

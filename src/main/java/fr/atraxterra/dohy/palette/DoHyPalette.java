package fr.atraxterra.dohy.palette;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

import java.util.Set;

/**
 * Les palettes d'armement, sous l'espace de noms {@code dohy}.
 *
 * <p>Compatibles Create et NTM NEXT par la capacite d'objets de NeoForge, que
 * les deux mods interrogent : entonnoirs, glissieres et bras mecaniques de
 * Create, grues et convoyeurs de NTM NEXT chargent et dechargent une palette
 * comme un coffre — le stock du groupe entier, par n'importe quelle palette.
 * Le tag {@code create:simple_mounted_storage} laisse Create embarquer les
 * palettes chargees sur ses machines mobiles (trains, chariots), contenu
 * compris.
 */
public final class DoHyPalette {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DoHy.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DoHy.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, DoHy.MOD_ID);

    public static final DeferredBlock<PaletteBlock> PALETTE = BLOCKS.registerBlock("palette", PaletteBlock::new,
            properties -> properties.mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS)
                    .strength(2.0f, 3.0f).sound(SoundType.WOOD).ignitedByLava().noOcclusion().dynamicShape());

    public static final DeferredItem<BlockItem> PALETTE_ITEM = ITEMS.registerSimpleBlockItem(PALETTE);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PaletteBlockEntity>> TYPE =
            TYPES.register("palette", () -> new BlockEntityType<>(PaletteBlockEntity::new, Set.of(PALETTE.get())));

    public static final DeferredHolder<MenuType<?>, MenuType<PaletteMenu>> MENU =
            MENUS.register("palette", () -> IMenuTypeExtension.create(PaletteMenu::new));

    private DoHyPalette() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(DoHyPalette::onCapabilities);
    }

    /**
     * Par une face : le stock du groupe. Sans face : les 16 piles de cette
     * palette seule — Create lit ainsi chaque palette qu'il embarque.
     */
    private static void onCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, TYPE.get(), (pallet, side) ->
                side == null ? VanillaContainerWrapper.of(pallet.stock()) : new WorldlyContainerWrapper(pallet, side));
    }
}

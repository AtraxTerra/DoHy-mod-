package fr.atraxterra.dohy.tech;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

/**
 * Les machines propres a DoHy, sous l'espace de noms {@code dohy}.
 *
 * <p>Le contenu repris de Paladium vit sous {@code palamod} pour que son pack
 * de textures s'applique. Ce qui n'en vient pas — ici le serveur de trading,
 * inspire de NationsGlory — n'a rien a y faire : il a son espace de noms et
 * son onglet.
 */
public final class DoHyTech {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DoHy.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DoHy.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DoHy.MOD_ID);

    public static final DeferredBlock<TradingServerBlock> TRADING_SERVER = BLOCKS.registerBlock(
            "trading_server", TradingServerBlock::new,
            properties -> properties.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(TradingServerBlock.LIT) ? 7 : 0));

    public static final DeferredItem<BlockItem> TRADING_SERVER_ITEM = ITEMS.registerSimpleBlockItem(
            TRADING_SERVER, properties -> properties.rarity(Rarity.UNCOMMON));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TradingServerBlockEntity>>
            TRADING_SERVER_TYPE = TYPES.register("trading_server",
            () -> new BlockEntityType<>(TradingServerBlockEntity::new, Set.of(TRADING_SERVER.get())));

    /** L'onglet des machines de DoHy. L'icone est un Supplier : pas d'ItemStack au chargement de la classe. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("machines",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + DoHy.MOD_ID + ".machines"))
                    .icon(() -> new ItemStack(TRADING_SERVER_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(TRADING_SERVER_ITEM.get());
                        output.accept(fr.atraxterra.dohy.spawn.DoHySpawn.TERMINAL_ITEM.get());
                    })
                    .build());

    private DoHyTech() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        TABS.register(modBus);
        modBus.addListener(DoHyTech::onCapabilities);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> TradingServerBlockEntity.forgetAll());
    }

    /**
     * L'energie du serveur, offerte sur toutes ses faces. C'est par cette
     * capacite que les cables de NTM NEXT — et de tout mod qui parle FE —
     * trouvent la machine.
     */
    private static void onCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, TRADING_SERVER_TYPE.get(),
                (server, side) -> server.energy());
    }
}

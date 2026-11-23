package fr.atraxterra.dohy.faction.tech;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
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

/** Le laboratoire de recherche de la nation (chapitre 23, lot 23c) : son bloc, son objet, son energie. */
public final class DoHyResearch {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DoHy.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DoHy.MOD_ID);

    public static final DeferredBlock<ResearchLabBlock> LAB = BLOCKS.registerBlock("research_lab",
            ResearchLabBlock::new, properties -> properties.mapColor(MapColor.METAL).strength(3.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion()
                    .lightLevel(state -> state.getValue(ResearchLabBlock.LIT) ? 9 : 0));

    public static final DeferredItem<BlockItem> LAB_ITEM = ITEMS.registerSimpleBlockItem(LAB,
            properties -> properties.rarity(Rarity.UNCOMMON));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResearchLabBlockEntity>> LAB_TYPE =
            TYPES.register("research_lab", () -> new BlockEntityType<>(ResearchLabBlockEntity::new, Set.of(LAB.get())));

    private DoHyResearch() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        modBus.addListener(DoHyResearch::onCapabilities);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> ResearchLabBlockEntity.forgetAll());
    }

    /** L'energie du laboratoire, offerte sur toutes ses faces : les cables de NTM NEXT et de tout mod FE la trouvent. */
    private static void onCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, LAB_TYPE.get(), (lab, side) -> lab.energy());
    }
}

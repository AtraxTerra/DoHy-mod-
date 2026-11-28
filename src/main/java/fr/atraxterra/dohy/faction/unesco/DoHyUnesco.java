package fr.atraxterra.dohy.faction.unesco;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Rarity;
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

import java.util.Set;
import java.util.function.Supplier;

/** Le programme UNESCO et les merveilles du monde (lot 28) : la plaque et le registre des monuments. */
public final class DoHyUnesco {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DoHy.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DoHy.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);

    public static final DeferredBlock<UnescoPlaqueBlock> PLAQUE = BLOCKS.registerBlock("unesco_plaque",
            UnescoPlaqueBlock::new, properties -> properties.mapColor(MapColor.QUARTZ).strength(2.0f, 6.0f)
                    .sound(SoundType.STONE).noOcclusion().lightLevel(state -> 4));
    public static final DeferredItem<BlockItem> PLAQUE_ITEM = ITEMS.registerSimpleBlockItem(PLAQUE,
            properties -> properties.rarity(Rarity.UNCOMMON));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UnescoPlaqueBlockEntity>> PLAQUE_TYPE =
            TYPES.register("unesco_plaque", () -> new BlockEntityType<>(UnescoPlaqueBlockEntity::new,
                    Set.<Block>of(PLAQUE.get())));

    /** Le registre des monuments et des merveilles, sur l'overworld. */
    public static final Supplier<AttachmentType<UnescoData>> DATA = ATTACHMENTS.register("unesco",
            () -> AttachmentType.builder(() -> new UnescoData())
                    .serialize(UnescoData.MAP_CODEC, data -> !data.isEmpty())
                    .build());

    private DoHyUnesco() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}

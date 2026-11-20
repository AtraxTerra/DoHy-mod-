package fr.atraxterra.dohy.earth.mine;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * L'enregistrement des mines de la Terre : la balise de concession (bloc et
 * objet) et la liste des mines capturees, posee sur le monde de la carte.
 */
public final class DoHyMines {

    /** Le prix d'une balise de concession a la Boutique ; la capture la consomme. */
    public static final long BEACON_PRICE = 25_000L;

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DoHy.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);

    public static final DeferredBlock<ConcessionBeaconBlock> BEACON = BLOCKS.registerBlock("concession_beacon",
            ConcessionBeaconBlock::new, properties -> properties.mapColor(MapColor.METAL).strength(1.5f, 6.0f)
                    .sound(SoundType.LANTERN).noOcclusion().lightLevel(state -> 9));

    public static final DeferredItem<ConcessionBeaconItem> BEACON_ITEM = ITEMS.registerItem("concession_beacon",
            properties -> new ConcessionBeaconItem(BEACON.get(), properties),
            properties -> properties.stacksTo(16).rarity(Rarity.UNCOMMON));

    /** Les mines capturees ; rien n'est ecrit tant qu'aucune ne l'est. */
    public static final Supplier<AttachmentType<MineLedger>> LEDGER = ATTACHMENTS.register("earth_mines",
            () -> AttachmentType.builder(() -> MineLedger.EMPTY)
                    .serialize(MineLedger.MAP_CODEC, MineLedger::exists)
                    .build());

    private DoHyMines() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}

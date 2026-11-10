package fr.atraxterra.dohy.palamod.reserve;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * La reserve d'or de faction (chapitre 22, lot 22e) : ses trois blocs, d'apres
 * le wiki (« Investissement Reserve d'or »). Un cube de 5, 6 ou 7 de cote —
 * aretes, dessus et dessous en coffres-forts, flancs vitres, coeur d'or
 * liquide — dans un claim de la faction ; chaque or liquide permet d'investir.
 *
 * <p>Seul un wither en vient a bout : ses blocs resistent a toutes les
 * explosions (3 600 000), comme le receptacle.
 */
public final class PalaReserve {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);

    /** Le coffre-fort : 5 titanes compresses et 4 obsidiennes. Mains nues sur lui, la reserve s'ouvre. */
    public static final DeferredBlock<FactionSafeBlock> FACTION_SAFE = BLOCKS.registerBlock(
            "faction_safe", FactionSafeBlock::new, properties -> properties
                    .strength(50.0f, 3_600_000.0f).sound(SoundType.NETHERITE_BLOCK).mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
    /** La vitre de coffre-fort : 8 vitres et 1 findium ; elle montre l'or. */
    public static final DeferredBlock<TransparentBlock> VAULT_GLASS = BLOCKS.registerBlock(
            "vault_glass", TransparentBlock::new, properties -> properties
                    .strength(50.0f, 3_600_000.0f).sound(SoundType.GLASS).mapColor(MapColor.NONE)
                    .requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK).noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));
    /** L'or liquide : 8 quartz electrifies et 1 pepite d'endium ; il luit. */
    public static final DeferredBlock<Block> LIQUID_GOLD = BLOCKS.registerBlock(
            "liquid_gold", Block::new, properties -> properties
                    .strength(5.0f, 3_600_000.0f).sound(SoundType.METAL).mapColor(MapColor.GOLD)
                    .requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK).lightLevel(state -> 12));

    public static final List<DeferredBlock<? extends Block>> ORDERED_BLOCKS = List.of(
            FACTION_SAFE, VAULT_GLASS, LIQUID_GOLD);
    private static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = ORDERED_BLOCKS.stream()
            .map(block -> ITEMS.registerSimpleBlockItem(block, properties -> properties)).toList();

    private PalaReserve() {
    }

    public static List<Supplier<? extends Item>> tab() {
        return new ArrayList<>(BLOCK_ITEMS);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}

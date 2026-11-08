package fr.atraxterra.dohy.palamod.nature;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Les ressources naturelles de PalaMod qui ne relevent d'aucun metier : le
 * buisson a XP et ses baies.
 */
public final class PalaNature {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);

    /** {@code BaseBlockBush} : feuillage, durete 0,3, son d'herbe. */
    public static final DeferredBlock<XpBushBlock> XP_BUSH = BLOCKS.registerBlock(
            "xp_bush", XpBushBlock::new, properties -> properties
                    .strength(0.3f).sound(SoundType.SWEET_BERRY_BUSH).noOcclusion().randomTicks()
                    .mapColor(MapColor.PLANT));
    public static final DeferredItem<BlockItem> XP_BUSH_ITEM = ITEMS.registerSimpleBlockItem(XP_BUSH);

    public static final DeferredItem<XpBerryItem> XP_BERRY = ITEMS.registerItem(
            "xp_berry", properties -> new XpBerryItem(1, properties));
    public static final DeferredItem<XpBerryItem> COMPRESSED_XP_BERRY = ITEMS.registerItem(
            "compressed_xp_berry", properties -> new XpBerryItem(9, properties));

    public static final List<DeferredItem<? extends Item>> ORDERED = List.of(XP_BUSH_ITEM, XP_BERRY, COMPRESSED_XP_BERRY);

    private PalaNature() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}

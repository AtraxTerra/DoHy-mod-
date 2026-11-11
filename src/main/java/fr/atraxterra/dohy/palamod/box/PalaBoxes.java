package fr.atraxterra.dohy.palamod.box;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Set;

/**
 * Les boxes de la boutique (jar PalaShop) : six coffres animes a poser, qui
 * ouvrent les caisses de DoHy. Voir {@link BoxBlock} et {@link BoxBlockEntity}.
 */
public final class PalaBoxes {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);

    public static final DeferredBlock<BoxBlock> AMETHYST = BLOCKS.registerBlock(
            "box_amethyst", properties -> new BoxBlock(BoxKind.AMETHYST, properties), PalaBoxes::box);
    public static final DeferredBlock<BoxBlock> PALADIUM = BLOCKS.registerBlock(
            "box_paladium", properties -> new BoxBlock(BoxKind.PALADIUM, properties), PalaBoxes::box);
    public static final DeferredBlock<BoxBlock> ENDIUM = BLOCKS.registerBlock(
            "box_endium", properties -> new BoxBlock(BoxKind.ENDIUM, properties), PalaBoxes::box);
    public static final DeferredBlock<BoxBlock> SAHARIA = BLOCKS.registerBlock(
            "box_saharia", properties -> new BoxBlock(BoxKind.SAHARIA, properties), PalaBoxes::box);
    public static final DeferredBlock<BoxBlock> TRIXIUM = BLOCKS.registerBlock(
            "box_trixium", properties -> new BoxBlock(BoxKind.TRIXIUM, properties), PalaBoxes::box);
    public static final DeferredBlock<BoxBlock> HALLOWEEN = BLOCKS.registerBlock(
            "box_halloween", properties -> new BoxBlock(BoxKind.HALLOWEEN, properties), PalaBoxes::box);

    /**
     * Le prix d'une box a la boutique. Elle ne donne rien de plus que la
     * caisse de son palier : on paie le coffre, une fois, pas le butin.
     */
    public static final long PRICE = 25_000L;

    /** Dans l'ordre de la boutique : les trois metaux, puis les trois habillages. */
    public static final List<DeferredBlock<BoxBlock>> BOXES =
            List.of(AMETHYST, PALADIUM, ENDIUM, SAHARIA, TRIXIUM, HALLOWEEN);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoxBlockEntity>> TYPE =
            TYPES.register("box", () -> new BlockEntityType<>(BoxBlockEntity::new, Set.of(
                    AMETHYST.get(), PALADIUM.get(), ENDIUM.get(), SAHARIA.get(), TRIXIUM.get(), HALLOWEEN.get())));

    public static final List<DeferredItem<BlockItem>> ORDERED = BOXES.stream()
            .map(block -> ITEMS.registerSimpleBlockItem(block, properties -> properties)).toList();

    /**
     * Un cube plein qui ne dessine rien : il ne doit ni cacher les faces de ses
     * voisins, ni assombrir le coffre qu'il contient, ni etouffer.
     */
    private static BlockBehaviour.Properties box(BlockBehaviour.Properties properties) {
        return properties.strength(1.5f, 6.0f)
                .sound(SoundType.METAL)
                .noOcclusion()
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .isRedstoneConductor((state, level, pos) -> false);
    }

    private PalaBoxes() {
    }

    /** L'objet d'une box, pour la boutique. */
    public static BlockItem item(BoxKind kind) {
        for (int index = 0; index < BOXES.size(); index++) {
            if (BOXES.get(index).get().kind() == kind) {
                return ORDERED.get(index).get();
            }
        }
        throw new IllegalArgumentException("Box inconnue : " + kind);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
    }
}

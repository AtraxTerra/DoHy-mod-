package fr.atraxterra.dohy.palamod.box;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Les boxes de la boutique (jar PalaShop) : six coffres animes a poser, qui
 * ouvrent les caisses de DoHy, et les cles de ces caisses. Voir
 * {@link BoxBlock}, {@link BoxBlockEntity} et {@link CrateKeys}.
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
     * Le prix du coffre a poser, a la boutique. Il ne donne rien de plus que
     * la roulette : on paie le coffre, une fois, pas le butin.
     */
    public static final long PRICE = 25_000L;

    /** De la moins chere a la plus chere. */
    public static final List<DeferredBlock<BoxBlock>> BOXES =
            List.of(AMETHYST, SAHARIA, PALADIUM, HALLOWEEN, TRIXIUM, ENDIUM);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoxBlockEntity>> TYPE =
            TYPES.register("box", () -> new BlockEntityType<>(BoxBlockEntity::new, Set.of(
                    AMETHYST.get(), PALADIUM.get(), ENDIUM.get(), SAHARIA.get(), TRIXIUM.get(), HALLOWEEN.get())));

    public static final List<DeferredItem<BlockItem>> BOX_ITEMS = BOXES.stream()
            .map(block -> ITEMS.registerSimpleBlockItem(block, properties -> properties)).toList();

    /**
     * Les cles, une par box ({@link CrateKeys}). La rarete de l'objet suit le
     * prix de la box. Les noms sont ecrits en toutes lettres : le controle des
     * ressources les lit ici.
     */
    public static final DeferredItem<CrateKeyItem> KEY_AMETHYST = ITEMS.registerItem(
            "crate_key_amethyst", properties -> new CrateKeyItem(BoxKind.AMETHYST.crate(), properties),
            properties -> properties.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<CrateKeyItem> KEY_SAHARIA = ITEMS.registerItem(
            "crate_key_saharia", properties -> new CrateKeyItem(BoxKind.SAHARIA.crate(), properties),
            properties -> properties.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<CrateKeyItem> KEY_PALADIUM = ITEMS.registerItem(
            "crate_key_paladium", properties -> new CrateKeyItem(BoxKind.PALADIUM.crate(), properties),
            properties -> properties.rarity(Rarity.RARE));
    public static final DeferredItem<CrateKeyItem> KEY_HALLOWEEN = ITEMS.registerItem(
            "crate_key_halloween", properties -> new CrateKeyItem(BoxKind.HALLOWEEN.crate(), properties),
            properties -> properties.rarity(Rarity.RARE));
    public static final DeferredItem<CrateKeyItem> KEY_TRIXIUM = ITEMS.registerItem(
            "crate_key_trixium", properties -> new CrateKeyItem(BoxKind.TRIXIUM.crate(), properties),
            properties -> properties.rarity(Rarity.EPIC));
    public static final DeferredItem<CrateKeyItem> KEY_ENDIUM = ITEMS.registerItem(
            "crate_key_endium", properties -> new CrateKeyItem(BoxKind.ENDIUM.crate(), properties),
            properties -> properties.rarity(Rarity.EPIC));

    /** De la box la moins chere a la plus chere. */
    public static final List<DeferredItem<CrateKeyItem>> KEYS =
            List.of(KEY_AMETHYST, KEY_SAHARIA, KEY_PALADIUM, KEY_HALLOWEEN, KEY_TRIXIUM, KEY_ENDIUM);

    static {
        // Les cles des trois anciennes caisses, restees dans un coffre ou un inventaire.
        alias("crate_key_common", "crate_key_amethyst");
        alias("crate_key_rare", "crate_key_paladium");
        alias("crate_key_legendary", "crate_key_endium");
    }

    private static void alias(String from, String to) {
        ITEMS.addAlias(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, from),
                Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, to));
    }

    public static final List<DeferredItem<? extends Item>> ORDERED = ordered();

    private static List<DeferredItem<? extends Item>> ordered() {
        List<DeferredItem<? extends Item>> list = new ArrayList<>(BOX_ITEMS);
        list.addAll(KEYS);
        return List.copyOf(list);
    }

    /** La cle d'une box. */
    public static CrateKeyItem key(BoxKind kind) {
        return switch (kind) {
            case AMETHYST -> KEY_AMETHYST.get();
            case SAHARIA -> KEY_SAHARIA.get();
            case PALADIUM -> KEY_PALADIUM.get();
            case HALLOWEEN -> KEY_HALLOWEEN.get();
            case TRIXIUM -> KEY_TRIXIUM.get();
            case ENDIUM -> KEY_ENDIUM.get();
        };
    }

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
                return BOX_ITEMS.get(index).get();
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

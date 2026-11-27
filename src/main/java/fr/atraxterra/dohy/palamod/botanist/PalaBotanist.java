package fr.atraxterra.dohy.palamod.botanist;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Le Botaniste de JobsReborn, lot 1 : les graines minerales et leurs plants.
 *
 * <p>JobsReborn (1.92.0) est le mod des metiers de la nouvelle generation de
 * Paladium ; son Botaniste fait pousser du minerai. Ce lot en reprend les huit
 * graines minerales, leurs plants, leurs caracteres (vitesse, pousse hors ligne)
 * et l'endium organique. Le laboratoire (pots, besoins des plantes, mutations,
 * scanner, centrifugeuse, centre de controle) est le lot suivant.
 *
 * <p>Pas de metier Botaniste a part : les graines se debloquent et rapportent au
 * <b>Fermier</b>, le metier de l'agriculture ici (voir {@code docs/PALAMOD.md} § 47).
 *
 * <p>Les noms sont ceux de JobsReborn ({@code amethyst_seed}, {@code amethyst_crop},
 * {@code organic_endium_nugget}), sous le namespace {@code palamod} comme le reste
 * du contenu de Paladium. Textures dessinees par {@code tools/botanist_assets.py}.
 */
public final class PalaBotanist {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PalaMod.NAMESPACE);

    /** Les caracteres d'une graine (NBT {@code speed} et {@code offline} dans JobsReborn). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SeedTraits>> TRAITS =
            COMPONENTS.registerComponentType("mineral_seed_traits", builder -> builder
                    .persistent(SeedTraits.CODEC)
                    .networkSynchronized(SeedTraits.STREAM_CODEC));

    /**
     * Le rattrapage des graines a ete fait pour ce joueur ({@link BotanistEvents}) :
     * une fois, mort comprise.
     */
    public static final Supplier<AttachmentType<Boolean>> SEEDS_CAUGHT_UP = ATTACHMENTS.register(
            "mineral_seeds_caught_up", () -> AttachmentType.builder(() -> Boolean.FALSE)
                    .serialize(Codec.BOOL.fieldOf("done"))
                    .copyOnDeath()
                    .build());

    // -- Les plants -------------------------------------------------------------

    public static final DeferredBlock<MineralCropBlock> AMETHYST_CROP = crop("amethyst_crop", MineralSeed.AMETHYST);
    public static final DeferredBlock<MineralCropBlock> TITANE_CROP = crop("titane_crop", MineralSeed.TITANE);
    public static final DeferredBlock<MineralCropBlock> PALADIUM_CROP = crop("paladium_crop", MineralSeed.PALADIUM);
    public static final DeferredBlock<MineralCropBlock> GREEN_PALADIUM_CROP =
            crop("green_paladium_crop", MineralSeed.GREEN_PALADIUM);
    public static final DeferredBlock<MineralCropBlock> FINDIUM_CROP = crop("findium_crop", MineralSeed.FINDIUM);
    public static final DeferredBlock<MineralCropBlock> TRIXIUM_CROP = crop("trixium_crop", MineralSeed.TRIXIUM);
    public static final DeferredBlock<MineralCropBlock> INVOKER_CROP = crop("invoker_crop", MineralSeed.INVOKER);
    public static final DeferredBlock<MineralCropBlock> ENDIUM_CROP = crop("endium_crop", MineralSeed.ENDIUM);

    public static final Map<MineralSeed, DeferredBlock<MineralCropBlock>> CROPS = byseed(List.of(
            AMETHYST_CROP, TITANE_CROP, PALADIUM_CROP, GREEN_PALADIUM_CROP,
            FINDIUM_CROP, TRIXIUM_CROP, INVOKER_CROP, ENDIUM_CROP));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MineralCropBlockEntity>> MINERAL_CROP =
            TYPES.register("mineral_crop", () -> new BlockEntityType<>(MineralCropBlockEntity::new,
                    Set.<Block>of(AMETHYST_CROP.get(), TITANE_CROP.get(), PALADIUM_CROP.get(),
                            GREEN_PALADIUM_CROP.get(), FINDIUM_CROP.get(), TRIXIUM_CROP.get(),
                            INVOKER_CROP.get(), ENDIUM_CROP.get())));

    // -- Les graines -----------------------------------------------------------------

    public static final DeferredItem<MineralSeedItem> AMETHYST_SEED =
            seed("amethyst_seed", AMETHYST_CROP, MineralSeed.AMETHYST);
    public static final DeferredItem<MineralSeedItem> TITANE_SEED =
            seed("titane_seed", TITANE_CROP, MineralSeed.TITANE);
    public static final DeferredItem<MineralSeedItem> PALADIUM_SEED =
            seed("paladium_seed", PALADIUM_CROP, MineralSeed.PALADIUM);
    public static final DeferredItem<MineralSeedItem> GREEN_PALADIUM_SEED =
            seed("green_paladium_seed", GREEN_PALADIUM_CROP, MineralSeed.GREEN_PALADIUM);
    public static final DeferredItem<MineralSeedItem> FINDIUM_SEED =
            seed("findium_seed", FINDIUM_CROP, MineralSeed.FINDIUM);
    public static final DeferredItem<MineralSeedItem> TRIXIUM_SEED =
            seed("trixium_seed", TRIXIUM_CROP, MineralSeed.TRIXIUM);
    public static final DeferredItem<MineralSeedItem> INVOKER_SEED =
            seed("invoker_seed", INVOKER_CROP, MineralSeed.INVOKER);
    public static final DeferredItem<MineralSeedItem> ENDIUM_SEED =
            seed("endium_seed", ENDIUM_CROP, MineralSeed.ENDIUM);

    public static final Map<MineralSeed, DeferredItem<MineralSeedItem>> SEEDS = byseed(List.of(
            AMETHYST_SEED, TITANE_SEED, PALADIUM_SEED, GREEN_PALADIUM_SEED,
            FINDIUM_SEED, TRIXIUM_SEED, INVOKER_SEED, ENDIUM_SEED));

    /** L'endium organique ({@code ItemBotanistOrganicEndium}) : il se change en pepite d'endium a l'etabli. */
    public static final DeferredItem<Item> ORGANIC_ENDIUM = ITEMS.registerItem("organic_endium_nugget",
            properties -> new BotanistItem("tooltip.palamod.organic_endium_nugget.use", properties));

    public static final List<DeferredItem<? extends Item>> ORDERED = List.of(
            AMETHYST_SEED, TITANE_SEED, PALADIUM_SEED, GREEN_PALADIUM_SEED,
            FINDIUM_SEED, TRIXIUM_SEED, INVOKER_SEED, ENDIUM_SEED, ORGANIC_ENDIUM);

    private PalaBotanist() {
    }

    /**
     * Un plant : les proprietes du ble vanilla (sans collision, casse d'un coup, son de
     * culture, detruit par un piston), sans ticks aleatoires.
     */
    private static DeferredBlock<MineralCropBlock> crop(String name, MineralSeed seed) {
        return BLOCKS.registerBlock(name, properties -> new MineralCropBlock(properties, seed), properties -> properties
                .noCollision()
                .instabreak()
                .sound(SoundType.CROP)
                .pushReaction(PushReaction.DESTROY));
    }

    private static DeferredItem<MineralSeedItem> seed(String name, DeferredBlock<MineralCropBlock> crop,
                                                      MineralSeed seed) {
        // Pas de composant par defaut sur l'objet : une graine sans caracteres vaut
        // SeedTraits.DEFAULT a la lecture, et la pile reste empilable avec une
        // recompense ou une graine de l'onglet creatif.
        return ITEMS.registerItem(name, properties -> new MineralSeedItem(crop.get(), properties, seed));
    }

    private static <T> Map<MineralSeed, T> byseed(List<T> ordered) {
        Map<MineralSeed, T> map = new EnumMap<>(MineralSeed.class);
        for (MineralSeed seed : MineralSeed.values()) {
            map.put(seed, ordered.get(seed.ordinal()));
        }
        return Map.copyOf(map);
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
    }
}

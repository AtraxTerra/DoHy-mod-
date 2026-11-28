package fr.atraxterra.dohy.palamod.botanist;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
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
 * Le Botaniste de JobsReborn : les graines minerales (lot 1) et le laboratoire
 * (lot 2a : pots, plantes de base, besoins, arrosoir et guitare).
 *
 * <p>JobsReborn (1.92.0) est le mod des metiers de la nouvelle generation de
 * Paladium ; son Botaniste fait pousser du minerai. Le lot 1 en reprend les huit
 * graines minerales, leurs plants, leurs caracteres (vitesse, pousse hors ligne)
 * et l'endium organique. Le lot 2a apporte le pot ({@link BotanistPotBlock}), les
 * trois plantes de base, une par climat ({@link Mutation}), leurs besoins
 * ({@link Need}), l'arrosoir et la guitare. Le lot 2b apporte les douze autres
 * plantes et les mutations a la floraison, la mutation inconnue et le dechet, le
 * scanner de Genis ({@link GeneScannerBlock}) et le codex ({@link BotanistCodex}),
 * les nibblers ({@link Nibbler}) et le filet ({@link NetItem}). La centrifugeuse,
 * la reserve de graines et le centre de controle sont du lot suivant.
 *
 * <p>Pas de metier Botaniste a part : les graines se debloquent et rapportent au
 * <b>Fermier</b>, le metier de l'agriculture ici (voir {@code docs/PALAMOD.md} § 47).
 *
 * <p>Les noms sont ceux de JobsReborn ({@code amethyst_seed}, {@code amethyst_crop},
 * {@code organic_endium_nugget}), sous le namespace {@code palamod} comme le reste
 * du contenu de Paladium. Textures de JobsReborn, reprises par {@code tools/botanist_assets.py}.
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
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, PalaMod.NAMESPACE);
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, PalaMod.NAMESPACE);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, PalaMod.NAMESPACE);

    /** Les caracteres d'une graine (NBT {@code speed} et {@code offline} dans JobsReborn). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SeedTraits>> TRAITS =
            COMPONENTS.registerComponentType("mineral_seed_traits", builder -> builder
                    .persistent(SeedTraits.CODEC)
                    .networkSynchronized(SeedTraits.STREAM_CODEC));

    /** Les charges de l'arrosoir (dix, comme l'{@code ABotanistChargedTool} de JobsReborn). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CHARGES =
            COMPONENTS.registerComponentType("watering_can_charges", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Les nibblers du filet (NBT {@code nibblers} de JobsReborn, huit au plus). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> NIBBLERS =
            COMPONENTS.registerComponentType("net_nibblers", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** La plante portee par une mutation inconnue (NBT {@code mutation} de JobsReborn). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> MUTATION_TARGET =
            COMPONENTS.registerComponentType("botanist_mutation_target", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** Le codex des mutations du joueur ({@link BotanistCodex}), garde a la mort. */
    public static final Supplier<AttachmentType<List<String>>> CODEX = ATTACHMENTS.register(
            "botanist_codex", () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf().fieldOf("discovered"))
                    .copyOnDeath()
                    .build());

    /**
     * Le rattrapage des graines a ete fait pour ce joueur ({@link BotanistEvents}) :
     * une fois, mort comprise.
     */
    public static final Supplier<AttachmentType<Boolean>> SEEDS_CAUGHT_UP = ATTACHMENTS.register(
            "mineral_seeds_caught_up", () -> AttachmentType.builder(() -> Boolean.FALSE)
                    .serialize(Codec.BOOL.fieldOf("done"))
                    .copyOnDeath()
                    .build());

    /** Le meme rattrapage pour le laboratoire : pots et graines de mutation. */
    public static final Supplier<AttachmentType<Boolean>> LAB_CAUGHT_UP = ATTACHMENTS.register(
            "botanist_lab_caught_up", () -> AttachmentType.builder(() -> Boolean.FALSE)
                    .serialize(Codec.BOOL.fieldOf("done"))
                    .copyOnDeath()
                    .build());

    /** Et pour le lot 2b : le scanner et le filet. */
    public static final Supplier<AttachmentType<Boolean>> LAB2_CAUGHT_UP = ATTACHMENTS.register(
            "botanist_lab2_caught_up", () -> AttachmentType.builder(() -> Boolean.FALSE)
                    .serialize(Codec.BOOL.fieldOf("done"))
                    .copyOnDeath()
                    .build());

    /** Et pour le lot 2c : la reserve, la centrifugeuse et le centre de controle. */
    public static final Supplier<AttachmentType<Boolean>> LAB3_CAUGHT_UP = ATTACHMENTS.register(
            "botanist_lab3_caught_up", () -> AttachmentType.builder(() -> Boolean.FALSE)
                    .serialize(Codec.BOOL.fieldOf("done"))
                    .copyOnDeath()
                    .build());

    /** La reserve de graines du joueur ({@link SeedReserve}) : plante -> graines rangees. */
    public static final Supplier<AttachmentType<Map<String, Integer>>> SEED_RESERVE = ATTACHMENTS.register(
            "botanist_seed_reserve", () -> AttachmentType.<Map<String, Integer>>builder(() -> Map.of())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("seeds"))
                    .copyOnDeath()
                    .build());

    /** Les plantes dont la premiere graine rangee a deja rapporte son XP. */
    public static final Supplier<AttachmentType<List<String>>> SEED_RESERVE_REWARDS = ATTACHMENTS.register(
            "botanist_seed_reserve_rewards", () -> AttachmentType.<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf().fieldOf("rewarded"))
                    .copyOnDeath()
                    .build());

    /** Le laboratoire du joueur ({@link BotanistLab}) : modules, boosts, constructions, pots suivis. */
    public static final Supplier<AttachmentType<BotanistLab.Data>> LAB = ATTACHMENTS.register(
            "botanist_lab", () -> AttachmentType.builder(() -> BotanistLab.Data.EMPTY)
                    .serialize(BotanistLab.Data.CODEC.fieldOf("lab"))
                    .copyOnDeath()
                    .build());

    // -- Les sons du lot 2b (ceux de JobsReborn) ----------------------------------------------

    public static final DeferredHolder<SoundEvent, SoundEvent> NIBBLER_IDLE = sound("botanist.nibbler.idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIBBLER_WALK = sound("botanist.nibbler.walk");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIBBLER_PANIC = sound("botanist.nibbler.panic");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIBBLER_SCREAM = sound("botanist.nibbler.scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> NET_GRAB = sound("botanist.net.grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> NET_NIBBLERS = sound("botanist.net.nibblers");
    public static final DeferredHolder<SoundEvent, SoundEvent> NET_WHOOSH = sound("botanist.net.whoosh");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCANNER_NO_COAL = sound("botanist.scanner.no_coal");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCANNER_COMPLETE =
            sound("botanist.scanner.scan_complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCANNER_SCAN = sound("botanist.scanner.scan_in_progress");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLANT_BUD = sound("botanist.plant.bud");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLANT_BLOOMING = sound("botanist.plant.blooming");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLANT_WILTED = sound("botanist.plant.wilted");

    // -- Les sons du lot 2c (ceux de JobsReborn) ----------------------------------------------

    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE_IDLE = sound("botanist.centrifuge.idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE_DIRECT_MUTATION =
            sound("botanist.centrifuge.direct_mutation");
    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE_BOOSTED_SEED =
            sound("botanist.centrifuge.boosted_seed");
    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE_FINISHED = sound("botanist.centrifuge.finished");
    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE_FINISHED_MUTATED =
            sound("botanist.centrifuge.finished_mutated");
    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE_FINISHED_BOOSTED =
            sound("botanist.centrifuge.finished_boosted");
    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE_FINISHED_BOOSTED_MUTATED =
            sound("botanist.centrifuge.finished_boosted_mutated");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONTROL_ALERT = sound("botanist.control_center.alert");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONTROL_CONSTRUCTION =
            sound("botanist.control_center.construction");

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

    // -- Le laboratoire (lot 2a) --------------------------------------------------------

    /** Le pot : terre cuite, se casse vite, son de pot decore ; pas un bloc plein. */
    public static final DeferredBlock<BotanistPotBlock> POT = BLOCKS.registerBlock("botanist_pot",
            BotanistPotBlock::new, properties -> properties
                    .strength(1.0f)
                    .sound(SoundType.DECORATED_POT)
                    .mapColor(MapColor.TERRACOTTA_ORANGE)
                    .noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BotanistPotBlockEntity>> POT_TYPE =
            TYPES.register("botanist_pot",
                    () -> new BlockEntityType<>(BotanistPotBlockEntity::new, Set.<Block>of(POT.get())));
    public static final DeferredItem<BlockItem> POT_ITEM = ITEMS.registerSimpleBlockItem(POT, properties -> properties);

    // Les graines des quinze plantes, une par plante, dans l'ordre de Mutation (par climat).
    public static final DeferredItem<MutationSeedItem> IRONWHEAT_SEED =
            mutationSeed("ironwheat_seed", Mutation.IRONWHEAT);
    public static final DeferredItem<MutationSeedItem> GLOWBLOOM_SEED =
            mutationSeed("glowbloom_seed", Mutation.GLOWBLOOM);
    public static final DeferredItem<MutationSeedItem> PUFFBLOOM_SEED =
            mutationSeed("puffbloom_seed", Mutation.PUFFBLOOM);
    public static final DeferredItem<MutationSeedItem> JADE_MAW_SEED =
            mutationSeed("jade_maw_seed", Mutation.JADE_MAW);
    public static final DeferredItem<MutationSeedItem> MOTHERROOT_SEED =
            mutationSeed("motherroot_seed", Mutation.MOTHERROOT);
    public static final DeferredItem<MutationSeedItem> MOONFROST_SEED =
            mutationSeed("moonfrost_seed", Mutation.MOONFROST);
    public static final DeferredItem<MutationSeedItem> THORNWICK_SEED =
            mutationSeed("thornwick_seed", Mutation.THORNWICK);
    public static final DeferredItem<MutationSeedItem> SONGLILY_SEED =
            mutationSeed("songlily_seed", Mutation.SONGLILY);
    public static final DeferredItem<MutationSeedItem> AURELYS_SEED =
            mutationSeed("aurelys_seed", Mutation.AURELYS);
    public static final DeferredItem<MutationSeedItem> ARCANIUM_SEED =
            mutationSeed("arcanium_seed", Mutation.ARCANIUM);
    public static final DeferredItem<MutationSeedItem> SAND_CORAL_SEED =
            mutationSeed("sand_coral_seed", Mutation.SAND_CORAL);
    public static final DeferredItem<MutationSeedItem> SUNBLOOM_SEED =
            mutationSeed("sunbloom_seed", Mutation.SUNBLOOM);
    public static final DeferredItem<MutationSeedItem> SMOKEPOD_SEED =
            mutationSeed("smokepod_seed", Mutation.SMOKEPOD);
    public static final DeferredItem<MutationSeedItem> MIASMA_BLOOM_SEED =
            mutationSeed("miasma_bloom_seed", Mutation.MIASMA_BLOOM);
    public static final DeferredItem<MutationSeedItem> EMBER_ORCHID_SEED =
            mutationSeed("ember_orchid_seed", Mutation.EMBER_ORCHID);

    public static final Map<Mutation, DeferredItem<MutationSeedItem>> MUTATION_SEEDS = bymutation(List.of(
            IRONWHEAT_SEED, GLOWBLOOM_SEED, PUFFBLOOM_SEED, JADE_MAW_SEED, MOTHERROOT_SEED,
            MOONFROST_SEED, THORNWICK_SEED, SONGLILY_SEED, AURELYS_SEED, ARCANIUM_SEED,
            SAND_CORAL_SEED, SUNBLOOM_SEED, SMOKEPOD_SEED, MIASMA_BLOOM_SEED, EMBER_ORCHID_SEED));


    /** L'arrosoir et la guitare : 32 utilisations chacun, sans reparation (JobsReborn). */
    public static final DeferredItem<WateringCanItem> WATERING_CAN = ITEMS.registerItem("watering_can",
            WateringCanItem::new, properties -> properties.durability(32));
    public static final DeferredItem<GuitarItem> GUITAR = ITEMS.registerItem("guitar",
            GuitarItem::new, properties -> properties.durability(32));

    // -- Le laboratoire (lot 2b) --------------------------------------------------------

    /** Le filet : huit nibblers, 32 utilisations, sans reparation (JobsReborn). */
    public static final DeferredItem<NetItem> NET = ITEMS.registerItem("net", NetItem::new,
            properties -> properties.durability(32));
    /** La mutation inconnue, et le dechet d'un scan sans plante a reveler. */
    public static final DeferredItem<UnknownMutationItem> UNKNOWN_MUTATION = ITEMS.registerItem("unknown_mutation",
            UnknownMutationItem::new);
    public static final DeferredItem<Item> MUTATION_WASTE = ITEMS.registerItem("mutation_waste",
            properties -> new BotanistItem("tooltip.palamod.mutation_waste.use", properties));

    /** Le scanner de Genis : deux blocs de haut, du metal, se casse a la pioche comme une machine. */
    public static final DeferredBlock<GeneScannerBlock> GENE_SCANNER = BLOCKS.registerBlock("gene_scanner",
            GeneScannerBlock::new, properties -> properties
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .mapColor(MapColor.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneScannerBlockEntity>> GENE_SCANNER_TYPE =
            TYPES.register("gene_scanner",
                    () -> new BlockEntityType<>(GeneScannerBlockEntity::new, Set.<Block>of(GENE_SCANNER.get())));
    public static final DeferredItem<BlockItem> GENE_SCANNER_ITEM = ITEMS.registerItem("gene_scanner",
            properties -> new BotanistBlockItem(GENE_SCANNER.get(), properties.useBlockDescriptionPrefix(),
                    "tooltip.palamod.gene_scanner.use", "tooltip.palamod.gene_scanner.fuel"));
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> GENE_SCANNER_MENU =
            MENUS.register("gene_scanner", () -> new MenuType<>(
                    (containerId, inventory) -> new MachineMenu(PalaBotanist.GENE_SCANNER_MENU.get(), containerId,
                            inventory, MachineLayout.GENE_SCANNER),
                    FeatureFlags.VANILLA_SET));

    // -- Le laboratoire (lot 2c) --------------------------------------------------------

    /** La centrifugeuse : deux blocs de haut, du metal. */
    public static final DeferredBlock<CentrifugeBlock> MUTATION_CENTRIFUGE = BLOCKS.registerBlock("mutation_centrifuge",
            CentrifugeBlock::new, PalaBotanist::machineProperties);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CentrifugeBlockEntity>> CENTRIFUGE_TYPE =
            TYPES.register("mutation_centrifuge",
                    () -> new BlockEntityType<>(CentrifugeBlockEntity::new, Set.<Block>of(MUTATION_CENTRIFUGE.get())));
    public static final DeferredItem<BlockItem> MUTATION_CENTRIFUGE_ITEM = ITEMS.registerItem("mutation_centrifuge",
            properties -> new BotanistBlockItem(MUTATION_CENTRIFUGE.get(), properties.useBlockDescriptionPrefix(),
                    "tooltip.palamod.mutation_centrifuge.use", "tooltip.palamod.mutation_centrifuge.items"));
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> CENTRIFUGE_MENU =
            MENUS.register("mutation_centrifuge", () -> new MenuType<>(
                    (containerId, inventory) -> new MachineMenu(PalaBotanist.CENTRIFUGE_MENU.get(), containerId,
                            inventory, MachineLayout.CENTRIFUGE),
                    FeatureFlags.VANILLA_SET));

    /** La reserve de graines : deux blocs de large et deux de haut. */
    public static final DeferredBlock<SeedReserveBlock> SEED_RESERVE_BLOCK = BLOCKS.registerBlock("seed_reserve",
            SeedReserveBlock::new, PalaBotanist::machineProperties);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SeedReserveBlockEntity>> SEED_RESERVE_TYPE =
            TYPES.register("seed_reserve",
                    () -> new BlockEntityType<>(SeedReserveBlockEntity::new, Set.<Block>of(SEED_RESERVE_BLOCK.get())));
    public static final DeferredItem<BlockItem> SEED_RESERVE_ITEM = ITEMS.registerItem("seed_reserve",
            properties -> new BotanistBlockItem(SEED_RESERVE_BLOCK.get(), properties.useBlockDescriptionPrefix(),
                    "tooltip.palamod.seed_reserve.use", "tooltip.palamod.seed_reserve.reward"));
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> SEED_RESERVE_MENU =
            MENUS.register("seed_reserve", () -> new MenuType<>(
                    (containerId, inventory) -> new MachineMenu(PalaBotanist.SEED_RESERVE_MENU.get(), containerId,
                            inventory, SeedReserve.Storage.client(), new net.minecraft.world.inventory.SimpleContainerData(
                            MachineMenu.DATA_COUNT), MachineLayout.SEED_RESERVE),
                    FeatureFlags.VANILLA_SET));

    /** Le centre de controle : deux blocs de large et deux de haut. */
    public static final DeferredBlock<LabControlBlock> LAB_CONTROL = BLOCKS.registerBlock("lab_control",
            LabControlBlock::new, PalaBotanist::machineProperties);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LabControlBlockEntity>> LAB_CONTROL_TYPE =
            TYPES.register("lab_control",
                    () -> new BlockEntityType<>(LabControlBlockEntity::new, Set.<Block>of(LAB_CONTROL.get())));
    public static final DeferredItem<BlockItem> LAB_CONTROL_ITEM = ITEMS.registerItem("lab_control",
            properties -> new BotanistBlockItem(LAB_CONTROL.get(), properties.useBlockDescriptionPrefix(),
                    "tooltip.palamod.lab_control.use", "tooltip.palamod.lab_control.boost"));

    /** Le nibbler : 0,4 de large, 0,3 de haut (JobsReborn). */
    public static final DeferredHolder<EntityType<?>, EntityType<Nibbler>> NIBBLER = ENTITIES.register("nibbler",
            key -> EntityType.Builder.<Nibbler>of(Nibbler::new, MobCategory.CREATURE)
                    .sized(0.4f, 0.3f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    public static final List<DeferredItem<? extends Item>> ORDERED = ordered();

    private PalaBotanist() {
    }

    /** Les machines du laboratoire : du metal, a la pioche, poussees par rien. */
    private static BlockBehaviour.Properties machineProperties(BlockBehaviour.Properties properties) {
        return properties
                .strength(3.5f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .mapColor(MapColor.METAL)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK);
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

    private static DeferredItem<MutationSeedItem> mutationSeed(String name, Mutation mutation) {
        return ITEMS.registerItem(name, properties -> new MutationSeedItem(properties, mutation));
    }

    /** Les graines par plante, d'apres la plante de chaque graine (et non leur ordre dans la liste). */
    private static Map<Mutation, DeferredItem<MutationSeedItem>> bymutation(
            List<DeferredItem<MutationSeedItem>> seeds) {
        Map<Mutation, DeferredItem<MutationSeedItem>> map = new EnumMap<>(Mutation.class);
        for (Mutation mutation : Mutation.values()) {
            for (DeferredItem<MutationSeedItem> seed : seeds) {
                if (seed.getId().getPath().equals(mutation.seedName())) {
                    map.put(mutation, seed);
                }
            }
        }
        if (map.size() != Mutation.values().length) {
            throw new IllegalStateException("Graine de mutation manquante : " + map.keySet());
        }
        return Map.copyOf(map);
    }

    /** L'ordre de l'onglet : graines minerales, pot et outils, graines du laboratoire par rarete, scanner. */
    private static List<DeferredItem<? extends Item>> ordered() {
        List<DeferredItem<? extends Item>> list = new java.util.ArrayList<>(List.of(
                AMETHYST_SEED, TITANE_SEED, PALADIUM_SEED, GREEN_PALADIUM_SEED,
                FINDIUM_SEED, TRIXIUM_SEED, INVOKER_SEED, ENDIUM_SEED, ORGANIC_ENDIUM,
                POT_ITEM, WATERING_CAN, GUITAR, NET, GENE_SCANNER_ITEM, MUTATION_CENTRIFUGE_ITEM, SEED_RESERVE_ITEM,
                LAB_CONTROL_ITEM));
        for (Rarity rarity : Rarity.values()) {
            for (Mutation mutation : Mutation.values()) {
                if (mutation.rarity() == rarity) {
                    list.add(MUTATION_SEEDS.get(mutation));
                }
            }
        }
        list.add(UNKNOWN_MUTATION);
        list.add(MUTATION_WASTE);
        return List.copyOf(list);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, name)));
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
        ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(PalaBotanist::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(NIBBLER.get(), Nibbler.createAttributes().build());
    }
}

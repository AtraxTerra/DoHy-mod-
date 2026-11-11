package fr.atraxterra.dohy.palamod.machine;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Les machines du PalaMod : blocs, types de block entity, ameliorations.
 *
 * <p>Le cobblebreaker et ses quatre paliers, le crusher et ses quatre recettes,
 * et depuis le chapitre 5 le totem de fertilite — dont les deux constantes de
 * cadence etaient restees ambigues (millisecondes ou ticks, un facteur 20
 * d'ecart sur l'equilibrage) jusqu'a ce que le bytecode les tranche : le tick
 * d'origine lit {@code System.currentTimeMillis()}, donc des millisecondes.
 * Voir {@link TotemBlockEntity}.
 *
 * <p>Les verrous de metier sont deja poses : {@code data/jobs.json} exige
 * Mineur 25 pour le cobblebreaker, 50 / 65 / 80 pour ses ameliorations, et
 * Fermier 40 pour le crusher. Ce chapitre n'ecrit aucune logique de verrou — il
 * enregistre des blocs dont les identifiants etaient deja nommes au chapitre 1,
 * et les entrees correspondantes cessent d'etre inertes.
 */
public final class PalaMachines {

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PalaMod.NAMESPACE);

    // -- Blocs ---------------------------------------------------------------

    public static final DeferredBlock<Block> COBBLEBREAKER_BLOCK = BLOCKS.registerBlock(
            "cobblebreaker", CobblebreakerBlock::new, PalaMachines::machineProperties);

    public static final DeferredBlock<Block> CRUSHER_BLOCK = BLOCKS.registerBlock(
            "crusher", CrusherBlock::new, PalaMachines::machineProperties);

    /**
     * Le totem de fertilite.
     *
     * <p>Ses proprietes ne sont pas celles des deux autres : un totem est en
     * bois dans l'original, et il n'a pas a demander une pioche pour etre
     * recupere. Le reste du chapitre 5 — palaforge, grinder, palamachine — est
     * bien du metal et reprendra {@code machineProperties}.
     */
    public static final DeferredBlock<Block> TOTEM_BLOCK = BLOCKS.registerBlock(
            "totem_of_fertility", TotemBlock::new, properties -> properties
                    .strength(2.5f)
                    .sound(SoundType.WOOD)
                    .noOcclusion());

    /**
     * Palafurnace : fer, son de pierre, durete 12, resistance 4,8 (setResistance(8)
     * en 1.7.10, qui stockait le triple et divisait par cinq), pioche en fer.
     * Son identifiant reprend le champ de PalaMod, {@code PALADIUM_FURNACE}.
     */
    public static final DeferredBlock<Block> PALAFURNACE_BLOCK = BLOCKS.registerBlock(
            "paladium_furnace", PalafurnaceBlock::new, properties -> properties
                    .strength(12.0f, 4.8f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE));

    /** Palaforge : fer, durete 12, resistance 4,8, pioche en pierre. */
    public static final DeferredBlock<Block> PALAFORGE_BLOCK = BLOCKS.registerBlock(
            "palaforge", PalaforgeBlock::new, properties -> properties
                    .strength(12.0f, 4.8f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    // -- Types de block entity -----------------------------------------------
    //
    // BlockEntityType$Builder n'existe plus en 26.2 (verifie a la sonde) : on
    // passe par le constructeur, qui prend la fabrique et l'ensemble des blocs
    // acceptes.

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CobblebreakerBlockEntity>>
            COBBLEBREAKER = TYPES.register("cobblebreaker",
            () -> new BlockEntityType<>(CobblebreakerBlockEntity::new,
                    Set.of(COBBLEBREAKER_BLOCK.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrusherBlockEntity>>
            CRUSHER = TYPES.register("crusher",
            () -> new BlockEntityType<>(CrusherBlockEntity::new,
                    Set.of(CRUSHER_BLOCK.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TotemBlockEntity>>
            TOTEM = TYPES.register("totem_of_fertility",
            () -> new BlockEntityType<>(TotemBlockEntity::new,
                    Set.of(TOTEM_BLOCK.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PalafurnaceBlockEntity>>
            PALAFURNACE = TYPES.register("paladium_furnace",
            () -> new BlockEntityType<>(PalafurnaceBlockEntity::new,
                    Set.of(PALAFURNACE_BLOCK.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PalaforgeBlockEntity>>
            PALAFORGE = TYPES.register("palaforge",
            () -> new BlockEntityType<>(PalaforgeBlockEntity::new,
                    Set.of(PALAFORGE_BLOCK.get())));

    // -- Grinder : le controleur et les deux blocs de sa structure ------------
    //
    // PSRegister_Blocks : le grinder durete 3, le casing et le frame 12 ; tous
    // les trois a la pioche en fer, resistance 12 apres conversion.
    public static final DeferredBlock<Block> GRINDER_BLOCK = BLOCKS.registerBlock(
            "grinder", GrinderBlock::new, properties -> properties
                    .strength(3.0f, 12.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));
    public static final DeferredBlock<Block> GRINDER_CASING = BLOCKS.registerBlock(
            "grinder_casing", Block::new, properties -> properties
                    .strength(12.0f, 12.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));
    public static final DeferredBlock<Block> GRINDER_FRAME = BLOCKS.registerBlock(
            "grinder_frame", Block::new, properties -> properties
                    .strength(12.0f, 12.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GrinderBlockEntity>>
            GRINDER = TYPES.register("grinder",
            () -> new BlockEntityType<>(GrinderBlockEntity::new, Set.of(GRINDER_BLOCK.get())));

    // -- Ameliorations du cobblebreaker --------------------------------------
    //
    // Des items ordinaires : c'est PalaMachineEvents qui leur donne un effet,
    // au clic droit sur la machine. Les identifiants reprennent exactement les
    // noms de texture du pack officiel.

    public static final DeferredItem<Item> AMETHYST_UPGRADE =
            ITEMS.registerSimpleItem("cobblebreaker_amethyst_upgrade", properties -> properties);
    public static final DeferredItem<Item> TITANE_UPGRADE =
            ITEMS.registerSimpleItem("cobblebreaker_titane_upgrade", properties -> properties);
    public static final DeferredItem<Item> PALADIUM_UPGRADE =
            ITEMS.registerSimpleItem("cobblebreaker_paladium_upgrade", properties -> properties);

    /** Amelioration de four : un BaseItem nu, en piles de 16. Lu par la Palafurnace. */
    public static final DeferredItem<Item> FURNACE_UPGRADE =
            ITEMS.registerSimpleItem("furnace_upgrade", properties -> properties.stacksTo(16));

    /** Blocs puis ameliorations, dans l'ordre d'affichage de l'onglet creatif. */
    public static final List<DeferredBlock<Block>> ORDERED_BLOCKS =
            List.of(COBBLEBREAKER_BLOCK, CRUSHER_BLOCK, TOTEM_BLOCK, PALAFURNACE_BLOCK, PALAFORGE_BLOCK,
                    GRINDER_BLOCK, GRINDER_CASING, GRINDER_FRAME);
    public static final List<DeferredItem<Item>> ORDERED_ITEMS =
            List.of(AMETHYST_UPGRADE, TITANE_UPGRADE, PALADIUM_UPGRADE, FURNACE_UPGRADE);

    /**
     * Les items des deux machines.
     *
     * <p>Construits par boucle : une machine ajoutee a {@link #ORDERED_BLOCKS}
     * recoit son item sans qu'on y pense, et il ne peut pas y avoir de machine
     * sans item — cas qui donnerait un bloc impossible a obtenir autrement
     * qu'en commande.
     *
     * <p><b>La liste est passee en argument, et ce n'est pas un detail.</b>
     * Lue directement dans {@code blockItems()}, elle serait encore
     * {@code null} si ce champ etait declare avant elle : les champs statiques
     * s'initialisent dans l'ordre du fichier, et le mod partirait en
     * {@code ExceptionInInitializerError} au chargement — ce qui est arrive
     * une fois. En la passant en argument, un champ place trop haut devient une
     * <b>erreur de compilation</b> (« illegal forward reference ») au lieu d'un
     * plantage au demarrage que la CI ne peut pas voir.
     */
    public static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = blockItems(ORDERED_BLOCKS);

    private PalaMachines() {
    }

    private static List<DeferredItem<BlockItem>> blockItems(List<DeferredBlock<Block>> blocks) {
        List<DeferredItem<BlockItem>> list = new ArrayList<>();
        for (DeferredBlock<Block> block : blocks) {
            list.add(ITEMS.registerSimpleBlockItem(block, properties -> properties));
        }
        return List.copyOf(list);
    }

    /**
     * Proprietes communes aux machines.
     *
     * <p>Calibrees sur le fourneau vanilla (3,5) : assez dures pour qu'on ne
     * les casse pas par megarde, assez tendres pour qu'on puisse deplacer son
     * atelier. {@code requiresCorrectToolForDrops} evite qu'une machine se
     * recupere a main nue.
     */
    private static Block.Properties machineProperties(Block.Properties properties) {
        return properties
                .strength(3.5f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
    }
}

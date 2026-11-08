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
 * <p>Deux machines dans ce chapitre, les deux dont la specification est
 * entierement lue au bytecode : le cobblebreaker avec ses quatre paliers et le
 * crusher avec ses quatre recettes. Le totem de fertilite attend le chapitre
 * suivant — ses deux constantes de cadence sont ambigues (millisecondes ou
 * ticks, un facteur 20 d'ecart sur l'equilibrage) et il vaut mieux l'etablir
 * que le deviner.
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

    /** Blocs puis ameliorations, dans l'ordre d'affichage de l'onglet creatif. */
    public static final List<DeferredBlock<Block>> ORDERED_BLOCKS =
            List.of(COBBLEBREAKER_BLOCK, CRUSHER_BLOCK);
    public static final List<DeferredItem<Item>> ORDERED_ITEMS =
            List.of(AMETHYST_UPGRADE, TITANE_UPGRADE, PALADIUM_UPGRADE);

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

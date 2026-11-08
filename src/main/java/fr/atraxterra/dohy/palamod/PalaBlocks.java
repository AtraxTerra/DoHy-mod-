package fr.atraxterra.dohy.palamod;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Minerais et blocs de stockage du PalaMod.
 *
 * <p><b>Les chiffres ne sont pas inventes.</b> Durete, resistance a l'explosion
 * et niveau de pioche sont releves au bytecode dans
 * {@code fr.paladium.palamod.modules.world.PWorld} — voir {@code docs/PALAMOD.md}
 * pour la table. Le niveau de pioche passe par les tags
 * {@code needs_stone_tool} / {@code needs_iron_tool} / {@code needs_diamond_tool},
 * generes avec les assets : en 26.2 un niveau d'outil est une donnee, plus un
 * appel de code comme le {@code setHarvestLevel} de 1.7.10.
 *
 * <p>Ce sont des {@link Block} nus et non des {@code DropExperienceBlock} : le
 * minerai ne lache pas d'XP vanilla, l'XP de minage du mod passe par le metier de Mineur.
 * Un bloc nu est aussi la forme dont la signature est la plus stable d'une
 * version a l'autre.
 */
public final class PalaBlocks {

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(PalaMod.NAMESPACE);

    // -- Minerais ------------------------------------------------------------
    // Les quatre arguments repris de PWorld : durete, resistance, et le son.
    // Le niveau de pioche vit dans les tags, pas ici.

    public static final DeferredBlock<Block> AMETHYST_ORE = ore("amethyst_ore", 3.0f);
    public static final DeferredBlock<Block> TITANE_ORE = ore("titane_ore", 3.0f);
    public static final DeferredBlock<Block> PALADIUM_ORE = ore("paladium_ore", 5.0f);
    public static final DeferredBlock<Block> PALADIUM_GREEN_ORE = ore("paladium_green_ore", 5.0f);
    public static final DeferredBlock<Block> FINDIUM_ORE = ore("findium_ore", 5.0f);
    public static final DeferredBlock<Block> TRIXIUM_ORE = ore("trixium_ore", 3.0f);

    // -- Blocs de stockage ---------------------------------------------------
    // Calibres sur le bloc de fer vanilla (5,0 / 6,0), son metallique.

    public static final DeferredBlock<Block> AMETHYST_BLOCK = storage("amethyst_block");
    public static final DeferredBlock<Block> TITANE_BLOCK = storage("titane_block");
    public static final DeferredBlock<Block> PALADIUM_BLOCK = storage("paladium_block");
    public static final DeferredBlock<Block> GREEN_PALADIUM_BLOCK = storage("green_paladium_block");
    public static final DeferredBlock<Block> ENDIUM_BLOCK = storage("endium_block");
    public static final DeferredBlock<Block> FINDIUM_BLOCK = storage("findium_block");
    public static final DeferredBlock<Block> TRIXIUM_BLOCK = storage("trixium_block");

    // -- Terre fertilisee ----------------------------------------------------
    //
    // Elle fait passer DEUX ticks de croissance supplementaires a la plante
    // posee dessus, a chaque tick aleatoire : la plante avance donc environ
    // trois fois plus vite. Voir PalaFertileSoilBlock.
    //
    // Deux variantes, comme le booleen `tilled` de BlockConnectedDirt : la
    // brute porte l'herbe, les fleurs et les pousses d'arbre, la labouree porte
    // les cultures. La houe fait passer de l'une a l'autre (PalaSoilEvents).
    //
    // Durete 0,6 et son de gravier, comme dans le constructeur d'origine.

    public static final DeferredBlock<Block> FERTILIZED_DIRT = soil("fertilized_dirt", false);
    public static final DeferredBlock<Block> FERTILIZED_FARMLAND = soil("fertilized_farmland", true);

    /**
     * Tous les blocs, dans l'ordre d'affichage de l'onglet creatif.
     *
     * <p>Minerais puis blocs de stockage, chaque famille dans l'ordre de rarete
     * de Paladium : amethyste, titane, paladium, paladium vert, endium, puis
     * les deux matieres a part (findium, trixium). C'est l'ordre dans lequel le
     * joueur les rencontre, donc celui dans lequel il les cherche.
     */
    public static final List<DeferredBlock<Block>> ORDERED = List.of(
            AMETHYST_ORE, TITANE_ORE, PALADIUM_ORE, PALADIUM_GREEN_ORE,
            FINDIUM_ORE, TRIXIUM_ORE,
            AMETHYST_BLOCK, TITANE_BLOCK, PALADIUM_BLOCK, GREEN_PALADIUM_BLOCK,
            ENDIUM_BLOCK, FINDIUM_BLOCK, TRIXIUM_BLOCK,
            FERTILIZED_DIRT, FERTILIZED_FARMLAND);

    private PalaBlocks() {
    }

    private static DeferredBlock<Block> ore(String name, float hardness) {
        return BLOCKS.registerSimpleBlock(name, properties -> properties
                .strength(hardness, 5.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE));
    }

    /**
     * Une terre fertilisee.
     *
     * <p>{@code randomTicks()} n'est pas decoratif : c'est lui qui donne au bloc
     * les ticks pendant lesquels il pousse la plante du dessus. Sans cet appel,
     * le bloc existerait et ne ferait rien — le genre de panne qui ne produit
     * aucune erreur et se diagnostique en regardant pousser du ble.
     */
    private static DeferredBlock<Block> soil(String name, boolean tilled) {
        return BLOCKS.registerBlock(name,
                properties -> new PalaFertileSoilBlock(properties, tilled),
                properties -> properties
                        .strength(0.6f)
                        .randomTicks()
                        .sound(SoundType.GRAVEL));
    }

    private static DeferredBlock<Block> storage(String name) {
        return BLOCKS.registerSimpleBlock(name, (BlockBehaviour.Properties properties) -> properties
                .strength(5.0f, 6.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL));
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}

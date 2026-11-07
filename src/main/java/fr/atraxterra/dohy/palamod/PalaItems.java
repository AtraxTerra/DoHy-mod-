package fr.atraxterra.dohy.palamod;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Items du PalaMod : particules, lingots, compresses, charbons mixes.
 *
 * <p>C'est le socle des chapitres suivants. Sans eux les machines n'ont ni
 * entree ni sortie : le cobblebreaker rend des particules, le crusher consomme
 * des charbons mixes et rend des lingots. Ce chapitre n'avait pas ete demande,
 * il precede simplement ceux qui l'ont ete.
 *
 * <p><b>Le fer, l'or et le diamant n'ont pas de lingot propre.</b> Les
 * particules correspondantes se craftent en items <i>vanilla</i> : un fer
 * parallele au fer du jeu casserait tout l'equilibrage du shop, qui raisonne
 * sur {@code minecraft:iron_ingot}. Seules les matieres que Paladium ajoute
 * vraiment ont leur lingot.
 */
public final class PalaItems {

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(PalaMod.NAMESPACE);

    /**
     * Les particules, sorties du cobblebreaker.
     *
     * <p>Six types dans le wiki, <b>sept dans le code</b> : la particule de
     * paladium vert existe et n'est pas documentee. On l'enregistre, sa texture
     * est dans le jar.
     */
    public static final DeferredItem<Item> IRON_PARTICLE = simple("iron_particle");
    public static final DeferredItem<Item> GOLD_PARTICLE = simple("gold_particle");
    public static final DeferredItem<Item> DIAMOND_PARTICLE = simple("diamond_particle");
    public static final DeferredItem<Item> AMETHYST_PARTICLE = simple("amethyst_particle");
    public static final DeferredItem<Item> TITANE_PARTICLE = simple("titane_particle");
    public static final DeferredItem<Item> PALADIUM_PARTICLE = simple("paladium_particle");
    public static final DeferredItem<Item> GREEN_PALADIUM_PARTICLE = simple("green_paladium_particle");

    // -- Lingots et matieres -------------------------------------------------

    public static final DeferredItem<Item> AMETHYST_INGOT = simple("amethyst_ingot");
    public static final DeferredItem<Item> TITANE_INGOT = simple("titane_ingot");
    public static final DeferredItem<Item> PALADIUM_INGOT = simple("paladium_ingot");
    public static final DeferredItem<Item> GREEN_PALADIUM_INGOT = simple("green_paladium_ingot");
    public static final DeferredItem<Item> ENDIUM_INGOT = simple("endium_ingot");
    public static final DeferredItem<Item> ENDIUM_NUGGET = simple("endium_nugget");
    public static final DeferredItem<Item> ENDIUM_FRAGMENT = simple("endium_fragment");
    public static final DeferredItem<Item> FINDIUM = simple("findium");
    public static final DeferredItem<Item> TRIXIUM = simple("trixium");

    // -- Compresses ----------------------------------------------------------

    public static final DeferredItem<Item> COMPRESSED_AMETHYST = simple("compressed_amethyst");
    public static final DeferredItem<Item> COMPRESSED_TITANE = simple("compressed_titane");
    public static final DeferredItem<Item> COMPRESSED_PALADIUM = simple("compressed_paladium");
    public static final DeferredItem<Item> COMPRESSED_ENDIUM = simple("compressed_endium");

    // -- Charbons mixes ------------------------------------------------------
    // Un par type de fruit, releve dans TileCrusher$EnumCrusherRecipes. Leurs
    // recettes de fabrication arrivent avec le crusher : les inventer ici
    // serait un chiffre de plus a defaire ensuite.

    public static final DeferredItem<Item> GOLD_MIXED_COAL = simple("gold_mixed_coal");
    public static final DeferredItem<Item> AMETHYST_MIXED_COAL = simple("amethyst_mixed_coal");
    public static final DeferredItem<Item> TITANE_MIXED_COAL = simple("titane_mixed_coal");
    public static final DeferredItem<Item> PALA_MIXED_COAL = simple("pala_mixed_coal");

    /** Les items, dans l'ordre d'affichage de l'onglet creatif. */
    public static final List<DeferredItem<Item>> ORDERED = List.of(
            IRON_PARTICLE, GOLD_PARTICLE, DIAMOND_PARTICLE, AMETHYST_PARTICLE,
            TITANE_PARTICLE, PALADIUM_PARTICLE, GREEN_PALADIUM_PARTICLE,
            AMETHYST_INGOT, TITANE_INGOT, PALADIUM_INGOT, GREEN_PALADIUM_INGOT,
            ENDIUM_INGOT, ENDIUM_NUGGET, ENDIUM_FRAGMENT, FINDIUM, TRIXIUM,
            COMPRESSED_AMETHYST, COMPRESSED_TITANE, COMPRESSED_PALADIUM, COMPRESSED_ENDIUM,
            GOLD_MIXED_COAL, AMETHYST_MIXED_COAL, TITANE_MIXED_COAL, PALA_MIXED_COAL);

    /**
     * Les {@code BlockItem} des blocs, dans l'ordre de {@link PalaBlocks#ORDERED}.
     *
     * <p>Construite par boucle et non a la main : un bloc ajoute a
     * {@code PalaBlocks.ORDERED} recoit son item sans qu'on y pense, et il ne
     * peut pas y avoir de bloc sans item — cas qui donne un bloc impossible a
     * obtenir autrement qu'en commande.
     */
    public static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = blockItems();

    private PalaItems() {
    }

    private static DeferredItem<Item> simple(String name) {
        return ITEMS.registerSimpleItem(name, properties -> properties);
    }

    private static List<DeferredItem<BlockItem>> blockItems() {
        List<DeferredItem<BlockItem>> list = new ArrayList<>();
        for (var block : PalaBlocks.ORDERED) {
            list.add(ITEMS.registerSimpleBlockItem(block, properties -> properties));
        }
        return List.copyOf(list);
    }

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}

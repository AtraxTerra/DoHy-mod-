package fr.atraxterra.dohy.palamod;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import fr.atraxterra.dohy.palamod.machine.VoidstoneItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
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

    // -- Fruits --------------------------------------------------------------
    // Ce que les cultures rendent, et ce que le crusher consomme : 32
    // aubergines, 32 cerfeuils, 64 kiwanos ou 128 orangeblues par cycle.

    public static final DeferredItem<Item> EGGPLANT = simple("eggplant");
    public static final DeferredItem<Item> CHERVIL = simple("chervil");
    public static final DeferredItem<Item> KIWANO = simple("kiwano");
    public static final DeferredItem<Item> ORANGEBLUE = simple("orangeblue");

    // -- Graines -------------------------------------------------------------
    //
    // Des BlockItem, et non l'ancien ItemNameBlockItem — cette classe n'existe
    // plus en 26.2 (verifie a la sonde). Elle ne servait qu'a deduire le bloc du
    // nom de l'item ; maintenant que l'identifiant est explicite, le detour n'a
    // plus de raison d'etre.
    //
    // C'est la pose du bloc de culture qui declenche le verrou de metier, pas
    // la graine : JobGateEvents annule EntityPlaceEvent, et l'annulation rend
    // la graine au joueur. Rien n'est perdu sur un refus.

    public static final DeferredItem<BlockItem> EGGPLANT_SEEDS =
            seed("eggplant_seeds", PalaCrops.EGGPLANT_CROP);
    public static final DeferredItem<BlockItem> CHERVIL_SEEDS =
            seed("chervil_seeds", PalaCrops.CHERVIL_CROP);
    public static final DeferredItem<BlockItem> KIWANO_SEEDS =
            seed("kiwano_seeds", PalaCrops.KIWANO_CROP);
    public static final DeferredItem<BlockItem> ORANGEBLUE_SEEDS =
            seed("orangeblue_seeds", PalaCrops.ORANGEBLUE_CROP);

    /**
     * Intermediaires de fabrication, releves dans les recettes de PalaMod.
     *
     * <p>Ils n'ont aucun comportement : ce sont des pieces qui n'existent que
     * pour etre assemblees. Les porter n'etait pas un choix esthetique — sans
     * les batons et le coeur, <b>dix des quinze recettes du chapitre etaient
     * impossibles a ecrire</b> : les cinq seedplanters, le crusher et le
     * charbon mixe de paladium les demandent tous.
     *
     * <p>Le coeur de paladium s'appelle {@code paladiumCore} dans le pack de
     * textures ; la correspondance est dans le generateur.
     */
    public static final DeferredItem<Item> AMETHYST_STICK = simple("amethyst_stick");
    public static final DeferredItem<Item> TITANE_STICK = simple("titane_stick");
    public static final DeferredItem<Item> PALADIUM_STICK = simple("paladium_stick");
    public static final DeferredItem<Item> PALADIUM_CORE = simple("paladium_core");

    /**
     * Le materiau de l'armure en endium mixe : 8 lingots de paladium vert et
     * un fragment d'endium en donnent 8. Sans lui, l'armure serait la seule de
     * l'equipement impossible a fabriquer.
     */
    public static final DeferredItem<Item> MIXED_ENDIUM_INGOT = simple("mixed_endium_ingot");

    /**
     * La chaine du cobblebreaker et de ses ameliorations.
     *
     * <p>L'orbe de vitesse est un {@code BaseItem} nu dans PalaMod. Les deux
     * modificateurs sont des {@code ItemGrinderModifier}, eux aussi sans aucun
     * comportement propre : c'est le grinder qui les lit, et il viendra avec le
     * chapitre 5b. Ils servent d'ici la d'ingredients, exactement comme dans
     * PalaMod ou on les fabriquait aussi pour le cobblebreaker.
     */
    public static final DeferredItem<Item> SPEED_ORB = simple("speed_orb");
    public static final DeferredItem<Item> SPEED_MODIFIER = simple("speed_modifier");
    public static final DeferredItem<Item> FORTUNE_MODIFIER = simple("fortune_modifier");

    /** La poubelle de poche. Avec son comportement : voir VoidstoneItem. */
    public static final DeferredItem<VoidstoneItem> VOIDSTONE =
            ITEMS.registerItem("voidstone", properties -> new VoidstoneItem(properties.stacksTo(1)));

    /** Fruits puis graines, chaque famille dans l'ordre de progression. */
    public static final List<DeferredItem<? extends Item>> CROP_ITEMS = List.of(
            EGGPLANT_SEEDS, CHERVIL_SEEDS, KIWANO_SEEDS, ORANGEBLUE_SEEDS,
            EGGPLANT, CHERVIL, KIWANO, ORANGEBLUE);

    /** Les items, dans l'ordre d'affichage de l'onglet creatif. */
    public static final List<DeferredItem<Item>> ORDERED = List.of(
            IRON_PARTICLE, GOLD_PARTICLE, DIAMOND_PARTICLE, AMETHYST_PARTICLE,
            TITANE_PARTICLE, PALADIUM_PARTICLE, GREEN_PALADIUM_PARTICLE,
            AMETHYST_INGOT, TITANE_INGOT, PALADIUM_INGOT, GREEN_PALADIUM_INGOT,
            ENDIUM_INGOT, ENDIUM_NUGGET, ENDIUM_FRAGMENT, FINDIUM, TRIXIUM,
            COMPRESSED_AMETHYST, COMPRESSED_TITANE, COMPRESSED_PALADIUM, COMPRESSED_ENDIUM,
            GOLD_MIXED_COAL, AMETHYST_MIXED_COAL, TITANE_MIXED_COAL, PALA_MIXED_COAL,
            AMETHYST_STICK, TITANE_STICK, PALADIUM_STICK, PALADIUM_CORE, MIXED_ENDIUM_INGOT,
            SPEED_ORB, SPEED_MODIFIER, FORTUNE_MODIFIER);

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

    /**
     * Une graine : un {@link BlockItem} qui pose la culture.
     *
     * <p>Le nom de l'item et celui du bloc diffferent volontairement
     * ({@code eggplant_seeds} pose {@code eggplant_crop}), comme
     * {@code wheat_seeds} pose {@code wheat} chez vanilla. C'est pour cela qu'on
     * ne passe pas par {@code registerSimpleBlockItem}, qui nommerait l'item
     * d'apres le bloc.
     */
    private static DeferredItem<BlockItem> seed(String name, DeferredBlock<Block> crop) {
        return ITEMS.registerItem(name, properties -> new BlockItem(crop.get(), properties));
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

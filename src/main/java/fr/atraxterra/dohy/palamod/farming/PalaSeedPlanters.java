package fr.atraxterra.dohy.palamod.farming;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Les cinq seedplanters.
 *
 * <p>Les rayons sont ceux de {@code PRegister_Items}, ou chaque planteur est
 * construit par {@code SeedPlanterSeedFood(nom, r, lvlmin, infos)} :
 *
 * <table>
 *   <tr><th>Planteur</th><th>{@code r}</th><th>Carre plante</th></tr>
 *   <tr><td>amethyste</td><td>3</td><td>2 x 2</td></tr>
 *   <tr><td>titane</td><td>5</td><td>4 x 4</td></tr>
 *   <tr><td>paladium</td><td>7</td><td>6 x 6</td></tr>
 *   <tr><td>endium</td><td>12</td><td>12 x 12</td></tr>
 *   <tr><td>paladium vert</td><td>12</td><td>12 x 12</td></tr>
 * </table>
 *
 * <p><b>Le paladium vert a le meme rayon que l'endium pour un niveau plus
 * bas</b> (Fermier 90 contre 100) : c'est une version alternative et non un
 * palier superieur. Conserve tel quel — c'est une donnee d'equilibrage de
 * Paladium, pas une incoherence a corriger.
 *
 * <p><b>Deux jeux de niveaux se contredisent dans PalaMod</b>, et il faut le
 * savoir avant de croire a une erreur de portage : le champ {@code lvlmin} de
 * l'item (5, 10, 15, 20, 17) et le niveau de {@code FarmerRegisterer}
 * (3, 6, 15, 18, 20 sur l'echelle de 20) ne concordent que pour le paladium.
 * C'est le registre de metier qui gouverne le verrou, et c'est lui que
 * {@code data/jobs.json} porte ; {@code lvlmin} n'est qu'une valeur
 * d'infobulle. Les deux sont notes dans {@code docs/PALAMOD.md}.
 *
 * <p>Les identifiants reprennent ceux du chapitre 1, ou les verrous les
 * nommaient deja. Les textures d'origine s'appellent {@code seedplanter_lvl1} a
 * {@code lvl5} : la correspondance est dans {@code tools/palamod_assets.py},
 * pas ici.
 */
public final class PalaSeedPlanters {

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(PalaMod.NAMESPACE);

    public static final DeferredItem<Item> AMETHYST = planter("amethyst_seedplanter", 3);
    public static final DeferredItem<Item> TITANE = planter("titane_seedplanter", 5);
    public static final DeferredItem<Item> PALADIUM = planter("paladium_seedplanter", 7);
    public static final DeferredItem<Item> GREEN_PALADIUM = planter("green_paladium_seedplanter", 12);
    public static final DeferredItem<Item> ENDIUM = planter("endium_seedplanter", 12);

    /**
     * Dans l'ordre des verrous de metier, donc de la progression du joueur.
     *
     * <p>Le paladium vert est avant l'endium : son verrou est a 90 et celui de
     * l'endium a 100, meme si les deux plantent le meme carre.
     */
    public static final List<DeferredItem<Item>> ORDERED =
            List.of(AMETHYST, TITANE, PALADIUM, GREEN_PALADIUM, ENDIUM);

    private PalaSeedPlanters() {
    }

    private static DeferredItem<Item> planter(String name, int radius) {
        return ITEMS.registerItem(name, properties -> new SeedPlanterItem(properties, radius));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}

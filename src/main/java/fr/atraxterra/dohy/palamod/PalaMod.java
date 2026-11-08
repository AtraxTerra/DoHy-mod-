package fr.atraxterra.dohy.palamod;

import net.neoforged.bus.api.IEventBus;

/**
 * Le contenu converti du PalaMod de Paladium.
 *
 * <p><b>Pourquoi un namespace separe.</b> Tout ce qui vient du PalaMod est
 * enregistre sous {@code palamod} et non sous {@code dohy}, avec les
 * identifiants d'origine. Cela a une consequence concrete et voulue : le pack
 * de textures officiel de Paladium, qui range ses fichiers sous
 * {@code assets/palamod/}, s'applique alors <b>tel quel</b>, sans conversion ni
 * seconde copie dans le jar. Le mod embarque les textures du PalaMod comme
 * defaut ; le pack officiel les remplace, ce qui est exactement son role.
 *
 * <p>Les assets et les donnees de ce namespace sont <b>generes</b> par
 * {@code tools/palamod_assets.py} depuis le jar de PalaMod. Vingt-quatre items
 * et treize blocs font plus de 150 fichiers dont la seule variable est un nom ;
 * les ecrire a la main garantit une faute de frappe, et une faute de frappe
 * dans un chemin de modele ne casse pas la compilation — elle donne une texture
 * violette en jeu, decouverte des semaines plus tard.
 *
 * <p>La CI verifie que les noms enregistres ici et les fichiers generes
 * correspondent exactement, dans les deux sens : un item enregistre sans assets
 * serait violet, un asset sans enregistrement serait un fichier mort.
 */
public final class PalaMod {

    /** Namespace de tout le contenu converti. */
    public static final String NAMESPACE = "palamod";

    private PalaMod() {
    }

    /**
     * Enregistre blocs, items et onglet creatif.
     *
     * <p>L'ordre compte : les blocs et les cultures avant les items, parce que
     * chaque bloc de {@link PalaBlocks} recoit son {@code BlockItem} dans
     * {@link PalaItems}, et chaque culture de {@link PalaCrops} y recoit sa
     * graine ; les uns referenceraient sinon les autres avant qu'ils existent.
     */
    public static void register(IEventBus modBus) {
        PalaBlocks.register(modBus);
        PalaCrops.register(modBus);
        PalaItems.register(modBus);
        PalaTab.register(modBus);
    }
}

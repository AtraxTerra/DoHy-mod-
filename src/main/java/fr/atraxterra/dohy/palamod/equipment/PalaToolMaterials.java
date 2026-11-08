package fr.atraxterra.dohy.palamod.equipment;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ToolMaterial;

/**
 * Les cinq materiaux d'outils, tels que {@code PToolMaterial} les declare.
 *
 * <p>Chaque ligne d'origine est
 * {@code EnumHelper.addToolMaterial(nom, recolte, durabilite, efficacite, degats, enchant)}.
 * Les quatre nombres se portent tels quels dans le record {@link ToolMaterial}
 * de 26.2 ; seul le niveau de recolte change de forme, devenu un tag de blocs
 * <i>que l'outil ne recolte pas</i>.
 *
 * <p><b>Les efficacites sont tres hautes, et c'est voulu.</b> 20 a 40, contre 9
 * pour la netherite : un outil Paladium creuse deux a quatre fois plus vite que
 * le meilleur outil vanilla. C'est la donnee d'origine ; la reduire changerait
 * l'economie de minage autour de laquelle tout le metier de mineur est bati.
 *
 * <p><b>Les degats se portent exactement</b>, parce que la formule de l'epee n'a
 * pas change : {@code 4 + bonus} en 1.7.10, {@code 1 + bonus + 3} en 26.2. Voir
 * {@link PalaEquipment}.
 *
 * <p>Le niveau de recolte 4 de l'endium est au-dela du diamant : il prend le tag
 * de la netherite, qui ne refuse rien de ce que ce mod genere.
 */
public final class PalaToolMaterials {

    public static final ToolMaterial AMETHYST = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1999, 20.0f, 3.0f, 20,
            PalaTags.REPAIRS_AMETHYST);

    public static final ToolMaterial TITANE = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 2999, 23.0f, 3.5f, 20,
            PalaTags.REPAIRS_TITANE);

    public static final ToolMaterial PALADIUM = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 4999, 28.0f, 6.0f, 15,
            PalaTags.REPAIRS_PALADIUM);

    /** Meme durabilite et memes degats que le paladium : seule l'efficacite monte. */
    public static final ToolMaterial GREEN_PALADIUM = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 4999, 35.0f, 6.0f, 15,
            PalaTags.REPAIRS_GREEN_PALADIUM);

    public static final ToolMaterial ENDIUM = new ToolMaterial(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 4999, 40.0f, 7.3f, 25,
            PalaTags.REPAIRS_ENDIUM);

    private PalaToolMaterials() {
    }
}

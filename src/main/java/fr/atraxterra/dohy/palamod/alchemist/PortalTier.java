package fr.atraxterra.dohy.palamod.alchemist;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.palamod.PalaBlocks;
import fr.atraxterra.dohy.palamod.PalaItems;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Les quatre portails de l'Alchimiste ({@code EnumPortalType},
 * {@code PortalRecipe} dans {@code PAlchimiste.preInit}).
 *
 * <p>Ce ne sont pas des portails qui menent quelque part : ce sont des autels.
 * On y jette de la seve, elle s'accumule dans la cle inseree, et le portail
 * rend un lingot par palier atteint :
 * <table>
 *   <tr><th>portail</th><th>seve</th><th>rend</th><th>pour</th><th>XP (niveau)</th></tr>
 *   <tr><td>amethyste</td><td>jacaranda</td><td>1 lingot d'amethyste</td><td>1 000</td><td>6 (15)</td></tr>
 *   <tr><td>titane</td><td>judée</td><td>1 lingot de titane</td><td>2 000</td><td>20 (30)</td></tr>
 *   <tr><td>paladium</td><td>érable</td><td>1 lingot de paladium</td><td>4 000</td><td>40 (50)</td></tr>
 *   <tr><td>endium</td><td>ostrya</td><td>1 pepite d'endium</td><td>3 448 000 + 2 pollens d'endium</td><td>aucune</td></tr>
 * </table>
 * Le seuil d'endium est bien {@code sipush 3448} fois mille : 3 448 fioles
 * pleines par pepite. Il est porte tel quel.
 */
public enum PortalTier implements StringRepresentable {
    AMETHYST("amethyst", Sap.JACARANDA, 1_000, 6.0d, 15, 0),
    TITANE("titane", Sap.JUDEECERCIS, 2_000, 20.0d, 30, 0),
    PALADIUM("paladium", Sap.ERABLE, 4_000, 40.0d, 50, 0),
    ENDIUM("endium", Sap.OSTRYA, 3_448_000, 0.0d, 0, 2);

    public static final Codec<PortalTier> CODEC = StringRepresentable.fromEnum(PortalTier::values);

    private final String id;
    private final Sap sap;
    private final int required;
    private final double xp;
    private final int xpLevel;
    private final int pollen;

    PortalTier(String id, Sap sap, int required, double xp, int xpLevel, int pollen) {
        this.id = id;
        this.sap = sap;
        this.required = required;
        this.xp = xp;
        this.xpLevel = xpLevel;
        this.pollen = pollen;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public Sap sap() {
        return sap;
    }

    /** Unites de seve par objet rendu. */
    public int required() {
        return required;
    }

    public double xp() {
        return xp;
    }

    public int xpLevel() {
        return xpLevel;
    }

    /** Pollens d'endium par objet rendu (portail d'endium seulement). */
    public int pollen() {
        return pollen;
    }

    public Item output() {
        return switch (this) {
            case AMETHYST -> PalaItems.AMETHYST_INGOT.get();
            case TITANE -> PalaItems.TITANE_INGOT.get();
            case PALADIUM -> PalaItems.PALADIUM_INGOT.get();
            case ENDIUM -> PalaItems.ENDIUM_NUGGET.get();
        };
    }

    /** Le bloc des quatre coins du cadre. */
    public Block corner() {
        return switch (this) {
            case AMETHYST -> PalaBlocks.AMETHYST_BLOCK.get();
            case TITANE -> PalaBlocks.TITANE_BLOCK.get();
            case PALADIUM -> PalaBlocks.PALADIUM_BLOCK.get();
            case ENDIUM -> PalaAlchemist.ENDIUM_ANGLE_PORTAL_BLOCK.get();
        };
    }

    /** Le bois brillant des cotes du cadre. */
    public Block frame() {
        return PalaWoods.shiny(sap);
    }

    public Item key() {
        return switch (this) {
            case AMETHYST -> PalaAlchemist.AMETHYST_PORTAL_KEY.get();
            case TITANE -> PalaAlchemist.TITANE_PORTAL_KEY.get();
            case PALADIUM -> PalaAlchemist.PALADIUM_PORTAL_KEY.get();
            case ENDIUM -> PalaAlchemist.ENDIUM_PORTAL_KEY.get();
        };
    }
}

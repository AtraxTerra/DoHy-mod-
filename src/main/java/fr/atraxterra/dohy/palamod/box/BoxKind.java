package fr.atraxterra.dohy.palamod.box;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Les six boxes de la boutique de Paladium (jar PalaShop,
 * {@code assets/palashop/boxes}). Chacune est une loot box a part entiere : sa
 * cle, son prix et sa table de butin, sous son identifiant dans
 * {@code data/crates.json} ({@link fr.atraxterra.dohy.boost.Crates}). Avant, il
 * y avait trois caisses (commune, rare, legendaire) que les boxes ouvraient
 * deux par deux : elles sont devenues les boxes d'amethyste, de paladium et
 * d'endium.
 */
public enum BoxKind implements StringRepresentable {
    AMETHYST("amethyst", "Amethyste", 0.65f),
    PALADIUM("paladium", "Paladium", 0.6f),
    ENDIUM("endium", "Endium", 0.45f),
    SAHARIA("saharia", "Saharia", 0.55f),
    TRIXIUM("trixium", "Trixium", 0.45f),
    HALLOWEEN("halloween", "Halloween", 0.5f);

    public static final Codec<BoxKind> CODEC = StringRepresentable.fromEnum(BoxKind::values);

    private final String id;
    private final String label;
    private final float scale;

    BoxKind(String id, String label, float scale) {
        this.id = id;
        this.label = label;
        this.scale = scale;
    }

    /** La box de cet identifiant, ou {@code null}. */
    public static BoxKind byId(String id) {
        for (BoxKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }

    /** L'identifiant de sa table de butin et de sa cle : celui de la box. */
    public String crate() {
        return id;
    }

    /** « Amethyste » : le nom de la box, sans accent, pour le tchat et les menus. */
    public String label() {
        return label;
    }

    /** Le modele anime, sous {@code assets/palarpg/entity}. */
    public String model() {
        return "box_" + id;
    }

    /**
     * L'echelle du modele. A 1, la taille de Paladium, les coffres font de 1,4
     * a 2,7 blocs de large ; chacun est ramene a un peu plus d'un bloc.
     */
    public float scale() {
        return scale;
    }

    /** La bande-son d'une ouverture ({@code assets/palarpg/sounds.json}). */
    public String sound(String rarity) {
        return "box." + id + ".fast_" + rarity;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}

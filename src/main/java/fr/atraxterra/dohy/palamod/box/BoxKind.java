package fr.atraxterra.dohy.palamod.box;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Les six boxes de la boutique de Paladium (jar PalaShop,
 * {@code assets/palashop/boxes}). Chacune ouvre une caisse de DoHy : son
 * palier decide de la cle qu'elle prend et de la table de butin qu'elle tire
 * ({@code data/crates.json}). Trois metaux, puis trois habillages des
 * memes paliers.
 */
public enum BoxKind implements StringRepresentable {
    AMETHYST("amethyst", "common", 0.65f),
    PALADIUM("paladium", "rare", 0.6f),
    ENDIUM("endium", "legendary", 0.45f),
    SAHARIA("saharia", "common", 0.55f),
    TRIXIUM("trixium", "rare", 0.45f),
    HALLOWEEN("halloween", "legendary", 0.5f);

    public static final Codec<BoxKind> CODEC = StringRepresentable.fromEnum(BoxKind::values);

    private final String id;
    private final String tier;
    private final float scale;

    BoxKind(String id, String tier, float scale) {
        this.id = id;
        this.tier = tier;
        this.scale = scale;
    }

    /** Le palier de caisse que cette box ouvre : {@code common}, {@code rare}, {@code legendary}. */
    public String tier() {
        return tier;
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

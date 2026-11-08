package fr.atraxterra.dohy.palamod.alchemist;

import net.minecraft.util.StringRepresentable;

/** Ce que montre un tank : rien, ou l'une des quatre seves. Propriete d'etat, pour le modele. */
public enum TankContent implements StringRepresentable {
    EMPTY(null),
    JACARANDA(Sap.JACARANDA),
    JUDEECERCIS(Sap.JUDEECERCIS),
    ERABLE(Sap.ERABLE),
    OSTRYA(Sap.OSTRYA);

    private final Sap sap;

    TankContent(Sap sap) {
        this.sap = sap;
    }

    public static TankContent of(Sap sap) {
        return sap == null ? EMPTY : values()[sap.ordinal() + 1];
    }

    @Override
    public String getSerializedName() {
        return sap == null ? "empty" : sap.id();
    }
}

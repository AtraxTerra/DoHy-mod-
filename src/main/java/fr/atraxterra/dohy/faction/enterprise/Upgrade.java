package fr.atraxterra.dohy.faction.enterprise;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Les quatre ameliorations d'une entreprise (lot 24b), chacune achetee une fois. Leur nom
 * change entre une entreprise legale et une affaire illegale ; leur effet aussi pour la
 * troisieme : une assurance contre les catastrophes, ou des guetteurs contre la police.
 *
 * @param share la part du prix d'ouverture qu'elle coute
 */
public enum Upgrade {
    EQUIPEMENT("Machines modernes", "Materiel neuf", "Produit 20 % de plus", "Produit 20 % de plus", 0.30d),
    LOGISTIQUE("Logistique", "Planques", "Frais 20 % moins chers", "Frais 20 % moins chers", 0.25d),
    SECURITE("Assurance", "Guetteurs", "Catastrophes et guerre coutent moitie moins",
            "Risque 25 % plus bas", 0.35d),
    FORMATION("Formation", "Equipe soudee", "Salaires -25 %, production +10 %", "Salaires -25 %, production +10 %",
            0.30d);

    private final String legalLabel;
    private final String illegalLabel;
    private final String legalEffect;
    private final String illegalEffect;
    private final double share;

    Upgrade(String legalLabel, String illegalLabel, String legalEffect, String illegalEffect, double share) {
        this.legalLabel = legalLabel;
        this.illegalLabel = illegalLabel;
        this.legalEffect = legalEffect;
        this.illegalEffect = illegalEffect;
        this.share = share;
    }

    public String label(Sector sector) {
        return sector.legal() ? legalLabel : illegalLabel;
    }

    public String effect(Sector sector) {
        return sector.legal() ? legalEffect : illegalEffect;
    }

    public long price(Sector sector) {
        return Math.round(sector.price() * share / 100.0d) * 100L;
    }

    public ItemStack icon() {
        return new ItemStack(switch (this) {
            case EQUIPEMENT -> Items.PISTON;
            case LOGISTIQUE -> Items.HOPPER;
            case SECURITE -> Items.SHIELD;
            case FORMATION -> Items.BOOK;
        });
    }

    public static Upgrade of(String name) {
        for (Upgrade upgrade : values()) {
            if (upgrade.name().equals(name)) {
                return upgrade;
            }
        }
        return null;
    }
}

package fr.atraxterra.dohy.faction.enterprise;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * Les quatre drogues des affaires illegales (lot 24b), chacune avec sa matiere premiere et son
 * produit fini. Tout reste abstrait : des unites de stock, des prix, un climat pour la plante.
 * La drogue de synthese ne pousse pas : sa matiere premiere s'achete a un fournisseur.
 *
 * @param yield     la matiere premiere qu'une plantation de niveau 1 donne par heure (0 : rien ne pousse)
 * @param raw       ce que vaut une unite de matiere premiere chez un fournisseur
 * @param wholesale ce que vaut une unite de produit fini en gros, chez un fournisseur
 * @param street    ce que paie la rue pour une unite, par un reseau de distribution
 * @param grey      le prix d'une unite de matiere premiere sans accord avec un fournisseur (0 : introuvable)
 * @param risk      ce que la drogue ajoute au risque d'une plantation ou d'un laboratoire
 */
public enum Line {
    CANNABIS("Cannabis", "Feuilles de cannabis", "Herbe conditionnee", 24.0d, 30L, 120L, 200L, 0L, 0.0d,
            () -> new ItemStack(Items.FERN), () -> new ItemStack(Items.DYE.pick(DyeColor.GREEN)), "Pousse presque partout, sauf aux poles"),
    COCAINE("Cocaine", "Feuilles de coca", "Poudre", 14.0d, 70L, 260L, 420L, 0L, 8.0d,
            () -> new ItemStack(Items.BIG_DRIPLEAF), () -> new ItemStack(Items.SUGAR), "Ne pousse que sous les tropiques"),
    HEROINE("Heroine", "Pavot", "Heroine", 12.0d, 80L, 290L, 460L, 0L, 10.0d,
            () -> new ItemStack(Items.POPPY), () -> new ItemStack(Items.DYE.pick(DyeColor.BROWN)), "Pousse entre 15 et 45 degres de latitude"),
    SYNTHESE("Drogue de synthese", "Precurseurs", "Pilules", 0.0d, 60L, 210L, 330L, 90L, 5.0d,
            () -> new ItemStack(Items.GLASS_BOTTLE), () -> new ItemStack(Items.AMETHYST_SHARD), "Ne pousse pas : ses precurseurs s'achetent");

    private final String label;
    private final String rawLabel;
    private final String productLabel;
    private final double yield;
    private final long raw;
    private final long wholesale;
    private final long street;
    private final long grey;
    private final double risk;
    private final Supplier<ItemStack> rawIcon;
    private final Supplier<ItemStack> productIcon;
    private final String climate;

    Line(String label, String rawLabel, String productLabel, double yield, long raw, long wholesale, long street,
         long grey, double risk, Supplier<ItemStack> rawIcon, Supplier<ItemStack> productIcon, String climate) {
        this.label = label;
        this.rawLabel = rawLabel;
        this.productLabel = productLabel;
        this.yield = yield;
        this.raw = raw;
        this.wholesale = wholesale;
        this.street = street;
        this.grey = grey;
        this.risk = risk;
        this.rawIcon = rawIcon;
        this.productIcon = productIcon;
        this.climate = climate;
    }

    public String label() {
        return label;
    }

    public String rawLabel() {
        return rawLabel;
    }

    public String productLabel() {
        return productLabel;
    }

    public double yield() {
        return yield;
    }

    public boolean grows() {
        return yield > 0.0d;
    }

    public long raw() {
        return raw;
    }

    public long wholesale() {
        return wholesale;
    }

    public long street() {
        return street;
    }

    public long grey() {
        return grey;
    }

    public double risk() {
        return risk;
    }

    public ItemStack rawIcon() {
        return rawIcon.get();
    }

    public ItemStack productIcon() {
        return productIcon.get();
    }

    public String climate() {
        return climate;
    }

    /** Vrai si la plante pousse a cette latitude. */
    public boolean growsAt(double latitude) {
        double abs = Math.abs(latitude);
        return switch (this) {
            case CANNABIS -> abs <= 60.0d;
            case COCAINE -> abs < 23.44d;
            case HEROINE -> abs >= 15.0d && abs <= 45.0d;
            case SYNTHESE -> false;
        };
    }

    /** La cle de stock de sa matiere premiere. */
    public String rawKey() {
        return "raw:" + name();
    }

    /** La cle de stock de son produit fini. */
    public String productKey() {
        return "drug:" + name();
    }

    public static Line of(String name) {
        for (Line line : values()) {
            if (line.name().equals(name)) {
                return line;
            }
        }
        return null;
    }
}

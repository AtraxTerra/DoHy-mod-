package fr.atraxterra.dohy.faction.org;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * Ce qu'est ta nation (lot 24a) : un pays officiel, une organisation criminelle, une faction
 * militaire ou un groupe independant. Le regime pese sur les revenus des affaires (legales et
 * illegales, lot 24b), sur leur risque, sur la confiance des douze nations et sur la crainte
 * qu'elles ont de toi.
 *
 * @param legal   le multiplicateur des revenus des entreprises legales
 * @param illegal le multiplicateur des revenus des affaires illegales
 * @param risk    le multiplicateur du risque des affaires illegales
 * @param trust   ce que le regime retire a l'hostilite que les nations tendent a avoir (negatif : il l'ajoute)
 * @param fear    ce qu'il ajoute a leur crainte de t'attaquer
 * @param crime   ce qu'il ajoute a l'estime des organisations criminelles (lot 24b)
 */
public enum Regime {
    INDEPENDANT("Groupe independant", "Ni pays ni cartel : rien ne t'aide, rien ne te freine",
            () -> new ItemStack(Items.LEATHER_HELMET), 0xFF9AA0A6, 1.00d, 1.00d, 1.00d, 0.0d, 0.0d, 0.0d),
    ETAT("Etat officiel", "Un pays reconnu : le commerce et les traites lui sourient, la police aussi",
            () -> new ItemStack(Items.BELL), 0xFF2F5BEA, 1.15d, 0.85d, 1.20d, 8.0d, 0.0d, -10.0d),
    CRIMINELLE("Organisation criminelle", "Un cartel : l'argent sale rapporte, les Etats se mefient",
            () -> new ItemStack(Items.WITHER_SKELETON_SKULL), 0xFFEF3826, 0.90d, 1.25d, 0.85d, -8.0d, 3.0d, 15.0d),
    MILITAIRE("Faction militaire", "Une junte : on te craint, on traite moins avec toi",
            () -> new ItemStack(Items.IRON_SWORD), 0xFF7A8F3A, 0.95d, 1.05d, 1.00d, -4.0d, 10.0d, 0.0d);

    private final String label;
    private final String summary;
    private final Supplier<ItemStack> icon;
    private final int color;
    private final double legal;
    private final double illegal;
    private final double risk;
    private final double trust;
    private final double fear;
    private final double crime;

    Regime(String label, String summary, Supplier<ItemStack> icon, int color, double legal, double illegal,
           double risk, double trust, double fear, double crime) {
        this.label = label;
        this.summary = summary;
        this.icon = icon;
        this.color = color;
        this.legal = legal;
        this.illegal = illegal;
        this.risk = risk;
        this.trust = trust;
        this.fear = fear;
        this.crime = crime;
    }

    public String label() {
        return label;
    }

    public String summary() {
        return summary;
    }

    public ItemStack icon() {
        return icon.get();
    }

    public int color() {
        return color;
    }

    public double legal() {
        return legal;
    }

    public double illegal() {
        return illegal;
    }

    public double risk() {
        return risk;
    }

    public double trust() {
        return trust;
    }

    public double fear() {
        return fear;
    }

    public double crime() {
        return crime;
    }

    /** Ses effets en une ligne, pour la carte du regime. */
    public String effects() {
        return percent("legal", legal) + " · " + percent("illegal", illegal) + " · " + percent("risque", risk);
    }

    private static String percent(String what, double factor) {
        long delta = Math.round((factor - 1.0d) * 100.0d);
        return what + " " + (delta > 0 ? "+" : "") + delta + " %";
    }

    public static Regime of(String name) {
        for (Regime regime : values()) {
            if (regime.name().equals(name)) {
                return regime;
            }
        }
        return INDEPENDANT;
    }
}

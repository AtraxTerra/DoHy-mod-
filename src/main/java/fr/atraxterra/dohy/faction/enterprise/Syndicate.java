package fr.atraxterra.dohy.faction.enterprise;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * Les six organisations criminelles du monde (lot 24b), des PNJ : chacune tient une region et
 * a sa specialite, qu'elle met a ton service par un accord. Toutes sont inventees.
 *
 * @param aggression sa promptitude a la guerre de territoire, de 0 a 1
 * @param greed      ce qu'elle demande pour un accord, de 0 a 1
 * @param honor      sa rancune quand on la trahit, de 0 a 1
 */
public enum Syndicate {
    DOCKS("Le Syndicat des Docks", Region.AMERIQUE_NORD, Specialty.CONTREBANDE, 0.45d, 0.60d, 0.55d,
            () -> Items.FISHING_ROD, 0xFF27C4E8),
    CONDOR("La Compagnie du Condor", Region.AMERIQUE_SUD, Specialty.RECEL, 0.55d, 0.45d, 0.40d,
            () -> Items.FEATHER, 0xFFE8590C),
    VALCORTE("La Famille Valcorte", Region.EUROPE, Specialty.CASINO, 0.35d, 0.70d, 0.80d,
            () -> Items.GOLD_INGOT, 0xFFFAC400),
    CARAVANE("La Caravane Ecarlate", Region.AFRIQUE, Specialty.RESEAU, 0.50d, 0.50d, 0.60d,
            () -> Items.RED_SAND, 0xFFEF3826),
    LOTUS("La Confrerie du Lotus", Region.ASIE, Specialty.ARMES, 0.40d, 0.55d, 0.75d,
            () -> Items.CHERRY_SAPLING, 0xFFEC4FB5),
    RECIFS("Le Cartel des Recifs", Region.OCEANIE, Specialty.DROGUES, 0.60d, 0.65d, 0.35d,
            () -> Items.BRAIN_CORAL, 0xFF22E558);

    /** Ce qu'elle apporte a ton organisation par un accord, et plus par une alliance. */
    public enum Specialty {
        CONTREBANDE("Contrebande : +25 % de revenus (+35 % alliee)"),
        RECEL("Recel : +25 % de revenus (+35 % alliee), le receleur paie 5 % de plus"),
        CASINO("Casinos : 25 % de blanchiment en plus, 5 points de commission en moins"),
        RESEAU("Reseaux : ecoulent 25 % de plus (35 % alliee)"),
        ARMES("Armes : vend des caisses a tes trafics, rachete ton surplus"),
        DROGUES("Drogues : vend matiere premiere et produits finis, rachete ton surplus");

        private final String effect;

        Specialty(String effect) {
            this.effect = effect;
        }

        public String effect() {
            return effect;
        }
    }

    private final String label;
    private final Region region;
    private final Specialty specialty;
    private final double aggression;
    private final double greed;
    private final double honor;
    private final Supplier<Item> icon;
    private final int color;

    Syndicate(String label, Region region, Specialty specialty, double aggression, double greed, double honor,
              Supplier<Item> icon, int color) {
        this.label = label;
        this.region = region;
        this.specialty = specialty;
        this.aggression = aggression;
        this.greed = greed;
        this.honor = honor;
        this.icon = icon;
        this.color = color;
    }

    public String label() {
        return label;
    }

    public Region region() {
        return region;
    }

    public Specialty specialty() {
        return specialty;
    }

    public double aggression() {
        return aggression;
    }

    public double greed() {
        return greed;
    }

    public double honor() {
        return honor;
    }

    public ItemStack icon() {
        return new ItemStack(icon.get());
    }

    public int color() {
        return color;
    }

    /** « du Syndicat des Docks », « de la Famille Valcorte ». */
    public String of() {
        if (label.startsWith("Le ")) {
            return "du " + label.substring(3);
        }
        return "de " + label.substring(0, 1).toLowerCase() + label.substring(1);
    }

    /** L'organisation qui tient cette region, ou {@code null}. */
    public static Syndicate in(Region region) {
        if (region == null) {
            return null;
        }
        for (Syndicate syndicate : values()) {
            if (syndicate.region == region) {
                return syndicate;
            }
        }
        return null;
    }

    public static Syndicate of(String name) {
        for (Syndicate syndicate : values()) {
            if (syndicate.name().equals(name)) {
                return syndicate;
            }
        }
        return null;
    }
}

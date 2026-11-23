package fr.atraxterra.dohy.faction;

import fr.atraxterra.dohy.earth.EarthNations;
import net.minecraft.server.level.ServerPlayer;

/**
 * Le rang d'une faction devenue nation : il suit les pays qu'elle tient sur la
 * carte de la Terre, et baisse la taxe du marche international.
 */
public enum NationRank {
    FACTION("Faction", 0, 0.15d, 0xFF9A9AA5),
    CITY("Cite", 0, 0.14d, 0xFFFFFFFF),
    STATE("Etat", 1, 0.12d, 0xFF22E558),
    KINGDOM("Royaume", 5, 0.10d, 0xFF27C4E8),
    EMPIRE("Empire", 15, 0.08d, 0xFFFAC400),
    SUPERPOWER("Superpuissance", 40, 0.06d, 0xFFEF3826);

    private final String label;
    private final int countries;
    private final double fee;
    private final int color;

    NationRank(String label, int countries, double fee, int color) {
        this.label = label;
        this.countries = countries;
        this.fee = fee;
        this.color = color;
    }

    public String label() {
        return label;
    }

    /** Les pays qu'il faut tenir pour ce rang. */
    public int countries() {
        return countries;
    }

    /** La taxe prelevee sur les ventes au marche international. */
    public double fee() {
        return fee;
    }

    /**
     * La taxe que paie ce joueur : celle du rang de sa nation, moins ce qu'effacent ses accords
     * et alliances (lot 24a) et les technologies de l'Economie (lot 23b, un point par palier qui
     * le dit), 2 % au plus bas, puis la part que son grade efface.
     */
    public static double fee(ServerPlayer player) {
        double treaties = fr.atraxterra.dohy.faction.org.Diplomacy.taxCut(player)
                + fr.atraxterra.dohy.faction.tech.TechPerks.of(player).marketTax() / 100.0d;
        double fee = Math.max(Math.min(of(player).fee, fr.atraxterra.dohy.faction.org.Diplomacy.TAX_FLOOR),
                of(player).fee - treaties);
        return fee * (1.0d - fr.atraxterra.dohy.grade.Grades.marketTaxCut(player));
    }

    public int color() {
        return color;
    }

    /**
     * Le rang d'une nation sans carte de la Terre : Faction jusqu'au Gouvernement I, Cite au II,
     * Etat au III, Royaume au IV, Empire au V, Superpuissance au VI.
     */
    public static NationRank byGovernment(int tier) {
        if (tier <= 1) {
            return FACTION;
        }
        return values()[Math.min(values().length - 1, tier - 1)];
    }

    /** Le palier du Gouvernement qui donne ce rang, sans carte de la Terre. */
    public static int governmentFor(NationRank rank) {
        return rank == FACTION ? 0 : rank.ordinal() + 1;
    }

    /** Le rang suivant, ou {@code null} au sommet. */
    public NationRank next() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
    }

    /**
     * Une faction sans capitale reste une faction ; avec, une cite ; ensuite, les pays comptent.
     * Sans carte de la Terre, pas de pays : le rang suit le Gouvernement.
     */
    public static NationRank of(ServerPlayer player) {
        if (!EarthNations.active()) {
            return byGovernment(Nation.government(FactionApi.data(player)));
        }
        int held = EarthNations.heldBy(player).size();
        if (held == 0) {
            return EarthNations.nation(player).capital() != 0 ? CITY : FACTION;
        }
        NationRank rank = STATE;
        for (NationRank candidate : values()) {
            if (candidate.countries > 0 && held >= candidate.countries) {
                rank = candidate;
            }
        }
        return rank;
    }
}

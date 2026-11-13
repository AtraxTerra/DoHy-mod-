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

    public int color() {
        return color;
    }

    /** Le rang suivant, ou {@code null} au sommet. */
    public NationRank next() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
    }

    /** Une faction sans capitale reste une faction ; avec, une cite ; ensuite, les pays comptent. */
    public static NationRank of(ServerPlayer player) {
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

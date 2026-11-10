package fr.atraxterra.dohy.faction;

import net.minecraft.ChatFormatting;

/**
 * Les cinq ligues de Paladium. Sur le serveur, la place se calcule en part des
 * factions actives (5 % en Diamant...) ; seul, il n'y a personne a qui se
 * comparer : la ligue suit l'ELO cumule, par paliers, et retombe d'une marche
 * apres une semaine sans victoire, comme sur Paladium.
 *
 * @param threshold ELO cumule pour y monter
 * @param reward    verse le lundi a la banque de faction, si la semaine compte une victoire
 */
public enum FactionLeague {
    BRONZE("Bronze", 0L, 25_000L, ChatFormatting.GOLD, 0xFFCD7F32),
    ARGENT("Argent", 5_000L, 75_000L, ChatFormatting.GRAY, 0xFFC0C0C8),
    OR("Or", 15_000L, 200_000L, ChatFormatting.YELLOW, 0xFFFAC400),
    PLATINE("Platine", 35_000L, 500_000L, ChatFormatting.AQUA, 0xFF7FE0E0),
    DIAMANT("Diamant", 75_000L, 1_250_000L, ChatFormatting.BLUE, 0xFF4FA8FF);

    private final String label;
    private final long threshold;
    private final long reward;
    private final ChatFormatting format;
    private final int color;

    FactionLeague(String label, long threshold, long reward, ChatFormatting format, int color) {
        this.label = label;
        this.threshold = threshold;
        this.reward = reward;
        this.format = format;
        this.color = color;
    }

    public String label() {
        return label;
    }

    public long threshold() {
        return threshold;
    }

    public long reward() {
        return reward;
    }

    public ChatFormatting format() {
        return format;
    }

    public int color() {
        return color;
    }

    public static FactionLeague of(int index) {
        FactionLeague[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, index))];
    }

    /** La plus haute ligue que cet ELO atteint. */
    public static FactionLeague forElo(long elo) {
        FactionLeague best = BRONZE;
        for (FactionLeague league : values()) {
            if (elo >= league.threshold) {
                best = league;
            }
        }
        return best;
    }

    public FactionLeague next() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
    }
}

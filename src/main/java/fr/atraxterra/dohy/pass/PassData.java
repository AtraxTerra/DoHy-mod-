package fr.atraxterra.dohy.pass;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Le pass du joueur : la saison en cours, son XP, les niveaux dont la
 * recompense est recuperee, et ses quetes (celles du jour, celles du mois).
 * Garde a la mort.
 */
public final class PassData {

    public static final MapCodec<PassData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("season", -1).forGetter(data -> data.season),
            Codec.LONG.optionalFieldOf("xp", 0L).forGetter(data -> data.xp),
            Codec.INT.listOf().optionalFieldOf("claimed", List.of()).forGetter(data -> List.copyOf(data.claimed)),
            Codec.LONG.optionalFieldOf("day", -1L).forGetter(data -> data.day),
            PassQuest.CODEC.listOf().optionalFieldOf("daily", List.of()).forGetter(data -> List.copyOf(data.daily)),
            PassQuest.CODEC.listOf().optionalFieldOf("monthly", List.of())
                    .forGetter(data -> List.copyOf(data.monthly))
    ).apply(instance, PassData::new));

    private int season;
    private long xp;
    private final Set<Integer> claimed;
    private long day;
    private final List<PassQuest> daily;
    private final List<PassQuest> monthly;

    public PassData() {
        this(-1, 0L, List.of(), -1L, List.of(), List.of());
    }

    private PassData(int season, long xp, List<Integer> claimed, long day, List<PassQuest> daily,
                     List<PassQuest> monthly) {
        this.season = season;
        this.xp = xp;
        this.claimed = new TreeSet<>(claimed);
        this.day = day;
        this.daily = new ArrayList<>(daily);
        this.monthly = new ArrayList<>(monthly);
    }

    public boolean isEmpty() {
        return season < 0;
    }

    public int season() {
        return season;
    }

    public long xp() {
        return xp;
    }

    public long day() {
        return day;
    }

    public List<PassQuest> daily() {
        return daily;
    }

    public List<PassQuest> monthly() {
        return monthly;
    }

    public boolean claimed(int level) {
        return claimed.contains(level);
    }

    public Set<Integer> claimedLevels() {
        return claimed;
    }

    /** Le niveau atteint, borne au dernier. */
    public int level(long xpPerLevel, int levels) {
        return (int) Math.min(levels, xp / Math.max(1L, xpPerLevel));
    }

    void addXp(long amount) {
        xp = Math.max(0L, xp + amount);
    }

    void setXp(long amount) {
        xp = Math.max(0L, amount);
    }

    void claim(int level) {
        claimed.add(level);
    }

    /** Une saison commence : XP, recompenses et quetes repartent de zero. */
    void start(int newSeason, List<PassQuest> quests) {
        season = newSeason;
        xp = 0L;
        claimed.clear();
        monthly.clear();
        monthly.addAll(quests);
        day = -1L;
        daily.clear();
    }

    void setDaily(long newDay, List<PassQuest> quests) {
        day = newDay;
        daily.clear();
        daily.addAll(quests);
    }

    void replace(boolean month, int index, PassQuest quest) {
        (month ? monthly : daily).set(index, quest);
    }
}

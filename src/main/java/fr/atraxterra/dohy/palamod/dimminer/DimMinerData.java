package fr.atraxterra.dohy.palamod.dimminer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * La session de dimension du joueur ({@code DimMinerPlayer},
 * {@code MinerDimensionPlayerData}).
 *
 * <p>Une session dure au plus <b>trois jours de temps reel</b> et dispose de
 * <b>1000 points</b>, que seuls trois minerais consomment : paladium 1,
 * paladium vert 5, pepite d'endium 501 — un seul endium par session. Elle
 * s'acheve quand les points tombent a zero ou que les trois jours sont
 * passes ; la dimension s'effondre alors trente secondes plus tard.
 *
 * <p>On garde aussi le portail d'entree (ses blocs de cadre, pour la casse
 * d'un sur trois a la fin, et ou revenir), et de quoi remplir l'ecran de fin.
 */
public final class DimMinerData {

    /** {@code DURATION} : trois jours, en secondes. */
    public static final long DURATION_SECONDS = 259_200L;
    /** {@code TOTAL_POINTS}. */
    public static final long TOTAL_POINTS = 1000L;

    /** Le portail qu'on a traverse ({@code PortalData}), et ou en ressortir. */
    public record Portal(ResourceKey<Level> dimension, List<BlockPos> frame, BlockPos exit) {
        public static final Codec<Portal> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Portal::dimension),
                BlockPos.CODEC.listOf().fieldOf("frame").forGetter(Portal::frame),
                BlockPos.CODEC.fieldOf("exit").forGetter(Portal::exit)
        ).apply(instance, Portal::new));
    }

    public static final MapCodec<DimMinerData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("section", -1).forGetter(data -> data.section),
            Portal.CODEC.optionalFieldOf("portal").forGetter(data -> Optional.ofNullable(data.portal)),
            Codec.LONG.optionalFieldOf("creation", 0L).forGetter(data -> data.creationTime),
            Codec.LONG.optionalFieldOf("points", 0L).forGetter(data -> data.points),
            Codec.BOOL.optionalFieldOf("expired", false).forGetter(data -> data.expired),
            Codec.LONG.optionalFieldOf("time_played", 0L).forGetter(data -> data.timePlayed),
            Codec.LONG.optionalFieldOf("mined", 0L).forGetter(data -> data.minedBlocks),
            Codec.LONG.optionalFieldOf("job_xp", 0L).forGetter(data -> data.jobXpStart),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ores", Map.of())
                    .forGetter(data -> data.minedOres),
            BlockPos.CODEC.optionalFieldOf("arrival").forGetter(data -> Optional.ofNullable(data.arrival)),
            BlockPos.CODEC.optionalFieldOf("last").forGetter(data -> Optional.ofNullable(data.lastPos))
    ).apply(instance, DimMinerData::new));

    private int section;
    private Portal portal;
    private long creationTime;
    private long points;
    private boolean expired;
    private long timePlayed;
    private long minedBlocks;
    private long jobXpStart;
    private final Map<String, Long> minedOres = new LinkedHashMap<>();
    private BlockPos arrival;
    private BlockPos lastPos;

    /** {@code inPortalTimer} : un tick de contact ouvre l'ecran, rester dedans ne le rouvre pas. */
    int inPortal;
    /** Ticks avant l'effondrement, -1 hors effondrement. Pas sauvegarde : il repart a la connexion. */
    int collapse = -1;

    public DimMinerData() {
        this(-1, Optional.empty(), 0L, 0L, false, 0L, 0L, 0L, Map.of(), Optional.empty(), Optional.empty());
    }

    private DimMinerData(int section, Optional<Portal> portal, long creationTime, long points, boolean expired,
                         long timePlayed, long minedBlocks, long jobXpStart, Map<String, Long> ores,
                         Optional<BlockPos> arrival, Optional<BlockPos> lastPos) {
        this.section = section;
        this.portal = portal.orElse(null);
        this.creationTime = creationTime;
        this.points = points;
        this.expired = expired;
        this.timePlayed = timePlayed;
        this.minedBlocks = minedBlocks;
        this.jobXpStart = jobXpStart;
        this.minedOres.putAll(ores);
        this.arrival = arrival.orElse(null);
        this.lastPos = lastPos.orElse(null);
    }

    public static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    public boolean isBlank() {
        return creationTime == 0L && portal == null;
    }

    /** Une session a ete creee et son ecran de fin n'a pas encore ete montre. */
    public boolean hasSession() {
        return creationTime != 0L;
    }

    /** {@code isActive} : moins de trois jours, des points, pas encore effondree. */
    public boolean isActive() {
        return hasSession() && now() - creationTime < DURATION_SECONDS && points > 0L && !expired;
    }

    public long remainingSeconds() {
        return hasSession() ? Math.max(0L, DURATION_SECONDS - (now() - creationTime)) : DURATION_SECONDS;
    }

    /** {@code create} : remise a zero, 1000 points, XP de Mineur de depart notee. */
    public void create(long jobXp, int newSection) {
        Portal kept = portal;
        reset();
        portal = kept;
        creationTime = now();
        points = TOTAL_POINTS;
        jobXpStart = jobXp;
        section = newSection;
    }

    public void reset() {
        section = -1;
        portal = null;
        creationTime = 0L;
        points = 0L;
        expired = false;
        timePlayed = 0L;
        minedBlocks = 0L;
        jobXpStart = 0L;
        minedOres.clear();
        arrival = null;
        lastPos = null;
        collapse = -1;
    }

    public int section() {
        return section;
    }

    public Portal portal() {
        return portal;
    }

    public void setPortal(Portal value) {
        portal = value;
    }

    public long points() {
        return points;
    }

    public void spend(long cost) {
        points = Math.max(0L, points - cost);
    }

    public boolean isExpired() {
        return expired;
    }

    public void expire() {
        expired = true;
    }

    public long timePlayed() {
        return timePlayed;
    }

    public void addMinute() {
        timePlayed++;
    }

    public long minedBlocks() {
        return minedBlocks;
    }

    public long jobXpStart() {
        return jobXpStart;
    }

    public Map<String, Long> minedOres() {
        return minedOres;
    }

    /** {@code breakBlock} : tout bloc compte au total, les minerais suivis aussi a part. */
    public void mined(String ore) {
        minedBlocks++;
        if (ore != null) {
            minedOres.merge(ore, 1L, Long::sum);
        }
    }

    public BlockPos arrival() {
        return arrival;
    }

    public void setArrival(BlockPos pos) {
        arrival = pos;
    }

    public BlockPos lastPos() {
        return lastPos;
    }

    public void setLastPos(BlockPos pos) {
        lastPos = pos;
    }

    public Map<String, Long> oresView() {
        return new HashMap<>(minedOres);
    }
}

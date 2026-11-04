package fr.atraxterra.dohy.boost;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Booster actif, cles de caisse, et coffre de vente automatique.
 *
 * <p>Les echeances sont en <b>minutes de jeu</b>, comme tout le reste : un
 * booster de vingt minutes doit durer vingt minutes de jeu, pas vingt minutes
 * pendant lesquelles le jeu est ferme.
 */
public final class BoostData {

    public static final MapCodec<BoostData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("booster", 1.0d).forGetter(data -> data.booster),
            Codec.LONG.optionalFieldOf("booster_until", -1L).forGetter(data -> data.boosterUntil),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("keys", Map.of())
                    .forGetter(data -> Map.copyOf(data.keys)),
            Codec.BOOL.optionalFieldOf("autosell_unlocked", false).forGetter(data -> data.autosellUnlocked),
            BlockPos.CODEC.optionalFieldOf("autosell_pos").forGetter(data -> Optional.ofNullable(data.autosellPos)),
            Codec.LONG.optionalFieldOf("autosell_last", -1L).forGetter(data -> data.autosellLast)
    ).apply(instance, BoostData::new));

    private double booster = 1.0d;
    private long boosterUntil = -1L;
    private final Map<String, Integer> keys = new HashMap<>();
    private boolean autosellUnlocked;
    private BlockPos autosellPos;
    private long autosellLast = -1L;

    public BoostData() {
    }

    private BoostData(double booster, long boosterUntil, Map<String, Integer> keys,
                      boolean autosellUnlocked, Optional<BlockPos> autosellPos, long autosellLast) {
        this.booster = booster;
        this.boosterUntil = boosterUntil;
        this.keys.putAll(keys);
        this.autosellUnlocked = autosellUnlocked;
        this.autosellPos = autosellPos.orElse(null);
        this.autosellLast = autosellLast;
    }

    /** Multiplicateur actif a cet instant de temps de jeu. */
    public double booster(long nowMinutes) {
        return boosterUntil > nowMinutes ? booster : 1.0d;
    }

    public long boosterUntil() {
        return boosterUntil;
    }

    public void setBooster(double multiplier, long untilMinutes) {
        this.booster = multiplier;
        this.boosterUntil = untilMinutes;
    }

    public int keys(String tier) {
        return keys.getOrDefault(tier, 0);
    }

    public void addKeys(String tier, int amount) {
        keys.merge(tier, amount, Integer::sum);
        keys.values().removeIf(value -> value <= 0);
    }

    public boolean consumeKey(String tier) {
        int available = keys(tier);
        if (available <= 0) {
            return false;
        }
        keys.put(tier, available - 1);
        keys.values().removeIf(value -> value <= 0);
        return true;
    }

    public Map<String, Integer> allKeys() {
        return Map.copyOf(keys);
    }

    public boolean autosellUnlocked() {
        return autosellUnlocked;
    }

    public void unlockAutosell() {
        autosellUnlocked = true;
    }

    public BlockPos autosellPos() {
        return autosellPos;
    }

    public void setAutosellPos(BlockPos pos) {
        autosellPos = pos;
    }

    public long autosellLast() {
        return autosellLast;
    }

    public void markAutosell(long minutes) {
        autosellLast = minutes;
    }

    public boolean isEmpty() {
        return booster <= 1.0d && boosterUntil < 0 && keys.isEmpty()
                && !autosellUnlocked && autosellPos == null;
    }
}

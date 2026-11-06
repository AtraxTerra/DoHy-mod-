package fr.atraxterra.dohy.upgrade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Les niveaux d'amelioration atteints.
 *
 * <p>Une map d'entiers, et rien d'autre : un achat est definitif, donc il n'y a
 * ni duree, ni charge, ni date a conserver. C'est ce qui rend ces ameliorations
 * differentes de tous les autres puits a argent du mod.
 */
public final class UpgradeData {

    public static final MapCodec<UpgradeData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("levels", Map.of())
                    .forGetter(data -> Map.copyOf(data.levels))
    ).apply(instance, UpgradeData::new));

    private final Map<String, Integer> levels;

    public UpgradeData() {
        this.levels = new HashMap<>();
    }

    private UpgradeData(Map<String, Integer> levels) {
        this.levels = new HashMap<>(levels);
    }

    public int levelOf(String id) {
        return levels.getOrDefault(id, 0);
    }

    public void set(String id, int level) {
        if (level <= 0) {
            levels.remove(id);
        } else {
            levels.put(id, level);
        }
    }

    public int total() {
        return levels.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean isEmpty() {
        return levels.isEmpty();
    }
}

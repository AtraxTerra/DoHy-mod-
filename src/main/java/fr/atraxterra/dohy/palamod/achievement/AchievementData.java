package fr.atraxterra.dohy.palamod.achievement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les succes d'un joueur : ses compteurs propres (ce que le jeu ne compte pas
 * lui-meme) et les succes deja valides, garde sur lui.
 *
 * <p>Les compteurs se modifient sur place, comme les statistiques de faction :
 * la piece jointe est relue a chaque sauvegarde du joueur.
 */
public final class AchievementData {

    public static final MapCodec<AchievementData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("counters", Map.of())
                    .forGetter(data -> Map.copyOf(data.counters)),
            Codec.STRING.listOf().optionalFieldOf("done", List.of()).forGetter(data -> List.copyOf(data.done))
    ).apply(instance, AchievementData::new));

    final Map<String, Long> counters;
    final Set<String> done;

    public AchievementData() {
        this(Map.of(), List.of());
    }

    private AchievementData(Map<String, Long> counters, List<String> done) {
        this.counters = new HashMap<>(counters);
        this.done = new HashSet<>(done);
    }

    public boolean isEmpty() {
        return counters.isEmpty() && done.isEmpty();
    }

    public long counter(String key) {
        return counters.getOrDefault(key, 0L);
    }

    void add(String key, long amount) {
        counters.merge(key, amount, Long::sum);
    }

    /** Garde le plus grand : pour ce qui se constate (« avoir eu 10 graines en main »). */
    void atLeast(String key, long value) {
        if (value > counter(key)) {
            counters.put(key, value);
        }
    }

    public boolean isDone(String id) {
        return done.contains(id);
    }

    public int doneCount() {
        return done.size();
    }
}

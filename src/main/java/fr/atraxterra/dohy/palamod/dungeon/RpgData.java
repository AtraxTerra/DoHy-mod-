package fr.atraxterra.dohy.palamod.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Le profil RPG d'un joueur : son XP RPG (le niveau en derive) et les noeuds
 * valides de chaque branche. Les points ne sont pas stockes : ils se
 * recalculent depuis le niveau RPG, les paliers et les metiers.
 */
public record RpgData(long xp, Map<String, Integer> nodes) {

    public static final RpgData EMPTY = new RpgData(0L, Map.of());

    public static final MapCodec<RpgData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.LONG.optionalFieldOf("xp", 0L).forGetter(RpgData::xp),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("nodes", Map.of()).forGetter(RpgData::nodes)
    ).apply(instance, RpgData::new));

    public boolean isBlank() {
        return xp == 0L && nodes.values().stream().allMatch(value -> value == 0);
    }

    public int nodes(RpgStat stat) {
        return nodes.getOrDefault(stat.key(), 0);
    }

    public RpgData withXp(long value) {
        return new RpgData(value, nodes);
    }

    public RpgData withNodes(Map<RpgStat, Integer> values) {
        Map<String, Integer> next = new HashMap<>();
        values.forEach((stat, count) -> {
            if (count > 0) {
                next.put(stat.key(), Math.min(RpgStat.NODES, count));
            }
        });
        return new RpgData(xp, Map.copyOf(next));
    }

    /** Les points depenses dans l'arbre. */
    public int spent() {
        int total = 0;
        for (RpgStat stat : RpgStat.values()) {
            total += RpgStat.costOf(nodes(stat));
        }
        return total;
    }
}

package fr.atraxterra.dohy.palamod.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Le profil RPG d'un joueur : son XP RPG (le niveau en derive) et les noeuds pris dans chaque
 * branche ({@link RpgTree}, un masque par branche). Les points ne sont pas stockes : ils se
 * recalculent depuis le niveau RPG, les paliers et les metiers.
 *
 * @param nodes l'ancien arbre (huit noeuds en ligne par branche) : lu pour rendre ses points
 *              ({@link Rpg#migrate}), jamais ecrit
 * @param tree  les noeuds pris, par branche
 */
public record RpgData(long xp, Map<String, Integer> nodes, Map<String, Integer> tree) {

    public static final RpgData EMPTY = new RpgData(0L, Map.of(), Map.of());

    public static final MapCodec<RpgData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.LONG.optionalFieldOf("xp", 0L).forGetter(RpgData::xp),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("nodes", Map.of()).forGetter(RpgData::nodes),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("tree", Map.of()).forGetter(RpgData::tree)
    ).apply(instance, RpgData::new));

    public boolean isBlank() {
        return xp == 0L && !legacy() && tree.values().stream().allMatch(value -> value == 0);
    }

    /** Des noeuds de l'ancien arbre, pas encore rendus. */
    public boolean legacy() {
        return nodes.values().stream().anyMatch(value -> value > 0);
    }

    /** Les noeuds pris dans cette branche. */
    public int mask(RpgStat stat) {
        return RpgTree.sane(tree.getOrDefault(stat.key(), 0) & RpgTree.ALL);
    }

    public RpgData withXp(long value) {
        return new RpgData(value, nodes, tree);
    }

    /** L'arbre remplace ; l'ancien est oublie. */
    public RpgData withTree(Map<RpgStat, Integer> masks) {
        Map<String, Integer> next = new HashMap<>();
        masks.forEach((stat, mask) -> {
            int clean = RpgTree.sane(mask & RpgTree.ALL);
            if (clean != 0) {
                next.put(stat.key(), clean);
            }
        });
        return new RpgData(xp, Map.of(), Map.copyOf(next));
    }

    /** Les points depenses dans l'arbre. */
    public int spent() {
        int total = 0;
        for (RpgStat stat : RpgStat.values()) {
            total += RpgTree.price(mask(stat));
        }
        return total;
    }
}

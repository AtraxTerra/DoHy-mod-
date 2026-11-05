package fr.atraxterra.dohy.collection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Ce que le joueur a collecte, item par item.
 *
 * <p>Compteur cumulatif et jamais decroissant : contrairement a la pression du
 * marche, une collection est un acquis. C'est ce qui en fait le bon support
 * pour les deblocages — un palier franchi ne se reperd pas.
 */
public final class CollectionData {

    public static final MapCodec<CollectionData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Identifier.CODEC, Codec.LONG)
                    .optionalFieldOf("collected", Map.of())
                    .forGetter(data -> Map.copyOf(data.collected)),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT)
                    .optionalFieldOf("prestige", Map.of())
                    .forGetter(data -> Map.copyOf(data.prestige))
    ).apply(instance, CollectionData::new));

    private final Map<Identifier, Long> collected;
    private final Map<Identifier, Integer> prestige;

    public CollectionData() {
        this.collected = new HashMap<>();
        this.prestige = new HashMap<>();
    }

    private CollectionData(Map<Identifier, Long> collected, Map<Identifier, Integer> prestige) {
        this.collected = new HashMap<>(collected);
        this.prestige = new HashMap<>(prestige);
    }

    public int prestigeOf(Identifier id) {
        return prestige.getOrDefault(id, 0);
    }

    /**
     * Passe un prestige : le compteur repart a zero, le niveau monte.
     *
     * <p>Le compteur est remis a zero et non conserve : c'est le cout du
     * prestige, et c'est ce qui rend l'arbitrage reel plutot que gratuit.
     */
    public int addPrestige(Identifier id) {
        int next = prestigeOf(id) + 1;
        prestige.put(id, next);
        collected.put(id, 0L);
        return next;
    }

    public long countOf(Identifier id) {
        return collected.getOrDefault(id, 0L);
    }

    /** Ajoute au compteur et retourne le nouveau total. Sature au lieu de deborder. */
    public long add(Identifier id, int quantity) {
        if (quantity <= 0) {
            return countOf(id);
        }
        long current = countOf(id);
        long next = current + quantity;
        if (next < current) {
            next = Long.MAX_VALUE;
        }
        collected.put(id, next);
        return next;
    }

    public int trackedItems() {
        return collected.size();
    }

    public boolean isEmpty() {
        return collected.isEmpty() && prestige.isEmpty();
    }

    public Map<Identifier, Integer> prestigeSnapshot() {
        return Map.copyOf(prestige);
    }

    public Map<Identifier, Long> snapshot() {
        return Map.copyOf(collected);
    }
}

package fr.atraxterra.dohy.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.List;

/**
 * Positions posees par un joueur dans un chunk.
 *
 * <p>Mutable, contrairement a {@code PlayerEconomy} : un ensemble de positions
 * est modifie des milliers de fois par session de construction, et recreer
 * l'objet a chaque bloc pose serait du gaspillage pur. En echange, toute
 * modification doit marquer le chunk comme non sauvegarde — c'est le role de
 * {@link PlacedBlocks}, qui est le seul a appeler les mutateurs d'ici.
 *
 * <p>Les positions sont stockees compactees en {@code long} ({@code BlockPos.asLong}),
 * dans un ensemble fastutil pour eviter l'autoboxing de plusieurs milliers de
 * {@code Long}.
 */
public final class PlacedBlockData {

    public static final MapCodec<PlacedBlockData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.LONG.listOf().optionalFieldOf("positions", List.of()).forGetter(PlacedBlockData::toList)
    ).apply(instance, PlacedBlockData::fromList));

    private final LongSet positions;

    public PlacedBlockData() {
        this.positions = new LongOpenHashSet();
    }

    private PlacedBlockData(LongSet positions) {
        this.positions = positions;
    }

    private static PlacedBlockData fromList(List<Long> values) {
        LongSet set = new LongOpenHashSet(values.size());
        for (long value : values) {
            set.add(value);
        }
        return new PlacedBlockData(set);
    }

    private List<Long> toList() {
        return new LongArrayList(positions);
    }

    boolean add(long packedPos) {
        return positions.add(packedPos);
    }

    boolean remove(long packedPos) {
        return positions.remove(packedPos);
    }

    boolean contains(long packedPos) {
        return positions.contains(packedPos);
    }

    public int size() {
        return positions.size();
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }
}

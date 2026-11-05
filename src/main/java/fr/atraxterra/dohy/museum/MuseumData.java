package fr.atraxterra.dohy.museum;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ce qui a ete donne au musee.
 *
 * <p>Un ensemble et non un compteur : le musee ne demande qu'un exemplaire de
 * chaque item, et un don est definitif. Il n'y a donc rien a cumuler.
 */
public final class MuseumData {

    public static final MapCodec<MuseumData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.listOf().optionalFieldOf("donated", List.of())
                    .forGetter(data -> List.copyOf(data.donated))
    ).apply(instance, MuseumData::new));

    private final Set<Identifier> donated;

    public MuseumData() {
        this.donated = new HashSet<>();
    }

    private MuseumData(List<Identifier> donated) {
        this.donated = new HashSet<>(donated);
    }

    public boolean has(Identifier id) {
        return donated.contains(id);
    }

    /** Enregistre un don. Retourne false s'il etait deja fait. */
    public boolean add(Identifier id) {
        return donated.add(id);
    }

    public int count() {
        return donated.size();
    }

    public Set<Identifier> all() {
        return Set.copyOf(donated);
    }

    public boolean isEmpty() {
        return donated.isEmpty();
    }
}

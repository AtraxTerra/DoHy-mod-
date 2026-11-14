package fr.atraxterra.dohy.guide;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Ce que le joueur a fait de l'encyclopedie : les fiches decouvertes, celles
 * qu'il a lues, et s'il a deja ete accueilli. Gardee a la mort.
 */
public final class EncyclopediaData {

    public static final MapCodec<EncyclopediaData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("seen", List.of()).forGetter(data -> List.copyOf(data.seen)),
            Codec.STRING.listOf().optionalFieldOf("read", List.of()).forGetter(data -> List.copyOf(data.read)),
            Codec.BOOL.optionalFieldOf("welcomed", false).forGetter(data -> data.welcomed)
    ).apply(instance, EncyclopediaData::new));

    private final Set<String> seen;
    private final Set<String> read;
    private boolean welcomed;

    public EncyclopediaData() {
        this(List.of(), List.of(), false);
    }

    private EncyclopediaData(List<String> seen, List<String> read, boolean welcomed) {
        this.seen = new LinkedHashSet<>(seen);
        this.read = new LinkedHashSet<>(read);
        this.welcomed = welcomed;
    }

    public boolean isEmpty() {
        return seen.isEmpty() && read.isEmpty() && !welcomed;
    }

    public boolean seen(String id) {
        return seen.contains(id);
    }

    public boolean read(String id) {
        return read.contains(id);
    }

    /** Une fiche nouvelle : decouverte, pas encore lue. */
    public boolean fresh(String id) {
        return seen.contains(id) && !read.contains(id);
    }

    /** Marque une fiche decouverte ; vrai si elle ne l'etait pas. */
    public boolean discover(String id) {
        return seen.add(id);
    }

    public void markRead(String id) {
        seen.add(id);
        read.add(id);
    }

    public int readCount() {
        return read.size();
    }

    public List<String> freshIds() {
        List<String> out = new ArrayList<>();
        for (String id : seen) {
            if (!read.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    public boolean welcomed() {
        return welcomed;
    }

    public void welcome() {
        welcomed = true;
    }
}

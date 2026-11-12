package fr.atraxterra.dohy.agri;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le carnet de recherche d'un joueur : ce qu'il a compose, ce qu'il a deja
 * fabrique, les recettes qu'il connait et les indices qu'il a recus.
 *
 * <p>La recette elle-meme n'est pas ici : elle se recalcule a la demande
 * ({@link GmoResearch#recipe}). Ne sont gardes que les faits qui ne se
 * recalculent pas.
 */
public final class AgriData {

    /** Indices gardes par OGM : le carnet ne montre que les derniers. */
    static final int MAX_NOTES = 6;

    /** Separe le verdict et les indices dans une note ; absent d'un texte encode. */
    static final String NOTE_SEPARATOR = "\n";

    public static final MapCodec<AgriData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("target", "").forGetter(data -> data.target),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("crafted", Map.of())
                    .forGetter(data -> Map.copyOf(data.crafted)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("known", Map.of())
                    .forGetter(data -> Map.copyOf(data.known)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("attempts", Map.of())
                    .forGetter(data -> Map.copyOf(data.attempts)),
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("notes", Map.of())
                    .forGetter(data -> Map.copyOf(data.notes)),
            Entry.CODEC.listOf().optionalFieldOf("composition", List.of()).forGetter(AgriData::entries)
    ).apply(instance, AgriData::new));

    /** Une ligne de la composition, pour que l'ordre survive a la sauvegarde. */
    private record Entry(Identifier item, int quantity) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.INT.fieldOf("quantity").forGetter(Entry::quantity)
        ).apply(instance, Entry::new));
    }

    private String target;
    private final Map<String, Integer> crafted;
    private final Map<String, Integer> known;
    private final Map<String, Integer> attempts;
    private final Map<String, List<String>> notes;
    private final Map<Identifier, Integer> composition;

    public AgriData() {
        this("", Map.of(), Map.of(), Map.of(), Map.of(), List.of());
    }

    private AgriData(String target, Map<String, Integer> crafted, Map<String, Integer> known,
                     Map<String, Integer> attempts, Map<String, List<String>> notes, List<Entry> composition) {
        this.target = target;
        this.crafted = new HashMap<>(crafted);
        this.known = new HashMap<>(known);
        this.attempts = new HashMap<>(attempts);
        this.notes = new HashMap<>();
        notes.forEach((key, lines) -> this.notes.put(key, new ArrayList<>(lines)));
        this.composition = new LinkedHashMap<>();
        for (Entry entry : composition) {
            if (entry.quantity() > 0) {
                this.composition.put(entry.item(), entry.quantity());
            }
        }
    }

    private List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        composition.forEach((item, quantity) -> list.add(new Entry(item, quantity)));
        return list;
    }

    public boolean isEmpty() {
        return target.isEmpty() && crafted.isEmpty() && known.isEmpty() && attempts.isEmpty() && notes.isEmpty()
                && composition.isEmpty();
    }

    // -- La cible et la composition --------------------------------------------------

    /** L'OGM vise, par sa cle ; vide tant que rien n'est choisi. */
    public String target() {
        return target;
    }

    public void target(String key) {
        target = key;
    }

    /** La composition en cours, dans l'ordre ou elle a ete posee. Vue modifiable. */
    public Map<Identifier, Integer> composition() {
        return composition;
    }

    // -- Ce qui a ete fabrique ---------------------------------------------------------

    /** Graines de cet OGM fabriquees depuis toujours. */
    public int crafted(String key) {
        return crafted.getOrDefault(key, 0);
    }

    void addCrafted(String key, int seeds) {
        crafted.merge(key, seeds, Integer::sum);
    }

    /** Essais rates et reussis sur cet OGM. */
    public int attempts(String key) {
        return attempts.getOrDefault(key, 0);
    }

    int countAttempt(String key) {
        return attempts.merge(key, 1, Integer::sum);
    }

    /** Vrai si le joueur connait la recette de cette generation. */
    public boolean knows(String key, int generation) {
        return known.getOrDefault(key, -1) == generation;
    }

    void learn(String key, int generation) {
        known.put(key, generation);
    }

    // -- Le carnet ---------------------------------------------------------------------

    /** Les derniers indices recus pour cet OGM, du plus recent au plus ancien. */
    public List<String> notes(String key) {
        return notes.getOrDefault(key, List.of());
    }

    void note(String key, String line) {
        List<String> lines = notes.computeIfAbsent(key, ignored -> new ArrayList<>());
        lines.add(0, line);
        while (lines.size() > MAX_NOTES) {
            lines.remove(lines.size() - 1);
        }
    }

    void forgetNotes(String key) {
        notes.remove(key);
    }
}

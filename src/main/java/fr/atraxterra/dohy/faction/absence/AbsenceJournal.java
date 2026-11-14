package fr.atraxterra.dohy.faction.absence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.faction.Factions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Le journal du monde : ce qui s'est passe pendant que personne ne regardait.
 *
 * <p>Les nations qui achetent et vendent au marche international, les
 * catastrophes lointaines, les terrains qui reviennent, les bases ennemies qui
 * s'effacent. Chaque systeme y note sa ligne au moment ou elle arrive ; le
 * rapport d'absence relit celles d'apres le depart du joueur. Le journal garde
 * ses {@link #KEPT} dernieres lignes, et rien de plus vieux que trente jours.
 */
public final class AbsenceJournal {

    public static final String NATIONS = "nations";
    public static final String WORLD = "monde";

    private static final int KEPT = 400;
    private static final long MAX_AGE = 30L * 86_400_000L;

    /** Une ligne : quand, de quelle famille, le texte, et l'objet de son icone. */
    public record Entry(long time, String kind, String text, Identifier icon) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("time").forGetter(Entry::time),
                Codec.STRING.fieldOf("kind").forGetter(Entry::kind),
                Codec.STRING.fieldOf("text").forGetter(Entry::text),
                Identifier.CODEC.optionalFieldOf("icon", Identifier.withDefaultNamespace("paper"))
                        .forGetter(Entry::icon)
        ).apply(instance, Entry::new));

        public Item item() {
            return BuiltInRegistries.ITEM.getOptional(icon).orElse(net.minecraft.world.item.Items.PAPER);
        }
    }

    public record State(List<Entry> entries) {
        public static final State EMPTY = new State(List.of());
        public static final MapCodec<State> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(State::entries)
        ).apply(instance, State::new));

        public boolean exists() {
            return !entries.isEmpty();
        }
    }

    private AbsenceJournal() {
    }

    /** Note une ligne au journal, avec l'objet de son icone. */
    public static void post(MinecraftServer server, String kind, String text, Item icon) {
        if (server == null || server.overworld() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Entry> entries = new ArrayList<>(server.overworld().getData(Factions.ABSENCE_JOURNAL.get()).entries());
        entries.add(0, new Entry(now, kind, text, BuiltInRegistries.ITEM.getKey(icon)));
        while (entries.size() > KEPT || !entries.isEmpty() && now - entries.get(entries.size() - 1).time() > MAX_AGE) {
            entries.remove(entries.size() - 1);
        }
        server.overworld().setData(Factions.ABSENCE_JOURNAL.get(), new State(List.copyOf(entries)));
    }

    /** Les lignes de cette famille notees depuis {@code since}, de la plus recente a la plus ancienne. */
    public static List<Entry> since(MinecraftServer server, String kind, long since) {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : server.overworld().getData(Factions.ABSENCE_JOURNAL.get()).entries()) {
            if (entry.time() <= since) {
                break;
            }
            if (entry.kind().equals(kind)) {
                out.add(entry);
            }
        }
        return out;
    }
}

package fr.atraxterra.dohy.casino;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La loterie du spawn, gardee sur l'overworld : le prochain tirage, la cagnotte, les tickets des
 * joueurs et des joueurs fictifs, les gains d'un absent, et les cinq derniers tirages.
 */
public final class LotteryData {

    /** Un tirage passe. */
    public record Draw(long time, String winner, long pot, int tickets, boolean player) {
        static final Codec<Draw> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("time").forGetter(Draw::time),
                Codec.STRING.optionalFieldOf("winner", "").forGetter(Draw::winner),
                Codec.LONG.fieldOf("pot").forGetter(Draw::pot),
                Codec.INT.optionalFieldOf("tickets", 0).forGetter(Draw::tickets),
                Codec.BOOL.optionalFieldOf("player", false).forGetter(Draw::player)
        ).apply(instance, Draw::new));
    }

    public static final MapCodec<LotteryData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.LONG.optionalFieldOf("draw", 0L).forGetter(data -> data.draw),
            Codec.LONG.optionalFieldOf("pot", 0L).forGetter(data -> data.pot),
            Codec.INT.optionalFieldOf("fictional", 0).forGetter(data -> data.fictional),
            Codec.INT.optionalFieldOf("candidates", 0).forGetter(data -> data.candidates),
            Codec.LONG.optionalFieldOf("last_sale", 0L).forGetter(data -> data.lastSale),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).optionalFieldOf("tickets", Map.of())
                    .forGetter(data -> data.tickets),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).optionalFieldOf("names", Map.of())
                    .forGetter(data -> data.names),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("owed", Map.of())
                    .forGetter(data -> data.owed),
            Draw.CODEC.listOf().optionalFieldOf("history", List.of()).forGetter(data -> data.history)
    ).apply(instance, LotteryData::new));

    /** Le prochain tirage, en secondes depuis l'epoque (0 : pas encore ouvert). */
    long draw;
    long pot;
    /** Les tickets des joueurs fictifs. */
    int fictional;
    int candidates;
    /** La derniere heure de vente aux joueurs fictifs. */
    long lastSale;
    final Map<UUID, Integer> tickets;
    final Map<UUID, String> names;
    /** Les gains d'un joueur absent au tirage, payes a sa connexion. */
    final Map<UUID, Long> owed;
    final List<Draw> history;

    public LotteryData() {
        this(0L, 0L, 0, 0, 0L, Map.of(), Map.of(), Map.of(), List.of());
    }

    private LotteryData(long draw, long pot, int fictional, int candidates, long lastSale, Map<UUID, Integer> tickets,
                        Map<UUID, String> names, Map<UUID, Long> owed, List<Draw> history) {
        this.draw = draw;
        this.pot = pot;
        this.fictional = fictional;
        this.candidates = candidates;
        this.lastSale = lastSale;
        this.tickets = new LinkedHashMap<>(tickets);
        this.names = new HashMap<>(names);
        this.owed = new HashMap<>(owed);
        this.history = new ArrayList<>(history);
    }

    boolean isEmpty() {
        return draw == 0L && history.isEmpty() && owed.isEmpty();
    }

    /** Le meme etat sous un autre objet : l'overworld voit un changement et l'ecrit. */
    LotteryData touched() {
        return new LotteryData(draw, pot, fictional, candidates, lastSale, tickets, names, owed, history);
    }

    public long draw() {
        return draw;
    }

    public long pot() {
        return pot;
    }

    public int candidates() {
        return candidates;
    }

    /** Tous les tickets du tirage, joueurs fictifs compris. */
    public int sold() {
        int total = fictional;
        for (int count : tickets.values()) {
            total += count;
        }
        return total;
    }

    public int tickets(UUID player) {
        return tickets.getOrDefault(player, 0);
    }

    public List<Draw> history() {
        return history;
    }
}

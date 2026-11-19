package fr.atraxterra.dohy.palamod.lucky;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Les lucky blocks d'un joueur : ce qu'il a deja vu sortir, et son Lucky Pass.
 *
 * <p>Les evenements vus font le « NEW » de l'ecran d'ouverture (les Lucky Stats
 * de Paladium, ici reduites a leur compte). Le Lucky Pass ({@code PlayerLuckyPassProperties})
 * garde le jour du dernier lancer et ce qu'il a donne.
 */
public final class LuckyData {

    public static final MapCodec<LuckyData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("seen", Map.of())
                    .forGetter(data -> Map.copyOf(data.seen)),
            Codec.INT.optionalFieldOf("opened", 0).forGetter(data -> data.opened),
            Codec.LONG.optionalFieldOf("pass_day", 0L).forGetter(data -> data.passDay),
            Codec.INT.listOf().optionalFieldOf("pass_wins", List.of()).forGetter(data -> List.copyOf(data.passWins)),
            Codec.INT.optionalFieldOf("pass_spins", 0).forGetter(data -> data.passSpins),
            Codec.INT.optionalFieldOf("pass_bonus", 0).forGetter(data -> data.passBonus)
    ).apply(instance, LuckyData::new));

    /** Combien de fois chaque evenement est sorti. */
    private final Map<String, Integer> seen = new HashMap<>();
    /** Les lucky blocks ouverts. */
    private int opened;
    /** La cle du jour du dernier lancer du Lucky Pass ({@code ContractApi.today}), 0 jamais. */
    private long passDay;
    /** Ce que ce lancer a donne : trois cases, -1 vide ou le numero du type. */
    private final List<Integer> passWins = new ArrayList<>();
    /** Les lancers du Lucky Pass, depuis le debut. */
    private int passSpins;
    /**
     * Les lancers offerts en plus de celui du jour ({@code setHasLuckyEventBypassNow}) :
     * la Roulette et la Boule de Crystal.
     */
    private int passBonus;

    public LuckyData() {
    }

    private LuckyData(Map<String, Integer> seen, int opened, long passDay, List<Integer> passWins, int passSpins,
                      int passBonus) {
        this.seen.putAll(seen);
        this.opened = opened;
        this.passDay = passDay;
        this.passWins.addAll(passWins);
        this.passSpins = passSpins;
        this.passBonus = passBonus;
    }

    public boolean isEmpty() {
        return seen.isEmpty() && opened == 0 && passDay == 0L && passSpins == 0 && passBonus == 0;
    }

    public boolean seen(String event) {
        return seen.getOrDefault(event, 0) > 0;
    }

    public int times(String event) {
        return seen.getOrDefault(event, 0);
    }

    /** Les evenements portes deja sortis au moins une fois. */
    public int discovered() {
        int count = 0;
        for (String id : seen.keySet()) {
            if (LuckyEvents.get(id) != null) {
                count++;
            }
        }
        return count;
    }

    public int opened() {
        return opened;
    }

    void record(String event) {
        seen.merge(event, 1, Integer::sum);
        opened++;
    }

    public long passDay() {
        return passDay;
    }

    public List<Integer> passWins() {
        return List.copyOf(passWins);
    }

    public int passSpins() {
        return passSpins;
    }

    void spin(long day, List<Integer> wins) {
        passDay = day;
        passWins.clear();
        passWins.addAll(wins);
        passSpins++;
    }

    public int passBonus() {
        return passBonus;
    }

    /** Un lancer offert de plus. */
    void giveBonus() {
        passBonus++;
    }

    /** Un lancer offert, le lancer du jour deja fait : le jour ne change pas. */
    void bonusSpin(List<Integer> wins) {
        passBonus = Math.max(0, passBonus - 1);
        passWins.clear();
        passWins.addAll(wins);
        passSpins++;
    }
}

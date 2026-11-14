package fr.atraxterra.dohy.jobboss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.job.Job;

import java.util.HashMap;
import java.util.Map;

/**
 * Les gardiens d'un joueur, metier par metier : le niveau du prochain combat,
 * ce qui est deja depose sur l'autel, l'heure d'arrivee une fois l'offrande
 * complete, les annonces deja faites, et les victoires. Garde a la mort.
 */
public final class JobBossData {

    /** Un gardien : niveau, depot en cours, arrivee (millisecondes, 0 : pas appele), annonces, victoires. */
    public static final class Guardian {

        static final Codec<Guardian> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("level", 1).forGetter(guardian -> guardian.level),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("deposited", Map.of())
                        .forGetter(guardian -> Map.copyOf(guardian.deposited)),
                Codec.LONG.optionalFieldOf("arrival", 0L).forGetter(guardian -> guardian.arrival),
                Codec.INT.optionalFieldOf("notices", 0).forGetter(guardian -> guardian.notices),
                Codec.INT.optionalFieldOf("kills", 0).forGetter(guardian -> guardian.kills)
        ).apply(instance, Guardian::new));

        /** L'annonce « dans dix minutes » est faite. */
        static final int WARNED = 1;
        /** L'annonce « il est arrive » est faite. */
        static final int ANNOUNCED = 2;

        int level;
        final Map<String, Integer> deposited;
        long arrival;
        int notices;
        int kills;

        Guardian() {
            this(1, Map.of(), 0L, 0, 0);
        }

        private Guardian(int level, Map<String, Integer> deposited, long arrival, int notices, int kills) {
            this.level = Math.max(1, level);
            this.deposited = new HashMap<>(deposited);
            this.arrival = arrival;
            this.notices = notices;
            this.kills = kills;
        }

        public int level() {
            return level;
        }

        public int deposited(String item) {
            return deposited.getOrDefault(item, 0);
        }

        public long arrival() {
            return arrival;
        }

        public int kills() {
            return kills;
        }

        boolean blank() {
            return level <= 1 && deposited.isEmpty() && arrival == 0L && kills == 0;
        }
    }

    public static final MapCodec<JobBossData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Guardian.CODEC).optionalFieldOf("guardians", Map.of())
                    .forGetter(data -> Map.copyOf(data.guardians))
    ).apply(instance, JobBossData::new));

    private final Map<String, Guardian> guardians = new HashMap<>();

    public JobBossData() {
    }

    private JobBossData(Map<String, Guardian> guardians) {
        this.guardians.putAll(guardians);
    }

    public boolean isEmpty() {
        return guardians.values().stream().allMatch(Guardian::blank);
    }

    /** Le gardien d'un metier ; un gardien vierge est cree a la demande. */
    public Guardian guardian(Job job) {
        return guardians.computeIfAbsent(job.key(), key -> new Guardian());
    }

    public int totalKills() {
        int total = 0;
        for (Guardian guardian : guardians.values()) {
            total += guardian.kills;
        }
        return total;
    }
}

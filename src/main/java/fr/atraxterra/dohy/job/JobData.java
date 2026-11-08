package fr.atraxterra.dohy.job;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * XP cumulee par metier.
 *
 * <p>On stocke l'XP totale et non le niveau : le niveau s'en deduit, si bien
 * qu'un reequilibrage de la courbe recalcule les niveaux de tout le monde sans
 * migration de donnees.
 *
 * <p>Le codec lit aussi les cles des anciens skills ({@code mining},
 * {@code combat}, {@code farming}, {@code alchemy}) : c'est ce qui permet de
 * relire l'ancienne piece jointe {@code dohy:skills} avec la meme classe, puis
 * de la verser dans la nouvelle (voir {@link JobApi#data}). Il n'ecrit que les
 * cles de metier.
 */
public final class JobData {

    public static final MapCodec<JobData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.LONG)
                    .optionalFieldOf("xp", Map.of())
                    .forGetter(JobData::asMap)
    ).apply(instance, JobData::fromMap));

    private final EnumMap<Job, Long> xp = new EnumMap<>(Job.class);

    public JobData() {
    }

    private static JobData fromMap(Map<String, Long> raw) {
        JobData data = new JobData();
        raw.forEach((key, value) -> {
            Job job = Job.byKeyOrLegacy(key);
            if (job != null && value != null && value > 0) {
                // Une cle actuelle et son ancienne cle ne coexistent jamais dans
                // une meme sauvegarde ; si c'etait le cas, on garde la plus haute
                // plutot que d'additionner deux fois la meme progression.
                data.xp.merge(job, value, Math::max);
            }
        });
        return data;
    }

    private Map<String, Long> asMap() {
        Map<String, Long> raw = new HashMap<>();
        xp.forEach((job, value) -> raw.put(job.key(), value));
        return raw;
    }

    public long xpOf(Job job) {
        return xp.getOrDefault(job, 0L);
    }

    /** Ajoute de l'XP et retourne le nouveau total. Sature au lieu de deborder. */
    public long addXp(Job job, long amount) {
        if (amount <= 0) {
            return xpOf(job);
        }
        long current = xpOf(job);
        long next = current + amount;
        if (next < current) {
            next = Long.MAX_VALUE;
        }
        xp.put(job, next);
        return next;
    }

    /** Reprend la progression d'une ancienne sauvegarde, sans jamais la baisser. */
    void absorb(JobData legacy) {
        legacy.xp.forEach((job, value) -> xp.merge(job, value, Math::max));
    }

    public boolean isEmpty() {
        return xp.isEmpty();
    }
}

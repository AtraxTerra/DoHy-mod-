package fr.atraxterra.dohy.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * XP cumulee par skill.
 *
 * <p>On stocke l'XP totale et non le niveau : le niveau s'en deduit, si bien
 * qu'un rééquilibrage de la courbe recalcule les niveaux de tout le monde sans
 * migration de donnees.
 */
public final class SkillData {

    public static final MapCodec<SkillData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.LONG)
                    .optionalFieldOf("xp", Map.of())
                    .forGetter(SkillData::asMap)
    ).apply(instance, SkillData::fromMap));

    private final EnumMap<Skill, Long> xp = new EnumMap<>(Skill.class);

    public SkillData() {
    }

    private static SkillData fromMap(Map<String, Long> raw) {
        SkillData data = new SkillData();
        raw.forEach((key, value) -> {
            Skill skill = Skill.byKey(key);
            if (skill != null) {
                data.xp.put(skill, value);
            }
        });
        return data;
    }

    private Map<String, Long> asMap() {
        Map<String, Long> raw = new HashMap<>();
        xp.forEach((skill, value) -> raw.put(skill.key(), value));
        return raw;
    }

    public long xpOf(Skill skill) {
        return xp.getOrDefault(skill, 0L);
    }

    /** Ajoute de l'XP et retourne le nouveau total. Sature au lieu de deborder. */
    public long addXp(Skill skill, long amount) {
        if (amount <= 0) {
            return xpOf(skill);
        }
        long current = xpOf(skill);
        long next = current + amount;
        if (next < current) {
            next = Long.MAX_VALUE;
        }
        xp.put(skill, next);
        return next;
    }

    public boolean isEmpty() {
        return xp.isEmpty();
    }
}

package fr.atraxterra.dohy.grade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.palamod.dungeon.ReturnPoint;

import java.util.Map;
import java.util.TreeMap;

/**
 * Ce que le joueur garde de ses grades : le grade achete, la fin de son Premium, ses homes,
 * ses teleportations au hasard du mois, et ses interrupteurs (vision nocturne, vol au spawn,
 * demandes de teleportation refusees).
 *
 * @param premiumUntil fin du Premium en millisecondes depuis 1970, 0 sans Premium
 * @param rtpMonth     le mois des {@code /rtp} comptes (annee x 12 + mois)
 */
public record GradeData(String grade, long premiumUntil, Map<String, ReturnPoint> homes, int rtpMonth, int rtpUsed,
                        boolean nightVision, boolean tpaClosed, boolean fly) {

    public static final GradeData EMPTY = new GradeData("", 0L, Map.of(), 0, 0, false, false, false);

    public static final MapCodec<GradeData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("grade", "").forGetter(GradeData::grade),
            Codec.LONG.optionalFieldOf("premium_until", 0L).forGetter(GradeData::premiumUntil),
            Codec.unboundedMap(Codec.STRING, ReturnPoint.MAP_CODEC.codec()).optionalFieldOf("homes", Map.of())
                    .forGetter(GradeData::homes),
            Codec.INT.optionalFieldOf("rtp_month", 0).forGetter(GradeData::rtpMonth),
            Codec.INT.optionalFieldOf("rtp_used", 0).forGetter(GradeData::rtpUsed),
            Codec.BOOL.optionalFieldOf("night_vision", false).forGetter(GradeData::nightVision),
            Codec.BOOL.optionalFieldOf("tpa_closed", false).forGetter(GradeData::tpaClosed),
            Codec.BOOL.optionalFieldOf("fly", false).forGetter(GradeData::fly)
    ).apply(instance, GradeData::new));

    public GradeData {
        homes = Map.copyOf(homes);
    }

    public boolean isEmpty() {
        return grade.isEmpty() && premiumUntil == 0L && homes.isEmpty() && rtpUsed == 0 && !nightVision && !tpaClosed
                && !fly;
    }

    /** Les homes par nom, dans l'ordre alphabetique. */
    public Map<String, ReturnPoint> sortedHomes() {
        return new TreeMap<>(homes);
    }

    public GradeData withGrade(String value) {
        return new GradeData(value, premiumUntil, homes, rtpMonth, rtpUsed, nightVision, tpaClosed, fly);
    }

    public GradeData withPremiumUntil(long value) {
        return new GradeData(grade, value, homes, rtpMonth, rtpUsed, nightVision, tpaClosed, fly);
    }

    public GradeData withHome(String name, ReturnPoint point) {
        Map<String, ReturnPoint> copy = new TreeMap<>(homes);
        if (point == null) {
            copy.remove(name);
        } else {
            copy.put(name, point);
        }
        return new GradeData(grade, premiumUntil, copy, rtpMonth, rtpUsed, nightVision, tpaClosed, fly);
    }

    public GradeData withRtp(int month, int used) {
        return new GradeData(grade, premiumUntil, homes, month, used, nightVision, tpaClosed, fly);
    }

    public GradeData withNightVision(boolean value) {
        return new GradeData(grade, premiumUntil, homes, rtpMonth, rtpUsed, value, tpaClosed, fly);
    }

    public GradeData withTpaClosed(boolean value) {
        return new GradeData(grade, premiumUntil, homes, rtpMonth, rtpUsed, nightVision, value, fly);
    }

    public GradeData withFly(boolean value) {
        return new GradeData(grade, premiumUntil, homes, rtpMonth, rtpUsed, nightVision, tpaClosed, value);
    }
}

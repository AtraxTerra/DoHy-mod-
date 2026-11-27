package fr.atraxterra.dohy.palamod.pet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le familier d'un joueur, garde avec lui (meme a la mort).
 *
 * <p>Un joueur n'a qu'un familier, pour toujours (wiki : on ne peut ni en avoir
 * un second ni changer). Le reste suit le wiki : niveau 1 a 100, bonheur 0 a 200,
 * vingt emplacements de competences (un tous les cinq niveaux), quatre quetes
 * d'entretien par jour, recharges des actives, et les effets « au prochain coup »
 * de certaines actives (mineur chanceux, enchante, reparo...).
 */
public final class PetData {

    public static final int SLOTS = 20;
    public static final int MISSIONS = 4;

    public static final MapCodec<PetData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("kind", "").forGetter(data -> data.kind),
            Codec.INT.optionalFieldOf("level", 1).forGetter(data -> data.level),
            Codec.LONG.optionalFieldOf("xp", 0L).forGetter(data -> data.xp),
            Codec.INT.optionalFieldOf("happiness", 50).forGetter(data -> data.happiness),
            Codec.BOOL.optionalFieldOf("visible", true).forGetter(data -> data.visible),
            Codec.STRING.listOf().optionalFieldOf("slots", List.of()).forGetter(data -> List.copyOf(data.slots)),
            Codec.LONG.listOf().optionalFieldOf("assigned", List.of()).forGetter(data -> List.copyOf(data.assigned)),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("cooldowns", Map.of())
                    .forGetter(data -> Map.copyOf(data.cooldowns)),
            Codec.LONG.optionalFieldOf("day", 0L).forGetter(data -> data.day),
            Codec.STRING.listOf().optionalFieldOf("missions", List.of()).forGetter(data -> List.copyOf(data.missions)),
            Codec.INT.listOf().optionalFieldOf("progress", List.of()).forGetter(data -> List.copyOf(data.progress)),
            Codec.INT.optionalFieldOf("rewarded", 0).forGetter(data -> data.rewarded),
            Codec.LONG.optionalFieldOf("login_day", 0L).forGetter(data -> data.loginDay),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("effects", Map.of())
                    .forGetter(data -> Map.copyOf(data.effects))
    ).apply(instance, PetData::new));

    String kind;
    int level;
    long xp;
    int happiness;
    boolean visible;
    final List<String> slots = new ArrayList<>();
    final List<Long> assigned = new ArrayList<>();
    final Map<String, Long> cooldowns = new HashMap<>();
    long day;
    final List<String> missions = new ArrayList<>();
    final List<Integer> progress = new ArrayList<>();
    /** Les quetes deja payees, en bits. */
    int rewarded;
    long loginDay;
    /** Les effets en attente des actives « au prochain coup ». */
    final Map<String, Double> effects = new HashMap<>();

    public PetData() {
        this("", 1, 0L, 50, true, List.of(), List.of(), Map.of(), 0L, List.of(), List.of(), 0, 0L, Map.of());
    }

    private PetData(String kind, int level, long xp, int happiness, boolean visible, List<String> slots,
                    List<Long> assigned, Map<String, Long> cooldowns, long day, List<String> missions,
                    List<Integer> progress, int rewarded, long loginDay, Map<String, Double> effects) {
        this.kind = kind;
        this.level = Math.max(1, Math.min(PetApi.MAX_LEVEL, level));
        this.xp = Math.max(0L, xp);
        this.happiness = happiness;
        this.visible = visible;
        for (int index = 0; index < SLOTS; index++) {
            this.slots.add(index < slots.size() ? slots.get(index) : "");
            this.assigned.add(index < assigned.size() ? assigned.get(index) : 0L);
        }
        this.cooldowns.putAll(cooldowns);
        this.day = day;
        this.missions.addAll(missions);
        for (int index = 0; index < missions.size(); index++) {
            this.progress.add(index < progress.size() ? progress.get(index) : 0);
        }
        this.rewarded = rewarded;
        this.loginDay = loginDay;
        this.effects.putAll(effects);
    }

    public boolean has() {
        return Familiar.byId(kind) != null;
    }

    public Familiar familiar() {
        return Familiar.byId(kind);
    }

    public int level() {
        return level;
    }

    public long xp() {
        return xp;
    }

    public int happiness() {
        return happiness;
    }

    public boolean visible() {
        return visible;
    }

    public String slot(int index) {
        return slots.get(index);
    }

    public long assignedAt(int index) {
        return assigned.get(index);
    }

    public List<String> missions() {
        return List.copyOf(missions);
    }

    public int progress(int index) {
        return index < progress.size() ? progress.get(index) : 0;
    }

    public boolean rewarded(int index) {
        return (rewarded & (1 << index)) != 0;
    }

    double effect(String key) {
        return effects.getOrDefault(key, 0.0d);
    }

    void setEffect(String key, double value) {
        if (value == 0.0d) {
            effects.remove(key);
        } else {
            effects.put(key, value);
        }
    }

    /** Les competences posees, sans les cases vides. */
    public List<PetSkill> equipped() {
        List<PetSkill> out = new ArrayList<>();
        for (String id : slots) {
            PetSkill skill = PetSkill.byId(id);
            if (skill != null) {
                out.add(skill);
            }
        }
        return out;
    }

    public boolean isEquipped(PetSkill skill) {
        return slots.contains(skill.id());
    }
}

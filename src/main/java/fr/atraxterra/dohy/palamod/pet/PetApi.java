package fr.atraxterra.dohy.palamod.pet;

import fr.atraxterra.dohy.util.RealClock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Les regles du familier, cote serveur : niveaux, bonheur, emplacements,
 * competences, recharges et quetes d'entretien (wiki « Java | Familiers »).
 */
public final class PetApi {

    public static final int MAX_LEVEL = 100;
    public static final int MAX_HAPPINESS = 200;
    /** Une a six actives equipees. */
    public static final int MAX_ACTIVES = 6;
    /** Une competence posee ne se change pas avant 24 heures. */
    public static final long LOCK_MS = 24L * 3600L * 1000L;
    /** Le bonheur perdu quand les quatre quetes du jour ne sont pas faites. */
    public static final int MISSED_DAY = 25;
    /** L'XP de la premiere connexion du jour. */
    public static final long LOGIN_XP = 1000L;
    /** L'XP de Chasseur d'un familier apprivoise. */
    public static final long TAME_HUNTER_XP = 5000L;
    /** Le bonheur d'une quete faite. */
    public static final int MISSION_HAPPINESS = 10;

    private PetApi() {
    }

    public static PetData data(ServerPlayer player) {
        return player.getData(PalaPets.DATA.get());
    }

    public static boolean has(ServerPlayer player) {
        return data(player).has();
    }

    /** XP du niveau {@code level} au suivant : 300 au niveau 1, 5 200 au niveau 99. */
    public static long xpToNext(int level) {
        return level >= MAX_LEVEL ? 0L : 250L + 50L * level;
    }

    /** Le bonheur maximum : 50 au niveau 1, 200 au niveau 100. */
    public static int maxHappiness(int level) {
        return level >= MAX_LEVEL ? MAX_HAPPINESS : 50 + Math.round((level - 1) * 150.0f / 99.0f);
    }

    /** Le multiplicateur des competences : bonheur / 100 (0 a 2). */
    public static double multiplier(PetData data) {
        return Math.max(0, data.happiness) / 100.0d;
    }

    /** Un emplacement tous les cinq niveaux, vingt au plus. */
    public static int slotCount(int level) {
        return Math.min(PetData.SLOTS, level / 5);
    }

    /**
     * La valeur d'une competence pour ce joueur : celle du palier, fois le bonheur ;
     * 0 sans familier, sans la competence posee, ou sous son niveau.
     */
    public static double value(ServerPlayer player, PetSkill skill) {
        PetData data = data(player);
        if (!data.has() || !data.isEquipped(skill) || data.level < skill.unlock()) {
            return 0.0d;
        }
        return skill.base(data.level) * multiplier(data);
    }

    /** Une valeur en pourcentage, ramenee a une fraction (et plafonnee a 1 si demande). */
    public static double fraction(ServerPlayer player, PetSkill skill, boolean capped) {
        double value = value(player, skill) / 100.0d;
        return capped ? Math.min(1.0d, value) : value;
    }

    // -- Niveaux et bonheur ---------------------------------------------------------------------

    /** Gagne de l'XP de familier, niveaux franchis compris. */
    public static void grantXp(ServerPlayer player, long amount) {
        PetData data = data(player);
        if (!data.has() || amount <= 0 || data.level >= MAX_LEVEL) {
            return;
        }
        int before = data.level;
        data.xp += amount;
        while (data.level < MAX_LEVEL && data.xp >= xpToNext(data.level)) {
            data.xp -= xpToNext(data.level);
            data.level++;
        }
        if (data.level >= MAX_LEVEL) {
            data.xp = 0L;
            data.happiness = MAX_HAPPINESS;
        }
        if (data.level > before) {
            player.sendSystemMessage(Component.literal("[Familier] " + data.familiar().label() + " passe niveau "
                    + data.level + " !").withStyle(ChatFormatting.LIGHT_PURPLE));
            if (slotCount(data.level) > slotCount(before)) {
                player.sendSystemMessage(Component.literal("[Familier] Nouvel emplacement de competence ("
                        + slotCount(data.level) + "/" + PetData.SLOTS + "). /pet").withStyle(ChatFormatting.GRAY));
            }
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                    0.5f, 1.6f);
            syncLook(player);
        }
    }

    /** Change le bonheur, entre 0 et le maximum du niveau (200 fige au niveau 100). */
    public static void addHappiness(ServerPlayer player, int amount) {
        PetData data = data(player);
        if (!data.has()) {
            return;
        }
        int before = data.happiness;
        data.happiness = data.level >= MAX_LEVEL ? MAX_HAPPINESS
                : Math.max(0, Math.min(maxHappiness(data.level), data.happiness + amount));
        if (data.happiness != before) {
            syncLook(player);
        }
    }

    /** Le familier apprivoise : il commence au niveau 1, avec 50 de bonheur. */
    public static void tame(ServerPlayer player, Familiar familiar) {
        PetData data = data(player);
        data.kind = familiar.id();
        data.level = 1;
        data.xp = 0L;
        data.happiness = maxHappiness(1);
        data.visible = true;
        data.day = 0L;
        data.loginDay = today();
        rollDay(player);
        syncLook(player);
    }

    public static void setVisible(ServerPlayer player, boolean visible) {
        data(player).visible = visible;
        syncLook(player);
    }

    /** Envoie l'espece, la visibilite et les passives du deltaplane a ceux qui voient le joueur. */
    public static void syncLook(ServerPlayer player) {
        PetData data = data(player);
        boolean obsidian = data.effect(PetActives.UNBREAKABLE_UNTIL) > System.currentTimeMillis();
        PetLook look = data.has()
                ? new PetLook(data.kind, data.visible, (float) fraction(player, PetSkill.AVIATOR, false),
                (float) fraction(player, PetSkill.AERIAL_EXPERT, false),
                (float) fraction(player, PetSkill.GOOD_PICKAXE, false),
                obsidian ? (float) data.effect(PetActives.UNBREAKABLE_FACTOR) : 0.0f)
                : PetLook.NONE;
        if (!look.equals(player.getData(PalaPets.LOOK.get()))) {
            player.setData(PalaPets.LOOK.get(), look);
        }
    }

    // -- Competences -----------------------------------------------------------------------------

    /** Millisecondes avant de pouvoir changer cet emplacement (24 h, moins « Changement rapide »). */
    public static long lockRemaining(ServerPlayer player, int slot) {
        return lock(player, slot, data(player).effect(PetActives.QUICK_CHANGE));
    }

    private static long lock(ServerPlayer player, int slot, double quick) {
        PetData data = data(player);
        long at = data.assigned.get(slot);
        if (at <= 0L || data.slots.get(slot).isEmpty()) {
            return 0L;
        }
        long lock = Math.round(LOCK_MS * (1.0d - Math.min(0.9d, quick / 100.0d)));
        return Math.max(0L, at + lock - System.currentTimeMillis());
    }

    /** Un changement que seul « Changement rapide » a permis consomme l'effet. */
    private static void spendQuickChange(ServerPlayer player, boolean shortened) {
        if (shortened) {
            data(player).setEffect(PetActives.QUICK_CHANGE, 0.0d);
        }
    }

    /** Pose une competence ; rend le refus a afficher, ou {@code null}. */
    public static String assign(ServerPlayer player, int slot, PetSkill skill) {
        PetData data = data(player);
        if (!data.has()) {
            return "Tu n'as pas de familier.";
        }
        if (slot < 0 || slot >= slotCount(data.level)) {
            return "Cet emplacement n'est pas encore ouvert.";
        }
        if (data.level < skill.unlock()) {
            return skill.label() + " se debloque au niveau " + skill.unlock() + ".";
        }
        if (data.isEquipped(skill)) {
            return skill.label() + " est deja posee.";
        }
        if (lockRemaining(player, slot) > 0L) {
            return "Cet emplacement est verrouille encore " + duration(lockRemaining(player, slot)) + ".";
        }
        if (skill.active()) {
            int actives = 0;
            for (PetSkill other : data.equipped()) {
                actives += other.active() ? 1 : 0;
            }
            PetSkill current = PetSkill.byId(data.slots.get(slot));
            if (current != null && current.active()) {
                actives--;
            }
            if (actives >= MAX_ACTIVES) {
                return "Six competences actives au plus.";
            }
        }
        boolean shortened = lock(player, slot, 0.0d) > 0L;
        data.slots.set(slot, skill.id());
        data.assigned.set(slot, System.currentTimeMillis());
        spendQuickChange(player, shortened);
        syncLook(player);
        return null;
    }

    /** Vide un emplacement ; rend le refus a afficher, ou {@code null}. */
    public static String clear(ServerPlayer player, int slot) {
        PetData data = data(player);
        if (slot < 0 || slot >= PetData.SLOTS || data.slots.get(slot).isEmpty()) {
            return null;
        }
        if (lockRemaining(player, slot) > 0L) {
            return "Cet emplacement est verrouille encore " + duration(lockRemaining(player, slot)) + ".";
        }
        boolean shortened = lock(player, slot, 0.0d) > 0L;
        data.slots.set(slot, "");
        data.assigned.set(slot, 0L);
        spendQuickChange(player, shortened);
        syncLook(player);
        return null;
    }

    /** Les actives posees, dans l'ordre des emplacements (la roue). */
    public static List<PetSkill> actives(PetData data) {
        List<PetSkill> out = new ArrayList<>();
        for (PetSkill skill : data.equipped()) {
            if (skill.active()) {
                out.add(skill);
            }
        }
        return out;
    }

    /** Millisecondes avant de pouvoir relancer une active. */
    public static long cooldownRemaining(ServerPlayer player, PetSkill skill) {
        return Math.max(0L, data(player).cooldowns.getOrDefault(skill.id(), 0L) - System.currentTimeMillis());
    }

    /** Lance la recharge d'une active, moins « Recuperation boostee ». */
    static void startCooldown(ServerPlayer player, PetSkill skill) {
        double reduction = Math.min(0.9d, value(player, PetSkill.DECREASED_COOLDOWN) / 100.0d);
        long cooldown = Math.round(skill.cooldownSeconds() * 1000.0d * (1.0d - reduction));
        data(player).cooldowns.put(skill.id(), System.currentTimeMillis() + cooldown);
    }

    // -- Quetes d'entretien --------------------------------------------------------------------

    /** La cle du jour : minuit, heure de Paris (wiki : UTC+1). */
    public static long today() {
        return RealClock.dayKey(0);
    }

    /**
     * Le changement de jour : le jour passe est solde (−25 de bonheur s'il manque une
     * quete, sauf chance de « Familier autosuffisant »), puis quatre quetes sont tirees.
     */
    public static void rollDay(ServerPlayer player) {
        PetData data = data(player);
        long today = today();
        if (!data.has() || data.day == today) {
            return;
        }
        if (data.day != 0L && !data.missions.isEmpty()) {
            boolean missed = false;
            double rescue = value(player, PetSkill.SELF_SUFFICIENT) / 100.0d;
            for (int index = 0; index < data.missions.size(); index++) {
                if (!data.rewarded(index) && player.getRandom().nextDouble() >= rescue) {
                    missed = true;
                }
            }
            if (missed && data.level < MAX_LEVEL) {
                data.happiness = Math.max(0, data.happiness - MISSED_DAY);
                player.sendSystemMessage(Component.literal("[Familier] Des quetes d'entretien manquaient hier : "
                        + "-" + MISSED_DAY + " de bonheur.").withStyle(ChatFormatting.RED));
                syncLook(player);
            }
        }
        data.day = today;
        data.missions.clear();
        data.progress.clear();
        data.rewarded = 0;
        List<PetMission> pool = new ArrayList<>(List.of(PetMission.values()));
        RandomSource random = player.getRandom();
        for (int index = 0; index < PetData.MISSIONS && !pool.isEmpty(); index++) {
            data.missions.add(pool.remove(random.nextInt(pool.size())).id());
            data.progress.add(0);
        }
    }

    /** Avance une quete du jour ; paie l'XP et le bonheur quand elle est finie. */
    public static void progress(ServerPlayer player, PetMission mission, int amount) {
        PetData data = data(player);
        if (!data.has() || amount <= 0) {
            return;
        }
        rollDay(player);
        int index = data.missions.indexOf(mission.id());
        if (index < 0 || data.rewarded(index)) {
            return;
        }
        int value = Math.min(mission.goal(), data.progress.get(index) + amount);
        data.progress.set(index, value);
        if (value >= mission.goal()) {
            data.rewarded |= 1 << index;
            player.sendSystemMessage(Component.literal("[Familier] Quete faite : " + mission.label() + " (+"
                    + mission.xp() + " XP, +" + MISSION_HAPPINESS + " bonheur)").withStyle(ChatFormatting.GREEN));
            addHappiness(player, MISSION_HAPPINESS);
            grantXp(player, mission.xp());
        }
    }

    /** L'XP de la premiere connexion du jour. */
    public static void login(ServerPlayer player) {
        PetData data = data(player);
        syncLook(player);
        if (!data.has()) {
            return;
        }
        rollDay(player);
        if (data.loginDay != today()) {
            data.loginDay = today();
            player.sendSystemMessage(Component.literal("[Familier] Premiere connexion du jour : +" + LOGIN_XP
                    + " XP pour " + data.familiar().label() + ".").withStyle(ChatFormatting.LIGHT_PURPLE));
            grantXp(player, LOGIN_XP);
        }
    }

    /** Un niveau de metier franchi : 20 XP par niveau atteint. */
    public static void onJobLevel(ServerPlayer player, int level) {
        grantXp(player, 20L * level);
    }

    /** « 2 h 05 », « 12 min ». */
    public static String duration(long millis) {
        long minutes = Math.max(1L, (millis + 59_999L) / 60_000L);
        if (minutes < 60L) {
            return minutes + " min";
        }
        return String.format("%d h %02d", minutes / 60L, minutes % 60L);
    }
}

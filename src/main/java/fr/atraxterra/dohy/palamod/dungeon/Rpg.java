package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.EnumMap;
import java.util.Map;

/**
 * Le niveau RPG, les points de competence et les statistiques RPG.
 *
 * <ul>
 *   <li><b>XP RPG</b> (wiki : « chaque salle terminee ») : {@code 10 + 2 x palier}
 *       par salle de combat ou d'enigme, le triple pour une salle de boss ;</li>
 *   <li><b>niveau</b> 1 a 100 : passer du niveau N au suivant demande
 *       {@code 50 + 20 N} XP, 103 950 au total pour le niveau 100 ;</li>
 *   <li><b>points</b> (wiki) : 1 par niveau RPG, 1 tous les 5 paliers reussis
 *       de chaque donjon, et — choix du joueur — 1 tous les 5 niveaux de
 *       chaque metier (le wiki dit 1 par niveau). 260 au plus, le prix de
 *       l'arbre entier.</li>
 * </ul>
 */
public final class Rpg {

    public static final int MAX_LEVEL = 100;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private Rpg() {
    }

    public static RpgData data(ServerPlayer player) {
        return player.getData(PalaDungeon.RPG.get());
    }

    static void store(ServerPlayer player, RpgData data) {
        player.setData(PalaDungeon.RPG.get(), data);
    }

    public static long xpToNext(int level) {
        return 50L + 20L * level;
    }

    public static int level(long xp) {
        int level = 1;
        long left = xp;
        while (level < MAX_LEVEL && left >= xpToNext(level)) {
            left -= xpToNext(level);
            level++;
        }
        return level;
    }

    /** L'XP deja faite dans le niveau courant. */
    public static long xpInLevel(long xp) {
        int level = 1;
        long left = xp;
        while (level < MAX_LEVEL && left >= xpToNext(level)) {
            left -= xpToNext(level);
            level++;
        }
        return left;
    }

    public static void grantXp(ServerPlayer player, long amount) {
        if (amount <= 0) {
            return;
        }
        RpgData data = data(player);
        int before = level(data.xp());
        store(player, data.withXp(data.xp() + amount));
        int after = level(data.xp() + amount);
        player.sendSystemMessage(Component.translatable("message.palamod.rpg.xp", amount)
                .withStyle(ChatFormatting.DARK_AQUA), true);
        if (after > before) {
            player.sendSystemMessage(Component.translatable("message.palamod.rpg.level", after)
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                    0.7f, 1.3f);
        }
    }

    /** Les points de chaque source : niveau RPG, paliers, metiers. */
    public record Points(int level, int paliers, int jobs) {
        public int total() {
            return level + paliers + jobs;
        }
    }

    public static Points points(ServerPlayer player) {
        int level = level(data(player).xp());
        int paliers = 0;
        for (Dungeon dungeon : Dungeon.values()) {
            paliers += DungeonManager.cleared(player, dungeon) / 5;
        }
        int jobs = 0;
        for (Job job : Job.values()) {
            jobs += JobApi.level(player, job) / 5;
        }
        return new Points(level, paliers, jobs);
    }

    public static int available(ServerPlayer player) {
        return points(player).total() - data(player).spent();
    }

    // -- Statistiques ------------------------------------------------------------

    public static Map<RpgStat, Float> tree(RpgData data) {
        Map<RpgStat, Float> stats = new EnumMap<>(RpgStat.class);
        for (RpgStat stat : RpgStat.values()) {
            stats.put(stat, stat.perNode() * data.nodes(stat));
        }
        return stats;
    }

    /** Les runes fusionnees dans l'armure portee. */
    public static Map<RpgStat, Float> runes(ServerPlayer player) {
        Map<RpgStat, Float> stats = new EnumMap<>(RpgStat.class);
        for (RpgStat stat : RpgStat.values()) {
            stats.put(stat, 0.0f);
        }
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            RuneData rune = player.getItemBySlot(slot).get(PalaDungeon.RUNE.get());
            if (rune != null) {
                for (RpgStat stat : RpgStat.values()) {
                    stats.merge(stat, rune.value(stat), Float::sum);
                }
            }
        }
        return stats;
    }

    /** Arbre et runes, plafonnes. */
    public static float stat(ServerPlayer player, RpgStat stat) {
        float total = stat.perNode() * data(player).nodes(stat) + runes(player).get(stat);
        return Math.min(stat.cap(), total);
    }
}

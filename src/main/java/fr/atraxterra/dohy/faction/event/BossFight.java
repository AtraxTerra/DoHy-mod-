package fr.atraxterra.dohy.faction.event;

import fr.atraxterra.dohy.palamod.boss.BossKind;
import fr.atraxterra.dohy.palamod.boss.EventBoss;
import fr.atraxterra.dohy.palamod.boss.PalaBosses;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Un event Boss en cours (lot 22f).
 *
 * <p>Le deroule du wiki : un des cinq boss apparait dans l'arene, les factions
 * le frappent, et a sa mort 700 ELO sont repartis entre elles selon leur rang
 * aux degats.
 *
 * <p><b>Solo</b> : les combattants des factions rivales frappent le boss (a 30 %,
 * {@link EventBoss#RIVAL_FACTOR}) et ne s'en prennent au joueur que s'il les
 * frappe. Seul contre le boss, on touche les 700 ; avec elles, il faut faire plus
 * de degats qu'elles pour finir premier.
 *
 * <p>Le combat s'arrete quand le boss meurt, s'il n'a pas ete frappe depuis cinq
 * minutes, ou au bout de trente.
 */
public final class BossFight extends EventRun {

    private static final int MAX_TICKS = 36_000;
    /** La chance qu'un boss vaincu laisse une cle de caisse rare en plus de la commune. */
    private static final float PALADIUM_KEY_CHANCE = 0.05f;
    private static final float HALLOWEEN_KEY_CHANCE = 0.01f;

    private final BossKind boss;
    private UUID bossId;

    private BossFight(BossKind boss, RandomSource random) {
        super(FactionEventKind.BOSS, random);
        this.boss = boss;
    }

    /** Ouvre l'arene et lache le boss. Rend {@code null} si la Warzone manque. */
    static BossFight start(MinecraftServer server, BossKind kind) {
        ServerLevel level = EventArena.level(server);
        if (level == null) {
            return null;
        }
        EventArena.buildBossArena(level);
        EventArena.hold(level, EventArena.BOSS_CENTER, true);
        BossFight fight = new BossFight(kind, level.getRandom());
        EventBoss entity = PalaBosses.type(kind).create(level, EntitySpawnReason.EVENT);
        if (entity == null) {
            EventArena.hold(level, EventArena.BOSS_CENTER, false);
            return null;
        }
        Vec3 center = Vec3.atBottomCenterOf(EventArena.BOSS_CENTER.above());
        entity.snapTo(center.x, center.y, center.z, 0.0f, 0.0f);
        entity.bind(fight.id);
        level.addFreshEntity(entity);
        fight.bossId = entity.getUUID();
        fight.wave(level);
        return fight;
    }

    public BossKind boss() {
        return boss;
    }

    @Override
    protected BlockPos center() {
        return EventArena.BOSS_CENTER;
    }

    @Override
    public Vec3 entry() {
        return EventArena.ringEntry(EventArena.BOSS_CENTER, EventArena.BOSS_RADIUS);
    }

    @Override
    protected Vec3 gate(int index) {
        return EventArena.ringGate(EventArena.BOSS_CENTER, EventArena.BOSS_RADIUS, index);
    }

    @Override
    public String title() {
        return "Boss : " + boss.label();
    }

    @Override
    public Item icon() {
        return Items.WITHER_SKELETON_SKULL;
    }

    @Override
    protected String unit() {
        return "degats";
    }

    @Override
    protected boolean pvp() {
        return false;
    }

    /** La part de vie du boss, de 0 a 1, ou -1 si on ne le voit pas. */
    @Override
    public float progress(MinecraftServer server) {
        ServerLevel level = EventArena.level(server);
        if (level != null && level.getEntity(bossId) instanceof EventBoss entity) {
            return entity.getHealth() / entity.getMaxHealth();
        }
        return -1.0f;
    }

    @Override
    public List<String> status(ServerPlayer player, MinecraftServer server) {
        float health = progress(server);
        int rank = rankOf(player);
        return List.of(ended() ? "Termine" : health >= 0.0f ? "Vie : " + Math.round(health * 100.0f) + " %"
                        : "En cours",
                rank > 0 ? "Ta faction : " + (rank == 1 ? "1re" : rank + "e") + " · " + Math.round(scoreOf(player))
                        + " degats" : "Pas encore de degats");
    }

    // -- Ce que le boss signale ------------------------------------------------

    public void addDamage(String key, float amount) {
        addScore(key, amount);
    }

    public void bossKilled(ServerLevel level, EventBoss entity) {
        // Les succes : chaque joueur qui a frappe ce boss l'a vaincu.
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (scoreOf(player) > 0) {
                fr.atraxterra.dohy.palamod.achievement.Achievements.count(player, "boss:" + boss.id(), 1);
                // Une cle Amethyste a qui l'a frappe, parfois une cle Paladium, rarement une
                // cle Halloween (BALANCE § 6 nonies).
                boolean paladium = level.getRandom().nextFloat() < PALADIUM_KEY_CHANCE;
                boolean halloween = level.getRandom().nextFloat() < HALLOWEEN_KEY_CHANCE;
                fr.atraxterra.dohy.palamod.box.CrateKeys.give(player, "amethyst", 1);
                if (paladium) {
                    fr.atraxterra.dohy.palamod.box.CrateKeys.give(player, "paladium", 1);
                }
                if (halloween) {
                    fr.atraxterra.dohy.palamod.box.CrateKeys.give(player, "halloween", 1);
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Le boss laisse une cle Amethyste" + (paladium ? ", une cle Paladium" : "")
                                + (halloween ? ", une cle Halloween" : "") + ".")
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
            }
        }
        conclude(level.getServer(), "Le boss " + boss.label() + " a succombe.");
    }

    // -- Deroulement -----------------------------------------------------------

    @Override
    protected void tickRunning(ServerLevel level) {
        EventBoss entity = level.getEntity(bossId) instanceof EventBoss found ? found : null;
        if (entity == null && ticks > 40) {
            stop(level, "Le boss " + boss.label() + " s'est evanoui.");
            return;
        }
        if (entity != null && entity.idleTicks() > EventBoss.BORED_TICKS) {
            entity.discard();
            stop(level, "Le boss " + boss.label() + " repart : personne ne l'a frappe depuis 5 min.");
            return;
        }
        if (ticks > MAX_TICKS) {
            if (entity != null) {
                entity.discard();
            }
            stop(level, "Le boss " + boss.label() + " repart : 30 min sont ecoulees.");
        }
    }

    @Override
    void stop(ServerLevel level, String reason) {
        super.stop(level, reason);
        if (level.getEntity(bossId) instanceof EventBoss entity) {
            entity.discard();
        }
    }
}

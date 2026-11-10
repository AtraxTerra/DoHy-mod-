package fr.atraxterra.dohy.faction.event;

import fr.atraxterra.dohy.palamod.boss.RivalFighter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le KOTH a points (lot 22f, wiki « Java | Event faction »), le samedi a 21 h 30.
 *
 * <ul>
 *   <li>Une zone de capture au centre de l'arene — ici, le sommet de la
 *       colline.</li>
 *   <li><b>Chaque seconde, un occupant de la zone est tire au sort</b> : sa
 *       faction marque un point. Plus une faction a de monde dans la zone, plus
 *       elle a de chances.</li>
 *   <li>Apres une mort, un joueur ne compte plus dans la zone pendant
 *       <b>dix minutes</b>.</li>
 *   <li>Trente minutes ; 10 000 ELO repartis selon le rang aux points ; titre du
 *       vainqueur pour tous, avec sa place pour chacun.</li>
 * </ul>
 *
 * <p><b>Solo</b> : les combattants des trois factions rivales montent sur la
 * colline et se battent entre eux et contre le joueur. Pour marquer, il faut
 * vider la zone.
 */
public final class KothRun extends EventRun {

    static final int DURATION = 36_000;
    static final int DEATH_LOCK = 12_000;

    private final Map<UUID, Integer> deaths = new HashMap<>();
    private final Map<UUID, Boolean> warned = new HashMap<>();
    private String holder;

    private KothRun(RandomSource random) {
        super(FactionEventKind.KOTH, random);
        bar = new ServerBossEvent(UUID.randomUUID(), Component.literal("KOTH"), BossEvent.BossBarColor.YELLOW,
                BossEvent.BossBarOverlay.PROGRESS);
    }

    static KothRun start(MinecraftServer server) {
        ServerLevel level = EventArena.level(server);
        if (level == null) {
            return null;
        }
        EventArena.buildKothArena(level);
        EventArena.hold(level, EventArena.KOTH_CENTER, true);
        KothRun run = new KothRun(level.getRandom());
        run.wave(level);
        return run;
    }

    @Override
    protected BlockPos center() {
        return EventArena.KOTH_CENTER;
    }

    @Override
    public Vec3 entry() {
        return EventArena.ringEntry(EventArena.KOTH_CENTER, EventArena.KOTH_RADIUS);
    }

    @Override
    protected Vec3 gate(int index) {
        return EventArena.ringGate(EventArena.KOTH_CENTER, EventArena.KOTH_RADIUS, index);
    }

    @Override
    public String title() {
        return "KOTH a points";
    }

    @Override
    public Item icon() {
        return Items.BEACON;
    }

    @Override
    protected String unit() {
        return "points";
    }

    @Override
    protected boolean pvp() {
        return true;
    }

    @Override
    public float progress(MinecraftServer server) {
        return Math.clamp(1.0f - ticks / (float) DURATION, 0.0f, 1.0f);
    }

    @Override
    public List<String> status(ServerPlayer player, MinecraftServer server) {
        int rank = rankOf(player);
        return List.of(ended() ? "Termine" : "Reste " + clock(DURATION - ticks) + " · zone : "
                        + (holder == null ? "libre" : nameOf(holder)),
                rank > 0 ? "Ta faction : " + (rank == 1 ? "1re" : rank + "e") + " · " + Math.round(scoreOf(player))
                        + " points" : "Pas encore de points");
    }

    /** Un joueur mort ne compte plus dans la zone pendant dix minutes. */
    void died(ServerPlayer player) {
        deaths.put(player.getUUID(), ticks);
        warned.remove(player.getUUID());
    }

    private boolean inZone(LivingEntity entity) {
        Vec3 zone = Vec3.atBottomCenterOf(EventArena.KOTH_ZONE);
        double dx = entity.getX() - zone.x;
        double dz = entity.getZ() - zone.z;
        return dx * dx + dz * dz <= EventArena.KOTH_ZONE_RADIUS * EventArena.KOTH_ZONE_RADIUS
                && entity.getY() >= zone.y - 0.5d && entity.getY() <= zone.y + 5.0d;
    }

    @Override
    protected void tickRunning(ServerLevel level) {
        if (ticks >= DURATION) {
            String leader = leader();
            conclude(level.getServer(), leader == null ? "Le KOTH est termine."
                    : "Le KOTH est termine : " + nameOf(leader) + " l'emporte.");
            return;
        }
        if (ticks % 20 != 0) {
            return;
        }
        RandomSource random = level.getRandom();
        List<LivingEntity> occupants = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive() || !inZone(player)) {
                continue;
            }
            Integer died = deaths.get(player.getUUID());
            if (died != null && ticks - died < DEATH_LOCK) {
                if (warned.put(player.getUUID(), true) == null) {
                    player.sendSystemMessage(Component.literal("Tu es mort : la zone ne te compte plus avant "
                            + clock(DEATH_LOCK - (ticks - died)) + ".").withStyle(ChatFormatting.RED));
                }
                continue;
            }
            occupants.add(player);
        }
        Vec3 zone = Vec3.atBottomCenterOf(EventArena.KOTH_ZONE);
        for (RivalFighter fighter : fighters(level)) {
            if (inZone(fighter)) {
                occupants.add(fighter);
            }
            // Chacun vise un point de la zone, pas tous le meme bloc.
            double angle = (fighter.getId() % 12) * Math.PI / 6.0d;
            fighter.setObjective(zone.add(Math.cos(angle) * 1.5d, 1.0d, Math.sin(angle) * 1.5d));
        }
        holder = null;
        if (!occupants.isEmpty()) {
            LivingEntity picked = occupants.get(random.nextInt(occupants.size()));
            holder = keyOf(picked);
            addScore(holder, 1.0f);
        }
        // Le cercle de la zone, d'or s'il est tenu.
        int count = 28;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0d * i / count;
            level.sendParticles(holder == null ? ParticleTypes.END_ROD : ParticleTypes.WAX_ON,
                    zone.x + Math.cos(angle) * EventArena.KOTH_ZONE_RADIUS, zone.y + 0.2d,
                    zone.z + Math.sin(angle) * EventArena.KOTH_ZONE_RADIUS, 1, 0.0d, 0.05d, 0.0d, 0.0d);
        }
        bar.setProgress(progress(level.getServer()));
        String leader = leader();
        bar.setName(Component.literal("KOTH · " + clock(DURATION - ticks) + " · zone : "
                + (holder == null ? "libre" : nameOf(holder))
                + (leader == null ? "" : " · en tete : " + nameOf(leader))));
    }
}

package fr.atraxterra.dohy.faction.event;

import fr.atraxterra.dohy.palamod.boss.PalaBosses;
import fr.atraxterra.dohy.palamod.boss.RivalFighter;
import fr.atraxterra.dohy.palamod.boss.VolcaniaDragon;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L'Egg Hunt (lot 22f, wiki « Java | Event faction »), le mardi et le dimanche
 * a 19 h.
 *
 * <ul>
 *   <li>D'abord, abattre <b>Volcania</b>, le dragon de l'Egg Hunt.</li>
 *   <li>Il laisse un <b>oeuf</b> sur l'autel. Celui qui le porte fait gagner du
 *       temps a sa faction : <b>le classement se fait au temps de port</b>.</li>
 *   <li><b>Tuer le porteur vole l'oeuf</b> ; mort autrement, ou lache
 *       ({@code /f event lacher}), l'oeuf retombe la ou il etait. Comme le
 *       PalaMod ({@code PICKUP_LOCK_MILLIS}), qui l'a perdu ne peut plus le
 *       reprendre pendant <b>10 min</b>.</li>
 *   <li><b>60 minutes</b> de chasse apres la mort du dragon ; 3 000 ELO par rang
 *       au temps de port (facteur 1,2, comme {@code EggHuntManager}).</li>
 * </ul>
 *
 * <p><b>Solo</b> : le PalaMod interdit de porter l'oeuf hors de la zone de
 * 1 000 a 5 000 blocs du spawn ou dans un claim ; ici, il ne quitte pas l'ile,
 * et tomber dans le vide le renvoie sur l'autel. Les combattants rivaux le
 * ramassent, s'enfuient avec, et pourchassent quiconque le porte ; tuer leur
 * porteur vole l'oeuf, comme pour un joueur. Si Volcania n'est pas abattu en
 * 20 min, il repart, sans oeuf.
 */
public final class EggHuntRun extends EventRun {

    static final int DRAGON_LIMIT = 24_000;
    static final int CONTEST = 72_000;
    static final int PICKUP_LOCK = 12_000;
    /** Les rivaux reprennent plus vite : sans quoi l'oeuf leur echapperait pour de bon. */
    static final int RIVAL_LOCK = 1_200;
    /**
     * Les rivaux ne touchent pas l'oeuf dans les 10 s ou il apparait : celui qui
     * a abattu Volcania, au pied de l'autel, doit avoir sa chance.
     */
    static final int RIVAL_GRACE = 200;

    private UUID dragonId;
    private int eggSince = -1;
    /** L'oeuf pose au sol, ou {@code null} s'il est porte. */
    private BlockPos egg;
    private UUID carrier;
    private String carrierKey;
    private final Map<UUID, Integer> locks = new HashMap<>();
    private final Map<UUID, Vec3> escapes = new HashMap<>();

    private EggHuntRun(net.minecraft.util.RandomSource random) {
        super(FactionEventKind.EGG_HUNT, random);
        bar = new ServerBossEvent(UUID.randomUUID(), Component.literal("Egg Hunt"), BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS);
    }

    static EggHuntRun start(MinecraftServer server) {
        ServerLevel level = EventArena.level(server);
        if (level == null) {
            return null;
        }
        EventArena.buildEggArena(level);
        EventArena.hold(level, EventArena.EGG_CENTER, true);
        EggHuntRun run = new EggHuntRun(level.getRandom());
        VolcaniaDragon dragon = PalaBosses.VOLCANIA.get().create(level, EntitySpawnReason.EVENT);
        if (dragon == null) {
            EventArena.hold(level, EventArena.EGG_CENTER, false);
            return null;
        }
        Vec3 sky = Vec3.atCenterOf(EventArena.EGG_CENTER.above(30));
        dragon.snapTo(sky.x, sky.y, sky.z, 0.0f, 0.0f);
        dragon.bind(run.id, EventArena.EGG_CENTER);
        level.addFreshEntity(dragon);
        run.dragonId = dragon.getUUID();
        run.wave(level);
        return run;
    }

    @Override
    protected BlockPos center() {
        return EventArena.EGG_CENTER;
    }

    @Override
    public Vec3 entry() {
        return EventArena.eggEntry();
    }

    @Override
    protected Vec3 gate(int index) {
        return EventArena.eggGate(index);
    }

    @Override
    public String title() {
        return "Egg Hunt";
    }

    @Override
    public Item icon() {
        return Items.DRAGON_EGG;
    }

    @Override
    protected String unit() {
        return "s de port";
    }

    @Override
    protected boolean pvp() {
        return true;
    }

    private boolean hatched() {
        return eggSince >= 0;
    }

    @Override
    public float progress(MinecraftServer server) {
        if (hatched()) {
            return Math.clamp(1.0f - (ticks - eggSince) / (float) CONTEST, 0.0f, 1.0f);
        }
        ServerLevel level = EventArena.level(server);
        if (level != null && level.getEntity(dragonId) instanceof VolcaniaDragon dragon) {
            return dragon.getHealth() / dragon.getMaxHealth();
        }
        return -1.0f;
    }

    @Override
    public List<String> status(ServerPlayer player, MinecraftServer server) {
        if (!hatched()) {
            float health = progress(server);
            return List.of(health >= 0.0f ? "Volcania : " + Math.round(health * 100.0f) + " %" : "Volcania",
                    "Abats le dragon : l'oeuf suivra");
        }
        int rank = rankOf(player);
        return List.of(ended() ? "Termine" : "Reste " + clock(CONTEST - (ticks - eggSince)) + " · oeuf : " + holder(),
                player.getUUID().equals(carrier) ? "Tu portes l'oeuf !" : rank > 0 ? "Ta faction : "
                        + (rank == 1 ? "1re" : rank + "e") + " · " + Math.round(scoreOf(player)) + " s"
                        : "Pas encore de temps de port");
    }

    private String holder() {
        return carrierKey != null ? nameOf(carrierKey) : egg != null && egg.equals(EventArena.EGG_ALTAR)
                ? "sur l'autel" : "au sol";
    }

    // -- L'oeuf ----------------------------------------------------------------

    /** Ce joueur porte-t-il l'oeuf ? */
    public boolean carries(UUID player) {
        return player.equals(carrier);
    }

    /** L'oeuf est-il a cet endroit ? */
    boolean isEgg(BlockPos pos) {
        return egg != null && egg.equals(pos);
    }

    /** Un joueur touche l'oeuf : il le prend, sauf s'il l'a perdu il y a moins de 10 min. */
    void tryPickup(ServerPlayer player) {
        if (ended() || egg == null) {
            return;
        }
        Integer until = locks.get(player.getUUID());
        if (until != null && until > ticks) {
            player.sendSystemMessage(Component.literal("Tu as perdu l'oeuf : tu pourras le reprendre dans "
                    + clock(until - ticks) + ".").withStyle(ChatFormatting.RED));
            return;
        }
        pickup((ServerLevel) player.level(), player);
    }

    private void pickup(ServerLevel level, LivingEntity who) {
        if (egg != null && level.getBlockState(egg).is(Blocks.DRAGON_EGG)) {
            level.setBlock(egg, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        egg = null;
        carrier = who.getUUID();
        carrierKey = keyOf(who);
        who.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60 * 60, 0));
        if (who instanceof RivalFighter fighter) {
            fighter.setCarrying(true);
        }
        level.playSound(null, who.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.6f);
        tellArena(level, nameOf(carrierKey) + " s'empare de l'oeuf !", ChatFormatting.GOLD);
    }

    /** L'oeuf quitte son porteur et retombe en {@code at} (ou sur l'autel, hors de l'ile). */
    private void dropAt(ServerLevel level, BlockPos at) {
        Entity old = carrier == null ? null : carrierEntity(level);
        if (old instanceof LivingEntity living) {
            living.removeEffect(MobEffects.GLOWING);
        }
        if (old instanceof RivalFighter fighter) {
            fighter.setCarrying(false);
        }
        carrier = null;
        carrierKey = null;
        BlockPos spot = ground(level, at);
        level.setBlock(spot, Blocks.DRAGON_EGG.defaultBlockState(), Block.UPDATE_ALL);
        egg = spot;
    }

    /** Un sol libre sous {@code at}, sur l'ile ; sinon l'autel. */
    private static BlockPos ground(ServerLevel level, BlockPos at) {
        if (!EventArena.onEggIsland(at)) {
            return EventArena.EGG_ALTAR;
        }
        BlockPos pos = at;
        while (pos.getY() > EventArena.EGG_CENTER.getY() && level.getBlockState(pos.below()).isAir()) {
            pos = pos.below();
        }
        while (!level.getBlockState(pos).isAir() && pos.getY() < EventArena.EGG_CENTER.getY() + 20) {
            pos = pos.above();
        }
        return level.getBlockState(pos).isAir() ? pos : EventArena.EGG_ALTAR;
    }

    /** Le porteur meurt : vole par son tueur, ou perdu sur place. */
    void onDeath(ServerLevel level, LivingEntity dead, Entity killer) {
        if (carrier == null || !carrier.equals(dead.getUUID()) || ended()) {
            return;
        }
        lock(dead);
        boolean thief = killer instanceof LivingEntity living && living.isAlive() && living.level() == level
                && (living instanceof ServerPlayer player ? !player.isSpectator() : living instanceof RivalFighter)
                && !locked(living);
        if (thief) {
            if (dead instanceof ServerPlayer victim) {
                victim.sendSystemMessage(Component.literal("On vient de te voler l'oeuf.").withStyle(ChatFormatting.RED));
            }
            if (killer instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("Tu as vole l'oeuf !").withStyle(ChatFormatting.GREEN));
            }
            carrier = null;
            pickup(level, (LivingEntity) killer);
            return;
        }
        if (dead instanceof ServerPlayer victim) {
            victim.sendSystemMessage(Component.literal("Tu as perdu l'oeuf.").withStyle(ChatFormatting.RED));
        }
        dropAt(level, dead.blockPosition());
    }

    /** {@code /f event lacher} : l'oeuf tombe aux pieds du porteur. */
    String drop(ServerPlayer player) {
        if (carrier == null || !carrier.equals(player.getUUID())) {
            return "Tu ne portes pas l'oeuf.";
        }
        lock(player);
        dropAt((ServerLevel) player.level(), player.blockPosition());
        return null;
    }

    /** Le porteur quitte l'arene : l'oeuf retourne sur l'autel. */
    void onLeave(ServerPlayer player) {
        if (carrier != null && carrier.equals(player.getUUID()) && EventArena.level(player.level().getServer())
                instanceof ServerLevel level) {
            lock(player);
            dropAt(level, EventArena.EGG_ALTAR);
        }
    }

    private void lock(Entity entity) {
        locks.put(entity.getUUID(), ticks + (entity instanceof RivalFighter ? RIVAL_LOCK : PICKUP_LOCK));
    }

    private boolean locked(Entity entity) {
        Integer until = locks.get(entity.getUUID());
        return until != null && until > ticks;
    }

    private Entity carrierEntity(ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(carrier);
        if (player != null) {
            return player.level() == level ? player : null;
        }
        return level.getEntity(carrier);
    }

    // -- Deroulement -----------------------------------------------------------

    @Override
    protected void tickRunning(ServerLevel level) {
        if (!hatched()) {
            tickDragon(level);
            return;
        }
        if (ticks - eggSince >= CONTEST) {
            String leader = leader();
            conclude(level.getServer(), leader == null ? "L'Egg Hunt est termine."
                    : "L'Egg Hunt est termine : " + nameOf(leader) + " remporte la chasse a l'oeuf.");
            return;
        }
        Entity held = carrier == null ? null : carrierEntity(level);
        if (carrier != null) {
            if (!(held instanceof LivingEntity living) || !living.isAlive()) {
                // Deconnecte, parti, ou mort sans que l'evenement ne nous parvienne.
                dropAt(level, EventArena.EGG_ALTAR);
                held = null;
            } else if (held.getY() < EventArena.EGG_CENTER.getY() - 8) {
                tellArena(level, "L'oeuf est tombe dans le vide : il retourne sur l'autel.", ChatFormatting.GRAY);
                lock(held);
                dropAt(level, EventArena.EGG_ALTAR);
                held = null;
            } else if (ticks % 10 == 0) {
                level.sendParticles(ParticleTypes.PORTAL, held.getX(), held.getY() + 1.0d, held.getZ(), 12,
                        0.4d, 0.6d, 0.4d, 0.3d);
            }
        }
        if (ticks % 20 != 0) {
            return;
        }
        if (carrierKey != null) {
            addScore(carrierKey, 1.0f);
        }
        steerRivals(level, held);
        if (egg != null) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, egg.getX() + 0.5d, egg.getY() + 0.6d, egg.getZ() + 0.5d,
                    10, 0.3d, 0.4d, 0.3d, 0.01d);
        }
        bar.setProgress(progress(level.getServer()));
        String leader = leader();
        bar.setName(Component.literal("Egg Hunt · " + clock(CONTEST - (ticks - eggSince)) + " · oeuf : " + holder()
                + (leader == null ? "" : " · en tete : " + nameOf(leader))));
    }

    private void tickDragon(ServerLevel level) {
        VolcaniaDragon dragon = level.getEntity(dragonId) instanceof VolcaniaDragon found ? found : null;
        if (dragon == null || dragon.isRemoved() || dragon.dragonDeathTime >= 190) {
            if (dragon != null) {
                dragon.discard();
            }
            eggSince = ticks;
            dropAt(level, EventArena.EGG_ALTAR);
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                if (fr.atraxterra.dohy.faction.FactionApi.has(player)) {
                    player.sendSystemMessage(Component.literal("[Event] Volcania est vaincu ! L'oeuf attend sur "
                            + "l'autel : 60 min de chasse.").withStyle(ChatFormatting.GOLD));
                }
            }
            return;
        }
        if (ticks > DRAGON_LIMIT) {
            dragon.discard();
            stop(level, "Volcania repart : personne ne l'a abattu en 20 min.");
            return;
        }
        if (ticks % 20 == 0) {
            // Pendant le combat, les rivaux patrouillent loin de l'autel.
            Vec3 ground = Vec3.atBottomCenterOf(EventArena.EGG_CENTER.above());
            for (RivalFighter fighter : fighters(level)) {
                double angle = (fighter.getId() % 10) * Math.PI / 5.0d + ticks / 1200.0d;
                fighter.setObjective(ground.add(Math.cos(angle) * 24.0d, 0.0d, Math.sin(angle) * 24.0d));
            }
            float health = dragon.getHealth() / dragon.getMaxHealth();
            bar.setProgress(Math.clamp(health, 0.0f, 1.0f));
            bar.setName(Component.literal("Egg Hunt · Volcania " + Math.round(health * 100.0f) + " %"));
        }
    }

    /** Les rivaux : a l'oeuf s'il est au sol ; a ses trousses s'il est porte ; en fuite s'ils le portent. */
    private void steerRivals(ServerLevel level, Entity held) {
        for (RivalFighter fighter : fighters(level)) {
            if (held == fighter) {
                Vec3 escape = escapes.get(fighter.getUUID());
                if (escape == null || fighter.position().distanceToSqr(escape) < 9.0d || ticks % 200 == 0) {
                    double angle = level.getRandom().nextDouble() * Math.PI * 2.0d;
                    double radius = 12.0d + level.getRandom().nextDouble() * (EventArena.EGG_RADIUS - 18.0d);
                    escape = Vec3.atBottomCenterOf(EventArena.EGG_CENTER.above()).add(Math.cos(angle) * radius, 0.0d,
                            Math.sin(angle) * radius);
                    escapes.put(fighter.getUUID(), escape);
                }
                fighter.setObjective(escape);
                continue;
            }
            if (egg != null) {
                if (ticks - eggSince < RIVAL_GRACE) {
                    double angle = (fighter.getId() % 10) * Math.PI / 5.0d;
                    fighter.setObjective(Vec3.atBottomCenterOf(EventArena.EGG_CENTER.above())
                            .add(Math.cos(angle) * 12.0d, 0.0d, Math.sin(angle) * 12.0d));
                    continue;
                }
                fighter.setObjective(Vec3.atBottomCenterOf(egg));
                if (fighter.position().distanceToSqr(Vec3.atBottomCenterOf(egg)) < 4.0d && !locked(fighter)) {
                    pickup(level, fighter);
                    return;
                }
                continue;
            }
            if (held instanceof LivingEntity target && !"r:".concat(String.valueOf(fighter.rival())).equals(carrierKey)
                    && fighter.distanceTo(target) < 48.0f
                    && !(target instanceof ServerPlayer player && (player.isCreative() || player.isSpectator()))) {
                fighter.setObjective(target.position());
                fighter.setTarget(target);
            }
        }
    }

    @Override
    protected void onFinish(ServerLevel level) {
        if (egg != null && level.getBlockState(egg).is(Blocks.DRAGON_EGG)) {
            level.setBlock(egg, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (carrier != null && carrierEntity(level) instanceof LivingEntity living) {
            living.removeEffect(MobEffects.GLOWING);
        }
        if (level.getEntity(dragonId) instanceof VolcaniaDragon dragon) {
            dragon.discard();
        }
    }

    @Override
    void stop(ServerLevel level, String reason) {
        super.stop(level, reason);
        if (level.getEntity(dragonId) instanceof VolcaniaDragon dragon) {
            dragon.discard();
        }
    }

    private static void tellArena(ServerLevel level, String text, ChatFormatting color) {
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.literal(text).withStyle(color));
        }
    }
}

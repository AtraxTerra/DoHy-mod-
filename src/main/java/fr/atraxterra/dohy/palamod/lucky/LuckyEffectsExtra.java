package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les effets du cinquieme lot, cote serveur : ceux que le deuxieme lot avait laisses
 * parce qu'ils ne vivaient que dans le rendu de PalaMod.
 *
 * <ul>
 *   <li>Aimante : tout ce qui est a cinq blocs (objets, creatures) glisse vers le joueur.
 *       PalaMod ne le faisait que sur l'ecran ; ici, on le sent ;</li>
 *   <li>56K : toutes les deux secondes, le joueur revient ou il etait une demi-seconde plus
 *       tot, comme quand le serveur rattrape une connexion qui rame ;</li>
 *   <li>T'as mange quoi ? : un bruit mou et un petit nuage tous les deux pas ;</li>
 *   <li>T'as une mauvaise mine : de petites taches vertes autour de la tete ;</li>
 *   <li>Cow Player : une vache sans cervelle suit le joueur pas a pas, et le joueur
 *       disparait derriere elle ({@code LuckyClientEvents} cache son corps) ;</li>
 *   <li>Ton heure a sonne : a la mort, tout ce qui serait tombe va dans une tombe posee
 *       en surface, que seul le joueur peut ouvrir.</li>
 * </ul>
 */
@EventBusSubscriber
public final class LuckyEffectsExtra {

    /** {@code new AxisAlignedBB(x - 5, ..., x + 5)} autour du joueur, et {@code 0.2F} par tic dans l'original. */
    private static final double MAGNET_RANGE = 5.0d;
    private static final double MAGNET_PULL = 0.06d;
    /** Le retour en arriere : tous les 40 tics, a la place d'il y a 10 tics. */
    private static final int LAG_PERIOD = 40;
    private static final int LAG_BACK = 10;
    /** Un bruit tous les 1,6 bloc marche. */
    private static final double FART_STEP = 1.6d;
    /** L'equipe locale de la vache : elle ne bouscule pas le joueur qu'elle cache. */
    private static final String DISGUISE_TEAM = "dohy_cow_player";

    private static final Map<UUID, Deque<Vec3>> TRAILS = new HashMap<>();
    private static final Map<UUID, Double> WALKED = new HashMap<>();
    private static final Map<UUID, UUID> COWS = new HashMap<>();

    private LuckyEffectsExtra() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.hasEffect(PalaLucky.MAGNETIC)) {
            magnet(player, level);
        }
        if (player.hasEffect(PalaLucky.LAG)) {
            lag(player);
        } else {
            TRAILS.remove(player.getUUID());
        }
        if (player.hasEffect(PalaLucky.FART)) {
            fart(player, level);
        }
        if (player.hasEffect(PalaLucky.BAD_FACE) && player.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.ITEM_SLIME, player.getX(), player.getEyeY(), player.getZ(), 3,
                    0.3d, 0.2d, 0.3d, 0.0d);
        }
        cow(player, level);
    }

    private static void magnet(ServerPlayer player, ServerLevel level) {
        AABB box = player.getBoundingBox().inflate(MAGNET_RANGE);
        for (Entity entity : level.getEntities(player, box, entity -> !(entity instanceof Player)
                && !COWS.containsValue(entity.getUUID()) && !entity.isSpectator())) {
            Vec3 pull = player.position().subtract(entity.position());
            if (pull.lengthSqr() < 1.0d) {
                continue;
            }
            entity.setDeltaMovement(entity.getDeltaMovement().add(pull.normalize().scale(MAGNET_PULL)));
            entity.hurtMarked = true;
        }
    }

    private static void lag(ServerPlayer player) {
        Deque<Vec3> trail = TRAILS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        trail.addLast(player.position());
        while (trail.size() > LAG_BACK) {
            trail.removeFirst();
        }
        if (player.tickCount % LAG_PERIOD == 0 && trail.size() == LAG_BACK) {
            Vec3 back = trail.peekFirst();
            if (back.distanceToSqr(player.position()) > 0.25d) {
                player.teleportTo(back.x, back.y, back.z);
            }
            trail.clear();
        }
    }

    private static void fart(ServerPlayer player, ServerLevel level) {
        if (!player.onGround()) {
            return;
        }
        double moved = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
        double walked = WALKED.merge(player.getUUID(), moved, Double::sum);
        if (walked < FART_STEP) {
            return;
        }
        WALKED.put(player.getUUID(), 0.0d);
        LuckyWorld.sound(level, player.getX(), player.getY(), player.getZ(), "entity.slime.squish_small",
                SoundSource.PLAYERS, 0.8f, 0.5f + level.getRandom().nextFloat() * 0.2f);
        Vec3 behind = player.position().subtract(player.getLookAngle().multiply(0.6d, 0.0d, 0.6d));
        level.sendParticles(ParticleTypes.SNEEZE, behind.x, player.getY() + 0.7d, behind.z, 6, 0.15d, 0.1d, 0.15d,
                0.01d);
    }

    // -- Cow Player -----------------------------------------------------------------

    private static void cow(ServerPlayer player, ServerLevel level) {
        UUID id = COWS.get(player.getUUID());
        Entity existing = id == null ? null : level.getEntity(id);
        if (!player.hasEffect(PalaLucky.COW_PLAYER) || player.isSpectator()) {
            if (id != null) {
                if (existing != null) {
                    existing.discard();
                }
                COWS.remove(player.getUUID());
                player.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(team()));
            }
            return;
        }
        if (!(existing instanceof Mob cow) || existing.getType() != EntityTypes.COW) {
            Mob created = LuckyWorld.create(level, EntityTypes.COW, player.getX(), player.getY(), player.getZ());
            if (created == null) {
                return;
            }
            created.setNoAi(true);
            created.setInvulnerable(true);
            created.setSilent(true);
            created.setPersistenceRequired();
            created.getPersistentData().putBoolean("dohy_cow_player", true);
            level.addFreshEntity(created);
            COWS.put(player.getUUID(), created.getUUID());
            PlayerTeam team = team();
            player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
            player.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(team,
                    created.getUUID().toString(), ClientboundSetPlayerTeamPacket.Action.ADD));
            return;
        }
        cow.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);
        cow.setYHeadRot(player.getYHeadRot());
        cow.setYBodyRot(player.getYRot());
        cow.setDeltaMovement(Vec3.ZERO);
    }

    private static PlayerTeam team() {
        PlayerTeam team = new PlayerTeam(new Scoreboard(), DISGUISE_TEAM);
        team.setCollisionRule(Team.CollisionRule.NEVER);
        return team;
    }

    // -- Ton heure a sonne --------------------------------------------------------------

    /** {@code onPlayerDeath} avec {@code TOMBE} : la tombe en surface, et ce qui tombait dedans. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasEffect(PalaLucky.TOMBE)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        int x = player.getBlockX();
        int z = player.getBlockZ();
        level.getChunk(x >> 4, z >> 4);
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        if (!LuckyWorld.place(player, level, pos, LuckyGadgets.TOMB.get().defaultBlockState())
                || !(level.getBlockEntity(pos) instanceof GadgetBlocks.TombBlockEntity tomb)) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            stacks.add(drop.getItem().copy());
        }
        tomb.fill(player.getUUID(), player.getName().getString(), stacks);
        event.getDrops().clear();
        player.removeEffect(PalaLucky.TOMBE);
    }

    /** Ton heure a sonne : l'effet, puis la mort ({@code setHealth(0)}). */
    static void tomb(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(PalaLucky.TOMBE, 99999, 0));
        player.hurtServer((ServerLevel) player.level(), player.damageSources().genericKill(), Float.MAX_VALUE);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        TRAILS.remove(id);
        WALKED.remove(id);
        UUID cow = COWS.remove(id);
        if (cow != null && event.getEntity().level() instanceof ServerLevel level && level.getEntity(cow) != null) {
            level.getEntity(cow).discard();
        }
    }

    /** Une vache de Cow Player orpheline (serveur redemarre) : elle s'en va. */
    @SubscribeEvent
    public static void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Mob cow && cow.getType() == EntityTypes.COW
                && cow.getPersistentData().getBooleanOr("dohy_cow_player", false)
                && !COWS.containsValue(cow.getUUID())) {
            event.setCanceled(true);
        }
    }
}

package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ce que le desert entend.
 *
 * <p>Tout ce qui bat le sable a un rythme fait du bruit : la moissonneuse qui
 * avale l'epice (beaucoup), celle qui roule (un peu), le pas de course (assez),
 * la marche (a peine), le marteleur (enormement), une eruption d'epice (d'un
 * coup). Rester immobile, ou marcher accroupi, ne fait aucun bruit ; la roche,
 * les blocs poses et la cuvette d'Arrakeen non plus.
 *
 * <p>Chaque source amasse une attention, qui retombe dans le silence. Passe le
 * seuil, un ver part vers elle ({@link SandWorm}) — sauf si un autre vient de
 * passer dans les parages. La moissonneuse previent son conducteur aux deux
 * tiers.
 */
@EventBusSubscriber(modid = DoHy.MOD_ID)
public final class WormNoise {

    /** L'attention qu'il faut pour qu'un ver vienne. */
    public static final double THRESHOLD = 150.0d;
    /** Ce que le silence fait retomber, par seconde. */
    private static final double DECAY = 1.0d;
    private static final double HARVESTER_WORKING = 2.5d;
    private static final double HARVESTER_MOVING = 1.0d;
    private static final double SPRINT = 1.2d;
    private static final double WALK = 0.25d;
    /** Apres un ver, plus d'autre a moins de ce rayon pendant {@link #COOLDOWN} ticks. */
    private static final double QUIET_RADIUS = 160.0d;
    private static final int COOLDOWN = 150 * 20;
    private static final int MAX_WORMS = 2;
    /** Sans bruit depuis ce temps, un ver perd la trace. */
    static final int LOST_TICKS = 8 * 20;
    /** Le marteleur couvre tout : un ver qui en entend un a cette distance s'y rend. */
    static final double THUMPER_PULL = 96.0d;

    /** Une source de bruit : une entite (joueur, moissonneuse) ou un point (eruption, marteleur). */
    public static final class Source {

        final UUID entity;
        BlockPos spot;
        double attention;
        long lastNoise;
        long expires = Long.MAX_VALUE;
        boolean thumper;
        boolean warned;
        /** Jusqu'a ce moment, la source ne se tait pas (le ver appele par un operateur). */
        long forcedUntil;

        Source(UUID entity, BlockPos spot) {
            this.entity = entity;
            this.spot = spot;
        }

        /** La position a viser maintenant ; {@code null} si l'entite n'est plus la. */
        BlockPos position(ServerLevel level) {
            if (entity == null) {
                return spot;
            }
            Entity found = level.getEntity(entity);
            if (found == null || !found.isAlive() || found.level() != level) {
                return null;
            }
            spot = found.blockPosition();
            return spot;
        }

        Entity entity(ServerLevel level) {
            return entity == null ? null : level.getEntity(entity);
        }

        /** Vrai si elle s'est tue depuis plus de {@link #LOST_TICKS}. */
        boolean silent(ServerLevel level) {
            long now = level.getGameTime();
            return now >= forcedUntil && (now - lastNoise > LOST_TICKS || now > expires);
        }
    }

    private static final Map<UUID, Source> ENTITIES = new HashMap<>();
    private static final Map<Long, Source> SPOTS = new HashMap<>();
    private static final List<long[]> QUIET = new ArrayList<>();
    private static final Map<UUID, Vec3> STEPS = new HashMap<>();
    private static long lastListen = -1L;

    private WormNoise() {
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        ENTITIES.clear();
        SPOTS.clear();
        QUIET.clear();
        STEPS.clear();
        lastListen = -1L;
    }

    // -- Le terrain des vers -------------------------------------------------------------

    /**
     * Le sable du grand erg : la ou un ver passe et frappe. Ni roche, ni sel, ni Arrakeen,
     * ni la ou le monde ne vit pas (un ver n'y bougerait plus).
     */
    static boolean wormGround(ServerLevel level, int x, int z) {
        if (Math.hypot(x, z) < SpiceBlows.SAFE_RADIUS
                || ArrakisRelief.column(x, z).region() != ArrakisRelief.Region.PLAINS
                || !level.isPositionEntityTicking(new BlockPos(x, 0, z))) {
            return false;
        }
        return sandy(level.getBlockState(new BlockPos(x, sandTop(level, x, z) - 1, z)));
    }

    /** Le dessus du sable en (x, z), le premier bloc d'air : un marteleur plante ne compte pas. */
    static int sandTop(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        if (level.getBlockState(new BlockPos(x, y - 1, z)).is(ArrakisContent.THUMPER.get())) {
            y--;
        }
        return y;
    }

    static boolean sandy(BlockState state) {
        return state.is(Blocks.SAND) || ArrakisContent.isSpice(state);
    }

    /** Vrai si ce qui se tient en {@code pos} bat le sable de l'erg (et non un bloc pose ou la roche). */
    static boolean onErg(ServerLevel level, BlockPos pos) {
        return level.dimension() == DoHyArrakis.DIMENSION && Math.hypot(pos.getX(), pos.getZ()) >= SpiceBlows.SAFE_RADIUS
                && ArrakisRelief.column(pos.getX(), pos.getZ()).region() == ArrakisRelief.Region.PLAINS
                && (sandy(level.getBlockState(pos.below())) || sandy(level.getBlockState(pos.below(2))));
    }

    // -- Le bruit -------------------------------------------------------------------------

    /** Une entite fait du bruit sur le sable : {@code amount} d'attention de plus. */
    public static void emit(ServerLevel level, Entity entity, double amount) {
        if (amount <= 0.0d || !onErg(level, entity.blockPosition())) {
            return;
        }
        Source source = ENTITIES.computeIfAbsent(entity.getUUID(), id -> new Source(id, entity.blockPosition()));
        source.spot = entity.blockPosition();
        source.attention += amount;
        source.lastNoise = level.getGameTime();
    }

    /** Un point fait du bruit (eruption, marteleur) pendant {@code lifetime} ticks au plus. */
    public static void emitAt(ServerLevel level, BlockPos pos, double amount, int lifetime, boolean thumper) {
        if (!onErg(level, pos.above())) {
            return;
        }
        Source source = SPOTS.computeIfAbsent(pos.asLong(), key -> new Source(null, pos.immutable()));
        source.attention += amount;
        source.lastNoise = level.getGameTime();
        source.expires = Math.max(source.expires == Long.MAX_VALUE ? 0 : source.expires,
                level.getGameTime() + lifetime);
        source.thumper |= thumper;
    }

    /** L'attention qu'une entite a attiree, de 0 a 1 (le seuil). */
    public static double attention(Entity entity) {
        Source source = ENTITIES.get(entity.getUUID());
        return source == null ? 0.0d : Math.min(1.0d, source.attention / THRESHOLD);
    }

    /** Un marteleur qui bat a portee d'oreille de ce ver, ou {@code null}. */
    static Source thumperNear(ServerLevel level, BlockPos at) {
        Source best = null;
        double bestDistance = THUMPER_PULL * THUMPER_PULL;
        for (Source source : SPOTS.values()) {
            if (source.thumper && !source.silent(level)) {
                double distance = source.spot.distSqr(at);
                if (distance < bestDistance) {
                    best = source;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /** {@code /arrakis ver} : la source de ce joueur, pleine, pour qu'un ver vienne tout de suite. */
    static Source summon(ServerPlayer player) {
        Source source = ENTITIES.computeIfAbsent(player.getUUID(), id -> new Source(id, player.blockPosition()));
        source.attention = 0.0d;
        source.lastNoise = player.level().getGameTime();
        source.forcedUntil = source.lastNoise + 60 * 20;
        source.spot = player.blockPosition();
        return source;
    }

    /** Un ver vient de frapper ici : les parages restent calmes un moment. */
    static void quiet(ServerLevel level, BlockPos at) {
        QUIET.add(new long[] {at.getX(), at.getZ(), level.getGameTime() + COOLDOWN});
    }

    private static boolean isQuiet(ServerLevel level, BlockPos at) {
        long now = level.getGameTime();
        QUIET.removeIf(entry -> entry[2] < now);
        for (long[] entry : QUIET) {
            if (Math.hypot(entry[0] - at.getX(), entry[1] - at.getZ()) < QUIET_RADIUS) {
                return true;
            }
        }
        return false;
    }

    // -- Chaque seconde ---------------------------------------------------------------------

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == DoHyArrakis.DIMENSION
                && level.getGameTime() % 20 == 0 && level.getGameTime() != lastListen) {
            // l'heure fige (/tick freeze) ne compte qu'une fois
            lastListen = level.getGameTime();
            listen(level);
        }
    }

    private static void listen(ServerLevel level) {
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            Vec3 position = player.position();
            Vec3 before = STEPS.put(player.getUUID(), position);
            if (before == null || player.isSpectator() || player.isCreative() || !player.isAlive()
                    || player.getVehicle() != null || !player.onGround() || player.isShiftKeyDown()) {
                continue;
            }
            double moved = Math.hypot(position.x - before.x, position.z - before.z);
            if (moved > 0.5d && moved < 30.0d) {
                emit(level, player, player.isSprinting() ? SPRINT : WALK);
            }
        }
        for (SpiceHarvester harvester : level.getEntities(ArrakisContent.HARVESTER.get(), Entity::isAlive)) {
            double amount = harvester.working() && harvester.fuel() > 0 ? HARVESTER_WORKING
                    : harvester.movedWithin(20) ? HARVESTER_MOVING : 0.0d;
            emit(level, harvester, amount);
            Source source = ENTITIES.get(harvester.getUUID());
            if (source != null) {
                if (!source.warned && source.attention >= THRESHOLD * 2.0d / 3.0d) {
                    source.warned = true;
                    harvester.warnDriver("Le desert ecoute ta moissonneuse : ferme la bouche et arrete-toi, "
                            + "ou un ver viendra.");
                } else if (source.attention < THRESHOLD / 3.0d) {
                    source.warned = false;
                }
            }
        }
        decay(level, now, ENTITIES.values().iterator(), true);
        decay(level, now, SPOTS.values().iterator(), false);
        dispatch(level);
    }

    private static void decay(ServerLevel level, long now, Iterator<Source> sources, boolean entities) {
        while (sources.hasNext()) {
            Source source = sources.next();
            if (now - source.lastNoise > 20) {
                source.attention = Math.max(0.0d, source.attention - DECAY);
            }
            boolean gone = entities ? source.entity(level) == null : now > source.expires;
            if (gone && source.attention <= 0.0d) {
                sources.remove();
            }
        }
    }

    private static void dispatch(ServerLevel level) {
        int active = level.getEntities(ArrakisContent.SANDWORM.get(), Entity::isAlive).size();
        if (active >= MAX_WORMS) {
            return;
        }
        List<Source> loud = new ArrayList<>();
        for (Source source : ENTITIES.values()) {
            if (source.attention >= THRESHOLD) {
                loud.add(source);
            }
        }
        for (Source source : SPOTS.values()) {
            if (source.attention >= THRESHOLD && level.getGameTime() <= source.expires) {
                loud.add(source);
            }
        }
        loud.sort(Comparator.comparingDouble((Source s) -> -s.attention));
        for (Source source : loud) {
            BlockPos at = source.position(level);
            if (at == null || isQuiet(level, at) || SandWorm.near(level, at, QUIET_RADIUS)) {
                source.attention = Math.min(source.attention, THRESHOLD);
                continue;
            }
            if (SandWorm.dispatch(level, source)) {
                source.attention = 0.0d;
                source.warned = false;
                quiet(level, at);
                if (++active >= MAX_WORMS) {
                    return;
                }
            }
        }
    }

    /** Un message aux joueurs proches d'un point, dans le chat. */
    static void tell(ServerLevel level, BlockPos at, double radius, String text, ChatFormatting color) {
        for (ServerPlayer player : level.players()) {
            if (Math.hypot(player.getX() - at.getX(), player.getZ() - at.getZ()) <= radius) {
                player.sendSystemMessage(Component.literal(text).withStyle(color));
            }
        }
    }
}

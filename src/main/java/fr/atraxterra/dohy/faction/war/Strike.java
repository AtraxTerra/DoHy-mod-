package fr.atraxterra.dohy.faction.war;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.earth.EarthNations;
import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.absence.AbsenceJournal;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Une frappe de missile : ce que fait une nation devant une base imprenable,
 * au lieu de lever le siege.
 *
 * <ol>
 *   <li><b>La preparation</b> (une minute au plus) : la zone se charge, son
 *       terrain est releve ({@link StrikeTerrain}). Une charge nucleaire qui
 *       toucherait le claim d'un autre joueur est remplacee par une charge
 *       classique.</li>
 *   <li><b>L'alerte</b> : 30 secondes avant l'impact, un titre, une sirene,
 *       une barre de compte a rebours, pour toi et pour les joueurs autour de
 *       ta base ; le missile part 15 secondes plus tard, de 480 blocs, depuis
 *       les terres de la nation.</li>
 *   <li><b>Le vol</b> : celui d'un missile de NTM, visible de loin, que les
 *       radars voient et qu'un antimissile peut abattre. En descente, il est
 *       guide sur ta base.</li>
 *   <li><b>L'impact</b> ({@link NtmArsenal#detonate}) : les blocs volent, les
 *       joueurs proches sont touches, mais les conteneurs et les machines
 *       restent, rien ne tombe au sol ; le terrain reviendra cinq heures plus
 *       tard.</li>
 *   <li><b>La retombee</b> (dix secondes, ou la fin de l'explosion
 *       nucleaire) : ce qui brule encore est eteint, l'abri est repose.</li>
 * </ol>
 *
 * <p><b>Jamais en ton absence</b> : te deconnecter avant l'impact annule le
 * tir (le missile en vol disparait) ; la guerre continue.
 */
final class Strike {

    enum Phase {
        PREPARE, ALERT, FLIGHT, SETTLE, EXTINGUISH, DONE
    }

    /** Les chiffres de la frappe : en ticks, sauf mention. */
    static final int PREPARE_LIMIT = 60 * 20;
    static final int ALERT_TICKS = 15 * 20;
    static final int LAUNCH_DISTANCE = 480;
    static final int LAUNCH_HEIGHT = 30;
    static final int LAUNCH_HOLD = 80;
    static final int SETTLE_TICKS = 10 * 20;
    static final int NUKE_QUIET = 5 * 20;
    static final int NUKE_LIMIT = 5 * 60 * 20;
    /** En descente, sous cette hauteur au-dessus de la cible, le missile est guide sur elle. */
    static final double GUIDE_FROM = 260.0d;
    static final String TAG = "dohy_frappe";

    final UUID id = UUID.randomUUID();
    final String key;
    final UUID player;
    final String playerName;
    final int nation;
    final NationTraits traits;
    final ServerLevel level;
    /** La colonne visee ; y : le dessus de la base. */
    final BlockPos target;
    NtmArsenal.Warhead warhead;
    long cost;
    final StrikeTerrain.Ledger terrain;
    final ServerBossEvent bar;

    Phase phase = Phase.PREPARE;
    int tick;
    int phaseTick;

    private final Map<Long, Boolean> forced = new LinkedHashMap<>();
    /** Les chunks autour du missile en vol, tenus a part : ils ne se melent pas a ceux de la zone. */
    private final UUID follower = UUID.randomUUID();
    private final LongOpenHashSet following = new LongOpenHashSet();
    private final Set<UUID> told = new HashSet<>();
    private final Set<UUID> guarded = new HashSet<>();
    private long launchChunk = Long.MIN_VALUE;

    UUID missile;
    /** Le vol prevu, en ticks : pour le compte a rebours. */
    int flightTicks;
    int flown;
    private double lastAbove = Double.MAX_VALUE;
    private Vec3 lastPos;
    private double angle;
    private String origin = "";

    Vec3 impact;
    UUID nuke;
    private int quiet;
    StrikeTerrain.Shelter shelter;
    boolean struck;
    /** Comment la frappe a fini : « frappe », « intercepte », « annule », « rate ». */
    String result = "";

    private long[] fires;
    private int fireIndex;
    int extinguished;

    Strike(ServerPlayer target, int nation, ServerLevel level, BlockPos vault, NtmArsenal.Warhead warhead, long cost) {
        this.player = target.getUUID();
        this.playerName = target.getGameProfile().name();
        this.nation = nation;
        this.traits = NationTraits.of(nation);
        this.level = level;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, vault.getX(), vault.getZ());
        this.target = new BlockPos(vault.getX(), top, vault.getZ());
        this.warhead = warhead;
        this.cost = cost;
        this.key = "frappe-" + Long.toString(System.currentTimeMillis(), 36) + "-" + id.toString().substring(0, 6);
        this.terrain = StrikeTerrain.open(key, traits.name() + " sur " + playerName, level, player, this.target.getX(),
                this.target.getZ(), warhead.blast() + 12, this.target.getY() - warhead.depth() - 4);
        this.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(warhead.title()),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    }

    // -- Les acces -------------------------------------------------------------------------------

    ServerPlayer target() {
        return level.getServer().getPlayerList().getPlayer(player);
    }

    boolean finished() {
        return phase == Phase.DONE;
    }

    /** Avant l'impact : le tir peut encore etre annule. */
    boolean pending() {
        return phase == Phase.PREPARE || phase == Phase.ALERT || phase == Phase.FLIGHT;
    }

    /** L'explosion et sa retombee : rien de ce qu'elle casse ne tombe au sol. */
    boolean blasting(ServerLevel at, BlockPos pos) {
        if (impact == null || at != level || phase != Phase.SETTLE && phase != Phase.EXTINGUISH) {
            return false;
        }
        double reach = warhead.blast() + 8.0d;
        return pos.distToCenterSqr(impact) <= reach * reach;
    }

    /** Une explosion nucleaire en cours, ou ce point est menace. */
    boolean irradiating(ServerLevel at, Vec3 pos) {
        if (impact == null || at != level || !warhead.nuclear() || phase != Phase.SETTLE) {
            return false;
        }
        double reach = warhead.danger();
        return pos.distanceToSqr(impact) <= reach * reach;
    }

    /** Pour le menu : ou en est la frappe, en une ligne. */
    String status() {
        return switch (phase) {
            case PREPARE -> "Ils pointent " + warhead.one() + " sur ta base";
            case ALERT -> "Lancement dans " + Math.max(0, (ALERT_TICKS - phaseTick) / 20) + " s";
            case FLIGHT -> "En vol, impact dans " + Math.max(0, (flightTicks - flown) / 20) + " s";
            case SETTLE, EXTINGUISH -> "Impact : la poussiere retombe";
            default -> "Fini";
        };
    }

    // -- Les messages ----------------------------------------------------------------------------

    void tell(String text, ChatFormatting color) {
        ServerPlayer victim = target();
        if (victim != null) {
            victim.sendSystemMessage(Component.literal("[" + traits.name() + "] ")
                    .withStyle(Style.EMPTY.withColor(traits.color().getTextColor()).withBold(true))
                    .append(Component.literal(text).withStyle(Style.EMPTY.withColor(color).withBold(false))));
        }
    }

    private static void title(ServerPlayer player, String title, String subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 15));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)
                .withStyle(ChatFormatting.GOLD)));
    }

    /** Les joueurs a portee du danger, sauf la cible. */
    private List<ServerPlayer> nearby() {
        double reach = warhead.danger() + 48.0d;
        return level.players().stream()
                .filter(other -> !other.getUUID().equals(player) && !other.isSpectator()
                        && Math.hypot(other.getX() - target.getX(), other.getZ() - target.getZ()) <= reach)
                .toList();
    }

    /** « a l'est », « au sud-ouest »... de ta base vers ce cap. */
    private static String bearing(double radians) {
        String[] names = {"de l'est", "du sud-est", "du sud", "du sud-ouest", "de l'ouest", "du nord-ouest",
                "du nord", "du nord-est"};
        return names[Math.floorMod((int) Math.round(Math.toDegrees(radians) / 45.0d), 8)];
    }

    private void refreshBar() {
        int total;
        int left;
        if (phase == Phase.ALERT) {
            total = ALERT_TICKS + Math.max(1, flightTicks);
            left = ALERT_TICKS - phaseTick + flightTicks;
            bar.setName(Component.literal(warhead.title() + " " + traits.de() + " \u00B7 lancement dans "
                    + Math.max(0, (ALERT_TICKS - phaseTick) / 20) + " s"));
        } else if (phase == Phase.FLIGHT) {
            total = ALERT_TICKS + Math.max(1, flightTicks);
            left = Math.max(0, flightTicks - flown);
            bar.setName(Component.literal(warhead.title() + " " + traits.de() + " \u00B7 impact dans "
                    + left / 20 + " s"));
        } else {
            return;
        }
        bar.setProgress((float) Mth.clamp((double) left / total, 0.0d, 1.0d));
        ServerPlayer victim = target();
        if (victim != null && !bar.getPlayers().contains(victim)) {
            bar.addPlayer(victim);
        }
        for (ServerPlayer other : nearby()) {
            if (!bar.getPlayers().contains(other)) {
                bar.addPlayer(other);
            }
            if (told.add(other.getUUID())) {
                title(other, "ALERTE MISSILE", "Base de " + playerName + " \u00B7 eloigne-toi de "
                        + warhead.danger() + " blocs");
                other.sendSystemMessage(FactionApi.prefix().append(Component.literal(warhead.sentence() + " "
                        + traits.de() + " vise la base de " + playerName + ", tout pres de toi. Eloigne-toi d'au moins "
                        + warhead.danger() + " blocs.").withStyle(ChatFormatting.RED)));
            }
        }
    }

    // -- Le tick ---------------------------------------------------------------------------------

    void tick() {
        tick++;
        phaseTick++;
        switch (phase) {
            case PREPARE -> tickPrepare();
            case ALERT -> tickAlert();
            case FLIGHT -> tickFlight();
            case SETTLE -> tickSettle();
            case EXTINGUISH -> tickExtinguish();
            case DONE -> {
            }
        }
    }

    private void phase(Phase next) {
        phase = next;
        phaseTick = 0;
    }

    private void tickPrepare() {
        if (phaseTick == 1) {
            forceArea();
        }
        if (!StrikeTerrain.prepare(level, terrain, phaseTick >= PREPARE_LIMIT)) {
            return;
        }
        // Le nucleaire ne connait pas les claims : il ne part pas si un voisin est dans le cratere.
        while (warhead.nuclear() && foreignClaims(warhead.blast() + 8)) {
            NtmArsenal.Warhead lighter = warhead.cheaper();
            if (lighter == null) {
                break;
            }
            refund(warhead.cost - lighter.cost);
            DoHy.LOGGER.info("Guerre : {} renonce a {} (un voisin de {} est trop pres)", traits.name(),
                    warhead.one(), playerName);
            warhead = lighter;
        }
        StrikeTerrain.arm(level.getServer(), terrain);
        phase(Phase.ALERT);
        alert();
    }

    private void tickAlert() {
        if (phaseTick % 20 == 0) {
            refreshBar();
        }
        if (phaseTick >= ALERT_TICKS) {
            launch();
        }
    }

    private void tickFlight() {
        flown++;
        if (flown == LAUNCH_HOLD) {
            releaseLaunch();
        }
        Entity entity = missile == null ? null : level.getEntity(missile);
        if (entity == null || entity.isRemoved()) {
            vanished();
            return;
        }
        if (flown > flightTicks + 600) {
            entity.discard();
            DoHy.LOGGER.warn("Guerre : le missile {} contre {} ne touche rien, il est retire", traits.name(),
                    playerName);
            abort("rate", target());
            return;
        }
        if (flown % 20 == 0) {
            refreshBar();
        }
    }

    private void tickSettle() {
        boolean over;
        if (warhead.nuclear() && nuke != null) {
            Entity blast = level.getEntity(nuke);
            if (blast != null && !blast.isRemoved() && phaseTick < NUKE_LIMIT) {
                quiet = 0;
                return;
            }
            if (blast != null && !blast.isRemoved()) {
                blast.discard();
                DoHy.LOGGER.warn("Guerre : l'explosion nucleaire contre {} dure trop, elle est arretee", playerName);
            }
            over = ++quiet >= NUKE_QUIET;
        } else {
            over = phaseTick >= SETTLE_TICKS;
        }
        if (!over) {
            return;
        }
        if (warhead.burns() && impact != null) {
            fires = terrain.chunks.keySet().toLongArray();
            fireIndex = 0;
            phase(Phase.EXTINGUISH);
            return;
        }
        finish();
    }

    /** Ce qui brule encore dans la zone s'eteint : un incendie de foret n'y reviendrait pas. */
    private void tickExtinguish() {
        long deadline = System.nanoTime() + 4_000_000L;
        int reach = warhead.blast() + 12;
        int minY = Math.max(level.getMinY(), (int) impact.y - reach);
        int maxY = Math.min(level.getMaxY(), (int) impact.y + reach);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (fireIndex < fires.length && System.nanoTime() < deadline) {
            long chunkKey = fires[fireIndex++];
            LevelChunk chunk = level.getChunkSource().getChunkNow(StrikeTerrain.keyX(chunkKey),
                    StrikeTerrain.keyZ(chunkKey));
            if (chunk == null) {
                continue;
            }
            for (int index = Math.max(0, chunk.getSectionIndex(minY));
                 index <= Math.min(chunk.getSectionsCount() - 1, chunk.getSectionIndex(maxY)); index++) {
                LevelChunkSection section = chunk.getSection(index);
                if (section.hasOnlyAir() || !section.maybeHas(state -> state.getBlock() instanceof BaseFireBlock)) {
                    continue;
                }
                int y0 = chunk.getSectionYFromSectionIndex(index) << 4;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (state.getBlock() instanceof BaseFireBlock) {
                                pos.set(chunk.getPos().getMinBlockX() + x, y0 + y, chunk.getPos().getMinBlockZ() + z);
                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), StrikeTerrain.FLAGS);
                                extinguished++;
                            }
                        }
                    }
                }
            }
        }
        if (fireIndex >= fires.length || phaseTick > 600) {
            finish();
        }
    }

    // -- La preparation --------------------------------------------------------------------------

    /** La zone reste chargee jusqu'a la fin ; le coeur, la ou tombe le missile, tourne. */
    private void forceArea() {
        for (int cx = terrain.fromX(); cx <= terrain.toX(); cx++) {
            for (int cz = terrain.fromZ(); cz <= terrain.toZ(); cz++) {
                if (terrain.covers(cx, cz)) {
                    boolean ticking = Math.abs(cx - (target.getX() >> 4)) <= 2
                            && Math.abs(cz - (target.getZ() >> 4)) <= 2;
                    force(StrikeTerrain.key(cx, cz), ticking);
                }
            }
        }
    }

    private void force(long chunk, boolean ticking) {
        if (!forced.containsKey(chunk) && DoHyWar.TICKETS.forceChunk(level, id, StrikeTerrain.keyX(chunk),
                StrikeTerrain.keyZ(chunk), true, ticking)) {
            forced.put(chunk, ticking);
        }
    }

    private void releaseTickets() {
        for (Map.Entry<Long, Boolean> entry : forced.entrySet()) {
            DoHyWar.TICKETS.forceChunk(level, id, StrikeTerrain.keyX(entry.getKey()),
                    StrikeTerrain.keyZ(entry.getKey()), false, entry.getValue());
        }
        forced.clear();
        releaseLaunch();
        follow(null);
    }

    /** Tient charges (et en marche) les neuf chunks autour du missile ; {@code null} les rend tous. */
    private void follow(Entity entity) {
        LongOpenHashSet wanted = new LongOpenHashSet();
        if (entity != null) {
            int cx = entity.getBlockX() >> 4;
            int cz = entity.getBlockZ() >> 4;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    wanted.add(StrikeTerrain.key(cx + dx, cz + dz));
                }
            }
        }
        for (long chunk : following.toLongArray()) {
            if (!wanted.contains(chunk)) {
                DoHyWar.TICKETS.forceChunk(level, follower, StrikeTerrain.keyX(chunk), StrikeTerrain.keyZ(chunk),
                        false, true);
                following.remove(chunk);
            }
        }
        for (long chunk : wanted.toLongArray()) {
            if (!following.contains(chunk) && DoHyWar.TICKETS.forceChunk(level, follower, StrikeTerrain.keyX(chunk),
                    StrikeTerrain.keyZ(chunk), true, true)) {
                following.add(chunk);
            }
        }
    }

    private void releaseLaunch() {
        if (launchChunk != Long.MIN_VALUE) {
            DoHyWar.TICKETS.forceChunk(level, id, StrikeTerrain.keyX(launchChunk), StrikeTerrain.keyZ(launchChunk),
                    false, true);
            launchChunk = Long.MIN_VALUE;
        }
    }

    /** Un claim d'un autre joueur (pas une base ennemie) a moins de {@code radius} blocs de la cible. */
    private boolean foreignClaims(int radius) {
        for (int cx = (target.getX() - radius) >> 4; cx <= (target.getX() + radius) >> 4; cx++) {
            for (int cz = (target.getZ() - radius) >> 4; cz <= (target.getZ() + radius) >> 4; cz++) {
                ClaimOwner owner = FactionApi.claimAt(level, new BlockPos((cx << 4) + 8, target.getY(), (cz << 4) + 8));
                if (owner.claimed() && !owner.enemy() && !owner.ownedBy(player)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void refund(long amount) {
        ServerPlayer victim = target();
        if (victim != null && amount > 0L) {
            WarStrategy.refund(victim, nation, amount);
        }
    }

    // -- L'alerte et le lancement ------------------------------------------------------------------

    private void alert() {
        angle = heading();
        // Le missile part d'un chunk charge : il tient ensuite le sien (NTM).
        int lx = (int) Math.floor(target.getX() + Math.cos(angle) * LAUNCH_DISTANCE);
        int lz = (int) Math.floor(target.getZ() + Math.sin(angle) * LAUNCH_DISTANCE);
        long chunk = StrikeTerrain.key(lx >> 4, lz >> 4);
        if (DoHyWar.TICKETS.forceChunk(level, id, lx >> 4, lz >> 4, true, true)) {
            launchChunk = chunk;
        }
        flightTicks = estimateFlight(LAUNCH_DISTANCE, LAUNCH_HEIGHT);
        int seconds = (ALERT_TICKS + flightTicks) / 20;
        ServerPlayer victim = target();
        if (victim != null) {
            title(victim, "ALERTE MISSILE", warhead.title() + " \u00B7 impact dans " + seconds + " s");
            victim.sendSystemMessage(FactionApi.prefix().append(Component.literal("Alerte : " + warhead.one()
                    + " " + traits.de() + " vise ta base, impact dans " + seconds + " s. Eloigne-toi de "
                    + warhead.danger() + " blocs.").withStyle(ChatFormatting.RED)));
        }
        level.playSound(null, target, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 64.0f, 0.7f);
        refreshBar();
        DoHy.LOGGER.info("Guerre : {} {} sur la base de {} ({} {} {}), alerte donnee",
                traits.verb("tire", "tirent"), warhead.one(), playerName, target.getX(), target.getY(), target.getZ());
    }

    private void launch() {
        double x = target.getX() + 0.5d + Math.cos(angle) * LAUNCH_DISTANCE;
        double z = target.getZ() + 0.5d + Math.sin(angle) * LAUNCH_DISTANCE;
        // Au-dessus du sol du lieu de lancement : un missile ne part pas de l'interieur d'une montagne.
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        double y = Math.min(level.getMaxY() - 8, Math.max(target.getY() + LAUNCH_HEIGHT, ground + 8));
        Entity entity = NtmArsenal.launch(level, warhead, x, y, z, target);
        if (entity == null) {
            DoHy.LOGGER.warn("Guerre : le tir {} {} ne part pas", warhead.of(), traits.de());
            abort("rate", target());
            return;
        }
        entity.addTag(TAG);
        missile = entity.getUUID();
        flown = 0;
        phase(Phase.FLIGHT);
        tell("Missile lance " + bearing(angle) + origin + " : impact dans " + flightTicks / 20 + " s.",
                ChatFormatting.DARK_RED);
        level.playSound(null, this.target.getX(), this.target.getY(), this.target.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 16.0f, 0.5f);
        refreshBar();
    }

    /** Le cap d'ou vient le missile : les terres de la nation sur la carte de la Terre, sinon le sien. */
    private double heading() {
        MinecraftServer server = level.getServer();
        EarthData earth = EarthData.get();
        if (EarthNations.active() && earth != null && earth.covers(level)) {
            List<List<Integer>> holdings = EarthNations.rivalHoldings(server);
            if (nation < holdings.size() && !holdings.get(nation).isEmpty()) {
                int country = holdings.get(nation).getFirst();
                int[] heart = EarthNations.heart(server, country);
                if (heart != null && Math.hypot(heart[0] - target.getX(), heart[1] - target.getZ()) > 64.0d) {
                    origin = ", depuis " + EarthNations.countryName(country);
                    return Math.atan2(heart[1] - target.getZ(), heart[0] - target.getX());
                }
            }
        }
        return Math.toRadians(nation * 137.5d + level.getRandom().nextInt(40));
    }

    /**
     * La duree du vol, en rejouant celui de NTM ({@code EntityMissileBaseNT.fly}) :
     * une montee qui accelere vers la cible, une descente qui freine.
     */
    static int estimateFlight(double distance, double height) {
        double decel = 2.0d / distance;
        double accel = 1.0d / distance;
        double x = 0.0d;
        double y = height;
        double dx = 0.0d;
        double dy = 2.0d;
        double v = 0.0d;
        for (int tick = 1; tick < 4000; tick++) {
            double nx = x + dx * v;
            double ny = y + dy * v;
            if (ny <= 0.0d) {
                return tick;
            }
            x = nx;
            y = ny;
            if (v < 4.0d) {
                v += Mth.clamp(tick / 60.0d * 0.05d, 0.0d, 0.05d);
            }
            double next = dy - decel * v;
            if (next > 0.0d) {
                dx += accel * v;
            } else if (next < 0.0d) {
                dx -= accel * v;
            }
            dy = next;
        }
        return 600;
    }

    // -- Le vol et l'impact ------------------------------------------------------------------------

    /**
     * Avant que le missile ne bouge (debut du tick du monde) : en descente, il est
     * guide sur la colonne de la base ; s'il touche dans ce tick (le meme
     * calcul que NTM), l'impact part d'ici.
     */
    void guide() {
        if (phase != Phase.FLIGHT || missile == null) {
            return;
        }
        Entity entity = level.getEntity(missile);
        if (entity == null || entity.isRemoved()) {
            return;
        }
        follow(entity);
        Vec3 delta = entity.getDeltaMovement();
        double above = entity.getY() - target.getY();
        double velocity = NtmArsenal.velocity(entity);
        lastAbove = above;
        lastPos = entity.position();
        if (delta.y < 0.0d && above < GUIDE_FROM) {
            double dx = target.getX() + 0.5d - entity.getX();
            double dz = target.getZ() + 0.5d - entity.getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            double fall = Math.max(1.0d, -delta.y * velocity);
            double left = Math.max(1.0d, above / fall);
            if (length > 0.05d) {
                double move = Math.min(length, Mth.clamp(length / left * 1.5d, 0.25d, 3.0d));
                entity.setPos(entity.getX() + dx / length * move, entity.getY(), entity.getZ() + dz / length * move);
            }
            entity.setDeltaMovement(0.0d, delta.y, 0.0d);
            delta = entity.getDeltaMovement();
        }
        Vec3 from = entity.position();
        Vec3 to = from.add(delta.scale(velocity));
        if (to.distanceToSqr(from) < 1.0E-6d) {
            return;
        }
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                entity));
        if (hit.getType() != HitResult.Type.MISS) {
            impact(entity, hit);
        }
    }

    private void impact(Entity entity, BlockHitResult hit) {
        Vec3 at = hit.getLocation();
        entity.discard();
        if (Math.hypot(at.x - target.getX() - 0.5d, at.z - target.getZ() - 0.5d) > terrain.radius - warhead.blast()) {
            // Loin de la base, hors du releve : la charge ne part pas, rien ne serait revenu.
            DoHy.LOGGER.warn("Guerre : le tir {} {} touche loin de sa cible ({} {}), la charge ne part pas",
                    warhead.of(), traits.de(), Math.round(at.x), Math.round(at.z));
            StrikeTerrain.discard(level.getServer(), terrain);
            result = "rate";
            ServerPlayer victim = target();
            if (victim != null) {
                WarStrategy.struck(victim, nation, result, warhead, cost);
            }
            finish();
            return;
        }
        impact = at;
        phase(Phase.SETTLE);
        bar.removeAllPlayers();
        BlockPos center = BlockPos.containing(at);
        if (warhead.nuclear()) {
            shelter = StrikeTerrain.Shelter.lift(level, key, center, warhead.blast() + 2);
            guard();
        }
        Entity shield = NtmArsenal.shield(level, at.x, at.y, at.z, "le missile " + traits.de(), player);
        boolean[] done = new boolean[1];
        Entity blast = NtmArsenal.detonate(level, warhead, at.x, at.y, at.z, shield, done);
        ServerPlayer victim = target();
        if (!done[0]) {
            DoHy.LOGGER.warn("Guerre : la charge {} n'a pas explose", warhead.of());
            if (shelter != null) {
                shelter.putBack(level.getServer());
                shelter = null;
            }
            StrikeTerrain.discard(level.getServer(), terrain);
            result = "rate";
            if (victim != null) {
                WarStrategy.struck(victim, nation, result, warhead, cost);
            }
            finish();
            return;
        }
        if (blast != null) {
            blast.addTag(TAG);
            nuke = blast.getUUID();
        }
        long now = System.currentTimeMillis();
        long due = now + StrikeTerrain.RETURN_MS;
        StrikeTerrain.strike(level.getServer(), terrain, due);
        struck = true;
        result = "frappe";
        if (victim != null) {
            WarStrategy.struck(victim, nation, result, warhead, cost);
            victim.sendSystemMessage(FactionApi.prefix().append(Component.literal("Impact sur ta base ! Le terrain "
                    + "reviendra " + WarStrategy.when(due, now) + " ; ce que tu poses ou casses d'ici la reste.")
                    .withStyle(ChatFormatting.GOLD)));
        }
        for (ServerPlayer other : nearby()) {
            other.sendSystemMessage(FactionApi.prefix().append(Component.literal("Impact sur la base de " + playerName
                    + ". Le terrain reviendra " + WarStrategy.when(due, now) + ".").withStyle(ChatFormatting.GRAY)));
        }
        AbsenceJournal.post(level.getServer(), AbsenceJournal.NATIONS, traits.verb("bombarde", "bombardent")
                + " la base de " + playerName + " (" + warhead.label() + ")", Items.TNT);
        DoHy.LOGGER.info("Guerre : impact {} {} sur la base de {} en {} {} {}", warhead.of(), traits.de(),
                playerName, center.getX(), center.getY(), center.getZ());
    }

    /** Le missile a disparu sans que l'impact parte d'ici : abattu en vol, ou tombe tout seul. */
    private void vanished() {
        ServerPlayer victim = target();
        if (lastPos != null && lastAbove <= 24.0d) {
            // NTM l'a fait exploser lui-meme : le terrain reviendra quand meme.
            impact = lastPos;
            long now = System.currentTimeMillis();
            StrikeTerrain.strike(level.getServer(), terrain, now + StrikeTerrain.RETURN_MS);
            struck = true;
            result = "frappe";
            phase(Phase.SETTLE);
            bar.removeAllPlayers();
            if (victim != null) {
                WarStrategy.struck(victim, nation, result, warhead, cost);
            }
            return;
        }
        result = "intercepte";
        StrikeTerrain.discard(level.getServer(), terrain);
        if (victim != null) {
            WarStrategy.struck(victim, nation, result, warhead, cost);
        }
        for (ServerPlayer other : nearby()) {
            other.sendSystemMessage(FactionApi.prefix().append(Component.literal("Le missile " + traits.de()
                    + " a ete abattu en vol.").withStyle(ChatFormatting.GREEN)));
        }
        DoHy.LOGGER.info("Guerre : le tir {} {} contre {} est abattu en vol", warhead.of(), traits.de(), playerName);
        finish();
    }

    /** Les objets au sol d'une zone nucleaire ne brulent pas : l'equipement d'un joueur tombe y survit. */
    private void guard() {
        double reach = warhead.danger();
        AABB box = new AABB(impact.x - reach, impact.y - reach, impact.z - reach, impact.x + reach, impact.y + reach,
                impact.z + reach);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
            guard(item);
        }
    }

    void guard(ItemEntity item) {
        if (!item.isInvulnerable() && guarded.add(item.getUUID())) {
            item.setInvulnerable(true);
        }
    }

    private void unguard() {
        for (UUID uuid : guarded) {
            if (level.getEntity(uuid) instanceof ItemEntity item) {
                item.setInvulnerable(false);
            }
        }
        guarded.clear();
    }

    // -- La fin ----------------------------------------------------------------------------------

    private void finish() {
        if (shelter != null) {
            int count = shelter.putBack(level.getServer());
            DoHy.LOGGER.info("Guerre : {} blocs reposes apres l'explosion ({})", count, key);
            shelter = null;
        }
        unguard();
        releaseTickets();
        bar.removeAllPlayers();
        phase(Phase.DONE);
    }

    /**
     * Le tir n'aura pas lieu (ou plus) : le missile en vol disparait, le
     * releve est oublie s'il n'a pas servi, l'abri est repose.
     */
    void abort(String why, ServerPlayer victim) {
        if (phase == Phase.DONE) {
            return;
        }
        if (missile != null && impact == null) {
            Entity entity = level.getEntity(missile);
            if (entity != null) {
                entity.discard();
            }
        }
        if (!struck) {
            StrikeTerrain.discard(level.getServer(), terrain);
        }
        if (nuke != null) {
            Entity blast = level.getEntity(nuke);
            if (blast != null) {
                blast.discard();
            }
        }
        boolean before = pending();
        result = why;
        if (before && victim != null) {
            WarStrategy.struck(victim, nation, why, warhead, cost);
        }
        finish();
    }
}

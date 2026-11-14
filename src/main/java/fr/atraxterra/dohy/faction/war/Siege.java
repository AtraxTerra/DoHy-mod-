package fr.atraxterra.dohy.faction.war;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.palamod.golem.GuardianGolem;
import fr.atraxterra.dohy.palamod.pillage.PalaPillage;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Un siege : l'assaut d'une nation rivale contre ta base, du camp au retour du
 * terrain. Le cerveau tactique de l'armee.
 *
 * <ol>
 *   <li><b>Le plan</b> : les chunks autour de ton coffre se chargent, le
 *       planificateur ({@link SiegePlanner}) cherche par ou entrer et ou
 *       dresser le camp. Une base sans entree possible est imprenable : la
 *       nation leve le siege avant d'avoir commence.</li>
 *   <li><b>Le camp</b> (une minute) : tentes, feu, et l'etendard de la nation
 *       sur son mat ; la premiere vague arrive au son du cor.</li>
 *   <li><b>L'assaut</b> : l'armee avance le long du chemin, obstacle apres
 *       obstacle. Devant chacun, la troupe se range a distance ; un sapeur pose
 *       sa charge et court se mettre a l'abri, un ingenieur creuse, pose une
 *       echelle ou jette un pont. Les archers couvrent depuis l'arriere, les
 *       medecins soignent, les fantassins tiennent la breche. Ce que tu
 *       rebouches se reperce ; une case ou des soldats tombent fait chercher un
 *       autre chemin.</li>
 *   <li><b>Le pillage</b> : le coffre est pris quand des soldats le tiennent
 *       sans defenseur assez longtemps. Le tresorier vient alors, et vide ta
 *       banque, une part toutes les trois secondes, tant qu'on le laisse
 *       faire.</li>
 *   <li><b>La fuite du tresorier</b> : il repart au camp avec son escorte.
 *       Arrive, l'argent est perdu ; tue en route, il le rend.</li>
 *   <li><b>La retraite</b>, puis <b>le retour du terrain</b>.</li>
 * </ol>
 *
 * <p><b>Le moral</b> tient l'armee : chaque mort l'entame (plus pour un
 * sapeur que pour un fantassin, beaucoup pour le commandant), l'etendard
 * abattu le brise, une breche ouverte ou un coffre tenu le relevent, ta mort
 * l'exalte. Sous 35, la troupe hesite et ne monte plus a l'assaut ; sous 15,
 * c'est la deroute. Tant que l'etendard tient et que le moral suit, des
 * renforts arrivent du camp toutes les deux minutes.
 */
final class Siege {

    enum Phase {
        PLANNING, CAMP, ASSAULT, PILLAGE, EXTRACT, RETREAT, CLEANUP, DONE
    }

    /** Les chiffres du siege : en ticks, sauf mention. */
    static final int CAMP_TICKS = 1200;
    static final int WAVE_TICKS = 2400;
    static final int ASSAULT_LIMIT = 30 * 60 * 20;
    static final int RETREAT_LIMIT = 90 * 20;
    static final int MAX_ALIVE = 16;
    static final double ZONE = 4.5d;
    static final int CAPTURE_TICKS = 600;
    static final int DRAIN_PERIOD = 60;
    static final int FUSE = 70;

    /** Une file a part pour le planificateur : il peut chercher une seconde sans geler le serveur. */
    private static final ExecutorService PLANNER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "DoHy siege planner");
        thread.setDaemon(true);
        return thread;
    });

    final UUID id = UUID.randomUUID();
    final UUID player;
    final String playerName;
    final int nation;
    final NationTraits traits;
    final ServerLevel level;
    final BlockPos vault;
    final long started = System.currentTimeMillis();
    final SiegeTerrain.Ledger terrain;
    final RandomSource random;
    final int radius;

    Phase phase = Phase.PLANNING;
    int tick;
    int phaseTick;

    SiegePlanner.Plan plan;
    private CompletableFuture<SiegePlanner.Plan> pending;
    private final long salt;
    final double intel;
    final SiegePlanner.Capabilities capabilities;
    int replans;
    private int lastReplan = -10_000;
    final List<BlockPos> deaths = new ArrayList<>();

    int obstacle;
    private Job job;

    /** Les soldats deployes, par identifiant. */
    final Map<UUID, SoldierRole> units = new LinkedHashMap<>();
    final EnumMap<SoldierRole, Integer> reserve = new EnumMap<>(SoldierRole.class);
    final EnumMap<SoldierRole, Integer> killed = new EnumMap<>(SoldierRole.class);
    private final List<UUID> guards = new ArrayList<>();
    UUID commander;
    UUID paymaster;
    double morale;
    int deployed;
    int fallen;
    private int nextWave;
    int waves;
    boolean commanderDead;
    boolean bannerDown;
    boolean paymasterDead;

    BlockPos camp;
    BlockPos banner;

    double capture;
    long bankStart;
    long drained;
    long drainCap;
    private int drainTimer;
    private int extractTimer;
    long bounty;
    boolean escaped;
    boolean playerDied;
    private boolean bannerWarned;

    final ServerBossEvent bar;
    final ServerBossEvent loot;
    /** Comment le siege a fini : « pillage », « brise », « deroute », « imprenable », « suspendu », « temps ». */
    String result = "";
    private final Map<UUID, Integer> charges = new LinkedHashMap<>();
    /** Les chunks tenus charges par ce siege, et s'ils tournent. */
    private final Map<Long, Boolean> forced = new LinkedHashMap<>();
    private final int force;

    Siege(ServerLevel level, ServerPlayer target, int nation, BlockPos vault, int force, double intel,
          SiegePlanner.Capabilities capabilities, double morale) {
        this.player = target.getUUID();
        this.playerName = target.getGameProfile().name();
        this.nation = nation;
        this.traits = NationTraits.of(nation);
        this.level = level;
        this.vault = vault.immutable();
        this.terrain = Sieges.terrain(level);
        this.random = RandomSource.create(id.getLeastSignificantBits());
        this.salt = id.getMostSignificantBits();
        this.intel = intel;
        this.capabilities = capabilities;
        this.morale = morale;
        this.radius = radiusFor(target, vault);
        this.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal("Assaut " + traits.de()),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        this.loot = new ServerBossEvent(UUID.randomUUID(), Component.literal("Ta banque"),
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
        this.loot.setVisible(false);
        this.force = force;
    }

    /** Un chunk en un seul nombre : x dans les 32 bits bas, z dans les 32 bits hauts. */
    static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    static int keyX(long key) {
        return (int) key;
    }

    static int keyZ(long key) {
        return (int) (key >> 32);
    }

    /** La taille de la zone : assez pour sortir de tes claims, et y dresser un camp. */
    private static int radiusFor(ServerPlayer target, BlockPos vault) {
        String dimension = FactionApi.dimension(target.level());
        java.util.Set<Long> claimed = new java.util.HashSet<>();
        for (FactionData.Claim claim : FactionApi.data(target).claims()) {
            if (claim.dimension().equals(dimension)) {
                claimed.add(key(claim.x(), claim.z()));
            }
        }
        int nearest = 160;
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2.0d * i / 16.0d;
            for (int d = 0; d <= 160; d += 6) {
                int x = vault.getX() + (int) Math.round(Math.cos(angle) * d);
                int z = vault.getZ() + (int) Math.round(Math.sin(angle) * d);
                if (!claimed.contains(key(x >> 4, z >> 4))) {
                    nearest = Math.min(nearest, d);
                    break;
                }
            }
        }
        return Mth.clamp(nearest + 40, 48, 112);
    }

    // -- Les acces -------------------------------------------------------------------------------

    boolean finished() {
        return phase == Phase.CLEANUP || phase == Phase.DONE;
    }

    /** Le siege tient-il ta banque sous scelle (du camp a la fuite du tresorier) ? */
    boolean sealing() {
        return phase == Phase.CAMP || phase == Phase.ASSAULT || phase == Phase.PILLAGE || phase == Phase.EXTRACT;
    }

    List<SiegePlanner.Step> steps() {
        return plan == null ? List.of() : plan.steps();
    }

    int last() {
        return Math.max(0, steps().size() - 1);
    }

    ServerPlayer target() {
        return level.getServer().getPlayerList().getPlayer(player);
    }

    private Soldier soldier(UUID uuid) {
        return uuid != null && level.getEntity(uuid) instanceof Soldier soldier && soldier.isAlive() ? soldier : null;
    }

    List<Soldier> alive() {
        List<Soldier> out = new ArrayList<>();
        for (UUID uuid : units.keySet()) {
            Soldier soldier = soldier(uuid);
            if (soldier != null) {
                out.add(soldier);
            }
        }
        return out;
    }

    int reserveCount() {
        int total = 0;
        for (int count : reserve.values()) {
            total += count;
        }
        return total;
    }

    // -- Les messages ----------------------------------------------------------------------------

    void tell(String text, ChatFormatting color) {
        ServerPlayer target = target();
        if (target != null) {
            target.sendSystemMessage(Component.literal("[" + traits.name() + "] ")
                    .withStyle(Style.EMPTY.withColor(traits.color().getTextColor()))
                    .append(Component.literal(text).withStyle(color)));
        }
    }

    private void horn() {
        BlockPos at = camp == null ? vault : camp;
        level.playSound(null, at, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 48.0f, 1.0f);
    }

    /** « au nord », « au sud-ouest »... du coffre vers ce point. */
    String bearing(BlockPos to) {
        double angle = Math.toDegrees(Math.atan2(to.getZ() - vault.getZ(), to.getX() - vault.getX()));
        String[] names = {"a l'est", "au sud-est", "au sud", "au sud-ouest", "a l'ouest", "au nord-ouest",
                "au nord", "au nord-est"};
        int index = Math.floorMod((int) Math.round(angle / 45.0d), 8);
        return names[index];
    }

    // -- Le tick ---------------------------------------------------------------------------------

    void tick() {
        tick++;
        phaseTick++;
        switch (phase) {
            case PLANNING -> tickPlanning();
            case CAMP -> tickCamp();
            case ASSAULT, PILLAGE, EXTRACT -> tickBattle();
            case RETREAT -> tickRetreat();
            case CLEANUP -> tickCleanup();
            case DONE -> {
            }
        }
        if (tick % 20 == 0) {
            terrain.save();
            refreshBars();
        }
    }

    private void phase(Phase next) {
        phase = next;
        phaseTick = 0;
    }

    // -- Le plan ---------------------------------------------------------------------------------

    private void tickPlanning() {
        if (phaseTick == 1) {
            forceArea(false);
        }
        if (pending == null) {
            if (!areaLoaded() && phaseTick < 400) {
                return;
            }
            requestPlan();
            return;
        }
        if (!pending.isDone()) {
            if (phaseTick > 2400) {
                pending.cancel(true);
                conclude("imprenable");
            }
            return;
        }
        SiegePlanner.Plan found = pending.getNow(null);
        pending = null;
        if (found == null || found.steps().size() < 2) {
            DoHy.LOGGER.info("Guerre : {} ne trouve aucune entree chez {}", traits.name(), playerName);
            conclude("imprenable");
            return;
        }
        plan = found;
        DoHy.LOGGER.info("Guerre : plan de {} contre {} : {} pas, {} ({} cases explorees)", traits.name(),
                playerName, found.steps().size(), found.summary(), found.expanded());
        releaseTickets();
        forceArea(true);
        compose();
        buildCamp();
        phase(Phase.CAMP);
        horn();
        int distance = (int) Math.sqrt(camp.distSqr(vault));
        tell(traits.verb("dresse", "dressent") + " son camp a " + distance + " blocs " + bearing(camp)
                + " de ton coffre. L'assaut part dans " + (CAMP_TICKS / 20) + " s.", ChatFormatting.RED);
        tell("Plan de bataille : " + found.summary() + ". Abats l'etendard, le commandant ou le tresorier.",
                ChatFormatting.GRAY);
        ServerPlayer target = target();
        if (target != null) {
            bar.addPlayer(target);
            loot.addPlayer(target);
        }
    }

    private void requestPlan() {
        List<BlockPos> memory = List.copyOf(deaths);
        SiegePlanner.Snapshot snapshot = SiegePlanner.capture(level, vault, player, radius, memory, intel, salt);
        SiegePlanner.Capabilities caps = capabilities;
        pending = CompletableFuture.supplyAsync(() -> SiegePlanner.search(snapshot, caps), PLANNER);
    }

    /** Recalcule le plan depuis le front : une breche rebouchee, un mur qui ne cede pas, des morts. */
    private void replan(String why) {
        if (commanderDead || pending != null || tick - lastReplan < 400 || plan == null) {
            return;
        }
        lastReplan = tick;
        replans++;
        DoHy.LOGGER.info("Guerre : {} replanifie ({})", traits.name(), why);
        requestPlan();
    }

    private void tickReplan() {
        if (pending == null || !pending.isDone()) {
            return;
        }
        SiegePlanner.Plan found = pending.getNow(null);
        pending = null;
        if (found == null || found.steps().size() < 2) {
            if (job != null && job.attempts >= 3) {
                tell("Le commandant ne trouve plus de passage.", ChatFormatting.GRAY);
                morale -= 10.0d;
            }
            return;
        }
        // Le nouveau chemin part du meme camp ; chaque soldat se recale sur le pas le plus proche.
        plan = found;
        obstacle = 0;
        job = null;
        for (Soldier soldier : alive()) {
            soldier.routeIndex = -1;
        }
        tell("Le commandant change de plan : " + found.summary() + ".", ChatFormatting.GRAY);
    }

    // -- Les chunks ------------------------------------------------------------------------------

    private void forceArea(boolean battle) {
        if (battle) {
            java.util.Set<Long> wanted = new java.util.LinkedHashSet<>();
            for (SiegePlanner.Step step : steps()) {
                int cx = step.pos().getX() >> 4;
                int cz = step.pos().getZ() >> 4;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        wanted.add(key(cx + dx, cz + dz));
                    }
                }
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    wanted.add(key((vault.getX() >> 4) + dx, (vault.getZ() >> 4) + dz));
                }
            }
            for (long key : wanted) {
                force(key, true);
            }
            return;
        }
        int from = (vault.getX() - radius) >> 4;
        int to = (vault.getX() + radius) >> 4;
        int fromZ = (vault.getZ() - radius) >> 4;
        int toZ = (vault.getZ() + radius) >> 4;
        for (int cx = from; cx <= to; cx++) {
            for (int cz = fromZ; cz <= toZ; cz++) {
                force(key(cx, cz), false);
            }
        }
    }

    private void force(long key, boolean ticking) {
        int cx = keyX(key);
        int cz = keyZ(key);
        if (!forced.containsKey(key) && DoHyWar.TICKETS.forceChunk(level, id, cx, cz, true, ticking)) {
            forced.put(key, ticking);
        }
    }

    private boolean areaLoaded() {
        int from = (vault.getX() - radius) >> 4;
        int to = (vault.getX() + radius) >> 4;
        int fromZ = (vault.getZ() - radius) >> 4;
        int toZ = (vault.getZ() + radius) >> 4;
        for (int cx = from; cx <= to; cx++) {
            for (int cz = fromZ; cz <= toZ; cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    void releaseTickets() {
        for (Map.Entry<Long, Boolean> entry : forced.entrySet()) {
            DoHyWar.TICKETS.forceChunk(level, id, keyX(entry.getKey()), keyZ(entry.getKey()), false,
                    entry.getValue());
        }
        forced.clear();
    }

    // -- L'armee ---------------------------------------------------------------------------------

    /** Compose l'armee selon la doctrine et ce que le plan demande ; la force est dans la reserve. */
    private void compose() {
        reserve.clear();
        NationTraits.Doctrine doctrine = traits.doctrine();
        boolean blast = false;
        boolean works = false;
        for (SiegePlanner.Obstacle next : plan.obstacles()) {
            if (next.kind() == SiegePlanner.Kind.BREACH && SiegePlanner.charge(level, next.cells()) > 0.0f) {
                blast = true;
            } else {
                works = true;
            }
        }
        int sappers = (int) Math.round(force * doctrine.sappers);
        int engineers = (int) Math.round(force * doctrine.engineers);
        int archers = (int) Math.round(force * doctrine.archers);
        int medics = Math.max(1, (int) Math.round(force * doctrine.medics));
        if (blast) {
            sappers = Math.max(2, sappers);
        }
        if (works) {
            engineers = Math.max(2, engineers);
        }
        if (!blast && !works) {
            sappers = Math.min(sappers, 1);
            engineers = Math.min(engineers, 1);
        }
        int infantry = Math.max(3, force - sappers - engineers - archers - medics);
        reserve.put(SoldierRole.FANTASSIN, infantry);
        reserve.put(SoldierRole.ARCHER, archers);
        reserve.put(SoldierRole.SAPEUR, sappers);
        reserve.put(SoldierRole.INGENIEUR, engineers);
        reserve.put(SoldierRole.MEDECIN, medics);
        reserve.put(SoldierRole.COMMANDANT, 1);
        reserve.put(SoldierRole.TRESORIER, 2);
    }

    private Soldier deploy(SoldierRole role) {
        int count = reserve.getOrDefault(role, 0);
        if (count <= 0 || camp == null) {
            return null;
        }
        Soldier soldier = DoHyWar.SOLDIER.get().create(level, EntitySpawnReason.EVENT);
        if (soldier == null) {
            return null;
        }
        BlockPos spot = spawnSpot();
        soldier.snapTo(spot.getX() + 0.5d, spot.getY(), spot.getZ() + 0.5d, random.nextFloat() * 360.0f, 0.0f);
        soldier.enlist(id, role, nation, random);
        if (!level.addFreshEntity(soldier)) {
            return null;
        }
        reserve.put(role, count - 1);
        units.put(soldier.getUUID(), role);
        deployed++;
        soldier.routeIndex = 0;
        level.sendParticles(ParticleTypes.CLOUD, soldier.getX(), soldier.getY() + 1.0d, soldier.getZ(), 8, 0.3d,
                0.6d, 0.3d, 0.02d);
        if (role == SoldierRole.COMMANDANT) {
            commander = soldier.getUUID();
        } else if (role == SoldierRole.TRESORIER && paymaster == null) {
            paymaster = soldier.getUUID();
        }
        return soldier;
    }

    private BlockPos spawnSpot() {
        for (int tries = 0; tries < 12; tries++) {
            int x = camp.getX() + random.nextInt(9) - 4;
            int z = camp.getZ() + random.nextInt(9) - 4;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                    && level.getFluidState(pos.below()).isEmpty()) {
                return pos;
            }
        }
        return camp;
    }

    /** La premiere vague : les ouvriers, l'etat-major, et la plus grosse part des combattants. */
    private void deployFirstWave() {
        deploy(SoldierRole.COMMANDANT);
        deploy(SoldierRole.TRESORIER);
        for (SoldierRole role : List.of(SoldierRole.SAPEUR, SoldierRole.INGENIEUR)) {
            int count = reserve.getOrDefault(role, 0);
            for (int i = 0; i < Math.min(count, 3); i++) {
                deploy(role);
            }
        }
        deploy(SoldierRole.MEDECIN);
        int infantry = (int) Math.ceil(reserve.getOrDefault(SoldierRole.FANTASSIN, 0) * 0.65d);
        int archers = (int) Math.ceil(reserve.getOrDefault(SoldierRole.ARCHER, 0) * 0.65d);
        for (int i = 0; i < infantry && alive().size() < MAX_ALIVE; i++) {
            Soldier soldier = deploy(SoldierRole.FANTASSIN);
            if (soldier != null && guards.size() < 2 && infantry >= 5) {
                guards.add(soldier.getUUID());
            }
        }
        for (int i = 0; i < archers && alive().size() < MAX_ALIVE; i++) {
            deploy(SoldierRole.ARCHER);
        }
        waves = 1;
        nextWave = WAVE_TICKS;
    }

    /** Des renforts du camp, si l'etendard tient et que le moral suit. */
    private void reinforce() {
        if (bannerDown || morale < 35.0d || reserveCount() == 0 || phase != Phase.ASSAULT && phase != Phase.PILLAGE) {
            return;
        }
        int room = MAX_ALIVE - alive().size();
        if (room <= 0) {
            return;
        }
        int sent = 0;
        // D'abord les ouvriers qui manquent : sans sapeur, une breche a la charge ne s'ouvre pas.
        for (SoldierRole role : List.of(SoldierRole.SAPEUR, SoldierRole.INGENIEUR, SoldierRole.MEDECIN,
                SoldierRole.FANTASSIN, SoldierRole.ARCHER, SoldierRole.FANTASSIN, SoldierRole.TRESORIER)) {
            if (sent >= Math.min(room, 6)) {
                break;
            }
            boolean missing = role.worker() && alive().stream().noneMatch(s -> s.role() == role);
            boolean needPaymaster = role == SoldierRole.TRESORIER && soldier(paymaster) == null;
            if ((role.worker() || role == SoldierRole.MEDECIN) && !missing) {
                continue;
            }
            if (role == SoldierRole.TRESORIER && !needPaymaster) {
                continue;
            }
            int count = role == SoldierRole.FANTASSIN || role == SoldierRole.ARCHER ? 2 : 1;
            for (int i = 0; i < count && sent < Math.min(room, 6); i++) {
                Soldier soldier = deploy(role);
                if (soldier != null) {
                    sent++;
                    if (role == SoldierRole.TRESORIER) {
                        paymaster = soldier.getUUID();
                    }
                }
            }
        }
        if (sent > 0) {
            waves++;
            horn();
            tell("Des renforts arrivent au camp : " + sent + " soldat(s).", ChatFormatting.RED);
        }
    }

    // -- Le camp ---------------------------------------------------------------------------------

    private void buildCamp() {
        camp = plan.camp();
        double fx = vault.getX() - camp.getX();
        double fz = vault.getZ() - camp.getZ();
        Direction forward = Math.abs(fx) > Math.abs(fz) ? (fx > 0 ? Direction.EAST : Direction.WEST)
                : (fz > 0 ? Direction.SOUTH : Direction.NORTH);
        Direction side = forward.getClockWise();
        BlockPos pole = ground(camp.relative(forward.getOpposite(), 3));
        banner = pole.above(2);
        placeCamp(pole, Blocks.OAK_FENCE.defaultBlockState());
        placeCamp(pole.above(), Blocks.OAK_FENCE.defaultBlockState());
        BlockState flag = Blocks.BANNER.pick(traits.color()).defaultBlockState()
                .setValue(BannerBlock.ROTATION, rotation(forward));
        if (placeCamp(banner, flag) && level.getBlockEntity(banner) instanceof BannerBlockEntity entity) {
            entity.applyComponentsFromItemStack(Soldier.banner(level, traits));
            entity.setChanged();
            level.sendBlockUpdated(banner, flag, flag, 3);
        }
        BlockState wool = Blocks.WOOL.pick(traits.color()).defaultBlockState();
        BlockState white = Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState();
        tent(camp.relative(forward.getOpposite(), 6).relative(side, 3), forward, side, wool, white);
        tent(camp.relative(forward.getOpposite(), 6).relative(side.getOpposite(), 3), forward, side, white, wool);
        placeCamp(ground(camp.relative(forward.getOpposite(), 6)), Blocks.CAMPFIRE.defaultBlockState());
        placeCamp(ground(camp.relative(forward.getOpposite(), 2).relative(side, 2)), Blocks.HAY_BLOCK.defaultBlockState());
        placeCamp(ground(camp.relative(forward.getOpposite(), 2).relative(side.getOpposite(), 2)),
                Blocks.CRAFTING_TABLE.defaultBlockState());
        terrain.save();
    }

    private static int rotation(Direction forward) {
        // Le drapeau regarde le coffre : 0 au sud, 4 a l'ouest, 8 au nord, 12 a l'est.
        return switch (forward) {
            case SOUTH -> 0;
            case WEST -> 4;
            case NORTH -> 8;
            default -> 12;
        };
    }

    /** Une tente en A : trois rangs, une toile de chaque cote, un faite. */
    private void tent(BlockPos center, Direction forward, Direction side, BlockState cloth, BlockState ridge) {
        for (int t = -1; t <= 1; t++) {
            BlockPos row = center.relative(forward, t);
            BlockPos base = ground(row);
            placeCamp(base.relative(side), cloth);
            placeCamp(base.relative(side.getOpposite()), cloth);
            placeCamp(base.above().relative(side), cloth);
            placeCamp(base.above().relative(side.getOpposite()), cloth);
            placeCamp(base.above(2), ridge);
        }
    }

    private BlockPos ground(BlockPos pos) {
        return new BlockPos(pos.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(),
                pos.getZ()), pos.getZ());
    }

    /** Pose un bloc du camp sur une case libre hors des claims ; vrai s'il est pose. */
    private boolean placeCamp(BlockPos pos, BlockState state) {
        BlockState here = level.getBlockState(pos);
        if (!(here.isAir() || here.canBeReplaced() && here.getFluidState().isEmpty())) {
            return false;
        }
        if (FactionApi.claimAt(level, pos).claimed()) {
            return false;
        }
        terrain.place(pos, state);
        return true;
    }

    private void tickCamp() {
        if (phaseTick == 20) {
            deployFirstWave();
        }
        if (phaseTick % 20 == 0) {
            command();
            checkBanner();
        }
        if (phaseTick == CAMP_TICKS - 200) {
            tell("L'assaut part dans 10 s.", ChatFormatting.RED);
        }
        if (phaseTick >= CAMP_TICKS) {
            phase(Phase.ASSAULT);
            horn();
            tell("A l'assaut !", ChatFormatting.DARK_RED);
            command();
        }
    }

    private void checkBanner() {
        if (bannerDown || banner == null) {
            return;
        }
        if (!(level.getBlockState(banner).getBlock() instanceof BannerBlock)) {
            bannerDown = true;
            morale -= 30.0d;
            tell("Notre etendard est tombe !", ChatFormatting.GOLD);
            ServerPlayer target = target();
            if (target != null) {
                target.sendSystemMessage(Component.literal("Tu as abattu l'etendard " + traits.de()
                        + " : plus de renforts, et leur moral s'effondre.").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    // -- La bataille -----------------------------------------------------------------------------

    private void tickBattle() {
        tickReplan();
        if (phase == Phase.ASSAULT) {
            tickJob();
        }
        if (tick % 10 == 0) {
            tickZone();
        }
        if (phase == Phase.PILLAGE) {
            tickPillage();
        }
        if (phase == Phase.EXTRACT) {
            tickExtract();
        }
        if (tick % 20 == 0) {
            command();
            checkBanner();
            stalled();
        }
        if (phase == Phase.ASSAULT || phase == Phase.PILLAGE) {
            if (--nextWave <= 0) {
                nextWave = WAVE_TICKS;
                reinforce();
            }
        }
        List<Soldier> alive = alive();
        if (alive.isEmpty() && (reserveCount() == 0 || bannerDown)) {
            if (result.isEmpty()) {
                result = "brise";
            }
            beginRetreat(false);
            return;
        }
        if (morale <= 15.0d && phase != Phase.EXTRACT) {
            result = "deroute";
            beginRetreat(true);
            return;
        }
        if (phaseTick > ASSAULT_LIMIT && phase == Phase.ASSAULT) {
            result = "temps";
            beginRetreat(true);
        }
    }

    /** Des soldats bloques en route : on cherche un autre chemin. */
    private void stalled() {
        int stuck = 0;
        for (Soldier soldier : alive()) {
            if (soldier.stuck >= 8) {
                stuck++;
            }
        }
        if (stuck >= 3) {
            replan("soldats bloques");
        }
    }

    /** L'etape ou la troupe attend : avant l'obstacle en cours, ou le coffre s'il n'y en a plus. */
    private int front() {
        if (plan == null) {
            return 0;
        }
        if (obstacle < plan.obstacles().size()) {
            return Math.max(0, plan.obstacles().get(obstacle).first() - 1);
        }
        return last();
    }

    private boolean storming() {
        return plan != null && obstacle >= plan.obstacles().size();
    }

    /** Distribue les ordres : ou chacun doit etre, selon son role et la phase. */
    private void command() {
        if (plan == null) {
            return;
        }
        int front = front();
        boolean storm = storming() && phase != Phase.CAMP && morale >= 35.0d;
        int rally = phase == Phase.CAMP ? 0 : storm ? last() : Math.max(0, front - 5);
        Soldier treasurer = soldier(paymaster);
        UUID worker = job == null ? null : job.worker;
        List<Soldier> escort = new ArrayList<>();
        for (Soldier soldier : alive()) {
            SoldierRole role = soldier.role();
            if (soldier.getUUID().equals(worker)) {
                continue;
            }
            if (phase == Phase.CAMP) {
                if (role == SoldierRole.COMMANDANT || role == SoldierRole.TRESORIER) {
                    soldier.orderHold(banner == null ? camp : banner.below(2), 2.5d);
                } else {
                    soldier.orderHold(camp, 4.5d);
                }
                continue;
            }
            switch (role) {
                case COMMANDANT -> soldier.orderHold(banner == null ? camp : banner.below(2), 3.0d);
                case TRESORIER -> {
                    if (!soldier.getUUID().equals(paymaster)) {
                        soldier.orderHold(camp, 3.0d);
                    } else if (phase == Phase.PILLAGE) {
                        soldier.orderRoute(last(), 1.1d);
                    } else if (phase == Phase.EXTRACT) {
                        soldier.orderRoute(0, 1.3d);
                    } else {
                        soldier.orderHold(banner == null ? camp : banner.below(2), 3.0d);
                    }
                }
                case FANTASSIN -> {
                    if (guards.contains(soldier.getUUID()) && !bannerDown) {
                        soldier.orderHold(camp, 5.0d);
                    } else if ((phase == Phase.PILLAGE || phase == Phase.EXTRACT) && treasurer != null
                            && escort.size() < 2) {
                        escort.add(soldier);
                        soldier.orderGoTo(treasurer.blockPosition(), 1.2d);
                    } else if (phase == Phase.EXTRACT) {
                        soldier.orderRoute(0, 1.1d);
                    } else if (storm || phase == Phase.PILLAGE) {
                        if (soldier.routeIndex >= last() - 2 || soldier.near(vault, ZONE + 2.0d)) {
                            soldier.orderHold(vault, 2.5d);
                        } else {
                            soldier.orderRoute(last(), 1.1d);
                        }
                    } else {
                        soldier.orderRoute(rally, 1.0d);
                    }
                }
                case ARCHER -> soldier.orderRoute(phase == Phase.EXTRACT ? 0
                        : Math.max(0, (storm || phase == Phase.PILLAGE ? last() - 8 : rally - 6)), 1.0d);
                case MEDECIN -> soldier.orderRoute(phase == Phase.EXTRACT ? 0
                        : Math.max(0, (storm || phase == Phase.PILLAGE ? last() - 5 : rally - 3)), 1.0d);
                case SAPEUR, INGENIEUR -> soldier.orderRoute(phase == Phase.EXTRACT ? 0 : Math.max(0, rally - 2),
                        1.0d);
            }
        }
    }

    // -- Les obstacles ---------------------------------------------------------------------------

    /** Un obstacle en travail : qui s'en charge, ou il en est. */
    private static final class Job {
        final SiegePlanner.Obstacle obstacle;
        UUID worker;
        int stage;
        int timer;
        int attempts;
        int cell;
        float charge;
        UUID tnt;
        int waited;

        Job(SiegePlanner.Obstacle obstacle) {
            this.obstacle = obstacle;
        }
    }

    private static final int ASSIGN = 0;
    private static final int GOTO = 1;
    private static final int WORK = 2;
    private static final int FLEE = 3;
    private static final int CHECK = 4;

    private boolean open(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() || state.is(net.minecraft.tags.BlockTags.WOODEN_DOORS)
                || state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock;
    }

    private boolean cleared(SiegePlanner.Obstacle next) {
        return switch (next.kind()) {
            case BREACH -> next.cells().stream().allMatch(this::open);
            case LADDER -> next.cells().stream().allMatch(pos -> level.getBlockState(pos).is(net.minecraft.tags.BlockTags.CLIMBABLE));
            case BRIDGE -> next.cells().stream().allMatch(pos -> !level.getBlockState(pos)
                    .getCollisionShape(level, pos).isEmpty());
        };
    }

    private void tickJob() {
        if (plan == null || obstacle >= plan.obstacles().size()) {
            return;
        }
        if (job == null) {
            job = new Job(plan.obstacles().get(obstacle));
        }
        SiegePlanner.Obstacle next = job.obstacle;
        if (job.stage != FLEE && job.stage != CHECK && cleared(next)) {
            finishObstacle();
            return;
        }
        int stage = Math.max(0, next.first() - 1);
        Soldier worker = soldier(job.worker);
        if (job.stage != ASSIGN && job.stage != FLEE && job.stage != CHECK && worker == null) {
            job.stage = ASSIGN;
        }
        switch (job.stage) {
            case ASSIGN -> assign(next, stage);
            case GOTO -> {
                BlockPos spot = steps().get(stage).pos();
                if (worker.near(spot, 1.2d)) {
                    job.stage = WORK;
                    job.timer = 0;
                } else {
                    worker.orderRoute(stage, 1.15d);
                    if (++job.waited > 1200) {
                        replan("ouvrier bloque");
                        job.waited = 0;
                    }
                }
            }
            case WORK -> work(next, worker, stage);
            case FLEE -> {
                job.timer++;
                if (worker != null) {
                    worker.setPose(Pose.STANDING);
                    worker.orderRoute(Math.max(0, stage - 7), 1.35d);
                }
                if (job.timer >= FUSE) {
                    detonate(job, next);
                    job.stage = CHECK;
                    job.timer = 0;
                }
            }
            case CHECK -> {
                if (++job.timer < 10) {
                    return;
                }
                if (cleared(next)) {
                    finishObstacle();
                } else if (++job.attempts >= 4) {
                    replan("mur qui ne cede pas");
                    job.stage = ASSIGN;
                } else {
                    job.stage = ASSIGN;
                }
            }
            default -> {
            }
        }
    }

    private void assign(SiegePlanner.Obstacle next, int stage) {
        SoldierRole wanted;
        if (next.kind() == SiegePlanner.Kind.BREACH) {
            List<BlockPos> remaining = next.cells().stream().filter(pos -> !open(pos)).toList();
            job.charge = SiegePlanner.charge(level, remaining);
            wanted = job.charge > 0.0f ? SoldierRole.SAPEUR : SoldierRole.INGENIEUR;
        } else {
            wanted = SoldierRole.INGENIEUR;
        }
        Soldier best = nearest(wanted, steps().get(stage).pos());
        if (best == null && wanted == SoldierRole.INGENIEUR) {
            // Sans ingenieur, un fantassin creuse, deux fois moins vite.
            best = nearest(SoldierRole.FANTASSIN, steps().get(stage).pos());
        }
        if (best == null) {
            if (reserve.getOrDefault(wanted, 0) == 0 && phaseTick % 200 == 0) {
                morale -= 2.0d;
                if (wanted == SoldierRole.SAPEUR) {
                    tell("Plus de sapeurs pour faire sauter tes murs...", ChatFormatting.GRAY);
                }
            }
            return;
        }
        job.worker = best.getUUID();
        job.stage = GOTO;
        job.waited = 0;
        best.orderRoute(stage, 1.15d);
    }

    private Soldier nearest(SoldierRole role, BlockPos to) {
        Soldier best = null;
        double distance = Double.MAX_VALUE;
        for (Soldier soldier : alive()) {
            if (soldier.role() != role || guards.contains(soldier.getUUID())) {
                continue;
            }
            double d = soldier.distanceToSqr(to.getX() + 0.5d, to.getY(), to.getZ() + 0.5d);
            if (d < distance) {
                distance = d;
                best = soldier;
            }
        }
        return best;
    }

    private void work(SiegePlanner.Obstacle next, Soldier worker, int stage) {
        job.timer++;
        switch (next.kind()) {
            case BREACH -> {
                if (job.charge > 0.0f) {
                    arm(next, worker, stage);
                } else {
                    dig(next, worker, stage);
                }
            }
            case LADDER -> ladder(next, worker, stage);
            case BRIDGE -> bridge(next, worker, stage);
        }
    }

    /** Le sapeur s'accroupit, pose sa charge contre le mur, et court s'abriter. */
    private void arm(SiegePlanner.Obstacle next, Soldier worker, int stage) {
        BlockPos spot = steps().get(stage).pos();
        worker.orderHold(spot, 0.8d);
        worker.setPose(Pose.CROUCHING);
        BlockPos target = next.cells().getFirst();
        worker.getLookControl().setLookAt(target.getX() + 0.5d, target.getY() + 0.5d, target.getZ() + 0.5d);
        if (job.timer % 8 == 0) {
            worker.swing(InteractionHand.MAIN_HAND);
        }
        if (job.timer < 30) {
            return;
        }
        // La charge, tout contre la face du mur.
        double x = spot.getX() + 0.5d + (target.getX() - spot.getX()) * 0.35d;
        double z = spot.getZ() + 0.5d + (target.getZ() - spot.getZ()) * 0.35d;
        double y = Math.min(spot.getY(), target.getY());
        PrimedTnt tnt = new PrimedTnt(level, x, y, z, worker);
        tnt.setFuse(FUSE + 10);
        tnt.setDeltaMovement(0.0d, 0.0d, 0.0d);
        tnt.setBlockState(job.charge >= SiegePlanner.CHARGE_ENDIUM ? PalaPillage.ENDIUM_TNT.get().defaultBlockState()
                : job.charge >= SiegePlanner.CHARGE_BIG ? PalaPillage.BIG_TNT.get().defaultBlockState()
                : Blocks.TNT.defaultBlockState());
        tnt.addTag(Sieges.CHARGE_TAG);
        level.addFreshEntity(tnt);
        level.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS,
                1.0f, 1.0f);
        job.tnt = tnt.getUUID();
        charges.put(tnt.getUUID(), Float.floatToIntBits(job.charge));
        Sieges.charge(tnt.getUUID(), this);
        worker.setPose(Pose.STANDING);
        job.stage = FLEE;
        job.timer = 0;
        if (job.attempts == 0) {
            tell("Un sapeur pose une charge contre ton mur !", ChatFormatting.RED);
        }
    }

    /** La charge saute : l'explosion vanilla, avec l'endium qui perce l'obsidienne a trois blocs. */
    private void detonate(Job current, SiegePlanner.Obstacle next) {
        Entity entity = current.tnt == null ? null : level.getEntity(current.tnt);
        if (!(entity instanceof PrimedTnt tnt) || !tnt.isAlive()) {
            Sieges.forget(current.tnt);
            return;
        }
        float power = current.charge >= SiegePlanner.CHARGE_ENDIUM ? 5.0f
                : current.charge >= SiegePlanner.CHARGE_BIG ? 5.5f : 4.0f;
        boolean endium = current.charge >= SiegePlanner.CHARGE_ENDIUM;
        double cx = tnt.getX();
        double cy = tnt.getY(0.0625d);
        double cz = tnt.getZ();
        ExplosionDamageCalculator calculator = new ExplosionDamageCalculator() {
            @Override
            public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter getter, BlockPos pos,
                                                               BlockState state, FluidState fluid) {
                if (endium && state.is(PalaPillage.ENDIUM_TNT_BREAKABLE)
                        && pos.distToCenterSqr(cx, cy, cz) <= 3.5d * 3.5d) {
                    return Optional.of(2.0f);
                }
                return super.getBlockExplosionResistance(explosion, getter, pos, state, fluid);
            }
        };
        level.explode(tnt, Explosion.getDefaultDamageSource(level, tnt), calculator, cx, cy, cz, power, false,
                Level.ExplosionInteraction.TNT);
        tnt.discard();
        Sieges.forget(current.tnt);
        current.tnt = null;
    }

    /** L'ingenieur creuse, case apres case, en avancant dans ce qu'il ouvre. */
    private void dig(SiegePlanner.Obstacle next, Soldier worker, int stage) {
        BlockPos cell = null;
        int cellStep = stage;
        for (BlockPos pos : next.cells()) {
            if (!open(pos)) {
                cell = pos;
                break;
            }
        }
        if (cell == null) {
            return;
        }
        // Il travaille depuis le pas qui precede celui de la case.
        for (int i = next.first(); i < steps().size() && i < next.first() + 10; i++) {
            if (steps().get(i).broken().contains(cell)) {
                cellStep = Math.max(stage, i - 1);
                break;
            }
        }
        BlockPos spot = steps().get(cellStep).pos();
        if (!worker.near(spot, 1.3d) && worker.distanceToSqr(cell.getX() + 0.5d, cell.getY() + 0.5d,
                cell.getZ() + 0.5d) > 4.0d * 4.0d) {
            worker.orderRoute(cellStep, 1.1d);
            job.timer = 0;
            return;
        }
        worker.orderHold(spot, 1.5d);
        worker.getLookControl().setLookAt(cell.getX() + 0.5d, cell.getY() + 0.5d, cell.getZ() + 0.5d);
        BlockState state = level.getBlockState(cell);
        float hardness = Math.max(0.2f, state.getDestroySpeed(level, cell));
        int ticks = (int) (10 + hardness * 16) * (worker.role() == SoldierRole.INGENIEUR ? 1 : 2);
        if (job.timer % 5 == 0) {
            worker.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, cell, state.getSoundType().getHitSound(), SoundSource.HOSTILE, 0.6f, 0.8f);
        }
        level.destroyBlockProgress(worker.getId(), cell, Math.min(9, job.timer * 10 / Math.max(1, ticks)));
        if (job.timer >= ticks) {
            level.destroyBlockProgress(worker.getId(), cell, -1);
            terrain.remove(cell, true);
            job.timer = 0;
        }
    }

    /** L'ingenieur pose les barreaux de bas en haut, un toutes les demi-secondes. */
    private void ladder(SiegePlanner.Obstacle next, Soldier worker, int stage) {
        BlockPos spot = steps().get(stage).pos();
        worker.orderHold(spot, 0.9d);
        if (job.timer % 10 != 0) {
            return;
        }
        while (job.cell < next.cells().size()) {
            BlockPos rung = next.cells().get(job.cell);
            BlockState here = level.getBlockState(rung);
            if (here.is(net.minecraft.tags.BlockTags.CLIMBABLE)) {
                job.cell++;
                continue;
            }
            if (!here.isAir() && !(here.canBeReplaced() && !here.getFluidState().isEmpty())) {
                replan("echelle bloquee");
                job.cell = next.cells().size();
                return;
            }
            BlockState state = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, next.facing())
                    .setValue(LadderBlock.WATERLOGGED, !here.getFluidState().isEmpty());
            terrain.place(rung, state);
            worker.swing(InteractionHand.OFF_HAND);
            level.playSound(null, rung, SoundEvents.LADDER_PLACE, SoundSource.HOSTILE, 1.0f, 1.0f);
            job.cell++;
            if (job.cell == 1) {
                tell("Des echelles se dressent contre ton mur !", ChatFormatting.RED);
            }
            return;
        }
    }

    /** L'ingenieur jette les planches devant lui, et avance dessus. */
    private void bridge(SiegePlanner.Obstacle next, Soldier worker, int stage) {
        if (job.cell >= next.cells().size()) {
            return;
        }
        BlockPos fill = next.cells().get(job.cell);
        int standStep = Math.max(stage, next.first() + job.cell - 1);
        BlockPos spot = steps().get(Math.min(standStep, last())).pos();
        if (!worker.near(spot, 1.4d)) {
            worker.orderRoute(standStep, 1.0d);
            return;
        }
        worker.orderHold(spot, 1.0d);
        if (job.timer % 8 != 0) {
            return;
        }
        BlockState here = level.getBlockState(fill);
        if (here.getCollisionShape(level, fill).isEmpty()) {
            boolean lava = here.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
            terrain.place(fill, lava ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
            worker.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, fill, SoundEvents.WOOD_PLACE, SoundSource.HOSTILE, 1.0f, 1.0f);
        }
        job.cell++;
    }

    private void finishObstacle() {
        SiegePlanner.Obstacle done = job == null ? null : job.obstacle;
        Soldier worker = job == null ? null : soldier(job.worker);
        if (worker != null) {
            worker.setPose(Pose.STANDING);
        }
        job = null;
        obstacle++;
        morale = Math.min(100.0d, morale + 6.0d);
        if (done != null && done.kind() == SiegePlanner.Kind.BREACH) {
            tell("Breche ouverte ! En avant !", ChatFormatting.RED);
        }
        if (storming()) {
            tell("Plus rien entre nous et ton coffre.", ChatFormatting.DARK_RED);
        }
        command();
    }

    // -- Le coffre -------------------------------------------------------------------------------

    private boolean inZone(Entity entity) {
        double dx = entity.getX() - (vault.getX() + 0.5d);
        double dz = entity.getZ() - (vault.getZ() + 0.5d);
        return dx * dx + dz * dz <= ZONE * ZONE && Math.abs(entity.getY() - vault.getY()) <= 3.5d;
    }

    private void tickZone() {
        if (phase != Phase.ASSAULT && phase != Phase.PILLAGE) {
            return;
        }
        int attackers = 0;
        for (Soldier soldier : alive()) {
            if (inZone(soldier) && soldier.role() != SoldierRole.TRESORIER) {
                attackers++;
            }
        }
        int defenders = 0;
        ServerPlayer target = target();
        if (target != null && target.isAlive() && !target.isSpectator() && !target.isCreative()
                && target.level() == level && inZone(target)) {
            defenders++;
        }
        AABB box = new AABB(vault).inflate(ZONE, 3.5d, ZONE);
        for (GuardianGolem golem : level.getEntitiesOfClass(GuardianGolem.class, box, GuardianGolem::isAlive)) {
            if (golem.owner().map(player::equals).orElse(false)) {
                defenders++;
            }
        }
        double before = capture;
        if (attackers > 0 && defenders == 0) {
            capture = Math.min(1.0d, capture + 10.0d / CAPTURE_TICKS * (1.0d + 0.15d * Math.min(6, attackers - 1)));
        } else if (defenders > 0 && attackers == 0) {
            capture = Math.max(0.0d, capture - 10.0d / 200.0d);
        }
        if (before < 0.5d && capture >= 0.5d && phase == Phase.ASSAULT) {
            morale = Math.min(100.0d, morale + 8.0d);
            tell("Nous tenons ton coffre !", ChatFormatting.RED);
        }
        if (capture >= 1.0d && phase == Phase.ASSAULT) {
            startPillage();
        }
    }

    private void startPillage() {
        ServerPlayer target = target();
        if (target == null) {
            return;
        }
        if (soldier(paymaster) == null && !paymasterDead) {
            Soldier spare = deploy(SoldierRole.TRESORIER);
            if (spare != null) {
                paymaster = spare.getUUID();
            }
        }
        if (soldier(paymaster) == null) {
            tell("Ton coffre est a nous, mais plus de tresorier pour l'emporter...", ChatFormatting.GRAY);
            morale -= 10.0d;
            capture = 0.5d;
            return;
        }
        FactionData data = FactionApi.data(target);
        bankStart = data.bank();
        drainCap = Math.max(0L, Math.round(bankStart * (0.55d + 0.45d * traits.greed())));
        phase(Phase.PILLAGE);
        morale = Math.min(100.0d, morale + 15.0d);
        horn();
        tell("Ton coffre est pris : notre tresorier arrive pour le vider !", ChatFormatting.DARK_RED);
        loot.setVisible(true);
        command();
    }

    private void tickPillage() {
        Soldier treasurer = soldier(paymaster);
        if (treasurer == null) {
            return;
        }
        if (capture < 0.35d) {
            // Le coffre est repris : le tresorier fuit avec ce qu'il a.
            if (drained > 0) {
                startExtract("Tu reprends ton coffre ! Notre tresorier fuit avec " + MoneyFormat.exact(drained) + " $.");
            } else {
                phase(Phase.ASSAULT);
            }
            return;
        }
        if (!inZone(treasurer)) {
            return;
        }
        if (++drainTimer < DRAIN_PERIOD) {
            if (drainTimer % 10 == 0) {
                level.sendParticles(ParticleTypes.WAX_ON, treasurer.getX(), treasurer.getY() + 1.6d, treasurer.getZ(),
                        4, 0.3d, 0.3d, 0.3d, 0.05d);
            }
            return;
        }
        drainTimer = 0;
        ServerPlayer target = target();
        if (target == null) {
            return;
        }
        FactionData data = FactionApi.data(target);
        long rate = Math.max(100L, Math.round(bankStart * (0.05d + 0.04d * traits.greed())));
        long take = Math.min(Math.min(rate, data.bank()), drainCap - drained);
        if (take > 0 && FactionApi.seize(target, take, "Pillage " + traits.de())) {
            drained += take;
            WarStrategy.transit(target, take);
            treasurer.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, treasurer.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.0f, 1.4f);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, vault.getX() + 0.5d, vault.getY() + 1.0d,
                    vault.getZ() + 0.5d, 10, 0.6d, 0.4d, 0.6d, 0.1d);
        }
        boolean full = drained >= drainCap || data.bank() <= 0;
        boolean weak = morale < 40.0d && drained > 0;
        if (full || weak || take <= 0) {
            startExtract("Notre tresorier repart avec " + MoneyFormat.exact(drained) + " $ ! Tue-le pour les reprendre.");
        }
    }

    private void startExtract(String message) {
        phase(Phase.EXTRACT);
        extractTimer = 0;
        tell(message, ChatFormatting.DARK_RED);
        Soldier treasurer = soldier(paymaster);
        if (treasurer != null) {
            treasurer.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                    .setBaseValue(SoldierRole.TRESORIER.speed() + 0.03d);
            treasurer.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING, 20 * 60 * 5, 0, false, false));
        }
        command();
    }

    private void tickExtract() {
        extractTimer++;
        Soldier treasurer = soldier(paymaster);
        if (treasurer == null) {
            if (!paymasterDead) {
                // Un tresorier disparu sans mourir (hors de la memoire) : l'argent revient.
                refund("Ton argent revient a ta banque.");
            }
            result = "brise";
            beginRetreat(false);
            return;
        }
        if (treasurer.near(camp, 3.0d) || extractTimer > 6 * 60 * 20) {
            escaped = true;
            result = drained > 0 ? "pillage" : "brise";
            level.sendParticles(ParticleTypes.POOF, treasurer.getX(), treasurer.getY() + 1.0d, treasurer.getZ(), 20,
                    0.4d, 0.6d, 0.4d, 0.05d);
            treasurer.discard();
            horn();
            tell("Notre tresorier est rentre au camp avec " + MoneyFormat.exact(drained) + " $. Merci pour ta banque.",
                    ChatFormatting.DARK_RED);
            beginRetreat(false);
        }
    }

    /** L'argent pris revient a la banque : tresorier tue, siege interrompu. */
    void refund(String message) {
        if (drained <= 0 || escaped) {
            return;
        }
        long amount = drained;
        drained = 0;
        ServerPlayer target = target();
        // Hors ligne, l'argent reste note en route (WarData.refund) et revient a la connexion.
        if (target != null) {
            WarStrategy.transit(target, -amount);
            FactionApi.creditBank(target, amount, "Butin repris " + traits.de());
            target.sendSystemMessage(Component.literal(MoneyFormat.exact(amount) + " $ reviennent a ta banque. "
                    + message).withStyle(ChatFormatting.GREEN));
        }
    }

    // -- Les morts -------------------------------------------------------------------------------

    void onDeath(Soldier soldier, DamageSource source) {
        if (units.remove(soldier.getUUID()) == null) {
            return;
        }
        SoldierRole role = soldier.role();
        fallen++;
        killed.merge(role, 1, Integer::sum);
        deaths.add(soldier.blockPosition());
        guards.remove(soldier.getUUID());
        double hit = role.weight() * (alive().stream().anyMatch(s -> s.role() == SoldierRole.MEDECIN) ? 0.8d : 1.0d);
        morale = Math.max(0.0d, morale - hit);
        Player killer = source.getEntity() instanceof Player p ? p : null;
        if (killer instanceof ServerPlayer serverPlayer && serverPlayer.getUUID().equals(player)) {
            long prize = switch (role) {
                case COMMANDANT -> 400L;
                case TRESORIER -> 150L;
                case SAPEUR, INGENIEUR -> 40L;
                case MEDECIN -> 30L;
                default -> 25L;
            };
            Economy.credit(serverPlayer, prize);
            bounty += prize;
        }
        switch (role) {
            case COMMANDANT -> {
                commanderDead = true;
                tell("Le commandant est tombe !", ChatFormatting.GOLD);
            }
            case TRESORIER -> {
                if (soldier.getUUID().equals(paymaster)) {
                    paymasterDead = true;
                    if (drained > 0) {
                        refund("Tu as abattu le tresorier " + traits.de() + " !");
                        tell("Notre tresorier est tombe... l'or est perdu.", ChatFormatting.GOLD);
                        if (phase == Phase.PILLAGE || phase == Phase.EXTRACT) {
                            result = "brise";
                            beginRetreat(true);
                        }
                    }
                    paymaster = null;
                }
            }
            default -> {
            }
        }
        if (job != null && soldier.getUUID().equals(job.worker)) {
            job.stage = job.stage == FLEE || job.stage == CHECK ? job.stage : ASSIGN;
            job.worker = null;
        }
        // Trois morts au meme endroit : on change de chemin.
        long near = deaths.stream().filter(pos -> pos.distSqr(soldier.blockPosition()) <= 9).count();
        if (near >= 3 && near % 3 == 0) {
            replan("pertes au meme endroit");
        }
    }

    void onPlayerDeath() {
        playerDied = true;
        morale = Math.min(100.0d, morale + 25.0d);
        tell("Ton corps tombe... nos soldats exultent.", ChatFormatting.DARK_RED);
    }

    // -- La fin ----------------------------------------------------------------------------------

    private void beginRetreat(boolean announce) {
        if (phase == Phase.RETREAT || finished()) {
            return;
        }
        if (result.isEmpty()) {
            result = "brise";
        }
        phase(Phase.RETREAT);
        loot.setVisible(false);
        if (announce) {
            tell(result.equals("deroute") ? "Retraite ! Retraite !" : "Nous levons le siege.", ChatFormatting.GOLD);
        }
        for (Soldier soldier : alive()) {
            soldier.setPose(Pose.STANDING);
            soldier.setTarget(null);
            soldier.orderRoute(0, 1.2d);
        }
        Sieges.concluded(this);
    }

    private void tickRetreat() {
        if (phaseTick % 20 == 0) {
            for (Soldier soldier : alive()) {
                soldier.orderRoute(0, 1.2d);
                if (camp != null && soldier.near(camp, 4.0d)) {
                    level.sendParticles(ParticleTypes.POOF, soldier.getX(), soldier.getY() + 1.0d, soldier.getZ(), 10,
                            0.3d, 0.5d, 0.3d, 0.02d);
                    units.remove(soldier.getUUID());
                    soldier.discard();
                }
            }
        }
        if (alive().isEmpty() || phaseTick > RETREAT_LIMIT) {
            cleanup();
        }
    }

    /** Fin immediate : les soldats partent, le terrain revient dans 15 s. */
    private void cleanup() {
        for (Soldier soldier : alive()) {
            soldier.discard();
        }
        units.clear();
        for (UUID charge : List.copyOf(charges.keySet())) {
            Entity entity = level.getEntity(charge);
            if (entity != null) {
                entity.discard();
            }
            Sieges.forget(charge);
        }
        charges.clear();
        bar.removeAllPlayers();
        loot.removeAllPlayers();
        phase(Phase.CLEANUP);
    }

    private void tickCleanup() {
        if (phaseTick < 15 * 20) {
            return;
        }
        if (phaseTick == 15 * 20 && !terrain.empty()) {
            ServerPlayer target = target();
            if (target != null) {
                target.sendSystemMessage(Component.literal("Le terrain du siege revient comme avant.")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        if (terrain.restoreStep()) {
            finish();
        }
    }

    private void finish() {
        terrain.save();
        releaseTickets();
        phase(Phase.DONE);
    }

    /** Leve le siege avant le combat : une base imprenable. */
    private void conclude(String why) {
        result = why;
        releaseTickets();
        bar.removeAllPlayers();
        loot.removeAllPlayers();
        Sieges.concluded(this);
        phase(Phase.DONE);
    }

    /**
     * Coupe le siege : joueur deconnecte, serveur arrete. L'argent en route
     * revient, les soldats partent, le terrain revient — d'un coup si le
     * serveur s'arrete.
     */
    void suspend(boolean now) {
        if (phase == Phase.DONE) {
            return;
        }
        boolean wasRunning = !finished();
        if (wasRunning) {
            refund("Le siege est interrompu.");
            if (phase != Phase.RETREAT) {
                result = "suspendu";
                Sieges.concluded(this);
            }
            cleanup();
        }
        if (now) {
            terrain.restore(-1);
            finish();
        }
    }

    // -- L'affichage -----------------------------------------------------------------------------

    private void refreshBars() {
        if (phase == Phase.PLANNING || finished()) {
            return;
        }
        int count = alive().size();
        String label = switch (phase) {
            case CAMP -> "Camp " + traits.de() + " · assaut dans " + Math.max(0, (CAMP_TICKS - phaseTick) / 20) + " s";
            case PILLAGE -> "Pillage " + traits.de() + " · coffre tenu";
            case EXTRACT -> "Le tresorier " + traits.de() + " s'enfuit !";
            case RETREAT -> "Retraite " + traits.de();
            default -> "Assaut " + traits.de() + " · moral " + (int) Math.round(morale) + " % · "
                    + count + " soldat(s)" + (bannerDown ? "" : " · renforts " + reserveCount());
        };
        bar.setName(Component.literal(label));
        bar.setProgress((float) Mth.clamp(morale / 100.0d, 0.0d, 1.0d));
        if (phase == Phase.PILLAGE || phase == Phase.EXTRACT) {
            loot.setVisible(true);
            loot.setName(Component.literal("Ta banque pillee : " + MoneyFormat.exact(drained) + " $"
                    + (bankStart > 0 ? " (" + Math.round(100.0d * drained / bankStart) + " %)" : "")));
            loot.setProgress((float) (bankStart <= 0 ? 0.0d : Mth.clamp((double) drained / bankStart, 0.0d, 1.0d)));
        } else if (capture > 0.0d && phase == Phase.ASSAULT) {
            loot.setVisible(true);
            loot.setName(Component.literal("Ton coffre : pris a " + Math.round(capture * 100.0d) + " %"));
            loot.setProgress((float) capture);
        } else {
            loot.setVisible(false);
        }
        ServerPlayer target = target();
        if (target != null && !bar.getPlayers().contains(target)) {
            bar.addPlayer(target);
            loot.addPlayer(target);
        }
    }

    /** Pour le menu : ou en est le siege, en une ligne. */
    String status() {
        return switch (phase) {
            case PLANNING -> "Leur commandant etudie ta base";
            case CAMP -> "Camp dresse " + bearing(camp == null ? vault : camp) + ", assaut dans "
                    + Math.max(0, (CAMP_TICKS - phaseTick) / 20) + " s";
            case ASSAULT -> storming() ? "Ils marchent sur ton coffre (pris a " + Math.round(capture * 100.0d) + " %)"
                    : "Obstacle " + (obstacle + 1) + " sur " + (plan == null ? 0 : plan.obstacles().size())
                    + " : " + (plan == null ? "" : plan.obstacles().get(Math.min(obstacle, plan.obstacles().size() - 1))
                    .describe());
            case PILLAGE -> "Ils vident ta banque : " + MoneyFormat.exact(drained) + " $";
            case EXTRACT -> "Leur tresorier fuit avec " + MoneyFormat.exact(drained) + " $";
            case RETREAT -> "Ils se retirent";
            default -> "Fini";
        };
    }

    int obstacles() {
        return plan == null ? 0 : plan.obstacles().size();
    }
}

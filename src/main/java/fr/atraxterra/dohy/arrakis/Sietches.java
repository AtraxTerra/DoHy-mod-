package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.spawn.DoHySpawn;
import fr.atraxterra.dohy.spawn.HologramBlockEntity;
import it.unimi.dsi.fastutil.longs.LongArrays;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Les sietchs d'Arrakis : huit refuges creuses au pied des falaises de roche de l'erg, entre
 * 650 et 1 800 blocs d'Arrakeen.
 *
 * <p>Leurs emplacements se calculent sur le relief d'Arrakis ({@link ArrakisRelief}), a graine
 * fixe : une falaise d'au moins vingt blocs qui tombe sur le sable, et derriere elle assez de
 * roche pour la salle, ses alcoves et le dortoir. Un sietch se creuse quand un joueur passe a
 * moins de {@link #TRIGGER} blocs (comme l'astroport, quelques milliers de blocs par tick), puis
 * ses habitants s'installent ; s'il en manque un, il revient quand quelqu'un approche.
 *
 * <p>L'interieur est protege : on n'y casse ni n'y pose rien, sauf en creatif.
 */
@EventBusSubscriber(modid = DoHy.MOD_ID)
public final class Sietches {

    /** A changer quand le dessin change : les sietchs deja creuses le sont a nouveau. */
    static final int VERSION = 1;
    /** A cette distance d'un sietch pas encore creuse, on le creuse. */
    static final double TRIGGER = 256.0d;
    /** A cette distance, on verifie que ses habitants sont la. */
    private static final double UPKEEP = 64.0d;
    private static final int PER_TICK = 2500;

    static final String[] NAMES = {"Bir Assal", "Ras Tamaar", "Umm Zahra", "Jabal Nour", "Wadi Selm", "Khor Imran",
            "Dhul Ramal", "Tell Adiya"};

    /** Un sietch : son nom, le pied de sa falaise (la ou s'ouvre la fissure) et le sens de l'entree. */
    public record Site(int index, String name, BlockPos face, Direction forward) {

        /** Le centre de la grande salle, a hauteur des pieds. */
        public BlockPos hall(int floor) {
            Direction right = forward.getClockWise();
            return new BlockPos(face.getX(), floor, face.getZ()).relative(forward, 28).relative(right, 3);
        }

        public double distanceSq(double x, double z) {
            double dx = face.getX() + 0.5d - x;
            double dz = face.getZ() + 0.5d - z;
            return dx * dx + dz * dz;
        }
    }

    private static List<Site> sites;
    private static Job job;
    /** Les volumes proteges des sietchs creuses, par indice. */
    private static final Map<Integer, BoundingBox> BOXES = new java.util.HashMap<>();
    private static int ticks;

    private Sietches() {
    }

    // -- Les emplacements ----------------------------------------------------------------------

    /** Les sietchs, calcules une fois sur le relief. */
    public static synchronized List<Site> sites() {
        if (sites == null) {
            List<Site> found = new ArrayList<>();
            for (int k = 0; k < NAMES.length; k++) {
                Site site = locate(k);
                if (site != null) {
                    found.add(site);
                }
            }
            sites = Collections.unmodifiableList(found);
            for (Site site : sites) {
                DoHy.LOGGER.info("Arrakis : sietch {} au pied de la falaise {} {}, entree vers {}", site.name(),
                        site.face().getX(), site.face().getZ(), site.forward().getOpposite().getSerializedName());
            }
        }
        return sites;
    }

    /** Le sietch le plus proche de ce point, ou {@code null}. */
    public static Site nearest(double x, double z) {
        Site best = null;
        double bestD = Double.MAX_VALUE;
        for (Site site : sites()) {
            double d = site.distanceSq(x, z);
            if (d < bestD) {
                bestD = d;
                best = site;
            }
        }
        return best;
    }

    private static double hash(int k, int salt) {
        long h = (k * 0x9E3779B97F4A7C15L) ^ (salt * 0xC2B2AE3D27D4EB4FL) ^ 0x5D1E7C4L;
        h ^= h >>> 31;
        h *= 0x7FB5D329728EA185L;
        h ^= h >>> 27;
        return ((h >>> 11) * 0x1.0p-53) * 2.0d - 1.0d;
    }

    private static Site locate(int k) {
        double angle = Math.toRadians(k * 45.0d + 17.0d + 14.0d * hash(k, 1));
        double dist = 760.0d + 900.0d * (0.5d + 0.5d * hash(k, 2));
        int cx = (int) Math.round(Math.cos(angle) * dist);
        int cz = (int) Math.round(Math.sin(angle) * dist);
        for (int r = 0; r <= 360; r += 8) {
            int n = r == 0 ? 1 : (int) Math.ceil(2.0d * Math.PI * r / 8.0d);
            for (int i = 0; i < n; i++) {
                double t = 2.0d * Math.PI * i / n + hash(k, 3);
                int x = cx + (int) Math.round(Math.cos(t) * r);
                int z = cz + (int) Math.round(Math.sin(t) * r);
                for (Direction out : Direction.Plane.HORIZONTAL) {
                    Site site = cliff(k, x, z, out);
                    if (site != null) {
                        return site;
                    }
                }
            }
        }
        DoHy.LOGGER.warn("Arrakis : pas de falaise pour le sietch {}", NAMES[k]);
        return null;
    }

    private static double height(int x, int z) {
        return ArrakisRelief.column(x, z).height();
    }

    private static boolean sand(int x, int z) {
        ArrakisRelief.Region region = ArrakisRelief.column(x, z).region();
        return region == ArrakisRelief.Region.PLAINS || region == ArrakisRelief.Region.LOWLANDS;
    }

    /**
     * Un pied de falaise convenable en (x, z), l'erg du cote {@code out} : le sable plat devant,
     * une paroi d'au moins vingt blocs, et derriere assez de roche pour tout le sietch.
     */
    private static Site cliff(int k, int x, int z, Direction out) {
        double r = Math.hypot(x, z);
        if (r < 650.0d || r > 1900.0d || !sand(x, z)) {
            return null;
        }
        Direction forward = out.getOpposite();
        Direction right = forward.getClockWise();
        double hs = height(x, z);
        int y0 = (int) Math.floor(hs) + 1;
        // la paroi : la premiere colonne de roche a moins de six blocs
        int fx = x;
        int fz = z;
        boolean wall = false;
        for (int s = 1; s <= 6; s++) {
            fx = x + forward.getStepX() * s;
            fz = z + forward.getStepZ() * s;
            if (height(fx, fz) >= y0 + 7) {
                wall = true;
                break;
            }
        }
        if (!wall || height(fx + forward.getStepX() * 3, fz + forward.getStepZ() * 3) < y0 + 20) {
            return null;
        }
        // le devant : du sable a peu pres de niveau, pour que la fissure s'ouvre sur l'erg
        for (int u = -6; u <= -1; u++) {
            for (int v = -1; v <= 4; v++) {
                int px = fx + forward.getStepX() * u + right.getStepX() * v;
                int pz = fz + forward.getStepZ() * u + right.getStepZ() * v;
                double h = height(px, pz);
                if (h > y0 + 2.5d || h < y0 - 5.0d) {
                    return null;
                }
            }
        }
        // le dedans : de la roche au-dessus de chaque salle, quatre blocs au moins sur la voute
        for (int u = 3; u <= 48; u += 2) {
            int vMin = u >= 20 && u <= 35 ? -11 : -5;
            int vMax = u >= 20 && u <= 35 ? 18 : 12;
            for (int v = vMin; v <= vMax; v += 2) {
                int px = fx + forward.getStepX() * u + right.getStepX() * v;
                int pz = fz + forward.getStepZ() * u + right.getStepZ() * v;
                if (height(px, pz) < y0 + 13) {
                    return null;
                }
            }
        }
        return new Site(k, NAMES[k], new BlockPos(fx, y0, fz), forward);
    }

    // -- La construction -----------------------------------------------------------------------

    private static boolean built(ServerLevel level, Site site) {
        return level.getData(DoHyArrakis.SIETCHES.get()).contains(site.index() * 1000 + VERSION);
    }

    private static void markBuilt(ServerLevel level, Site site) {
        List<Integer> list = new ArrayList<>();
        for (int value : level.getData(DoHyArrakis.SIETCHES.get())) {
            if (value / 1000 != site.index()) {
                list.add(value);
            }
        }
        list.add(site.index() * 1000 + VERSION);
        level.setData(DoHyArrakis.SIETCHES.get(), List.copyOf(list));
    }

    /** Le volume protege d'un sietch creuse (calcule sur son plan). */
    static BoundingBox box(ServerLevel level, Site site) {
        BoundingBox known = BOXES.get(site.index());
        if (known == null) {
            known = SietchBuilder.bounds(site, floor(level, site));
            BOXES.put(site.index(), known);
        }
        return known;
    }

    /** Le niveau du sol du sietch : le sable devant la fissure, tel qu'il est dans le monde. */
    public static int floor(ServerLevel level, Site site) {
        BlockPos face = site.face();
        Direction out = site.forward().getOpposite();
        int best = Integer.MIN_VALUE;
        for (int s = 2; s <= 4; s++) {
            BlockPos p = face.relative(out, s);
            best = Math.max(best, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()));
        }
        return Math.max(face.getY() - 4, Math.min(face.getY() + 3, best));
    }

    /** Vrai si cette position est dans un sietch deja creuse. */
    public static boolean inside(ServerLevel level, BlockPos pos) {
        if (level.dimension() != DoHyArrakis.DIMENSION) {
            return false;
        }
        for (Site site : sites()) {
            if (site.distanceSq(pos.getX(), pos.getZ()) < 90.0d * 90.0d && built(level, site)
                    && box(level, site).isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    /** Le sietch ou se tient ce joueur, ou {@code null}. */
    public static Site at(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !DoHyArrakis.onArrakis(player)) {
            return null;
        }
        for (Site site : sites()) {
            if (site.distanceSq(player.getX(), player.getZ()) < 90.0d * 90.0d && built(level, site)
                    && box(level, site).inflatedBy(2).isInside(player.blockPosition())) {
                return site;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (job != null) {
            if (job.step()) {
                Job done = job;
                job = null;
                markBuilt(done.level, done.site);
                BOXES.put(done.site.index(), done.plan.box());
                DoHy.LOGGER.info("Arrakis : sietch {} creuse", done.site.name());
            }
            return;
        }
        if (++ticks % 20 != 0 || !DoHyArrakis.available()) {
            return;
        }
        ServerLevel level = ArrakisPort.level(event.getServer());
        if (level == null || level.players().isEmpty()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            for (Site site : sites()) {
                double d = site.distanceSq(player.getX(), player.getZ());
                if (d > TRIGGER * TRIGGER) {
                    continue;
                }
                if (!built(level, site)) {
                    job = new Job(level, site);
                    DoHy.LOGGER.info("Arrakis : on creuse le sietch {}", site.name());
                    return;
                }
                if (d < UPKEEP * UPKEEP && ticks % 100 == 0) {
                    upkeep(level, site);
                }
            }
        }
    }

    /** Remet a sa place chaque habitant qui manque (tue en creatif, perdu, jamais apparu). */
    static void upkeep(ServerLevel level, Site site) {
        SietchBuilder.Plan plan = SietchBuilder.plan(site, floor(level, site), 0);
        BoundingBox box = plan.box();
        List<SietchDweller> present = level.getEntitiesOfClass(SietchDweller.class,
                net.minecraft.world.phys.AABB.of(box).inflate(4.0d), d -> d.sietch() == site.index());
        Map<SietchDweller.Role, Integer> count = new EnumMap<>(SietchDweller.Role.class);
        for (SietchDweller dweller : present) {
            count.merge(dweller.role(), 1, Integer::sum);
        }
        Map<SietchDweller.Role, Integer> wanted = new EnumMap<>(SietchDweller.Role.class);
        for (SietchBuilder.Spot spot : plan.spots()) {
            int already = wanted.merge(spot.role(), 1, Integer::sum);
            if (count.getOrDefault(spot.role(), 0) >= already) {
                continue;
            }
            spawn(level, site, spot);
        }
    }

    private static void spawn(ServerLevel level, Site site, SietchBuilder.Spot spot) {
        SietchDweller dweller = SietchContent.DWELLER.get().create(level, EntitySpawnReason.EVENT);
        if (dweller == null) {
            return;
        }
        BlockPos pos = spot.pos();
        dweller.snapTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, spot.yaw(), 0.0f);
        dweller.setYHeadRot(spot.yaw());
        dweller.setYBodyRot(spot.yaw());
        dweller.settle(site.index(), spot.role(), pos, level.getRandom().nextInt(SietchDweller.VARIANTS));
        level.addFreshEntity(dweller);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        job = null;
        BOXES.clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !player.isCreative()
                && player.level() instanceof ServerLevel level && inside(level, event.getPos())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("La roche du sietch ne se taille pas : ses habitants y veillent.")
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.isCreative()
                && player.level() instanceof ServerLevel level && inside(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == DoHyArrakis.DIMENSION) {
            event.getAffectedBlocks().removeIf(pos -> inside(level, pos));
        }
    }

    /** Un sietch qu'on creuse : poser le plan chunk apres chunk, raccorder, etiqueter, peupler. */
    private static final class Job {
        private final ServerLevel level;
        private final Site site;
        private final SietchBuilder.Plan plan;
        private final long[] order;
        private final List<BlockPos> shaped = new ArrayList<>();
        private int index;
        private int shapeIndex;
        private boolean finished;

        Job(ServerLevel level, Site site) {
            this.level = level;
            this.site = site;
            int floor = floor(level, site);
            BlockPos hall = site.hall(floor);
            int summit = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall.getX(), hall.getZ());
            this.plan = SietchBuilder.plan(site, floor, summit);
            this.order = plan.blocks().keySet().toLongArray();
            LongArrays.quickSort(order, (a, b) -> {
                int ca = Integer.compare(BlockPos.getX(a) >> 4, BlockPos.getX(b) >> 4);
                if (ca != 0) {
                    return ca;
                }
                int cz = Integer.compare(BlockPos.getZ(a) >> 4, BlockPos.getZ(b) >> 4);
                return cz != 0 ? cz : Integer.compare(BlockPos.getY(a), BlockPos.getY(b));
            });
        }

        boolean step() {
            if (index < order.length) {
                for (int count = 0; count < PER_TICK && index < order.length; count++, index++) {
                    BlockPos pos = BlockPos.of(order[index]);
                    BlockState state = plan.blocks().get(order[index]);
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    if (state.getBlock() instanceof IronBarsBlock || state.getBlock() instanceof WallBlock) {
                        shaped.add(pos);
                    }
                }
                return false;
            }
            if (shapeIndex < shaped.size()) {
                for (int count = 0; count < PER_TICK && shapeIndex < shaped.size(); count++) {
                    BlockPos pos = shaped.get(shapeIndex++);
                    BlockState state = level.getBlockState(pos);
                    BlockState joined = Block.updateFromNeighbourShapes(state, level, pos);
                    if (joined != state) {
                        level.setBlock(pos, joined, Block.UPDATE_CLIENTS);
                    }
                }
                return false;
            }
            if (!finished) {
                finished = true;
                for (SietchBuilder.Label label : plan.labels()) {
                    level.setBlock(label.pos(), DoHySpawn.HOLOGRAM.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                    if (level.getBlockEntity(label.pos()) instanceof HologramBlockEntity hologram) {
                        hologram.set(label.text(), label.color(), label.scale());
                    }
                }
                for (SietchBuilder.Spot spot : plan.spots()) {
                    spawn(level, site, spot);
                }
                return false;
            }
            return true;
        }
    }

}

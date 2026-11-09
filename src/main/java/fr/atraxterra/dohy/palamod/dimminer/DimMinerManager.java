package fr.atraxterra.dohy.palamod.dimminer;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.PalaBlocks;
import fr.atraxterra.dohy.palamod.miner.PalaMiner;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * La dimension du Mineur, cote serveur ({@code BlockMinerPortal},
 * {@code CSPacketDimMinerJoin}, {@code ServerDimMinerEventHandler}).
 *
 * <p>Le reseau de Paladium disparait : on n'est plus transfere vers un
 * serveur ni renvoye par un kick, on est teleporte. Entrer passe par le
 * portail et son ecran ; sortir, par le portail d'arrivee, par
 * {@code /dimminer quitter}, ou par l'effondrement de fin de session. On
 * ressort toujours devant le portail par lequel on est entre.
 */
public final class DimMinerManager {

    /** Mineur 18, x5. L'original testait 18 au portail, 17 au paquet, et ecrivait 17. */
    public static final int ACCESS_LEVEL = 90;
    /** Trente secondes d'effondrement. */
    private static final int COLLAPSE_TICKS = 600;
    /** {@code findSafeTeleportLocation} : onze sections au plus. */
    private static final int SECTION_TRIES = 11;

    /** {@code POINTS_MAP}. */
    private static final Map<Supplier<Block>, Long> COSTS = Map.of(
            PalaBlocks.PALADIUM_ORE::get, 1L,
            PalaBlocks.PALADIUM_GREEN_ORE::get, 5L,
            PalaMiner.ENDIUM_NUGGET_ORE::get, 501L);

    /** {@code MINED_BLOCK_LIST}, dans l'ordre de l'ecran de fin. */
    static final List<Supplier<Block>> TRACKED = List.of(
            PalaMiner.ENDIUM_NUGGET_ORE::get, PalaBlocks.PALADIUM_GREEN_ORE::get, PalaBlocks.PALADIUM_ORE::get,
            PalaDimMiner.INVOKER_ORE::get, PalaBlocks.TRIXIUM_ORE::get, PalaBlocks.FINDIUM_ORE::get,
            PalaBlocks.AMETHYST_ORE::get, PalaBlocks.TITANE_ORE::get);

    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private DimMinerManager() {
    }

    public static DimMinerData data(ServerPlayer player) {
        return player.getData(PalaDimMiner.DATA.get());
    }

    public static boolean inDimension(Entity entity) {
        return entity.level().dimension() == PalaDimMiner.DIMENSION;
    }

    static void message(ServerPlayer player, ChatFormatting color, String key, Object... args) {
        player.sendSystemMessage(Component.translatable("message.palamod.dimminer." + key, args).withStyle(color));
    }

    public static long cost(BlockState state) {
        for (Map.Entry<Supplier<Block>, Long> entry : COSTS.entrySet()) {
            if (state.is(entry.getKey().get())) {
                return entry.getValue();
            }
        }
        return 0L;
    }

    public static String trackedKey(BlockState state) {
        for (Supplier<Block> block : TRACKED) {
            if (state.is(block.get())) {
                return BuiltInRegistries.BLOCK.getKey(block.get()).toString();
            }
        }
        return null;
    }

    // -- Le portail ----------------------------------------------------------------

    /**
     * {@code onEntityCollidedWithBlock}. Dans la dimension : on ressort. Ailleurs :
     * niveau, proprietaire (le premier a entrer prend tout le portail), puis
     * l'ecran d'entree — une fois par passage, tant qu'on reste dedans.
     */
    public static void touchPortal(ServerPlayer player, BlockPos pos) {
        DimMinerData data = data(player);
        boolean already = data.inPortal > 0;
        data.inPortal = 20;
        if (already) {
            return;
        }
        if (inDimension(player)) {
            leave(player, true);
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (!JobApi.meets(player, Job.MINER, ACCESS_LEVEL)) {
            message(player, ChatFormatting.RED, "level", ACCESS_LEVEL);
            return;
        }
        if (level.getBlockEntity(pos) instanceof MinerPortalBlockEntity portal) {
            if (portal.owner() != null && !portal.owner().equals(player.getUUID())) {
                message(player, ChatFormatting.AQUA, "not_owner");
                repel(player, pos);
                return;
            }
            if (portal.owner() == null) {
                for (BlockPos cell : BlockPos.betweenClosed(pos.offset(-3, -4, -3), pos.offset(3, 4, 3))) {
                    if (level.getBlockEntity(cell) instanceof MinerPortalBlockEntity other && other.owner() == null) {
                        other.setOwner(player.getUUID());
                    }
                }
            }
        }
        List<BlockPos> frame = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(pos.offset(-4, -5, -4), pos.offset(4, 5, 4))) {
            BlockState state = level.getBlockState(cell);
            if (state.is(PalaDimMiner.WITHERED_OBSIDIAN.get()) && state.getValue(WitheredObsidianBlock.CREATED)) {
                frame.add(cell.immutable());
            }
        }
        if (settle(player)) {
            // L'ecran de fin vient de s'ouvrir : l'ecran d'entree attendra le
            // prochain passage, sans quoi il le recouvrirait.
            return;
        }
        if (!frame.isEmpty()) {
            data.setPortal(new DimMinerData.Portal(level.dimension(), frame, exitOf(level, pos, player)));
        }
        DimMinerMenus.openEntry(player);
    }

    /** Repousse le joueur hors du portail d'un autre, avec un eclair pour l'image. */
    private static void repel(ServerPlayer player, BlockPos pos) {
        double dx = player.getX() - (pos.getX() + 0.5d);
        double dz = player.getZ() - (pos.getZ() + 0.5d);
        player.setDeltaMovement(Math.signum(dx) * 0.3d, 0.1d, Math.signum(dz) * 0.3d);
        player.hurtMarked = true;
        ServerLevel level = (ServerLevel) player.level();
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.snapTo(player.getX(), player.getY(), player.getZ(), 0.0f, 0.0f);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                1.0f, 1.0f);
    }

    /** Une case libre devant le portail, du cote d'ou vient le joueur. */
    private static BlockPos exitOf(ServerLevel level, BlockPos portal, ServerPlayer player) {
        BlockState state = level.getBlockState(portal);
        Direction.Axis axis = state.hasProperty(MinerPortalBlock.AXIS)
                ? state.getValue(MinerPortalBlock.AXIS) : Direction.Axis.X;
        Direction normal = axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        double side = axis == Direction.Axis.X ? player.getZ() - (portal.getZ() + 0.5d)
                : player.getX() - (portal.getX() + 0.5d);
        Direction first = side < 0 ? normal.getOpposite() : normal;
        BlockPos feet = new BlockPos(portal.getX(), player.blockPosition().getY(), portal.getZ());
        for (Direction direction : new Direction[]{first, first.getOpposite()}) {
            for (int step = 1; step <= 2; step++) {
                BlockPos candidate = feet.relative(direction, step);
                if (free(level, candidate)) {
                    return candidate;
                }
            }
        }
        return feet.relative(first, 1);
    }

    private static boolean free(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).canBeReplaced() && level.getBlockState(feet.above()).canBeReplaced()
                && !level.getBlockState(feet).is(PalaDimMiner.MINER_PORTAL.get());
    }

    // -- Entrer ----------------------------------------------------------------------

    /**
     * {@code CSPacketDimMinerJoin} : cree la session si besoin (1000 points,
     * XP de Mineur de depart notee, section neuve), puis teleporte dans la
     * dimension — ou on l'avait quittee, sinon a l'arrivee.
     */
    public static void join(ServerPlayer player) {
        if (inDimension(player)) {
            return;
        }
        if (!JobApi.meets(player, Job.MINER, ACCESS_LEVEL)) {
            message(player, ChatFormatting.RED, "level", ACCESS_LEVEL);
            return;
        }
        DimMinerData data = data(player);
        if (settle(player)) {
            // La session a pris fin l'ecran ouvert : son bilan d'abord, et le
            // portail est a retraverser (la remise a zero l'a oublie).
            return;
        }
        if (data.portal() == null) {
            message(player, ChatFormatting.RED, "no_portal");
            return;
        }
        MinecraftServer server = player.level().getServer();
        ServerLevel dimension = server.getLevel(PalaDimMiner.DIMENSION);
        if (dimension == null) {
            message(player, ChatFormatting.RED, "unavailable");
            return;
        }
        if (!data.isActive()) {
            data.create(JobApi.xp(player, Job.MINER), DimMinerSections.next(server).index());
            message(player, ChatFormatting.GREEN, "creating");
        }
        BlockPos target = data.lastPos() != null ? data.lastPos() : data.arrival();
        if (target == null) {
            target = arrive(server, dimension, data);
            data.setArrival(target);
        }
        message(player, ChatFormatting.GREEN, "teleporting");
        player.teleport(new TeleportTransition(dimension, Vec3.atBottomCenterOf(target), Vec3.ZERO,
                player.getYRot(), 0.0f, TeleportTransition.PLAY_PORTAL_SOUND));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("message.palamod.dimminer.title").withStyle(ChatFormatting.DARK_PURPLE)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.translatable("message.palamod.dimminer.subtitle").withStyle(ChatFormatting.LIGHT_PURPLE)));
    }

    /**
     * {@code onLogin} : un sol sur pres du centre de la section, sinon les
     * sections suivantes ; on y pose la plateforme d'arrivee et son portail de
     * sortie. <b>Ecart</b> : a defaut apres onze sections (des oceans), une
     * plateforme au-dessus de la mer plutot qu'un echec.
     */
    private static BlockPos arrive(MinecraftServer server, ServerLevel dimension, DimMinerData data) {
        DimMinerSections.Section section = DimMinerSections.of(data.section());
        for (int attempt = 0; attempt < SECTION_TRIES; attempt++) {
            BlockPos ground = safeSpot(dimension, section);
            if (ground != null) {
                return build(dimension, ground);
            }
            section = DimMinerSections.next(server);
            data.create(data.jobXpStart(), section.index());
        }
        return build(dimension, new BlockPos(section.centerX(), dimension.getSeaLevel() + 1, section.centerZ()));
    }

    /**
     * {@code findSafeTeleportLocation} : centre, puis spirale carree jusqu'a 20
     * blocs ; un sol opaque et non liquide, et 7 x 7 x 6 de place au-dessus
     * (l'herbe et les fleurs comptent comme place libre). Rend la premiere case
     * d'air au-dessus du sol.
     */
    private static BlockPos safeSpot(ServerLevel level, DimMinerSections.Section section) {
        for (int radius = 0; radius <= 20; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = section.centerX() + dx;
                    int z = section.centerZ() + dz;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos ground = new BlockPos(x, y - 1, z);
                    BlockState below = level.getBlockState(ground);
                    if (!below.isSolidRender() || !below.getFluidState().isEmpty()) {
                        continue;
                    }
                    if (roomy(level, new BlockPos(x, y, z))) {
                        return new BlockPos(x, y, z);
                    }
                }
            }
        }
        return null;
    }

    private static boolean roomy(ServerLevel level, BlockPos base) {
        for (BlockPos cell : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 5, 3))) {
            BlockState state = level.getBlockState(cell);
            if (!state.canBeReplaced() || !state.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * La plateforme d'arrivee : 6 x 5 d'obsidienne wither au niveau du sol et
     * un cadre de portail a deux pas devant. Dans l'original le cadre etait
     * decoratif (un feu dessus, pas de charge) : ici il porte le portail de
     * sortie. Le joueur arrive sur la plateforme.
     */
    private static BlockPos build(ServerLevel level, BlockPos base) {
        int x = base.getX();
        int y = base.getY();
        int z = base.getZ();
        BlockState obsidian = PalaDimMiner.WITHERED_OBSIDIAN.get().defaultBlockState();
        for (BlockPos cell : BlockPos.betweenClosed(x - 3, y + 1, z - 2, x + 2, y + 6, z + 2)) {
            if (level.getBlockState(cell).canBeReplaced()) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        for (BlockPos cell : BlockPos.betweenClosed(x - 3, y, z - 2, x + 2, y, z + 2)) {
            level.setBlock(cell, obsidian, Block.UPDATE_CLIENTS);
        }
        int frameZ = z + 2;
        for (int dy = 0; dy <= 5; dy++) {
            level.setBlock(new BlockPos(x - 2, y + dy, frameZ), obsidian, Block.UPDATE_CLIENTS);
            level.setBlock(new BlockPos(x + 1, y + dy, frameZ), obsidian, Block.UPDATE_CLIENTS);
        }
        for (int dx = -1; dx <= 0; dx++) {
            level.setBlock(new BlockPos(x + dx, y + 1, frameZ), obsidian, Block.UPDATE_CLIENTS);
            level.setBlock(new BlockPos(x + dx, y + 5, frameZ), obsidian, Block.UPDATE_CLIENTS);
        }
        MinerPortalShape shape = MinerPortalShape.find(level, new BlockPos(x, y + 2, frameZ), Direction.Axis.X, false);
        if (shape != null) {
            shape.fill();
        }
        return base.above();
    }

    // -- Sortir ------------------------------------------------------------------------

    /**
     * Retour devant le portail d'entree ; la session continue. Sorti par la
     * commande, on retrouvera sa place ; sorti par le portail de l'arrivee, on
     * reviendra a l'arrivee — garder la case du portail renverrait dehors des
     * l'entree.
     */
    public static void leave(ServerPlayer player, boolean viaPortal) {
        DimMinerData data = data(player);
        data.setLastPos(data.isActive() && !viaPortal ? player.blockPosition() : null);
        sendBack(player);
    }

    private static void sendBack(ServerPlayer player) {
        DimMinerData data = data(player);
        MinecraftServer server = player.level().getServer();
        DimMinerData.Portal portal = data.portal();
        ServerLevel level = portal == null ? null : server.getLevel(portal.dimension());
        hideBar(player);
        if (level == null) {
            // Aucun portail connu (teleporte par commande) : le point de reapparition.
            player.teleport(TeleportTransition.createDefault(player, TeleportTransition.DO_NOTHING));
            return;
        }
        player.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(portal.exit()), Vec3.ZERO,
                player.getYRot(), 0.0f, TeleportTransition.PLAY_PORTAL_SOUND));
    }

    /**
     * La session est finie et le joueur est dehors : l'ecran de fin, la casse
     * d'un bloc de cadre sur trois, la remise a zero ({@code onLogin} du serveur
     * Faction). Rend vrai si c'etait le cas.
     */
    public static boolean settle(ServerPlayer player) {
        DimMinerData data = data(player);
        if (!data.hasSession() || data.isActive() || inDimension(player)) {
            return false;
        }
        DimMinerMenus.openStats(player, data);
        DimMinerData.Portal portal = data.portal();
        if (portal != null) {
            ServerLevel level = player.level().getServer().getLevel(portal.dimension());
            if (level != null) {
                for (BlockPos cell : portal.frame()) {
                    if (level.getBlockState(cell).is(PalaDimMiner.WITHERED_OBSIDIAN.get())
                            && level.getRandom().nextInt(3) == 0) {
                        level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
        data.reset();
        return true;
    }

    // -- Chaque tick ---------------------------------------------------------------------

    public static void tick(ServerPlayer player) {
        DimMinerData data = data(player);
        if (data.inPortal > 0) {
            data.inPortal--;
        }
        if (!inDimension(player)) {
            hideBar(player);
            if (data.hasSession() && !data.isActive() && player.tickCount % 20 == 0) {
                settle(player);
            }
            return;
        }
        if (!data.hasSession()) {
            // Venu par commande sans session : renvoye, sauf en creatif.
            if (!player.isCreative() && !player.isSpectator()) {
                sendBack(player);
            }
            return;
        }
        if (data.isActive()) {
            if (player.tickCount % 1200 == 0) {
                data.addMinute();
            }
        } else if (data.collapse < 0) {
            data.expire();
            data.collapse = COLLAPSE_TICKS;
            message(player, ChatFormatting.RED, "collapsing");
        }
        if (data.collapse > 0 && --data.collapse == 0) {
            data.collapse = -1;
            message(player, ChatFormatting.RED, "collapsed");
            sendBack(player);
            settle(player);
            return;
        }
        DimMinerSections.Section section = DimMinerSections.of(data.section());
        if (!section.contains(player.getX(), player.getZ()) && !player.isCreative() && !player.isSpectator()) {
            double x = Math.clamp(player.getX(), section.minX() + 1, section.maxX() - 1);
            double z = Math.clamp(player.getZ(), section.minZ() + 1, section.maxZ() - 1);
            player.teleportTo(x, player.getY(), z);
            message(player, ChatFormatting.RED, "border");
        }
        if (player.tickCount % 20 == 0) {
            showBar(player, data);
        }
    }

    /** {@code UIDimMinerOverlay} : la barre de points et le temps restant, en barre de boss. */
    private static void showBar(ServerPlayer player, DimMinerData data) {
        ServerBossEvent bar = BARS.computeIfAbsent(player.getUUID(), id -> new ServerBossEvent(UUID.randomUUID(),
                Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));
        bar.setName(Component.translatable("message.palamod.dimminer.bar",
                String.format("%,d", data.points()).replace(',', '.'), clock(data.remainingSeconds())));
        bar.setProgress(Math.clamp(data.points() / (float) DimMinerData.TOTAL_POINTS, 0.0f, 1.0f));
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    public static void hideBar(ServerPlayer player) {
        ServerBossEvent bar = BARS.remove(player.getUUID());
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    /** {@code %02d:%02d:%02d:%02d} : jours, heures, minutes, secondes. */
    public static String clock(long seconds) {
        long s = Math.max(0L, seconds);
        return String.format("%02d:%02d:%02d:%02d", s / 86_400L, s / 3600L % 24L, s / 60L % 60L, s % 60L);
    }

    /** Le compte des minerais dans l'ordre de {@code MINED_BLOCK_LIST}, pour l'ecran de fin. */
    static Map<Block, Long> oreCounts(DimMinerData data) {
        Map<Block, Long> counts = new LinkedHashMap<>();
        for (Supplier<Block> block : TRACKED) {
            Long count = data.minedOres().get(BuiltInRegistries.BLOCK.getKey(block.get()).toString());
            if (count != null && count > 0) {
                counts.put(block.get(), count);
            }
        }
        return counts;
    }

    static boolean levelIs(Level level) {
        return level.dimension() == PalaDimMiner.DIMENSION;
    }
}

package fr.atraxterra.dohy.arrakis;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.spawn.DoHySpawn;
import fr.atraxterra.dohy.spawn.HologramBlockEntity;
import fr.atraxterra.dohy.spawn.SpawnApi;
import fr.atraxterra.dohy.util.MoneyFormat;
import it.unimi.dsi.fastutil.longs.LongArrays;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * La navette du spawn et l'astroport d'Arrakeen.
 *
 * <p>Le billet coute {@link #TICKET} ; le retour, par la borne de l'aire ou
 * {@code /spawn}, est gratuit et ramene sur la plateforme du spawn, d'ou la
 * borne RETOUR renvoie la ou l'on etait avant le spawn. L'astroport se
 * construit a la premiere navette, quelques milliers de blocs par tick, puis
 * la cuvette d'Arrakeen est protegee comme le spawn : rien ne s'y casse, rien
 * ne s'y pose, hors mode creatif.
 */
@EventBusSubscriber(modid = DoHy.MOD_ID)
public final class ArrakisPort {

    public static final long TICKET = 2_000_000L;
    /** Rayon protege autour de l'astroport : la cuvette plate d'Arrakeen. */
    public static final double PROTECTED = ArrakisRelief.CITY_R;
    private static final int PER_TICK = 2500;

    private static Job job;

    private ArrakisPort() {
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(DoHyArrakis.DIMENSION);
    }

    /** Vrai si l'astroport est construit dans sa version courante. */
    public static boolean ready(ServerLevel level) {
        return level.getData(DoHyArrakis.PORT_BUILT.get()) == PortBuilder.VERSION;
    }

    /** La borne du spawn : le billet, puis le depart (apres la construction de l'astroport s'il le faut). */
    public static void depart(ServerPlayer player) {
        if (!DoHyArrakis.available()) {
            tell(player, "Arrakis indisponible (NTM NEXT Space absent).",
                    ChatFormatting.RED);
            return;
        }
        ServerLevel level = level(player.level().getServer());
        if (level == null) {
            tell(player, "Arrakis n'est pas chargee sur ce serveur.", ChatFormatting.RED);
            return;
        }
        if (DoHyArrakis.onArrakis(player)) {
            tell(player, "Tu es deja sur Arrakis.", ChatFormatting.GOLD);
            return;
        }
        if (!SpawnApi.inSpawn(player)) {
            tell(player, "La navette part de la plateforme du spawn.", ChatFormatting.RED);
            return;
        }
        if (Economy.balance(player) < TICKET) {
            tell(player, "Le billet pour Arrakis coute " + MoneyFormat.exact(TICKET) + " $.", ChatFormatting.RED);
            return;
        }
        if (!ready(level)) {
            if (job == null) {
                job = new Job(level);
                DoHy.LOGGER.info("Arrakis : construction de l'astroport ({} blocs)", job.order.length);
            }
            job.waiting.add(player.getUUID());
            tell(player, "Decollage dans quelques secondes.",
                    ChatFormatting.GOLD);
            return;
        }
        board(player, level);
    }

    private static void board(ServerPlayer player, ServerLevel level) {
        if (!Economy.tryDebit(player, TICKET)) {
            tell(player, "Le billet pour Arrakis coute " + MoneyFormat.exact(TICKET) + " $.", ChatFormatting.RED);
            return;
        }
        player.closeContainer();
        arrive(player, level);
        tell(player, "Bienvenue sur Arrakis, a l'astroport d'Arrakeen.", ChatFormatting.GOLD);
        tell(player, "Le retour est gratuit : la borne NAVETTE de l'aire, ou /spawn.", ChatFormatting.GRAY);
    }

    /** Pose le joueur au milieu de l'aire d'arrivee, tourne vers le hall. */
    public static void arrive(ServerPlayer player, ServerLevel level) {
        BlockPos at = PortBuilder.ARRIVAL;
        // jamais d'arrivee dans un mur : du sable tombe ou un bloc pose la, on degage
        for (int dy = 0; dy < 2; dy++) {
            BlockPos head = at.above(dy);
            if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
                level.setBlock(head, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        player.fallDistance = 0.0f;
        player.teleport(new TeleportTransition(level, new Vec3(at.getX() + 0.5d, at.getY(), at.getZ() + 0.5d),
                Vec3.ZERO, 180.0f, 0.0f, TeleportTransition.DO_NOTHING));
    }

    /** La borne de l'astroport : retour gratuit sur la plateforme du spawn. */
    public static void home(ServerPlayer player) {
        player.closeContainer();
        SpawnApi.backToPad(player);
    }

    /** Au demarrage : un astroport d'une version precedente est repose, avec ce qui a change. */
    static void refresh(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null) {
            return;
        }
        int built = level.getData(DoHyArrakis.PORT_BUILT.get());
        if (built > 0 && built != PortBuilder.VERSION) {
            DoHy.LOGGER.info("Arrakis : astroport de version {}, on le repose en version {}", built,
                    PortBuilder.VERSION);
            prepare(level);
        }
    }

    /** Lance la construction si elle n'est pas faite ; vrai si l'astroport est deja pret. */
    public static boolean prepare(ServerLevel level) {
        if (ready(level)) {
            return true;
        }
        if (job == null) {
            job = new Job(level);
        }
        return false;
    }

    /** Vrai tant que l'astroport se construit. */
    public static boolean building() {
        return job != null;
    }

    private static void tell(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    static boolean isProtected(BlockPos pos) {
        double x = pos.getX() + 0.5d;
        double z = pos.getZ() + 0.5d;
        return x * x + z * z <= PROTECTED * PROTECTED;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (job == null) {
            return;
        }
        if (job.step()) {
            Job done = job;
            job = null;
            done.level.setData(DoHyArrakis.PORT_BUILT.get(), PortBuilder.VERSION);
            DoHy.LOGGER.info("Arrakis : astroport construit");
            for (UUID id : done.waiting) {
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
                if (player != null && player.isAlive() && SpawnApi.inSpawn(player)) {
                    board(player, done.level);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        job = null;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && DoHyArrakis.onArrakis(player)
                && !player.isCreative() && isProtected(event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && DoHyArrakis.onArrakis(player)
                && !player.isCreative() && isProtected(event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension() == DoHyArrakis.DIMENSION) {
            event.getAffectedBlocks().removeIf(ArrakisPort::isProtected);
        }
    }

    /** Une construction en cours : poser le plan chunk apres chunk, raccorder, etiqueter. */
    private static final class Job {
        private final ServerLevel level;
        private final PortBuilder.Plan plan;
        private final long[] order;
        private final Set<UUID> waiting = new LinkedHashSet<>();
        private final List<BlockPos> shaped = new ArrayList<>();
        private int index;
        private int shapeIndex;
        private boolean labelled;

        Job(ServerLevel level) {
            this.level = level;
            this.plan = PortBuilder.plan();
            this.order = plan.blocks().keySet().toLongArray();
            // chunk par chunk : chaque tick ne fait generer que quelques chunks d'Arrakis
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
            if (!labelled) {
                labelled = true;
                for (PortBuilder.Label label : plan.labels()) {
                    level.setBlock(label.pos(), DoHySpawn.HOLOGRAM.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                    if (level.getBlockEntity(label.pos()) instanceof HologramBlockEntity hologram) {
                        hologram.set(label.text(), label.color(), label.scale());
                    }
                }
                return false;
            }
            return true;
        }
    }
}

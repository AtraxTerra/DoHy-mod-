package fr.atraxterra.dohy.spawn;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.palamod.dungeon.ReturnPoint;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Aller au spawn, en revenir, et le construire quand il le faut.
 *
 * <p>La construction se fait par tranches, quelques milliers de blocs par
 * tick, pour ne pas figer le jeu : celui qui demande le spawn pendant ce temps
 * attend ou il est et y est envoye a la fin.
 */
public final class SpawnApi {

    /** Blocs poses par tick pendant la construction. */
    private static final int PER_TICK = 5000;

    private static Job job;

    private SpawnApi() {
    }

    public static boolean inSpawn(net.minecraft.world.entity.Entity entity) {
        return entity.level().dimension() == DoHySpawn.DIMENSION;
    }

    /** La commande et la carte du menu : au spawn si l'on n'y est pas, retour sinon. */
    public static void toggle(ServerPlayer player) {
        if (inSpawn(player)) {
            leave(player);
        } else {
            enter(player);
        }
    }

    public static void enter(ServerPlayer player) {
        if (player.level().dimension() != Level.OVERWORLD && player.level().dimension() != Level.NETHER
                && player.level().dimension() != Level.END) {
            tell(player, "Le spawn ne se rejoint pas d'ici.", ChatFormatting.RED);
            return;
        }
        MinecraftServer server = player.level().getServer();
        ServerLevel level = server.getLevel(DoHySpawn.DIMENSION);
        if (level == null) {
            tell(player, "Le spawn n'existe pas dans ce monde.", ChatFormatting.RED);
            return;
        }
        if (level.getData(DoHySpawn.BUILT.get()) != SpawnBuilder.VERSION) {
            if (job == null) {
                job = new Job(level, level.getData(DoHySpawn.BUILT.get()) > 0);
                DoHy.LOGGER.info("Spawn : construction de la version {} ({} blocs)", SpawnBuilder.VERSION,
                        job.plan.blocks().size());
            }
            job.waiting.add(player.getUUID());
            tell(player, "Le spawn se construit, tu y seras envoye dans quelques secondes.", ChatFormatting.GOLD);
            return;
        }
        send(player, level);
    }

    private static void send(ServerPlayer player, ServerLevel level) {
        player.setData(DoHySpawn.RETURN.get(), ReturnPoint.of(player));
        player.closeContainer();
        toPad(player, level);
        tell(player, "Bienvenue au spawn. /spawn pour repartir.", ChatFormatting.GREEN);
    }

    /** Pose le joueur au centre de la plateforme d'arrivee, tourne vers le pont. */
    static void toPad(ServerPlayer player, ServerLevel level) {
        player.fallDistance = 0.0f;
        player.teleport(new TeleportTransition(level,
                new Vec3(0.5d, SpawnBuilder.FLOOR + 1.0d, SpawnBuilder.PAD_Z + 0.5d), Vec3.ZERO, 180.0f, 0.0f,
                TeleportTransition.DO_NOTHING));
    }

    /** Ramene le joueur d'ou il venait. */
    public static void leave(ServerPlayer player) {
        if (!inSpawn(player)) {
            return;
        }
        ReturnPoint point = player.getData(DoHySpawn.RETURN.get());
        player.setData(DoHySpawn.RETURN.get(), ReturnPoint.NONE);
        player.closeContainer();
        player.fallDistance = 0.0f;
        point.send(player);
    }

    private static void tell(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    /** Un tick de construction, s'il y en a une en cours. */
    static void tick(MinecraftServer server) {
        if (job == null) {
            return;
        }
        if (job.step()) {
            Job done = job;
            job = null;
            done.level.setData(DoHySpawn.BUILT.get(), SpawnBuilder.VERSION);
            DoHy.LOGGER.info("Spawn : construit");
            for (UUID id : done.waiting) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null && !inSpawn(player)) {
                    send(player, done.level);
                }
            }
        }
    }

    static void stop() {
        job = null;
    }

    /** Vrai tant qu'une construction est en cours. */
    public static boolean building() {
        return job != null;
    }

    /** Une construction en cours : vider l'ancien, poser le nouveau, raccorder, etiqueter. */
    private static final class Job {
        private final ServerLevel level;
        private final SpawnBuilder.Plan plan;
        private final Set<UUID> waiting = new LinkedHashSet<>();
        private final ObjectIterator<Long2ObjectMap.Entry<BlockState>> placing;
        private final List<BlockPos> shaped = new ArrayList<>();
        private boolean clearing;
        private int clearX = -SpawnBuilder.CLEAR_XZ;
        private int shapeIndex;
        private boolean labelled;

        Job(ServerLevel level, boolean rebuild) {
            this.level = level;
            this.plan = SpawnBuilder.plan();
            this.placing = plan.blocks().long2ObjectEntrySet().iterator();
            this.clearing = rebuild;
        }

        /** Avance d'un tick ; vrai quand tout est fini. */
        boolean step() {
            if (clearing) {
                clear();
                return false;
            }
            if (placing.hasNext()) {
                for (int count = 0; count < PER_TICK && placing.hasNext(); count++) {
                    Long2ObjectMap.Entry<BlockState> entry = placing.next();
                    BlockPos pos = BlockPos.of(entry.getLongKey());
                    BlockState state = entry.getValue();
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    if (state.getBlock() instanceof IronBarsBlock || state.getBlock() instanceof WallBlock) {
                        shaped.add(pos);
                    }
                }
                return false;
            }
            if (shapeIndex < shaped.size()) {
                // Barreaux et murets : leur forme depend de leurs voisins, connus maintenant.
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
                for (SpawnBuilder.Label label : plan.labels()) {
                    level.setBlock(label.pos(), DoHySpawn.HOLOGRAM.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                    if (level.getBlockEntity(label.pos()) instanceof HologramBlockEntity hologram) {
                        hologram.set(label.text(), label.color(), label.scale());
                    }
                }
                return false;
            }
            return true;
        }

        /**
         * Vide le volume bati de l'ancienne version, une tranche de x par tick :
         * ce qui n'est pas dans le nouveau plan redevient de l'air. La roche
         * autour n'est pas touchee.
         */
        private void clear() {
            for (int slice = 0; slice < 4 && clearX <= SpawnBuilder.CLEAR_XZ; slice++, clearX++) {
                for (int z = -SpawnBuilder.CLEAR_XZ; z <= SpawnBuilder.PAD_Z + 16; z++) {
                    for (int y = SpawnBuilder.CLEAR_Y_MIN; y <= SpawnBuilder.CLEAR_Y_MAX; y++) {
                        BlockPos pos = new BlockPos(clearX, y, z);
                        if (!plan.blocks().containsKey(pos.asLong()) && !level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
            if (clearX > SpawnBuilder.CLEAR_XZ) {
                clearing = false;
            }
        }
    }
}

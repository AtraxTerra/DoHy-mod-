package fr.atraxterra.dohy.palamod.defense;

import fr.atraxterra.dohy.palamod.wither.PalaWither;
import fr.atraxterra.dohy.palamod.wither.WitherUpgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Le liant des objets de defense : les verrous de pose et d'usage, les
 * withers contre les murs, les pieges a declencher, et les chunks purges par le
 * home remover.
 */
@EventBusSubscriber
public final class DefenseEvents {

    private record Pending(ServerLevel level, BlockPos pos, BlockState state) {
    }

    private static final Queue<Pending> PENDING = new ArrayDeque<>();

    private DefenseEvents() {
    }

    /**
     * Declenche le piege de {@code state} au prochain tick du serveur. Pas
     * tout de suite : la destruction est en cours (explosion, wither), et un
     * piege qui explose a son tour ne doit pas rentrer dans l'explosion qui
     * l'a casse.
     */
    public static void defer(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DefenseObsidianBlock obsidian && obsidian.effect() != ObsidianEffect.NONE) {
            PENDING.add(new Pending(level, pos.immutable(), state));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int budget = PENDING.size();
        while (budget-- > 0) {
            Pending pending = PENDING.poll();
            if (pending == null) {
                break;
            }
            ((DefenseObsidianBlock) pending.state().getBlock()).effect()
                    .apply(pending.level(), pending.pos(), pending.state());
        }
    }

    /** Pose d'un bloc verrouille : annulee, l'objet revient au joueur. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String key = DefenseGate.key(event.getPlacedBlock().getBlock());
        if (key != null && !DefenseGate.check(player, key)) {
            event.setCanceled(true);
        }
    }

    /** Le seau de Fake Water : un seau vanilla, verrouille ici plutot que dans sa classe. */
    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getItemStack().is(PalaDefense.FAKE_WATER_BUCKET.get())
                && !DefenseGate.check(player, DefenseGate.key(PalaDefense.FAKE_WATER_BUCKET.get()))) {
            event.setCanceled(true);
        }
    }

    /**
     * Un monstre (wither, dragon) qui casse un bloc. Les enclumes encaissent
     * leurs coups ; les obsidiennes piegees cedent et se declenchent.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityBreak(LivingDestroyBlockEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getState();
        if (state.getBlock() instanceof DefenseAnvilBlock anvil) {
            int hits = state.getValue(DefenseAnvilBlock.HITS);
            if (hits < anvil.resistance()) {
                event.setCanceled(true);
                level.setBlock(event.getPos(), state.setValue(DefenseAnvilBlock.HITS, hits + 1), Block.UPDATE_ALL);
            }
            return;
        }
        // Le wither visqueux (lot 22d-bis) casse la slime obsidienne sans la declencher.
        if (event.getEntity() instanceof PalaWither wither && wither.upgrade() == WitherUpgrade.SLIME
                && state.is(PalaDefense.SLIME_OBSIDIAN.get())) {
            return;
        }
        defer(level, event.getPos(), state);
    }

    /** Un chunk purge par le home remover ne fait plus naitre de monstres pendant une heure. */
    @SubscribeEvent
    public static void onSpawn(FinalizeSpawnEvent event) {
        if (!(event.getEntity() instanceof Enemy)) {
            return;
        }
        EntitySpawnReason reason = event.getSpawnType();
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION
                && reason != EntitySpawnReason.PATROL && reason != EntitySpawnReason.REINFORCEMENT) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        // Le chunk par l'accesseur de l'evenement, comme la protection des
        // claims : pendant la generation, c'est un proto-chunk sans donnees.
        if (event.getLevel().getChunk(pos) instanceof LevelChunk chunk
                && chunk.getData(PalaDefense.SPAWN_WARD.get()) > event.getLevel().getLevel().getGameTime()) {
            event.setSpawnCancelled(true);
        }
    }
}

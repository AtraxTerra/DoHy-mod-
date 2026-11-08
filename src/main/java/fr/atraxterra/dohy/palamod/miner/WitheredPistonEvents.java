package fr.atraxterra.dohy.palamod.miner;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * L'obtention du piston renforce wither.
 *
 * <p>Releve dans {@code EventsManager.onWitheredPiston} : sur une explosion
 * causee par un Wither, si le joueur le plus proche (30 blocs) a le niveau de
 * Mineur requis (18 dans le code, donc 90), chaque piston normal pris dans
 * l'explosion — pas collant — devient un piston renforce wither au sol.
 *
 * <p>En 1.7.10, {@code getExplosivePlacedBy} ne rendait une source que pour une
 * creature : seule l'explosion d'apparition du Wither comptait, pas ses cranes.
 * La source <i>directe</i> doit donc etre le Wither lui-meme. Et avec
 * {@code mobGriefing} a faux, l'explosion n'emporte aucun bloc en 26.2 : la
 * duplication que permettait l'original (pistons gagnes sans perdre les
 * pistons) n'existe plus.
 */
@EventBusSubscriber
public final class WitheredPistonEvents {

    /** {@code getClosestPlayer(..., 30.0)}. */
    private static final double PLAYER_RANGE = 30.0d;

    /** {@code addBlacklistedCraft(18, WITHERED_REINFORCED_PISTON)}, x5. */
    private static final int REQUIRED_LEVEL = 90;

    private WitheredPistonEvents() {
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getExplosion().getDirectSourceEntity() instanceof WitherBoss)) {
            return;
        }
        Vec3 center = event.getExplosion().center();
        if (!(level.getNearestPlayer(center.x, center.y, center.z, PLAYER_RANGE, false)
                instanceof ServerPlayer player)) {
            return;
        }
        if (!player.isCreative() && !JobApi.meets(player, Job.MINER, REQUIRED_LEVEL)) {
            return;
        }
        for (BlockPos pos : event.getAffectedBlocks()) {
            if (!level.getBlockState(pos).is(Blocks.PISTON)) {
                continue;
            }
            level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5d, pos.getY() + 0.5d,
                    pos.getZ() + 0.5d, new ItemStack(PalaMiner.WITHERED_REINFORCED_PISTON_ITEM.get())));
        }
    }
}

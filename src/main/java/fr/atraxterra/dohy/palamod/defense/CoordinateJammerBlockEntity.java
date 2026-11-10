package fr.atraxterra.dohy.palamod.defense;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Le brouillage : une fois par seconde, les monstres alentour perdent les joueurs lointains. */
public class CoordinateJammerBlockEntity extends BlockEntity {

    static final double RADIUS = 16.0d;
    static final double SIGHT = 6.0d;

    public CoordinateJammerBlockEntity(BlockPos pos, BlockState state) {
        super(PalaDefense.COORDINATE_JAMMER_ENTITY.get(), pos, state);
    }

    static void tick(Level level, BlockPos pos, BlockState state, CoordinateJammerBlockEntity jammer) {
        if ((level.getGameTime() + pos.asLong()) % 20L != 0L) {
            return;
        }
        AABB box = new AABB(pos).inflate(RADIUS);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, mob -> mob instanceof Enemy && mob.isAlive())) {
            LivingEntity target = mob.getTarget();
            if (target instanceof Player && mob.distanceToSqr(target) > SIGHT * SIGHT) {
                mob.setTarget(null);
            }
        }
    }
}

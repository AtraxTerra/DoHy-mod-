package fr.atraxterra.dohy.palamod.unlock;

import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * Le bateau en bambou ({@code EntityBambooBoat} de PalaJobs, Chasseur 10).
 *
 * <p>Le wiki : « multiplie par 3 la vitesse de navigation ». Le code le dit
 * aussi : la vitesse maximale passe de 0,35 a 1,05 et la poussee de 0,07 a
 * 0,21, soit trois fois celles du bateau vanilla de 1.7.10.
 *
 * <p>En 26.2, la poussee du bateau ({@code controlBoat}, 0,04 par tick quand
 * on avance) est privee : ce bateau y ajoute deux fois la meme poussee, sur
 * l'eau seulement, quand les deux pagaies battent (on avance). Avec le
 * frottement de l'eau (0,9), la vitesse de croisiere triple. Sur la glace
 * ou la terre, il reste un bateau ordinaire.
 */
public class BambooBoat extends Boat {

    /** La poussee vanilla d'un tick ({@code controlBoat} : 0,04). */
    static final float VANILLA_PUSH = 0.04f;
    /** Trois fois la poussee : les deux tiers manquants, ajoutes a chaque tick. */
    static final float EXTRA_PUSH = VANILLA_PUSH * 2.0f;

    public BambooBoat(EntityType<? extends Boat> type, Level level, Supplier<Item> drop) {
        super(type, level, drop);
    }

    /**
     * Assis sur le plancher : la texture de PalaJobs ne dessine que le fond, le
     * bateau est un radeau. On s'y assoit a la hauteur du radeau de 26.2
     * ({@code Raft.rideHeight} : la hauteur x 0,8888889), pas dans un creux.
     */
    @Override
    protected double rideHeight(EntityDimensions dimensions) {
        return dimensions.height() * 0.8888889f;
    }

    @Override
    public void tick() {
        super.tick();
        if (!isLocalInstanceAuthoritative() || !getPaddleState(0) || !getPaddleState(1) || !onWater()) {
            return;
        }
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(motion.x - Mth.sin(yaw) * EXTRA_PUSH, motion.y, motion.z + Mth.cos(yaw) * EXTRA_PUSH);
    }

    /** L'eau sous la coque, ou autour d'elle. */
    private boolean onWater() {
        return level().getFluidState(blockPosition()).is(FluidTags.WATER)
                || level().getFluidState(blockPosition().below()).is(FluidTags.WATER);
    }
}

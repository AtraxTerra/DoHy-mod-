package fr.atraxterra.dohy.palamod.mount;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Le Tedarok ({@code EntityTedarokMount}, {@code TedarokMount}) : la monture
 * legere. 1,45 x 1,65 ; 200 PV et 18 cases au niveau 100 ; vitesse x1,15 ;
 * detente x3,5, un saut d'environ cinq blocs.
 *
 * <p>Ses competences : une marche de cinq blocs ({@code AutoJumpSkill(5)}),
 * la course x1,9 a la touche de course ({@code RunningSkill}), la marche sur
 * l'eau ({@code WaterWalkSkill}) et, au niveau 50, la course sur l'eau
 * ({@code WaterRunSkill}).
 *
 * <p><b>Ecart</b> : Mount le laissait nager a la surface, une poussee
 * rattrapant sa chute ; ici il marche sur l'eau calme comme l'arpenteur sur la
 * lave, a la vitesse de la terre. Tombe dans l'eau, il remonte.
 */
public class Tedarok extends Dancarok {

    public Tedarok(EntityType<? extends Tedarok> type, Level level) {
        super(type, level);
    }

    /** Celles de toute monture, et la marche de cinq blocs. */
    public static AttributeSupplier.Builder createAttributes() {
        return Dancarok.createAttributes().add(Attributes.STEP_HEIGHT, MountSpecies.TEDAROK_STEP);
    }

    @Override
    public MountSpecies species() {
        return MountSpecies.TEDAROK;
    }

    @Override
    public boolean canStandOnFluid(FluidState fluid) {
        return isTamed() && fluid.is(FluidTags.WATER);
    }

    /** Enfonce dans l'eau, la poussee de {@code WaterWalkSkill} le ramene a la surface, ou il se tient. */
    @Override
    public void tick() {
        super.tick();
        if (isTamed() && getFluidHeight(FluidTags.WATER) > 0.5d) {
            Vec3 motion = getDeltaMovement();
            if (motion.y < 0.1d) {
                setDeltaMovement(motion.x, Math.min(0.1d, motion.y + 0.1d), motion.z);
            }
        }
    }

    /**
     * La course : x1,9 sur la terre, et sur l'eau des le niveau 50 ; sinon
     * x1,15, la vitesse de l'espece, sur la terre comme sur l'eau.
     */
    @Override
    protected double speedFactor(Player player) {
        if (riderSprint && (!onWater() || mountLevel() >= MountSpecies.WATER_RUN_LEVEL)) {
            return MountSpecies.TEDAROK_RUN;
        }
        return species().speed();
    }

    private boolean onWater() {
        return isInWater() || level().getFluidState(BlockPos.containing(getX(), getY() - 0.1d, getZ()))
                .is(FluidTags.WATER);
    }

    /** En course : l'animation de course. */
    public boolean isRunning() {
        return isVehicle() && riderSprint;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return PalaMount.TEDAROK_IDLE.get();
    }

    @Override
    protected SoundEvent jumpSound() {
        return PalaMount.TEDAROK_JUMP.get();
    }

    /** {@code func_145780_a} : un pas sur trois, et de temps en temps un cri. */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (state.liquid()) {
            return;
        }
        if (tickCount % 3 == 0) {
            playSound(PalaMount.TEDAROK_WALK.get(), 0.15f, 1.0f);
        }
        if (random.nextInt(30) == 0) {
            playSound(PalaMount.TEDAROK_IDLE.get(), 0.6f, 1.0f);
        }
    }
}

package fr.atraxterra.dohy.palamod.mount;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Le Ravirok ({@code EntityRavirokMount}, {@code RavirokMount}) : la monture
 * volante. 1,85 x 1,6 ; 100 PV et 9 cases au niveau 100 ; vitesse x1,3 ;
 * aucune detente.
 *
 * <p>En selle, il ne marche pas : la touche de saut le fait monter (0,15 bloc
 * par tick, jamais au-dessus de la couche 256), la touche de course descendre
 * (0,3), et il plane entre les deux ({@code CSMountMovement} 1 et 2,
 * {@code FlySkill}). Il bat des ailes toutes les secondes en vol. Le cavalier
 * qui descend en plein vol tombe, et prend ses degats de chute.
 */
public class Ravirok extends Dancarok {

    /** {@code setFlyMovementPower(0.15F)} et {@code (-0.3F)}. */
    private static final double CLIMB = 0.15d;
    private static final double DIVE = -0.3d;
    /** {@code onEntityUpdate} : ramene a 256. */
    public static final double CEILING = 256.0d;

    /** {@code animation.ravirok.fly}, en vol. */
    public final AnimationState flyAnimation = new AnimationState();

    public Ravirok(EntityType<? extends Ravirok> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Dancarok.createAttributes();
    }

    @Override
    public MountSpecies species() {
        return MountSpecies.RAVIROK;
    }

    /** Monte, et pas posee sans monter : en l'air. */
    public boolean isFlying() {
        return isTamed() && isVehicle() && !(onGround() && !riderJump);
    }

    @Override
    public void tick() {
        // Montee, la monture ne tombe pas : le serveur ne la prend pas pour un vehicule qui flotte.
        setNoGravity(isTamed() && isVehicle());
        super.tick();
        if (getY() > CEILING) {
            setPos(getX(), CEILING, getZ());
        }
        if (level().isClientSide()) {
            flyAnimation.animateWhen(isVehicle() && !onGround(), tickCount);
        }
    }

    @Override
    protected void ridingTick(ServerPlayer player, MountData data) {
        if (getControllingPassenger() == player && !onGround() && tickCount % 20 == 0) {
            playSound(PalaMount.RAVIROK_FLAP.get(), 0.4f, 1.0f);
        }
    }

    /** Il ne marche pas : au sol, sans monter, il ne bouge pas. */
    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travel) {
        if (onGround() && !riderJump) {
            return Vec3.ZERO;
        }
        return super.getRiddenInput(player, travel);
    }

    /**
     * En vol : l'elan vertical vient des touches, sans gravite ; a
     * l'horizontale, l'air ralentit comme pour tout ce qui vole (0,91).
     */
    @Override
    public void travel(Vec3 input) {
        if (!isFlying()) {
            super.travel(input);
            return;
        }
        double climb = riderJump && getY() < CEILING ? CLIMB : riderSprint ? DIVE : 0.0d;
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(motion.x, climb, motion.z);
        moveRelative(getSpeed() * 0.1f, new Vec3(input.x, 0.0d, input.z));
        move(MoverType.SELF, getDeltaMovement());
        Vec3 after = getDeltaMovement();
        setDeltaMovement(after.x * 0.91d, 0.0d, after.z * 0.91d);
        calculateEntityAnimation(true);
    }

    @Override
    public boolean canJump() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return PalaMount.RAVIROK_IDLE.get();
    }

    @Override
    protected SoundEvent jumpSound() {
        return PalaMount.RAVIROK_JUMP.get();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        // un oiseau ne fait pas de pas sonores
    }
}

package fr.atraxterra.dohy.palamod.throne;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Le siege d'un trone ({@code EntitySittableBlock}) : une entite sans taille ni
 * rendu, posee sur l'assise, que le joueur monte.
 *
 * <p>Elle disparait des que son passager descend ou que le trone n'est plus la ;
 * elle n'est jamais sauvegardee. Le joueur assis regarde du cote du trone, a
 * 105 degres pres, comme dans une barque.
 */
public class ThroneSeat extends Entity {

    /** L'ecart permis entre le regard du joueur et celui du trone, comme la barque. */
    private static final float MAX_TURN = 105.0f;

    public ThroneSeat(EntityType<? extends ThroneSeat> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Pose le siege sur l'assise du trone, tourne comme lui. */
    void seatAt(BlockPos throne, Direction facing) {
        setPos(throne.getX() + 0.5d, throne.getY() + ThroneBlock.SEAT_HEIGHT, throne.getZ() + 0.5d);
        setYRot(facing.toYRot());
        yRotO = getYRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel && (getPassengers().isEmpty() || !onThrone())) {
            ejectPassengers();
            discard();
        }
    }

    private boolean onThrone() {
        return level().getBlockState(blockPosition()).getBlock() instanceof ThroneBlock;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        super.positionRider(passenger, move);
        clampRotation(passenger);
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        clampRotation(passenger);
    }

    /** {@code AbstractBoat.clampRotation} : le corps suit le trone, la tete tourne a 105 degres pres. */
    private void clampRotation(Entity passenger) {
        passenger.setYBodyRot(getYRot());
        float delta = Mth.wrapDegrees(passenger.getYRot() - getYRot());
        float clamped = Mth.clamp(delta, -MAX_TURN, MAX_TURN);
        passenger.yRotO += clamped - delta;
        passenger.setYRot(passenger.getYRot() + clamped - delta);
        passenger.setYHeadRot(passenger.getYRot());
    }

    /** On se releve devant le trone ; a defaut, au-dessus. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos throne = blockPosition();
        BlockState state = level().getBlockState(throne);
        Direction facing = state.getBlock() instanceof ThroneBlock ? state.getValue(ThroneBlock.FACING)
                : Direction.fromYRot(getYRot());
        BlockPos front = throne.relative(facing);
        for (BlockPos candidate : new BlockPos[] {front, front.relative(facing.getClockWise()),
                front.relative(facing.getCounterClockWise()), throne.above(2)}) {
            Vec3 spot = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), candidate, true);
            if (spot != null) {
                return spot;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }
}

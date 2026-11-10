package fr.atraxterra.dohy.palamod.pillage;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Une dynamite lancee ({@code DynamiteEntity}) : elle vole comme une boule de
 * neige, se colle au premier bloc ou a la premiere creature touchee, puis saute.
 *
 * <p>Le genre de dynamite est celui de l'objet lance ({@link #getItem()}),
 * synchronise par {@link ThrowableItemProjectile} : le client dessine donc la
 * bonne texture sans rien de plus. L'etat colle, lui, est synchronise ici —
 * sans lui, le client continuerait de la faire tomber entre deux corrections
 * de position du serveur.
 *
 * <p>La source de l'explosion est la dynamite, son auteur le lanceur : la
 * protection des claims laisse donc un joueur dynamiter son propre claim, et
 * celui de personne d'autre.
 */
public class DynamiteEntity extends ThrowableItemProjectile {

    private static final EntityDataAccessor<Boolean> STUCK =
            SynchedEntityData.defineId(DynamiteEntity.class, EntityDataSerializers.BOOLEAN);

    private int fuse = DynamiteKind.FUSE;

    public DynamiteEntity(EntityType<? extends DynamiteEntity> type, Level level) {
        super(type, level);
    }

    public DynamiteEntity(Level level, LivingEntity owner, ItemStack stack) {
        super(PalaPillage.DYNAMITE_ENTITY.get(), owner, level, stack);
        this.fuse = DynamiteKind.FUSE + level.getRandom().nextInt(DynamiteKind.FUSE_SPREAD);
    }

    @Override
    protected Item getDefaultItem() {
        return PalaPillage.DYNAMITE.get();
    }

    public DynamiteKind kind() {
        return getItem().getItem() instanceof DynamiteItem dynamite ? dynamite.kind() : DynamiteKind.DYNAMITE;
    }

    public boolean stuck() {
        return entityData.get(STUCK);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STUCK, false);
    }

    @Override
    public void tick() {
        if (stuck()) {
            // Collee : plus de vol ni de gravite, seulement le tic de base (feu, eau).
            setDeltaMovement(Vec3.ZERO);
            baseTick();
        } else {
            super.tick();
        }
        if (isRemoved()) {
            return;
        }
        if (level() instanceof ServerLevel server) {
            if (--fuse <= 0) {
                discard();
                explode(server);
            }
        } else if (tickCount % 2 == 0) {
            level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.2d, getZ(), 0.0d, 0.02d, 0.0d);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        // Un rien en retrait du point d'impact : collee a la face, pas dedans.
        Vec3 motion = getDeltaMovement();
        Vec3 at = hit.getLocation();
        if (motion.lengthSqr() > 1.0e-6d) {
            at = at.subtract(motion.normalize().scale(0.05d));
        }
        stick(at);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        stick(position());
    }

    private void stick(Vec3 at) {
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        entityData.set(STUCK, true);
    }

    private void explode(ServerLevel level) {
        if (!level.getGameRules().get(GameRules.TNT_EXPLODES)) {
            return;
        }
        DynamiteKind kind = kind();
        if (kind.silent()) {
            // Sans son ni eclats de blocs : la ninja dynamite.
            level.explode(this, Explosion.getDefaultDamageSource(level, this), null, getX(), getY(), getZ(),
                    kind.power(), false, Level.ExplosionInteraction.TNT, ParticleTypes.EXPLOSION,
                    ParticleTypes.EXPLOSION_EMITTER, WeightedList.of(), Holder.direct(SoundEvents.EMPTY));
        } else {
            level.explode(this, getX(), getY(), getZ(), kind.power(), Level.ExplosionInteraction.TNT);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("fuse", fuse);
        output.putBoolean("stuck", stuck());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        fuse = input.getIntOr("fuse", DynamiteKind.FUSE);
        entityData.set(STUCK, input.getBooleanOr("stuck", false));
        if (stuck()) {
            setNoGravity(true);
        }
    }
}

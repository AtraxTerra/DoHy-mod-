package fr.atraxterra.dohy.palamod.wither;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Un wither du pillage : le wither vanilla, et ce que son genre y change
 * ({@link WitherKind}).
 *
 * <p>Le genre se lit dans le type d'entite — un par genre, pour que chacun ait
 * ses points de vie, sa taille et son rendu. Il est donc connu des le
 * constructeur de {@code Mob}, ou les buts d'IA sont poses.
 *
 * <ul>
 *   <li><b>Allie</b> : ses buts ne visent que les monstres, et ses deux tetes
 *       laterales refusent toute autre cible ({@link #setAlternativeTarget}).</li>
 *   <li><b>Immobile</b> : sa position horizontale est rendue apres chaque pas
 *       d'IA ; l'assoiffe avance quand on le pousse, le predateur et le supreme
 *       vers le point de leur laser de ciblage.</li>
 *   <li><b>Monte</b> : une selle de wither pour s'y asseoir, la canne a peche de
 *       wither en main pour le diriger du regard. L'eau le desselle, pour de
 *       bon.</li>
 *   <li><b>Supreme</b> : on ne le tue pas ; il appelle un predateur toutes les
 *       deux minutes et s'autodetruit au bout d'un quart d'heure.</li>
 * </ul>
 */
public class PalaWither extends WitherBoss {

    /** Le supreme : un predateur toutes les deux minutes, cinq au plus autour de lui. */
    static final int SUPREME_CALL = 2 * 60 * 20;
    static final int SUPREME_CALL_MAX = 5;
    /** Le supreme saute au bout de quinze minutes. */
    static final int SUPREME_LIFE = 15 * 60 * 20;
    static final float SUPREME_BLAST = 16.0f;
    /** Le supreme ne s'eveille qu'au bout d'une minute (douze heures sur Paladium). */
    static final int SUPREME_WAKE = 60 * 20;
    static final double WAYPOINT_SPEED = 0.3d;
    static final double STEER_SPEED = 0.5d;

    private int life;
    private BlockPos waypoint;
    private String upgrade = "";
    private long upgradeUntil;
    private boolean saddled;
    private boolean saddleSpent;
    private Vec3 pendingPush = Vec3.ZERO;

    public PalaWither(EntityType<? extends PalaWither> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes(WitherKind kind) {
        return WitherBoss.createAttributes()
                .add(Attributes.MAX_HEALTH, kind.maxHealth())
                .add(Attributes.SCALE, kind.scale());
    }

    public WitherKind kind() {
        return WitherKind.of(getType());
    }

    /** Ce qu'un wither allie peut viser : les monstres, pas les autres withers. */
    static boolean hostile(LivingEntity entity) {
        return entity instanceof Enemy && !(entity instanceof WitherBoss) && entity.isAlive();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        if (WitherKind.of(getType()).allied()) {
            targetSelector.removeAllGoals(goal -> true);
            targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 0, false, false,
                    (target, level) -> hostile(target)));
        }
    }

    @Override
    public void setTarget(LivingEntity target) {
        if (target != null && kind().allied() && !hostile(target)) {
            return;
        }
        super.setTarget(target);
    }

    @Override
    public void setAlternativeTarget(int head, int id) {
        if (id > 0 && kind().allied() && !(level().getEntity(id) instanceof LivingEntity living && hostile(living))) {
            id = 0;
        }
        super.setAlternativeTarget(head, id);
    }

    // -- Degats ------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (kind() == WitherKind.SUPREME && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (upgrade() == WitherUpgrade.RESISTANCE && !(source.getEntity() instanceof Player)) {
            amount *= 0.5f;
        }
        return super.hurtServer(level, source, amount);
    }

    // -- Mouvement ---------------------------------------------------------------

    @Override
    public void aiStep() {
        if (level().isClientSide()) {
            super.aiStep();
            return;
        }
        WitherKind kind = kind();
        Player rider = steeringRider();
        if (rider != null) {
            super.setTarget(null);
            super.setAlternativeTarget(0, 0);
            setDeltaMovement(rider.getLookAngle().scale(STEER_SPEED));
        }
        double x = getX();
        double z = getZ();
        super.aiStep();
        if (rider == null && kind.pinned()) {
            setPos(x, getY(), z);
            setDeltaMovement(0.0d, getDeltaMovement().y, 0.0d);
            Vec3 step = Vec3.ZERO;
            if (waypoint != null) {
                Vec3 toward = new Vec3(waypoint.getX() + 0.5d - x, 0.0d, waypoint.getZ() + 0.5d - z);
                if (toward.horizontalDistanceSqr() > 9.0d) {
                    step = toward.normalize().scale(WAYPOINT_SPEED);
                } else {
                    waypoint = null;
                }
            }
            step = step.add(pendingPush.x, 0.0d, pendingPush.z);
            pendingPush = Vec3.ZERO;
            if (step.lengthSqr() > 1.0e-6d) {
                move(MoverType.SELF, step);
            }
        }
        if (isVehicle() && isInWater()) {
            // L'eau desselle le wither, et il ne se remonte plus.
            ejectPassengers();
            saddled = false;
            saddleSpent = true;
        }
        if (upgradeUntil > 0L && level().getGameTime() >= upgradeUntil) {
            clearUpgrade();
        }
        life++;
        if (kind == WitherKind.SUPREME && level() instanceof ServerLevel server) {
            superiorTick(server);
        }
    }

    /** Le cavalier qui dirige : assis, la canne a peche de wither en main. */
    private Player steeringRider() {
        if (!(getFirstPassenger() instanceof Player player) || !saddled) {
            return null;
        }
        return player.getMainHandItem().is(PalaWithers.WITHER_FISHING_ROD.get())
                || player.getOffhandItem().is(PalaWithers.WITHER_FISHING_ROD.get()) ? player : null;
    }

    private void superiorTick(ServerLevel level) {
        if (life >= SUPREME_LIFE) {
            discard();
            level.explode(this, getX(), getY(), getZ(), SUPREME_BLAST, Level.ExplosionInteraction.MOB);
            return;
        }
        if (life > SUPREME_WAKE && life % SUPREME_CALL == 0
                && level.getEntitiesOfClass(PalaWither.class, getBoundingBox().inflate(64.0d),
                        wither -> wither.kind() == WitherKind.PREDATOR).size() < SUPREME_CALL_MAX) {
            PalaWither predator = PalaWithers.type(WitherKind.PREDATOR).get().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (predator != null) {
                predator.setPos(getX() + 4.0d, getY(), getZ());
                predator.waypoint = waypoint;
                WitherSummons.copy(this, predator);
                level.addFreshEntity(predator);
            }
        }
    }

    @Override
    public void push(double x, double y, double z) {
        if (kind() == WitherKind.THIRSTY && !level().isClientSide()) {
            pendingPush = pendingPush.add(x, 0.0d, z);
            return;
        }
        super.push(x, y, z);
    }

    @Override
    public boolean isAffectedByFluids() {
        return !liquidProof() && super.isAffectedByFluids();
    }

    @Override
    public boolean isPushedByFluid() {
        return !liquidProof() && super.isPushedByFluid();
    }

    @Override
    public boolean isPushedByFluid(FluidType type) {
        return !liquidProof() && super.isPushedByFluid(type);
    }

    /** L'assoiffe et le supreme ne sentent pas les liquides. */
    private boolean liquidProof() {
        WitherKind kind = kind();
        return kind == WitherKind.THIRSTY || kind == WitherKind.SUPREME;
    }

    // -- Laser, ameliorations, selle ---------------------------------------------

    public BlockPos waypoint() {
        return waypoint;
    }

    public void setWaypoint(BlockPos pos) {
        this.waypoint = pos == null ? null : pos.immutable();
    }

    public WitherUpgrade upgrade() {
        return upgrade.isEmpty() ? null : WitherUpgrade.byId(upgrade);
    }

    public void applyUpgrade(WitherUpgrade value) {
        upgrade = value.id();
        upgradeUntil = level().getGameTime() + WitherUpgrade.DURATION;
        if (value == WitherUpgrade.STEALTH && level() instanceof ServerLevel server) {
            for (ServerPlayer player : server.players()) {
                super.stopSeenByPlayer(player);
            }
        }
    }

    private void clearUpgrade() {
        boolean wasStealth = upgrade() == WitherUpgrade.STEALTH;
        upgrade = "";
        upgradeUntil = 0L;
        if (wasStealth && level() instanceof ServerLevel server) {
            for (ServerPlayer player : server.players()) {
                if (player.distanceToSqr(this) < 128.0d * 128.0d) {
                    startSeenByPlayer(player);
                }
            }
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        // Discret : pas de barre de vie.
        if (upgrade() != WitherUpgrade.STEALTH) {
            super.startSeenByPlayer(player);
        }
    }

    public boolean saddled() {
        return saddled;
    }

    /** Pose la selle, si le wither en veut une : le cavalier monte aussitot. */
    public boolean saddle(Player player) {
        if (!kind().saddleable() || saddled || saddleSpent || isVehicle()) {
            return false;
        }
        saddled = true;
        return player.startRiding(this, true, true);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return saddled && !isVehicle();
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        // Descendu, il ne se remonte plus : la selle de wither ne sert qu'une fois.
        if (!level().isClientSide() && saddled) {
            saddled = false;
            saddleSpent = true;
        }
    }

    // -- Sons ----------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        if (upgrade() == WitherUpgrade.STEALTH) {
            return null;
        }
        return kind() == WitherKind.FUNNY ? PalaWithers.FUNNY_WITHER_LOOP.get() : super.getAmbientSound();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return upgrade() == WitherUpgrade.STEALTH ? null : super.getHurtSound(source);
    }

    @Override
    protected SoundEvent getDeathSound() {
        return upgrade() == WitherUpgrade.STEALTH ? null : super.getDeathSound();
    }

    // -- Sauvegarde ----------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("pala_life", life);
        if (waypoint != null) {
            output.putLong("pala_waypoint", waypoint.asLong());
        }
        output.putString("pala_upgrade", upgrade);
        output.putLong("pala_upgrade_until", upgradeUntil);
        output.putBoolean("pala_saddled", saddled);
        output.putBoolean("pala_saddle_spent", saddleSpent);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        life = input.getIntOr("pala_life", 0);
        long point = input.getLongOr("pala_waypoint", Long.MIN_VALUE);
        waypoint = point == Long.MIN_VALUE ? null : BlockPos.of(point);
        upgrade = input.getStringOr("pala_upgrade", "");
        upgradeUntil = input.getLongOr("pala_upgrade_until", 0L);
        saddled = input.getBooleanOr("pala_saddled", false);
        saddleSpent = input.getBooleanOr("pala_saddle_spent", false);
    }
}

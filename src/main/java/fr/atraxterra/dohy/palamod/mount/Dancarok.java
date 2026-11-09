package fr.atraxterra.dohy.palamod.mount;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Le Dancarok ({@code EntityDancarokMount}, {@code EntityMount}).
 *
 * <p><b>Sauvage</b> (sans proprietaire) : 53 PV, immobile — Mount ne lui
 * donne que « regarder le joueur » et « regarder autour » —, jamais de
 * disparition, ni monte ni nourri. On le capture a l'epee de capture.
 *
 * <p><b>Invoque</b> : une projection de la monture equipee du joueur
 * ({@link MountData}). Ses PV affiches sont recalcules a chaque tick depuis
 * les degats du joueur, tous les coups qu'il recoit y sont reportes (doubles
 * s'il est monte), il consomme un point de nourriture par bloc parcouru, et il
 * disparait si son maitre se deconnecte, change de dimension ou l'a
 * desinvoque. Seul son maitre le monte.
 *
 * <p>Monte : vitesse 0,225 (pres de 10 blocs/s), pas d'un bloc, saut fixe de
 * puissance 50 — environ deux tiers de bloc —, marche arriere au quart. Il
 * coule dans l'eau et son cavalier reste en selle.
 */
public class Dancarok extends PathfinderMob implements PlayerRideableJumping, HasCustomInventoryScreen {

    /** {@code setJumpPower(50)} : 0,4 + 0,4 x 50 / 90. Le client envoie toujours 50. */
    private static final float JUMP_POWER = 0.4f + 0.4f * 50.0f / 90.0f;
    /** {@code special} joue pendant 500 ms apres chaque coup de corne. */
    private static final int SPECIAL_TICKS = 10;

    private static final EntityDataAccessor<Boolean> TAMED =
            SynchedEntityData.defineId(Dancarok.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> HORN =
            SynchedEntityData.defineId(Dancarok.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STRIKE =
            SynchedEntityData.defineId(Dancarok.class, EntityDataSerializers.INT);

    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState specialAnimation = new AnimationState();
    private int specialStart = -SPECIAL_TICKS;

    private UUID owner;
    private Vec3 lastFoodPos;
    private float pendingJump;
    private boolean jumping;
    private int steps;

    public Dancarok(EntityType<? extends Dancarok> type, Level level) {
        super(type, level);
    }

    /** {@code EntityMount.applyEntityAttributes} : 53 PV, vitesse 0,225, saut 0,7. */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 53.0d)
                .add(Attributes.MOVEMENT_SPEED, 0.225d)
                .add(Attributes.JUMP_STRENGTH, 0.7d)
                .add(Attributes.STEP_HEIGHT, 1.0d);
    }

    /** Sur un sol ou un animal peut apparaitre, et a la lumiere ({@code EntityAnimal.getCanSpawnHere}). */
    public static boolean checkSpawnRules(EntityType<Dancarok> type, ServerLevelAccessor level, EntitySpawnReason reason,
                                          BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON) && level.getRawBrightness(pos, 0) > 8;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TAMED, false);
        builder.define(HORN, 0);
        builder.define(STRIKE, 0);
    }

    /** {@code EntityAIWatchClosest(EntityPlayer, 6)} et {@code EntityAILookIdle} : rien d'autre. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0f) {
            @Override
            public boolean canUse() {
                return !isVehicle() && super.canUse();
            }
        });
        goalSelector.addGoal(8, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return !isVehicle() && super.canUse();
            }
        });
    }

    // -- Proprietaire --------------------------------------------------------------

    public boolean isTamed() {
        return entityData.get(TAMED);
    }

    public UUID owner() {
        return owner;
    }

    public void bind(ServerPlayer player) {
        owner = player.getUUID();
        entityData.set(TAMED, true);
    }

    /** 0 sans corne, 1 a 3 selon le palier : choisit la texture. */
    public int hornType() {
        return entityData.get(HORN);
    }

    /** Un coup de corne vient d'etre donne : l'animation {@code special} repart. */
    public void markStrike() {
        entityData.set(STRIKE, entityData.get(STRIKE) + 1);
    }

    private ServerPlayer ownerPlayer(ServerLevel level) {
        return owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
    }

    // -- Tick --------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            serverTick(server);
        } else {
            boolean moving = getX() != xo || getZ() != zo;
            idleAnimation.animateWhen(!moving, tickCount);
            if (specialAnimation.isStarted() && tickCount - specialStart > SPECIAL_TICKS) {
                specialAnimation.stop();
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STRIKE.equals(key) && level().isClientSide()) {
            specialStart = tickCount;
            specialAnimation.start(tickCount);
        }
    }

    private void serverTick(ServerLevel server) {
        if (owner == null) {
            return;
        }
        ServerPlayer player = ownerPlayer(server);
        MountData data = player == null ? null : player.getData(PalaMount.MOUNT);
        if (data == null || !getUUID().equals(data.summoned()) || player.level() != server || !data.isEquipped()) {
            // Monture orpheline : maitre parti, desinvoquee, ou rechargee avec
            // son chunk. Mount la supprimait de meme au chargement du chunk.
            if (data != null && getUUID().equals(data.summoned())) {
                data.setSummoned(null);
            }
            ejectPassengers();
            discard();
            return;
        }

        float max = Math.max(1.0f, data.maxHealth());
        if (getAttributeBaseValue(Attributes.MAX_HEALTH) != max) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        }
        setHealth(Math.max(0.5f, max - data.damage()));
        MountHornItem.Tier tier = MountHornItem.tierOf(data.horn());
        entityData.set(HORN, tier == null ? 0 : tier.id());

        // EntityMount.onLivingUpdate : un point de nourriture par bloc parcouru
        // sous son maitre.
        if (getControllingPassenger() == player) {
            if (lastFoodPos == null) {
                lastFoodPos = position();
            } else if (position().distanceTo(lastFoodPos) >= 1.0d) {
                lastFoodPos = position();
                data.setFood(data.food() - 1);
                if (data.food() <= 0) {
                    MountTexts.error(player, "no_energy");
                    MountManager.unsummon(player, true);
                }
            }
        } else {
            lastFoodPos = null;
        }
    }

    // -- Interaction -------------------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isTamed()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            if (isVehicle()) {
                MountTexts.error(server, "already_used");
            } else if (!server.getUUID().equals(owner)) {
                MountTexts.error(server, "not_yours");
            } else {
                server.startRiding(this);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (player instanceof ServerPlayer server && server.getUUID().equals(owner)) {
            MountMenus.openInventory(server);
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    // -- Conduite ------------------------------------------------------------------

    @Override
    public LivingEntity getControllingPassenger() {
        return isTamed() && getFirstPassenger() instanceof Player player ? player : super.getControllingPassenger();
    }

    /** Lacet du cavalier, la moitie de son tangage. */
    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        setRot(player.getYRot(), player.getXRot() * 0.5f);
        yRotO = getYRot();
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        if (onGround()) {
            jumping = false;
        }
        if (isLocalInstanceAuthoritative() && pendingJump > 0.0f && onGround() && !jumping) {
            executeJump(pendingJump, input);
        }
        pendingJump = 0.0f;
    }

    /** {@code moveEntityWithHeading} : de cote a moitie, en arriere au quart. */
    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travel) {
        float strafe = player.xxa * 0.5f;
        float forward = player.zza;
        if (forward <= 0.0f) {
            forward *= 0.25f;
        }
        return new Vec3(strafe, 0.0d, forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    /**
     * {@code motionY = saut (0,7) x puissance x 1,0 / 1,5}, plus l'effet de
     * saut, et un elan de 0,4 x puissance vers l'avant si l'on avance.
     */
    private void executeJump(float power, Vec3 input) {
        double up = getAttributeValue(Attributes.JUMP_STRENGTH) * power / 1.5d + getJumpBoostPower();
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(motion.x, up, motion.z);
        jumping = true;
        if (input.z > 0.0d) {
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            setDeltaMovement(getDeltaMovement().add(-0.4f * Mth.sin(yaw) * power, 0.0d, 0.4f * Mth.cos(yaw) * power));
        }
    }

    /**
     * Le saut des montures vanilla part au relachement de la touche, avec une
     * puissance chargee ; celui de Mount est fixe. On saute donc a chaque
     * relachement, meme d'un appui trop bref pour charger la barre.
     */
    @Override
    public void onPlayerJump(int strength) {
        if (isTamed()) {
            pendingJump = JUMP_POWER;
        }
    }

    @Override
    public boolean canJump() {
        return isTamed();
    }

    @Override
    public void handleStartJump(int strength) {
    }

    @Override
    public void handleStopJump() {
    }

    // -- Degats --------------------------------------------------------------------

    /**
     * {@code ServerListener.onEntityDamagedEvent} : un sauvage prend ses coups
     * normalement ; une monture invoquee les reporte sur les PV de son maitre,
     * doubles si elle est montee. Le cavalier ne peut pas blesser sa propre
     * monture.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (owner == null) {
            return super.hurtServer(level, source, amount);
        }
        Entity attacker = source.getEntity();
        if (attacker != null && hasPassenger(attacker)) {
            return false;
        }
        if (invulnerableTime > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
            return false;
        }
        ServerPlayer player = ownerPlayer(level);
        if (!isCurrentMount(player)) {
            // Une ancienne monture restee dans un chunk qui ne tourne plus : elle
            // ne doit pas blesser celle qui est invoquee maintenant.
            discard();
            return false;
        }
        invulnerableTime = 20;
        level.broadcastDamageEvent(this, source);
        MountManager.hurt(player, this, amount * (isVehicle() ? 2.0f : 1.0f));
        return true;
    }

    /** {@code fall} : pas de degats de chute, un son a l'atterrissage au-dela d'un bloc. */
    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        if (distance > 1.0d) {
            playSound(PalaMount.DANCAROK_JUMP.get(), 0.4f, 1.0f);
        }
        return false;
    }

    @Override
    public void die(DamageSource source) {
        if (owner != null && level() instanceof ServerLevel server) {
            ServerPlayer player = ownerPlayer(server);
            if (isCurrentMount(player)) {
                MountManager.unsummon(player, true);
            } else {
                discard();
            }
            return;
        }
        super.die(source);
    }

    /** Vrai si cette entite est la monture invoquee de ce joueur. */
    private boolean isCurrentMount(ServerPlayer player) {
        return player != null && getUUID().equals(player.getData(PalaMount.MOUNT).summoned());
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // -- Sons ----------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return PalaMount.DANCAROK_IDLE.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 400;
    }

    @Override
    protected float getSoundVolume() {
        return 0.8f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    /** Un pas sur trois ; de temps en temps, un grognement. */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (state.liquid()) {
            return;
        }
        steps++;
        if (steps % 3 == 0) {
            playSound(PalaMount.DANCAROK_WALK.get(), 0.15f, 1.0f);
        }
        if (steps > 5 && random.nextInt(10) == 0) {
            playSound(PalaMount.DANCAROK_IDLE.get(), 0.6f, 1.0f);
        }
    }

    // -- Sauvegarde ----------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (owner != null) {
            output.store("Owner", UUIDUtil.CODEC, owner);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        entityData.set(TAMED, owner != null);
    }
}

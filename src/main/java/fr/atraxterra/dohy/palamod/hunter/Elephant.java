package fr.atraxterra.dohy.palamod.hunter;

import fr.atraxterra.dohy.palamod.achievement.Achievements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * L'elephant de PalaMod ({@code EntityElephant}, module hunter), releve au
 * bytecode avec son modele et son rendu.
 *
 * <p><b>Trois ages</b>, tires au hasard a la naissance ({@code entityInit} :
 * {@code rand(3)}), chacun sa taille, sa vie, sa force et sa vitesse :
 * <table>
 *   <tr><th></th><th>taille</th><th>vie</th><th>coup</th><th>vitesse</th><th>recul</th></tr>
 *   <tr><td>petit</td><td>1 x 1,5</td><td>25</td><td>5</td><td>0,6</td><td>insensible</td></tr>
 *   <tr><td>ado</td><td>1,5 x 2,3</td><td>50</td><td>10</td><td>0,5</td><td>0,65</td></tr>
 *   <tr><td>adulte</td><td>3 x 4</td><td>100</td><td>25</td><td>0,3</td><td>insensible</td></tr>
 * </table>
 * Le petit est insensible au recul par erreur : l'original comparait l'age a
 * 0,3 et tombait sur la valeur de l'adulte. C'est garde.
 *
 * <p><b>Neutre</b> : il se promene et regarde les joueurs, et ne charge que
 * celui qui le frappe ({@code EntityAIAttackOnCollide} reserve aux joueurs, a
 * 0,8). <b>Ecarts</b> : PalaMod en faisait un monstre ({@code EntityMob}), qui
 * disparaissait en paisible ; c'est ici une creature, qui sait nager. Sans
 * nom ni maitre, il s'en va quand plus personne n'est la, comme un monstre.
 *
 * <p><b>Le bambou</b> ({@code interact}) : un clic droit en donne un, un bloc
 * de bambou en vaut neuf, et accroupi toute la pile y passe. Chaque bambou le
 * soigne d'un point. Seul le petit se laisse nourrir a l'etat sauvage, et
 * s'apprivoise si son compte de bambou depasse un tirage de 0 a 9 : le joueur
 * devient son maitre. Apprivoise, il grandit : ado a 64 bambous, adulte a 128,
 * une deuxieme robe a 1 000, une troisieme a 5 000. A 100 000, il n'a plus
 * faim.
 *
 * <p><b>Monture</b> : son maitre monte en selle d'un clic droit main vide. Un
 * bambou en main, l'elephant avance droit devant, dans le sens du regard du
 * cavalier ({@code onLivingUpdate}) — le petit trotte, l'adulte avance au pas.
 * Sinon il se promene a sa guise, le cavalier sur le dos.
 *
 * <p><b>A sa mort</b>, un ado ou un adulte lache une fois sur 25 de zero a deux
 * defenses d'elephant (plus avec Butin). L'XP de Chasseur est dans
 * {@code job_progression.json} ; le bonus de l'elephant bien nourri, dans
 * {@link ElephantEvents}.
 */
public class Elephant extends PathfinderMob {

    public static final int CHILD = 0;
    public static final int TEEN = 1;
    public static final int ADULT = 2;

    /** Le compte de bambou au-dela duquel il n'en veut plus. */
    public static final int FULL = 100_000;

    private static final EntityDataAccessor<Integer> AGE =
            SynchedEntityData.defineId(Elephant.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR =
            SynchedEntityData.defineId(Elephant.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FEED =
            SynchedEntityData.defineId(Elephant.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> TAMED =
            SynchedEntityData.defineId(Elephant.class, EntityDataSerializers.BOOLEAN);

    /**
     * {@code setSize} par age. Le cavalier s'assied a {@code 0,75 x hauteur} de
     * 1.7.10 ({@code getMountedYOffset}), ou la position du joueur etait celle de
     * ses yeux : ses pieds y etaient a {@code 0,75 x hauteur - 0,5}, et le point
     * d'attache de 26.2 se compte 0,6 plus haut que les pieds.
     */
    private static final EntityDimensions[] SIZES = {size(1.0f, 1.5f), size(1.5f, 2.3f), size(3.0f, 4.0f)};

    private static final double[] HEALTH = {25.0d, 50.0d, 100.0d};
    private static final double[] SPEED = {0.6d, 0.5d, 0.3d};
    private static final double[] DAMAGE = {5.0d, 10.0d, 25.0d};
    private static final double[] KNOCKBACK = {1.0d, 0.65d, 1.0d};

    /** {@code moveEntityWithHeading(0, pas)} sous le cavalier, par age. */
    private static final float[] STRIDE = {0.4f, 0.3f, 0.2f};

    /**
     * La vitesse sous le cavalier, en part de l'attribut. L'original avancait a
     * celle que son IA lui avait laissee, celle de sa promenade (0,6 x
     * l'attribut) ; le sol de 26.2 en donne 1,33 fois plus qu'en 1.7.10.
     */
    private static final double RIDDEN_SPEED = 0.6d / 1.33d;

    /** {@code nextInt(100) < 4} : une mort sur 25. */
    private static final int TUSK_CHANCE = 4;

    private UUID owner;

    public Elephant(EntityType<? extends Elephant> type, Level level) {
        super(type, level);
        xpReward = 5;
        if (!level.isClientSide()) {
            // entityInit : un age au hasard pour toute creature neuve ; celui
            // d'une creature chargee vient ensuite de sa sauvegarde.
            setAge(random.nextInt(3));
            setHealth(getMaxHealth());
        }
    }

    private static EntityDimensions size(float width, float height) {
        float seat = 0.75f * height - 0.5f + 0.6f;
        return EntityDimensions.scalable(width, height)
                .withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0f, seat, 0.0f));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH[CHILD])
                .add(Attributes.MOVEMENT_SPEED, SPEED[CHILD])
                .add(Attributes.ATTACK_DAMAGE, DAMAGE[CHILD])
                .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK[CHILD]);
    }

    public static boolean checkSpawnRules(EntityType<Elephant> type, ServerLevelAccessor level, EntitySpawnReason reason,
                                          BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON) && level.getRawBrightness(pos, 0) > 8;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AGE, CHILD);
        builder.define(COLOR, 0);
        builder.define(FEED, 0);
        builder.define(TAMED, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 0.8d, false) {
            @Override
            public boolean canUse() {
                return Elephant.this.getTarget() instanceof Player && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return Elephant.this.getTarget() instanceof Player && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(2, new RandomStrollGoal(this, 0.6d));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    // -- Etat ------------------------------------------------------------------------

    /** 0 petit, 1 ado, 2 adulte. */
    public int age() {
        return Mth.clamp(entityData.get(AGE), CHILD, ADULT);
    }

    /** La robe de l'adulte : 0, puis 1 a 1 000 bambous, 2 a 5 000. */
    public int color() {
        return Mth.clamp(entityData.get(COLOR), 0, 2);
    }

    /** Le bambou mange depuis sa naissance. */
    public int feed() {
        return entityData.get(FEED);
    }

    public boolean isTamed() {
        return entityData.get(TAMED);
    }

    public UUID owner() {
        return owner;
    }

    /** Change d'age, et prend la vie, la force et la vitesse du nouveau. */
    void setAge(int age) {
        int clamped = Mth.clamp(age, CHILD, ADULT);
        entityData.set(AGE, clamped);
        base(Attributes.MAX_HEALTH, HEALTH[clamped]);
        base(Attributes.MOVEMENT_SPEED, SPEED[clamped]);
        base(Attributes.ATTACK_DAMAGE, DAMAGE[clamped]);
        base(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK[clamped]);
        setHealth(Math.min(getHealth(), getMaxHealth()));
    }

    private void base(Holder<Attribute> attribute, double value) {
        var instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    void tame(ServerPlayer player) {
        owner = player.getUUID();
        entityData.set(TAMED, true);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (AGE.equals(key)) {
            refreshDimensions();
        }
        super.onSyncedDataUpdated(key);
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return SIZES[age()];
    }

    // -- Le bambou -------------------------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return super.mobInteract(player, hand);
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            // En selle : le maitre seul, la main vide.
            if (!isTamed() || isVehicle()) {
                return super.mobInteract(player, hand);
            }
            if (player instanceof ServerPlayer server && server.getUUID().equals(owner)) {
                server.startRiding(this);
            }
            return InteractionResult.SUCCESS;
        }
        int value = held.is(Items.BAMBOO) ? 1 : held.is(Items.BAMBOO_BLOCK) ? 9 : 0;
        if (value == 0 || feed() >= FULL || (!isTamed() && age() != CHILD)) {
            return super.mobInteract(player, hand);
        }
        if (player instanceof ServerPlayer server && level() instanceof ServerLevel level) {
            eatBamboo(server, held, value, level);
        }
        return InteractionResult.SUCCESS;
    }

    /** Un bambou, ou la pile entiere accroupi ; un bloc en vaut neuf. */
    private void eatBamboo(ServerPlayer player, ItemStack held, int value, ServerLevel level) {
        int count = player.isShiftKeyDown() ? held.getCount() : 1;
        int eaten = value * count;
        entityData.set(FEED, feed() + eaten);
        heal(eaten);
        Achievements.count(player, "elephant:bamboo", eaten);
        boolean full = value == 1 && feed() >= FULL;
        burst(level, full ? ParticleTypes.HEART : ParticleTypes.CRIT, full ? 200 : 75, full ? 0.3d : 0.2d);
        if (!player.isCreative()) {
            held.shrink(count);
        }
        if (!isTamed()) {
            if (age() == CHILD && feed() >= random.nextInt(10)) {
                tame(player);
                burst(level, ParticleTypes.HEART, 100, 0.15d);
            }
            return;
        }
        int feed = feed();
        if (feed >= 5_000) {
            grow(ADULT, 2);
        } else if (feed >= 1_000) {
            grow(ADULT, 1);
        } else if (feed >= 128) {
            grow(ADULT, color());
        } else if (feed >= 64) {
            grow(TEEN, color());
        }
    }

    private void grow(int age, int color) {
        if (age != age()) {
            setAge(age);
        }
        entityData.set(COLOR, color);
    }

    private void burst(ServerLevel level, ParticleOptions particle, int count, double speed) {
        level.sendParticles(particle, getX(), getY() + 1.0d, getZ(), count, 0.0d, 0.0d, 0.0d, speed);
    }

    // -- Monture ---------------------------------------------------------------------

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    /** Le cavalier ne conduit qu'un bambou en main ; sinon l'elephant va ou il veut. */
    @Override
    public LivingEntity getControllingPassenger() {
        if (isTamed() && getFirstPassenger() instanceof Player player && player.getMainHandItem().is(Items.BAMBOO)) {
            return player;
        }
        return super.getControllingPassenger();
    }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        // L'original ne prenait que le lacet du cavalier.
        setYRot(player.getYRot());
        yRotO = getYRot();
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travel) {
        return new Vec3(0.0d, 0.0d, STRIDE[age()]);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) (getAttributeValue(Attributes.MOVEMENT_SPEED) * RIDDEN_SPEED);
    }

    // -- Mort, sons, disparition -------------------------------------------------------

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (age() == CHILD || random.nextInt(100) >= TUSK_CHANCE) {
            return;
        }
        // dropFewItems : de 0 a 2, plus de 0 a Butin.
        int count = random.nextInt(3);
        int looting = source.getEntity() instanceof LivingEntity killer
                ? EnchantmentHelper.getItemEnchantmentLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.LOOTING), killer.getMainHandItem())
                : 0;
        if (looting > 0) {
            count += random.nextInt(looting + 1);
        }
        for (int index = 0; index < count; index++) {
            spawnAtLocation(level, new ItemStack(PalaHunter.TUSK.get()));
        }
    }

    /** Les sons d'un monstre de 1.7.10 ; l'original n'en avait d'autres que pour les noms de son equipe. */
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.HOSTILE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.HOSTILE_DEATH;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !isTamed();
    }

    // -- Sauvegarde ------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("color", color());
        output.putInt("age", age());
        output.putInt("feed", feed());
        output.putBoolean("tamed", isTamed());
        if (owner != null) {
            output.store("owner", UUIDUtil.CODEC, owner);
        }
    }

    /**
     * L'age remet les attributs du sien : une creature invoquee avec un age
     * ({@code /summon ... {age:2}}) n'en a pas d'autres que ceux tires a sa
     * construction.
     */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(COLOR, input.getIntOr("color", 0));
        setAge(input.getIntOr("age", CHILD));
        entityData.set(FEED, input.getIntOr("feed", 0));
        entityData.set(TAMED, input.getBooleanOr("tamed", false));
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
    }
}

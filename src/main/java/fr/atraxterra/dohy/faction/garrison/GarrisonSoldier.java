package fr.atraxterra.dohy.faction.garrison;

import fr.atraxterra.dohy.faction.war.Soldier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Un soldat de ta garnison : le soldat des nations rivales ({@link Soldier}), passe de ton cote.
 *
 * <p>Il n'est que la projection d'un {@link Garrison.Member} : jamais ecrit sur disque, il est
 * deploye quand tu es la (autour de ta caserne, ou derriere toi) et rappele quand tu pars ; sa vie
 * est gardee dans ta garnison entre deux deploiements. Mort, il quitte la garnison.
 *
 * <p>Il attaque les soldats des nations, les monstres (pas les creepers) et ce qui s'en prend a
 * toi, aide quand tu frappes, et ne blesse jamais ni toi ni les siens.
 */
public class GarrisonSoldier extends PathfinderMob implements RangedAttackMob {

    private static final EntityDataAccessor<Integer> DATA_ROLE =
            SynchedEntityData.defineId(GarrisonSoldier.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(GarrisonSoldier.class, EntityDataSerializers.INT);

    /** Au-dela, un soldat qui suit son joueur le rejoint d'un saut. */
    static final double CATCH_UP = 20.0d;
    /** Le rayon de l'aura du capitaine. */
    private static final double AURA = 12.0d;

    private UUID owner;
    private UUID member;
    private BlockPos post = BlockPos.ZERO;
    private Garrison.Order order = Garrison.Order.GARDE;
    private int tierBonus;
    private int healCooldown = 40;
    private boolean goalsInstalled;

    public GarrisonSoldier(EntityType<? extends GarrisonSoldier> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        if (getNavigation() instanceof GroundPathNavigation ground) {
            ground.setCanOpenDoors(true);
            ground.setCanFloat(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 26.0d)
                .add(Attributes.ATTACK_DAMAGE, 4.0d)
                .add(Attributes.MOVEMENT_SPEED, 0.27d)
                .add(Attributes.FOLLOW_RANGE, 32.0d)
                .add(Attributes.SAFE_FALL_DISTANCE, 5.0d)
                .add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.5d);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROLE, 0);
        builder.define(DATA_VARIANT, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    // -- Identite --------------------------------------------------------------------------------

    public GarrisonRole role() {
        GarrisonRole[] roles = GarrisonRole.values();
        return roles[Math.floorMod(entityData.get(DATA_ROLE), roles.length)];
    }

    /** Son visage, l'une des textures des soldats. */
    public int variant() {
        return Math.floorMod(entityData.get(DATA_VARIANT), Soldier.VARIANTS);
    }

    public UUID owner() {
        return owner;
    }

    public UUID memberId() {
        return member;
    }

    /** Son joueur, s'il est en ligne. */
    public ServerPlayer ownerPlayer() {
        return owner != null && level() instanceof ServerLevel server
                ? server.getServer().getPlayerList().getPlayer(owner) : null;
    }

    public BlockPos post() {
        return post;
    }

    Garrison.Order order() {
        return order;
    }

    void setOrder(Garrison.Order next, BlockPos nextPost) {
        this.order = next;
        this.post = nextPost.immutable();
        if (next == Garrison.Order.REPOS) {
            setTarget(null);
        }
    }

    /** Un allie : son joueur, ou un soldat du meme joueur. */
    public boolean ally(Entity other) {
        if (other == null || owner == null) {
            return false;
        }
        if (other instanceof Player player) {
            return owner.equals(player.getUUID());
        }
        return other instanceof GarrisonSoldier soldier && owner.equals(soldier.owner);
    }

    /**
     * Le fait soldat de {@code player} : son role, son palier, sa vie gardee, ses couleurs (le
     * cuir teint a la banniere de la faction), son nom, son poste.
     */
    void enlist(UUID ownerId, Garrison garrison, Garrison.Member entry, DyeColor color, BlockPos postPos) {
        this.owner = ownerId;
        this.member = entry.id();
        this.post = postPos.immutable();
        this.order = garrison.order();
        GarrisonRole role = entry.role();
        GarrisonTier tier = garrison.tier();
        entityData.set(DATA_ROLE, role.ordinal());
        entityData.set(DATA_VARIANT, Math.floorMod(entry.id().hashCode(), Soldier.VARIANTS));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(1.0d, role.health() * tier.health()));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(role.damage() + tier.damage());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(role.speed());
        if (role == GarrisonRole.CAPITAINE) {
            getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.6d);
        }
        tierBonus = (int) tier.damage();
        float health = entry.health() < 0.0f ? getMaxHealth() : Math.min(getMaxHealth(), entry.health());
        setHealth(Math.max(1.0f, health));
        equip(role, tier, color);
        setCustomName(Component.literal(entry.title()));
        setCustomNameVisible(role == GarrisonRole.CAPITAINE);
        installGoals();
    }

    private void equip(GarrisonRole role, GarrisonTier tier, DyeColor color) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0f);
            setItemSlot(slot, ItemStack.EMPTY);
        }
        boolean iron = tier == GarrisonTier.FER;
        switch (role) {
            case FANTASSIN -> {
                setItemSlot(EquipmentSlot.HEAD, piece(tier, "helmet"));
                setItemSlot(EquipmentSlot.CHEST, iron ? dyed(Items.LEATHER_CHESTPLATE, color) : piece(tier, "chestplate"));
                setItemSlot(EquipmentSlot.LEGS, iron ? dyed(Items.LEATHER_LEGGINGS, color) : piece(tier, "leggings"));
                setItemSlot(EquipmentSlot.FEET, piece(tier, "boots"));
                setItemSlot(EquipmentSlot.MAINHAND, piece(tier, "sword"));
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            }
            case ARCHER -> {
                setItemSlot(EquipmentSlot.HEAD, iron ? dyed(Items.LEATHER_HELMET, color) : piece(tier, "helmet"));
                setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, color));
                setItemSlot(EquipmentSlot.FEET, iron ? dyed(Items.LEATHER_BOOTS, color) : piece(tier, "boots"));
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            }
            case MEDECIN -> {
                setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, DyeColor.WHITE));
                setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, DyeColor.WHITE));
                setItemSlot(EquipmentSlot.LEGS, iron ? dyed(Items.LEATHER_LEGGINGS, color) : piece(tier, "leggings"));
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GLISTERING_MELON_SLICE));
            }
            case CAPITAINE -> {
                setItemSlot(EquipmentSlot.HEAD, piece(tier, "helmet"));
                setItemSlot(EquipmentSlot.CHEST, piece(tier, "chestplate"));
                setItemSlot(EquipmentSlot.LEGS, iron ? dyed(Items.LEATHER_LEGGINGS, color) : piece(tier, "leggings"));
                setItemSlot(EquipmentSlot.FEET, piece(tier, "boots"));
                setItemSlot(EquipmentSlot.MAINHAND, iron ? new ItemStack(Items.DIAMOND_SWORD) : piece(tier, "sword"));
            }
        }
    }

    private static ItemStack piece(GarrisonTier tier, String suffix) {
        Item item = tier.piece(suffix);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static ItemStack dyed(Item item, DyeColor color) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color.getTextureDiffuseColor() & 0xFFFFFF));
        return stack;
    }

    private void installGoals() {
        if (level().isClientSide() || goalsInstalled) {
            return;
        }
        goalsInstalled = true;
        goalSelector.removeAllGoals(goal -> true);
        targetSelector.removeAllGoals(goal -> true);
        GarrisonRole role = role();
        goalSelector.addGoal(0, new FloatGoal(this));
        switch (role) {
            case ARCHER -> goalSelector.addGoal(2, new BowGoal(1.0d, 25, 18.0f));
            case MEDECIN -> goalSelector.addGoal(2, new HealGoal());
            default -> goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15d, true));
        }
        goalSelector.addGoal(3, new FollowGoal());
        goalSelector.addGoal(4, new PostGoal());
        goalSelector.addGoal(5, new OpenDoorGoal(this, true));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        if (role != GarrisonRole.MEDECIN) {
            targetSelector.addGoal(1, new DefendOwnerGoal());
            targetSelector.addGoal(2, new HurtByTargetGoal(this, GarrisonSoldier.class));
            targetSelector.addGoal(3, new AssistOwnerGoal());
            targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Soldier.class, 10, true, false,
                    (target, level) -> engages(target)));
            targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                    (target, level) -> !(target instanceof Creeper) && engages(target)));
        }
    }

    /** Engager cette cible : pas au repos ; en garde, pres du poste ; en marche, pres du joueur. */
    boolean engages(LivingEntity target) {
        if (order == Garrison.Order.REPOS || ally(target)) {
            return false;
        }
        if (order == Garrison.Order.SUIVRE) {
            ServerPlayer player = ownerPlayer();
            return player != null && player.distanceToSqr(target) < 18.0d * 18.0d;
        }
        double dx = target.getX() - (post.getX() + 0.5d);
        double dz = target.getZ() - (post.getZ() + 0.5d);
        return dx * dx + dz * dz < 24.0d * 24.0d;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || tickCount % 40 != 0 || !isAlive()) {
            return;
        }
        // a son poste, sans ennemi : il panse ses plaies
        if (order != Garrison.Order.SUIVRE && getTarget() == null && hurtTime == 0 && getHealth() < getMaxHealth()
                && !post.equals(BlockPos.ZERO)
                && distanceToSqr(post.getX() + 0.5d, post.getY(), post.getZ() + 0.5d) < 6.0d * 6.0d) {
            heal(1.0f);
        }
        if (role() == GarrisonRole.CAPITAINE) {
            // l'aura du capitaine : Force et Resistance aux siens alentour
            for (GarrisonSoldier other : level().getEntitiesOfClass(GarrisonSoldier.class,
                    getBoundingBox().inflate(AURA), other -> other.isAlive() && ally(other))) {
                other.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 0, true, false));
                other.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 0, true, false));
            }
        }
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        ItemStack weapon = getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        ItemStack ammo = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, power, weapon);
        // la fleche d'un squelette fait 2 ; l'equipement de la garnison y ajoute
        arrow.setBaseDamage(2.0d + tierBonus * 0.5d);
        double dx = target.getX() - getX();
        double dy = target.getY(0.3333d) - arrow.getY();
        double dz = target.getZ() - getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (level() instanceof ServerLevel server) {
            Projectile.spawnProjectileUsingShoot(arrow, server, ammo, dx, dy + distance * 0.2d, dz, 1.6f, 6.0f);
        }
        playSound(SoundEvents.SKELETON_SHOOT, 1.0f, 1.0f / (getRandom().nextFloat() * 0.4f + 0.8f));
    }

    /** Jamais sur disque : la garnison le refait (voir la classe). */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** Rejoint son joueur d'un saut : un endroit libre a deux ou trois blocs de lui. */
    boolean catchUp(ServerPlayer player) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = getRandom().nextIntBetweenInclusive(-3, 3);
            int dz = getRandom().nextIntBetweenInclusive(-3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                continue;
            }
            BlockPos spot = player.blockPosition().offset(dx, 0, dz);
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos feet = spot.above(dy);
                if (Garrisons.standable(level(), feet)) {
                    snapTo(feet.getX() + 0.5d, feet.getY(), feet.getZ() + 0.5d, getYRot(), getXRot());
                    getNavigation().stop();
                    return true;
                }
            }
        }
        return false;
    }

    // -- Les buts --------------------------------------------------------------------------------

    /**
     * L'archer : il bande son arc, tire a l'arret quand il voit sa cible, tourne autour d'elle. Le but
     * de l'arc vanilla, reecrit pour un soldat qui n'est pas un monstre.
     */
    private final class BowGoal extends Goal {
        private final double speed;
        private final int interval;
        private final float radiusSqr;
        private int attackTime = -1;
        private int seeTime;
        private boolean clockwise;
        private boolean backwards;
        private int strafingTime = -1;

        BowGoal(double speed, int interval, float radius) {
            this.speed = speed;
            this.interval = interval;
            this.radiusSqr = radius * radius;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        private boolean holdingBow() {
            return isHolding(Items.BOW);
        }

        @Override
        public boolean canUse() {
            return getTarget() != null && holdingBow();
        }

        @Override
        public boolean canContinueToUse() {
            return (canUse() || !getNavigation().isDone()) && holdingBow();
        }

        @Override
        public void start() {
            setAggressive(true);
        }

        @Override
        public void stop() {
            setAggressive(false);
            seeTime = 0;
            attackTime = -1;
            stopUsingItem();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            double distance = distanceToSqr(target.getX(), target.getY(), target.getZ());
            boolean sees = getSensing().hasLineOfSight(target);
            if (sees != seeTime > 0) {
                seeTime = 0;
            }
            seeTime += sees ? 1 : -1;
            if (distance <= radiusSqr && seeTime >= 20) {
                getNavigation().stop();
                strafingTime++;
            } else {
                getNavigation().moveTo(target, speed);
                strafingTime = -1;
            }
            if (strafingTime >= 20) {
                if (getRandom().nextFloat() < 0.3f) {
                    clockwise = !clockwise;
                }
                if (getRandom().nextFloat() < 0.3f) {
                    backwards = !backwards;
                }
                strafingTime = 0;
            }
            if (strafingTime > -1) {
                if (distance > radiusSqr * 0.75f) {
                    backwards = false;
                } else if (distance < radiusSqr * 0.25f) {
                    backwards = true;
                }
                getMoveControl().strafe(backwards ? -0.5f : 0.5f, clockwise ? 0.5f : -0.5f);
                lookAt(target, 30.0f, 30.0f);
            } else {
                getLookControl().setLookAt(target, 30.0f, 30.0f);
            }
            if (isUsingItem()) {
                if (!sees && seeTime < -60) {
                    stopUsingItem();
                } else if (sees) {
                    int drawn = getTicksUsingItem();
                    if (drawn >= 20) {
                        stopUsingItem();
                        performRangedAttack(target, BowItem.getPowerForTime(drawn));
                        attackTime = interval;
                    }
                }
            } else if (--attackTime <= 0 && seeTime >= -60) {
                startUsingItem(ProjectileUtil.getWeaponHoldingHand(GarrisonSoldier.this, Items.BOW));
            }
        }
    }

    /** En marche : il suit son joueur, et le rejoint d'un saut s'il est trop loin. */
    private final class FollowGoal extends Goal {
        FollowGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            ServerPlayer player = order == Garrison.Order.SUIVRE ? ownerPlayer() : null;
            return player != null && player.level() == level() && getTarget() == null
                    && distanceToSqr(player) > 5.0d * 5.0d;
        }

        @Override
        public void tick() {
            ServerPlayer player = ownerPlayer();
            if (player == null) {
                return;
            }
            if (distanceToSqr(player) > CATCH_UP * CATCH_UP && catchUp(player)) {
                return;
            }
            if (getNavigation().isDone() || tickCount % 10 == 0) {
                getNavigation().moveTo(player, 1.2d);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() && distanceToSqr(ownerPlayer()) > 3.0d * 3.0d;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    /** En garde ou au repos : sans ennemi, il revient a son poste. */
    private final class PostGoal extends Goal {
        PostGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return order != Garrison.Order.SUIVRE && getTarget() == null && !post.equals(BlockPos.ZERO)
                    && distanceToSqr(post.getX() + 0.5d, post.getY(), post.getZ() + 0.5d) > 2.5d * 2.5d;
        }

        @Override
        public void tick() {
            if (distanceToSqr(post.getX() + 0.5d, post.getY(), post.getZ() + 0.5d) > 48.0d * 48.0d) {
                snapTo(post.getX() + 0.5d, post.getY(), post.getZ() + 0.5d, getYRot(), getXRot());
                return;
            }
            if (getNavigation().isDone() || tickCount % 20 == 0) {
                getNavigation().moveTo(post.getX() + 0.5d, post.getY(), post.getZ() + 0.5d, 1.0d);
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    /** Ce qui blesse son joueur devient sa cible. */
    private final class DefendOwnerGoal extends TargetGoal {
        private LivingEntity attacker;
        private int stamp;

        DefendOwnerGoal() {
            super(GarrisonSoldier.this, false);
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            ServerPlayer player = ownerPlayer();
            if (player == null || order == Garrison.Order.REPOS) {
                return false;
            }
            attacker = player.getLastHurtByMob();
            return attacker != null && player.getLastHurtByMobTimestamp() != stamp && !ally(attacker)
                    && attacker.distanceToSqr(GarrisonSoldier.this) < 20.0d * 20.0d
                    && canAttack(attacker, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            ServerPlayer player = ownerPlayer();
            if (player != null) {
                stamp = player.getLastHurtByMobTimestamp();
            }
            mob.setTarget(attacker);
            super.start();
        }
    }

    /** Ce que son joueur frappe, il le frappe aussi. */
    private final class AssistOwnerGoal extends TargetGoal {
        private LivingEntity victim;
        private int stamp;

        AssistOwnerGoal() {
            super(GarrisonSoldier.this, false);
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            ServerPlayer player = ownerPlayer();
            if (player == null || order == Garrison.Order.REPOS) {
                return false;
            }
            victim = player.getLastHurtMob();
            return victim != null && player.getLastHurtMobTimestamp() != stamp && !ally(victim)
                    && !(victim instanceof Player) && victim.distanceToSqr(GarrisonSoldier.this) < 20.0d * 20.0d
                    && canAttack(victim, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            ServerPlayer player = ownerPlayer();
            if (player != null) {
                stamp = player.getLastHurtMobTimestamp();
            }
            mob.setTarget(victim);
            super.start();
        }
    }

    /** Le medecin : il soigne le plus blesse des siens (ou son joueur) a 12 blocs. */
    private final class HealGoal extends Goal {
        private LivingEntity patient;

        HealGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (--healCooldown > 0) {
                return false;
            }
            healCooldown = 20;
            LivingEntity worst = null;
            double ratio = 0.75d;
            List<LivingEntity> around = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12.0d),
                    other -> other.isAlive() && ally(other) && other != GarrisonSoldier.this);
            for (LivingEntity other : around) {
                double health = other.getHealth() / other.getMaxHealth();
                if (health < ratio) {
                    ratio = health;
                    worst = other;
                }
            }
            patient = worst;
            return patient != null;
        }

        @Override
        public boolean canContinueToUse() {
            return patient != null && patient.isAlive() && patient.getHealth() < patient.getMaxHealth() * 0.9d
                    && distanceToSqr(patient) < 16.0d * 16.0d;
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(patient, 30.0f, 30.0f);
            if (distanceToSqr(patient) > 3.0d * 3.0d) {
                getNavigation().moveTo(patient, 1.2d);
                return;
            }
            getNavigation().stop();
            if (level() instanceof ServerLevel server) {
                patient.heal(6.0f + tierBonus);
                swing(InteractionHand.MAIN_HAND);
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, patient.getX(),
                        patient.getY() + patient.getBbHeight() + 0.3d, patient.getZ(), 4, 0.3d, 0.2d, 0.3d, 0.0d);
                playSound(SoundEvents.BREWING_STAND_BREW, 0.6f, 1.4f);
                healCooldown = 80;
                patient = null;
            }
        }

        @Override
        public void stop() {
            patient = null;
        }
    }
}

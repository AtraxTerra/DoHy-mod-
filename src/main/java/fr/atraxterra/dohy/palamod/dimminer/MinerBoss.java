package fr.atraxterra.dohy.palamod.dimminer;

import fr.atraxterra.dohy.palamod.PalaItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Le gardien des cristaux, boss de la dimension du Mineur ({@code EntityMinerBoss}, PalaMod 10.3.0,
 * relu au bytecode avec ses attaques et son IA). Enregistre mais jamais invoque chez Paladium ; ici, la
 * Pierre d'Invocation le reveille ({@link InvokerStoneItem}).
 *
 * <ul>
 *   <li>2 x 2, 1 000 PV, insensible au recul et au feu. Six cristaux le protegent : tant qu'il en
 *       reste, aucun coup ne porte, sauf quand il est etourdi.</li>
 *   <li>Etourdi, il est fige ; le premier coup le reveille, renvoie 14 points de degats a ce qui l'a
 *       frappe et fend un cristal ; deux coups le brisent. Le coup qui ne brise pas le dernier projette
 *       le joueur ({@code EntityMinerBossRepulseAttack}). Les six brises, il encaisse normalement et ne
 *       s'etourdit plus.</li>
 *   <li>Ses quatre attaques s'annoncent bras leves et frappent au 45e tick : le coup de poing (a 3,5
 *       blocs, s'il voit sa cible), la toupie (a 3,5 blocs, du 45e au 60e tick, des 4 cristaux
 *       brises), l'onde de choc du bras gauche (des 3) et la frappe au sol, qui l'etourdit quatre
 *       secondes. Les deux dernieres tuent a moins de 5 blocs au 50e tick.</li>
 *   <li>Toute attaque qui tue sa cible le reforme : cristaux et vie.</li>
 * </ul>
 * Il lache de 0 a 2 pepites d'endium, plus de 0 a Butin.
 */
public class MinerBoss extends Monster {

    public static final int CRYSTALS = 6;
    /** L'attaque porte au 45e tick ; la frappe au sol etourdit pour 80. */
    public static final int HIT_TICK = 45;
    public static final int STUN_TICKS = 80;
    /** Apres un depart, une autre attaque attend que le compteur retombe a 20 : 100 + sa duree. */
    private static final int ATTACK_DELAY = 100;
    private static final float THORNS = 14.0f;
    /** {@code 2.1474836E9F} : les attaques tuent. */
    private static final float LETHAL = 2.1474836E9f;
    private static final float REPULSE = 0.7f;

    /**
     * Les attaques ({@code IEntityMinerBossAttack}) : duree, chances selon les cristaux brises
     * ({@code getChance}, de 0 a 6) et disponibilite ({@code isAvailable}).
     */
    public enum Attack {
        NONE(0, 0, 0, 0, 0, 0, 0, 0),
        PUNCH(50, 75, 80, 85, 35, 20, 17, 18),
        TURN(60, 0, 0, 0, 0, 28, 33, 43),
        SHOCKWAVE(50, 0, 0, 0, 40, 32, 35, 38),
        STRIKE(50, 25, 20, 15, 25, 20, 15, 0);

        private final int duration;
        private final int[] chances;

        Attack(int duration, int... chances) {
            this.duration = duration;
            this.chances = chances;
        }

        public int duration() {
            return duration;
        }

        int chance(int state) {
            return chances[Math.clamp(state, 0, CRYSTALS)];
        }

        boolean available(int state) {
            return switch (this) {
                case NONE -> false;
                case PUNCH -> true;
                case TURN -> state > 3;
                case SHOCKWAVE -> state > 2;
                case STRIKE -> state < CRYSTALS;
            };
        }
    }

    private static final EntityDataAccessor<Integer> STATE =
            SynchedEntityData.defineId(MinerBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> STUNNED =
            SynchedEntityData.defineId(MinerBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ATTACK =
            SynchedEntityData.defineId(MinerBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK_TICK =
            SynchedEntityData.defineId(MinerBoss.class, EntityDataSerializers.INT);

    private final ServerBossEvent bar;
    private int subState;
    private int stunDelay;
    /** Le compteur de l'IA ({@code attackTick} d'{@code EntityAIMinerBossAttackOnCollide}). */
    private int attackCooldown;

    public MinerBoss(EntityType<? extends MinerBoss> type, Level level) {
        super(type, level);
        this.bar = new ServerBossEvent(UUID.randomUUID(), getDisplayName(), BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.NOTCHED_6);
        setPersistenceRequired();
    }

    /**
     * {@code applyEntityAttributes} : 1 000 PV, 50 d'attaque (que rien n'utilise : ses attaques
     * tuent), vitesse 0,2, recul 10 000 ; la portee de suivi de 1.7.10, 32.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0d)
                .add(Attributes.ATTACK_DAMAGE, 50.0d)
                .add(Attributes.MOVEMENT_SPEED, 0.2d)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0d)
                .add(Attributes.FOLLOW_RANGE, 32.0d);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, 0);
        builder.define(STUNNED, false);
        builder.define(ATTACK, 0);
        builder.define(ATTACK_TICK, 0);
    }

    /**
     * Les taches de l'original : regarder autour (priorite 0, d'ou ses courtes pauses), foncer et
     * attaquer, regarder le joueur a 8 blocs ; cibler le joueur le plus proche, qu'il voit.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new RandomLookAroundGoal(this));
        goalSelector.addGoal(1, new AttackGoal());
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0f));
        targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // -- Etat ----------------------------------------------------------------------------------

    /** Les cristaux brises, de 0 a 6 : la texture {@code miner_boss_<etat>}. */
    public int state() {
        return entityData.get(STATE);
    }

    public boolean isStunned() {
        return entityData.get(STUNNED);
    }

    public Attack attack() {
        int id = entityData.get(ATTACK);
        return id >= 0 && id < Attack.values().length ? Attack.values()[id] : Attack.NONE;
    }

    /** Le tick de l'attaque en cours, de 0 a sa duree. */
    public int attackTick() {
        return entityData.get(ATTACK_TICK);
    }

    private void startAttack(Attack attack) {
        entityData.set(ATTACK, attack.ordinal());
        entityData.set(ATTACK_TICK, 0);
    }

    private void setStunned(boolean stunned) {
        entityData.set(STUNNED, stunned);
        stunDelay = stunned ? STUN_TICKS : 0;
    }

    // -- Chaque tick ---------------------------------------------------------------------------

    /**
     * {@code onLivingUpdate} : etourdi, il ne fait rien d'autre que compter (ni mouvement, ni IA) ;
     * sinon, l'attaque en cours avance tant qu'il a une cible.
     */
    @Override
    public void aiStep() {
        if (level() instanceof ServerLevel level) {
            if (isStunned()) {
                if (--stunDelay <= 0) {
                    setStunned(false);
                }
                updateBar();
                return;
            }
            LivingEntity target = getTarget();
            if (attack() != Attack.NONE && target != null) {
                updateAttack(level, target);
            }
            updateBar();
        }
        super.aiStep();
    }

    private void updateAttack(ServerLevel level, LivingEntity target) {
        Attack attack = attack();
        int tick = attackTick();
        switch (attack) {
            case PUNCH -> {
                if (tick == HIT_TICK) {
                    sound(level, SoundEvents.IRON_GOLEM_ATTACK, 0.6f);
                    if (hasLineOfSight(target) && distanceTo(target) < 3.5f) {
                        lethal(level, target);
                    }
                }
            }
            case TURN -> {
                if (tick == HIT_TICK) {
                    sound(level, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f);
                }
                if (tick >= HIT_TICK && distanceTo(target) < 3.5f) {
                    lethal(level, target);
                }
            }
            case SHOCKWAVE, STRIKE -> {
                if (tick == HIT_TICK) {
                    earthquake(level);
                }
                if (tick >= attack.duration() && distanceTo(target) < 5.0f) {
                    lethal(level, target);
                }
            }
            default -> {
            }
        }
        if (tick >= attack.duration()) {
            startAttack(Attack.NONE);
            if (attack == Attack.STRIKE) {
                setStunned(true);
                sound(level, SoundEvents.RAVAGER_STUNNED, 1.0f);
            }
        } else {
            entityData.set(ATTACK_TICK, tick + 1);
        }
    }

    /** La cible touchee meurt ; si le coup a porte, il se reforme : cristaux et vie. */
    private void lethal(ServerLevel level, LivingEntity target) {
        if (target.hurtServer(level, damageSources().mobAttack(this), LETHAL)) {
            entityData.set(STATE, 0);
            subState = 0;
            setHealth(getMaxHealth());
        }
    }

    /**
     * {@code earthquake} : 24 blocs a 4 blocs de lui, en huit arcs. L'original y lancait des
     * {@code EntityCustomFallingBlock} sans type (donc sans effet) du bloc a hauteur de ses pieds,
     * de l'air : rien ne se voyait. Ici, le sol jaillit en eclats, sans plus d'effet.
     */
    private void earthquake(ServerLevel level) {
        sound(level, SoundEvents.GENERIC_EXPLODE.value(), 0.5f);
        float[] angles = {230.0f, 270.0f, 315.0f, 360.0f, 45.0f, 90.0f, 135.0f, 180.0f};
        for (float step = 0.5f; step < 3.0f; step += 1.0f) {
            for (float angle : angles) {
                double radians = Math.toRadians(step * 6.0f + angle + 90.0f);
                double x = getX() + 4.0d * Math.cos(radians);
                double z = getZ() + 4.0d * Math.sin(radians);
                BlockState ground = level.getBlockState(BlockPos.containing(x, getY() - 1.0d, z));
                if (ground.isAir()) {
                    ground = Blocks.STONE.defaultBlockState();
                }
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, getY() + 0.2d, z,
                        12, 0.3d, 0.3d, 0.3d, 0.2d);
            }
        }
    }

    /**
     * {@code attackEntityFrom}. Etourdi : 14 a ce qui l'a frappe (une fleche les prend a la place
     * du tireur), fin de l'etourdissement, un demi-cristal ; puis il projette l'attaquant s'il lui
     * reste des cristaux. Sinon, rien ne porte avant que les six soient brises.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, amount);
        }
        if (isDeadOrDying() || fireImmune() && source.is(DamageTypeTags.IS_FIRE)) {
            return false;
        }
        if (isStunned()) {
            Entity direct = source.getDirectEntity();
            if (direct != null) {
                direct.hurtServer(level, damageSources().mobAttack(this), THORNS);
            }
            setStunned(false);
            if (subState == 1) {
                entityData.set(STATE, Math.min(state() + 1, CRYSTALS));
                subState = 0;
                sound(level, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.8f);
                level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.6d, getZ(), 30, 0.8d, 0.6d, 0.8d,
                        0.12d);
            } else {
                subState++;
                sound(level, SoundEvents.AMETHYST_BLOCK_HIT, 0.7f);
            }
            if (state() >= CRYSTALS) {
                super.hurtServer(level, source, amount);
            } else if (direct instanceof LivingEntity living) {
                repulse(living);
            }
            return true;
        }
        return state() >= CRYSTALS && super.hurtServer(level, source, amount);
    }

    /** {@code EntityMinerBossRepulseAttack} : 0,7 sur chaque axe, a l'oppose du boss, et 0,7 en hauteur. */
    private void repulse(LivingEntity living) {
        double x = living.getX() - getX() > 0.0d ? REPULSE : -REPULSE;
        double z = living.getZ() - getZ() > 0.0d ? REPULSE : -REPULSE;
        living.push(x, REPULSE, z);
        living.hurtMarked = true;
    }

    private void sound(ServerLevel level, net.minecraft.sounds.SoundEvent sound, float pitch) {
        level.playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, 1.5f, pitch);
    }

    /** {@code dropFewItems} : de 0 a 2 pepites d'endium, plus de 0 a Butin. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        int count = random.nextInt(3);
        int looting = source.getEntity() instanceof LivingEntity killer
                ? EnchantmentHelper.getItemEnchantmentLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.LOOTING), killer.getMainHandItem())
                : 0;
        if (looting > 0) {
            count += random.nextInt(looting + 1);
        }
        for (int index = 0; index < count; index++) {
            spawnAtLocation(level, new ItemStack(PalaItems.ENDIUM_NUGGET.get()));
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // -- Barre de boss -------------------------------------------------------------------------

    /** Les cristaux restants en violet, par sixiemes ; les six brises, la vie en rouge. */
    private void updateBar() {
        if (state() < CRYSTALS) {
            bar.setColor(BossEvent.BossBarColor.PURPLE);
            bar.setOverlay(BossEvent.BossBarOverlay.NOTCHED_6);
            bar.setProgress(Mth.clamp((CRYSTALS - state() - subState * 0.5f) / CRYSTALS, 0.0f, 1.0f));
        } else {
            bar.setColor(BossEvent.BossBarColor.RED);
            bar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
            bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0f, 1.0f));
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.removePlayer(player);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        bar.removeAllPlayers();
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bar.removeAllPlayers();
    }

    // -- Sauvegarde ----------------------------------------------------------------------------

    /** <b>Ecart</b> : l'original oubliait ses cristaux au rechargement ; on les garde. */
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("State", state());
        output.putInt("SubState", subState);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(STATE, Math.clamp(input.getIntOr("State", 0), 0, CRYSTALS));
        subState = Math.clamp(input.getIntOr("SubState", 0), 0, 1);
    }

    /** Un gardien deja reveille a moins de 128 blocs. */
    public static boolean awake(ServerLevel level, Vec3 around) {
        return !level.getEntitiesOfClass(MinerBoss.class, AABB.ofSize(around, 256.0d, 256.0d, 256.0d),
                Entity::isAlive).isEmpty();
    }

    /**
     * {@code EntityAIMinerBossAttackOnCollide} : la poursuite de l'{@code EntityAIAttackOnCollide}
     * de 1.7.10, sans coup ordinaire. A portee ({@code (2 x largeur)^2 + largeur de la cible}, a
     * peine plus de 4 blocs) et le compteur a 20 ou moins, il tire une attaque parmi celles que ses
     * cristaux permettent, et le compteur repart a 100 + sa duree.
     */
    private final class AttackGoal extends Goal {

        private int pathDelay;

        AttackGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            pathDelay = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            getLookControl().setLookAt(target, 30.0f, 30.0f);
            double distance = distanceToSqr(target.getX(), target.getBoundingBox().minY, target.getZ());
            if (--pathDelay <= 0 && getSensing().hasLineOfSight(target)) {
                pathDelay = 4 + getRandom().nextInt(7);
                if (distance > 1024.0d) {
                    pathDelay += 10;
                } else if (distance > 256.0d) {
                    pathDelay += 5;
                }
                if (!getNavigation().moveTo(target, 1.0d)) {
                    pathDelay += 15;
                }
            }
            attackCooldown = Math.max(attackCooldown - 1, 0);
            double reach = getBbWidth() * 2.0f * getBbWidth() * 2.0f + target.getBbWidth();
            if (distance > reach || attackCooldown > 20 || attack() != Attack.NONE) {
                return;
            }
            Attack chosen = choose();
            if (chosen != Attack.NONE) {
                startAttack(chosen);
                attackCooldown = ATTACK_DELAY + chosen.duration();
            }
        }

        /** Le tirage de l'original : un nombre de 0 a 99, et la premiere attaque dont le cumul l'atteint. */
        private Attack choose() {
            int state = state();
            int roll = getRandom().nextInt(100);
            int sum = 0;
            for (Attack attack : Attack.values()) {
                if (!attack.available(state)) {
                    continue;
                }
                if (attack.chance(state) + sum >= roll) {
                    return attack;
                }
                sum += attack.chance(state);
            }
            return Attack.NONE;
        }
    }
}

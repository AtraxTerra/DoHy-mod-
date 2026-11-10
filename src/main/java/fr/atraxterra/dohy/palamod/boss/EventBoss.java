package fr.atraxterra.dohy.palamod.boss;

import fr.atraxterra.dohy.faction.event.BossFight;
import fr.atraxterra.dohy.faction.event.FactionEvents;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Un boss de l'event Boss (lot 22f).
 *
 * <p>Il n'existe que pour un combat : {@link #fight} designe le
 * {@link BossFight} qui l'a fait apparaitre. Sans combat en cours a ce nom —
 * serveur redemarre, event termine —, il disparait au tick suivant. Rien a
 * sauver, rien a nettoyer a la main.
 *
 * <p>Il note les degats recus <b>par faction</b> : c'est le classement du wiki
 * (« 700 elo repartis selon les degats infliges »). Le joueur compte pour sa
 * faction, un combattant rival pour la sienne ; les degats des sbires du boss
 * ne comptent pour personne.
 */
public class EventBoss extends Monster {

    /** Points de vie en solo ; 6 500 sur Paladium, ou ils se partagent. */
    public static final float SOLO_HEALTH = 1500.0f;

    /** Les degats d'attaque du wiki, divises par quatre pour le solo. */
    public static final float DAMAGE_SCALE = 0.25f;

    /**
     * Les combattants rivaux frappent fort et a plusieurs : leurs coups ne
     * comptent qu'a 30 %, sans quoi ils tueraient le boss avant le joueur.
     */
    public static final float RIVAL_FACTOR = 0.3f;

    /** Ticks sans coup avant que le boss ne reparte : cinq minutes. */
    public static final int BORED_TICKS = 6000;

    private static final EntityDataAccessor<Boolean> STUNNED =
            SynchedEntityData.defineId(EventBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CHARGING =
            SynchedEntityData.defineId(EventBoss.class, EntityDataSerializers.BOOLEAN);

    private final BossKind kind;
    private final ServerBossEvent bar;
    private UUID fight;
    private int cooldown = 60;
    private int stun;
    private int lastHit;
    /** Prochain seuil de vie (en pourcentage) pour Seisme ou Dechainement. */
    private int milestone = 100;
    /** Le prisonnier de Mecabras et le temps qu'il lui reste. */
    LivingEntity grabbed;
    int grabTicks;
    /** La cible d'une ruee (Belier, Charge, Morsure) et le temps qu'il lui reste. */
    LivingEntity rushTarget;
    int rushTicks;
    int koudboulCooldown;

    public EventBoss(EntityType<? extends EventBoss> type, Level level, BossKind kind) {
        super(type, level);
        this.kind = kind;
        this.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(kind.label()), kind.color(),
                BossEvent.BossBarOverlay.NOTCHED_10);
        this.xpReward = 200;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes(BossKind kind) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, SOLO_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, kind.attackDamage())
                .add(Attributes.MOVEMENT_SPEED, kind.speed())
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0d)
                .add(Attributes.FOLLOW_RANGE, 64.0d)
                .add(Attributes.ARMOR, 8.0d);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STUNNED, false);
        builder.define(CHARGING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0d, true));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8d));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, RivalFighter.class, true));
    }

    public BossKind kind() {
        return kind;
    }

    public void bind(UUID fight) {
        this.fight = fight;
        this.lastHit = tickCount;
    }

    public UUID fight() {
        return fight;
    }

    public boolean isStunned() {
        return entityData.get(STUNNED);
    }

    public boolean isCharging() {
        return entityData.get(CHARGING);
    }

    /** Ticks depuis le dernier coup recu d'un joueur ou d'un rival. */
    public int idleTicks() {
        return tickCount - lastHit;
    }

    void stun(int ticks) {
        stun = Math.max(stun, ticks);
        entityData.set(STUNNED, true);
        rushTarget = null;
        entityData.set(CHARGING, false);
        getNavigation().stop();
    }

    void startRush(LivingEntity target, int ticks) {
        rushTarget = target;
        rushTicks = ticks;
        entityData.set(CHARGING, true);
    }

    void endRush() {
        rushTarget = null;
        rushTicks = 0;
        entityData.set(CHARGING, false);
    }

    BossFight host() {
        return fight == null ? null : FactionEvents.fight(fight);
    }

    /** Les cibles a portee : joueurs (hors createur et spectateur) et combattants rivaux. */
    List<LivingEntity> targets(ServerLevel level, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), entity ->
                entity != this && entity.isAlive() && entity.distanceTo(this) <= radius + getBbWidth() / 2.0f
                        && (entity instanceof ServerPlayer player ? !player.isCreative() && !player.isSpectator()
                        : entity instanceof RivalFighter));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        BossFight host = host();
        if (host == null) {
            bar.removeAllPlayers();
            discard();
            return;
        }
        bar.setProgress(Math.clamp(getHealth() / getMaxHealth(), 0.0f, 1.0f));
        for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
            if (player.level() != level || player.distanceTo(this) > 96.0f) {
                bar.removePlayer(player);
            }
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceTo(this) <= 96.0f) {
                bar.addPlayer(player);
            }
        }
        if (koudboulCooldown > 0) {
            koudboulCooldown--;
        }
        if (stun > 0) {
            if (--stun == 0) {
                entityData.set(STUNNED, false);
            }
            getNavigation().stop();
            if (tickCount % 5 == 0) {
                level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() + 0.3d, getZ(),
                        6, 0.6d, 0.2d, 0.6d, 0.1d);
            }
            return;
        }
        BossAttacks.tickGrab(this, level);
        BossAttacks.tickRush(this, level);
        if (--cooldown <= 0) {
            cooldown = BossAttacks.special(this, level) ? kind.cooldown() : 20;
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker instanceof RivalFighter) {
            amount *= RIVAL_FACTOR;
        }
        float before = getHealth();
        boolean hurt = super.hurtServer(level, source, amount);
        BossFight host = host();
        if (!hurt || host == null) {
            return hurt;
        }
        String key = host.keyOf(attacker);
        if (key != null) {
            host.addDamage(key, before - getHealth());
            lastHit = tickCount;
        }
        if (attacker instanceof LivingEntity living && source.getDirectEntity() == attacker) {
            BossAttacks.onMelee(this, level, living);
        }
        // Seisme (tous les 15 %) et Dechainement (tous les 20 %).
        int step = kind == BossKind.BEEP_BOOP ? 15 : kind == BossKind.AZHUR ? 20 : 0;
        if (step > 0) {
            int percent = (int) (getHealth() * 100.0f / getMaxHealth());
            while (milestone - step >= percent && milestone - step > 0) {
                milestone -= step;
                BossAttacks.milestone(this, level);
            }
        }
        return hurt;
    }

    /** Les epines : le palier d'enchantement que le boss porte en ce moment. */
    int thornsLevel() {
        if (kind == BossKind.TOBALT) {
            return isStunned() ? 0 : 4;
        }
        float ratio = getHealth() / getMaxHealth();
        return ratio < 0.25f ? 2 : ratio < 0.5f ? 1 : 0;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) {
            Vec3 away = living.position().subtract(position()).normalize().scale(1.2d);
            living.setDeltaMovement(away.x, 0.45d, away.z);
            living.hurtMarked = true;
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        bar.removeAllPlayers();
        BossFight host = host();
        if (host != null && level() instanceof ServerLevel level) {
            host.bossKilled(level, this);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bar.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bar.removeAllPlayers();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
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

    /** Le boss ne se pousse pas : il pousse. */
    @Override
    public boolean isPushable() {
        return false;
    }

    BlockPos feet() {
        return blockPosition();
    }
}

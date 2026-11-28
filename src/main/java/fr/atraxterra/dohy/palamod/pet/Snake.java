package fr.atraxterra.dohy.palamod.pet;

import fr.atraxterra.dohy.palamod.lucky.LuckyGadgets;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/**
 * Le serpent de PalaMod ({@code EntitySnake}) : 20 points de vie, vitesse 0,3,
 * 10 de degats et cinq secondes de poison a chaque morsure, il attaque le joueur
 * a vue.
 *
 * <p>Tue par un joueur, il laisse un appat a familier trois fois sur quatre
 * ({@code PetLootListener} : 75 %) ; son venin, le trophee du Chasseur, reste
 * rare ({@code getDropItem} : {@code nextInt(1000) < 4}, puis de 0 a 2 plus
 * Butin, comme {@code dropFewItems}).
 */
public class Snake extends Monster {

    /** {@code PetLootListener.SNAKE_LOOT_CHANCE} : 75 %. */
    static final double BAIT_CHANCE = 0.75d;
    /** {@code nextInt(1000) < 4}. */
    static final int TROPHY_IN_1000 = 4;

    public Snake(EntityType<? extends Snake> type, Level level) {
        super(type, level);
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0d)
                .add(Attributes.MOVEMENT_SPEED, 0.3d)
                .add(Attributes.ATTACK_DAMAGE, 10.0d);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0d, false));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0d));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** {@code attackEntityAsMob} : la morsure empoisonne cinq secondes. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0), this);
        }
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return PalaPets.SNAKE_LIVING.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return PalaPets.SNAKE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return PalaPets.SNAKE_DEATH.get();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        drops(this, level, source, BAIT_CHANCE, new ItemStack(LuckyGadgets.SNAKE_VENOM.get()));
    }

    /**
     * Les drops du serpent et de l'escargot : l'appat si un joueur l'a tue, et le
     * trophee rare ({@code getDropItem} puis {@code dropFewItems}).
     */
    static void drops(LivingEntity entity, ServerLevel level, DamageSource source, double baitChance,
                      ItemStack trophy) {
        RandomSource random = entity.getRandom();
        if (source.getEntity() instanceof Player && random.nextDouble() < baitChance) {
            entity.spawnAtLocation(level, new ItemStack(PalaPets.PET_BAIT.get()));
        }
        if (random.nextInt(1000) >= TROPHY_IN_1000) {
            return;
        }
        int count = random.nextInt(3);
        if (source.getEntity() instanceof LivingEntity killer) {
            int looting = EnchantmentHelper.getItemEnchantmentLevel(level.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING), killer.getMainHandItem());
            if (looting > 0) {
                count += random.nextInt(looting + 1);
            }
        }
        for (int index = 0; index < count; index++) {
            entity.spawnAtLocation(level, trophy.copy());
        }
    }
}

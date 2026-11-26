package fr.atraxterra.dohy.palamod.pet;

import fr.atraxterra.dohy.palamod.lucky.LuckyGadgets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * L'escargot de PalaMod ({@code EntitySnail}) : un coeur de vie, tres lent, il
 * s'ecarte du joueur qui s'approche a trois blocs et fuit quand on le frappe.
 * Tue par un joueur, il laisse un appat a familier une fois sur quatre
 * ({@code PetLootListener} : 25 %) ; sa coquille reste un trophee rare.
 *
 * <p>Comme l'elephant, il vit dans la liste des creatures de ses biomes (tag
 * {@code palamod:snail_spawns}) et {@code Wildlife} en ramene.
 */
public class Snail extends PathfinderMob {

    /** {@code PetLootListener.SNAIL_LOOT_CHANCE} : 25 %. */
    static final double BAIT_CHANCE = 0.25d;

    public Snail(EntityType<? extends Snail> type, Level level) {
        super(type, level);
        xpReward = 1;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0d)
                .add(Attributes.MOVEMENT_SPEED, 0.15d);
    }

    /** Sur l'herbe et a la lumiere, comme un animal. */
    public static boolean checkSpawnRules(EntityType<Snail> type, ServerLevelAccessor level, EntitySpawnReason reason,
                                          BlockPos pos, RandomSource random) {
        return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON) && level.getRawBrightness(pos, 0) > 8;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // EntitySnail : panique a 0,7, s'ecarte a 3 blocs (0,5 puis 1,0), regarde a 8 blocs.
        goalSelector.addGoal(1, new PanicGoal(this, 0.7d));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 3.0f, 0.5d, 1.0d));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0d));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SLIME_SQUISH_SMALL;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SLIME_DEATH_SMALL;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Snake.drops(this, level, source, BAIT_CHANCE, new ItemStack(LuckyGadgets.SNAIL_SHELL.get()));
    }
}

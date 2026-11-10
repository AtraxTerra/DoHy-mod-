package fr.atraxterra.dohy.palamod.defense;

import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.palamod.pvp.PalaPvp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Ce que fait une obsidienne piegee quand on la detruit ({@code ObjectEffect}
 * de PalaMod, {@code applyEffect}).
 *
 * <p>Releve au bytecode (obsidiennes de {@code pillage.common.effects.blocks.obsi}) :
 * <ul>
 *   <li>empoisonnee : Poison II, 4 s, a 3,5 blocs ;</li>
 *   <li>boom : explosion de force 1,5 et 17,5 points de degats a 1,5 bloc ;</li>
 *   <li>mega boom : explosion de force 2 et 35 points a 2 blocs ;</li>
 *   <li>de lave et sulfurique : une source de lave, une source de Fake Water ;</li>
 *   <li>knockback : repousse a 3,5 blocs, vitesse bornee a 2 ;</li>
 *   <li>gelee : Lenteur XI, 3 s, et l'immobilisation de 4 s ;</li>
 *   <li>anti-aggro : les withers a 3,5 blocs perdent leur cible ;</li>
 *   <li>compacte : l'obsidienne revient ;</li>
 *   <li>slime : 8 a 14 slimepads semes autour ;</li>
 *   <li>a wither : les withers a 10 blocs sont retenus ;</li>
 *   <li>a spike : le spike de son metal prend sa place, pointe vers sa face avant.</li>
 * </ul>
 *
 * <p><b>Solo.</b> Sur Paladium, le piege frappe l'assaillant d'une autre
 * faction. Ici il frappe tout ce qui vit autour — <b>sauf le proprietaire du
 * claim</b>, que ses propres pieges epargnent. Deux elargissements, parce que
 * les assaillants sont des monstres : le poison passe en Wither sur les
 * morts-vivants (que le poison ignore), et l'anti-aggro calme tous les
 * monstres, pas seulement les withers, a 6 blocs.
 */
public enum ObsidianEffect {

    /** L'obsidienne renforcee : rien, elle est seulement dure. */
    NONE,
    POISON,
    BOOM,
    MEGA_BOOM,
    LAVA,
    SULFURIC,
    KNOCKBACK,
    FROZEN,
    ANTI_AGGRO,
    BIG,
    SLIME,
    WITHER,
    SPIKE;

    /**
     * Applique l'effet de l'obsidienne {@code state}, detruite en {@code pos}.
     * Appele au tick suivant la destruction ({@link DefenseEvents#defer}) : le
     * bloc a disparu, la place est libre pour ce qui le remplace.
     */
    public void apply(ServerLevel level, BlockPos pos, BlockState state) {
        Vec3 center = Vec3.atCenterOf(pos);
        switch (this) {
            case NONE -> {
            }
            case POISON -> {
                for (LivingEntity victim : victims(level, pos, 3.5d)) {
                    MobEffectInstance poison = new MobEffectInstance(MobEffects.POISON, 80, 1);
                    victim.addEffect(victim.canBeAffected(poison) ? poison
                            : new MobEffectInstance(MobEffects.WITHER, 80, 1));
                }
                burst(level, center, ParticleTypes.ITEM_SLIME, SoundEvents.SPLASH_POTION_BREAK);
            }
            case BOOM -> explode(level, pos, center, 1.5f, 17.5f, 1.5d);
            case MEGA_BOOM -> explode(level, pos, center, 2.0f, 35.0f, 2.0d);
            case LAVA -> fill(level, pos, Blocks.LAVA.defaultBlockState());
            case SULFURIC -> fill(level, pos, PalaDefense.FAKE_WATER_BLOCK.get().defaultBlockState());
            case KNOCKBACK -> {
                for (LivingEntity victim : victims(level, pos, 3.5d)) {
                    Vec3 away = victim.position().subtract(center);
                    Vec3 push = away.lengthSqr() < 1.0e-4d ? new Vec3(0.0d, 1.0d, 0.0d) : away.normalize();
                    Vec3 velocity = new Vec3(clamp(push.x * 2.0d), clamp(Math.max(0.4d, push.y + 0.5d)),
                            clamp(push.z * 2.0d));
                    victim.setDeltaMovement(velocity);
                    victim.hurtMarked = true;
                }
                burst(level, center, ParticleTypes.CLOUD, SoundEvents.PLAYER_ATTACK_KNOCKBACK);
            }
            case FROZEN -> {
                for (LivingEntity victim : victims(level, pos, 3.5d)) {
                    victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 10));
                    victim.setDeltaMovement(0.0d, Math.min(0.0d, victim.getDeltaMovement().y), 0.0d);
                    victim.hurtMarked = true;
                }
                burst(level, center, ParticleTypes.SNOWFLAKE, SoundEvents.GLASS_BREAK);
            }
            case ANTI_AGGRO -> {
                AABB box = new AABB(pos).inflate(6.0d);
                for (Mob mob : level.getEntitiesOfClass(Mob.class, box, Mob::isAlive)) {
                    mob.setTarget(null);
                }
                burst(level, center, ParticleTypes.HEART, SoundEvents.AMETHYST_BLOCK_CHIME);
            }
            case BIG -> fill(level, pos, Blocks.OBSIDIAN.defaultBlockState());
            case SLIME -> slimepads(level, pos);
            case WITHER -> {
                AABB box = new AABB(pos).inflate(10.0d);
                for (WitherBoss wither : level.getEntitiesOfClass(WitherBoss.class, box, WitherBoss::isAlive)) {
                    wither.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 6));
                    wither.setDeltaMovement(Vec3.ZERO);
                    wither.hurtMarked = true;
                }
                burst(level, center, ParticleTypes.SMOKE, SoundEvents.WITHER_HURT);
            }
            case SPIKE -> {
                if (state.getBlock() instanceof SpikeObsidianBlock spiked) {
                    Direction facing = state.getValue(DirectionalObsidianBlock.FACING);
                    BlockState spike = spiked.spike().defaultBlockState();
                    if (spike.hasProperty(DirectionalObsidianBlock.FACING)) {
                        spike = spike.setValue(DirectionalObsidianBlock.FACING, facing);
                    }
                    fill(level, pos, spike);
                }
            }
        }
    }

    /**
     * Ce que le piege atteint : ce qui vit dans la boite, moins les joueurs en
     * creatif ou spectateurs, et moins le proprietaire du claim ou il se trouve.
     */
    static List<LivingEntity> victims(ServerLevel level, BlockPos pos, double radius) {
        ClaimOwner owner = FactionApi.claimAt(level, pos);
        AABB box = new AABB(pos).inflate(radius);
        return level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity.isAlive()
                && !(entity instanceof Player player
                && (player.isCreative() || player.isSpectator() || owner.ownedBy(player.getUUID()))));
    }

    private static void explode(ServerLevel level, BlockPos pos, Vec3 center, float power, float damage,
                                double radius) {
        List<LivingEntity> victims = victims(level, pos, radius);
        level.explode(null, center.x, center.y, center.z, power, Level.ExplosionInteraction.TNT);
        for (LivingEntity victim : victims) {
            victim.hurtServer(level, level.damageSources().explosion(null, null), damage);
        }
    }

    /** Pose {@code state} a la place de l'obsidienne, si rien ne l'a prise entre-temps. */
    private static void fill(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).canBeReplaced()) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
    }

    private static void slimepads(ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        int wanted = 8 + random.nextInt(7);
        BlockState pad = PalaPvp.SLIMEPAD.get().defaultBlockState();
        int placed = 0;
        for (int attempt = 0; attempt < 60 && placed < wanted; attempt++) {
            BlockPos cell = attempt == 0 ? pos
                    : pos.offset(random.nextInt(5) - 2, random.nextInt(3) - 1, random.nextInt(5) - 2);
            if (level.getBlockState(cell).isAir()) {
                level.setBlock(cell, pad, Block.UPDATE_ALL);
                placed++;
            }
        }
        level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
    }

    private static void burst(ServerLevel level, Vec3 center, ParticleOptions particle, SoundEvent sound) {
        level.sendParticles(particle, center.x, center.y, center.z, 24, 1.2d, 0.8d, 1.2d, 0.05d);
        level.playSound(null, center.x, center.y, center.z, sound, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    private static double clamp(double value) {
        return Math.max(-2.0d, Math.min(2.0d, value));
    }
}

package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Les capacites des boss. Le wiki ne decrit aucune attaque : ce sont celles de
 * DoHy, assez generales pour donner a chaque boss une facon de se battre.
 *
 * <p>Les degats sont des degats « de base », multiplies ensuite par le palier
 * comme ceux des monstres : la source est le boss, et
 * {@link DungeonEvents} applique son facteur a tout ce qu'il inflige.
 */
public enum BossAbility {

    /** Une ruee vers sa cible. */
    CHARGE,
    /** Un bond suivi d'une onde de choc : degats et projection a 5 blocs. */
    SLAM,
    /** Des renforts : deux monstres, plus un par joueur au-dela du premier. */
    SUMMON,
    /** Une salve de cinq boules de feu vers sa cible. */
    VOLLEY,
    /** Attire a lui les joueurs a 12 blocs. */
    PULL,
    /** Reapparait dans le dos de sa cible. */
    TELEPORT,
    /** Enflamme et blesse les joueurs a 6 blocs. */
    FIRE_RING,
    /** Ralentit fortement les joueurs a 10 blocs. */
    ROOT;

    void perform(DungeonRun run, Mob boss, ServerLevel level, ServerPlayer target) {
        List<ServerPlayer> players = run.onlineMembers(level);
        switch (this) {
            case CHARGE -> {
                Vec3 push = target.position().subtract(boss.position()).normalize().scale(1.6d);
                boss.setDeltaMovement(push.x, 0.35d, push.z);
                boss.hurtMarked = true;
                level.sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 0.5d, boss.getZ(),
                        12, 0.4d, 0.2d, 0.4d, 0.02d);
            }
            case SLAM -> {
                level.sendParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY(), boss.getZ(),
                        6, 2.5d, 0.2d, 2.5d, 0.0d);
                level.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE,
                        0.8f, 0.8f);
                for (ServerPlayer player : players) {
                    if (player.distanceTo(boss) <= 5.0f) {
                        player.hurtServer(level, level.damageSources().mobAttack(boss), 6.0f);
                        Vec3 away = player.position().subtract(boss.position()).normalize().scale(1.2d);
                        player.setDeltaMovement(away.x, 0.7d, away.z);
                        player.hurtMarked = true;
                    }
                }
            }
            case SUMMON -> {
                int count = 2 + Math.max(0, players.size() - 1);
                for (int i = 0; i < count; i++) {
                    run.spawnMinion(level, boss.position(), i % 2 == 0 ? MobKind.MELEE : MobKind.ASSASSIN);
                }
                level.sendParticles(ParticleTypes.PORTAL, boss.getX(), boss.getY() + 1.0d, boss.getZ(),
                        40, 1.0d, 1.0d, 1.0d, 0.2d);
            }
            case VOLLEY -> {
                Vec3 from = boss.getEyePosition();
                for (int i = 0; i < 5; i++) {
                    Vec3 aim = target.getEyePosition().subtract(from).normalize()
                            .add(level.getRandom().nextGaussian() * 0.08d, level.getRandom().nextGaussian() * 0.05d,
                                    level.getRandom().nextGaussian() * 0.08d);
                    SmallFireball fireball = new SmallFireball(level, boss, aim);
                    fireball.setPos(from.x, from.y, from.z);
                    level.addFreshEntity(fireball);
                }
                level.playSound(null, boss.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0f, 0.9f);
            }
            case PULL -> {
                for (ServerPlayer player : players) {
                    if (player.distanceTo(boss) <= 12.0f) {
                        Vec3 toward = boss.position().subtract(player.position()).normalize().scale(1.1d);
                        player.setDeltaMovement(toward.x, 0.3d, toward.z);
                        player.hurtMarked = true;
                    }
                }
                level.sendParticles(ParticleTypes.SCULK_SOUL, boss.getX(), boss.getY() + 1.0d, boss.getZ(),
                        30, 3.0d, 1.0d, 3.0d, 0.05d);
            }
            case TELEPORT -> {
                Vec3 behind = target.position().subtract(target.getLookAngle().multiply(2.0d, 0.0d, 2.0d));
                if (run.insideRoom(behind)) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, boss.getX(), boss.getY() + 1.0d, boss.getZ(),
                            30, 0.5d, 1.0d, 0.5d, 0.1d);
                    boss.teleportTo(behind.x, target.getY(), behind.z);
                    level.playSound(null, boss.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE,
                            1.0f, 1.0f);
                }
            }
            case FIRE_RING -> {
                level.sendParticles(ParticleTypes.FLAME, boss.getX(), boss.getY() + 0.5d, boss.getZ(),
                        60, 3.0d, 0.3d, 3.0d, 0.02d);
                for (ServerPlayer player : players) {
                    if (player.distanceTo(boss) <= 6.0f) {
                        player.igniteForSeconds(4.0f);
                        player.hurtServer(level, level.damageSources().mobAttack(boss), 4.0f);
                    }
                }
            }
            case ROOT -> {
                for (ServerPlayer player : players) {
                    if (player.distanceTo(boss) <= 10.0f) {
                        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                    }
                }
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, boss.getX(), boss.getY() + 1.0d, boss.getZ(),
                        40, 4.0d, 1.0d, 4.0d, 0.0d);
            }
        }
    }
}

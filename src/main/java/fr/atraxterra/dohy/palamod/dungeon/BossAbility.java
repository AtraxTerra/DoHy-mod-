package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.block.Blocks;
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
    ROOT,
    /** Un seisme : degats, lenteur et secousse aux joueurs au sol a 9 blocs. */
    QUAKE,
    /** Un nuage toxique pose sous sa cible : poison tant qu'on y reste, cinq secondes. */
    POISON,
    /** Des toiles autour de sa cible, cinq secondes. */
    WEB,
    /** Boss Dark : une pulsation d'ombre — obscurite, fletrissement et degats a 10 blocs. */
    DARK_PULSE,
    /** Boss Dark : deux ombres de lui-meme, fragiles, qui s'effacent au bout de vingt secondes. */
    SHADOWS,
    /** Boss Dark : il draine la vie des joueurs a 8 blocs et s'en soigne. */
    DRAIN;

    /** Le nom affiche dans l'encyclopedie et les fiches de boss. */
    public String label() {
        return switch (this) {
            case CHARGE -> "Ruee";
            case SLAM -> "Onde de choc";
            case SUMMON -> "Renforts";
            case VOLLEY -> "Salve de feu";
            case PULL -> "Attraction";
            case TELEPORT -> "Teleportation";
            case FIRE_RING -> "Cercle de feu";
            case ROOT -> "Enracinement";
            case QUAKE -> "Seisme";
            case POISON -> "Nuage toxique";
            case WEB -> "Toiles";
            case DARK_PULSE -> "Pulsation sombre";
            case SHADOWS -> "Ombres";
            case DRAIN -> "Drain de vie";
        };
    }

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
            case QUAKE -> {
                level.sendParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY(), boss.getZ(),
                        10, 4.0d, 0.1d, 4.0d, 0.0d);
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, boss.getX(), boss.getY(), boss.getZ(),
                        20, 4.5d, 0.1d, 4.5d, 0.01d);
                level.playSound(null, boss.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0f, 0.5f);
                for (ServerPlayer player : players) {
                    if (player.onGround() && player.distanceTo(boss) <= 9.0f) {
                        player.hurtServer(level, level.damageSources().mobAttack(boss), 5.0f);
                        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
                        player.setDeltaMovement(player.getDeltaMovement().x, 0.5d, player.getDeltaMovement().z);
                        player.hurtMarked = true;
                    }
                }
            }
            case POISON -> {
                run.addHazard(target.position(), 3.0d, 100, false);
                level.playSound(null, target.blockPosition(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.HOSTILE,
                        1.0f, 0.8f);
            }
            case WEB -> {
                BlockPos feet = target.blockPosition();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) <= 1 || level.getRandom().nextBoolean()) {
                            run.placeTemporary(feet.offset(dx, 0, dz), Blocks.COBWEB.defaultBlockState(), 100);
                        }
                    }
                }
                level.playSound(null, feet, SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.0f, 0.6f);
            }
            case DARK_PULSE -> {
                level.sendParticles(ParticleTypes.SCULK_SOUL, boss.getX(), boss.getY() + 1.0d, boss.getZ(),
                        50, 5.0d, 1.0d, 5.0d, 0.02d);
                level.sendParticles(ParticleTypes.SQUID_INK, boss.getX(), boss.getY() + 1.0d, boss.getZ(),
                        40, 3.0d, 1.0d, 3.0d, 0.05d);
                level.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE,
                        0.8f, 0.7f);
                for (ServerPlayer player : players) {
                    if (player.distanceTo(boss) <= 10.0f) {
                        player.hurtServer(level, level.damageSources().mobAttack(boss), 4.0f);
                        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
                        player.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
                    }
                }
            }
            case SHADOWS -> run.spawnShadows(boss);
            case DRAIN -> {
                float drained = 0.0f;
                for (ServerPlayer player : players) {
                    if (player.distanceTo(boss) <= 8.0f) {
                        player.hurtServer(level, level.damageSources().mobAttack(boss), 3.0f);
                        drained += 3.0f;
                        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY() + 1.0d,
                                player.getZ(), 6, 0.3d, 0.4d, 0.3d, 0.1d);
                    }
                }
                if (drained > 0.0f) {
                    boss.heal(drained * 3.0f * (float) Math.max(1.0d, run.damageFactor()));
                    level.sendParticles(ParticleTypes.HEART, boss.getX(), boss.getY() + boss.getBbHeight(),
                            boss.getZ(), 6, 0.6d, 0.3d, 0.6d, 0.0d);
                    level.playSound(null, boss.blockPosition(), SoundEvents.WITHER_HURT, SoundSource.HOSTILE,
                            0.8f, 1.4f);
                }
            }
        }
    }
}

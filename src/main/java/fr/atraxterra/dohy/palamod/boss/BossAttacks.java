package fr.atraxterra.dohy.palamod.boss;

import fr.atraxterra.dohy.faction.event.BossFight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Les attaques des cinq boss, telles que le wiki les decrit (« Java | Boss »).
 *
 * <p>Chaque boss en a trois. Une attaque ne part que si une cible est dans sa
 * portee, comme sur Paladium ; parmi celles qui le peuvent, le boss en tire une
 * au hasard toutes les 4 ou 5 s. Les rayons sont ceux du wiki, les degats sont
 * divises par quatre ({@link EventBoss#DAMAGE_SCALE}).
 *
 * <p>Deux attaques ne sont pas tirees mais declenchees : Seisme (Beep Boop) a
 * chaque tranche de 15 % de vie perdue, Dechainement (Azhur) a chaque tranche de
 * 20 %. Koudboul (Tobalt) repond a un coup au corps a corps.
 *
 * <p><b>Solo.</b> Picaglagla demandait deux joueurs dans la zone : ici, deux
 * cibles, et les combattants des factions rivales comptent. Dechainement fait
 * venir 8 zombies du desert (25 sur Paladium) et Captoile 4 araignees (7).
 */
final class BossAttacks {

    private enum Attack { MECABRAS, TIC_TAC, ERUPTION, BELIER, JETSABLE, SUNAMI, STUPEFREEZE, CHARGE, PICAGLAGLA,
        EXPLOWEB, MORSURE, CAPTOILE }

    private BossAttacks() {
    }

    /** Lance une attaque speciale ; rend faux si aucune n'a de cible. */
    static boolean special(EventBoss boss, ServerLevel level) {
        if (boss.grabbed != null || boss.rushTarget != null) {
            return false;
        }
        List<Attack> ready = new ArrayList<>();
        switch (boss.kind()) {
            case BEEP_BOOP -> {
                add(ready, Attack.MECABRAS, nearest(boss, level, 0, 5));
                add(ready, Attack.TIC_TAC, nearest(boss, level, 5, 25));
            }
            case TOBALT -> {
                add(ready, Attack.ERUPTION, nearest(boss, level, 0, 25));
                add(ready, Attack.BELIER, nearest(boss, level, 8, 30));
            }
            case AZHUR -> {
                add(ready, Attack.JETSABLE, nearest(boss, level, 0, 7));
                add(ready, Attack.SUNAMI, nearest(boss, level, 0, 25));
            }
            case MORSULA -> {
                add(ready, Attack.STUPEFREEZE, nearest(boss, level, 0, 15));
                add(ready, Attack.CHARGE, nearest(boss, level, 3, 15));
                if (boss.targets(level, 40).size() >= 2) {
                    ready.add(Attack.PICAGLAGLA);
                }
            }
            case ARACHNA -> {
                add(ready, Attack.EXPLOWEB, nearest(boss, level, 0, 25));
                add(ready, Attack.MORSURE, nearest(boss, level, 0, 13));
                add(ready, Attack.CAPTOILE, nearest(boss, level, 7, 25));
            }
        }
        if (ready.isEmpty()) {
            return false;
        }
        perform(boss, level, ready.get(boss.getRandom().nextInt(ready.size())));
        return true;
    }

    private static void add(List<Attack> ready, Attack attack, LivingEntity target) {
        if (target != null) {
            ready.add(attack);
        }
    }

    /** La cible la plus proche entre deux distances (au bord du boss). */
    private static LivingEntity nearest(EventBoss boss, ServerLevel level, double min, double max) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity target : boss.targets(level, max)) {
            double distance = target.distanceTo(boss) - boss.getBbWidth() / 2.0f;
            if (distance >= min && distance < bestDistance) {
                best = target;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void perform(EventBoss boss, ServerLevel level, Attack attack) {
        RandomSource random = boss.getRandom();
        switch (attack) {
            case MECABRAS -> {
                // Le bras attrape une cible a 5 blocs : 3 s immobile, 20 par seconde, puis projetee.
                LivingEntity target = nearest(boss, level, 0, 5);
                boss.grabbed = target;
                boss.grabTicks = 60;
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 6));
                sound(level, boss, SoundEvents.IRON_GOLEM_ATTACK, 0.6f);
            }
            case TIC_TAC -> {
                // Un engrenage imparable : 5 points, a travers l'armure.
                LivingEntity target = nearest(boss, level, 5, 25);
                line(level, boss.getEyePosition(), target.getEyePosition(), ParticleTypes.CRIT);
                target.hurtServer(level, level.damageSources().indirectMagic(boss, boss), 4.0f);
                sound(level, boss, SoundEvents.ITEM_BREAK.value(), 1.6f);
            }
            case ERUPTION -> {
                for (LivingEntity target : boss.targets(level, 25)) {
                    hit(boss, level, target, 30.0f);
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 6));
                }
                level.sendParticles(ParticleTypes.LAVA, boss.getX(), boss.getY() + boss.getBbHeight(), boss.getZ(),
                        80, 3.0d, 1.0d, 3.0d, 0.5d);
                ring(level, boss, 25.0d, ParticleTypes.FLAME);
                sound(level, boss, SoundEvents.GENERIC_EXPLODE.value(), 0.6f);
            }
            case BELIER -> {
                boss.startRush(nearest(boss, level, 8, 30), 50);
                sound(level, boss, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.4f);
            }
            case JETSABLE -> {
                LivingEntity target = nearest(boss, level, 0, 7);
                line(level, boss.getEyePosition(), target.position().add(0.0d, 1.0d, 0.0d), sand());
                hit(boss, level, target, 40.0f);
                push(target, boss.position(), 0.8d, 0.35d);
                sound(level, boss, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.7f);
            }
            case SUNAMI -> {
                for (LivingEntity target : boss.targets(level, 25)) {
                    hit(boss, level, target, 40.0f);
                }
                ring(level, boss, 8.0d, sand());
                ring(level, boss, 16.0d, sand());
                ring(level, boss, 25.0d, sand());
                sound(level, boss, SoundEvents.GENERIC_EXPLODE.value(), 0.5f);
            }
            case STUPEFREEZE -> {
                for (LivingEntity target : boss.targets(level, 15)) {
                    hit(boss, level, target, 40.0f);
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 6));
                    target.setTicksFrozen(Math.max(target.getTicksFrozen(), 240));
                }
                ring(level, boss, 15.0d, ParticleTypes.SNOWFLAKE);
                sound(level, boss, SoundEvents.SPLASH_POTION_BREAK, 0.5f);
            }
            case CHARGE -> {
                boss.startRush(nearest(boss, level, 3, 15), 30);
                sound(level, boss, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.5f);
            }
            case PICAGLAGLA -> {
                for (LivingEntity target : boss.targets(level, 40)) {
                    line(level, boss.getEyePosition(), target.getEyePosition(), ParticleTypes.SNOWFLAKE);
                    hit(boss, level, target, 40.0f);
                }
                sound(level, boss, SoundEvents.SPLASH_POTION_BREAK, 1.4f);
            }
            case EXPLOWEB -> {
                BossFight host = boss.host();
                for (LivingEntity target : boss.targets(level, 25)) {
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1));
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
                    if (host != null) {
                        BlockPos pos = target.blockPosition().offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
                        host.tempBlock(level, pos, Blocks.COBWEB.defaultBlockState(), 200);
                    }
                }
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 1.0d, boss.getZ(),
                        1, 0.0d, 0.0d, 0.0d, 0.0d);
                ring(level, boss, 25.0d, ParticleTypes.ITEM_SLIME);
                sound(level, boss, SoundEvents.GENERIC_EXPLODE.value(), 1.2f);
            }
            case MORSURE -> {
                boss.startRush(nearest(boss, level, 0, 13), 25);
                sound(level, boss, SoundEvents.PLAYER_ATTACK_CRIT, 0.5f);
            }
            case CAPTOILE -> {
                LivingEntity target = nearest(boss, level, 7, 25);
                BossFight host = boss.host();
                if (host != null) {
                    BlockPos feet = target.blockPosition();
                    for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, 0, -1), feet.offset(1, 2, 1))) {
                        host.tempBlock(level, pos.immutable(), Blocks.COBWEB.defaultBlockState(), 200);
                    }
                    for (int i = 0; i < 4; i++) {
                        minion(boss, level, EntityTypes.CAVE_SPIDER, target.position().add(
                                random.nextInt(5) - 2, 0.0d, random.nextInt(5) - 2));
                    }
                }
                sound(level, boss, SoundEvents.SLIME_BLOCK_PLACE, 0.6f);
            }
        }
    }

    /** Seisme (Beep Boop) ou Dechainement (Azhur), a chaque tranche de vie perdue. */
    static void milestone(EventBoss boss, ServerLevel level) {
        if (boss.kind() == BossKind.BEEP_BOOP) {
            for (LivingEntity target : boss.targets(level, 50)) {
                hit(boss, level, target, 40.0f);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY(), boss.getZ(),
                    3, 2.0d, 0.2d, 2.0d, 0.0d);
            ring(level, boss, 12.0d, ParticleTypes.EXPLOSION);
            sound(level, boss, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f);
        } else if (boss.kind() == BossKind.AZHUR) {
            for (LivingEntity target : boss.targets(level, 15)) {
                hit(boss, level, target, 30.0f);
                push(target, boss.position(), 2.2d, 0.6d);
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            }
            ring(level, boss, 15.0d, sand());
            RandomSource random = boss.getRandom();
            for (int i = 0; i < 8; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0d;
                minion(boss, level, EntityTypes.HUSK, boss.position().add(Math.cos(angle) * 8.0d, 0.0d,
                        Math.sin(angle) * 8.0d));
            }
            sound(level, boss, SoundEvents.WITHER_SPAWN, 1.4f);
        }
    }

    /** Mecabras : la prise dure 3 s, 20 points par seconde, puis le jet a 3 blocs. */
    static void tickGrab(EventBoss boss, ServerLevel level) {
        LivingEntity target = boss.grabbed;
        if (target == null) {
            return;
        }
        // Une perle de l'Ender libere : la cible s'est eloignee d'un coup.
        if (!target.isAlive() || target.level() != level || target.distanceTo(boss) > boss.getBbWidth() + 8.0f) {
            boss.grabbed = null;
            return;
        }
        boss.grabTicks--;
        if (boss.grabTicks % 20 == 0) {
            hit(boss, level, target, 20.0f);
        }
        if (boss.grabTicks <= 0) {
            push(target, boss.position(), 1.0d, 0.4d);
            boss.grabbed = null;
        }
    }

    /** Les ruees : Belier (Tobalt), Charge (Morsula), Morsure (Arachna). */
    static void tickRush(EventBoss boss, ServerLevel level) {
        LivingEntity target = boss.rushTarget;
        if (target == null) {
            return;
        }
        if (!target.isAlive() || target.level() != level || --boss.rushTicks <= 0) {
            boss.endRush();
            return;
        }
        Vec3 toward = target.position().subtract(boss.position()).multiply(1.0d, 0.0d, 1.0d).normalize();
        double speed = boss.kind() == BossKind.TOBALT ? 0.7d : boss.kind() == BossKind.MORSULA ? 0.8d : 0.9d;
        boss.setDeltaMovement(toward.x * speed, boss.getDeltaMovement().y, toward.z * speed);
        boss.setYRot((float) (Math.atan2(toward.z, toward.x) * 180.0d / Math.PI) - 90.0f);
        boss.yBodyRot = boss.getYRot();
        level.sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 0.3d, boss.getZ(), 3,
                boss.getBbWidth() / 3.0d, 0.1d, boss.getBbWidth() / 3.0d, 0.01d);

        // Belier : un bloc pose sur son chemin l'etourdit, selon sa resistance.
        if (boss.kind() == BossKind.TOBALT && boss.horizontalCollision) {
            BlockPos ahead = BlockPos.containing(boss.position().add(toward.scale(boss.getBbWidth() / 2.0d + 0.6d)))
                    .above();
            BlockState wall = level.getBlockState(ahead);
            float resistance = wall.isAir() ? 0.0f : wall.getBlock().getExplosionResistance();
            boss.stun(Math.clamp(Math.round(20.0f + resistance * 10.0f), 40, 200));
            sound(level, boss, SoundEvents.IRON_GOLEM_ATTACK, 0.4f);
            return;
        }

        if (target.distanceTo(boss) > boss.getBbWidth() / 2.0f + 1.5f) {
            return;
        }
        switch (boss.kind()) {
            case TOBALT -> {
                hit(boss, level, target, 100.0f);
                push(target, boss.position(), 2.0d, 0.5d);
            }
            case MORSULA -> {
                hit(boss, level, target, 100.0f);
                target.setDeltaMovement(target.getDeltaMovement().x, 1.25d, target.getDeltaMovement().z);
                target.hurtMarked = true;
            }
            default -> {
                // Morsure : ecarte les autres, mord, empoisonne.
                for (LivingEntity other : boss.targets(level, 3)) {
                    if (other != target) {
                        push(other, boss.position(), 0.9d, 0.3d);
                    }
                }
                hit(boss, level, target, 60.0f);
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
                if (target instanceof ServerPlayer player) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Arachna t'a mordu !").withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
                }
            }
        }
        sound(level, boss, SoundEvents.PLAYER_ATTACK_CRIT, 0.6f);
        boss.endRush();
    }

    /** Un coup au corps a corps : epines, et Koudboul pour Tobalt. */
    static void onMelee(EventBoss boss, ServerLevel level, LivingEntity attacker) {
        int thorns = boss.thornsLevel();
        if (thorns > 0 && boss.getRandom().nextFloat() < 0.15f * thorns) {
            attacker.hurtServer(level, level.damageSources().thorns(boss), 1 + boss.getRandom().nextInt(4));
        }
        if (boss.kind() == BossKind.TOBALT && !boss.isStunned() && boss.koudboulCooldown == 0
                && attacker.distanceTo(boss) <= boss.getBbWidth() / 2.0f + 5.0f) {
            // Koudboul : l'attaquant est projete a 15 blocs.
            hit(boss, level, attacker, 40.0f);
            push(attacker, boss.position(), 2.4d, 0.7d);
            boss.koudboulCooldown = 60;
            sound(level, boss, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.5f);
        }
    }

    // -- Outils ----------------------------------------------------------------

    private static void hit(EventBoss boss, ServerLevel level, LivingEntity target, float wikiDamage) {
        target.hurtServer(level, level.damageSources().mobAttack(boss), wikiDamage * EventBoss.DAMAGE_SCALE);
    }

    private static void push(LivingEntity target, Vec3 from, double horizontal, double vertical) {
        Vec3 away = target.position().subtract(from).multiply(1.0d, 0.0d, 1.0d);
        away = away.lengthSqr() < 1.0e-4d ? new Vec3(1.0d, 0.0d, 0.0d) : away.normalize();
        target.setDeltaMovement(away.x * horizontal, vertical, away.z * horizontal);
        target.hurtMarked = true;
    }

    private static void minion(EventBoss boss, ServerLevel level, EntityType<? extends Mob> type, Vec3 at) {
        BossFight host = boss.host();
        Mob mob = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (mob == null || host == null) {
            return;
        }
        mob.snapTo(at.x, boss.getY(), at.z, boss.getRandom().nextFloat() * 360.0f, 0.0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.MOB_SUMMONED,
                null);
        host.minion(level, mob);
    }

    private static BlockParticleOption sand() {
        return new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
    }

    private static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle) {
        Vec3 step = to.subtract(from);
        int count = (int) Math.max(4, step.length() * 2.0d);
        for (int i = 0; i <= count; i++) {
            Vec3 at = from.add(step.scale(i / (double) count));
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0.05d, 0.05d, 0.05d, 0.0d);
        }
    }

    private static void ring(ServerLevel level, EventBoss boss, double radius, ParticleOptions particle) {
        int count = (int) (radius * 6.0d);
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0d * i / count;
            level.sendParticles(particle, boss.getX() + Math.cos(angle) * radius, boss.getY() + 0.3d,
                    boss.getZ() + Math.sin(angle) * radius, 1, 0.1d, 0.1d, 0.1d, 0.0d);
        }
    }

    private static void sound(ServerLevel level, EventBoss boss, SoundEvent sound, float pitch) {
        level.playSound(null, boss.blockPosition(), sound, SoundSource.HOSTILE, 2.0f, pitch);
    }
}

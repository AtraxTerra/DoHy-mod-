package fr.atraxterra.dohy.client.rpg;

import fr.atraxterra.dohy.palamod.dungeon.RpgSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;

import java.util.List;

/**
 * L'animation d'un monstre de donjon, suivie image apres image cote client.
 *
 * <p>Le monstre reste un monstre vanilla : ses actions se lisent sur ce que le
 * jeu synchronise deja. Il attaque quand il balance le bras, bande son arc,
 * lance un sort ou joue son animation d'attaque propre (golem, ravageur,
 * zoglin, gardien) ; il est blesse quand son {@code hurtTime} repart ; il
 * meurt, ou vient d'apparaitre. Une seule action a la fois, la mort avant
 * l'apparition, avant l'attaque, avant la blessure.
 */
final class RpgAnimator {

    private enum Action { NONE, HURT, ATTACK, SPAWN, DEATH }

    /** Le fondu d'entree et de sortie d'une action, en secondes. */
    private static final float BLEND = 0.12f;
    /** Un monstre mort disparait au bout d'une seconde : sa mort est jouee dans ce temps. */
    private static final float DEATH_SECONDS = 0.95f;

    private Action action = Action.NONE;
    private RpgModels.Clip clip;
    private float start;
    private float speed = 1.0f;
    private boolean seen;
    private boolean wasAttacking;
    private int lastHurtTime;
    private int nextAttack;

    private String pose = "";
    private String[] steps = new String[0];
    private float poseStart;

    /**
     * Un objet de decor : il joue la pose que le serveur lui donne. Une pose
     * {@code "a>b"} joue {@code a} une fois puis {@code b} ; la derniere
     * boucle ou tient sa derniere image selon son fichier.
     */
    private RpgFrame posed(LivingEntity entity, RpgSkin skin, RpgModels.Entry entry, float age) {
        if (!skin.pose().equals(pose)) {
            // Au premier regard, une pose en deux temps est deja passee : on montre la seconde.
            poseStart = seen || entity.tickCount < 10 ? age : -1.0e6f;
            pose = skin.pose();
            steps = pose.split(">");
        }
        seen = true;
        float seconds = (age - poseStart) / 20.0f;
        RpgModels.Clip current = null;
        for (int i = 0; i < steps.length; i++) {
            RpgModels.Clip step = entry.named().get(steps[i]);
            if (step == null) {
                continue;
            }
            current = step;
            if (i == steps.length - 1 || seconds < step.length()) {
                break;
            }
            seconds -= step.length();
        }
        if (current != null && !current.loop()) {
            seconds = Math.min(seconds, current.length());
        }
        return new RpgFrame(entry, entry.texture(skin.variant()).at(age), skin.scale(), 0L, 0.0f,
                current, (long) (seconds * 1000.0f), 1.0f);
    }

    RpgFrame frame(LivingEntity entity, RpgSkin skin, RpgModels.Entry entry, float partialTick, float walkSpeed) {
        float age = entity.tickCount + partialTick;
        if (!skin.pose().isEmpty()) {
            return posed(entity, skin, entry, age);
        }
        if (!seen) {
            seen = true;
            if (entity.tickCount < 10 && entry.spawn() != null && !entity.isDeadOrDying()) {
                begin(Action.SPAWN, entry.spawn(), age, 1.0f);
            }
        }
        if (entity.isDeadOrDying()) {
            if (action != Action.DEATH && entry.death() != null) {
                begin(Action.DEATH, entry.death(), age, Math.max(1.0f, entry.death().length() / DEATH_SECONDS));
            }
        } else {
            boolean attacking = attacking(entity);
            List<RpgModels.Clip> attacks = entry.attacks();
            if (attacking && !attacks.isEmpty() && (action == Action.NONE || action == Action.HURT)
                    && (!wasAttacking || action == Action.NONE)) {
                begin(Action.ATTACK, attacks.get(nextAttack++ % attacks.size()), age, 1.0f);
            }
            wasAttacking = attacking;
            if (entity.hurtTime > lastHurtTime && action == Action.NONE && entry.hurt() != null) {
                begin(Action.HURT, entry.hurt(), age, 1.0f);
            }
            lastHurtTime = entity.hurtTime;
        }

        float seconds = 0.0f;
        float weight = 0.0f;
        if (clip != null) {
            seconds = Math.max(0.0f, (age - start) / 20.0f * speed);
            if (action != Action.DEATH && seconds >= clip.length()) {
                action = Action.NONE;
                clip = null;
            } else {
                float in = seconds / BLEND;
                float out = action == Action.DEATH ? 1.0f : (clip.length() - seconds) / BLEND;
                // L'apparition commence sur sa premiere image, sans fondu.
                weight = Mth.clamp(Math.min(action == Action.SPAWN ? 1.0f : in, out), 0.0f, 1.0f);
            }
        }
        RpgModels.Texture texture = entry.texture(skin.variant());
        return new RpgFrame(entry, texture.at(age), skin.scale(), (long) (age * 50.0f),
                Mth.clamp(walkSpeed * 3.0f, 0.0f, 1.0f), clip, (long) (seconds * 1000.0f), weight);
    }

    private void begin(Action next, RpgModels.Clip nextClip, float age, float nextSpeed) {
        action = next;
        clip = nextClip;
        start = age;
        speed = nextSpeed;
    }

    private static boolean attacking(LivingEntity entity) {
        if (entity.swinging || entity.isUsingItem()) {
            return true;
        }
        return switch (entity) {
            case SpellcasterIllager caster -> caster.isCastingSpell();
            case IronGolem golem -> golem.getAttackAnimationTick() > 0;
            case Ravager ravager -> ravager.getAttackTick() > 0;
            case Zoglin zoglin -> zoglin.getAttackAnimationRemainingTicks() > 0;
            case Guardian guardian -> guardian.hasActiveAttackTarget();
            default -> false;
        };
    }
}

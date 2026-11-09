package fr.atraxterra.dohy.palamod.pvp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Les sept sticks de combat ({@code ItemStickBase}, {@code PRegister_Items}).
 *
 * <table>
 *   <tr><th>Stick</th><th>Durabilite</th><th>Recharge</th><th>Effet ({@code getTypeEffect})</th></tr>
 *   <tr><td>regeneration</td><td>15</td><td>10 s</td><td>soigne 6 points (3 coeurs)</td></tr>
 *   <tr><td>vitesse</td><td>15</td><td>15 s</td><td>vitesse VII, 3,5 s</td></tr>
 *   <tr><td>force</td><td>15</td><td>15 s</td><td>force III, 3,5 s</td></tr>
 *   <tr><td>saut</td><td>15</td><td>30 s</td><td>+1 de vitesse verticale (~5 blocs)</td></tr>
 *   <tr><td>divin</td><td>8</td><td>25 s</td><td>regeneration VI 4 s, 6 points d'absorption,
 *       saturation II 1,5 s, vitesse IX 4 s, force III 5 s</td></tr>
 *   <tr><td>dommage</td><td>16</td><td>30 s</td><td>repousse et souleve tout ce qui vit a 3 blocs</td></tr>
 *   <tr><td>super saut</td><td>8</td><td>40 s</td><td>+2,365 de vitesse verticale (~25 blocs)</td></tr>
 * </table>
 *
 * <p>Les niveaux sont ceux du code ({@code new PotionEffect(id, duree, amplificateur)}),
 * pas ceux du wiki, qui annonce vitesse III et force III : l'amplificateur 6 du
 * stick de vitesse donne bien vitesse VII en jeu.
 *
 * <p>La couleur est celle des particules et de la jauge de recharge
 * ({@code PacketStickBaseCooldown}).
 */
public enum StickType {

    HEAL("heal", 15, 10, 0xFF0000),
    SPEED("speed", 15, 15, 0x49ADF4),
    STRENGTH("strength", 15, 15, 0xF4D442),
    JUMP("jump", 15, 30, 0xF48F42),
    GOD("god", 8, 25, 0xF4EE42),
    DAMAGE("damage", 16, 30, 0xFF0000),
    HYPER_JUMP("hyper_jump", 8, 40, 0x50F442);

    /** Les sticks plafonnent a cinq passages par la palamachine ({@code limiteType}). */
    public static final int MAX_REPAIRS = 5;

    private final String key;
    private final int durability;
    private final int cooldownSeconds;
    private final int color;

    StickType(String key, int durability, int cooldownSeconds, int color) {
        this.key = key;
        this.durability = durability;
        this.cooldownSeconds = cooldownSeconds;
        this.color = color;
    }

    public String key() {
        return key;
    }

    public int durability() {
        return durability;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public int color() {
        return color;
    }

    /**
     * L'effet, cote serveur.
     *
     * <p>Chaque effet de potion est d'abord retire puis repose
     * ({@code removePotionEffect} puis {@code addPotionEffect}) : un effet plus
     * long mais plus faible ne masque donc pas celui du stick.
     */
    void apply(ServerPlayer player, ServerLevel level) {
        switch (this) {
            case HEAL -> player.heal(6.0f);
            case SPEED -> replace(player, MobEffects.SPEED, 70, 6);
            case STRENGTH -> replace(player, MobEffects.STRENGTH, 70, 2);
            case JUMP -> launch(player, 1.0d);
            case GOD -> {
                replace(player, MobEffects.REGENERATION, 80, 5);
                absorb(player);
                player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 30, 1));
                replace(player, MobEffects.SPEED, 80, 8);
                replace(player, MobEffects.STRENGTH, 100, 2);
            }
            case DAMAGE -> shockwave(player, level);
            case HYPER_JUMP -> launch(player, 2.365d);
        }
    }

    private static void replace(LivingEntity entity, Holder<MobEffect> effect, int duration, int amplifier) {
        entity.removeEffect(effect);
        entity.addEffect(new MobEffectInstance(effect, duration, amplifier));
    }

    /**
     * {@code setAbsorptionAmount(6)}.
     *
     * <p>En 26.2 l'absorption est plafonnee par l'attribut d'absorption
     * maximale, nul sans effet : poser 6 points a nu ne donnerait rien. On pose
     * donc l'effet d'absorption II (plafond 8) le temps de celui d'une pomme
     * doree, deux minutes, puis les 6 points du stick. Dans l'original ces
     * points restaient jusqu'au premier coup recu ; ici aussi, dans la limite
     * de ces deux minutes.
     */
    private static void absorb(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 1));
        player.setAbsorptionAmount(6.0f);
    }

    /**
     * {@code motionY += force}, puis le paquet de vitesse.
     *
     * <p>Le mouvement d'un joueur est calcule par son client : sans
     * {@code hurtMarked}, la nouvelle vitesse resterait cote serveur et le
     * joueur ne decollerait pas. L'original envoyait de meme un
     * {@code S12PacketEntityVelocity}.
     */
    private static void launch(ServerPlayer player, double force) {
        player.push(0.0d, force, 0.0d);
        player.hurtMarked = true;
    }

    /**
     * Le stick de dommage : tout ce qui vit dans un cube de 3 blocs autour du
     * joueur est repousse de 0,7 (en x et en z, a l'oppose du joueur) et
     * souleve de 0,7. Aucun degat.
     *
     * <p>L'original postait un {@code LivingHurtEvent} a zero degat et epargnait
     * la cible si un module l'annulait — c'est par la que passaient les zones
     * protegees et le PvP desactive. L'equivalent ici : {@code canHarmPlayer}
     * pour un joueur (PvP du serveur, equipes), {@code canAttack} pour le reste.
     *
     * <p>Le « tremblement de terre » qui suivait — des blocs du sol projetes
     * en arc devant le joueur pendant huit ticks, par des entites de bloc qui
     * tombent — est rendu par des particules du bloc vise, en une fois.
     */
    private static void shockwave(ServerPlayer player, ServerLevel level) {
        AABB area = new AABB(player.getX() - 3.0d, player.getY() - 3.0d, player.getZ() - 3.0d,
                player.getX() + 3.0d, player.getY() + 3.0d, player.getZ() + 3.0d);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (target == player || target.isSpectator()) {
                continue;
            }
            if (target instanceof Player other ? !player.canHarmPlayer(other) : !player.canAttack(target)) {
                continue;
            }
            double dx = target.getX() - player.getX() > 0.0d ? 0.7d : -0.7d;
            double dz = target.getZ() - player.getZ() > 0.0d ? 0.7d : -0.7d;
            target.push(dx, 0.7d, dz);
            target.hurtMarked = true;
        }
        earthquake(player, level);
    }

    /**
     * {@code earthquake} : pour chaque rayon de 2 a 9 blocs, trois points a
     * 3, 9 et 15 degres a droite du regard ({@code yaw + 90 + 6f}).
     */
    private static void earthquake(ServerPlayer player, ServerLevel level) {
        for (int radius = 2; radius <= 9; radius++) {
            for (float step = 0.5f; step < 3.0f; step += 1.0f) {
                double angle = Math.toRadians(step * 6.0f + player.getYRot() + 90.0f);
                double x = radius * Math.cos(angle) + player.getX();
                double z = radius * Math.sin(angle) + player.getZ();
                BlockPos ground = BlockPos.containing(x, player.getY() - 1.0d, z);
                BlockState state = level.getBlockState(ground);
                if (state.isAir()) {
                    continue;
                }
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        x, ground.getY() + 1.1d, z, 12, 0.25d, 0.2d, 0.25d, 0.15d);
            }
        }
    }
}

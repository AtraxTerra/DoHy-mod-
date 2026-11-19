package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Ce que font les gadgets du cinquieme lot hors de leur clic : le jetpack, le bateau a moteur,
 * la bombe fumigene qui tombe, et le detecteur de chat.
 */
@EventBusSubscriber
public final class GadgetEvents {

    /** Le bateau a moteur, marque a sa pose. */
    static final String TURBOAT = "dohy_turboat";
    /** La couleur de la fumee, portee par la boule lancee. */
    static final String SMOKE = "dohy_smoke";
    /** Un feu d'artifice brule toutes les 40 tics de vol ({@code Munition : fireworks}). */
    private static final int FUEL_TICKS = 40;

    private GadgetEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        jetpack(player, level);
        turboat(player, level);
    }

    /**
     * Le jetpack ({@code ItemJetpack}) : « Appuyez sur votre touche de saut en portant le jetpack ».
     * Il monte tant qu'on tient le saut, et brule des feux d'artifice ({@code Munition : fireworks}).
     */
    private static void jetpack(ServerPlayer player, ServerLevel level) {
        if (!player.getItemBySlot(EquipmentSlot.CHEST).is(LuckyGadgets.JETPACK.get())
                || !player.getLastClientInput().jump() || player.getAbilities().flying) {
            return;
        }
        ItemStack fuel = GadgetItems.find(player, Items.FIREWORK_ROCKET);
        if (fuel.isEmpty() && !player.getAbilities().instabuild) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, Math.min(0.5d, motion.y + 0.18d), motion.z);
        player.hurtMarked = true;
        player.fallDistance = 0.0d;
        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.6d, player.getZ(), 2, 0.1d, 0.05d,
                0.1d, 0.01d);
        if (player.tickCount % FUEL_TICKS == 0 && !player.getAbilities().instabuild) {
            fuel.shrink(1);
            level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.4f,
                    1.4f);
        }
    }

    /** Le Turboat : tant qu'on avance, le bateau est pousse plus fort. */
    private static void turboat(ServerPlayer player, ServerLevel level) {
        Entity boat = player.getVehicle();
        if (boat == null || !boat.getPersistentData().getBooleanOr(TURBOAT, false) || !player.getLastClientInput().forward()) {
            return;
        }
        Vec3 look = Vec3.directionFromRotation(0.0f, boat.getYRot());
        Vec3 motion = boat.getDeltaMovement().add(look.scale(0.04d));
        double speed = motion.horizontalDistance();
        if (speed > 1.2d) {
            motion = new Vec3(motion.x / speed * 1.2d, motion.y, motion.z / speed * 1.2d);
        }
        boat.setDeltaMovement(motion);
        boat.hurtMarked = true;
        if (player.tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, boat.getX() - look.x, boat.getY() + 0.5d, boat.getZ() - look.z, 2,
                    0.05d, 0.05d, 0.05d, 0.0d);
        }
    }

    /** La bombe fumigene touche : un nuage de sa couleur pendant dix secondes, qui aveugle. */
    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        Entity projectile = event.getProjectile();
        if (!(projectile.level() instanceof ServerLevel level) || !projectile.getPersistentData().contains(SMOKE)) {
            return;
        }
        int color = projectile.getPersistentData().getIntOr(SMOKE, 0xC8C8C8);
        Vec3 at = event.getRayTraceResult().getLocation();
        DustParticleOptions dust = new DustParticleOptions(color, 3.0f);
        level.playSound(null, projectile.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.6f);
        LuckyTasks.repeat(null, 0, 5, 40, step -> {
            level.sendParticles(dust, at.x, at.y + 1.0d, at.z, 40, 2.0d, 1.2d, 2.0d, 0.0d);
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.0d))) {
                living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
            }
        });
        projectile.discard();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        GadgetBlocks.ChatDetectorBlock.onChat(event.getPlayer().level().getServer());
    }
}

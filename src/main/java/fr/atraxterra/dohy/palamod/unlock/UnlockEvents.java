package fr.atraxterra.dohy.palamod.unlock;

import fr.atraxterra.dohy.palamod.pillage.PalaPillage;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Les effets des objets de ce lot qui passent par des evenements : les
 * fleches moddees ({@code EventHandlerArrow}), les impregnations
 * ({@code PotionHandler.imbueHandler}) et l'etiquette en endium
 * ({@code onEndiumNameTagEntityDeath}).
 */
@EventBusSubscriber
public final class UnlockEvents {

    /** {@code EventHandlerArrow} : poison II 6 s, wither I 9 s, lenteur III 5 s, givre 20 s. */
    private static final int POISON_TICKS = 120;
    private static final int WITHER_TICKS = 180;
    private static final int SLOWNESS_TICKS = 100;
    private static final int FROST_TICKS = 400;

    /** {@code imbueHandler} : wither I 5 s, poison I 10 ticks, 2 s de feu. */
    private static final int IMBUE_WITHER_TICKS = 100;
    private static final int IMBUE_POISON_TICKS = 10;
    private static final float IMBUE_FIRE_SECONDS = 2.0f;

    private UnlockEvents() {
    }

    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || arrow.level().isClientSide()
                || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity target)) {
            return;
        }
        PalaArrowItem.Kind kind = null;
        for (PalaArrowItem.Kind candidate : PalaArrowItem.Kind.values()) {
            if (arrow.entityTags().contains(candidate.tag())) {
                kind = candidate;
                break;
            }
        }
        if (kind == null) {
            return;
        }
        arrow.removeTag(kind.tag());
        ItemStack weapon = arrow.getWeaponItem();
        if (weapon == null || !weapon.is(PalaPillage.PALADIUM_BOW.get()) || target == arrow.getOwner()) {
            return;
        }
        switch (kind) {
            case POISON -> target.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 1), arrow.getOwner());
            case WITHER -> target.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, 0), arrow.getOwner());
            case SLOWNESS -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_TICKS, 2),
                    arrow.getOwner());
            case FROST -> target.addEffect(new MobEffectInstance(PalaUnlocks.FROST, FROST_TICKS, 0), arrow.getOwner());
        }
    }

    /** Un coup porte par une creature impregnee passe son effet a la cible. */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker.level().isClientSide()
                || attacker == event.getEntity()) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (attacker.hasEffect(PalaUnlocks.IMBUE_WITHER)) {
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, IMBUE_WITHER_TICKS, 0), attacker);
        }
        if (attacker.hasEffect(PalaUnlocks.IMBUE_POISON)) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, IMBUE_POISON_TICKS, 0), attacker);
        }
        if (attacker.hasEffect(PalaUnlocks.IMBUE_FIRE)) {
            target.igniteForSeconds(IMBUE_FIRE_SECONDS);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide()
                && mob.getPersistentData().contains(EndiumNametagItem.OWNER)) {
            EndiumNametagItem.forget(mob);
        }
    }
}

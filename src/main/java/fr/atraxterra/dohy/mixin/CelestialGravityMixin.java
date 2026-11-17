package fr.atraxterra.dohy.mixin;

import fr.atraxterra.dohy.arrakis.DoHyArrakis;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sur Arrakis, on pese son poids : NTM Space donne a Duna un tiers de la
 * pesanteur terrestre, ce qui ferait sauter par-dessus les dunes. Sa
 * pesanteur de reference (1,6, celle de Kerbin) est rendue a tout ce qui vit
 * sur la planete ; les orbites et les fusees n'en savent rien.
 *
 * <p>Classe de NTM Space visee par son nom : sans le mod, rien ne s'applique.
 */
@Pseudo
@Mixin(targets = "com.hbmspace.dim.CelestialBody", remap = false)
public abstract class CelestialGravityMixin {

    @Inject(method = "getGravity(Lnet/minecraft/world/entity/LivingEntity;)F", at = @At("RETURN"),
            cancellable = true, require = 0)
    private static void dohy$arrakisWeight(LivingEntity entity, CallbackInfoReturnable<Float> cir) {
        if (entity != null && entity.level() != null && entity.level().dimension() == DoHyArrakis.DIMENSION) {
            cir.setReturnValue(1.6f);
        }
    }
}

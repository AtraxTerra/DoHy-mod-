package fr.atraxterra.dohy.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Arrakis a son propre relief : les plateaux que NTM Space empile sur les
 * collines de Duna (jusqu'a trente blocs de sable rajoutes apres le terrain)
 * recouvraient la ville et les falaises, et l'on arrivait enterre dans
 * l'astroport. Pour les reglages de terrain de Duna, il n'y a plus de plateaux.
 *
 * <p>Classe de NTM Space visee par son nom : sans le mod, rien ne s'applique.
 */
@Pseudo
@Mixin(targets = "com.hbmspace.world.gen.Terrace", remap = false)
public abstract class ArrakisTerraceMixin {

    @Unique
    private static final ResourceKey<NoiseGeneratorSettings> DOHY$ARRAKIS =
            ResourceKey.create(Registries.NOISE_SETTINGS, Identifier.fromNamespaceAndPath("hbmspace", "duna"));

    @Inject(method = "of(Lnet/minecraft/core/Holder;)Lcom/hbmspace/world/gen/Terrace;", at = @At("HEAD"),
            cancellable = true, require = 0)
    private static void dohy$noPlateauOnArrakis(Holder<NoiseGeneratorSettings> settings,
                                               CallbackInfoReturnable<Object> cir) {
        if (settings != null && settings.is(DOHY$ARRAKIS)) {
            cir.setReturnValue(null);
        }
    }
}

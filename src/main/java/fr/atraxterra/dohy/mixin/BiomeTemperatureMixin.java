package fr.atraxterra.dohy.mixin;

import fr.atraxterra.dohy.season.SeasonClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * La saison dans la temperature des biomes.
 *
 * <p>Tout ce qui, dans le jeu, depend du froid passe par une seule methode
 * privee, {@code Biome.getTemperature(BlockPos, int)} : la pluie qui devient
 * neige (des deux cotes, donc aussi a l'ecran), l'eau qui gele, la neige qui
 * s'accumule, et la decoration des chunks neufs. Y ajouter le decalage de
 * saison suffit a donner l'hiver a tout cela.
 *
 * <p>On ajoute apres le cache du jeu, pas avant : le cache garde mille
 * positions par biome et par fil, et un decalage mis dedans y resterait fige.
 * Le decalage vient d'une table precalculee ({@link SeasonClimate}) : une
 * lecture de tableau, rien de plus, sur un chemin tres frequente.
 */
@Mixin(Biome.class)
public abstract class BiomeTemperatureMixin {

    @Inject(method = "getTemperature(Lnet/minecraft/core/BlockPos;I)F", at = @At("RETURN"), cancellable = true)
    private void dohy$season(BlockPos pos, int seaLevel, CallbackInfoReturnable<Float> callback) {
        float offset = SeasonClimate.offset(pos.getX(), pos.getZ());
        if (offset != 0.0f) {
            callback.setReturnValue(callback.getReturnValueF() + offset);
        }
    }
}

package fr.atraxterra.dohy.mixin;

import fr.atraxterra.dohy.client.season.SeasonColors;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Les couleurs de saison de l'herbe et des feuilles (client seulement).
 *
 * <p>Le jeu teinte l'herbe et le feuillage par trois fonctions de
 * {@link BiomeColors}, appelees bloc par bloc quand un chunk se construit ; les
 * blocs des autres mods qui suivent le biome (Biomes O' Plenty compris) passent
 * par les memes. On prend leur resultat et on le tire vers la teinte de la
 * saison ({@link SeasonColors}) : roux en automne, terne en hiver, vif au
 * printemps, intact l'ete — d'autant plus fort qu'on monte en latitude.
 *
 * <p>Ces fonctions tournent sur les fils de construction des chunks : la
 * teinte ne lit que des valeurs figees, remplacees d'un bloc a chaque palier.
 */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {

    @Inject(method = "getAverageGrassColor", at = @At("RETURN"), cancellable = true)
    private static void dohy$grass(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(SeasonColors.grass(callback.getReturnValueI(), pos));
    }

    @Inject(method = "getAverageFoliageColor", at = @At("RETURN"), cancellable = true)
    private static void dohy$foliage(BlockAndTintGetter level, BlockPos pos,
                                     CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(SeasonColors.foliage(callback.getReturnValueI(), pos));
    }

    @Inject(method = "getAverageDryFoliageColor", at = @At("RETURN"), cancellable = true)
    private static void dohy$dryFoliage(BlockAndTintGetter level, BlockPos pos,
                                        CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(SeasonColors.foliage(callback.getReturnValueI(), pos));
    }
}

package fr.atraxterra.dohy.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * La temperature d'un biome en un point, telle que le jeu la voit : altitude et
 * saison comprises.
 *
 * <p>En 26.2 la methode est privee ; le jeu n'en publie que des seuils
 * ({@code warmEnoughToRain}, {@code coldEnoughToSnow}). Les saisons ont besoin
 * du nombre lui-meme : pour fondre la neige un peu au-dessus du seuil ou elle
 * tombe, et pour afficher des degres.
 */
@Mixin(Biome.class)
public interface BiomeAccessor {

    @Invoker("getTemperature")
    float dohy$invokeTemperature(BlockPos pos, int seaLevel);
}

package fr.atraxterra.dohy.mixin;

import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Les cartes faites d'avance (WorldPainter) dont les chunks attendent leur
 * decoration.
 *
 * <p>Un outil de dessin de cartes qui veut laisser le jeu poser les minerais
 * et les arbres enregistre ses chunks a l'etape {@code liquid_carvers} : le
 * terrain est fait, il reste la decoration. Cette etape a disparu du jeu. En
 * 26.2, {@code ChunkStatus.byName("liquid_carvers")} rend {@code empty}
 * (sonde le 2026-11-12), et les correcteurs de donnees ne touchent pas un
 * chunk qui se dit deja recent : le jeu prend alors un chunk tout dessine pour
 * un chunk vide et lui repasse le terrain de sa propre generation par-dessus.
 * Une carte de la Terre en ressort defiguree.
 *
 * <p>Un mixin parce que rien d'autre ne passe par la : le nom est lu au
 * chargement du chunk, avant tout evenement. On rend a ce nom le sens qu'il
 * avait — l'etape juste apres le creusement, {@code carvers} — et le jeu
 * reprend ou l'outil s'est arrete : la decoration, la lumiere, puis le chunk
 * complet. Aucun autre nom n'est touche.
 */
@Mixin(ChunkStatus.class)
public abstract class ChunkStatusMixin {

    @Inject(method = "byName", at = @At("HEAD"), cancellable = true)
    private static void dohy$liquidCarvers(String name, CallbackInfoReturnable<ChunkStatus> callback) {
        if ("liquid_carvers".equals(name) || "minecraft:liquid_carvers".equals(name)) {
            callback.setReturnValue(ChunkStatus.CARVERS);
        }
    }
}

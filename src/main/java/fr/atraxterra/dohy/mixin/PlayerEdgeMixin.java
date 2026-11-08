package fr.atraxterra.dohy.mixin;

import fr.atraxterra.dohy.palamod.alchemist.NoFall;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * L'anti-chute de l'Alchimiste ({@code IMixinEntity} de PalaMod) : avec des
 * bottes enchantees, le bord d'un bloc retient le joueur comme s'il etait
 * accroupi.
 *
 * <p>Un mixin parce qu'aucun evenement ne couvre ce point : {@code Player}
 * decide seul, dans {@code maybeBackOffFromEdge}, en demandant
 * {@code isStayingOnGroundSurface()} — qui ne fait que lire la touche
 * d'accroupissement. La methode existe telle quelle en 26.2 (sonde d'API), et
 * {@code LocalPlayer} ne la redefinit pas : le client, qui calcule son propre
 * deplacement, passe donc aussi par ici.
 */
@Mixin(Player.class)
public abstract class PlayerEdgeMixin {

    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
    private void dohy$noFall(CallbackInfoReturnable<Boolean> callback) {
        if (NoFall.active((Player) (Object) this)) {
            callback.setReturnValue(true);
        }
    }
}

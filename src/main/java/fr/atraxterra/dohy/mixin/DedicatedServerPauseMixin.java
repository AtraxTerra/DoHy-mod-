package fr.atraxterra.dohy.mixin;

import fr.atraxterra.dohy.faction.absence.KeptBases;
import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * La base qui tourne sans son joueur : un serveur dedie vide ne se met pas en
 * pause tant qu'une base est tenue.
 *
 * <p>Depuis 1.21.2, un serveur dedie sans joueur s'arrete de tourner au bout de
 * {@code pause-when-empty-seconds} (60 s par defaut) : plus aucun tick, ni pour
 * les chunks tenus, ni pour le marche des nations. {@code MinecraftServer.tickServer}
 * lit ce delai par {@code pauseWhenEmptySeconds()}, que {@code DedicatedServer}
 * redefinit pour lire {@code server.properties} (sonde d'API sur 26.2) : on y
 * repond 0, « jamais de pause », tant qu'une base est tenue. Sans base, le
 * reglage du serveur s'applique comme avant.
 */
@Mixin(DedicatedServer.class)
public abstract class DedicatedServerPauseMixin {

    @Inject(method = "pauseWhenEmptySeconds", at = @At("HEAD"), cancellable = true)
    private void dohy$keepBases(CallbackInfoReturnable<Integer> callback) {
        if (KeptBases.any()) {
            callback.setReturnValue(0);
        }
    }
}

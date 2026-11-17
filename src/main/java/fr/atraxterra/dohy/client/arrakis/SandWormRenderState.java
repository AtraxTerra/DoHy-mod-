package fr.atraxterra.dohy.client.arrakis;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Ce que le rendu du ver lit de l'entite, image par image. */
public class SandWormRenderState extends LivingEntityRenderState {

    /** Sorti du sable : avant, il n'y a rien a dessiner. */
    public boolean emerged;
    /** Ticks depuis qu'il a jailli, fraction d'image comprise. */
    public float since;
}

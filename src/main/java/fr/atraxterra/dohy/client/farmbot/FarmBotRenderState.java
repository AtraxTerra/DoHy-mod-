package fr.atraxterra.dohy.client.farmbot;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Ce que le rendu du bot de farming lit de l'entite, image par image. */
public class FarmBotRenderState extends LivingEntityRenderState {

    /** Le coup de houe en cours, de 0 a 1. */
    public float swing;
}

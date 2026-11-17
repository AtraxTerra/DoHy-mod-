package fr.atraxterra.dohy.client.arrakis;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Ce que le rendu de la moissonneuse lit de l'entite, image par image. */
public class HarvesterRenderState extends LivingEntityRenderState {

    /** La bouche est ouverte : le tambour tourne. */
    public boolean working;
}

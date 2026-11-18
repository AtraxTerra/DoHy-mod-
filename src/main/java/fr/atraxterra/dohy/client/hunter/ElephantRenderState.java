package fr.atraxterra.dohy.client.hunter;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Ce que le rendu de l'elephant lit de l'entite, image par image. */
public class ElephantRenderState extends LivingEntityRenderState {

    /** 0 petit, 1 ado, 2 adulte : le modele et la texture en dependent. */
    public int age;
    public Identifier texture;
}

package fr.atraxterra.dohy.client.mount;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/** Ce que le rendu du dancarok lit de l'entite, image par image. */
public class DancarokRenderState extends LivingEntityRenderState {

    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState specialAnimation = new AnimationState();
    /** Monture invoquee : la course remplace la marche, comme dans Mount. */
    public boolean running;
    public Identifier texture;
}

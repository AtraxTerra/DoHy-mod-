package fr.atraxterra.dohy.client.mount;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.mount.Dancarok;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * {@code DancarokGeoRenderer} : la texture suit la corne posee
 * ({@code DancarokEntityModel.getTextureLocation}) — sans corne, puis
 * amethyste, titane et paladium. Le jar a aussi une variante en paladium
 * vert, jamais utilisee : elle ne l'est pas ici non plus.
 *
 * <p>Animations ({@code predicateMovement}) : en mouvement, la marche pour un
 * sauvage et la course pour une monture invoquee, sprint ou pas ; immobile,
 * l'attente. Le coup de corne joue {@code special} 500 ms. {@code attack} et
 * {@code death} existent dans le fichier mais ne sont jamais jouees.
 */
public class DancarokRenderer extends MobRenderer<Dancarok, DancarokRenderState, DancarokModel> {

    private static final Identifier[] TEXTURES = {
            texture("dancarok"),
            texture("dancarok_amethyst_horn"),
            texture("dancarok_titane_horn"),
            texture("dancarok_paladium_horn")};

    public DancarokRenderer(EntityRendererProvider.Context context) {
        super(context, new DancarokModel(context.bakeLayer(DancarokModel.LAYER)), 1.2f);
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "textures/entity/" + name + ".png");
    }

    @Override
    public DancarokRenderState createRenderState() {
        return new DancarokRenderState();
    }

    @Override
    public void extractRenderState(Dancarok mount, DancarokRenderState state, float partialTick) {
        super.extractRenderState(mount, state, partialTick);
        state.idleAnimation.copyFrom(mount.idleAnimation);
        state.specialAnimation.copyFrom(mount.specialAnimation);
        state.running = mount.isTamed();
        int horn = mount.hornType();
        state.texture = TEXTURES[horn >= 0 && horn < TEXTURES.length ? horn : 0];
    }

    @Override
    public Identifier getTextureLocation(DancarokRenderState state) {
        return state.texture == null ? TEXTURES[0] : state.texture;
    }
}

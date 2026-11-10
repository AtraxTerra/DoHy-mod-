package fr.atraxterra.dohy.client.wither;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.wither.WitherKind;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.monster.wither.WitherBossModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.WitherBossRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.WitherRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Le rendu d'un wither du pillage, d'apres les rendus du wiki : chaque genre
 * a sa peau ({@code entity/wither/<id>.png}, peinte par
 * {@code tools/wither_skins.py}), certains leurs pieces en plus
 * ({@link PalaWitherGeometry}) et des yeux qui brillent dans le noir
 * (predateur, supreme, baby). Le wither amusant garde la peau de PalaMod, le
 * wither de base celle du vanilla. La taille vient de l'attribut d'echelle, que
 * le rendu vanilla applique deja. Pendant la charge, la peau d'invulnerabilite
 * clignote comme sur le vanilla : la bleue du vanilla, ou celle du genre.
 */
public class PalaWitherRenderer extends WitherBossRenderer {

    private static final Identifier FUNNY = texture("funny_wither");

    private final WitherKind kind;
    private final Identifier skin;
    private final Identifier invulnerable;

    public PalaWitherRenderer(EntityRendererProvider.Context context, WitherKind kind) {
        super(context);
        this.kind = kind;
        ModelLayerLocation layer = PalaWitherGeometry.layer(kind);
        if (layer != null) {
            this.model = new PalaWitherModel(context.bakeLayer(layer), kind == WitherKind.SUPREME);
        }
        boolean skinned = PalaWitherGeometry.skinned(kind);
        this.skin = skinned ? texture(kind.id()) : null;
        this.invulnerable = skinned ? texture(kind.id() + "_invulnerable") : null;
        if (PalaWitherGeometry.glowing(kind)) {
            addLayer(new Glow(this, texture(kind.id() + "_eyes")));
        }
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "textures/entity/wither/" + name + ".png");
    }

    @Override
    public Identifier getTextureLocation(WitherRenderState state) {
        int ticks = Mth.floor(state.invulnerableTicks);
        boolean flashing = ticks > 0 && (ticks > 80 || ticks / 5 % 2 != 1);
        if (skin != null) {
            return flashing ? invulnerable : skin;
        }
        if (kind == WitherKind.FUNNY && !flashing) {
            return FUNNY;
        }
        return super.getTextureLocation(state);
    }

    /** Les yeux (et les braises du predateur) : pleine lumiere, meme de nuit. */
    private static final class Glow extends EyesLayer<WitherRenderState, WitherBossModel> {

        private final RenderType type;

        Glow(RenderLayerParent<WitherRenderState, WitherBossModel> parent, Identifier texture) {
            super(parent);
            this.type = RenderTypes.eyes(texture);
        }

        @Override
        public RenderType renderType() {
            return type;
        }
    }
}

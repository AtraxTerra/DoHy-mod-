package fr.atraxterra.dohy.client.wither;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.wither.WitherKind;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitherBossRenderer;
import net.minecraft.client.renderer.entity.state.WitherRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Le rendu d'un wither du pillage : le modele du wither vanilla, teinte selon
 * son genre (pale pour le passif, bleu pour l'assoiffe, rouge pour le
 * predateur, or pour le supreme). Le wither amusant a sa propre peau, celle de
 * PalaMod ({@code entity/wither/funny_wither}). La taille vient de l'attribut
 * d'echelle, que le rendu vanilla applique deja. Pendant la charge, la peau
 * bleue d'invulnerabilite clignote comme sur le vanilla.
 */
public class PalaWitherRenderer extends WitherBossRenderer {

    private static final Identifier FUNNY =
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "textures/entity/wither/funny_wither.png");

    private final WitherKind kind;

    public PalaWitherRenderer(EntityRendererProvider.Context context, WitherKind kind) {
        super(context);
        this.kind = kind;
    }

    @Override
    public Identifier getTextureLocation(WitherRenderState state) {
        if (kind != WitherKind.FUNNY) {
            return super.getTextureLocation(state);
        }
        int ticks = Mth.floor(state.invulnerableTicks);
        boolean flashing = ticks > 0 && (ticks > 80 || ticks / 5 % 2 != 1);
        return flashing ? super.getTextureLocation(state) : FUNNY;
    }

    @Override
    protected int getModelTint(WitherRenderState state) {
        return kind.tint() != 0 ? kind.tint() : super.getModelTint(state);
    }
}

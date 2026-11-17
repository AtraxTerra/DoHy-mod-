package fr.atraxterra.dohy.client.arrakis;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.arrakis.SandWorm;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Le rendu du ver : rien tant qu'il file sous le sable (on ne voit que le sable
 * qui bouillonne, envoye par le serveur), puis le ver qui creve le sable gueule
 * ouverte, se dresse, rugit, la referme et replonge en s'inclinant. Le modele
 * est dessine petit ; le rendu le grandit deux fois et demie (pres de trente
 * blocs crocs dresses), et ne le cache jamais derriere sa petite boite.
 */
public class SandWormRenderer extends MobRenderer<SandWorm, SandWormRenderState, SandWormModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "textures/entity/sandworm.png");
    private static final float SCALE = 2.5f;
    /** La longueur du ver, en blocs une fois double. */
    private static final float LENGTH = SandWormGeometry.LENGTH / 16.0f * SCALE;

    public SandWormRenderer(EntityRendererProvider.Context context) {
        super(context, new SandWormModel(context.bakeLayer(SandWormModel.LAYER)), 0.0f);
    }

    @Override
    public SandWormRenderState createRenderState() {
        return new SandWormRenderState();
    }

    @Override
    public void extractRenderState(SandWorm worm, SandWormRenderState state, float partialTick) {
        super.extractRenderState(worm, state, partialTick);
        state.emerged = worm.emerged();
        state.since = worm.emergedTicks() + partialTick;
    }

    @Override
    public boolean shouldRender(SandWorm worm, Frustum frustum, double camX, double camY, double camZ) {
        return worm.emerged();
    }

    /** La part du ver hors du sable, de 0 a 1 : il creve le sable d'un coup, puis se dresse plus lentement. */
    static float rise(float t) {
        float up = SandWorm.RISE;
        float down = up + SandWorm.OPEN + SandWorm.BITE;
        if (t < up) {
            float left = 1.0f - Math.max(0.0f, t) / up;
            return 1.0f - left * left * left;
        }
        if (t < down) {
            return 1.0f;
        }
        return 1.0f - SandWormModel.smooth((t - down) / SandWorm.SINK);
    }

    @Override
    protected void scale(SandWormRenderState state, PoseStack pose) {
        // le repere est deja retourne : descendre, c'est aller vers les y positifs
        pose.translate(0.0f, (1.0f - rise(state.since)) * LENGTH, 0.0f);
        float down = SandWorm.RISE + SandWorm.OPEN + SandWorm.BITE;
        if (state.since > down) {
            pose.mulPose(Axis.XP.rotationDegrees(SandWormModel.smooth((state.since - down) / SandWorm.SINK) * 28.0f));
        }
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public Identifier getTextureLocation(SandWormRenderState state) {
        return TEXTURE;
    }
}

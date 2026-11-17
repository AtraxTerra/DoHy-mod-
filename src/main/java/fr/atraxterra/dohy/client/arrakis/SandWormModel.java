package fr.atraxterra.dohy.client.arrakis;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.arrakis.SandWorm;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Le ver des sables : ses anneaux ondulent l'un apres l'autre, ses crocs
 * s'ouvrent quand il creve le sable, se serrent sur sa proie, s'ouvrent grand
 * au sommet et se referment d'un coup ; les couronnes de dents tournent au fond
 * de sa gorge. La geometrie est dans
 * {@link SandWormGeometry}, ecrite par {@code tools/sandworm_model.py}.
 */
public class SandWormModel extends EntityModel<SandWormRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "sandworm"), "main");

    /** Crocs replies sur la gueule, et grands ouverts. */
    private static final float CLOSED = 0.55f;
    private static final float OPEN = -0.95f;
    private static final float SWAY = 0.05f;
    /** La courbure du corps dresse, par anneau : il se penche sur sa proie. */
    private static final float BEND = 0.035f;

    private final ModelPart[] segments = new ModelPart[SandWormGeometry.SEGMENTS];
    private final ModelPart[] tusks = new ModelPart[SandWormGeometry.TUSKS];
    private final ModelPart teeth;

    public SandWormModel(ModelPart root) {
        super(root);
        ModelPart part = root.getChild("segment0");
        segments[0] = part;
        for (int i = 1; i < segments.length; i++) {
            part = part.getChild("segment" + i);
            segments[i] = part;
        }
        ModelPart head = part.getChild("head");
        for (int k = 0; k < tusks.length; k++) {
            tusks[k] = head.getChild("tusk_arm" + k).getChild("tusk" + k);
        }
        this.teeth = head.getChild("teeth");
    }

    /** Il creve le sable gueule ouverte, puis la serre sur ce qu'il a pris. */
    private static final float BURST = 0.8f;
    private static final float HOLD = 0.12f;
    private static final float GRAB = 11.0f;

    /**
     * L'ouverture de la gueule, de 0 a 1, selon le moment du jaillissement : ouverte
     * en crevant le sable, serree pendant qu'il se dresse, grande ouverte au sommet
     * (il rugit), refermee d'un coup, close quand il replonge.
     */
    static float mouth(float t) {
        float rise = SandWorm.RISE;
        float roar = rise + SandWorm.OPEN * 0.6f;
        float open = rise + SandWorm.OPEN;
        float shut = open + SandWorm.BITE;
        float grip = SandWorm.SWALLOW_AT;
        if (t < grip) {
            return BURST;
        }
        if (t < GRAB) {
            return Mth.lerp(smooth((t - grip) / (GRAB - grip)), BURST, HOLD);
        }
        if (t < rise) {
            return HOLD;
        }
        if (t < roar) {
            return Mth.lerp(smooth((t - rise) / (roar - rise)), HOLD, 1.0f);
        }
        if (t < open) {
            return 1.0f;
        }
        if (t < shut) {
            return 1.0f - smooth((t - open) / SandWorm.BITE);
        }
        return 0.0f;
    }

    static float smooth(float x) {
        float c = Mth.clamp(x, 0.0f, 1.0f);
        return c * c * (3.0f - 2.0f * c);
    }

    @Override
    public void setupAnim(SandWormRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        // droit tant qu'il creve le sable (sa tete reste sur sa proie), courbe une fois dresse
        float up = SandWormRenderer.rise(state.since);
        float bend = BEND * up * up;
        for (int i = 0; i < segments.length; i++) {
            float sway = i == 0 ? 0.0f : SWAY;
            segments[i].xRot = (i == 0 ? 0.0f : bend) + Mth.sin(age * 0.07f + i * 0.7f) * sway;
            segments[i].zRot = Mth.cos(age * 0.05f + i * 0.55f) * sway;
        }
        float open = mouth(state.since);
        float flex = open > 0.99f ? Mth.sin(age * 0.6f) * 0.06f : 0.0f;
        for (ModelPart tusk : tusks) {
            tusk.xRot = Mth.lerp(open, CLOSED, OPEN) + flex;
        }
        teeth.yRot = age * 0.05f;
    }
}

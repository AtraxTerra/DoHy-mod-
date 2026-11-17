package fr.atraxterra.dohy.client.hunter;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.hunter.Elephant;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

/**
 * {@code RenderElephant} : un modele par age, et la texture
 * {@code elephant/<age>/elephant_<robe>[_tamed].png} — la robe ne change qu'a
 * l'age adulte (grise, vert pale, verte), l'apprivoise porte un caparacon.
 * L'ombre est d'un bloc, a tout age, comme l'original.
 *
 * <p><b>Ecart</b> : l'original habillait d'une peau bariolee l'elephant nomme
 * d'apres un personnage de livre pour enfants ; ce clin d'oeil a l'oeuvre d'un
 * autre n'est pas repris.
 */
public class ElephantRenderer extends MobRenderer<Elephant, ElephantRenderState, ElephantModel> {

    private static final String[] AGES = {"child", "ado", "adult"};

    /** [age][robe][apprivoise] ; les petits et les ados n'ont que la robe 0. */
    private static final Identifier[][][] TEXTURES = textures();

    private final ElephantModel[] models;

    public ElephantRenderer(EntityRendererProvider.Context context) {
        super(context, ElephantModel.child(context.bakeLayer(ElephantModel.CHILD)), 1.0f);
        this.models = new ElephantModel[]{model,
                ElephantModel.teen(context.bakeLayer(ElephantModel.TEEN)),
                ElephantModel.adult(context.bakeLayer(ElephantModel.ADULT))};
    }

    private static Identifier[][][] textures() {
        Identifier[][][] all = new Identifier[AGES.length][3][2];
        for (int age = 0; age < AGES.length; age++) {
            for (int color = 0; color < 3; color++) {
                for (int tamed = 0; tamed < 2; tamed++) {
                    int robe = age == Elephant.ADULT ? color : 0;
                    all[age][color][tamed] = Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE,
                            "textures/entity/elephant/" + AGES[age] + "/elephant_" + robe
                                    + (tamed == 1 ? "_tamed" : "") + ".png");
                }
            }
        }
        return all;
    }

    @Override
    public ElephantRenderState createRenderState() {
        return new ElephantRenderState();
    }

    @Override
    public void extractRenderState(Elephant elephant, ElephantRenderState state, float partialTick) {
        super.extractRenderState(elephant, state, partialTick);
        state.age = elephant.age();
        state.texture = TEXTURES[state.age][elephant.color()][elephant.isTamed() ? 1 : 0];
    }

    @Override
    public void submit(ElephantRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        model = models[Math.clamp(state.age, 0, models.length - 1)];
        super.submit(state, pose, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(ElephantRenderState state) {
        return state.texture == null ? TEXTURES[0][0][0] : state.texture;
    }
}

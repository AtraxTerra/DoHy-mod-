package fr.atraxterra.dohy.client.botanist;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.atraxterra.dohy.client.rpg.BedrockModel;
import fr.atraxterra.dohy.palamod.botanist.GeneScannerBlock;
import fr.atraxterra.dohy.palamod.botanist.GeneScannerBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Le scanner de Genis, avec le modele de JobsReborn ({@code TileEntityBotanistMachineRenderer}) :
 * l'animation de son etat (attente, sans charbon, scan en cours, scan termine), et l'objet analyse
 * pose sur son plateau ({@code seed_anchor}), qui tourne doucement.
 *
 * <p>Comme dans JobsReborn, l'animation du scan s'etire sur la duree du scan : l'intro et la fin
 * se jouent a vitesse normale, la barre de progression du modele ({@code progress_bar}, de 1 s a
 * 4,175 s dans le fichier) se remplit en meme temps que le scan.
 *
 * <p>Le modele fait 69 pixels de haut (le scanner du laboratoire) : il est reduit pour tenir dans
 * ses deux blocs.
 */
public class GeneScannerRenderer implements BlockEntityRenderer<GeneScannerBlockEntity, GeneScannerRenderer.State> {

    /** 69 pixels a 0,44 : un peu moins de deux blocs. */
    private static final float SCALE = 0.44f;
    /** Le modele est decale de 3,5 pixels vers l'avant : on le recentre sur le bloc. */
    private static final float CENTER_Z = 3.5f / 16.0f;
    /** Le plateau ou se pose la graine ({@code seed_anchor}), en pixels au-dessus du sol du modele. */
    private static final float ANCHOR_Y = 16.0f;
    /** La fenetre de la barre de progression dans l'animation du scan, et sa duree (le fichier du jar). */
    private static final float WINDOW_START = 1.0f;
    private static final float WINDOW_END = 4.175f;
    private static final float SCAN_LENGTH = 5.0f;

    private final ItemModelResolver items;

    public GeneScannerRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.itemModelResolver();
    }

    public static final class State extends BlockEntityRenderState {
        String clip = "idle";
        float seconds;
        float yaw;
        float age;
        boolean hasItem;
        final ItemStackRenderState item = new ItemStackRenderState();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GeneScannerBlockEntity scanner, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(scanner, state, crumbling);
        long now = scanner.getLevel() == null ? 0L : scanner.getLevel().getGameTime();
        float age = now + partialTick;
        state.age = age;
        GeneScannerBlockEntity.Anim anim = scanner.anim();
        state.clip = anim.clip();
        state.seconds = switch (anim) {
            case SCANNING -> stretched((age - scanner.scanStart()) / 20.0f, scanner.scanDuration() / 20.0f);
            case COMPLETE -> (age - scanner.animSince()) / 20.0f;
            default -> age / 20.0f;
        };
        Direction facing = scanner.getBlockState().hasProperty(GeneScannerBlock.FACING)
                ? scanner.getBlockState().getValue(GeneScannerBlock.FACING) : Direction.NORTH;
        state.yaw = 180.0f - facing.toYRot();
        ItemStack display = scanner.display();
        state.hasItem = !display.isEmpty();
        state.item.clear();
        if (state.hasItem) {
            items.updateForTopItem(state.item, display, ItemDisplayContext.GROUND, scanner.getLevel(), null, 0);
        }
    }

    /**
     * Le temps dans l'animation du scan, {@code elapsed} secondes apres son debut, pour un scan de
     * {@code total} secondes : l'intro et la fin a vitesse normale, la fenetre de la barre etiree.
     */
    static float stretched(float elapsed, float total) {
        float outro = SCAN_LENGTH - WINDOW_END;
        float middle = total - WINDOW_START - outro;
        if (middle <= 0.0f || elapsed < WINDOW_START) {
            return Math.max(0.0f, elapsed);
        }
        if (elapsed < WINDOW_START + middle) {
            return WINDOW_START + (elapsed - WINDOW_START) / middle * (WINDOW_END - WINDOW_START);
        }
        return Math.min(SCAN_LENGTH, WINDOW_END + (elapsed - WINDOW_START - middle));
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        BedrockModel model = BotanistModels.scanner();
        int light = state.lightCoords;
        if (model != null) {
            String clip = state.clip;
            float seconds = state.seconds;
            pose.pushPose();
            pose.translate(0.5f, 0.0f, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(state.yaw));
            pose.scale(SCALE, SCALE, SCALE);
            pose.translate(0.0f, 0.0f, CENTER_Z);
            // Le repere des modeles Bedrock du moteur des monstres : retourne, sol a 24 pixels.
            pose.scale(-1.0f, -1.0f, 1.0f);
            pose.translate(0.0f, -1.501f, 0.0f);
            collector.submitCustomGeometry(pose, model.renderType(BotanistModels.SCANNER_TEXTURE), (last, buffer) -> {
                model.resetPose();
                model.animate(clip, seconds);
                model.draw(last, buffer, light, OverlayTexture.NO_OVERLAY);
            });
            pose.popPose();
        }
        if (state.hasItem) {
            pose.pushPose();
            float bob = (float) Math.sin(state.age / 12.0f) * 0.02f;
            pose.translate(0.5f, ANCHOR_Y / 16.0f * SCALE + 0.02f + bob, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(state.age * 2.0f % 360.0f));
            pose.scale(0.55f, 0.55f, 0.55f);
            state.item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(GeneScannerBlockEntity scanner) {
        BlockPos pos = scanner.getBlockPos();
        return new AABB(pos.getX() - 0.25d, pos.getY(), pos.getZ() - 0.25d, pos.getX() + 1.25d, pos.getY() + 2.1d,
                pos.getZ() + 1.25d);
    }
}

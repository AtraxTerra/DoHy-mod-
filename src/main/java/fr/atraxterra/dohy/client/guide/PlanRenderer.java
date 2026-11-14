package fr.atraxterra.dohy.client.guide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.atraxterra.dohy.guide.plan.Blueprint;
import fr.atraxterra.dohy.guide.plan.PlanData;
import fr.atraxterra.dohy.guide.plan.PlanItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * L'hologramme des plans de construction.
 *
 * <p>Chaque plan pose de l'inventaire (dans la dimension, a moins de 160
 * blocs) dessine ses cases en cubes translucides, a la texture du bloc
 * attendu : teintes de bleu pour un bloc a poser, de rouge pour un bloc a
 * retirer, de jaune pour un bloc a finir, d'un voile vert pour un bloc en
 * place. Un plan tenu en main et pas encore pose montre ou il irait, sur le
 * bloc vise, en blanc.
 *
 * <p>Tout se lit chez le client : le plan porte sa pose ({@link PlanData}),
 * et le monde du client dit ce qui est construit.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class PlanRenderer {

    private static final int FULL_BRIGHT = 0xF000F0;
    private static final double RANGE = 160.0d;
    private static final double DRAW = 72.0d;

    private static final int TO_PLACE = 0x9C7FB6FF;
    private static final int TO_REMOVE = 0xB4FF4A4A;
    private static final int TO_FINISH = 0xA8FFD34A;
    private static final int IN_PLACE = 0x3022E558;
    private static final int PREVIEW = 0x70FFFFFF;

    /** Un cube a dessiner, en coordonnees relatives a la camera. */
    private record Ghost(float x, float y, float z, TextureAtlasSprite sprite, int color, float inset) {
    }

    private PlanRenderer() {
    }

    @SubscribeEvent
    public static void onSubmit(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        List<Ghost> ghosts = new ArrayList<>();
        String dimension = level.dimension().identifier().toString();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof PlanItem) {
                PlanData data = PlanItem.data(stack);
                data.anchor().ifPresent(anchor -> {
                    if (anchor.dimension().equals(dimension)
                            && anchor.pos().distToCenterSqr(player.position()) < RANGE * RANGE) {
                        placed(minecraft, level, data, anchor, camera, ghosts);
                    }
                });
            }
        }
        preview(minecraft, player, camera, ghosts);
        if (ghosts.isEmpty()) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        RenderType type = RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        event.getSubmitNodeCollector().submitCustomGeometry(pose, type, (last, buffer) -> {
            for (Ghost ghost : ghosts) {
                cube(last, buffer, ghost);
            }
        });
    }

    /** Un plan pose : chaque case selon ce qui est construit. */
    private static void placed(Minecraft minecraft, ClientLevel level, PlanData data, PlanData.Anchor anchor,
                               Vec3 camera, List<Ghost> ghosts) {
        Blueprint.Variant variant = data.resolve().variant(data.variant());
        for (Blueprint.Cell cell : variant.cells()) {
            Blueprint.Expect expect = cell.expect();
            if (!expect.required() && !expect.exclusive()) {
                continue;
            }
            BlockPos pos = Blueprint.place(anchor.pos(), anchor.quarter(), cell.x(), cell.y(), cell.z());
            if (pos.distToCenterSqr(camera) > DRAW * DRAW) {
                continue;
            }
            Blueprint.Status status = expect.status(level, pos);
            if (status == Blueprint.Status.OPTIONAL) {
                continue;
            }
            int color;
            float inset;
            switch (status) {
                case CORRECT -> {
                    color = IN_PLACE;
                    inset = -0.004f;
                }
                case WRONG -> {
                    color = TO_REMOVE;
                    inset = -0.006f;
                }
                case PARTIAL -> {
                    color = TO_FINISH;
                    inset = -0.005f;
                }
                default -> {
                    color = TO_PLACE;
                    inset = 0.06f;
                }
            }
            ghosts.add(ghost(minecraft, pos, camera, expect.display(), color, inset));
        }
    }

    /** Un plan tenu, pas encore pose : ou il irait sur le bloc vise. */
    private static void preview(Minecraft minecraft, LocalPlayer player, Vec3 camera, List<Ghost> ghosts) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(held.getItem() instanceof PlanItem) || player.isShiftKeyDown()) {
            return;
        }
        PlanData data = PlanItem.data(held);
        if (data.anchor().isPresent()) {
            return;
        }
        HitResult hit = minecraft.hitResult;
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos anchor = block.getBlockPos().relative(block.getDirection());
        int quarter = player.getDirection().get2DDataValue();
        Blueprint.Variant variant = data.resolve().variant(data.variant());
        for (Blueprint.Cell cell : variant.cells()) {
            if (!cell.expect().required()) {
                continue;
            }
            BlockPos pos = Blueprint.place(anchor, quarter, cell.x(), cell.y(), cell.z());
            ghosts.add(ghost(minecraft, pos, camera, cell.expect().display(), PREVIEW, 0.08f));
        }
    }

    private static Ghost ghost(Minecraft minecraft, BlockPos pos, Vec3 camera, BlockState display, int color,
                               float inset) {
        TextureAtlasSprite sprite = minecraft.getModelManager().getBlockStateModelSet()
                .getParticleMaterial(display).sprite();
        return new Ghost((float) (pos.getX() - camera.x), (float) (pos.getY() - camera.y),
                (float) (pos.getZ() - camera.z), sprite, color, inset);
    }

    // -- Un cube --------------------------------------------------------------------------

    private static void cube(PoseStack.Pose pose, VertexConsumer buffer, Ghost ghost) {
        float a = ghost.inset();
        float x0 = ghost.x() + a;
        float y0 = ghost.y() + a;
        float z0 = ghost.z() + a;
        float x1 = ghost.x() + 1.0f - a;
        float y1 = ghost.y() + 1.0f - a;
        float z1 = ghost.z() + 1.0f - a;
        TextureAtlasSprite sprite = ghost.sprite();
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        int color = ghost.color();
        // Le haut, le bas, puis les quatre cotes, chacun vu de l'exterieur.
        quad(pose, buffer, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, u0, v0, u1, v1, color, 0, 1, 0);
        quad(pose, buffer, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, u0, v0, u1, v1, color, 0, -1, 0);
        quad(pose, buffer, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, u0, v0, u1, v1, color, 0, 0, 1);
        quad(pose, buffer, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, u0, v0, u1, v1, color, 0, 0, -1);
        quad(pose, buffer, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, u0, v0, u1, v1, color, 1, 0, 0);
        quad(pose, buffer, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, u0, v0, u1, v1, color, -1, 0, 0);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1, int color, float nx, float ny, float nz) {
        vertex(pose, buffer, ax, ay, az, u0, v0, color, nx, ny, nz);
        vertex(pose, buffer, bx, by, bz, u0, v1, color, nx, ny, nz);
        vertex(pose, buffer, cx, cy, cz, u1, v1, color, nx, ny, nz);
        vertex(pose, buffer, dx, dy, dz, u1, v0, color, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v,
                               int color, float nx, float ny, float nz) {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
    }
}

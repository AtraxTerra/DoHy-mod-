package fr.atraxterra.dohy.client.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;

import java.util.Arrays;

/**
 * Une forme du moteur {@link Zephyr} : des quads, chacun avec son UV (la coordonnee de
 * la forme que lit le shader) et une couleur par sommet, poses dans le repere courant de
 * l'interface. Un element d'interface du jeu : il se range et se coupe comme les autres
 * (ordre, ciseaux), sans texture.
 */
final class ZephyrShape implements GuiElementRenderState {

    /** Par quad : x0, y0, x1, y1, u0, v0, u1, v1. */
    private static final int FLOATS = 8;
    /** Par quad : la couleur des sommets (x0, y0), (x1, y0), (x1, y1), (x0, y1). */
    private static final int COLORS = 4;

    private final RenderPipeline pipeline;
    private final Matrix3x2f pose;
    private final ScreenRectangle scissor;
    private final ScreenRectangle bounds;
    private final float[] quads;
    private final int[] colors;
    private final int count;

    private ZephyrShape(RenderPipeline pipeline, Matrix3x2f pose, ScreenRectangle scissor, ScreenRectangle bounds,
                        float[] quads, int[] colors, int count) {
        this.pipeline = pipeline;
        this.pose = pose;
        this.scissor = scissor;
        this.bounds = bounds;
        this.quads = quads;
        this.colors = colors;
        this.count = count;
    }

    static Builder builder(RenderPipeline pipeline) {
        return new Builder(pipeline);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (int quad = 0; quad < count; quad++) {
            int f = quad * FLOATS;
            int c = quad * COLORS;
            float x0 = quads[f];
            float y0 = quads[f + 1];
            float x1 = quads[f + 2];
            float y1 = quads[f + 3];
            float u0 = quads[f + 4];
            float v0 = quads[f + 5];
            float u1 = quads[f + 6];
            float v1 = quads[f + 7];
            // L'ordre des sommets du BlitRenderState du jeu.
            consumer.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(colors[c]);
            consumer.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(colors[c + 3]);
            consumer.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(colors[c + 2]);
            consumer.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(colors[c + 1]);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return pipeline;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public ScreenRectangle scissorArea() {
        return scissor;
    }

    @Override
    public ScreenRectangle bounds() {
        return bounds;
    }

    /** Une forme en construction. */
    static final class Builder {

        private final RenderPipeline pipeline;
        private float[] quads = new float[FLOATS * 9];
        private int[] colors = new int[COLORS * 9];
        private int count;
        private float minX = Float.MAX_VALUE;
        private float minY = Float.MAX_VALUE;
        private float maxX = -Float.MAX_VALUE;
        private float maxY = -Float.MAX_VALUE;

        private Builder(RenderPipeline pipeline) {
            this.pipeline = pipeline;
        }

        /** Un quad d'une seule couleur. */
        Builder quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int color) {
            return quad(x0, y0, x1, y1, u0, v0, u1, v1, color, color, color, color);
        }

        /**
         * Un quad : ses coins, l'UV de ses coins (0, 0) et (1, 1), et la couleur de chaque
         * sommet, dans l'ordre (x0, y0), (x1, y0), (x1, y1), (x0, y1).
         */
        Builder quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                     int c00, int c10, int c11, int c01) {
            if (x1 <= x0 || y1 <= y0) {
                return this;
            }
            if ((count + 1) * FLOATS > quads.length) {
                quads = Arrays.copyOf(quads, quads.length * 2);
                colors = Arrays.copyOf(colors, colors.length * 2);
            }
            int f = count * FLOATS;
            quads[f] = x0;
            quads[f + 1] = y0;
            quads[f + 2] = x1;
            quads[f + 3] = y1;
            quads[f + 4] = u0;
            quads[f + 5] = v0;
            quads[f + 6] = u1;
            quads[f + 7] = v1;
            int c = count * COLORS;
            colors[c] = c00;
            colors[c + 1] = c10;
            colors[c + 2] = c11;
            colors[c + 3] = c01;
            count++;
            minX = Math.min(minX, x0);
            minY = Math.min(minY, y0);
            maxX = Math.max(maxX, x1);
            maxY = Math.max(maxY, y1);
            return this;
        }

        boolean isEmpty() {
            return count == 0;
        }

        /** Pose la forme dans l'interface, dans le repere et sous les ciseaux du moment. */
        void submit(GuiGraphicsExtractor graphics) {
            if (count == 0) {
                return;
            }
            Matrix3x2f pose = new Matrix3x2f(graphics.pose());
            ScreenRectangle scissor = graphics.peekScissorStack();
            int x = (int) Math.floor(minX);
            int y = (int) Math.floor(minY);
            ScreenRectangle area = new ScreenRectangle(x, y, Math.max(1, (int) Math.ceil(maxX) - x),
                    Math.max(1, (int) Math.ceil(maxY) - y)).transformMaxBounds(pose);
            ScreenRectangle bounds = scissor == null ? area : scissor.intersection(area);
            if (bounds == null) {
                // Hors des ciseaux : rien a dessiner.
                return;
            }
            graphics.submitGuiElementRenderState(new ZephyrShape(pipeline, pose, scissor, bounds, quads, colors,
                    count));
        }
    }
}

package fr.atraxterra.dohy.client.press;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.atraxterra.dohy.palamod.press.PrintPressBlock;
import fr.atraxterra.dohy.palamod.press.PrintPressBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * La presse a imprimer posee ({@code TileEntityPrintPressRenderer}).
 *
 * <p>Comme l'original : le modele a 1,15 bloc du sol, tourne selon
 * l'orientation, retourne autour de x, a l'echelle 0,05. La plaque d'encre
 * change de texture avec la reserve (neuf etats, une marche toutes les huit
 * encres), les livres vierges s'empilent (un par seize livres, quatre au plus),
 * la plaque et le livre imprime brillent du reflet des objets enchantes.
 *
 * <p><b>Le mouvement.</b> L'original faisait avancer le plateau et les rouleaux a
 * chaque image, donc plus vite sur un ecran rapide. Ici il suit le temps du
 * jeu : tant que la presse imprime, le plateau s'abaisse de 0,3 rad puis
 * remonte, et les rouleaux encreurs basculent de 1,7 rad et reviennent — les
 * amplitudes d'origine — en un cycle d'une seconde et demie.
 *
 * <p>Regarder la presse affiche ses encres et ses livres au-dessus d'elle, comme
 * l'original.
 */
public class PrintPressRenderer implements BlockEntityRenderer<PrintPressBlockEntity, PrintPressRenderer.State> {

    private static final Identifier PRESS = texture("press/printpress");
    private static final Identifier BOOKS = texture("press/pressbooks");
    private static final Identifier[] INK = new Identifier[9];

    static {
        for (int level = 0; level < INK.length; level++) {
            INK[level] = texture("press/inkplate" + level);
        }
    }

    /** Un cycle : 12 ticks de plateau, 18 de rouleaux. */
    private static final int CYCLE = 30;
    private static final int PLATE_TICKS = 12;
    private static final double LABEL_RANGE = 8.0d;

    private final ModelPart[] main;
    private final ModelPart plate;
    private final ModelPart press;
    private final ModelPart rollers;
    private final ModelPart inkPlate;
    private final ModelPart[] emptyBooks;
    private final ModelPart finishedBook;

    public PrintPressRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(PressGeometry.PRESS);
        this.main = parts(root, PressGeometry.PRESS_MAIN);
        this.plate = root.getChild("plate");
        this.press = root.getChild("press");
        this.rollers = root.getChild("inkRollers");
        this.inkPlate = context.bakeLayer(PressGeometry.INK_PLATE).getChild("inkplate");
        ModelPart books = context.bakeLayer(PressGeometry.PRESS_BOOKS);
        this.emptyBooks = parts(books, PressGeometry.EMPTY_BOOKS);
        this.finishedBook = books.getChild("Fbook");
    }

    static Identifier texture(String path) {
        return Identifier.fromNamespaceAndPath("palamod", "textures/block/" + path + ".png");
    }

    static ModelPart[] parts(ModelPart root, String[] names) {
        ModelPart[] parts = new ModelPart[names.length];
        for (int index = 0; index < names.length; index++) {
            parts[index] = root.getChild(names[index]);
        }
        return parts;
    }

    static PoseStack stack(PoseStack.Pose last) {
        PoseStack stack = new PoseStack();
        stack.last().set(last);
        return stack;
    }

    /** L'angle de rendu d'une orientation : 0 au sud, le sens de {@code pressAngle}. */
    static float angle(Direction facing) {
        return -facing.toYRot();
    }

    /** {@code getinkStackSize} : 1 a 7 encres -> 1, 8 a 15 -> 2, ..., 56 et plus -> 8. */
    static int inkLevel(int count) {
        return count <= 0 ? 0 : Math.min(8, count / 8 + 1);
    }

    /** {@code numberBlanks} : un livre par tranche de seize, quatre au plus. */
    static int stacks(int count) {
        return count <= 0 ? 0 : Math.min(4, (count - 1) / 16 + 1);
    }

    /** La presse ou la table est-elle visee de pres par le joueur ? */
    static boolean looked(BlockPos pos, Vec3 camera) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(pos)
                && camera.distanceToSqr(Vec3.atCenterOf(pos)) < LABEL_RANGE * LABEL_RANGE;
    }

    public static final class State extends BlockEntityRenderState {
        float facing;
        int ink;
        boolean plate;
        int books;
        boolean output;
        float pressRot;
        float rollerRot;
        Component inkLabel;
        Component booksLabel;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PrintPressBlockEntity entity, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(entity, state, crumbling);
        Direction facing = entity.getBlockState().hasProperty(PrintPressBlock.FACING)
                ? entity.getBlockState().getValue(PrintPressBlock.FACING) : Direction.SOUTH;
        state.facing = angle(facing);
        int ink = entity.getItem(PrintPressBlockEntity.SLOT_INK).getCount();
        int books = entity.getItem(PrintPressBlockEntity.SLOT_BOOKS).getCount();
        state.ink = inkLevel(ink);
        state.plate = !entity.getItem(PrintPressBlockEntity.SLOT_PLATE).isEmpty();
        state.books = stacks(books);
        state.output = !entity.getItem(PrintPressBlockEntity.SLOT_OUTPUT).isEmpty();
        float lower = 0.0f;
        float swing = 0.0f;
        if (entity.printing() && entity.getLevel() != null) {
            float time = ((entity.getLevel().getGameTime() % CYCLE) + partialTick) % CYCLE;
            if (time < PLATE_TICKS) {
                lower = 0.3f * Mth.sin(Mth.PI * time / PLATE_TICKS);
            } else {
                swing = 1.7f * Mth.sin(Mth.PI * (time - PLATE_TICKS) / (CYCLE - PLATE_TICKS));
            }
        }
        // {@code press.rotateAngleX = 0.5F - move1} ; {@code inkRollers.rotateAngleX = 0.12F + move2}.
        state.pressRot = 0.5f - lower;
        state.rollerRot = 0.12f + swing;
        boolean looked = looked(entity.getBlockPos(), camera);
        state.inkLabel = looked ? Component.translatable("hud.palamod.print_press.ink", ink) : null;
        state.booksLabel = looked ? Component.translatable("hud.palamod.print_press.books", books) : null;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = state.lightCoords;
        int overlay = OverlayTexture.NO_OVERLAY;
        boolean hasPlate = state.plate;
        boolean output = state.output;
        int books = state.books;
        float pressRot = state.pressRot;
        float rollerRot = state.rollerRot;
        pose.pushPose();
        pose.translate(0.5f, 1.15f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(state.facing));
        // Le demi-tour autour de x du rendu de 1.7.10, puis le recentrage : les pieds
        // de la presse sont centres sur z = -3 unites du modele.
        pose.mulPose(Axis.XP.rotationDegrees(180.0f));
        pose.translate(0.0f, 0.0f, 0.15f);
        pose.scale(PressGeometry.PRESS_SCALE, PressGeometry.PRESS_SCALE, PressGeometry.PRESS_SCALE);

        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(INK[state.ink]),
                (last, buffer) -> inkPlate.render(stack(last), buffer, light, overlay));
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(PRESS), (last, buffer) -> {
            PoseStack stack = stack(last);
            for (ModelPart part : main) {
                part.render(stack, buffer, light, overlay);
            }
            if (hasPlate) {
                plate.render(stack, buffer, light, overlay);
            }
            press.xRot = pressRot;
            press.render(stack, buffer, light, overlay);
            rollers.xRot = rollerRot;
            rollers.render(stack, buffer, light, overlay);
        });
        if (books > 0 || output) {
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(BOOKS), (last, buffer) -> {
                PoseStack stack = stack(last);
                for (int index = 0; index < books; index++) {
                    emptyBooks[index].render(stack, buffer, light, overlay);
                }
                if (output) {
                    finishedBook.render(stack, buffer, light, overlay);
                }
            });
        }
        // Le reflet des objets enchantes, sur la plaque gravee et le livre imprime.
        if (hasPlate || output) {
            collector.submitCustomGeometry(pose, RenderTypes.entityGlint(), (last, buffer) -> {
                PoseStack stack = stack(last);
                if (hasPlate) {
                    plate.render(stack, buffer, light, overlay);
                }
                if (output) {
                    finishedBook.render(stack, buffer, light, overlay);
                }
            });
        }
        pose.popPose();

        if (state.inkLabel != null) {
            collector.submitNameTag(pose, new Vec3(0.5d, 1.55d, 0.5d), -10, state.inkLabel, false, light, camera);
            collector.submitNameTag(pose, new Vec3(0.5d, 1.55d, 0.5d), 0, state.booksLabel, false, light, camera);
        }
    }

    /** La presse deborde de son bloc vers le haut : ses rouleaux et ses etiquettes. */
    @Override
    public AABB getRenderBoundingBox(PrintPressBlockEntity entity) {
        BlockPos pos = entity.getBlockPos();
        return new AABB(pos.getX() - 0.5d, pos.getY(), pos.getZ() - 0.5d,
                pos.getX() + 1.5d, pos.getY() + 2.0d, pos.getZ() + 1.5d);
    }
}

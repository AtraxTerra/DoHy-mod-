package fr.atraxterra.dohy.client.press;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.atraxterra.dohy.palamod.press.TypeMachineBlock;
import fr.atraxterra.dohy.palamod.press.TypeMachineBlockEntity;
import fr.atraxterra.dohy.palamod.press.TypesetCost;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
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
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * La table d'ecriture posee ({@code TileEntityTypeMachineRenderer}).
 *
 * <p>Comme l'original : le modele a 0,92 bloc du sol, tourne selon
 * l'orientation, retourne autour de x, a l'echelle 1/48 — le meuble
 * ({@code ModelTypesetBase}), la casse d'imprimeur posee dessus
 * ({@code renderTop}), les plaques d'impression empilees au fond (une couche par
 * seize plaques, quatre au plus), la plaque gravee a droite et le livre enchante
 * a gauche, ces deux-la avec le reflet des objets enchantes.
 *
 * <p>Regarder la table affiche le prix de la gravure et les enchantements du
 * livre (trois au plus), comme l'original.
 */
public class TypeMachineRenderer implements BlockEntityRenderer<TypeMachineBlockEntity, TypeMachineRenderer.State> {

    private static final Identifier TOP = PrintPressRenderer.texture("typeset/typeset_top");
    private static final Identifier BASE = PrintPressRenderer.texture("typeset/typeset_base");
    private static final Identifier BOOK = PrintPressRenderer.texture("typeset/typeset_book");

    private final ModelPart[] top;
    private final ModelPart[][] chases;
    private final ModelPart plate;
    private final ModelPart base;
    private final ModelPart book;

    public TypeMachineRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(PressGeometry.TYPESET);
        this.top = PrintPressRenderer.parts(root, PressGeometry.TYPESET_TOP);
        this.chases = new ModelPart[PressGeometry.CHASE_LAYERS.length][];
        for (int layer = 0; layer < chases.length; layer++) {
            chases[layer] = PrintPressRenderer.parts(root, PressGeometry.CHASE_LAYERS[layer]);
        }
        this.plate = root.getChild("brPlate");
        this.base = context.bakeLayer(PressGeometry.TYPESET_BASE).getChild("base");
        this.book = context.bakeLayer(PressGeometry.TYPESET_BOOK).getChild("book");
    }

    public static final class State extends BlockEntityRenderState {
        float facing;
        int chases;
        boolean plate;
        boolean book;
        final List<Component> labels = new ArrayList<>();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TypeMachineBlockEntity entity, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(entity, state, crumbling);
        Direction facing = entity.getBlockState().hasProperty(TypeMachineBlock.FACING)
                ? entity.getBlockState().getValue(TypeMachineBlock.FACING) : Direction.SOUTH;
        state.facing = PrintPressRenderer.angle(facing);
        state.chases = PrintPressRenderer.stacks(entity.getItem(TypeMachineBlockEntity.SLOT_CHASE).getCount());
        state.plate = !entity.getItem(TypeMachineBlockEntity.SLOT_PLATE).isEmpty();
        ItemStack bookStack = entity.getItem(TypeMachineBlockEntity.SLOT_BOOK);
        state.book = !bookStack.isEmpty();
        state.labels.clear();
        if (state.book && PrintPressRenderer.looked(entity.getBlockPos(), camera)) {
            state.labels.add(Component.translatable("hud.palamod.type_machine.cost", TypesetCost.levels(bookStack))
                    .withStyle(ChatFormatting.WHITE));
            ItemEnchantments stored = bookStack.getOrDefault(DataComponents.STORED_ENCHANTMENTS,
                    ItemEnchantments.EMPTY);
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
                if (state.labels.size() > 3) {
                    break;
                }
                state.labels.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()));
            }
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = state.lightCoords;
        int overlay = OverlayTexture.NO_OVERLAY;
        int chaseLayers = state.chases;
        boolean hasPlate = state.plate;
        boolean hasBook = state.book;
        pose.pushPose();
        pose.translate(0.5f, 0.92f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(state.facing));
        // Le demi-tour autour de x de 1.7.10, puis le recentrage : le meuble va de
        // z = -23 a z = 25 unites, son milieu est a z = 1.
        pose.mulPose(Axis.XP.rotationDegrees(180.0f));
        pose.translate(0.0f, 0.0f, -1.0f / 48.0f);
        pose.scale(PressGeometry.TYPESET_SCALE, PressGeometry.TYPESET_SCALE, PressGeometry.TYPESET_SCALE);

        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(TOP), (last, buffer) -> {
            PoseStack stack = PrintPressRenderer.stack(last);
            for (ModelPart part : top) {
                part.render(stack, buffer, light, overlay);
            }
            for (int layer = 0; layer < chaseLayers; layer++) {
                for (ModelPart part : chases[layer]) {
                    part.render(stack, buffer, light, overlay);
                }
            }
            if (hasPlate) {
                plate.render(stack, buffer, light, overlay);
            }
        });
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(BASE),
                (last, buffer) -> base.render(PrintPressRenderer.stack(last), buffer, light, overlay));
        if (hasBook) {
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(BOOK),
                    (last, buffer) -> book.render(PrintPressRenderer.stack(last), buffer, light, overlay));
        }
        if (hasPlate || hasBook) {
            collector.submitCustomGeometry(pose, RenderTypes.entityGlint(), (last, buffer) -> {
                PoseStack stack = PrintPressRenderer.stack(last);
                if (hasPlate) {
                    plate.render(stack, buffer, light, overlay);
                }
                if (hasBook) {
                    book.render(stack, buffer, light, overlay);
                }
            });
        }
        pose.popPose();

        List<Component> labels = state.labels;
        for (int index = 0; index < labels.size(); index++) {
            collector.submitNameTag(pose, new Vec3(0.5d, 1.25d, 0.5d), (index - labels.size() + 1) * 10,
                    labels.get(index), false, light, camera);
        }
    }

    @Override
    public AABB getRenderBoundingBox(TypeMachineBlockEntity entity) {
        BlockPos pos = entity.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0d, pos.getY() + 2.0d, pos.getZ() + 1.0d);
    }
}

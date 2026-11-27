package fr.atraxterra.dohy.client.agri.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.atraxterra.dohy.agri.machine.BigMachine;
import fr.atraxterra.dohy.agri.machine.BigMachines;
import java.util.EnumMap;
import java.util.Map;
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
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Les pieces mobiles des grandes machines de l'agriculture : la girouette du silo,
 * la meule, l'arbre et l'engrenage du moulin, la roue du petrin et la gueule
 * rougeoyante du four, le volant du biogenerateur, le fouet de la machine a OGM.
 *
 * <p>Le reste de la machine est fait de modeles de bloc, case par case
 * ({@link BigMachine}). Ici, la commande dessine ses pieces mobiles dans le repere
 * de la machine (pixels, y vers le haut, face avant au nord), tourne selon
 * {@code facing}. Elles tournent quand la machine travaille ({@code lit}), ou
 * toujours (girouette, fouet) ; la gueule du four ne se montre qu'en cuisson, a
 * pleine lumiere. Les angles viennent du temps du monde : deux machines voisines
 * tournent de concert, et rien n'est garde entre deux images.
 */
public class BigMachineRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, BigMachineRenderer.State> {

    /** La lumiere pleine (ciel et bloc a 15) : la braise ne depend pas de l'ombre. */
    private static final int FULL_BRIGHT = 0xF000F0;

    private final Map<BigMachine, ModelPart> roots = new EnumMap<>(BigMachine.class);

    public BigMachineRenderer(BlockEntityRendererProvider.Context context) {
        roots.put(BigMachine.SILO, context.bakeLayer(BigMachineGeometry.SILO));
        roots.put(BigMachine.FLOUR_MILL, context.bakeLayer(BigMachineGeometry.FLOUR_MILL));
        roots.put(BigMachine.BREAD_MACHINE, context.bakeLayer(BigMachineGeometry.BREAD_MACHINE));
        roots.put(BigMachine.BIOGENERATOR, context.bakeLayer(BigMachineGeometry.BIOGENERATOR));
        roots.put(BigMachine.GMO_MACHINE, context.bakeLayer(BigMachineGeometry.GMO_MACHINE));
    }

    private static BigMachineGeometry.Spin[] spins(BigMachine machine) {
        return switch (machine) {
            case SILO -> BigMachineGeometry.SILO_SPINS;
            case FLOUR_MILL -> BigMachineGeometry.FLOUR_MILL_SPINS;
            case BREAD_MACHINE -> BigMachineGeometry.BREAD_MACHINE_SPINS;
            case BIOGENERATOR -> BigMachineGeometry.BIOGENERATOR_SPINS;
            case GMO_MACHINE -> BigMachineGeometry.GMO_MACHINE_SPINS;
            case FUEL_PUMP -> new BigMachineGeometry.Spin[0];
        };
    }

    public static final class State extends BlockEntityRenderState {
        BigMachine machine;
        boolean formed;
        boolean lit;
        float facing;
        float time;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(entity, state, crumbling);
        BlockState block = entity.getBlockState();
        state.machine = BigMachine.of(block.getBlock());
        state.formed = state.machine != null && block.hasProperty(BigMachines.FORMED)
                && block.getValue(BigMachines.FORMED);
        state.lit = block.hasProperty(BlockStateProperties.LIT) && block.getValue(BlockStateProperties.LIT);
        Direction facing = block.hasProperty(BigMachines.FACING) ? block.getValue(BigMachines.FACING) : Direction.NORTH;
        state.facing = switch (facing) {
            case EAST -> 90.0f;
            case SOUTH -> 180.0f;
            case WEST -> 270.0f;
            default -> 0.0f;
        };
        long ticks = entity.getLevel() == null ? 0L : entity.getLevel().getGameTime() % 72000L;
        state.time = ticks + partialTick;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.formed || state.machine == null) {
            return;
        }
        ModelPart root = roots.get(state.machine);
        if (root == null) {
            return;
        }
        BigMachine machine = state.machine;
        Identifier texture = Identifier.fromNamespaceAndPath("dohy", "textures/entity/machine/" + machine.id() + ".png");
        pose.pushPose();
        pose.translate(0.5f, 0.0f, 0.5f);
        // le blockstate tourne y de 90 vers l'est (sens horaire vu d'en haut) : l'inverse d'un YP positif
        pose.mulPose(Axis.YP.rotationDegrees(-state.facing));
        pose.translate(-(machine.controllerX() * 16 + 8) / 16.0f, -machine.controllerY(),
                -(machine.controllerZ() * 16 + 8) / 16.0f);
        for (BigMachineGeometry.Spin spin : spins(machine)) {
            if (spin.glow() && !state.lit) {
                continue;
            }
            ModelPart part = root.getChild(spin.part());
            boolean running = spin.always() || state.lit;
            float angle = running ? (float) Math.toRadians((state.time * spin.speed()) % 360.0f) : 0.0f;
            char axis = spin.axis();
            int light = spin.glow() ? FULL_BRIGHT : state.lightCoords;
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(texture), (last, buffer) -> {
                part.xRot = axis == 'x' ? angle : 0.0f;
                part.yRot = axis == 'y' ? angle : 0.0f;
                part.zRot = axis == 'z' ? angle : 0.0f;
                PoseStack stack = new PoseStack();
                stack.last().set(last);
                part.render(stack, buffer, light, OverlayTexture.NO_OVERLAY);
            });
        }
        pose.popPose();
    }

    /** La machine deborde de sa commande : trois blocs autour, toute sa hauteur. */
    @Override
    public AABB getRenderBoundingBox(T entity) {
        BlockPos pos = entity.getBlockPos();
        BigMachine machine = BigMachine.of(entity.getBlockState().getBlock());
        int height = machine == null ? 1 : machine.height();
        return new AABB(pos.getX() - 3, pos.getY(), pos.getZ() - 3, pos.getX() + 4, pos.getY() + height + 1,
                pos.getZ() + 4);
    }
}

package fr.atraxterra.dohy.client.botanist;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.atraxterra.dohy.palamod.botanist.BotanistPotBlockEntity;
import fr.atraxterra.dohy.palamod.botanist.Mutation;
import fr.atraxterra.dohy.palamod.botanist.Need;
import fr.atraxterra.dohy.palamod.botanist.PalaBotanist;
import fr.atraxterra.dohy.palamod.botanist.PotStage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Le pot pose : l'objet du besoin flotte au-dessus de la plante (l'icone de
 * besoin de JobsReborn), et, vise de pres, ce que dirait son viseur
 * (« Bourgeon dans », « Fleurit dans », « Reclame : », « Recoltes restantes »,
 * « Disparait dans », « Hors climat »). La plante elle-meme est dans le modele du
 * bloc.
 */
public class BotanistPotRenderer implements BlockEntityRenderer<BotanistPotBlockEntity, BotanistPotRenderer.State> {

    private static final double LABEL_RANGE = 8.0d;

    private final ItemModelResolver items;

    public BotanistPotRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.itemModelResolver();
    }

    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState icon = new ItemStackRenderState();
        boolean hasIcon;
        float age;
        Component line;
        Component detail;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BotanistPotBlockEntity pot, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(pot, state, crumbling);
        long now = pot.getLevel() == null ? 0L : pot.getLevel().getGameTime();
        state.age = now + partialTick;
        ItemStack icon = icon(pot);
        state.hasIcon = !icon.isEmpty();
        if (state.hasIcon) {
            items.updateForTopItem(state.icon, icon, ItemDisplayContext.GROUND, pot.getLevel(), null, 0);
        } else {
            state.icon.clear();
        }
        boolean looked = looked(pot.getBlockPos(), camera);
        state.line = looked ? line(pot, now) : null;
        state.detail = looked ? detail(pot, now) : null;
    }

    /** L'objet qui repond au besoin : l'arrosoir, la guitare, une plume, une boule, ou l'objet reclame. */
    private static ItemStack icon(BotanistPotBlockEntity pot) {
        Need need = pot.need();
        if (need == null) {
            return ItemStack.EMPTY;
        }
        return switch (need) {
            case WATER -> new ItemStack(PalaBotanist.WATERING_CAN.get());
            case MUSIC -> new ItemStack(PalaBotanist.GUITAR.get());
            case SHAKE -> new ItemStack(Items.FEATHER);
            case BURST -> new ItemStack(Items.SLIME_BALL);
            case ITEM -> new ItemStack(Mutation.item(pot.needItem()));
        };
    }

    private static Component line(BotanistPotBlockEntity pot, long now) {
        Mutation mutation = pot.mutation();
        if (mutation == null || pot.stage() == PotStage.EMPTY) {
            return Component.literal("Pot vide").withStyle(ChatFormatting.GRAY);
        }
        String clock = BotanistPotBlockEntity.clock(pot.remaining(now));
        return switch (pot.stage()) {
            case SEED -> Component.literal(mutation.label() + " · bourgeon dans " + clock)
                    .withStyle(ChatFormatting.GREEN);
            case BUD -> Component.literal(mutation.label() + " · fleur dans " + clock)
                    .withStyle(ChatFormatting.GREEN);
            case BLOOMING -> Component.literal(mutation.label() + " · en fleur, a recolter")
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
            case WILTED -> Component.literal(mutation.label() + " fanee · disparait dans " + clock)
                    .withStyle(ChatFormatting.GOLD);
            default -> Component.literal(mutation.label()).withStyle(ChatFormatting.GRAY);
        };
    }

    private static Component detail(BotanistPotBlockEntity pot, long now) {
        if (pot.mutation() == null || pot.stage() == PotStage.EMPTY) {
            return null;
        }
        if (pot.need() != null) {
            return Component.literal("Reclame : ").append(pot.needText()).append(" · "
                    + BotanistPotBlockEntity.clock(pot.needRemaining(now))).withStyle(ChatFormatting.YELLOW);
        }
        if (pot.outOfClimate() && pot.stage() == PotStage.SEED) {
            return Component.literal("Hors climat : fanera au bourgeon").withStyle(ChatFormatting.RED);
        }
        if (!pot.active() && pot.stage() != PotStage.WILTED) {
            return Component.literal("En pause : " + pot.ownerName() + " est loin").withStyle(ChatFormatting.DARK_GRAY);
        }
        return switch (pot.stage()) {
            case BLOOMING -> Component.literal("Recoltes restantes : " + pot.harvestsLeft() + " · fane dans "
                    + BotanistPotBlockEntity.clock(pot.remaining(now))).withStyle(ChatFormatting.LIGHT_PURPLE);
            case WILTED -> Component.literal("Clic droit : recuperer les graines").withStyle(ChatFormatting.GOLD);
            default -> null;
        };
    }

    private static boolean looked(BlockPos pos, Vec3 camera) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(pos)
                && camera.distanceToSqr(Vec3.atCenterOf(pos)) < LABEL_RANGE * LABEL_RANGE;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = state.lightCoords;
        if (state.hasIcon) {
            pose.pushPose();
            float bob = (float) Math.sin(state.age / 8.0f) * 0.05f;
            pose.translate(0.5f, 1.65f + bob, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(state.age * 3.0f));
            pose.scale(0.5f, 0.5f, 0.5f);
            state.icon.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        if (state.line != null) {
            collector.submitNameTag(pose, new Vec3(0.5d, 2.3d, 0.5d), 0, state.line, false, light, camera);
        }
        if (state.detail != null) {
            collector.submitNameTag(pose, new Vec3(0.5d, 2.05d, 0.5d), 0, state.detail, false, light, camera);
        }
    }

    @Override
    public AABB getRenderBoundingBox(BotanistPotBlockEntity pot) {
        BlockPos pos = pot.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0d, pos.getY() + 2.5d, pos.getZ() + 1.0d);
    }
}

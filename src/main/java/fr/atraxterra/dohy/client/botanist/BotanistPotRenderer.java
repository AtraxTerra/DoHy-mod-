package fr.atraxterra.dohy.client.botanist;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.atraxterra.dohy.client.rpg.BedrockModel;
import fr.atraxterra.dohy.palamod.botanist.BotanistPotBlockEntity;
import fr.atraxterra.dohy.palamod.botanist.Mutation;
import fr.atraxterra.dohy.palamod.botanist.Need;
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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Le pot pose, avec les modeles de JobsReborn : le pot, et la plante dans
 * l'etat de son stade (graine, bourgeon, fleur, fanee), animee — l'animation
 * d'attente de l'etat, ou celle du passage d'un etat au suivant quand il vient
 * d'arriver.
 *
 * <p>Au-dessus, l'icone du besoin sur le losange du kit du botaniste, tournee
 * vers le joueur ; et, vise de pres, ce que dirait le viseur de JobsReborn
 * (« Bourgeon dans », « Fleurit dans », « Reclame : », « Recoltes restantes »,
 * « Disparait dans », « Hors climat »).
 *
 * <p>Une fleur qui a mute (lot 2b) prend la terre cuite mutee du jar, et le pot montre ses
 * anneaux et ses etincelles, animes par son animation d'attente.
 */
public class BotanistPotRenderer implements BlockEntityRenderer<BotanistPotBlockEntity, BotanistPotRenderer.State> {

    private static final double LABEL_RANGE = 8.0d;
    /** La plante pousse dans le terreau, a 10 pixels du fond du pot. */
    private static final float SOIL = 10.0f / 16.0f;
    /** L'icone du besoin flotte au-dessus de la plante : au plus bas ici, plus haut pour les grandes. */
    private static final float NEED_Y = 1.95f;
    /**
     * La hauteur de la plante a son plus grand etat, en pixels au-dessus du terreau (mesuree sur les
     * modeles du jar) : l'icone du besoin et l'etiquette passent au-dessus.
     */
    private static final Map<Mutation, Integer> HEIGHTS = Map.ofEntries(
            Map.entry(Mutation.IRONWHEAT, 14), Map.entry(Mutation.MOONFROST, 14), Map.entry(Mutation.SAND_CORAL, 18),
            Map.entry(Mutation.GLOWBLOOM, 17), Map.entry(Mutation.PUFFBLOOM, 20), Map.entry(Mutation.JADE_MAW, 23),
            Map.entry(Mutation.MOTHERROOT, 26), Map.entry(Mutation.THORNWICK, 16), Map.entry(Mutation.SONGLILY, 25),
            Map.entry(Mutation.AURELYS, 18), Map.entry(Mutation.ARCANIUM, 14), Map.entry(Mutation.SUNBLOOM, 18),
            Map.entry(Mutation.SMOKEPOD, 20), Map.entry(Mutation.MIASMA_BLOOM, 27), Map.entry(Mutation.EMBER_ORCHID, 25));
    private static final float BADGE = 0.24f;
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Le fond anthracite et les filets de Paladium-UI : vert, puis rouge dans les vingt dernieres secondes. */
    private static final int BADGE_FILL = 0xFF1E1D23;
    private static final int BADGE_CALM = 0xFF25E251;
    private static final int BADGE_URGENT = 0xFFEF3926;
    private static final int URGENT_TICKS = 20 * 20;
    private static final Identifier BADGE_TEXTURE = BotanistModels.id("textures/botanist/need/badge.png");
    private static final Identifier OUTLINE_TEXTURE = BotanistModels.id("textures/botanist/need/badge_outline.png");
    /** Les objets reclames qui ont une icone de besoin dans JobsReborn. */
    private static final Map<String, String> ITEM_ICONS = Map.of(
            "minecraft:coal", "coal",
            "minecraft:bone_meal", "bone_meal",
            "minecraft:glowstone_dust", "glowstone",
            "minecraft:gold_nugget", "gold_nugget",
            "minecraft:ice", "ice",
            "minecraft:sand", "sand",
            "palamod:paladium_ingot", "paladium");

    private final ItemModelResolver items;

    public BotanistPotRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.itemModelResolver();
    }

    public static final class State extends BlockEntityRenderState {
        Mutation mutation;
        PotStage stage = PotStage.EMPTY;
        boolean mutated;
        /** La hauteur de l'icone du besoin, en blocs ; l'etiquette passe au-dessus. */
        float top = NEED_Y;
        /** L'animation a jouer et ou on en est, en secondes. */
        String clip;
        float clipSeconds;
        Identifier needIcon;
        final ItemStackRenderState needItem = new ItemStackRenderState();
        boolean hasNeedItem;
        boolean urgent;
        float facing;
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
        state.stage = pot.stage();
        state.mutation = pot.stage() == PotStage.EMPTY ? null : pot.mutation();
        state.mutated = pot.mutated();
        state.top = state.mutation == null ? NEED_Y
                : Math.max(NEED_Y, (10.0f + HEIGHTS.getOrDefault(state.mutation, 18) + 7.0f) / 16.0f);
        pot.trackStage(now);
        state.clip = null;
        if (state.mutation != null) {
            int index = index(state.stage);
            float since = (now - pot.seenSince() + partialTick) / 20.0f;
            String evolve = "evolve_" + (index - 1) + "_to_" + index;
            BedrockModel plant = BotanistModels.plant(state.mutation);
            if (plant != null && index(pot.seenFrom()) == index - 1 && since < plant.length(evolve)) {
                state.clip = evolve;
                state.clipSeconds = since;
            } else {
                state.clip = "idle_state_" + index;
                state.clipSeconds = state.age / 20.0f;
            }
        }
        Vec3 center = Vec3.atCenterOf(pot.getBlockPos());
        state.facing = (float) Math.toDegrees(Math.atan2(camera.x - center.x, camera.z - center.z));
        needIcon(pot, state, now);
        boolean looked = looked(pot.getBlockPos(), camera);
        state.line = looked ? line(pot, now) : null;
        state.detail = looked ? detail(pot, now) : null;
    }

    /** SEED, BUD, BLOOMING, WILTED : les etats 1 a 4 des modeles de JobsReborn. */
    private static int index(PotStage stage) {
        return switch (stage) {
            case SEED -> 1;
            case BUD -> 2;
            case BLOOMING -> 3;
            case WILTED -> 4;
            default -> 0;
        };
    }

    private void needIcon(BotanistPotBlockEntity pot, State state, long now) {
        Need need = pot.need();
        state.needIcon = null;
        state.hasNeedItem = false;
        state.needItem.clear();
        if (need == null) {
            return;
        }
        state.urgent = pot.needRemaining(now) <= URGENT_TICKS;
        String icon = switch (need) {
            case WATER -> "water";
            case MUSIC -> "music";
            case SHAKE -> "shake";
            case BURST -> "burst";
            case NIBBLER -> "nibbler";
            case ITEM -> ITEM_ICONS.get(pot.needItem());
        };
        if (icon != null) {
            state.needIcon = BotanistModels.id("textures/botanist/need/" + icon + ".png");
            return;
        }
        // Un objet sans icone dans JobsReborn (la boule de neige) : l'objet lui-meme.
        items.updateForTopItem(state.needItem, new ItemStack(Mutation.item(pot.needItem())), ItemDisplayContext.GROUND,
                pot.getLevel(), null, 0);
        state.hasNeedItem = true;
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
            case BUD -> Component.literal(mutation.label() + " · fleurit dans " + clock)
                    .withStyle(ChatFormatting.GREEN);
            case BLOOMING -> pot.mutated()
                    ? Component.literal(mutation.label() + " · a mute ! a recolter").withStyle(ChatFormatting.AQUA)
                    : Component.literal(mutation.label() + " · en fleur, a recolter")
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
        pose.pushPose();
        // Le repere des modeles Bedrock du moteur des monstres : retourne, sol a 24 pixels.
        pose.translate(0.5f, 0.0f, 0.5f);
        pose.scale(-1.0f, -1.0f, 1.0f);
        pose.translate(0.0f, -1.501f, 0.0f);
        BedrockModel potModel = BotanistModels.pot();
        if (potModel != null) {
            boolean mutated = state.mutated;
            float seconds = state.age / 20.0f;
            Identifier texture = mutated ? BotanistModels.POT_MUTATED_TEXTURE : BotanistModels.POT_TEXTURE;
            collector.submitCustomGeometry(pose, potModel.renderType(texture), (last, buffer) -> {
                potModel.resetPose();
                potModel.setVisible("pot_body", true);
                potModel.setVisibleByPrefix("particles_", false);
                potModel.draw(last, buffer, light, OverlayTexture.NO_OVERLAY);
            });
            if (mutated) {
                // Les anneaux et etincelles d'une fleur qui a mute : animes, en pleine lumiere.
                collector.submitCustomGeometry(pose, potModel.renderType(texture), (last, buffer) -> {
                    potModel.resetPose();
                    potModel.setVisible("pot_body", false);
                    potModel.setVisibleByPrefix("particles_", true);
                    potModel.animate("idle", seconds);
                    potModel.draw(last, buffer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    potModel.setVisible("pot_body", true);
                    potModel.setVisibleByPrefix("particles_", false);
                });
            }
        }
        if (state.mutation != null && state.clip != null) {
            BedrockModel plant = BotanistModels.plant(state.mutation);
            if (plant != null) {
                String clip = state.clip;
                float seconds = state.clipSeconds;
                pose.pushPose();
                // y est retourne : remonter de la hauteur du terreau.
                pose.translate(0.0f, -SOIL, 0.0f);
                collector.submitCustomGeometry(pose, plant.renderType(BotanistModels.texture(state.mutation)),
                        (last, buffer) -> {
                            plant.resetPose();
                            plant.animate(clip, seconds);
                            plant.draw(last, buffer, light, OverlayTexture.NO_OVERLAY);
                        });
                pose.popPose();
            }
        }
        pose.popPose();

        if (state.needIcon != null || state.hasNeedItem) {
            pose.pushPose();
            float bob = (float) Math.sin(state.age / 10.0f) * 0.04f;
            pose.translate(0.5f, state.top + bob, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(state.facing));
            if (state.needIcon != null) {
                Identifier icon = state.needIcon;
                int line = state.urgent ? BADGE_URGENT : BADGE_CALM;
                collector.submitCustomGeometry(pose, RenderTypes.entityCutout(BADGE_TEXTURE),
                        (last, buffer) -> quad(last, buffer, BADGE, 0.0f, BADGE_FILL));
                collector.submitCustomGeometry(pose, RenderTypes.entityCutout(OUTLINE_TEXTURE),
                        (last, buffer) -> quad(last, buffer, BADGE, 0.002f, line));
                collector.submitCustomGeometry(pose, RenderTypes.entityCutout(icon),
                        (last, buffer) -> quad(last, buffer, BADGE * 0.5f, 0.004f, -1));
            } else {
                pose.scale(0.4f, 0.4f, 0.4f);
                state.needItem.submit(pose, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            }
            pose.popPose();
        }
        double labelY = state.top + 0.4d;
        if (state.line != null) {
            collector.submitNameTag(pose, new Vec3(0.5d, labelY, 0.5d), -10, state.line, false, light, camera);
        }
        if (state.detail != null) {
            collector.submitNameTag(pose, new Vec3(0.5d, labelY, 0.5d), 0, state.detail, false, light, camera);
        }
    }

    /** Un carre centre, tourne vers +z (le joueur), en pleine lumiere. */
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float half, float z, int color) {
        vertex(pose, buffer, -half, -half, z, 0.0f, 1.0f, color);
        vertex(pose, buffer, half, -half, z, 1.0f, 1.0f, color);
        vertex(pose, buffer, half, half, z, 1.0f, 0.0f, color);
        vertex(pose, buffer, -half, half, z, 0.0f, 0.0f, color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v,
                               int color) {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
    }

    @Override
    public AABB getRenderBoundingBox(BotanistPotBlockEntity pot) {
        BlockPos pos = pot.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0d, pos.getY() + 3.2d, pos.getZ() + 1.0d);
    }
}

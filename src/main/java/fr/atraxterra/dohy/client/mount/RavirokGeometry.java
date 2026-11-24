package fr.atraxterra.dohy.client.mount;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERE par tools/bedrock_model.py depuis ravirok.geo.json et ravirok.animation.json.
 * Ne pas modifier a la main : relancer le script.
 */
public final class RavirokGeometry {

    private RavirokGeometry() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition All = root.addOrReplaceChild("All", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, 24F, 0F, 0F, 0F, 0F));
        PartDefinition body = All.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-8F, -12F, -27F, 16F, 12F, 32F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -11F, 14F, 0F, 0F, 0F));
        PartDefinition feathers = body.addOrReplaceChild("feathers", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-6F, -12F, 5F, 0F, 0F, 0F));
        PartDefinition bone = feathers.addOrReplaceChild("bone", CubeListBuilder.create(),
                PartPose.offsetAndRotation(6F, 0F, 0F, 0F, 0F, 0F));
        bone.addOrReplaceChild("bone_cube0", CubeListBuilder.create().texOffs(64, 14).addBox(-3F, 0F, 0F, 6F, 1F, 12F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.5236F, 0F, 0F));
        PartDefinition bone2 = feathers.addOrReplaceChild("bone2", CubeListBuilder.create(),
                PartPose.offsetAndRotation(12F, 0F, 0F, 0F, 0F, 0F));
        bone2.addOrReplaceChild("bone2_cube0", CubeListBuilder.create().texOffs(64, 14).addBox(-4F, 0F, -2F, 6F, 1F, 12F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0.5236F, 0.3927F, 0F));
        PartDefinition bone3 = feathers.addOrReplaceChild("bone3", CubeListBuilder.create(),
                PartPose.offsetAndRotation(12F, 0F, 0F, 0F, 0F, 0F));
        bone3.addOrReplaceChild("bone3_cube0", CubeListBuilder.create().texOffs(64, 14).addBox(-2F, 0F, -2F, 6F, 1F, 12F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-12F, 0F, 0F, 0.5236F, -0.3927F, 0F));
        PartDefinition rightWing = body.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 66).addBox(0F, 0F, -11.5F, 24F, 1F, 21F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(8F, -10F, -13.5F, 0F, 0F, 1.021F));
        PartDefinition rightFrontWing = rightWing.addOrReplaceChild("right_front_wing", CubeListBuilder.create().texOffs(0, 44).addBox(0F, 0F, -11.5F, 24F, 1F, 21F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(24F, 0F, 0F, 0F, 0F, -2.0944F));
        PartDefinition leftWing = body.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 66).mirror().addBox(-24F, 0F, -11.5F, 24F, 1F, 21F, new CubeDeformation(0F)).mirror(false),
                PartPose.offsetAndRotation(-8F, -10F, -13.5F, 0F, 0F, -1.021F));
        PartDefinition leftFrontWing = leftWing.addOrReplaceChild("left_front_wing", CubeListBuilder.create().texOffs(0, 44).mirror().addBox(-24F, 0F, -11.5F, 24F, 1F, 21F, new CubeDeformation(0F)).mirror(false),
                PartPose.offsetAndRotation(-24F, 0F, 0F, 0F, 0F, 2.0944F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 108).addBox(-8F, -5F, -17F, 16F, 10F, 10F, new CubeDeformation(0F)).texOffs(0, 88).addBox(-5F, -2F, -25F, 10F, 6F, 8F, new CubeDeformation(0F)).texOffs(0, 0).addBox(-4F, -3F, -7F, 8F, 6F, 7F, new CubeDeformation(0F)).texOffs(80, 0).addBox(-3F, 2F, -22F, 6F, 2F, 5F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -6F, -26F, 0F, 0F, 0F));
        head.addOrReplaceChild("head_cube0", CubeListBuilder.create().texOffs(36, 88).addBox(-2F, -10F, -0.5F, 4F, 10F, 1F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, -5F, -11.5F, -0.2182F, 0F, 0F));
        head.addOrReplaceChild("head_cube1", CubeListBuilder.create().texOffs(46, 88).addBox(-2F, -9F, -0.5F, 4F, 10F, 1F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(3F, -5F, -10.5F, -0.2182F, 0F, 0.3927F));
        head.addOrReplaceChild("head_cube2", CubeListBuilder.create().texOffs(46, 88).mirror().addBox(-2F, -9F, -0.5F, 4F, 10F, 1F, new CubeDeformation(0F)).mirror(false),
                PartPose.offsetAndRotation(-3F, -5F, -10.5F, -0.2182F, 0F, -0.3927F));
        PartDefinition rightLeg = All.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(64, 0).addBox(-2F, -1.025F, -2F, 4F, 10F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(4F, -10.975F, 6F, 0F, 0F, 0F));
        PartDefinition foot = rightLeg.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(80, 0).addBox(-3F, 0F, -3F, 6F, 2F, 5F, new CubeDeformation(0F)).texOffs(108, 7).addBox(-1.5F, 0F, 2F, 3F, 2F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 8.975F, 0F, 0F, 0F, 0F));
        foot.addOrReplaceChild("foot_cube0", CubeListBuilder.create().texOffs(80, 7).addBox(0F, 0F, -3F, 3F, 2F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-3F, 0.025F, -3F, 0F, 0.2618F, 0F));
        foot.addOrReplaceChild("foot_cube1", CubeListBuilder.create().texOffs(94, 7).addBox(-3F, 0F, -3F, 3F, 2F, 4F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(3F, 0.025F, -3F, 0F, -0.2618F, 0F));
        PartDefinition leftLeg = All.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(64, 0).mirror().addBox(-2F, -1.025F, -4F, 4F, 10F, 4F, new CubeDeformation(0F)).mirror(false),
                PartPose.offsetAndRotation(-4F, -10.975F, 8F, 0F, 0F, 0F));
        PartDefinition foot2 = leftLeg.addOrReplaceChild("foot2", CubeListBuilder.create().texOffs(80, 0).mirror().addBox(-3F, 0F, -3F, 6F, 2F, 5F, new CubeDeformation(0F)).mirror(false).texOffs(108, 7).mirror().addBox(-1.5F, 0F, 2F, 3F, 2F, 4F, new CubeDeformation(0F)).mirror(false),
                PartPose.offsetAndRotation(0F, 8.975F, -2F, 0F, 0F, 0F));
        foot2.addOrReplaceChild("foot2_cube0", CubeListBuilder.create().texOffs(80, 7).mirror().addBox(-3F, 0F, -3F, 3F, 2F, 4F, new CubeDeformation(0F)).mirror(false),
                PartPose.offsetAndRotation(3F, 0.025F, -3F, 0F, -0.2618F, 0F));
        foot2.addOrReplaceChild("foot2_cube1", CubeListBuilder.create().texOffs(94, 7).mirror().addBox(0F, 0F, -3F, 3F, 2F, 4F, new CubeDeformation(0F)).mirror(false),
                PartPose.offsetAndRotation(-3F, 0.025F, -3F, 0F, 0.2618F, 0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** {@code animation.ravirok.idle} en boucle. */
    public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(2.5417F).looping()
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.1667F, KeyframeAnimations.degreeVec(0F, 2.5F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.SCALE,
                    new Keyframe(0F, KeyframeAnimations.scaleVec(1F, 1F, 1F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.scaleVec(1.02F, 1.02F, 1F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.5417F, KeyframeAnimations.scaleVec(1F, 1F, 1F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("bone", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-7.5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.8333F, KeyframeAnimations.degreeVec(5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.3333F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("bone2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.degreeVec(15F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0833F, KeyframeAnimations.degreeVec(-5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.3333F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("bone3", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.degreeVec(7.5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.6667F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.3333F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_wing", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4583F, KeyframeAnimations.degreeVec(0F, 0F, -17.5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.6667F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_front_wing", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 62.5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.degreeVec(0F, 0F, 37.5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F, KeyframeAnimations.degreeVec(0F, 0F, 62.5F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_wing", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(0F, 0F, 10F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_front_wing", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, -62.5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(0F, 0F, -50F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(0F, 0F, -62.5F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1F, KeyframeAnimations.degreeVec(0F, 12.5F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0F, -26.67F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.5F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, -7.5F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("foot", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, -15F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 10F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("foot2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 10F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .build();

    /** {@code animation.ravirok.walk} en boucle. */
    public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(2F).looping()
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.degreeVec(5F, -7.5F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(5.012F, 7.4976F, 0.11F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.posVec(0F, -1F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.posVec(0F, -1F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_wing", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(1F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0F, 10F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_wing", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.degreeVec(0F, -20F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.degreeVec(0F, 10F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0F, -17.5F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(-30F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0F, -1F, -4F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("foot", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(30F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(1F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.25F, KeyframeAnimations.degreeVec(-27.5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(1F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.25F, KeyframeAnimations.posVec(0F, -1.7F, -5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("foot2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(1F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.25F, KeyframeAnimations.degreeVec(30F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("All", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.degreeVec(0F, -7.5F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0F, 7.5F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("All", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5F, KeyframeAnimations.posVec(0F, 0F, -3F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.posVec(0F, 0F, -3F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .build();

    /** {@code animation.ravirok.fly} en boucle. */
    public static final AnimationDefinition FLY = AnimationDefinition.Builder.withLength(1.5F).looping()
            .addAnimation("bone", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.875F, KeyframeAnimations.degreeVec(-40F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("bone2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(-45F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("bone3", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(-32.5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_wing", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, -87.5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5417F, KeyframeAnimations.degreeVec(0F, 0F, 5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0F, 0F, -87.5F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_front_wing", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 67.5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(0F, 0F, 205F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0F, 0F, 67.5F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_wing", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 87.5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.5417F, KeyframeAnimations.degreeVec(0F, 0F, -5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0F, 0F, 87.5F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_front_wing", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, -67.5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(0F, 0F, -205F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0F, 0F, -67.5F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(-6F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(54.7684F, 6.1378F, -4.3184F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("foot", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.8333F, KeyframeAnimations.degreeVec(15F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(56.1451F, -6.7938F, 3.1846F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("foot2", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(15F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("All", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1F, KeyframeAnimations.posVec(0F, 6F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .build();
}

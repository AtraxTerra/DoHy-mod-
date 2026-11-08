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
 * GENERE par tools/bedrock_model.py depuis dancarok.geo.json et dancarok.animation.json.
 * Ne pas modifier a la main : relancer le script.
 */
public final class DancarokGeometry {

    private DancarokGeometry() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, -1F, -19F, 0.6109F, 0F, 0F));
        PartDefinition skull = head.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 55).addBox(-13F, -6F, -13F, 26F, 12F, 26F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 0F, -7F, 0F, -0.7854F, 0F));
        skull.addOrReplaceChild("skull_cube0", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -7F, -3F, 6F, 14F, 6F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(7F, -13F, -5F, 0F, -0.7854F, 0F));
        skull.addOrReplaceChild("skull_cube1", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -7F, -3F, 6F, 14F, 6F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-5F, -13F, 7F, 0F, -0.7854F, 0F));
        skull.addOrReplaceChild("skull_cube2", CubeListBuilder.create().texOffs(0, 55).addBox(-3F, -3.5F, -3F, 6F, 7F, 6F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(-7F, -9.5F, -7F, 0F, -0.7854F, 0F));
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, 4.5F, 4F, 0F, 0F, 0F));
        body.addOrReplaceChild("body_cube0", CubeListBuilder.create().texOffs(0, 0).addBox(-14F, -11.5F, -16F, 28F, 23F, 32F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(0F, 1.6438F, -4.1591F, -0.0873F, 0F, 0F));
        PartDefinition frontRightLeg = body.addOrReplaceChild("front_right_leg", CubeListBuilder.create().texOffs(78, 55).addBox(-6F, -3F, -6F, 12F, 14F, 12F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(14F, 8.5F, -17F, 0F, 0F, 0F));
        PartDefinition frontLeftLeg = body.addOrReplaceChild("front_left_leg", CubeListBuilder.create().texOffs(78, 55).mirror().addBox(-6F, -3F, -6F, 12F, 14F, 12F, new CubeDeformation(0F)).mirror(false),
                PartPose.offsetAndRotation(-14F, 8.5F, -17F, 0F, 0F, 0F));
        PartDefinition rightLeg = body.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(88, 0).addBox(-6F, -1F, -6F, 12F, 12F, 12F, new CubeDeformation(0F)),
                PartPose.offsetAndRotation(14F, 8.5F, 9F, 0F, 0F, 0F));
        PartDefinition leftLeg = body.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(88, 0).mirror().addBox(-6F, -1F, -6F, 12F, 12F, 12F, new CubeDeformation(0F)).mirror(false),
                PartPose.offsetAndRotation(-14F, 8.5F, 9F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 256, 256);
    }

    /** {@code animation.dancarok.walking} en boucle. */
    public static final AnimationDefinition WALKING = AnimationDefinition.Builder.withLength(1.24F).looping()
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 2.5F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.28F, KeyframeAnimations.degreeVec(-7.5F, 0.33F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.64F, KeyframeAnimations.degreeVec(0F, -2.5F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.96F, KeyframeAnimations.degreeVec(-7.5F, 0.33F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.24F, KeyframeAnimations.degreeVec(0F, 2.5F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.28F, KeyframeAnimations.posVec(0F, 2F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.64F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.96F, KeyframeAnimations.posVec(0F, 2F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.24F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 5F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.28F, KeyframeAnimations.degreeVec(-7.5F, 0.33F, 7.5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.64F, KeyframeAnimations.degreeVec(0F, -5F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.96F, KeyframeAnimations.degreeVec(-7.5F, 0.33F, -7.5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.24F, KeyframeAnimations.degreeVec(0F, 5F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, -5F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.28F, KeyframeAnimations.degreeVec(7.5F, -2.67F, -7.5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.64F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.8F, KeyframeAnimations.degreeVec(15F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.12F, KeyframeAnimations.degreeVec(-45F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.24F, KeyframeAnimations.degreeVec(0F, -5F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_right_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0.1F, -2F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.28F, KeyframeAnimations.posVec(-0.5F, -0.25F, -0.13F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.64F, KeyframeAnimations.posVec(-0.5F, 0.1F, 1F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.96F, KeyframeAnimations.posVec(0.5F, 1F, -0.11F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.24F, KeyframeAnimations.posVec(0F, 0.1F, -2F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.12F, KeyframeAnimations.degreeVec(22.5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.44F, KeyframeAnimations.degreeVec(-45F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.64F, KeyframeAnimations.degreeVec(0F, 5F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.96F, KeyframeAnimations.degreeVec(7.5F, 2.33F, 7.5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.24F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_left_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(-0.5F, 0.1F, 1F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.28F, KeyframeAnimations.posVec(-0.5F, 1F, 0.12F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.64F, KeyframeAnimations.posVec(0F, 0.1F, -2F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.96F, KeyframeAnimations.posVec(0.5F, -0.25F, -0.13F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.24F, KeyframeAnimations.posVec(-0.5F, 0.1F, 1F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.44F, KeyframeAnimations.degreeVec(-35F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.64F, KeyframeAnimations.degreeVec(0F, 5F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.96F, KeyframeAnimations.degreeVec(7.5F, 2.33F, 7.5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.24F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(-0.5F, 0.1F, 1F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.28F, KeyframeAnimations.posVec(-0.5F, 5F, -0.12F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.64F, KeyframeAnimations.posVec(0F, 0.1F, 2F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.96F, KeyframeAnimations.posVec(0.5F, -0.25F, -0.13F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.24F, KeyframeAnimations.posVec(-0.5F, 0.1F, 1F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, -5F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.28F, KeyframeAnimations.degreeVec(7.5F, -2.67F, -7.5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.64F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.12F, KeyframeAnimations.degreeVec(-35F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.24F, KeyframeAnimations.degreeVec(0F, -5F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0.1F, 2F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.28F, KeyframeAnimations.posVec(-0.5F, -0.25F, -0.13F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.64F, KeyframeAnimations.posVec(-0.5F, 0.1F, 1F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.96F, KeyframeAnimations.posVec(0.5F, 5F, -0.11F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.24F, KeyframeAnimations.posVec(0F, 0.1F, 2F), AnimationChannel.Interpolations.LINEAR)))
            .build();

    /** {@code animation.dancarok.idle} en boucle. */
    public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(1.16F).looping()
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.56F, KeyframeAnimations.degreeVec(-7.5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.16F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.56F, KeyframeAnimations.posVec(0F, -1F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.16F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.56F, KeyframeAnimations.degreeVec(2.5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.16F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.56F, KeyframeAnimations.posVec(0F, -1F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.16F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.56F, KeyframeAnimations.degreeVec(-2.5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.16F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_right_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.56F, KeyframeAnimations.posVec(1F, 1.75F, -1F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.16F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.56F, KeyframeAnimations.degreeVec(-2.5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.16F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_left_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.56F, KeyframeAnimations.posVec(-1F, 1.75F, -1F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.16F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.56F, KeyframeAnimations.degreeVec(-2.5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.16F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.56F, KeyframeAnimations.posVec(1F, 0.25F, 0.25F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.16F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.56F, KeyframeAnimations.degreeVec(-2.5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.16F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.56F, KeyframeAnimations.posVec(-1F, 0.25F, 0.25F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.16F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .build();

    /** {@code animation.dancarok.special}. */
    public static final AnimationDefinition SPECIAL = AnimationDefinition.Builder.withLength(1.48F)
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.12F, KeyframeAnimations.degreeVec(0F, 20F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.24F, KeyframeAnimations.degreeVec(0F, -20F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.36F, KeyframeAnimations.degreeVec(0F, 7.5F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.48F, KeyframeAnimations.degreeVec(0F, -3F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.56F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.12F, KeyframeAnimations.degreeVec(10F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2F, KeyframeAnimations.degreeVec(-79.72F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.36F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.48F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.36F, KeyframeAnimations.posVec(0F, -1F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.12F, KeyframeAnimations.posVec(0F, -3F, 5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2F, KeyframeAnimations.posVec(0F, 13F, -4F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.36F, KeyframeAnimations.posVec(0F, 5F, 1F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.48F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.SCALE,
                    new Keyframe(0F, KeyframeAnimations.scaleVec(1F, 1F, 1F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.36F, KeyframeAnimations.scaleVec(1.2F, 1.2F, 1.2F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2F, KeyframeAnimations.scaleVec(1.2F, 1.2F, 1.2F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.48F, KeyframeAnimations.scaleVec(1F, 1F, 1F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.36F, KeyframeAnimations.degreeVec(2.5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.12F, KeyframeAnimations.degreeVec(25F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2F, KeyframeAnimations.degreeVec(-5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.28F, KeyframeAnimations.degreeVec(-1.79F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.36F, KeyframeAnimations.degreeVec(-3.99F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.48F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.36F, KeyframeAnimations.posVec(0F, -1F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.12F, KeyframeAnimations.posVec(0F, 2F, 4F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2F, KeyframeAnimations.posVec(0F, 1.56F, -5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.48F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.32F, KeyframeAnimations.degreeVec(-2.5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.12F, KeyframeAnimations.degreeVec(-25F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2F, KeyframeAnimations.degreeVec(5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.28F, KeyframeAnimations.degreeVec(1.79F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.36F, KeyframeAnimations.degreeVec(3.99F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.48F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_right_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.32F, KeyframeAnimations.posVec(1F, 1.75F, -1F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.12F, KeyframeAnimations.posVec(0F, 5.75F, -2F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2F, KeyframeAnimations.posVec(0F, -3.25F, -1F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.28F, KeyframeAnimations.posVec(0F, -1.65F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.36F, KeyframeAnimations.posVec(0F, -1.9F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.48F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.32F, KeyframeAnimations.degreeVec(-2.5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.12F, KeyframeAnimations.degreeVec(-25F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2F, KeyframeAnimations.degreeVec(5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.28F, KeyframeAnimations.degreeVec(1.79F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.36F, KeyframeAnimations.degreeVec(3.99F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.48F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_left_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.32F, KeyframeAnimations.posVec(-1F, 1.75F, -1F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.12F, KeyframeAnimations.posVec(0F, 5.75F, -2F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2F, KeyframeAnimations.posVec(0F, -3.25F, -1F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.28F, KeyframeAnimations.posVec(0F, -1.65F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.36F, KeyframeAnimations.posVec(0F, -1.9F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.48F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.32F, KeyframeAnimations.degreeVec(-2.5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.12F, KeyframeAnimations.degreeVec(-25F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2F, KeyframeAnimations.degreeVec(5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.48F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.32F, KeyframeAnimations.posVec(1F, 0.25F, 0.25F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.12F, KeyframeAnimations.posVec(0F, -6F, -3F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2F, KeyframeAnimations.posVec(0F, -1.42F, -1.33F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.48F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.32F, KeyframeAnimations.degreeVec(-2.5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.12F, KeyframeAnimations.degreeVec(-25F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2F, KeyframeAnimations.degreeVec(5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.48F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.32F, KeyframeAnimations.posVec(-1F, 0.25F, 0.25F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.12F, KeyframeAnimations.posVec(0F, -6F, -3F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.2F, KeyframeAnimations.posVec(0F, -1.42F, -1.33F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.48F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .build();

    /** {@code animation.dancarok.attack}. */
    public static final AnimationDefinition ATTACK = AnimationDefinition.Builder.withLength(0.76F)
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4F, KeyframeAnimations.degreeVec(10F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.52F, KeyframeAnimations.degreeVec(10F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4F, KeyframeAnimations.posVec(0F, -3F, 5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.52F, KeyframeAnimations.posVec(0F, 5F, -5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4F, KeyframeAnimations.degreeVec(25F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.52F, KeyframeAnimations.degreeVec(-10F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4F, KeyframeAnimations.posVec(0F, 2F, 4F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.52F, KeyframeAnimations.posVec(0F, 3.06F, -5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4F, KeyframeAnimations.degreeVec(-25F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.52F, KeyframeAnimations.degreeVec(5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_right_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4F, KeyframeAnimations.posVec(0F, 5.75F, -2F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.52F, KeyframeAnimations.posVec(0F, -3.25F, -1F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4F, KeyframeAnimations.degreeVec(-25F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.52F, KeyframeAnimations.degreeVec(5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_left_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4F, KeyframeAnimations.posVec(0F, 5.75F, -2F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.52F, KeyframeAnimations.posVec(0F, -3.25F, -1F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4F, KeyframeAnimations.degreeVec(-25F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.52F, KeyframeAnimations.degreeVec(10F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4F, KeyframeAnimations.posVec(0F, -6F, -3F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.52F, KeyframeAnimations.posVec(0F, -1.42F, -1.33F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4F, KeyframeAnimations.degreeVec(-25F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.52F, KeyframeAnimations.degreeVec(10F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4F, KeyframeAnimations.posVec(0F, -6F, -3F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.52F, KeyframeAnimations.posVec(0F, -1.42F, -1.33F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.72F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .build();

    /** {@code animation.dancarok.death}. */
    public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(0.16F)
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.16F, KeyframeAnimations.degreeVec(-17.5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.16F, KeyframeAnimations.posVec(0F, -9F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.16F, KeyframeAnimations.degreeVec(5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.16F, KeyframeAnimations.posVec(0F, -6F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.16F, KeyframeAnimations.degreeVec(-90F, -52.5F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_right_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.16F, KeyframeAnimations.posVec(1F, 3.5F, -2F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.16F, KeyframeAnimations.degreeVec(-90F, 52.5F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_left_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.16F, KeyframeAnimations.posVec(-1F, 3.5F, -2F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.16F, KeyframeAnimations.degreeVec(-90F, -52.5F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.16F, KeyframeAnimations.posVec(1F, 0.5F, -2F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.16F, KeyframeAnimations.degreeVec(-90F, 52.5F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.16F, KeyframeAnimations.posVec(-1F, 0.5F, -2F), AnimationChannel.Interpolations.LINEAR)))
            .build();

    /** {@code animation.dancarok.run} en boucle. */
    public static final AnimationDefinition RUN = AnimationDefinition.Builder.withLength(0.96F).looping()
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.28F, KeyframeAnimations.degreeVec(15F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.48F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.68F, KeyframeAnimations.degreeVec(15F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.96F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.28F, KeyframeAnimations.posVec(0F, 2F, -5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.48F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.68F, KeyframeAnimations.posVec(0F, 2F, -5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.96F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.28F, KeyframeAnimations.degreeVec(-5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.68F, KeyframeAnimations.degreeVec(-5F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.96F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.28F, KeyframeAnimations.posVec(0F, 2F, -5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.48F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.68F, KeyframeAnimations.posVec(0F, 2F, -5F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.96F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.28F, KeyframeAnimations.degreeVec(-45F, 0F, -10F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.68F, KeyframeAnimations.degreeVec(45F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.96F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("front_left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.28F, KeyframeAnimations.degreeVec(-45F, 0F, 10F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.68F, KeyframeAnimations.degreeVec(45F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.96F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.28F, KeyframeAnimations.degreeVec(45F, 0F, -10F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.68F, KeyframeAnimations.degreeVec(-45F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.96F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.28F, KeyframeAnimations.degreeVec(-45F, 0F, 10F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.68F, KeyframeAnimations.degreeVec(-45F, 0F, 0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.96F, KeyframeAnimations.degreeVec(0F, 0F, 0F), AnimationChannel.Interpolations.LINEAR)))
            .build();
}

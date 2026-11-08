#!/usr/bin/env python3
"""Convertit un modele Bedrock (.geo.json) et ses animations en code Java 26.2.

    python3 tools/bedrock_model.py <geo.json> <animation.json> <Classe> <paquet> <sortie.java>

Les montures de Paladium (mod Mount) sont dessinees en Bedrock et animees par
Lindworm, la bibliotheque maison de Paladium. Le jeu vanilla sait faire la
meme chose sans dependance : un LayerDefinition pour la geometrie, des
AnimationDefinition pour les images cles. Ce script ecrit les deux.

Conventions (celles de Blockbench, verifiees sur le cochon vanilla, present
dans les deux formats) :
  - os d'un parent : offset(px, 24 - py, pz) ; enfant : (p - parent) avec y
    retourne ;
  - cube : addBox(ox - px, -(oy + sy - py), oz - pz, sx, sy, sz) ;
  - rotations en degres, memes signes ; positions animees par posVec (qui
    retourne y elle-meme), echelles par scaleVec.
Un cube qui a sa propre rotation devient une sous-piece pivotee.
"""
import json
import math
import sys


def f(value):
    text = ("%.4f" % value).rstrip("0").rstrip(".")
    if text in ("-0", ""):
        text = "0"
    return text + "F"


def rad(degrees):
    return f(math.radians(degrees))


def main():
    geo_path, anim_path, cls, package, out = sys.argv[1:6]
    geo = json.load(open(geo_path, encoding="utf-8"))["minecraft:geometry"][0]
    anims = json.load(open(anim_path, encoding="utf-8"))["animations"]
    width = geo["description"]["texture_width"]
    height = geo["description"]["texture_height"]
    bones = {bone["name"]: bone for bone in geo["bones"]}

    lines = []
    emit = lines.append
    emit("    public static LayerDefinition createBodyLayer() {")
    emit("        MeshDefinition mesh = new MeshDefinition();")
    emit("        PartDefinition root = mesh.getRoot();")
    variables = {}

    def cube_line(cube, pivot):
        ox, oy, oz = cube["origin"]
        sx, sy, sz = cube["size"]
        u, v = cube.get("uv", [0, 0])
        inflate = cube.get("inflate", 0)
        mirror = ".mirror()" if cube.get("mirror") else ""
        return ".texOffs(%d, %d)%s.addBox(%s, %s, %s, %s, %s, %s, new CubeDeformation(%s))%s" % (
            u, v, mirror, f(ox - pivot[0]), f(-(oy + sy - pivot[1])), f(oz - pivot[2]),
            f(sx), f(sy), f(sz), f(inflate), ".mirror(false)" if mirror else "")

    order = []
    seen = set()

    def visit(name):
        if name in seen:
            return
        parent = bones[name].get("parent")
        if parent:
            visit(parent)
        seen.add(name)
        order.append(name)

    for name in bones:
        visit(name)

    for name in order:
        bone = bones[name]
        pivot = bone.get("pivot", [0, 0, 0])
        rot = bone.get("rotation", [0, 0, 0])
        parent = bone.get("parent")
        if parent:
            pp = bones[parent].get("pivot", [0, 0, 0])
            offset = (pivot[0] - pp[0], -(pivot[1] - pp[1]), pivot[2] - pp[2])
            owner = variables[parent]
        else:
            offset = (pivot[0], 24 - pivot[1], pivot[2])
            owner = "root"
        var = "".join(part.capitalize() if i else part for i, part in enumerate(name.split("_")))
        variables[name] = var
        plain = [c for c in bone.get("cubes", []) if not c.get("rotation")]
        rotated = [c for c in bone.get("cubes", []) if c.get("rotation")]
        boxes = "".join(cube_line(c, pivot) for c in plain)
        emit('        PartDefinition %s = %s.addOrReplaceChild("%s", CubeListBuilder.create()%s,' % (
            var, owner, name, boxes))
        emit("                PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s));" % (
            f(offset[0]), f(offset[1]), f(offset[2]), rad(rot[0]), rad(rot[1]), rad(rot[2])))
        for index, cube in enumerate(rotated):
            cp = cube.get("pivot", pivot)
            cr = cube["rotation"]
            emit('        %s.addOrReplaceChild("%s_cube%d", CubeListBuilder.create()%s,' % (
                var, name, index, cube_line(cube, cp)))
            emit("                PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s));" % (
                f(cp[0] - pivot[0]), f(-(cp[1] - pivot[1])), f(cp[2] - pivot[2]),
                rad(cr[0]), rad(cr[1]), rad(cr[2])))
    emit("        return LayerDefinition.create(mesh, %d, %d);" % (width, height))
    emit("    }")

    targets = {"rotation": ("ROTATION", "degreeVec"), "position": ("POSITION", "posVec"),
               "scale": ("SCALE", "scaleVec")}

    def vector(value):
        if isinstance(value, dict):
            value = value.get("vector", value.get("post", value.get("pre")))
        if isinstance(value, (int, float)):
            value = [value, value, value]
        return [float(component) for component in value]

    def easing(value):
        name = value.get("easing", value.get("lerp_mode", "linear")) if isinstance(value, dict) else "linear"
        return "CATMULLROM" if "catmull" in str(name).lower() else "LINEAR"

    for anim_name, anim in anims.items():
        short = anim_name.split(".")[-1].upper()
        length = float(anim.get("animation_length", 0))
        builder = "AnimationDefinition.Builder.withLength(%s)" % f(length)
        if anim.get("loop") is True:
            builder += ".looping()"
        emit("")
        emit("    /** {@code %s}%s. */" % (anim_name, " en boucle" if anim.get("loop") is True else ""))
        emit("    public static final AnimationDefinition %s = %s" % (short, builder))
        for bone_name, channels in anim.get("bones", {}).items():
            for channel, frames in channels.items():
                if channel not in targets:
                    continue
                target, factory = targets[channel]
                if not isinstance(frames, dict):
                    frames = {"0.0": frames}
                keys = sorted(frames.items(), key=lambda item: float(item[0]))
                emit('            .addAnimation("%s", new AnimationChannel(AnimationChannel.Targets.%s,' % (
                    bone_name, target))
                entries = []
                for time, value in keys:
                    x, y, z = vector(value)
                    entries.append("                    new Keyframe(%s, KeyframeAnimations.%s(%s, %s, %s), "
                                   "AnimationChannel.Interpolations.%s)" % (
                                       f(float(time)), factory, f(x), f(y), f(z), easing(value)))
                emit(",\n".join(entries) + "))")
        emit("            .build();")

    header = """package %s;

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
 * GENERE par tools/bedrock_model.py depuis %s et %s.
 * Ne pas modifier a la main : relancer le script.
 */
public final class %s {

    private %s() {
    }

""" % (package, geo_path.split("/")[-1], anim_path.split("/")[-1], cls, cls)
    with open(out, "w", encoding="utf-8") as handle:
        handle.write(header + "\n".join(lines) + "\n}\n")
    print("ecrit %s : %d os, %d animations" % (out, len(bones), len(anims)))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Les familiers : geometrie et textures des trois familiers, cage, appat, oeufs, noms.

    python3 -I tools/pet_assets.py [--jar <jar de PalaMod extrait>] [--preview DOSSIER]
    python3 -I tools/pet_assets.py --check

Les trois familiers (Pyrolynx, Mordillon, Feu-follet) sont propres au mod : leurs
boites et leurs couleurs sont decrites ici, et le script en tire
`client/pet/FamiliarGeometry.java`, leur texture (`textures/entity/familiar/`) et
leur portrait pour les menus (`textures/gui/pet/`, 96 x 96, rendu ici en
logiciel). La cage a familier, l'appat et les deux oeufs sont dessines ici aussi.

Le serpent et l'escargot gardent leur modele et leur texture de PalaMod 10.3.0
(`client/pet/SnakeGeometry.java` et `SnailGeometry.java`, traduits par
`tools/modelbase2java.py`) ; `--jar` recopie leurs textures, celles de la
friandise et des potions d'XP de familier, et les sons du serpent.

`--check` (CI) refait tout en memoire et echoue si un fichier genere a change.
Sans Pillow (la CI), il ne verifie que le JSON et le Java.

Repere des modeles : celui des entites (y vers le bas, pieds a y = 24, la tete
vers -z), en pixels.
"""
import io
import json
import math
import os
import shutil
import sys

try:
    import numpy as np
except ImportError:
    np = None

try:
    from PIL import Image
except ImportError:  # la CI n'a pas Pillow : --check verifie alors le JSON et le Java
    Image = None

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "palamod")
DATA = os.path.join(RES, "data", "palamod")
JAVA = os.path.join(ROOT, "src", "main", "java", "fr", "atraxterra", "dohy", "client", "pet",
                    "FamiliarGeometry.java")
NS = "palamod"


def rgb(value, alpha=255):
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


# --------------------------------------------------------------------------------------------
# Les familiers
# --------------------------------------------------------------------------------------------
#
# Une piece : (nom, parent, pivot, rotation en degres, boites). Une boite : (u, v, x, y, z,
# w, h, d, gonfle, miroir, peinture). x, y, z : le coin, depuis le pivot. La peinture nomme
# la matiere de chaque face : {"all": ..., "top": ..., "front": ...}, et des pixels poses
# par dessus ("marks" : face -> [(x, y, couleur)], depuis le coin haut-gauche de la face).

ASH = {"base": rgb("8f8b86"), "light": rgb("bcb6ae"), "dark": rgb("5f5b57")}
EMBER = {"base": rgb("ff7a1f"), "light": rgb("ffb347"), "dark": rgb("c8461a")}
CREAM = {"base": rgb("e9d6ad"), "light": rgb("f7ead0"), "dark": rgb("c8b081")}
BROWN = {"base": rgb("8b5a2b"), "light": rgb("a8743f"), "dark": rgb("64401d")}
SCARF = {"base": rgb("c8302a"), "light": rgb("e0483f"), "dark": rgb("8f1f1a")}
FLAME = {"base": rgb("3fa9f5", 205), "light": rgb("8fd8ff", 215), "dark": rgb("2478c8", 195)}
GLOW = {"base": rgb("a8ecff", 225), "light": rgb("e4fbff", 235), "dark": rgb("6cc9f2", 215)}
SPARK = {"base": rgb("e8fbff", 240), "light": rgb("ffffff", 250), "dark": rgb("bdefff", 235)}

AMBER = rgb("ffc533")
PUPIL = rgb("2a1a10")
WHITE = rgb("ffffff")
NOSE = rgb("2a1b12")
PINK_NOSE = rgb("b8645c")
NAVY = rgb("0b2340", 255)
BLUSH = rgb("e88a7a")


def fur(material, **faces):
    paint = {"all": material}
    paint.update(faces)
    return paint


FAMILIARS = {
    # Un petit lynx gris cendre, debout, pointes d'oreilles et de queue couleur braise.
    "ember_lynx": {
        "label": "Pyrolynx",
        "size": (32, 32),
        "parts": [
            ("body", None, (0.0, 18.0, 0.0), (0.0, 0.0, 0.0), [
                (0, 0, -2.0, 0.0, -3.0, 4, 3, 6, 0.0, False,
                 fur(ASH, bottom=CREAM, marks={"top": [(1, 1, "dark"), (2, 3, "dark"), (1, 5, "dark")]})),
            ]),
            ("head", "body", (0.0, 0.0, -3.0), (0.0, 0.0, 0.0), [
                (0, 9, -2.0, -3.0, -3.0, 4, 3, 3, 0.0, False,
                 fur(ASH, marks={"front": [(0, 1, AMBER), (3, 1, AMBER), (1, 2, "light"), (2, 2, "light")]})),
                (14, 9, -1.0, -1.0, -4.0, 2, 1, 1, 0.0, False,
                 fur(CREAM, marks={"front": [(0, 0, PINK_NOSE), (1, 0, PINK_NOSE)]})),
            ]),
            ("ear_left", "head", (-1.5, -3.0, -1.5), (0.0, 0.0, -10.0), [
                (20, 9, -0.5, -2.0, -0.5, 1, 2, 1, 0.0, False, fur(ASH, top=EMBER, marks={
                    "front": [(0, 0, "ember")], "back": [(0, 0, "ember")], "left": [(0, 0, "ember")],
                    "right": [(0, 0, "ember")]})),
            ]),
            ("ear_right", "head", (1.5, -3.0, -1.5), (0.0, 0.0, 10.0), [
                (20, 9, -0.5, -2.0, -0.5, 1, 2, 1, 0.0, True, fur(ASH, top=EMBER, marks={
                    "front": [(0, 0, "ember")], "back": [(0, 0, "ember")], "left": [(0, 0, "ember")],
                    "right": [(0, 0, "ember")]})),
            ]),
            ("leg_front_left", "body", (-1.0, 3.0, -2.0), (0.0, 0.0, 0.0), [
                (24, 9, -0.5, 0.0, -0.5, 1, 3, 1, 0.0, False, fur(ASH, bottom=CREAM)),
            ]),
            ("leg_front_right", "body", (1.0, 3.0, -2.0), (0.0, 0.0, 0.0), [
                (24, 9, -0.5, 0.0, -0.5, 1, 3, 1, 0.0, True, fur(ASH, bottom=CREAM)),
            ]),
            ("leg_back_left", "body", (-1.0, 3.0, 2.0), (0.0, 0.0, 0.0), [
                (24, 9, -0.5, 0.0, -0.5, 1, 3, 1, 0.0, False, fur(ASH, bottom=CREAM)),
            ]),
            ("leg_back_right", "body", (1.0, 3.0, 2.0), (0.0, 0.0, 0.0), [
                (24, 9, -0.5, 0.0, -0.5, 1, 3, 1, 0.0, True, fur(ASH, bottom=CREAM)),
            ]),
            ("tail", "body", (0.0, 0.5, 3.0), (-50.0, 0.0, 0.0), [
                (0, 15, -0.5, -0.5, 0.0, 1, 1, 5, 0.0, False, fur(ASH, back=EMBER, marks={
                    "top": [(0, 3, "ember"), (0, 4, "ember")], "left": [(3, 0, "ember"), (4, 0, "ember")],
                    "right": [(0, 0, "ember"), (1, 0, "ember")], "bottom": [(0, 3, "ember"), (0, 4, "ember")]})),
            ]),
        ],
    },
    # Un chiot brun et creme, oreilles tombantes, foulard rouge au cou.
    "pup": {
        "label": "Mordillon",
        "size": (32, 32),
        "parts": [
            ("body", None, (0.0, 17.0, 0.0), (0.0, 0.0, 0.0), [
                (0, 0, -2.0, 0.0, -2.5, 4, 4, 5, 0.0, False,
                 fur(BROWN, bottom=CREAM, marks={"front": [(1, 2, "cream"), (2, 2, "cream"), (1, 3, "cream"),
                                                           (2, 3, "cream")]})),
            ]),
            ("scarf", "body", (0.0, 0.0, -2.5), (0.0, 0.0, 0.0), [
                (18, 0, -2.0, -0.5, -1.0, 4, 1, 3, 0.3, False,
                 fur(SCARF, marks={"front": [(1, 0, WHITE)], "top": [(0, 1, WHITE), (3, 2, WHITE)]})),
                (18, 4, -0.5, 0.5, -1.3, 1, 2, 1, 0.0, False, fur(SCARF)),
            ]),
            ("head", "body", (0.0, 0.5, -2.5), (0.0, 0.0, 0.0), [
                (0, 9, -2.0, -4.0, -3.0, 4, 4, 3, 0.0, False,
                 fur(BROWN, marks={"front": [(0, 1, PUPIL), (3, 1, PUPIL), (0, 0, "light"), (3, 0, "light"),
                                             (1, 3, "cream"), (2, 3, "cream")]})),
                (14, 9, -1.0, -2.0, -5.0, 2, 2, 2, 0.0, False,
                 fur(CREAM, marks={"front": [(0, 0, NOSE), (1, 0, NOSE)], "top": [(0, 1, NOSE), (1, 1, NOSE)]})),
            ]),
            ("ear_left", "head", (-2.0, -3.5, -1.5), (0.0, 0.0, 12.0), [
                (22, 9, -1.0, 0.0, -0.5, 1, 3, 1, 0.0, False, fur(BROWN, front=BROWN)),
            ]),
            ("ear_right", "head", (2.0, -3.5, -1.5), (0.0, 0.0, -12.0), [
                (22, 9, 0.0, 0.0, -0.5, 1, 3, 1, 0.0, True, fur(BROWN, front=BROWN)),
            ]),
            ("leg_front_left", "body", (-1.0, 4.0, -1.5), (0.0, 0.0, 0.0), [
                (26, 9, -0.5, 0.0, -0.5, 1, 3, 1, 0.0, False, fur(BROWN, bottom=CREAM, marks={
                    "front": [(0, 2, "cream")]})),
            ]),
            ("leg_front_right", "body", (1.0, 4.0, -1.5), (0.0, 0.0, 0.0), [
                (26, 9, -0.5, 0.0, -0.5, 1, 3, 1, 0.0, True, fur(BROWN, bottom=CREAM, marks={
                    "front": [(0, 2, "cream")]})),
            ]),
            ("leg_back_left", "body", (-1.0, 4.0, 1.5), (0.0, 0.0, 0.0), [
                (26, 9, -0.5, 0.0, -0.5, 1, 3, 1, 0.0, False, fur(BROWN, bottom=CREAM)),
            ]),
            ("leg_back_right", "body", (1.0, 4.0, 1.5), (0.0, 0.0, 0.0), [
                (26, 9, -0.5, 0.0, -0.5, 1, 3, 1, 0.0, True, fur(BROWN, bottom=CREAM)),
            ]),
            ("tail", "body", (0.0, 1.0, 2.5), (-40.0, 0.0, 0.0), [
                (0, 16, -0.5, -0.5, 0.0, 1, 1, 3, 0.0, False, fur(BROWN, back=CREAM)),
            ]),
        ],
    },
    # Une flammeche bleue a petite frimousse, qui flotte en ondulant, deux etincelles autour.
    "wisp": {
        "label": "Feu-follet",
        "size": (32, 32),
        "parts": [
            ("flame", None, (0.0, 19.0, 0.0), (0.0, 0.0, 0.0), [
                (0, 0, -3.0, -2.0, -3.0, 6, 4, 6, 0.0, False,
                 fur(FLAME, front=GLOW, marks={"front": [(1, 1, NAVY), (4, 1, NAVY), (1, 2, NAVY), (4, 2, NAVY),
                                                         (2, 3, "dark"), (3, 3, "dark")]})),
                (0, 10, -2.0, -5.0, -2.0, 4, 3, 4, 0.0, False, fur(GLOW)),
            ]),
            ("tip", "flame", (0.0, -5.0, 0.0), (0.0, 45.0, 0.0), [
                (16, 10, -1.0, -2.0, -1.0, 2, 2, 2, 0.0, False, fur(GLOW, top=SPARK)),
                (24, 10, -0.5, -4.0, -0.5, 1, 2, 1, 0.0, False, fur(SPARK)),
            ]),
            ("sparks", "flame", (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), []),
            ("spark_a", "sparks", (4.5, -3.0, 0.0), (0.0, 0.0, 0.0), [
                (28, 10, -0.5, -0.5, -0.5, 1, 1, 1, 0.0, False, fur(SPARK)),
            ]),
            ("spark_b", "sparks", (-4.5, 0.0, 0.0), (0.0, 0.0, 0.0), [
                (28, 10, -0.5, -0.5, -0.5, 1, 1, 1, 0.0, False, fur(SPARK)),
            ]),
        ],
    },
}


def fmt(value):
    text = repr(float(value))
    if "e" in text or "E" in text:
        text = "%.6f" % value
    return text + "f"


def java_name(part):
    head, *rest = part.split("_")
    return head + "".join(word.capitalize() for word in rest)


def java_source():
    lines = [
        "package fr.atraxterra.dohy.client.pet;",
        "",
        "import net.minecraft.client.model.geom.PartPose;",
        "import net.minecraft.client.model.geom.builders.CubeDeformation;",
        "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
        "import net.minecraft.client.model.geom.builders.LayerDefinition;",
        "import net.minecraft.client.model.geom.builders.MeshDefinition;",
        "import net.minecraft.client.model.geom.builders.PartDefinition;",
        "",
        "/**",
        " * La geometrie des trois familiers, propres au mod : ecrite par",
        " * {@code tools/pet_assets.py}. Ne pas modifier a la main : regenerer.",
        " */",
        "public final class FamiliarGeometry {",
        "",
        "    private FamiliarGeometry() {",
        "    }",
    ]
    for key, familiar in FAMILIARS.items():
        method = "create" + "".join(word.capitalize() for word in key.split("_")) + "Layer"
        lines += [
            "",
            "    /** %s, texture de %d x %d. */" % (familiar["label"], familiar["size"][0], familiar["size"][1]),
            "    public static LayerDefinition %s() {" % method,
            "        MeshDefinition mesh = new MeshDefinition();",
            "        PartDefinition root = mesh.getRoot();",
        ]
        for name, parent, pivot, rotation, boxes in familiar["parts"]:
            builder = "CubeListBuilder.create()"
            for (u, v, x, y, z, w, h, d, inflate, mirror, _paint) in boxes:
                builder += "\n                .texOffs(%d, %d)%s.addBox(%s, %s, %s, %s, %s, %s, new CubeDeformation(%s))" % (
                    u, v, ".mirror()" if mirror else ".mirror(false)", fmt(x), fmt(y), fmt(z), fmt(w), fmt(h),
                    fmt(d), fmt(inflate))
            rx, ry, rz = (math.radians(a) for a in rotation)
            if rotation == (0.0, 0.0, 0.0):
                pose = "PartPose.offset(%s, %s, %s)" % tuple(fmt(c) for c in pivot)
            else:
                pose = "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % (
                    tuple(fmt(c) for c in pivot) + (fmt(round(rx, 4)), fmt(round(ry, 4)), fmt(round(rz, 4))))
            target = "root" if parent is None else java_name(parent)
            lines.append("        PartDefinition %s = %s.addOrReplaceChild(\"%s\", %s,\n                %s);" % (
                java_name(name), target, name, builder, pose))
        lines += [
            "        return LayerDefinition.create(mesh, %d, %d);" % familiar["size"],
            "    }",
        ]
    lines += ["}", ""]
    return "\n".join(lines)


# --------------------------------------------------------------------------------------------
# Les textures des familiers
# --------------------------------------------------------------------------------------------

def shade(color, factor):
    return tuple(max(0, min(255, int(round(c * factor)))) for c in color[:3]) + (color[3],)


def resolve(color, material):
    if isinstance(color, tuple):
        return color
    if color in ("light", "dark", "base"):
        return material[color]
    if color == "ember":
        return EMBER["base"]
    if color == "cream":
        return CREAM["base"]
    raise ValueError(color)


def face_rects(u, v, w, h, d):
    """Les six faces d'une boite dans sa texture (convention des boites vanilla)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "left": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "right": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def grain(x, y, seed):
    """Un bruit fixe, pour que deux passages donnent la meme texture."""
    value = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)
    return ((value >> 3) % 7) / 6.0


def paint_familiar(key):
    familiar = FAMILIARS[key]
    image = Image.new("RGBA", familiar["size"], (0, 0, 0, 0))
    pixels = image.load()
    seed = sum(ord(c) for c in key)
    for name, _parent, _pivot, _rotation, boxes in familiar["parts"]:
        for (u, v, _x, _y, _z, w, h, d, _inflate, _mirror, paint) in boxes:
            for face, (fx, fy, fw, fh) in face_rects(u, v, w, h, d).items():
                material = paint.get(face, paint["all"])
                light = {"top": 1.08, "bottom": 0.78}.get(face, 1.0)
                for dy in range(fh):
                    for dx in range(fw):
                        noise = grain(fx + dx, fy + dy, seed)
                        color = material["light"] if noise > 0.86 else material["dark"] if noise < 0.12 \
                            else material["base"]
                        # Un leger degrade vers le bas des flancs.
                        if face in ("left", "right", "front", "back") and fh > 1:
                            light2 = light * (1.04 - 0.1 * dy / (fh - 1))
                        else:
                            light2 = light
                        pixels[fx + dx, fy + dy] = shade(color, light2)
                for (mx, my, color) in paint.get("marks", {}).get(face, []):
                    if 0 <= mx < fw and 0 <= my < fh:
                        pixels[fx + mx, fy + my] = resolve(color, material)
    return image


# --------------------------------------------------------------------------------------------
# Le portrait : la geometrie rendue en logiciel (projection orthographique, tampon de
# profondeur), vue de trois quarts.
# --------------------------------------------------------------------------------------------

def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def part_transforms(parts, overrides=None):
    """La matrice (3 x 3) et la translation de chaque piece, comme {@code translateAndRotate}."""
    overrides = overrides or {}
    table = {}
    for name, parent, pivot, rotation, _boxes in parts:
        rx, ry, rz = (math.radians(a) for a in overrides.get(name, rotation))
        local = rot_z(rz) @ rot_y(ry) @ rot_x(rx)
        if parent is None:
            table[name] = (local, np.array(pivot, dtype=float))
        else:
            pm, pt = table[parent]
            table[name] = (pm @ local, pt + pm @ np.array(pivot, dtype=float))
    return table


def box_faces(x, y, z, w, h, d, inflate):
    """Les faces d'une boite : (nom, quatre coins dans l'ordre haut-gauche, haut-droit,
    bas-droit, bas-gauche de la texture, normale)."""
    x0, y0, z0 = x - inflate, y - inflate, z - inflate
    x1, y1, z1 = x + w + inflate, y + h + inflate, z + d + inflate
    return [
        ("front", [(x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0)], (0, 0, -1)),
        ("back", [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], (0, 0, 1)),
        ("left", [(x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1)], (1, 0, 0)),
        ("right", [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)], (-1, 0, 0)),
        ("top", [(x1, y0, z1), (x0, y0, z1), (x0, y0, z0), (x1, y0, z0)], (0, -1, 0)),
        ("bottom", [(x1, y1, z0), (x0, y1, z0), (x0, y1, z1), (x1, y1, z1)], (0, 1, 0)),
    ]


def render_model(parts, texture, size=96, yaw=-35.0, pitch=18.0, margin=8, overrides=None):
    """Le modele vu de trois quarts, centre et a l'echelle de l'image."""
    tex = np.asarray(texture.convert("RGBA")).astype(np.float32)
    th, tw = tex.shape[0], tex.shape[1]
    transforms = part_transforms(parts, overrides)
    # Le monde : x vers la droite, y vers le haut, l'oeil du cote des z negatifs : le modele,
    # qui regarde vers -z, nous fait face.
    view = rot_x(math.radians(pitch)) @ rot_y(math.radians(yaw)) @ np.diag([1.0, -1.0, 1.0])
    quads = []
    for name, _parent, _pivot, _rotation, boxes in parts:
        matrix, offset = transforms[name]
        for (u, v, x, y, z, w, h, d, inflate, mirror, _paint) in boxes:
            rects = face_rects(u, v, w, h, d)
            for face, corners, normal in box_faces(x, y, z, w, h, d, inflate):
                points = np.array([view @ (offset + matrix @ np.array(c, dtype=float)) for c in corners])
                world_normal = view @ (matrix @ np.array(normal, dtype=float))
                if world_normal[2] >= -1e-6:
                    continue
                quads.append((points, rects[face], mirror, world_normal))
    if not quads:
        return Image.new("RGBA", (size, size), (0, 0, 0, 0))
    every = np.concatenate([q[0] for q in quads])
    low, high = every.min(axis=0), every.max(axis=0)
    span = max(high[0] - low[0], high[1] - low[1])
    scale = (size - 2 * margin) / span
    center = (low + high) / 2.0
    color = np.zeros((size, size, 4), dtype=np.float32)
    depth = np.full((size, size), np.inf)
    light_dir = np.array([-0.35, 0.8, -0.5])
    light_dir /= np.linalg.norm(light_dir)
    for points, (fx, fy, fw, fh), mirror, normal in quads:
        screen = np.empty((4, 3))
        screen[:, 0] = (points[:, 0] - center[0]) * scale + size / 2.0
        screen[:, 1] = size / 2.0 - (points[:, 1] - center[1]) * scale
        screen[:, 2] = points[:, 2]
        p0, p1, p3 = screen[0], screen[1], screen[3]
        e1, e2 = p1[:2] - p0[:2], p3[:2] - p0[:2]
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-9:
            continue
        n = normal / np.linalg.norm(normal)
        brightness = 0.58 + 0.42 * max(0.0, float(np.dot(n * np.array([1.0, 1.0, -1.0]), light_dir)))
        xs = screen[:, 0]
        ys = screen[:, 1]
        for py in range(max(0, int(math.floor(ys.min()))), min(size, int(math.ceil(ys.max())) + 1)):
            for px in range(max(0, int(math.floor(xs.min()))), min(size, int(math.ceil(xs.max())) + 1)):
                q = np.array([px + 0.5, py + 0.5]) - p0[:2]
                s = (q[0] * e2[1] - q[1] * e2[0]) / det
                t = (e1[0] * q[1] - e1[1] * q[0]) / det
                if s < 0 or s > 1 or t < 0 or t > 1:
                    continue
                z = p0[2] + s * (p1[2] - p0[2]) + t * (p3[2] - p0[2])
                if z >= depth[py, px]:
                    continue
                sx = s if not mirror else 1.0 - s
                tu = min(fw - 1, int(sx * fw))
                tv = min(fh - 1, int(t * fh))
                texel = tex[min(th - 1, fy + tv), min(tw - 1, fx + tu)]
                if texel[3] < 8:
                    continue
                depth[py, px] = z
                color[py, px, :3] = np.clip(texel[:3] * brightness, 0, 255)
                color[py, px, 3] = max(color[py, px, 3], texel[3]) if texel[3] < 255 else 255
    return Image.fromarray(color.astype(np.uint8), "RGBA")


def portrait(key):
    familiar = FAMILIARS[key]
    texture = paint_familiar(key)
    image = render_model(familiar["parts"], texture)
    # Un halo doux sous le familier, pour qu'il ne flotte pas sur le fond du menu.
    halo = Image.new("RGBA", image.size, (0, 0, 0, 0))
    hp = halo.load()
    size = image.size[0]
    for y in range(size):
        for x in range(size):
            dx = (x - size / 2.0) / (size * 0.36)
            dy = (y - size * 0.86) / (size * 0.08)
            r = dx * dx + dy * dy
            if r < 1.0:
                hp[x, y] = (0, 0, 0, int(70 * (1.0 - r)))
    return Image.alpha_composite(halo, image)


# --------------------------------------------------------------------------------------------
# La cage, l'appat et les oeufs
# --------------------------------------------------------------------------------------------

STEEL = rgb("7d8794")
STEEL_LIGHT = rgb("b7c2cf")
STEEL_DARK = rgb("4a525d")
BRASS = rgb("c9a14a")
BRASS_DARK = rgb("8c6a26")
STRAW = rgb("d9b45a")
STRAW_DARK = rgb("a8842f")


def cage_texture():
    """16 x 16 : les barreaux (colonnes 0 a 2), le cadre (lignes 12 a 15), la paille du fond."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = image.load()
    for y in range(16):
        for x in range(16):
            if x < 3 and y < 12:
                px[x, y] = [STEEL_LIGHT, STEEL, STEEL_DARK][x]
            elif y >= 12:
                base = STEEL if (x + y) % 5 else STEEL_DARK
                px[x, y] = STEEL_LIGHT if y == 12 else STEEL_DARK if y == 15 else base
                if y in (13, 14) and x % 5 == 2:
                    px[x, y] = BRASS
            elif x >= 4 and y < 12:
                noise = grain(x, y, 7)
                px[x, y] = STRAW_DARK if noise < 0.3 or (x + 2 * y) % 7 == 0 else STRAW
            else:
                px[x, y] = STEEL_DARK
    return image


def cage_model():
    """La cage : un fond de paille, un cadre, des barreaux, une poignee ; uv calees sur la texture."""
    tex = NS + ":block/pet_cage"
    elements = []

    def element(frm, to, uvs):
        faces = {}
        for face, uv in uvs.items():
            faces[face] = {"uv": uv, "texture": "#cage"}
        elements.append({"from": list(frm), "to": list(to), "faces": faces})

    frame = [0, 12, 16, 15]
    # Le fond et sa paille.
    element((1, 0, 1), (15, 1, 15), {"up": [4, 0, 16, 12], "down": frame, "north": frame, "south": frame,
                                     "east": frame, "west": frame})
    # Le toit.
    element((1, 13, 1), (15, 14, 15), {"up": [0, 12, 16, 15], "down": [0, 12, 16, 15], "north": frame,
                                       "south": frame, "east": frame, "west": frame})
    # Les quatre montants.
    for x, z in ((1, 1), (14, 1), (1, 14), (14, 14)):
        uv = [0, 0, 1, 12]
        element((x, 1, z), (x + 1, 13, z + 1), {"north": uv, "south": uv, "east": uv, "west": uv})
    # Les barreaux, trois par cote.
    bar = [1, 0, 2, 12]
    for offset in (4.5, 7.5, 10.5):
        for frm, to in (((offset, 1, 1.25), (offset + 1, 13, 1.75)), ((offset, 1, 14.25), (offset + 1, 13, 14.75)),
                        ((1.25, 1, offset), (1.75, 13, offset + 1)), ((14.25, 1, offset), (14.75, 13, offset + 1))):
            element(frm, to, {"north": bar, "south": bar, "east": bar, "west": bar})
    # La poignee.
    element((7, 14, 7.5), (9, 15, 8.5), {"up": [0, 12, 2, 13], "north": [0, 12, 2, 13], "south": [0, 12, 2, 13],
                                         "east": [0, 12, 1, 13], "west": [0, 12, 1, 13]})
    element((6, 14, 7.5), (7, 16, 8.5), {"north": [0, 0, 1, 2], "south": [0, 0, 1, 2], "east": [0, 0, 1, 2],
                                         "west": [0, 0, 1, 2], "up": [0, 0, 1, 1]})
    element((9, 14, 7.5), (10, 16, 8.5), {"north": [0, 0, 1, 2], "south": [0, 0, 1, 2], "east": [0, 0, 1, 2],
                                          "west": [0, 0, 1, 2], "up": [0, 0, 1, 1]})
    element((6, 16, 7.5), (10, 16.5, 8.5), {"up": [0, 12, 4, 13], "down": [0, 12, 4, 13], "north": [0, 12, 4, 13],
                                            "south": [0, 12, 4, 13]})
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"cage": tex, "particle": tex}, "elements": elements}


def egg_texture(base, spots, outline):
    """Un oeuf d'apparition, 16 x 16, a la forme ovale, tachete."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = image.load()
    for y in range(16):
        for x in range(16):
            dx = (x + 0.5 - 8.0) / 5.4
            dy = (y + 0.5 - 8.8) / (6.6 if y > 8 else 7.2)
            r = dx * dx + dy * dy
            if r <= 1.0:
                color = base
                if r > 0.72:
                    color = outline
                elif (x * 7 + y * 13) % 11 in (0, 4) and r < 0.6:
                    color = spots
                elif x < 7 and y < 7 and r < 0.35:
                    color = shade(base, 1.18)
                px[x, y] = color
    return image


def bait_texture():
    """L'appat : un hamecon de laiton et un morceau de viande crue, a la main."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = image.load()
    meat = rgb("c2453b")
    meat_light = rgb("e1675a")
    meat_dark = rgb("8e2a24")
    fat = rgb("f2d7c6")
    line = rgb("e8e2d6")
    for y in range(1, 6):
        px[9, y] = line
    hook = [(9, 6), (9, 7), (9, 8), (9, 9), (9, 10), (8, 11), (7, 11), (6, 11), (5, 10), (5, 9), (6, 8)]
    for (x, y) in hook:
        px[x, y] = BRASS
    px[10, 7] = BRASS_DARK
    px[4, 9] = BRASS_DARK
    for y in range(7, 14):
        for x in range(4, 13):
            dx = (x + 0.5 - 8.5) / 4.2
            dy = (y + 0.5 - 10.5) / 3.0
            r = dx * dx + dy * dy
            if r <= 1.0 and (x, y) not in hook:
                color = meat_light if r < 0.25 and x < 9 else meat_dark if r > 0.7 else meat
                if (x + y) % 6 == 0 and r < 0.6:
                    color = fat
                px[x, y] = color
    return image


# --------------------------------------------------------------------------------------------
# Les fichiers
# --------------------------------------------------------------------------------------------

LANG = {
    "fr_fr": {
        "block.palamod.pet_cage": "Cage a familier",
        "item.palamod.pet_bait": "Appat a familier",
        "item.palamod.pet_snack": "Friandise pour familier",
        "item.palamod.pet_xp_small": "Petite potion d'XP de familier",
        "item.palamod.pet_xp_medium": "Moyenne potion d'XP de familier",
        "item.palamod.pet_xp_big": "Grande potion d'XP de familier",
        "item.palamod.snake_spawn_egg": "Oeuf d'apparition de serpent",
        "item.palamod.snail_spawn_egg": "Oeuf d'apparition d'escargot",
        "entity.palamod.snake": "Serpent",
        "entity.palamod.snail": "Escargot",
        "tooltip.palamod.pet_snack": "Clic droit : ton familier la croque (+%s bonheur, quete du jour)",
        "tooltip.palamod.pet_xp": "A boire : +%s XP pour ton familier",
        "tooltip.palamod.pet_bait": "Clic droit sur ta cage a familier : 5 minutes de piege",
        "key.dohy.pet_wheel": "Roue du familier",
        "subtitles.palamod.snake.living": "Le serpent siffle",
        "subtitles.palamod.snake.hurt": "Le serpent est blesse",
        "subtitles.palamod.snake.death": "Le serpent meurt",
    },
    "en_us": {
        "block.palamod.pet_cage": "Pet Cage",
        "item.palamod.pet_bait": "Pet Bait",
        "item.palamod.pet_snack": "Pet Snack",
        "item.palamod.pet_xp_small": "Small Pet XP Potion",
        "item.palamod.pet_xp_medium": "Medium Pet XP Potion",
        "item.palamod.pet_xp_big": "Big Pet XP Potion",
        "item.palamod.snake_spawn_egg": "Snake Spawn Egg",
        "item.palamod.snail_spawn_egg": "Snail Spawn Egg",
        "entity.palamod.snake": "Snake",
        "entity.palamod.snail": "Snail",
        "tooltip.palamod.pet_snack": "Right click: your pet eats it (+%s happiness, daily quest)",
        "tooltip.palamod.pet_xp": "Drink it: +%s XP for your pet",
        "tooltip.palamod.pet_bait": "Right click your pet cage: a 5 minute trap",
        "key.dohy.pet_wheel": "Pet wheel",
        "subtitles.palamod.snake.living": "Snake hisses",
        "subtitles.palamod.snake.hurt": "Snake hurts",
        "subtitles.palamod.snake.death": "Snake dies",
    },
}

# Les fichiers du jar : destination (sous assets/palamod) <- source (sous assets/palamod).
FROM_JAR = {
    "textures/entity/snake.png": "textures/entity/snake.png",
    "textures/entity/snail.png": "textures/entity/snail.png",
    "textures/item/pet_snack.png": "textures/items/pet_snack.png",
    "textures/item/pet_xp_small.png": "textures/items/pet_xp_small.png",
    "textures/item/pet_xp_medium.png": "textures/items/pet_xp_medium.png",
    "textures/item/pet_xp_big.png": "textures/items/pet_xp_big.png",
    "sounds/mob/snake/snake_living.ogg": "sounds/mob/snake/neytoxss/snake_living.ogg",
    "sounds/mob/snake/snake_hurt.ogg": "sounds/mob/snake/neytoxss/snake_hurt.ogg",
    "sounds/mob/snake/snake_death.ogg": "sounds/mob/snake/neytoxss/snake_death.ogg",
}


def json_bytes(data):
    return (json.dumps(data, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def png_bytes(image):
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    return buffer.getvalue()


def outputs(images=True):
    files = {JAVA: java_source().encode("utf-8")}

    def put(path, payload):
        files[os.path.join(RES, path)] = json_bytes(payload)

    # La cage.
    put("assets/%s/models/block/pet_cage.json" % NS, cage_model())
    put("assets/%s/blockstates/pet_cage.json" % NS, {"variants": {
        "facing=%s" % facing: ({"model": NS + ":block/pet_cage", "y": angle} if angle else
                               {"model": NS + ":block/pet_cage"})
        for facing, angle in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    put("assets/%s/items/pet_cage.json" % NS, {"model": {"type": "minecraft:model", "model": NS + ":block/pet_cage"}})
    put("data/%s/loot_table/blocks/pet_cage.json" % NS, {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": NS + ":pet_cage"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # La recette du wiki : quatre batons et quatre lingots de titane, une amethyste compressee.
    put("data/%s/recipe/pet_cage.json" % NS, {
        "type": "minecraft:crafting_shaped", "pattern": ["STS", "TCT", "STS"],
        "key": {"S": NS + ":titane_stick", "T": NS + ":titane_ingot", "C": NS + ":compressed_amethyst"},
        "result": {"id": NS + ":pet_cage", "count": 1}})

    # Les objets plats.
    for item in ("pet_bait", "pet_snack", "pet_xp_small", "pet_xp_medium", "pet_xp_big", "snake_spawn_egg",
                 "snail_spawn_egg"):
        put("assets/%s/models/item/%s.json" % (NS, item),
            {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, item)}})
        put("assets/%s/items/%s.json" % (NS, item),
            {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, item)}})

    # Les biomes : le serpent la nuit dans les pays chauds et humides, l'escargot au frais.
    put("data/%s/tags/worldgen/biome/snake_spawns.json" % NS, {"values": [
        "minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle", "minecraft:swamp",
        "minecraft:mangrove_swamp", "minecraft:desert", "minecraft:savanna", "minecraft:savanna_plateau",
        {"id": "#c:is_jungle", "required": False}, {"id": "#c:is_desert", "required": False},
        {"id": "#c:is_savanna", "required": False}, {"id": "#c:is_swamp", "required": False}]})
    put("data/%s/tags/worldgen/biome/snail_spawns.json" % NS, {"values": [
        "minecraft:forest", "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:flower_forest",
        "minecraft:plains", "minecraft:meadow", "minecraft:swamp", "minecraft:old_growth_birch_forest",
        {"id": "#c:is_forest", "required": False}, {"id": "#c:is_plains", "required": False},
        {"id": "#c:is_swamp", "required": False}]})
    put("data/%s/neoforge/biome_modifier/snake.json" % NS, {
        "type": "neoforge:add_spawns", "biomes": "#%s:snake_spawns" % NS,
        "spawners": {"type": NS + ":snake", "weight": 8, "minCount": 1, "maxCount": 1}})
    put("data/%s/neoforge/biome_modifier/snail.json" % NS, {
        "type": "neoforge:add_spawns", "biomes": "#%s:snail_spawns" % NS,
        "spawners": {"type": NS + ":snail", "weight": 6, "minCount": 1, "maxCount": 2}})

    if images:
        def image(path, img):
            files[os.path.join(ASSETS, "textures", path)] = img
        image("block/pet_cage.png", cage_texture())
        image("item/pet_bait.png", bait_texture())
        image("item/snake_spawn_egg.png", egg_texture(rgb("3f8f2f"), rgb("1f4f17"), rgb("2a5f20")))
        image("item/snail_spawn_egg.png", egg_texture(rgb("ffaebe"), rgb("8a5a3c"), rgb("d67f92")))
        for key in FAMILIARS:
            image("entity/familiar/%s.png" % key, paint_familiar(key))
            image("gui/pet/%s.png" % key, portrait(key))
    return files


def same(path, data):
    if not os.path.exists(path):
        return False
    if isinstance(data, bytes):
        with open(path, "rb") as handle:
            return handle.read() == data
    on_disk = Image.open(path).convert("RGBA")
    return on_disk.size == data.size and on_disk.tobytes() == data.convert("RGBA").tobytes()


def main():
    args = sys.argv[1:]
    check = "--check" in args
    images = Image is not None and np is not None
    if not images and not check:
        raise SystemExit("Pillow et numpy manquent : il les faut pour ecrire les textures.")
    jar = args[args.index("--jar") + 1] if "--jar" in args else None
    files = outputs(images)
    stale = []
    for path, data in files.items():
        if same(path, data):
            continue
        if check:
            stale.append(os.path.relpath(path, ROOT))
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as handle:
            handle.write(data if isinstance(data, bytes) else png_bytes(data))

    for code, labels in LANG.items():
        path = os.path.join(ASSETS, "lang", "%s.json" % code)
        with open(path, encoding="utf-8") as handle:
            lang = json.load(handle)
        missing = {key: value for key, value in labels.items() if lang.get(key) != value}
        if not missing:
            continue
        if check:
            stale.append("%s : %s" % (os.path.relpath(path, ROOT), ", ".join(sorted(missing))))
            continue
        lang.update(missing)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(lang, handle, indent=2, ensure_ascii=False)
            handle.write("\n")

    for target, source in FROM_JAR.items():
        path = os.path.join(ASSETS, target)
        if jar and not check:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            shutil.copyfile(os.path.join(jar, "assets", "palamod", source), path)
        elif not os.path.exists(path):
            stale.append("%s absent (relancer avec --jar)" % os.path.relpath(path, ROOT))

    if stale:
        for line in stale:
            print("::error::familiers : %s n'est pas a jour. Lance 'python3 -I tools/pet_assets.py'." % line)
        sys.exit(1)
    if "--preview" in args and images:
        folder = args[args.index("--preview") + 1]
        os.makedirs(folder, exist_ok=True)
        for key in FAMILIARS:
            big = render_model(FAMILIARS[key]["parts"], paint_familiar(key), size=256, margin=20)
            big.save(os.path.join(folder, key + ".png"))
            paint_familiar(key).resize((256, 256), Image.NEAREST).save(os.path.join(folder, key + "_tex.png"))
        print("apercus :", folder)
    note = "" if images else " (sans Pillow : JSON et Java seulement)"
    print("familiers : %d fichiers a jour%s" % (len(files), note))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Dessine la moissonneuse d'epice d'Arrakis : sa geometrie et sa texture.

Une chenillette de chantier, dessinee pour Berskar boite par boite et reprise
d'aucun film ni d'aucun jeu : deux chenilles, une bouche d'aspiration large de
toute la machine avec son tambour, une cabine a l'avant, la tremie a epice en
caisse derriere, un cyclone qui separe le sable de l'epice, un elevateur a
godets et deux pots d'echappement. Peinture ocre de chantier, patinee de sable.

    python3 -I tools/harvester_model.py [dossier_apercu]

Le script ecrit :

* `client/arrakis/HarvesterGeometry.java` — la geometrie, en code vanilla ;
* `assets/dohy/textures/entity/spice_harvester.png` — la texture, peinte face par face.

Avec un dossier en argument, il y pose aussi des apercus (PIL requis).

Reperes : x vers la droite, y vers le haut depuis le sol, z negatif vers
l'avant, le tout en pixels (16 par bloc). La conversion vers le repere des
modeles de Minecraft (y vers le bas, sol a 24) est faite a l'ecriture.
"""

import math
import os
import random
import struct
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
JAVA = os.path.join(ROOT, "src", "main", "java", "fr", "atraxterra", "dohy", "client", "arrakis",
                    "HarvesterGeometry.java")
PNG = os.path.join(ROOT, "src", "main", "resources", "assets", "dohy", "textures", "entity", "spice_harvester.png")
SIZE = 512
SIZE_H = 256

MATERIALS = {
    "paint": (204, 148, 66),      # ocre de chantier
    "paint_dark": (164, 112, 50),
    "rust": (146, 80, 42),
    "dark": (56, 54, 52),
    "steel": (150, 148, 142),
    "track": (46, 44, 42),
    "tire": (36, 34, 33),
    "hub": (176, 170, 158),
    "glass": (62, 96, 112),
    "light": (255, 196, 72),
    "beacon": (255, 120, 40),
    "pipe": (72, 70, 68),
}
SAND = (222, 188, 128)


class Box:
    def __init__(self, x, y, z, w, h, d, material, key=None):
        self.x, self.y, self.z, self.w, self.h, self.d = x, y, z, w, h, d
        self.material = material
        self.key = key
        self.uv = None


class Part:
    def __init__(self, name, pivot=(0, 0, 0), rot=(0.0, 0.0, 0.0)):
        self.name = name
        self.pivot = pivot
        self.rot = rot
        self.boxes = []
        self.children = []

    def box(self, x, y, z, w, h, d, material, key=None):
        self.boxes.append(Box(x, y, z, w, h, d, material, key))
        return self

    def child(self, part):
        self.children.append(part)
        return part


def wheel(name, x, y, z, radius, width, tag):
    """Une roue de chenille : deux croix decalees d'un huitieme de tour, et le moyeu qui depasse."""
    part = Part(name, (x, y, z))
    arm = radius * 2
    thick = 4 if radius >= 5 else 3
    half = width / 2.0
    part.box(x - half, y - radius, z - thick / 2.0, width, arm, thick, "tire", tag + "_v")
    part.box(x - half, y - thick / 2.0, z - radius, width, thick, arm, "tire", tag + "_h")
    hub = 3 if radius >= 5 else 2
    part.box(x - half - 0.5, y - hub / 2.0, z - hub / 2.0, width + 1, hub, hub, "hub", tag + "_hub")
    cross = part.child(Part(name + "_cross", (x, y, z), (math.pi / 4, 0.0, 0.0)))
    cross.box(x - half + 0.25, y - radius, z - thick / 2.0, width - 0.5, arm, thick, "tire", tag + "_v")
    cross.box(x - half + 0.25, y - thick / 2.0, z - radius, width - 0.5, thick, arm, "tire", tag + "_h")
    return part


def build():
    parts = []

    body = Part("body")
    body.box(-20, 9, -26, 40, 5, 52, "dark", "deck")                 # le plateau entre les chenilles
    body.box(-18, 14, -12, 36, 16, 36, "paint", "hull")              # la caisse de la tremie
    body.box(-16, 30, -9, 32, 5, 31, "paint_dark", "hopper_top")     # le haut de la tremie
    body.box(-10, 35, -2, 20, 1, 16, "steel", "hatch")               # la trappe de chargement
    body.box(-9, 14, -27, 18, 24, 13, "paint", "cab")                # la cabine
    body.box(-10, 38, -28, 20, 2, 15, "dark", "cab_roof")
    body.box(-8, 31, -27.5, 3, 2, 0.5, "light", "headlight")         # les phares
    body.box(5, 31, -27.5, 3, 2, 0.5, "light", "headlight")
    body.box(-1, 40, -22, 2, 2, 2, "beacon", "beacon")               # le gyrophare
    body.box(6, 40, -17, 1, 10, 1, "dark")                           # l'antenne
    body.box(11, 9, -27, 6, 27, 6, "rust", "elevator")               # l'elevateur a godets
    body.box(10, 36, -27, 8, 3, 16, "rust", "chute")                 # la goulotte vers la tremie
    body.box(18, 14, -6, 3, 7, 18, "dark", "tank")                   # le reservoir, a droite
    body.box(-21, 16, 2, 3, 6, 14, "steel", "toolbox")               # le coffre a outils, a gauche
    for x in (14, -16):                                              # les echappements
        body.box(x, 30, 20, 2, 16, 2, "pipe", "stack")
        body.box(x - 0.5, 46, 19.5, 3, 1, 3, "dark", "stack_cap")
    body.box(-18, 9, 24, 36, 4, 4, "dark", "bumper")                 # le pare-chocs arriere
    body.box(12, 13, 26, 3, 20, 1, "steel", "ladder")                # l'echelle
    for side in (1, -1):                                             # les garde-boue des chenilles
        x = 10.5 if side > 0 else -22.5
        body.box(x, 12, -27, 12, 1, 54, "paint_dark", "guard")
    parts.append(body)

    cyclone = Part("cyclone", (-9, 41, 14))                          # le cyclone qui trie l'epice
    cyclone.box(-14, 35, 11, 10, 12, 6, "steel", "cyclone_a")
    cyclone.box(-12, 35, 9, 6, 12, 10, "steel", "cyclone_b")
    turned = cyclone.child(Part("cyclone_cross", (-9, 41, 14), (0.0, math.pi / 4, 0.0)))
    turned.box(-14, 35.5, 11, 10, 11, 6, "steel", "cyclone_a")
    turned.box(-12, 35.5, 9, 6, 11, 10, "steel", "cyclone_b")
    cyclone.box(-11, 47, 12, 4, 2, 4, "dark", "cyclone_top")
    cyclone.box(-4, 42, 13, 6, 2, 2, "pipe", "cyclone_pipe")
    parts.append(cyclone)

    for side, name in ((1, "right"), (-1, "left")):
        track = Part("track_" + name)
        x0 = 11 if side > 0 else -22
        track.box(x0, 1, -24, 11, 11, 48, "track", "track_main")
        track.box(x0, 3, -28, 11, 7, 4, "track", "track_cap")
        track.box(x0, 3, 24, 11, 7, 4, "track", "track_cap")
        parts.append(track)
        outer = 22.75 if side > 0 else -22.75
        parts.append(wheel("sprocket_front_" + name, outer, 6.5, -22, 5.5, 1.5, "sprocket"))
        parts.append(wheel("sprocket_rear_" + name, outer, 6.5, 22, 5.5, 1.5, "sprocket"))
        parts.append(wheel("roller_front_" + name, outer, 5, -8, 4, 1.5, "roller"))
        parts.append(wheel("roller_rear_" + name, outer, 5, 8, 4, 1.5, "roller"))

    intake = Part("intake")                                          # la bouche d'aspiration
    intake.box(-22, 0.5, -37, 44, 1.5, 9, "steel", "intake_floor")
    intake.box(-22, 2, -29, 44, 9, 2, "rust", "intake_back")
    intake.box(-22, 10, -37, 44, 2, 9, "paint", "intake_hood")
    intake.box(-22, 2, -37, 1, 8, 8, "paint", "intake_side")
    intake.box(21, 2, -37, 1, 8, 8, "paint", "intake_side")
    for x in (16, -19):
        intake.box(x, 8, -28, 3, 3, 4, "dark", "intake_arm")
    drum = intake.child(Part("drum", (0, 6, -33)))
    drum.box(-20, 5, -34, 40, 2, 2, "dark", "drum_core")
    drum.box(-19, 2, -33.5, 38, 8, 1, "steel", "drum_paddle_v")
    drum.box(-19, 5.5, -37, 38, 1, 8, "steel", "drum_paddle_h")
    blades = drum.child(Part("drum_cross", (0, 6, -33), (math.pi / 4, 0.0, 0.0)))
    blades.box(-19, 2.5, -33.25, 38, 7, 0.5, "steel", "drum_blade_v")
    blades.box(-19, 5.75, -36.5, 38, 0.5, 7, "steel", "drum_blade_h")
    parts.append(intake)
    return parts


# -- La texture ----------------------------------------------------------------------------

def walk(parts):
    for part in parts:
        yield part
        yield from walk(part.children)


def footprint(box):
    w, h, d = math.ceil(box.w), math.ceil(box.h), math.ceil(box.d)
    return 2 * (w + d), d + h


def pack(parts):
    """Range les morceaux par etageres, les plus hauts d'abord."""
    seen = {}
    todo = []
    for part in walk(parts):
        for box in part.boxes:
            key = box.key
            if key is not None and key in seen:
                box.uv = seen[key]
                continue
            todo.append(box)
            if key is not None:
                seen[key] = box
    todo.sort(key=lambda b: (-footprint(b)[1], -footprint(b)[0]))
    x = y = shelf = 0
    for box in todo:
        w, h = footprint(box)
        if x + w > SIZE:
            x, y, shelf = 0, y + shelf + 1, 0
        if y + h > SIZE_H:
            raise SystemExit("la texture de %d x %d px est trop petite" % (SIZE, SIZE_H))
        box.uv = (x, y)
        x += w + 1
        shelf = max(shelf, h)
    for part in walk(parts):
        for box in part.boxes:
            if isinstance(box.uv, Box):
                box.uv = box.uv.uv


def shade(color, factor):
    return tuple(max(0, min(255, int(round(c * factor)))) for c in color)


def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3))


FACE_SHADE = {"top": 1.10, "bottom": 0.72, "side_a": 0.96, "side_b": 0.96, "front": 0.90, "back": 0.86}


def paint(parts):
    rng = random.Random(1965)
    pixels = [[None] * SIZE for _ in range(SIZE_H)]
    done = set()
    for part in walk(parts):
        for box in part.boxes:
            if box.uv in done:
                continue
            done.add(box.uv)
            u, v = box.uv
            w, h, d = math.ceil(box.w), math.ceil(box.h), math.ceil(box.d)
            faces = {
                "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
                "side_a": (u, v + d, d, h), "front": (u + d, v + d, w, h),
                "side_b": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
            }
            for face, (fx, fy, fw, fh) in faces.items():
                paint_face(pixels, rng, box, face, fx, fy, fw, fh)
    return pixels


def paint_face(pixels, rng, box, face, fx, fy, fw, fh):
    base = MATERIALS[box.material]
    key = box.key or ""
    material = box.material
    side = face in ("side_a", "side_b")
    upright = face not in ("top", "bottom")
    for j in range(fh):
        for i in range(fw):
            color = shade(base, FACE_SHADE[face] * (0.95 + rng.random() * 0.10))
            edge = i == 0 or j == 0 or i == fw - 1 or j == fh - 1
            if edge and fw > 2 and fh > 2 and material not in ("tire", "light", "beacon", "glass"):
                color = shade(color, 0.78)
            if material in ("paint", "paint_dark"):
                if upright and fh >= 8 and j % 7 == 6:
                    color = shade(color, 0.80)                       # les joints des toles
                if upright and fw >= 10 and i % 9 == 8:
                    color = shade(color, 0.84)
                if upright and fw >= 10 and fh >= 8 and i % 9 == 1 and j % 7 == 1:
                    color = shade(base, 1.22)                        # les rivets
                if rng.random() < 0.05:
                    color = shade(color, 0.86)                       # la peinture ecaillee
                if rng.random() < 0.025:
                    color = MATERIALS["rust"]
            if key == "hull" and side and 2 <= j <= 3:
                color = shade(MATERIALS["dark"], 1.1)                # le bandeau sombre de la caisse
            if key == "hull" and upright and j >= fh - 3:
                color = shade(MATERIALS["rust"], 0.95 + rng.random() * 0.1)
            if key == "cab" and upright:
                rows = 2 <= j <= 8
                cols = 2 <= i <= fw - 3
                if rows and cols and not (face in ("front", "back") and i == fw // 2):
                    glint = (i + j) % 7 == 0
                    color = shade(MATERIALS["glass"], 1.45 if glint else 1.0 + 0.08 * (8 - j) / 6.0)
            if key == "intake_hood" and face == "front":
                color = (232, 186, 40) if ((i + j) // 3) % 2 == 0 else (34, 32, 30)   # les rayures
            if key == "intake_back" and face == "front":
                color = shade(MATERIALS["dark"], 0.6 + 0.2 * ((i // 2) % 2))         # la grille d'aspiration
            if key == "track_main" or key == "track_cap":
                if not side:
                    color = shade(base, 1.7 if (j if face in ("top", "bottom") else i) % 3 == 0 else 0.9)
                elif upright:
                    if j <= 1 or j >= fh - 2:
                        color = shade(base, 1.6 if i % 3 == 0 else 1.0)      # les patins
                    else:
                        color = shade(MATERIALS["dark"], 0.7)
            if material == "tire":
                if not side:
                    color = shade(base, 1.8 if (j // 2) % 2 == 0 else 0.9)
            if material == "hub" and side:
                cx, cy = (fw - 1) / 2.0, (fh - 1) / 2.0
                if math.hypot(i - cx, j - cy) < 0.8:
                    color = shade(MATERIALS["dark"], 1.0)
            if key == "elevator" and upright and j % 4 == 3:
                color = shade(base, 0.62)                            # les godets
            if key == "hopper_top" and face == "top" and (i % 4 == 0 or j % 4 == 0):
                color = shade(base, 0.72)                            # la grille de la tremie
            if key == "hatch" and face == "top" and (i + j) % 3 == 0:
                color = shade(base, 1.25)                            # la tole striee
            if key == "tank" and upright and i % 6 == 2:
                color = shade(MATERIALS["steel"], 0.9)               # les sangles
            if key == "toolbox" and upright and j == fh // 2:
                color = shade(base, 0.6)
            if key == "ladder" and face in ("front", "back") and j % 3 == 1:
                color = shade(base, 1.2)
            if key == "stack" and upright and j <= 2:
                color = shade(MATERIALS["dark"], 0.55)               # la suie
            if material in ("light", "beacon"):
                color = shade(base, 1.0 if not edge else 0.88)
            if material == "glass":
                color = shade(base, 1.2)
            # le sable colle au bas des pieces : plus on est bas, plus il y en a
            if upright and material not in ("light", "beacon", "glass") and fh >= 4:
                low = j / float(fh - 1)
                if low > 0.7 and rng.random() < (low - 0.7) * 2.2:
                    color = mix(color, SAND, 0.55)
            if face == "top" and material in ("paint", "paint_dark", "dark", "steel") and rng.random() < 0.10:
                color = mix(color, SAND, 0.5)
            pixels[fy + j][fx + i] = color


def write_png(path, rows):
    raw = bytearray()
    for row in rows:
        raw.append(0)
        for pixel in row:
            raw.extend((0, 0, 0, 0) if pixel is None else (pixel[0], pixel[1], pixel[2], 255))

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n"
                 + chunk(b"IHDR", struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0))
                 + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


# -- Le code -------------------------------------------------------------------------------

def num(value):
    text = ("%.4f" % value).rstrip("0").rstrip(".")
    return ("0" if text in ("-0", "") else text) + "F"


def camel(name):
    head, *tail = name.split("_")
    return head + "".join(word.capitalize() for word in tail)


def emit(part, parent_var, parent_pivot, lines):
    px, py, pz = part.pivot
    cubes = "CubeListBuilder.create()"
    for box in part.boxes:
        cubes += "\n                .texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s, new CubeDeformation(0F))" % (
            box.uv[0], box.uv[1], num(box.x - px), num(py - box.y - box.h), num(box.z - pz),
            num(box.w), num(box.h), num(box.d))
    ox, oy, oz = px - parent_pivot[0], parent_pivot[1] - py, pz - parent_pivot[2]
    if parent_var == "root":
        oy = 24 - py
    var = camel(part.name)
    lines.append("        PartDefinition %s = %s.addOrReplaceChild(\"%s\", %s,\n"
                 "                PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s));" % (
                     var, parent_var, part.name, cubes, num(ox), num(oy), num(oz),
                     num(part.rot[0]), num(part.rot[1]), num(part.rot[2])))
    for child in part.children:
        emit(child, var, part.pivot, lines)


def java(parts):
    lines = []
    for part in parts:
        emit(part, "root", (0, 0, 0), lines)
    body = "\n".join(lines)
    return """package fr.atraxterra.dohy.client.arrakis;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERE par tools/harvester_model.py. Ne pas modifier a la main : relancer le script.
 *
 * <p>La geometrie de la moissonneuse d'epice, dessinee pour Berskar.
 */
@SuppressWarnings("unused")
public final class HarvesterGeometry {

    private HarvesterGeometry() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
%s
        return LayerDefinition.create(mesh, %d, %d);
    }
}
""" % (body, SIZE, SIZE_H)


# -- L'apercu ------------------------------------------------------------------------------

def rotate(point, rot):
    x, y, z = point
    rx, ry, rz = rot
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return x, y, z


def faces_of(part, transform, out, pixels):
    def place(point):
        local = (point[0] - part.pivot[0], part.pivot[1] - point[1], point[2] - part.pivot[2])
        x, y, z = rotate(local, part.rot)
        return transform((x + part.pivot[0], part.pivot[1] - y, z + part.pivot[2]))

    for box in part.boxes:
        x0, y0, z0, x1, y1, z1 = box.x, box.y, box.z, box.x + box.w, box.y + box.h, box.z + box.d
        corner = lambda a, b, c: place((x1 if a else x0, y1 if b else y0, z1 if c else z0))
        u, v = box.uv
        w, h, d = math.ceil(box.w), math.ceil(box.h), math.ceil(box.d)
        rects = {
            "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
            "side_a": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "side_b": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
        }
        quads = {
            "top": [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
            "bottom": [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)],
            "front": [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)],
            "back": [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)],
            "side_a": [(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)],
            "side_b": [(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)],
        }
        for face, corners in quads.items():
            fx, fy, fw, fh = rects[face]
            # couleur moyenne de la face peinte, pour un apercu fidele a la texture
            acc = [0, 0, 0]
            n = 0
            for jj in range(fh):
                for ii in range(fw):
                    p = pixels[fy + jj][fx + ii]
                    if p is not None:
                        acc = [acc[k] + p[k] for k in range(3)]
                        n += 1
            color = tuple(c // max(1, n) for c in acc) if n else shade(MATERIALS[box.material], FACE_SHADE[face])
            out.append(([corner(*c) for c in corners], color))
    for child in part.children:
        faces_of(child, place, out, pixels)


def preview(parts, folder, pixels):
    from PIL import Image, ImageDraw
    os.makedirs(folder, exist_ok=True)
    views = {"avant": (-0.65, 0.42), "arriere": (2.45, 0.42), "profil": (-math.pi / 2, 0.08),
             "dessus": (-0.4, 1.1)}
    for view, (yaw, pitch) in views.items():
        faces = []
        for part in parts:
            faces_of(part, lambda p: p, faces, pixels)
        projected = []
        for corners, color in faces:
            points = []
            depth = 0.0
            for x, y, z in corners:
                x, z = x * math.cos(yaw) + z * math.sin(yaw), -x * math.sin(yaw) + z * math.cos(yaw)
                y, z = y * math.cos(pitch) + z * math.sin(pitch), -y * math.sin(pitch) + z * math.cos(pitch)
                points.append((x, y))
                depth += z
            projected.append((depth / 4.0, points, color))
        projected.sort(key=lambda item: -item[0])
        scale = 9
        image = Image.new("RGB", (900, 680), (232, 206, 156))
        draw = ImageDraw.Draw(image)
        for _depth, points, color in projected:
            draw.polygon([(450 + px * scale, 500 - py * scale) for px, py in points], fill=color,
                         outline=shade(color, 0.6))
        image.save(os.path.join(folder, "moissonneuse_%s.png" % view))


def main():
    parts = build()
    pack(parts)
    pixels = paint(parts)
    write_png(PNG, pixels)
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8") as fh:
        fh.write(java(parts))
    if len(sys.argv) > 1:
        preview(parts, sys.argv[1], pixels)


if __name__ == "__main__":
    main()

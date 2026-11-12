#!/usr/bin/env python3
"""Dessine le tracteur de DoHy : sa geometrie et sa texture.

Le modele est decrit ici, boite par boite, et rien n'est repris d'un autre mod
ni d'un autre jeu : un tracteur ouvert a arceau, bleu petrole, et quatre outils
traines larges de trois blocs (charrue, semoir, epandeur, moissonneuse).

    python3 -I tools/tractor_model.py [dossier_apercu]

Le script ecrit :

* `client/agri/TractorGeometry.java` — la geometrie, en code vanilla ;
* `assets/dohy/textures/entity/tractor.png` — la texture, peinte face par face.

Avec un dossier en argument, il y pose aussi des apercus (PIL requis) : ils ne
servent qu'a regler la silhouette sans lancer le jeu.

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
JAVA = os.path.join(ROOT, "src", "main", "java", "fr", "atraxterra", "dohy", "client", "agri",
                    "TractorGeometry.java")
PNG = os.path.join(ROOT, "src", "main", "resources", "assets", "dohy", "textures", "entity", "tractor.png")
SIZE = 256

# -- Les matieres : une couleur, et ce que le peintre y ajoute -----------------------------

MATERIALS = {
    "body": (38, 118, 128),      # la carrosserie, bleu petrole
    "dark": (52, 54, 60),        # chassis, essieux, arceau
    "grille": (34, 36, 40),
    "seat": (30, 28, 30),
    "tire": (28, 28, 30),
    "hub": (228, 214, 182),      # jantes creme
    "steel": (150, 156, 164),
    "blade": (196, 202, 208),
    "accent": (196, 88, 44),     # les outils, orange brique
    "light": (255, 176, 40),
    "pipe": (88, 90, 96),
}


class Box:
    def __init__(self, x, y, z, w, h, d, material, key=None):
        self.x, self.y, self.z, self.w, self.h, self.d = x, y, z, w, h, d
        self.material = material
        # les boites de meme cle partagent leur morceau de texture (les roues, les socs)
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
    """Une roue : deux croix decalees d'un huitieme de tour — un octogone — et la jante qui depasse."""
    part = Part(name, (x, y, z))
    arm = radius * 2
    thick = 8 if radius >= 9 else 5
    half = width / 2.0
    part.box(x - half, y - radius, z - thick / 2.0, width, arm, thick, "tire", tag + "_v")
    part.box(x - half, y - thick / 2.0, z - radius, width, thick, arm, "tire", tag + "_h")
    hub = 8 if radius >= 9 else 4
    part.box(x - half - 0.5, y - hub / 2.0, z - hub / 2.0, width + 1, hub, hub, "hub", tag + "_hub")
    # la seconde croix, un peu moins large : ses flancs ne se confondent pas avec ceux de la premiere
    cross = part.child(Part(name + "_cross", (x, y, z), (math.pi / 4, 0.0, 0.0)))
    cross.box(x - half + 0.25, y - radius, z - thick / 2.0, width - 0.5, arm, thick, "tire", tag + "_v")
    cross.box(x - half + 0.25, y - thick / 2.0, z - radius, width - 0.5, thick, arm, "tire", tag + "_h")
    return part


def build():
    parts = []

    body = Part("body")
    body.box(-5, 7, -20, 10, 4, 36, "dark")                 # le chassis
    body.box(-6, 11, -21, 12, 9, 18, "body", "hood")        # le capot
    body.box(-5, 12, -22, 10, 7, 1, "grille", "grille")     # la calandre
    body.box(-4, 6, -24, 8, 4, 3, "dark")                   # les masses avant
    body.box(3, 20, -15, 2, 9, 2, "pipe")                   # l'echappement
    body.box(-6, 11, -3, 12, 11, 3, "body", "dash")         # le tableau de bord
    body.box(-8, 9, 0, 16, 2, 17, "dark")                   # le plancher
    body.box(-4, 11, 6, 8, 3, 8, "seat")                    # le siege
    body.box(-4, 14, 12, 8, 8, 2, "seat")
    for side in (1, -1):                                    # les ailes
        x = 8 if side > 0 else -15
        body.box(x, 20, -2, 7, 2, 19, "body", "fender")
        body.box(7 if side > 0 else -8, 11, 0, 1, 9, 17, "body", "cheek")
        body.box(6 if side > 0 else -8, 22, 15, 2, 14, 2, "dark", "post")   # l'arceau
    body.box(-8, 36, 15, 16, 2, 2, "dark")
    body.box(-1, 38, 15, 2, 2, 2, "light")                  # le gyrophare
    body.box(-9, 5, -15, 18, 2, 2, "dark")                  # les essieux
    body.box(-10, 9, 7, 20, 2, 2, "dark")
    body.box(-1, 20, -2, 2, 4, 2, "dark")                   # la colonne de direction
    steer = body.child(Part("steer", (0, 24, 0), (-0.5, 0.0, 0.0)))
    steer.box(-3, 23.5, -3, 6, 1, 6, "seat")
    parts.append(body)

    parts.append(wheel("wheel_rear_right", 11.5, 10, 8, 10, 5, "rear"))
    parts.append(wheel("wheel_rear_left", -11.5, 10, 8, 10, 5, "rear"))
    parts.append(wheel("wheel_front_right", 9, 6, -14, 6, 4, "front"))
    parts.append(wheel("wheel_front_left", -9, 6, -14, 6, 4, "front"))

    hitch = Part("hitch")
    hitch.box(-1, 6, 16, 2, 2, 6, "dark")
    hitch.box(-1, 12, 16, 2, 2, 7, "dark")
    parts.append(hitch)

    plow = Part("plow")
    plow.box(-23, 9, 22, 46, 3, 3, "accent")
    for x in (-20, -10, 0, 10, 20):
        plow.box(x - 1, 3, 23, 2, 6, 2, "steel", "plow_leg")
        plow.box(x - 2, 0, 21, 4, 3, 8, "blade", "plow_share")
        plow.box(x - 2, 3, 26, 4, 4, 3, "blade", "plow_board")
    parts.append(plow)

    seeder = Part("seeder")
    seeder.box(-23, 9, 21, 46, 8, 8, "accent")
    seeder.box(-24, 17, 20, 48, 1, 10, "steel")
    for x in (-21, -14, -7, 0, 7, 14, 21):
        seeder.box(x - 1, 2, 24, 2, 7, 2, "pipe", "seed_tube")
    for x in (-24, 22):
        seeder.box(x, 0, 23, 2, 7, 7, "tire", "seed_wheel")
    parts.append(seeder)

    spreader = Part("spreader")
    spreader.box(-8, 7, 21, 16, 11, 17, "accent")
    spreader.box(-9, 18, 20, 18, 1, 19, "steel")
    spreader.box(-24, 4, 38, 48, 1, 2, "steel")
    spreader.box(-1, 4, 36, 2, 3, 2, "pipe")
    for x in (9, -12):
        spreader.box(x, 0, 25, 3, 9, 9, "tire", "spread_wheel")
    spreader.box(-9, 4, 29, 18, 1, 1, "dark")
    parts.append(spreader)

    harvester = Part("harvester")
    harvester.box(-24, 2, 20, 48, 2, 11, "accent")
    harvester.box(-24, 4, 30, 48, 8, 1, "accent")
    for x in (-24, 23):
        harvester.box(x, 4, 20, 1, 8, 10, "accent", "harv_side")
    harvester.box(-24, 1, 19, 48, 1, 1, "blade")
    reel = harvester.child(Part("reel", (0, 9, 24)))
    reel.box(-23, 8, 23, 46, 2, 2, "dark")
    reel.box(-22, 12.5, 23.5, 44, 1, 1, "steel", "reel_bar")
    reel.box(-22, 4.5, 23.5, 44, 1, 1, "steel", "reel_bar")
    reel.box(-22, 8.5, 19.5, 44, 1, 1, "steel", "reel_bar")
    reel.box(-22, 8.5, 27.5, 44, 1, 1, "steel", "reel_bar")
    for x in (-22, 21):
        reel.box(x, 4.5, 23.5, 1, 9, 1, "steel", "reel_spoke_v")
        reel.box(x, 8.5, 19.5, 1, 1, 9, "steel", "reel_spoke_h")
    parts.append(harvester)
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
                seen[key] = box  # remplace par l'uv une fois range
    todo.sort(key=lambda b: (-footprint(b)[1], -footprint(b)[0]))
    x = y = shelf = 0
    for box in todo:
        w, h = footprint(box)
        if x + w > SIZE:
            x, y, shelf = 0, y + shelf + 1, 0
        if y + h > SIZE:
            raise SystemExit("la texture de %d px est trop petite" % SIZE)
        box.uv = (x, y)
        x += w + 1
        shelf = max(shelf, h)
    for part in walk(parts):
        for box in part.boxes:
            if isinstance(box.uv, Box):
                box.uv = box.uv.uv


def shade(color, factor):
    return tuple(max(0, min(255, int(round(c * factor)))) for c in color)


def paint(parts):
    rng = random.Random(2026)
    pixels = [[None] * SIZE for _ in range(SIZE)]
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


FACE_SHADE = {"top": 1.10, "bottom": 0.72, "side_a": 0.96, "side_b": 0.96, "front": 0.90, "back": 0.86}


def paint_face(pixels, rng, box, face, fx, fy, fw, fh):
    base = MATERIALS[box.material]
    key = box.key or ""
    for j in range(fh):
        for i in range(fw):
            color = shade(base, FACE_SHADE[face] * (0.97 + rng.random() * 0.06))
            edge = i == 0 or j == 0 or i == fw - 1 or j == fh - 1
            if edge and fw > 2 and fh > 2 and box.material not in ("tire", "light"):
                color = shade(color, 0.80)
            material = box.material
            side = face in ("side_a", "side_b")
            if material == "tire":
                if not side:
                    # la bande de roulement : des crampons en travers
                    color = shade(base, 1.9 if (j // 2) % 2 == 0 else 0.9)
            elif material == "hub" and side:
                cx, cy = (fw - 1) / 2.0, (fh - 1) / 2.0
                dist = math.hypot(i - cx, j - cy)
                if dist < 1.2:
                    color = shade(MATERIALS["dark"], 1.0)
                elif abs(dist - (min(fw, fh) / 2.0 - 1.6)) < 0.6 and (i + j) % 2 == 0:
                    color = shade(MATERIALS["dark"], 1.3)   # les ecrous
            elif key == "grille" and face == "front":
                color = shade(base, 1.0 if j % 2 == 0 else 1.9)
                if j in (1, 2) and (i in (1, 2) or i in (fw - 3, fw - 2)):
                    color = (255, 240, 170)                 # les phares
            elif key == "hood":
                if side and j in (2, 3):
                    color = shade(MATERIALS["hub"], 0.98)   # le filet creme
                elif side and 5 <= j <= 6 and 3 <= i <= fw - 4 and i % 3 != 0:
                    color = shade(base, 0.55)               # les ouies
                elif face == "top" and i in (fw // 2 - 1, fw // 2):
                    color = shade(base, 0.82)
            elif key == "dash" and face == "back":
                if 2 <= j <= 3 and i in (2, 3, 5, 6, fw - 4, fw - 3):
                    color = (236, 232, 214)                 # les cadrans
                elif j == 3 and i == fw // 2 + 1:
                    color = (214, 60, 48)                   # le voyant
            elif key == "fender" and face == "top" and j in (fh // 2,):
                color = shade(base, 1.12)
            elif material == "accent":
                if side or face in ("front", "back"):
                    if fh >= 6 and j == fh // 2:
                        color = shade(base, 0.74)           # un pli de tole
                    if fw >= 12 and i % 8 == 4 and 1 < j < fh - 2 and j % 3 == 1:
                        color = shade(base, 1.25)           # les rivets
            elif material == "steel" and fw >= 8 and i % 6 == 3 and fh >= 3 and j == fh // 2:
                color = shade(base, 0.7)
            elif material == "seat" and face in ("top", "front") and (i + j) % 3 == 0:
                color = shade(base, 1.5)
            elif material == "light":
                color = shade(base, 1.0 if not edge else 0.85)
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
        # du repere du script (y vers le haut) a celui du modele (y vers le bas), autour du pivot
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
    return """package fr.atraxterra.dohy.client.agri;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERE par tools/tractor_model.py. Ne pas modifier a la main : relancer le script.
 *
 * <p>La geometrie du tracteur et de ses quatre outils, dessinee pour DoHy.
 */
@SuppressWarnings("unused")
public final class TractorGeometry {

    private TractorGeometry() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
%s
        return LayerDefinition.create(mesh, %d, %d);
    }
}
""" % (body, SIZE, SIZE)


# -- L'apercu ------------------------------------------------------------------------------

def rotate(point, rot):
    x, y, z = point
    rx, ry, rz = rot
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return x, y, z


def faces_of(part, transform, out, hidden):
    def place(point):
        # repere du modele : y vers le bas, autour du pivot de la piece
        local = (point[0] - part.pivot[0], part.pivot[1] - point[1], point[2] - part.pivot[2])
        x, y, z = rotate(local, part.rot)
        return transform((x + part.pivot[0], part.pivot[1] - y, z + part.pivot[2]))

    if part.name in hidden:
        return
    for box in part.boxes:
        x0, y0, z0, x1, y1, z1 = box.x, box.y, box.z, box.x + box.w, box.y + box.h, box.z + box.d
        corner = lambda a, b, c: place((x1 if a else x0, y1 if b else y0, z1 if c else z0))
        quads = {
            "top": [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
            "bottom": [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)],
            "front": [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)],
            "back": [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)],
            "side_a": [(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)],
            "side_b": [(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)],
        }
        for face, corners in quads.items():
            out.append(([corner(*c) for c in corners], shade(MATERIALS[box.material], FACE_SHADE[face])))
    for child in part.children:
        faces_of(child, place, out, hidden)


def preview(parts, folder):
    from PIL import Image, ImageDraw
    os.makedirs(folder, exist_ok=True)
    tools = ["plow", "seeder", "spreader", "harvester"]
    views = {"avant": (-0.65, 0.42), "arriere": (2.45, 0.42), "profil": (-math.pi / 2, 0.08)}
    for tool in [None] + tools:
        hidden = set(t for t in tools if t != tool)
        if tool is None:
            hidden.add("hitch")
        for view, (yaw, pitch) in views.items():
            faces = []
            for part in parts:
                faces_of(part, lambda p: p, faces, hidden)
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
            image = Image.new("RGB", (900, 620), (150, 190, 230))
            draw = ImageDraw.Draw(image)
            for _depth, points, color in projected:
                draw.polygon([(450 + px * scale, 470 - py * scale) for px, py in points], fill=color,
                             outline=shade(color, 0.6))
            image.save(os.path.join(folder, "tracteur_%s_%s.png" % (tool or "nu", view)))


def main():
    parts = build()
    pack(parts)
    write_png(PNG, paint(parts))
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8") as fh:
        fh.write(java(parts))
    if len(sys.argv) > 1:
        preview(parts, sys.argv[1])


if __name__ == "__main__":
    main()

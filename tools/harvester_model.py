#!/usr/bin/env python3
"""Dessine la moissonneuse d'epice d'Arrakis : sa geometrie, sa texture et ses lumieres.

Une chenillette de chantier dessinee pour Berskar, piece par piece, reprise
d'aucun film ni d'aucun jeu. Deux chenilles a galets et barbotins, une bouche
d'aspiration a dents et a tambour, une cabine a pare-soleil et rampe de phares,
la tremie en caisse avec ses hublots ou l'epice luit, un cyclone qui trie la
poussiere, un elevateur a godets, deux echappements, des rambardes et une
echelle. Peinture ocre de chantier, toles rivetees, rouille et sable.

La texture suit la maniere des textures de PalaMod : peu de tons par matiere
(cinq a six, du contour au reflet), des toles biseautees (contour sombre,
lisere clair en haut a gauche), des joints, des rivets, et des accents qui
brillent. Les lumieres ont leur propre texture, rendue en pleine lumiere.

    python3 -I tools/harvester_model.py [dossier_apercu]

Le script ecrit :

* `client/arrakis/HarvesterGeometry.java` — la geometrie, en code vanilla ;
* `assets/dohy/textures/entity/spice_harvester.png` — la texture ;
* `assets/dohy/textures/entity/spice_harvester_glow.png` — ce qui brille la nuit.

Avec un dossier en argument, il y pose des apercus textures (PIL requis).

Reperes : x vers la droite, y vers le haut depuis le sol, z negatif vers
l'avant, en pixels (16 par bloc). La conversion vers le repere des modeles de
Minecraft (y vers le bas, sol a 24) est faite a l'ecriture.
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
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "dohy", "textures", "entity")
PNG = os.path.join(TEX, "spice_harvester.png")
GLOW = os.path.join(TEX, "spice_harvester_glow.png")
W, H = 512, 256

# -- Les matieres : des rampes courtes, du contour au reflet --------------------------------

RAMPS = {
    "paint": [(78, 42, 14), (128, 74, 26), (170, 108, 40), (206, 144, 58), (234, 180, 88), (250, 214, 140)],
    "paint_dark": [(62, 34, 12), (102, 60, 22), (138, 88, 32), (170, 116, 44), (198, 146, 64), (222, 180, 104)],
    "steel_dark": [(20, 20, 24), (34, 34, 40), (50, 50, 58), (68, 68, 78), (92, 92, 104), (124, 124, 136)],
    "steel": [(54, 56, 62), (86, 90, 98), (118, 122, 130), (150, 154, 162), (184, 188, 194), (220, 222, 226)],
    "rust": [(56, 22, 10), (90, 38, 16), (124, 58, 24), (156, 80, 34), (186, 108, 50), (212, 140, 74)],
    "rubber": [(14, 13, 14), (24, 23, 24), (36, 34, 34), (50, 48, 46), (68, 64, 60), (90, 86, 80)],
    "glass": [(10, 20, 30), (18, 40, 56), (32, 66, 88), (54, 102, 128), (96, 152, 178), (184, 222, 236)],
    "sand": [(150, 110, 62), (182, 142, 88), (206, 170, 114), (224, 192, 138), (238, 212, 164), (250, 232, 196)],
}
HAZARD_Y = [(120, 84, 10), (196, 146, 18), (240, 192, 36), (255, 222, 96)]
HAZARD_K = [(16, 15, 14), (30, 28, 26), (44, 42, 38)]
LIGHT = [(150, 96, 30), (230, 170, 70), (255, 222, 140), (255, 246, 214)]
RED = [(90, 14, 10), (170, 30, 20), (230, 60, 40), (255, 140, 110)]
BEACON = [(130, 50, 6), (214, 96, 16), (255, 150, 40), (255, 214, 130)]
SPICE = [(90, 30, 6), (170, 64, 14), (228, 110, 26), (255, 160, 56), (255, 214, 120), (255, 246, 200)]


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


def cylinder_x(part, name, x0, x1, y, z, r, material, key):
    """Un cylindre couche selon x : une croix, et la meme croix tournee d'un huitieme de tour."""
    w = x1 - x0
    s = r * 0.83
    part.box(x0, y - r, z - s * 0.5, w, 2 * r, s, material, key + "_v")
    part.box(x0, y - s * 0.5, z - r, w, s, 2 * r, material, key + "_h")
    turned = part.child(Part(name + "_x", (x0, y, z), (math.pi / 4, 0.0, 0.0)))
    turned.box(x0 + 0.05, y - r, z - s * 0.5, w - 0.1, 2 * r, s, material, key + "_v")
    turned.box(x0 + 0.05, y - s * 0.5, z - r, w - 0.1, s, 2 * r, material, key + "_h")
    return turned


def cylinder_y(part, name, x, z, y0, y1, r, material, key):
    """Un cylindre debout : une croix, et la meme croix tournee d'un huitieme de tour."""
    h = y1 - y0
    s = r * 0.83
    part.box(x - r, y0, z - s * 0.5, 2 * r, h, s, material, key + "_a")
    part.box(x - s * 0.5, y0, z - r, s, h, 2 * r, material, key + "_b")
    turned = part.child(Part(name + "_y", (x, y0, z), (0.0, math.pi / 4, 0.0)))
    turned.box(x - r, y0 + 0.05, z - s * 0.5, 2 * r, h - 0.1, s, material, key + "_a")
    turned.box(x - s * 0.5, y0 + 0.05, z - r, s, h - 0.1, 2 * r, material, key + "_b")


def wheel(name, x, y, z, r, width, key):
    """Une roue qui tourne : pneu en croix doublee, moyeu en saillie."""
    part = Part(name, (x, y, z))
    half = width / 2.0
    s = r * 0.83
    part.box(x - half, y - r, z - s * 0.5, width, 2 * r, s, "rubber", key + "_v")
    part.box(x - half, y - s * 0.5, z - r, width, s, 2 * r, "rubber", key + "_h")
    hub = max(2.0, round(r * 0.9))
    part.box(x - half - 0.5, y - hub / 2.0, z - hub / 2.0, width + 1, hub, hub, "steel", key + "_hub")
    turned = part.child(Part(name + "_x", (x, y, z), (math.pi / 4, 0.0, 0.0)))
    turned.box(x - half + 0.1, y - r, z - s * 0.5, width - 0.2, 2 * r, s, "rubber", key + "_v")
    turned.box(x - half + 0.1, y - s * 0.5, z - r, width - 0.2, s, 2 * r, "rubber", key + "_h")
    return part


def build():
    parts = []

    body = Part("body")
    b = body.box
    b(-12, 3, -26, 24, 10, 52, "steel_dark", "chassis")              # le bas de caisse entre les chenilles
    b(-18, 13, -8, 36, 17, 34, "paint", "hull")                      # la caisse de la tremie
    b(-17, 30, -7, 34, 2, 32, "steel_dark", "roof")                  # le toit, en caillebotis
    b(-8, 32, 3, 16, 1, 13, "steel", "hatch")                        # la trappe de chargement
    for x, z in ((-17, -7), (16, -7), (-17, 24), (16, 24), (-17, 8), (16, 8)):
        b(x, 32, z, 1, 4, 1, "paint_dark", "post")                   # les poteaux des rambardes
    b(-17, 36, -7, 1, 1, 32, "paint_dark", "rail_side")
    b(16, 36, -7, 1, 1, 32, "paint_dark", "rail_side")
    b(-16, 36, 24, 32, 1, 1, "paint_dark", "rail_back")
    b(18, 18, 2, 0.5, 6, 6, "spiceglass", "sight")                   # les hublots de la tremie
    b(18, 18, 13, 0.5, 6, 6, "spiceglass", "sight")
    b(-21, 14, 2, 3, 9, 18, "steel_dark", "tank")                    # le reservoir, a gauche
    b(12, 13, -26, 6, 7, 12, "steel_dark", "toolbox")                # le coffre, a droite de l'elevateur

    b(-18, 13, -28, 16, 23, 18, "paint", "cab")                      # la cabine
    b(-19, 36, -29, 18, 2, 20, "steel_dark", "cab_roof")
    b(-18.5, 34, -30.5, 17, 1, 2.5, "steel_dark", "visor")           # le pare-soleil
    for x in (-17.5, -13.5, -7.5, -3.5):
        b(x, 38, -28.5, 2, 1.5, 1.5, "light", "roof_light")          # la rampe de phares
    b(-5, 38, -12, 2, 2, 2, "beacon", "beacon")                      # le gyrophare
    b(-17, 38, -11.5, 1, 11, 1, "steel_dark", "antenna")
    b(-21, 30, -27, 3, 1, 1, "steel_dark", "mirror_arm")             # le retroviseur
    b(-22.5, 27, -27.5, 1.5, 5, 2, "glass", "mirror")
    b(-18.5, 17, -16, 0.5, 9, 1, "steel", "grab")                    # la poignee de la cabine
    b(-17, 16, -28.5, 3, 2, 0.5, "light", "headlight")               # les phares bas
    b(-7, 16, -28.5, 3, 2, 0.5, "light", "headlight")

    b(-2, 13, -28, 6, 1, 20, "steel_dark", "walkway")                # la passerelle
    b(4, 9, -27, 8, 29, 8, "rust", "elevator")                       # l'elevateur a godets
    b(3, 38, -28, 10, 4, 18, "rust", "elevator_head")
    b(5, 33, -10, 6, 5, 4, "rust", "chute")                          # la goulotte dans la tremie
    b(6, 33, -6, 2, 2, 20, "steel", "pipe_z")                        # le conduit vers le cyclone
    b(-5, 33, 14, 13, 2, 2, "steel", "pipe_x")

    b(-18, 5, 26, 36, 8, 4, "steel_dark", "bumper")                  # le pare-chocs, contrepoids
    b(-17, 9, 30, 3, 2, 0.5, "light_red", "tail")                    # les feux arriere
    b(14, 9, 30, 3, 2, 0.5, "light_red", "tail")
    b(-3, 13, 26, 1, 19, 1, "steel", "ladder_rail")                  # l'echelle
    b(2, 13, 26, 1, 19, 1, "steel", "ladder_rail")
    for y in (15, 18, 21, 24, 27, 30):
        b(-2, y, 26.25, 4, 0.75, 0.75, "steel", "rung")
    for x in (9, 13):                                                # les echappements
        b(x, 30, 21, 2, 15, 2, "steel_dark", "stack")
        b(x - 0.5, 45, 20.5, 3, 1, 3, "steel_dark", "stack_cap")
    parts.append(body)

    cyclone = Part("cyclone")                                        # le cyclone qui trie l'epice
    cylinder_y(cyclone, "cyclone_body", -10, 16, 32, 46, 5, "steel", "cyclone")
    cylinder_y(cyclone, "cyclone_top", -10, 16, 46, 47.5, 4, "steel_dark", "cyclone_cap")
    cyclone.box(-11, 47.5, 15, 2, 3, 2, "steel_dark", "vent")
    parts.append(cyclone)

    for side, name in ((1, "right"), (-1, "left")):
        track = Part("track_" + name)
        x0 = 12 if side > 0 else -22
        track.box(x0, 10, -24, 10, 2, 48, "rubber", "track_run")
        track.box(x0, 0, -24, 10, 2, 48, "rubber", "track_run")
        track.box(x0 + 1, 2, -24, 8, 8, 48, "steel_dark", "track_frame")
        for zc in (-24, 24):
            cylinder_x(track, "track_end_%s_%d" % (name, 0 if zc < 0 else 1), x0, x0 + 10, 6, zc, 6, "rubber",
                       "track_end")
        track.box(x0 - 0.5, 12.5, -27, 11, 1, 54, "paint_dark", "guard")
        track.box(x0 - 0.5, 9, -27.5, 11, 3.5, 0.5, "paint", "guard_front")
        parts.append(track)
        outer = x0 + 9.75 if side > 0 else x0 + 0.25
        for i, zc in enumerate((-14, -5, 4, 13)):
            parts.append(wheel("wheel_%s%d" % (name, i + 1), outer, 5.5, zc, 3.5, 1.5, "roller"))
        hub = x0 + 10.4 if side > 0 else x0 - 0.4
        parts.append(wheel("hub_front_" + name, hub, 6, -24, 4.5, 0.8, "sprocket"))
        parts.append(wheel("hub_rear_" + name, hub, 6, 24, 4.5, 0.8, "sprocket"))

    intake = Part("intake")                                          # la bouche d'aspiration
    intake.box(-22, 0.5, -42, 44, 1.5, 13, "steel", "intake_floor")
    for k in range(9):
        intake.box(-20 + k * 5, 0.5, -44, 2, 1.5, 2, "steel", "tooth")
    intake.box(-22, 2, -30, 44, 10, 2, "steel_dark", "intake_back")
    intake.box(-22, 11, -42, 44, 2, 13, "paint", "intake_hood")
    intake.box(-22.5, 2, -42, 1, 9, 13, "paint", "intake_cheek")
    intake.box(21.5, 2, -42, 1, 9, 13, "paint", "intake_cheek")
    for x in (14, -17):
        intake.box(x, 9, -28, 3, 3, 6, "steel_dark", "ram")
        intake.box(x + 0.75, 9.75, -30.5, 1.5, 1.5, 3, "steel", "ram_rod")
    drum = intake.child(Part("drum", (0, 6.5, -36)))
    drum.box(-20, 5, -37.5, 40, 3, 3, "steel_dark", "drum_core")
    drum.box(-19, 2, -36.5, 38, 9, 1, "steel", "drum_blade_v")
    drum.box(-19, 6, -40.5, 38, 1, 9, "steel", "drum_blade_h")
    blades = drum.child(Part("drum_x", (0, 6.5, -36), (math.pi / 4, 0.0, 0.0)))
    blades.box(-19, 2.5, -36.25, 38, 8, 0.5, "steel", "drum_blade_v2")
    blades.box(-19, 6.25, -40, 38, 0.5, 8, "steel", "drum_blade_h2")
    parts.append(intake)
    return parts


# -- La mise en page de la texture ----------------------------------------------------------

def walk(parts):
    for part in parts:
        yield part
        yield from walk(part.children)


def dims(box):
    return math.ceil(box.w - 1e-6), math.ceil(box.h - 1e-6), math.ceil(box.d - 1e-6)


def footprint(box):
    w, h, d = dims(box)
    return 2 * (w + d), d + h


def pack(parts):
    """Range les morceaux par etageres, les plus hauts d'abord ; meme cle, meme morceau."""
    seen = {}
    todo = []
    for part in walk(parts):
        for box in part.boxes:
            if box.key is not None and box.key in seen:
                box.uv = seen[box.key]
                continue
            todo.append(box)
            if box.key is not None:
                seen[box.key] = box
    todo.sort(key=lambda b: (-footprint(b)[1], -footprint(b)[0]))
    x = y = shelf = 0
    for box in todo:
        w, h = footprint(box)
        if x + w > W:
            x, y, shelf = 0, y + shelf + 1, 0
        if y + h > H:
            raise SystemExit("la texture de %d x %d px est trop petite" % (W, H))
        box.uv = (x, y)
        x += w + 1
        shelf = max(shelf, h)
    for part in walk(parts):
        for box in part.boxes:
            if isinstance(box.uv, Box):
                box.uv = box.uv.uv


def face_rects(box):
    u, v = box.uv
    w, h, d = dims(box)
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "side_a": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "side_b": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }


# -- La peinture -----------------------------------------------------------------------------

def value_noise(fw, fh, rng, cell):
    """Un bruit doux : une grille de valeurs au hasard, interpolee."""
    gw, gh = fw // cell + 2, fh // cell + 2
    grid = [[rng.random() for _ in range(gw)] for _ in range(gh)]
    out = [[0.0] * fw for _ in range(fh)]
    for j in range(fh):
        for i in range(fw):
            gx, gy = i / float(cell), j / float(cell)
            x0, y0 = int(gx), int(gy)
            tx, ty = gx - x0, gy - y0
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = grid[y0][x0] * (1 - tx) + grid[y0][x0 + 1] * tx
            c = grid[y0 + 1][x0] * (1 - tx) + grid[y0 + 1][x0 + 1] * tx
            out[j][i] = a * (1 - ty) + c * ty
    return out


def splits(n, size):
    """Les positions des joints pour couper n pixels en panneaux d'environ {size}."""
    if n < size + 4:
        return []
    count = max(1, round(n / float(size)))
    return [round(n * k / float(count)) for k in range(1, count)]


class Canvas:
    def __init__(self):
        self.color = [[None] * W for _ in range(H)]
        self.glow = [[None] * W for _ in range(H)]

    def put(self, x, y, c):
        if 0 <= x < W and 0 <= y < H:
            self.color[y][x] = c

    def shine(self, x, y, c):
        if 0 <= x < W and 0 <= y < H:
            self.glow[y][x] = c


def tone(ramp, index):
    return ramp[max(0, min(len(ramp) - 1, index))]


def plate(rng, fw, fh, base=3, seams=True, rivets=True, panel=10, mottle=True):
    """Une tole : fond a deux tons, joints en creux, rivets, contour biseaute. Rend des indices de rampe."""
    g = [[base] * fw for _ in range(fh)]
    if mottle and fw >= 3 and fh >= 3:
        n = value_noise(fw, fh, rng, 3)
        for j in range(fh):
            for i in range(fw):
                if n[j][i] < 0.22:
                    g[j][i] = base - 1
                elif n[j][i] > 0.86:
                    g[j][i] = base + 1
    cols = splits(fw, panel) if seams else []
    rows = splits(fh, panel - 2) if seams else []
    for c in cols:
        for j in range(fh):
            g[j][c] = 1
            if c + 1 < fw:
                g[j][c + 1] = base + 1
    for r in rows:
        for i in range(fw):
            g[r][i] = 1
            if r + 1 < fh:
                g[r + 1][i] = base + 1
    if rivets and fw >= 6 and fh >= 5:
        xs = [1] + [c + 2 for c in cols] + [fw - 2]
        ys = [1] + [r + 2 for r in rows] + [fh - 2]
        for x in xs:
            for y in ys:
                if 0 < x < fw - 1 and 0 < y < fh - 1:
                    g[y][x] = 5
                    if y + 1 < fh - 1 and x + 1 < fw - 1:
                        g[y + 1][x + 1] = 1
    bevel(g, base)
    return g


def bevel(g, base):
    fh, fw = len(g), len(g[0])
    if fw < 3 or fh < 3:
        return
    for i in range(fw):
        g[0][i] = 1 if g[0][i] != 5 else 5
        g[fh - 1][i] = 0
    for j in range(fh):
        g[j][0] = 1
        g[j][fw - 1] = 0
    if fw >= 4 and fh >= 4:
        for i in range(1, fw - 1):
            if g[1][i] in (base, base - 1):
                g[1][i] = base + 1
        for j in range(1, fh - 1):
            if g[j][1] in (base, base - 1):
                g[j][1] = base + 1


def brushed(rng, fw, fh, base=3):
    """Un acier brosse : des stries horizontales, un contour biseaute."""
    g = [[base] * fw for _ in range(fh)]
    for j in range(fh):
        streak = rng.random()
        for i in range(fw):
            r = rng.random()
            if streak < 0.25 and r < 0.6:
                g[j][i] = base - 1
            elif streak > 0.85 and r < 0.5:
                g[j][i] = base + 1
    bevel(g, base)
    return g


def weather(canvas, rng, fx, fy, fw, fh, upright, rust_amount=0.5, dust_amount=1.0, top=False):
    """La rouille qui coule des rivets et des joints, le sable colle en bas et pose sur le dessus."""
    rust = RAMPS["rust"]
    sand = RAMPS["sand"]
    if upright and fw >= 6 and fh >= 6:
        for _ in range(int(fw * fh * 0.012 * rust_amount) + (1 if rng.random() < rust_amount else 0)):
            x = rng.randrange(1, fw - 1)
            y = rng.randrange(1, max(2, fh // 2))
            length = rng.randrange(2, max(3, fh // 3))
            for k in range(length):
                if y + k < fh - 1:
                    canvas.put(fx + x, fy + y + k, tone(rust, 3 if k == 0 else 2 + (k % 2)))
    if upright and fh >= 4:
        for j in range(fh):
            low = j / float(fh - 1)
            if low < 0.68:
                continue
            for i in range(fw):
                if rng.random() < (low - 0.68) * 2.6 * dust_amount:
                    canvas.put(fx + i, fy + j, tone(sand, 2 + (1 if rng.random() < 0.4 else 0)))
    if top:
        for j in range(fh):
            for i in range(fw):
                edge = min(i, j, fw - 1 - i, fh - 1 - j)
                p = 0.10 * dust_amount + (0.18 if edge == 0 else 0.0)
                if rng.random() < p:
                    canvas.put(fx + i, fy + j, tone(sand, 3 if rng.random() < 0.5 else 2))


def apply(canvas, fx, fy, g, ramp):
    for j, row in enumerate(g):
        for i, index in enumerate(row):
            canvas.put(fx + i, fy + j, tone(ramp, index))


def hazard(canvas, fx, fy, fw, fh, period=3):
    for j in range(fh):
        for i in range(fw):
            stripe = ((i + j) // period) % 2 == 0
            edge = j == 0 or j == fh - 1 or i == 0 or i == fw - 1
            if stripe:
                c = HAZARD_Y[1] if edge else (HAZARD_Y[3] if (i + j) % period == 0 else HAZARD_Y[2])
            else:
                c = HAZARD_K[0] if edge else HAZARD_K[1 if (i + j) % period else 2]
            canvas.put(fx + i, fy + j, c)


def glass(canvas, fx, fy, fw, fh, frame=True, frame_ramp="steel_dark"):
    ramp = RAMPS["glass"]
    for j in range(fh):
        for i in range(fw):
            t = j / float(max(1, fh - 1))
            c = tone(ramp, 3 if t < 0.35 else 2)
            streak = (i + j) % 9
            if streak in (0, 1) and t < 0.8:
                c = tone(ramp, 4 if streak == 0 else 5)
            if frame and (i == 0 or j == 0 or i == fw - 1 or j == fh - 1):
                c = tone(RAMPS[frame_ramp], 1)
            canvas.put(fx + i, fy + j, c)


def light(canvas, fx, fy, fw, fh, ramp):
    for j in range(fh):
        for i in range(fw):
            edge = i == 0 or j == 0 or i == fw - 1 or j == fh - 1
            c = ramp[1] if edge and fw > 2 and fh > 2 else (ramp[3] if (i + j) % 3 == 0 else ramp[2])
            canvas.put(fx + i, fy + j, c)
            canvas.shine(fx + i, fy + j, ramp[3] if not edge else ramp[2])


def tread(canvas, fx, fy, fw, fh, along_width):
    """La bande de roulement : des tuiles de chenille de trois pixels, crampon clair, creux sombre."""
    ramp = RAMPS["rubber"]
    for j in range(fh):
        for i in range(fw):
            k = (i if along_width else j) % 3
            edge = (j if along_width else i) in (0, (fh if along_width else fw) - 1)
            c = tone(ramp, 4 if k == 0 else (2 if k == 1 else 1))
            if edge:
                c = tone(ramp, 1)
            canvas.put(fx + i, fy + j, c)


def links(canvas, fx, fy, fw, fh):
    """Le flanc de la chenille : les maillons et leurs axes."""
    ramp = RAMPS["rubber"]
    steel = RAMPS["steel"]
    for j in range(fh):
        for i in range(fw):
            k = i % 3
            c = tone(ramp, 3 if k == 0 else 2)
            if k == 0 and j == fh // 2:
                c = tone(steel, 3)
            if j == 0:
                c = tone(ramp, 4)
            if j == fh - 1:
                c = tone(ramp, 0)
            canvas.put(fx + i, fy + j, c)


def wheel_side(canvas, fx, fy, fw, fh, hub=False):
    """Le flanc d'une roue : jante d'acier, moyeu, boulons."""
    steel = RAMPS["steel"]
    rubber = RAMPS["rubber"]
    cx, cy = (fw - 1) / 2.0, (fh - 1) / 2.0
    r = min(fw, fh) / 2.0
    for j in range(fh):
        for i in range(fw):
            d = math.hypot(i - cx, j - cy) / max(0.5, r)
            if hub:
                c = tone(steel, 4 if d < 0.35 else (2 if d < 0.6 else 3))
                if 0.6 <= d < 0.85 and (i + j) % 2 == 0:
                    c = tone(steel, 5)
            else:
                c = tone(rubber, 2) if d > 0.78 else tone(steel, 3 if d > 0.45 else (5 if d < 0.2 else 2))
            canvas.put(fx + i, fy + j, c)


def emblem(canvas, cx, cy, ramp):
    """L'embleme de la moissonneuse : un soleil sur une dune, symetrique, peint au pochoir."""
    art = [
        "...###...",
        "..#####..",
        "..#####..",
        "...###...",
        ".........",
        "....#....",
        "..#####..",
        ".#######.",
        "#########",
    ]
    for dy, row in enumerate(art):
        for dx, c in enumerate(row):
            if c == "#":
                canvas.put(cx - 4 + dx, cy - 4 + dy, tone(ramp, 1))


def louvers(canvas, fx, fy, fw, fh, ramp):
    for j in range(fh):
        for i in range(fw):
            c = tone(ramp, 1 if j % 2 == 0 else 3)
            if i == 0 or i == fw - 1:
                c = tone(ramp, 0)
            canvas.put(fx + i, fy + j, c)


def paint(parts):
    canvas = Canvas()
    done = set()
    for part in walk(parts):
        for box in part.boxes:
            if box.uv in done:
                continue
            done.add(box.uv)
            seed = sum(ord(c) for c in (box.key or box.material)) * 31 + box.uv[0] * 7 + box.uv[1]
            rng = random.Random(seed)
            for face, (fx, fy, fw, fh) in face_rects(box).items():
                if fw > 0 and fh > 0:
                    paint_face(canvas, rng, box, face, fx, fy, fw, fh)
    return canvas


def paint_face(canvas, rng, box, face, fx, fy, fw, fh):
    m = box.material
    key = box.key or ""
    upright = face not in ("top", "bottom")
    side = face in ("side_a", "side_b")
    if m in ("light", "light_red", "beacon"):
        light(canvas, fx, fy, fw, fh, {"light": LIGHT, "light_red": RED, "beacon": BEACON}[m])
        return
    if m == "glass":
        glass(canvas, fx, fy, fw, fh)
        return
    if m == "spiceglass":
        frame = RAMPS["steel"]
        for j in range(fh):
            for i in range(fw):
                edge = i == 0 or j == 0 or i == fw - 1 or j == fh - 1
                if edge:
                    canvas.put(fx + i, fy + j, tone(frame, 2 if (i + j) % 2 else 4))
                else:
                    level = 1.0 - j / float(max(1, fh - 1))
                    c = SPICE[3] if level < 0.55 else SPICE[4]
                    if (i * 3 + j * 5) % 7 == 0:
                        c = SPICE[5]
                    canvas.put(fx + i, fy + j, c)
                    canvas.shine(fx + i, fy + j, c)
        return
    if m == "rubber":
        if key.startswith("track_run") or key.startswith("track_end"):
            if side:
                links(canvas, fx, fy, fw, fh)
            else:
                tread(canvas, fx, fy, fw, fh, along_width=False)
        elif key.startswith("roller") or key.startswith("sprocket"):
            if side:
                wheel_side(canvas, fx, fy, fw, fh)
            else:
                tread(canvas, fx, fy, fw, fh, along_width=False)
        else:
            apply(canvas, fx, fy, brushed(rng, fw, fh, 2), RAMPS["rubber"])
        return
    if m == "steel" and key.endswith("_hub"):
        if side:
            wheel_side(canvas, fx, fy, fw, fh, hub=True)
        else:
            apply(canvas, fx, fy, brushed(rng, fw, fh, 3), RAMPS["steel"])
        return

    ramp = RAMPS[m]
    # le fond : tole, acier brosse, rouille
    if m in ("paint", "paint_dark"):
        big = fw >= 10 and fh >= 8
        g = plate(rng, fw, fh, base=3, seams=big, rivets=fw >= 6 and fh >= 5, panel=11)
    elif m == "rust":
        g = plate(rng, fw, fh, base=3, seams=fw >= 12 and fh >= 12, rivets=fw >= 6 and fh >= 6, panel=9)
    elif m == "steel_dark" and key in ("chassis", "bumper", "tank", "toolbox", "cab_roof", "intake_back",
                                       "track_frame"):
        g = plate(rng, fw, fh, base=3, seams=fw >= 14 and fh >= 10, rivets=fw >= 8 and fh >= 6, panel=12)
    else:
        g = brushed(rng, fw, fh, 3)
    apply(canvas, fx, fy, g, ramp)

    # les motifs propres a chaque piece
    if key in ("roof", "walkway") and face == "top":
        for j in range(fh):
            for i in range(fw):
                bar = i % 3 == 0 or j % 3 == 0
                canvas.put(fx + i, fy + j, tone(ramp, 3 if bar else 0) if 0 < i < fw - 1 and 0 < j < fh - 1
                           else tone(ramp, 1))
    if key == "hatch" and face == "top":
        for j in range(1, fh - 1):
            for i in range(1, fw - 1):
                if (i + 2 * j) % 4 == 0:
                    canvas.put(fx + i, fy + j, tone(ramp, 5))
                elif (i + 2 * j) % 4 == 1:
                    canvas.put(fx + i, fy + j, tone(ramp, 2))
    if key == "hull" and side:
        emblem(canvas, fx + fw // 2, fy + 6, RAMPS["paint"])
        for i in range(1, fw - 1):
            canvas.put(fx + i, fy + 11, tone(RAMPS["paint"], 1))
            canvas.put(fx + i, fy + 12, tone(RAMPS["paint"], 4))
    if key == "hull" and face == "back":
        louvers(canvas, fx + fw // 2 - 8, fy + 3, 16, 8, RAMPS["steel_dark"])
    if key == "cab" and upright:
        top, bottom = 2, 11
        if face == "front":
            glass(canvas, fx + 2, fy + top, fw - 4, bottom - top)
            for j in range(top, bottom):
                canvas.put(fx + fw // 2, fy + j, tone(ramp, 1))
        elif side:
            glass(canvas, fx + 2, fy + top, fw - 4, bottom - top)
            for j in range(bottom + 1, fh - 2):
                canvas.put(fx + 2, fy + j, tone(ramp, 1))
                canvas.put(fx + fw - 3, fy + j, tone(ramp, 1))
            for i in range(2, fw - 2):
                canvas.put(fx + i, fy + bottom + 1, tone(ramp, 1))
            canvas.put(fx + fw - 5, fy + bottom + 5, tone(RAMPS["steel"], 4))
            canvas.put(fx + fw - 6, fy + bottom + 5, tone(RAMPS["steel"], 2))
        else:
            glass(canvas, fx + 4, fy + top, fw - 8, 6)
    if key == "intake_hood" and face == "front":
        hazard(canvas, fx, fy, fw, fh)
    if key == "intake_cheek" and side:
        hazard(canvas, fx, fy, fw, fh, period=2)
    if key == "guard_front" and face in ("front", "back"):
        hazard(canvas, fx, fy, fw, fh, period=2)
    if key == "intake_back" and face == "front":
        louvers(canvas, fx, fy, fw, fh, RAMPS["steel_dark"])
    if key == "elevator" and upright:
        for j in range(2, fh - 2):
            for i in range(2, fw - 2):
                canvas.put(fx + i, fy + j, tone(RAMPS["steel_dark"], 0 if (j % 4) else 3))
        for j in range(2, fh - 2):
            if j % 4 == 0:
                canvas.put(fx + 2, fy + j, tone(RAMPS["rust"], 4))
    if key == "toolbox" and upright and fw >= 6:
        for i in range(1, fw - 1):
            canvas.put(fx + i, fy + fh // 2, tone(ramp, 1))
        canvas.put(fx + fw // 2, fy + fh // 2 + 1, tone(RAMPS["steel"], 4))
    if key == "tank" and upright:
        for j in range(1, fh - 1):
            for x in (3, fw - 4):
                if 0 < x < fw - 1:
                    canvas.put(fx + x, fy + j, tone(RAMPS["steel"], 3))
    if key in ("stack", "vent") and upright:
        for j in range(min(3, fh)):
            for i in range(fw):
                canvas.put(fx + i, fy + j, tone(RAMPS["steel_dark"], 0))
    if key == "post" or key.startswith("rail"):
        for j in range(fh):
            for i in range(fw):
                if ((i + j) // 2) % 2 == 0:
                    canvas.put(fx + i, fy + j, HAZARD_Y[2])
    if key == "tooth":
        for j in range(fh):
            for i in range(fw):
                canvas.put(fx + i, fy + j, tone(RAMPS["steel"], 5 if j == 0 else 3))
    if key.startswith("cyclone") and upright and m == "steel":
        for j in range(fh):
            if j % 5 == 2:
                for i in range(fw):
                    canvas.put(fx + i, fy + j, tone(ramp, 1))
                    if j + 1 < fh:
                        canvas.put(fx + i, fy + j + 1, tone(ramp, 4))

    # l'usure, partout ou il y a de la peinture ou de la tole
    if m in ("paint", "paint_dark", "steel_dark", "rust", "steel"):
        weather(canvas, rng, fx, fy, fw, fh, upright,
                rust_amount=0.9 if m in ("paint", "paint_dark") else 0.4,
                dust_amount=1.0 if box.y < 16 else 0.4, top=face == "top")


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


def emit(part, parent_var, parent_pivot, lines, names):
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
    if var in names:
        raise SystemExit("deux pieces s'appellent %s" % part.name)
    names.add(var)
    lines.append("        PartDefinition %s = %s.addOrReplaceChild(\"%s\", %s,\n"
                 "                PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s));" % (
                     var, parent_var, part.name, cubes, num(ox), num(oy), num(oz),
                     num(part.rot[0]), num(part.rot[1]), num(part.rot[2])))
    for child in part.children:
        emit(child, var, part.pivot, lines, names)


def java(parts):
    lines = []
    names = set()
    for part in parts:
        emit(part, "root", (0, 0, 0), lines, names)
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
""" % (body, W, H)


# -- L'apercu, texture appliquee -------------------------------------------------------------

def rotate(point, rot):
    x, y, z = point
    rx, ry, rz = rot
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return x, y, z


def texel_faces(part, transform, out, canvas):
    """Chaque face de chaque boite, decoupee en ses texels : (4 coins 3D, couleur, lueur, face)."""
    def place(point):
        local = (point[0] - part.pivot[0], part.pivot[1] - point[1], point[2] - part.pivot[2])
        x, y, z = rotate(local, part.rot)
        return transform((x + part.pivot[0], part.pivot[1] - y, z + part.pivot[2]))

    for box in part.boxes:
        x0, y0, z0 = box.x, box.y, box.z
        x1, y1, z1 = x0 + box.w, y0 + box.h, z0 + box.d
        rects = face_rects(box)
        faces = {
            "front": ((x1, y1, z0), (-box.w, 0, 0), (0, -box.h, 0)),
            "back": ((x0, y1, z1), (box.w, 0, 0), (0, -box.h, 0)),
            "side_a": ((x1, y1, z1), (0, 0, -box.d), (0, -box.h, 0)),
            "side_b": ((x0, y1, z0), (0, 0, box.d), (0, -box.h, 0)),
            "top": ((x1, y1, z1), (-box.w, 0, 0), (0, 0, -box.d)),
            "bottom": ((x1, y0, z0), (-box.w, 0, 0), (0, 0, box.d)),
        }
        for face, (origin, du, dv) in faces.items():
            fx, fy, fw, fh = rects[face]
            if fw <= 0 or fh <= 0:
                continue
            for j in range(fh):
                for i in range(fw):
                    if not (0 <= fy + j < H and 0 <= fx + i < W):
                        continue
                    color = canvas.color[fy + j][fx + i]
                    if color is None:
                        continue
                    glow = canvas.glow[fy + j][fx + i]
                    a, bb = i / float(fw), (i + 1) / float(fw)
                    c, d = j / float(fh), (j + 1) / float(fh)
                    corners = []
                    for uu, vv in ((a, c), (bb, c), (bb, d), (a, d)):
                        p = (origin[0] + du[0] * uu + dv[0] * vv, origin[1] + du[1] * uu + dv[1] * vv,
                             origin[2] + du[2] * uu + dv[2] * vv)
                        corners.append(place(p))
                    out.append((corners, color, glow, face))
    for child in part.children:
        texel_faces(child, place, out, canvas)


def preview(parts, folder, canvas):
    from PIL import Image, ImageDraw
    os.makedirs(folder, exist_ok=True)
    texels = []
    for part in parts:
        texel_faces(part, lambda p: p, texels, canvas)
    views = {"avant": (-0.75, 0.38), "arriere": (2.35, 0.38), "profil": (-math.pi / 2, 0.10),
             "dessus": (-0.5, 0.95), "trois_quarts": (0.75, 0.30)}
    light_of = {"top": 1.0, "bottom": 0.5, "front": 0.8, "back": 0.8, "side_a": 0.6, "side_b": 0.6}
    for view, (yaw, pitch) in views.items():
        projected = []
        for corners, color, glow, face in texels:
            pts = []
            depth = 0.0
            for x, y, z in corners:
                x2, z2 = x * math.cos(yaw) + z * math.sin(yaw), -x * math.sin(yaw) + z * math.cos(yaw)
                y2, z3 = y * math.cos(pitch) + z2 * math.sin(pitch), -y * math.sin(pitch) + z2 * math.cos(pitch)
                pts.append((x2, y2))
                depth += z3
            area = 0.0
            for k in range(4):
                xa, ya = pts[k]
                xb, yb = pts[(k + 1) % 4]
                area += xa * yb - xb * ya
            if area <= 0:
                continue
            shade = light_of[face]
            c = glow if glow is not None else tuple(int(v * shade) for v in color)
            projected.append((depth / 4.0, pts, c))
        projected.sort(key=lambda item: -item[0])
        scale = 10
        image = Image.new("RGB", (1000, 760), (236, 210, 160))
        draw = ImageDraw.Draw(image)
        for _depth, pts, c in projected:
            draw.polygon([(500 + px * scale, 560 - py * scale) for px, py in pts], fill=c)
        image.save(os.path.join(folder, "moissonneuse_%s.png" % view))


def main():
    parts = build()
    pack(parts)
    canvas = paint(parts)
    write_png(PNG, canvas.color)
    write_png(GLOW, canvas.glow)
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8") as fh:
        fh.write(java(parts))
    if len(sys.argv) > 1:
        preview(parts, sys.argv[1], canvas)


if __name__ == "__main__":
    main()

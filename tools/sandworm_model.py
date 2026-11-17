#!/usr/bin/env python3
"""Dessine le ver des sables d'Arrakis : sa geometrie et sa texture.

Un ver geant dessine pour Berskar, reprise d'aucun film ni d'aucun jeu : un
corps en anneaux octogonaux qui s'amincit vers la queue, chaque anneau
retombant sur le suivant comme une tuile ; une rangee d'ecussons de croute
sur le dos, des verrues de sable cuit ; une tete en tube dont la levre se
retrousse, une gueule ronde gardee par huit crocs articules qui s'ouvrent et
se referment, deux couronnes de dents qui tournent au fond, et la gorge, sombre.

Le modele est dessine petit : le rendu le grandit deux fois et demie. Dresse,
le ver depasse vingt-cinq blocs ; sa gueule en fait plus de cinq.

La texture suit la maniere de PalaMod : peu de tons par matiere, une lumiere
venue d'en haut, des contours d'un ton sombre, des reflets francs ; une peau
ocre de sable cuit, en cotes fines qui ondulent, marbree, du sable pris dans
les plis.

    python3 -I tools/sandworm_model.py [dossier_apercu]

Le script ecrit :

* `client/arrakis/SandWormGeometry.java` — la geometrie, en code vanilla ;
* `assets/dohy/textures/entity/sandworm.png` — la texture.

Reperes : x vers la droite, y vers le haut depuis le sol, en pixels du modele.
"""

import math
import os
import random
import struct
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
JAVA = os.path.join(ROOT, "src", "main", "java", "fr", "atraxterra", "dohy", "client", "arrakis",
                    "SandWormGeometry.java")
PNG = os.path.join(ROOT, "src", "main", "resources", "assets", "dohy", "textures", "entity", "sandworm.png")
W, H = 512, 512

R = 18           # rayon du corps sous la tete ; il s'amincit vers la queue
L = 16           # hauteur d'un anneau
SEGMENTS = 9     # anneaux du corps, sous la tete
HEAD = 14        # hauteur du tube de la tete
TUSKS = 8
TUSK = 27        # hauteur des crocs dresses au-dessus de la tete

# Les tons, du contour a la lumiere. La peau est un ocre de sable cuit : plus
# claire que le sable a l'ombre, plus sombre en plein jour, pour se detacher
# de l'erg sans jurer avec lui.
RAMPS = {
    "hide": [(66, 46, 30), (104, 76, 48), (140, 106, 68), (172, 134, 88), (200, 162, 112), (226, 194, 146)],
    "ridge": [(58, 40, 26), (94, 68, 42), (130, 98, 62), (164, 128, 84), (194, 158, 110), (222, 192, 146)],
    "crust": [(56, 42, 30), (88, 68, 48), (120, 96, 70), (150, 124, 94), (180, 154, 120), (210, 188, 154)],
    "flesh": [(52, 12, 16), (90, 22, 26), (130, 36, 38), (170, 60, 54), (204, 94, 80), (232, 138, 116)],
    "bone": [(112, 96, 72), (150, 132, 102), (186, 170, 136), (214, 202, 170), (236, 228, 204), (250, 246, 232)],
    "throat": [(12, 4, 6), (28, 8, 10), (50, 14, 16), (76, 24, 24), (102, 36, 34), (128, 52, 46)],
    "sand": [(150, 110, 62), (182, 142, 88), (206, 170, 114), (224, 192, 138), (238, 212, 164), (250, 232, 196)],
}


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


def octagon(part, name, y0, y1, r, material, key):
    """Un fut octogonal : une croix, et la meme croix tournee d'un huitieme de tour."""
    h = y1 - y0
    s = 2.0 * r * math.tan(math.pi / 8.0)
    part.box(-r, y0, -s / 2.0, 2 * r, h, s, material, key + "_a")
    part.box(-s / 2.0, y0, -r, s, h, 2 * r, material, key + "_b")
    turned = part.child(Part(name + "_o", (0, y0, 0), (0.0, math.pi / 4, 0.0)))
    turned.box(-r, y0 + 0.02, -s / 2.0, 2 * r, h - 0.04, s, material, key + "_a")
    turned.box(-s / 2.0, y0 + 0.02, -r, s, h - 0.04, 2 * r, material, key + "_b")


def radius(i):
    """Le corps s'amincit vers la queue : trois anneaux par epaisseur."""
    return R - (2 - i // 3) * 1.5 if i < SEGMENTS else R


def build():
    rng = random.Random(1965)
    parts = []
    parent = None
    for i in range(SEGMENTS):
        y0 = i * L
        r = radius(i)
        seg = Part("segment%d" % i, (0, y0, 0))
        size = "%d" % int(r * 2)
        octagon(seg, "segment%d_body" % i, y0 + 1, y0 + L, r, "hide", "hide%s_%s" % (size, "ab"[i % 2]))
        # le bord de l'anneau retombe sur le suivant, comme une tuile
        octagon(seg, "segment%d_fold" % i, y0, y0 + 3, r + 1.5, "ridge", "ridge" + size)
        # les ecussons du dos : une rangee de plaques de croute, d'un seul cote
        for k, (angle, w, h) in enumerate(((0.0, 9, 7), (0.62, 6, 5), (-0.62, 6, 5))):
            if k > 0 and rng.random() < 0.35:
                continue
            plate = seg.child(Part("segment%d_plate%d" % (i, k), (0, y0, 0), (0.0, angle + rng.uniform(-0.08, 0.08),
                                                                              0.0)))
            yy = y0 + 5 + rng.uniform(0, 2.5)
            plate.box(-w / 2.0, yy, r - 0.6, w, h, 2.2, "crust", "scute_%dx%d" % (w, h))
            plate.box(-w / 2.0 + 1, yy + 1, r + 1.4, w - 2, h - 3, 0.8, "crust", "scute_top_%dx%d" % (w - 2, h - 3))
            # la carene de l'ecusson, un bourrelet en long
            plate.box(-1, yy + 0.5, r + 2.0, 2, h - 1, 1.2, "crust", "keel_%d" % (h - 1))
        # des verrues de sable cuit, semees au hasard
        for k in range(3):
            angle = rng.uniform(0.9, 2 * math.pi - 0.9)
            wart = seg.child(Part("segment%d_wart%d" % (i, k), (0, y0, 0), (0.0, angle, 0.0)))
            w = rng.choice((2, 3, 3, 4))
            h = rng.choice((2, 2, 3))
            wart.box(-w / 2.0, y0 + rng.uniform(4, L - h - 1), r - 0.4, w, h, 1.4, "crust", "wart_%dx%d" % (w, h))
        if parent is None:
            parts.append(seg)
        else:
            parent.child(seg)
        parent = seg

    # la tete : un tube a levre retroussee, la gorge, les dents qui tournent, les crocs
    y0 = SEGMENTS * L
    head = Part("head", (0, y0, 0))
    octagon(head, "head_fold", y0, y0 + 3, R + 2, "ridge", "ridge_head")
    wall_w = 2.0 * R * math.tan(math.pi / 8.0) + 0.6
    lip_w = 2.0 * (R + 3.5) * math.tan(math.pi / 8.0) + 0.8
    for k in range(8):
        side = head.child(Part("head_wall%d" % k, (0, y0, 0), (0.0, k * math.pi / 4, 0.0)))
        side.box(-wall_w / 2.0, y0 + 1, R - 5, wall_w, HEAD - 1, 5, "hide", "wall")
        side.box(-lip_w / 2.0, y0 + HEAD - 3, R - 2, lip_w, 4, 5.5, "ridge", "lip")
        side.box(-wall_w / 2.0 + 1, y0 + HEAD - 4, R - 6.5, wall_w - 2, 4, 1.5, "flesh", "gum")
    octagon(head, "head_throat", y0 - 6, y0 + 3, R - 4.6, "throat", "throat")
    teeth = head.child(Part("teeth", (0, y0, 0)))
    for k in range(24):
        ring = k % 2
        tooth = teeth.child(Part("tooth%d" % k, (0, y0, 0),
                                 (0.0, k * math.pi / 12 + (math.pi / 24 if ring else 0), 0.0)))
        level = y0 + (8 if ring == 0 else 3)
        depth = R - 9.5 if ring == 0 else R - 11.5
        tooth.box(-1, level, depth, 2, 4 if ring == 0 else 3, 4, "bone", "tooth%d" % ring)
    for k in range(TUSKS):
        arm = head.child(Part("tusk_arm%d" % k, (0, y0, 0), (0.0, k * math.pi / 4 + math.pi / 8, 0.0)))
        base_y = y0 + HEAD + 1
        z = R - 1.5
        tusk = arm.child(Part("tusk%d" % k, (0, base_y, z)))
        tusk.box(-3, base_y - 3, z - 3, 6, 10, 6, "bone", "fang_base")
        tusk.box(-3.5, base_y - 3, z + 2.5, 7, 4, 1.5, "ridge", "fang_sheath")
        mid = tusk.child(Part("fang_mid%d" % k, (0, base_y + 7, z), (0.30, 0.0, 0.0)))
        mid.box(-2.5, base_y + 7, z - 2.5, 5, 8, 5, "bone", "fang_mid")
        tip = mid.child(Part("fang_tip%d" % k, (0, base_y + 15, z), (0.34, 0.0, 0.0)))
        tip.box(-2, base_y + 15, z - 2, 4, 6, 4, "bone", "fang_tip")
        point = tip.child(Part("fang_point%d" % k, (0, base_y + 21, z), (0.30, 0.0, 0.0)))
        point.box(-1, base_y + 21, z - 1, 2, 4, 2, "bone", "fang_point")
    parent.child(head)
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


class Canvas:
    def __init__(self):
        self.color = [[None] * W for _ in range(H)]

    def put(self, x, y, c):
        if 0 <= x < W and 0 <= y < H:
            self.color[y][x] = c


def tone(ramp, index):
    return ramp[max(0, min(len(ramp) - 1, index))]


def outline(g, dark=0, light=None):
    fh, fw = len(g), len(g[0])
    if fw < 3 or fh < 3:
        return
    for i in range(fw):
        g[fh - 1][i] = dark
        if light is not None:
            g[0][i] = light
    for j in range(fh):
        g[j][fw - 1] = dark


def hide(rng, fw, fh, upright):
    """Le cuir du ver : des cotes fines en anneaux, qui ondulent et se chevauchent ; une
    arete claire, un flanc, un pli sombre. Des marbrures franches cassent la regularite,
    quelques rides et pores ; pas de briques, pas de bruit."""
    g = [[3] * fw for _ in range(fh)]
    blot = value_noise(fw, fh, rng, 6)
    if not upright:
        for j in range(fh):
            for i in range(fw):
                v = blot[j][i]
                g[j][i] = 2 if v < 0.32 else (3 if v < 0.7 else 4)
        return g
    # le pli de chaque cote ondule doucement, d'un pixel au plus
    drift = value_noise(fw, 4, rng, 7)
    wave = [1 if drift[1][i] > 0.62 else (-1 if drift[1][i] < 0.3 else 0) for i in range(fw)]
    bands = []
    y = -rng.randrange(0, 3)
    while y < fh + 2:
        h = rng.choice((3, 4, 4, 5))
        bands.append((y, h))
        y += h
    for i in range(fw):
        for top, h in bands:
            for k in range(h):
                j = top + k + wave[i]
                if not 0 <= j < fh:
                    continue
                if k == h - 1:
                    index = 1                                      # le pli sous la cote
                elif k == 0:
                    index = 5 if blot[j][i] > 0.35 else 4          # l'arete, en pleine lumiere
                elif k == h - 2:
                    index = 2                                      # le bas de la cote, deja dans l'ombre
                else:
                    index = 3
                v = blot[j][i]
                if 0 < k < h - 1:
                    index += -1 if v < 0.26 else (1 if v > 0.8 else 0)
                g[j][i] = max(0, min(5, index))
    # des rides courtes et quelques pores
    for _ in range(max(1, fw * fh // 140)):
        x, y = rng.randrange(fw), rng.randrange(max(1, fh - 1))
        if g[y][x] >= 2 and g[y + 1][x] >= 2:
            g[y][x] = 1
            g[y + 1][x] = 2
    for _ in range(max(1, fw * fh // 120)):
        x, y = rng.randrange(fw), rng.randrange(fh)
        if g[y][x] == 3:
            g[y][x] = 1
    return g


def sand_in_folds(canvas, rng, fx, fy, g, amount):
    """Du sable pris dans les plis : les pixels les plus sombres, parfois, s'eclaircissent."""
    sand = RAMPS["sand"]
    for j, row in enumerate(g):
        for i, index in enumerate(row):
            if index <= 1 and rng.random() < amount:
                canvas.put(fx + i, fy + j, tone(sand, 1 if rng.random() < 0.5 else 2))


def paint(parts):
    canvas = Canvas()
    done = set()
    for part in walk(parts):
        for box in part.boxes:
            if box.uv in done:
                continue
            done.add(box.uv)
            rng = random.Random(sum(ord(c) for c in (box.key or "")) * 131 + box.uv[0] * 7 + box.uv[1])
            for face, (fx, fy, fw, fh) in face_rects(box).items():
                if fw > 0 and fh > 0:
                    paint_face(canvas, rng, box, face, fx, fy, fw, fh)
    return canvas


def put_grid(canvas, fx, fy, g, ramp):
    for j, row in enumerate(g):
        for i, index in enumerate(row):
            canvas.put(fx + i, fy + j, tone(ramp, index))


def sand_dust(canvas, rng, fx, fy, fw, fh, amount):
    sand = RAMPS["sand"]
    for j in range(fh):
        for i in range(fw):
            if rng.random() < amount:
                canvas.put(fx + i, fy + j, tone(sand, 2 if rng.random() < 0.6 else 3))


def plate(rng, fw, fh, upright):
    """Une plaque de croute : bombee, eclairee d'en haut a gauche, cernee d'un ton sombre,
    grenue, fendue d'une ou deux craquelures."""
    g = [[3] * fw for _ in range(fh)]
    for j in range(fh):
        for i in range(fw):
            r = rng.random()
            g[j][i] = 2 if r < 0.2 else (4 if r > 0.82 else 3)
    if fw >= 3 and fh >= 3:
        for i in range(fw):
            g[0][i] = 5 if upright else 4
            g[fh - 1][i] = 0
        for j in range(fh):
            g[j][0] = max(g[j][0], 4) if 0 < j < fh - 1 else g[j][0]
            g[j][fw - 1] = 1
        if fw >= 5 and fh >= 4 and upright:
            x = rng.randrange(1, fw - 1)
            for j in range(1, fh - 1):
                if rng.random() < 0.8:
                    g[j][x] = 1
                x = max(1, min(fw - 2, x + rng.choice((-1, 0, 0, 1))))
    return g


def paint_face(canvas, rng, box, face, fx, fy, fw, fh):
    m = box.material
    key = box.key or ""
    upright = face not in ("top", "bottom")
    ramp = RAMPS[m]
    if m == "hide":
        if key == "wall" and face == "front":
            # l'interieur de la gueule : de la chair cotelee, plus sombre vers le fond
            flesh = RAMPS["flesh"]
            for j in range(fh):
                for i in range(fw):
                    index = (3 if i % 3 else 1) - (1 if j > fh * 0.7 else 0) + (1 if j == 0 else 0)
                    canvas.put(fx + i, fy + j, tone(flesh, index))
            return
        g = hide(rng, fw, fh, upright)
        put_grid(canvas, fx, fy, g, ramp)
        sand_in_folds(canvas, rng, fx, fy, g, 0.12 if upright else 0.04)
        if not upright:
            sand_dust(canvas, rng, fx, fy, fw, fh, 0.05)
        return
    if m == "ridge":
        # le bord de l'anneau : une arete claire, un flanc, une ombre franche dessous
        g = [[3] * fw for _ in range(fh)]
        for j in range(fh):
            for i in range(fw):
                if upright:
                    index = 5 if j == 0 else (4 if j == 1 else (3 if j < fh - 1 else 1))
                    if j == 0 and rng.random() < 0.25:
                        index = 4
                    if 0 < j < fh - 1 and rng.random() < 0.12:
                        index -= 1
                elif face == "top":
                    index = 4 if rng.random() < 0.7 else 3
                else:
                    index = 0 if rng.random() < 0.7 else 1
                g[j][i] = index
        if upright:
            x = rng.randrange(0, 5)
            while x < fw:
                for j in range(1, fh - 1):
                    g[j][x] = 1
                x += rng.choice((5, 6, 7, 8))
        put_grid(canvas, fx, fy, g, ramp)
        if face == "top":
            sand_dust(canvas, rng, fx, fy, fw, fh, 0.22)
        return
    if m == "crust":
        g = plate(rng, fw, fh, upright)
        put_grid(canvas, fx, fy, g, ramp)
        sand_dust(canvas, rng, fx, fy, fw, fh, 0.10 if upright else 0.25)
        return
    if m == "bone":
        for j in range(fh):
            for i in range(fw):
                t = j / float(max(1, fh - 1))
                index = 4 if t < 0.3 else (3 if t < 0.75 else 2)
                if key in ("fang_base", "tooth0", "tooth1") and t > 0.8:
                    index = 1
                if i == 0:
                    index += 1
                if i == fw - 1:
                    index -= 2
                if (i * 7 + j * 3) % 11 == 0:
                    index -= 1
                if key == "fang_base" and upright and j % 4 == 3:
                    index -= 1                                # les stries de croissance
                canvas.put(fx + i, fy + j, tone(ramp, index))
        if key == "fang_point" and face == "top":
            for i in range(fw):
                canvas.put(fx + i, fy, tone(ramp, 5))
        return
    if m == "flesh":
        for j in range(fh):
            for i in range(fw):
                index = 3 if (i % 3) else 2
                if j == 0:
                    index = 4
                if j == fh - 1:
                    index = 1
                canvas.put(fx + i, fy + j, tone(ramp, index))
        return
    if m == "throat":
        cx, cy = (fw - 1) / 2.0, (fh - 1) / 2.0
        for j in range(fh):
            for i in range(fw):
                if face == "top":
                    d = math.hypot(i - cx, j - cy) / max(1.0, min(fw, fh) / 2.0)
                    ring = int(d * 6)
                    index = [0, 0, 1, 2, 1, 3, 2][min(6, ring)]
                    if ring in (3, 5) and (i + j) % 2 == 0:
                        index = 4
                else:
                    index = 1 if j % 3 else 2
                canvas.put(fx + i, fy + j, tone(ramp, index))
        return


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
 * GENERE par tools/sandworm_model.py. Ne pas modifier a la main : relancer le script.
 *
 * <p>La geometrie du ver des sables, dessinee pour Berskar, a la moitie de sa taille.
 */
@SuppressWarnings("unused")
public final class SandWormGeometry {

    /** Anneaux du corps, sous la tete ; chacun est l'enfant du precedent. */
    public static final int SEGMENTS = %d;
    /** Crocs autour de la gueule. */
    public static final int TUSKS = %d;
    /** Hauteur du ver debout, en pixels du modele (avant le doublement du rendu). */
    public static final float LENGTH = %sF;

    private SandWormGeometry() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
%s
        return LayerDefinition.create(mesh, %d, %d);
    }
}
""" % (SEGMENTS, TUSKS, num(SEGMENTS * L + HEAD + TUSK)[:-1], body, W, H)


# -- L'apercu, texture appliquee -------------------------------------------------------------

def rotate(point, rot):
    x, y, z = point
    rx, ry, rz = rot
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return x, y, z


BEND = 0.035     # la courbure du corps dresse, par anneau (le modele Java fait de meme)


def texel_faces(part, transform, out, canvas, open_angle):
    rot = part.rot
    if part.name.startswith("tusk") and not part.name.startswith("tusk_arm") and part.name[4:].isdigit():
        rot = (open_angle, 0.0, 0.0)
    if part.name.startswith("segment") and part.name[7:].isdigit() and part.name != "segment0":
        rot = (BEND, 0.0, 0.0)

    def place(point):
        local = (point[0] - part.pivot[0], part.pivot[1] - point[1], point[2] - part.pivot[2])
        x, y, z = rotate(local, rot)
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
                    color = canvas.color[fy + j][fx + i] if 0 <= fy + j < H and 0 <= fx + i < W else None
                    if color is None:
                        continue
                    a, bb = i / float(fw), (i + 1) / float(fw)
                    c, d = j / float(fh), (j + 1) / float(fh)
                    corners = []
                    for uu, vv in ((a, c), (bb, c), (bb, d), (a, d)):
                        p = (origin[0] + du[0] * uu + dv[0] * vv, origin[1] + du[1] * uu + dv[1] * vv,
                             origin[2] + du[2] * uu + dv[2] * vv)
                        corners.append(place(p))
                    out.append((corners, color, face))
    for child in part.children:
        texel_faces(child, place, out, canvas, open_angle)


def preview(parts, folder, canvas):
    from PIL import Image, ImageDraw
    os.makedirs(folder, exist_ok=True)
    light_of = {"top": 1.0, "bottom": 0.5, "front": 0.8, "back": 0.8, "side_a": 0.62, "side_b": 0.62}
    for label, open_angle, yaw, pitch, scale in (("ouvert", -0.9, -0.6, 0.5, 2.6),
                                                 ("ferme", 0.5, -0.6, 0.5, 2.6),
                                                 ("gueule", -0.9, -0.3, 1.25, 7.0)):
        texels = []
        for part in parts:
            texel_faces(part, lambda p: p, texels, canvas, open_angle)
        projected = []
        for corners, color, face in texels:
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
            c = tuple(int(v * light_of[face]) for v in color)
            projected.append((depth / 4.0, pts, c))
        projected.sort(key=lambda item: -item[0])
        image = Image.new("RGB", (900, 1000), (236, 210, 160))
        draw = ImageDraw.Draw(image)
        top = SEGMENTS * L + HEAD
        for _depth, pts, c in projected:
            if label == "gueule":
                draw.polygon([(450 + px * scale, 500 - (py - top * math.cos(pitch)) * scale) for px, py in pts],
                             fill=c)
            else:
                draw.polygon([(450 + px * scale, 960 - py * scale) for px, py in pts], fill=c)
        image.save(os.path.join(folder, "ver_%s.png" % label))


def main():
    parts = build()
    pack(parts)
    canvas = paint(parts)
    write_png(PNG, canvas.color)
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8") as fh:
        fh.write(java(parts))
    if len(sys.argv) > 1:
        preview(parts, sys.argv[1], canvas)


if __name__ == "__main__":
    main()

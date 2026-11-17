#!/usr/bin/env python3
"""Dessine la chambre cryogenique : sa geometrie, ses textures, l'ADN et la seringue.

Une colonne de cryogenie dessinee pour Berskar : un socle a marche en tole
striee, une console a ecran sur l'avant, un rebord a liseret de lumiere, une
cuve de verre givre tenue par quatre piliers et trois meneaux, des conduites
de froid aux angles, un collier a lumiere, un chapiteau a ouies, une trappe et
un emetteur au sommet. Trois blocs sur trois, cinq de haut.

La texture suit la maniere des textures de PalaMod, comme la moissonneuse
(tools/harvester_model.py) : peu de tons par matiere, des toles biseautees
(contour sombre, lisere clair en haut a gauche), des joints, des rivets, et
des accents qui brillent. Deux texels par pixel de modele : la chambre est
grande, elle se regarde de pres.

    python3 -I tools/cryo_chamber_assets.py [dossier_apercu]

Le script ecrit :

* models/block/cryo_chamber_{lower,upper,glass,part}.json : trois morceaux
  poses chacun sur un bloc de la colonne centrale (un modele de bloc ne sort
  pas de -16..32), et le bloc sans dessin des autres cases ;
* models/item/cryo_chamber.json : la chambre en miniature, pour l'inventaire ;
* blockstates/cryo_chamber.json, items/cryo_chamber.json ;
* textures/block/cryo_chamber{,_glass,_particle}.png ;
* l'ADN (fiole, liquide, bague) et la seringue d'extraction, en 32 x 32.

Reperes : la chambre occupe x 0..48, y 0..80, z 0..48 en pixels de modele,
l'avant au nord (z = 0). Le bloc maitre est la case (1, 0, 1) ; les
morceaux sont poses sur (1, 1, 1), (1, 2, 1) et (1, 4, 1).
"""

import json
import math
import os
import random
import struct
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dohy")
NS = "dohy"
DENS = 2

# -- Les matieres -------------------------------------------------------------------------

RAMPS = {
    # acier bleui des toles, du contour au reflet
    "steel": [(14, 17, 22), (24, 29, 36), (35, 42, 51), (48, 57, 68), (63, 74, 87), (86, 99, 113), (118, 132, 146)],
    # acier sombre du socle et des piliers
    "dark": [(10, 12, 15), (17, 20, 25), (26, 30, 37), (36, 42, 50), (48, 55, 65), (66, 75, 87), (92, 102, 115)],
    # acier clair des garnitures, brosse
    "trim": [(40, 46, 54), (62, 70, 80), (86, 96, 107), (112, 123, 134), (140, 151, 161), (176, 186, 194),
             (214, 222, 228)],
    # la lumiere froide
    "cyan": [(6, 40, 56), (8, 78, 104), (14, 122, 150), (26, 170, 196), (64, 214, 232), (150, 242, 250),
             (226, 255, 255)],
    # le givre
    "frost": [(120, 150, 172), (160, 190, 210), (196, 222, 236), (226, 242, 250), (246, 252, 255)],
}
GLASS = [(46, 92, 150), (64, 120, 182), (86, 148, 210), (120, 178, 232), (168, 212, 246), (220, 240, 255)]


def tone(ramp, index):
    ramp = RAMPS[ramp] if isinstance(ramp, str) else ramp
    return ramp[max(0, min(len(ramp) - 1, index))]


class Img:
    """Une image RGBA en listes, assez petite pour qu'on n'ait pas besoin de PIL."""

    def __init__(self, w, h, fill=(0, 0, 0, 0)):
        self.w, self.h = w, h
        self.px = [[fill] * w for _ in range(h)]

    def put(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = (c[0], c[1], c[2], a) if len(c) == 3 else c

    def get(self, x, y):
        return self.px[y][x]

    def blit(self, other, ox, oy):
        for j in range(other.h):
            for i in range(other.w):
                self.put(ox + i, oy + j, other.px[j][i])


def write_png(path, img):
    raw = b"".join(b"\x00" + bytes(v for p in row for v in p) for row in img.px)

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", img.w, img.h, 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        f.write(chunk(b"IEND", b""))


def value_noise(w, h, rng, cell):
    gw, gh = w // cell + 2, h // cell + 2
    grid = [[rng.random() for _ in range(gw)] for _ in range(gh)]
    out = [[0.0] * w for _ in range(h)]
    for j in range(h):
        for i in range(w):
            gx, gy = i / float(cell), j / float(cell)
            x0, y0 = int(gx), int(gy)
            tx, ty = gx - x0, gy - y0
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = grid[y0][x0] * (1 - tx) + grid[y0][x0 + 1] * tx
            c = grid[y0 + 1][x0] * (1 - tx) + grid[y0 + 1][x0 + 1] * tx
            out[j][i] = a * (1 - ty) + c * ty
    return out


def splits(n, size):
    if n < size + 4:
        return []
    count = max(1, round(n / float(size)))
    return [round(n * k / float(count)) for k in range(1, count)]


# -- Les gestes du peintre ------------------------------------------------------------------

def plate(img, rng, x0, y0, w, h, ramp="steel", base=3, panel=0, rivets=True, mottle=True, bevel=True):
    """Une tole : deux tons marbres, joints en creux, rivets, contour biseaute."""
    if w <= 0 or h <= 0:
        return
    noise = value_noise(w, h, rng, 4) if mottle and w > 2 and h > 2 else None
    for j in range(h):
        for i in range(w):
            k = base
            if noise is not None:
                if noise[j][i] < 0.24:
                    k = base - 1
                elif noise[j][i] > 0.84:
                    k = base + 1
            img.put(x0 + i, y0 + j, tone(ramp, k))
    cols = splits(w, panel) if panel else []
    rows = splits(h, panel) if panel else []
    for c in cols:
        for j in range(h):
            img.put(x0 + c, y0 + j, tone(ramp, base - 2))
            img.put(x0 + c + 1, y0 + j, tone(ramp, base + 1))
    for r in rows:
        for i in range(w):
            img.put(x0 + i, y0 + r, tone(ramp, base - 2))
            img.put(x0 + i, y0 + r + 1, tone(ramp, base + 1))
    if bevel and w >= 3 and h >= 3:
        for i in range(w):
            img.put(x0 + i, y0, tone(ramp, base - 2))
            img.put(x0 + i, y0 + h - 1, tone(ramp, 0))
            if h >= 5 and 0 < i < w - 1:
                img.put(x0 + i, y0 + 1, tone(ramp, base + 2))
        for j in range(h):
            img.put(x0, y0 + j, tone(ramp, base - 2))
            img.put(x0 + w - 1, y0 + j, tone(ramp, 0))
            if w >= 5 and 0 < j < h - 1:
                img.put(x0 + 1, y0 + j, tone(ramp, base + 2))
    if rivets and w >= 8 and h >= 8:
        xs = [3] + [c + 3 for c in cols] + [w - 4]
        ys = [3] + [r + 3 for r in rows] + [h - 4]
        for x in xs:
            for y in ys:
                rivet(img, x0 + x, y0 + y, ramp)


def rivet(img, x, y, ramp="steel"):
    img.put(x, y, tone(ramp, 6))
    img.put(x + 1, y, tone(ramp, 5))
    img.put(x, y + 1, tone(ramp, 4))
    img.put(x + 1, y + 1, tone(ramp, 1))


def bolt(img, cx, cy, ramp="trim"):
    """Un gros boulon a six pans, trois pixels sur trois."""
    for j in range(-1, 2):
        for i in range(-1, 2):
            img.put(cx + i, cy + j, tone(ramp, 3))
    img.put(cx - 1, cy - 1, tone(ramp, 5))
    img.put(cx, cy - 1, tone(ramp, 5))
    img.put(cx - 1, cy, tone(ramp, 4))
    img.put(cx + 1, cy + 1, tone(ramp, 1))
    img.put(cx + 1, cy, tone(ramp, 2))
    img.put(cx, cy + 1, tone(ramp, 2))
    img.put(cx, cy, tone(ramp, 6))


def louvers(img, x0, y0, w, h, ramp="dark"):
    """Des ouies : des lames inclinees, levre claire, creux noir."""
    for j in range(h):
        k = j % 3
        for i in range(w):
            if i == 0 or i == w - 1:
                img.put(x0 + i, y0 + j, tone(ramp, 1))
            elif k == 0:
                img.put(x0 + i, y0 + j, tone(ramp, 5))
            elif k == 1:
                img.put(x0 + i, y0 + j, tone(ramp, 3))
            else:
                img.put(x0 + i, y0 + j, tone(ramp, 0))


def glow_line(img, x0, y0, w, h, pulse=0):
    """Une ligne de lumiere froide : un coeur blanc bleu, des bords cyan."""
    for j in range(h):
        for i in range(w):
            edge = j == 0 or j == h - 1 or (w > 4 and (i == 0 or i == w - 1))
            if edge:
                c = tone("cyan", 3)
            else:
                c = tone("cyan", 5 if (i + pulse) % 7 else 6)
            img.put(x0 + i, y0 + j, c)


def hatch(img, rng, cx, cy, r, ramp="dark"):
    """Une trappe ronde : couronne de rivets, joint en creux, centre bombe."""
    for j in range(-r - 1, r + 2):
        for i in range(-r - 1, r + 2):
            d = math.hypot(i + 0.5, j + 0.5)
            if d > r + 0.5:
                continue
            if d > r - 1.0:
                c = tone(ramp, 0)
            elif d > r - 2.5:
                c = tone(ramp, 4 if (i + j) < 0 else 2)
            else:
                light = 0.5 - (i + j) / (4.0 * r)
                c = tone(ramp, 2 + int(light * 4))
            img.put(cx + i, cy + j, c)
    count = max(6, int(r * 1.2))
    for k in range(count):
        a = 2 * math.pi * k / count
        x = cx + int(round(math.cos(a) * (r - 1.8)))
        y = cy + int(round(math.sin(a) * (r - 1.8)))
        img.put(x, y, tone("trim", 5))


def frost_specks(img, rng, x0, y0, w, h, density=0.04, alpha=None):
    for j in range(h):
        for i in range(w):
            if rng.random() < density:
                c = tone("frost", rng.randrange(2, 5))
                img.put(x0 + i, y0 + j, c if alpha is None else (c[0], c[1], c[2], alpha))


# -- Les faces ------------------------------------------------------------------------------

def paint(skin, w, h, seed):
    """Peint une face de {w} x {h} texels (vue de dehors, le haut en haut)."""
    rng = random.Random(seed)
    img = Img(w, h)
    if skin == "plinth_side":
        plate(img, rng, 0, 0, w, h, "dark", 3, rivets=False)
        for x in range(6, w - 4, 16):
            bolt(img, x, h // 2, "trim")
    elif skin == "plinth_top":
        plate(img, rng, 0, 0, w, h, "dark", 2, rivets=False, mottle=False)
    elif skin == "under":
        plate(img, rng, 0, 0, w, h, "dark", 1, rivets=False, mottle=False, bevel=False)
    elif skin == "rim_side":
        plate(img, rng, 0, 0, w, h, "trim", 2, rivets=False, mottle=False)
        for i in range(w):
            img.put(i, 0, tone("trim", 5))
    elif skin == "rim_top":
        tread(img, rng, 0, 0, w, h)
    elif skin in ("housing_side", "housing_front"):
        plate(img, rng, 0, 0, w, h, "steel", 3, panel=0, rivets=True)
        if skin == "housing_side":
            louvers(img, 10, 4, w - 20, h - 8, "dark")
        else:
            # le logement de la console, et deux voyants
            plate(img, rng, 22, 1, w - 44, h - 2, "dark", 1, rivets=False, mottle=False)
            for k, c in enumerate(((60, 220, 120), (240, 180, 40))):
                x = 10 + k * 5
                img.put(x, h // 2, c)
                img.put(x + 1, h // 2, c)
                img.put(x, h // 2 + 1, tuple(v // 2 for v in c))
                img.put(x + 1, h // 2 + 1, tuple(v // 2 for v in c))
            for k in range(3):
                img.put(w - 12 + k * 2, h // 2, tone("cyan", 4))
    elif skin == "foot_side":
        plate(img, rng, 0, 0, w, h, "dark", 3, rivets=False)
        bolt(img, w // 2, h // 2 - 1, "trim")
        for i in range(1, w - 1):
            img.put(i, h - 3, tone("dark", 1))
    elif skin == "foot_top":
        plate(img, rng, 0, 0, w, h, "dark", 4, rivets=False)
        bolt(img, w // 2, h // 2, "trim")
    elif skin == "console_face":
        screen(img, rng, w, h)
    elif skin == "trim":
        plate(img, rng, 0, 0, w, h, "trim", 3, rivets=False, mottle=False)
    elif skin == "ledge_side":
        plate(img, rng, 0, 0, w, h, "steel", 4, rivets=False, mottle=False)
        for x in range(8, w - 6, 24):
            rivet(img, x, 1, "steel")
    elif skin == "ledge_top":
        ledge_top(img, rng, w, h)
    elif skin == "pillar":
        pillar(img, rng, w, h)
    elif skin == "midbar":
        plate(img, rng, 0, 0, w, h, "dark", 3, rivets=False)
        # la colonne de voyants : l'etat de la cuve
        cx = w // 2 - 1
        for y in range(10, h - 10, 6):
            on = (y // 6) % 3 != 0
            c = tone("cyan", 5 if on else 1)
            img.put(cx, y, c)
            img.put(cx + 1, y, c)
            img.put(cx, y + 1, tone("cyan", 3 if on else 0))
            img.put(cx + 1, y + 1, tone("cyan", 3 if on else 0))
        rivet(img, 2, 3, "dark")
        rivet(img, w - 4, h - 5, "dark")
    elif skin == "midbar_side":
        plate(img, rng, 0, 0, w, h, "dark", 2, rivets=False, mottle=False)
    elif skin == "pipe":
        pipe(img, rng, w, h)
    elif skin == "clamp":
        plate(img, rng, 0, 0, w, h, "trim", 4, rivets=False, mottle=False)
    elif skin == "collar_side":
        plate(img, rng, 0, 0, w, h, "dark", 3, rivets=False)
        for x in range(6, w - 4, 12):
            rivet(img, x, 2, "dark")
            rivet(img, x, h - 4, "dark")
    elif skin == "collar_top":
        plate(img, rng, 0, 0, w, h, "dark", 3, rivets=False, mottle=False)
    elif skin == "ceiling":
        ceiling(img, rng, w, h)
    elif skin in ("cap_side", "cap_front"):
        plate(img, rng, 0, 0, w, h, "steel", 3, panel=28, rivets=True)
        if skin == "cap_side":
            louvers(img, w // 2 - 14, 4, 28, h - 8, "dark")
        else:
            emblem(img, rng, w // 2, h // 2)
    elif skin == "cap_top":
        cap_top(img, rng, w, h)
    elif skin == "step_side":
        plate(img, rng, 0, 0, w, h, "trim", 2, rivets=False, mottle=False)
    elif skin == "step_top":
        plate(img, rng, 0, 0, w, h, "steel", 4, rivets=True)
        hatch(img, rng, w // 2, h // 2, w // 2 - 6, "dark")
    elif skin == "beacon_side":
        for j in range(h):
            for i in range(w):
                c = tone("trim", 2 if j == 0 or j == h - 1 else 3)
                img.put(i, j, c)
        glow_line(img, 1, 2, w - 2, h - 4)
    elif skin == "beacon_top":
        for j in range(h):
            for i in range(w):
                d = math.hypot(i - w / 2 + 0.5, j - h / 2 + 0.5)
                if d < w * 0.22:
                    c = tone("cyan", 6)
                elif d < w * 0.36:
                    c = tone("cyan", 5)
                elif d < w * 0.48:
                    c = tone("cyan", 3)
                else:
                    c = tone("trim", 2)
                img.put(i, j, c)
    elif skin == "glow":
        glow_line(img, 0, 0, w, h, seed % 7)
    elif skin == "emitter":
        emitter(img, w, h)
    elif skin == "glass":
        glass(img, rng, w, h)
    else:
        raise ValueError(skin)
    return img


def tread(img, rng, x0, y0, w, h):
    """Une tole striee : des grains en diagonale, comme un plancher de machine."""
    for j in range(h):
        for i in range(w):
            img.put(x0 + i, y0 + j, tone("dark", 3))
    for j in range(0, h, 4):
        for i in range(0, w, 4):
            o = 2 if (j // 4) % 2 else 0
            x, y = x0 + i + o, y0 + j
            img.put(x, y, tone("dark", 5))
            img.put(x + 1, y + 1, tone("dark", 1))
    for i in range(w):
        img.put(x0 + i, y0, tone("dark", 1))
        img.put(x0 + i, y0 + h - 1, tone("dark", 0))
    for j in range(h):
        img.put(x0, y0 + j, tone("dark", 1))
        img.put(x0 + w - 1, y0 + j, tone("dark", 0))


def screen(img, rng, w, h):
    """L'ecran de la console : cadre, verre sombre, une double helice et des barres."""
    for j in range(h):
        for i in range(w):
            img.put(i, j, tone("trim", 2))
    for i in range(w):
        img.put(i, 0, tone("trim", 4))
        img.put(i, h - 1, tone("trim", 0))
    for j in range(h):
        img.put(0, j, tone("trim", 4))
        img.put(w - 1, j, tone("trim", 0))
    sx, sy, sw, sh = 2, 2, w - 4, h - 4
    for j in range(sh):
        for i in range(sw):
            img.put(sx + i, sy + j, (6, 22, 30) if (j % 2) else (8, 28, 38))
    # la double helice, a gauche
    for i in range(min(10, sw - 2)):
        a = i / 10.0 * 2 * math.pi
        y1 = sy + sh / 2 + math.sin(a) * (sh / 2 - 1.5)
        y2 = sy + sh / 2 - math.sin(a) * (sh / 2 - 1.5)
        img.put(sx + 1 + i, int(round(y1 - 0.5)), tone("cyan", 5))
        img.put(sx + 1 + i, int(round(y2 - 0.5)), tone("cyan", 4))
        if i % 3 == 1:
            for y in range(int(min(y1, y2)), int(max(y1, y2))):
                img.put(sx + 1 + i, y, tone("cyan", 2))
    # des lignes de releve, a droite
    for k, y in enumerate(range(sy + 1, sy + sh - 1, 2)):
        length = 4 + (k * 5 + 3) % max(5, sw - 18)
        for i in range(length):
            img.put(sx + 14 + i, y, tone("cyan", 4 if i < length - 1 else 6))
    for i in range(sw):
        if (i * 7) % 11 < 2:
            img.put(sx + i, sy, tone("cyan", 2))


def ledge_top(img, rng, w, h):
    """Le dessus du rebord : la bordure en tole, le plancher de la cuve en caillebotis."""
    plate(img, rng, 0, 0, w, h, "steel", 4, rivets=False, mottle=True)
    inner = 10
    for j in range(inner, h - inner):
        for i in range(inner, w - inner):
            grid = (i % 4 == 0) or (j % 4 == 0)
            img.put(i, j, tone("dark", 3 if grid else 1))
            if grid and (i % 4 == 0 and j % 4 == 0):
                img.put(i, j, tone("dark", 4))
    for i in range(inner - 1, w - inner + 1):
        img.put(i, inner - 1, tone("steel", 1))
        img.put(i, h - inner, tone("steel", 5))
    for j in range(inner - 1, h - inner + 1):
        img.put(inner - 1, j, tone("steel", 1))
        img.put(w - inner, j, tone("steel", 5))
    for (x, y) in ((4, 4), (w - 6, 4), (4, h - 6), (w - 6, h - 6)):
        bolt(img, x, y, "trim")


def emitter(img, w, h):
    """L'emetteur du plancher : un disque de lumiere froide en couronnes ; le coin reste vide."""
    cx, cy = w / 2.0, h / 2.0
    r = min(w, h) / 2.0
    for j in range(h):
        for i in range(w):
            d = math.hypot(i + 0.5 - cx, j + 0.5 - cy) / r
            if d > 1.0:
                continue
            if d > 0.86:
                c = tone("trim", 2)
            elif d > 0.78:
                c = tone("cyan", 3)
            elif d > 0.62:
                c = tone("cyan", 1)
            elif d > 0.54:
                c = tone("cyan", 4)
            elif d > 0.30:
                c = tone("cyan", 2) if int(math.degrees(math.atan2(j - cy, i - cx)) // 30) % 2 else tone("cyan", 3)
            else:
                c = tone("cyan", 6 if d < 0.16 else 5)
            img.put(i, j, c)


def pillar(img, rng, w, h):
    """Un pilier : tole sombre, un joint vertical, des rivets, deux conduites et des bagues."""
    plate(img, rng, 0, 0, w, h, "dark", 3, rivets=False)
    for j in range(2, h - 2):
        img.put(3, j, tone("dark", 1))
        img.put(4, j, tone("dark", 5))
        img.put(w - 4, j, tone("dark", 2))
    for y in (4, h // 2 - 1, h - 6):
        for i in range(1, w - 1):
            img.put(i, y, tone("trim", 3))
            img.put(i, y + 1, tone("trim", 1))
        rivet(img, w // 2, y - 3, "dark")
    for j in range(8, h - 8):
        if (j // 3) % 9 == 0:
            img.put(w - 3, j, tone("frost", 2))


def pipe(img, rng, w, h):
    """Une conduite de froid : un cylindre brosse, le givre qui s'y accroche."""
    for j in range(h):
        for i in range(w):
            t = (i + 0.5) / w
            k = 1 if t < 0.15 else 5 if t < 0.35 else 4 if t < 0.6 else 3 if t < 0.85 else 1
            img.put(i, j, tone("trim", k))
    frost_specks(img, rng, 0, 0, w, h, 0.06)


def ceiling(img, rng, w, h):
    """Le plafond de la cuve, vu d'en dessous : une grille et sa couronne de lampes."""
    plate(img, rng, 0, 0, w, h, "dark", 2, rivets=False, mottle=False)
    cx, cy = w // 2, h // 2
    r = min(w, h) // 2 - 12
    for j in range(h):
        for i in range(w):
            d = math.hypot(i - cx + 0.5, j - cy + 0.5)
            if d < r - 3:
                img.put(i, j, tone("dark", 3 if (i % 3 == 0 or j % 3 == 0) else 0))
            elif d < r:
                img.put(i, j, tone("trim", 2))
            elif d < r + 2.2:
                a = math.degrees(math.atan2(j - cy, i - cx)) % 30
                img.put(i, j, tone("cyan", 6) if a < 12 else tone("cyan", 2))


def cap_top(img, rng, w, h):
    """Le dessus du chapiteau : quatre toles, des boulons aux angles."""
    plate(img, rng, 0, 0, w, h, "steel", 3, panel=w // 2, rivets=True)
    for (x, y) in ((5, 5), (w - 6, 5), (5, h - 6), (w - 6, h - 6)):
        bolt(img, x, y, "trim")


def emblem(img, rng, cx, cy):
    """L'embleme de la chambre : un flocon dans un losange, en lumiere froide sur une plaque."""
    for j in range(-7, 8):
        for i in range(-12, 13):
            edge = abs(j) == 7 or abs(i) == 12
            img.put(cx + i, cy + j, tone("dark", 0 if edge else 1))
    for k in range(-5, 6):
        for (dx, dy) in ((k, 0), (0, k), (k, k), (k, -k)):
            if abs(dy) <= 5:
                img.put(cx + dx, cy + dy, tone("cyan", 5 if abs(k) < 4 else 3))
    for (dx, dy) in ((-3, -1), (-3, 1), (3, -1), (3, 1), (-1, -3), (1, -3), (-1, 3), (1, 3)):
        img.put(cx + dx, cy + dy, tone("cyan", 4))
    img.put(cx, cy, tone("cyan", 6))
    for i in (-10, -9, 9, 10):
        img.put(cx + i, cy - 5, tone("cyan", 3))
        img.put(cx + i, cy + 5, tone("cyan", 3))


def glass(img, rng, w, h):
    """Le verre de la cuve : clair au centre pour voir le clone, givre en bas et en haut, deux reflets en biais."""
    noise = value_noise(w, h, rng, 8)
    for j in range(h):
        for i in range(w):
            t = j / float(h - 1)
            n = noise[j][i]
            c = GLASS[3] if n > 0.55 else GLASS[2]
            a = 62 + int(34 * n)
            # le givre monte du bas, tombe du haut, et borde les montants
            frost = 0.0
            if t > 0.84:
                frost = (t - 0.84) / 0.16 * 0.95
            elif t < 0.07:
                frost = (0.07 - t) / 0.07 * 0.7
            if i < 2 or i > w - 3:
                frost = max(frost, 0.45)
            if rng.random() < frost * (0.55 + n * 0.6):
                c = tone("frost", 2 + int(rng.random() * 2))
                a = 185 + int(rng.random() * 40)
            elif rng.random() < 0.006:
                c, a = tone("frost", 3), 150
            # deux reflets fins, en biais
            s = (i + int(j * 0.42)) % 41
            if 0.1 < t < 0.82 and s in (0, 1):
                c, a = GLASS[5], 104
            elif 0.1 < t < 0.82 and s == 6:
                c, a = GLASS[4], 84
            img.put(i, j, (c[0], c[1], c[2], a))
    for j in range(h):
        img.put(0, j, (*tone("frost", 1), 215))
        img.put(w - 1, j, (*tone("frost", 1), 215))


# -- La geometrie ---------------------------------------------------------------------------

SIDES = ("north", "south", "west", "east")


class El:
    """Un element de la chambre, en pixels de modele, et ce que porte chacune de ses faces."""

    def __init__(self, name, frm, to, faces, layer="frame", light=0, skins=None):
        self.name, self.frm, self.to, self.faces = name, frm, to, faces
        self.layer, self.light = layer, light


def face_size(el, d):
    (x0, y0, z0), (x1, y1, z1) = el.frm, el.to
    if d in ("north", "south"):
        return x1 - x0, y1 - y0
    if d in ("east", "west"):
        return z1 - z0, y1 - y0
    return x1 - x0, z1 - z0


def build():
    E = []

    def box(name, frm, to, faces, **kw):
        E.append(El(name, frm, to, faces, **kw))

    def sides(skin, front=None):
        f = {d: skin for d in SIDES}
        if front:
            f["north"] = front
        return f

    # Le socle : une plaque, un rebord, le caisson de la machine et ses quatre pieds.
    box("plinth", (0, 0, 0), (48, 4, 48), {**sides("plinth_side"), "up": "plinth_top", "down": "under"})
    box("rim", (1, 4, 1), (47, 6, 47), {**sides("rim_side"), "up": "rim_top"})
    box("housing", (3, 6, 3), (45, 14, 45), sides("housing_side", "housing_front"))
    for (x, z) in ((0, 0), (41, 0), (0, 41), (41, 41)):
        box("foot", (x, 4, z), (x + 7, 13, z + 7), {**sides("foot_side"), "up": "foot_top"})
    box("console", (15, 7, 2), (33, 13, 3), {"north": "console_face", "up": "trim", "west": "trim", "east": "trim"},
        light=9)
    # Le rebord, son liseret de lumiere, l'emetteur du plancher.
    box("ledge", (1, 14, 1), (47, 17, 47), {**sides("ledge_side"), "up": "ledge_top", "down": "under"})
    box("ledge_glow_n", (4, 15, 0.9), (44, 16, 1), {"north": "glow"}, light=12)
    box("ledge_glow_s", (4, 15, 47), (44, 16, 47.1), {"south": "glow"}, light=12)
    box("ledge_glow_w", (0.9, 15, 4), (1, 16, 44), {"west": "glow"}, light=12)
    box("ledge_glow_e", (47, 15, 4), (47.1, 16, 44), {"east": "glow"}, light=12)
    box("emitter", (14, 17, 14), (34, 17.1, 34), {"up": "emitter"}, light=12)
    # La cuve : quatre piliers, trois meneaux, sept vitres, et les conduites des angles.
    for (x, z) in ((4, 4), (38, 4), (4, 38), (38, 38)):
        box("pillar", (x, 17, z), (x + 6, 62, z + 6), sides("pillar"))
    box("midbar_s", (21, 17, 39), (27, 62, 43), {"south": "midbar", "north": "midbar_side", "west": "midbar_side",
                                                   "east": "midbar_side"})
    box("midbar_w", (5, 17, 21), (9, 62, 27), {"west": "midbar", "east": "midbar_side", "north": "midbar_side",
                                                 "south": "midbar_side"})
    box("midbar_e", (39, 17, 21), (43, 62, 27), {"east": "midbar", "west": "midbar_side", "north": "midbar_side",
                                                   "south": "midbar_side"})
    # l'avant n'a pas de meneau : une seule grande vitre, pour voir le clone
    box("glass_n", (10, 17, 6.5), (38, 62, 7.5), {"north": "glass", "south": "glass"}, layer="glass")
    for (x0, x1) in ((10, 21), (27, 38)):
        box("glass_s", (x0, 17, 40.5), (x1, 62, 41.5), {"south": "glass", "north": "glass"}, layer="glass")
        box("glass_w", (6.5, 17, x0), (7.5, 62, x1), {"west": "glass", "east": "glass"}, layer="glass")
        box("glass_e", (40.5, 17, x0), (41.5, 62, x1), {"east": "glass", "west": "glass"}, layer="glass")
    for (x, z) in ((2, 2), (44, 2), (2, 44), (44, 44)):
        box("pipe", (x, 17, z), (x + 2, 62, z + 2), sides("pipe"))
        for y in (27, 44, 56):
            box("clamp", (x - 0.5, y, z - 0.5), (x + 2.5, y + 1.5, z + 2.5),
                {**sides("clamp"), "up": "clamp", "down": "clamp"})
    # Le collier, sa lumiere, le chapiteau, la trappe et l'emetteur du sommet.
    box("collar", (2, 62, 2), (46, 66, 46), {**sides("collar_side"), "down": "ceiling", "up": "collar_top"})
    box("collar_glow_n", (6, 63.5, 1.9), (42, 64.5, 2), {"north": "glow"}, light=12)
    box("collar_glow_s", (6, 63.5, 46), (42, 64.5, 46.1), {"south": "glow"}, light=12)
    box("collar_glow_w", (1.9, 63.5, 6), (2, 64.5, 42), {"west": "glow"}, light=12)
    box("collar_glow_e", (46, 63.5, 6), (46.1, 64.5, 42), {"east": "glow"}, light=12)
    box("cap", (3, 66, 3), (45, 74, 45), {**sides("cap_side", "cap_front"), "up": "cap_top"})
    box("step", (8, 74, 8), (40, 77, 40), {**sides("step_side"), "up": "step_top"})
    box("beacon", (20, 77, 20), (28, 80, 28), {**sides("beacon_side"), "up": "beacon_top"}, light=14)
    return E


# Les morceaux : le bloc de la colonne centrale qui porte chacun, et la tranche de hauteur qu'il couvre.
PIECES = {
    "lower": {"offset": (16, 16, 16), "span": (0, 48), "layer": "frame"},
    "upper": {"offset": (16, 64, 16), "span": (48, 96), "layer": "frame"},
    "glass": {"offset": (16, 32, 16), "span": (16, 64), "layer": "glass"},
}


# -- La texture : chaque face peinte une fois, rangee dans l'atlas ---------------------------

def faces_of(elements):
    """Les faces a peindre : une par (peau, taille) ; les faces jumelles partagent leur dessin."""
    out = {}
    for el in elements:
        for d, skin in el.faces.items():
            w, h = face_size(el, d)
            key = (skin, int(round(w * DENS)), int(round(h * DENS)))
            out.setdefault(key, el.layer)
    return out


def pack(keys, width):
    """Un rangement en etageres, des plus hautes aux plus basses."""
    order = sorted(keys, key=lambda k: (-k[2], -k[1]))
    spots = {}
    x = y = shelf = 0
    for key in order:
        w, h = key[1], key[2]
        if x + w > width:
            x, y, shelf = 0, y + shelf, 0
        spots[key] = (x, y)
        x += w
        shelf = max(shelf, h)
    height = y + shelf
    size = 16
    while size < height:
        size *= 2
    return spots, size


def atlas(elements, layer, width):
    keys = [k for k, l in faces_of(elements).items() if l == layer]
    spots, height = pack(keys, width)
    img = Img(width, height)
    for n, key in enumerate(sorted(keys)):
        seed = zlib.crc32(("%s/%d/%d" % key).encode())
        img.blit(paint(key[0], key[1], key[2], seed), *spots[key])
    return img, spots


def num(v):
    v = round(v, 4)
    return int(v) if v == int(v) else v


def uv_of(spots, key, sub, tex_w, tex_h):
    """La region de la face dans l'atlas, en unites de modele (0..16), reduite a la tranche {sub}."""
    x, y = spots[key]
    w, h = key[1], key[2]
    u0, v0, u1, v1 = sub
    return [num((x + u0 * w) * 16.0 / tex_w), num((y + v0 * h) * 16.0 / tex_h),
            num((x + u1 * w) * 16.0 / tex_w), num((y + v1 * h) * 16.0 / tex_h)]


def face_json(full, d, sub_y, spots, sizes):
    """Une face : sa region dans l'atlas, reduite a la tranche de hauteur {sub_y} pour une face de cote."""
    w, h = face_size(full, d)
    key = (full.faces[d], int(round(w * DENS)), int(round(h * DENS)))
    tex = "glass" if full.layer == "glass" else "frame"
    sub = (0.0, 1.0 - sub_y[1], 1.0, 1.0 - sub_y[0]) if d in SIDES else (0.0, 0.0, 1.0, 1.0)
    return {"uv": uv_of(spots[tex], key, sub, *sizes[tex]), "texture": "#" + tex}


def element_json(full, frm, to, faces, sub_y, spots, sizes, place):
    """Un element de modele : ses coins passes par {place}, et ses faces."""
    a, b = place(frm), place(to)
    out = {"from": [num(v) for v in a], "to": [num(v) for v in b],
           "faces": {d: face_json(full, d, sub_y, spots, sizes) for d in faces}}
    if full.light:
        out["light_emission"] = full.light
        out["shade"] = False
    return out


def models(elements, spots, sizes):
    textures = {"frame": NS + ":block/cryo_chamber", "glass": NS + ":block/cryo_chamber_glass",
                "particle": NS + ":block/cryo_chamber_particle"}
    out = {}
    for piece, spec in PIECES.items():
        lo, hi = spec["span"]
        offset = spec["offset"]
        parts = []
        for el in elements:
            if el.layer != spec["layer"]:
                continue
            y0, y1 = el.frm[1], el.to[1]
            a, b = max(y0, lo), min(y1, hi)
            if b <= a:
                continue
            # les faces du dessus et du dessous ne vont qu'au morceau qui les porte
            faces = [d for d in el.faces if d in SIDES or (d == "up" and b == y1) or (d == "down" and a == y0)]
            if not faces:
                continue
            sub_y = ((a - y0) / float(y1 - y0), (b - y0) / float(y1 - y0))
            parts.append(element_json(el, (el.frm[0], a, el.frm[2]), (el.to[0], b, el.to[2]), faces, sub_y,
                                      spots, sizes, lambda p, o=offset: [p[k] - o[k] for k in range(3)]))
        out["cryo_chamber_" + piece] = {"ambientocclusion": False, "textures": textures, "elements": parts}
    out["cryo_chamber_part"] = {"textures": {"particle": textures["particle"]}, "elements": []}
    # La miniature : la chambre entiere au cinquieme, centree.
    mini = []
    for el in elements:
        mini.append(element_json(el, el.frm, el.to, list(el.faces), (0.0, 1.0), spots, sizes,
                                 lambda p: [(p[0] - 24.0) * 0.2 + 8.0, p[1] * 0.2, (p[2] - 24.0) * 0.2 + 8.0]))
    out["item/cryo_chamber"] = {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": textures,
        "elements": mini,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -0.5, 0], "scale": [0.8, 0.8, 0.8]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]},
        },
    }
    return out


def blockstate():
    parts = [{"apply": {"model": NS + ":block/cryo_chamber_part"}}]
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for piece, py in (("lower", 1), ("glass", 2), ("upper", 4)):
            apply = {"model": NS + ":block/cryo_chamber_" + piece}
            if rot:
                apply["y"] = rot
            parts.append({"when": {"facing": facing, "px": "1", "py": str(py), "pz": "1"}, "apply": apply})
    return {"multipart": parts}


# -- L'ADN et la seringue -----------------------------------------------------------------

def vial():
    """La fiole d'ADN : verre, bouchon de metal, une bague de grade ; le liquide est a part."""
    glass_img = Img(32, 32)
    fluid = Img(32, 32)
    band = Img(32, 32)
    cx = 15.5
    for y in range(32):
        for x in range(32):
            dx = abs(x - cx)
            # le corps : un tube arrondi en bas, de 9 a 28 ; le col de 5 a 9
            body = 9 <= y <= 27 and dx <= 5.6 - (max(0, y - 25) ** 1.6) * 0.5
            neck = 6 <= y <= 9 and dx <= 3.5
            cap = 2 <= y <= 5 and dx <= 4.5
            if cap:
                k = 5 if x < cx - 2 else 4 if x < cx else 3 if x < cx + 2 else 1
                if y == 2:
                    k = 6 if dx < 3 else 4
                if y == 5:
                    k = 1
                glass_img.put(x, y, tone("trim", k))
            elif neck:
                edge = dx > 2.6
                glass_img.put(x, y, (200, 230, 245, 220) if edge else (160, 200, 230, 120))
                if y == 7:
                    band.put(x, y, (220, 220, 220, 255) if dx < 3.4 else (140, 140, 140, 255))
                if y == 8:
                    band.put(x, y, (150, 150, 150, 255))
            elif body:
                edge = dx > 4.6 - (max(0, y - 25) ** 1.6) * 0.5 or y == 27
                if edge:
                    glass_img.put(x, y, (190, 225, 245, 235) if x < cx else (120, 160, 190, 235))
                else:
                    level = 13
                    if y >= level:
                        # le liquide, en gris pour la teinte de l'espece
                        shade = 200 - int((y - level) * 4) - int(dx * 6)
                        fluid.put(x, y, (shade, shade, shade, 255))
                    else:
                        glass_img.put(x, y, (170, 210, 240, 70))
    # la double helice, en clair dans le liquide
    for k in range(13):
        y = 14 + k
        a = k / 13.0 * 2.2 * math.pi
        x1 = int(round(cx + math.sin(a) * 2.6 - 0.5))
        x2 = int(round(cx - math.sin(a) * 2.6 - 0.5))
        fluid.put(x1, y, (255, 255, 255, 255))
        fluid.put(x2, y, (235, 235, 235, 255))
        if k % 3 == 1:
            for x in range(min(x1, x2) + 1, max(x1, x2)):
                fluid.put(x, y, (225, 225, 225, 255))
    # le reflet du verre, par-dessus le liquide
    for y in range(11, 25):
        glass_img.put(int(cx - 3), y, (240, 250, 255, 170))
    glass_img.put(int(cx - 2), 11, (240, 250, 255, 140))
    return glass_img, fluid, band


def syringe():
    """La seringue d'extraction, en diagonale : piston, corps gradue, liquide cyan, aiguille."""
    img = Img(32, 32)

    def put_line(x0, y0, x1, y1, w, color_at):
        steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
        for s in range(steps + 1):
            t = s / float(steps)
            x = x0 + (x1 - x0) * t
            y = y0 + (y1 - y0) * t
            for oy in range(-w, w + 1):
                for ox in range(-w, w + 1):
                    if abs(ox) + abs(oy) <= w:
                        c = color_at(t, ox - oy)
                        if c:
                            img.put(int(round(x + ox)), int(round(y + oy)), c)

    # l'aiguille, en bas a gauche
    put_line(3, 28, 9, 22, 0, lambda t, o: tone("trim", 6 if t < 0.5 else 5))
    # l'embout
    put_line(9, 22, 11, 20, 1, lambda t, o: tone("trim", 4 if o < 0 else 2))
    # le corps : verre, liquide jusqu'a mi-hauteur, graduations
    def body(t, o):
        if abs(o) >= 2:
            return (200, 230, 245, 255) if o < 0 else (110, 150, 180, 255)
        if t < 0.55:
            return tone("cyan", 5 if o < 0 else 4 if o == 0 else 3)
        return (150, 196, 228, 255) if o <= 0 else (120, 166, 200, 255)
    put_line(11, 20, 21, 10, 2, body)
    for k in range(4):
        t = 0.2 + k * 0.2
        x = int(round(11 + 10 * t)) + 1
        y = int(round(20 - 10 * t)) + 1
        img.put(x, y, tone("dark", 1))
    # la collerette et le piston
    put_line(20, 8, 23, 11, 1, lambda t, o: tone("trim", 5 if o < 0 else 3))
    put_line(22, 9, 27, 4, 0, lambda t, o: tone("trim", 5))
    put_line(25, 2, 29, 6, 1, lambda t, o: tone("trim", 6 if o < 0 else 4))
    # le contour
    out = Img(32, 32)
    for y in range(32):
        for x in range(32):
            p = img.get(x, y)
            if p[3]:
                out.put(x, y, p)
                continue
            near = any(0 <= x + dx < 32 and 0 <= y + dy < 32 and img.get(x + dx, y + dy)[3]
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if near:
                out.put(x, y, (12, 16, 22, 255))
    return out


def item_files():
    glass_img, fluid, band = vial()
    write_png(os.path.join(ASSETS, "textures", "item", "dna_vial.png"), glass_img)
    write_png(os.path.join(ASSETS, "textures", "item", "dna_fluid.png"), fluid)
    write_png(os.path.join(ASSETS, "textures", "item", "dna_band.png"), band)
    write_png(os.path.join(ASSETS, "textures", "item", "dna_syringe.png"), syringe())
    dump(os.path.join(ASSETS, "models", "item", "dna.json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": NS + ":item/dna_vial", "layer1": NS + ":item/dna_fluid", "layer2": NS + ":item/dna_band"},
    })
    dump(os.path.join(ASSETS, "items", "dna.json"), {"model": {
        "type": "minecraft:model", "model": NS + ":item/dna",
        "tints": [{"type": "minecraft:constant", "value": -1},
                  {"type": "minecraft:custom_model_data", "index": 0, "default": -9395789},
                  {"type": "minecraft:custom_model_data", "index": 1, "default": -5592406}]}})
    dump(os.path.join(ASSETS, "models", "item", "dna_syringe.json"), {
        "parent": "minecraft:item/handheld", "textures": {"layer0": NS + ":item/dna_syringe"}})
    dump(os.path.join(ASSETS, "items", "dna_syringe.json"), {"model": {
        "type": "minecraft:model", "model": NS + ":item/dna_syringe"}})


def dump(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def particle():
    img = Img(16, 16)
    plate(img, random.Random(5), 0, 0, 16, 16, "steel", 3, rivets=True)
    return img


def main():
    elements = build()
    frame, frame_spots = atlas(elements, "frame", 256)
    glass_tex, glass_spots = atlas(elements, "glass", 128)
    spots = {"frame": frame_spots, "glass": glass_spots}
    sizes = {"frame": (frame.w, frame.h), "glass": (glass_tex.w, glass_tex.h)}
    write_png(os.path.join(ASSETS, "textures", "block", "cryo_chamber.png"), frame)
    write_png(os.path.join(ASSETS, "textures", "block", "cryo_chamber_glass.png"), glass_tex)
    write_png(os.path.join(ASSETS, "textures", "block", "cryo_chamber_particle.png"), particle())
    for name, data in models(elements, spots, sizes).items():
        folder = "item" if name.startswith("item/") else "block"
        dump(os.path.join(ASSETS, "models", folder, name.split("/")[-1] + ".json"), data)
    dump(os.path.join(ASSETS, "blockstates", "cryo_chamber.json"), blockstate())
    dump(os.path.join(ASSETS, "items", "cryo_chamber.json"), {"model": {
        "type": "minecraft:model", "model": NS + ":item/cryo_chamber"}})
    item_files()
    print("atlas %dx%d, verre %dx%d, %d elements" % (frame.w, frame.h, glass_tex.w, glass_tex.h, len(elements)))
    if len(sys.argv) > 1:
        preview(sys.argv[1], frame, glass_tex)


def preview(folder, frame, glass_tex):
    from PIL import Image
    os.makedirs(folder, exist_ok=True)
    for name, img in (("atlas", frame), ("verre", glass_tex)):
        im = Image.new("RGBA", (img.w, img.h))
        im.putdata([p for row in img.px for p in row])
        bg = Image.new("RGBA", im.size, (60, 60, 70, 255))
        bg.alpha_composite(im)
        bg.resize((img.w * 3, img.h * 3), Image.NEAREST).save(os.path.join(folder, name + ".png"))


if __name__ == "__main__":
    main()

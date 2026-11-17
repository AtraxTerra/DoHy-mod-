#!/usr/bin/env python3
"""Les cereales de l'agriculture, redessinees en detail : huit stades par culture, leurs icones.

    python3 -I tools/agri_crop_assets.py [dossier_apercu]

D'apres les planches de reference fournies (mais, ble, orge, tournesol, soja, fonio, sorgho,
kamut), au meme grain qu'elles : trente-deux pixels par bloc.

* graminees en touffes, quatre plans en diese comme le ble : orge, avoine, seigle, fonio,
  kamut (32 x 32) ;
* plantes a feuilles, deux plans en croix : soja en buisson (32 x 32) ; mais, tournesol et
  sorgho, qui montent sur deux blocs a partir du cinquieme stade (32 x 64, modele
  dohy:block/crop_cross_tall).

Ecrit textures/block/<cereale>_crop_stage_<0-7>.png, textures/item/<cereale>.png et
<cereale>_seeds.png, les modeles des stades.

La maniere de PalaMod, a double resolution : des rampes de sept tons par matiere, la lumiere
venue d'en haut a gauche, un contour d'un ton sombre de la matiere du cote de l'ombre (jamais de
noir pur), un lisere clair du cote de la lumiere, et une ombre portee la ou une piece en
recouvre une autre.
"""

import json
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dohy")
TEX = os.path.join(ASSETS, "textures")

R = 32  # pixels par bloc
CEREALS = ["corn", "barley", "oat", "rye", "sunflower", "soy", "fonio", "sorghum", "kamut"]
CROSS = {"corn", "sunflower", "sorghum", "soy"}
TALL_FROM = 4

# -- Les rampes : 0 contour, 1 ombre profonde, 2 ombre, 3 corps, 4 clair, 5 lisere, 6 reflet ------

LEAF = [(16, 42, 10), (28, 68, 14), (44, 98, 20), (68, 132, 28), (98, 164, 40), (136, 194, 58), (180, 222, 96)]
SPROUT = [(30, 64, 8), (50, 96, 12), (78, 132, 18), (110, 166, 28), (144, 198, 44), (182, 226, 74),
          (220, 244, 138)]
OLIVE = [(40, 48, 14), (64, 76, 22), (92, 106, 32), (122, 134, 44), (154, 162, 60), (186, 190, 86),
         (216, 218, 126)]
STRAW = [(98, 72, 28), (134, 102, 44), (170, 136, 62), (198, 166, 86), (222, 194, 114), (240, 216, 148),
         (252, 238, 190)]
GOLD = [(104, 66, 16), (146, 102, 28), (182, 136, 40), (208, 164, 56), (228, 190, 82), (244, 214, 120),
        (252, 236, 170)]
KAMUT = [(106, 58, 10), (150, 92, 20), (190, 128, 32), (218, 160, 48), (236, 188, 74), (248, 212, 116),
         (254, 236, 170)]
RYE = [(72, 64, 46), (104, 96, 72), (140, 132, 100), (174, 166, 130), (202, 196, 162), (226, 222, 192),
       (244, 242, 222)]
RYE_GREEN = [(16, 46, 30), (30, 72, 48), (50, 102, 70), (76, 134, 96), (108, 166, 124), (146, 196, 158),
             (194, 226, 200)]
OAT = [(90, 88, 50), (124, 124, 78), (158, 158, 106), (188, 188, 136), (212, 212, 166), (232, 232, 196),
       (248, 248, 226)]
FONIO = [(78, 78, 12), (116, 116, 20), (156, 152, 28), (192, 184, 40), (220, 208, 62), (240, 230, 102),
         (250, 246, 160)]
SORGHUM = [(18, 4, 6), (38, 8, 12), (62, 12, 16), (92, 18, 22), (130, 28, 28), (174, 48, 40), (214, 92, 66)]
PETAL = [(132, 76, 0), (182, 120, 0), (214, 156, 0), (234, 188, 0), (246, 210, 16), (252, 230, 72),
         (255, 246, 160)]
DISC = [(30, 18, 8), (50, 30, 12), (72, 46, 16), (96, 64, 22), (122, 84, 30), (150, 108, 42), (180, 136, 64)]
COB = [(136, 80, 6), (186, 124, 12), (220, 164, 24), (240, 194, 40), (250, 216, 72), (254, 234, 122),
       (255, 248, 190)]
SILK = [(132, 100, 48), (172, 140, 80), (202, 174, 114), (224, 200, 146), (238, 220, 178), (248, 236, 206),
        (255, 250, 232)]
POD = [(74, 48, 12), (124, 86, 30), (164, 120, 44), (196, 150, 58), (218, 176, 80), (236, 200, 114),
       (248, 226, 160)]
POD_GREEN = [(38, 70, 10), (64, 106, 18), (100, 144, 30), (136, 178, 46), (170, 206, 70), (200, 228, 106),
             (230, 246, 164)]
BARK = [(52, 32, 12), (80, 52, 20), (108, 72, 28), (136, 94, 38), (164, 118, 52), (190, 144, 72),
        (214, 172, 100)]
WHITE = [(120, 120, 112), (164, 164, 154), (200, 200, 192), (226, 226, 220), (242, 242, 238), (250, 250, 248),
         (255, 255, 255)]
MAUVE = [(72, 40, 96), (102, 60, 132), (134, 86, 166), (164, 116, 196), (192, 148, 220), (216, 180, 236),
         (236, 214, 248)]
SEED_HULL = [(22, 20, 24), (40, 38, 44), (62, 60, 66), (98, 96, 100), (150, 148, 150), (204, 202, 200),
             (240, 238, 232)]
SORGHUM_SEED = [(34, 16, 8), (60, 30, 14), (88, 48, 22), (116, 68, 32), (144, 92, 48), (172, 120, 70),
                (200, 152, 102)]


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def mix_ramp(a, b, t):
    t = max(0.0, min(1.0, t))
    return [mix(a[i], b[i], t) for i in range(len(a))]


def scale(ramp, f):
    return [tuple(min(255, int(round(c * f))) for c in col) for col in ramp]


def darker(color, f=0.72):
    return tuple(int(c * f) for c in color)


# -- La toile -----------------------------------------------------------------------------------------

class Canvas:
    def __init__(self, w=R, h=R):
        self.w, self.h = w, h
        self.px = {}

    def inside(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def put(self, x, y, color):
        x, y = int(math.floor(x)), int(math.floor(y))
        if self.inside(x, y):
            self.px[(x, y)] = color

    def get(self, x, y):
        return self.px.get((int(x), int(y)))

    def paste(self, shape, shadow=True):
        """Pose une piece ({(x, y): couleur}) ; si shadow, elle assombrit ce qu'elle recouvre
        juste en dessous a droite (l'ombre portee qui detache les plans)."""
        if shadow:
            for (x, y) in shape:
                for ox, oy in ((1, 1), (0, 1)):
                    k = (x + ox, y + oy)
                    if k not in shape and k in self.px:
                        self.px[k] = darker(self.px[k], 0.8 if oy and ox else 0.88)
        for (x, y), c in shape.items():
            if self.inside(x, y):
                self.px[(x, y)] = c

    def image(self):
        image = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
        for (x, y), c in self.px.items():
            image.putpixel((x, y), tuple(c) + (255,))
        return image


def sprite(target, x, y, rows, palette, flip=False):
    """Une petite carte de pixels : un caractere par ton, '.' transparent. target : Canvas ou
    dict (piece)."""
    shape = {}
    for dy, row in enumerate(rows):
        width = len(row)
        for dx, ch in enumerate(row):
            if ch in ". ":
                continue
            px = x + (width - 1 - dx if flip else dx)
            shape[(px, y + dy)] = palette[ch]
    if isinstance(target, Canvas):
        target.paste(shape, shadow=False)
    else:
        target.update(shape)
    return shape


def tones(ramp, letters="0123456"):
    return {letters[i]: ramp[i] for i in range(min(len(letters), len(ramp)))}


# -- Les courbes --------------------------------------------------------------------------------------

def bez(points, t):
    pts = list(points)
    while len(pts) > 1:
        pts = [(pts[i][0] + (pts[i + 1][0] - pts[i][0]) * t, pts[i][1] + (pts[i + 1][1] - pts[i][1]) * t)
               for i in range(len(pts) - 1)]
    return pts[0]


def bez_tan(points, t):
    a = bez(points, max(0.0, t - 0.01))
    b = bez(points, min(1.0, t + 0.01))
    dx, dy = b[0] - a[0], b[1] - a[1]
    n = math.hypot(dx, dy) or 1.0
    return dx / n, dy / n


LIGHT = (-0.55, -0.83)  # d'ou vient la lumiere : en haut, un peu a gauche


def ribbon(points, width, ramp, rib=None, light_rim=True, outline=True, tip=0.9, base_dark=0.0, steps=None,
           sheen=None, pattern=None):
    """La piece de base : une bande le long d'une courbe de Bezier (feuille, brin, tige, petale).

    width : fonction t -> epaisseur en pixels (t = 0 a l'attache, 1 a la pointe).
    Le ton suit la position dans l'epaisseur : clair du cote de la lumiere, sombre de l'autre,
    contour sombre sur le bord a l'ombre, lisere sur le bord eclaire, pointe plus sombre.
    rib : ton de la nervure centrale (None : pas de nervure)."""
    length = sum(math.hypot(bez(points, (i + 1) / 20)[0] - bez(points, i / 20)[0],
                            bez(points, (i + 1) / 20)[1] - bez(points, i / 20)[1]) for i in range(20))
    steps = steps or max(16, int(length * 5))
    acc = {}
    for i in range(steps + 1):
        t = i / steps
        cx, cy = bez(points, t)
        tx, ty = bez_tan(points, t)
        nx, ny = -ty, tx
        if nx * LIGHT[0] + ny * LIGHT[1] < 0:
            nx, ny = -nx, -ny
        w = width(t)
        if w <= 0.05:
            continue
        half = w / 2
        k = max(1, int(math.ceil(half * 4)))
        for j in range(-k, k + 1):
            o = half * j / k
            key = (int(math.floor(cx + nx * o)), int(math.floor(cy + ny * o)))
            v = o / half
            a = acc.get(key)
            if a is None:
                acc[key] = [v, 1, t]
            else:
                a[0] += v
                a[1] += 1
                a[2] = min(a[2], t)
    shape = {}
    for key, (sv, n, t) in acc.items():
        v = sv / n
        x, y = key
        edge = any((x + ox, y + oy) not in acc for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if v > 0.55:
            tone = 5
        elif v > 0.18:
            tone = 4
        elif v > -0.22:
            tone = 3
        elif v > -0.6:
            tone = 2
        else:
            tone = 1
        if edge:
            if v < -0.05 and outline:
                tone = 0
            elif v > 0.3 and light_rim:
                tone = 5
        if rib is not None and abs(v) < 0.16 and 0.04 < t < 0.8 and not edge:
            tone = rib
        if sheen and sheen[0] < t < sheen[1] and v > 0.3 and not edge:
            tone = 6
        if t > tip:
            tone = min(tone, 2) if not edge else 1
        if base_dark and t < base_dark:
            tone = max(1, tone - 1)
        if pattern is not None:
            tone = max(0, min(6, pattern(t, v, tone, key)))
        shape[key] = ramp[tone]
    return shape


def taper(w, start=0.75, peak=0.35, power=0.6):
    """Une lame : un peu moins large a l'attache, pleine vers le tiers, effilee jusqu'a la pointe."""
    def f(t):
        if t < peak:
            return w * (start + (1 - start) * t / peak)
        return w * max(0.0, 1 - (t - peak) / (1 - peak)) ** power
    return f


def lance(w, power=0.75):
    """Une feuille en fer de lance : nulle aux deux bouts, pleine vers 40 %."""
    return lambda t: w * max(0.0, math.sin(math.pi * min(1.0, t ** 0.85))) ** power


def const(w, end=None):
    return lambda t: w if end is None else w + (end - w) * t


def blade(x0, y0, side, length, rise, droop, width, ramp, rib=None, sheen=(0.12, 0.42)):
    """Une feuille de mais, de sorgho ou de graminee : elle part de (x0, y0), monte, s'arrondit
    et laisse tomber sa pointe."""
    p0 = (x0, y0)
    p1 = (x0 + side * length * 0.30, y0 - rise * 1.6)
    p2 = (x0 + side * length * 0.70, y0 - rise * 1.4)
    p3 = (x0 + side * length, y0 - rise + droop)
    return ribbon([p0, p1, p2, p3], taper(width), ramp, rib=rib, sheen=sheen)


def upright(x0, y0, height, lean, width, ramp, curl=0.0, sheen=(0.2, 0.5)):
    """Un brin qui monte du sol en s'ecartant ; curl : la pointe qui s'incline (pixels)."""
    p0 = (x0, y0)
    p1 = (x0 + lean * 0.1, y0 - height * 0.45)
    p2 = (x0 + lean * 0.55, y0 - height * 0.85)
    p3 = (x0 + lean + curl, y0 - height + abs(curl) * 0.6)
    return ribbon([p0, p1, p2, p3], taper(width, start=1.0, peak=0.05, power=0.75), ramp, sheen=sheen)


def stalk(x0, y0, x1, y1, width, ramp, nodes=0, bend=0.0):
    """Une tige : bande droite ou a peine courbee, des noeuds marques tous les nodes pixels."""
    mx, my = (x0 + x1) / 2 + bend, (y0 + y1) / 2
    shape = ribbon([(x0, y0), (mx, my), (x1, y1)], const(width), ramp, tip=1.1, light_rim=True)
    if nodes:
        ys = [y for (_, y) in shape]
        top, bottom = min(ys), max(ys)
        for y in range(bottom - nodes, top + 2, -nodes):
            row = sorted(x for (x, yy) in shape if yy == y)
            for x in row:
                shape[(x, y)] = ramp[1] if x != row[0] else ramp[2]
            for x in row:
                if (x, y - 1) in shape:
                    shape[(x, y - 1)] = ramp[5] if x == row[0] else ramp[4]
    return shape


def oval(cx, cy, rx, ry, ramp, outline=True, spec=True):
    """Un petit grain rond ou ovale, eclaire d'en haut a gauche."""
    shape = {}
    for y in range(int(math.floor(cy - ry)) - 1, int(math.ceil(cy + ry)) + 2):
        for x in range(int(math.floor(cx - rx)) - 1, int(math.ceil(cx + rx)) + 2):
            px, py = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            d = px * px + py * py
            if d <= 1.0:
                lit = -(px * 0.6 + py * 0.8)
                tone = 5 if lit > 0.45 else 4 if lit > 0.05 else 3 if lit > -0.35 else 2
                if d > 0.55 and lit < -0.1 and outline:
                    tone = 1 if d > 0.75 else 2
                shape[(x, y)] = ramp[tone]
    if spec:
        hx, hy = int(math.floor(cx - rx * 0.35)), int(math.floor(cy - ry * 0.4))
        if (hx, hy) in shape:
            shape[(hx, hy)] = ramp[6]
    return shape


def outline_shape(shape, color, sides=((1, 0), (0, 1), (1, 1))):
    """Ajoute un contour exterieur du cote de l'ombre."""
    extra = {}
    for (x, y) in shape:
        for ox, oy in sides:
            k = (x + ox, y + oy)
            if k not in shape:
                extra[k] = color
    shape.update(extra)
    return shape


# -- Le mais ------------------------------------------------------------------------------------------

def husk_cob(x, y, ramp_leaf, ripe=True, flip=False, w=8, h=16):
    """Un epi de mais : des grains en rangs decales (deux pixels sur deux, un reflet chacun),
    ombre comme un cylindre ; les spathes vertes qui l'enveloppent par le bas ; les soies au
    bout. (x, y) : coin haut gauche de l'epi."""
    shape = {}
    cob = COB if ripe else mix_ramp(POD_GREEN, SILK, 0.3)
    cols = w // 2
    for j in range(h):
        t = j / (h - 1)
        # le bout arrondi en haut, la base un peu plus etroite
        half = w / 2 * (min(1.0, 0.45 + t * 2.4) if t < 0.25 else 1.0 - max(0.0, t - 0.85) * 1.2)
        for i in range(w):
            dx = i + 0.5 - w / 2
            if abs(dx) > half:
                continue
            col = i // 2
            base = [5, 4, 3, 2][min(3, col * 4 // cols)]
            if ripe:
                ky = (j + (col % 2)) % 2
                kx = i % 2
                if kx == 0 and ky == 0:
                    tone = base + 1
                elif kx == 1 and ky == 1:
                    tone = base - 2
                else:
                    tone = base
            else:
                tone = base - (1 if (j + i) % 4 == 0 else 0)
            if abs(dx) > half - 1 and dx > 0:
                tone = 0
            shape[(x + i, y + j)] = cob[max(0, min(6, tone))]
    # les spathes : des feuilles etroites et pointues qui collent aux flancs de l'epi, par le bas
    back = ribbon([(x + w / 2 + 1, y + h + 2), (x + w + 2, y + h - 3), (x + w + 2.5, y + h - 8), (x + w + 3, y + h - 11)],
                  lance(2.2), scale(ramp_leaf, 0.85))
    left = ribbon([(x + w / 2 - 1, y + h + 2), (x - 0.5, y + h - 1), (x - 0.5, y + h - 6), (x + 0.5, y + h - 10)],
                  lance(2.6), ramp_leaf, sheen=(0.15, 0.5))
    right = ribbon([(x + w / 2, y + h + 2), (x + w + 0.5, y + h - 1), (x + w + 0.5, y + h - 5),
                    (x + w - 0.5, y + h - 8)], lance(2.4), ramp_leaf)
    mid = ribbon([(x + w / 2, y + h + 2), (x + w / 2 - 1, y + h - 2), (x + w / 2 - 1.5, y + h - 4)],
                 lance(2.0), ramp_leaf)
    out = dict(back)
    out.update(shape)
    out.update(left)
    out.update(right)
    out.update(mid)
    # les soies qui sortent du bout
    silk = SILK if not ripe else mix_ramp(SILK, BARK, 0.5)
    for k, (ox, oy) in enumerate(((w // 2, -1), (w // 2 + 1, -2), (w // 2 - 1, -2), (w // 2 + 2, -3),
                                  (w // 2, -3), (w // 2 - 2, -3), (w // 2 + 1, -4))):
        out[(x + ox, y + oy)] = silk[5 - k % 3]
    if flip:
        out = {(2 * x + w - 1 - xx, yy): c for (xx, yy), c in out.items()}
    return out


def tassel(cx, top, ramp):
    """La panicule male au sommet : un epi central dresse et, de chaque cote, des branches qui
    s'arquent et retombent, perlees d'antheres (un pixel clair, un pixel sombre)."""
    shape = {}
    for k, y in enumerate(range(top, top + 11)):
        shape[(cx, y)] = ramp[6] if k == 0 else ramp[5] if k % 2 else ramp[3]
        if k >= 2:
            shape[(cx + 1, y)] = ramp[2] if k % 2 else ramp[4]
    branches = (
        # depart (rang), pixels successifs (dx, dy) depuis l'axe
        (3, ((-1, -1), (-2, -1), (-3, 0), (-4, 1), (-5, 2), (-5, 3))),
        (3, ((2, -1), (3, -1), (4, 0), (5, 1), (6, 2), (6, 3))),
        (5, ((-1, 0), (-2, 1), (-3, 2), (-4, 3), (-4, 4))),
        (5, ((2, 0), (3, 1), (4, 2), (5, 3), (5, 4))),
        (8, ((-1, 0), (-2, 1), (-3, 2))),
        (8, ((2, 0), (3, 1), (4, 2))),
    )
    for start, steps in branches:
        for k, (dx, dy) in enumerate(steps):
            shape[(cx + dx, top + start + dy)] = ramp[5] if k % 2 == 0 else ramp[2]
            if k % 2 == 0 and k > 0:
                shape[(cx + dx, top + start + dy + 1)] = ramp[1]
    return shape


# stade : (hauteur, feuilles [(cote, hauteur de l'attache, longueur, montee, chute, largeur)])
CORN = {
    1: (11, [(-1, 4, 7, 2.5, 2.2, 3.6), (1, 6, 7, 3, 2.2, 3.6)]),
    2: (17, [(1, 3, 9, 3, 4.3, 4), (-1, 6, 9, 3.5, 4.3, 4), (1, 10, 8, 3.5, 2.9, 3.8),
             (-1, 13, 6, 3.5, 1.4, 3.4)]),
    3: (27, [(-1, 3, 12, 3.5, 6.5, 4.7), (1, 6, 12, 4, 6.5, 4.7), (-1, 11, 12, 4, 5.8, 4.5),
             (1, 15, 11, 4, 5, 4.5), (-1, 19, 9, 4, 3.6, 4), (1, 22, 7, 4, 2.2, 3.6)]),
    4: (40, [(1, 4, 13, 3.5, 7.9, 5.4), (-1, 8, 14, 4, 7.9, 5.4), (1, 13, 14, 4, 7.2, 5.2),
             (-1, 18, 14, 4.5, 7.2, 5.2), (1, 23, 13, 4.5, 6.5, 4.9), (-1, 28, 11, 4.5, 5, 4.5),
             (1, 32, 9, 4.5, 2.9, 4), (-1, 35, 6, 4, 1.4, 3.4)]),
    5: (50, [(-1, 4, 13, 3.5, 8.6, 5.4), (1, 8, 14, 4, 8.6, 5.6), (-1, 13, 14, 4, 7.9, 5.6),
             (1, 19, 14, 4.5, 7.9, 5.4), (-1, 25, 14, 4.5, 7.2, 5.2), (1, 31, 13, 4.5, 6.5, 4.9),
             (-1, 37, 11, 4.5, 5, 4.5), (1, 42, 8, 4.5, 2.9, 3.8), (-1, 45, 6, 4, 1.4, 3.4)]),
    6: (54, [(1, 4, 13, 3.5, 8.6, 5.4), (-1, 8, 14, 4, 8.6, 5.6), (1, 13, 14, 4, 7.9, 5.6),
             (-1, 19, 14, 4.5, 7.9, 5.4), (1, 25, 14, 4.5, 7.2, 5.2), (-1, 31, 13, 4.5, 6.5, 4.9),
             (1, 37, 11, 4.5, 5, 4.5), (-1, 42, 8, 4.5, 2.9, 3.8)]),
    7: (54, [(-1, 4, 13, 3.5, 8.6, 5.4), (1, 8, 14, 4, 8.6, 5.6), (-1, 13, 14, 4, 7.9, 5.6),
             (1, 19, 14, 4.5, 7.9, 5.4), (-1, 25, 14, 4.5, 7.2, 5.2), (1, 31, 13, 4.5, 6.5, 4.9),
             (-1, 37, 11, 4.5, 5, 4.5), (1, 42, 8, 4.5, 2.9, 3.8)]),
}


def corn(age):
    tall = age >= TALL_FROM
    c = Canvas(R, 2 * R if tall else R)
    g = c.h - 1
    if age == 0:
        c.paste(upright(15.5, g + 1, 6, -3, 2.6, SPROUT, curl=-1.5))
        c.paste(upright(16.5, g + 1, 7, 3, 2.6, SPROUT, curl=1.5))
        c.paste(upright(16, g + 1, 5, 0.5, 2.0, SPROUT))
        return c
    h, leaves = CORN[age]
    ramp = LEAF if age >= 3 else SPROUT
    if age == 7:
        ramp = mix_ramp(LEAF, OLIVE, 0.25)
    width = 3.0 if age <= 2 else 4.0
    # les feuilles de derriere, puis la tige, puis celles de devant
    back = [l for k, l in enumerate(leaves) if k % 2 == 1]
    front = [l for k, l in enumerate(leaves) if k % 2 == 0]
    for side, up, length, rise, droop, w in back:
        c.paste(blade(16 + side * 1.5, g - up, side, length, rise, droop, w, scale(ramp, 0.86), rib=4))
    c.paste(stalk(16, g + 1, 16, g - h + 3, width, ramp, nodes=6 if tall else 0), shadow=False)
    for side, up, length, rise, droop, w in front:
        c.paste(blade(16 + side * 1.5, g - up, side, length, rise, droop, w, ramp, rib=4))
    # le coeur : deux feuilles roulees qui pointent vers le ciel (la panicule les remplace)
    top = g - h
    if age < 6:
        c.paste(upright(16, top + 6, 7, -1.5, 2.4, ramp, curl=-1))
        c.paste(upright(16.5, top + 5, 6, 1.5, 2.2, ramp, curl=1))
    else:
        c.paste(stalk(16, top + 4, 16, top - 1, 2.4, ramp), shadow=False)
    if tall:
        # les racines d'appui au pied
        for ox, oy in ((-3, 0), (-2, -1), (3, 0), (2, -1), (-4, 1), (4, 1)):
            c.put(16 + ox, g + oy, BARK[2] if ox < 0 else BARK[1])
    if age >= 6:
        silk = SILK if age == 6 else mix_ramp(SILK, GOLD, 0.5)
        c.paste(tassel(15, top - 10, silk))
    if age == 6:
        c.paste(husk_cob(19, g - 36, ramp, ripe=False, w=6, h=13))
    if age == 7:
        c.paste(husk_cob(19, g - 41, ramp))
        c.paste(husk_cob(4, g - 33, ramp))
    return c


# -- Le tournesol -------------------------------------------------------------------------------------

def ovate(w, peak=0.32):
    """Une feuille en coeur allonge : vite large apres l'attache, puis effilee en pointe."""
    def f(t):
        if t < peak:
            return w * (t / peak) ** 0.45
        return w * max(0.0, 1 - ((t - peak) / (1 - peak)) ** 1.5)
    return f


def broad_leaf(x0, y0, side, length, angle, width, ramp, petiole=2.0, droop=0.3, veins=True):
    """Une feuille large au bout de son petiole : angle en degres (positif : elle se dresse),
    droop : la pointe qui retombe (0 a 1). Nervure centrale claire, nervures laterales."""
    a = math.radians(angle)
    dx, dy = side * math.cos(a), -math.sin(a)
    px, py = x0 + dx * petiole, y0 + dy * petiole
    shape = {}
    if petiole > 0.5:
        shape.update(ribbon([(x0, y0), ((x0 + px) / 2, (y0 + py) / 2 - 0.3), (px, py)], const(1.4), ramp, tip=1.1))
    end = (px + dx * length, py + dy * length + droop * length * 0.6)
    ctrl1 = (px + dx * length * 0.35, py + dy * length * 0.35 - length * 0.08)
    ctrl2 = (px + dx * length * 0.7, py + dy * length * 0.7 + droop * length * 0.15)
    leaf = ribbon([(px, py), ctrl1, ctrl2, end], ovate(width), ramp, rib=5, sheen=(0.1, 0.35))
    if veins:
        # deux ou trois nervures laterales, du milieu vers le bord eclaire
        for k, t in enumerate((0.3, 0.5, 0.68)):
            cx, cy = bez([(px, py), ctrl1, ctrl2, end], t)
            tx, ty = bez_tan([(px, py), ctrl1, ctrl2, end], t)
            nx, ny = -ty, tx
            if nx * LIGHT[0] + ny * LIGHT[1] < 0:
                nx, ny = -nx, -ny
            for o in (1, 2):
                key = (int(math.floor(cx + nx * o + tx * o * 0.8)), int(math.floor(cy + ny * o + ty * o * 0.8)))
                if key in leaf and leaf[key] not in (ramp[0], ramp[5]):
                    leaf[key] = ramp[4] if o == 1 else ramp[3]
    shape.update(leaf)
    return shape


def flower_head(cx, cy, radius, disk, petals=21, ramp=PETAL, center=DISC, open_t=1.0, seed=5):
    """Un capitule de tournesol vu de face : deux couronnes de petales (la plus sombre
    derriere), un coeur brun garni de graines en spirale, eclaire d'en haut a gauche."""
    rng = random.Random(seed)
    shape = {}
    back_ramp = mix_ramp(ramp, KAMUT, 0.45)
    reach = disk + (radius - disk) * open_t
    for layer in (0, 1):
        for k in range(petals):
            th = 2 * math.pi * (k + (0.5 if layer == 0 else 0.0)) / petals + 0.1
            r0 = disk - 0.8
            r1 = reach * (0.9 if layer == 0 else 1.0) + rng.uniform(-0.6, 0.4)
            bend = rng.uniform(-0.5, 0.5)
            p0 = (cx + math.cos(th) * r0, cy + math.sin(th) * r0)
            p1 = (cx + math.cos(th + bend * 0.05) * (r0 + r1) / 2, cy + math.sin(th + bend * 0.05) * (r0 + r1) / 2)
            p2 = (cx + math.cos(th + bend * 0.1) * r1, cy + math.sin(th + bend * 0.1) * r1)
            facing = -(math.cos(th) * 0.6 + math.sin(th) * 0.8)  # +1 vers la lumiere
            base = back_ramp if layer == 0 else ramp
            pr = scale(base, 1.0 + 0.08 * facing) if layer else scale(base, 0.92 + 0.06 * facing)
            width = 3.6 if layer else 3.2
            shape.update(ribbon([p0, p1, p2], lance(width * min(1.0, 0.5 + open_t), 0.6), pr, tip=0.94,
                                sheen=(0.3, 0.6) if facing > 0.2 and layer else None))
    # le coeur : une boule brune, des graines en spirale, plus sombre au bord
    golden = math.radians(137.508)
    seeds = set()
    for k in range(int(disk * disk * 2.2)):
        r = 0.62 * math.sqrt(k)
        if r > disk - 0.8:
            break
        th = k * golden
        seeds.add((int(math.floor(cx + math.cos(th) * r)), int(math.floor(cy + math.sin(th) * r))))
    for y in range(int(cy - disk) - 1, int(cy + disk) + 2):
        for x in range(int(cx - disk) - 1, int(cx + disk) + 2):
            ox, oy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(ox, oy) / disk
            if d > 1.0:
                continue
            lit = -(ox * 0.6 + oy * 0.8) / disk
            tone = 3 if lit > 0.3 else 2 if lit > -0.3 else 1
            if (x, y) in seeds:
                tone += 1 if lit > -0.5 else 0
            if d > 0.86:
                tone = 1 if lit > -0.2 else 0
            elif d < 0.3 and open_t >= 1.0:
                tone = 2 + ((x + y) % 2)
            shape[(x, y)] = center[max(0, min(6, tone))]
    # un reflet sur le coeur
    shape[(int(cx - disk * 0.45), int(cy - disk * 0.5))] = center[5]
    return shape


def sun_bud(cx, top, ramp, show=0.0):
    """Le bouton : des bracteoles pointues serrees en ogive, une pointe de jaune qui perce."""
    shape = {}
    for k, (ox, h, lean, w) in enumerate(((-3.5, 6, -1.5, 3.0), (3.5, 6, 1.5, 3.0), (-1.5, 8, -0.5, 3.2),
                                          (1.5, 8, 0.5, 3.2), (0, 9, 0, 3.4))):
        shape.update(upright(cx + ox * 0.6, top + 10, h, lean, w, ramp if k >= 2 else scale(ramp, 0.88)))
    if show:
        for k, (ox, oy) in enumerate(((0, 0), (-1, 1), (1, 1), (0, 1), (-2, 2), (2, 2))):
            shape[(int(cx + ox), int(top + 1 + oy))] = PETAL[5 - k % 3]
    return shape


# stade : (hauteur de tige, largeur de tige, feuilles [(cote, attache, longueur, angle, largeur, chute)])
SUN = {
    1: (9, 1.6, [(-1, 8, 4.8, 25, 3.5, 0.1), (1, 8, 4.8, 25, 3.5, 0.1)]),
    2: (15, 2.0, [(-1, 6, 6, 15, 3.9, 0.2), (1, 6, 6, 15, 3.9, 0.2), (-1, 12, 6, 35, 3.9, 0.1),
                  (1, 13, 6, 35, 3.9, 0.1)]),
    3: (24, 2.6, [(-1, 5, 9.6, -5, 6.5, 0.4), (1, 8, 9.6, -5, 6.5, 0.4), (-1, 12, 8.4, 15, 5.8, 0.3),
                  (1, 15, 8.4, 15, 5.8, 0.3), (-1, 19, 6, 35, 4.8, 0.1), (1, 21, 6, 35, 4.8, 0.1)]),
    4: (36, 3.0, [(-1, 6, 12, -15, 8, 0.5), (1, 10, 12, -10, 8, 0.5), (-1, 15, 10.8, 0, 7.6, 0.4),
                  (1, 20, 10.8, 5, 7.1, 0.4), (-1, 25, 9.6, 15, 6.5, 0.3), (1, 29, 8.4, 25, 5.8, 0.2),
                  (-1, 32, 6, 40, 4.8, 0.1), (1, 33, 6, 40, 4.5, 0.1)]),
    5: (42, 3.4, [(-1, 6, 13.2, -15, 8.6, 0.5), (1, 10, 13.2, -12, 8.6, 0.5), (-1, 16, 12, -5, 8.2, 0.4),
                  (1, 21, 12, 0, 7.8, 0.4), (-1, 27, 10.8, 10, 7.1, 0.3), (1, 32, 9.6, 20, 6.5, 0.3),
                  (-1, 36, 7.2, 35, 5.4, 0.2)]),
    6: (44, 3.6, [(-1, 6, 13.2, -15, 8.6, 0.5), (1, 10, 13.2, -12, 8.6, 0.5), (-1, 16, 12, -5, 8.2, 0.4),
                  (1, 21, 12, 0, 7.8, 0.4), (-1, 27, 10.8, 10, 7.1, 0.3), (1, 32, 9.6, 20, 6.5, 0.3)]),
    7: (50, 3.8, [(-1, 6, 13.2, -15, 8.6, 0.5), (1, 10, 13.2, -12, 8.6, 0.5), (-1, 16, 12, -5, 8.2, 0.4),
                  (1, 21, 12, 0, 7.8, 0.4), (-1, 27, 10.8, 10, 7.1, 0.3), (1, 31, 9.6, 15, 6.5, 0.3)]),
}


def sunflower(age):
    tall = age >= TALL_FROM
    c = Canvas(R, 2 * R if tall else R)
    g = c.h - 1
    if age == 0:
        c.paste(stalk(16, g + 1, 16, g - 4, 1.6, SPROUT), shadow=False)
        c.paste(broad_leaf(16, g - 4, -1, 4, 20, 3.4, SPROUT, petiole=0, veins=False))
        c.paste(broad_leaf(16, g - 4, 1, 4, 20, 3.4, SPROUT, petiole=0, veins=False))
        return c
    h, width, leaves = SUN[age]
    ramp = SPROUT if age <= 2 else LEAF
    top = g - h
    back = [l for k, l in enumerate(leaves) if k % 2 == 1]
    front = [l for k, l in enumerate(leaves) if k % 2 == 0]
    for side, up, length, angle, w, droop in back:
        c.paste(broad_leaf(16, g - up, side, length, angle, w, scale(ramp, 0.88), droop=droop, veins=age >= 3))
    c.paste(stalk(16, g + 1, 16, top + 1, width, ramp), shadow=False)
    # des poils clairs sur la tige
    if age >= 3:
        for y in range(top + 3, g - 1, 3):
            c.put(16 - width / 2 - 0.5 + (y % 2), y, ramp[5])
    for side, up, length, angle, w, droop in front:
        c.paste(broad_leaf(16, g - up, side, length, angle, w, ramp, droop=droop, veins=age >= 3))
    if 2 <= age <= 4:
        c.paste(upright(16, top + 4, 6, -1, 3.0, ramp, curl=-0.5))
        c.paste(upright(16.5, top + 4, 5, 1, 2.6, ramp, curl=0.5))
    elif age == 5:
        c.paste(sun_bud(16, top - 8, LEAF, show=1.0))
    elif age == 6:
        c.paste(flower_head(16, top - 4, 9.5, 4.2, petals=17, open_t=0.6, center=mix_ramp(DISC, OLIVE, 0.35)))
        c.paste(upright(11, top + 2, 4, -3, 2.6, LEAF))
        c.paste(upright(21, top + 2, 4, 3, 2.6, scale(LEAF, 0.9)))
    elif age == 7:
        c.paste(flower_head(16, 13.5, 13.4, 6.4))
    return c


# -- Le sorgho ----------------------------------------------------------------------------------------

def panicle(cx, top, w, h, ramp, seed=7, droop=1.5, specks=0.12, dark=0):
    """Une panicule serree de grains (sorgho) : un fuseau arrondi un peu penche, la silhouette
    bosselee, garni de grains ronds de deux ou trois pixels, chacun avec son reflet, plus clair a
    gauche, plus sombre a droite, contourne du ton le plus sombre."""
    rng = random.Random(seed)
    mask = {}
    for j in range(h):
        t = j / max(1, h - 1)
        half = w / 2 * math.sin(math.pi * (0.05 + 0.88 * t)) ** 0.65
        center = cx + droop * (1 - t) ** 2
        for x in range(int(center - half) - 2, int(center + half) + 3):
            if abs(x + 0.5 - center) <= half + rng.uniform(-0.4, 0.6):
                mask[(x, top + j)] = (x + 0.5 - center) / max(0.5, half)
    # les grains : des bosses sur le pourtour, puis le remplissage
    grains = []
    for k in range(int(w * h * 0.42)):
        x, y = rng.choice(list(mask))
        grains.append((x + rng.uniform(-0.5, 0.5), y + rng.uniform(-0.5, 0.5), rng.choice((1.0, 1.2, 1.4))))
    shape = {}
    for (x, y), u in mask.items():
        shape[(x, y)] = ramp[1] if u > -0.3 else ramp[2]
    for gx, gy, r in sorted(grains, key=lambda g: g[1]):
        u = mask.get((int(gx), int(gy)), 0.0)
        base = (4 if u < -0.45 else 3 if u < 0.15 else 2) - dark
        for y in range(int(gy - r) - 1, int(gy + r) + 2):
            for x in range(int(gx - r) - 1, int(gx + r) + 2):
                ox, oy = (x + 0.5 - gx) / r, (y + 0.5 - gy) / r
                d = ox * ox + oy * oy
                if d > 1.0:
                    continue
                lit = -(ox * 0.6 + oy * 0.8)
                tone = base + (1 if lit > 0.35 else 0 if lit > -0.25 else -1)
                if d > 0.6 and lit < -0.2:
                    tone = base - 2
                shape[(x, y)] = ramp[max(0, min(6, tone))]
        if rng.random() < specks and u < 0.35:
            shape[(int(gx - r * 0.3), int(gy - r * 0.3))] = ramp[6] if u < -0.2 else ramp[5]
    for (x, y), col in list(shape.items()):
        if (x + 1, y) not in shape or (x, y + 1) not in shape:
            u = mask.get((x, y), 1.0)
            if u > -0.1:
                shape[(x, y)] = ramp[0]
    return shape


# stade : (hauteur, largeur de tige, feuilles [(cote, attache, longueur, montee, chute, largeur)])
SORGHUM_STAGES = {
    3: (25, 2.6, [(-1, 3, 11, 3, 8, 3.4), (1, 7, 11, 3.5, 8, 3.4), (-1, 12, 10, 3.5, 7, 3.2),
                  (1, 16, 9, 3.5, 5, 3.0), (-1, 20, 7, 3.5, 3, 2.6)]),
    4: (38, 3.0, [(1, 4, 13, 3, 11, 3.8), (-1, 9, 14, 3.5, 11, 3.8), (1, 14, 14, 3.5, 10, 3.6),
                  (-1, 19, 13, 4, 9, 3.6), (1, 24, 12, 4, 8, 3.4), (-1, 29, 10, 4, 6, 3.0),
                  (1, 33, 7, 4, 3, 2.6)]),
    5: (44, 3.2, [(-1, 4, 13, 3, 12, 3.8), (1, 9, 14, 3.5, 12, 4.0), (-1, 14, 14, 3.5, 11, 3.8),
                  (1, 20, 14, 4, 10, 3.8), (-1, 26, 13, 4, 9, 3.6), (1, 31, 11, 4, 7, 3.2),
                  (-1, 36, 9, 4.5, 4, 3.0)]),
    6: (44, 3.2, [(1, 4, 13, 2.5, 13, 3.8), (-1, 9, 14, 3, 13, 4.0), (1, 14, 14, 3, 12, 3.8),
                  (-1, 20, 14, 3.5, 11, 3.8), (1, 26, 13, 3.5, 10, 3.6), (-1, 31, 11, 4, 8, 3.2),
                  (1, 36, 9, 4, 5, 3.0)]),
    7: (35, 3.2, [(-1, 4, 13, 2, 14, 3.8), (1, 9, 14, 2.5, 14, 4.0), (-1, 14, 14, 3, 13, 3.8),
                  (1, 19, 14, 3, 12, 3.8), (-1, 24, 13, 3.5, 10, 3.6), (1, 28, 11, 3.5, 8, 3.2)]),
}


def sorghum(age):
    tall = age >= TALL_FROM
    c = Canvas(R, 2 * R if tall else R)
    g = c.h - 1
    if age <= 2:
        h = {0: 6, 1: 11, 2: 17}[age]
        blades = [(15, h, -4, 2.4, -1.5), (17, h - 2, 4, 2.4, 1.5), (16, h + 1, 0.5, 2.6, 0.5),
                  (14, h - 4, -6, 2.2, -2), (18, h - 5, 6, 2.2, 2)][: 3 + (age >= 1) + (age >= 2)]
        for x0, hh, lean, w, curl in blades:
            c.paste(upright(x0, g + 1, hh, lean, w, SPROUT, curl=curl))
        return c
    h, width, leaves = SORGHUM_STAGES[age]
    ramp = LEAF if age <= 5 else mix_ramp(LEAF, OLIVE, 0.5 if age == 6 else 0.8)
    top = g - h
    back = [l for k, l in enumerate(leaves) if k % 2 == 1]
    front = [l for k, l in enumerate(leaves) if k % 2 == 0]
    for side, up, length, rise, droop, w in back:
        c.paste(blade(16 + side * 1.2, g - up, side, length, rise, droop, w, scale(ramp, 0.86), rib=4))
    c.paste(stalk(16, g + 1, 16, top, width, ramp, nodes=7 if tall else 0), shadow=False)
    for side, up, length, rise, droop, w in front:
        c.paste(blade(16 + side * 1.2, g - up, side, length, rise, droop, w, ramp, rib=4))
    if age <= 4:
        c.paste(upright(16, top + 6, 8, -1, 2.6, ramp, curl=-1))
        c.paste(upright(16.5, top + 5, 7, 1.5, 2.2, ramp, curl=1))
    elif age == 5:
        # la tete qui sort de la derniere feuille, verte et serree
        c.paste(panicle(16, top - 11, 5, 12, mix_ramp(SPROUT, FONIO, 0.35), seed=5, droop=0.5, specks=0.0))
        c.paste(upright(14.5, top + 4, 8, -2.5, 3.0, ramp, curl=-1))
    elif age == 6:
        c.paste(stalk(16, top + 1, 16.5, top - 3, 2.0, ramp), shadow=False)
        c.paste(panicle(16.5, top - 17, 10, 17, mix_ramp(SORGHUM, KAMUT, 0.16), seed=6, droop=1.0))
    else:
        c.paste(stalk(16, top + 1, 17, top - 4, 2.0, ramp), shadow=False)
        c.paste(panicle(17, top - 26, 14, 25, SORGHUM, seed=7, droop=2.0, dark=1, specks=0.22))
    return c


# -- Le soja ------------------------------------------------------------------------------------------

def trifoliate(x0, y0, side, angle, size, ramp, petiole=None):
    """Une feuille de soja : un long petiole, trois folioles ovales et pointues (deux laterales un
    peu en retrait, la terminale devant), chacune sa nervure."""
    a = math.radians(angle)
    pl = size * 0.75 if petiole is None else petiole
    px, py = x0 + side * math.cos(a) * pl, y0 - math.sin(a) * pl
    shape = {}
    shape.update(ribbon([(x0, y0), ((x0 + px) / 2, (y0 + py) / 2 - 0.6), (px, py)], const(1.2), ramp, tip=1.1))
    for da in (48, -42):
        shape.update(broad_leaf(px, py, side, size * 0.8, angle + da, size * 0.52, scale(ramp, 0.9), petiole=0.5,
                                droop=0.12, veins=size >= 7))
    shape.update(broad_leaf(px, py, side, size, angle + 4, size * 0.56, ramp, petiole=0.7, droop=0.18,
                            veins=size >= 7))
    return shape


def pod(x0, y0, x1, y1, ramp, width=3.0, beans=3, bend=1.0, fuzz=0.0, seed=1, amp=0.22, shine=False):
    """Une gousse : une bande bosselee d'autant de graines, courbee, ombree, contournee ; fuzz : le
    duvet clair sur les bords."""
    rng = random.Random(seed)
    mx, my = (x0 + x1) / 2, (y0 + y1) / 2
    dx, dy = x1 - x0, y1 - y0
    n = math.hypot(dx, dy) or 1
    ctrl = (mx - dy / n * bend, my + dx / n * bend)

    def w(t):
        env = max(0.0, math.sin(math.pi * min(1.0, 0.06 + t * 0.94))) ** 0.35
        bump = (1 - amp) + amp * abs(math.sin(beans * math.pi * t)) if beans else 1.0
        return width * env * bump

    shape = ribbon([(x0, y0), ctrl, (x1, y1)], w, ramp, tip=0.95, sheen=None if shine else (0.15, 0.7))
    if shine and beans:
        # un reflet arrondi sur chaque graine, du cote de la lumiere
        for k in range(beans):
            t = (k + 0.5) / beans
            cx, cy = bez([(x0, y0), ctrl, (x1, y1)], t)
            tx, ty = bez_tan([(x0, y0), ctrl, (x1, y1)], t)
            nx, ny = -ty, tx
            if nx * LIGHT[0] + ny * LIGHT[1] < 0:
                nx, ny = -nx, -ny
            for o, tone in ((width * 0.22, 6), (width * 0.05, 5), (width * 0.3, 5)):
                key = (int(math.floor(cx + nx * o)), int(math.floor(cy + ny * o)))
                if key in shape:
                    shape[key] = ramp[tone]
        # le creux entre deux graines, plus sombre
        for k in range(1, beans):
            t = k / beans
            cx, cy = bez([(x0, y0), ctrl, (x1, y1)], t)
            key = (int(math.floor(cx)), int(math.floor(cy)))
            if key in shape:
                shape[key] = ramp[2]
    if fuzz:
        for (x, y) in list(shape):
            for ox, oy in ((-1, 0), (0, -1)):
                if (x + ox, y + oy) not in shape and rng.random() < fuzz:
                    shape[(x + ox, y + oy)] = ramp[6]
    return shape


def soy_flower(x, y, ramp=MAUVE):
    """Une petite fleur papilionacee : l'etendard clair, la carene plus sombre."""
    return sprite({}, x, y, [".54.",
                             "5643",
                             ".32."], tones(ramp))


# stade : (hauteur de tige, feuilles [(cote, attache, angle, taille, petiole)])
SOY = {
    2: (12, [(-1, 8, 25, 6.5, 3), (1, 10, 35, 6.5, 3)]),
    3: (17, [(-1, 6, 5, 7.5, 4), (1, 9, 15, 7.5, 4), (-1, 13, 40, 6.5, 2.5)]),
    4: (22, [(-1, 5, -5, 8.5, 5), (1, 8, 0, 8.5, 5), (-1, 13, 25, 8.0, 4), (1, 15, 30, 7.5, 4),
             (-1, 19, 55, 6.5, 2)]),
    5: (23, [(-1, 5, -5, 8.5, 5), (1, 8, 0, 8.5, 5), (-1, 13, 25, 8.0, 4), (1, 15, 30, 7.5, 4),
             (-1, 19, 55, 6.5, 2)]),
    6: (23, [(-1, 5, -5, 8.5, 5), (1, 8, 0, 8.5, 5), (-1, 13, 25, 8.0, 4), (1, 15, 30, 7.5, 4),
             (-1, 19, 55, 6.5, 2)]),
}


def soy(age):
    c = Canvas()
    g = c.h - 1
    if age == 0:
        # la crosse qui sort de terre, deux cotyledons ronds
        c.paste(ribbon([(16, g + 1), (15.5, g - 3), (17, g - 5)], const(1.8), SPROUT, tip=1.1), shadow=False)
        c.paste(oval(14.5, g - 5.5, 2.4, 1.7, SPROUT))
        c.paste(oval(19, g - 5.5, 2.4, 1.7, scale(SPROUT, 0.92)))
        return c
    if age == 1:
        c.paste(stalk(16, g + 1, 16, g - 9, 1.8, SPROUT), shadow=False)
        c.paste(oval(13.5, g - 3.5, 2.4, 1.6, mix_ramp(SPROUT, FONIO, 0.4)))
        c.paste(oval(18.5, g - 3.5, 2.4, 1.6, scale(mix_ramp(SPROUT, FONIO, 0.4), 0.92)))
        c.paste(broad_leaf(16, g - 9, -1, 5, 25, 4.0, SPROUT, petiole=1, droop=0.2, veins=False))
        c.paste(broad_leaf(16, g - 9, 1, 5, 30, 4.0, SPROUT, petiole=1, droop=0.2, veins=False))
        return c
    if age == 7:
        return soy_ripe()
    h, leaves = SOY[age]
    ramp = SPROUT if age <= 2 else LEAF if age < 6 else scale(LEAF, 0.86)
    top = g - h
    back = [l for k, l in enumerate(leaves) if k % 2 == 1]
    front = [l for k, l in enumerate(leaves) if k % 2 == 0]
    for side, up, angle, size, pet in back:
        c.paste(trifoliate(16, g - up, side, angle, size, scale(ramp, 0.88), petiole=pet))
    c.paste(stalk(16, g + 1, 16, top, 1.8, ramp), shadow=False)
    for side, up, angle, size, pet in front:
        c.paste(trifoliate(16, g - up, side, angle, size, ramp, petiole=pet))
    c.paste(trifoliate(16, top + 1, 1, 85, 6.0 if age >= 3 else 5.0, ramp, petiole=1.5))
    if age == 5:
        for (x, y, r) in ((11, 14, MAUVE), (20, 17, WHITE), (13, 21, MAUVE), (19, 10, WHITE), (16, 19, MAUVE),
                          (22, 23, WHITE)):
            c.paste(soy_flower(x, y, r), shadow=False)
    if age == 6:
        pods = mix_ramp(POD_GREEN, FONIO, 0.3)
        for k, (x, y, dx, dy) in enumerate(((12, 12, -3, 7), (20, 14, 3, 7), (10, 19, -3, 6), (22, 20, 3, 6),
                                            (14, 22, -2, 6), (18, 23, 2, 6), (8, 25, -2, 5))):
            c.paste(pod(x, y, x + dx, y + dy, pods, width=2.6, beans=3, bend=0.6 * (1 if dx > 0 else -1),
                        fuzz=0.12, seed=k))
    return c


def soy_ripe():
    """Le soja mur, comme sur la planche : une tige doree et, en epi de part et d'autre, des
    gousses dodues qui pointent vers le haut en s'ecartant, chacune detachee par son contour."""
    c = Canvas()
    g = c.h - 1
    stem = mix_ramp(POD, BARK, 0.25)
    c.paste(stalk(16, g + 1, 16, 3, 2.4, stem), shadow=False)
    pods = [(1, 27, 38, 10.0), (-1, 24, 142, 10.0), (1, 20, 45, 9.5), (-1, 17, 135, 9.5), (1, 13, 52, 8.5),
            (-1, 10, 128, 8.0)]
    for k, (side, y, ang, length) in enumerate(pods):
        a = math.radians(ang)
        x0 = 16 + side * 1.0
        x1, y1 = x0 + math.cos(a) * length, y - math.sin(a) * length
        c.paste(pod(x0, y, x1, y1, POD, width=3.6, beans=0, bend=0.6 * side, fuzz=0.0, seed=20 + k))
    c.paste(pod(16, 7, 17, 0, POD, width=3.4, beans=0, bend=0.3, seed=40))
    return c


# -- Les graminees ------------------------------------------------------------------------------------

def line(x0, y0, x1, y1, colors):
    """Un trait d'un pixel, la couleur suivant la longueur (liste du debut a la fin)."""
    shape = {}
    n = max(1, int(max(abs(x1 - x0), abs(y1 - y0)) * 2))
    for i in range(n + 1):
        t = i / n
        key = (int(math.floor(x0 + (x1 - x0) * t)), int(math.floor(y0 + (y1 - y0) * t)))
        shape[key] = colors[min(len(colors) - 1, int(t * len(colors)))]
    return shape


# Les grains d'epi, dessines a la main : a gauche du rachis (pointe en haut a gauche), a droite
# (pointe en haut a droite), et celui du sommet. Tons : 1 a 6.
EAR_GRAINS = {
    "barley": (["65.",
                "554",
                ".32"],
               [".56",
                "455",
                "23."],
               [".5.",
                "565",
                "454",
                ".3."]),
    "kamut": (["65..",
               "5654",
               ".543",
               "..32"],
              ["..56",
               "4565",
               "3454",
               "23.."],
              [".55.",
               "5665",
               "4554",
               ".43."]),
    "rye": (["5.",
             "54",
             ".3"],
            [".5",
             "45",
             "3."],
            ["5",
             "6",
             "4"]),
}

# rangs, pas, ecart des grains au rachis, barbes (longueur, sur combien de rangs du haut)
EAR_SPEC = {
    "barley": (5, 2, 1, 2, 5),
    "kamut": (4, 3, 1, 7, 3),
    "rye": (6, 2, 1, 3, 6),
}


def ear(cx, top, ramp, kind, tilt=0.0, awn_ramp=None, scale_=1.0, seed=1):
    """Un epi vu de cote, en arete de poisson comme sur la planche : un rachis sombre, et de
    chaque cote des grains dessines a la main qui montent en s'ecartant, decales d'un rang a
    l'autre ; au bout, les barbes. tilt : l'epi penche (decalage des rangs, en pixels par
    pixel de hauteur). Rend (piece, point d'attache en bas)."""
    rng = random.Random(seed)
    left, right, crown = EAR_GRAINS[kind]
    n, step, gap, awn, awn_rows = EAR_SPEC[kind]
    n = max(3, int(round(n * scale_)))
    awn = int(round(awn * scale_))
    awn_ramp = awn_ramp or ramp
    gw = len(left[0])
    cw = len(crown[0])
    grid = {}  # (x, y) relatifs au sommet, x = 0 sur le rachis -> ton

    def stamp(rows, x0, y0):
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch != ".":
                    grid[(x0 + dx, y0 + dy)] = ("g", int(ch))

    def awn_line(x0, y0, dx, length):
        for k in range(length):
            x = x0 + int(round(dx * (k + 1)))
            y = y0 - 1 - k
            if (x, y) not in grid:
                grid[(x, y)] = ("a", 5 if k == 0 else 4 if k < length * 0.6 else 3 if k < length - 1 else 2)

    height = len(crown) + n * step + 2
    # le rachis, sombre, entre les grains
    for y in range(1, height):
        grid[(0, y)] = ("g", 1)
    stamp(crown, -(cw // 2), 0)
    for i in range(n):
        ly = len(crown) - 1 + i * step
        stamp(left, -gap - gw + 1, ly)
        stamp(right, gap, ly + 1)
    # les barbes : du bout des grains du haut, vers le haut en s'ouvrant
    if awn > 0:
        awn_line(0, 0, rng.choice((-0.15, 0.0, 0.15)), awn + 1)
        for i in range(min(n, awn_rows)):
            ly = len(crown) - 1 + i * step
            length = max(1, awn - i // 2)
            awn_line(-gap - gw + 1, ly, -0.3 - 0.05 * i, length)
            awn_line(gap + gw - 1, ly + 1, 0.3 + 0.05 * i, length)
    shape = {}
    for (x, y), (kind_, tone) in grid.items():
        shift = int(round(tilt * (height - y)))
        color = (awn_ramp if kind_ == "a" else ramp)[max(0, min(6, tone))]
        shape[(int(cx) + x + shift, int(top) + y)] = color
    base = (cx + 0.5, top + height)
    return shape, base


OAT_SPIKELET = [".5.",
                "565",
                "454",
                "343",
                "2.2"]


def oat_panicle(cx, top, ramp, size=1.0, seed=3, droop=0.0):
    """Une panicule d'avoine : un axe fin, des rameaux capillaires qui s'ecartent et retombent,
    au bout de chacun un epillet en clochette (deux glumes pointues) qui pend."""
    rng = random.Random(seed)
    shape = {}
    h = max(6, int(12 * size))
    axis = [(cx + droop * (i / h) ** 2, top + i) for i in range(h + 1)]
    shape.update(line(axis[0][0], axis[0][1], axis[-1][0], axis[-1][1], [ramp[3], ramp[2]]))
    tips = [(axis[0][0], axis[0][1] - 1)]
    levels = ((0.25, -1, 4), (0.35, 1, 4), (0.55, -1, 5), (0.65, 1, 5), (0.85, -1, 3), (0.9, 1, 3))
    for frac, side, length in levels[: 4 if size < 1 else 6]:
        ax, ay = axis[int(frac * h)]
        length = length * size + rng.uniform(-0.5, 0.5)
        ex, ey = ax + side * length, ay + length * 0.3
        shape.update(line(ax, ay, ex, ey, [ramp[3], ramp[3], ramp[2]]))
        tips.append((ex, ey))
    pal = tones(ramp)
    for ex, ey in tips:
        sprite(shape, int(ex) - 1, int(ey), OAT_SPIKELET, pal)
    return shape


def fonio_panicle(cx, top, ramp, size=1.0, seed=4, arms=3):
    """Une panicule de fonio : deux a quatre grappes greles qui partent du sommet, s'arquent et
    retombent, perlees de grains minuscules (un clair, un sombre), piquees de points bruns."""
    rng = random.Random(seed)
    shape = {}

    def beads(t, v, tone, key):
        k = int(t * 14)
        tone = tone + (1 if k % 2 == 0 else -1)
        if rng.random() < 0.08:
            tone = 1
        return tone

    for k in range(arms):
        side = (-1, 1, -1, 1)[k]
        spread = (0.55 + 0.45 * (k // 2)) * side
        length = (10 + rng.uniform(-1.5, 1.0)) * size
        pts = [(cx, top + 1), (cx + spread * length * 0.25, top - length * 0.25),
               (cx + spread * length * 0.75, top - length * 0.05), (cx + spread * length * 0.9, top + length * 0.55)]
        shape.update(ribbon(pts, lambda t: 2.6 * (1 - 0.5 * t), ramp, tip=0.97, pattern=beads))
    return shape


# Les touffes : brins (pied x, hauteur, ecart de la pointe, largeur, inclinaison de la pointe)
GRASS_BLADES = {
    0: [(15, 6, -3, 2.4, -1), (16.5, 7, 2, 2.6, 1), (16, 5, 0, 2.2, 0)],
    1: [(14, 9, -5, 2.8, -1.5), (15.5, 11, -2, 3.0, -1), (17, 10, 2, 3.0, 1), (18, 8, 5, 2.8, 1.5),
        (16, 7, 0, 2.6, 0)],
    2: [(13, 12, -8, 3.0, -2), (14.5, 15, -4, 3.2, -1.5), (16, 16, -1, 3.2, -0.5), (17, 15, 2, 3.2, 1),
        (18.5, 13, 6, 3.0, 2), (15, 9, -2, 2.8, 0), (17.5, 10, 3, 2.8, 1)],
    3: [(12, 15, -10, 3.2, -3), (13.5, 19, -6, 3.4, -2), (15, 22, -3, 3.4, -1), (16.5, 22, 1, 3.4, 1),
        (18, 20, 4, 3.4, 2), (19.5, 16, 9, 3.2, 3), (14, 12, -3, 3.0, -1), (18, 13, 4, 3.0, 1),
        (16, 17, 0, 3.0, 0)],
    4: [(12, 13, -10, 3.2, -3), (14, 16, -7, 3.4, -2), (18, 16, 7, 3.4, 2), (20, 13, 10, 3.2, 3),
        (16, 11, 1, 3.0, 1)],
    5: [(12, 10, -10, 3.0, -3), (14, 12, -8, 3.2, -3), (18, 12, 8, 3.2, 3), (20, 10, 10, 3.0, 3)],
    6: [(12, 9, -10, 3.0, -3.5), (14, 10, -8, 3.0, -3), (18, 10, 8, 3.0, 3), (20, 9, 10, 3.0, 3.5)],
    7: [(12, 8, -10, 3.0, -4), (14, 9, -8, 3.0, -3.5), (18, 9, 8, 3.0, 3.5), (20, 8, 10, 3.0, 4)],
}

# Les tiges a epi : (pied x, sommet x, sommet y) pour quatre epis larges ou cinq fins
GRASS_STEMS = {
    4: ([(14, 8, 11), (16, 15, 8), (17, 22, 10)], [(14, 7, 11), (15, 13, 8), (17, 19, 9), (18, 25, 12)]),
    5: ([(14, 5, 4), (15, 12, 1), (17, 19, 2), (18, 26, 5)],
        [(13, 4, 5), (15, 10, 2), (16, 16, 1), (17, 22, 3), (19, 28, 6)]),
    6: ([(14, 5, 4), (15, 12, 1), (17, 19, 2), (18, 26, 5)],
        [(13, 4, 5), (15, 10, 2), (16, 16, 1), (17, 22, 3), (19, 28, 6)]),
    7: ([(14, 5, 5), (15, 12, 2), (17, 19, 3), (18, 26, 6)],
        [(13, 4, 6), (15, 10, 3), (16, 16, 2), (17, 22, 4), (19, 28, 7)]),
}

GRASS = {
    # vert du feuillage, ton mur des tiges et des feuilles, ton de l'epi, ton des barbes, epis larges
    "barley": (SPROUT, STRAW, GOLD, GOLD, True),
    "kamut": (SPROUT, STRAW, KAMUT, mix_ramp(KAMUT, BARK, 0.4), True),
    "rye": (RYE_GREEN, RYE, RYE, RYE, False),
    "oat": (SPROUT, OAT, OAT, OAT, True),
    "fonio": (SPROUT, FONIO, FONIO, FONIO, False),
}

def fonio_tops(c, age, stalks, heads, g):
    """Le fonio en epiaison : des tiges greles et droites qui s'ouvrent a peine, chargees sur
    leurs deux tiers du haut de grains minuscules, clairs et sombres en alternance, piques de
    points bruns ; une masse touffue et mouchetee, comme sur la planche."""
    rng = random.Random(70 + age)
    density = {4: 0.25, 5: 0.5, 6: 0.8, 7: 1.0}[age]
    stems = [(13, 6, 22), (14, 10, 26), (15, 13, 28), (16, 16, 29), (17, 19, 28), (18, 22, 26), (19, 26, 22),
             (15, 8, 24), (17, 24, 24)]
    if age == 4:
        stems = stems[1:6]
    tops = []
    for k, (x0, tx, h) in enumerate(stems):
        h = int(h * (0.8 if age == 4 else 1.0))
        top = (tx, g - h)
        depth = 0.88 if k >= 7 else 1.0
        c.paste(ribbon([(x0, g + 1), (x0 + (tx - x0) * 0.3, g - h * 0.5), top], const(1.3), scale(stalks, depth),
                       tip=1.1), shadow=False)
        tops.append((x0, tx, h, depth))
    for x0, tx, h, depth in tops:
        pts = [(x0, g + 1), (x0 + (tx - x0) * 0.3, g - h * 0.5), (tx, g - h)]
        n = int(h * 2.2 * density)
        for i in range(n):
            t = rng.uniform(0.35, 1.0)
            x, y = bez(pts, t)
            side = rng.choice((-1, 1))
            off = rng.uniform(0.6, 1.8 + 1.2 * (1 - t))
            gx, gy = int(math.floor(x + side * off)), int(math.floor(y + rng.uniform(-0.5, 0.5)))
            r = rng.random()
            tone = 6 if r < 0.12 else 5 if r < 0.42 else 4 if r < 0.62 else 2 if r < 0.9 else 1
            col = scale(heads, depth)[tone]
            if rng.random() < 0.07 * density:
                col = BARK[1]
            c.put(gx, gy, col)
            if tone >= 4 and rng.random() < 0.5:
                c.put(gx + (1 if side > 0 else 0), gy + 1, scale(heads, depth)[tone - 2])


RIPE = {4: 0.0, 5: 0.15, 6: 0.55, 7: 1.0}


def grasses(name, age):
    c = Canvas()
    g = c.h - 1
    green, ripe_stalk, head, awn, wide = GRASS[name]
    ripe = RIPE.get(age, 0.0)
    leaves = mix_ramp(green, ripe_stalk, ripe * 0.85)
    # chaque espece a son port : le fonio grele, le kamut epais, l'avoine un peu plus haute
    widths = {"fonio": 0.72, "kamut": 1.12, "oat": 0.95, "rye": 0.88}.get(name, 1.0)
    heights = {"oat": 1.06, "rye": 1.08, "fonio": 0.92}.get(name, 1.0)
    blades = [(x0, min(29, h * heights), lean, w * widths, curl) for x0, h, lean, w, curl in GRASS_BLADES[age]]
    back = blades[0::2] if age >= 4 else blades
    for x0, h, lean, w, curl in back:
        c.paste(upright(x0, g + 1, h, lean, w, scale(leaves, 0.9) if age < 4 else scale(leaves, 0.92), curl=curl))
    if age < 4:
        return c
    stalks = mix_ramp(green, ripe_stalk, 0.15 + ripe * 0.85)
    heads = mix_ramp(green, head, 0.25 + ripe * 0.75)
    awns = mix_ramp(green, awn, 0.25 + ripe * 0.75)
    if name == "fonio":
        fonio_tops(c, age, stalks, heads, g)
        for x0, h, lean, w, curl in blades[1::2]:
            c.paste(upright(x0, g + 1, h, lean, w, leaves, curl=curl))
        return c
    stems = GRASS_STEMS[age][0 if wide else 1]
    young = age == 4
    order = sorted(range(len(stems)), key=lambda k: -stems[k][2])  # les plus hautes derriere
    pieces = []
    for k in order:
        x0, tx, ty = stems[k]
        depth = 0.9 if k % 2 == 0 else 1.0
        sr, hr, ar = scale(stalks, depth), scale(heads, depth), scale(awns, depth)
        if name in ("barley", "kamut", "rye"):
            piece, (bx, by) = ear(tx, ty, hr, name, tilt=0.0, awn_ramp=ar, scale_=0.65 if young else 1.0, seed=k)
        elif name == "oat":
            piece = oat_panicle(tx, ty, hr, size=0.7 if young else 1.0, seed=k, droop=(tx - 16) * 0.12)
            bx, by = tx, ty + 1
        else:
            piece = fonio_panicle(tx, ty, hr, size=0.7 if young else 1.0, seed=k, arms=2 if young else 3)
            bx, by = tx, ty + 1
        c.paste(ribbon([(x0, g + 1), (x0 + (bx - x0) * 0.25, (g + by) / 2), (bx, by)], const(1.8), sr, tip=1.1),
                shadow=False)
        c.paste(piece)
    for x0, h, lean, w, curl in blades[1::2]:
        c.paste(upright(x0, g + 1, h, lean, w, leaves, curl=curl))
    return c


# -- Les icones et les graines --------------------------------------------------------------------

def capsule(w, flat=0.0):
    """Le profil d'un grain : bouts arrondis (flat : un bout plus plat, 0 a 1)."""
    return lambda t: w * max(0.0, math.sin(math.pi * min(1.0, max(0.0, t)))) ** (0.5 - 0.2 * flat)


def grain(x0, y0, x1, y1, width, ramp, crease=True, bend=0.0, pattern=None):
    """Un gros grain vu de cote : une capsule ombree, son sillon sombre au milieu, un reflet."""
    mx, my = (x0 + x1) / 2, (y0 + y1) / 2
    dx, dy = x1 - x0, y1 - y0
    n = math.hypot(dx, dy) or 1
    ctrl = (mx - dy / n * bend, my + dx / n * bend)
    return ribbon([(x0, y0), ctrl, (x1, y1)], capsule(width), ramp, rib=2 if crease else None, tip=1.1,
                  sheen=(0.2, 0.55), pattern=pattern)


def bundle(ears, tie_y=21, band=BARK):
    """Une gerbe : des epis dont les tiges se croisent sous un lien de paille tressee."""
    c = Canvas()
    for piece in ears:
        c.paste(piece)
    for x in range(10, 22):
        k = x - 10
        c.put(x, tie_y, band[4] if k % 3 else band[5])
        c.put(x, tie_y + 1, band[2] if k % 3 else band[3])
        c.put(x, tie_y + 2, band[0])
    return c


def stems_to(c_shape, x0, y0, x1, y1, ramp):
    c_shape.update(ribbon([(x0, y0), ((x0 + x1) / 2, (y0 + y1) / 2), (x1, y1)], const(1.8), ramp, tip=1.1))


def sheaf_icon(name):
    """L'icone des cereales a epi : trois epis noues en gerbe, tiges croisees en bas."""
    green, ripe_stalk, head, awn, wide = GRASS[name]
    pieces = []
    tops = ((9, 3, -0.18), (16, 1, 0.0), (23, 3, 0.18)) if wide else ((8, 4, -0.2), (16, 1, 0.0),
                                                                     (24, 4, 0.2))
    for k, (x, y, tilt) in enumerate(tops):
        shape = {}
        foot_x = 16 + (16 - x) * 0.5
        stems_to(shape, x + 0.5, y + 15, foot_x, 30, ripe_stalk)
        if name in ("barley", "kamut", "rye"):
            piece, base = ear(x, y, head, name, tilt=tilt * 0.5, awn_ramp=awn, seed=k)
        elif name == "oat":
            piece = oat_panicle(x, y + 1, head, size=1.0, seed=k, droop=tilt * 6)
        else:
            piece = fonio_panicle(x, y + 3, head, size=0.9, seed=k, arms=3)
        shape.update(piece)
        pieces.append(shape)
    return bundle([pieces[0], pieces[2], pieces[1]])


def corn_icon():
    """L'epi de mais de la planche : en diagonale, ses rangs de grains, ses spathes vertes qui
    s'ouvrent a la base, ses soies au bout."""
    c = Canvas()
    L = 26.0

    def kernels(t, v, tone, key):
        x, y = key
        row = int(t * L / 2.0)
        col = int((v + 1) * 2.6)
        lit = 1 if (row + col) % 2 == 0 else 0
        base = 5 if v > 0.45 else 4 if v > -0.05 else 3 if v > -0.5 else 2
        return base + (1 if lit and tone > 0 else -1 if not lit else 0) if tone > 0 else 0

    cob = ribbon([(9, 25), (16, 16), (26, 5)], capsule(10.5), COB, tip=1.1, pattern=kernels)
    back = ribbon([(7, 29), (14, 24), (20, 19)], lance(5.0), scale(LEAF, 0.85), sheen=(0.2, 0.5))
    c.paste(back)
    c.paste(cob)
    c.paste(ribbon([(7, 29), (5, 20), (7, 12)], lance(5.2), LEAF, sheen=(0.15, 0.5)))
    c.paste(ribbon([(8, 29), (13, 26), (21, 23)], lance(5.0), mix_ramp(LEAF, SPROUT, 0.3), sheen=(0.15, 0.5)))
    c.paste(ribbon([(8, 30), (9, 24), (12, 18)], lance(3.6), SPROUT, sheen=(0.2, 0.6)))
    for k, (x, y) in enumerate(((26, 3), (27, 2), (28, 3), (27, 4), (29, 1), (25, 2))):
        c.put(x, y, SILK[5 - k % 3])
    return c


def sunflower_icon():
    c = Canvas()
    c.paste(flower_head(16, 16, 14.6, 7.0, petals=22, seed=11))
    return c


def soy_icon():
    """Une gousse de soja verte et duveteuse, bosselee de ses trois graines, et deux graines
    blondes devant."""
    c = Canvas()
    green = mix_ramp(POD_GREEN, FONIO, 0.12)
    c.paste(pod(4, 26, 25, 5, green, width=9.0, beans=3, bend=2.5, fuzz=0.3, seed=3, amp=0.4, shine=True))
    c.paste(ribbon([(25, 5), (27, 3), (28, 1)], const(1.6), LEAF, tip=1.1))
    bean = mix_ramp(POD, WHITE, 0.25)
    for (x, y) in ((19, 24), (25, 27)):
        b = oval(x, y, 3.8, 3.2, bean)
        b[(int(x + 1), int(y + 1))] = POD[0]
        b[(int(x + 2), int(y + 1))] = POD[0]
        c.paste(b)
    return c


def sorghum_icon():
    """La panicule de sorgho, sombre, sur son bout de tige."""
    c = Canvas()
    c.paste(ribbon([(14, 31), (15, 26), (16, 22)], const(2.4), mix_ramp(LEAF, OLIVE, 0.8), tip=1.1))
    c.paste(panicle(16, 1, 16, 23, SORGHUM, seed=9, droop=1.5, dark=1, specks=0.22))
    return c


def fonio_icon():
    """Une botte de fonio : des tiges greles liees en bas, qui s'ouvrent en eventail, chargees
    en haut de grains minuscules mouchetes."""
    green, ripe_stalk, head, awn, wide = GRASS["fonio"]
    c = Canvas()
    rng = random.Random(12)
    stems = [(-9, 3), (-6, 1), (-3, 0), (0, -1), (3, 0), (6, 1), (9, 3), (-4.5, 4), (4.5, 4)]
    for k, (dx, top) in enumerate(stems):
        depth = 0.88 if k >= 7 else 1.0
        pts = [(16 + dx * 0.15, 30), (16 + dx * 0.3, 18), (16 + dx, top + 2)]
        c.paste(ribbon(pts, const(1.3), scale(ripe_stalk, depth), tip=1.1), shadow=False)
        for i in range(26):
            t = rng.uniform(0.5, 1.0)
            x, y = bez(pts, t)
            side = rng.choice((-1, 1))
            gx = int(math.floor(x + side * rng.uniform(0.6, 2.0)))
            gy = int(math.floor(y + rng.uniform(-0.5, 0.5)))
            r = rng.random()
            tone = 6 if r < 0.12 else 5 if r < 0.42 else 4 if r < 0.62 else 2 if r < 0.9 else 1
            c.put(gx, gy, BARK[1] if rng.random() < 0.06 else scale(head, depth)[tone])
    for x in range(12, 21):
        k = x - 12
        c.put(x, 22, BARK[4] if k % 3 else BARK[5])
        c.put(x, 23, BARK[2] if k % 3 else BARK[3])
        c.put(x, 24, BARK[0])
    return c


ICONS = {
    "corn": corn_icon,
    "fonio": fonio_icon,
    "sunflower": sunflower_icon,
    "soy": soy_icon,
    "sorghum": sorghum_icon,
}


def icon(name):
    if name in ICONS:
        return ICONS[name]()
    return sheaf_icon(name)


def kernel_corn(x, y, angle=0.0, ramp=COB):
    """Un grain de mais en forme de dent : le haut large et arrondi, la pointe en bas, le germe
    pale sur la face."""
    a = math.radians(angle)
    up = (math.sin(a), -math.cos(a))
    p0 = (x - up[0] * 5, y - up[1] * 5)
    p2 = (x + up[0] * 4, y + up[1] * 4)
    shape = ribbon([p0, (x, y), p2], lambda t: 8.0 * (0.3 + 0.7 * min(1.0, t * 1.3)) *
                   max(0.0, math.sin(math.pi * min(1.0, 0.2 + t * 0.8))) ** 0.35, ramp, tip=1.1, sheen=(0.55, 0.85))
    germ = mix_ramp(SILK, WHITE, 0.3)
    for k, (ox, oy) in enumerate(((0, 0), (0, 1), (-1, 1), (0, 2), (-1, 0))):
        key = (int(x + ox - up[0] * 1.5), int(y + oy - up[1] * 1.5))
        if key in shape:
            shape[key] = germ[5 if k == 0 else 4 if k < 3 else 3]
    return shape


def sunflower_seed(x0, y0, x1, y1):
    """Une graine de tournesol : une goutte noire striee de gris."""
    def stripes(t, v, tone, key):
        if tone == 0:
            return 0
        if abs(v) > 0.55 or abs(v) < 0.12:
            return min(6, tone + 2)
        return max(1, tone - 1)
    return ribbon([(x0, y0), ((x0 + x1) / 2, (y0 + y1) / 2), (x1, y1)],
                  lambda t: 6.0 * max(0.0, math.sin(math.pi * min(1.0, 0.08 + t * 0.92))) ** 0.6 * (1 - 0.35 * t),
                  SEED_HULL, tip=1.1, pattern=stripes)


def seeds_icon(name):
    c = Canvas()
    if name == "corn":
        c.paste(kernel_corn(10, 11, -20))
        c.paste(kernel_corn(21, 12, 15))
        c.paste(kernel_corn(15, 22, -5))
    elif name == "sunflower":
        c.paste(sunflower_seed(6, 23, 18, 7))
        c.paste(sunflower_seed(16, 28, 27, 13))
    elif name == "soy":
        bean = mix_ramp(POD, WHITE, 0.25)
        for (x, y, r) in ((11, 12, 5.0), (21, 15, 4.8), (14, 23, 5.0)):
            b = oval(x, y, r, r * 0.86, bean)
            for k in range(2):
                b[(int(x + 1 + k), int(y + 1))] = POD[0]
            c.paste(b)
    elif name == "fonio":
        # comme sur la planche : un grain olive-dore, ovale, en diagonale
        c.paste(grain(7, 25, 24, 7, 11.0, mix_ramp(FONIO, OLIVE, 0.25), crease=False))
    elif name == "sorghum":
        s = oval(16, 16, 9.5, 8.5, SORGHUM_SEED)
        s.update(oval(12.5, 12, 2.6, 2.2, mix_ramp(SORGHUM_SEED, WHITE, 0.25), outline=False))
        s[(9, 22)] = SORGHUM_SEED[0]
        c.paste(s)
    elif name == "kamut":
        c.paste(grain(5, 27, 27, 5, 9.0, KAMUT, crease=True, bend=0.8))
    else:
        ramp = {"barley": GOLD, "oat": mix_ramp(OAT, STRAW, 0.3), "rye": mix_ramp(RYE, RYE_GREEN, 0.25)}[name]
        width = {"barley": 7.0, "oat": 6.0, "rye": 5.4}[name]
        c.paste(grain(5, 20, 20, 7, width, ramp, bend=0.6))
        c.paste(grain(12, 28, 27, 14, width, scale(ramp, 1.04), bend=0.6))
    return c


def seeds(name):
    return seeds_icon(name)


# -- L'ecriture ---------------------------------------------------------------------------------------

STAGES = {"corn": corn, "sunflower": sunflower, "sorghum": sorghum, "soy": soy,
          "barley": lambda a: grasses("barley", a), "oat": lambda a: grasses("oat", a),
          "rye": lambda a: grasses("rye", a), "fonio": lambda a: grasses("fonio", a),
          "kamut": lambda a: grasses("kamut", a)}
ICONS_DONE = set(CEREALS)


def stage(name, age):
    return STAGES[name](age)


def write_json(data, *path):
    target = os.path.join(ASSETS, *path)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, "w", encoding="utf-8") as fh:
        fh.write(json.dumps(data, indent=2) + "\n")


def models(name, age, tall):
    texture = "dohy:block/%s_crop_stage_%d" % (name, age)
    if name in CROSS:
        parent = "dohy:block/crop_cross_tall" if tall else "dohy:block/crop_cross"
        data = {"parent": parent, "textures": {"cross": texture}}
    else:
        data = {"parent": "minecraft:block/crop", "textures": {"crop": texture}}
    write_json(data, "models", "block", "%s_crop_stage_%d.json" % (name, age))


def cross_models():
    """La croix des plantes a feuilles, descendue d'un pixel comme le modele crop de Minecraft,
    pour que la tige touche la terre labouree (plus basse d'un pixel qu'un bloc plein) ; et sa
    version haute de deux blocs."""
    for name, height in (("crop_cross", 16), ("crop_cross_tall", 32)):
        plane = {"uv": [0, 0, 16, 16], "texture": "#cross"}
        write_json({
            "ambientocclusion": False,
            "textures": {"particle": "#cross"},
            "elements": [
                {"from": [0.8, -1, 8], "to": [15.2, height - 1, 8], "shade": False,
                 "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
                 "faces": {"north": dict(plane), "south": dict(plane)}},
                {"from": [8, -1, 0.8], "to": [8, height - 1, 15.2], "shade": False,
                 "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
                 "faces": {"west": dict(plane), "east": dict(plane)}},
            ],
        }, "models", "block", name + ".json")


def preview_sheet(rows, path, s=4):
    """La planche : les huit stades de chaque culture sur une bande de terre, l'icone et la
    graine au bout."""
    cell = R + 4
    sheet = Image.new("RGBA", ((cell * 10 + 4) * s, len(rows) * (2 * R + 6) * s), (128, 176, 222, 255))
    for r, (name, images, item, seed) in enumerate(rows):
        y0 = r * (2 * R + 6) * s
        soil = Image.new("RGBA", (cell * 8 * s, 4 * s), (96, 60, 26, 255))
        sheet.alpha_composite(soil, (0, y0 + (2 * R) * s))
        for a, image in enumerate(images):
            big = image.resize((image.width * s, image.height * s), Image.NEAREST)
            sheet.alpha_composite(big, (a * cell * s, y0 + (2 * R + 2 - image.height) * s))
        if item is not None:
            sheet.alpha_composite(item.resize((R * s, R * s), Image.NEAREST), (8 * cell * s + 4 * s, y0 + 4 * s))
        if seed is not None:
            sheet.alpha_composite(seed.resize((R * s, R * s), Image.NEAREST),
                                  (8 * cell * s + 4 * s, y0 + (R + 4) * s))
    sheet.save(path)


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    rows = []
    for name in CEREALS:
        if name not in STAGES:
            continue
        images = []
        for age in range(8):
            canvas = stage(name, age)
            image = canvas.image()
            image.save(os.path.join(TEX, "block", "%s_crop_stage_%d.png" % (name, age)))
            models(name, age, canvas.h > R)
            images.append(image)
        item = seed = None
        if name in ICONS_DONE:
            item = icon(name).image()
            seed = seeds(name).image()
            item.save(os.path.join(TEX, "item", name + ".png"))
            seed.save(os.path.join(TEX, "item", name + "_seeds.png"))
        rows.append((name, images, item, seed))
    cross_models()
    if preview:
        os.makedirs(preview, exist_ok=True)
        preview_sheet(rows, os.path.join(preview, "cultures.png"))


if __name__ == "__main__":
    main()

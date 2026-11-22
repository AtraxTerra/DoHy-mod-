#!/usr/bin/env python3
"""Les assets de l'economie souterraine (lot 24c) : textures originales, modeles, etats de
blocs, definitions d'objets, tables de butin, recettes et tags.

    python3 -I tools/underground_assets.py

Tout est dessine ici, pixel par pixel, a la palette du mod : rien n'est copie d'ailleurs.
Le script est deterministe ; relance, il reecrit les memes fichiers. Il ne touche qu'aux
fichiers de l'economie souterraine (la liste ITEMS, CROPS et BLOCKS ci-dessous) : les langues
se completent a la main, par fusion.
"""
import json
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dohy")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
NS = "dohy"

# -- La palette ---------------------------------------------------------------------------------

T = (0, 0, 0, 0)
OUT = (30, 26, 34)
GREEN = [(28, 64, 30), (46, 104, 44), (74, 148, 60), (118, 190, 86), (170, 222, 120)]
COCA = [(34, 80, 30), (60, 130, 46), (96, 170, 64), (150, 206, 96)]
POD = [(70, 90, 50), (110, 140, 80), (150, 182, 110), (196, 214, 150)]
WHITE = [(150, 150, 165), (200, 200, 212), (232, 232, 240), (255, 255, 255)]
BEIGE = [(102, 70, 44), (146, 104, 66), (186, 142, 98), (220, 184, 140)]
PINK = [(160, 50, 100), (220, 90, 150), (248, 150, 190), (255, 210, 230)]
BLUE = [(40, 80, 160), (80, 140, 220), (140, 190, 245), (210, 230, 255)]
CYAN = [(40, 110, 160), (80, 170, 210), (150, 220, 240), (225, 250, 255)]
BAG = [(116, 124, 138), (184, 192, 204), (218, 224, 232), (244, 247, 251)]
CARD = [(96, 62, 34), (140, 96, 54), (176, 126, 74), (206, 160, 104)]
TAPE = (226, 206, 150)
WOOD = [(70, 46, 26), (110, 74, 40), (146, 104, 58), (182, 138, 82)]
METAL = [(56, 60, 70), (92, 98, 110), (138, 144, 156), (190, 196, 206), (228, 232, 238)]
ARMY = [(40, 52, 30), (66, 84, 46), (96, 118, 66), (130, 152, 92)]
YELLOW = [(150, 116, 20), (206, 166, 40), (236, 204, 72), (250, 232, 140)]
RUBBER = [(26, 28, 34), (46, 50, 58), (74, 80, 90)]
GLASS = [(80, 120, 140), (150, 200, 222), (205, 236, 248)]
SEED = [(70, 54, 40), (112, 88, 62), (150, 124, 90)]
RED = [(110, 24, 22), (170, 40, 34), (220, 70, 56), (246, 130, 110)]
LIQUID = [(30, 110, 70), (60, 170, 110), (130, 220, 160)]
FIRE = [(200, 70, 20), (250, 150, 40), (255, 220, 120)]


class Canvas:
    """Une image de pixels, avec quelques outils de dessin."""

    def __init__(self, width=16, height=16):
        self.w = width
        self.h = height
        self.px = [[T for _ in range(width)] for _ in range(height)]

    def set(self, x, y, color):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = color if len(color) == 4 else (color[0], color[1], color[2], 255)

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[y][x]
        return T

    def rect(self, x0, y0, x1, y1, color):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, color)

    def hline(self, x0, x1, y, color):
        for x in range(x0, x1 + 1):
            self.set(x, y, color)

    def vline(self, x, y0, y1, color):
        for y in range(y0, y1 + 1):
            self.set(x, y, color)

    def points(self, pts, color):
        for x, y in pts:
            self.set(x, y, color)

    def grid(self, rows, colors, ox=0, oy=0):
        """Dessine une grille de caracteres : chaque caractere renvoie a une couleur (« . » : rien)."""
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != "." and ch != " ":
                    self.set(ox + x, oy + y, colors[ch])

    def outline(self, color=OUT):
        """Entoure d'un trait sombre ce qui est dessine (les pixels transparents voisins)."""
        marks = []
        for y in range(self.h):
            for x in range(self.w):
                if self.px[y][x][3] != 0:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if self.get(x + dx, y + dy)[3] != 0 and self.get(x + dx, y + dy)[:3] != color:
                        marks.append((x, y))
                        break
        for x, y in marks:
            self.set(x, y, color)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        image = Image.new("RGBA", (self.w, self.h))
        image.putdata([pixel for row in self.px for pixel in row])
        image.save(path)


# -- Les objets ---------------------------------------------------------------------------------

def leaf_cannabis(c, ox=0, oy=0, ramp=GREEN):
    """Une feuille palmee a sept folioles dentelees, tige en bas."""
    rows = [
        "........3.......",
        ".......323......",
        "..3....323....3.",
        "..23...323...32.",
        "...23..323..32..",
        "...223.323.322..",
        "....223323322...",
        "3....2232322...3",
        "23....22322...32",
        ".2233.12221.332.",
        "..22221121222...",
        "....111111......",
        ".......1........",
        ".......0........",
        ".......0........",
        "................",
    ]
    c.grid(rows, {"0": GREEN[0], "1": ramp[1], "2": ramp[2], "3": ramp[3]}, ox, oy)


def item_cannabis_leaf():
    c = Canvas()
    leaf_cannabis(c)
    c.points([(7, 1), (8, 3), (6, 5)], GREEN[4])
    c.outline()
    return c


def leaf_ellipse(c, cx, cy, a, b, angle, ramp, rib):
    """Une feuille ovale : une ellipse inclinee, claire d'un cote, sombre de l'autre, une nervure."""
    import math
    cos_a, sin_a = math.cos(angle), math.sin(angle)
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            u = dx * cos_a + dy * sin_a
            v = -dx * sin_a + dy * cos_a
            if (u / a) ** 2 + (v / b) ** 2 <= 1.0:
                if abs(v) < 0.55 and abs(u) < a - 0.8:
                    c.set(x, y, rib)
                elif v < 0:
                    c.set(x, y, ramp[3] if v < -b * 0.55 else ramp[2])
                else:
                    c.set(x, y, ramp[1])


def item_coca_leaf():
    import math
    c = Canvas()
    leaf_ellipse(c, 8.5, 6.5, 6.6, 3.0, -math.pi / 4, COCA, COCA[0])
    leaf_ellipse(c, 5.2, 10.8, 4.2, 2.1, -math.pi / 5, COCA, COCA[0])
    c.points([(2, 14), (3, 13), (1, 15)], GREEN[0])
    c.outline()
    return c


def item_poppy_pod():
    c = Canvas()
    rows = [
        "................",
        "......0.0.0.....",
        ".....0101010....",
        "......00000.....",
        ".....3333322....",
        "....332222211...",
        "...33222221110..",
        "...32222211110..",
        "...32222211100..",
        "...22222111100..",
        "....221111100...",
        ".....1111000....",
        "......00000.....",
        ".......11.......",
        ".......11.......",
        ".......1........",
    ]
    c.grid(rows, {"0": POD[0], "1": POD[1], "2": POD[2], "3": POD[3]})
    c.points([(6, 1), (8, 1), (10, 1)], (60, 50, 40))
    c.outline()
    return c


def seeds(colors, dots):
    c = Canvas()
    for x, y in dots:
        c.rect(x, y, x + 1, y + 1, colors[1])
        c.set(x, y, colors[2])
        c.set(x + 1, y + 1, colors[0])
    c.outline()
    return c


def item_cannabis_seeds():
    return seeds(SEED, [(4, 5), (9, 4), (6, 9), (11, 9), (8, 12), (3, 11)])


def item_coca_seeds():
    c = Canvas()
    for x, y in [(4, 4), (9, 5), (6, 9), (11, 10), (4, 12)]:
        c.rect(x, y, x + 2, y + 1, RED[2])
        c.set(x, y, RED[3])
        c.set(x + 2, y + 1, RED[1])
    c.outline()
    return c


def item_poppy_seeds():
    c = Canvas()
    pile = [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "......2121......",
        "....12121212....",
        "...2121212121...",
        "..121212121212..",
        "..212121212121..",
        "...1111111111...",
        "................",
        "................",
        "................",
        "................",
    ]
    c.grid(pile, {"1": (54, 60, 80), "2": (92, 100, 124)})
    c.outline()
    return c


def item_precursor_can():
    c = Canvas()
    rows = [
        "................",
        ".....0000.......",
        ".....0330.......",
        "...000000000....",
        "..03333333320...",
        "..03222222210...",
        "..03244442210...",
        "..03245542210...",
        "..03244442210...",
        "..03222222210...",
        "..03666666610...",
        "..03222222210...",
        "..03222222210...",
        "..02111111110...",
        "...000000000....",
        "................",
    ]
    c.grid(rows, {"0": OUT, "1": BLUE[0], "2": BLUE[1], "3": BLUE[2], "4": YELLOW[2], "5": OUT,
                  "6": YELLOW[1]})
    return c


def mound(c, ramp, top=7, x0=2, x1=13, bottom=13):
    """Un petit tas : arrondi en haut, ombre a droite."""
    width = x1 - x0
    for y in range(top, bottom + 1):
        inset = max(0, (top + 3 - y)) * 2
        for x in range(x0 + inset, x1 - inset + 1):
            t = (x - x0) / max(1, width)
            c.set(x, y, ramp[3] if t < 0.3 and y < top + 3 else ramp[2] if t < 0.6 else ramp[1])
    c.hline(x0 + 1, x1 - 1, bottom, ramp[0])


def item_dried_herb():
    c = Canvas()
    buds = [(3, 8), (6, 6), (9, 7), (11, 9), (5, 10), (8, 10)]
    for x, y in buds:
        c.rect(x, y, x + 2, y + 2, GREEN[2])
        c.set(x, y, GREEN[3])
        c.set(x + 2, y + 2, GREEN[1])
        c.set(x + 1, y + 1, (150, 132, 70))
    c.points([(4, 9), (10, 8), (7, 11)], (190, 150, 80))
    c.outline()
    return c


def item_cocaine_powder():
    c = Canvas()
    mound(c, WHITE)
    c.points([(5, 8), (7, 7)], WHITE[3])
    c.outline()
    return c


def item_heroin():
    c = Canvas()
    mound(c, BEIGE, top=8)
    c.rect(9, 6, 11, 8, BEIGE[2])
    c.set(9, 6, BEIGE[3])
    c.set(11, 8, BEIGE[1])
    c.outline()
    return c


def pill(c, x, y, ramp, mark=False):
    c.rect(x, y, x + 3, y + 2, ramp[2])
    c.set(x, y, ramp[3])
    c.hline(x, x + 3, y + 2, ramp[1])
    if mark:
        c.vline(x + 2, y, y + 2, ramp[1])


def item_synthetic_pills():
    c = Canvas()
    pill(c, 2, 4, PINK, True)
    pill(c, 9, 6, BLUE)
    pill(c, 5, 10, PINK)
    pill(c, 10, 11, BLUE, True)
    c.outline()
    return c


def crystals(c, ox=0, oy=0):
    rows = [
        ".....3....",
        "....32..3.",
        "....321.32",
        "..3.321.21",
        ".32.32112.",
        ".3212211..",
        "..2111100.",
        "...0000...",
    ]
    c.grid(rows, {"0": CYAN[0], "1": CYAN[1], "2": CYAN[2], "3": CYAN[3]}, ox, oy)


def item_synthetic_crystals():
    c = Canvas()
    crystals(c, 3, 4)
    c.outline()
    return c


def bag_base(c, filled=False):
    """Un sachet zip transparent, vu de face."""
    c.rect(3, 3, 12, 13, BAG[1] if filled else BAG[2])
    c.hline(3, 12, 3, BAG[1])
    c.hline(3, 12, 4, (200, 60, 60))
    c.vline(12, 5, 13, BAG[1])
    c.hline(3, 12, 13, BAG[1])
    c.points([(4, 6), (4, 7), (5, 6)], BAG[3])


def bag_with(fill):
    c = Canvas()
    bag_base(c, fill is not None)
    if fill is not None:
        fill(c)
    c.points([(4, 6), (4, 7)], BAG[3])
    c.outline(BAG[0])
    return c


def item_empty_bag():
    return bag_with(None)


def item_herb_bag():
    def fill(c):
        for x, y in [(5, 8), (8, 8), (6, 10), (9, 10), (4, 11)]:
            c.rect(x, y, x + 1, y + 1, GREEN[2])
            c.set(x + 1, y + 1, GREEN[1])
        c.points([(7, 9), (10, 9)], (150, 132, 70))
    return bag_with(fill)


def item_powder_bag():
    def fill(c):
        c.rect(4, 9, 11, 12, WHITE[3])
        c.hline(5, 10, 8, WHITE[3])
        c.hline(4, 11, 12, WHITE[2])
        c.points([(4, 8), (11, 8)], WHITE[1])
    return bag_with(fill)


def item_heroin_bag():
    def fill(c):
        c.rect(4, 9, 11, 12, BEIGE[2])
        c.hline(5, 10, 8, BEIGE[3])
        c.hline(4, 11, 12, BEIGE[1])
    return bag_with(fill)


def item_pill_bag():
    def fill(c):
        for x, y, ramp in [(4, 8, PINK), (8, 9, BLUE), (5, 11, BLUE), (9, 11, PINK)]:
            c.rect(x, y, x + 2, y + 1, ramp[2])
            c.set(x, y, ramp[3])
    return bag_with(fill)


def item_crystal_bag():
    def fill(c):
        crystals(c, 3, 6)
    return bag_with(fill)


def parcel(label):
    """Un colis : une brique de carton scotchee, une etiquette de couleur."""
    c = Canvas()
    c.rect(2, 4, 13, 12, CARD[2])
    c.hline(2, 13, 4, CARD[3])
    c.vline(13, 5, 12, CARD[1])
    c.hline(2, 13, 12, CARD[0])
    c.rect(2, 7, 13, 8, TAPE)
    c.rect(7, 4, 8, 12, TAPE)
    c.rect(3, 9, 6, 11, (238, 232, 214))
    c.rect(3, 10, 6, 11, label[2])
    c.hline(3, 6, 10, label[3])
    c.points([(10, 5), (11, 6)], CARD[3])
    c.outline()
    return c


def item_herb_parcel():
    return parcel(GREEN[1:])


def item_powder_parcel():
    return parcel(WHITE)


def item_heroin_parcel():
    return parcel(BEIGE)


def item_synthetic_parcel():
    return parcel(PINK)


def item_precision_scale():
    c = Canvas()
    rows = [
        "................",
        "................",
        "................",
        "................",
        "...4444444444...",
        "...3333333333...",
        "......2222......",
        "..222222222222..",
        "..244444444442..",
        "..2455555554D2..",
        "..2456666654D2..",
        "..245555555412..",
        "..244444444442..",
        "..111111111111..",
        "................",
        "................",
    ]
    c.grid(rows, {"1": METAL[0], "2": METAL[1], "3": METAL[2], "4": METAL[3], "5": (30, 60, 40),
                  "6": (110, 230, 140), "D": RED[2]})
    c.outline()
    return c


def item_lab_glassware():
    c = Canvas()
    flask = [
        ".....22.....",
        ".....22.....",
        ".....22.....",
        "....2..2....",
        "...2....2...",
        "..2......2..",
        "..2LLLLLL2..",
        ".2LMMMMMML2.",
        ".2LMMMMMML2.",
        ".22222222222",
    ]
    c.grid(flask, {"2": GLASS[1], "L": LIQUID[2], "M": LIQUID[1]}, 1, 4)
    c.points([(3, 11), (4, 12)], GLASS[2])
    tube = [
        ".2.",
        "2.2",
        "2.2",
        "2L2",
        "2M2",
        "2M2",
        ".2.",
    ]
    c.grid(tube, {"2": GLASS[1], "L": PINK[2], "M": PINK[1]}, 12, 3)
    c.outline()
    return c


def item_gas_mask():
    c = Canvas()
    rows = [
        "................",
        "....11111111....",
        "...1222222221...",
        "..122222222221..",
        "..12GG2222GG21..",
        "..1GLLG22GLLG1..",
        "..1GLLG22GLLG1..",
        "..12GG2222GG21..",
        "..122222222221..",
        "...1222332221...",
        "....12344321....",
        ".....134431.....",
        ".....133331.....",
        "......1111......",
        "................",
        "................",
    ]
    c.grid(rows, {"1": RUBBER[0], "2": RUBBER[1], "G": METAL[2], "L": GLASS[1], "3": METAL[1],
                  "4": METAL[3]})
    c.points([(4, 5), (11, 5)], GLASS[2])
    return c


def item_hazmat_suit():
    c = Canvas()
    rows = [
        "................",
        "....22....22....",
        "...2332..2332...",
        "..233332233332..",
        "..233333333332..",
        "..2333R33R3332..",
        "..23332333.332..",
        "..23.3333333.2..",
        "..23.3333333.2..",
        "..22.3311133.2..",
        "..11.3333333.1..",
        "..11.3333333.1..",
        ".....3332333....",
        ".....2222222....",
        "................",
        "................",
    ]
    c.grid(rows, {"2": YELLOW[1], "3": YELLOW[2], "1": RUBBER[1], "R": RUBBER[2]})
    c.points([(4, 3), (11, 3)], YELLOW[3])
    c.outline()
    return c


def item_machined_parts():
    c = Canvas()
    gear = [
        "..2.22.2..",
        ".23333332.",
        "2334444332",
        ".34411443.",
        "234100143.",
        "234100143.",
        ".34411443.",
        "2334444332",
        ".23333332.",
        "..2.22.2..",
    ]
    c.grid(gear, {"0": T, "1": METAL[0], "2": METAL[1], "3": METAL[2], "4": METAL[3]}, 1, 1)
    bolt = [
        "33.",
        "443",
        "232",
        ".2.",
        ".2.",
        ".1.",
    ]
    c.grid(bolt, {"1": METAL[0], "2": METAL[1], "3": METAL[2], "4": METAL[3]}, 11, 8)
    c.outline()
    return c


def item_sealed_crate():
    c = Canvas()
    c.rect(1, 4, 14, 13, ARMY[2])
    c.hline(1, 14, 4, ARMY[3])
    c.vline(14, 5, 13, ARMY[1])
    c.hline(1, 14, 13, ARMY[0])
    c.rect(1, 8, 14, 9, ARMY[1])
    for x in (1, 13):
        for y in (4, 12):
            c.rect(x, y, x + 1, y + 1, METAL[2])
    c.rect(6, 6, 9, 7, (220, 214, 180))
    c.points([(7, 10), (8, 11), (9, 10), (8, 10)], (220, 214, 180))
    c.outline()
    return c


def item_incomplete_sealed_crate():
    """La caisse ouverte, des pieces qui depassent : l'objet de passage de la chaine de Create."""
    c = Canvas()
    c.rect(2, 4, 13, 6, ARMY[0])
    c.grid([
        "..33....",
        ".3443.2.",
        "34443.22",
    ], {"2": METAL[1], "3": METAL[2], "4": METAL[3]}, 3, 2)
    c.points([(10, 5), (11, 5)], (196, 160, 72))
    c.rect(1, 6, 14, 13, ARMY[2])
    c.hline(1, 14, 6, ARMY[3])
    c.vline(14, 7, 13, ARMY[1])
    c.hline(1, 14, 13, ARMY[0])
    c.rect(1, 9, 14, 10, ARMY[1])
    for x in (1, 13):
        c.rect(x, 12, x + 1, 13, METAL[2])
    c.outline()
    return c


ITEMS = {
    "cannabis_seeds": item_cannabis_seeds,
    "coca_seeds": item_coca_seeds,
    "poppy_seeds": item_poppy_seeds,
    "cannabis_leaf": item_cannabis_leaf,
    "coca_leaf": item_coca_leaf,
    "poppy_pod": item_poppy_pod,
    "precursor_can": item_precursor_can,
    "dried_herb": item_dried_herb,
    "cocaine_powder": item_cocaine_powder,
    "heroin": item_heroin,
    "synthetic_pills": item_synthetic_pills,
    "synthetic_crystals": item_synthetic_crystals,
    "empty_bag": item_empty_bag,
    "herb_bag": item_herb_bag,
    "powder_bag": item_powder_bag,
    "heroin_bag": item_heroin_bag,
    "pill_bag": item_pill_bag,
    "crystal_bag": item_crystal_bag,
    "herb_parcel": item_herb_parcel,
    "powder_parcel": item_powder_parcel,
    "heroin_parcel": item_heroin_parcel,
    "synthetic_parcel": item_synthetic_parcel,
    "precision_scale": item_precision_scale,
    "lab_glassware": item_lab_glassware,
    "gas_mask": item_gas_mask,
    "hazmat_suit": item_hazmat_suit,
    "machined_parts": item_machined_parts,
    "sealed_crate": item_sealed_crate,
    "incomplete_sealed_crate": item_incomplete_sealed_crate,
}

# -- Les plantes (une texture en croix par stade, quatre stades) --------------------------------


def stem(c, x, y0, y1, color):
    c.vline(x, y0, y1, color)


def crop_cannabis(stage):
    c = Canvas()
    if stage == 0:
        stem(c, 7, 12, 15, GREEN[1])
        c.points([(6, 12), (8, 12), (5, 11), (9, 11)], GREEN[2])
    elif stage == 1:
        stem(c, 7, 8, 15, GREEN[1])
        for dy, spread in ((9, 3), (12, 4)):
            for i in range(1, spread + 1):
                c.set(7 - i, dy - i // 2, GREEN[2])
                c.set(7 + i, dy - i // 2, GREEN[2])
        c.points([(7, 7), (6, 8), (8, 8)], GREEN[3])
    elif stage == 2:
        stem(c, 7, 4, 15, GREEN[1])
        leaf_small = [(0, 0), (-1, -1), (1, -1), (-2, 0), (2, 0), (-3, 1), (3, 1)]
        for cy in (6, 9, 12):
            for dx, dy in leaf_small:
                c.set(7 + dx, cy + dy, GREEN[2] if abs(dx) < 2 else GREEN[3])
        c.points([(7, 3), (6, 4), (8, 4)], GREEN[3])
    else:
        stem(c, 7, 2, 15, GREEN[1])
        for cy in (5, 8, 11):
            for i in range(1, 6):
                c.set(7 - i, cy + (i // 2), GREEN[2] if i < 4 else GREEN[3])
                c.set(7 + i, cy + (i // 2), GREEN[2] if i < 4 else GREEN[3])
        for x, y in [(7, 1), (6, 2), (8, 2), (7, 4), (6, 7), (8, 7)]:
            c.set(x, y, (186, 170, 90))
        c.points([(5, 3), (9, 3)], GREEN[3])
    return c


def crop_coca(stage):
    c = Canvas()
    base = 15
    heights = [12, 9, 6, 4]
    top = heights[stage]
    for x in (6, 9):
        stem(c, x, top + 2, base, WOOD[1])
    for y in range(top, base - 1, 2):
        width = 2 + (y - top) // 3
        for dx in range(-width, width + 1):
            if (dx + y) % 3 != 0:
                c.set(7 + dx, y, COCA[2] if dx < 0 else COCA[1])
                c.set(8 + dx, y + 1, COCA[3] if dx < -1 else COCA[2])
    if stage == 3:
        for x, y in [(4, 7), (10, 8), (7, 10), (11, 11), (3, 11)]:
            c.set(x, y, RED[2])
            c.set(x + 1, y, RED[1])
    return c


def crop_poppy(stage):
    c = Canvas()
    if stage == 0:
        stem(c, 7, 12, 15, POD[1])
        c.points([(6, 12), (8, 13), (5, 13)], POD[2])
    elif stage == 1:
        stem(c, 7, 8, 15, POD[1])
        for y in (10, 12, 14):
            c.points([(5, y), (6, y), (8, y), (9, y)], POD[2])
        c.set(7, 7, POD[2])
    elif stage == 2:
        stem(c, 7, 6, 15, POD[1])
        for y in (11, 13):
            c.points([(5, y), (6, y), (8, y), (9, y)], POD[2])
        petals = [(5, 3), (6, 2), (7, 2), (8, 2), (9, 3), (5, 4), (9, 4), (6, 5), (7, 5), (8, 5), (4, 4), (10, 4)]
        c.points(petals, (232, 214, 236))
        c.points([(6, 3), (7, 3), (8, 3), (6, 4), (7, 4), (8, 4)], (190, 120, 200))
        c.set(7, 4, (60, 50, 40))
    else:
        stem(c, 7, 6, 15, POD[1])
        for y in (11, 13):
            c.points([(5, y), (6, y), (8, y), (9, y)], POD[2])
        c.rect(6, 2, 8, 5, POD[2])
        c.points([(6, 2), (7, 2)], POD[3])
        c.points([(8, 5), (8, 4)], POD[1])
        c.points([(6, 1), (8, 1)], (60, 50, 40))
    return c


CROPS = {"cannabis_crop": crop_cannabis, "coca_crop": crop_coca, "poppy_crop": crop_poppy}

# -- Les blocs : facades, flancs et dessus des postes en relief ----------------------------------
#
# Les postes sont des modeles a elements, comme l'alambic ou le lutrin du jeu. Leurs grandes
# pieces (meuble, plateau, pieds, coffre) n'ont pas d'uv : le jeu les cale alors sur la position
# de la face dans le bloc. Chaque poste se dessine donc comme un plan : sa facade (vue du nord),
# son flanc et son dessus ; chaque piece y lit le morceau qui se trouve devant elle. Les petits
# objets poses dessus (verrerie, bidon, balance, sachets, colis, etau, caisse) prennent leur
# matiere dans la planche des pieces, plus bas.

SAFE = [(26, 34, 32), (40, 54, 50), (60, 78, 72), (88, 110, 100), (128, 150, 138)]
SHOP = [(34, 48, 44), (50, 70, 62), (70, 96, 84), (98, 128, 112)]
EPOXY = [(24, 26, 32), (36, 38, 46), (50, 54, 62), (72, 76, 86)]
OAK = [(92, 62, 34), (130, 92, 52), (164, 122, 74), (196, 156, 102)]
PINE = [(120, 88, 50), (166, 126, 76), (198, 160, 104), (224, 194, 140)]
BRASS = (196, 160, 72)


def hazard(c, x0, x1, y0, y1):
    """Un bandeau de danger : des rayures jaunes et noires en biais."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            c.set(x, y, YELLOW[2] if ((x + y) // 2) % 2 == 0 else OUT)


def lab_front():
    c = Canvas()
    # la hotte (y 12 a 16) : un cadre d'acier et sa grille
    c.rect(0, 0, 15, 3, METAL[2])
    c.hline(0, 15, 0, METAL[3])
    for x in range(2, 14):
        c.set(x, 1, OUT if x % 2 == 0 else METAL[1])
        c.set(x, 2, OUT if x % 2 == 0 else METAL[0])
    c.hline(0, 15, 3, METAL[1])
    c.points([(14, 1), (14, 2)], YELLOW[2])
    # le chant du plan de travail (y 10 a 12)
    c.hline(0, 15, 4, EPOXY[3])
    c.hline(0, 15, 5, EPOXY[1])
    # le meuble (y 0 a 10) : un bandeau de danger, deux portes, une plinthe
    hazard(c, 0, 15, 6, 7)
    c.rect(0, 8, 15, 13, METAL[1])
    for x0 in (1, 9):
        c.rect(x0, 9, x0 + 5, 13, METAL[2])
        c.hline(x0, x0 + 5, 9, METAL[3])
        c.vline(x0, 9, 13, METAL[3])
        c.vline(x0 + 5, 10, 13, METAL[1])
    c.vline(5, 10, 12, METAL[4])
    c.vline(10, 10, 12, METAL[4])
    c.grid(["YYY", "YOY", "YYY"], {"Y": YELLOW[2], "O": OUT}, 12, 10)
    c.hline(0, 15, 14, METAL[0])
    c.hline(0, 15, 15, RUBBER[1])
    return c


def lab_side():
    c = Canvas()
    c.rect(0, 0, 15, 3, METAL[2])
    c.hline(0, 15, 0, METAL[3])
    c.hline(0, 15, 3, METAL[1])
    c.hline(0, 15, 4, EPOXY[3])
    c.hline(0, 15, 5, EPOXY[1])
    c.rect(0, 6, 15, 13, METAL[2])
    c.hline(0, 15, 6, METAL[3])
    c.vline(0, 6, 13, METAL[1])
    c.vline(15, 6, 13, METAL[1])
    for y in (8, 10):
        c.hline(4, 11, y, METAL[0])
        c.hline(4, 11, y + 1, METAL[3])
    for x, y in ((2, 7), (13, 7), (2, 12), (13, 12)):
        c.set(x, y, METAL[4])
    c.hline(0, 15, 14, METAL[0])
    c.hline(0, 15, 15, RUBBER[1])
    return c


def lab_top():
    c = Canvas()
    c.rect(0, 0, 15, 13, EPOXY[1])
    c.hline(0, 15, 0, EPOXY[3])
    specks = ((1, 2), (4, 9), (7, 2), (12, 6), (14, 11), (9, 13), (2, 6), (6, 12), (13, 1), (10, 8), (5, 4))
    for i, (x, y) in enumerate(specks):
        c.set(x, y, EPOXY[2] if i % 2 else EPOXY[0])
    c.points([(7, 9), (8, 9), (8, 10), (7, 10), (9, 10), (8, 11)], LIQUID[0])
    c.points([(2, 11), (3, 11), (3, 12)], EPOXY[0])
    c.points([(14, 7), (14, 8)], PINK[0])
    # le dessus de la hotte (z 14 a 16)
    c.rect(0, 14, 15, 15, METAL[2])
    c.hline(0, 15, 14, METAL[3])
    c.points([(3, 15), (12, 15)], METAL[0])
    return c


def table_top():
    c = Canvas()
    c.rect(0, 0, 15, 15, OAK[2])
    for y in (0, 4, 8, 12):
        c.hline(0, 15, y, OAK[1])
    for y in (1, 5, 9, 13):
        for x in range(0, 16, 5):
            c.set(x + y % 3, y, OAK[3])
    for x, y in ((3, 2), (4, 2), (10, 6), (11, 6), (6, 10), (7, 10), (13, 14), (14, 14), (1, 13)):
        c.set(x, y, OAK[1])
    # un peu de poudre pres de la balance, des entailles de lame
    c.points([(7, 6), (8, 7), (7, 8), (8, 5)], WHITE[2])
    c.points([(11, 10), (12, 11), (13, 12), (9, 14)], OAK[0])
    return c


def table_wood():
    c = Canvas()
    c.rect(0, 0, 15, 15, OAK[2])
    # le chant du plateau (y 12 a 14) et les ceintures (y 10 a 12) : du fil en long
    c.hline(0, 15, 2, OAK[3])
    c.hline(0, 15, 3, OAK[2])
    c.points([(3, 3), (9, 3), (14, 3)], OAK[1])
    c.hline(0, 15, 4, OAK[2])
    c.hline(0, 15, 5, OAK[1])
    # le bois debout des pieds (y 0 a 10)
    for y in range(6, 16):
        for x in range(16):
            c.set(x, y, OAK[1] if x % 3 == 2 else OAK[2])
    c.points([(1, 9), (14, 12), (7, 7)], OAK[0])
    c.hline(0, 15, 15, OAK[0])
    return c


def bench_front():
    c = Canvas()
    c.rect(0, 0, 15, 15, SHOP[1])
    # le chant du plateau d'acier (y 12 a 14)
    c.hline(0, 15, 2, METAL[3])
    c.hline(0, 15, 3, METAL[1])
    # le caisson a tiroirs (x 0 a 7 : colonnes 9 a 15)
    c.rect(9, 4, 15, 15, SHOP[2])
    for y0, y1 in ((5, 7), (8, 10), (11, 13)):
        c.rect(10, y0, 14, y1, SHOP[2])
        c.hline(10, 14, y0, SHOP[3])
        c.hline(10, 14, y1, SHOP[0])
        c.hline(11, 13, y0 + 1, METAL[4])
        c.set(12, y0 + 1, METAL[3])
    c.vline(9, 4, 15, SHOP[0])
    c.vline(15, 4, 15, SHOP[0])
    c.hline(9, 15, 14, SHOP[0])
    c.hline(9, 15, 15, RUBBER[1])
    # les pieds (x 13 a 15 : colonnes 1 et 2), le bord de l'etagere (y 4 a 5 : rangee 11)
    c.rect(1, 4, 2, 15, SHOP[2])
    c.vline(1, 4, 15, SHOP[3])
    c.hline(1, 2, 15, RUBBER[1])
    c.hline(1, 8, 11, METAL[2])
    return c


def bench_side():
    c = Canvas()
    c.rect(0, 0, 15, 15, SHOP[1])
    c.hline(0, 15, 2, METAL[3])
    c.hline(0, 15, 3, METAL[1])
    c.rect(1, 4, 14, 15, SHOP[2])
    c.hline(1, 14, 4, SHOP[3])
    c.hline(1, 14, 14, SHOP[0])
    c.hline(1, 14, 15, RUBBER[1])
    for x, y in ((2, 5), (13, 5), (2, 13), (13, 13)):
        c.set(x, y, METAL[3])
    c.rect(5, 7, 10, 9, (210, 200, 160))
    c.hline(6, 9, 8, OUT)
    c.set(10, 9, (160, 150, 120))
    return c


def bench_top():
    c = Canvas()
    c.rect(0, 0, 15, 15, METAL[2])
    c.hline(0, 15, 0, METAL[3])
    c.vline(8, 1, 15, METAL[1])
    for x, y in ((3, 9), (4, 9), (5, 10), (10, 12), (11, 12), (12, 13), (5, 4), (6, 4)):
        c.set(x, y, METAL[1])
    c.points([(12, 6), (13, 6), (12, 7), (13, 7), (14, 7), (13, 8)], METAL[0])
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, METAL[4])
    return c


def safe_front():
    c = Canvas()
    c.rect(0, 0, 15, 15, SAFE[1])
    # le corps (x 1 a 15, y 1 a 15)
    c.rect(1, 1, 14, 14, SAFE[2])
    c.hline(1, 14, 1, SAFE[3])
    c.vline(1, 1, 14, SAFE[3])
    c.hline(1, 14, 14, SAFE[0])
    c.vline(14, 1, 14, SAFE[0])
    # la porte (x 2 a 14, y 2 a 14), son panneau en creux et la plaque du fabricant
    c.rect(2, 2, 13, 13, SAFE[2])
    c.hline(2, 13, 2, SAFE[4])
    c.vline(2, 2, 13, SAFE[3])
    c.hline(2, 13, 13, SAFE[0])
    c.vline(13, 2, 13, SAFE[1])
    c.hline(4, 11, 5, SAFE[1])
    c.vline(4, 5, 11, SAFE[1])
    c.hline(4, 11, 11, SAFE[3])
    c.vline(11, 5, 11, SAFE[3])
    c.hline(6, 9, 3, BRASS)
    return c


def safe_side():
    c = Canvas()
    c.rect(0, 0, 15, 15, SAFE[2])
    c.hline(1, 14, 1, SAFE[3])
    c.vline(1, 1, 14, SAFE[3])
    c.vline(14, 1, 14, SAFE[1])
    c.hline(1, 14, 14, SAFE[0])
    for y in (3, 8, 12):
        for x in (3, 12):
            c.set(x, y, SAFE[4])
    return c


def safe_top():
    c = Canvas()
    c.rect(0, 0, 15, 15, SAFE[2])
    c.rect(2, 2, 13, 13, SAFE[3])
    c.hline(2, 13, 2, SAFE[4])
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, SAFE[4])
    c.points([(5, 9), (9, 6), (11, 11)], SAFE[2])
    return c


def rack_wood():
    """Du pin clair, fil en long : les montants et les traverses du sechoir."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.set(x, y, PINE[1] if (x + (y // 5)) % 4 == 3 else PINE[2])
    c.points([(2, 4), (9, 11), (13, 2)], PINE[0])
    return c


def rack_leaves(dry):
    """Trois bottes de feuilles pendues tete en bas, a lire sur 14 x 10 pixels (colonnes 1 a 15)."""
    c = Canvas()
    ramp = [(96, 78, 40), (140, 116, 60), (176, 152, 84), (206, 186, 112)] if dry else GREEN[1:]
    bunch = [
        "..s..",
        "..s..",
        "..t..",
        ".lmr.",
        "llmr.",
        ".lmrr",
        "llmr.",
        ".lmr.",
        ".dmd.",
        "..d..",
    ]
    colors = {"s": (214, 200, 160), "t": WOOD[1], "l": ramp[2], "m": ramp[3], "r": ramp[1], "d": ramp[0]}
    for cx in (3, 8, 13):
        c.grid(bunch, colors, cx - 2, 0)
    return c


BLOCK_TEXTURES = {
    "clandestine_lab_front": lab_front,
    "clandestine_lab_side": lab_side,
    "clandestine_lab_top": lab_top,
    "packaging_table_top": table_top,
    "packaging_table_wood": table_wood,
    "arms_workbench_front": bench_front,
    "arms_workbench_side": bench_side,
    "arms_workbench_top": bench_top,
    "stash_chest_front": safe_front,
    "stash_chest_side": safe_side,
    "stash_chest_top": safe_top,
    "drying_rack_wood": rack_wood,
    "drying_rack_leaves": lambda: rack_leaves(False),
    "drying_rack_leaves_dry": lambda: rack_leaves(True),
}

# Les textures des anciens postes en cube, retirees quand ils ont pris du relief.
RETIRED_TEXTURES = ("packaging_table_side", "packaging_table_front")

# -- La planche des pieces : 32 x 32, des cases de 4 x 4 ------------------------------------------
#
# Les cases unies servent aux petits objets (on lit leur centre) ; les cases dessinees
# (ecran, cadran, plaque chauffante...) se lisent en entier. Une unite d'uv vaut deux pixels.

PARTS = "underground_parts"

PARTS_CELLS = [
    # verre, liquides, sachets
    "glass", "glass_dark", "liquid", "liquid_pink", "liquid_blue", "powder", "bag", "bag_shade",
    # metaux
    "steel", "steel_mid", "steel_dark", "iron_black", "rubber", "brass", "copper", "chrome",
    # couleurs
    "can", "can_dark", "label", "red", "red_dark", "army", "army_dark", "safe",
    # bois, carton, ficelle
    "card", "card_dark", "tape", "wood", "wood_dark", "string", "paper", "pine",
    # cases dessinees
    "lcd", "dial", "coil", "hazard", "vent", "gear", "stencil", "band",
]

PARTS_FLAT = {
    "glass": GLASS[2], "glass_dark": GLASS[1], "liquid": LIQUID[1], "liquid_pink": PINK[1],
    "liquid_blue": CYAN[1], "powder": WHITE[2], "bag": BAG[2], "bag_shade": BAG[1],
    "steel": METAL[3], "steel_mid": METAL[2], "steel_dark": METAL[1], "iron_black": METAL[0],
    "rubber": RUBBER[1], "brass": BRASS, "copper": (180, 104, 62), "chrome": METAL[4],
    "can": YELLOW[2], "can_dark": YELLOW[0], "label": OUT, "red": RED[2], "red_dark": RED[1],
    "army": ARMY[2], "army_dark": ARMY[1], "safe": SAFE[1],
    "card": CARD[2], "card_dark": CARD[1], "tape": TAPE, "wood": WOOD[2], "wood_dark": WOOD[1],
    "string": (214, 200, 160), "paper": WHITE[3], "pine": PINE[2],
}

PARTS_DRAWN = {
    "lcd": (["dddd", "dgdg", "dggd", "dddd"], {"d": (30, 60, 40), "g": (110, 230, 140)}),
    "dial": (["kcck", "cbdc", "cddc", "kcck"], {"k": SAFE[0], "c": METAL[4], "d": OUT, "b": BRASS}),
    "coil": (["krrk", "rkkr", "rkkr", "krrk"], {"k": RUBBER[1], "r": RED[2]}),
    "hazard": (["yyoo", "yoox", "ooyy", "oyyo"], {"y": YELLOW[2], "o": OUT, "x": YELLOW[2]}),
    "vent": (["ssss", "dddd", "ssss", "dddd"], {"s": METAL[2], "d": METAL[0]}),
    "gear": (["bbbb", "bddb", "bddb", "bbbb"], {"b": BRASS, "d": (120, 90, 40)}),
    "stencil": (["aaaa", "alla", "aala", "aaaa"], {"a": ARMY[2], "l": (220, 214, 180)}),
    "band": (["kkkk", "kssk", "kkkk", "kkkk"], {"k": METAL[1], "s": METAL[3]}),
}


def parts_sheet():
    c = Canvas(32, 32)
    for index, name in enumerate(PARTS_CELLS):
        x0, y0 = (index % 8) * 4, (index // 8) * 4
        if name in PARTS_FLAT:
            c.rect(x0, y0, x0 + 3, y0 + 3, PARTS_FLAT[name])
        else:
            rows, colors = PARTS_DRAWN[name]
            c.grid(rows, colors, x0, y0)
    return c


def cell_uv(name):
    """Les uv d'une case : le centre d'une case unie, toute la case dessinee."""
    index = PARTS_CELLS.index(name)
    u, v = (index % 8) * 2, (index // 8) * 2
    if name in PARTS_FLAT:
        return [u + 0.5, v + 0.5, u + 1.5, v + 1.5]
    return [u, v, u + 2, v + 2]


# -- L'equipement porte (masque sur la tete, combinaison sur le corps et les bras) ----------------


def equipment_protection():
    c = Canvas(64, 32)
    # la tete : 8 x 8 x 8, a (0, 0) ; faces dessus (8,0) dessous (16,0) droite (0,8) devant (8,8)
    # gauche (16,8) derriere (24,8) ; seul ce qui est dessine se voit sur le joueur
    for fx, fy in ((8, 0), (16, 0), (0, 8), (8, 8), (16, 8), (24, 8)):
        c.rect(fx, fy, fx + 7, fy + 7, RUBBER[1])
        c.hline(fx, fx + 7, fy, RUBBER[2])
    c.rect(9, 10, 10, 11, GLASS[1])
    c.rect(13, 10, 14, 11, GLASS[1])
    c.set(9, 10, GLASS[2])
    c.set(13, 10, GLASS[2])
    c.rect(11, 12, 12, 14, METAL[2])
    c.set(11, 13, METAL[0])
    c.hline(8, 15, 15, RUBBER[0])
    # le corps : (16, 16) a (40, 32) ; les bras : (40, 16) a (56, 32)
    c.rect(16, 16, 39, 31, YELLOW[2])
    c.rect(40, 16, 55, 31, YELLOW[2])
    for x in range(16, 40):
        c.set(x, 31, YELLOW[1])
    for x in range(40, 56):
        c.set(x, 31, RUBBER[1])
        c.set(x, 30, RUBBER[1])
    c.rect(20, 26, 27, 27, YELLOW[1])
    c.rect(23, 21, 24, 23, RUBBER[2])
    c.vline(24, 20, 31, YELLOW[1])
    return c


# -- Les fichiers JSON --------------------------------------------------------------------------

def write_json(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(value, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def item_model(name, texture=None):
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": NS + ":item/" + (texture or name)},
    })
    write_json(os.path.join(ASSETS, "items", name + ".json"), {
        "model": {"type": "minecraft:model", "model": NS + ":item/" + name},
    })


def block_item(name, model):
    write_json(os.path.join(ASSETS, "items", name + ".json"), {
        "model": {"type": "minecraft:model", "model": NS + ":block/" + model},
    })


SIDES = ("north", "east", "south", "west", "up", "down")


def touches_edge(side, frm, to):
    """La face est-elle sur le bord du bloc ? Le jeu la tait alors contre un bloc plein."""
    return {"down": frm[1] == 0, "up": to[1] == 16, "north": frm[2] == 0, "south": to[2] == 16,
            "west": frm[0] == 0, "east": to[0] == 16}[side]


def piece(frm, to, textures, skip=()):
    """Une grande piece : chaque face lit le plan du poste la ou elle se trouve (pas d'uv).

    textures : la variable de chaque face, "all" pour celles qui ne sont pas nommees."""
    faces = {}
    for side in SIDES:
        if side in skip:
            continue
        face = {"texture": textures.get(side, textures.get("all"))}
        if touches_edge(side, frm, to):
            face["cullface"] = side
        faces[side] = face
    return {"from": list(frm), "to": list(to), "faces": faces}


def part(frm, to, material, skip=(), faces=None):
    """Un petit objet, d'une matiere de la planche des pieces (une autre sur certaines faces)."""
    out = {}
    for side in SIDES:
        if side in skip:
            continue
        face = {"uv": cell_uv((faces or {}).get(side, material)), "texture": "#parts"}
        if touches_edge(side, frm, to):
            face["cullface"] = side
        out[side] = face
    return {"from": list(frm), "to": list(to), "faces": out}


def lab_elements():
    plan = {"north": "#front", "up": "#top", "all": "#side"}
    return [
        # le meuble a deux portes, sous un plan de travail noir qui deborde d'un pixel devant
        piece([0, 0, 1], [16, 10, 16], plan, skip=("up",)),
        piece([0, 10, 0], [16, 12, 16], plan),
        # la hotte d'aspiration, au fond
        piece([0, 12, 14], [16, 16, 16], plan, skip=("down",)),
        # la plaque chauffante et son ballon : du liquide vert en bas, du verre en haut
        part([2, 12, 3], [7, 13, 8], "steel_dark", skip=("down",), faces={"up": "coil"}),
        part([3, 13, 4], [6, 14.25, 7], "liquid", skip=("up", "down")),
        part([3, 14.25, 4], [6, 15, 7], "glass", skip=("down",)),
        part([4, 15, 5], [5, 15.9, 6], "glass", skip=("down",)),
        # le pont de distillation et l'erlenmeyer rose
        part([5, 15.4, 5.25], [9, 15.9, 5.75], "glass_dark"),
        part([8, 12, 4], [11, 13.25, 7], "liquid_pink", skip=("down",)),
        part([8.5, 13.25, 4.5], [10.5, 14, 6.5], "glass", skip=("down",)),
        part([9, 14, 5], [10, 15.9, 6], "glass", skip=("down",)),
        # deux tubes a essai sur leur portoir
        part([11.5, 12, 2], [15, 12.75, 4.5], "wood", skip=("down",)),
        part([12, 12.75, 2.75], [13, 13.75, 3.75], "liquid_blue", skip=("up", "down")),
        part([12, 13.75, 2.75], [13, 15, 3.75], "glass", skip=("down",)),
        part([13.75, 12.75, 2.75], [14.75, 13.5, 3.75], "liquid", skip=("up", "down")),
        part([13.75, 13.5, 2.75], [14.75, 15, 3.75], "glass", skip=("down",)),
        # un bidon de precurseurs, son etiquette et son bouchon
        part([11.5, 12, 9], [14.5, 15.5, 12], "can", skip=("down",)),
        part([11.4, 13, 8.9], [14.6, 14.25, 12.1], "label", skip=("up", "down")),
        part([12.5, 15.5, 10], [13.5, 16, 11], "iron_black", skip=("down",)),
    ]


def table_elements():
    wood = {"all": "#wood"}
    top = {"up": "#top", "all": "#wood"}
    legs = [piece([x, 0, z], [x + 2, 12, z + 2], wood, skip=("up",)) for x in (1, 13) for z in (1, 13)]
    return legs + [
        # les ceintures sous le plateau, l'etagere du bas, le plateau
        piece([3, 10, 1.5], [13, 12, 2.5], wood, skip=("up",)),
        piece([3, 10, 13.5], [13, 12, 14.5], wood, skip=("up",)),
        piece([1.5, 10, 3], [2.5, 12, 13], wood, skip=("up",)),
        piece([13.5, 10, 3], [14.5, 12, 13], wood, skip=("up",)),
        piece([1.5, 3, 1.5], [14.5, 4, 14.5], top),
        piece([0, 12, 0], [16, 14, 16], top),
        # la balance de precision : son ecran vert, son plateau, un peu de poudre
        part([2, 14, 2], [7, 15.25, 7], "steel_dark", skip=("down",)),
        part([3, 14.3, 1.9], [6, 15, 2], "lcd", skip=("south", "down")),
        part([2.5, 15.25, 2.5], [6.5, 15.5, 6.5], "steel", skip=("down",)),
        part([3.5, 15.5, 3.5], [5.5, 15.8, 5.5], "powder", skip=("down",)),
        # trois sachets fermes d'un trait rouge
        part([9, 14, 2], [12, 15, 5], "bag", skip=("down",)),
        part([9, 15, 2.2], [12, 15.1, 2.8], "red", skip=("down",)),
        part([12.5, 14, 2.5], [15, 15, 5], "bag", skip=("down",)),
        part([12.5, 15, 2.7], [15, 15.1, 3.2], "red", skip=("down",)),
        part([10, 15, 3], [13, 15.9, 5.5], "bag", skip=("down",)),
        part([10, 15.9, 3.2], [13, 16, 3.7], "red", skip=("down",)),
        # un colis ferme au ruban, un rouleau de ruban
        part([3, 14, 9], [8, 15.9, 14], "card", skip=("down",)),
        part([5, 14, 8.9], [6, 16, 14.1], "tape", skip=("down",)),
        part([2.9, 14, 11], [8.1, 16, 12], "tape", skip=("down",)),
        part([10.5, 14, 9.5], [13.5, 15, 12.5], "tape", skip=("down",)),
        part([11.5, 15, 10.5], [12.5, 15.05, 11.5], "card_dark", skip=("down",)),
        # sur l'etagere : des cartons a plat et une pile de sachets vides
        part([3, 4, 3], [9, 5.5, 12], "card", skip=("down",)),
        part([10, 4, 4], [14, 5, 10], "bag_shade", skip=("down",)),
    ]


def bench_elements():
    plan = {"north": "#front", "up": "#top", "all": "#side"}
    return [
        # le caisson a tiroirs, les pieds, l'etagere du bas, sa traverse, le plateau d'acier
        piece([0, 0, 1], [7, 12, 15], plan, skip=("up",)),
        piece([13, 0, 1], [15, 12, 3], plan, skip=("up",)),
        piece([13, 0, 13], [15, 12, 15], plan, skip=("up",)),
        piece([7, 4, 2], [15, 5, 14], plan),
        piece([7, 2, 13.5], [13, 3.5, 14.5], plan),
        piece([0, 12, 0], [16, 14, 16], plan),
        # sur l'etagere : une caisse a outils rouge et sa poignee
        part([8.5, 5, 4], [12.5, 7.5, 8], "red", skip=("down",), faces={"up": "red_dark"}),
        part([9.5, 7.5, 5.75], [11.5, 8.25, 6.25], "iron_black", skip=("down",)),
        # l'etau, sa vis et sa barre ; une piece de laiton serree entre les mors
        part([10, 14, 2], [14, 14.75, 5.5], "steel_dark", skip=("down",)),
        part([10, 14.75, 4], [14, 16, 5.5], "steel", skip=("down",)),
        part([10, 14.75, 1.5], [14, 16, 2.5], "steel", skip=("down",)),
        part([11, 14.75, 2.5], [13, 15.75, 4], "brass", skip=("down",)),
        part([11.5, 15, 0.5], [12.5, 15.75, 1.5], "iron_black"),
        part([10, 15.1, 0], [14, 15.6, 0.5], "chrome"),
        # une caisse scellee, cerclee d'acier
        part([1.5, 14, 8.5], [7.5, 15.9, 14], "army", skip=("down",), faces={"up": "stencil"}),
        part([1.4, 14, 10.75], [7.6, 16, 11.75], "band", skip=("down",)),
        # un bac de pieces : un engrenage, des vis
        part([8.5, 14, 9], [12.5, 14.5, 13], "steel_dark", skip=("down",)),
        part([9, 14.5, 9.5], [10.5, 15, 11], "gear", skip=("down",)),
        part([11, 14.5, 11], [12, 15, 12.5], "steel", skip=("down",)),
        # un marteau pose sur l'etabli
        part([2, 14, 3], [6.5, 14.5, 3.75], "wood", skip=("down",)),
        part([6.5, 14, 2.25], [7.75, 15, 4.5], "iron_black", skip=("down",)),
    ]


def safe_elements():
    body = {"north": "#front", "up": "#top", "all": "#side"}
    feet = [part([x, 0, z], [x + 2, 1, z + 2], "rubber", skip=("up",)) for x in (1.5, 12.5) for z in (1.5, 12.5)]
    return feet + [
        piece([1, 1, 1], [15, 15, 15], body),
        # la porte en saillie et ses gonds
        piece([2, 2, 0.5], [14, 14, 1], {"north": "#front", "all": "#side"}, skip=("south",)),
        part([13.75, 3, 0.3], [14.5, 5, 1], "chrome", skip=("south",)),
        part([13.75, 11, 0.3], [14.5, 13, 1], "chrome", skip=("south",)),
        # le cadran et son bouton
        part([7, 8, 0.1], [10, 11, 0.5], "safe", skip=("south",), faces={"north": "dial"}),
        part([8, 9, -0.2], [9, 10, 0.1], "brass", skip=("south",)),
        # la poignee en croix
        part([3, 5.25, 0.1], [7, 5.75, 0.5], "chrome", skip=("south",)),
        part([4.75, 3.5, 0.1], [5.25, 7.5, 0.5], "chrome", skip=("south",)),
        part([4.5, 5, -0.3], [5.5, 6, 0.5], "steel", skip=("south",)),
        # la fente de depot, au-dessus du cadran
        part([6, 12, 0.3], [10, 12.5, 0.5], "iron_black", skip=("south",)),
    ]


def rack_elements(leaves):
    wood = {"all": "#wood"}
    posts = [piece([x, 0, z], [x + 1.5, 15, z + 1.5], wood) for x in (1, 13.5) for z in (1, 13.5)]
    rails = [
        piece([2.5, 13, 1.25], [13.5, 14.5, 2.25], wood),
        piece([2.5, 13, 13.75], [13.5, 14.5, 14.75], wood),
        piece([1.25, 13, 2.5], [2.25, 14.5, 13.5], wood),
        piece([13.75, 13, 2.5], [14.75, 14.5, 13.5], wood),
        piece([1.25, 2, 2.5], [2.25, 3, 13.5], wood),
        piece([13.75, 2, 2.5], [14.75, 3, 13.5], wood),
    ]
    strings = [part([2.25, 13.6, z - 0.15], [13.75, 13.9, z + 0.15], "string") for z, _ in RACK_ROWS]
    elements = posts + rails + strings
    if leaves:
        # les rangees sont decalees d'un pixel l'une de l'autre : de face, on voit entre les bottes
        for z, shift in RACK_ROWS:
            elements.append({"from": [2 + shift, 3.75, z], "to": [14 + shift, 13.6, z], "shade": False, "faces": {
                "north": {"uv": [1, 0, 15, 9.85], "texture": "#leaves"},
                "south": {"uv": [1, 0, 15, 9.85], "texture": "#leaves"}}})
    return elements


RACK_ROWS = ((4.5, -1), (8, 0), (11.5, 1))


def tex(name):
    return NS + ":block/" + name


def station_model(name, textures, elements, particle, occlusion=True):
    body = {"parent": "minecraft:block/block", "textures": dict(textures, particle=tex(particle)),
            "elements": elements}
    if not occlusion:
        body["ambientocclusion"] = False
    write_json(os.path.join(ASSETS, "models", "block", name + ".json"), body)


def facing_states(name):
    """Un modele tourne vers le nord, et le jeu le tourne vers les trois autres faces."""
    variants = {}
    for facing, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        variant = {"model": NS + ":block/" + name}
        if rotation:
            variant["y"] = rotation
        variants["facing=" + facing] = variant
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})
    block_item(name, name)


def station_models():
    parts = tex(PARTS)
    station_model("clandestine_lab", {"front": tex("clandestine_lab_front"), "side": tex("clandestine_lab_side"),
                                      "top": tex("clandestine_lab_top"), "parts": parts},
                  lab_elements(), "clandestine_lab_side")
    station_model("packaging_table", {"top": tex("packaging_table_top"), "wood": tex("packaging_table_wood"),
                                      "parts": parts}, table_elements(), "packaging_table_wood")
    station_model("arms_workbench", {"front": tex("arms_workbench_front"), "side": tex("arms_workbench_side"),
                                     "top": tex("arms_workbench_top"), "parts": parts},
                  bench_elements(), "arms_workbench_side")
    station_model("stash_chest", {"front": tex("stash_chest_front"), "side": tex("stash_chest_side"),
                                  "top": tex("stash_chest_top"), "parts": parts}, safe_elements(), "stash_chest_side")
    for name in ("clandestine_lab", "packaging_table", "arms_workbench", "stash_chest"):
        facing_states(name)
    for state, leaves in (("empty", None), ("fresh", "drying_rack_leaves"), ("dry", "drying_rack_leaves_dry")):
        textures = {"wood": tex("drying_rack_wood"), "parts": parts}
        if leaves:
            textures["leaves"] = tex(leaves)
        station_model("drying_rack_" + state, textures, rack_elements(leaves), "drying_rack_wood", occlusion=False)
    write_json(os.path.join(ASSETS, "blockstates", "drying_rack.json"), {"variants": {
        "stage=0": {"model": NS + ":block/drying_rack_empty"},
        "stage=1": {"model": NS + ":block/drying_rack_fresh"},
        "stage=2": {"model": NS + ":block/drying_rack_dry"}}})
    block_item("drying_rack", "drying_rack_fresh")


def crop_models(name):
    for stage in range(4):
        write_json(os.path.join(ASSETS, "models", "block", name + "_stage_" + str(stage) + ".json"), {
            "parent": NS + ":block/crop_cross",
            "textures": {"cross": NS + ":block/" + name + "_stage_" + str(stage)},
        })
    ages = {0: 0, 1: 0, 2: 1, 3: 1, 4: 2, 5: 2, 6: 2, 7: 3}
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {
        "age=" + str(age): {"model": NS + ":block/" + name + "_stage_" + str(stage)}
        for age, stage in ages.items()}})


# -- Les donnees --------------------------------------------------------------------------------

def self_drop(name):
    write_json(os.path.join(DATA, NS, "loot_table", "blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": NS + ":" + name}]}],
    })


def crop_drop(name, harvest, low, high, seed):
    mature = {"condition": "minecraft:block_state_property", "block": NS + ":" + name, "properties": {"age": "7"}}
    write_json(os.path.join(DATA, NS, "loot_table", "blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [
            {"rolls": 1, "conditions": [mature], "entries": [{"type": "minecraft:item", "name": NS + ":" + harvest,
                                                              "functions": [{"function": "minecraft:set_count",
                                                                             "count": {"type": "minecraft:uniform",
                                                                                       "min": low, "max": high}},
                                                                            {"function": "minecraft:explosion_decay"}]}]},
            {"rolls": 1, "conditions": [mature], "entries": [{"type": "minecraft:item", "name": NS + ":" + seed,
                                                              "functions": [{"function": "minecraft:set_count",
                                                                             "count": {"type": "minecraft:uniform",
                                                                                       "min": 1, "max": 2}}]}]},
            {"rolls": 1, "conditions": [{"condition": "minecraft:inverted", "term": mature}],
             "entries": [{"type": "minecraft:item", "name": NS + ":" + seed}]},
        ],
    })


def shaped(name, pattern, key, count=1, category="misc"):
    write_json(os.path.join(DATA, NS, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": category, "pattern": pattern, "key": key,
        "result": {"id": NS + ":" + name, "count": count},
    })


def recipes():
    shaped("empty_bag", ["PPP"], {"P": "minecraft:paper"}, 6)
    shaped("precision_scale", [" G ", "IRI", "SSS"], {"G": "minecraft:glass_pane", "I": "minecraft:iron_ingot",
                                                       "R": "minecraft:redstone",
                                                       "S": "minecraft:smooth_stone_slab"}, 1, "equipment")
    shaped("lab_glassware", ["G G", "GBG", " G "], {"G": "minecraft:glass", "B": "minecraft:glass_bottle"}, 2)
    shaped("gas_mask", ["LLL", "GIG", " L "], {"L": "minecraft:leather", "G": "minecraft:glass_pane",
                                                "I": "minecraft:iron_ingot"}, 1, "equipment")
    shaped("hazmat_suit", ["L L", "LSL", "LLL"], {"L": "minecraft:leather", "S": "minecraft:slime_ball"}, 1,
           "equipment")
    shaped("drying_rack", ["SSS", "STS", "S S"], {"S": "minecraft:stick", "T": "minecraft:string"}, 1, "building")
    shaped("clandestine_lab", ["GGG", "IBI", "ICI"], {"G": NS + ":lab_glassware", "I": "minecraft:iron_ingot",
                                                       "B": "minecraft:brewing_stand", "C": "minecraft:cauldron"},
           1, "building")
    shaped("packaging_table", ["PPP", "WCW", "W W"], {"P": "minecraft:paper", "C": "minecraft:crafting_table",
                                                       "W": "#minecraft:planks"}, 1, "building")
    shaped("arms_workbench", ["III", "WCW", "W W"], {"I": "minecraft:iron_ingot", "C": "minecraft:smithing_table",
                                                      "W": "#minecraft:planks"}, 1, "building")
    shaped("stash_chest", ["III", "ICI", "IHI"], {"I": "minecraft:iron_ingot", "C": "minecraft:chest",
                                                  "H": "minecraft:tripwire_hook"}, 1, "building")


# -- NTM NEXT ----------------------------------------------------------------------------------
#
# Chaque fichier porte la condition neoforge:mod_loaded hbm : sans NTM NEXT, il n'existe pas.
# Les formats sont ceux des recettes de NTM NEXT 1.0.3 (usine chimique, presse, assembleuse).

NTM_LOADED = [{"type": "neoforge:mod_loaded", "modid": "hbm"}]
NTM_MASKS = ("gas_mask", "gas_mask_m65", "gas_mask_mono", "gas_mask_olde", "hazmat_helmet", "hazmat_helmet_red",
             "hazmat_helmet_grey", "hazmat_paa_helmet")
NTM_SUITS = ("hazmat_plate", "hazmat_plate_red", "hazmat_plate_grey", "hazmat_paa_plate")


def ntm_recipe(folder, name, body):
    write_json(os.path.join(DATA, NS, "recipe", folder, name + ".json"),
               dict({"neoforge:conditions": NTM_LOADED}, **body))


def ntm_links():
    # l'usine chimique : deux bidons de precurseurs pour une plaque d'acier et du gaz de petrole
    ntm_recipe("chemical_plant", "chem.dohy_precursor_can", {
        "type": "hbm:chemical_plant", "duration": 100,
        "input_fluids": [{"type": "hbm:petroleum", "amount": 500}],
        "input_items": [{"ingredient": "#c:plates/steel"}],
        "order": 60101, "output_items": [{"count": 2, "id": NS + ":precursor_can"}], "power": 100})
    # la presse, matrice plate : un cristal de synthese en deux pilules (deux unites, comme lui)
    ntm_recipe("press", "press.dohy_synthetic_pills", {
        "type": "hbm:press", "input_items": [{"ingredient": NS + ":synthetic_crystals"}],
        "order": 60102, "output_items": [{"count": 2, "id": NS + ":synthetic_pills"}], "stamp": "flat"})
    # l'assembleuse : des pieces usinees dans l'acier, puis des caisses scellees
    ntm_recipe("assembly_machine", "ass.dohy_machined_parts", {
        "type": "hbm:assembly_machine", "duration": 100,
        "input_items": [{"count": 2, "ingredient": "#c:plates/steel"}],
        "order": 60103, "output_items": [{"count": 3, "id": NS + ":machined_parts"}], "power": 100})
    ntm_recipe("assembly_machine", "ass.dohy_sealed_crate", {
        "type": "hbm:assembly_machine", "duration": 200,
        "input_items": [{"count": 6, "ingredient": NS + ":machined_parts"},
                        {"count": 2, "ingredient": "#c:plates/steel"}],
        "order": 60104, "output_items": [{"count": 2, "id": NS + ":sealed_crate"}], "power": 100})
    # au laboratoire, les masques a gaz et les tenues hazmat de NTM protegent aussi des vapeurs
    write_json(os.path.join(DATA, NS, "tags", "item", "lab_masks.json"), {
        "values": [NS + ":gas_mask"] + [{"id": "hbm:" + name, "required": False} for name in NTM_MASKS]})
    write_json(os.path.join(DATA, NS, "tags", "item", "lab_suits.json"), {
        "values": [NS + ":hazmat_suit"] + [{"id": "hbm:" + name, "required": False} for name in NTM_SUITS]})


def merge_tag(relative, values):
    """Ajoute des valeurs a un tag existant, sans en retirer ni en reordonner."""
    path = os.path.join(DATA, relative)
    if os.path.exists(path):
        with open(path, encoding="utf-8") as handle:
            tag = json.load(handle)
    else:
        tag = {"replace": False, "values": []}
    for value in values:
        if value not in tag["values"]:
            tag["values"].append(value)
    write_json(path, tag)


def main():
    for name, draw in ITEMS.items():
        canvas = draw()
        assert (canvas.w, canvas.h) == (16, 16), name
        canvas.save(os.path.join(ASSETS, "textures", "item", name + ".png"))
        item_model(name)
    for name, draw in CROPS.items():
        for stage in range(4):
            draw(stage).save(os.path.join(ASSETS, "textures", "block", name + "_stage_" + str(stage) + ".png"))
        crop_models(name)
    for name, draw in BLOCK_TEXTURES.items():
        draw().save(os.path.join(ASSETS, "textures", "block", name + ".png"))
    equipment_protection().save(os.path.join(ASSETS, "textures", "entity", "equipment", "humanoid",
                                             "protection.png"))
    write_json(os.path.join(ASSETS, "equipment", "protection.json"), {
        "layers": {"humanoid": [{"texture": NS + ":protection"}]}})
    parts_sheet().save(os.path.join(ASSETS, "textures", "block", PARTS + ".png"))
    for name in RETIRED_TEXTURES:
        retired = os.path.join(ASSETS, "textures", "block", name + ".png")
        if os.path.exists(retired):
            os.remove(retired)
    station_models()
    for name in ("drying_rack", "clandestine_lab", "packaging_table", "arms_workbench", "stash_chest"):
        self_drop(name)
    crop_drop("cannabis_crop", "cannabis_leaf", 3, 5, "cannabis_seeds")
    crop_drop("coca_crop", "coca_leaf", 3, 5, "coca_seeds")
    crop_drop("poppy_crop", "poppy_pod", 2, 3, "poppy_seeds")
    recipes()
    ntm_links()
    merge_tag(os.path.join("minecraft", "tags", "block", "mineable", "pickaxe.json"),
              [NS + ":clandestine_lab", NS + ":arms_workbench", NS + ":stash_chest"])
    merge_tag(os.path.join("minecraft", "tags", "block", "mineable", "axe.json"),
              [NS + ":drying_rack", NS + ":packaging_table"])
    crops = [NS + ":cannabis_crop", NS + ":coca_crop", NS + ":poppy_crop"]
    merge_tag(os.path.join("minecraft", "tags", "block", "crops.json"), crops)
    merge_tag(os.path.join("minecraft", "tags", "block", "maintains_farmland.json"), crops)
    print("economie souterraine : %d objets, %d plantes, %d textures de blocs" % (
        len(ITEMS), len(CROPS), len(BLOCK_TEXTURES)))
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Les peaux des PNJ du spawn (SpawnNpc) : une par guichet, 64 x 64, disposition HumanoidModel.

    python3 -I tools/spawn_npc_assets.py [dossier_apercu]

Quatorze tenues, une par service, a la charte de Paladium : l'uniforme anthracite de la maison,
son rouge au col, aux poignets et a la ceinture, une boucle et un insigne d'or ; chaque guichet
porte une matiere de PalaMod (paladium, titane, amethyste, endium, paladium vert), et certains les
vraies pieces d'armure de PalaMod. Le marchand en tablier rouge, la boutiquiere en veste de
paladium, le commissaire-priseur au gilet d'amethyste, le banquier a cravate d'endium, l'agent des
contrats en titane, la conservatrice au foulard de paladium vert, le chef de gare en rouge,
l'hotesse en or, le passeur en robe d'endium, le contremaitre en armure de titane et casque de
speleo, le receleur sous la cagoule, la pretresse en amethyste, le pilote de la navette et
l'intendant des kits en armure de paladium. Et, en decembre seulement, le Pere Noel du parvis
(le calendrier de l'Avent) : manteau rouge borde de fourrure, bonnet a pompon, barbe blanche.

Le bras et la jambe gauches du modele reprennent, en miroir, la texture des droits : les
details asymetriques (insigne, pochette) ne vont donc que sur le torse et la tete.

La maniere de PalaMod : peu de tons par matiere, la lumiere d'en haut, les cotes et le dos
plus sombres, des contours d'un ton sombre de la matiere. Un dessin propre au mod.

Avec un dossier en argument, une planche d'apercu (face et dos, agrandis) y est ecrite.
"""

import os
import random
import sys
import zlib

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "dohy", "textures", "entity", "spawn_npc")

# Les pieces du modele : u, v, largeur, hauteur, profondeur
HEAD = (0, 0, 8, 8, 8)
HAT = (32, 0, 8, 8, 8)
BODY = (16, 16, 8, 12, 4)
ARM = (40, 16, 4, 12, 4)
LEG = (0, 16, 4, 12, 4)

SHADE = {"front": 1.0, "right": 0.88, "left": 0.84, "back": 0.78, "top": 1.08, "bottom": 0.7}


def shade(color, f):
    return tuple(max(0, min(255, int(round(c * f)))) for c in color)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def face_rect(part, face):
    u, v, w, h, d = part
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }[face]


class Skin:
    def __init__(self, name):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.rng = random.Random(zlib.crc32(name.encode()))

    def raw(self, x, y, color, jitter=4):
        j = self.rng.randint(-jitter, jitter) if jitter else 0
        self.img.putpixel((x, y), tuple(max(0, min(255, c + j)) for c in color[:3]) + (255,))

    def clear(self, x, y):
        self.img.putpixel((x, y), (0, 0, 0, 0))

    def on(self, part, face, lx, ly, color, jitter=4, lit=True):
        """Un pixel d'une face, en coordonnees locales (0, 0 en haut a gauche de la face)."""
        x0, y0, w, h = face_rect(part, face)
        if 0 <= lx < w and 0 <= ly < h:
            self.raw(x0 + lx, y0 + ly, shade(color, SHADE[face]) if lit else color, jitter)

    def fill(self, part, face, x0, y0, x1, y1, color, jitter=4, lit=True):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.on(part, face, x, y, color, jitter, lit)

    def box(self, part, color, jitter=4):
        for face in SHADE:
            _, _, w, h = face_rect(part, face)
            self.fill(part, face, 0, 0, w, h, color, jitter)

    def ring(self, part, y0, y1, color, jitter=4):
        """Une bande tout autour d'une piece (ceinture, bas de pantalon, poignet)."""
        for face in ("right", "front", "left", "back"):
            _, _, w, _ = face_rect(part, face)
            self.fill(part, face, 0, y0, w, y1, color, jitter)

    def column(self, part, face, x, y0, y1, color, jitter=3):
        self.fill(part, face, x, y0, x + 1, y1, color, jitter)


# -- La tete -----------------------------------------------------------------------------------

def head(s, skin, hair, style="short", eyes=(70, 52, 36), brows=True):
    s.box(HEAD, skin, 3)
    dark = shade(hair, 0.82)
    if style != "bald":
        s.fill(HEAD, "top", 0, 0, 8, 8, hair, 5)
    # le dos et les cotes
    back_rows = {"short": 6, "slick": 6, "long": 8, "bun": 6, "bob": 7, "bald": 3, "none": 0}[style]
    side_rows = {"short": 3, "slick": 3, "long": 7, "bun": 3, "bob": 5, "bald": 0, "none": 0}[style]
    s.fill(HEAD, "back", 0, 0, 8, back_rows, hair, 5)
    for face in ("right", "left"):
        s.fill(HEAD, face, 0, 0, 8, side_rows, hair, 5)
        if style in ("short", "slick", "bun") and back_rows > side_rows:
            # derriere l'oreille, les cheveux descendent comme au dos (la moitie arriere de la face)
            behind = (0, 4) if face == "right" else (4, 8)
            s.fill(HEAD, face, behind[0], side_rows, behind[1], back_rows, hair, 5)
        if style in ("short", "slick", "bun"):
            # la patte, pres du visage
            front_col = 7 if face == "right" else 0
            s.fill(HEAD, face, front_col, side_rows, front_col + 1, side_rows + 1, hair, 4)
        if style == "bald":
            s.fill(HEAD, face, 0, 2, 5, 4, hair, 4)
    if style == "bald":
        s.fill(HEAD, "back", 0, 1, 8, 3, hair, 4)
    # la frange
    if style == "short":
        s.fill(HEAD, "front", 0, 0, 8, 1, hair, 4)
        s.fill(HEAD, "front", 0, 1, 3, 2, hair, 4)
        s.fill(HEAD, "front", 6, 1, 8, 2, hair, 4)
    elif style == "slick":
        s.fill(HEAD, "front", 0, 0, 8, 1, hair, 3)
        s.on(HEAD, "front", 0, 1, hair, 3)
        s.on(HEAD, "front", 7, 1, hair, 3)
        s.on(HEAD, "front", 5, 0, shade(hair, 1.25), 2)
    elif style in ("long", "bob"):
        s.fill(HEAD, "front", 0, 0, 8, 2, hair, 4)
        bottom = 7 if style == "long" else 5
        s.fill(HEAD, "front", 0, 2, 1, bottom, hair, 4)
        s.fill(HEAD, "front", 7, 2, 8, bottom, hair, 4)
        s.on(HEAD, "front", 3, 1, shade(hair, 1.15), 2)
    elif style == "bun":
        s.fill(HEAD, "front", 0, 0, 8, 1, hair, 4)
        s.fill(HEAD, "front", 0, 1, 2, 2, hair, 4)
        s.fill(HEAD, "front", 6, 1, 8, 2, hair, 4)
        # le chignon, sur le calque du chapeau
        s.fill(HAT, "back", 2, 0, 6, 3, hair, 4)
        s.fill(HAT, "top", 2, 5, 6, 8, hair, 4)
        s.fill(HAT, "back", 3, 3, 5, 4, dark, 3)
    # le visage
    if brows:
        s.fill(HEAD, "front", 1, 3, 3, 4, dark if style not in ("bald", "none") else shade(skin, 0.7), 2)
        s.fill(HEAD, "front", 5, 3, 7, 4, dark if style not in ("bald", "none") else shade(skin, 0.7), 2)
    s.on(HEAD, "front", 1, 4, (240, 240, 240), 0, lit=False)
    s.on(HEAD, "front", 2, 4, eyes, 0, lit=False)
    s.on(HEAD, "front", 5, 4, eyes, 0, lit=False)
    s.on(HEAD, "front", 6, 4, (240, 240, 240), 0, lit=False)
    s.fill(HEAD, "front", 3, 5, 5, 6, shade(skin, 0.86), 2)
    s.fill(HEAD, "front", 3, 6, 5, 7, shade(skin, 0.66), 2)


def mustache(s, hair):
    s.fill(HEAD, "front", 2, 6, 6, 7, hair, 3)
    s.on(HEAD, "front", 2, 7, shade(hair, 0.9), 3)
    s.on(HEAD, "front", 5, 7, shade(hair, 0.9), 3)


def beard(s, hair):
    s.fill(HEAD, "front", 1, 6, 7, 8, hair, 5)
    s.fill(HEAD, "front", 0, 5, 1, 8, hair, 5)
    s.fill(HEAD, "front", 7, 5, 8, 8, hair, 5)
    s.fill(HEAD, "front", 3, 6, 5, 7, shade(hair, 0.55), 2)
    s.fill(HEAD, "bottom", 0, 0, 8, 4, hair, 5)
    for face, col in (("right", 7), ("left", 0)):
        s.fill(HEAD, face, 0, 4, 8, 8, hair, 5)
        s.fill(HEAD, face, 0, 4, 8, 5, shade(hair, 0.85), 3)


def glasses(s, frame, lens=(200, 226, 240)):
    s.fill(HEAD, "front", 0, 4, 8, 5, frame, 2)
    s.on(HEAD, "front", 1, 4, lens, 0, lit=False)
    s.on(HEAD, "front", 2, 4, mix(lens, (40, 40, 60), 0.6), 0, lit=False)
    s.on(HEAD, "front", 5, 4, mix(lens, (40, 40, 60), 0.6), 0, lit=False)
    s.on(HEAD, "front", 6, 4, lens, 0, lit=False)
    s.on(HEAD, "right", 7, 4, frame, 2)
    s.on(HEAD, "right", 6, 4, frame, 2)
    s.on(HEAD, "left", 0, 4, frame, 2)
    s.on(HEAD, "left", 1, 4, frame, 2)


def lips(s, color):
    s.fill(HEAD, "front", 3, 6, 5, 7, color, 2)


def earrings(s, color):
    s.on(HEAD, "right", 4, 5, color, 0)
    s.on(HEAD, "left", 3, 5, color, 0)


# -- Les coiffes (calque du chapeau) --------------------------------------------------------------

def cap(s, color, band=None, visor=(28, 28, 32), rows=3):
    s.fill(HAT, "top", 0, 0, 8, 8, color, 4)
    for face in ("right", "left", "back", "front"):
        s.fill(HAT, face, 0, 0, 8, rows, color, 4)
    if band:
        for face in ("right", "left", "back", "front"):
            s.fill(HAT, face, 0, rows - 1, 8, rows, band, 3)
    s.fill(HAT, "front", 0, rows, 8, rows + 1, visor, 2)


def hard_hat(s, color, lamp=(255, 236, 140)):
    s.fill(HAT, "top", 0, 0, 8, 8, color, 4)
    s.fill(HAT, "top", 3, 0, 5, 8, shade(color, 1.12), 3)
    for face in ("right", "left", "back", "front"):
        s.fill(HAT, face, 0, 0, 8, 3, color, 4)
        s.fill(HAT, face, 0, 2, 8, 3, shade(color, 0.8), 3)
    s.fill(HAT, "front", 3, 0, 5, 2, lamp, 0, lit=False)
    s.fill(HAT, "front", 3, 2, 5, 3, (90, 90, 96), 2)


def hood(s, color, trim=None, open_rows=(2, 8), open_cols=(1, 7)):
    s.fill(HAT, "top", 0, 0, 8, 8, color, 5)
    for face in ("right", "left", "back"):
        s.fill(HAT, face, 0, 0, 8, 8, color, 5)
    s.fill(HAT, "front", 0, 0, 8, 8, color, 5)
    # l'ouverture du visage
    x0, x1 = open_cols
    y0, y1 = open_rows
    x_start, y_start, _, _ = face_rect(HAT, "front")
    for y in range(y0, y1):
        for x in range(x0, x1):
            s.clear(x_start + x, y_start + y)
    if trim:
        for y in range(y0, y1):
            s.on(HAT, "front", x0 - 1, y, trim, 2)
            s.on(HAT, "front", x1, y, trim, 2)
        s.fill(HAT, "front", x0 - 1, y0 - 1, x1 + 1, y0, trim, 2)
    # l'ombre de la capuche sur le front
    s.fill(HEAD, "front", 1, 0, 7, 2, shade(color, 0.55), 2)


def beret(s, color):
    s.fill(HAT, "top", 0, 0, 8, 8, color, 4)
    for face in ("right", "back", "front"):
        s.fill(HAT, face, 0, 0, 8, 2, color, 4)
    s.fill(HAT, "left", 0, 0, 8, 3, color, 4)
    s.on(HAT, "top", 4, 4, shade(color, 0.7), 2)
    s.fill(HAT, "front", 0, 1, 8, 2, shade(color, 0.75), 2)


def pillbox(s, color, trim):
    s.fill(HAT, "top", 1, 1, 7, 7, color, 3)
    s.fill(HAT, "front", 1, 0, 7, 1, color, 3)
    s.fill(HAT, "right", 1, 0, 7, 1, color, 3)
    s.fill(HAT, "left", 1, 0, 7, 1, color, 3)
    s.fill(HAT, "back", 1, 0, 7, 1, color, 3)
    s.fill(HAT, "front", 1, 0, 7, 1, trim, 2)


def circlet(s, gold, gem):
    for face in ("right", "left", "back", "front"):
        s.fill(HAT, face, 0, 1, 8, 2, gold, 2)
    s.on(HAT, "front", 3, 1, gem, 0, lit=False)
    s.on(HAT, "front", 4, 1, gem, 0, lit=False)
    s.on(HAT, "front", 3, 0, gold, 2)
    s.on(HAT, "front", 4, 0, gold, 2)


def helmet(s, color, visor, stripe):
    s.fill(HAT, "top", 0, 0, 8, 8, color, 3)
    s.fill(HAT, "top", 3, 0, 5, 8, stripe, 2)
    for face in ("right", "left", "back"):
        s.fill(HAT, face, 0, 0, 8, 6, color, 3)
    s.fill(HAT, "back", 3, 0, 5, 6, stripe, 2)
    s.fill(HAT, "front", 0, 0, 8, 2, color, 3)
    s.fill(HAT, "front", 3, 0, 5, 1, stripe, 2)
    # la visiere relevee, et le micro sur la joue droite
    s.fill(HAT, "front", 0, 2, 8, 3, visor, 1, lit=False)
    s.on(HAT, "front", 1, 2, mix(visor, (255, 255, 255), 0.6), 0, lit=False)
    s.fill(HAT, "front", 0, 3, 1, 6, color, 3)
    s.fill(HAT, "front", 7, 3, 8, 6, color, 3)
    s.fill(HAT, "right", 5, 6, 8, 7, (40, 40, 44), 1)


# -- Le corps -------------------------------------------------------------------------------------

def outfit(s, top, sleeves=None, trousers=(60, 62, 70), shoes=(30, 30, 34), skin=(220, 170, 130),
           hands=True, cuff=None, rolled=False):
    sleeves = sleeves or top
    s.box(BODY, top, 4)
    s.box(ARM, sleeves, 4)
    s.box(LEG, trousers, 4)
    # le cou, au milieu du dessus du torse
    s.fill(BODY, "top", 3, 1, 5, 3, skin, 2)
    if rolled:
        s.ring(ARM, 5, 12, skin, 3)
        s.ring(ARM, 4, 5, shade(sleeves, 0.85), 3)
    if cuff:
        s.ring(ARM, 9, 10, cuff, 3)
    if hands:
        s.ring(ARM, 10, 12, skin, 3)
        s.fill(ARM, "bottom", 0, 0, 4, 4, skin, 3)
    s.ring(LEG, 10, 12, shoes, 3)
    s.fill(LEG, "front", 0, 9, 4, 10, shade(shoes, 1.25), 2)
    s.fill(LEG, "bottom", 0, 0, 4, 4, shade(shoes, 0.6), 2)


def belt(s, color, buckle=None, row=10):
    s.ring(BODY, row, row + 2, color, 3)
    if buckle:
        s.fill(BODY, "front", 3, row, 5, row + 2, buckle, 2)


def shirt_v(s, shirt, depth=4):
    """Le col en V d'une veste ouverte sur une chemise."""
    for y in range(depth):
        half = 1 if y < depth - 1 else 0
        s.fill(BODY, "front", 3 - half, y, 5 + half, y + 1, shirt, 2)


def tie(s, color, top=0, bottom=8):
    s.fill(BODY, "front", 3, top, 5, top + 1, shade(color, 0.85), 2)
    s.column(BODY, "front", 3, top + 1, bottom, color)
    s.column(BODY, "front", 4, top + 1, bottom, shade(color, 0.9))
    s.on(BODY, "front", 3, bottom, shade(color, 0.8), 2)


def bow_tie(s, color):
    s.fill(BODY, "front", 2, 1, 6, 2, color, 2)
    s.on(BODY, "front", 3, 1, shade(color, 0.7), 1)
    s.on(BODY, "front", 4, 1, shade(color, 0.7), 1)


def lapels(s, color, depth=5):
    for y in range(depth):
        s.on(BODY, "front", 2 if y < depth - 1 else 3, y, color, 2)
        s.on(BODY, "front", 5 if y < depth - 1 else 4, y, color, 2)


def buttons(s, color, rows=(5, 7, 9), col=4):
    for y in rows:
        s.on(BODY, "front", col, y, color, 1)


def apron(s, color, strap=None):
    strap = strap or shade(color, 0.8)
    s.fill(BODY, "front", 1, 3, 7, 12, color, 4)
    s.column(BODY, "front", 1, 0, 3, strap)
    s.column(BODY, "front", 6, 0, 3, strap)
    s.fill(BODY, "front", 2, 5, 6, 6, shade(color, 0.8), 2)
    s.fill(LEG, "front", 0, 0, 4, 7, color, 4)
    s.fill(LEG, "front", 0, 6, 4, 7, shade(color, 0.8), 2)
    # le noeud dans le dos
    s.fill(BODY, "back", 0, 9, 8, 10, strap, 2)
    s.fill(BODY, "back", 3, 8, 5, 11, shade(strap, 0.9), 2)


def robe(s, color, trim=None, sash=None):
    s.box(LEG, color, 4)
    s.ring(LEG, 11, 12, shade(color, 0.7), 2)
    if trim:
        s.ring(LEG, 10, 11, trim, 2)
        s.column(BODY, "front", 3, 0, 12, trim)
        s.column(LEG, "front", 3, 0, 10, trim)
        s.ring(ARM, 9, 10, trim, 2)
    if sash:
        s.ring(BODY, 8, 10, sash, 3)
        s.fill(BODY, "front", 5, 10, 6, 12, sash, 2)


def stripes(s, color, rows=(6, 8)):
    for y in rows:
        s.ring(BODY, y, y + 1, color, 2)
        s.ring(ARM, y - 2, y - 1, color, 2)


# -- Les quatorze ---------------------------------------------------------------------------------

# -- La charte de Paladium ------------------------------------------------------------------------
#
# Le personnel du spawn porte l'uniforme de la maison : le gris anthracite des interfaces de
# Paladium (Ui.BG), son rouge (Ui.RED, EF3826) au col, aux poignets et a la ceinture, une boucle et
# un insigne en or de paladium. Chaque guichet a sa matiere de PalaMod — paladium, titane,
# amethyste, endium, paladium vert — au brassard, a la cravate ou a la robe, et certains portent
# les vraies pieces d'armure de PalaMod (le casque de speleo du Mineur, l'armure en paladium de
# l'intendant des kits). L'insigne est un losange propre au mod, pas le logo de Paladium.

ANTHRACITE = (40, 39, 46)
ANTHRACITE_DARK = (27, 26, 32)
ANTHRACITE_LIGHT = (62, 61, 70)
SHOES = (18, 17, 22)
RED = (239, 56, 38)
RED_DARK = (168, 36, 24)
WHITE = (233, 232, 238)
GOLD = (255, 178, 62)
GOLD_LIGHT = (255, 227, 101)

# Les matieres de PalaMod (relevees sur les armures de PalaMod).
PALADIUM = (221, 84, 17)
PALADIUM_LIGHT = (255, 111, 39)
TITANE = (117, 110, 103)
TITANE_LIGHT = (168, 163, 156)
AMETHYST = (172, 61, 211)
AMETHYST_LIGHT = (221, 66, 223)
ENDIUM = (43, 46, 189)
ENDIUM_LIGHT = (105, 92, 255)
GREEN_PALADIUM = (70, 165, 113)
GREEN_PALADIUM_LIGHT = (86, 198, 137)

ARMOR = os.path.join(ROOT, "src", "main", "resources", "assets", "palamod", "textures", "entity", "equipment")


def armor(s, name, pieces):
    """Les vraies pieces d'armure de PalaMod, peintes sur la peau : meme disposition que le modele.

    helmet : la tete de l'armure sur le calque du chapeau ; cap : son haut seulement (le visage
    reste visible) ; chest : le torse et les bras ;
    boots : le bas des jambes ; leggings : la ceinture et les jambes (deuxieme calque).
    """
    layer1 = Image.open(os.path.join(ARMOR, "humanoid", name + ".png")).convert("RGBA")
    regions = []
    if "helmet" in pieces:
        regions.append((layer1, (0, 0, 32, 16), (32, 0)))
    if "cap" in pieces:
        # le haut du casque seulement, pour garder le visage : le dessus et trois rangs tout autour
        regions.append((layer1, (8, 0, 16, 8), (40, 0)))
        regions.append((layer1, (0, 8, 32, 11), (32, 8)))
    if "chest" in pieces:
        regions.append((layer1, (16, 16, 40, 32), (16, 16)))
        regions.append((layer1, (40, 16, 56, 32), (40, 16)))
    if "boots" in pieces:
        regions.append((layer1, (0, 16, 16, 32), (0, 16)))
    if "leggings" in pieces:
        layer2 = Image.open(os.path.join(ARMOR, "humanoid_leggings", name + ".png")).convert("RGBA")
        regions.append((layer2, (16, 16, 40, 32), (16, 16)))
        regions.append((layer2, (0, 16, 16, 32), (0, 16)))
    for source, box, at in regions:
        s.img.alpha_composite(source.crop(box), at)


def badge(s, col=5, row=2):
    """L'insigne de la maison : un petit losange rouge a coeur d'or, sur la poitrine."""
    s.on(BODY, "front", col, row, RED, 0)
    s.on(BODY, "front", col - 1, row + 1, RED, 0)
    s.on(BODY, "front", col, row + 1, GOLD, 0)
    s.on(BODY, "front", col + 1, row + 1, RED_DARK, 0)
    s.on(BODY, "front", col, row + 2, RED_DARK, 0)


def uniform(s, skin, accent, top=ANTHRACITE, trousers=ANTHRACITE_DARK, collar=RED, armband=True,
            emblem=True, cuff=RED):
    """L'uniforme du personnel : anthracite, col et poignets rouges, ceinture rouge a boucle d'or."""
    outfit(s, top, trousers=trousers, shoes=SHOES, skin=skin, cuff=cuff)
    # le col montant, rouge, tout autour du cou
    s.ring(BODY, 0, 1, collar, 2)
    s.fill(BODY, "top", 1, 0, 7, 1, collar, 2)
    s.fill(BODY, "top", 1, 3, 7, 4, collar, 2)
    # la couture du milieu et l'ourlet
    s.column(BODY, "front", 3, 1, 10, shade(top, 0.8), 2)
    belt(s, RED, buckle=GOLD, row=9)
    s.ring(BODY, 11, 12, shade(top, 0.75), 2)
    # le brassard a la matiere du guichet
    if armband:
        s.ring(ARM, 2, 4, accent, 3)
        s.ring(ARM, 2, 3, shade(accent, 1.15), 2)
    # la bande rouge le long du pantalon
    s.column(LEG, "right", 1, 0, 10, RED_DARK, 2)
    if emblem:
        badge(s)


def scarf(s, color):
    """Un foulard a la matiere du guichet, noue sur le col."""
    s.fill(BODY, "front", 2, 0, 6, 2, color, 2)
    s.fill(BODY, "front", 3, 2, 5, 4, shade(color, 0.9), 2)
    s.on(BODY, "front", 4, 4, shade(color, 0.8), 1)


def shop(s):
    # le marchand : le tablier rouge de la maison sur l'uniforme
    skin = (224, 172, 132)
    hair = (92, 58, 34)
    head(s, skin, hair, "short")
    mustache(s, shade(hair, 0.9))
    uniform(s, skin, RED, emblem=False)
    apron(s, RED, strap=RED_DARK)
    s.fill(BODY, "front", 2, 5, 6, 6, RED_DARK, 1)
    badge(s, col=4, row=6)
    s.on(BODY, "front", 6, 7, GOLD_LIGHT, 0)  # un crayon dans la poche
    cap(s, ANTHRACITE, band=RED, rows=2)


def boutique(s):
    # la boutiquiere : veste en or de paladium sur l'anthracite
    skin = (238, 196, 160)
    hair = (64, 36, 24)
    head(s, skin, hair, "long", eyes=(52, 96, 60))
    lips(s, (186, 82, 92))
    earrings(s, GOLD_LIGHT)
    uniform(s, skin, PALADIUM, top=PALADIUM, collar=ANTHRACITE, cuff=ANTHRACITE)
    shirt_v(s, WHITE, 4)
    lapels(s, ANTHRACITE)
    s.ring(ARM, 0, 9, PALADIUM, 3)
    s.ring(ARM, 0, 1, PALADIUM_LIGHT, 2)


def market(s):
    # le commissaire-priseur : trois-pieces anthracite, gilet et noeud d'amethyste
    skin = (210, 160, 120)
    hair = (150, 150, 152)
    head(s, skin, hair, "slick", eyes=(60, 80, 110))
    glasses(s, GOLD)
    uniform(s, skin, AMETHYST, collar=WHITE, cuff=WHITE)
    shirt_v(s, WHITE, 4)
    s.fill(BODY, "front", 2, 4, 6, 9, AMETHYST, 3)  # le gilet
    s.fill(BODY, "front", 2, 4, 6, 5, AMETHYST_LIGHT, 2)
    buttons(s, GOLD, rows=(5, 7), col=3)
    bow_tie(s, AMETHYST_LIGHT)
    lapels(s, ANTHRACITE_LIGHT)
    s.on(BODY, "front", 6, 3, WHITE, 0)  # la pochette


def bank(s):
    # le banquier : costume anthracite, cravate d'endium, lunettes d'or
    skin = (232, 186, 150)
    hair = (40, 32, 28)
    head(s, skin, hair, "slick", eyes=(70, 52, 36))
    glasses(s, GOLD)
    uniform(s, skin, ENDIUM, collar=WHITE, cuff=WHITE)
    shirt_v(s, WHITE, 5)
    tie(s, ENDIUM_LIGHT, top=0, bottom=8)
    lapels(s, ANTHRACITE_LIGHT, depth=6)
    s.fill(BODY, "front", 6, 2, 7, 3, GOLD_LIGHT, 0)  # la pince a billets


def contracts(s):
    # l'agent des contrats : veste de titane, sacoche en bandouliere, casquette de la maison
    skin = (196, 150, 112)
    hair = (30, 24, 20)
    head(s, skin, hair, "short")
    uniform(s, skin, TITANE, top=TITANE, collar=RED)
    cap(s, ANTHRACITE, band=RED, rows=2)
    buttons(s, WHITE, rows=(3, 5, 7), col=4)
    # la bandouliere, de l'epaule droite a la hanche gauche
    for y in range(9):
        s.on(BODY, "front", 7 - y * 7 // 8, y + 1, (96, 60, 34), 2)
    s.fill(BODY, "back", 0, 2, 8, 3, (96, 60, 34), 2)
    s.ring(ARM, 0, 2, TITANE_LIGHT, 2)


def museum(s):
    # la conservatrice : tailleur anthracite, foulard de paladium vert, chignon et lunettes
    skin = (240, 200, 170)
    hair = (120, 64, 36)
    head(s, skin, hair, "bun", eyes=(60, 110, 70))
    glasses(s, (40, 40, 44))
    lips(s, (170, 70, 80))
    uniform(s, skin, GREEN_PALADIUM)
    scarf(s, GREEN_PALADIUM_LIGHT)
    buttons(s, GOLD, rows=(6, 8), col=4)


def return_(s):
    # le chef de gare : uniforme rouge de la maison, galons d'or, casquette
    skin = (214, 164, 124)
    hair = (60, 42, 30)
    head(s, skin, hair, "short")
    mustache(s, hair)
    uniform(s, skin, GOLD, top=RED, trousers=ANTHRACITE_DARK, collar=ANTHRACITE, cuff=GOLD)
    cap(s, RED, band=GOLD, rows=3)
    buttons(s, GOLD_LIGHT, rows=(3, 5, 7), col=3)
    buttons(s, GOLD_LIGHT, rows=(3, 5, 7), col=4)
    s.fill(BODY, "front", 0, 1, 2, 2, GOLD, 1)  # les epaulettes
    s.fill(BODY, "front", 6, 1, 8, 2, GOLD, 1)
    s.ring(ARM, 0, 1, GOLD, 1)


def daily(s):
    # l'hotesse : tailleur anthracite, foulard et toque en or de paladium
    skin = (236, 190, 156)
    hair = (232, 200, 110)
    head(s, skin, hair, "bob", eyes=(56, 110, 190))
    lips(s, (200, 90, 96))
    uniform(s, skin, PALADIUM_LIGHT)
    pillbox(s, ANTHRACITE, PALADIUM_LIGHT)
    scarf(s, PALADIUM_LIGHT)
    buttons(s, GOLD, rows=(6, 8), col=4)


def portals(s):
    # le passeur : la robe d'endium, liseres d'or, capuche
    skin = (200, 170, 150)
    head(s, skin, (40, 30, 30), "none", eyes=(178, 134, 255), brows=False)
    outfit(s, ENDIUM, trousers=ENDIUM, shoes=SHOES, skin=skin, cuff=GOLD)
    robe(s, ENDIUM, trim=GOLD, sash=RED)
    hood(s, shade(ENDIUM, 0.8), trim=ENDIUM_LIGHT)
    s.ring(ARM, 0, 2, ENDIUM_LIGHT, 2)
    badge(s, col=5, row=2)


def mine(s):
    # le contremaitre : l'armure en titane et le casque de speleo de PalaMod
    skin = (206, 156, 116)
    hair = (110, 70, 40)
    head(s, skin, hair, "short", eyes=(70, 90, 110))
    beard(s, hair)
    uniform(s, skin, TITANE)
    armor(s, "titane", ("chest", "boots"))
    armor(s, "cave_helmet", ("cap",))


def black_market(s):
    # le receleur : cagoule de PalaMod, manteau noir, liseres d'amethyste
    skin = (180, 136, 104)
    head(s, skin, (30, 26, 30), "none", eyes=(247, 141, 255), brows=False)
    coat = (22, 21, 26)
    outfit(s, coat, trousers=ANTHRACITE_DARK, shoes=SHOES, skin=(24, 22, 26))
    armor(s, "balaclava", ("helmet",))
    buttons(s, AMETHYST, rows=(3, 5, 7, 9), col=3)
    s.column(BODY, "front", 4, 0, 12, shade(coat, 0.7))
    belt(s, (14, 14, 18), buckle=AMETHYST_LIGHT)
    s.ring(ARM, 9, 10, AMETHYST, 2)
    s.ring(BODY, 0, 1, AMETHYST, 2)


def job_altar(s):
    # la pretresse de l'autel : robe d'amethyste, etole blanche, diademe d'or
    skin = (226, 180, 140)
    hair = (236, 232, 226)
    head(s, skin, hair, "long", eyes=(221, 66, 223))
    outfit(s, AMETHYST, trousers=AMETHYST, shoes=SHOES, skin=skin)
    robe(s, AMETHYST, trim=GOLD, sash=RED)
    s.column(BODY, "front", 2, 0, 12, WHITE)
    s.column(BODY, "front", 5, 0, 12, WHITE)
    s.column(LEG, "front", 1, 0, 9, WHITE)
    s.ring(ARM, 0, 2, AMETHYST_LIGHT, 2)
    circlet(s, GOLD, AMETHYST_LIGHT)


def shuttle(s):
    # le pilote de la navette : combinaison anthracite, casque blanc a visiere d'endium
    skin = (200, 156, 120)
    hair = (46, 34, 26)
    head(s, skin, hair, "short")
    uniform(s, skin, ENDIUM, trousers=ANTHRACITE)
    helmet(s, WHITE, ENDIUM, RED)
    s.column(BODY, "front", 4, 1, 9, (150, 150, 156))  # la fermeture
    s.fill(BODY, "front", 1, 3, 3, 5, ENDIUM_LIGHT, 1)  # l'ecusson
    s.fill(ARM, "right", 1, 5, 3, 7, WHITE, 1)
    s.ring(LEG, 6, 8, ANTHRACITE_DARK, 2)


def kits(s):
    # l'intendant des kits : l'armure en paladium de PalaMod, de pied en cap
    skin = (220, 170, 130)
    hair = (70, 44, 30)
    head(s, skin, hair, "short", eyes=(60, 90, 60))
    uniform(s, skin, PALADIUM)
    armor(s, "paladium", ("chest", "leggings", "boots"))
    beret(s, RED)
    s.on(HAT, "front", 6, 1, GOLD, 0)


SANTA_RED = (196, 30, 36)
FUR = (240, 240, 244)
GLOVES = (24, 22, 26)


def advent(s):
    # le Pere Noel du parvis, en decembre : manteau rouge borde de fourrure blanche, ceinture
    # noire a boucle d'or, gants et bottes noirs, barbe et moustache blanches, bonnet a pompon
    skin = (232, 182, 150)
    hair = (236, 236, 240)
    head(s, skin, hair, "short", eyes=(60, 90, 140))
    beard(s, FUR)
    s.fill(HEAD, "front", 2, 6, 6, 7, (252, 252, 254), 1)  # la moustache
    s.fill(HEAD, "front", 3, 7, 5, 8, (150, 70, 70), 1)  # la bouche
    s.on(HEAD, "front", 1, 5, (236, 146, 132), 1)  # les joues
    s.on(HEAD, "front", 6, 5, (236, 146, 132), 1)
    outfit(s, SANTA_RED, trousers=SANTA_RED, shoes=SHOES, skin=skin, cuff=FUR)
    s.ring(ARM, 10, 12, GLOVES, 2)
    s.fill(ARM, "bottom", 0, 0, 4, 4, GLOVES, 2)
    # la fourrure : l'ourlet du manteau et sa fermeture
    s.ring(BODY, 11, 12, FUR, 2)
    s.column(BODY, "front", 3, 0, 12, FUR, 2)
    s.column(BODY, "front", 4, 0, 12, FUR, 2)
    s.ring(BODY, 0, 1, FUR, 2)
    belt(s, GLOVES, buckle=GOLD, row=8)
    # les bottes, hautes, a revers de fourrure
    s.ring(LEG, 8, 12, SHOES, 3)
    s.ring(LEG, 7, 8, FUR, 2)
    # le bonnet : rouge, bande de fourrure, pompon blanc qui retombe derriere
    s.fill(HAT, "top", 0, 0, 8, 8, SANTA_RED, 4)
    for face in ("right", "left", "back", "front"):
        s.fill(HAT, face, 0, 0, 8, 2, SANTA_RED, 4)
        s.fill(HAT, face, 0, 2, 8, 3, FUR, 2)
    s.fill(HAT, "top", 5, 5, 8, 8, FUR, 1)
    s.fill(HAT, "back", 5, 0, 8, 2, FUR, 1)


NPCS = [
    ("shop", shop), ("boutique", boutique), ("market", market), ("bank", bank), ("contracts", contracts),
    ("museum", museum), ("return", return_), ("daily", daily), ("portals", portals), ("mine", mine),
    ("black_market", black_market), ("job_altar", job_altar), ("shuttle", shuttle), ("kits", kits),
    ("advent", advent),
]


def paint(name, painter):
    s = Skin(name)
    painter(s)
    return s.img


# -- L'apercu -------------------------------------------------------------------------------------

def figure(img, view):
    """Assemble une silhouette de face ou de dos, a l'echelle 1 (16 x 32)."""
    out = Image.new("RGBA", (16, 32), (0, 0, 0, 0))

    def crop(part, face):
        x, y, w, h = face_rect(part, face)
        return img.crop((x, y, x + w, y + h))

    face = view
    head_img = crop(HEAD, face)
    hat_img = crop(HAT, face)
    body = crop(BODY, face)
    arm = crop(ARM, face)
    leg = crop(LEG, face)
    out.alpha_composite(head_img, (4, 0))
    out.alpha_composite(hat_img, (4, 0))
    out.alpha_composite(body, (4, 8))
    out.alpha_composite(arm, (0, 8))
    out.alpha_composite(arm.transpose(Image.FLIP_LEFT_RIGHT), (12, 8))
    out.alpha_composite(leg, (4, 20))
    out.alpha_composite(leg.transpose(Image.FLIP_LEFT_RIGHT), (8, 20))
    return out


def preview(images, folder):
    scale = 6
    cell_w, cell_h = 16 * scale * 2 + 24, 32 * scale + 24
    rows = (len(images) + 6) // 7
    sheet = Image.new("RGBA", (cell_w * 7, cell_h * rows), (40, 42, 48, 255))
    for index, (name, img) in enumerate(images):
        col, row = index % 7, index // 7
        for k, view in enumerate(("front", "back")):
            fig = figure(img, view).resize((16 * scale, 32 * scale), Image.NEAREST)
            sheet.alpha_composite(fig, (col * cell_w + 8 + k * (16 * scale + 8), row * cell_h + 12))
    os.makedirs(folder, exist_ok=True)
    sheet.save(os.path.join(folder, "spawn_npcs.png"))


def main():
    os.makedirs(OUT, exist_ok=True)
    images = []
    for name, painter in NPCS:
        img = paint(name, painter)
        img.save(os.path.join(OUT, name + ".png"))
        images.append((name, img))
    print(f"{len(images)} peaux ecrites dans {os.path.relpath(OUT, ROOT)}")
    if len(sys.argv) > 1:
        preview(images, sys.argv[1])
        print("apercu :", os.path.join(sys.argv[1], "spawn_npcs.png"))


if __name__ == "__main__":
    main()

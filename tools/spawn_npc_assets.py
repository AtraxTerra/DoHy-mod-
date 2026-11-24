#!/usr/bin/env python3
"""Les peaux des PNJ du spawn (SpawnNpc) : une par guichet, 64 x 64, disposition HumanoidModel.

    python3 -I tools/spawn_npc_assets.py [dossier_apercu]

Quatorze tenues originales, une par service : le marchand en tablier, la boutiquiere en veste
d'or, le commissaire-priseur en trois-pieces, le banquier en costume, l'agent des contrats et
sa sacoche, la conservatrice a lunettes, le passeur en uniforme de chef de gare, l'hotesse,
le gardien des donjons sous sa capuche, le contremaitre casque, le receleur masque, la
pretresse de l'autel, le pilote de la navette et l'intendant des kits.

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

def shop(s):
    skin = (224, 172, 132)
    hair = (92, 58, 34)
    head(s, skin, hair, "short")
    mustache(s, shade(hair, 0.9))
    outfit(s, (236, 226, 200), trousers=(96, 70, 48), shoes=(52, 36, 26), skin=skin, rolled=True)
    apron(s, (46, 140, 70), strap=(32, 98, 50))
    s.on(BODY, "front", 5, 6, (240, 210, 120), 0)  # un crayon dans la poche


def boutique(s):
    skin = (238, 196, 160)
    hair = (64, 36, 24)
    head(s, skin, hair, "long", eyes=(52, 96, 60))
    lips(s, (186, 82, 92))
    earrings(s, (250, 210, 80))
    black = (30, 26, 32)
    outfit(s, (226, 174, 32), trousers=black, shoes=black, skin=skin, cuff=black)
    shirt_v(s, (248, 244, 236), 4)
    lapels(s, black)
    buttons(s, black, rows=(6, 8))
    belt(s, black, buckle=(250, 214, 90))
    s.fill(LEG, "front", 1, 10, 3, 11, (250, 214, 90), 0)


def market(s):
    skin = (210, 160, 120)
    hair = (150, 150, 152)
    head(s, skin, hair, "slick", eyes=(60, 80, 110))
    glasses(s, (60, 46, 30))
    suit = (150, 82, 36)
    outfit(s, suit, trousers=shade(suit, 0.8), shoes=(24, 24, 28), skin=skin, cuff=(244, 240, 230))
    shirt_v(s, (244, 240, 230), 4)
    s.fill(BODY, "front", 2, 4, 6, 9, (110, 58, 28), 3)  # le gilet
    buttons(s, (230, 190, 90), rows=(5, 7), col=3)
    bow_tie(s, (176, 28, 40))
    lapels(s, shade(suit, 0.75))
    s.on(BODY, "front", 6, 3, (250, 250, 250), 0)  # la pochette
    s.fill(BODY, "front", 2, 10, 6, 11, shade(suit, 0.9), 2)


def bank(s):
    skin = (232, 186, 150)
    hair = (30, 28, 30)
    head(s, skin, hair, "slick", eyes=(70, 52, 36))
    glasses(s, (206, 168, 70))
    navy = (34, 46, 90)
    outfit(s, navy, trousers=shade(navy, 0.9), shoes=(16, 16, 20), skin=skin, cuff=(244, 244, 248))
    shirt_v(s, (244, 244, 248), 5)
    tie(s, (54, 163, 255), top=0, bottom=8)
    s.on(BODY, "front", 3, 5, (250, 210, 80), 0)  # la pince a cravate
    s.on(BODY, "front", 4, 5, (250, 210, 80), 0)
    lapels(s, shade(navy, 0.7), depth=6)
    s.fill(BODY, "front", 5, 3, 7, 4, shade(navy, 1.3), 2)  # la poche de poitrine
    s.ring(ARM, 9, 10, (244, 244, 248), 2)
    s.on(ARM, "front", 1, 9, (250, 210, 80), 0)  # la montre


def contracts(s):
    skin = (190, 140, 100)
    hair = (40, 30, 24)
    head(s, skin, hair, "short")
    coat = (100, 120, 220)
    outfit(s, coat, trousers=(90, 92, 100), shoes=(80, 54, 34), skin=skin)
    cap(s, (34, 40, 80), band=coat, rows=2)
    s.fill(BODY, "front", 3, 0, 5, 2, (236, 236, 240), 2)
    s.fill(BODY, "front", 1, 7, 3, 9, shade(coat, 0.8), 2)
    s.fill(BODY, "front", 5, 7, 7, 9, shade(coat, 0.8), 2)
    buttons(s, (230, 230, 240), rows=(3, 5, 7, 9), col=4)
    # la sanglade de la sacoche, en travers du torse
    strap = (110, 72, 40)
    for i in range(8):
        s.on(BODY, "front", 7 - i, i + 1, strap, 2)
        s.on(BODY, "back", i, i + 1, strap, 2)
    s.fill(BODY, "left", 0, 8, 4, 12, strap, 3)
    s.fill(BODY, "left", 1, 9, 3, 10, (200, 170, 90), 1)


def museum(s):
    skin = (244, 206, 176)
    hair = (150, 60, 36)
    head(s, skin, hair, "bun", eyes=(60, 110, 70))
    glasses(s, (40, 40, 44))
    lips(s, (170, 70, 80))
    s.on(HEAD, "right", 6, 5, (210, 180, 90), 0)  # la chainette des lunettes
    s.on(HEAD, "left", 1, 5, (210, 180, 90), 0)
    magenta = (200, 60, 150)
    plum = (70, 30, 60)
    outfit(s, magenta, trousers=plum, shoes=(40, 24, 36), skin=skin, cuff=(248, 240, 244))
    shirt_v(s, (248, 240, 244), 3)
    lapels(s, shade(magenta, 0.75), depth=4)
    s.on(BODY, "front", 2, 4, (250, 210, 80), 0)  # la broche
    s.on(BODY, "front", 2, 5, (236, 79, 181), 0)
    belt(s, plum)


def return_(s):
    skin = (200, 150, 110)
    hair = (60, 40, 28)
    head(s, skin, hair, "short")
    mustache(s, hair)
    red = (190, 46, 40)
    gold = (236, 190, 70)
    outfit(s, red, trousers=(28, 28, 32), shoes=(16, 16, 18), skin=skin, cuff=gold)
    cap(s, red, band=gold, rows=3)
    s.fill(BODY, "front", 3, 0, 5, 2, (240, 240, 240), 2)
    for y in (3, 5, 7, 9):
        s.on(BODY, "front", 2, y, gold, 1)
        s.on(BODY, "front", 5, y, gold, 1)
    s.fill(ARM, "top", 0, 0, 4, 4, gold, 2)
    s.ring(ARM, 0, 1, gold, 2)
    s.column(LEG, "right", 1, 0, 10, red)
    s.on(BODY, "front", 6, 3, (230, 230, 230), 0)  # le sifflet


def daily(s):
    skin = (236, 190, 150)
    hair = (232, 200, 110)
    head(s, skin, hair, "bob", eyes=(56, 110, 190))
    lips(s, (200, 90, 96))
    yellow = (240, 200, 40)
    navy = (40, 48, 80)
    outfit(s, yellow, trousers=navy, shoes=navy, skin=skin, cuff=(250, 250, 250))
    pillbox(s, yellow, navy)
    # le foulard bleu noue au cou
    scarf = (54, 120, 220)
    s.fill(BODY, "front", 2, 0, 6, 2, scarf, 2)
    s.fill(BODY, "front", 4, 2, 6, 4, shade(scarf, 0.85), 2)
    s.fill(BODY, "back", 2, 0, 6, 1, scarf, 2)
    s.fill(BODY, "front", 1, 4, 3, 5, (250, 250, 250), 0)  # le badge
    buttons(s, navy, rows=(6, 8))
    belt(s, navy)


def portals(s):
    skin = (170, 130, 110)
    head(s, skin, (40, 30, 30), "none", eyes=(199, 125, 255), brows=False)
    s.fill(HEAD, "front", 0, 6, 8, 8, shade(skin, 0.85), 2)
    purple = (100, 50, 150)
    gold = (220, 176, 70)
    outfit(s, purple, trousers=purple, shoes=(40, 26, 50), skin=skin)
    robe(s, purple, trim=gold, sash=(70, 40, 30))
    hood(s, shade(purple, 0.9), trim=gold)
    s.fill(BODY, "front", 2, 1, 6, 3, gold, 2)  # le fermoir


def mine(s):
    skin = (206, 150, 108)
    hair = (160, 80, 40)
    head(s, skin, hair, "short", eyes=(70, 90, 110))
    beard(s, hair)
    outfit(s, (110, 112, 118), trousers=(52, 76, 120), shoes=(96, 60, 32), skin=skin, rolled=False)
    hard_hat(s, (250, 200, 40))
    vest = (95, 216, 232)
    s.fill(BODY, "front", 0, 1, 3, 11, vest, 3)
    s.fill(BODY, "front", 5, 1, 8, 11, vest, 3)
    s.fill(BODY, "back", 0, 1, 8, 11, vest, 3)
    s.fill(BODY, "right", 0, 1, 4, 11, vest, 3)
    s.fill(BODY, "left", 0, 1, 4, 11, vest, 3)
    for y in (6, 8):
        s.ring(BODY, y, y + 1, (220, 224, 230), 2)
    s.fill(BODY, "front", 3, 6, 5, 7, (110, 112, 118), 2)
    s.fill(BODY, "front", 3, 8, 5, 9, (110, 112, 118), 2)
    belt(s, (70, 46, 26), buckle=(180, 180, 186))
    s.fill(LEG, "front", 0, 9, 4, 10, (240, 140, 40), 1)  # les lacets


def black_market(s):
    skin = (180, 136, 104)
    head(s, skin, (30, 26, 30), "none", eyes=(230, 200, 80), brows=False)
    mask = (36, 32, 44)
    s.fill(HEAD, "front", 0, 5, 8, 8, mask, 3)
    s.fill(HEAD, "right", 4, 5, 8, 8, mask, 3)
    s.fill(HEAD, "left", 0, 5, 4, 8, mask, 3)
    coat = (60, 40, 80)
    outfit(s, coat, trousers=(40, 40, 46), shoes=(14, 14, 16), skin=(24, 22, 26))
    hood(s, (28, 26, 32), trim=(155, 107, 255))
    buttons(s, (20, 18, 24), rows=(3, 5, 7, 9), col=3)
    s.column(BODY, "front", 4, 0, 12, shade(coat, 0.7))
    belt(s, (24, 22, 26), buckle=(155, 107, 255))
    s.ring(ARM, 9, 10, shade(coat, 0.7), 2)


def job_altar(s):
    skin = (226, 180, 140)
    hair = (236, 232, 226)
    head(s, skin, hair, "long", eyes=(190, 120, 40))
    orange = (230, 120, 40)
    cream = (246, 238, 220)
    outfit(s, orange, trousers=orange, shoes=(120, 80, 46), skin=skin)
    robe(s, orange, trim=(250, 200, 80), sash=(150, 60, 20))
    # l'etole blanche
    s.column(BODY, "front", 2, 0, 12, cream)
    s.column(BODY, "front", 5, 0, 12, cream)
    s.column(LEG, "front", 1, 0, 9, cream)
    circlet(s, (240, 196, 80), (255, 138, 61))


def shuttle(s):
    skin = (200, 156, 120)
    hair = (46, 34, 26)
    head(s, skin, hair, "short")
    suit = (240, 140, 50)
    outfit(s, suit, trousers=suit, shoes=(24, 24, 28), skin=skin)
    helmet(s, (236, 236, 240), (40, 60, 110), (232, 116, 44))
    s.column(BODY, "front", 4, 0, 12, (150, 150, 156))  # la fermeture
    s.fill(BODY, "front", 1, 3, 3, 5, (54, 110, 200), 1)  # l'ecusson
    s.on(BODY, "front", 1, 3, (250, 250, 250), 0)
    s.fill(BODY, "front", 5, 6, 7, 8, shade(suit, 0.8), 2)
    belt(s, (30, 30, 34), buckle=(190, 190, 196))
    s.fill(ARM, "right", 1, 2, 3, 4, (250, 250, 250), 1)
    s.fill(LEG, "right", 0, 4, 4, 6, shade(suit, 0.8), 2)
    s.ring(LEG, 8, 10, (30, 30, 34), 2)


def kits(s):
    skin = (220, 170, 130)
    hair = (70, 44, 30)
    head(s, skin, hair, "short", eyes=(60, 90, 60))
    white = (236, 236, 240)
    red = (239, 57, 38)
    outfit(s, white, trousers=(50, 52, 58), shoes=(16, 16, 18), skin=skin, cuff=red)
    beret(s, red)
    lapels(s, red, depth=5)
    s.fill(BODY, "front", 3, 0, 5, 1, (40, 40, 44), 2)
    buttons(s, (236, 190, 70), rows=(5, 7, 9), col=3)
    buttons(s, (236, 190, 70), rows=(5, 7, 9), col=4)
    s.ring(ARM, 2, 4, red, 2)
    belt(s, (40, 40, 44), buckle=(236, 190, 70))
    s.column(LEG, "right", 1, 0, 10, red)


NPCS = [
    ("shop", shop), ("boutique", boutique), ("market", market), ("bank", bank), ("contracts", contracts),
    ("museum", museum), ("return", return_), ("daily", daily), ("portals", portals), ("mine", mine),
    ("black_market", black_market), ("job_altar", job_altar), ("shuttle", shuttle), ("kits", kits),
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
    sheet = Image.new("RGBA", (cell_w * 7, cell_h * 2), (40, 42, 48, 255))
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

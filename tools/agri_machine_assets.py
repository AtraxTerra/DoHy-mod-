#!/usr/bin/env python3
"""Les grandes machines de l'agriculture, a la maniere de Create.

    python3 -I tools/agri_machine_assets.py            ecrit les assets et le code genere
    python3 -I tools/agri_machine_assets.py --check    verifie qu'ils sont a jour (CI)
    python3 -I tools/agri_machine_assets.py --preview DOSSIER
                                                       ecrit aussi des apercus (textures, machines)

Six machines de la ferme quittent leur bloc pour une vraie construction : le silo (3 x 5 x 3),
le moulin (3 x 4 x 3), la machine a pain (3 x 4 x 3), le biogenerateur (3 x 3 x 3), la machine
a OGM (3 x 3 x 3) et la pompe a carburant (2 x 3 x 2). Le dessin est propre au mod, dans
l'esprit de Create : un socle de pierre d'andesite cerclee de bois, du laiton, du cuivre, des
planches, des engrenages et des arbres de transmission qui tournent quand la machine travaille.

Chaque machine est decrite ici en boites (pixels, 16 par bloc), dans son repere : x vers la
droite vue de face, y vers le haut, z vers l'arriere ; la face avant regarde le nord. Le script
en tire :

  - un modele de bloc par case occupee : les boites coupees a la case, les faces interieures
    retirees, les textures calees sur la grille des blocs (uv automatiques) ;
  - les etats des blocs : le bloc de commande (la machine elle-meme, propriete formed) et le bloc
    de partie de chaque machine (proprietes x, y, z, facing), tournes selon facing ;
  - le modele d'objet : la machine entiere en miniature ;
  - les pieces mobiles (engrenages, meule, volant, fouet) en modele d'entite, avec leur texture,
    pour le rendu de l'entite de bloc (BigMachineRenderer) ;
  - le code Java des formes : les cases occupees et la boite de chaque case (BigMachineShapes),
    et la geometrie des pieces mobiles (BigMachineGeometry).

Les textures sont dessinees ici, pixel par pixel : rien n'est copie de Create.
"""

import hashlib
import io
import json
import math
import os
import random
import sys

try:
    import numpy as np
except ImportError:  # l'apercu seul en a besoin
    np = None

from PIL import Image, ImageDraw

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "dohy")
JAVA = os.path.join(ROOT, "src", "main", "java", "fr", "atraxterra", "dohy")

TEX_DIR = "block/machine"  # sous textures/
MODEL_DIR = "block/machine"  # sous models/

# --------------------------------------------------------------------------------------------
# Les textures : 16 x 16, dessinees ici
# --------------------------------------------------------------------------------------------


def rgb(hex_color):
    hex_color = hex_color.lstrip("#")
    return tuple(int(hex_color[i:i + 2], 16) for i in (0, 2, 4))


def shade(color, f):
    return tuple(max(0, min(255, int(round(c * f)))) for c in color[:3])


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


class Tex:
    def __init__(self, name, seed=None):
        self.img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        self.rng = random.Random(int(hashlib.md5((seed or name).encode()).hexdigest()[:8], 16))

    def px(self, x, y, color, jitter=0, alpha=255):
        j = self.rng.randint(-jitter, jitter) if jitter else 0
        self.img.putpixel((x % 16, y % 16), tuple(max(0, min(255, c + j)) for c in color[:3]) + (alpha,))

    def rect(self, x0, y0, x1, y1, color, jitter=0):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.px(x, y, color, jitter)

    def frame(self, x0, y0, x1, y1, light, dark):
        """Un cadre en relief : clair en haut et a gauche, sombre en bas et a droite."""
        for x in range(x0, x1):
            self.px(x, y0, light)
            self.px(x, y1 - 1, dark)
        for y in range(y0, y1):
            self.px(x0, y, light)
            self.px(x1 - 1, y, dark)

    def noise(self, palette, weights=None, jitter=0):
        for y in range(16):
            for x in range(16):
                self.px(x, y, self.rng.choices(palette, weights=weights)[0], jitter)


def andesite_stone(t):
    """La pierre d'andesite : un gris vert, mouchete, quelques eclats."""
    t.noise([rgb("8b8d85"), rgb("979990"), rgb("7f817a"), rgb("a3a59b")], [4, 3, 2, 1], 3)
    for _ in range(5):
        x, y = t.rng.randrange(16), t.rng.randrange(16)
        t.px(x, y, rgb("b4b6ac"))
        t.px(x + 1, y, rgb("6f716a"))


def wood_frame(t, light=rgb("9a7448"), mid=rgb("7d5c38"), dark=rgb("5a4026")):
    """Un cadre de bois de deux pixels, chevilles aux coins, fil du bois en long."""
    for i in range(16):
        for k in (0, 1):
            t.px(i, k, light if k == 0 else mid, 3)
            t.px(i, 15 - k, dark if k == 0 else mid, 3)
            t.px(k, i, light if k == 0 else mid, 3)
            t.px(15 - k, i, dark if k == 0 else mid, 3)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        t.px(x, y, rgb("c9a46a"))
    # le joint entre le bois et la pierre
    for i in range(2, 14):
        t.px(i, 2, shade(rgb("8b8d85"), 0.7))
        t.px(2, i, shade(rgb("8b8d85"), 0.75))


def tex_andesite_casing():
    t = Tex("andesite_casing")
    andesite_stone(t)
    wood_frame(t)
    return t.img


def tex_andesite_plain():
    t = Tex("andesite_plain")
    andesite_stone(t)
    return t.img


def brass_plate(t, rivets=True):
    t.noise([rgb("d8a23e"), rgb("e2ae48"), rgb("cf9836")], [4, 2, 2], 2)
    # le poli : une diagonale claire
    for i in range(16):
        t.px(i, 15 - i, rgb("f2cd6b"), 2)
        if i < 15:
            t.px(i + 1, 15 - i, rgb("eabf5a"), 2)
    t.frame(0, 0, 16, 16, rgb("f6da86"), rgb("9c6c22"))
    if rivets:
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            t.px(x, y, rgb("fff0b8"))
            t.px(x + 1, y + 1, rgb("8a5c1c"))


def tex_brass():
    t = Tex("brass")
    brass_plate(t)
    return t.img


def tex_brass_band():
    """Le cerclage de laiton : sans rivets, le poli en long."""
    t = Tex("brass_band")
    t.noise([rgb("d8a23e"), rgb("dfaa45")], [3, 2], 2)
    for x in range(16):
        t.px(x, 0, rgb("f6da86"))
        t.px(x, 1, rgb("eabf5a"))
        t.px(x, 15, rgb("9c6c22"))
        t.px(x, 14, rgb("b98431"))
    for x in range(0, 16, 8):
        t.px(x + 3, 7, rgb("fff0b8"))
        t.px(x + 4, 8, rgb("8a5c1c"))
    return t.img


def tex_copper():
    """Une plaque de cuivre : orange chaud, quelques taches de vert-de-gris."""
    t = Tex("copper")
    t.noise([rgb("c46b3c"), rgb("cf7746"), rgb("b8623a")], [4, 2, 2], 3)
    t.frame(0, 0, 16, 16, rgb("e8955c"), rgb("8e4a2a"))
    for _ in range(4):
        x, y = t.rng.randrange(2, 14), t.rng.randrange(2, 14)
        t.px(x, y, rgb("6fb59f"))
        t.px(x + 1, y, rgb("5a9e88"))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        t.px(x, y, rgb("f0b085"))
    return t.img


def tex_copper_tank():
    """La cuve de cuivre : des viroles rivetees, une couture tous les huit pixels."""
    t = Tex("copper_tank")
    t.noise([rgb("c46b3c"), rgb("cc7442"), rgb("bb6438")], [4, 2, 2], 3)
    for y in (0, 8):
        for x in range(16):
            t.px(x, y, rgb("8e4a2a"))
            t.px(x, y + 1, rgb("e08a55"))
        for x in range(2, 16, 4):
            t.px(x, y + 3, rgb("f0b085"))
    for x in (0, 1):
        for y in range(16):
            t.px(x, y, shade(rgb("c46b3c"), 0.85 + 0.15 * x), 2)
    for _ in range(3):
        x, y = t.rng.randrange(3, 14), t.rng.randrange(3, 14)
        t.px(x, y, rgb("6fb59f"))
    return t.img


def tex_copper_roof():
    """Le toit de cuivre : des bardeaux en ecailles, le vert-de-gris coule par endroits."""
    t = Tex("copper_roof")
    base = [rgb("b9653a"), rgb("c4703f"), rgb("ad5d36")]
    t.noise(base, [3, 2, 2], 3)
    for row in range(4):
        y = row * 4 + 3
        off = 0 if row % 2 == 0 else 2
        for x in range(16):
            t.px(x, y, rgb("7e4024"))
        for x in range(off, 16, 4):
            t.px(x, y - 1, rgb("7e4024"))
            t.px(x, y - 2, rgb("e08a55"))
    for _ in range(7):
        x, y = t.rng.randrange(16), t.rng.randrange(16)
        t.px(x, y, rgb("6fb59f"), 6)
    return t.img


def tex_corrugated():
    """La tole ondulee galvanisee du silo : des cretes verticales tous les deux pixels."""
    t = Tex("corrugated")
    for x in range(16):
        col = [rgb("d3d8db"), rgb("b9bfc3"), rgb("9ea4a9"), rgb("b9bfc3")][x % 4]
        for y in range(16):
            t.px(x, y, col, 3)
    # un joint de tole tous les huit pixels en hauteur
    for x in range(16):
        t.px(x, 7, rgb("8a9095"))
        t.px(x, 8, rgb("e4e8ea"))
    for x in (1, 9):
        t.px(x, 4, rgb("eef1f2"))
        t.px(x, 12, rgb("eef1f2"))
    return t.img


def tex_planks():
    """Des planches d'epicea : trois lames, le fil, les clous."""
    t = Tex("planks")
    lames = [(0, 5), (5, 11), (11, 16)]
    tones = [rgb("7a5a3a"), rgb("84623f"), rgb("6f5134")]
    for (y0, y1), tone in zip(lames, tones):
        for y in range(y0, y1):
            for x in range(16):
                t.px(x, y, tone, 4)
        for x in range(16):
            t.px(x, y1 - 1, shade(tone, 0.72))
        for _ in range(3):
            x = t.rng.randrange(16)
            t.px(x, t.rng.randrange(y0, y1 - 1), shade(tone, 0.86))
        t.px(2, y0 + 1, rgb("4b4b50"))
        t.px(13, y0 + 1, rgb("4b4b50"))
    return t.img


def tex_log():
    """Une poutre equarrie, sombre, au fil vertical."""
    t = Tex("log")
    for x in range(16):
        tone = [rgb("5a4026"), rgb("634629"), rgb("4f3820"), rgb("5e4327")][x % 4]
        for y in range(16):
            t.px(x, y, tone, 4)
    for x in (0, 15):
        for y in range(16):
            t.px(x, y, rgb("3e2c19"))
    for _ in range(4):
        x, y = t.rng.randrange(1, 15), t.rng.randrange(16)
        t.px(x, y, rgb("7a5a3a"))
    return t.img


def tex_log_top():
    t = Tex("log_top")
    t.noise([rgb("8a6a44"), rgb("7d5f3c")], [3, 2], 3)
    for r, tone in ((7, rgb("6b5034")), (5, rgb("9a7a52")), (3, rgb("6b5034")), (1, rgb("9a7a52"))):
        for a in range(0, 360, 10):
            x = int(round(7.5 + r * math.cos(math.radians(a))))
            y = int(round(7.5 + r * math.sin(math.radians(a))))
            t.px(x, y, tone, 2)
    t.frame(0, 0, 16, 16, rgb("5a4026"), rgb("3e2c19"))
    return t.img


def tex_shaft():
    """L'arbre de transmission : un alliage d'andesite, lisse, aux aretes sombres."""
    t = Tex("shaft")
    for x in range(16):
        tone = mix(rgb("a8aa9f"), rgb("c4c6bb"), 1 - abs(x - 6) / 8)
        for y in range(16):
            t.px(x, y, tone, 2)
    for y in range(16):
        t.px(0, y, rgb("6f716a"))
        t.px(15, y, rgb("6f716a"))
    for y in (0, 8):
        for x in range(16):
            t.px(x, y, rgb("8b8d85"))
    return t.img


def tex_iron():
    """Le fer sombre des ferrures et des cercles."""
    t = Tex("iron")
    t.noise([rgb("4a4b51"), rgb("54555c"), rgb("43444a")], [3, 2, 2], 3)
    t.frame(0, 0, 16, 16, rgb("6e7078"), rgb("2e2f34"))
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        t.px(x, y, rgb("8a8c94"))
    return t.img


def tex_bricks():
    """Les briques du four : rouges, joints clairs, en quinconce."""
    t = Tex("bricks")
    mortar = rgb("c9b89f")
    for y in range(16):
        for x in range(16):
            t.px(x, y, mortar, 3)
    for row in range(4):
        y0 = row * 4
        off = 0 if row % 2 == 0 else 4
        for b in range(3):
            x0 = (off + b * 8) % 16
            tone = t.rng.choice([rgb("9a4b35"), rgb("a8543b"), rgb("8e4430")])
            for y in range(y0, y0 + 3):
                for x in range(x0, x0 + 7):
                    t.px(x, y, tone, 5)
            for x in range(x0, x0 + 7):
                t.px(x, y0, shade(tone, 1.15))
    return t.img


def tex_millstone_side():
    """Le flanc de la meule : une pierre claire, les rainures de taille."""
    t = Tex("millstone_side")
    t.noise([rgb("a9a49b"), rgb("b4afa5"), rgb("9b968d")], [3, 2, 2], 3)
    for x in range(0, 16, 4):
        for y in range(16):
            t.px(x, y, rgb("858078"))
    for x in range(16):
        t.px(x, 0, rgb("c8c3b8"))
        t.px(x, 15, rgb("77726a"))
    return t.img


def tex_millstone_top():
    """Le dessus de la meule : les sillons rayonnants, l'oeil au centre."""
    t = Tex("millstone_top")
    t.noise([rgb("aaa59c"), rgb("b6b1a7"), rgb("9e998f")], [3, 2, 2], 3)
    for a in range(0, 360, 45):
        for r in range(2, 9):
            x = int(round(7.5 + r * math.cos(math.radians(a + r * 4))))
            y = int(round(7.5 + r * math.sin(math.radians(a + r * 4))))
            t.px(x, y, rgb("807b73"))
    t.rect(6, 6, 10, 10, rgb("5a5852"))
    t.rect(7, 7, 9, 9, rgb("3c3b37"))
    return t.img


def tex_cog():
    """Le bois d'un engrenage : des lames rayonnantes, un moyeu de laiton."""
    t = Tex("cog")
    t.noise([rgb("8b6b44"), rgb("94744b"), rgb("7f6140")], [3, 2, 2], 3)
    for y in range(16):
        t.px(7, y, rgb("6b5034"))
        t.px(8, y, rgb("a7875a"))
    t.frame(0, 0, 16, 16, rgb("a7875a"), rgb("5a4026"))
    return t.img


def tex_grain():
    """Le grain dans la tremie : de l'or pale, des grains bruns."""
    t = Tex("grain")
    t.noise([rgb("e2c27a"), rgb("d8b66a"), rgb("eccf8b"), rgb("c9a55a")], [4, 3, 2, 2], 4)
    for _ in range(10):
        t.px(t.rng.randrange(16), t.rng.randrange(16), rgb("a8823e"))
    return t.img


def tex_flour():
    """La farine qui sort de la goulotte."""
    t = Tex("flour")
    t.noise([rgb("f2eee4"), rgb("e9e4d7"), rgb("faf7f0")], [4, 3, 2], 2)
    return t.img


def tex_glass():
    """Une vitre cerclee de laiton, transparente au milieu (decoupee)."""
    t = Tex("glass")
    for y in range(16):
        for x in range(16):
            t.px(x, y, rgb("dff3f5"), 0, alpha=0)
    t.frame(0, 0, 16, 16, rgb("f6da86"), rgb("9c6c22"))
    for i in range(3, 7):
        t.px(i, 9 - i, rgb("f4fbfc"), 0, 200)
    t.px(11, 4, rgb("f4fbfc"), 0, 200)
    return t.img


def tex_green_liquid():
    """Le bouillon de la machine a OGM : un vert vif, des bulles."""
    t = Tex("green_liquid")
    t.noise([rgb("4fbf3a"), rgb("5acb44"), rgb("45b032")], [4, 3, 2], 3)
    for _ in range(6):
        x, y = t.rng.randrange(1, 15), t.rng.randrange(1, 15)
        t.px(x, y, rgb("a8f08f"))
        t.px(x + 1, y + 1, rgb("3a9a2a"))
    return t.img


def tex_hatch():
    """Une trappe de bois sombre, cerclee de laiton, sa poignee."""
    t = Tex("hatch")
    for y in range(16):
        for x in range(16):
            t.px(x, y, [rgb("4e3824"), rgb("57402a")][(x // 3) % 2], 3)
    t.frame(0, 0, 16, 16, rgb("f6da86"), rgb("9c6c22"))
    t.frame(1, 1, 15, 15, rgb("d8a23e"), rgb("b98431"))
    t.rect(10, 7, 13, 9, rgb("d8a23e"))
    t.px(10, 7, rgb("fff0b8"))
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        t.px(x, y, rgb("fff0b8"))
    return t.img


def tex_gauge():
    """Un manometre : cadran blanc, aiguille rouge, cerclage de laiton."""
    t = Tex("gauge")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 7.6:
                t.px(x, y, rgb("d8a23e") if d > 6.2 else rgb("f3efe4"))
            else:
                t.px(x, y, rgb("000000"), 0, 0)
    for a in range(200, 341, 35):
        x = int(round(7.5 + 5 * math.cos(math.radians(a))))
        y = int(round(7.5 + 5 * math.sin(math.radians(a))))
        t.px(x, y, rgb("3c3b37"))
    for r in range(0, 5):
        t.px(int(round(7.5 + r * math.cos(math.radians(300)))), int(round(7.5 + r * math.sin(math.radians(300)))),
             rgb("d23a2a"))
    t.px(7, 7, rgb("3c3b37"))
    t.px(8, 8, rgb("3c3b37"))
    return t.img


def tex_fire():
    """La gueule du four, braises et flammes (rendue a pleine lumiere quand il cuit)."""
    t = Tex("fire")
    for y in range(16):
        for x in range(16):
            heat = (y / 15.0) + t.rng.uniform(-0.15, 0.15)
            color = mix(rgb("ffe27a"), rgb("e2531c"), max(0.0, min(1.0, 1.0 - heat)))
            t.px(x, y, color if heat > 0.35 else mix(rgb("e2531c"), rgb("5a1a0c"), 0.5), 6)
    for x in range(16):
        t.px(x, 15, rgb("ffb44a"))
    return t.img


def tex_oven_mouth():
    """La gueule eteinte : du noir de suie."""
    t = Tex("oven_mouth")
    t.noise([rgb("1e1a18"), rgb("26201c"), rgb("171412")], [3, 2, 2], 2)
    for x in range(16):
        t.px(x, 15, rgb("3a2a20"))
    return t.img


def tex_hose():
    """Le tuyau de caoutchouc noir, ses anneaux."""
    t = Tex("hose")
    t.noise([rgb("2a2a2e"), rgb("323237")], [3, 2], 2)
    for y in range(0, 16, 4):
        for x in range(16):
            t.px(x, y, rgb("46464c"))
    return t.img


def tex_sack():
    """Un sac de toile de chanvre, ficele."""
    t = Tex("sack")
    t.noise([rgb("cdb68a"), rgb("c4ad80"), rgb("d6bf93")], [3, 2, 2], 3)
    for y in range(0, 16, 2):
        for x in range((y // 2) % 2, 16, 2):
            t.px(x, y, rgb("b89f72"))
    for x in range(16):
        t.px(x, 3, rgb("8a6a44"))
    return t.img


def tex_bread():
    """La croute d'une miche."""
    t = Tex("bread")
    t.noise([rgb("c8873e"), rgb("d39448"), rgb("b97a34")], [3, 2, 2], 4)
    for x in range(3, 13, 3):
        t.px(x, 6, rgb("f0d39a"))
        t.px(x + 1, 7, rgb("f0d39a"))
    return t.img


def tex_fuel():
    """Le carburant, vu par le hublot."""
    t = Tex("fuel")
    t.noise([rgb("3b2a12"), rgb("4a3416"), rgb("33240f")], [3, 2, 2], 2)
    for x in range(16):
        t.px(x, 0, rgb("8a6a2a"))
    return t.img


TEXTURES = {
    "andesite_casing": tex_andesite_casing,
    "andesite": tex_andesite_plain,
    "brass": tex_brass,
    "brass_band": tex_brass_band,
    "copper": tex_copper,
    "copper_tank": tex_copper_tank,
    "copper_roof": tex_copper_roof,
    "corrugated": tex_corrugated,
    "planks": tex_planks,
    "log": tex_log,
    "log_top": tex_log_top,
    "shaft": tex_shaft,
    "iron": tex_iron,
    "bricks": tex_bricks,
    "millstone_side": tex_millstone_side,
    "millstone_top": tex_millstone_top,
    "cog": tex_cog,
    "grain": tex_grain,
    "flour": tex_flour,
    "glass": tex_glass,
    "green_liquid": tex_green_liquid,
    "hatch": tex_hatch,
    "gauge": tex_gauge,
    "fire": tex_fire,
    "oven_mouth": tex_oven_mouth,
    "hose": tex_hose,
    "sack": tex_sack,
    "bread": tex_bread,
    "fuel": tex_fuel,
}

# Les textures a decouper (transparence) : leur modele prend le rendu cutout.
CUTOUT = {"glass", "gauge"}


def texture_images():
    return {name: painter() for name, painter in TEXTURES.items()}


# --------------------------------------------------------------------------------------------
# Les boites
# --------------------------------------------------------------------------------------------

FACES = ("north", "south", "west", "east", "up", "down")


class Box:
    """Une boite du modele, en pixels du repere de la machine.

    tex : la texture de toutes les faces ; faces : texture par face (None retire la face) ;
    rot : (axe, angle, (ox, oy, oz)), angle de -45 a 45 par pas de 22,5 — une boite tournee
    n'est pas coupee, elle va entiere dans la case de son centre ;
    uv : uv explicites par face, pour un decor qui doit tenir sa texture entiere.
    """

    def __init__(self, x0, y0, z0, x1, y1, z1, tex, faces=None, rot=None, uv=None):
        self.a = (min(x0, x1), min(y0, y1), min(z0, z1))
        self.b = (max(x0, x1), max(y0, y1), max(z0, z1))
        self.tex = tex
        self.faces = faces or {}
        self.rot = rot
        self.uv = uv or {}

    def face_tex(self, face):
        if face in self.faces:
            return self.faces[face]
        return self.tex


def box(x0, y0, z0, x1, y1, z1, tex, **kw):
    return [Box(x0, y0, z0, x1, y1, z1, tex, **kw)]


def octagon(cx, cz, y0, y1, r, cut, tex, **kw):
    """Un prisme a huit pans (approche) : deux boites en croix et une troisieme a mi-chemin."""
    mid = cut // 2
    return [
        Box(cx - r, y0, cz - (r - cut), cx + r, y1, cz + (r - cut), tex, **kw),
        Box(cx - (r - cut), y0, cz - r, cx + (r - cut), y1, cz + r, tex, **kw),
        Box(cx - (r - mid), y0, cz - (r - mid), cx + (r - mid), y1, cz + (r - mid), tex, **kw),
    ]


class Moving:
    """Une piece mobile : des boites qui tournent autour d'un pivot.

    axis : x, y ou z ; speed : degres par tick ; when : 'lit' (quand la machine travaille) ou
    'always' ; glow : rendue a pleine lumiere (la gueule du four) et seulement quand lit.
    """

    def __init__(self, name, pivot, axis, speed, boxes, when="lit", glow=False):
        self.name = name
        self.pivot = pivot
        self.axis = axis
        self.speed = speed
        self.boxes = boxes
        self.when = when
        self.glow = glow


class Machine:
    """Une machine : sa taille en cases, sa case de commande, ses boites, ses pieces mobiles.

    block : l'identifiant du bloc de commande (la machine) ; compact : ses modeles d'un bloc
    (formed=false), par etat ; extra : les autres proprietes du bloc de commande.
    """

    def __init__(self, name, block, size, controller, boxes, moving=(), compact=None, lit=False,
                 particle="andesite_casing", miniature_scale=None):
        self.name = name
        self.block = block
        self.size = size
        self.controller = controller
        self.boxes = boxes
        self.moving = list(moving)
        self.compact = compact
        self.lit = lit
        self.particle = particle
        self.miniature_scale = miniature_scale


# --------------------------------------------------------------------------------------------
# La coupe en cases
# --------------------------------------------------------------------------------------------

def r6(v):
    """Un nombre propre pour le JSON (pas de 3.0000000004)."""
    v = round(v, 4)
    return int(v) if v == int(v) else v


def cut(machine):
    """Les morceaux de chaque case : {(cx, cy, cz): [(from, to, faces, rot), ...]}."""
    W, H, D = machine.size
    cells = {}
    for bx in machine.boxes:
        if bx.rot:
            center = [(bx.a[i] + bx.b[i]) / 2.0 for i in range(3)]
            cell = tuple(int(math.floor(center[i] / 16.0)) for i in range(3))
            origin = [cell[i] * 16 for i in range(3)]
            frm = [bx.a[i] - origin[i] for i in range(3)]
            to = [bx.b[i] - origin[i] for i in range(3)]
            for v in frm + to:
                if v < -16 or v > 32:
                    raise SystemExit(f"{machine.name} : boite tournee hors de portee de sa case {cell}")
            faces = {f: (bx.face_tex(f), bx.uv.get(f)) for f in FACES if bx.face_tex(f)}
            axis, angle, pivot = bx.rot
            rot = {"angle": angle, "axis": axis, "origin": [r6(pivot[i] - origin[i]) for i in range(3)]}
            cells.setdefault(cell, []).append((frm, to, faces, rot))
            continue
        lo = [int(math.floor(bx.a[i] / 16.0)) for i in range(3)]
        hi = [int(math.ceil(bx.b[i] / 16.0)) - 1 for i in range(3)]
        for i in range(3):
            if bx.a[i] == bx.b[i]:
                # une boite plate : elle va dans la case ou tombe son plan
                lo[i] = hi[i] = min(int(math.floor(bx.a[i] / 16.0)), [W, H, D][i] - 1)
        for cx in range(lo[0], hi[0] + 1):
            for cy in range(lo[1], hi[1] + 1):
                for cz in range(lo[2], hi[2] + 1):
                    c0 = (cx * 16, cy * 16, cz * 16)
                    c1 = (c0[0] + 16, c0[1] + 16, c0[2] + 16)
                    a = [max(bx.a[i], c0[i]) for i in range(3)]
                    b = [min(bx.b[i], c1[i]) for i in range(3)]
                    if any(b[i] < a[i] for i in range(3)):
                        continue
                    if any(b[i] == a[i] and bx.a[i] != bx.b[i] for i in range(3)):
                        continue
                    faces = {}
                    exterior = {
                        "west": a[0] == bx.a[0], "east": b[0] == bx.b[0],
                        "down": a[1] == bx.a[1], "up": b[1] == bx.b[1],
                        "north": a[2] == bx.a[2], "south": b[2] == bx.b[2],
                    }
                    for f in FACES:
                        if exterior[f] and bx.face_tex(f):
                            uv = bx.uv.get(f)
                            if uv and (a != list(bx.a) or b != list(bx.b)):
                                raise SystemExit(f"{machine.name} : un decor a uv explicites est coupe ({bx.a} {bx.b})")
                            faces[f] = (bx.face_tex(f), uv)
                    if not faces:
                        continue
                    frm = [a[i] - c0[i] for i in range(3)]
                    to = [b[i] - c0[i] for i in range(3)]
                    cells.setdefault((cx, cy, cz), []).append((frm, to, faces, None))
    for cell in cells:
        for i in range(3):
            if not 0 <= cell[i] < machine.size[i]:
                raise SystemExit(f"{machine.name} : une boite deborde de la machine (case {cell})")
    return cells


def cell_model(machine, pieces):
    textures = {}
    elements = []
    cutout = False
    for frm, to, faces, rot in pieces:
        el = {"from": [r6(v) for v in frm], "to": [r6(v) for v in to], "faces": {}}
        if rot:
            el["rotation"] = rot
        for f in FACES:
            if f not in faces:
                continue
            key, uv = faces[f]
            textures[key] = f"dohy:{TEX_DIR}/{key}"
            face = {"texture": "#" + key}
            if uv:
                face["uv"] = uv
            el["faces"][f] = face
            if key in CUTOUT:
                cutout = True
        elements.append(el)
    textures["particle"] = f"dohy:{TEX_DIR}/{machine.particle}"
    model = {"textures": dict(sorted(textures.items())), "elements": elements}
    if cutout:
        model = {"render_type": "minecraft:cutout", **model}
    return model


def cell_shape(pieces):
    """La boite de collision d'une case : l'enveloppe de ses morceaux, bornee a la case."""
    lo = [16.0, 16.0, 16.0]
    hi = [0.0, 0.0, 0.0]
    for frm, to, _, _ in pieces:
        for i in range(3):
            lo[i] = min(lo[i], max(0.0, frm[i]))
            hi[i] = max(hi[i], min(16.0, to[i]))
    for i in range(3):
        if hi[i] - lo[i] < 2.0:
            # une case presque vide garde une prise : deux pixels au moins
            mid = (lo[i] + hi[i]) / 2.0
            lo[i], hi[i] = max(0.0, mid - 1.0), min(16.0, mid + 1.0)
    return [r6(v) for v in lo] + [r6(v) for v in hi]


# --------------------------------------------------------------------------------------------
# Les etats, les modeles d'objet
# --------------------------------------------------------------------------------------------

FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}
PART_RANGE = (3, 5, 3)  # x, y, z : les proprietes des blocs de partie


def part_blockstate(machine, cells):
    variants = {}
    cx, cy, cz = machine.controller
    for facing, yrot in FACING_Y.items():
        for x in range(PART_RANGE[0]):
            for y in range(PART_RANGE[1]):
                for z in range(PART_RANGE[2]):
                    key = f"facing={facing},x={x},y={y},z={z}"
                    if (x, y, z) in cells and (x, y, z) != (cx, cy, cz):
                        v = {"model": f"dohy:{MODEL_DIR}/{machine.name}/{x}_{y}_{z}"}
                        if yrot:
                            v["y"] = yrot
                    else:
                        v = {"model": f"dohy:{MODEL_DIR}/empty"}
                    variants[key] = v
    return {"variants": variants}


def controller_blockstate(machine):
    """Le bloc de commande : formed=false garde le modele d'un bloc, formed=true sa case."""
    variants = {}
    cx, cy, cz = machine.controller
    cell = f"dohy:{MODEL_DIR}/{machine.name}/{cx}_{cy}_{cz}"
    lits = (False, True) if machine.lit else (None,)
    for facing, yrot in FACING_Y.items():
        for lit in lits:
            for formed in (False, True):
                parts = [f"facing={facing}", f"formed={'true' if formed else 'false'}"]
                if lit is not None:
                    parts.insert(1, f"lit={'true' if lit else 'false'}")
                if formed:
                    v = {"model": cell}
                else:
                    v = {"model": machine.compact[1] if lit else machine.compact[0]}
                if yrot:
                    v["y"] = yrot
                variants[",".join(parts)] = v
    return {"variants": dict(sorted(variants.items()))}


def miniature(machine):
    """L'objet : la machine entiere, reduite pour tenir dans un bloc et demi."""
    W, H, D = machine.size
    span = max(W, H, D) * 16
    scale = machine.miniature_scale or (24.0 / span)
    ox = 8 - W * 16 * scale / 2.0
    oz = 8 - D * 16 * scale / 2.0
    oy = 0.0
    textures = {}
    elements = []
    for bx in machine.boxes:
        frm = [ox + bx.a[0] * scale, oy + bx.a[1] * scale, oz + bx.a[2] * scale]
        to = [ox + bx.b[0] * scale, oy + bx.b[1] * scale, oz + bx.b[2] * scale]
        el = {"from": [r6(v) for v in frm], "to": [r6(v) for v in to], "faces": {}}
        if bx.rot:
            axis, angle, pivot = bx.rot
            el["rotation"] = {"angle": angle, "axis": axis,
                              "origin": [r6(ox + pivot[0] * scale), r6(oy + pivot[1] * scale),
                                         r6(oz + pivot[2] * scale)]}
        for f in FACES:
            key = bx.face_tex(f)
            if not key:
                continue
            textures[key] = f"dohy:{TEX_DIR}/{key}"
            el["faces"][f] = {"texture": "#" + key}
        elements.append(el)
    for mv in machine.moving:
        if mv.glow:
            continue
        for x0, y0, z0, x1, y1, z1, key in mv.boxes:
            textures[key] = f"dohy:{TEX_DIR}/{key}"
            el = {"from": [r6(ox + x0 * scale), r6(oy + y0 * scale), r6(oz + z0 * scale)],
                  "to": [r6(ox + x1 * scale), r6(oy + y1 * scale), r6(oz + z1 * scale)],
                  "faces": {f: {"texture": "#" + key} for f in FACES}}
            elements.append(el)
    textures["particle"] = f"dohy:{TEX_DIR}/{machine.particle}"
    cutout = any(k in CUTOUT for k in textures)
    model = {"textures": dict(sorted(textures.items())), "elements": elements,
             "display": {
                 "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
                 "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
                 "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
                 "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                           "scale": [0.375, 0.375, 0.375]},
                 "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0],
                                           "scale": [0.4, 0.4, 0.4]},
                 "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0],
                                          "scale": [0.4, 0.4, 0.4]}}}
    if cutout:
        model = {"render_type": "minecraft:cutout", **model}
    return model


# --------------------------------------------------------------------------------------------
# Les pieces mobiles : texture d'entite et geometrie Java
# --------------------------------------------------------------------------------------------

def pack(machine, tex_images):
    """Range les boites des pieces mobiles sur une texture (uv de boite de Minecraft)."""
    W = 128
    x = y = row = 0
    placed = []
    for mv in machine.moving:
        for bx in mv.boxes:
            w, h, d = (int(round(bx[3] - bx[0])), int(round(bx[4] - bx[1])), int(round(bx[5] - bx[2])))
            need_w, need_h = 2 * d + 2 * w, d + h
            if need_w > W:
                raise SystemExit(f"{machine.name} : boite trop large pour la texture ({need_w})")
            if x + need_w > W:
                x, y, row = 0, y + row, 0
            placed.append((mv, bx, x, y, w, h, d))
            x += need_w
            row = max(row, need_h)
    height = 16
    while height < y + row:
        height *= 2
    atlas = Image.new("RGBA", (W, height), (0, 0, 0, 0))
    for mv, bx, u, v, w, h, d in placed:
        tile = tex_images[bx[6]]
        regions = [(u + d, v, w, d), (u + d + w, v, w, d), (u, v + d, d, h), (u + d, v + d, w, h),
                   (u + d + w, v + d, d, h), (u + 2 * d + w, v + d, w, h)]
        for rx, ry, rw, rh in regions:
            for py in range(rh):
                for pxl in range(rw):
                    atlas.putpixel((rx + pxl, ry + py), tile.getpixel((pxl % 16, py % 16)))
    return atlas, placed


def java_name(name):
    return "".join(p.capitalize() for p in name.split("_"))


def geometry_java(machines, packs):
    out = []
    out.append("package fr.atraxterra.dohy.client.agri.machine;")
    out.append("")
    out.append("import net.minecraft.client.model.geom.ModelLayerLocation;")
    out.append("import net.minecraft.client.model.geom.PartPose;")
    out.append("import net.minecraft.client.model.geom.builders.CubeListBuilder;")
    out.append("import net.minecraft.client.model.geom.builders.LayerDefinition;")
    out.append("import net.minecraft.client.model.geom.builders.MeshDefinition;")
    out.append("import net.minecraft.client.model.geom.builders.PartDefinition;")
    out.append("import net.minecraft.resources.Identifier;")
    out.append("")
    out.append("/**")
    out.append(" * GENERE par tools/agri_machine_assets.py. Ne pas modifier a la main : relancer le script.")
    out.append(" *")
    out.append(" * <p>Les pieces mobiles des grandes machines de l'agriculture, en pixels du repere de la")
    out.append(" * machine (y vers le haut, la face avant au nord), chacune autour de son pivot.")
    out.append(" */")
    out.append("@SuppressWarnings(\"unused\")")
    out.append("public final class BigMachineGeometry {")
    out.append("")
    out.append("    /** Une piece mobile : son nom dans le modele, son axe, sa vitesse (degres par tick). */")
    out.append("    public record Spin(String part, char axis, float speed, boolean always, boolean glow) {")
    out.append("    }")
    out.append("")
    for m in machines:
        if not m.moving:
            continue
        jn = m.name.upper()
        out.append(f"    public static final ModelLayerLocation {jn} = new ModelLayerLocation(")
        out.append(f"            Identifier.fromNamespaceAndPath(\"dohy\", \"machine_{m.name}\"), \"main\");")
        spins = ", ".join(
            f"new Spin(\"{mv.name}\", '{mv.axis}', {mv.speed}F, {'true' if mv.when == 'always' else 'false'}, "
            f"{'true' if mv.glow else 'false'})" for mv in m.moving)
        out.append(f"    public static final Spin[] {jn}_SPINS = {{{spins}}};")
        out.append("")
    out.append("    private BigMachineGeometry() {")
    out.append("    }")
    for m in machines:
        if not m.moving:
            continue
        atlas, placed = packs[m.name]
        out.append("")
        out.append(f"    public static LayerDefinition create{java_name(m.name)}() {{")
        out.append("        MeshDefinition mesh = new MeshDefinition();")
        out.append("        PartDefinition root = mesh.getRoot();")
        for mv in m.moving:
            px, py, pz = mv.pivot
            cubes = [p for p in placed if p[0] is mv]
            out.append(f"        root.addOrReplaceChild(\"{mv.name}\", CubeListBuilder.create()")
            for _, bx, u, v, w, h, d in cubes:
                out.append(f"                .texOffs({u}, {v}).addBox({r6(bx[0] - px)}F, {r6(bx[1] - py)}F, "
                           f"{r6(bx[2] - pz)}F, {w}F, {h}F, {d}F)")
            out.append(f"                , PartPose.offset({r6(px)}F, {r6(py)}F, {r6(pz)}F));")
        out.append(f"        return LayerDefinition.create(mesh, {atlas.width}, {atlas.height});")
        out.append("    }")
    out.append("}")
    return "\n".join(out) + "\n"


def shapes_java(machines, all_cells):
    out = []
    out.append("package fr.atraxterra.dohy.agri.machine;")
    out.append("")
    out.append("/**")
    out.append(" * GENERE par tools/agri_machine_assets.py. Ne pas modifier a la main : relancer le script.")
    out.append(" *")
    out.append(" * <p>Les cases des grandes machines de l'agriculture, dans le repere de la machine (x vers la")
    out.append(" * droite vue de face, y vers le haut, z vers l'arriere, la face avant au nord) : pour chacune,")
    out.append(" * x, y, z de la case puis sa boite de collision en pixels (x0, y0, z0, x1, y1, z1).")
    out.append(" */")
    out.append("public final class BigMachineShapes {")
    out.append("")
    out.append("    private BigMachineShapes() {")
    out.append("    }")
    for m in machines:
        cells = all_cells[m.name]
        jn = m.name.upper()
        W, H, D = m.size
        cx, cy, cz = m.controller
        out.append("")
        out.append(f"    /** {m.name} : {W} x {H} x {D} cases, la commande en ({cx}, {cy}, {cz}). */")
        out.append(f"    public static final int[] {jn}_SIZE = {{{W}, {H}, {D}, {cx}, {cy}, {cz}}};")
        out.append(f"    public static final double[][] {jn}_CELLS = {{")
        for cell in sorted(cells):
            shape = cell_shape(cells[cell])
            vals = ", ".join(str(v) for v in list(cell) + shape)
            out.append(f"            {{{vals}}},")
        out.append("    };")
    out.append("}")
    return "\n".join(out) + "\n"


# --------------------------------------------------------------------------------------------
# Les machines
# --------------------------------------------------------------------------------------------

def silo():
    """Le silo : une tour de tole ondulee cerclee de laiton, toit de cuivre a gradins, echelle,
    goulotte de remplissage et trappe de visite ; une girouette d'aeration tourne au sommet."""
    b = []
    b += octagon(24, 24, 0, 6, 23, 7, "andesite_casing")
    b += octagon(24, 24, 6, 62, 21, 6, "corrugated", faces={"up": "copper_roof", "down": None})
    for y in (14, 30, 46):
        b += octagon(24, 24, y, y + 2, 22, 6, "brass_band")
    b += octagon(24, 24, 62, 66, 22, 6, "copper_roof")
    b += octagon(24, 24, 66, 70, 17, 5, "copper_roof")
    b += octagon(24, 24, 70, 74, 12, 4, "copper_roof")
    b += octagon(24, 24, 74, 77, 7, 2, "copper_roof")
    # la trappe de visite, sur le socle
    b += box(19, 6, 2, 29, 16, 3, "brass", faces={"north": "hatch"}, uv={"north": [0, 0, 16, 16]})
    # l'echelle, a droite
    b += box(45, 6, 19, 47, 62, 20, "iron")
    b += box(45, 6, 28, 47, 62, 29, "iron")
    for y in range(9, 62, 4):
        b += box(45, y, 20, 46, y + 1, 28, "iron")
    # la goulotte de remplissage, a gauche : du sol au toit
    b += box(0, 6, 9, 5, 66, 14, "copper")
    for y in (6, 22, 38, 54):
        b += box(0, y, 8, 6, y + 2, 15, "brass_band")
    b += box(0, 66, 9, 12, 71, 14, "copper")
    b += box(0, 62, 9, 5, 66, 14, "copper")
    vent = Moving("vent", (24, 77, 24), "y", 2.0, [
        (21, 77, 21, 27, 80, 27, "brass"),
        (16, 78, 23, 32, 79, 25, "iron"),
        (23, 78, 16, 25, 79, 32, "iron"),
    ], when="always")
    return Machine("silo", "silo", (3, 5, 3), (1, 0, 1), b, [vent],
                   compact=("dohy:block/silo", "dohy:block/silo"), particle="corrugated")


def flour_mill():
    """Le moulin : un socle d'andesite, la meule dormante et la meule tournante, l'arbre et le
    grand engrenage dans un bati de poutres, la tremie a grain en haut, le moteur de laiton."""
    b = []
    b += box(1, 0, 1, 47, 12, 47, "andesite_casing")
    b += box(0, 12, 0, 48, 14, 48, "brass_band")
    b += box(19, 1, 0, 29, 12, 1, "brass", faces={"north": "hatch"}, uv={"north": [0, 0, 16, 16]})
    b += box(21, 2, 0, 27, 4, 1, "flour")
    # la meule dormante
    b += octagon(24, 24, 14, 20, 19, 6, "millstone_side", faces={"up": "millstone_top"})
    # le bati
    for x0, z0 in ((2, 2), (42, 2), (2, 42), (42, 42)):
        b += box(x0, 14, z0, x0 + 4, 58, z0 + 4, "log", faces={"up": "log_top", "down": "log_top"})
    b += box(6, 54, 2, 42, 58, 6, "log")
    b += box(6, 54, 42, 42, 58, 46, "log")
    b += box(2, 54, 6, 6, 58, 42, "log")
    b += box(42, 54, 6, 46, 58, 42, "log")
    b += box(6, 50, 21, 42, 54, 27, "log")
    b += box(20, 49, 20, 28, 55, 28, "brass")
    # la tremie
    b += box(14, 58, 14, 34, 60, 34, "planks")
    b += box(11, 60, 11, 37, 62, 37, "planks")
    b += box(8, 62, 8, 40, 64, 40, "planks", faces={"up": "grain"})
    # le moteur, sur le socle a droite
    b += box(37, 14, 16, 46, 26, 32, "brass")
    b += box(38, 26, 18, 45, 29, 30, "copper")
    b += box(34, 18, 22, 37, 22, 26, "shaft")
    # des sacs de farine
    b += box(7, 14, 3, 14, 22, 9, "sack")
    b += box(8, 22, 4, 13, 24, 8, "sack")
    b += box(7, 14, 10, 13, 20, 15, "sack")
    runner = Moving("runner", (24, 20, 24), "y", 4.0, [
        (6, 20, 11, 42, 27, 37, "millstone_side"),
        (11, 20, 6, 37, 27, 42, "millstone_side"),
        (20, 27, 20, 28, 29, 28, "iron"),
        (22, 29, 22, 26, 50, 26, "shaft"),
        (12, 44, 16, 36, 48, 32, "cog"),
        (16, 44, 12, 32, 48, 36, "cog"),
        (9, 44, 22, 12, 48, 26, "cog"),
        (36, 44, 22, 39, 48, 26, "cog"),
        (22, 44, 9, 26, 48, 12, "cog"),
        (22, 44, 36, 26, 48, 39, "cog"),
        (21, 43, 21, 27, 49, 27, "brass"),
    ])
    return Machine("flour_mill", "flour_mill", (3, 4, 3), (1, 0, 1), b, [runner],
                   compact=("dohy:block/flour_mill", "dohy:block/flour_mill_on"), lit=True)


def bread_machine():
    """La machine a pain : un grand four de briques a voute, gueule cerclee de laiton qui rougeoie
    quand il cuit, cheminee de cuivre, thermometre, et le petrin dont la roue tourne au flanc."""
    b = []
    b += box(1, 0, 1, 47, 8, 47, "andesite_casing")
    b += box(3, 8, 5, 45, 34, 45, "bricks")
    b += box(6, 34, 8, 42, 38, 42, "bricks")
    b += box(10, 38, 12, 38, 41, 38, "bricks")
    b += box(15, 41, 17, 33, 43, 33, "bricks")
    # la gueule
    b += box(16, 12, 4, 32, 26, 5, "oven_mouth")
    b += box(15, 26, 3, 33, 28, 5, "brass")
    b += box(14, 10, 3, 16, 28, 5, "brass")
    b += box(32, 10, 3, 34, 28, 5, "brass")
    b += box(14, 10, 2, 34, 12, 5, "iron")
    # la cheminee
    b += box(34, 30, 34, 42, 58, 42, "copper")
    b += box(33, 56, 33, 43, 58, 43, "brass_band")
    b += box(35, 58, 35, 41, 62, 41, "iron")
    b += box(33, 62, 33, 43, 64, 43, "iron")
    # le thermometre
    b += box(36, 18, 4, 42, 24, 5, "brass", faces={"north": "gauge"}, uv={"north": [0, 0, 16, 16]})
    # des miches sur le socle
    b += box(3, 8, 1, 8, 11, 4, "bread")
    b += box(9, 8, 1, 13, 11, 4, "bread")
    b += box(3, 11, 1, 7, 13, 4, "bread")
    fire = Moving("fire", (24, 16, 3), "y", 0.0, [(17, 13, 3, 31, 25, 4, "fire")], glow=True)
    wheel = Moving("wheel", (46, 20, 25), "x", 6.0, [
        (45, 14, 22, 47, 26, 28, "cog"),
        (45, 17, 19, 47, 23, 31, "cog"),
        (45, 12, 24, 47, 28, 26, "cog"),
        (45, 20, 17, 47, 22, 33, "cog"),
        (44, 18, 23, 48, 22, 27, "brass"),
    ])
    return Machine("bread_machine", "bread_machine", (3, 4, 3), (1, 0, 1), b, [fire, wheel],
                   compact=("dohy:block/bread_machine", "dohy:block/bread_machine_on"), lit=True,
                   particle="bricks")


def biogenerator():
    """Le biogenerateur : la cuve de digestion en cuivre cerclee de laiton, sa tremie a fourrage,
    la conduite de gaz vers le groupe electrogene, son manometre et son volant qui tourne."""
    b = []
    b += box(1, 0, 1, 47, 6, 47, "andesite_casing")
    b += octagon(20, 28, 6, 40, 16, 5, "copper_tank", faces={"up": "copper", "down": None})
    for y in (14, 30):
        b += octagon(20, 28, y, y + 2, 17, 5, "brass_band")
    b += octagon(20, 28, 40, 44, 12, 4, "copper")
    b += box(18, 44, 26, 22, 47, 30, "brass")
    # la conduite de gaz
    b += box(22, 41, 26, 40, 44, 29, "copper")
    b += box(37, 28, 26, 40, 41, 29, "copper")
    # le groupe electrogene
    b += box(30, 6, 4, 46, 28, 22, "andesite_casing")
    b += box(29, 28, 3, 47, 30, 23, "brass_band")
    b += box(34, 16, 3, 42, 24, 4, "brass", faces={"north": "gauge"}, uv={"north": [0, 0, 16, 16]})
    # la tremie a fourrage et sa goulotte
    b += box(4, 30, 6, 14, 36, 14, "planks", faces={"up": "grain"})
    b += box(7, 18, 9, 11, 30, 13, "copper")
    flywheel = Moving("flywheel", (47, 17, 13), "x", 8.0, [
        (45, 15, 11, 48, 19, 15, "brass"),
        (46, 9, 12, 48, 25, 14, "iron"),
        (46, 16, 5, 48, 18, 21, "iron"),
        (46, 24, 8, 48, 26, 18, "iron"),
        (46, 8, 8, 48, 10, 18, "iron"),
        (46, 11, 4, 48, 23, 6, "iron"),
        (46, 11, 20, 48, 23, 22, "iron"),
    ])
    return Machine("biogenerator", "biogenerator", (3, 3, 3), (1, 0, 1), b, [flywheel],
                   compact=("dohy:block/biogenerator", "dohy:block/biogenerator_on"), lit=True,
                   particle="copper_tank")


def gmo_machine():
    """La machine a OGM : une cuve vitree de bouillon vert sous un portique d'andesite, le
    malaxeur de laiton dont le fouet tourne sans cesse, le pupitre et ses eprouvettes."""
    b = []
    b += box(1, 0, 1, 47, 8, 47, "andesite_casing")
    b += box(10, 8, 10, 38, 24, 38, "green_liquid", faces={"down": None})
    b += box(9, 8, 9, 39, 30, 10, "glass")
    b += box(9, 8, 38, 39, 30, 39, "glass")
    b += box(9, 8, 10, 10, 30, 38, "glass")
    b += box(38, 8, 10, 39, 30, 38, "glass")
    for x0, z0 in ((8, 8), (38, 8), (8, 38), (38, 38)):
        b += box(x0, 8, z0, x0 + 2, 31, z0 + 2, "brass")
    # le portique
    b += box(2, 8, 20, 8, 44, 28, "andesite_casing")
    b += box(40, 8, 20, 46, 44, 28, "andesite_casing")
    b += box(2, 38, 20, 46, 44, 28, "andesite_casing")
    b += box(16, 31, 16, 32, 38, 32, "brass")
    # le pupitre
    b += box(34, 8, 2, 45, 18, 7, "brass")
    b += box(36, 10, 1, 42, 16, 2, "brass", faces={"north": "gauge"}, uv={"north": [0, 0, 16, 16]})
    # les eprouvettes
    b += box(2, 8, 2, 16, 9, 6, "planks")
    for x in (3, 7, 11):
        b += box(x, 9, 3, x + 2, 15, 5, "green_liquid")
    whisk = Moving("whisk", (24, 14, 24), "y", 5.0, [
        (22, 14, 22, 26, 31, 26, "shaft"),
        (14, 14, 23, 34, 17, 25, "brass"),
        (23, 14, 14, 25, 17, 34, "brass"),
        (16, 19, 23, 32, 21, 25, "iron"),
        (23, 19, 16, 25, 21, 32, "iron"),
    ], when="always")
    return Machine("gmo_machine", "gmo_machine", (3, 3, 3), (1, 0, 1), b, [whisk],
                   compact=("dohy:block/gmo_machine", "dohy:block/gmo_machine"))


def fuel_pump():
    """La pompe a carburant : une cuve de cuivre a hublot, la colonne de pompe en andesite et
    laiton avec son compteur, le tuyau et le pistolet."""
    b = []
    b += box(1, 0, 1, 31, 4, 31, "andesite_casing")
    b += octagon(14, 20, 4, 40, 10, 4, "copper_tank", faces={"up": "copper", "down": None})
    b += octagon(14, 20, 40, 43, 7, 3, "copper")
    b += box(12, 43, 18, 16, 46, 22, "brass")
    for y in (12, 28):
        b += octagon(14, 20, y, y + 2, 11, 4, "brass_band")
    b += box(11, 14, 9, 17, 28, 10, "fuel")
    # la colonne de pompe
    b += box(18, 4, 2, 30, 30, 10, "andesite_casing")
    b += box(17, 30, 1, 31, 32, 11, "brass")
    b += box(20, 18, 1, 28, 26, 2, "brass", faces={"north": "gauge"}, uv={"north": [0, 0, 16, 16]})
    # le tuyau et le pistolet
    b += box(30, 8, 4, 32, 28, 6, "hose")
    b += box(29, 6, 3, 32, 10, 7, "iron")
    return Machine("fuel_pump", "fuel_pump", (2, 3, 2), (0, 0, 0), b, [],
                   compact=("dohy:block/fuel_pump", "dohy:block/fuel_pump"), particle="copper_tank")


MACHINES = [silo(), flour_mill(), bread_machine(), biogenerator(), gmo_machine(), fuel_pump()]


# --------------------------------------------------------------------------------------------
# L'apercu : une vue isometrique, face avant et cote droit
# --------------------------------------------------------------------------------------------

def preview_machine(machine, tex_images, scale=5):
    """Lance un rayon par pixel (numpy) : vue de face (nord) et de droite (est), d'en haut."""
    import numpy as np

    W, H, D = (s_ * 16 for s_ in machine.size)
    boxes = [(bx.a, bx.b, bx) for bx in machine.boxes]
    for mv in machine.moving:
        for x0, y0, z0, x1, y1, z1, key in mv.boxes:
            boxes.append(((x0, y0, z0), (x1, y1, z1), Box(x0, y0, z0, x1, y1, z1, key)))
    f = np.array([-1.0, -0.9, 1.0])
    f /= np.linalg.norm(f)
    right = np.cross(f, [0.0, 1.0, 0.0])
    right /= np.linalg.norm(right)
    up = np.cross(right, f)
    center = np.array([W / 2.0, H / 2.0, D / 2.0])
    radius = math.sqrt(W * W + H * H + D * D) / 2.0 + 4
    n = int(2 * radius * scale)
    jj, ii = np.mgrid[0:n, 0:n]
    sx = (ii - n / 2.0) / scale
    sy = (n / 2.0 - jj) / scale
    origin = center[None, None, :] + sx[..., None] * right + sy[..., None] * up - f * (radius * 3)
    inv = 1.0 / np.where(np.abs(f) < 1e-9, 1e-9, f)
    best_t = np.full((n, n), np.inf)
    best_box = np.full((n, n), -1)
    best_axis = np.zeros((n, n), dtype=int)
    for k, (a, b, bx) in enumerate(boxes):
        a_ = np.array(a, dtype=float)
        b_ = np.array(b, dtype=float)
        t1 = (a_ - origin) * inv
        t2 = (b_ - origin) * inv
        tmin = np.minimum(t1, t2)
        tmax = np.maximum(t1, t2)
        tn = tmin.max(axis=2)
        tf = tmax.min(axis=2)
        axis = tmin.argmax(axis=2)
        hit = (tn <= tf) & (tf > 0) & (tn < best_t)
        if not hit.any():
            continue
        # les faces decoupees (vitres) laissent passer le rayon la ou la texture est vide
        p = origin + tn[..., None] * f
        keys = {0: ("west" if f[0] > 0 else "east"), 1: ("down" if f[1] > 0 else "up"),
                2: ("north" if f[2] > 0 else "south")}
        for ax, face in keys.items():
            key = bx.face_tex(face)
            sel = hit & (axis == ax)
            if key is None:
                hit &= ~sel
                continue
            if key in CUTOUT:
                tile = np.array(tex_images[key])
                u, v = _uv(p, face, bx)
                alpha = tile[(v.astype(int)) % 16, (u.astype(int)) % 16, 3]
                hit &= ~(sel & (alpha < 128))
        best_t = np.where(hit, tn, best_t)
        best_box = np.where(hit, k, best_box)
        best_axis = np.where(hit, axis, best_axis)
    out = np.zeros((n, n, 4), dtype=np.uint8)
    out[...] = (52, 54, 60, 255)
    p_all = origin + np.where(np.isfinite(best_t), best_t, 0)[..., None] * f
    light = {"north": 0.82, "south": 0.82, "east": 0.64, "west": 0.64, "up": 1.0, "down": 0.5}
    for k, (a, b, bx) in enumerate(boxes):
        for ax in range(3):
            sel = (best_box == k) & (best_axis == ax)
            if not sel.any():
                continue
            face = {0: ("west" if f[0] > 0 else "east"), 1: ("down" if f[1] > 0 else "up"),
                    2: ("north" if f[2] > 0 else "south")}[ax]
            key = bx.face_tex(face)
            tile = np.array(tex_images[key]).astype(float)
            u, v = _uv(p_all, face, bx)
            col = tile[(v.astype(int)) % 16, (u.astype(int)) % 16, :3] * light[face]
            out[sel, :3] = np.clip(col[sel], 0, 255).astype(np.uint8)
    img = Image.fromarray(out, "RGBA")
    bbox = Image.fromarray(((best_box >= 0) * 255).astype(np.uint8)).getbbox()
    if bbox:
        img = img.crop((max(0, bbox[0] - 8), max(0, bbox[1] - 8), min(n, bbox[2] + 8), min(n, bbox[3] + 8)))
    return img


def _uv(p, face, bx):
    """Les coordonnees de texture d'un point d'une face, comme les uv automatiques du jeu."""
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    explicit = bx.uv.get(face)
    if explicit:
        if face in ("north", "south"):
            u = (x - bx.a[0]) / max(1e-9, bx.b[0] - bx.a[0]) * 16
            v = (bx.b[1] - y) / max(1e-9, bx.b[1] - bx.a[1]) * 16
        elif face in ("east", "west"):
            u = (z - bx.a[2]) / max(1e-9, bx.b[2] - bx.a[2]) * 16
            v = (bx.b[1] - y) / max(1e-9, bx.b[1] - bx.a[1]) * 16
        else:
            u = (x - bx.a[0]) / max(1e-9, bx.b[0] - bx.a[0]) * 16
            v = (z - bx.a[2]) / max(1e-9, bx.b[2] - bx.a[2]) * 16
        if face == "north":
            u = 16 - u
        return np.clip(u, 0, 15.999), np.clip(v, 0, 15.999)
    if face == "north":
        return (16 - (x % 16)) % 16, (16 - (y % 16)) % 16
    if face == "south":
        return x % 16, (16 - (y % 16)) % 16
    if face == "east":
        return (16 - (z % 16)) % 16, (16 - (y % 16)) % 16
    if face == "west":
        return z % 16, (16 - (y % 16)) % 16
    return x % 16, z % 16


# --------------------------------------------------------------------------------------------
# L'ecriture
# --------------------------------------------------------------------------------------------

def png_bytes(img):
    buf = io.BytesIO()
    img.save(buf, format="PNG", optimize=True)
    return buf.getvalue()


def json_bytes(data):
    return (json.dumps(data, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def outputs():
    """Tous les fichiers ecrits par ce script : {chemin: octets}."""
    files = {}
    tex_images = texture_images()
    for name, img in tex_images.items():
        files[os.path.join(ASSETS, "textures", TEX_DIR, name + ".png")] = png_bytes(img)
    files[os.path.join(ASSETS, "models", MODEL_DIR, "empty.json")] = json_bytes(
        {"textures": {"particle": f"dohy:{TEX_DIR}/andesite_casing"}, "elements": []})
    all_cells = {}
    packs = {}
    for m in MACHINES:
        cells = cut(m)
        if m.controller not in cells:
            raise SystemExit(f"{m.name} : la case de commande {m.controller} est vide")
        all_cells[m.name] = cells
        for cell, pieces in cells.items():
            path = os.path.join(ASSETS, "models", MODEL_DIR, m.name, f"{cell[0]}_{cell[1]}_{cell[2]}.json")
            files[path] = json_bytes(cell_model(m, pieces))
        files[os.path.join(ASSETS, "blockstates", m.block + "_part.json")] = json_bytes(part_blockstate(m, cells))
        files[os.path.join(ASSETS, "blockstates", m.block + ".json")] = json_bytes(controller_blockstate(m))
        files[os.path.join(ASSETS, "models", "item", "machine", m.name + ".json")] = json_bytes(miniature(m))
        files[os.path.join(ASSETS, "items", m.block + ".json")] = json_bytes(
            {"model": {"type": "minecraft:model", "model": f"dohy:item/machine/{m.name}"}})
        if m.moving:
            atlas, placed = pack(m, tex_images)
            packs[m.name] = (atlas, placed)
            files[os.path.join(ASSETS, "textures", "entity", "machine", m.name + ".png")] = png_bytes(atlas)
    files[os.path.join(JAVA, "client", "agri", "machine", "BigMachineGeometry.java")] = \
        geometry_java(MACHINES, packs).encode("utf-8")
    files[os.path.join(JAVA, "agri", "machine", "BigMachineShapes.java")] = \
        shapes_java(MACHINES, all_cells).encode("utf-8")
    return files, tex_images, all_cells


def main():
    args = sys.argv[1:]
    files, tex_images, all_cells = outputs()
    if "--check" in args:
        stale = []
        for path, data in files.items():
            if not os.path.exists(path) or open(path, "rb").read() != data:
                stale.append(os.path.relpath(path, ROOT))
        if stale:
            for path in stale:
                print(f"::error file={path}::pas a jour : relancer 'python3 -I tools/agri_machine_assets.py'")
            sys.exit(1)
        print(f"machines agricoles : {len(files)} fichiers a jour")
        return
    for path, data in files.items():
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if not os.path.exists(path) or open(path, "rb").read() != data:
            with open(path, "wb") as fh:
                fh.write(data)
    print(f"machines agricoles : {len(files)} fichiers ecrits")
    for m in MACHINES:
        print(f"  {m.name} : {len(all_cells[m.name])} cases sur {m.size[0] * m.size[1] * m.size[2]}")
    if "--preview" in args:
        folder = args[args.index("--preview") + 1]
        os.makedirs(folder, exist_ok=True)
        for m in MACHINES:
            preview_machine(m, tex_images).save(os.path.join(folder, m.name + ".png"))
        print("apercus :", folder)


if __name__ == "__main__":
    main()

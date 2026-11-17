#!/usr/bin/env python3
"""Les textures 16 x 16 de l'epice d'Arrakis, a la maniere des textures de PalaMod.

    python3 -I tools/arrakis_assets.py [dossier_apercu]

Ecrit dans assets/dohy/textures :

* block/spice_sand.png      — le sable d'epice : un sable rouille qui se raccorde sans couture,
                              grains de cannelle et paillettes d'epice ;
* item/melange.png          — une pincee de melange : un petit tas qui luit, des eclats au-dessus ;
* item/spice_harvester.png  — la moissonneuse de trois quarts ;
* block/terminal_spice.png  — la borne du comptoir : l'ecran des bornes, un tas d'epice et son eclat ;
* block/thumper_*.png       — le marteleur : sa masselotte rouille rayee, l'acier de son mat, son socle ;
* item/worm_tooth.png       — une dent de ver, courbe, de la racine rougie a la pointe.

La maniere de PalaMod : peu de tons par matiere (cinq a sept), une lumiere
venue d'en haut a gauche, un contour d'un ton sombre de la matiere (pas de noir
pur), des reflets francs. Rien n'est repris d'ailleurs ; la borne part de la
texture de la borne NAVETTE de Berskar, dont seul le pictogramme change.
"""

import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "dohy", "textures")

SPICE_ORANGE = (232, 116, 44)


def save(image, *path):
    target = os.path.join(TEX, *path)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    image.save(target)
    return image


# -- Le sable d'epice -----------------------------------------------------------------------

SAND = [
    (112, 46, 20),    # 0 grain de cannelle
    (146, 64, 28),    # 1
    (174, 84, 36),    # 2
    (198, 104, 44),   # 3 le fond
    (218, 126, 56),   # 4
    (236, 152, 74),   # 5 reflet
    (255, 214, 138),  # 6 paillette
]


def tile_noise(rng, cells, size=16):
    """Un bruit doux qui se raccorde sur ses bords : une grille periodique, interpolee."""
    grid = [[rng.random() for _ in range(cells)] for _ in range(cells)]
    out = [[0.0] * size for _ in range(size)]
    step = size / float(cells)
    for y in range(size):
        for x in range(size):
            gx, gy = x / step, y / step
            x0, y0 = int(gx) % cells, int(gy) % cells
            x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
            tx, ty = gx - int(gx), gy - int(gy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
            b = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
            out[y][x] = a * (1 - ty) + b * ty
    return out


def spice_sand():
    rng = random.Random(10191)
    coarse = tile_noise(rng, 4)
    fine = tile_noise(rng, 8)
    g = [[3] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            n = coarse[y][x] * 0.6 + fine[y][x] * 0.4 + (rng.random() - 0.5) * 0.22
            if n < 0.30:
                g[y][x] = 2
            elif n < 0.62:
                g[y][x] = 3
            elif n < 0.80:
                g[y][x] = 4
            else:
                g[y][x] = 5 if rng.random() < 0.35 else 4
    # des grains de cannelle : un pixel sombre, son voisin a un ton de moins
    for _ in range(9):
        x, y = rng.randrange(16), rng.randrange(16)
        g[y][x] = 0 if rng.random() < 0.5 else 1
        g[(y + 1) % 16][x] = min(g[(y + 1) % 16][x], 2)
    # quelques grains clairs, ombres dessous : le relief du sable
    for _ in range(10):
        x, y = rng.randrange(16), rng.randrange(16)
        if g[y][x] >= 3:
            g[y][x] = 5
            g[(y + 1) % 16][x] = 2
    # trois paillettes d'epice : un point vif, un reflet a cote, une ombre dessous
    for x, y in ((3, 4), (11, 9), (6, 13)):
        g[y][x] = 6
        g[y][(x + 1) % 16] = 5
        g[(y + 1) % 16][x] = 2
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            image.putpixel((x, y), SAND[g[y][x]] + (255,))
    return save(image, "block", "spice_sand.png")


# -- Le melange -----------------------------------------------------------------------------

MELANGE = {
    "o": (82, 28, 8),      # contour
    "1": (124, 44, 12),
    "2": (166, 68, 18),
    "3": (206, 98, 28),
    "4": (238, 138, 44),
    "5": (255, 186, 82),
    "6": (255, 236, 176),  # eclat
    "b": (132, 206, 255),  # l'eclat bleu de l'epice
}


def melange():
    """Un tas de poudre : la forme d'une dune, eclairee d'en haut a gauche, grains et eclats."""
    rows = [
        "................",
        "............6...",
        "...........6b6..",
        "....6.......6...",
        "......ooo.......",
        ".....o554o......",
        "....o55443o.....",
        "...o5543332o....",
        "...o4543332o....",
        "..o44433322o....",
        "..o44343322o....",
        ".o4434332322o...",
        ".o3433332221o...",
        "o432332222211o..",
        "o22121211111o.3.",
        ".ooooooooooo.2.4",
    ]
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in MELANGE:
                image.putpixel((x, y), MELANGE[c] + (255,))
    return save(image, "item", "melange.png")


# -- L'icone de la moissonneuse -------------------------------------------------------------

HARVESTER = {
    "k": (40, 24, 12),     # contour
    "o": (140, 82, 28),    # ocre sombre
    "p": (192, 130, 48),   # ocre
    "q": (230, 174, 84),   # ocre clair
    "r": (252, 218, 150),  # reflet
    "g": (26, 54, 76),     # vitre sombre
    "G": (104, 166, 200),  # vitre
    "W": (206, 238, 250),  # reflet de la vitre
    "s": (112, 116, 124),  # acier
    "S": (194, 198, 204),  # acier clair
    "D": (34, 34, 40),     # bas de caisse
    "T": (72, 68, 64),     # chenille
    "w": (168, 170, 174),  # galet
    "h": (110, 112, 116),  # galet, ombre
    "t": (24, 22, 22),     # creux de la chenille
    "y": (244, 196, 40),   # rayure jaune
    "n": (36, 32, 28),     # rayure noire
    "l": (255, 236, 160),  # phares du toit
    "e": (255, 160, 52),   # hublot d'epice
    "E": (255, 222, 140),  # hublot, reflet
}


def harvester_icon():
    """La moissonneuse de profil, l'avant a gauche : bouche rayee, cabine vitree, tremie, cyclone."""
    rows = [
        "..........kkk...",
        ".........kSSsk..",
        "..kkkkk..kSssk..",
        "..klklk..kSssk..",
        "..kqqqkkkkkkkkk.",
        "..kGWgkrrqqqqqqk",
        "..kGggkqppEeppok",
        "..kpppkqppeeppok",
        ".kkooooooooooook",
        "kykkDDDDDDDDDDDk",
        "knyTTTTTTTTTTTTk",
        "kyTwhTwhTwhTwhTk",
        "kkTthTthTthTthk.",
        ".kkkkkkkkkkkkk..",
        "................",
        "................",
    ]
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in HARVESTER:
                image.putpixel((x, y), HARVESTER[c] + (255,))
    return save(image, "item", "spice_harvester.png")


# -- La borne du comptoir -------------------------------------------------------------------

def terminal():
    """L'ecran des bornes de Berskar, un nouveau pictogramme : un tas d'epice, son eclat."""
    source = Image.open(os.path.join(TEX, "block", "terminal_shuttle.png")).convert("RGBA")
    image = source.copy()
    screen = source.getpixel((3, 3))
    for y in range(3, 11):
        for x in range(3, 13):
            image.putpixel((x, y), screen)
    pale = (255, 224, 122)
    picto = [
        "......Y...",
        ".....YWY..",
        "......Y...",
        "...oo.....",
        "..oYoo....",
        ".oYooooo..",
        ".oooooooo.",
        "oooooooooo",
    ]
    colors = {"o": SPICE_ORANGE, "Y": pale, "W": (255, 250, 230)}
    for dy, row in enumerate(picto):
        for dx, c in enumerate(row):
            if c in colors:
                image.putpixel((3 + dx, 3 + dy), colors[c] + (255,))
    accent = source.getpixel((12, 13))
    for y in range(12, 16):
        for x in range(16):
            if source.getpixel((x, y)) == accent:
                image.putpixel((x, y), SPICE_ORANGE + (255,))
    return save(image, "block", "terminal_spice.png")



# -- Le marteleur ---------------------------------------------------------------------------

STEEL = [(26, 26, 32), (44, 44, 52), (64, 66, 76), (88, 90, 102), (118, 122, 134), (160, 164, 176)]
RUST = [(70, 30, 12), (110, 50, 18), (150, 72, 26), (190, 98, 36), (222, 132, 54), (246, 172, 90)]
HAZARD = {"y": (240, 192, 36), "Y": (255, 224, 110), "k": (30, 28, 26), "K": (52, 48, 44)}


def bevelled(ramp, base=3):
    """Une tole de 16 : contour sombre, lisere clair en haut a gauche, coins rivetes."""
    g = [[base] * 16 for _ in range(16)]
    for i in range(16):
        g[0][i] = 1
        g[15][i] = 0
        g[i][0] = 1
        g[i][15] = 0
    for i in range(1, 15):
        g[1][i] = base + 1
        g[i][1] = base + 1
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        g[y][x] = 5
        g[y + 1][x + 1] = 1
    return g


def paint_grid(g, ramp):
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            image.putpixel((x, y), ramp[g[y][x]] + (255,))
    return image


def thumper_textures():
    rng = random.Random(26)
    # la masselotte : tole rouille, bande rayee au milieu
    g = bevelled(RUST)
    for y in range(3, 13):
        for x in range(3, 13):
            if g[y][x] == 3 and rng.random() < 0.18:
                g[y][x] = 2
    weight = paint_grid(g, RUST)
    for y in range(6, 10):
        for x in range(1, 15):
            stripe = ((x + y) // 2) % 2 == 0
            c = HAZARD["y"] if stripe else HAZARD["k"]
            if y == 6:
                c = HAZARD["Y"] if stripe else HAZARD["K"]
            weight.putpixel((x, y), c + (255,))
    save(weight, "block", "thumper_weight.png")
    # l'acier du mat : rainures verticales
    g = bevelled(STEEL)
    for y in range(2, 14):
        for x in (5, 10):
            g[y][x] = 1
            g[y][x + 1] = 4
    save(paint_grid(g, STEEL), "block", "thumper_steel.png")
    # le socle : tole larmee, cadre sombre, boulons
    g = bevelled(STEEL, base=2)
    for y in range(3, 13):
        for x in range(3, 13):
            if (x + 2 * y) % 4 == 0:
                g[y][x] = 4
            elif (x + 2 * y) % 4 == 1:
                g[y][x] = 1
    return save(paint_grid(g, STEEL), "block", "thumper_base.png")


# -- La dent de ver -------------------------------------------------------------------------

BONE = {"o": (74, 52, 34), "1": (132, 112, 84), "2": (170, 152, 118), "3": (204, 190, 156), "4": (230, 222, 196),
        "5": (248, 244, 228), "r": (120, 36, 30), "R": (160, 58, 44)}


def worm_tooth():
    """Un croc courbe, de la racine (en bas a gauche) a la pointe (en haut a droite)."""
    def curve(t):
        # une courbe de Bezier : racine, point de controle, pointe
        p0, p1, p2 = (6.0, 11.8), (5.2, 4.6), (13.2, 1.8)
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        return x, y

    samples = [curve(k / 200.0) for k in range(201)]
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    inside = {}
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            best, bt, side = 99.0, 0.0, 0.0
            for k, (cx, cy) in enumerate(samples):
                d = math.hypot(px - cx, py - cy)
                if d < best:
                    best, bt = d, k / 200.0
                    nx, ny = samples[min(200, k + 1)][0] - cx, samples[min(200, k + 1)][1] - cy
                    side = nx * (py - cy) - ny * (px - cx)
            width = 3.1 * (1.0 - bt) ** 0.7 + 0.5
            if best <= width:
                inside[(x, y)] = (bt, side, best / width)
    for (x, y), (t, side, depth) in inside.items():
        light = 3.2 - depth * 1.6 + (1.0 if side < 0 else -0.6) + t * 0.8
        index = max(1, min(5, int(round(light))))
        c = BONE[str(index)]
        if t < 0.07:
            c = BONE["R"] if side < 0 else BONE["r"]      # la racine, encore rouge de gencive
        image.putpixel((x, y), c + (255,))
    for y in range(16):
        for x in range(16):
            if (x, y) in inside:
                continue
            if any((x + dx, y + dy) in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                image.putpixel((x, y), BONE["o"] + (255,))
    return save(image, "item", "worm_tooth.png")


def preview(folder, images):
    os.makedirs(folder, exist_ok=True)
    sheet = Image.new("RGBA", (len(images) * 272 + 3 * 16 * 8 + 16, 400), (64, 64, 64, 255))
    for i, image in enumerate(images):
        sheet.alpha_composite(image.resize((256, 256), Image.NEAREST), (i * 272 + 8, 8))
    # le sable en mosaique, pour voir les raccords
    tiled = Image.new("RGBA", (48, 48))
    for ty in range(3):
        for tx in range(3):
            tiled.paste(images[0], (tx * 16, ty * 16))
    sheet.alpha_composite(tiled.resize((384, 384), Image.NEAREST), (len(images) * 272 + 8, 8))
    sheet.save(os.path.join(folder, "epice_textures.png"))


def main():
    images = [spice_sand(), melange(), harvester_icon(), terminal(), thumper_textures(), worm_tooth()]
    if len(sys.argv) > 1:
        preview(sys.argv[1], images)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Les textures 16 x 16 de l'epice d'Arrakis, a la maniere des textures de PalaMod.

    python3 -I tools/arrakis_assets.py [dossier_apercu]

Ecrit dans assets/dohy/textures :

* block/spice_sand.png      — le sable d'epice : un sable rouille qui se raccorde sans couture,
                              grains de cannelle et paillettes d'epice ;
* item/melange.png          — une pincee de melange : un petit tas qui luit, des eclats au-dessus ;
* item/spice_harvester.png  — la moissonneuse de trois quarts ;
* block/terminal_spice.png  — la borne du comptoir : l'ecran des bornes, un tas d'epice et son eclat.

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
    images = [spice_sand(), melange(), harvester_icon(), terminal()]
    if len(sys.argv) > 1:
        preview(sys.argv[1], images)


if __name__ == "__main__":
    main()

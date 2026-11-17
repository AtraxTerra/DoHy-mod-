#!/usr/bin/env python3
"""Les petites textures de l'epice d'Arrakis, peintes pixel par pixel.

    python3 -I tools/arrakis_assets.py

Ecrit dans assets/dohy/textures :

* block/spice_sand.png      — le sable d'epice : du sable rouille, grains de cannelle et paillettes ;
* item/melange.png          — une pincee d'epice, un petit tas qui brille ;
* item/spice_harvester.png  — la moissonneuse vue de profil ;
* block/terminal_spice.png  — la borne du comptoir : l'ecran des autres bornes, une dune et une etincelle.

Rien n'est repris d'ailleurs ; la borne part de la texture de la borne NAVETTE
de Berskar, dont seul le pictogramme change.
"""

import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "dohy", "textures")

SPICE = (232, 116, 44)


def save(image, *path):
    target = os.path.join(TEX, *path)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    image.save(target)


def spice_sand():
    rng = random.Random(1965)
    palette = [
        ((196, 102, 46), 34),   # le fond rouille
        ((184, 92, 40), 22),
        ((208, 116, 54), 18),
        ((150, 66, 30), 9),     # grains de cannelle
        ((122, 52, 26), 4),
        ((236, 148, 72), 7),    # paillettes
        ((252, 176, 96), 2),
        ((214, 172, 118), 4),   # grains de sable pale
    ]
    total = sum(w for _, w in palette)
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            pick = rng.uniform(0, total)
            for color, weight in palette:
                pick -= weight
                if pick <= 0:
                    break
            image.putpixel((x, y), color + (255,))
    save(image, "block", "spice_sand.png")


def melange():
    """Un petit tas d'epice : sombre a la base, orange vif au sommet, quelques eclats."""
    rows = [
        "................",
        "................",
        "................",
        "................",
        "................",
        ".......*........",
        "......oOo.......",
        ".....oOOOo......",
        "....oOO*OOo.....",
        "...oOOOOOOOo..*.",
        "..ooOOO*OOOOo...",
        "..rooOOOOOOoor..",
        ".rrooooOooooorr.",
        ".Rrrrooooooorrr.",
        "..RRRrrrrrrRRR..",
        "................",
    ]
    colors = {
        "*": (255, 222, 150),
        "O": (238, 132, 52),
        "o": (206, 102, 40),
        "r": (162, 72, 30),
        "R": (112, 46, 22),
    }
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in colors:
                image.putpixel((x, y), colors[c] + (255,))
    save(image, "item", "melange.png")


def harvester_icon():
    """La moissonneuse de profil, l'avant a gauche : bouche rayee, cabine, caisse, cyclone."""
    rows = [
        "................",
        "............k...",
        "..a........kSk..",
        "..a..pp....kSk..",
        ".kkkkkk...kSSSk.",
        ".kPgGPk.PPPPPPP.",
        ".kPgGPkPPPPPPPPd",
        ".kPPPPkPPPPPPPPd",
        "ykPPPPkPPddPPPPd",
        "YkddddddddddddDd",
        "ykTTTTTTTTTTTTTT",
        "YTwTTTwTTTTwTTTw",
        "yTTwTTTTwTTTwTTT",
        "..TTTTTTTTTTTTT.",
        "................",
        "................",
    ]
    colors = {
        "k": (52, 50, 48),
        "P": (204, 148, 66),
        "p": (255, 120, 40),
        "d": (150, 100, 46),
        "D": (110, 74, 36),
        "g": (70, 110, 128),
        "G": (120, 170, 190),
        "S": (160, 158, 152),
        "T": (44, 42, 40),
        "w": (150, 146, 138),
        "y": (232, 186, 40),
        "Y": (40, 38, 36),
        "a": (60, 58, 56),
    }
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in colors:
                image.putpixel((x, y), colors[c] + (255,))
    save(image, "item", "spice_harvester.png")


def terminal():
    """L'ecran des bornes, un nouveau pictogramme : une dune, une etincelle d'epice au-dessus."""
    source = Image.open(os.path.join(TEX, "block", "terminal_shuttle.png")).convert("RGBA")
    image = source.copy()
    screen = source.getpixel((3, 3))
    for y in range(3, 11):
        for x in range(3, 13):
            image.putpixel((x, y), screen)
    picto = [
        "....#.....",
        "...###....",
        "....#.....",
        "..........",
        "......##..",
        "....##..#.",
        "..##.....#",
        "##########",
    ]
    for dy, row in enumerate(picto):
        for dx, c in enumerate(row):
            if c == "#":
                image.putpixel((3 + dx, 3 + dy), SPICE + (255,))
    # le voyant de couleur, en bas a droite, comme sur les autres bornes
    accent = source.getpixel((12, 13))
    for y in range(16):
        for x in range(16):
            if source.getpixel((x, y)) == accent and y >= 12:
                image.putpixel((x, y), SPICE + (255,))
    save(image, "block", "terminal_spice.png")


def main():
    spice_sand()
    melange()
    harvester_icon()
    terminal()


if __name__ == "__main__":
    main()

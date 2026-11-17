#!/usr/bin/env python3
"""L'eau d'Arrakis : textures 16 x 16, couches d'armure et modeles, a la maniere de PalaMod.

    python3 -I tools/arrakis_water_assets.py [dossier_apercu]

Ecrit dans assets/dohy :

* textures/item/canteen.png                 — la gourde : un flacon rond gaine de toile, col
                                              d'acier, bandouliere, une goutte cousue ;
* textures/item/stillsuit_*.png             — le distille : masque, combinaison, jambieres, bottes ;
* textures/entity/equipment/humanoid[_leggings]/stillsuit.png
                                            — les couches portees du distille : toile kaki,
                                              tubes d'ardoise, sangles, poches a eau bleues ;
* textures/block/windtrap_*.png             — le piege a vent : socle, citerne a jauge (cinq
                                              niveaux), collecteur, mat, ailettes, coiffe ;
* models/block/windtrap_*.json              — son modele (deux blocs de haut), la citerne a part
                                              pour la jauge ; blockstates/windtrap.json ;
* textures/block/terminal_water.png         — la borne EAU : l'ecran des bornes, une goutte ;
* textures/gui/sprites/hud/water.png        — la goutte de la barre d'eau du HUD.

La maniere de PalaMod : peu de tons par matiere (cinq a sept), une lumiere venue
d'en haut a gauche, un contour d'un ton sombre de la matiere (pas de noir pur),
des reflets francs. Rien n'est repris d'ailleurs : dessins propres a Berskar.
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

# -- Les tons -------------------------------------------------------------------------------

FABRIC = [(58, 50, 40), (90, 80, 64), (122, 110, 90), (154, 142, 118), (186, 174, 148), (216, 206, 182)]
TUBE = {"t": (36, 40, 46), "u": (62, 68, 76), "T": (108, 116, 126)}
STRAP = {"b": (74, 48, 28), "B": (112, 76, 44), "c": (150, 106, 62)}
BRASS = {"y": (176, 130, 56), "Y": (232, 194, 110)}
WATER = {"w": (26, 88, 148), "W": (62, 158, 230), "V": (176, 232, 255)}
LEATHER = [(70, 44, 24), (108, 72, 40), (144, 100, 58), (178, 132, 82), (208, 166, 110), (234, 204, 152)]
STEEL = [(32, 36, 44), (58, 64, 76), (92, 100, 114), (132, 140, 154), (176, 184, 196), (222, 228, 236)]
SANDMETAL = [(66, 50, 34), (100, 80, 56), (136, 110, 78), (170, 140, 102), (200, 170, 128), (228, 204, 164)]
GLASS = {"g": (22, 30, 38), "G": (52, 66, 78)}

DETAIL = {}
DETAIL.update(TUBE)
DETAIL.update(STRAP)
DETAIL.update(BRASS)
DETAIL.update(WATER)
DETAIL.update({"k": (30, 26, 22)})


def save(image, *path):
    target = os.path.join(TEX, *path)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    image.save(target)
    return image


def write_json(data, *path):
    target = os.path.join(ASSETS, *path)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, "w", encoding="utf-8") as fh:
        fh.write(json.dumps(data, indent=2) + "\n")


# -- Les icones par silhouette ----------------------------------------------------------------

def shaded(shape, ramp, base=3, overlay=None, rng=None):
    """Une silhouette remplie a la maniere de PalaMod : contour sombre de la matiere, arete
    claire en haut a gauche, ombre en bas a droite, grain leger ; puis les details par-dessus."""
    h, w = len(shape), len(shape[0])
    inside = {(x, y) for y in range(h) for x in range(w) if shape[y][x] != "."}
    image = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    rng = rng or random.Random(7)
    for (x, y) in inside:
        edge = any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            index = 0
        else:
            index = base
            if (x - 1, y) not in inside or (x, y - 1) not in inside or (x - 1, y - 1) not in inside:
                index = base + 2
            elif (x - 2, y) not in inside or (x, y - 2) not in inside:
                index = base + 1
            elif (x + 1, y) not in inside or (x, y + 1) not in inside or (x + 1, y + 1) not in inside:
                index = base - 2
            elif (x + 2, y) not in inside or (x, y + 2) not in inside:
                index = base - 1
            elif rng.random() < 0.12:
                index = base - 1
            elif rng.random() < 0.06:
                index = base + 1
        image.putpixel((x, y), ramp[max(0, min(len(ramp) - 1, index))] + (255,))
    if overlay:
        for y, row in enumerate(overlay):
            for x, c in enumerate(row):
                if c in DETAIL:
                    image.putpixel((x, y), DETAIL[c] + (255,))
                elif c.isdigit():
                    image.putpixel((x, y), ramp[int(c)] + (255,))
    return image


def canteen():
    """Un flacon rond gaine de toile, cousu tout autour, une goutte au milieu ; col et bouchon
    d'acier, une bandouliere de cuir qui part des deux cotes du col."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cx, cy, r = 8.0, 9.6, 6.1
    inside = set()
    for y in range(16):
        for x in range(16):
            if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r:
                inside.add((x, y))
    for (x, y) in inside:
        nx, ny = (x + 0.5 - cx) / r, (y + 0.5 - cy) / r
        edge = any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        light = -(nx * 0.72 + ny * 0.72)
        index = 3 + (2 if light > 0.55 else 1 if light > 0.2 else 0 if light > -0.3 else -1 if light > -0.65 else -2)
        d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
        if r - 2.1 <= d < r - 1.2 and (x + y) % 2 == 0 and ny < 0.6:
            index = 5 if light > 0.0 else 4                 # la couture, en points clairs
        if edge:
            index = 0
        image.putpixel((x, y), LEATHER[max(0, min(5, index))] + (255,))
    # la goutte cousue au milieu
    drop = ["..w..", ".wVw.", "wWVWw", "wWWWw", ".www."]
    for dy, row in enumerate(drop):
        for dx, c in enumerate(row):
            if c in WATER:
                image.putpixel((6 + dx, 7 + dy), WATER[c] + (255,))
    # le col (trois pixels de haut) et le bouchon, cernes de leur ton le plus sombre
    neck = [
        "oooooo",
        "o5543o",
        "o4332o",
        "o4321o",
    ]
    for dy, row in enumerate(neck):
        for dx, c in enumerate(row):
            image.putpixel((5 + dx, 1 + dy), STEEL[0 if c == "o" else int(c)] + (255,))
    # la bandouliere : elle part des deux cotes du col et file vers le haut des epaules
    strap = {(4, 3): "c", (3, 2): "c", (2, 1): "B", (4, 4): "B", (3, 3): "B", (2, 2): "b", (1, 1): "b",
             (11, 3): "c", (12, 2): "B", (13, 1): "B", (11, 4): "B", (12, 3): "b", (13, 2): "b", (14, 1): "b"}
    for (x, y), c in strap.items():
        image.putpixel((x, y), STRAP[c] + (255,))
    return save(image, "item", "canteen.png")


MASK_SHAPE = [
    "................",
    "....XXXXXXXX....",
    "...XXXXXXXXXX...",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    ".XXXXXXXXXXXXXX.",
    ".XXXXXXXXXXXXXX.",
    ".XXXXXXXXXXXXXX.",
    ".XXXXXXXXXXXXXX.",
    ".XXXXXXXXXXXXXX.",
    ".XXXXXXXXXXXXXX.",
    "..XXXXXXXXXXXX..",
    "...XXXXXXXXXX...",
    "......XXXX......",
    "......XXXX......",
    "................",
]
MASK_DETAIL = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "...kkkkkkkkkk...",
    "...kTkkkkkkTk...",
    "...kkkkkkkkkk...",
    "...tuuuuuuuut...",
    "...uTuuuuuuTu...",
    "...utuuWWuutu...",
    "...tuuuwwuuut...",
    "....tttuuttt....",
    "......tTut......",
    "......tuut......",
    "................",
]

SUIT_SHAPE = [
    "................",
    ".XXXX......XXXX.",
    "XXXXXX....XXXXXX",
    "XXXXXXXXXXXXXXXX",
    "XXXXXXXXXXXXXXXX",
    ".XXXXXXXXXXXXXX.",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    "...XXXXXXXXXX...",
    "................",
]
SUIT_DETAIL = [
    "................",
    "................",
    "................",
    "................",
    "....T......T....",
    "....tuuuuuut....",
    "....tuTWWTut....",
    "....tuuwwuut....",
    "....T.tuut.T....",
    "....t......t....",
    "....T......t....",
    "...bBbbyYbbBb...",
    "...bwbbyybbwb...",
    "...WW......WW...",
    "................",
    "................",
]

LEGS_SHAPE = [
    "................",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    "..XXXXX..XXXXX..",
    "..XXXXX..XXXXX..",
    "..XXXXX..XXXXX..",
    "..XXXXX..XXXXX..",
    "..XXXXX..XXXXX..",
    "..XXXXX..XXXXX..",
    "..XXXXX..XXXXX..",
    "..XXXXX..XXXXX..",
    "..XXXXX..XXXXX..",
    "..XXXXX..XXXXX..",
    "..XXXXX..XXXXX..",
    "................",
]
LEGS_DETAIL = [
    "................",
    "...bBBbyYbBBb...",
    "...bbbbyybbbb...",
    "................",
    "...T........T...",
    "...t........t...",
    "...t........t...",
    "...T54....45T...",
    "...t21....12t...",
    "...t........t...",
    "...T........T...",
    "...t........t...",
    "...t........t...",
    "................",
    "................",
    "................",
]

BOOTS_SHAPE = [
    "................",
    "................",
    "...XXXXX.XXXXX..",
    "...XXXXX.XXXXX..",
    "...XXXXX.XXXXX..",
    "...XXXXX.XXXXX..",
    "...XXXXX.XXXXX..",
    "...XXXXX.XXXXX..",
    "..XXXXXX.XXXXX..",
    ".XXXXXXXXXXXXX..",
    "XXXXXXXXXXXXXX..",
    "XXXXXXX.XXXXXX..",
    "XXXXXXX.XXXXXX..",
    "XXXXXXX.XXXXXX..",
    "................",
    "................",
]
BOOTS_DETAIL = [
    "................",
    "................",
    "................",
    "....bBb...bBb...",
    "....bbb...bbb...",
    "................",
    "....cBb...cBb...",
    "....bbb...bbb...",
    "......w.....w...",
    "................",
    "................",
    ".1tttuu..ttuu...",
    "................",
    "................",
    "................",
    "................",
]


def stillsuit_icons():
    rng = random.Random(1987)
    images = []
    for name, shape, detail in (("stillsuit_mask", MASK_SHAPE, MASK_DETAIL), ("stillsuit_suit", SUIT_SHAPE, SUIT_DETAIL),
                                ("stillsuit_leggings", LEGS_SHAPE, LEGS_DETAIL),
                                ("stillsuit_boots", BOOTS_SHAPE, BOOTS_DETAIL)):
        images.append(save(shaded(shape, FABRIC, 3, detail, rng), "item", name + ".png"))
    return images


# -- Les couches portees du distille ------------------------------------------------------------

def fabric(image, x0, y0, w, h, rng, base=3, rows=None):
    """Un pan de toile : grain leger, arete claire en haut, ourlet sombre en bas."""
    for y in range(h):
        if rows is not None and y not in rows:
            continue
        for x in range(w):
            index = base
            r = rng.random()
            if r < 0.14:
                index = base - 1
            elif r < 0.22:
                index = base + 1
            if y == 0:
                index = base + 1
            if y == h - 1:
                index = base - 1
            image.putpixel((x0 + x, y0 + y), FABRIC[index] + (255,))


def put(image, x, y, key):
    image.putpixel((x, y), (DETAIL[key] if key in DETAIL else FABRIC[int(key)]) + (255,))


def stillsuit_layers():
    rng = random.Random(2049)
    outer = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # -- la tete : capuche de toile, les yeux libres, le masque sur le nez et la bouche
    fabric(outer, 8, 0, 8, 8, rng, 4)                        # dessus
    fabric(outer, 16, 0, 8, 8, rng, 2)                       # dessous
    for x0 in (0, 16, 24):                                   # cotes et nuque
        fabric(outer, x0, 8, 8, 8, rng, 3)
        for x in range(8):
            put(outer, x0 + x, 13, "b")                      # la sangle du masque
            put(outer, x0 + x, 12, "B" if x % 3 else "b")
    for x in range(24, 32):
        put(outer, x, 13, "b")
    put(outer, 27, 13, "y")
    put(outer, 28, 13, "Y")
    fabric(outer, 8, 8, 8, 3, rng, 4)                         # front : le front couvert
    for x in range(8, 16):
        put(outer, x, 10, "2")
    for y in (11, 12):                                       # les yeux : la capuche les encadre
        put(outer, 8, y, "3")
        put(outer, 15, y, "2")
    mask = ["tuuuuuut", "uTuWWuTu", "tuuwwuut"]
    for dy, row in enumerate(mask):
        for dx, c in enumerate(row):
            put(outer, 8 + dx, 13 + dy, c)
    # -- le torse : tubes, pompe a la poitrine, ceinture a poches ; reservoir dans le dos
    fabric(outer, 20, 16, 8, 4, rng, 4)                       # epaules
    fabric(outer, 28, 16, 8, 4, rng, 2)
    for x0, w in ((16, 4), (20, 8), (28, 4), (32, 8)):
        fabric(outer, x0, 20, w, 12, rng, 3)
    for y in range(21, 29):                                  # les deux tubes de devant
        put(outer, 21, y, "t" if y % 3 else "T")
        put(outer, 26, y, "t" if y % 3 else "T")
    pump = ["tuut", "TWWT", "uwwu"]
    for dy, row in enumerate(pump):
        for dx, c in enumerate(row):
            put(outer, 22 + dx, 22 + dy, c)
    for x0, w in ((16, 4), (20, 8), (28, 4), (32, 8)):       # la ceinture, tout autour
        for x in range(w):
            put(outer, x0 + x, 29, "B" if (x0 + x) % 4 else "b")
            put(outer, x0 + x, 30, "b")
    put(outer, 23, 29, "y")
    put(outer, 24, 29, "Y")
    put(outer, 23, 30, "y")
    put(outer, 24, 30, "y")
    for x in (21, 26):
        put(outer, x, 31, "W")                               # les poches a eau
    pack = ["1111111", "1uuuuu1", "1uWuWu1", "1uwuwu1", "1uuuuu1", "1tTttt1", "1111111"]
    for dy, row in enumerate(pack):                          # le reservoir du dos
        for dx, c in enumerate(row):
            put(outer, 33 + dx, 21 + dy, c)
    # -- les bras : manches, tube a l'exterieur, poignets bandes
    fabric(outer, 44, 16, 4, 4, rng, 4)
    fabric(outer, 48, 16, 4, 4, rng, 2)
    for x0 in (40, 44, 48, 52):
        fabric(outer, x0, 20, 4, 12, rng, 3)
        for x in range(4):
            put(outer, x0 + x, 30, "b")
            put(outer, x0 + x, 31, "B" if x % 2 else "b")
            put(outer, x0 + x, 25, "2")                      # le coude
    for y in range(20, 30):
        put(outer, 41, y, "t" if y % 4 else "T")
    # -- les bottes : la bande basse des jambes, semelle sombre, pompe au talon
    for x0 in (0, 4, 8, 12):
        fabric(outer, x0, 27, 4, 5, rng, 2)
        for x in range(4):
            put(outer, x0 + x, 27, "B")
            put(outer, x0 + x, 28, "b")
            put(outer, x0 + x, 31, "t")
    for x in range(8, 12):
        for y in range(16, 20):
            put(outer, x, y, "t")
    put(outer, 13, 29, "W")
    put(outer, 14, 29, "w")
    save(outer, "entity", "equipment", "humanoid", "stillsuit.png")

    inner = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # -- les jambes : toile, genouilleres, tube sur l'exterieur, poche de cuisse
    fabric(inner, 4, 16, 4, 4, rng, 4)
    fabric(inner, 8, 16, 4, 4, rng, 2)
    for x0 in (0, 4, 8, 12):
        fabric(inner, x0, 20, 4, 12, rng, 3)
    knee = ["5554", "4221"]
    for dy, row in enumerate(knee):
        for dx, c in enumerate(row):
            put(inner, 4 + dx, 24 + dy, c)
    for y in range(20, 31):
        put(inner, 1, y, "t" if y % 4 else "T")
    for dy, row in enumerate(["bbb", "bWb", "bbb"]):
        for dx, c in enumerate(row):
            put(inner, 1 + dx, 21 + dy, c)
    # -- la taille : la ceinture du bas du torse
    for x0, w in ((16, 4), (20, 8), (28, 4), (32, 8)):
        fabric(inner, x0, 28, w, 4, rng, 3)
        for x in range(w):
            put(inner, x0 + x, 28, "B" if (x0 + x) % 4 else "b")
            put(inner, x0 + x, 29, "b")
    put(inner, 23, 28, "y")
    put(inner, 24, 28, "Y")
    fabric(inner, 28, 16, 8, 4, rng, 2)
    save(inner, "entity", "equipment", "humanoid_leggings", "stillsuit.png")
    return outer, inner


# -- Le piege a vent ------------------------------------------------------------------------------

def bevel(ramp, base=3, rivets=True):
    g = [[base] * 16 for _ in range(16)]
    for i in range(16):
        g[0][i] = base + 2 if i < 15 else base
        g[15][i] = 0
        g[i][0] = base + 1 if i < 15 else 0
        g[i][15] = 0
    for i in range(1, 15):
        g[1][i] = max(g[1][i], base + 1)
    if rivets:
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            g[y][x] = 5
            g[y + 1][x + 1] = 1
    return g


def paint(g, ramp):
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            image.putpixel((x, y), ramp[max(0, min(len(ramp) - 1, g[y][x]))] + (255,))
    return image


def windtrap_textures():
    rng = random.Random(7041)
    images = []
    # le socle : tole de sable sombre, cadre, boulons
    g = bevel(SANDMETAL, 2)
    for y in range(3, 13):
        for x in range(3, 13):
            if rng.random() < 0.12:
                g[y][x] = 1
    images.append(save(paint(g, SANDMETAL), "block", "windtrap_base.png"))
    # la citerne : un panneau rivete, une jauge vitree au milieu ; cinq niveaux d'eau
    for level in range(5):
        g = [[3] * 16 for _ in range(16)]
        for y in range(16):
            for x in range(16):
                if rng.random() < 0.1:
                    g[y][x] = 2
        for x in range(16):
            g[4][x] = 5
            g[5][x] = 4
            g[11][x] = 1
            g[10][x] = 2
        for x in (3, 12):
            for y in (6, 9):
                g[y][x] = 5
                g[y + 1][x] = 1
        image = paint(g, SANDMETAL)
        # la jauge : cadre d'acier, vitre sombre, l'eau qui monte
        for y in range(5, 12):
            for x in range(6, 10):
                frame = x in (6, 9) or y in (5, 11)
                image.putpixel((x, y), (STEEL[1] if frame else GLASS["g"]) + (255,))
        filled = [0, 1, 3, 4, 5][level]
        for k in range(filled):
            y = 10 - k
            for x in (7, 8):
                c = WATER["V"] if (k == filled - 1 and level == 4) else WATER["W"] if x == 7 else WATER["w"]
                image.putpixel((x, y), c + (255,))
        if filled < 5:
            image.putpixel((7, 10 - filled), GLASS["G"] + (255,))
        images.append(save(image, "block", "windtrap_tank_%d.png" % level))
    # le collecteur : une rigole circulaire qui descend vers le mat
    g = bevel(STEEL, 3, rivets=False)
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if 3.0 < d < 5.0:
                g[y][x] = 1 if d < 4.0 else 2
            elif d <= 3.0:
                g[y][x] = 2
    images.append(save(paint(g, STEEL), "block", "windtrap_lip.png"))
    # le mat : acier rainure
    g = bevel(STEEL, 3, rivets=False)
    for y in range(1, 15):
        for x in (4, 10):
            g[y][x] = 1
            g[y][x + 1] = 4
    images.append(save(paint(g, STEEL), "block", "windtrap_steel.png"))
    # les ailettes : des lames d'acier serrees, un cadre
    g = bevel(STEEL, 3, rivets=False)
    for y in range(2, 14):
        for x in range(2, 14):
            g[y][x] = 4 if y % 3 == 0 else 1 if y % 3 == 2 else 3
    images.append(save(paint(g, STEEL), "block", "windtrap_fin.png"))
    # la coiffe : un dome de laiton sable, une bague sombre
    ramp = [(78, 56, 26), (122, 88, 40), (164, 122, 58), (200, 156, 78), (230, 192, 112), (250, 226, 164)]
    g = bevel(ramp, 3, rivets=False)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 3.0:
                g[y][x] = 5 if x + y < 14 else 4
            if 6.0 < d < 7.0:
                g[y][x] = 1
    images.append(save(paint(g, ramp), "block", "windtrap_cap.png"))
    return images


def face(texture, uv, cull=None):
    data = {"texture": texture, "uv": uv}
    if cull:
        data["cullface"] = cull
    return data


def cube(start, end, textures, uvs=None, cull=None):
    """Un element : la meme texture sur les cotes, d'autres dessus et dessous si donnees."""
    sides = textures.get("side")
    up = textures.get("up", sides)
    down = textures.get("down", sides)
    (x0, y0, z0), (x1, y1, z1) = start, end
    side_uv = uvs.get("side") if uvs else None
    faces = {}
    for name in ("north", "south", "east", "west"):
        if name in ("north", "south"):
            uv = side_uv or [x0, 16 - y1, x1, 16 - y0]
        else:
            uv = side_uv or [z0, 16 - y1, z1, 16 - y0]
        faces[name] = face(sides, [max(0, min(16, v)) for v in uv])
    faces["up"] = face(up, (uvs.get("up") if uvs else None) or [x0, z0, x1, z1])
    faces["down"] = face(down, (uvs.get("down") if uvs else None) or [x0, z0, x1, z1], cull)
    return {"from": list(start), "to": list(end), "faces": faces}


def windtrap_models():
    frame = [
        cube((1, 0, 1), (15, 2, 15), {"side": "#base"}, {"side": [1, 2, 15, 4]}, cull="down"),
        cube((1, 10, 1), (15, 11, 15), {"side": "#steel", "up": "#lip", "down": "#steel"},
             {"side": [1, 6, 15, 7], "up": [1, 1, 15, 15]}),
        cube((6.5, 11, 6.5), (9.5, 26, 9.5), {"side": "#steel"}, {"side": [6, 0, 9, 15]}),
        cube((2, 13, 2), (14, 14, 14), {"side": "#fin"}, {"side": [2, 0, 14, 1], "up": [2, 2, 14, 14]}),
        cube((2.5, 16, 2.5), (13.5, 17, 13.5), {"side": "#fin"}, {"side": [2, 0, 13, 1], "up": [2, 2, 13, 13]}),
        cube((3, 19, 3), (13, 20, 13), {"side": "#fin"}, {"side": [3, 0, 13, 1], "up": [3, 3, 13, 13]}),
        cube((3.5, 22, 3.5), (12.5, 23, 12.5), {"side": "#fin"}, {"side": [3, 0, 12, 1], "up": [3, 3, 12, 12]}),
        cube((5.5, 26, 5.5), (10.5, 28, 10.5), {"side": "#cap"}, {"side": [5, 8, 10, 10], "up": [5, 5, 10, 10]}),
        cube((7.5, 28, 7.5), (8.5, 31, 8.5), {"side": "#steel"}, {"side": [7, 1, 8, 4], "up": [7, 7, 8, 8]}),
    ]
    textures = {"particle": "dohy:block/windtrap_base", "base": "dohy:block/windtrap_base",
                "steel": "dohy:block/windtrap_steel", "lip": "dohy:block/windtrap_lip",
                "fin": "dohy:block/windtrap_fin", "cap": "dohy:block/windtrap_cap"}
    write_json({"textures": textures, "elements": frame}, "models", "block", "windtrap_frame.json")
    tank = cube((2, 2, 2), (14, 10, 14), {"side": "#tank", "up": "#base", "down": "#base"},
                {"side": [2, 4, 14, 12], "up": [2, 2, 14, 14]})
    for level in range(5):
        write_json({"textures": {"particle": "dohy:block/windtrap_base", "base": "dohy:block/windtrap_base",
                                 "tank": "dohy:block/windtrap_tank_%d" % level}, "elements": [tank]},
                   "models", "block", "windtrap_tank_%d.json" % level)
    full = dict(textures)
    full["tank"] = "dohy:block/windtrap_tank_3"
    display = {
        "gui": {"rotation": [30, 225, 0], "translation": [0, -2.5, 0], "scale": [0.42, 0.42, 0.42]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, -3, 0], "scale": [0.42, 0.42, 0.42]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 0], "scale": [0.3, 0.3, 0.3]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -1, 0], "scale": [0.3, 0.3, 0.3]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, -1, 0], "scale": [0.3, 0.3, 0.3]},
    }
    write_json({"textures": full, "elements": frame + [tank], "display": display},
               "models", "block", "windtrap.json")
    levels = [("0", 0), ("1|2|3|4", 1), ("5|6|7|8", 2), ("9|10|11|12", 3), ("13|14|15|16", 4)]
    multipart = [{"apply": {"model": "dohy:block/windtrap_frame"}}]
    for values, level in levels:
        multipart.append({"when": {"water": values}, "apply": {"model": "dohy:block/windtrap_tank_%d" % level}})
    write_json({"multipart": multipart}, "blockstates", "windtrap.json")
    write_json({"model": {"type": "minecraft:model", "model": "dohy:block/windtrap"}}, "items", "windtrap.json")


# -- La borne EAU et la goutte du HUD --------------------------------------------------------------

def terminal():
    """L'ecran des bornes de Berskar, un nouveau pictogramme : une goutte et ses ronds."""
    source = Image.open(os.path.join(TEX, "block", "terminal_shuttle.png")).convert("RGBA")
    image = source.copy()
    screen = source.getpixel((3, 3))
    for y in range(3, 11):
        for x in range(3, 13):
            image.putpixel((x, y), screen)
    blue = (79, 184, 255)
    picto = [
        "....W.....",
        "...WVW....",
        "..WWVWW...",
        "..WWWWw...",
        "...Www....",
        ".w......w.",
        "..wwwwww..",
        "..........",
    ]
    colors = {"W": blue, "V": (214, 244, 255), "w": (40, 118, 196)}
    for dy, row in enumerate(picto):
        for dx, c in enumerate(row):
            if c in colors:
                image.putpixel((3 + dx, 3 + dy), colors[c] + (255,))
    accent = source.getpixel((12, 13))
    for y in range(12, 16):
        for x in range(16):
            if source.getpixel((x, y)) == accent:
                image.putpixel((x, y), blue + (255,))
    save(image, "block", "terminal_water.png")
    write_json({"parent": "minecraft:block/orientable", "textures": {
        "top": "dohy:block/terminal_top", "front": "dohy:block/terminal_water", "side": "dohy:block/terminal_side"}},
        "models", "block", "terminal_water.json")
    return image


def hud_drop():
    rows = [
        "....o....",
        "...oWo...",
        "..oWVWo..",
        "..oWVWo..",
        ".oWVWWwo.",
        ".oWWWWwo.",
        ".owWWwwo.",
        "..owwwo..",
        "...ooo...",
    ]
    colors = {"o": (14, 38, 72), "w": (34, 108, 188), "W": (80, 172, 242), "V": (206, 242, 255)}
    image = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in colors:
                image.putpixel((x, y), colors[c] + (255,))
    return save(image, "gui", "sprites", "hud", "water.png")


def item_models():
    for name in ("canteen", "stillsuit_mask", "stillsuit_suit", "stillsuit_leggings", "stillsuit_boots"):
        write_json({"parent": "minecraft:item/generated", "textures": {"layer0": "dohy:item/" + name}},
                   "models", "item", name + ".json")
        write_json({"model": {"type": "minecraft:model", "model": "dohy:item/" + name}}, "items", name + ".json")
    write_json({"layers": {"humanoid": [{"texture": "dohy:stillsuit"}],
                           "humanoid_leggings": [{"texture": "dohy:stillsuit"}]}}, "equipment", "stillsuit.json")


def terminal_blockstate():
    path = os.path.join(ASSETS, "blockstates", "terminal.json")
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    variants = data["variants"]
    for facing, y in (("north", None), ("east", 90), ("south", 180), ("west", 270)):
        entry = {"model": "dohy:block/terminal_water"}
        if y is not None:
            entry["y"] = y
        variants["facing=%s,kind=water" % facing] = entry
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(json.dumps(data, indent=2) + "\n")


def preview(folder, icons, layers, windtrap):
    os.makedirs(folder, exist_ok=True)
    sheet = Image.new("RGBA", (1400, 980), (64, 64, 64, 255))
    for i, image in enumerate(icons):
        sheet.alpha_composite(image.resize((160, 160), Image.NEAREST), (10 + i * 172, 10))
    for i, image in enumerate(layers):
        sheet.alpha_composite(image.resize((512, 256), Image.NEAREST), (10 + i * 530, 190))
    for i, image in enumerate(windtrap):
        sheet.alpha_composite(image.resize((112, 112), Image.NEAREST), (10 + (i % 11) * 122, 470 + (i // 11) * 122))
    sheet.save(os.path.join(folder, "eau_textures.png"))


def main():
    icons = [canteen()] + stillsuit_icons() + [terminal(), hud_drop().resize((16, 16), Image.NEAREST)]
    layers = list(stillsuit_layers())
    windtrap = windtrap_textures()
    windtrap_models()
    item_models()
    terminal_blockstate()
    if len(sys.argv) > 1:
        preview(sys.argv[1], icons, layers, windtrap)


if __name__ == "__main__":
    main()

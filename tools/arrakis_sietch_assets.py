#!/usr/bin/env python3
"""Les textures et modeles des sietchs d'Arrakis.

    python3 -I tools/arrakis_sietch_assets.py [dossier_apercu]

* les habitants : huit peaux originales (64 x 64, disposition HumanoidModel) — le porteur
  d'eau, l'artisan, l'equipeur, l'ancienne, et quatre habitants sans poste : une robe du
  desert sur le distille, un capuchon, un voile ou un masque, les yeux bleus de l'epice ;
* le paracompas : trente-deux images de l'aiguille, et sa definition (comme la boussole :
  l'aiguille suit la cible, tourne sans fin sans elle) ;
* le krys, le cafe d'epice ;
* la gourde de sietch et le distille de sietch : ceux d'Arrakeen, reteints aux couleurs des
  sietchs (terre brune, liseres rouille, tubes turquoise), objets et armure portee.

La maniere de PalaMod : peu de tons par matiere, la lumiere d'en haut a gauche, des contours
d'un ton sombre de la matiere, des reflets francs. Un dessin propre au mod.
"""

import colorsys
import zlib
import json
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dohy")
TEX = os.path.join(ASSETS, "textures")


def write_json(data, *path):
    target = os.path.join(ASSETS, *path)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, "w", encoding="utf-8") as fh:
        fh.write(json.dumps(data, indent=2) + "\n")


def shade(color, f):
    return tuple(max(0, min(255, int(round(c * f)))) for c in color)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


# -- Les habitants ----------------------------------------------------------------------------------

class Skin:
    def __init__(self, seed):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.rng = random.Random(seed)

    def px(self, x, y, color, jitter=5):
        j = self.rng.randint(-jitter, jitter) if jitter else 0
        self.img.putpixel((x, y), tuple(max(0, min(255, c + j)) for c in color) + (255,))

    def rect(self, x0, y0, x1, y1, color, jitter=5):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.px(x, y, color, jitter)

    def box(self, u, v, w, h, d, color, side=0.88, back=0.8, top=1.08, bottom=0.7):
        """Les six faces d'un cube de la peau, ombrees : dessus clair, cotes et dos plus sombres."""
        self.rect(u + d, v, u + d + w, v + d, shade(color, top))
        self.rect(u + d + w, v, u + d + w + w, v + d, shade(color, bottom))
        self.rect(u, v + d, u + d, v + d + h, shade(color, side))
        self.rect(u + d, v + d, u + d + w, v + d + h, color)
        self.rect(u + d + w, v + d, u + d + w + d, v + d + h, shade(color, side * 0.95))
        self.rect(u + d + w + d, v + d, u + d + w + d + w, v + d + h, shade(color, back))


EYE_WHITE = (150, 186, 236)
EYE_IRIS = (36, 78, 196)
SUIT = (64, 62, 60)
SUIT_SEAM = (42, 40, 40)
TUBE = (70, 168, 176)
BOOT = (70, 50, 34)
SOLE = (44, 32, 24)
BELT = (96, 58, 32)
BUCKLE = (206, 190, 150)

DWELLERS = {
    # nom : peau, cheveux, robe, liseret, voile ("wrap", "mask", None), particularite
    "water": dict(skin=(168, 120, 84), hair=(40, 30, 26), cloak=(196, 170, 122), trim=(56, 110, 190),
                  face="wrap", extra="sash"),
    "artisan": dict(skin=(150, 104, 70), hair=(30, 24, 22), cloak=(128, 92, 60), trim=(170, 120, 70),
                    face=None, extra="apron"),
    "outfitter": dict(skin=(184, 136, 98), hair=(54, 36, 26), cloak=(170, 104, 56), trim=(222, 196, 142),
                      face="wrap", extra="stripes"),
    "elder": dict(skin=(160, 116, 86), hair=(176, 172, 166), cloak=(70, 54, 44), trim=(196, 150, 70),
                  face=None, extra="elder"),
    "dweller_0": dict(skin=(124, 86, 60), hair=(26, 22, 20), cloak=(186, 160, 116), trim=(130, 100, 66),
                      face="wrap", extra=None),
    "dweller_1": dict(skin=(176, 128, 92), hair=(46, 32, 24), cloak=(150, 80, 50), trim=(206, 170, 110),
                      face=None, extra=None),
    "dweller_2": dict(skin=(196, 150, 112), hair=(62, 44, 30), cloak=(118, 108, 94), trim=(176, 150, 104),
                      face="wrap", extra=None),
    "dweller_3": dict(skin=(140, 98, 68), hair=(34, 26, 22), cloak=(92, 70, 52), trim=(196, 168, 116),
                      face="mask", extra=None),
}


def paint(name, v):
    s = Skin(zlib.crc32(name.encode()))
    skin, hair, cloak, trim = v["skin"], v["hair"], v["cloak"], v["trim"]
    # -- la tete : peau, cheveux dessus et derriere, les yeux bleus
    s.box(0, 0, 8, 8, 8, skin)
    s.rect(8, 0, 16, 8, hair)
    s.rect(24, 8, 32, 16, hair)
    s.rect(0, 8, 8, 11, hair)
    s.rect(16, 8, 24, 11, hair)
    s.rect(8, 8, 16, 10, hair)
    for x in (9, 13):
        s.px(x, 11, shade(hair, 0.9), 2)
        s.px(x + 1, 11, shade(hair, 0.9), 2)
    # les yeux bleus de l'epice : le blanc dehors, l'iris vers le nez
    s.px(9, 12, EYE_WHITE, 0)
    s.px(10, 12, EYE_IRIS, 0)
    s.px(13, 12, EYE_IRIS, 0)
    s.px(14, 12, EYE_WHITE, 0)
    s.px(11, 13, shade(skin, 0.82), 2)
    s.px(12, 13, shade(skin, 0.86), 2)
    s.rect(10, 14, 14, 15, shade(skin, 0.7), 2)
    if v["extra"] == "elder":
        for (x, y) in ((9, 13), (14, 13), (10, 15), (13, 15)):
            s.px(x, y, shade(skin, 0.78), 2)
    if v["face"] == "mask":
        s.rect(9, 13, 15, 16, SUIT, 3)
        s.rect(10, 14, 14, 15, SUIT_SEAM, 2)
        s.px(15, 14, TUBE, 0)
        s.px(15, 15, TUBE, 0)
        s.rect(16, 13, 19, 14, TUBE, 0)
    # -- le capuchon (calque du chapeau) : tout le tour, et un cadre autour du visage
    hood = cloak
    s.box(32, 0, 8, 8, 8, hood)
    for y in range(8, 16):
        for x in range(41, 47):
            s.img.putpixel((x, y), (0, 0, 0, 0))
    s.rect(40, 8, 48, 9, shade(hood, 1.05))
    s.rect(40, 9, 41, 16, shade(hood, 0.92))
    s.rect(47, 9, 48, 16, shade(hood, 0.85))
    for y in range(9, 16):
        s.px(41, y, shade(trim, 0.95), 3)
        s.px(46, y, shade(trim, 0.85), 3)
    s.rect(41, 9, 47, 10, shade(trim, 1.0), 3)
    if v["extra"] == "elder":
        for x in (42, 45):
            s.px(x, 10, hair, 3)
    if v["face"] == "wrap":
        wrap = mix(cloak, (230, 220, 196), 0.25)
        s.rect(41, 13, 47, 16, wrap, 4)
        for x in range(41, 47):
            s.px(x, 13, shade(wrap, 1.08), 3)
        s.px(44, 15, shade(wrap, 0.85), 2)
    # des plis sur le capuchon
    for y in range(9, 16, 2):
        s.px(58, y, shade(hood, 0.7), 2)
        s.px(61, y + 1, shade(hood, 0.72), 2)
        s.px(34, y, shade(hood, 0.74), 2)
    # -- le corps : le distille dessous, la robe ouverte dessus, la ceinture
    s.box(16, 16, 8, 12, 4, cloak)
    s.rect(23, 20, 25, 26, SUIT, 3)
    for y in range(20, 26):
        s.px(23, y, SUIT_SEAM, 2)
    s.px(24, 21, TUBE, 0)
    s.px(24, 22, TUBE, 0)
    for y in range(20, 32):
        s.px(22, y, shade(trim, 0.95), 3)
        s.px(25, y, shade(trim, 0.85), 3)
    s.rect(20, 26, 28, 27, BELT, 3)
    s.px(23, 26, BUCKLE, 0)
    s.px(24, 26, shade(BUCKLE, 0.8), 0)
    s.rect(32, 26, 40, 27, shade(BELT, 0.85), 3)
    s.rect(16, 26, 20, 27, shade(BELT, 0.9), 3)
    s.rect(28, 26, 32, 27, shade(BELT, 0.88), 3)
    for x in (34, 37):
        for y in range(21, 32, 2):
            s.px(x, y, shade(cloak, 0.66), 2)
    if v["extra"] == "sash":
        for k in range(8):
            x, y = 20 + k, 20 + k
            if y < 26:
                s.px(x, y, trim, 3)
                s.px(min(27, x + 1), y, shade(trim, 0.8), 3)
        s.rect(26, 27, 28, 29, (90, 150, 210), 3)
        s.px(26, 27, (160, 200, 240), 0)
    if v["extra"] == "apron":
        s.rect(21, 22, 27, 32, (82, 54, 34), 4)
        for y in range(22, 32):
            s.px(21, y, (110, 74, 46), 3)
        for (x, y) in ((22, 27), (25, 27), (23, 29)):
            s.px(x, y, (224, 216, 196), 0)
        s.rect(21, 26, 27, 27, BELT, 2)
    if v["extra"] == "stripes":
        for y in (21, 24, 29):
            for x in list(range(16, 23)) + list(range(25, 40)):
                s.px(x, y, trim, 3)
    if v["extra"] == "elder":
        for y in range(20, 32):
            s.px(22, y, trim, 2)
            s.px(25, y, shade(trim, 0.85), 2)
        for x in range(20, 28):
            s.px(x, 31, trim, 2)
    # -- les bras : manches de robe, avant-bras de distille (ou nus pour l'artisan), mains
    s.box(40, 16, 4, 12, 4, cloak)
    forearm = skin if v["extra"] == "apron" else SUIT
    s.rect(40, 27, 56, 31, forearm, 3)
    s.rect(40, 31, 56, 32, skin, 3)
    s.rect(48, 16, 52, 20, skin, 3)
    s.rect(40, 26, 56, 27, shade(trim, 0.9), 3)
    if forearm == SUIT:
        s.px(45, 29, TUBE, 0)
    # -- les jambes : le bas de la robe, le pantalon du distille, les bottes
    s.box(0, 16, 4, 12, 4, cloak)
    s.rect(0, 25, 16, 30, SUIT, 3)
    s.rect(0, 30, 16, 32, BOOT, 3)
    s.rect(8, 16, 12, 20, SOLE, 2)
    s.rect(0, 24, 16, 25, shade(trim, 0.85), 3)
    for x in (5, 13):
        s.px(x, 27, SUIT_SEAM, 2)
    return s.img


# -- Le paracompas ------------------------------------------------------------------------------------

BRASS = [(70, 46, 18), (120, 82, 30), (170, 124, 50), (206, 164, 76), (238, 206, 120)]
FACE = [(150, 128, 92), (196, 176, 136), (224, 208, 170), (240, 230, 200)]
NEEDLE_TIP = [(24, 70, 150), (60, 130, 220), (150, 200, 250)]
NEEDLE_TAIL = [(70, 40, 30), (130, 70, 50)]


def paracompass(frame):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8.0, y + 0.5 - 8.5
            d = math.hypot(dx, dy)
            if d <= 6.6:
                lit = -(dx * 0.6 + dy * 0.8) / 6.6
                if d > 5.4:
                    c = BRASS[4] if lit > 0.45 else BRASS[3] if lit > 0.0 else BRASS[2] if lit > -0.5 else BRASS[1]
                    if d > 6.15 and lit < 0.2:
                        c = BRASS[0]
                else:
                    c = FACE[3] if lit > 0.5 else FACE[2] if lit > -0.4 else FACE[1]
                    if d > 4.8:
                        c = FACE[1] if lit > 0 else FACE[0]
                img.putpixel((x, y), c + (255,))
    # la belliere en haut
    for (x, y, c) in ((7, 1, BRASS[3]), (8, 1, BRASS[2]), (6, 2, BRASS[2]), (9, 2, BRASS[1])):
        img.putpixel((x, y), c + (255,))
    # les reperes des quatre vents
    for (x, y) in ((7, 4), (8, 4), (12, 8), (7, 13), (8, 13), (3, 8)):
        img.putpixel((x, y), FACE[0] + (255,))
    # l'aiguille : la pointe bleue vers la cible, la queue rouille ; image 16 = vers le haut
    angle = math.radians(((frame - 16) % 32) / 32.0 * 360.0)
    ox, oy = 8.0, 8.5
    ux, uy = math.sin(angle), -math.cos(angle)
    for i in range(1, 9):
        t = i / 2.0
        if t > 4.3:
            break
        x, y = int(math.floor(ox + ux * t)), int(math.floor(oy + uy * t))
        tone = NEEDLE_TIP[1] if t < 3.5 else NEEDLE_TIP[2]
        img.putpixel((x, y), tone + (255,))
    for i in range(1, 6):
        t = i / 2.0
        if t > 2.6:
            break
        x, y = int(math.floor(ox - ux * t)), int(math.floor(oy - uy * t))
        img.putpixel((x, y), NEEDLE_TAIL[1 if t < 2 else 0] + (255,))
    img.putpixel((7, 8), BRASS[3] + (255,))
    return img


def paracompass_definition():
    entries = []
    for k in range(33):
        entries.append({"model": {"type": "minecraft:model", "model": "dohy:item/paracompass_%02d" % ((16 + k) % 32)},
                        "threshold": 0.0 if k == 0 else k - 0.5})
    spin = []
    for k in range(33):
        spin.append({"model": {"type": "minecraft:model", "model": "dohy:item/paracompass_%02d" % ((16 + k) % 32)},
                     "threshold": 0.0 if k == 0 else k - 0.5})
    return {"model": {
        "type": "minecraft:condition",
        "property": "minecraft:has_component",
        "component": "minecraft:lodestone_tracker",
        "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": "lodestone",
                    "scale": 32.0, "entries": entries},
        "on_false": {"type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": "none",
                     "scale": 32.0, "entries": spin},
    }}


# -- Le krys, le cafe d'epice ---------------------------------------------------------------------------

KRYS = [
    "...............5",
    "..............54",
    ".............543",
    "............5432",
    "...........5432.",
    "..........5432..",
    ".........5432...",
    "........5432....",
    ".......5432.....",
    "..g...5432......",
    "..Gg.5432.......",
    "...GBb32........",
    "...lRb..........",
    "..lLr...........",
    ".lLr............",
    "Lr..............",
]
KRYS_PAL = {"5": (250, 248, 238), "4": (226, 222, 208), "3": (188, 184, 172), "2": (132, 128, 122),
            "g": (230, 200, 120), "G": (170, 130, 60), "B": (236, 228, 206), "b": (176, 168, 148),
            "l": (150, 96, 54), "L": (104, 64, 34), "r": (70, 42, 22), "R": (124, 80, 44)}

COFFEE = [
    "......4.........",
    ".....4..4.......",
    "......4.4.......",
    ".......4........",
    "....rrrrrrr.....",
    "...rCCcCCCcr....",
    "...ROOccOOcR....",
    "...RhOOOOOdR....",
    "...RhOOOOOdR....",
    "...RhOOOOOdR....",
    "....RhOOOdR.....",
    "....RhOOOdR.....",
    ".....RRRRR......",
    "....DDDDDDD.....",
    "................",
    "................",
]
COFFEE_PAL = {"4": (230, 226, 216), "r": (120, 64, 38), "C": (70, 40, 24), "c": (196, 110, 40),
              "R": (96, 50, 30), "O": (190, 106, 62), "h": (226, 150, 96), "d": (140, 74, 44),
              "D": (60, 44, 34)}


def sprite(rows, pal):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), pal[ch] + (255,))
    return img


# -- Les reteintes du sietch ------------------------------------------------------------------------------

def sietch_tint(img):
    """Reteint un objet d'Arrakeen aux couleurs des sietchs : le sable devient terre brune, le
    bleu des tubes turquoise, le cuir rouille, le laiton os."""
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            deg = h * 360.0
            if s > 0.35 and 180.0 <= deg <= 250.0:
                h2, s2, v2 = 176.0 / 360.0, min(1.0, s * 0.9), v * 0.95
            elif s > 0.45 and 30.0 <= deg <= 55.0 and v > 0.6:
                h2, s2, v2 = 40.0 / 360.0, s * 0.35, min(1.0, v * 1.02)
            elif s > 0.3 and (deg < 30.0 or deg > 340.0):
                h2, s2, v2 = 12.0 / 360.0, min(1.0, s * 1.25), v * 0.92
            elif s < 0.4 and v > 0.35:
                h2, s2, v2 = 28.0 / 360.0, min(1.0, s * 1.35 + 0.08), v * 0.72
            else:
                h2, s2, v2 = h, s, v * 0.85
            r2, g2, b2 = colorsys.hsv_to_rgb(h2, s2, v2)
            px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    return out


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    skins = os.path.join(TEX, "entity", "sietch")
    os.makedirs(skins, exist_ok=True)
    painted = {}
    for name, v in DWELLERS.items():
        img = paint(name, v)
        img.save(os.path.join(skins, name + ".png"))
        painted[name] = img
    # le paracompas
    for k in range(32):
        paracompass(k).save(os.path.join(TEX, "item", "paracompass_%02d.png" % k))
        write_json({"parent": "minecraft:item/generated", "textures": {"layer0": "dohy:item/paracompass_%02d" % k}},
                   "models", "item", "paracompass_%02d.json" % k)
    write_json(paracompass_definition(), "items", "paracompass.json")
    # le krys, le cafe
    sprite(KRYS, KRYS_PAL).save(os.path.join(TEX, "item", "krys.png"))
    write_json({"parent": "minecraft:item/handheld", "textures": {"layer0": "dohy:item/krys"}}, "models", "item",
               "krys.json")
    write_json({"model": {"type": "minecraft:model", "model": "dohy:item/krys"}}, "items", "krys.json")
    sprite(COFFEE, COFFEE_PAL).save(os.path.join(TEX, "item", "spice_coffee.png"))
    simple = ["spice_coffee"]
    # les reteintes : la gourde, le distille (objets), l'armure portee
    for src, dst in (("canteen", "sietch_canteen"), ("stillsuit_mask", "sietch_mask"),
                     ("stillsuit_suit", "sietch_suit"), ("stillsuit_leggings", "sietch_leggings"),
                     ("stillsuit_boots", "sietch_boots")):
        sietch_tint(Image.open(os.path.join(TEX, "item", src + ".png")).convert("RGBA")).save(
            os.path.join(TEX, "item", dst + ".png"))
        simple.append(dst)
    for name in simple:
        write_json({"parent": "minecraft:item/generated", "textures": {"layer0": "dohy:item/" + name}},
                   "models", "item", name + ".json")
        write_json({"model": {"type": "minecraft:model", "model": "dohy:item/" + name}}, "items", name + ".json")
    for layer in ("humanoid", "humanoid_leggings"):
        src = os.path.join(TEX, "entity", "equipment", layer, "stillsuit.png")
        sietch_tint(Image.open(src).convert("RGBA")).save(
            os.path.join(TEX, "entity", "equipment", layer, "sietch_stillsuit.png"))
    write_json({"layers": {"humanoid": [{"texture": "dohy:sietch_stillsuit"}],
                           "humanoid_leggings": [{"texture": "dohy:sietch_stillsuit"}]}},
               "equipment", "sietch_stillsuit.json")
    if preview:
        os.makedirs(preview, exist_ok=True)
        s = 8
        sheet = Image.new("RGBA", (len(painted) * 66 * s // 2, 34 * s // 2 + 20 * s), (60, 60, 60, 255))
        for i, (name, img) in enumerate(painted.items()):
            sheet.alpha_composite(img.crop((0, 0, 64, 32)).resize((64 * s // 2, 32 * s // 2), Image.NEAREST),
                                  (i * 66 * s // 2, 0))
        items = [paracompass(16), paracompass(20), paracompass(24), sprite(KRYS, KRYS_PAL),
                 sprite(COFFEE, COFFEE_PAL)] + [Image.open(os.path.join(TEX, "item", n + ".png")).convert("RGBA")
                                                for n in simple[1:]]
        for i, img in enumerate(items):
            sheet.alpha_composite(img.resize((16 * s, 16 * s), Image.NEAREST), (i * 18 * s, 34 * s // 2 + 2 * s))
        sheet.save(os.path.join(preview, "sietch_assets.png"))


if __name__ == "__main__":
    main()

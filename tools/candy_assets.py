#!/usr/bin/env python3
"""Les bonbons de metier : textures (16 x 16), modeles, objets, noms.

    python3 -I tools/candy_assets.py <dossier des textures fournies>
    python3 -I tools/candy_assets.py            (textures dessinees ici)

Les textures sont celles que le joueur a fournies (2026-11-15, mod et depot
prives) : le dossier textures/item du portage PalaMod 1.21. candy_green,
candy_yellow, candy_blue et candy_rainbow gardent leur nom ; candy_pink devient
candy_purple (le mauve de l'Alchimiste), candy devient candy_wrapped (le
bonbon a deballer).

Sans dossier, le script dessine ses propres textures : un bonbon dur dans son
papier tortille, une couleur par metier, le multicolore raye des quatre, le
bonbon a deballer dans un papier raye rouge et blanc.
"""
import shutil
import sys
import json
import os
import struct
import zlib

RES = "src/main/resources/assets/palamod"

# contour, sombre, moyen, clair, reflet ; papier clair, papier ombre
PALETTES = {
    "candy_green": ((27, 94, 32), (46, 125, 50), (76, 175, 80), (129, 199, 132), (232, 255, 232),
                    (215, 238, 216), (170, 214, 172)),
    "candy_yellow": ((128, 92, 8), (230, 150, 20), (253, 210, 50), (255, 236, 110), (255, 253, 235),
                     (255, 247, 205), (250, 225, 120)),
    "candy_blue": ((13, 60, 140), (21, 101, 192), (66, 165, 245), (144, 202, 249), (232, 245, 255),
                   (205, 228, 250), (150, 196, 240)),
    "candy_purple": ((70, 20, 120), (106, 27, 154), (171, 71, 188), (206, 147, 216), (246, 232, 250),
                     (232, 205, 238), (196, 150, 210)),
}
RAINBOW = [(76, 175, 80), (253, 210, 50), (66, 165, 245), (171, 71, 188)]

NAMES = {
    "candy_green": ("Bonbon vert", "Green Candy"),
    "candy_yellow": ("Bonbon jaune", "Yellow Candy"),
    "candy_blue": ("Bonbon bleu", "Blue Candy"),
    "candy_purple": ("Bonbon mauve", "Purple Candy"),
    "candy_rainbow": ("Bonbon multicolore", "Rainbow Candy"),
    "candy_wrapped": ("Bonbon a deballer", "Wrapped Candy"),
}
LANG = {
    "fr_fr": {
        "tooltip.palamod.candy": "+%s XP de %s",
        "tooltip.palamod.candy.all": "+%s XP dans chaque metier",
        "tooltip.palamod.candy.hint": "Clic droit : le croquer. Accroupi : toute la pile.",
        "tooltip.palamod.candy_wrapped": "Un bonbon de metier au hasard, parfois multicolore",
        "tooltip.palamod.candy_wrapped.hint": "Clic droit : le deballer.",
        "message.palamod.candy": "[Bonbon] +%s XP de %s",
        "message.palamod.candy_wrapped": "[Bonbon] Dans le papier : %s !",
    },
    "en_us": {
        "tooltip.palamod.candy": "+%s %s XP",
        "tooltip.palamod.candy.all": "+%s XP in every job",
        "tooltip.palamod.candy.hint": "Right-click: crunch it. Sneak: the whole stack.",
        "tooltip.palamod.candy_wrapped": "A random job candy, sometimes a rainbow one",
        "tooltip.palamod.candy_wrapped.hint": "Right-click: unwrap it.",
        "message.palamod.candy": "[Candy] +%s %s XP",
        "message.palamod.candy_wrapped": "[Candy] Inside the wrapper: %s!",
    },
}


def shape():
    """Les cases du dessin : 'b' bonbon, 'p' papier, '.' vide."""
    grid = [["."] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 3.7 ** 2:
                grid[y][x] = "b"
            elif x <= 3 or x >= 12:
                edge = 3 - x if x <= 3 else x - 12
                half = 1.0 + edge * 1.05
                if abs(y - 7.5) <= half + 0.5:
                    grid[y][x] = "p"
    return grid


def outline(grid, x, y):
    if grid[y][x] == ".":
        return False
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, ny = x + dx, y + dy
        if not (0 <= nx < 16 and 0 <= ny < 16) or grid[ny][nx] == ".":
            return True
    return False


def draw(name):
    grid = shape()
    pixels = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            cell = grid[y][x]
            if cell == ".":
                continue
            if name in PALETTES:
                line, dark, mid, light, shine, paper, shade = PALETTES[name]
            elif name == "candy_rainbow":
                line, paper, shade = (60, 36, 72), (246, 246, 250), (214, 214, 226)
                dark = mid = light = shine = None
            else:
                line, paper, shade = (120, 20, 20), (255, 248, 228), (236, 220, 190)
                dark = mid = light = shine = None
            if outline(grid, x, y):
                color = line
            elif cell == "b":
                d = (x - 7.5) + (y - 7.5)
                if name == "candy_rainbow":
                    color = RAINBOW[((x + y) // 2) % 4]
                    if d < -3.5:
                        color = tuple(min(255, c + 60) for c in color)
                    elif d > 3:
                        color = tuple(int(c * 0.7) for c in color)
                elif name == "candy_wrapped":
                    color = (229, 57, 53) if ((x + y) // 2) % 2 == 0 else (255, 248, 228)
                    if d > 3:
                        color = tuple(int(c * 0.82) for c in color)
                else:
                    color = light if d < -2.5 else dark if d > 2.5 else mid
                if (x, y) in ((5, 5), (6, 5), (5, 6)) and name != "candy_wrapped":
                    color = shine or (255, 255, 255)
            else:
                # Le papier tortille : une colonne sur deux dans l'ombre, la plus
                # proche du bonbon serree comme un noeud.
                near = x in (3, 12)
                if name == "candy_wrapped" and near:
                    color = (255, 193, 7)
                else:
                    color = shade if (x % 2 == 0) == (x <= 3) else paper
                if name == "candy_wrapped" and x in (0, 15) and not near:
                    color = (229, 57, 53)
                if name == "candy_rainbow" and x in (0, 15):
                    color = RAINBOW[y % 4]
            pixels[y][x] = color + (255,) if len(color) == 3 else color
    return pixels


def png(path, pixels):
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    data = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    with open(path, "wb") as handle:
        handle.write(data)


def write_json(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(value, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


# Texture fournie -> objet de DoHy.
PROVIDED = {
    "candy_green": "candy_green.png", "candy_yellow": "candy_yellow.png", "candy_blue": "candy_blue.png",
    "candy_purple": "candy_pink.png", "candy_rainbow": "candy_rainbow.png", "candy_wrapped": "candy.png",
}


def main():
    source = sys.argv[1] if len(sys.argv) > 1 else None
    for name in NAMES:
        target = os.path.join(RES, "textures/item/%s.png" % name)
        if source:
            shutil.copyfile(os.path.join(source, PROVIDED[name]), target)
        else:
            png(target, draw(name))
        write_json(os.path.join(RES, "models/item/%s.json" % name),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "palamod:item/%s" % name}})
        write_json(os.path.join(RES, "items/%s.json" % name),
                   {"model": {"type": "minecraft:model", "model": "palamod:item/%s" % name}})
    for code, entries in LANG.items():
        path = os.path.join(RES, "lang/%s.json" % code)
        with open(path, encoding="utf-8") as handle:
            lang = json.load(handle)
        for name, labels in NAMES.items():
            lang["item.palamod.%s" % name] = labels[0 if code == "fr_fr" else 1]
        lang.update(entries)
        write_json(path, lang)
    print("bonbons : %d textures, modeles, objets et noms" % len(NAMES))


if __name__ == "__main__":
    main()

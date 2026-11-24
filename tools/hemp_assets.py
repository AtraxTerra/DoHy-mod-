#!/usr/bin/env python3
"""Les icones du chanvre de NTM NEXT dans DoHy : fibre, graines, toile (16 x 16).

    python3 -I tools/hemp_assets.py [dossier_apercu]

Un dessin propre au mod, a la maniere de PalaMod : peu de tons par matiere, la lumiere en haut
a gauche, un contour d'un ton sombre de la matiere. Ecrit dans
src/main/resources/assets/dohy/textures/item/ ; avec un dossier en argument, un apercu agrandi.
"""

import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "dohy", "textures", "item")


def canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img, x, y, color):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(color) + (255,))


def outline(img, color):
    """Un contour sombre autour de la forme, sur les pixels vides qui la touchent."""
    solid = [[img.getpixel((x, y))[3] > 0 for x in range(16)] for y in range(16)]
    for y in range(16):
        for x in range(16):
            if solid[y][x]:
                continue
            near = any(0 <= x + dx < 16 and 0 <= y + dy < 16 and solid[y + dy][x + dx]
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if near:
                put(img, x, y, color)


def fiber():
    """Un echeveau de fibre en diagonale : quatre brins de tons voisins, deux liens, les bouts ecartes."""
    img = canvas()
    tones = {-3: (226, 216, 164), -2: (222, 212, 158), -1: (198, 188, 130), 0: (168, 178, 108),
             1: (150, 136, 86), 2: (140, 126, 80)}
    for y in range(16):
        for x in range(16):
            d = x + y - 15      # l'ecart a l'axe du brin
            t = x - y           # la place le long du brin
            if -11 <= t <= 11 and -2 <= d <= 1:
                put(img, x, y, tones[d])
            elif 11 < abs(t) <= 14 and -3 <= d <= 2 and d % 2 == 0:
                put(img, x, y, tones[d])
            if t in (-5, -4, 4, 5) and -2 <= d <= 1:
                put(img, x, y, (112, 78, 46) if t % 2 == 0 else (86, 58, 34))
    outline(img, (84, 74, 42))
    return img


def seeds():
    """Un petit tas de graines grises et brunes, marbrees."""
    img = canvas()
    base, dark, light, vein = (132, 118, 88), (88, 76, 56), (184, 168, 128), (104, 92, 66)
    grains = [
        (3, 11), (6, 12), (9, 11), (12, 12), (4, 8), (7, 9), (10, 8), (6, 5), (9, 6), (8, 3),
        (2, 13), (11, 9), (13, 10),
    ]
    for gx, gy in grains:
        # une graine : un ovale de 3 x 2, plus clair en haut a gauche
        for dx, dy, c in ((0, 0, light), (1, 0, base), (2, 0, base), (0, 1, base), (1, 1, vein), (2, 1, dark)):
            put(img, gx + dx, gy + dy, c)
    for x, y in ((4, 12), (7, 13), (10, 12), (8, 10), (7, 6), (9, 4)):
        put(img, x, y, (156, 140, 102))
    outline(img, (58, 50, 36))
    return img


def canvas_cloth():
    """Un rouleau de toile : le cylindre, sa tranche roulee a droite, le pan qui pend dessous."""
    img = canvas()
    rows = {4: (232, 220, 182), 5: (220, 208, 168), 6: (212, 198, 156), 7: (206, 192, 150),
            8: (198, 184, 142), 9: (188, 174, 132), 10: (176, 162, 120), 11: (160, 146, 106)}
    for y in range(4, 12):
        for x in range(1, 12):
            c = rows[y]
            if x % 3 == 0:
                c = tuple(max(0, v - 10) for v in c)
            put(img, x, y, c)
    # la tranche, roulee en spirale
    for y in range(3, 13):
        for x in range(10, 15):
            e = ((x - 12) / 2.2) ** 2 + ((y - 7.5) / 4.4) ** 2
            if e <= 1.0:
                ring = int(e * 6) % 2 == 0
                put(img, x, y, (186, 170, 128) if ring else (214, 200, 160))
    put(img, 12, 7, (120, 106, 72))
    put(img, 12, 8, (120, 106, 72))
    # le pan deroule, frange en bas
    for y in range(12, 15):
        for x in range(3, 10):
            put(img, x, y, (204, 190, 148) if (x + y) % 2 else (190, 176, 134))
    for x in range(3, 10, 2):
        put(img, x, 15, (196, 182, 140))
    outline(img, (112, 98, 66))
    return img


ICONS = [("hemp_fiber", fiber), ("hemp_seeds", seeds), ("hemp_canvas", canvas_cloth)]


def main():
    os.makedirs(OUT, exist_ok=True)
    images = []
    for name, painter in ICONS:
        img = painter()
        img.save(os.path.join(OUT, name + ".png"))
        images.append(img)
    print(f"{len(images)} icones ecrites dans {os.path.relpath(OUT, ROOT)}")
    if len(sys.argv) > 1:
        sheet = Image.new("RGBA", (16 * 12 * len(images) + 24 * len(images), 16 * 12 + 24), (52, 54, 60, 255))
        for index, img in enumerate(images):
            big = img.resize((16 * 12, 16 * 12), Image.NEAREST)
            sheet.alpha_composite(big, (12 + index * (16 * 12 + 24), 12))
        os.makedirs(sys.argv[1], exist_ok=True)
        sheet.save(os.path.join(sys.argv[1], "hemp_icons.png"))


if __name__ == "__main__":
    main()

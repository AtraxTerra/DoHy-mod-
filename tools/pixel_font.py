"""Rasterise la police pixelnes en police bitmap de Minecraft, un texel par pixel de police.

La police est dessinee sur une grille de 200 unites (2048 par em) : on echantillonne
le contour au centre de chaque pixel, ce qui rend la grille exacte.

    python3 tools/pixel_font.py src/main/resources/assets/dohy/font/pixelnes.otf \
        src/main/resources/assets/dohy/textures/font/pixel.png src/main/resources/assets/dohy/font/pixel.json

Une police vectorielle a pixels est rendue floue ou trouee hors des tailles entieres :
la police bitmap, dessinee a un nombre entier de pixels physiques par texel (Ui.snap),
reste nette a toutes les echelles.
"""
import json
import sys
from fontTools.ttLib import TTFont
from fontTools.pens.basePen import BasePen
from PIL import Image

SRC, OUT_PNG, OUT_JSON = sys.argv[1], sys.argv[2], sys.argv[3]
GRID = 200.0
ASCENT_ROWS = 10      # lignes au-dessus de la ligne de base
CELL_H = 12
CELL_W = 12
COLS = 16


class Flatten(BasePen):
    def __init__(self, glyphset):
        super().__init__(glyphset)
        self.contours = []
        self.cur = None

    def _moveTo(self, p):
        self.cur = [p]
        self.contours.append(self.cur)

    def _lineTo(self, p):
        self.cur.append(p)

    def _curveToOne(self, p1, p2, p3):
        p0 = self.cur[-1]
        for i in range(1, 9):
            t = i / 8.0
            mt = 1 - t
            x = mt ** 3 * p0[0] + 3 * mt * mt * t * p1[0] + 3 * mt * t * t * p2[0] + t ** 3 * p3[0]
            y = mt ** 3 * p0[1] + 3 * mt * mt * t * p1[1] + 3 * mt * t * t * p2[1] + t ** 3 * p3[1]
            self.cur.append((x, y))

    def _qCurveToOne(self, p1, p2):
        p0 = self.cur[-1]
        for i in range(1, 9):
            t = i / 8.0
            mt = 1 - t
            self.cur.append((mt * mt * p0[0] + 2 * mt * t * p1[0] + t * t * p2[0],
                             mt * mt * p0[1] + 2 * mt * t * p1[1] + t * t * p2[1]))

    def _closePath(self):
        pass


def winding(contours, x, y):
    wn = 0
    for c in contours:
        n = len(c)
        for i in range(n):
            x0, y0 = c[i]
            x1, y1 = c[(i + 1) % n]
            if y0 <= y:
                if y1 > y and (x1 - x0) * (y - y0) - (x - x0) * (y1 - y0) > 0:
                    wn += 1
            elif y1 <= y and (x1 - x0) * (y - y0) - (x - x0) * (y1 - y0) < 0:
                wn -= 1
    return wn


font = TTFont(SRC)
glyphs = font.getGlyphSet()
cmap = font.getBestCmap()
wanted = [c for c in range(0x21, 0x100) if c in cmap and c != 0xAD and c != 0xA0]
wanted += [c for c in (0x2022, 0x2026, 0x203A, 0x2039, 0x2014, 0x2013, 0x2019) if c in cmap]
rows = (len(wanted) + COLS - 1) // COLS
image = Image.new("RGBA", (COLS * CELL_W, rows * CELL_H), (0, 0, 0, 0))
chars = []
widest = 0
for index, code in enumerate(wanted):
    pen = Flatten(glyphs)
    glyphs[cmap[code]].draw(pen)
    col, row = index % COLS, index // COLS
    used = 0
    for j in range(CELL_H):
        for i in range(CELL_W):
            x = (i + 0.5) * GRID
            y = (ASCENT_ROWS - j - 0.5) * GRID
            if pen.contours and winding(pen.contours, x, y) != 0:
                image.putpixel((col * CELL_W + i, row * CELL_H + j), (255, 255, 255, 255))
                used = max(used, i + 1)
    widest = max(widest, used)
    if row >= len(chars):
        chars.append("")
    chars[row] += chr(code)
chars[-1] = chars[-1] + "\u0000" * (COLS - len(chars[-1]))
image.save(OUT_PNG)
space = round(glyphs[cmap[0x20]].width / GRID * 0.75)
definition = {"providers": [
    {"type": "space", "advances": {" ": space, " ": space}},
    {"type": "bitmap", "file": "dohy:font/pixel.png", "ascent": 7, "height": 9, "chars": chars},
    {"type": "reference", "id": "minecraft:default"}]}
json.dump(definition, open(OUT_JSON, "w", encoding="utf-8"), ensure_ascii=True, indent=2)
print("glyphes", len(wanted), "rangees", rows, "plus large", widest, "espace", space)

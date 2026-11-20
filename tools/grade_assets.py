"""Genere les quatre insignes des grades (16 x 16) et leurs fichiers d'objet.

Usage : python3 -I tools/grade_assets.py <racine du depot> [apercu.png]

Les insignes sont les icones des grades dans les menus (boutique, page des grades, barre
laterale). Chacun est dessine ici, pixel par pixel, a partir de formes simples : un ecu pour
les trois grades (une epee pour Heros, une etoile pour Legende, une gemme couronnee pour
Divinite), une medaille etincelante pour Premium. Rien n'est copie d'un autre jeu ou d'un
autre mod, ni des logos de Paladium. Les noms sont dans les fichiers de langue de dohy.
"""
import json
import math
import os
import sys

from PIL import Image

ROOT = sys.argv[1]
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'dohy')
CLEAR = (0, 0, 0, 0)


def rgb(value, alpha=255):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, alpha)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


class Icon:
    def __init__(self):
        self.img = Image.new('RGBA', (16, 16), CLEAR)
        self.px = self.img.load()

    def put(self, x, y, color):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[x, y] = color

    def filled(self, x, y):
        return 0 <= x < 16 and 0 <= y < 16 and self.px[x, y][3] > 0

    def shape(self, inside, color):
        for y in range(16):
            for x in range(16):
                if inside(x + 0.5, y + 0.5):
                    self.px[x, y] = color

    def shade(self, inside, color):
        for y in range(16):
            for x in range(16):
                if self.filled(x, y) and inside(x + 0.5, y + 0.5):
                    self.px[x, y] = color

    def outline(self, color):
        edge = []
        for y in range(16):
            for x in range(16):
                if self.filled(x, y):
                    continue
                if any(self.filled(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    edge.append((x, y))
        for x, y in edge:
            self.px[x, y] = color


def shield(cx=8.0, top=2.0, width=10.0, straight=6.5, bottom=14.6):
    """Un ecu : bord superieur droit, flancs droits, puis une pointe arrondie."""
    half = width / 2.0

    def inside(x, y):
        if y < top or y > bottom:
            return False
        if y <= top + straight:
            return abs(x - cx) <= half
        # de la fin des flancs a la pointe : le flanc rentre en arc
        t = (y - top - straight) / (bottom - top - straight)
        return abs(x - cx) <= half * math.sqrt(max(0.0, 1.0 - t * t))
    return inside


def polygon(points):
    def inside(x, y):
        hit = False
        n = len(points)
        for i in range(n):
            x1, y1 = points[i]
            x2, y2 = points[(i + 1) % n]
            if (y1 > y) != (y2 > y):
                cross = x1 + (y - y1) * (x2 - x1) / (y2 - y1)
                if x < cross:
                    hit = not hit
        return hit
    return inside


def disk(cx, cy, r):
    return lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def star(cx, cy, outer, inner, tips=5, turn=-math.pi / 2):
    points = []
    for i in range(tips * 2):
        r = outer if i % 2 == 0 else inner
        a = turn + i * math.pi / tips
        points.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return polygon(points)


def crest(base):
    """L'ecu colore, eclaire en haut a gauche, avec son liseré clair et son trait sombre."""
    icon = Icon()
    color = rgb(base)
    light = mix(color, (255, 255, 255), 0.45)
    dark = mix(color, (0, 0, 0), 0.45)
    icon.shape(shield(), color)
    icon.shade(lambda x, y: x + y < 11.0, mix(color, (255, 255, 255), 0.22))
    icon.shade(lambda x, y: x - y > 4.5 or y > 12.0, mix(color, (0, 0, 0), 0.22))
    # le liseré interieur
    inner = shield(top=3.0, width=8.0, straight=5.5, bottom=13.4)
    for y in range(16):
        for x in range(16):
            if icon.filled(x, y) and not inner(x + 0.5, y + 0.5):
                icon.px[x, y] = light if x + y < 14 else dark
    icon.outline(rgb(0x1A1420))
    return icon


def heros():
    icon = crest(0x3FA9F5)
    steel = rgb(0xE8EEF4)
    shadow = rgb(0x9FB0C2)
    gold = rgb(0xF2C230)
    for y in range(4, 10):
        icon.put(7, y, steel)
        icon.put(8, y, shadow)
    icon.put(7, 3, steel)
    for x in range(5, 11):
        icon.put(x, 10, gold)
    icon.put(7, 11, rgb(0x7A4A22))
    icon.put(8, 11, rgb(0x5C3517))
    icon.put(7, 12, gold)
    icon.put(8, 12, gold)
    return icon


def pattern(icon, rows, ox, oy, colors):
    """Pose un motif ecrit en lettres : chaque lettre est une couleur, le point est vide."""
    for dy, row in enumerate(rows):
        for dx, char in enumerate(row):
            if char in colors:
                icon.put(ox + dx, oy + dy, colors[char])


STAR = [
    "...a...",
    "...a...",
    "aaaabbb",
    ".aaabb.",
    "..abb..",
    ".ab.bb.",
    ".a...b.",
]


def legende():
    icon = crest(0xFAC400)
    pattern(icon, STAR, 5, 4, {'a': rgb(0xFFFBE8), 'b': rgb(0xF9E08A)})
    return icon


def divinite():
    icon = crest(0xC86BFA)
    # une gemme taillee au centre
    gem = polygon([(8.0, 5.0), (11.2, 8.0), (8.0, 12.2), (4.8, 8.0)])
    icon.shade(gem, rgb(0xFFC2F1))
    icon.shade(lambda x, y: gem(x, y) and x > 8.0, rgb(0xF07BD6))
    icon.shade(lambda x, y: gem(x, y) and y < 8.0 and x <= 8.0, rgb(0xFFFFFF))
    # une couronne posee sur l'ecu
    gold = rgb(0xF2C230)
    for x in range(5, 11):
        icon.put(x, 1, gold)
    for x in (5, 8, 10):
        icon.put(x, 0, gold)
    icon.put(7, 0, gold)
    icon.put(7, 1, rgb(0xFF5A8A))
    return icon


SPARKLE = [
    "...a...",
    "...a...",
    "..aab..",
    "aaaabbb",
    "..abb..",
    "...b...",
    "...b...",
]


def premium():
    icon = Icon()
    color = rgb(0x2BE0A8)
    medal = polygon([(5.0, 1.5), (11.0, 1.5), (14.5, 5.0), (14.5, 11.0), (11.0, 14.5), (5.0, 14.5),
                     (1.5, 11.0), (1.5, 5.0)])
    icon.shape(medal, color)
    icon.shade(lambda x, y: x + y < 12.0, mix(color, (255, 255, 255), 0.25))
    icon.shade(lambda x, y: x + y > 20.0, mix(color, (0, 0, 0), 0.25))
    inner = polygon([(5.6, 2.8), (10.4, 2.8), (13.2, 5.6), (13.2, 10.4), (10.4, 13.2), (5.6, 13.2),
                     (2.8, 10.4), (2.8, 5.6)])
    for y in range(16):
        for x in range(16):
            if icon.filled(x, y) and not inner(x + 0.5, y + 0.5):
                icon.px[x, y] = mix(color, (255, 255, 255), 0.5) if x + y < 15 else mix(color, (0, 0, 0), 0.45)
    pattern(icon, SPARKLE, 5, 5, {'a': rgb(0xFFFFFF), 'b': rgb(0xC9FFEE)})
    icon.outline(rgb(0x10241E))
    return icon


BADGES = [
    ('grade_heros', heros),
    ('grade_legende', legende),
    ('grade_divinite', divinite),
    ('grade_premium', premium),
]


def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as handle:
        json.dump(payload, handle, indent=2)
        handle.write('\n')


def main():
    sheet = Image.new('RGBA', (16 * 8 * len(BADGES), 16 * 8), (40, 40, 48, 255))
    for index, (path, draw) in enumerate(BADGES):
        icon = draw()
        texture = os.path.join(ASSETS, 'textures', 'item', path + '.png')
        os.makedirs(os.path.dirname(texture), exist_ok=True)
        icon.img.save(texture)
        write_json(os.path.join(ASSETS, 'models', 'item', path + '.json'),
                   {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'dohy:item/' + path}})
        write_json(os.path.join(ASSETS, 'items', path + '.json'),
                   {'model': {'type': 'minecraft:model', 'model': 'dohy:item/' + path}})
        sheet.alpha_composite(icon.img.resize((128, 128), Image.NEAREST), (index * 128, 0))
    if len(sys.argv) > 2:
        sheet.save(sys.argv[2])
    print('insignes ecrits :', len(BADGES))


main()

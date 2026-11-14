"""Genere les douze reliques du pass mensuel (16 x 16) et leurs fichiers d'objet.

Usage : python3 -I tools/pass_assets.py <racine du depot> [apercu.png]

Chaque icone est dessinee ici, pixel par pixel, a partir de formes simples
(disques, ellipses, polygones), puis cernee d'un trait sombre : rien n'est
copie d'un autre jeu ou d'un autre mod. Les noms sont dans tools/dohy_assets.py
(RELICS), les effets dans pass/Relic.java.
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
        """Recolore les pixels deja peints qui satisfont la condition."""
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


def ellipse(cx, cy, rx, ry):
    return lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0


def disk(cx, cy, r):
    return ellipse(cx, cy, r, r)


def polygon(points):
    def inside(x, y):
        hit = False
        j = len(points) - 1
        for i in range(len(points)):
            xi, yi = points[i]
            xj, yj = points[j]
            if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
                hit = not hit
            j = i
        return hit
    return inside


def segment(x1, y1, x2, y2, width):
    def inside(x, y):
        dx, dy = x2 - x1, y2 - y1
        t = max(0.0, min(1.0, ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)))
        return math.hypot(x - (x1 + t * dx), y - (y1 + t * dy)) <= width / 2.0
    return inside


def crystal():
    icon = Icon()
    body = polygon([(8, 1), (13, 6), (11.5, 14), (4.5, 14), (3, 6)])
    icon.shape(body, rgb(0x7FD3FF))
    icon.shade(lambda x, y: x < 8 and y < 9, rgb(0xC9F0FF))
    icon.shade(lambda x, y: x > 9.5, rgb(0x3A9BE0))
    icon.shade(segment(8, 1.5, 8, 14, 1.0), rgb(0xE8FAFF))
    icon.shade(segment(3.5, 6, 12.5, 6, 0.9), rgb(0xA8E4FF))
    icon.outline(rgb(0x123A66))
    icon.put(6, 3, rgb(0xFFFFFF))
    return icon


def heart():
    icon = Icon()

    def inside(x, y):
        u = (x - 8) / 6.2
        v = (7.2 - y) / 6.2
        return (u * u + v * v - 0.36) ** 3 - u * u * v ** 3 * 0.9 <= 0
    icon.shape(lambda x, y: inside(x, y), rgb(0xFF5C8D))
    icon.shade(lambda x, y: x > 9 and y > 6, rgb(0xD8336A))
    icon.shade(disk(5.2, 5.4, 1.6), rgb(0xFFC2D6))
    icon.outline(rgb(0x5E0F2A))
    icon.put(5, 5, rgb(0xFFFFFF))
    return icon


def clover():
    icon = Icon()
    green, light, dark = rgb(0x46C24E), rgb(0x8EE58A), rgb(0x24863A)
    icon.shape(segment(8.5, 9, 12.5, 15, 1.4), rgb(0x2F7A2E))
    for cx, cy in ((5.6, 5.0), (10.4, 5.0), (5.6, 9.8), (10.4, 9.8)):
        icon.shape(disk(cx, cy, 2.9), green)
    icon.shade(lambda x, y: x > 8 and y > 7.4, dark)
    for cx, cy in ((4.8, 4.2), (9.6, 4.2), (4.8, 9.0)):
        icon.shade(disk(cx, cy, 0.9), light)
    icon.outline(rgb(0x123D16))
    icon.put(8, 7, rgb(0xF2E86A))
    return icon


def egg():
    icon = Icon()
    icon.shape(lambda x, y: ((x - 8) / 5.0) ** 2 + ((y - 8.6) / (6.4 if y < 8.6 else 5.6)) ** 2 <= 1,
               rgb(0xF6C431))
    icon.shade(lambda x, y: x > 9.5 or y > 12, rgb(0xD39A16))
    icon.shade(segment(4, 9, 12, 9, 1.2), rgb(0xFFE47A))
    icon.shade(lambda x, y: abs(y - 9) < 0.6 and int(x) % 2 == 0, rgb(0xC2541B))
    icon.shade(disk(6, 5.4, 1.3), rgb(0xFFF3B8))
    icon.outline(rgb(0x6B4207))
    return icon


def muguet():
    icon = Icon()
    # une large feuille a gauche, trois clochettes blanches, cernees ; puis la tige en arc, sans trait
    icon.shape(polygon([(2, 12.5), (2.5, 8), (4.8, 5.5), (5.6, 9), (4.8, 14)]), rgb(0x4FB653))
    icon.shade(lambda x, y: x > 4, rgb(0x3E9B42))
    for x, y in ((7, 4), (8, 4), (7, 5), (8, 5), (10, 4), (11, 4), (10, 5), (11, 5), (13, 8), (14, 8),
                 (13, 9), (14, 9)):
        icon.put(x, y, rgb(0xFAFAF2))
    icon.outline(rgb(0x1E4A22))
    for x, y in ((7, 5), (11, 5), (14, 9)):
        icon.put(x, y, rgb(0xD5D9C2))
    stem = rgb(0x2F8A34)
    for x, y in ((5, 15), (5, 14), (5, 13), (6, 12), (6, 11), (6, 10), (6, 9), (6, 8), (6, 7), (7, 6),
                 (7, 3), (8, 2), (9, 2), (10, 2), (11, 2), (12, 3), (12, 4), (13, 5), (13, 6), (13, 7)):
        icon.put(x, y, stem)
    return icon


def lyre():
    icon = Icon()
    gold, light, dark = rgb(0xE8B33A), rgb(0xFFE08A), rgb(0xA8761C)
    # deux bras courbes, en cornes, une traverse en haut, une caisse en bas, trois cordes
    for step in range(13):
        y = 2 + step
        bulge = 1.8 * math.sin(step / 12 * math.pi)
        icon.shape(disk(4.2 - bulge + step * 0.12, y + 0.5, 0.95), gold)
        icon.shape(disk(11.8 + bulge - step * 0.12, y + 0.5, 0.95), gold)
    icon.shape(segment(4, 3.2, 12, 3.2, 1.3), dark)
    icon.shape(polygon([(4.5, 12), (11.5, 12), (10.5, 14.5), (5.5, 14.5)]), dark)
    for x in (6, 8, 10):
        for y in range(4, 12):
            icon.put(x, y, rgb(0xF4EEDC))
    icon.shade(lambda x, y: x < 4.5 and y < 11, light)
    icon.outline(rgb(0x4A3208))
    return icon


def shell():
    icon = Icon()
    base, light, dark = rgb(0xFFB7A0), rgb(0xFFE1D3), rgb(0xE07A62)
    icon.shape(lambda x, y: y <= 12.5 and ((x - 8) / 6.8) ** 2 + ((y - 12.5) / 10.5) ** 2 <= 1, base)
    icon.shape(polygon([(5.5, 12), (10.5, 12), (9.5, 14.5), (6.5, 14.5)]), dark)
    for angle in (-58, -30, 0, 30, 58):
        a = math.radians(angle)
        icon.shade(segment(8, 12.5, 8 + 6.8 * math.sin(a), 12.5 - 10.5 * math.cos(a), 0.9), dark)
    icon.shade(lambda x, y: x < 6.5 and y < 8.5, light)
    icon.outline(rgb(0x6B2A1E))
    return icon


def sun():
    icon = Icon()
    for k in range(8):
        a = math.radians(k * 45)
        icon.shape(segment(8 + 4.6 * math.cos(a), 8 + 4.6 * math.sin(a),
                           8 + 7.2 * math.cos(a), 8 + 7.2 * math.sin(a), 1.7), rgb(0xFF8A1E))
    icon.shape(disk(8, 8, 4.3), rgb(0xFFC229))
    icon.shade(disk(6.8, 6.8, 1.9), rgb(0xFFF08A))
    icon.shade(lambda x, y: (x - 8) ** 2 + (y - 8) ** 2 <= 19 and x + y > 18, rgb(0xF59A12))
    icon.outline(rgb(0x7A3006))
    return icon


def lantern():
    icon = Icon()
    iron, dark = rgb(0x50545E), rgb(0x2B2E35)
    icon.shape(polygon([(4.5, 4.5), (11.5, 4.5), (11.5, 13.5), (4.5, 13.5)]), iron)
    icon.shape(polygon([(5.5, 5.5), (10.5, 5.5), (10.5, 12.5), (5.5, 12.5)]), rgb(0xFFC93C))
    icon.shade(polygon([(6.5, 6.5), (9.5, 6.5), (9.5, 11.5), (6.5, 11.5)]), rgb(0xFFF2A8))
    icon.shape(segment(6, 2.5, 10, 2.5, 1.2), dark)
    icon.shape(segment(6, 2.5, 6, 4.5, 1.0), dark)
    icon.shape(segment(10, 2.5, 10, 4.5, 1.0), dark)
    icon.shape(polygon([(3.5, 13.5), (12.5, 13.5), (12.5, 14.5), (3.5, 14.5)]), dark)
    for y in (8, 9):
        icon.put(5, y, iron)
        icon.put(10, y, iron)
    icon.outline(rgb(0x16181C))
    return icon


def pumpkin():
    icon = Icon()
    orange, dark = rgb(0xF07A1A), rgb(0xB24E0C)
    icon.shape(ellipse(8, 9.2, 6.3, 5.2), orange)
    icon.shade(lambda x, y: abs(x - 8) < 0.6 or abs(x - 4.8) < 0.5 or abs(x - 11.2) < 0.5, dark)
    icon.shape(polygon([(7.2, 2.5), (9.2, 2.2), (8.8, 4.6), (7.4, 4.6)]), rgb(0x4E7A24))
    face = rgb(0x2A0F3D)
    for x, y in ((5, 8), (6, 8), (10, 8), (11, 8), (6, 7), (10, 7),
                 (5, 11), (6, 11), (7, 12), (8, 11), (9, 12), (10, 11), (11, 11)):
        icon.put(x, y, face)
    icon.put(6, 9, rgb(0xB44DFF))
    icon.put(10, 9, rgb(0xB44DFF))
    icon.shade(disk(4.4, 7.0, 1.0), rgb(0xFFB25C))
    icon.outline(rgb(0x4A1E05))
    return icon


def leaf():
    icon = Icon()
    body = polygon([(8, 1.5), (10, 4.5), (13.5, 3.5), (12, 7), (14.5, 8.5), (11, 10), (12, 12.5), (8.5, 11.5),
                    (8, 13.5), (7.5, 11.5), (4, 12.5), (5, 10), (1.5, 8.5), (4, 7), (2.5, 3.5), (6, 4.5)])
    icon.shape(body, rgb(0xE5672A))
    icon.shade(lambda x, y: x > 8.5, rgb(0xC2421E))
    icon.shade(lambda x, y: y < 6 and x < 7.5, rgb(0xF59B3C))
    icon.shape(segment(8, 11, 8.6, 15.2, 1.1), rgb(0x7A3B12))
    for x2, y2 in ((8, 3), (4, 8.5), (12, 8.5), (5, 11.5), (11, 11.5)):
        icon.shade(segment(8, 10.5, x2, y2, 0.7), rgb(0xFFC168))
    icon.outline(rgb(0x4E1A08))
    return icon


def star():
    icon = Icon()
    points = []
    for k in range(10):
        a = math.radians(-90 + k * 36)
        r = 7.0 if k % 2 == 0 else 3.0
        points.append((8 + r * math.cos(a), 8.6 + r * math.sin(a)))
    icon.shape(polygon(points), rgb(0xFFD23A))
    icon.shade(lambda x, y: x > 8.5 and y > 8.5, rgb(0xE5A512))
    icon.shade(disk(7, 6.6, 1.3), rgb(0xFFF6C4))
    icon.outline(rgb(0x6B4405))
    icon.put(8, 1, rgb(0xFFFFFF))
    return icon


RELICS = [
    ("relic_janvier", crystal), ("relic_fevrier", heart), ("relic_mars", clover), ("relic_avril", egg),
    ("relic_mai", muguet), ("relic_juin", lyre), ("relic_juillet", shell), ("relic_aout", sun),
    ("relic_septembre", lantern), ("relic_octobre", pumpkin), ("relic_novembre", leaf), ("relic_decembre", star),
]


def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as handle:
        json.dump(payload, handle, indent=2)
        handle.write('\n')


def main():
    sheet = Image.new('RGBA', (16 * 6 * 8, 16 * 2 * 8), (40, 40, 48, 255))
    for index, (path, draw) in enumerate(RELICS):
        icon = draw()
        texture = os.path.join(ASSETS, 'textures', 'item', path + '.png')
        os.makedirs(os.path.dirname(texture), exist_ok=True)
        icon.img.save(texture)
        write_json(os.path.join(ASSETS, 'models', 'item', path + '.json'),
                   {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'dohy:item/' + path}})
        write_json(os.path.join(ASSETS, 'items', path + '.json'),
                   {'model': {'type': 'minecraft:model', 'model': 'dohy:item/' + path}})
        big = icon.img.resize((128, 128), Image.NEAREST)
        sheet.alpha_composite(big, ((index % 6) * 128, (index // 6) * 128))
    if len(sys.argv) > 2:
        sheet.save(sys.argv[2])
    print('reliques ecrites :', len(RELICS))


main()

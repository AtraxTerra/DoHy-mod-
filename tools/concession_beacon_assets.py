"""Genere la balise de concession des mines de la Terre : ses quatre textures de bloc, son icone,
son modele de bloc, son etat de bloc, son objet et sa table de butin.

Usage : python3 -I tools/concession_beacon_assets.py <racine du depot> [apercu.png]

La balise est un dessin original : un socle de metal sombre, un mat d'acier, une lampe ambre en
haut et un fanion orange frappe d'une pioche. Les boites du modele sont celles de la forme de
earth/mine/ConcessionBeaconBlock.java (socle de 8 x 2 x 8, mat de 2 x 14 x 2).
"""
import json
import os
import sys

from PIL import Image

ROOT = sys.argv[1]
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'dohy')
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'dohy')
CLEAR = (0, 0, 0, 0)


def rgb(value, alpha=255):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, alpha)


BASE = rgb(0x3A3F47)
BASE_LIGHT = rgb(0x4E545E)
BASE_DARK = rgb(0x262A30)
RIVET = rgb(0x8A929C)
STEEL = rgb(0xB9C0C8)
STEEL_LIGHT = rgb(0xD9DEE4)
STEEL_DARK = rgb(0x7D858F)
LAMP = rgb(0xFFC34A)
LAMP_LIGHT = rgb(0xFFE7A3)
LAMP_DARK = rgb(0xD98E1F)
FLAG = rgb(0xE8590C)
FLAG_LIGHT = rgb(0xF57A35)
FLAG_DARK = rgb(0xB8440A)
EMBLEM = rgb(0xF6EBD8)
HANDLE = rgb(0x8B5A2B)
OUTLINE = rgb(0x15171B)


class Sheet:
    def __init__(self, size=16):
        self.img = Image.new('RGBA', (size, size), CLEAR)
        self.px = self.img.load()
        self.size = size

    def put(self, x, y, color):
        if 0 <= x < self.size and 0 <= y < self.size:
            self.px[x, y] = color

    def rect(self, x, y, w, h, color):
        for dx in range(w):
            for dy in range(h):
                self.put(x + dx, y + dy, color)


def pattern(sheet, rows, colors):
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if char in colors:
                sheet.put(x, y, colors[char])


def base_texture():
    s = Sheet()
    s.rect(0, 0, 16, 16, BASE)
    for i in range(16):
        s.put(i, 0, BASE_LIGHT)
        s.put(0, i, BASE_LIGHT)
        s.put(i, 15, BASE_DARK)
        s.put(15, i, BASE_DARK)
    # une plaque striee : des rayures sombres en diagonale
    for y in range(2, 14):
        for x in range(2, 14):
            if (x + y) % 4 == 0:
                s.put(x, y, BASE_DARK)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        s.put(x, y, RIVET)
    return s.img


def pole_texture():
    s = Sheet()
    for x in range(16):
        color = STEEL_LIGHT if x % 4 == 0 else STEEL_DARK if x % 4 == 3 else STEEL
        for y in range(16):
            s.put(x, y, color)
    for y in (3, 9, 15):
        for x in range(16):
            s.put(x, y, STEEL_DARK)
    return s.img


def lamp_texture():
    s = Sheet()
    s.rect(0, 0, 16, 16, LAMP)
    for i in range(16):
        s.put(i, 0, LAMP_DARK)
        s.put(i, 15, LAMP_DARK)
        s.put(0, i, LAMP_DARK)
        s.put(15, i, LAMP_DARK)
    s.rect(4, 4, 8, 8, LAMP_LIGHT)
    s.rect(6, 6, 4, 4, rgb(0xFFF6D6))
    return s.img


FLAG_ROWS = [
    "ffffffffffffffff",
    "fLLLLLLLLLLLLLLf",
    "fLllllllllllllDf",
    "fLlllllleellllDf",
    "fLllllleeeelllDf",
    "fLlllleellleelDf",
    "fLllllhlllllllDf",
    "fLlllhllllllllDf",
    "fLllhlllllllllDf",
    "fLlhllllllllllDf",
    "fLllllllllllllDf",
    "fLDDDDDDDDDDDDDf",
    "ffffffffffffffff",
    "................",
    "................",
    "................",
]


def flag_texture():
    s = Sheet()
    pattern(s, FLAG_ROWS, {'f': FLAG_DARK, 'L': FLAG_LIGHT, 'l': FLAG, 'D': FLAG_DARK, 'e': EMBLEM,
                           'h': HANDLE})
    return s.img


ICON_ROWS = [
    ".......kk.......",
    "......kyyk......",
    "......kyyk......",
    ".......ss.ooooo.",
    ".......ssoOOOOo.",
    ".......ssoOeeOo.",
    ".......ssoOhOOo.",
    ".......ssoohooo.",
    ".......ss.......",
    ".......ss.......",
    ".......ss.......",
    ".......ss.......",
    "....kkkkkkkk....",
    "....kbbbbbbk....",
    "....kddddddk....",
    "................",
]


def icon():
    s = Sheet()
    pattern(s, ICON_ROWS, {'k': OUTLINE, 'y': LAMP, 's': STEEL, 'o': FLAG_DARK, 'O': FLAG, 'e': EMBLEM,
                           'h': HANDLE, 'b': BASE_LIGHT, 'd': BASE})
    return s.img


def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as handle:
        json.dump(payload, handle, indent=2)
        handle.write('\n')


def save(image, *parts):
    path = os.path.join(ASSETS, 'textures', *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)


def face(texture, uv):
    return {'texture': '#' + texture, 'uv': uv}


def box(start, end, texture, uvs=None):
    faces = {}
    for name in ('north', 'south', 'east', 'west', 'up', 'down'):
        faces[name] = face(texture, (uvs or {}).get(name, [0, 0, 16, 16]))
    return {'from': start, 'to': end, 'faces': faces}


def model():
    flag = {
        'from': [9, 9, 7.75], 'to': [15, 15, 8.25],
        'faces': {
            'north': face('flag', [1, 1, 15, 12]),
            'south': face('flag', [15, 1, 1, 12]),
            'east': face('flag', [14, 1, 15, 12]),
            'up': face('flag', [1, 0, 15, 1]),
            'down': face('flag', [1, 12, 15, 13]),
        },
    }
    return {
        'parent': 'minecraft:block/block',
        'textures': {
            'particle': 'dohy:block/concession_beacon_base',
            'base': 'dohy:block/concession_beacon_base',
            'pole': 'dohy:block/concession_beacon_pole',
            'lamp': 'dohy:block/concession_beacon_lamp',
            'flag': 'dohy:block/concession_beacon_flag',
        },
        'elements': [
            box([4, 0, 4], [12, 2, 12], 'base', {
                'north': [4, 14, 12, 16], 'south': [4, 14, 12, 16], 'east': [4, 14, 12, 16],
                'west': [4, 14, 12, 16], 'up': [4, 4, 12, 12], 'down': [4, 4, 12, 12]}),
            box([7, 2, 7], [9, 14, 9], 'pole', {
                'north': [7, 2, 9, 14], 'south': [7, 2, 9, 14], 'east': [7, 2, 9, 14], 'west': [7, 2, 9, 14],
                'up': [7, 7, 9, 9], 'down': [7, 7, 9, 9]}),
            box([6.5, 14, 6.5], [9.5, 16, 9.5], 'lamp', {
                'north': [6, 6, 10, 9], 'south': [6, 6, 10, 9], 'east': [6, 6, 10, 9], 'west': [6, 6, 10, 9],
                'up': [6, 6, 10, 10], 'down': [6, 6, 10, 10]}),
            flag,
        ],
    }


def main():
    base = base_texture()
    pole = pole_texture()
    lamp = lamp_texture()
    flag = flag_texture()
    item_icon = icon()
    save(base, 'block', 'concession_beacon_base.png')
    save(pole, 'block', 'concession_beacon_pole.png')
    save(lamp, 'block', 'concession_beacon_lamp.png')
    save(flag, 'block', 'concession_beacon_flag.png')
    save(item_icon, 'item', 'concession_beacon.png')
    write_json(os.path.join(ASSETS, 'models', 'block', 'concession_beacon.json'), model())
    write_json(os.path.join(ASSETS, 'blockstates', 'concession_beacon.json'),
               {'variants': {'': {'model': 'dohy:block/concession_beacon'}}})
    write_json(os.path.join(ASSETS, 'models', 'item', 'concession_beacon.json'),
               {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'dohy:item/concession_beacon'}})
    write_json(os.path.join(ASSETS, 'items', 'concession_beacon.json'),
               {'model': {'type': 'minecraft:model', 'model': 'dohy:item/concession_beacon'}})
    write_json(os.path.join(DATA, 'loot_table', 'blocks', 'concession_beacon.json'), {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1,
            'entries': [{'type': 'minecraft:item', 'name': 'dohy:concession_beacon'}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}],
        }],
        'random_sequence': 'dohy:blocks/concession_beacon',
    })
    if len(sys.argv) > 2:
        sheet = Image.new('RGBA', (16 * 8 * 5 + 40, 16 * 8), (40, 40, 48, 255))
        for index, image in enumerate((base, pole, lamp, flag, item_icon)):
            sheet.alpha_composite(image.resize((128, 128), Image.NEAREST), (index * 136, 0))
        sheet.save(sys.argv[2])
    print('balise de concession : textures, modeles et butin ecrits')


main()

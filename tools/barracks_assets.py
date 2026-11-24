"""Genere la caserne de la garnison (lot 22n) : ses textures, son modele, son etat de bloc, son
objet, sa table de butin, sa recette.

Usage : python3 -I tools/barracks_assets.py <racine du depot> [apercu.png]

La caserne est un dessin original : un corps de garde de pierre taillee sur un socle sombre, une
porte de chene ferree sous un blason, des meurtrieres sur les cotes, un chemin de ronde creneele et
un fanion rouge aux epees croisees sur son mat. Le modele est fait de boites ; les UV sont ceux que
le jeu deduit des positions (pas de champ "uv"), les textures sont donc dessinees comme des faces
pleines de 16 pixels.
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


STONE = rgb(0x8A8A86)
STONE_LIGHT = rgb(0xA3A39E)
STONE_SHADE = rgb(0x737370)
MORTAR = rgb(0x5C5C59)
DARK = rgb(0x4E4E4C)
DARK_LIGHT = rgb(0x62625F)
DARK_SHADE = rgb(0x3C3C3A)
SLIT = rgb(0x1E1E20)
OAK = rgb(0x7A5634)
OAK_LIGHT = rgb(0x93683F)
OAK_DARK = rgb(0x5B3F25)
IRON = rgb(0x9DA3A8)
IRON_DARK = rgb(0x5F6468)
RED = rgb(0xB3261E)
RED_LIGHT = rgb(0xD6372D)
RED_DARK = rgb(0x7E1A15)
GOLD = rgb(0xF2C230)
GOLD_DARK = rgb(0xB98A16)
POLE = rgb(0x4A3524)
POLE_LIGHT = rgb(0x5E4430)


class Sheet:
    def __init__(self, fill=CLEAR):
        self.img = Image.new('RGBA', (16, 16), fill)
        self.px = self.img.load()

    def put(self, x, y, color):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[x, y] = color

    def rect(self, x0, y0, x1, y1, color):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, color)


def noise(x, y, salt):
    """Un bruit fixe, sans hasard : le meme dessin a chaque passage."""
    value = (x * 73856093) ^ (y * 19349663) ^ (salt * 83492791)
    return (value >> 3) % 7


def bricks(sheet, base, light, shade, mortar, salt):
    """Des briques de pierre taillee : rangs de 4 pixels, joints decales d'un rang a l'autre."""
    for y in range(16):
        for x in range(16):
            row = y // 4
            offset = 4 if row % 2 else 0
            if y % 4 == 3 or (x + offset) % 8 == 7:
                sheet.put(x, y, mortar)
                continue
            n = noise(x, y, salt)
            color = light if n == 0 else shade if n == 1 else base
            if y % 4 == 0:
                color = light if n < 3 else base
            sheet.put(x, y, color)


def wall():
    """Le cote : la pierre et une meurtriere en croix."""
    sheet = Sheet()
    bricks(sheet, STONE, STONE_LIGHT, STONE_SHADE, MORTAR, 1)
    sheet.rect(7, 6, 8, 12, SLIT)
    sheet.rect(6, 8, 9, 9, SLIT)
    for y in range(6, 13):
        if y not in (8, 9):
            sheet.put(6, y, STONE_LIGHT)
            sheet.put(9, y, STONE_SHADE)
    sheet.rect(6, 13, 9, 13, STONE_LIGHT)
    return sheet


def front():
    """La facade : la porte de chene ferree, cintree, et le blason au-dessus.

    Le corps de garde va de 2 a 12 pixels de haut : seules les lignes 4 a 13 de la texture se voient.
    """
    sheet = Sheet()
    bricks(sheet, STONE, STONE_LIGHT, STONE_SHADE, MORTAR, 2)
    # le blason
    sheet.rect(6, 4, 9, 5, RED)
    sheet.rect(7, 6, 8, 6, RED_DARK)
    sheet.put(6, 4, RED_LIGHT)
    sheet.put(7, 4, GOLD)
    sheet.put(8, 4, GOLD)
    sheet.put(7, 5, GOLD_DARK)
    sheet.put(8, 5, GOLD_DARK)
    sheet.put(5, 4, MORTAR)
    sheet.put(10, 4, MORTAR)
    # l'encadrement de pierre claire
    sheet.rect(4, 8, 11, 13, STONE_LIGHT)
    sheet.rect(5, 7, 10, 7, STONE_LIGHT)
    # la porte : un arc, deux vantaux, deux pentures, une poignee
    sheet.rect(6, 8, 9, 8, OAK)
    sheet.rect(5, 9, 10, 13, OAK)
    for y in range(8, 14):
        for x in range(5, 11):
            if sheet.px[x, y] != OAK:
                continue
            if x == 7 or x == 8 and y == 8:
                sheet.put(x, y, OAK_DARK)
            elif noise(x, y, 5) == 0:
                sheet.put(x, y, OAK_LIGHT)
    for x in range(5, 11):
        sheet.put(x, 10, IRON_DARK)
        sheet.put(x, 12, IRON_DARK)
    sheet.put(5, 10, IRON)
    sheet.put(5, 12, IRON)
    sheet.put(9, 11, GOLD)
    return sheet


def stone():
    """Le socle, le chemin de ronde vu de cote, les creneaux : une pierre plus sombre."""
    sheet = Sheet()
    bricks(sheet, DARK, DARK_LIGHT, DARK_SHADE, SLIT, 3)
    return sheet


def top():
    """Le chemin de ronde : des dalles, et une trappe au milieu."""
    sheet = Sheet()
    for y in range(16):
        for x in range(16):
            n = noise(x, y, 4)
            color = STONE_LIGHT if n == 0 else STONE_SHADE if n == 1 else STONE
            if x % 8 == 0 or y % 8 == 0:
                color = MORTAR
            sheet.put(x, y, color)
    sheet.rect(5, 5, 10, 10, OAK)
    sheet.rect(5, 5, 10, 5, OAK_LIGHT)
    sheet.rect(5, 10, 10, 10, OAK_DARK)
    sheet.rect(7, 5, 7, 10, OAK_DARK)
    sheet.put(9, 8, IRON)
    return sheet


def pole():
    sheet = Sheet()
    for y in range(16):
        for x in range(16):
            sheet.put(x, y, POLE_LIGHT if x % 4 == 1 else POLE)
    sheet.rect(0, 0, 15, 0, GOLD)
    return sheet


def flag():
    """Le fanion : rouge, bordure sombre, deux epees d'or croisees."""
    sheet = Sheet()
    sheet.rect(0, 0, 15, 15, RED)
    for x in range(16):
        sheet.put(x, 0, RED_DARK)
        sheet.put(x, 15, RED_DARK)
    for y in range(16):
        sheet.put(15, y, RED_DARK)
    for y in range(1, 15):
        if noise(0, y, 6) == 0:
            sheet.put(1, y, RED_LIGHT)
    for i in range(3, 13):
        sheet.put(i, i, GOLD)
        sheet.put(15 - i, i, GOLD)
    for i in (4, 5):
        sheet.put(i - 1, i + 1, GOLD_DARK)
        sheet.put(16 - i, i + 1, GOLD_DARK)
    sheet.rect(2, 12, 4, 13, GOLD_DARK)
    sheet.rect(11, 12, 13, 13, GOLD_DARK)
    return sheet


def box(origin, to, faces):
    return {"from": origin, "to": to, "faces": faces}


def model():
    wall_faces = lambda side: {"texture": "#" + side}
    elements = []
    # le socle
    elements.append(box([0, 0, 0], [16, 2, 16], {
        "down": {"texture": "#stone", "cullface": "down"},
        "north": {"texture": "#stone", "cullface": "north"},
        "south": {"texture": "#stone", "cullface": "south"},
        "east": {"texture": "#stone", "cullface": "east"},
        "west": {"texture": "#stone", "cullface": "west"},
        "up": {"texture": "#stone"}}))
    # le corps de garde
    elements.append(box([1, 2, 1], [15, 12, 15], {
        "north": wall_faces("front"), "south": wall_faces("wall"),
        "east": wall_faces("wall"), "west": wall_faces("wall")}))
    # le chemin de ronde
    elements.append(box([0, 12, 0], [16, 13, 16], {
        "down": {"texture": "#stone"}, "up": {"texture": "#top"},
        "north": {"texture": "#stone"}, "south": {"texture": "#stone"},
        "east": {"texture": "#stone"}, "west": {"texture": "#stone"}}))
    # les creneaux : quatre aux coins, un au milieu de chaque cote
    merlon = {face: {"texture": "#stone"} for face in ("north", "south", "east", "west", "up")}
    for x0, z0 in ((0, 0), (13, 0), (0, 13), (13, 13)):
        elements.append(box([x0, 13, z0], [x0 + 3, 16, z0 + 3], merlon))
    elements.append(box([6.5, 13, 0], [9.5, 15.5, 1.5], merlon))
    elements.append(box([6.5, 13, 14.5], [9.5, 15.5, 16], merlon))
    elements.append(box([0, 13, 6.5], [1.5, 15.5, 9.5], merlon))
    elements.append(box([14.5, 13, 6.5], [16, 15.5, 9.5], merlon))
    # le mat et son fanion
    elements.append(box([7.5, 13, 7.5], [8.5, 24, 8.5], {
        face: {"texture": "#pole"} for face in ("north", "south", "east", "west", "up")}))
    elements.append(box([8.5, 19, 7.75], [14.5, 23.5, 8.25], {
        "north": {"texture": "#flag", "uv": [1, 2, 15, 14]},
        "south": {"texture": "#flag", "uv": [15, 2, 1, 14]},
        "east": {"texture": "#flag", "uv": [14, 2, 15, 14]},
        "up": {"texture": "#flag", "uv": [1, 0, 15, 1]},
        "down": {"texture": "#flag", "uv": [1, 15, 15, 16]}}))
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "dohy:block/barracks_wall",
            "front": "dohy:block/barracks_front",
            "wall": "dohy:block/barracks_wall",
            "stone": "dohy:block/barracks_stone",
            "top": "dohy:block/barracks_top",
            "pole": "dohy:block/barracks_pole",
            "flag": "dohy:block/barracks_flag",
        },
        "elements": elements,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.48, 0.48, 0.48]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, -1.5, 0], "scale": [0.45, 0.45, 0.45]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                      "scale": [0.35, 0.35, 0.35]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0],
                                      "scale": [0.36, 0.36, 0.36]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0],
                                     "scale": [0.36, 0.36, 0.36]},
        },
    }


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as out:
        json.dump(obj, out, indent=2, ensure_ascii=True)
        out.write('\n')


def main():
    textures = {
        'barracks_wall': wall(),
        'barracks_front': front(),
        'barracks_stone': stone(),
        'barracks_top': top(),
        'barracks_pole': pole(),
        'barracks_flag': flag(),
    }
    folder = os.path.join(ASSETS, 'textures', 'block')
    os.makedirs(folder, exist_ok=True)
    for name, sheet in textures.items():
        sheet.img.save(os.path.join(folder, name + '.png'))
    write_json(os.path.join(ASSETS, 'models', 'block', 'barracks.json'), model())
    variants = {}
    for facing, turn in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        entry = {"model": "dohy:block/barracks"}
        if turn:
            entry["y"] = turn
        variants[f"facing={facing}"] = entry
    write_json(os.path.join(ASSETS, 'blockstates', 'barracks.json'), {"variants": variants})
    write_json(os.path.join(ASSETS, 'items', 'barracks.json'),
               {"model": {"type": "minecraft:model", "model": "dohy:block/barracks"}})
    write_json(os.path.join(DATA, 'loot_table', 'blocks', 'barracks.json'), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": "dohy:barracks"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
    # De la pierre taillee, deux blocs de fer et un bloc de paladium : une base deja installee.
    write_json(os.path.join(DATA, 'recipe', 'barracks.json'), {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "pattern": ["BBB", "IPI", "BBB"],
        "key": {"B": "minecraft:stone_bricks", "I": "minecraft:iron_block", "P": "palamod:paladium_block"},
        "result": {"id": "dohy:barracks", "count": 1},
    })
    if len(sys.argv) > 2:
        # un apercu : les six textures, agrandies huit fois
        preview = Image.new('RGBA', (16 * 8 * 6 + 5 * 8, 16 * 8), (40, 40, 44, 255))
        for index, sheet in enumerate(textures.values()):
            preview.paste(sheet.img.resize((128, 128), Image.NEAREST), (index * 136, 0))
        preview.save(sys.argv[2])
    print("Caserne : 6 textures, modele, etat de bloc, objet, butin, recette.")


if __name__ == '__main__':
    main()

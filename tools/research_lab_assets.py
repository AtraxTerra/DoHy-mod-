"""Genere le laboratoire de recherche de la nation (chapitre 23, lot 23c) : ses textures, ses deux
modeles (eteint, en marche), son etat de bloc, son objet, sa table de butin et sa recette.

Usage : python3 -I tools/research_lab_assets.py <racine du depot> [apercu.png]

Le laboratoire est un dessin original : une paillasse blanche a bandeau cyan, un plan de travail
d'epoxy sombre, une cuve de verre cerclee d'acier ou bouillonne un liquide, un ecran sur pied et
deux fioles. En marche, le liquide s'eclaircit, l'ecran trace une courbe et le voyant passe au vert.
Les boites du modele restent dans la forme de faction/tech/ResearchLabBlock.java (la paillasse sur
toute la largeur, ce qui est pose dessus au centre).

En marche, l'ecran affiche un histogramme qui monte.
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


WHITE = rgb(0xDCE1E7)
WHITE_LIGHT = rgb(0xF1F4F7)
WHITE_SHADE = rgb(0xB8C0C9)
WHITE_DARK = rgb(0x8E97A2)
OUTLINE = rgb(0x5B636D)
CYAN = rgb(0x27C4E8)
CYAN_DARK = rgb(0x1A8FAD)
SLATE = rgb(0x2C3038)
SLATE_LIGHT = rgb(0x3A3F49)
SLATE_DARK = rgb(0x1D2026)
STEEL = rgb(0x8C949E)
STEEL_LIGHT = rgb(0xB2B9C1)
STEEL_DARK = rgb(0x5F666F)
GLASS = rgb(0xCDEBF4)
GLASS_SHINE = rgb(0xF4FCFF)
FLUID = rgb(0x1F8A8F)
FLUID_LIGHT = rgb(0x2AA6AB)
FLUID_DARK = rgb(0x166A6E)
BUBBLE = rgb(0x7FDDE2)
FLUID_ON = rgb(0x3BEFC6)
FLUID_ON_LIGHT = rgb(0x93FFE8)
FLUID_ON_DARK = rgb(0x1FC79F)
BUBBLE_ON = rgb(0xE6FFF9)
BEZEL = rgb(0x1B1D22)
BEZEL_LIGHT = rgb(0x2E3138)
SCREEN_OFF = rgb(0x0F1B21)
SCREEN_GLARE = rgb(0x22343D)
SCREEN_ON = rgb(0x05291A)
TRACE = rgb(0x3DFF7A)
TRACE_DIM = rgb(0x1E9E4B)
PINK = rgb(0xE85AA8)
PINK_DARK = rgb(0xB23C80)
LIME = rgb(0x8BE85A)
LIME_DARK = rgb(0x5DB338)
CORK = rgb(0xB5875A)
LED_OFF = rgb(0x5C2323)
LED_ON = rgb(0x3DFF7A)
KNOB_RED = rgb(0xE5483B)
KNOB_GREEN = rgb(0x46D35F)


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


def noise(x, y, seed):
    """Un bruit fixe : la meme case donne toujours la meme valeur, l'image ne change pas d'un tirage a l'autre."""
    value = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    value = (value ^ (value >> 13)) * 1274126177 & 0xFFFFFFFF
    return (value ^ (value >> 16)) & 255


def panel(s, x0, y0, x1, y1):
    """Un panneau blanc de paillasse : bord sombre, lumiere en haut a gauche."""
    s.rect(x0, y0, x1 - x0, y1 - y0, WHITE)
    for x in range(x0, x1):
        s.put(x, y0, WHITE_LIGHT)
        s.put(x, y1 - 1, WHITE_SHADE)
    for y in range(y0, y1):
        s.put(x0, y, WHITE_LIGHT)
        s.put(x1 - 1, y, WHITE_SHADE)


def cabinet_front(lit):
    s = Sheet()
    s.rect(0, 0, 16, 16, WHITE_DARK)
    # La zone utile : colonnes 1 a 14, lignes 7 a 15 (la face avant de la paillasse, 14 x 9).
    s.rect(1, 7, 14, 9, OUTLINE)
    # le bandeau cyan sous le plan de travail
    for x in range(1, 15):
        s.put(x, 7, CYAN)
        s.put(x, 8, CYAN_DARK)
    # deux portes, une poignee chacune
    panel(s, 2, 9, 8, 15)
    panel(s, 8, 9, 14, 15)
    for y in range(10, 13):
        s.put(6, y, STEEL_DARK)
        s.put(9, y, STEEL_DARK)
    s.put(6, 10, STEEL_LIGHT)
    s.put(9, 10, STEEL_LIGHT)
    # une grille d'aeration en bas de la porte gauche, une etiquette sur la droite
    for x in range(3, 6):
        s.put(x, 13, WHITE_DARK)
    s.rect(10, 13, 3, 1, CYAN_DARK)
    # le voyant, dans le bandeau
    s.put(13, 7, LED_ON if lit else LED_OFF)
    s.put(12, 7, LED_ON if lit else LED_OFF)
    # la plinthe
    for x in range(1, 15):
        s.put(x, 15, SLATE_DARK)
    return s


def cabinet_side():
    s = Sheet()
    s.rect(0, 0, 16, 16, WHITE_DARK)
    s.rect(1, 7, 14, 9, OUTLINE)
    for x in range(1, 15):
        s.put(x, 7, CYAN)
        s.put(x, 8, CYAN_DARK)
    panel(s, 2, 9, 14, 15)
    # des fentes d'aeration
    for y in (10, 12):
        for x in range(4, 12):
            s.put(x, y, WHITE_DARK if (x + y) % 2 == 0 else WHITE_SHADE)
    for x in range(1, 15):
        s.put(x, 15, SLATE_DARK)
    # le dessous (lignes 1 a 14) : une tole simple
    for y in range(1, 7):
        for x in range(1, 15):
            s.put(x, y, WHITE_SHADE if noise(x, y, 3) < 200 else WHITE_DARK)
    return s


def worktop():
    s = Sheet()
    for y in range(16):
        for x in range(16):
            n = noise(x, y, 7)
            s.put(x, y, SLATE_LIGHT if n > 225 else SLATE_DARK if n < 18 else SLATE)
    # des carreaux d'epoxy, et le bord en acier
    for i in range(16):
        s.put(i, 0, STEEL)
        s.put(0, i, STEEL)
        s.put(i, 15, STEEL_DARK)
        s.put(15, i, STEEL_DARK)
    for i in range(1, 15):
        s.put(i, 8, SLATE_DARK)
        s.put(8, i, SLATE_DARK)
    # un repere de securite pres du bord avant (en bas de l'image : le nord du plan regarde vers -z)
    for x in range(10, 14):
        s.put(x, 2, CYAN_DARK if x % 2 == 0 else SLATE_LIGHT)
    return s


def metal():
    s = Sheet()
    for y in range(16):
        for x in range(16):
            n = noise(x, y, 11)
            s.put(x, y, STEEL_LIGHT if n > 220 else STEEL_DARK if n < 28 else STEEL)
    # la ligne 0 : la tranche du plan de travail, plus claire en haut
    for x in range(16):
        s.put(x, 0, STEEL_LIGHT)
    # la bague de la cuve (lignes 4 a 5) : plus sombre, rivetee
    for x in range(16):
        s.put(x, 4, STEEL_DARK)
        if x % 3 == 1:
            s.put(x, 4, STEEL_LIGHT)
    # deux boutons de commande
    s.put(14, 14, KNOB_RED)
    s.put(15, 14, KNOB_GREEN)
    return s


def glass():
    s = Sheet()
    # une paroi de 6 x 5 (colonnes 0 a 5, lignes 0 a 4) : cadre d'acier, verre, un reflet
    for x in range(6):
        s.put(x, 0, STEEL_LIGHT)
        s.put(x, 4, STEEL)
    for y in range(5):
        s.put(0, y, GLASS)
        s.put(5, y, GLASS)
    s.put(1, 1, GLASS_SHINE)
    s.put(1, 2, GLASS_SHINE)
    s.put(2, 1, GLASS)
    # un couvercle de 6 x 6 (colonnes 8 a 13, lignes 8 a 13) : anneau d'acier, verre au centre
    for i in range(6):
        s.put(8 + i, 8, STEEL)
        s.put(8 + i, 13, STEEL_DARK)
        s.put(8, 8 + i, STEEL)
        s.put(13, 8 + i, STEEL_DARK)
    s.rect(10, 10, 2, 2, STEEL_LIGHT)
    return s


def fluid(lit):
    s = Sheet()
    base, light, dark, bubble = (FLUID_ON, FLUID_ON_LIGHT, FLUID_ON_DARK, BUBBLE_ON) if lit \
        else (FLUID, FLUID_LIGHT, FLUID_DARK, BUBBLE)
    for y in range(16):
        for x in range(16):
            n = noise(x, y, 19)
            s.put(x, y, light if n > 210 else dark if n < 40 else base)
    for x, y in ((1, 1), (2, 3), (0, 2), (3, 0), (5, 5), (6, 7), (4, 6)):
        s.put(x, y, bubble)
    if lit:
        for x, y in ((3, 2), (1, 3), (5, 6), (7, 5)):
            s.put(x, y, bubble)
    return s


def screen(lit):
    s = Sheet()
    # un ecran de 6 x 6 (colonnes 0 a 5, lignes 0 a 5) : un bord d'un pixel, une dalle de 4 x 4
    s.rect(0, 0, 6, 6, BEZEL)
    s.rect(1, 1, 4, 4, SCREEN_ON if lit else SCREEN_OFF)
    if lit:
        # un histogramme qui monte : des barres de 1, 2, 3 et 4 pixels
        for column, height in enumerate((1, 2, 3, 4)):
            for row in range(height):
                s.put(1 + column, 4 - row, TRACE if row == height - 1 else TRACE_DIM)
    else:
        s.put(1, 1, SCREEN_GLARE)
        s.put(2, 1, SCREEN_GLARE)
        s.put(1, 2, SCREEN_GLARE)
    # le reste de l'image : le capot de l'ecran
    for y in range(6, 16):
        for x in range(16):
            s.put(x, y, BEZEL_LIGHT if noise(x, y, 23) > 200 else BEZEL)
    for x in range(6, 16):
        for y in range(6):
            s.put(x, y, BEZEL_LIGHT if noise(x, y, 29) > 200 else BEZEL)
    return s


def flask():
    s = Sheet()
    s.rect(0, 0, 2, 2, PINK)
    s.put(0, 0, PINK_DARK)
    s.rect(2, 0, 2, 2, CORK)
    s.put(4, 0, GLASS)
    s.rect(0, 2, 2, 2, LIME)
    s.put(1, 3, LIME_DARK)
    for y in range(4, 16):
        for x in range(16):
            s.put(x, y, GLASS)
    return s


def box(origin, to, faces):
    return {"from": origin, "to": to, "faces": faces}


def faces(texture, uv_side, uv_top=None, uv_bottom=None, skip=()):
    result = {}
    for side in ("north", "east", "south", "west"):
        if side not in skip:
            result[side] = {"uv": uv_side, "texture": texture}
    if "up" not in skip:
        result["up"] = {"uv": uv_top or uv_side, "texture": texture}
    if "down" not in skip:
        result["down"] = {"uv": uv_bottom or uv_top or uv_side, "texture": texture}
    return result


def model(lit):
    suffix = "_on" if lit else ""
    elements = [
        # la paillasse : portes devant, panneaux sur les cotes
        box([1, 0, 1], [15, 9, 15], {
            "north": {"uv": [1, 7, 15, 16], "texture": "#front"},
            "east": {"uv": [1, 7, 15, 16], "texture": "#side"},
            "south": {"uv": [1, 7, 15, 16], "texture": "#side"},
            "west": {"uv": [1, 7, 15, 16], "texture": "#side"},
            "down": {"uv": [1, 1, 15, 15], "texture": "#side", "cullface": "down"},
        }),
        # le plan de travail, qui deborde d'un pixel
        box([0, 9, 0], [16, 10, 16], {
            "north": {"uv": [0, 0, 16, 1], "texture": "#metal"},
            "east": {"uv": [0, 0, 16, 1], "texture": "#metal"},
            "south": {"uv": [0, 0, 16, 1], "texture": "#metal"},
            "west": {"uv": [0, 0, 16, 1], "texture": "#metal"},
            "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
            "down": {"uv": [0, 0, 16, 16], "texture": "#metal"},
        }),
        # la bague d'acier de la cuve
        box([1.5, 10, 6.5], [8.5, 11, 13.5], faces("#metal", [0, 4, 7, 5], [1, 6, 8, 13])),
        # le liquide
        box([3, 11, 8], [7, 14.5, 12], faces("#fluid", [0, 0, 4, 3.5], [4, 4, 8, 8], skip=("down",))),
        # la cuve de verre
        box([2, 11, 7], [8, 16, 13], faces("#glass", [0, 0, 6, 5], [8, 8, 14, 14], skip=("down",))),
        # l'ecran, pose sur le plan de travail
        box([9, 10, 9], [15, 16, 11], {
            "north": {"uv": [0, 0, 6, 6], "texture": "#screen"},
            "east": {"uv": [6, 0, 8, 6], "texture": "#screen"},
            "south": {"uv": [6, 0, 12, 6], "texture": "#screen"},
            "west": {"uv": [6, 0, 8, 6], "texture": "#screen"},
            "up": {"uv": [0, 6, 6, 8], "texture": "#screen"},
        }),
        # le tuyau de la cuve a l'ecran
        box([8, 13.5, 9.5], [9, 14.5, 10.5], faces("#metal", [6, 6, 7, 7], skip=("east", "west"))),
        # deux fioles et leurs bouchons
        box([10.5, 10, 2.5], [12.5, 12, 4.5], faces("#flask", [0, 0, 2, 2], [0, 0, 2, 2], skip=("down",))),
        box([11, 12, 3], [12, 13, 4], faces("#flask", [2, 0, 3, 1], [2, 0, 3, 1], skip=("down",))),
        box([13, 10, 3], [14.5, 11.5, 4.5], faces("#flask", [0, 2, 1.5, 3.5], [0, 2, 1.5, 3.5], skip=("down",))),
        # deux boutons de commande sur le plan de travail
        box([3, 10, 2], [4, 10.5, 3], faces("#metal", [14, 14, 15, 14.5], [14, 14, 15, 15], skip=("down",))),
        box([5, 10, 2], [6, 10.5, 3], faces("#metal", [15, 14, 16, 14.5], [15, 14, 16, 15], skip=("down",))),
    ]
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {
            "particle": "dohy:block/research_lab_side",
            "front": "dohy:block/research_lab_front" + suffix,
            "side": "dohy:block/research_lab_side",
            "top": "dohy:block/research_lab_top",
            "metal": "dohy:block/research_lab_metal",
            "glass": "dohy:block/research_lab_glass",
            "fluid": "dohy:block/research_lab_fluid" + suffix,
            "screen": "dohy:block/research_lab_screen" + suffix,
            "flask": "dohy:block/research_lab_flask",
        },
        "elements": elements,
    }


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as out:
        json.dump(data, out, indent=2, ensure_ascii=False)
        out.write('\n')


def main():
    textures = {
        'research_lab_front': cabinet_front(False),
        'research_lab_front_on': cabinet_front(True),
        'research_lab_side': cabinet_side(),
        'research_lab_top': worktop(),
        'research_lab_metal': metal(),
        'research_lab_glass': glass(),
        'research_lab_fluid': fluid(False),
        'research_lab_fluid_on': fluid(True),
        'research_lab_screen': screen(False),
        'research_lab_screen_on': screen(True),
        'research_lab_flask': flask(),
    }
    folder = os.path.join(ASSETS, 'textures', 'block')
    os.makedirs(folder, exist_ok=True)
    for name, sheet in textures.items():
        sheet.img.save(os.path.join(folder, name + '.png'))
    write_json(os.path.join(ASSETS, 'models', 'block', 'research_lab.json'), model(False))
    write_json(os.path.join(ASSETS, 'models', 'block', 'research_lab_on.json'), model(True))
    variants = {}
    for facing, turn in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in (False, True):
            entry = {"model": "dohy:block/research_lab" + ("_on" if lit else "")}
            if turn:
                entry["y"] = turn
            variants[f"facing={facing},lit={'true' if lit else 'false'}"] = entry
    write_json(os.path.join(ASSETS, 'blockstates', 'research_lab.json'), {"variants": variants})
    write_json(os.path.join(ASSETS, 'items', 'research_lab.json'),
               {"model": {"type": "minecraft:model", "model": "dohy:block/research_lab"}})
    write_json(os.path.join(DATA, 'loot_table', 'blocks', 'research_lab.json'), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": "dohy:research_lab"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
    # Une recette de l'etabli, sans piece de NTM : le laboratoire doit pouvoir se poser des le premier
    # jour (Gouvernement I), avant toute recherche.
    write_json(os.path.join(DATA, 'recipe', 'research_lab.json'), {
        "type": "minecraft:crafting_shaped",
        "category": "redstone",
        "pattern": ["IGI", "RBR", "ICI"],
        "key": {
            "I": "minecraft:iron_ingot",
            "G": "minecraft:glass",
            "R": "minecraft:redstone",
            "B": "minecraft:bookshelf",
            "C": "minecraft:comparator",
        },
        "result": {"id": "dohy:research_lab", "count": 1},
    })
    if len(sys.argv) > 2:
        preview = Image.new('RGBA', (len(textures) * 136, 136), (40, 42, 48, 255))
        for index, sheet in enumerate(textures.values()):
            preview.paste(sheet.img.resize((128, 128), Image.NEAREST), (index * 136 + 4, 4), sheet.img.resize(
                (128, 128), Image.NEAREST))
        preview.save(sys.argv[2])
    print(f"laboratoire : {len(textures)} textures, 2 modeles, etat de bloc, objet, butin, recette")


if __name__ == '__main__':
    main()

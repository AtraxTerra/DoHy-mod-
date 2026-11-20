"""Genere la texture du bot de farming (64 x 64), ses deux icones et leurs fichiers d'objet.

Usage : python3 -I tools/farm_bot_assets.py <racine du depot> [apercu.png]

Le bot est un petit robot sur chenilles, coiffe d'un chapeau de paille, une houe a la main : un
dessin original, fait ici pixel par pixel. Les boites et leurs coordonnees de texture sont celles
de client/farmbot/FarmBotModel.java ; les changer d'un cote demande de les changer de l'autre.

Disposition d'une boite (u, v, largeur w, hauteur h, profondeur d), comme le jeu la deplie :
le dessus en (u + d, v), le dessous en (u + d + w, v), puis sur la rangee v + d : le cote droit
(u), l'avant (u + d), le cote gauche (u + d + w), l'arriere (u + 2d + w).
"""
import json
import os
import sys

from PIL import Image

ROOT = sys.argv[1]
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'dohy')
CLEAR = (0, 0, 0, 0)


def rgb(value, alpha=255):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, alpha)


GREEN = rgb(0x3E9B4F)
GREEN_DARK = rgb(0x2F7A3D)
GREEN_LIGHT = rgb(0x5CBF6B)
YELLOW = rgb(0xF2C230)
METAL = rgb(0xC9CED6)
METAL_DARK = rgb(0x9AA2AD)
METAL_SHADE = rgb(0x6E7680)
VISOR = rgb(0x1F2430)
EYE = rgb(0x5FE3FF)
EYE_GLOW = rgb(0xB8F4FF)
TRACK = rgb(0x3A3D42)
TRACK_LIGHT = rgb(0x5A5E66)
TRACK_DARK = rgb(0x26282C)
STRAW = rgb(0xE3C26A)
STRAW_DARK = rgb(0xC9A64E)
STRAW_LIGHT = rgb(0xF2D98E)
BAND = rgb(0xC8443A)
WOOD = rgb(0x7A5230)
WOOD_DARK = rgb(0x5C3B22)
IRON = rgb(0xB8BEC6)
IRON_DARK = rgb(0x7D848E)
OUTLINE = rgb(0x15171B)


class Sheet:
    def __init__(self, size):
        self.img = Image.new('RGBA', (size, size), CLEAR)
        self.px = self.img.load()
        self.size = size

    def put(self, x, y, color):
        if 0 <= x < self.size and 0 <= y < self.size:
            self.px[x, y] = color

    def rect(self, x, y, w, h, color):
        for dy in range(h):
            for dx in range(w):
                self.put(x + dx, y + dy, color)


def faces(u, v, w, h, d):
    """Les six faces d'une boite : nom -> (x, y, largeur, hauteur)."""
    return {
        'top': (u + d, v, w, d),
        'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h),
        'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h),
        'back': (u + 2 * d + w, v + d, w, h),
    }


def fill_box(sheet, u, v, w, h, d, colors):
    for name, (x, y, fw, fh) in faces(u, v, w, h, d).items():
        sheet.rect(x, y, fw, fh, colors.get(name, colors['default']))


def entity_texture():
    s = Sheet(64)
    # Les chenilles : 8 x 3 x 8 en (0, 0).
    fill_box(s, 0, 0, 8, 3, 8, {'default': TRACK, 'top': TRACK_DARK, 'bottom': TRACK_DARK})
    for name in ('right', 'left', 'front', 'back'):
        x, y, fw, fh = faces(0, 0, 8, 3, 8)[name]
        for dx in range(0, fw, 2):
            s.rect(x + dx, y, 1, fh, TRACK_LIGHT)
        s.rect(x, y + fh - 1, fw, 1, TRACK_DARK)
    # Le corps : 8 x 7 x 6 en (0, 11).
    fill_box(s, 0, 11, 8, 7, 6, {'default': GREEN, 'top': GREEN_DARK, 'bottom': GREEN_DARK,
                                 'right': GREEN_DARK, 'left': GREEN_DARK})
    fx, fy, fw, fh = faces(0, 11, 8, 7, 6)['front']
    s.rect(fx, fy + 1, fw, 1, YELLOW)                       # la bande jaune
    s.rect(fx + 2, fy + 3, 4, 2, VISOR)                      # l'ecran
    s.rect(fx + 2, fy + 4, 3, 1, GREEN_LIGHT)                # sa jauge
    s.put(fx + 1, fy + 6, METAL_DARK)
    s.put(fx + 6, fy + 6, METAL_DARK)
    bx, by, bw, bh = faces(0, 11, 8, 7, 6)['back']
    for row in range(1, bh - 1, 2):                          # les ouies
        s.rect(bx + 1, by + row, bw - 2, 1, GREEN_DARK)
    for name in ('right', 'left'):
        x, y, fw, fh = faces(0, 11, 8, 7, 6)[name]
        s.rect(x, y + 1, fw, 1, YELLOW)
        s.put(x + fw // 2, y + 4, METAL_DARK)
    # La tete : 7 x 5 x 6 en (28, 11).
    fill_box(s, 28, 11, 7, 5, 6, {'default': METAL, 'top': METAL_DARK, 'bottom': METAL_SHADE,
                                  'right': METAL_DARK, 'left': METAL_DARK})
    hx, hy, hw, hh = faces(28, 11, 7, 5, 6)['front']
    s.rect(hx, hy + 1, hw, 3, VISOR)                         # la visiere
    for ex in (1, 4):
        s.rect(hx + ex, hy + 2, 2, 1, EYE)
        s.put(hx + ex, hy + 2, EYE_GLOW)
    s.rect(hx + 2, hy + 4, 3, 1, METAL_SHADE)                # la bouche
    for name in ('right', 'left'):
        x, y, fw, fh = faces(28, 11, 7, 5, 6)[name]
        s.put(x + fw // 2, y + 2, METAL_SHADE)               # un rivet
    # Le bord du chapeau : 11 x 1 x 10 en (0, 24).
    fill_box(s, 0, 24, 11, 1, 10, {'default': STRAW, 'bottom': STRAW_DARK})
    tx, ty, tw, th = faces(0, 24, 11, 1, 10)['top']
    for dy in range(th):
        for dx in range(tw):
            if (dx + dy) % 3 == 0:
                s.put(tx + dx, ty + dy, STRAW_DARK)
            elif (dx + 2 * dy) % 5 == 0:
                s.put(tx + dx, ty + dy, STRAW_LIGHT)
    # Le haut du chapeau : 7 x 2 x 6 en (0, 35), son ruban rouge.
    fill_box(s, 0, 35, 7, 2, 6, {'default': STRAW, 'bottom': STRAW_DARK})
    for name in ('right', 'left', 'front', 'back'):
        x, y, fw, fh = faces(0, 35, 7, 2, 6)[name]
        s.rect(x, y + 1, fw, 1, BAND)
    cx, cy, cw, ch = faces(0, 35, 7, 2, 6)['top']
    for dy in range(ch):
        for dx in range(cw):
            if (dx + dy) % 3 == 0:
                s.put(cx + dx, cy + dy, STRAW_DARK)
    # Les bras : 2 x 6 x 2 en (42, 24) et (50, 24), la main plus sombre.
    for u in (42, 50):
        fill_box(s, u, 24, 2, 6, 2, {'default': METAL_DARK, 'bottom': METAL_SHADE, 'top': METAL})
        for name in ('right', 'left', 'front', 'back'):
            x, y, fw, fh = faces(u, 24, 2, 6, 2)[name]
            s.rect(x, y + 2, fw, 1, METAL_SHADE)             # le coude
            s.rect(x, y + fh - 2, fw, 2, METAL_SHADE)        # la main
    # Le manche de la houe : 1 x 1 x 7 en (26, 35) ; la lame : 1 x 3 x 1 en (42, 32).
    fill_box(s, 26, 35, 1, 1, 7, {'default': WOOD, 'bottom': WOOD_DARK})
    fill_box(s, 42, 32, 1, 3, 1, {'default': IRON, 'right': IRON_DARK, 'back': IRON_DARK})
    return s.img


def pattern(sheet, rows, ox, oy, colors):
    for dy, row in enumerate(rows):
        for dx, char in enumerate(row):
            if char in colors:
                sheet.put(ox + dx, oy + dy, colors[char])


BOT_ICON = [
    "....cccccc......",
    "....cbbbbc......",
    "..ssssssssss....",
    "...mmmmmmmm.....",
    "...mvvvvvvm.....",
    "...mveevvem.....",
    "...mmmmmmmm.....",
    "..agggggggga....",
    "..ayyyyyyyya.h..",
    "..agddddddga.h..",
    "..agdlllldga.h..",
    "..aggggggggaii..",
    "..tttttttttt....",
    "..tTtTtTtTtt....",
    "...tttttttt.....",
    "................",
]

REMOTE_ICON = [
    "...........a....",
    "..........a.....",
    ".........a......",
    "......kkkk......",
    ".....kmmmmk.....",
    ".....kmssmk.....",
    ".....kmssmk.....",
    ".....kmmmmk.....",
    ".....kgmrmk.....",
    ".....kmmmmk.....",
    ".....kmyymk.....",
    ".....kmmmmk.....",
    ".....kmmmmk.....",
    ".....kddddk.....",
    "......kkkk......",
    "................",
]


def bot_icon():
    s = Sheet(16)
    pattern(s, BOT_ICON, 0, 0, {
        'c': STRAW, 'b': BAND, 's': STRAW_DARK, 'm': METAL, 'v': VISOR, 'e': EYE, 'a': METAL_DARK,
        'g': GREEN, 'y': YELLOW, 'd': VISOR, 'l': GREEN_LIGHT, 't': TRACK, 'T': TRACK_LIGHT, 'h': WOOD,
        'i': IRON})
    return s.img


def remote_icon():
    s = Sheet(16)
    pattern(s, REMOTE_ICON, 0, 0, {
        'a': METAL_DARK, 'k': OUTLINE, 'm': rgb(0x4A4F57), 's': rgb(0x7FE08A), 'g': rgb(0x3FD15A),
        'r': rgb(0xE5484D), 'y': YELLOW, 'd': rgb(0x30343A)})
    return s.img


def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as handle:
        json.dump(payload, handle, indent=2)
        handle.write('\n')


def item(path, image):
    texture = os.path.join(ASSETS, 'textures', 'item', path + '.png')
    os.makedirs(os.path.dirname(texture), exist_ok=True)
    image.save(texture)
    write_json(os.path.join(ASSETS, 'models', 'item', path + '.json'),
               {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'dohy:item/' + path}})
    write_json(os.path.join(ASSETS, 'items', path + '.json'),
               {'model': {'type': 'minecraft:model', 'model': 'dohy:item/' + path}})


def main():
    entity = entity_texture()
    target = os.path.join(ASSETS, 'textures', 'entity', 'farm_bot.png')
    os.makedirs(os.path.dirname(target), exist_ok=True)
    entity.save(target)
    bot = bot_icon()
    remote = remote_icon()
    item('farm_bot', bot)
    item('bot_remote', remote)
    if len(sys.argv) > 2:
        sheet = Image.new('RGBA', (64 * 8 + 16 * 8 * 2, 64 * 8), (40, 40, 48, 255))
        sheet.alpha_composite(entity.resize((512, 512), Image.NEAREST), (0, 0))
        sheet.alpha_composite(bot.resize((128, 128), Image.NEAREST), (512, 0))
        sheet.alpha_composite(remote.resize((128, 128), Image.NEAREST), (640, 0))
        sheet.save(sys.argv[2])
    print('bot de farming : texture et icones ecrites')


main()

"""Genere l'icone originale du plan de construction (16 x 16) et ses fichiers d'objet.

Usage : python3 -I tools/guide_assets.py <racine du depot>
Un rouleau de papier bleu, son quadrillage clair et un petit cube trace en blanc.
"""
import json
import os
import sys

from PIL import Image

ROOT = sys.argv[1]
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'dohy')

PAPER = (44, 90, 160, 255)
PAPER_DARK = (33, 68, 124, 255)
GRID = (86, 132, 205, 255)
INK = (230, 240, 255, 255)
ROLL = (30, 60, 112, 255)
ROLL_LIGHT = (92, 136, 214, 255)
EDGE = (20, 40, 78, 255)


def plan():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    # La feuille, de x = 3 a 14, de y = 2 a 13, coin bas droit replie.
    for x in range(3, 15):
        for y in range(2, 14):
            if x >= 12 and y >= 11 and (x - 12) + (y - 11) >= 2:
                continue
            px[x, y] = PAPER if (x + y) % 7 else PAPER_DARK
    for x in range(3, 15):
        for y in range(2, 14):
            if px[x, y][3] and (x % 4 == 0 or y % 4 == 3):
                px[x, y] = GRID
    # Le coin replie.
    for (x, y) in [(12, 13), (13, 12), (14, 11), (13, 13), (14, 12)]:
        if px[x, y][3] == 0:
            continue
    px[12, 12] = ROLL_LIGHT
    px[13, 11] = ROLL_LIGHT
    px[12, 11] = PAPER_DARK
    # Une maison tracee a l'encre blanche : le toit, les murs, la porte.
    house = [(8, 4), (7, 5), (9, 5), (6, 6), (10, 6), (5, 7), (11, 7),
             (6, 7), (6, 8), (6, 9), (6, 10), (10, 7), (10, 8), (10, 9), (10, 10),
             (7, 10), (8, 10), (9, 10), (8, 9)]
    for (x, y) in house:
        px[x, y] = INK
    # Le rouleau a gauche.
    for y in range(1, 15):
        px[1, y] = EDGE
        px[2, y] = ROLL if y % 4 else ROLL_LIGHT
        px[3, y] = ROLL_LIGHT if y < 14 else ROLL
    px[2, 1] = EDGE
    px[2, 14] = EDGE
    # Le bord de la feuille.
    for x in range(4, 12):
        px[x, 1] = EDGE
        px[x, 14] = EDGE
    for y in range(2, 11):
        px[15, y] = EDGE
    px[12, 1] = EDGE
    px[13, 1] = EDGE
    px[14, 1] = EDGE
    return img


def main():
    texture = os.path.join(ASSETS, 'textures', 'item', 'plan.png')
    os.makedirs(os.path.dirname(texture), exist_ok=True)
    plan().save(texture)
    with open(os.path.join(ASSETS, 'items', 'plan.json'), 'w', encoding='utf-8') as out:
        json.dump({'model': {'type': 'minecraft:model', 'model': 'dohy:item/plan'}}, out, indent=2)
        out.write('\n')
    with open(os.path.join(ASSETS, 'models', 'item', 'plan.json'), 'w', encoding='utf-8') as out:
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'dohy:item/plan'}}, out, indent=2)
        out.write('\n')
    print(texture)


main()

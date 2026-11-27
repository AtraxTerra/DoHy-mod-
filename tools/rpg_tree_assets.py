"""Les images de l'ecran du profil RPG, reprises de PalaRPG (fourni par le joueur).

Usage : python3 -I tools/rpg_tree_assets.py <racine du depot> <PalaRPG extrait>

- le fond de l'arbre (une croix de lumiere floue, 3 800 pixels) ramene a 1 024 : il est flou, rien
  ne se perd, et la texture tient en memoire ;
- le losange des points et son halo ;
- les icones des statistiques montrees, et une version grise de celles des branches pour les
  noeuds pas encore ouverts.
"""
import os
import sys

from PIL import Image

ROOT = sys.argv[1]
SOURCE = os.path.join(sys.argv[2], 'assets', 'palarpg', 'textures')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'palarpg', 'textures')

ICONS = ['damage', 'critical_chance', 'critical_damage', 'dodge', 'resistance', 'max_health']
for dungeon in ('talikus', 'nimbria', 'vitalys', 'manelios'):
    ICONS += [dungeon + '_resistance', dungeon + '_damage']
BRANCHES = ['damage', 'dodge', 'critical_chance', 'resistance']


def save(image, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, optimize=True)


def gray(image):
    """Les couleurs eteintes, comme un noeud ferme de Paladium : un gris un peu sombre."""
    image = image.convert('RGBA')
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            luma = int((0.299 * r + 0.587 * g + 0.114 * b) * 0.62)
            pixels[x, y] = (luma, luma, luma + 4 if luma < 251 else 255, a)
    return image


def main():
    background = Image.open(os.path.join(SOURCE, 'profile', 'skilltree', 'background.png')).convert('RGBA')
    save(background.resize((1024, 1024), Image.LANCZOS),
         os.path.join(OUT, 'profile', 'skilltree', 'background.png'))
    for name in ('cost.png', 'cost_blur.png'):
        save(Image.open(os.path.join(SOURCE, 'profile', 'skilltree', name)).convert('RGBA'),
             os.path.join(OUT, 'profile', 'skilltree', name))
    for name in ICONS:
        icon = Image.open(os.path.join(SOURCE, 'stats', 'icon', name + '.png')).convert('RGBA')
        save(icon, os.path.join(OUT, 'stats', 'icon', name + '.png'))
        if name in BRANCHES:
            save(gray(icon), os.path.join(OUT, 'stats', 'icon', 'locked', name + '.png'))
    print('profil RPG : fond, losange, %d icones (%d grises)' % (len(ICONS), len(BRANCHES)))


if __name__ == '__main__':
    main()

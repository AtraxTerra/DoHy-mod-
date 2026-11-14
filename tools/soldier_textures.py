"""Genere les quatre textures originales des soldats (64 x 64, disposition HumanoidModel).

Usage : python3 -I soldier_textures.py <dossier de sortie> [dossier d'apercu]
"""
import os
import random
import sys

from PIL import Image

OUT = sys.argv[1]
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None

VARIANTS = [
    # peau, ombre de peau, cheveux, iris, tunique, barbe ('beard', 'mustache', 'none', 'stubble')
    dict(skin=(226, 182, 150), hair=(86, 56, 34), iris=(64, 92, 150), tunic=(176, 156, 118), beard='beard'),
    dict(skin=(196, 142, 102), hair=(34, 28, 26), iris=(88, 60, 38), tunic=(128, 118, 100), beard='none'),
    dict(skin=(122, 82, 56), hair=(24, 20, 20), iris=(42, 30, 24), tunic=(112, 112, 74), beard='stubble'),
    dict(skin=(240, 200, 168), hair=(156, 78, 38), iris=(74, 114, 62), tunic=(108, 78, 54), beard='mustache'),
]

TROUSERS = (68, 58, 50)
BOOTS = (88, 58, 36)
SOLE = (50, 36, 26)
BELT = (58, 40, 26)
BUCKLE = (206, 172, 72)
WHITE = (238, 236, 230)
MOUTH = (120, 60, 52)


def shade(color, amount):
    return tuple(max(0, min(255, int(c * amount))) for c in color)


class Skin:
    def __init__(self, seed):
        self.img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.rng = random.Random(seed)

    def px(self, x, y, color, jitter=6):
        j = self.rng.randint(-jitter, jitter) if jitter else 0
        r, g, b = (max(0, min(255, c + j)) for c in color)
        self.img.putpixel((x, y), (r, g, b, 255))

    def rect(self, x0, y0, x1, y1, color, jitter=6):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.px(x, y, color, jitter)


def head(s, v):
    skin, hair = v['skin'], v['hair']
    dark_skin = shade(skin, 0.86)
    # dessus et dessous
    s.rect(8, 0, 16, 8, hair)
    s.rect(16, 0, 24, 8, dark_skin)
    # cotes et arriere : cheveux en haut, peau en bas, l'oreille au milieu
    for x0 in (0, 16, 24):
        s.rect(x0, 8, x0 + 8, 16, skin)
        s.rect(x0, 8, x0 + 8, 10, hair)
    s.rect(24, 8, 32, 14, hair)
    s.rect(24, 14, 32, 16, shade(hair, 0.85))
    for x0 in (0, 16):
        # tempes et nuque
        s.rect(x0, 10, x0 + 2, 12, hair) if x0 == 16 else s.rect(x0 + 6, 10, x0 + 8, 12, hair)
        s.rect(x0 + 3, 11, x0 + 5, 13, shade(skin, 0.9))
    # visage (8, 8) - (16, 16)
    s.rect(8, 8, 16, 16, skin)
    s.rect(8, 8, 16, 9, hair)
    s.px(8, 9, hair)
    s.px(15, 9, hair)
    s.px(9, 9, shade(hair, 1.1))
    s.px(14, 9, shade(hair, 1.1))
    # sourcils
    for x in (9, 10, 13, 14):
        s.px(x, 10, shade(hair, 0.8), 2)
    # yeux
    s.px(9, 11, WHITE, 2)
    s.px(10, 11, v['iris'], 2)
    s.px(13, 11, v['iris'], 2)
    s.px(14, 11, WHITE, 2)
    # nez et joues
    s.px(11, 12, dark_skin, 2)
    s.px(12, 12, dark_skin, 2)
    s.px(11, 13, shade(skin, 0.8), 2)
    s.px(12, 13, shade(skin, 0.8), 2)
    beard = v['beard']
    if beard == 'beard':
        s.rect(8, 13, 9, 16, hair)
        s.rect(15, 13, 16, 16, hair)
        s.rect(9, 14, 15, 16, hair)
        s.px(11, 14, MOUTH, 2)
        s.px(12, 14, MOUTH, 2)
        # la barbe couvre aussi le dessous du menton et le bas des joues
        s.rect(16, 0, 24, 8, hair)
        s.rect(5, 13, 8, 16, hair)
        s.rect(16, 13, 19, 16, hair)
    elif beard == 'mustache':
        s.rect(10, 14, 14, 15, hair)
        s.px(11, 15, MOUTH, 2)
        s.px(12, 15, MOUTH, 2)
    elif beard == 'stubble':
        for x in range(9, 15):
            s.px(x, 15, shade(skin, 0.72), 4)
        s.px(8, 14, shade(skin, 0.75), 4)
        s.px(15, 14, shade(skin, 0.75), 4)
        s.rect(10, 14, 14, 15, MOUTH)
        s.px(10, 14, shade(skin, 0.75))
    else:
        s.rect(10, 14, 14, 15, MOUTH)
        # une cicatrice sur la joue
        s.px(13, 13, shade(skin, 0.72), 2)
        s.px(14, 14, shade(skin, 0.72), 2)


def body(s, v):
    tunic = v['tunic']
    dark = shade(tunic, 0.82)
    # dessus, dessous
    s.rect(20, 16, 28, 20, tunic)
    s.rect(28, 16, 36, 20, dark)
    # faces : droite, devant, gauche, dos
    for x0, w in ((16, 4), (20, 8), (28, 4), (32, 8)):
        s.rect(x0, 20, x0 + w, 32, tunic)
        # le matelassage : une ligne sombre toutes les deux colonnes
        for x in range(x0 + 1, x0 + w, 2):
            for y in range(20, 32):
                s.px(x, y, shade(tunic, 0.9), 4)
        for y in (23, 26):
            for x in range(x0, x0 + w):
                s.px(x, y, shade(tunic, 0.86), 3)
        # la ceinture
        s.rect(x0, 28, x0 + w, 29, BELT)
    # le col de chemise, la boucle
    s.rect(22, 20, 26, 21, WHITE)
    s.px(23, 21, WHITE)
    s.px(24, 21, WHITE)
    s.px(23, 28, BUCKLE, 2)
    s.px(24, 28, BUCKLE, 2)
    # le bas de la tunique, plus sombre
    s.rect(20, 29, 28, 32, shade(tunic, 0.92))


def arm(s, v):
    tunic = v['tunic']
    skin = v['skin']
    s.rect(44, 16, 48, 20, tunic)
    s.rect(48, 16, 52, 20, skin)
    for x0 in (40, 44, 48, 52):
        s.rect(x0, 20, x0 + 4, 29, tunic)
        s.rect(x0, 29, x0 + 4, 30, shade(tunic, 0.75))
        s.rect(x0, 30, x0 + 4, 32, skin)
        for y in (23, 26):
            for x in range(x0, x0 + 4):
                s.px(x, y, shade(tunic, 0.86), 3)


def leg(s):
    s.rect(4, 16, 8, 20, TROUSERS)
    s.rect(8, 16, 12, 20, SOLE)
    for x0 in (0, 4, 8, 12):
        s.rect(x0, 20, x0 + 4, 27, TROUSERS)
        s.rect(x0, 27, x0 + 4, 31, BOOTS)
        s.rect(x0, 31, x0 + 4, 32, SOLE)
        s.rect(x0, 27, x0 + 4, 28, shade(BOOTS, 1.15))


def main():
    os.makedirs(OUT, exist_ok=True)
    for index, variant in enumerate(VARIANTS):
        skin = Skin(1000 + index * 77)
        head(skin, variant)
        body(skin, variant)
        arm(skin, variant)
        leg(skin)
        path = os.path.join(OUT, 'soldier_%d.png' % index)
        skin.img.save(path)
        if PREVIEW:
            os.makedirs(PREVIEW, exist_ok=True)
            skin.img.resize((512, 512), Image.NEAREST).save(os.path.join(PREVIEW, 'soldier_%d.png' % index))
        print(path)


main()

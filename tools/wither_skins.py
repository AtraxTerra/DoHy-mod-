#!/usr/bin/env python3
"""Les peaux des withers du pillage (chapitre 22, lot 22d-bis).

Le module PalaWither n'est pas dans les jars : ses peaux non plus. On les
redessine d'apres les rendus du wiki (« Réceptacle à Wither ») :

  - predateur : cranes anthracite aux dessus lavande, yeux et bouche
    incandescents orange, braises dans les cotes, deux cornes ;
  - supreme : violet, yeux et croix de poitrine roses lumineux, epines,
    deux grands bras griffus ;
  - assoiffe : os sable, orbites brunes, des fleches plantees dans le crane ;
  - baby : ardoise aux dessus gris clair, yeux cyan lumineux ;
  - passif : os pale couvert de mousse qui coule, pousses vertes sur les tetes.

Chaque peau part de la texture du wither (le pack, 64 x 64 ; le modele n'en
lit que la moitie haute) : la luminance de chaque pixel est rendue dans la
gamme du genre, les dessus de tete eclaircis, les yeux et la bouche repeints.
La moitie basse porte les pieces ajoutees (cornes, bras, fleches, pousses),
aux emplacements que PalaWitherGeometry leur donne. A cote, une texture
« _eyes » ne garde que ce qui brille, pour la couche lumineuse du rendu.

    python3 tools/wither_skins.py <wither.png du pack> <dossier de sortie>

palamod_assets.py l'appelle (generate) avec le pack de textures.
"""
import os
import sys

from PIL import Image

# Les faces du wither vanilla (texOffs des cubes, 64 x 64).
CENTER_FRONT = (8, 8)      # tete centrale 8x8x8 en (0, 0) : face avant en (8, 8)
CENTER_TOP = (8, 0)
SIDE_FRONT = (38, 6)       # tetes laterales 6x6x6 en (32, 0) : face avant en (38, 6)
SIDE_TOP = (38, 0)
RIBCAGE_FRONT = (3, 25)    # colonne 3x10x3 en (0, 22)
RIBS = (24, 22, 26, 4)     # cotes 11x2x2 en (24, 22)

# Les yeux 2 x 2 et la bouche, en coordonnees de face.
CENTER_EYES = [(1, 3), (2, 3), (1, 4), (2, 4), (5, 3), (6, 3), (5, 4), (6, 4)]
CENTER_MOUTH = [(2, 6), (3, 6), (4, 6), (5, 6)]
SIDE_EYES = [(0, 1), (1, 1), (0, 2), (1, 2), (4, 1), (5, 1), (4, 2), (5, 2)]
SIDE_MOUTH = [(1, 4), (2, 4), (3, 4), (4, 4)]

PALETTES = {
    # gamme du corps (sombre -> clair), dessus de tete, yeux, bouche, lumineux ?
    "predator_wither": dict(ramp=[(28, 25, 33), (52, 48, 60), (88, 82, 98)], top=[(110, 102, 122), (168, 160, 182)],
                            eyes=(255, 120, 40), mouth=(255, 70, 30), glow=True),
    "supreme_wither": dict(ramp=[(30, 16, 44), (66, 34, 92), (118, 62, 160)], top=[(150, 90, 196), (214, 160, 240)],
                           eyes=(255, 196, 255), mouth=(240, 110, 230), glow=True),
    "thirsty_wither": dict(ramp=[(120, 92, 62), (176, 146, 108), (224, 204, 164)], top=[(214, 196, 158), (242, 230, 204)],
                           eyes=(62, 40, 26), mouth=(78, 52, 34), glow=False),
    "baby_wither": dict(ramp=[(30, 34, 44), (56, 62, 76), (92, 98, 114)], top=[(150, 154, 166), (204, 208, 218)],
                        eyes=(130, 226, 255), mouth=(20, 24, 32), glow=True),
    "passive_wither": dict(ramp=[(128, 116, 94), (178, 166, 140), (224, 214, 190)], top=[(206, 196, 172), (236, 228, 208)],
                           eyes=(40, 38, 30), mouth=(56, 50, 40), glow=False),
}

MOSS = [(64, 112, 44), (92, 150, 58), (128, 186, 78)]


def lum(pixel):
    return 0.299 * pixel[0] + 0.587 * pixel[1] + 0.114 * pixel[2]


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ramp(stops, t):
    t = max(0.0, min(1.0, t))
    if t <= 0.5:
        return lerp(stops[0], stops[1], t * 2.0)
    return lerp(stops[1], stops[2], (t - 0.5) * 2.0)


def in_rect(x, y, origin, size):
    return origin[0] <= x < origin[0] + size[0] and origin[1] <= y < origin[1] + size[1]


def noise(x, y, seed=0):
    """Un bruit deterministe, pour que la generation redonne les memes pixels."""
    value = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)
    return (value & 0xFFFF) / 65535.0


def paint_box(image, u, v, w, h, d, color_of):
    """Remplit la zone UV d'un cube (texOffs u, v ; taille w, h, d) avec color_of(x, y, face)."""
    faces = {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h), "south": (u + d + w + d, v + d, w, h),
    }
    for face, (fx, fy, fw, fh) in faces.items():
        for x in range(fx, fx + fw):
            for y in range(fy, fy + fh):
                if 0 <= x < image.width and 0 <= y < image.height:
                    image.putpixel((x, y), color_of(x - fx, y - fy, face) + (255,))


def build(base, kind):
    palette = PALETTES[kind]
    source = base.convert("RGBA")
    skin = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    eyes = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    lums = [lum(source.getpixel((x, y))) for x in range(64) for y in range(32)
            if source.getpixel((x, y))[3] > 0 and lum(source.getpixel((x, y))) < 140]
    low, high = min(lums), max(lums)
    for x in range(64):
        for y in range(32):
            pixel = source.getpixel((x, y))
            if pixel[3] == 0:
                continue
            t = (min(lum(pixel), 140) - low) / max(1.0, high - low)
            if in_rect(x, y, CENTER_TOP, (8, 8)) or in_rect(x, y, SIDE_TOP, (6, 6)) \
                    or in_rect(x, y, (32 + 6, 0), (6, 6)):
                color = lerp(palette["top"][0], palette["top"][1], t)
            else:
                color = ramp(palette["ramp"], t)
            skin.putpixel((x, y), color + (255,))

    def mark(origin, cells, color, glowing):
        for cx, cy in cells:
            position = (origin[0] + cx, origin[1] + cy)
            skin.putpixel(position, color + (255,))
            if glowing:
                eyes.putpixel(position, color + (255,))

    for origin, eye_cells, mouth_cells in ((CENTER_FRONT, CENTER_EYES, CENTER_MOUTH),
                                           (SIDE_FRONT, SIDE_EYES, SIDE_MOUTH)):
        mark(origin, eye_cells, palette["eyes"], palette["glow"])
        mark(origin, mouth_cells, palette["mouth"], palette["glow"] and kind != "baby_wither")

    if kind == "predator_wither":
        # Des braises entre les cotes et le long de l'echine.
        for x in range(RIBS[0], RIBS[0] + RIBS[2]):
            for y in range(RIBS[1], RIBS[1] + RIBS[3]):
                if noise(x, y, 1) > 0.72:
                    ember = (255, 110 + int(noise(x, y, 2) * 60), 30)
                    skin.putpixel((x, y), ember + (255,))
                    eyes.putpixel((x, y), ember + (255,))
        for y in range(RIBCAGE_FRONT[1], RIBCAGE_FRONT[1] + 10, 3):
            skin.putpixel((RIBCAGE_FRONT[0] + 1, y), (255, 120, 40, 255))
            eyes.putpixel((RIBCAGE_FRONT[0] + 1, y), (255, 120, 40, 255))
        # Les cornes en L : un moignon lateral, un fut dresse, une pointe.
        bone = lambda fx, fy, face, h: lerp((132, 124, 146), (222, 216, 232), 1.0 if face == "top" else 1.0 - fy / max(1, h))
        paint_box(skin, 0, 32, 2, 6, 2, lambda fx, fy, face: bone(fx, fy, face, 6))
        paint_box(skin, 8, 32, 3, 2, 2, lambda fx, fy, face: bone(fx, fy, face, 4))
        paint_box(skin, 20, 32, 1, 3, 1, lambda fx, fy, face: lerp((236, 232, 242), (196, 188, 208), fy / 3.0))
    elif kind == "supreme_wither":
        # La croix rose de la poitrine.
        cx, cy = RIBCAGE_FRONT[0] + 1, RIBCAGE_FRONT[1] + 1
        for dx, dy in ((0, 0), (0, 1), (0, 2), (0, 3), (-1, 1), (1, 1)):
            skin.putpixel((cx + dx, cy + dy), (255, 150, 240, 255))
            eyes.putpixel((cx + dx, cy + dy), (255, 150, 240, 255))
        dark = (36, 20, 52)
        flesh = lambda base: (lambda fx, fy, face: ramp(palette["ramp"], base + noise(fx, fy, 3) * 0.3))
        paint_box(skin, 16, 32, 4, 12, 4, flesh(0.35))       # bras
        paint_box(skin, 32, 32, 3, 12, 3, flesh(0.15))       # avant-bras
        paint_box(skin, 44, 32, 1, 5, 1, lambda fx, fy, face: lerp(dark, (96, 54, 128), fy / 5.0))
        paint_box(skin, 48, 32, 1, 4, 1, lambda fx, fy, face: lerp((70, 40, 94), (16, 8, 24), fy / 4.0))
        paint_box(skin, 0, 48, 5, 5, 5, lambda fx, fy, face: lerp(palette["top"][0], palette["top"][1], 0.3)
                  if face == "top" else ramp(palette["ramp"], 0.55 + noise(fx, fy, 5) * 0.3))
    elif kind == "thirsty_wither":
        # La fleche : un fut de bois, l'empennage blanc et rouge en haut ; la pointe est dans l'os.
        def arrow(fx, fy, face):
            if face == "top" or fy < 2:
                return (238, 236, 230) if (fx + fy) % 2 == 0 else (196, 60, 48)
            return lerp((96, 66, 38), (146, 106, 64), noise(fx, fy, 6))
        paint_box(skin, 0, 32, 1, 8, 1, arrow)
    elif kind == "passive_wither":
        # La mousse : les dessus verts, des coulures sur les faces avant et laterales.
        for origin, size in ((CENTER_TOP, (8, 8)), (SIDE_TOP, (6, 6))):
            for x in range(origin[0], origin[0] + size[0]):
                for y in range(origin[1], origin[1] + size[1]):
                    skin.putpixel((x, y), MOSS[int(noise(x, y, 7) * 2.99)] + (255,))
        for origin, width, height in ((CENTER_FRONT, 8, 8), ((0, 8), 8, 8), ((16, 8), 8, 8), ((24, 8), 8, 8),
                                      (SIDE_FRONT, 6, 6), ((32, 6), 6, 6), ((44, 6), 6, 6), ((50, 6), 6, 6)):
            for x in range(origin[0], origin[0] + width):
                depth = 1 + int(noise(x, origin[1], 8) * 3.5)
                for y in range(origin[1], origin[1] + min(depth, height)):
                    if (x - origin[0], y - origin[1]) in CENTER_EYES or (x - origin[0], y - origin[1]) in SIDE_EYES:
                        continue
                    skin.putpixel((x, y), MOSS[min(2, int(noise(x, y, 9) * 2.99))] + (255,))
        sprout = lambda fx, fy, face: MOSS[2] if fy == 0 else MOSS[1] if fy < 2 else MOSS[0]
        paint_box(skin, 0, 32, 1, 3, 1, sprout)
        paint_box(skin, 4, 32, 1, 4, 1, sprout)
        paint_box(skin, 8, 32, 1, 2, 1, sprout)
    return skin, eyes


def head_icon(skin):
    """L'icone de la tete : la face avant de la tete centrale, agrandie, cerclee de noir."""
    face = skin.crop((CENTER_FRONT[0], CENTER_FRONT[1], CENTER_FRONT[0] + 8, CENTER_FRONT[1] + 8))
    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    icon.paste(face.resize((14, 14), Image.NEAREST), (1, 1))
    for i in range(16):
        for j in (0, 15):
            if 0 < i < 15:
                icon.putpixel((i, j), (16, 14, 18, 255))
                icon.putpixel((j, i), (16, 14, 18, 255))
    return icon


INVULNERABLE = [(18, 34, 92), (52, 86, 170), (120, 170, 236)]


def invulnerable(skin, vanilla):
    """La peau de charge : celle du vanilla pour le squelette, les pieces ajoutees passees au bleu."""
    out = vanilla.convert("RGBA").copy()
    for x in range(64):
        for y in range(32, 64):
            out.putpixel((x, y), (0, 0, 0, 0))
            pixel = skin.getpixel((x, y))
            if pixel[3]:
                out.putpixel((x, y), ramp(INVULNERABLE, lum(pixel) / 255.0) + (255,))
    return out


def generate(base_png, invulnerable_png, entity_dir, item_dir):
    """Ecrit les peaux, les yeux et la peau de charge de chaque genre, et l'icone de sa tete
    (pas pour le baby : il n'a pas de tete a crafter)."""
    base = Image.open(base_png)
    vanilla_invulnerable = Image.open(invulnerable_png)
    os.makedirs(entity_dir, exist_ok=True)
    os.makedirs(item_dir, exist_ok=True)
    for kind in PALETTES:
        skin, eyes = build(base, kind)
        skin.save(os.path.join(entity_dir, kind + ".png"))
        if PALETTES[kind]["glow"]:
            eyes.save(os.path.join(entity_dir, kind + "_eyes.png"))
        invulnerable(skin, vanilla_invulnerable).save(os.path.join(entity_dir, kind + "_invulnerable.png"))
        if kind != "baby_wither":
            head_icon(skin).save(os.path.join(item_dir, kind + "_head.png"))


def main():
    folder = os.path.dirname(sys.argv[1])
    generate(sys.argv[1], os.path.join(folder, "wither_invulnerable.png"), sys.argv[2], sys.argv[2])


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Genere les assets et les donnees du namespace palamod.

Pourquoi un generateur plutot que 150 fichiers ecrits a la main : chaque item
demande trois fichiers (texture, modele, definition d'item) et chaque bloc six.
Pour 24 items et 13 blocs cela fait plus de 150 fichiers dont la seule variable
est un nom. Les ecrire a la main garantit une faute de frappe quelque part, et
une faute de frappe dans un chemin de modele ne fait pas echouer la compilation
— elle donne une texture violette en jeu, trois semaines plus tard.

    mkdir pack && cd pack && unzip ../updated_faithfull_paladium_officiel.zip
    mkdir jar  && cd jar  && unzip ../PalaMod-10.3.0.pala
    python3 tools/palamod_assets.py pack jar

Les textures viennent du **pack officiel** en priorite, et du jar de PalaMod
seulement pour celles que le pack n'a pas. Le pack est la version entretenue :
il corrige au passage des defauts du jar — c'est lui qui a le bon
`endium_block` en 16x16, la ou le jar traine trois homonymes dont deux en 15x15.

Le repli n'est jamais silencieux : la provenance de chaque texture est comptee
et affichee, et les fichiers pris dans le jar sont listes nommement.

Le namespace reste `palamod` : un joueur qui installe le pack officiel par
dessus retrouve donc exactement les memes fichiers, et ses mises a jour
s'appliquent sans rien regenerer.

26.2 est pointilleux sur deux choses, verifiees dans le jar vanilla :
  - un item demande DEUX fichiers, `models/item/x.json` (la geometrie) ET
    `items/x.json` (la definition). Sans le second l'icone est violette ;
  - les dossiers de donnees sont au SINGULIER : `loot_table/`, `recipe/`,
    `tags/item/`, `tags/block/`.
"""
import json
import math
import re
import os
import shutil
import struct
import sys

NS = "palamod"
RES = "src/main/resources"

# ---------------------------------------------------------------------------
# Items. nom -> (texture source dans le jar, nom FR, nom EN)
# ---------------------------------------------------------------------------

PARTICLES = {
    # Sorties du cobblebreaker, de la plus courante a la plus rare.
    # Neuf particules d'un meme minerai donnent un lingot.
    "iron_particle": ("particle/particle_iron.png", "Particule de fer", "Iron Particle"),
    "gold_particle": ("particle/particle_gold.png", "Particule d'or", "Gold Particle"),
    "diamond_particle": ("particle/particle_diamond.png", "Particule de diamant", "Diamond Particle"),
    "amethyst_particle": ("particle/particle_amethyst.png", "Particule d'amethyste", "Amethyst Particle"),
    "titane_particle": ("particle/particle_titane.png", "Particule de titane", "Titane Particle"),
    "paladium_particle": ("particle/particle_paladium.png", "Particule de paladium", "Paladium Particle"),
    # Septieme type, present dans le code et absent du wiki.
    "green_paladium_particle": ("particle/particle_green_paladium.png",
                                "Particule de paladium vert", "Green Paladium Particle"),
}

MATERIALS = {
    "amethyst_ingot": ("materials/amethyst_ingot.png", "Lingot d'amethyste", "Amethyst Ingot"),
    "titane_ingot": ("materials/titane_ingot.png", "Lingot de titane", "Titane Ingot"),
    "paladium_ingot": ("materials/paladium_ingot.png", "Lingot de paladium", "Paladium Ingot"),
    "green_paladium_ingot": ("materials/paladium_green_ingot.png",
                             "Lingot de paladium vert", "Green Paladium Ingot"),
    "endium_ingot": ("materials/endium_ingot.png", "Lingot d'endium", "Endium Ingot"),
    "endium_nugget": ("materials/endium_nugget.png", "Pepite d'endium", "Endium Nugget"),
    "endium_fragment": ("materials/endium_fragment.png", "Fragment d'endium", "Endium Fragment"),
    "findium": ("materials/findium.png", "Findium", "Findium"),
    "trixium": ("materials/trixium.png", "Trixium", "Trixium"),
}

COMPRESSED = {
    "compressed_amethyst": ("machine/compressed_amethyst.png",
                            "Amethyste compressee", "Compressed Amethyst"),
    "compressed_titane": ("machine/compressed_titane.png",
                          "Titane compresse", "Compressed Titane"),
    "compressed_paladium": ("machine/compressed_paladium.png",
                            "Paladium compresse", "Compressed Paladium"),
    "compressed_endium": ("machine/compressed_endium.png",
                          "Endium compresse", "Compressed Endium"),
}

# Carburants du crusher. Un charbon par type de fruit, releve dans
# TileCrusher$EnumCrusherRecipes. Leurs recettes de fabrication arrivent au
# chapitre des machines : les inventer ici serait un chiffre de plus a defaire.
MIXED_COALS = {
    "gold_mixed_coal": ("machine/goldMixedCoal.png", "Charbon mixe d'or", "Goldmixed Coal"),
    "amethyst_mixed_coal": ("machine/amethystMixedCoal.png",
                            "Charbon mixe d'amethyste", "Amethystmixed Coal"),
    "titane_mixed_coal": ("machine/titaneMixedCoal.png",
                          "Charbon mixe de titane", "Titanemixed Coal"),
    "pala_mixed_coal": ("machine/palaMixedCoal.png",
                        "Charbon mixe de paladium", "Palamixed Coal"),
}

# Intermediaires de fabrication, sans aucun comportement. Releves parce que dix
# des quinze recettes du chapitre les demandent : les cinq seedplanters, le
# crusher et le charbon mixe de paladium.
CRAFTING_PARTS = {
    "amethyst_stick": ("stick/amethyst.png", "Baton en amethyste", "Amethyst Stick"),
    "titane_stick": ("stick/titane.png", "Baton en titane", "Titane Stick"),
    "paladium_stick": ("stick/paladium.png", "Baton en paladium", "Paladium Stick"),
    "paladium_core": ("paladiumCore.png", "Coeur en paladium", "Paladium Core"),
    # La chaine du cobblebreaker. Les modificateurs sont ceux du grinder
    # (PSRegister_Items), l'orbe un BaseItem nu, la voidstone une poubelle.
    "speed_orb": ("machine/orb_speed.png", "Orbe de vitesse", "Speed Orb"),
    "speed_modifier": ("modifier/speed.png", "Modificateur de vitesse", "Speed Modifier"),
    "fortune_modifier": ("modifier/fortune.png", "Modificateur de fortune", "Fortune Modifier"),
    "voidstone": ("voidstone.png", "Voidstone", "Voidstone"),
}

ITEMS = {}
ITEMS.update(PARTICLES)
ITEMS.update(MATERIALS)
ITEMS.update(COMPRESSED)
ITEMS.update(MIXED_COALS)
ITEMS.update(CRAFTING_PARTS)

# ---------------------------------------------------------------------------
# Blocs. Durete, resistance et niveau de pioche releves au bytecode dans
# fr.paladium.palamod.modules.world.PWorld — pas devines.
# ---------------------------------------------------------------------------

TOOL_TAG = {1: "needs_stone_tool", 2: "needs_iron_tool", 3: "needs_diamond_tool"}

ORES = {
    # nom: (texture, durete, resistance, niveau de pioche, item lache, FR, EN)
    "amethyst_ore": ("ores/amethyst_ore.png", 3.0, 5.0, 1,
                     "palamod:amethyst_ingot", "Minerai d'amethyste", "Amethyst Ore"),
    "titane_ore": ("ores/titane_ore.png", 3.0, 5.0, 2,
                   "palamod:titane_ingot", "Minerai de titane", "Titane Ore"),
    "paladium_ore": ("ores/paladium_ore.png", 5.0, 5.0, 3,
                     "palamod:paladium_ingot", "Minerai de paladium", "Paladium Ore"),
    "paladium_green_ore": ("ores/paladium_green_ore.png", 5.0, 5.0, 3,
                           "palamod:green_paladium_ingot",
                           "Minerai de paladium vert", "Green Paladium Ore"),
    "findium_ore": ("ores/findium_ore.png", 5.0, 5.0, 3,
                    "palamod:findium", "Minerai de findium", "Findium Ore"),
    "trixium_ore": ("ores/trixium_ore.png", 3.0, 5.0, 3,
                    "palamod:trixium", "Minerai de trixium", "Trixium Ore"),
}

# XP de fonte : celle du fer (0,7) pour les deux premiers, de l'or (1,0)
# pour les autres. PalaMod n'en donnait pas : il lachait le lingot.
ORE_XP = {
    "amethyst_ore": 0.7, "titane_ore": 0.7, "paladium_ore": 1.0, "paladium_green_ore": 1.0,
    "findium_ore": 1.0, "trixium_ore": 1.0, "endium_nugget_ore": 1.0,
}


def smelt(ore, result, xp):
    """Le minerai au four (200 ticks) et au haut fourneau (100)."""
    for kind, ticks in (("smelting", 200), ("blasting", 100)):
        write("data/%s/recipe/%s_from_%s.json" % (NS, result.split(":")[1], kind), {
            "type": "minecraft:%s" % kind, "category": "misc", "cookingtime": ticks,
            "experience": xp, "group": result.split(":")[1],
            "ingredient": "%s:%s" % (NS, ore), "result": {"id": result}})


STORAGE = {
    # nom: (texture, lingot correspondant, FR, EN)
    "amethyst_block": ("amethyst/amethyst_block.png", "palamod:amethyst_ingot",
                       "Bloc d'amethyste", "Block of Amethyst"),
    "titane_block": ("titane/titane_block.png", "palamod:titane_ingot",
                     "Bloc de titane", "Block of Titane"),
    "paladium_block": ("paladium/paladium_block.png", "palamod:paladium_ingot",
                       "Bloc de paladium", "Block of Paladium"),
    "green_paladium_block": ("gpaladium/gpaladium_block.png", "palamod:green_paladium_ingot",
                             "Bloc de paladium vert", "Block of Green Paladium"),
    # Attention au chemin : PalaMod contient TROIS endium_block.png, et deux
    # sont en 15x15 — un fichier isole a la racine de blocks/ et un asset de
    # GUI de LuckyBlock. Le bon est dans le sous-dossier endium/, comme pour
    # tous les autres materiaux. La garde de taille ci-dessous existe a cause
    # de cette confusion, qui est passee en revue une premiere fois.
    "endium_block": ("endium/endium_block.png", "palamod:endium_ingot",
                     "Bloc d'endium", "Block of Endium"),
    "findium_block": ("findium/findium_block.png", "palamod:findium",
                      "Bloc de findium", "Block of Findium"),
    "trixium_block": ("trixium/trixium_block.png", "palamod:trixium",
                      "Bloc de trixium", "Block of Trixium"),
}

# ---------------------------------------------------------------------------
# Cultures.
#
# Tout vient de BaseBlockCrops.getDrops et de PRegister_Blocks, lus au
# bytecode. Le mecanisme du lot rare n'est NULLE PART sur le wiki : a maturite,
# un tirage sur 10 000 decide entre un lingot et le fruit.
#
# nom : (
#   graine, fruit, nombre de dessins, stades d'origine,
#   lot rare, chance du lot sur 10000, chance de rendre la graine,
#   FR du bloc, FR de la graine, FR du fruit,
#   EN du bloc, EN de la graine, EN du fruit,
# )
# ---------------------------------------------------------------------------

CROP_AGE_MAX = 7  # AGE_7 : 8 stades, ce que CropBlock impose en 26.2

CROPS = {
    "eggplant_crop": (
        "eggplant_seeds", "eggplant", 4, 8,
        "palamod:amethyst_ingot", 800, 1.0,
        "Aubergines", "Graines d'aubergine", "Aubergine",
        "Eggplants", "Eggplant Seeds", "Eggplant"),
    "chervil_crop": (
        "chervil_seeds", "chervil", 4, 8,
        "palamod:titane_ingot", 500, 1.0,
        "Cerfeuil", "Graines de cerfeuil", "Cerfeuil",
        "Chervil", "Chervil Seeds", "Chervil"),
    "kiwano_crop": (
        "kiwano_seeds", "kiwano", 5, 10,
        "palamod:paladium_ingot", 200, 1.0,
        "Kiwanos", "Graines de kiwano", "Kiwano",
        "Kiwanos", "Kiwano Seeds", "Kiwano"),
    # L'orangeblue est la seule dont la graine ne revient pas toujours : un
    # second tirage, sur 30, ne la rend que si le resultat vaut 0 ou 1, soit
    # 6,67 %. C'est ce qui l'empeche de s'auto-entretenir, et c'est voulu —
    # c'est la seule source d'endium du jeu.
    "orangeblue_crop": (
        "orangeblue_seeds", "orangeblue", 5, 10,
        "palamod:endium_nugget", 25, 2.0 / 30.0,
        "Orangeblues", "Graines d'orangeblue", "Orangeblue",
        "Orangeblues", "Orangeblue Seeds", "Orangeblue"),
}

# Textures sources des graines et des fruits. Elles ne suivent pas le nom de
# l'item : la graine d'aubergine est items/seeds/eggplant.png et son fruit
# items/fruits/eggplants.png, au pluriel.
CROP_TEXTURES = {
    "eggplant_seeds": "seeds/eggplant.png",
    "chervil_seeds": "seeds/chervil.png",
    "kiwano_seeds": "seeds/kiwano.png",
    "orangeblue_seeds": "seeds/orangeblue.png",
    "eggplant": "fruits/eggplants.png",
    "chervil": "fruits/chervil.png",
    "kiwano": "fruits/kiwano.png",
    "orangeblue": "fruits/orangeblue.png",
}


def stage_for_age(age, frames):
    """Quel dessin montrer a cet age.

    <p>Le PalaMod faisait avancer un dessin tous les deux stades : 8 stades pour
    4 dessins, 10 pour 5. Avec 8 stades imposes, les cultures a 4 dessins
    gardent ce rythme exactement ; celles a 5 dessins le gardent jusqu'au
    quatrieme et montrent le dernier sur le seul stade final. La maturite reste
    donc lisible d'un coup d'oeil, ce qui est tout ce qu'on demande au dessin.
    """
    if frames >= 5:
        return min(frames - 1, age // 2 if age < 6 else age - 3)
    return min(frames - 1, age // 2)


# Generation du monde. nom -> (taille de filon, nombre par chunk, y min, y max)
#
# La taille de filon est RELEVEE dans BaseWorldGenerator : amethyste 8,
# titane 6, paladium 4, paladium vert 3, findium 3, trixium 3. Le nombre et la
# profondeur sont CALIBRES sur le monde de 26.2 — voir le commentaire a l'usage.
ORE_GEN = {
    "amethyst_ore": (8, 8, -24, 64),
    "titane_ore": (6, 6, -32, 40),
    "paladium_ore": (4, 4, -48, 16),
    "paladium_green_ore": (3, 2, -56, -8),
    "findium_ore": (3, 3, -64, 0),
    "trixium_ore": (3, 1, -64, -32),
}

# Neuf particules -> un lingot. Le fer, l'or et le diamant retombent sur les
# items vanilla : le cobblebreaker ne doit pas creer un fer parallele.
PARTICLE_TO_INGOT = {
    "iron_particle": "minecraft:iron_ingot",
    "gold_particle": "minecraft:gold_ingot",
    "diamond_particle": "minecraft:diamond",
    "amethyst_particle": "palamod:amethyst_ingot",
    "titane_particle": "palamod:titane_ingot",
    "paladium_particle": "palamod:paladium_ingot",
    "green_paladium_particle": "palamod:green_paladium_ingot",
}

# Les compresses, d'apres PRegister_Craft : huit blocs autour d'un lingot, et pas
# de retour (un retour a neuf blocs rendrait huit lingots a chaque tour). Le
# portage du chapitre 2 les faisait de neuf blocs, avec retour, avant le releve
# des recettes ; corrige le 2026-11-11. L'endium compresse est a part, plus bas.
COMPRESSED_FROM = {
    "compressed_amethyst": ("palamod:amethyst_block", "palamod:amethyst_ingot"),
    "compressed_titane": ("palamod:titane_block", "palamod:titane_ingot"),
    "compressed_paladium": ("palamod:paladium_block", "palamod:paladium_ingot"),
}


def write(path, payload):
    full = os.path.join(RES, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def write_rgba_png(path, width, height, pixels):
    """Ecrit une PNG RGBA. pixels : {(x, y): (r, g, b, a)}, le reste transparent."""
    import zlib
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(pixels.get((x, y), (0, 0, 0, 0)))

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    full = os.path.join(RES, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
                 + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


# Le totem de fertilite, d'apres ModelTotem (PalaMod 10.3.0) : decalage de
# texture, position, taille, miroir. Reperes d'un modele Techne : y descend, le
# sol est a 24 ; RenderTotem le retourne de 180 degres autour de z.
TOTEM_BOXES = [
    ((0, 34), (-7, 23, -7), (14, 1, 14), False),   # le socle
    ((0, 17), (-4, 14, -4), (8, 9, 8), True),      # la tete du bas
    ((32, 17), (-3, 12, -3), (6, 2, 6), False),
    ((0, 0), (-4, 3, -4), (8, 9, 8), False),       # la tete du milieu, visage devant
    ((32, 25), (-3, 1, -3), (6, 2, 6), False),
    ((32, 0), (-4, -8, -4), (8, 9, 8), False),     # la tete du haut, visage derriere
    ((0, 49), (-6, 4, 0), (2, 7, 1), False),       # les ailes, en escalier
    ((56, 41), (-8, 3, 0), (2, 6, 1), False),
    ((56, 22), (-10, 2, 0), (2, 5, 1), False),
    ((56, 17), (-11, 1, 0), (1, 4, 1), False),
    ((60, 17), (10, 1, 0), (1, 4, 1), False),
    ((56, 28), (8, 2, 0), (2, 5, 1), False),
    ((56, 34), (6, 3, 0), (2, 6, 1), False),
    ((6, 49), (4, 4, 0), (2, 7, 1), False),
]

# Le visage de totem_layer.png : sept lignes de six pixels, # allume. L'original
# le peint sur la face avant de la tete du milieu et la face arriere de celle du
# haut ; ici il a sa propre texture de 16x16, le visage dans le coin.
TOTEM_FACE = ["######", "..##..", "..##..", "##..##", "#....#", "#.##.#", "######"]

# La couleur du visage par niveau de charge. RenderTotem la tire de la teinte
# max(lingots / 64 / 3, 0.07), saturation 1 : de l'orange au vert. Eteint sans
# carburant.
TOTEM_FACE_COLORS = [(58, 50, 46), (255, 107, 0), (255, 221, 0), (64, 255, 48)]


def totem_face_pixels(color):
    pixels = {}
    for row, line in enumerate(TOTEM_FACE):
        for column, lit in enumerate(line):
            pixels[(column, row)] = color + (255,) if lit == "#" else (16, 10, 8, 255)
    return pixels


def totem_elements():
    """Les boites de ModelTotem en elements de modele de bloc.

    Un point (x, y, z) du modele arrive en (8 - x, 24 - y, 8 + z) : la face
    -x du modele est a l'est du bloc, sa face du dessus (y minimal) en haut.
    Les UV sont ceux d'une boite ModelBox : -x, z0, +x, z1 cote a cote sous le
    dessus et le dessous. Le dessus est tourne de 180 degres et le dessous
    retourne, comme le rend le TESR.
    """
    def uv(u0, v0, u1, v1):
        return [u0 / 4.0, v0 / 4.0, u1 / 4.0, v1 / 4.0]

    elements = []
    for (u, v), (x, y, z), (w, h, d), mirror in TOTEM_BOXES:
        faces = {
            "east": (u, v + d, u + d, v + d + h),
            "north": (u + d, v + d, u + d + w, v + d + h),
            "west": (u + d + w, v + d, u + d + w + d, v + d + h),
            "south": (u + d + w + d, v + d, u + d + w + d + w, v + d + h),
            "up": (u + d, v, u + d + w, v + d),
            "down": (u + d + w + w, v, u + d + w, v + d),
        }
        if mirror:
            faces["east"], faces["west"] = faces["west"], faces["east"]
            # Le miroir d'un ModelBox echange les faces est et ouest et retourne les autres.
            faces = {name: rect if name in ("east", "west") else (rect[2], rect[1], rect[0], rect[3])
                     for name, rect in faces.items()}
        element = {
            "from": [8 - (x + w), 24 - (y + h), 8 + z],
            "to": [8 - x, 24 - y, 8 + z + d],
            "faces": {name: {"uv": uv(*rect), "texture": "#wood"} for name, rect in faces.items()},
        }
        element["faces"]["up"]["rotation"] = 180
        elements.append(element)
    # Les deux visages : un plan devant la face, lumineux.
    elements.append({
        "from": [5, 13, 3.98], "to": [11, 20, 3.98], "shade": False, "light_emission": 15,
        "faces": {"north": {"uv": [0, 0, 6, 7], "texture": "#face"}},
    })
    elements.append({
        "from": [5, 24, 12.02], "to": [11, 31, 12.02], "shade": False, "light_emission": 15,
        "faces": {"south": {"uv": [0, 0, 6, 7], "texture": "#face"}},
    })
    return elements


def read_rgba_png(path):
    """Lit une PNG RGBA 8 bits non entrelacee : (largeur, hauteur, lignes d'octets)."""
    import zlib
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    chunks = []
    offset = 8
    while offset < len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        chunks.append((data[offset + 4:offset + 8], data[offset + 8:offset + 8 + length]))
        offset += 12 + length
    width, height, depth, color, _, _, interlace = struct.unpack(">IIBBBBB", chunks[0][1])
    assert (depth, color, interlace) == (8, 6, 0), "PNG RGBA 8 bits attendue : %s" % path
    raw = zlib.decompress(b"".join(body for kind, body in chunks if kind == b"IDAT"))
    row = width * 4
    rows = []
    previous = bytearray(row)
    for y in range(height):
        start = y * (row + 1)
        kind = raw[start]
        line = bytearray(raw[start + 1:start + 1 + row])
        if kind == 1:
            for x in range(4, row):
                line[x] = (line[x] + line[x - 4]) & 0xFF
        elif kind == 2:
            for x in range(row):
                line[x] = (line[x] + previous[x]) & 0xFF
        elif kind == 3:
            for x in range(row):
                left = line[x - 4] if x >= 4 else 0
                line[x] = (line[x] + ((left + previous[x]) >> 1)) & 0xFF
        elif kind == 4:
            for x in range(row):
                left = line[x - 4] if x >= 4 else 0
                up = previous[x]
                corner = previous[x - 4] if x >= 4 else 0
                p = left + up - corner
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - corner)
                line[x] = (line[x] + (left if pa <= pb and pa <= pc else up if pb <= pc else corner)) & 0xFF
        rows.append(line)
        previous = line
    return width, height, rows


def shrink_icon(source, destination, size):
    """Reduit une grande vignette en icone carree de `size` pixels.

    La vignette est rognee a ce qu'elle dessine, centree dans un carre, puis
    chaque pixel de l'icone est la moyenne de sa zone (couleurs ponderees par
    l'opacite, sinon le transparent noircit les bords).
    """
    width, height, rows = read_rgba_png(source)
    left, top, right, bottom = width, height, 0, 0
    for y, line in enumerate(rows):
        for x in range(width):
            if line[x * 4 + 3]:
                left, right = min(left, x), max(right, x + 1)
                top, bottom = min(top, y), max(bottom, y + 1)
    side = max(right - left, bottom - top)
    x0 = (left + right - side) // 2
    y0 = (top + bottom - side) // 2
    pixels = {}
    for py in range(size):
        ya, yb = y0 + py * side // size, y0 + (py + 1) * side // size
        for px in range(size):
            xa, xb = x0 + px * side // size, x0 + (px + 1) * side // size
            r = g = b = a = 0
            for y in range(max(ya, 0), min(yb, height)):
                line = rows[y]
                for x in range(max(xa, 0), min(xb, width)):
                    alpha = line[x * 4 + 3]
                    r += line[x * 4] * alpha
                    g += line[x * 4 + 1] * alpha
                    b += line[x * 4 + 2] * alpha
                    a += alpha
            area = max(1, (yb - ya) * (xb - xa))
            if a:
                pixels[(px, py)] = ((r + a // 2) // a, (g + a // 2) // a, (b + a // 2) // a,
                                    min(255, (a + area // 2) // area))
    write_rgba_png(destination, size, size, pixels)


def pad_png_height(path, height):
    """Allonge une PNG vers le bas jusqu'a `height`, par des lignes vides.

    Un atlas de blocs prend le plus petit exposant de deux de ses textures pour
    ses niveaux de mipmap : une seule texture de 46 pixels de haut les coupe a
    un niveau pour TOUS les blocs du jeu (« dropping miplevel from 4 to 1 »).
    Des lignes ajoutees a la fin, de filtre 0 et d'octets nuls, sont valides
    quel que soit le type de couleur : inutile de defiltrer l'image existante.
    """
    import zlib
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    chunks = []
    offset = 8
    while offset < len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        chunks.append((kind, data[offset + 8:offset + 8 + length]))
        offset += 12 + length
    ihdr = chunks[0][1]
    width, old_height, depth, color, _, _, interlace = struct.unpack(">IIBBBBB", ihdr)
    if old_height >= height:
        return
    assert interlace == 0, "PNG entrelacee : %s" % path
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color]
    row = (width * channels * depth + 7) // 8
    raw = zlib.decompress(b"".join(body for kind, body in chunks if kind == b"IDAT"))
    raw += (b"\x00" * (row + 1)) * (height - old_height)
    ihdr = struct.pack(">IIBBBBB", width, height, depth, color, 0, 0, 0)

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    out = [b"\x89PNG\r\n\x1a\n", chunk(b"IHDR", ihdr)]
    out += [chunk(kind, body) for kind, body in chunks[1:] if kind not in (b"IDAT", b"IEND")]
    out += [chunk(b"IDAT", zlib.compress(raw, 9)), chunk(b"IEND", b"")]
    open(path, "wb").write(b"".join(out))


def png_size(path):
    """Largeur et hauteur d'un PNG, lues dans son en-tete IHDR."""
    with open(path, "rb") as fh:
        head = fh.read(24)
    if len(head) < 24 or head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", head[16:24])


# Les textures hors 16x16 sont refusees.
#
# PalaMod traine des homonymes : il y a trois `endium_block.png`, dont deux en
# 15x15 (un fichier isole a la racine de blocks/ et un asset de GUI). Le script
# a pris le mauvais la premiere fois, et rien ne l'a signale — une texture
# 15x15 part dans l'atlas sans erreur de chargement et se voit seulement en
# jeu, sur un bloc qu'il faut d'abord pouvoir obtenir.
#
# Un chemin explicite ne suffit donc pas : il faut verifier ce qu'on a copie.
EXPECTED_SIZE = (16, 16)


# Le pack officiel ne range pas tout comme le jar. Une entree ici dit ou
# chercher dans le pack quand le chemin du jar n'y mene pas.
PACK_PATHS = {
    # Dans le jar, blocks/endium_block.png est un fichier isole en 15x15 et le
    # bon est dans blocks/endium/. Dans le pack, la racine porte le bon.
    "blocks/endium/endium_block.png": "blocks/endium_block.png",
}

# Compteur de provenance, rempli par copy_texture et affiche a la fin.
provenance = {"pack": 0, "jar": []}


def copy_texture(roots, relative, destination, wrong_size=None, expected=None):
    """Copie une texture, pack d'abord, jar en repli.

    roots est (pack, jar). Le pack est la version entretenue ; le jar ne sert
    que pour ce qu'il est seul a avoir, et chaque repli est enregistre pour
    etre affiche — un repli silencieux ferait croire que le pack couvre tout.
    """
    pack_root, jar_root = roots
    candidates = []
    if pack_root:
        candidates.append(("pack", os.path.join(
            pack_root, "assets", NS, "textures", PACK_PATHS.get(relative, relative))))
    if jar_root:
        candidates.append(("jar", os.path.join(jar_root, "assets", NS, "textures", relative)))

    for origin, src in candidates:
        if not os.path.exists(src):
            continue
        size = png_size(src)
        # Les icones font 16x16 ; les couches d'armure portees font 64x32, la
        # disposition UV du modele de joueur — la meme en 1.7.10 et en 26.2.
        want = expected or EXPECTED_SIZE
        if size is not None and size != want:
            if wrong_size is not None:
                wrong_size.append("%s (%s) : %dx%d au lieu de %dx%d"
                                  % (relative, origin, size[0], size[1], *want))
            continue
        dst = os.path.join(RES, "assets", NS, "textures", destination)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(src, dst)
        if origin == "pack":
            provenance["pack"] += 1
        else:
            provenance["jar"].append(relative)
        return True
    return False


def main():
    if len(sys.argv) < 2:
        raise SystemExit(
            "usage: palamod_assets.py <pack officiel extrait> [<jar PalaMod extrait>]")
    root = (sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)

    missing = []
    wrong_size = []
    fr = {}
    en = {}

    # ---- items ----
    for name, (texture, label_fr, label_en) in ITEMS.items():
        if not copy_texture(root, "items/" + texture, "item/%s.png" % name, wrong_size):
            missing.append("items/" + texture)
        write("assets/%s/models/item/%s.json" % (NS, name), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "%s:item/%s" % (NS, name)},
        })
        # La definition d'item. 26.2 l'exige en plus du modele : sans elle
        # l'icone est violette, et c'est la premiere cause d'icones manquantes.
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)},
        })
        fr["item.%s.%s" % (NS, name)] = label_fr
        en["item.%s.%s" % (NS, name)] = label_en

    # ---- blocs ----
    blocks = {}
    for name, (texture, _h, _r, _lvl, drop, label_fr, label_en) in ORES.items():
        blocks[name] = (texture, drop, label_fr, label_en)
    for name, (texture, ingot, label_fr, label_en) in STORAGE.items():
        blocks[name] = (texture, "%s:%s" % (NS, name), label_fr, label_en)

    for name, (texture, drop, label_fr, label_en) in blocks.items():
        if not copy_texture(root, "blocks/" + texture, "block/%s.png" % name, wrong_size):
            missing.append("blocks/" + texture)
        write("assets/%s/blockstates/%s.json" % (NS, name), {
            "variants": {"": {"model": "%s:block/%s" % (NS, name)}},
        })
        write("assets/%s/models/block/%s.json" % (NS, name), {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": "%s:block/%s" % (NS, name)},
        })
        write("assets/%s/models/item/%s.json" % (NS, name),
              {"parent": "%s:block/%s" % (NS, name)})
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:block/%s" % (NS, name)},
        })
        fr["block.%s.%s" % (NS, name)] = label_fr
        en["block.%s.%s" % (NS, name)] = label_en

    # Les tags s'ACCUMULENT au lieu de s'ecraser.
    #
    # Plusieurs familles ecrivent dans le meme fichier : minerais, blocs de
    # stockage et machines vont tous dans mineable/pickaxe. Avec une ecriture
    # directe, le dernier appel effacait les precedents — et un tag efface ne
    # produit aucune erreur, juste un bloc qui se casse a la main a vitesse
    # normale. On collecte, et on ecrit une fois a la fin.
    collected_tags = {}

    def tag(path, entries):
        collected_tags.setdefault(path, set()).update(entries)

    # ---- tables de butin ----
    # Un minerai lache son lingot, et le bloc lui-meme a la pioche soie. C'est
    # le comportement de Paladium, et il satisfait la regle de DESIGN.md
    # « minerai != metal » : deux marchandises distinctes, deux tiers.
    # Demande du joueur (2026-11-09) : le minerai se rend lui-meme, comme le
    # fer ou l'or bruts d'avant 1.17, et se fond au four ou au haut fourneau.
    # PalaMod lachait le lingot avec la Fortune (getItemDropped) ; la Fortune
    # ne joue plus ici, sauf a la pioche des dieux, qui fond ce qu'elle casse.
    for name, (_t, _h, _r, _lvl, drop, _fr, _en) in ORES.items():
        write("data/%s/loot_table/blocks/%s.json" % (NS, name), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, name)}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
        smelt(name, drop, ORE_XP[name])

    for name in STORAGE:
        write("data/%s/loot_table/blocks/%s.json" % (NS, name), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, name)}],
            }],
        })

    # ---- recettes ----
    # Format plat des ingredients : "palamod:x", pas {"item": "..."}.
    def shapeless(path, ingredients, result, count=1, group=None):
        payload = {
            "type": "minecraft:crafting_shapeless",
            "ingredients": ingredients,
            "result": {"id": result, "count": count},
        }
        if group:
            payload["group"] = group
        write("data/%s/recipe/%s.json" % (NS, path), payload)

    def shaped(path, pattern, keys, result, count=1):
        """Une recette a grille, telle qu'elle est relevee au bytecode.

        `pattern` et `keys` sont recopies de la sortie de
        tools/extract_crafts.py, sans reinterpretation. Les lignes de grille
        d'origine peuvent faire une ou deux cases de large : le format moderne
        l'accepte, il n'y a rien a completer.
        """
        write("data/%s/recipe/%s.json" % (NS, path), {
            "type": "minecraft:crafting_shaped",
            "pattern": pattern,
            "key": keys,
            "result": {"id": result, "count": count},
        })

    def packed(path, ingredient, result, group=None):
        payload = {
            "type": "minecraft:crafting_shaped",
            "pattern": ["XXX", "XXX", "XXX"],
            "key": {"X": ingredient},
            "result": {"id": result, "count": 1},
        }
        if group:
            payload["group"] = group
        write("data/%s/recipe/%s.json" % (NS, path), payload)

    for particle, ingot in PARTICLE_TO_INGOT.items():
        packed("%s_from_particles" % ingot.split(":")[1],
               "%s:%s" % (NS, particle), ingot, group="palamod_particles")

    for name, (_t, ingot, _fr, _en) in STORAGE.items():
        packed("%s_from_ingots" % name, ingot, "%s:%s" % (NS, name))
        shapeless("%s_from_block" % ingot.split(":")[1],
                  ["%s:%s" % (NS, name)], ingot, count=9)

    for compressed, (block, ingot) in COMPRESSED_FROM.items():
        shaped("%s_from_blocks" % compressed, ["XXX", "XYX", "XXX"], {"X": block, "Y": ingot},
               "%s:%s" % (NS, compressed))
    # L'endium compresse : quatre fragments d'endium aux coins, cinq blocs de
    # paladium en croix (le portage demandait neuf blocs d'endium, 729 pepites).
    shaped("compressed_endium_from_blocks", ["XYX", "YYY", "XYX"],
           {"X": "%s:endium_fragment" % NS, "Y": "%s:paladium_block" % NS}, "%s:compressed_endium" % NS)

    packed("endium_ingot_from_nuggets", "%s:endium_nugget" % NS, "%s:endium_ingot" % NS)
    shapeless("endium_nugget_from_ingot", ["%s:endium_ingot" % NS],
              "%s:endium_nugget" % NS, count=9)
    # Pepite et fragments, dans les deux sens (PRegister_Craft) : le fragment
    # entre dans le lingot d'endium mixte.
    shapeless("endium_fragment_from_nugget", ["%s:endium_nugget" % NS], "%s:endium_fragment" % NS, count=9)
    packed("endium_nugget_from_fragments", "%s:endium_fragment" % NS, "%s:endium_nugget" % NS)

    # Un lingot rend neuf particules (PRegister_Craft). Pas pour le fer ni l'or :
    # la meme case vide donne deja leurs pepites vanilla, et deux recettes sur la
    # meme grille se masquent.
    for particle, ingot in PARTICLE_TO_INGOT.items():
        if particle in ("iron_particle", "gold_particle", "green_paladium_particle"):
            continue
        shapeless("%s_from_ingot" % particle, [ingot], "%s:%s" % (NS, particle), count=9)

    # ---- tags ----
    tag("%s/tags/item/particles.json" % NS,
        ["%s:%s" % (NS, n) for n in sorted(PARTICLES)])
    tag("%s/tags/item/ingots.json" % NS, [
        "%s:amethyst_ingot" % NS, "%s:titane_ingot" % NS, "%s:paladium_ingot" % NS,
        "%s:green_paladium_ingot" % NS, "%s:endium_ingot" % NS])
    tag("%s/tags/item/compressed.json" % NS,
        ["%s:%s" % (NS, n) for n in sorted(COMPRESSED)])
    tag("%s/tags/item/mixed_coals.json" % NS,
        ["%s:%s" % (NS, n) for n in sorted(MIXED_COALS)])
    tag("%s/tags/block/ores.json" % NS, ["%s:%s" % (NS, n) for n in sorted(ORES)])
    # Le tag de convention : les contrats « chasse aux minerais » (#c:ores) et
    # tout mod qui le lit comptent alors les minerais de PalaMod.
    tag("c/tags/block/ores.json", ["%s:%s" % (NS, n) for n in sorted(ORES)]
        + ["%s:endium_nugget_ore" % NS, "%s:invoker_ore" % NS])
    tag("%s/tags/block/storage_blocks.json" % NS,
        ["%s:%s" % (NS, n) for n in sorted(STORAGE)])

    # Tags vanilla : ils fusionnent, donc on ajoute sans remplacer. Sans
    # mineable/pickaxe un bloc se casse a la main a vitesse normale, et sans
    # needs_*_tool le niveau de pioche releve dans PWorld n'est pas applique.
    tag("minecraft/tags/block/mineable/pickaxe.json",
        ["%s:%s" % (NS, n) for n in sorted(list(ORES) + list(STORAGE))])
    by_level = {}
    for name, (_t, _h, _r, level, _d, _fr, _en) in ORES.items():
        by_level.setdefault(level, []).append("%s:%s" % (NS, name))
    # Un bloc de stockage suit le niveau de son minerai ; l'endium et le
    # trixium n'ont pas de minerai classique, ils prennent le diamant.
    by_level.setdefault(1, []).append("%s:amethyst_block" % NS)
    by_level.setdefault(2, []).append("%s:titane_block" % NS)
    by_level.setdefault(3, []).extend([
        "%s:paladium_block" % NS, "%s:green_paladium_block" % NS,
        "%s:endium_block" % NS, "%s:findium_block" % NS, "%s:trixium_block" % NS])
    for level, entries in by_level.items():
        tag("minecraft/tags/block/%s.json" % TOOL_TAG[level], sorted(entries))

    # ---- terre fertilisee ----
    #
    # Deux blocs, une seule texture. La version labouree reutilise la texture de
    # la version brute sur ses cotes et son dessous, et emprunte le dessus de la
    # terre labouree irriguee de vanilla : c'est exactement ce que le bloc fait
    # — une terre toujours humide — et aucun dessus n'existe dans les sources.
    if not copy_texture(root, "blocks/connectedDirt.png",
                        "block/fertilized_dirt.png", wrong_size):
        missing.append("blocks/connectedDirt.png")

    write("assets/%s/models/block/fertilized_dirt.json" % NS, {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": "%s:block/fertilized_dirt" % NS},
    })
    write("assets/%s/models/block/fertilized_farmland.json" % NS, {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": "minecraft:block/farmland_moist",
            "side": "%s:block/fertilized_dirt" % NS,
            "bottom": "%s:block/fertilized_dirt" % NS,
        },
    })

    for soil, label_fr, label_en in (
            ("fertilized_dirt", "Terre fertilisee", "Fertilized Dirt"),
            ("fertilized_farmland", "Terre fertilisee labouree", "Tilled Fertilized Dirt")):
        write("assets/%s/blockstates/%s.json" % (NS, soil),
              {"variants": {"": {"model": "%s:block/%s" % (NS, soil)}}})
        write("assets/%s/models/item/%s.json" % (NS, soil),
              {"parent": "%s:block/%s" % (NS, soil)})
        write("assets/%s/items/%s.json" % (NS, soil), {
            "model": {"type": "minecraft:model", "model": "%s:block/%s" % (NS, soil)},
        })
        fr["block.%s.%s" % (NS, soil)] = label_fr
        en["block.%s.%s" % (NS, soil)] = label_en
        # Les deux rendent de la terre fertilisee BRUTE : labourer puis casser
        # ne doit rien couter, sinon un coup de houe mal place devient une
        # perte seche.
        write("data/%s/loot_table/blocks/%s.json" % (NS, soil), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item",
                             "name": "%s:fertilized_dirt" % NS}],
            }],
        })

    # RECETTE INVENTEE — la seule du chapitre, et elle est signalee comme telle.
    #
    # Le PalaMod n'en donne aucune : sur le serveur, la terre fertilisee
    # s'achetait. Plutot que de la laisser inaccessible hors creatif, on pose
    # une recette thematique (des os, donc de l'engrais) et calibree pour une
    # ferme : un bloc d'os pour huit blocs de terre, soit un peu plus d'une
    # poudre d'os par bloc. Elle n'accelere que la croissance, jamais le
    # rendement, donc le risque d'equilibrage est faible.
    #
    # C'est un fichier de datapack : la changer ne demande pas de recompiler.
    write("data/%s/recipe/fertilized_dirt.json" % NS, {
        "type": "minecraft:crafting_shaped",
        "pattern": ["DDD", "DBD", "DDD"],
        "key": {"D": "minecraft:dirt", "B": "minecraft:bone_block"},
        "result": {"id": "%s:fertilized_dirt" % NS, "count": 8},
    })

    # La version brute entre dans le tag dirt : pousses d'arbre, fleurs et
    # herbe y poussent alors sans une ligne de code. La version labouree n'y
    # entre pas — ce serait lui faire porter aussi les plantes de surface.
    tag("minecraft/tags/block/dirt.json", ["%s:fertilized_dirt" % NS])
    # La version labouree entre dans les deux tags de la terre labouree.
    #
    # grows_crops : CropBlock.getGrowthSpeed (et les tiges, qui l'appellent)
    # ne compte un sol que s'il est dans ce tag ; NeoForge lui passe le sol
    # lui-meme comme « plante », si bien que le canSustainPlant du bloc ne peut
    # pas repondre a sa place. Hors du tag, la terre fertilisee valait 0 :
    # moins qu'une terre labouree seche. Le bonus d'irrigation, lui, vient de
    # isFertile (PalaFertileSoilBlock), qui rend toujours vrai.
    #
    # supports_crops : c'est le tag que lisent les tiges de melon et de
    # citrouille (via supports_stem_crops) pour se poser. Les cultures passent
    # deja par canSustainPlant ; les tiges non, n'etant pas des CropBlock.
    tag("minecraft/tags/block/grows_crops.json", ["%s:fertilized_farmland" % NS])
    tag("minecraft/tags/block/supports_crops.json", ["%s:fertilized_farmland" % NS])
    tag("minecraft/tags/block/mineable/shovel.json",
        ["%s:fertilized_dirt" % NS, "%s:fertilized_farmland" % NS])

    # ---- cultures ----
    for crop, spec in CROPS.items():
        (seed, fruit, frames, _original_stages, rare, rare_chance, seed_chance,
         fr_block, fr_seed, fr_fruit, en_block, en_seed, en_fruit) = spec
        base = crop.removesuffix("_crop")

        # Les dessins de stade. Un modele par dessin, pas un par stade.
        for frame in range(frames):
            texture = "%s_stage_%d" % (base, frame)
            if not copy_texture(root, "blocks/crops/%s.png" % texture,
                                "block/%s.png" % texture, wrong_size):
                missing.append("blocks/crops/%s.png" % texture)
            write("assets/%s/models/block/%s_stage_%d.json" % (NS, crop, frame), {
                "parent": "minecraft:block/crop",
                "textures": {"crop": "%s:block/%s" % (NS, texture)},
            })

        write("assets/%s/blockstates/%s.json" % (NS, crop), {
            "variants": {
                "age=%d" % age: {
                    "model": "%s:block/%s_stage_%d" % (NS, crop, stage_for_age(age, frames))
                }
                for age in range(CROP_AGE_MAX + 1)
            },
        })
        fr["block.%s.%s" % (NS, crop)] = fr_block
        en["block.%s.%s" % (NS, crop)] = en_block

        # Graine et fruit : des items plats ordinaires.
        for name, label_fr, label_en in ((seed, fr_seed, en_seed),
                                         (fruit, fr_fruit, en_fruit)):
            source = CROP_TEXTURES[name]
            if not copy_texture(root, "items/" + source, "item/%s.png" % name, wrong_size):
                missing.append("items/" + source)
            write("assets/%s/models/item/%s.json" % (NS, name), {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": "%s:item/%s" % (NS, name)},
            })
            write("assets/%s/items/%s.json" % (NS, name), {
                "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)},
            })
            fr["item.%s.%s" % (NS, name)] = label_fr
            en["item.%s.%s" % (NS, name)] = label_en

        # La table de butin, et c'est la que vit tout l'interet des cultures.
        #
        # Une culture immature ne lache RIEN, pas meme sa graine : la condition
        # d'age porte sur les deux reserves. C'est le comportement d'origine,
        # plus dur que le ble vanilla, et il est reproduit tel quel.
        mature = {
            "condition": "minecraft:block_state_property",
            "block": "%s:%s" % (NS, crop),
            "properties": {"age": str(CROP_AGE_MAX)},
        }
        seed_pool = {
            "rolls": 1,
            "conditions": [mature],
            "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, seed)}],
        }
        if seed_chance < 1.0:
            seed_pool["conditions"] = [mature, {
                "condition": "minecraft:random_chance",
                "chance": round(seed_chance, 6),
            }]

        write("data/%s/loot_table/blocks/%s.json" % (NS, crop), {
            "type": "minecraft:block",
            "pools": [
                {
                    "rolls": 1,
                    "conditions": [mature],
                    # alternatives : le lot rare s'il sort, le fruit sinon. Le
                    # lingot REMPLACE le fruit, il ne s'y ajoute pas.
                    "entries": [{
                        "type": "minecraft:alternatives",
                        "children": [
                            {"type": "minecraft:item", "name": rare,
                             "conditions": [{"condition": "minecraft:random_chance",
                                             "chance": rare_chance / 10000.0}]},
                            {"type": "minecraft:item", "name": "%s:%s" % (NS, fruit)},
                        ],
                    }],
                },
                seed_pool,
            ],
        })

    tag("%s/tags/item/seeds.json" % NS,
        sorted("%s:%s" % (NS, spec[0]) for spec in CROPS.values()))
    tag("%s/tags/item/fruits.json" % NS,
        sorted("%s:%s" % (NS, spec[1]) for spec in CROPS.values()))
    tag("%s/tags/block/crops.json" % NS, ["%s:%s" % (NS, c) for c in sorted(CROPS)])
    # Les cultures comptent comme des cultures pour le jeu : la houe les
    # recolte, les villageois les reconnaissent, et le tag DoHy des cultures
    # sert deja a l'XP d'agriculture.
    tag("minecraft/tags/block/crops.json", ["%s:%s" % (NS, c) for c in sorted(CROPS)])

    # ---- machines ----
    #
    # Le cobblebreaker a QUATRE apparences, une par palier : c'est ce qui dit au
    # joueur ou en est sa machine sans qu'il ouvre quoi que ce soit, puisque le
    # mod n'a pas d'interface dessinee. Le palier est une propriete d'etat, donc
    # un modele par palier et une variante de blockstate par palier.
    for tier in ("default", "amethyst", "titane", "paladium"):
        for face in ("front", "side", "top"):
            source = "blocks/machines/cobblebreaker/cobblebreaker_%s_%s.png" % (tier, face)
            target = "block/cobblebreaker_%s_%s.png" % (tier, face)
            if not copy_texture(root, source, target, wrong_size):
                missing.append(source)
        write("assets/%s/models/block/cobblebreaker_%s.json" % (NS, tier), {
            # cube_bottom_top donne une face distincte dessus et dessous ; le
            # dessous reprend le dessus, le PalaMod n'ayant pas de texture
            # propre pour lui.
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {
                "top": "%s:block/cobblebreaker_%s_top" % (NS, tier),
                "bottom": "%s:block/cobblebreaker_%s_top" % (NS, tier),
                "side": "%s:block/cobblebreaker_%s_side" % (NS, tier),
            },
        })

    write("assets/%s/blockstates/cobblebreaker.json" % NS, {
        "variants": {
            "tier=%s" % tier: {"model": "%s:block/cobblebreaker_%s" % (NS, tier)}
            for tier in ("default", "amethyst", "titane", "paladium")
        },
    })
    # L'item du bloc montre le palier de base : c'est celui qu'on pose.
    write("assets/%s/models/item/cobblebreaker.json" % NS,
          {"parent": "%s:block/cobblebreaker_default" % NS})
    write("assets/%s/items/cobblebreaker.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:block/cobblebreaker_default" % NS},
    })

    # Le crusher : une seule apparence. Ses textures ne sont QUE dans le jar.
    for face in ("front", "side", "top"):
        source = "blocks/machines/crusher_%s.png" % face
        if not copy_texture(root, source, "block/crusher_%s.png" % face, wrong_size):
            missing.append(source)
    write("assets/%s/models/block/crusher.json" % NS, {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": "%s:block/crusher_top" % NS,
            "bottom": "%s:block/crusher_top" % NS,
            "side": "%s:block/crusher_side" % NS,
        },
    })
    write("assets/%s/blockstates/crusher.json" % NS,
          {"variants": {"": {"model": "%s:block/crusher" % NS}}})
    write("assets/%s/models/item/crusher.json" % NS, {"parent": "%s:block/crusher" % NS})
    write("assets/%s/items/crusher.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:block/crusher" % NS},
    })

    for machine, label_fr, label_en in (
            ("cobblebreaker", "Cobblebreaker", "Cobblebreaker"),
            ("crusher", "Crusher", "Crusher")):
        fr["block.%s.%s" % (NS, machine)] = label_fr
        en["block.%s.%s" % (NS, machine)] = label_en
        write("data/%s/loot_table/blocks/%s.json" % (NS, machine), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [
                {"type": "minecraft:item", "name": "%s:%s" % (NS, machine)}]}],
        })

    # Les trois ameliorations du cobblebreaker.
    for tier, label_fr, label_en in (
            ("amethyst", "Amelioration amethyste (x1,5)", "Amethyst Upgrade (x1.5)"),
            ("titane", "Amelioration titane (x2,5)", "Titane Upgrade (x2.5)"),
            ("paladium", "Amelioration paladium (x4)", "Paladium Upgrade (x4)")):
        name = "cobblebreaker_%s_upgrade" % tier
        if not copy_texture(root, "items/%s.png" % name, "item/%s.png" % name, wrong_size):
            missing.append("items/%s.png" % name)
        write("assets/%s/models/item/%s.json" % (NS, name), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "%s:item/%s" % (NS, name)},
        })
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)},
        })
        fr["item.%s.%s" % (NS, name)] = label_fr
        en["item.%s.%s" % (NS, name)] = label_en

    tag("minecraft/tags/block/mineable/pickaxe.json",
        ["%s:cobblebreaker" % NS, "%s:crusher" % NS])
    tag("minecraft/tags/block/needs_stone_tool.json",
        ["%s:cobblebreaker" % NS, "%s:crusher" % NS])

    # ---- chapitre 5 : le totem de fertilite ----
    #
    # L'original le dessine avec un TESR (ModelTotem, RenderTotem) : un mat
    # sculpte de deux blocs de haut, trois tetes empilees et deux ailes, et un
    # visage qui luit de la couleur du carburant. Le portage en faisait un cube
    # texture ; c'est maintenant le modele d'origine, traduit en modele de bloc
    # (aucun renderer de block entity) : les quatorze boites de ModelTotem,
    # relevees au bytecode, et le visage de totem_layer.png en deux plans
    # lumineux, un par niveau de charge (propriete `charge` du bloc).
    if not copy_texture(root, "models/totem.png", "block/totem_of_fertility.png", wrong_size, expected=(64, 64)):
        missing.append("models/totem.png")
    for charge, color in enumerate(TOTEM_FACE_COLORS):
        write_rgba_png("assets/%s/textures/block/totem_of_fertility_face_%d.png" % (NS, charge), 16, 16,
                       totem_face_pixels(color))
    write("assets/%s/models/block/totem_of_fertility.json" % NS, {
        "parent": "minecraft:block/block",
        "textures": {"wood": "%s:block/totem_of_fertility" % NS,
                     "face": "%s:block/totem_of_fertility_face_0" % NS,
                     "particle": "minecraft:block/spruce_log"},
        "elements": totem_elements(),
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -3.25, 0], "scale": [0.4, 0.4, 0.4]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, -3.5, 0], "scale": [0.45, 0.45, 0.45]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 0],
                                      "scale": [0.3, 0.3, 0.3]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0],
                                      "scale": [0.3, 0.3, 0.3]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0],
                                     "scale": [0.3, 0.3, 0.3]},
        },
    })
    for charge in range(1, len(TOTEM_FACE_COLORS)):
        write("assets/%s/models/block/totem_of_fertility_%d.json" % (NS, charge), {
            "parent": "%s:block/totem_of_fertility" % NS,
            "textures": {"face": "%s:block/totem_of_fertility_face_%d" % (NS, charge)},
        })
    # Le visage regarde celui qui pose le totem : nord sans rotation.
    write("assets/%s/blockstates/totem_of_fertility.json" % NS, {"variants": {
        "charge=%d,facing=%s" % (charge, facing): dict(
            {"model": "%s:block/totem_of_fertility%s" % (NS, "_%d" % charge if charge else "")},
            **({"y": rot} if rot else {}))
        for charge in range(len(TOTEM_FACE_COLORS))
        for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270))
    }})
    write("assets/%s/models/item/totem_of_fertility.json" % NS,
          {"parent": "%s:block/totem_of_fertility_3" % NS})
    write("assets/%s/items/totem_of_fertility.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:block/totem_of_fertility_3" % NS},
    })
    write("data/%s/loot_table/blocks/totem_of_fertility.json" % NS, {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": "%s:totem_of_fertility" % NS}]}],
    })
    fr["block.%s.totem_of_fertility" % NS] = "Totem de fertilite"
    en["block.%s.totem_of_fertility" % NS] = "Totem of Fertility"

    # A la hache, pas a la pioche : c'est du bois, et il n'exige aucun palier
    # d'outil. Les autres machines du chapitre 5 seront du metal.
    tag("minecraft/tags/block/mineable/axe.json", ["%s:totem_of_fertility" % NS])

    # ---- chapitre 5 : les cinq seedplanters ----
    #
    # Les identifiants viennent des verrous du chapitre 1 ; les textures
    # d'origine s'appellent seedplanter_lvl1 a lvl5. La correspondance est ici
    # et nulle part ailleurs — le Java ne connait que les identifiants.
    for name, level, side, label_fr, label_en in (
            ("amethyst_seedplanter", 1, 2, "Seedplanter en amethyste", "Amethyst Seedplanter"),
            ("titane_seedplanter", 2, 4, "Seedplanter en titane", "Titane Seedplanter"),
            ("paladium_seedplanter", 3, 6, "Seedplanter en paladium", "Paladium Seedplanter"),
            ("green_paladium_seedplanter", 5, 12,
             "Seedplanter en paladium vert", "Green Paladium Seedplanter"),
            ("endium_seedplanter", 4, 12, "Seedplanter en endium", "Endium Seedplanter")):
        source = "items/seedplanter/seedplanter_lvl%d.png" % level
        if not copy_texture(root, source, "item/%s.png" % name, wrong_size):
            missing.append(source)
        write("assets/%s/models/item/%s.json" % (NS, name), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "%s:item/%s" % (NS, name)},
        })
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)},
        })
        # Le cote du carre est dans le nom affiche : c'est la seule difference
        # entre les cinq, et un joueur qui compare deux planteurs dans son
        # inventaire n'a pas d'autre moyen de la connaitre.
        fr["item.%s.%s" % (NS, name)] = "%s (%dx%d)" % (label_fr, side, side)
        en["item.%s.%s" % (NS, name)] = "%s (%dx%d)" % (label_en, side, side)

    # ---- chapitre 7 : l'equipement ----
    #
    # Quarante-huit objets et le lingot d'endium mixe. Les icones viennent du
    # pack officiel ; les couches portees de textures/models/<set>_armor_1 et _2,
    # au format 64x32 du modele de joueur — la meme disposition UV en 1.7.10 et
    # en 26.2, donc rien a redessiner.
    #
    # Le paladium vert s'appelle paladium_green_* dans les textures et
    # green_paladium_* ici, comme le reste du mod. Les houes, qui viennent de
    # PalaJobs, s'appelaient deja green_paladium_radius_hoe.
    EQUIPMENT_METALS = (
        # (id, prefixe de texture, set d'armure 1.7.10 ou None, FR, EN, outils)
        ("amethyst", "amethyst", "amethyst", "en amethyste", "Amethyst",
         ("sword", "pickaxe", "axe", "shovel")),
        ("titane", "titane", "titane", "en titane", "Titane",
         ("sword", "pickaxe", "axe", "shovel")),
        ("paladium", "paladium", "paladium", "en paladium", "Paladium",
         ("sword", "pickaxe", "axe", "shovel")),
        ("green_paladium", "paladium_green", "paladium_green", "en paladium vert", "Green Paladium",
         ("sword", "pickaxe", "axe", "shovel")),
        ("mixed_endium", "mixed_endium", "mixed_endium", "en endium mixe", "Mixed Endium",
         ()),
        # Pas de pelle en endium dans PalaMod.
        ("endium", "endium", "endium", "en endium", "Endium",
         ("sword", "pickaxe", "axe")),
    )
    TOOL_FR = {"sword": "Epee", "pickaxe": "Pioche", "axe": "Hache", "shovel": "Pelle"}
    TOOL_EN = {"sword": "Sword", "pickaxe": "Pickaxe", "axe": "Axe", "shovel": "Shovel"}
    ARMOR_FR = {"helmet": "Casque", "chestplate": "Plastron", "leggings": "Jambieres", "boots": "Bottes"}
    ARMOR_EN = {"helmet": "Helmet", "chestplate": "Chestplate", "leggings": "Leggings", "boots": "Boots"}
    HOE_SIDE = {"amethyst": 2, "titane": 4, "paladium": 6, "green_paladium": 8, "endium": 12}

    def equipment_item(name, texture, handheld):
        if not copy_texture(root, "items/%s.png" % texture, "item/%s.png" % name, wrong_size):
            missing.append("items/%s.png" % texture)
        # Un outil se tient en diagonale dans la main ; une piece d'armure non.
        write("assets/%s/models/item/%s.json" % (NS, name), {
            "parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated",
            "textures": {"layer0": "%s:item/%s" % (NS, name)},
        })
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)},
        })

    for metal, prefix, armor_set, fr_suffix, en_prefix, tools in EQUIPMENT_METALS:
        for tool in tools:
            name = "%s_%s" % (metal, tool)
            equipment_item(name, "%s_%s" % (prefix, tool), True)
            fr["item.%s.%s" % (NS, name)] = "%s %s" % (TOOL_FR[tool], fr_suffix)
            en["item.%s.%s" % (NS, name)] = "%s %s" % (en_prefix, TOOL_EN[tool])
            tag("minecraft/tags/item/%s.json" % {"sword": "swords", "pickaxe": "pickaxes",
                                                 "axe": "axes", "shovel": "shovels"}[tool],
                ["%s:%s" % (NS, name)])

        if metal in HOE_SIDE:
            name = "%s_radius_hoe" % metal
            # Le nom de texture vient de PalaJobs : green_paladium_*, pas
            # paladium_green_*.
            equipment_item(name, name, True)
            side = HOE_SIDE[metal]
            fr["item.%s.%s" % (NS, name)] = "Houe %s (%dx%d)" % (fr_suffix, side, side)
            en["item.%s.%s" % (NS, name)] = "%s Radius Hoe (%dx%d)" % (en_prefix, side, side)
            tag("minecraft/tags/item/hoes.json", ["%s:%s" % (NS, name)])

        # L'armure : quatre icones, une definition d'equipement, deux couches.
        for piece in ("helmet", "chestplate", "leggings", "boots"):
            name = "%s_%s" % (metal, piece)
            equipment_item(name, "%s_%s" % (prefix, piece), False)
            fr["item.%s.%s" % (NS, name)] = "%s %s" % (ARMOR_FR[piece], fr_suffix)
            en["item.%s.%s" % (NS, name)] = "%s %s" % (en_prefix, ARMOR_EN[piece])
            # Ces tags decident a eux seuls quels enchantements s'appliquent :
            # sans eux, une epee Paladium refuserait Tranchant et un casque
            # Protection, sans la moindre erreur. Et trimmable_armor ouvre les
            # garnitures.
            tag("minecraft/tags/item/%s.json" % {"helmet": "head_armor", "chestplate": "chest_armor",
                                                 "leggings": "leg_armor", "boots": "foot_armor"}[piece],
                ["%s:%s" % (NS, name)])
            tag("minecraft/tags/item/trimmable_armor.json", ["%s:%s" % (NS, name)])

        for layer, suffix in (("humanoid", 1), ("humanoid_leggings", 2)):
            source = "models/%s_armor_%d.png" % (armor_set, suffix)
            if not copy_texture(root, source, "entity/equipment/%s/%s.png" % (layer, metal),
                                wrong_size, expected=(64, 32)):
                missing.append(source)
        # La definition d'equipement : c'est elle que l'ArmorMaterial designe
        # par son identifiant. Absente, le joueur porte une armure invisible.
        write("assets/%s/equipment/%s.json" % (NS, metal), {
            "layers": {
                "humanoid": [{"texture": "%s:%s" % (NS, metal)}],
                "humanoid_leggings": [{"texture": "%s:%s" % (NS, metal)}],
            },
        })

    # Le lingot d'endium mixe.
    if not copy_texture(root, "items/mixed_endium_ingot.png", "item/mixed_endium_ingot.png", wrong_size):
        missing.append("items/mixed_endium_ingot.png")
    write("assets/%s/models/item/mixed_endium_ingot.json" % NS, {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "%s:item/mixed_endium_ingot" % NS},
    })
    write("assets/%s/items/mixed_endium_ingot.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:item/mixed_endium_ingot" % NS},
    })
    fr["item.%s.mixed_endium_ingot" % NS] = "Lingot d'endium mixe"
    en["item.%s.mixed_endium_ingot" % NS] = "Mixed Endium Ingot"

    # Les tags de reparation. Une entree de plus que de metaux : l'armure en
    # paladium se repare au TITANE, seule exception sur les 43 pieces.
    for tag_name, ingot in (("amethyst", "amethyst_ingot"), ("titane", "titane_ingot"),
                            ("paladium", "paladium_ingot"), ("paladium_armor", "titane_ingot"),
                            ("green_paladium", "green_paladium_ingot"),
                            ("mixed_endium", "mixed_endium_ingot"), ("endium", "endium_ingot")):
        tag("%s/tags/item/repairs/%s.json" % (NS, tag_name), ["%s:%s" % (NS, ingot)])

    # ---- objets du Mineur (chapitre 8) ----
    #
    # Les excavatrices et les outils de construction se tiennent comme des
    # outils ; les objets de poche sont plats. Les excavatrices s'enchantent
    # comme des pelles (voir « enchantements : outils et cornes »).
    MINER_ITEMS = {
        "amethyst_excavator": ("excavator/amethyst.png", True,
                               "Excavatrice en amethyste", "Amethyst Excavator"),
        "titane_excavator": ("excavator/titane.png", True,
                             "Excavatrice en titane", "Titane Excavator"),
        "paladium_excavator": ("excavator/paladium.png", True,
                               "Excavatrice en paladium", "Paladium Excavator"),
        "amethyst_builder_wand": ("builder_wand_amethyst.png", True,
                                  "Outil de construction en amethyste", "Amethyst Builder Wand"),
        "titane_builder_wand": ("builder_wand_titane.png", True,
                                "Outil de construction en titane", "Titane Builder Wand"),
        "paladium_builder_wand": ("builder_wand_paladium.png", True,
                                  "Outil de construction en paladium", "Paladium Builder Wand"),
        "endium_builder_wand": ("builder_wand_endium.png", True,
                                "Outil de construction en endium", "Endium Builder Wand"),
        "moula_stone": ("moula_stone.png", False, "Dollars Stone", "Dollars Stone"),
        "hardness_potion": ("hardness_potion.png", False, "Potion de durete", "Hardness Potion"),
    }
    for name, (texture, handheld, label_fr, label_en) in MINER_ITEMS.items():
        if not copy_texture(root, "items/" + texture, "item/%s.png" % name, wrong_size):
            missing.append("items/" + texture)
        write("assets/%s/models/item/%s.json" % (NS, name), {
            "parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated",
            "textures": {"layer0": "%s:item/%s" % (NS, name)},
        })
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)},
        })
        fr["item.%s.%s" % (NS, name)] = label_fr
        en["item.%s.%s" % (NS, name)] = label_en

    # L'aimant change d'icone selon son etat. L'etat est la presence du
    # composant enchantment_glint_override (MagnetItem) : la definition d'item
    # le teste avec has_component, sans code client.
    for state, texture in (("magnet", "magnet.png"), ("magnet_on", "magnet_on.png")):
        if not copy_texture(root, "items/" + texture, "item/%s.png" % state, wrong_size):
            missing.append("items/" + texture)
        write("assets/%s/models/item/%s.json" % (NS, state), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "%s:item/%s" % (NS, state)},
        })
    write("assets/%s/items/magnet.json" % NS, {
        "model": {
            "type": "minecraft:condition",
            "property": "minecraft:has_component",
            "component": "minecraft:enchantment_glint_override",
            "on_true": {"type": "minecraft:model", "model": "%s:item/magnet_on" % NS},
            "on_false": {"type": "minecraft:model", "model": "%s:item/magnet" % NS},
        },
    })
    fr["item.%s.magnet" % NS] = "Aimant"
    en["item.%s.magnet" % NS] = "Magnet"

    # L'icone de l'effet de durete : PalaMod n'en avait pas (CustomPotion sans
    # setIconIndex), et 26.2 afficherait du violet. On reprend la potion,
    # centree dans les 18x18 d'une icone d'effet.
    potion = os.path.join(RES, "assets", NS, "textures", "item", "hardness_potion.png")
    if os.path.exists(potion):
        from PIL import Image
        icon = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
        icon.paste(Image.open(potion).convert("RGBA"), (1, 1))
        target = os.path.join(RES, "assets", NS, "textures", "mob_effect", "hardness.png")
        os.makedirs(os.path.dirname(target), exist_ok=True)
        icon.save(target)
    fr["effect.%s.hardness" % NS] = "Durete"
    en["effect.%s.hardness" % NS] = "Hardness"

    # La Voidstone minage : un objet plat.
    if not copy_texture(root, "items/voidstone_minage.png", "item/voidstone_minage.png", wrong_size):
        missing.append("items/voidstone_minage.png")
    write("assets/%s/models/item/voidstone_minage.json" % NS, {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "%s:item/voidstone_minage" % NS},
    })
    write("assets/%s/items/voidstone_minage.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:item/voidstone_minage" % NS},
    })
    fr["item.%s.voidstone_minage" % NS] = "Voidstone minage"
    en["item.%s.voidstone_minage" % NS] = "Mining Voidstone"

    def miner_block(name, textures, label_fr, label_en, parent="minecraft:block/cube_all",
                    orientable=False, drop=True, item=True, render_type=None, pickaxe=True):
        """Un bloc du Mineur : textures, modele, etat, objet, butin, nom, tags.

        textures : {cle du modele: chemin sous textures/ de PalaMod}. Chaque
        texture est copiee sous block/<name>_<cle>.png, ou block/<name>.png
        s'il n'y en a qu'une.
        """
        model_textures = {}
        for key, source in textures.items():
            target = name if len(textures) == 1 else "%s_%s" % (name, key)
            if not copy_texture(root, source, "block/%s.png" % target, wrong_size):
                missing.append(source)
            model_textures[key] = "%s:block/%s" % (NS, target)
        if "particle" not in model_textures:
            model_textures["particle"] = next(iter(model_textures.values()))
        model = {"parent": parent, "textures": model_textures}
        if render_type:
            model["render_type"] = render_type
        write("assets/%s/models/block/%s.json" % (NS, name), model)
        if orientable:
            # La face avant regarde le joueur : nord sans rotation, comme le
            # fourneau vanilla.
            write("assets/%s/blockstates/%s.json" % (NS, name), {"variants": {
                "facing=%s" % facing: dict({"model": "%s:block/%s" % (NS, name)},
                                           **({"y": rot} if rot else {}))
                for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270))
            }})
        else:
            write("assets/%s/blockstates/%s.json" % (NS, name), {
                "variants": {"": {"model": "%s:block/%s" % (NS, name)}},
            })
        if item:
            write("assets/%s/models/item/%s.json" % (NS, name),
                  {"parent": "%s:block/%s" % (NS, name)})
            write("assets/%s/items/%s.json" % (NS, name), {
                "model": {"type": "minecraft:model", "model": "%s:block/%s" % (NS, name)},
            })
        write("data/%s/loot_table/blocks/%s.json" % (NS, name), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, name)}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }] if drop else [],
        })
        fr["block.%s.%s" % (NS, name)] = label_fr
        en["block.%s.%s" % (NS, name)] = label_en
        if pickaxe:
            tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, name)])

    # La void stone : aucun drop par table, elle rend la Voidstone minage et sa
    # reserve par playerWillDestroy. Pas d'objet propre.
    miner_block("void_stone", {"all": "blocks/voidstone.png"},
                "Void stone", "Void Stone", drop=False, item=False)
    # Le piston renforce wither : oriente, face avant + cotes, dessus et
    # dessous a la texture de cote (getIcon de BlockWitheredReinforcedPiston).
    miner_block("withered_reinforced_piston",
                {"front": "blocks/withered_reinforced_piston_front.png",
                 "side": "blocks/withered_reinforced_piston_side.png"},
                "Piston renforce wither", "Withered Reinforced Piston",
                parent="minecraft:block/orientable", orientable=True)
    # orientable demande aussi « top » : le cote, comme dans l'original.
    write("assets/%s/models/block/withered_reinforced_piston.json" % NS, {
        "parent": "minecraft:block/orientable",
        "textures": {
            "front": "%s:block/withered_reinforced_piston_front" % NS,
            "side": "%s:block/withered_reinforced_piston_side" % NS,
            "top": "%s:block/withered_reinforced_piston_side" % NS,
            "particle": "%s:block/withered_reinforced_piston_side" % NS,
        },
    })

    # Les cave blocks : du verre troue. Pas de render_type : depuis 26.1 le
    # jeu choisit lui-meme la passe cutout d'apres les pixels transparents de
    # la texture. Pioche en pierre (BlockCave : setHarvestLevel("pickaxe", 1)).
    miner_block("cave_block", {"all": "blocks/caveblock/cave_block.png"},
                "Cave block", "Cave Block")
    miner_block("endium_cave_block", {"all": "blocks/caveblock/endium_cave_block.png"},
                "Cave block en endium", "Endium Cave Block")
    tag("minecraft/tags/block/needs_stone_tool.json",
        ["%s:cave_block" % NS, "%s:endium_cave_block" % NS])

    # Le minerai de pepites d'endium : une pepite, toujours une seule — ni
    # fortune ni toucher de soie (BlockEndiumNuggetOre.getDrops). Le casser
    # demande Mineur 100, par le tag palamod:break_locked que lit JobGateEvents.
    miner_block("endium_nugget_ore", {"all": "blocks/miner/endium_nuggets_ore.png"},
                "Minerai de pepites d'endium", "Endium Nugget Ore")
    # Demande du joueur (2026-11-09) : le minerai se rend lui-meme et donne sa
    # pepite au four, comme les autres.
    write("data/%s/loot_table/blocks/endium_nugget_ore.json" % NS, {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": "%s:endium_nugget_ore" % NS}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
    smelt("endium_nugget_ore", "%s:endium_nugget" % NS, ORE_XP["endium_nugget_ore"])
    tag("%s/tags/block/break_locked.json" % NS, ["%s:endium_nugget_ore" % NS])

    # La Palafurnace et la Palaforge : orientees comme le fourneau vanilla,
    # avec une face avant eteinte et une allumee (propriete lit).
    def lit_machine(name, front_off, front_on, side, top, label_fr, label_en, tool):
        sources = {"front_off": front_off, "front_on": front_on, "side": side, "top": top}
        for key, source in sources.items():
            if not copy_texture(root, source, "block/%s_%s.png" % (name, key), wrong_size):
                missing.append(source)
        for suffix, front in (("", "front_off"), ("_on", "front_on")):
            write("assets/%s/models/block/%s%s.json" % (NS, name, suffix), {
                "parent": "minecraft:block/orientable",
                "textures": {
                    "front": "%s:block/%s_%s" % (NS, name, front),
                    "side": "%s:block/%s_side" % (NS, name),
                    "top": "%s:block/%s_top" % (NS, name),
                    "particle": "%s:block/%s_side" % (NS, name),
                },
            })
        write("assets/%s/blockstates/%s.json" % (NS, name), {"variants": {
            "facing=%s,lit=%s" % (facing, lit): dict(
                {"model": "%s:block/%s%s" % (NS, name, "_on" if lit == "true" else "")},
                **({"y": rot} if rot else {}))
            for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270))
            for lit in ("false", "true")
        }})
        write("assets/%s/models/item/%s.json" % (NS, name), {"parent": "%s:block/%s" % (NS, name)})
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:block/%s" % (NS, name)},
        })
        write("data/%s/loot_table/blocks/%s.json" % (NS, name), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, name)}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
        fr["block.%s.%s" % (NS, name)] = label_fr
        en["block.%s.%s" % (NS, name)] = label_en
        tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, name)])
        tag("minecraft/tags/block/%s.json" % tool, ["%s:%s" % (NS, name)])

    # Dessus = cote pour la Palafurnace (BlockPaladiumFurnace.getIcon) ;
    # la forge a un dessus propre.
    lit_machine("paladium_furnace", "blocks/machines/paladium_furnace_front_off.png",
                "blocks/machines/paladium_furnace_front_on.png",
                "blocks/machines/paladium_furnace_side.png",
                "blocks/machines/paladium_furnace_side.png",
                "Palafurnace", "Palafurnace", "needs_iron_tool")
    lit_machine("palaforge", "blocks/machines/forge_front_off.png",
                "blocks/machines/forge_front_on.png", "blocks/machines/forge_side.png",
                "blocks/machines/forge_top.png", "Palaforge", "Palaforge", "needs_stone_tool")

    # L'amelioration de fourneau : jusqu'a seize dans la case de gauche.
    if not copy_texture(root, "items/machine/furnace_upgrade.png", "item/furnace_upgrade.png",
                        wrong_size):
        missing.append("items/machine/furnace_upgrade.png")
    write("assets/%s/models/item/furnace_upgrade.json" % NS, {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "%s:item/furnace_upgrade" % NS},
    })
    write("assets/%s/items/furnace_upgrade.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:item/furnace_upgrade" % NS},
    })
    fr["item.%s.furnace_upgrade" % NS] = "Amelioration de fourneau"
    en["item.%s.furnace_upgrade" % NS] = "Furnace Upgrade"

    # L'entonnoir en paladium : le modele de l'entonnoir vanilla, retexture.
    # Le modele vanilla declare ses propres elements et lit #top, #side,
    # #inside et #particle : un enfant qui ne change que les textures suffit.
    for key in ("top", "inside", "outside"):
        source = "blocks/paladium_hopper_%s.png" % key
        if not copy_texture(root, source, "block/paladium_hopper_%s.png" % key, wrong_size):
            missing.append(source)
    hopper_textures = {
        "particle": "%s:block/paladium_hopper_outside" % NS,
        "top": "%s:block/paladium_hopper_top" % NS,
        "side": "%s:block/paladium_hopper_outside" % NS,
        "inside": "%s:block/paladium_hopper_inside" % NS,
    }
    write("assets/%s/models/block/paladium_hopper.json" % NS,
          {"parent": "minecraft:block/hopper", "textures": hopper_textures})
    write("assets/%s/models/block/paladium_hopper_side.json" % NS,
          {"parent": "minecraft:block/hopper_side", "textures": hopper_textures})
    write("assets/%s/blockstates/paladium_hopper.json" % NS, {"variants": {
        "facing=down": {"model": "%s:block/paladium_hopper" % NS},
        "facing=north": {"model": "%s:block/paladium_hopper_side" % NS},
        "facing=east": {"model": "%s:block/paladium_hopper_side" % NS, "y": 90},
        "facing=south": {"model": "%s:block/paladium_hopper_side" % NS, "y": 180},
        "facing=west": {"model": "%s:block/paladium_hopper_side" % NS, "y": 270},
    }})
    # L'objet est une icone plate, comme l'entonnoir vanilla.
    if not copy_texture(root, "items/paladium_hopper.png", "item/paladium_hopper.png", wrong_size):
        missing.append("items/paladium_hopper.png")
    write("assets/%s/models/item/paladium_hopper.json" % NS, {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "%s:item/paladium_hopper" % NS},
    })
    write("assets/%s/items/paladium_hopper.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:item/paladium_hopper" % NS},
    })
    write("data/%s/loot_table/blocks/paladium_hopper.json" % NS, {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": "%s:paladium_hopper" % NS}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
    fr["block.%s.paladium_hopper" % NS] = "Entonnoir en paladium"
    en["block.%s.paladium_hopper" % NS] = "Paladium Hopper"
    tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:paladium_hopper" % NS])
    # Les blocs propres a DoHy (tools/dohy_assets.py) : ce fichier de tag est ecrit ici,
    # un second generateur ne peut pas y ajouter sa ligne sans que celui-ci l'efface.
    tag("minecraft/tags/block/mineable/pickaxe.json",
        ["dohy:trading_server", "dohy:gmo_machine", "dohy:water_dispenser"])
    tag("minecraft/tags/block/mineable/axe.json", ["dohy:trough"])
    # Les cereales de DoHy sont des cultures comme les autres : le tag vanilla
    # les fait pietiner, fertiliser et recolter par les villageois.
    tag("minecraft/tags/block/crops.json", ["dohy:%s_crop" % cereal for cereal in
                                           ("corn", "oat", "rye", "barley", "soy", "fonio", "sorghum", "kamut")])

    # Les coffres en amethyste et en titane. L'original les dessine avec le
    # ModelChest de 1.7.10 (TileEntityModdedChestRenderer) ; le meme modele en
    # JSON, couvercle ferme : socle 14x10x14, couvercle 14x5x14, serrure
    # 2x4x1, sur une texture de coffre 64x64 (UV en pixels / 4). Face avant au
    # sud, comme le coffre vanilla sans rotation.
    def chest_faces(faces):
        return {face: {"uv": uv, "texture": "#chest"} for face, uv in faces.items()}

    chest_elements = [
        {"from": [1, 0, 1], "to": [15, 10, 15], "faces": chest_faces({
            "north": [10.5, 8.25, 14, 10.75], "south": [3.5, 8.25, 7, 10.75],
            "west": [0, 8.25, 3.5, 10.75], "east": [7, 8.25, 10.5, 10.75],
            "down": [7, 4.75, 10.5, 8.25], "up": [3.5, 4.75, 7, 8.25]})},
        {"from": [1, 9, 1], "to": [15, 14, 15], "faces": chest_faces({
            "north": [10.5, 3.5, 14, 4.75], "south": [3.5, 3.5, 7, 4.75],
            "west": [0, 3.5, 3.5, 4.75], "east": [7, 3.5, 10.5, 4.75],
            "up": [3.5, 0, 7, 3.5], "down": [7, 0, 10.5, 3.5]})},
        {"from": [7, 7, 15], "to": [9, 11, 16], "faces": chest_faces({
            "south": [0.25, 0.25, 0.75, 1.25], "west": [0, 0.25, 0.25, 1.25],
            "east": [0.75, 0.25, 1, 1.25], "up": [0.25, 0, 0.75, 0.25],
            "down": [0.75, 0, 1.25, 0.25], "north": [1, 0.25, 1.5, 1.25]})},
    ]
    # Les cinq coffres de PalaMod, le coffre de l'End en paladium, et le coffre
    # en or d'Iron Chests (dont la texture n'est que dans son jar).
    iron_chests = os.path.join(os.path.dirname(root[1].rstrip("/")), "ironchest-prod") if root[1] else None
    for name, texture, particle, label_fr, label_en in (
            ("amethyst_chest", "models/AmethystChest.png", "%s:block/amethyst_block" % NS,
             "Coffre en amethyste", "Amethyst Chest"),
            ("titane_chest", "models/TitaneChest.png", "%s:block/titane_block" % NS,
             "Coffre en titane", "Titane Chest"),
            ("paladium_chest", "models/PaladiumChest.png", "%s:block/paladium_block" % NS,
             "Coffre en paladium", "Paladium Chest"),
            ("green_paladium_chest", "models/gpaladium_Chest.png", "%s:block/green_paladium_block" % NS,
             "Coffre en paladium vert", "Green Paladium Chest"),
            ("endium_chest", "models/EndiumChest.png", "%s:block/endium_block" % NS,
             "Coffre en endium", "Endium Chest"),
            ("paladium_ender_chest", "models/PaladiumEnderChest.png", "minecraft:block/obsidian",
             "Coffre de l'End en paladium", "Paladium Ender Chest"),
            ("gold_chest", None, "minecraft:block/gold_block", "Coffre en or", "Gold Chest")):
        if texture is None:
            source = os.path.join(iron_chests or "", "assets", "ironchest", "textures", "model", "goldchest.png")
            if iron_chests and os.path.exists(source) and png_size(source) == (64, 64):
                target = os.path.join(RES, "assets", NS, "textures", "block", "%s.png" % name)
                shutil.copyfile(source, target)
            else:
                missing.append("ironchest:model/goldchest.png")
        elif not copy_texture(root, texture, "block/%s.png" % name, wrong_size, expected=(64, 64)):
            missing.append(texture)
        write("assets/%s/models/block/%s.json" % (NS, name), {
            "parent": "minecraft:block/block",
            "textures": {"chest": "%s:block/%s" % (NS, name),
                         "particle": particle},
            "elements": chest_elements,
        })
        write("assets/%s/blockstates/%s.json" % (NS, name), {"variants": {
            "facing=%s" % facing: dict({"model": "%s:block/%s" % (NS, name)},
                                       **({"y": rot} if rot else {}))
            for facing, rot in (("south", 0), ("west", 90), ("north", 180), ("east", 270))
        }})
        write("assets/%s/models/item/%s.json" % (NS, name), {"parent": "%s:block/%s" % (NS, name)})
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:block/%s" % (NS, name)},
        })
        if name == "paladium_ender_chest":
            # Comme le coffre de l'End vanilla : 8 obsidiennes, lui-meme au
            # Toucher de soie.
            write("data/%s/loot_table/blocks/%s.json" % (NS, name), {
                "type": "minecraft:block",
                "pools": [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
                    {"type": "minecraft:item", "name": "%s:%s" % (NS, name), "conditions": [{
                        "condition": "minecraft:match_tool", "predicate": {"predicates": {
                            "minecraft:enchantments": [{"enchantments": "minecraft:silk_touch",
                                                        "levels": {"min": 1}}]}}}]},
                    {"type": "minecraft:item", "name": "minecraft:obsidian", "functions": [
                        {"function": "minecraft:set_count", "count": 8},
                        {"function": "minecraft:explosion_decay"}]}]}]}]})
        else:
            write("data/%s/loot_table/blocks/%s.json" % (NS, name), {
                "type": "minecraft:block",
                "pools": [{
                    "rolls": 1,
                    "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, name)}],
                    "conditions": [{"condition": "minecraft:survives_explosion"}],
                }],
            })
        fr["block.%s.%s" % (NS, name)] = label_fr
        en["block.%s.%s" % (NS, name)] = label_en
        tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, name)])
        if name != "gold_chest":
            tag("minecraft/tags/block/needs_iron_tool.json", ["%s:%s" % (NS, name)])
    fr["container.%s.paladium_ender_chest" % NS] = "Coffre de l'End en paladium"
    en["container.%s.paladium_ender_chest" % NS] = "Paladium Ender Chest"
    # PaladiumEnderChest CraftRegistry : un coffre de l'End, sans rien d'autre.
    shapeless("paladium_ender_chest", ["minecraft:ender_chest"], "%s:paladium_ender_chest" % NS)
    # Iron Chests : huit lingots d'or autour d'un coffre en fer. Le coffre en fer
    # n'est pas porte : le coffre vanilla le remplace, entoure de 4 lingots de
    # fer et 4 d'or — le fer qu'aurait coute le coffre en fer, en partie.
    shaped("gold_chest", ["GIG", "ICI", "GIG"], {"G": "minecraft:gold_ingot", "I": "minecraft:iron_ingot",
                                                  "C": "minecraft:chest"}, "%s:gold_chest" % NS)

    # Ce qu'aucun outil de zone ne casse (AreaMining.IMMUNE).
    tag("%s/tags/block/area_mining_immune.json" % NS,
        ["minecraft:obsidian", "minecraft:crying_obsidian"])

    # ---- chapitre 9 : l'eau angelique ----
    #
    # Un fluide : ses textures animees (16 x 16n, avec leur .mcmeta) vont dans
    # block/fluid/, ou les lit le modele de fluide enregistre par DoHyClient. Le
    # bloc n'a qu'une particule, comme l'eau vanilla, et pas d'objet : c'est le
    # seau.
    for source, target in (("blocks/fluids/angelicwater_still", "block/fluid/angelic_water_still"),
                           ("blocks/fluids/angelicwater_flowing", "block/fluid/angelic_water_flow")):
        found = False
        for origin in (root[0], root[1]):
            if not origin:
                continue
            base = os.path.join(origin, "assets", NS, "textures", source)
            if os.path.exists(base + ".png"):
                destination = os.path.join(RES, "assets", NS, "textures", target)
                os.makedirs(os.path.dirname(destination), exist_ok=True)
                shutil.copyfile(base + ".png", destination + ".png")
                if os.path.exists(base + ".png.mcmeta"):
                    shutil.copyfile(base + ".png.mcmeta", destination + ".png.mcmeta")
                found = True
                break
        if not found:
            missing.append(source + ".png")
    write("assets/%s/models/block/angelic_water.json" % NS,
          {"textures": {"particle": "%s:block/fluid/angelic_water_still" % NS}})
    write("assets/%s/blockstates/angelic_water.json" % NS,
          {"variants": {"": {"model": "%s:block/angelic_water" % NS}}})
    write("data/%s/loot_table/blocks/angelic_water.json" % NS, {"type": "minecraft:block", "pools": []})
    if not copy_texture(root, "items/fluids/bucket_angelic.png", "item/angelic_water_bucket.png", wrong_size):
        missing.append("items/fluids/bucket_angelic.png")
    write("assets/%s/models/item/angelic_water_bucket.json" % NS, {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "%s:item/angelic_water_bucket" % NS},
    })
    write("assets/%s/items/angelic_water_bucket.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:item/angelic_water_bucket" % NS},
    })
    fr["block.%s.angelic_water" % NS] = "Eau angelique"
    en["block.%s.angelic_water" % NS] = "Angelic Water"
    fr["item.%s.angelic_water_bucket" % NS] = "Seau d'eau angelique"
    en["item.%s.angelic_water_bucket" % NS] = "Angelic Water Bucket"
    # PRegister_Items : ["Y", "X"], une potion de regeneration (meta 8193) sur
    # un seau d'eau. Le seau d'eau rend son seau vide, comme en 1.7.10. La potion
    # se reconnait a son contenu : un ingredient a composants de NeoForge.
    shaped("angelic_water_bucket", ["Y", "X"], {
        "Y": {"neoforge:ingredient_type": "neoforge:components",
              "items": "minecraft:potion",
              "components": {"minecraft:potion_contents": {"potion": "minecraft:regeneration"}},
              "strict": False},
        "X": "minecraft:water_bucket",
    }, "%s:angelic_water_bucket" % NS)

    # ---- chapitre 10 : le Chasseur ----
    def hunter_item(name, textures, label_fr, label_en, parent="minecraft:item/generated"):
        """Un objet a icone : textures = {suffixe: chemin sous textures/ du pack}."""
        for suffix, source in textures.items():
            destination = "item/%s%s.png" % (name, suffix)
            pack = os.path.join(root[0] or "", "assets", source)
            if os.path.exists(pack):
                target = os.path.join(RES, "assets", NS, "textures", destination)
                os.makedirs(os.path.dirname(target), exist_ok=True)
                shutil.copyfile(pack, target)
                provenance["pack"] += 1
            else:
                missing.append(source)
            write("assets/%s/models/item/%s%s.json" % (NS, name, suffix), {
                "parent": parent, "textures": {"layer0": "%s:item/%s%s" % (NS, name, suffix)}})
        fr["item.%s.%s" % (NS, name)] = label_fr
        en["item.%s.%s" % (NS, name)] = label_en

    # Canne a peche : lancee ou non, comme la canne vanilla.
    hunter_item("paladium_fishing_rod", {"": "palajobs/textures/items/paladium_rod_uncast.png",
                                         "_cast": "palajobs/textures/items/paladium_rod_cast.png"},
                "Canne a peche en paladium", "Paladium Fishing Rod", parent="minecraft:item/handheld_rod")
    write("assets/%s/items/paladium_fishing_rod.json" % NS, {"model": {
        "type": "minecraft:condition", "property": "minecraft:fishing_rod/cast",
        "on_true": {"type": "minecraft:model", "model": "%s:item/paladium_fishing_rod_cast" % NS},
        "on_false": {"type": "minecraft:model", "model": "%s:item/paladium_fishing_rod" % NS},
    }})

    hunter_item("capture_sword", {"": "palamod/textures/items/capture_sword.png"},
                "Epee de capture", "Capture Sword", parent="minecraft:item/handheld")
    write("assets/%s/items/capture_sword.json" % NS,
          {"model": {"type": "minecraft:model", "model": "%s:item/capture_sword" % NS}})

    # Pierre de capture : pleine des qu'elle porte une creature.
    hunter_item("capture_stone", {"": "palamod/textures/items/capture_stone_empty.png",
                                  "_full": "palamod/textures/items/capture_stone_full.png"},
                "Pierre de capture", "Capture Stone")
    write("assets/%s/items/capture_stone.json" % NS, {"model": {
        "type": "minecraft:condition", "property": "minecraft:has_component",
        "component": "%s:captured_entity" % NS,
        "on_true": {"type": "minecraft:model", "model": "%s:item/capture_stone_full" % NS},
        "on_false": {"type": "minecraft:model", "model": "%s:item/capture_stone" % NS},
    }})
    fr["item.%s.capture_stone.full" % NS] = "Pierre de capture : %s"
    en["item.%s.capture_stone.full" % NS] = "Capture Stone: %s"
    fr["item.%s.capture_stone.empty" % NS] = "Cette pierre de capture est vide"
    en["item.%s.capture_stone.empty" % NS] = "This capture stone is empty"
    fr["item.%s.capture_stone.entity" % NS] = "Entite : %s"
    en["item.%s.capture_stone.entity" % NS] = "Entity: %s"

    hunter_item("cavern_hammer", {"": "palamod/textures/items/cavern_hammer.png"},
                "Marteau des cavernes", "Cavern Hammer", parent="minecraft:item/handheld")
    write("assets/%s/items/cavern_hammer.json" % NS,
          {"model": {"type": "minecraft:model", "model": "%s:item/cavern_hammer" % NS}})
    fr["item.%s.cavern_hammer.tooltip" % NS] = "Casse un spawner de donjon pour en recolter l'ame"
    en["item.%s.cavern_hammer.tooltip" % NS] = "Break a dungeon spawner to harvest its soul"
    for key, label_fr, label_en in (
            ("cannot_collect", "Impossible de recuperer les ames de ce spawner.",
             "You can't collect the souls of this spawner."),
            ("wrong_type", "Vous ne pouvez pas recuperer les ames de ce spawner.",
             "You can't collect souls from this spawner."),
            ("no_spawner", "Vous n'avez pas de spawner disponible pour recuperer les ames.",
             "You have no spawner available to collect the souls.")):
        fr["message.%s.cavern_hammer.%s" % (NS, key)] = label_fr
        en["message.%s.cavern_hammer.%s" % (NS, key)] = label_en

    # Spawner casse : un objet seulement, dessine en cube.
    source = os.path.join(root[0] or "", "assets", "palamod", "textures", "blocks", "empty_broken_mob_spawner.png")
    if os.path.exists(source):
        target = os.path.join(RES, "assets", NS, "textures", "block", "broken_spawner.png")
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copyfile(source, target)
    else:
        missing.append(source)
    write("assets/%s/models/item/broken_spawner.json" % NS, {
        "parent": "minecraft:block/cube_all", "textures": {"all": "%s:block/broken_spawner" % NS}})
    write("assets/%s/items/broken_spawner.json" % NS,
          {"model": {"type": "minecraft:model", "model": "%s:item/broken_spawner" % NS}})
    fr["item.%s.broken_spawner" % NS] = "Spawner casse"
    en["item.%s.broken_spawner" % NS] = "Broken Spawner"

    # Spawner customise : une cage par palier (spawner/ du pack), dessus et
    # dessous communs. L'objet suit le palier par sa donnee de modele.
    tiers = ("regular", "amethyst", "titane", "paladium", "endium")
    for name in ("spawner_top",) + tuple("%s_spawner_side" % tier for tier in tiers):
        source = os.path.join(root[0] or "", "assets", "palamod", "textures", "blocks", "spawner", name + ".png")
        if os.path.exists(source):
            target = os.path.join(RES, "assets", NS, "textures", "block", "spawner", name + ".png")
            os.makedirs(os.path.dirname(target), exist_ok=True)
            shutil.copyfile(source, target)
        else:
            missing.append(source)
    for index, tier in enumerate(tiers):
        write("assets/%s/models/block/empty_mob_spawner_tier%d.json" % (NS, index), {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {"top": "%s:block/spawner/spawner_top" % NS,
                         "bottom": "%s:block/spawner/spawner_top" % NS,
                         "side": "%s:block/spawner/%s_spawner_side" % (NS, tier),
                         "particle": "%s:block/spawner/%s_spawner_side" % (NS, tier)}})
    write("assets/%s/blockstates/empty_mob_spawner.json" % NS, {"variants": {
        "tier=%d" % index: {"model": "%s:block/empty_mob_spawner_tier%d" % (NS, index)} for index in range(5)}})
    write("assets/%s/models/item/empty_mob_spawner.json" % NS, {"parent": "%s:block/empty_mob_spawner_tier0" % NS})
    write("assets/%s/items/empty_mob_spawner.json" % NS, {"model": {
        "type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
        "entries": [{"threshold": index, "model": {"type": "minecraft:model",
                                                   "model": "%s:block/empty_mob_spawner_tier%d" % (NS, index)}}
                    for index in range(1, 5)],
        "fallback": {"type": "minecraft:model", "model": "%s:block/empty_mob_spawner_tier0" % NS},
    }})
    # Rendu par l'evenement des drops, avec ses ames : la table reste vide.
    write("data/%s/loot_table/blocks/empty_mob_spawner.json" % NS, {"type": "minecraft:block", "pools": []})
    fr["block.%s.empty_mob_spawner" % NS] = "Spawner vide"
    en["block.%s.empty_mob_spawner" % NS] = "Empty Spawner"
    fr["item.%s.empty_mob_spawner.filled" % NS] = "Spawner (%s)"
    en["item.%s.empty_mob_spawner.filled" % NS] = "Spawner (%s)"
    fr["item.%s.empty_mob_spawner.no_souls" % NS] = "Ne contient aucune ame"
    en["item.%s.empty_mob_spawner.no_souls" % NS] = "Contains no souls"
    fr["item.%s.empty_mob_spawner.tier" % NS] = "Palier %s : %s creatures au plus"
    en["item.%s.empty_mob_spawner.tier" % NS] = "Tier %s: up to %s creatures"
    fr["item.%s.empty_mob_spawner.souls" % NS] = "%s / %s ames de %s"
    en["item.%s.empty_mob_spawner.souls" % NS] = "%s / %s souls of %s"
    tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:empty_mob_spawner" % NS])

    # Recettes. La canne (CraftRegistry de PalaJobs) n'est pas dans le relevé.
    shaped("paladium_fishing_rod", ["  P", " SX", "S X"], {
        "P": "%s:paladium_ingot" % NS, "S": "%s:paladium_stick" % NS, "X": "minecraft:string",
    }, "%s:paladium_fishing_rod" % NS)
    # Epee de capture : la recette d'origine. Les quatre sticks a effet
    # (vitesse, force, soin, degats) sortent de la palamachine depuis le
    # chapitre 16 ; ils avaient ete remplaces par des batons en paladium.
    shaped("capture_sword", ["ABC", "DEF", "GHI"], {
        "A": "%s:speed_stick" % NS, "B": "%s:broadsword_head" % NS, "C": "%s:strength_stick" % NS,
        "D": "%s:fastsword_head" % NS, "E": "%s:compressed_paladium" % NS, "F": "%s:sword_head" % NS,
        "G": "%s:heal_stick" % NS, "H": "%s:paladium_stick" % NS, "I": "%s:damage_stick" % NS,
    }, "%s:capture_sword" % NS)
    # Marteau des cavernes : la recette d'origine, maintenant que le marteau en
    # paladium (grinder) existe.
    shaped("cavern_hammer", ["AAA", "BCB", "AAA"], {
        "A": "%s:titane_block" % NS, "B": "minecraft:obsidian", "C": "%s:paladium_hammer" % NS,
    }, "%s:cavern_hammer" % NS)
    # Reparation du spawner casse : la recette d'origine, maintenant que la
    #   pierre de gardien (mod GuardianGolem) existe.
    shaped("empty_mob_spawner", ["ABA", "CDE", "AFA"], {
        "A": "%s:findium" % NS, "B": "minecraft:skeleton_skull", "C": "%s:compressed_paladium" % NS,
        "D": "%s:broken_spawner" % NS, "E": "%s:guardian_stone" % NS, "F": "%s:crusher" % NS,
    }, "%s:empty_mob_spawner" % NS)

    tag("minecraft/tags/item/swords.json", ["%s:capture_sword" % NS])
    tag("minecraft/tags/item/pickaxes.json", ["%s:cavern_hammer" % NS])
    tag("minecraft/tags/item/enchantable/fishing.json", ["%s:paladium_fishing_rod" % NS])
    tag("minecraft/tags/item/enchantable/durability.json", ["%s:paladium_fishing_rod" % NS])

    # ---- chapitre 11 : l'Alchimiste ----
    #
    # Textures : le pack d'abord (blocks/log, planks, leave, sapling,
    # alchimiste, tanks, flowers ; items/alchimiste), le jar pour ce qu'il est
    # seul a avoir — la seve des tanks (alchimiste/seve-*, 32 x 32) et le modele
    # de l'extracteur (alchimiste/models/tape_*, 64 x 32).
    def tex(relative, destination, size=None):
        if not copy_texture(root, relative, destination, wrong_size, expected=size):
            missing.append(relative)

    def block_item(name):
        write("assets/%s/models/item/%s.json" % (NS, name), {"parent": "%s:block/%s" % (NS, name)})
        write("assets/%s/items/%s.json" % (NS, name),
              {"model": {"type": "minecraft:model", "model": "%s:block/%s" % (NS, name)}})

    def flat_item(name, texture):
        write("assets/%s/models/item/%s.json" % (NS, name),
              {"parent": "minecraft:item/generated", "textures": {"layer0": texture}})
        write("assets/%s/items/%s.json" % (NS, name),
              {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}})

    def cube(name, texture):
        write("assets/%s/blockstates/%s.json" % (NS, name),
              {"variants": {"": {"model": "%s:block/%s" % (NS, name)}}})
        write("assets/%s/models/block/%s.json" % (NS, name),
              {"parent": "minecraft:block/cube_all", "textures": {"all": texture}})

    def drops_itself(name):
        write("data/%s/loot_table/blocks/%s.json" % (NS, name), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, name)}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

    def drops_with(name, components):
        """Le bloc se rend avec les composants de sa block entity (tank, extracteur)."""
        write("data/%s/loot_table/blocks/%s.json" % (NS, name), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{
                "type": "minecraft:item", "name": "%s:%s" % (NS, name),
                "functions": [{"function": "minecraft:copy_components", "source": "block_entity",
                               "include": ["%s:%s" % (NS, component) for component in components]}]}]}]})

    def label(kind, name, label_fr, label_en):
        fr["%s.%s.%s" % (kind, NS, name)] = label_fr
        en["%s.%s.%s" % (kind, NS, name)] = label_en

    WOODS = (("jacaranda", "jacaranda", "de jacaranda", "Jacaranda"),
             ("judeecercis", "judeecercis", "de judeecercis", "Judeecercis"),
             ("erable", "erable", "d'erable", "Maple"),
             ("ostrya", "ostrya", "d'ostrya", "Ostrya"))

    # Buches : cote et bout, dans les trois axes.
    def log(name, source, label_fr, label_en):
        tex("blocks/log/%s.png" % source, "block/%s.png" % name)
        tex("blocks/log/%s_top.png" % source, "block/%s_top.png" % name)
        write("assets/%s/models/block/%s.json" % (NS, name), {
            "parent": "minecraft:block/cube_column",
            "textures": {"side": "%s:block/%s" % (NS, name), "end": "%s:block/%s_top" % (NS, name)}})
        write("assets/%s/models/block/%s_horizontal.json" % (NS, name), {
            "parent": "minecraft:block/cube_column_horizontal",
            "textures": {"side": "%s:block/%s" % (NS, name), "end": "%s:block/%s_top" % (NS, name)}})
        write("assets/%s/blockstates/%s.json" % (NS, name), {"variants": {
            "axis=y": {"model": "%s:block/%s" % (NS, name)},
            "axis=z": {"model": "%s:block/%s_horizontal" % (NS, name), "x": 90},
            "axis=x": {"model": "%s:block/%s_horizontal" % (NS, name), "x": 90, "y": 90}}})
        block_item(name)
        drops_itself(name)
        label("block", name, label_fr, label_en)
        tag("minecraft/tags/block/mineable/axe.json", ["%s:%s" % (NS, name)])
        # Les buches dans #logs : l'abattage de la hache en endium les prend.
        # Pas dans #logs_that_burn : PalaMod n'en faisait pas de charbon.
        tag("minecraft/tags/block/logs.json", ["%s:%s" % (NS, name)])
        tag("minecraft/tags/item/logs.json", ["%s:%s" % (NS, name)])

    fuels = {}
    for wood, source, of_fr, label_en in WOODS:
        log("%s_log" % wood, source, "Buche %s" % of_fr, "%s Log" % label_en)
        planks = "%s_planks" % wood
        tex("blocks/planks/%s.png" % source, "block/%s.png" % planks)
        cube(planks, "%s:block/%s" % (NS, planks))
        block_item(planks)
        drops_itself(planks)
        label("block", planks, "Planches %s" % of_fr, "%s Planks" % label_en)
        tag("minecraft/tags/block/mineable/axe.json", ["%s:%s" % (NS, planks)])
        # PWorld : une buche, quatre planches. Les planches ne sont pas dans
        # #planks : ni batons ni etabli, comme dans PalaMod.
        shapeless(planks, ["%s:%s_log" % (NS, wood)], "%s:%s" % (NS, planks), 4)
        fuels["%s:%s_log" % (NS, wood)] = 300
        fuels["%s:%s" % (NS, planks)] = 300

        leaves = "%s_leaves" % wood
        tex("blocks/leave/%s.png" % source, "block/%s.png" % leaves)
        cube(leaves, "%s:block/%s" % (NS, leaves))
        block_item(leaves)
        label("block", leaves, "Feuilles %s" % of_fr, "%s Leaves" % label_en)
        tag("minecraft/tags/block/mineable/hoe.json", ["%s:%s" % (NS, leaves)])
        tag("minecraft/tags/block/leaves.json", ["%s:%s" % (NS, leaves)])
        # BlockLeaveBase : la feuille a la cisaille ou au toucher de soie, sinon
        # (int)(Math.random() * 10000) <= 100, une pousse sur 99.
        write("data/%s/loot_table/blocks/%s.json" % (NS, leaves), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "name": "%s:%s" % (NS, leaves), "conditions": [{
                    "condition": "minecraft:any_of", "terms": [
                        {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}},
                        {"condition": "minecraft:match_tool", "predicate": {"predicates": {
                            "minecraft:enchantments": [{"enchantments": "minecraft:silk_touch",
                                                        "levels": {"min": 1}}]}}}]}]},
                {"type": "minecraft:item", "name": "%s:%s_sapling" % (NS, wood),
                 "conditions": [{"condition": "minecraft:random_chance", "chance": 0.0101}]},
            ]}]}]})

        sapling = "%s_sapling" % wood
        tex("blocks/sapling/%s.png" % source, "block/%s.png" % sapling)
        write("assets/%s/blockstates/%s.json" % (NS, sapling), {"variants": {
            "stage=0": {"model": "%s:block/%s" % (NS, sapling)},
            "stage=1": {"model": "%s:block/%s" % (NS, sapling)}}})
        write("assets/%s/models/block/%s.json" % (NS, sapling),
              {"parent": "minecraft:block/cross", "textures": {"cross": "%s:block/%s" % (NS, sapling)}})
        flat_item(sapling, "%s:block/%s" % (NS, sapling))
        drops_itself(sapling)
        label("block", sapling, "Pousse %s" % of_fr, "%s Sapling" % label_en)

        # L'arbre : l'une des deux formes de PalaMod (PalaTreeFeature).
        write("data/%s/worldgen/configured_feature/%s_tree.json" % (NS, wood), {
            "type": "%s:tree" % NS,
            "config": {"log": {"Name": "%s:%s_log" % (NS, wood), "Properties": {"axis": "y"}},
                       "leaves": {"Name": "%s:%s" % (NS, leaves)}}})
        # BaseWorldGenerator : un essai par essence et par chunk, sur de
        # l'herbe nue, nextInt(100) <= 8 soit 9 % — une chance sur 11. Divise
        # par deux a la demande du joueur : une sur 22.
        write("data/%s/worldgen/placed_feature/%s_tree.json" % (NS, wood), {
            "feature": "%s:%s_tree" % (NS, wood),
            "placement": [
                {"type": "minecraft:rarity_filter", "chance": 22},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
                {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
                {"type": "minecraft:block_predicate_filter", "predicate": {
                    "type": "minecraft:matching_blocks", "offset": [0, -1, 0], "blocks": "minecraft:grass_block"}},
                {"type": "minecraft:biome"}]})

        tapped = "tapped_%s_log" % wood
        # Buche entamee : du bois mort en haut, la buche en bas, en six etapes.
        for step in range(1, 7):
            height = round(16 * step / 6, 4)
            log_part = {"from": [0, 0, 0], "to": [16, height, 16], "faces": {
                "down": {"texture": "#log_top", "uv": [0, 0, 16, 16], "cullface": "down"}}}
            for face in ("north", "south", "east", "west"):
                log_part["faces"][face] = {"texture": "#log", "uv": [0, 16 - height, 16, 16], "cullface": face}
            elements = [log_part]
            if step == 6:
                log_part["faces"]["up"] = {"texture": "#log_top", "uv": [0, 0, 16, 16], "cullface": "up"}
            else:
                dead = {"from": [0, height, 0], "to": [16, 16, 16], "faces": {
                    "up": {"texture": "#dead_top", "uv": [0, 0, 16, 16], "cullface": "up"}}}
                for face in ("north", "south", "east", "west"):
                    dead["faces"][face] = {"texture": "#dead", "uv": [0, 0, 16, 16 - height], "cullface": face}
                elements.append(dead)
            write("assets/%s/models/block/%s_%d.json" % (NS, tapped, step), {
                "textures": {"particle": "%s:block/%s_log" % (NS, wood),
                             "log": "%s:block/%s_log" % (NS, wood), "log_top": "%s:block/%s_log_top" % (NS, wood),
                             "dead": "%s:block/dead_wood" % NS, "dead_top": "%s:block/dead_wood_top" % NS},
                "elements": elements})
        write("assets/%s/blockstates/%s.json" % (NS, tapped), {"variants": {
            "sap=%d" % sap: {"model": "%s:block/%s_%d" % (NS, tapped, (sap + 5) // 6)} for sap in range(1, 37)}})
        # Cassee, elle donne du bois mort : sa seve est perdue.
        write("data/%s/loot_table/blocks/%s.json" % (NS, tapped), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s:dead_wood" % NS}]}]})
        label("block", tapped, "Buche %s entamee" % of_fr, "Tapped %s Log" % label_en)
        tag("minecraft/tags/block/mineable/axe.json", ["%s:%s" % (NS, tapped)])

        shiny = "shiny_%s_wood" % wood
        tex("blocks/alchimiste/shiny_%s_block.png" % source, "block/%s.png" % shiny)
        cube(shiny, "%s:block/%s" % (NS, shiny))
        block_item(shiny)
        drops_itself(shiny)
        label("block", shiny, "Bois %s brillant" % of_fr, "Shiny %s Wood" % label_en)
        tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, shiny)])
        label("sap", wood, "Seve %s" % of_fr, "%s Sap" % label_en)

    # Bois mort : jamais genere, ce que laisse une buche videe.
    log("dead_wood", "dead_wood", "Buche de bois mort", "Dead Wood Log")
    tex("blocks/planks/dead_wood.png", "block/dead_wood_planks.png")
    cube("dead_wood_planks", "%s:block/dead_wood_planks" % NS)
    block_item("dead_wood_planks")
    drops_itself("dead_wood_planks")
    label("block", "dead_wood_planks", "Planches de bois mort", "Dead Wood Planks")
    tag("minecraft/tags/block/mineable/axe.json", ["%s:dead_wood_planks" % NS])
    shapeless("dead_wood_planks", ["%s:dead_wood" % NS], "%s:dead_wood_planks" % NS, 4)
    fuels["%s:dead_wood" % NS] = 300
    fuels["%s:dead_wood_planks" % NS] = 300
    # Bois de 1.7.10 (Material.wood) : 300 ticks au four.
    write("data/neoforge/data_maps/item/furnace_fuels.json",
          {"values": {item: {"burn_time": ticks} for item, ticks in sorted(fuels.items())}})
    write("data/%s/neoforge/biome_modifier/trees.json" % NS, {
        "type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
        "features": ["%s:%s_tree" % (NS, wood) for wood, _s, _f, _e in WOODS],
        "step": "vegetal_decoration"})

    # Fiole : vide, puis un modele par sixieme.
    for level in range(7):
        tex("items/alchimiste/flask%d.png" % level, "item/flask_%d.png" % level)
        write("assets/%s/models/item/flask_%d.json" % (NS, level),
              {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/flask_%d" % (NS, level)}})
    write("assets/%s/items/flask.json" % NS, {"model": {
        "type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
        "entries": [{"threshold": level, "model": {"type": "minecraft:model", "model": "%s:item/flask_%d" % (NS, level)}}
                    for level in range(1, 7)],
        "fallback": {"type": "minecraft:model", "model": "%s:item/flask_0" % NS}}})
    label("item", "flask", "Fiole vide", "Empty Flask")
    label("item", "flask.filled", "Fiole de seve", "Sap Flask")
    label("item", "flask.level", "Niveau de seve : %s%%", "Sap level: %s%%")
    label("item", "flask.sap", "Seve : %s", "Sap: %s")
    label("item", "flask.no_sap", "Aucune", "None")

    # Extracteur : le ModelExtractor de PalaMod (boites Techne), en elements.
    # Texture 64 x 32 : u / 4 et v / 2 pour l'espace 16 x 16 des modeles.
    tex("alchimiste/models/tape_on.png", "block/extractor_on.png", (64, 32))
    tex("alchimiste/models/tape_off.png", "block/extractor_off.png", (64, 32))
    extractor_boxes = (  # (de, a, u, v, largeur x, hauteur y, profondeur z)
        ((14.5, 3.5, 5.5), (15.5, 8.5, 10.5), 51, 11, 1, 5, 5),   # BaseContour
        ((5.5, 4, 6), (14.5, 8, 10), 0, 12, 9, 4, 4),             # Base
        ((7.5, 8, 7.5), (8.5, 10, 8.5), 32, 8, 1, 2, 1),          # Robinet
        ((6, 9.5, 7.5), (10, 10.5, 8.5), 35, 16, 4, 1, 1),        # LeverOne
        ((7.5, 9.5, 6), (8.5, 10.5, 10), 35, 11, 1, 1, 4),        # LeverTwo
        ((6.5, 0, 6.5), (9.5, 4, 9.5), 13, 13, 3, 4, 3),          # Exit
        ((6, 0, 6), (10, 1, 10), 12, 12, 4, 1, 4),                # ExitContour
    )

    def uv(u0, v0, u1, v1):
        return [round(u0 / 4, 4), round(v0 / 2, 4), round(u1 / 4, 4), round(v1 / 2, 4)]

    extractor_elements = []
    for start, end, u, v, w, h, d in extractor_boxes:
        extractor_elements.append({"from": list(start), "to": list(end), "faces": {
            "up": {"texture": "#tape", "uv": uv(u + d, v, u + d + w, v + d)},
            "down": {"texture": "#tape", "uv": uv(u + d + w, v, u + d + 2 * w, v + d)},
            "east": {"texture": "#tape", "uv": uv(u, v + d, u + d, v + d + h)},
            "west": {"texture": "#tape", "uv": uv(u + d + w, v + d, u + 2 * d + w, v + d + h)},
            "north": {"texture": "#tape", "uv": uv(u + d, v + d, u + d + w, v + d + h)},
            "south": {"texture": "#tape", "uv": uv(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)}}})
    for state in ("on", "off"):
        write("assets/%s/models/block/extractor_%s.json" % (NS, state), {
            "textures": {"tape": "%s:block/extractor_%s" % (NS, state),
                         "particle": "%s:block/extractor_%s" % (NS, state)},
            "elements": extractor_elements})
    # La fiole : TileEntityExtractorRenderer la dessine au pied de l'extracteur,
    # de cote (0,5 ; 0,25 pour l'angle 0), a 0,45 de sa taille. Ici deux
    # plans croises de 7 pixels, comme une fleur, avec l'icone de la fiole.
    for level in range(7):
        tex("items/alchimiste/flask%d.png" % level, "block/extractor_flask_%d.png" % level)
        sprite = "%s:block/extractor_flask_%d" % (NS, level)
        rotation = {"origin": [8, 0, 3.5], "axis": "y", "angle": 45}
        write("assets/%s/models/block/extractor_flask_%d.json" % (NS, level), {
            "textures": {"flask": sprite, "particle": sprite},
            "elements": [
                {"from": [4.5, 0, 3.5], "to": [11.5, 7, 3.5], "rotation": rotation, "shade": False, "faces": {
                    "north": {"texture": "#flask", "uv": [0, 0, 16, 16]},
                    "south": {"texture": "#flask", "uv": [0, 0, 16, 16]}}},
                {"from": [8, 0, 0], "to": [8, 7, 7], "rotation": rotation, "shade": False, "faces": {
                    "east": {"texture": "#flask", "uv": [0, 0, 16, 16]},
                    "west": {"texture": "#flask", "uv": [0, 0, 16, 16]}}}]})
    rotations = {"east": 0, "south": 90, "west": 180, "north": 270}
    parts = []
    for facing, rotation in rotations.items():
        for enabled in ("true", "false"):
            parts.append({"when": {"facing": facing, "enabled": enabled}, "apply": {
                "model": "%s:block/extractor_%s" % (NS, "on" if enabled == "true" else "off"), "y": rotation}})
        for shown in range(1, 8):
            parts.append({"when": {"facing": facing, "flask": str(shown)}, "apply": {
                "model": "%s:block/extractor_flask_%d" % (NS, shown - 1), "y": rotation}})
    write("assets/%s/blockstates/extractor.json" % NS, {"multipart": parts})
    write("assets/%s/models/item/extractor.json" % NS, {"parent": "%s:block/extractor_off" % NS})
    write("assets/%s/items/extractor.json" % NS,
          {"model": {"type": "minecraft:model", "model": "%s:block/extractor_off" % NS}})
    drops_with("extractor", ["extractor_uses"])
    label("block", "extractor", "Extracteur", "Extractor")
    label("item", "extractor.uses", "Utilisations restantes : %s/%s", "Uses left: %s/%s")
    tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:extractor" % NS])

    # Tanks : la coque a fenetres, et la seve a sa hauteur (multipart).
    for sap, source, _f, _e in WOODS:
        tex("alchimiste/seve-%s.png" % source, "block/sap/%s.png" % sap, (32, 32))
        for fill in range(1, 17):
            top = round(0.6 + fill * 14.8 / 16, 4)
            faces = {"up": {"texture": "#sap", "uv": [0, 0, 16, 16]},
                     "down": {"texture": "#sap", "uv": [0, 0, 16, 16]}}
            for face in ("north", "south", "east", "west"):
                faces[face] = {"texture": "#sap", "uv": [0, round(16 - fill, 4), 16, 16]}
            write("assets/%s/models/block/tank_sap_%s_%d.json" % (NS, sap, fill), {
                "textures": {"sap": "%s:block/sap/%s" % (NS, sap), "particle": "%s:block/sap/%s" % (NS, sap)},
                "elements": [{"from": [0.6, 0.6, 0.6], "to": [15.4, top, 15.4], "faces": faces}]})
    for tier, source, label_fr, label_en in (("gold", "gold", "Tank en or", "Gold Tank"),
                                             ("amethyst", "amethyste", "Tank en amethyste", "Amethyst Tank"),
                                             ("titane", "titane", "Tank en titane", "Titanium Tank"),
                                             ("paladium", "paladium", "Tank en paladium", "Paladium Tank")):
        name = "%s_tank" % tier
        tex("blocks/tanks/%s_tank.png" % source, "block/%s.png" % name)
        write("assets/%s/models/block/%s.json" % (NS, name),
              {"parent": "minecraft:block/cube_all", "textures": {"all": "%s:block/%s" % (NS, name)}})
        parts = [{"apply": {"model": "%s:block/%s" % (NS, name)}}]
        for sap, _s, _f, _e in WOODS:
            for fill in range(1, 17):
                parts.append({"when": {"sap": sap, "fill": str(fill)},
                              "apply": {"model": "%s:block/tank_sap_%s_%d" % (NS, sap, fill)}})
        write("assets/%s/blockstates/%s.json" % (NS, name), {"multipart": parts})
        block_item(name)
        drops_with(name, ["tank_sap"])
        label("block", name, label_fr, label_en)
        tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, name)])
    label("item", "tank.empty", "Vide", "Empty")
    label("item", "tank.content", "Contenu : %s", "Content: %s")
    label("item", "tank.level", "Niveau : %s / %s", "Level: %s / %s")
    label("message", "tank.empty", "Vide — 0 / %s", "Empty — 0 / %s")
    label("message", "tank.content", "%s : %s / %s", "%s: %s / %s")
    label("message", "tank.full", "Le tank est plein", "The tank is full")
    label("message", "tank.other_sap", "Ce tank contient une autre seve", "This tank holds another sap")
    label("message", "tank.not_enough", "Pas assez de seve dans le tank (%s)", "Not enough sap in the tank (%s)")

    # Chaudron.
    tex("items/alchimiste/lightning_potion.png", "item/lightning_potion.png")
    flat_item("lightning_potion", "%s:item/lightning_potion" % NS)
    label("item", "lightning_potion", "Potion de foudre", "Lightning Potion")
    tex("blocks/alchimiste/cauldron_top.png", "block/cauldron_top.png")
    tex("blocks/alchimiste/cauldron_side.png", "block/cauldron_side.png")
    tex("blocks/alchimiste/cauldron_core.png", "block/cauldron_core.png")
    write("assets/%s/blockstates/cauldron.json" % NS, {"variants": {"": {"model": "%s:block/cauldron" % NS}}})
    write("assets/%s/models/block/cauldron.json" % NS, {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {"top": "%s:block/cauldron_top" % NS, "bottom": "%s:block/cauldron_side" % NS,
                     "side": "%s:block/cauldron_side" % NS}})
    block_item("cauldron")
    drops_itself("cauldron")
    label("block", "cauldron", "Bloc du chaudron", "Cauldron Block")
    write("assets/%s/models/block/cauldron_core.json" % NS,
          {"parent": "minecraft:block/cube_all", "textures": {"all": "%s:block/cauldron_core" % NS}})
    write("assets/%s/blockstates/cauldron_core.json" % NS, {"variants": {
        "formed=false": {"model": "%s:block/cauldron_core" % NS},
        "formed=true": {"model": "%s:block/cauldron_core" % NS}}})
    block_item("cauldron_core")
    drops_itself("cauldron_core")
    label("block", "cauldron_core", "Coeur du chaudron", "Cauldron Core")
    tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:cauldron" % NS, "%s:cauldron_core" % NS])
    label("message", "cauldron.formed", "Le chaudron est forme", "The cauldron is formed")
    label("message", "cauldron.incomplete",
          "Il manque des blocs du chaudron : 7 x 7 au sol, un anneau de 5 x 5 au-dessus, le coeur au centre",
          "Cauldron blocks are missing: 7 x 7 floor, a 5 x 5 ring above, the core in the middle")

    # Fleurs. WorldGenCustomFlower : deux essais par chunk a nextInt(N) == 1,
    # mais a une hauteur tiree entre 50 et 74 qui doit tomber pile sur le sol —
    # une fois sur 25 environ. On garde ce rythme effectif : 25 N / 2 chunks.
    for name, source, label_fr, label_en, rarity in (
            ("harpagophytum_flower", "flower_harpagophytum", "Harpagophytum", "Harpagophytum", 12500),
            ("sauge_flower", "flower_sauge", "Sauge", "Sage", 18750),
            ("clathrusarcheri_flower", "flower_clathrusarcheri", "Clathrusarcheri", "Clathrus Archeri", 1250),
            ("endium_flower", "flower_endium", "Fleur d'endium", "Endium Flower", 2500),
            ("paladium_flower", "flower_paladium", "Fleur de paladium", "Paladium Flower", 125),
            ("mineral_flower", "flower_mineral", "Fleur minerale", "Mineral Flower", 6250)):
        tex("blocks/flowers/%s.png" % source, "block/%s.png" % name)
        write("assets/%s/blockstates/%s.json" % (NS, name), {"variants": {"": {"model": "%s:block/%s" % (NS, name)}}})
        write("assets/%s/models/block/%s.json" % (NS, name),
              {"parent": "minecraft:block/cross", "textures": {"cross": "%s:block/%s" % (NS, name)}})
        flat_item(name, "%s:block/%s" % (NS, name))
        drops_itself(name)
        label("block", name, label_fr, label_en)
        state = {"Name": "%s:%s" % (NS, name)}
        write("data/%s/worldgen/configured_feature/%s.json" % (NS, name), {
            "type": "minecraft:simple_block",
            "config": {"to_place": {"type": "minecraft:simple_state_provider", "state": state}}})
        write("data/%s/worldgen/placed_feature/%s.json" % (NS, name), {
            "feature": "%s:%s" % (NS, name),
            "placement": [
                {"type": "minecraft:rarity_filter", "chance": rarity},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"},
                {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
                    {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                    {"type": "minecraft:would_survive", "state": state}]}},
                {"type": "minecraft:biome"}]})
    # « Plains », « Extreme Hills », « Forest » seulement.
    write("data/%s/neoforge/biome_modifier/flowers.json" % NS, {
        "type": "neoforge:add_features",
        "biomes": ["minecraft:plains", "minecraft:windswept_hills", "minecraft:forest"],
        "features": ["%s:%s" % (NS, name) for name in (
            "harpagophytum_flower", "sauge_flower", "clathrusarcheri_flower", "endium_flower",
            "paladium_flower", "mineral_flower")],
        "step": "vegetal_decoration"})

    for metal, label_fr, label_en in (("amethyst", "Pollen d'amethyste", "Amethyst Pollen"),
                                      ("titane", "Pollen de titane", "Titanium Pollen"),
                                      ("paladium", "Pollen de paladium", "Paladium Pollen"),
                                      ("endium", "Pollen d'endium", "Endium Pollen")):
        tex("items/%s_pollen.png" % metal, "item/%s_pollen.png" % metal)
        flat_item("%s_pollen" % metal, "%s:item/%s_pollen" % (NS, metal))
        label("item", "%s_pollen" % metal, label_fr, label_en)

    # Portails.
    for metal, source, of_fr, label_en in (("amethyst", "amethyste", "en amethyste", "Amethyst"),
                                           ("titane", "titane", "en titane", "Titanium"),
                                           ("paladium", "paladium", "en paladium", "Paladium"),
                                           ("endium", "endium", "en endium", "Endium")):
        name = "%s_portal_block" % metal
        tex("blocks/alchimiste/%s_portal_block.png" % source, "block/%s.png" % name)
        cube(name, "%s:block/%s" % (NS, name))
        block_item(name)
        drops_itself(name)
        label("block", name, "Bloc de portail %s" % of_fr, "%s Portal Block" % label_en)
        tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, name)])
        key = "%s_portal_key" % metal
        tex("items/alchimiste/%s_portal_key.png" % source, "item/%s.png" % key)
        flat_item(key, "%s:item/%s" % (NS, key))
        label("item", key, "Cle de portail %s" % of_fr, "%s Portal Key" % label_en)
    tex("blocks/alchimiste/endium_angle_portal_block.png", "block/endium_angle_portal_block.png")
    cube("endium_angle_portal_block", "%s:block/endium_angle_portal_block" % NS)
    block_item("endium_angle_portal_block")
    drops_itself("endium_angle_portal_block")
    label("block", "endium_angle_portal_block", "Bloc d'angle du portail en endium", "Endium Portal Corner Block")
    tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:endium_angle_portal_block" % NS])
    tex("items/alchimiste/endium_heart.png", "item/endium_heart.png")
    flat_item("endium_heart", "%s:item/endium_heart" % NS)
    label("item", "endium_heart", "Coeur en endium", "Endium Heart")
    # La surface : une nappe animee de deux pixels (16 x 512, 32 images).
    for origin in (root[0], root[1]):
        source = os.path.join(origin or "", "assets", NS, "textures", "blocks", "alchimiste", "portal.png")
        if origin and os.path.exists(source):
            target = os.path.join(RES, "assets", NS, "textures", "block", "alchemist_portal.png")
            os.makedirs(os.path.dirname(target), exist_ok=True)
            shutil.copyfile(source, target)
            shutil.copyfile(source + ".mcmeta", target + ".mcmeta")
            break
    else:
        missing.append("blocks/alchimiste/portal.png")
    write("assets/%s/models/block/alchemist_portal.json" % NS, {
        "textures": {"portal": "%s:block/alchemist_portal" % NS, "particle": "%s:block/alchemist_portal" % NS},
        "elements": [{"from": [0, 0, 0], "to": [16, 2, 16], "faces": {
            "up": {"texture": "#portal", "uv": [0, 0, 16, 16]},
            "down": {"texture": "#portal", "uv": [0, 0, 16, 16], "cullface": "down"},
            "north": {"texture": "#portal", "uv": [0, 14, 16, 16]},
            "south": {"texture": "#portal", "uv": [0, 14, 16, 16]},
            "east": {"texture": "#portal", "uv": [0, 14, 16, 16]},
            "west": {"texture": "#portal", "uv": [0, 14, 16, 16]}}}]})
    write("assets/%s/blockstates/alchemist_portal.json" % NS,
          {"variants": {"": {"model": "%s:block/alchemist_portal" % NS}}})
    write("data/%s/loot_table/blocks/alchemist_portal.json" % NS, {"type": "minecraft:block", "pools": []})
    label("block", "alchemist_portal", "Portail d'alchimiste", "Alchemist Portal")
    label("message", "portal.wrong_key", "Cette cle n'ouvre pas ce portail", "This key doesn't open this portal")
    label("message", "portal.wrong_item", "Ce portail ne prend que de la seve de %s, en fiole pleine ou en tank",
          "This portal only takes %s sap, in a full flask or a tank")
    label("message", "cauldron.fill", "Remplissez d'abord le bassin : jetez-y (touche Q) un seau d'eau angelique",
          "Fill the basin first: drop (Q key) a bucket of angelic water into it")
    label("message", "cauldron.need_log", "Il manque une buche de jacaranda, de judee, d'erable ou d'ostrya",
          "Missing a jacaranda, judeecercis, maple or ostrya log")
    label("message", "cauldron.need_potion", "Il manque une potion de foudre", "Missing a lightning potion")
    label("message", "cauldron.unknown", "Le chaudron ne transforme pas : %s", "The cauldron does nothing with: %s")
    label("message", "portal.no_key", "Inserez la cle du portail", "Insert the portal key")
    label("message", "portal.sap", "Seve : %s / %s", "Sap: %s / %s")
    label("message", "portal.sap_pollen", "Seve : %s / %s — pollen d'endium : %s / %s",
          "Sap: %s / %s — endium pollen: %s / %s")
    label("item", "portal_key.sap", "Seve stockee : %s", "Stored sap: %s")
    label("item", "portal_key.no_sap", "Aucune seve stockee", "No stored sap")

    # Enchantement : la bibliotheque, la table, deux enchantements.
    tex("blocks/alchimiste/mystical_bookshelf.png", "block/mystical_bookshelf.png")
    cube("mystical_bookshelf", "%s:block/mystical_bookshelf" % NS)
    block_item("mystical_bookshelf")
    drops_itself("mystical_bookshelf")
    label("block", "mystical_bookshelf", "Bibliotheque mystique", "Mystical Bookshelf")
    tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:mystical_bookshelf" % NS])
    # Ni a la table vanilla ni en livre : absents des tags in_enchanting_table,
    # tradeable et on_random_loot. On ne les obtient qu'a la table de l'Alchimiste.
    write("data/%s/enchantment/no_fall.json" % NS, {
        "description": {"translate": "enchantment.%s.no_fall" % NS},
        "supported_items": "#minecraft:enchantable/foot_armor",
        "weight": 1, "max_level": 1,
        "min_cost": {"base": 30, "per_level_above_first": 0},
        "max_cost": {"base": 50, "per_level_above_first": 0},
        "anvil_cost": 4, "slots": ["feet"]})
    write("data/%s/enchantment/grass_breaker.json" % NS, {
        "description": {"translate": "enchantment.%s.grass_breaker" % NS},
        "supported_items": "#minecraft:swords",
        "weight": 1, "max_level": 3,
        "min_cost": {"base": 30, "per_level_above_first": 0},
        "max_cost": {"base": 50, "per_level_above_first": 0},
        "anvil_cost": 4, "slots": ["mainhand"]})
    label("enchantment", "no_fall", "Anti chute", "No Fall")
    label("enchantment", "no_fall.desc", "Empeche de tomber d'un bloc, comme accroupi",
          "Keeps you from walking off edges, as if sneaking")
    label("enchantment", "grass_breaker", "Grass Breaker", "Grass Breaker")
    label("enchantment", "grass_breaker.desc", "Plus de graines sur les hautes herbes",
          "More seeds from tall grass")
    label("container", "mystic_enchant", "Table de l'Alchimiste", "Alchemist Table")
    for key, label_fr, label_en in (
            ("done", "Enchante : %s", "Enchanted: %s"),
            ("missing", "Enchantement introuvable", "Enchantment not found"),
            ("no_item", "Tenez l'objet a enchanter en main", "Hold the item to enchant"),
            ("unsupported", "Cet objet ne prend pas cet enchantement", "This item can't take this enchantment"),
            ("max", "Cet enchantement est deja au niveau maximum", "This enchantment is already at its maximum level"),
            ("job", "Il faut %s niveau %s", "Requires %s level %s"),
            ("xp", "Il faut au moins %s niveaux d'experience", "Requires at least %s experience levels"),
            ("level", "Niveau : %s / %s", "Level: %s / %s"),
            ("cost", "Cout : %s niveaux d'experience", "Cost: %s experience levels"),
            ("requires", "Requiert %s niveau %s", "Requires %s level %s"),
            ("click", "Cliquez pour enchanter", "Click to enchant")):
        label("message", "mystic_enchant." + key, label_fr, label_en)

    # ---- grinder, outils du grinder, buisson a XP ----
    #
    # PSRegister_Blocks / PSRegister_Items : textures sous blocks/smeltery et
    # items/{patern,toolpart,modifier,hammer,broadsword,fastsword}, toutes dans
    # le pack officiel.
    def copy_animated(relative, destination, size):
        """Une texture animee (bande verticale) et son .mcmeta, pack d'abord."""
        tex(relative, destination, size)
        for origin in root:
            meta = origin and os.path.join(origin, "assets", NS, "textures", relative + ".mcmeta")
            if meta and os.path.exists(meta):
                shutil.copyfile(meta, os.path.join(RES, "assets", NS, "textures", destination + ".mcmeta"))
                return
        missing.append(relative + ".mcmeta")

    # Le grinder : la face avant regarde le joueur, les autres faces sont le cote.
    tex("blocks/smeltery/grinder_block_front.png", "block/grinder_front.png")
    tex("blocks/smeltery/grinder_block_side.png", "block/grinder_side.png")
    write("assets/%s/models/block/grinder.json" % NS, {
        "parent": "minecraft:block/orientable",
        "textures": {"front": "%s:block/grinder_front" % NS, "side": "%s:block/grinder_side" % NS,
                     "top": "%s:block/grinder_side" % NS, "particle": "%s:block/grinder_side" % NS}})
    write("assets/%s/blockstates/grinder.json" % NS, {"variants": {
        "facing=%s,lit=%s" % (facing, lit): {"model": "%s:block/grinder" % NS, "y": rotation}
        for facing, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270))
        for lit in ("true", "false")}})
    block_item("grinder")
    drops_itself("grinder")
    label("block", "grinder", "Grinder", "Grinder")
    for name, source, label_fr, label_en in (
            ("grinder_casing", "grinder_casing_block", "Casing du grinder", "Grinder Casing"),
            ("grinder_frame", "grinder_frame_block", "Cadre du grinder", "Grinder Frame")):
        tex("blocks/smeltery/%s.png" % source, "block/%s.png" % name)
        cube(name, "%s:block/%s" % (NS, name))
        block_item(name)
        drops_itself(name)
        label("block", name, label_fr, label_en)
    for name in ("grinder", "grinder_casing", "grinder_frame"):
        tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, name)])
        tag("minecraft/tags/block/needs_iron_tool.json", ["%s:%s" % (NS, name)])
    label("container", "grinder", "Grinder", "Grinder")
    label("message", "grinder.incomplete",
          "Structure incomplete : un cube de 3 x 3 x 3 — 20 cadres, 5 casings, une source de lave au centre",
          "Incomplete structure: a 3 x 3 x 3 cube — 20 frames, 5 casings, a lava source in the middle")

    for kind, folder, prefix_fr, prefix_en, names in (
            ("pattern", "patern", "Pattern", "Pattern", (
                ("axe", "de hache", "Axe"), ("hammer", "de marteau", "Hammer"), ("shovel", "de pelle", "Shovel"),
                ("broadsword", "de broadsword", "Broadsword"), ("fastsword", "de fastsword", "Fastsword"),
                ("pickaxe", "de pioche", "Pickaxe"), ("sword", "d'epee", "Sword"), ("ingot", "de lingot", "Ingot"),
                ("block", "de bloc", "Block"), ("socket", "de base", "Socket"))),
            ("head", "toolpart", "Tete", "Head", (
                ("axe", "de hache", "Axe"), ("hammer", "de marteau", "Hammer"), ("shovel", "de pelle", "Shovel"),
                ("broadsword", "de broadsword", "Broadsword"), ("fastsword", "de fastsword", "Fastsword"),
                ("pickaxe", "de pioche", "Pickaxe"), ("sword", "d'epee", "Sword")))):
        for name, of_fr, label_en in names:
            item = "%s_%s" % (name, kind)
            source = "items/%s/%s.png" % (folder, name if kind == "pattern" else "head_" + name)
            tex(source, "item/%s.png" % item)
            flat_item(item, "%s:item/%s" % (NS, item))
            label("item", item, "%s %s" % (prefix_fr, of_fr), "%s %s" % (label_en, prefix_en))

    for name, source, label_fr, label_en in (
            ("smelt_modifier", "modifier/smelt", "Modificateur de fusion", "Smelt Modifier"),
            ("damage_modifier", "modifier/damage", "Modificateur de dommage", "Damage Modifier"),
            ("flame_modifier", "modifier/flame", "Modificateur de flamme", "Flame Modifier"),
            ("knockback_modifier", "modifier/knockback", "Modificateur de recul", "Knockback Modifier"),
            ("more_upgrade_modifier", "modifier/moreupgrade", "Amelioration d'emplacement de modificateur",
             "Modifier Slot Upgrade"),
            ("knockback_orb", "machine/orb_knockback", "Orbe de recul", "Knockback Orb")):
        tex("items/%s.png" % source, "item/%s.png" % name)
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, label_fr, label_en)
    for modifier, label_fr, label_en in (("smelt", "Fusion", "Smelt"), ("fortune", "Fortune", "Fortune"),
                                         ("speed", "Vitesse", "Speed"), ("damage", "Dommage", "Damage"),
                                         ("flame", "Flamme", "Flame"), ("knockback", "Recul", "Knockback"),
                                         ("more_upgrade", "Emplacement", "Slot")):
        label("modifier", modifier, label_fr, label_en)
    label("item", "modifiers.slots", "Modificateurs : %s/%s", "Modifiers: %s/%s")

    # Marteaux, broadswords, fastswords : tenus comme des outils.
    for family, folder, of_fr, label_en in (("hammer", "hammer", "Marteau", "Hammer"),
                                            ("broadsword", "broadsword", "Broadsword", "Broadsword"),
                                            ("fastsword", "fastsword", "Fastsword", "Fastsword")):
        for metal, metal_fr, metal_en in (("amethyst", "en amethyste", "Amethyst"),
                                          ("titane", "en titane", "Titanium"),
                                          ("paladium", "en paladium", "Paladium")):
            name = "%s_%s" % (metal, family)
            tex("items/%s/%s.png" % (folder, metal), "item/%s.png" % name)
            write("assets/%s/models/item/%s.json" % (NS, name),
                  {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
            write("assets/%s/items/%s.json" % (NS, name),
                  {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}})
            label("item", name, "%s %s" % (of_fr, metal_fr), "%s %s" % (metal_en, label_en))

    # Buisson a XP : quatre etapes de taille croissante, la derniere mure (animee).
    tex("blocks/bush/berry_xp_bush_fancy.png", "block/xp_bush.png")
    copy_animated("blocks/bush/berry_xp_bush_ripe_fancy.png", "block/xp_bush_ripe.png", (16, 112))
    for age, (low, high) in enumerate(((4, 12), (2, 14), (1, 15), (1, 15))):
        top = (8, 12, 15, 15)[age]
        texture = "%s:block/%s" % (NS, "xp_bush_ripe" if age == 3 else "xp_bush")
        faces = {face: {"texture": "#bush", "uv": [low, 16 - top if face not in ("up", "down") else low,
                                                   high, 16 if face not in ("up", "down") else high]}
                 for face in ("north", "south", "east", "west", "up", "down")}
        write("assets/%s/models/block/xp_bush_%d.json" % (NS, age), {
            "textures": {"bush": texture, "particle": texture},
            "elements": [{"from": [low, 0, low], "to": [high, top, high], "faces": faces}]})
    write("assets/%s/blockstates/xp_bush.json" % NS, {"variants": {
        "age=%d" % age: {"model": "%s:block/xp_bush_%d" % (NS, age)} for age in range(4)}})
    write("assets/%s/models/item/xp_bush.json" % NS, {"parent": "%s:block/xp_bush_2" % NS})
    write("assets/%s/items/xp_bush.json" % NS,
          {"model": {"type": "minecraft:model", "model": "%s:block/xp_bush_2" % NS}})
    drops_itself("xp_bush")
    label("block", "xp_bush", "Buisson a XP", "XP Bush")
    tag("minecraft/tags/block/mineable/hoe.json", ["%s:xp_bush" % NS])
    for name, source, label_fr, label_en in (("xp_berry", "xp_berry", "Baie d'XP", "XP Berry"),
                                             ("compressed_xp_berry", "compressed_xp_berry",
                                              "Baie d'XP compressee", "Compressed XP Berry")):
        copy_animated("items/fruits/%s.png" % source, "item/%s.png" % name, (16, 128))
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, label_fr, label_en)
    # PRegister_Craft.addShapelessRecipe : la boucle des recettes ne lit que les
    # grilles, celle-ci (une baie compressee -> neuf baies) est recopiee.
    shapeless("xp_berry", ["%s:compressed_xp_berry" % NS], "%s:xp_berry" % NS, 9)
    # BaseWorldGenerator : trois essais par chunk, une chance sur cinq, un filon
    # de trois dans la pierre, a la hauteur du titane (calee comme lui sur -32..40).
    write("data/%s/worldgen/configured_feature/xp_bush.json" % NS, {
        "type": "minecraft:ore",
        "config": {"size": 3, "discard_chance_on_air_exposure": 0.0, "targets": [
            {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"},
             "state": {"Name": "%s:xp_bush" % NS, "Properties": {"age": "0"}}}]}})
    write("data/%s/worldgen/placed_feature/xp_bush.json" % NS, {
        "feature": "%s:xp_bush" % NS,
        "placement": [
            {"type": "minecraft:count", "count": 3},
            {"type": "minecraft:rarity_filter", "chance": 5},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                          "min_inclusive": {"absolute": -32},
                                                          "max_inclusive": {"absolute": 40}}},
            {"type": "minecraft:biome"}]})
    write("data/%s/neoforge/biome_modifier/xp_bush.json" % NS, {
        "type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
        "features": ["%s:xp_bush" % NS], "step": "underground_ores"})

    # ---- pioche des dieux ----
    # ItemGodPickaxe : vingt textures, une tous les cinq niveaux, choisies par
    # la custom_model_data que la pioche pose en changeant de palier.
    for index in range(20):
        name = "god_pickaxe_%d" % index
        tex("items/god_pickaxe/%s.png" % name, "item/%s.png" % name)
        write("assets/%s/models/item/%s.json" % (NS, name),
              {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    write("assets/%s/items/god_pickaxe.json" % NS, {"model": {
        "type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
        "entries": [{"threshold": index, "model": {"type": "minecraft:model",
                                                   "model": "%s:item/god_pickaxe_%d" % (NS, index)}}
                    for index in range(1, 20)],
        "fallback": {"type": "minecraft:model", "model": "%s:item/god_pickaxe_0" % NS}}})
    # Le nom est code en dur dans PalaMod, sans cle de langue.
    label("item", "god_pickaxe", "Pickaxe of the gods", "Pickaxe of the Gods")
    label("item", "god_pickaxe.level", " (Niveau %s)", " (Level %s)")
    label("item", "god_pickaxe.silk", " [Silk]", " [Silk]")
    label("item", "god_pickaxe.tooltip.level", "Niveau %s", "Level %s")
    label("item", "god_pickaxe.tooltip.max", "Niveau max", "Max level")
    label("item", "god_pickaxe.tooltip.next", "Prochain niveau dans : %s exp", "Next level in: %s xp")
    label("item", "god_pickaxe.tooltip.auto_smelt", "⚫ Auto smelt (I)", "⚫ Auto smelt (I)")
    tex("items/god_pickaxe_auto_smelt.png", "item/god_pickaxe_auto_smelt.png")
    flat_item("god_pickaxe_auto_smelt", "%s:item/god_pickaxe_auto_smelt" % NS)
    label("item", "god_pickaxe_auto_smelt", "Fusion automatique de la pioche des dieux", "God Pickaxe Auto Smelt")
    label("message", "god_pickaxe.silk_on", "Silk Touch active", "Silk Touch enabled")
    label("message", "god_pickaxe.silk_off", "Silk Touch desactive", "Silk Touch disabled")
    label("message", "god_pickaxe.given", "Pioche des dieux de niveau %s donnee", "Level %s god pickaxe given")
    label("message", "god_pickaxe.not_held", "Il faut tenir une pioche des dieux en main",
          "You must hold a god pickaxe")
    label("message", "god_pickaxe.upgraded", "Amelioration ajoutee", "Upgrade added")
    # ToolHandler.minerals : les minerais ou la Fortune multiplie la fusion. Le
    # cuivre n'existait pas en 1.7.10 ; il est minerai comme les autres.
    tag("%s/tags/block/god_pickaxe_minerals.json" % NS, [
        "#minecraft:coal_ores", "#minecraft:iron_ores", "#minecraft:gold_ores", "#minecraft:copper_ores",
        "#minecraft:redstone_ores", "#minecraft:lapis_ores", "#minecraft:diamond_ores",
        "#minecraft:emerald_ores", "minecraft:nether_quartz_ore", "minecraft:nether_gold_ore",
        "%s:findium_ore" % NS, "%s:paladium_ore" % NS, "%s:titane_ore" % NS, "%s:amethyst_ore" % NS,
        "%s:trixium_ore" % NS])

    # ---- golem gardien ----
    # GuardianGolem 6.1.0 : ses textures ne sont que dans son jar (le pack
    # officiel n'a pas d'espace guardiangolem). Troisieme argument, ou le
    # dossier voisin du jar de PalaMod.
    golem_root = sys.argv[3] if len(sys.argv) > 3 else (
        os.path.join(os.path.dirname(root[1].rstrip("/")), "GuardianGolem-6.1.0") if root[1] else None)

    def golem_tex(relative, destination, size=(16, 16)):
        source = os.path.join(golem_root or "", "assets", "guardiangolem", "textures", relative)
        if not golem_root or not os.path.exists(source):
            missing.append("guardiangolem:" + relative)
            return
        if png_size(source) != size:
            wrong_size.append("guardiangolem:%s : %s au lieu de %s" % (relative, png_size(source), size))
            return
        target = os.path.join(RES, "assets", NS, "textures", destination)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copyfile(source, target)

    for name, label_fr, label_en in (("guardian_block", "Bloc gardien", "Guardian Block"),
                                     ("guardian_box_block", "Boite a golem", "Golem Box"),
                                     ("guardian_box_frame", "Cadre de boite a golem", "Golem Box Frame")):
        golem_tex("blocks/%s.png" % name, "block/%s.png" % name)
        cube(name, "%s:block/%s" % (NS, name))
        block_item(name)
        drops_itself(name)
        label("block", name, label_fr, label_en)
    golem_tex("blocks/guardian_keeper.png", "block/guardian_keeper.png")
    golem_tex("blocks/guardian_keeper_top.png", "block/guardian_keeper_top.png")
    write("assets/%s/blockstates/guardian_keeper.json" % NS,
          {"variants": {"": {"model": "%s:block/guardian_keeper" % NS}}})
    write("assets/%s/models/block/guardian_keeper.json" % NS, {
        "parent": "minecraft:block/cube_column",
        "textures": {"side": "%s:block/guardian_keeper" % NS, "end": "%s:block/guardian_keeper_top" % NS}})
    block_item("guardian_keeper")
    drops_itself("guardian_keeper")
    label("block", "guardian_keeper", "Gardien de golem", "Guardian Keeper")

    # FoodMachineModel : un cube et un couvercle de 10 x 4 x 10, sur une texture
    # de 64 x 46 lue en boites de modele. La texture est allongee a 64 x 64 (voir
    # pad_png_height : 46 pixels coupaient les mipmaps de tout l'atlas des blocs),
    # d'ou uv / 4 dans les deux sens.
    golem_tex("models/food_machine.png", "block/food_machine.png", (64, 46))
    food_machine = os.path.join(RES, "assets", NS, "textures", "block", "food_machine.png")
    if os.path.exists(food_machine):
        pad_png_height(food_machine, 64)

    def box_uv(u, v, w, h, d):
        def rect(x0, y0, x1, y1):
            return [round(x0 / 4.0, 3), round(y0 / 4.0, 3), round(x1 / 4.0, 3), round(y1 / 4.0, 3)]
        return {
            "up": rect(u + d, v, u + d + w, v + d),
            "down": rect(u + d + w, v, u + d + 2 * w, v + d),
            "west": rect(u + d + w, v + d, u + 2 * d + w, v + d + h),
            "east": rect(u, v + d, u + d, v + d + h),
            "north": rect(u + d, v + d, u + d + w, v + d + h),
            "south": rect(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
        }

    def faces(uvs):
        return {face: {"texture": "#machine", "uv": uv} for face, uv in uvs.items()}

    write("assets/%s/models/block/food_machine.json" % NS, {
        "textures": {"machine": "%s:block/food_machine" % NS, "particle": "%s:block/food_machine" % NS},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces(box_uv(0, 0, 16, 16, 16))},
            {"from": [3, 16, 3], "to": [13, 20, 13], "faces": faces(box_uv(0, 32, 10, 4, 10))},
        ]})
    write("assets/%s/blockstates/food_machine.json" % NS,
          {"variants": {"": {"model": "%s:block/food_machine" % NS}}})
    block_item("food_machine")
    drops_itself("food_machine")
    label("block", "food_machine", "Machine a croquettes", "Food Machine")
    label("container", "food_machine", "Machine a croquettes", "Food Machine")
    label("container", "golem_box", "Boite a golem", "Golem Box")
    label("container", "guardian_keeper", "Gardien de golem", "Guardian Keeper")
    for name in ("guardian_block", "guardian_keeper", "guardian_box_block", "guardian_box_frame", "food_machine"):
        tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, name)])

    for name, source, label_fr, label_en, handheld in (
            ("guardian_stone", "guardian_stone", "Pierre de gardien", "Guardian Stone", False),
            ("mini_golem", "mini_golem", "Mini golem", "Mini Golem", False),
            ("guardian_hammer", "guardianhammer", "Marteau du gardien", "Guardian Hammer", True),
            ("whitelist", "whitelist", "Liste blanche du golem", "Guardian Whitelist", False),
            ("wrench", "wrench", "Cle", "Wrench", True),
            ("croquette", "food", "Croquette", "Croquette", False)):
        golem_tex("items/%s.png" % source, "item/%s.png" % name)
        write("assets/%s/models/item/%s.json" % (NS, name), {
            "parent": "minecraft:item/%s" % ("handheld" if handheld else "generated"),
            "textures": {"layer0": "%s:item/%s" % (NS, name)}})
        write("assets/%s/items/%s.json" % (NS, name),
              {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}})
        label("item", name, label_fr, label_en)
    tex("items/paladium_apple.png", "item/paladium_apple.png")
    flat_item("paladium_apple", "%s:item/paladium_apple" % NS)
    label("item", "paladium_apple", "Pomme en paladium", "Paladium Apple")

    # Le golem : la texture du golem de fer de 1.7.10, 128 x 128.
    golem_tex("entity/golem.png", "entity/guardian_golem.png", (128, 128))
    label("entity", "guardian_golem", "Golem gardien", "Guardian Golem")
    label("entity", "guardian_golem.level", " (niveau %s)", " (level %s)")
    label("entity", "guardian_golem.loading", " (%ss)", " (%ss)")
    for key, label_fr, label_en in (
            ("guardian_stone.empty", "Vide : eveille un golem neuf", "Empty: awakens a new golem"),
            ("guardian_stone.level", "Golem de niveau %s", "Level %s golem"),
            ("guardian_stone.owner", "Proprietaire : %s", "Owner: %s"),
            ("guardian_stone.points", "%s points libres, %s ameliorations", "%s free points, %s upgrades"),
            ("guardian_stone.death", "Mort (%s) le %s en %s", "Died (%s) on %s at %s"),
            ("mini_golem.durability", "Productions : %s / %s", "Productions: %s / %s"),
            ("mini_golem.no_inbox", "Sans « Golem en boite » : refuse par la boite",
             "Without 'Golem in a box': refused by the box"),
            ("whitelist.players", "Joueurs autorises :", "Allowed players:"),
            ("whitelist.none", "aucun", "none")):
        label("item", key, label_fr, label_en)
    for key, label_fr, label_en in (
            ("golem.structure", "Il faut un bloc de paladium dessous et un de chaque cote du bloc gardien",
             "The guardian block needs a paladium block below it and one on each side"),
            ("golem.not_yours", "Ce n'est pas votre golem !", "This is not your golem!"),
            ("golem.max_level", "Ce golem est deja au niveau maximum", "This golem is already at max level"),
            ("golem.whitelist", "Liste blanche retenue : %s joueurs", "Whitelist stored: %s players"),
            ("golem.kept", "Votre golem est mort : sa pierre vous attend dans votre gardien",
             "Your golem died: its stone is waiting in your keeper"),
            ("golem.lost", "Votre golem est mort, et votre gardien est plein : il est perdu",
             "Your golem died and your keeper is full: it is lost"),
            ("golem.spawned", "Golem de niveau %s eveille", "Level %s golem awakened"),
            ("golem_box.incomplete",
             "Structure incomplete : un cube de 3 x 3 x 3, 25 cadres, de l'eau angelique au centre",
             "Incomplete structure: a 3 x 3 x 3 cube, 25 frames, angelic water in the middle"),
            ("golem_box.no_golem", "Aucun mini golem en boite", "No mini golem in the box"),
            ("golem_box.metal", "Production : %s", "Production: %s"),
            ("golem_box.metal.iron", "fer", "iron"),
            ("golem_box.metal.amethyst", "amethyste", "amethyst"),
            ("golem_box.metal.titane", "titane", "titanium"),
            ("golem_box.metal.paladium", "paladium", "paladium"),
            ("whitelist.not_held", "Il faut tenir une liste blanche du golem", "You must hold a guardian whitelist"),
            ("whitelist.invalid", "Nom invalide (16 caracteres au plus)", "Invalid name (16 characters at most)"),
            ("whitelist.present", "%s est deja dans la liste", "%s is already listed"),
            ("whitelist.absent", "%s n'est pas dans la liste", "%s is not listed"),
            ("whitelist.full", "La liste est pleine (%s noms)", "The list is full (%s names)"),
            ("whitelist.added", "%s ajoute", "%s added"),
            ("whitelist.removed", "%s retire", "%s removed")):
        label("message", key, label_fr, label_en)

    # CraftInit : les recettes de GuardianGolem, hors du releve de PalaMod.
    shaped("guardian_stone", [" X ", "XYX", " X "],
           {"X": "minecraft:stone", "Y": "%s:compressed_paladium" % NS}, "%s:guardian_stone" % NS)
    shaped("guardian_keeper", ["YYY", "ZXZ", "YYY"],
           {"Y": "%s:paladium_ingot" % NS, "Z": "%s:compressed_paladium" % NS, "X": "%s:guardian_stone" % NS},
           "%s:guardian_keeper" % NS)
    shaped("guardian_block", ["YYY", "YXY", "YYY"],
           {"X": "%s:guardian_stone" % NS, "Y": "%s:titane_ingot" % NS}, "%s:guardian_block" % NS)
    # La rangee du haut, vide en 1.7.10, disparait en 26.2.
    shaped("whitelist", ["PPP", "B B"], {"P": "minecraft:paper", "B": "minecraft:book"}, "%s:whitelist" % NS)
    shaped("guardian_hammer", ["TPT", " S ", " S "],
           {"T": "%s:titane_ingot" % NS, "P": "%s:paladium_ingot" % NS, "S": "minecraft:stick"},
           "%s:guardian_hammer" % NS)
    shaped("food_machine", ["XYX", "ZCZ", "ONO"], {
        "X": "%s:paladium_apple" % NS, "Y": "%s:findium" % NS, "Z": "minecraft:water_bucket",
        "C": "%s:compressed_paladium" % NS, "O": "minecraft:obsidian", "N": "minecraft:nether_star"},
           "%s:food_machine" % NS)
    shaped("guardian_box_frame", ["TTT", "TPA", "AAA"], {
        "T": "%s:titane_ingot" % NS, "P": "%s:paladium_ingot" % NS, "A": "%s:amethyst_ingot" % NS},
           "%s:guardian_box_frame" % NS)
    shaped("guardian_box_block", ["PPP", "PFP", "PPP"],
           {"P": "%s:paladium_ingot" % NS, "F": "%s:findium" % NS}, "%s:guardian_box_block" % NS)
    shaped("wrench", ["T T", "PSP", " S "], {
        "T": "%s:titane_block" % NS, "P": "%s:paladium_block" % NS, "S": "%s:paladium_stick" % NS},
           "%s:wrench" % NS)
    # MiniGolemRecipe et MiniGolemRepair : des recettes qui recopient le golem.
    write("data/%s/recipe/mini_golem.json" % NS, {"type": "%s:mini_golem" % NS})
    write("data/%s/recipe/mini_golem_repair.json" % NS, {"type": "%s:mini_golem_repair" % NS})

    # ---- montures (chapitre 14) ----
    # Mount 6.1.0 : ses textures et ses sons ne sont que dans son jar (le pack
    # officiel n'a pas d'espace mount). Quatrieme argument, ou le dossier voisin
    # du jar de PalaMod.
    mount_root = sys.argv[4] if len(sys.argv) > 4 else (
        os.path.join(os.path.dirname(root[1].rstrip("/")), "Mount-6.1.0") if root[1] else None)

    def mount_file(relative, destination, size=None):
        source = os.path.join(mount_root or "", "assets", "mount", relative)
        if not mount_root or not os.path.exists(source):
            missing.append("mount:" + relative)
            return
        if size is not None and png_size(source) != size:
            wrong_size.append("mount:%s : %s au lieu de %s" % (relative, png_size(source), size))
            return
        target = os.path.join(RES, "assets", NS, destination)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copyfile(source, target)

    # Le Dancarok : une texture par corne. La variante en paladium vert du jar
    # n'est referencee nulle part dans Mount ; elle n'est pas copiee.
    for source, name in (("dancarok", "dancarok"), ("dancarok_amethyst_helm", "dancarok_amethyst_horn"),
                         ("dancarok_titane_helm", "dancarok_titane_horn"),
                         ("dancarok_paladium_helm", "dancarok_paladium_horn")):
        mount_file("textures/mount/%s.png" % source, "textures/entity/%s.png" % name, (256, 256))
    label("entity", "dancarok", "Dancarok", "Dancarok")

    # Objets.
    for name, label_fr, label_en in (("bandage", "Bandage", "Bandage"),
                                     ("amethyst_horn", "Corne en amethyste", "Amethyst Horn"),
                                     ("titane_horn", "Corne en titane", "Titanium Horn"),
                                     ("paladium_horn", "Corne en paladium", "Paladium Horn")):
        mount_file("textures/items/%s.png" % name, "textures/item/%s.png" % name, (16, 16))
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, label_fr, label_en)
    mount_file("textures/items/mount_gemstone.png", "textures/item/mount_gemstone.png", (16, 16))
    mount_file("textures/items/mount_gemstone_tamed.png", "textures/item/mount_gemstone_tamed.png", (16, 16))
    for model in ("mount_gemstone", "mount_gemstone_tamed"):
        write("assets/%s/models/item/%s.json" % (NS, model),
              {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, model)}})
    # Grise sauvage, doree apprivoisee : le premier drapeau de custom_model_data.
    write("assets/%s/items/mount_gemstone.json" % NS, {"model": {
        "type": "minecraft:condition", "property": "minecraft:custom_model_data", "index": 0,
        "on_true": {"type": "minecraft:model", "model": "%s:item/mount_gemstone_tamed" % NS},
        "on_false": {"type": "minecraft:model", "model": "%s:item/mount_gemstone" % NS},
    }})
    label("item", "mount_gemstone", "Gemme de monture", "Mount Gem")
    tex("items/machine/orb_heal.png", "item/heal_orb.png")
    flat_item("heal_orb", "%s:item/heal_orb" % NS)
    label("item", "heal_orb", "Orbe de regeneration", "Regeneration Orb")
    for key, label_fr, label_en in (
            ("mount_gemstone.of", "Gemme de %s", "%s Gem"),
            ("mount_gemstone.wild", " sauvage", " (wild)"),
            ("mount_gemstone.tame_hint", "Mettez cette gemme dans un enclos pour l'apprivoiser.",
             "Put this gem in a barn to tame your mount."),
            ("mount_gemstone.name", "Nom : %s", "Name: %s"),
            ("mount_gemstone.level", "Niv. %s (%s xp / %s)", "Lvl %s (%s xp / %s)"),
            ("mount_gemstone.health", "Vie : %s", "Health: %s"),
            ("mount_gemstone.care", "En soin pendant %s", "In care for %s"),
            ("mount_gemstone.expired", "Cette gemme est perimee", "This gem has expired"),
            ("mount_gemstone.expires", "Perime dans %s", "Expires in %s"),
            ("horn.uses", "Utilisations %s/%s", "Uses %s/%s")):
        label("item", key, label_fr, label_en)

    # Blocs de l'enclos : bois, a la pioche.
    mount_file("textures/blocks/barn_foundation.png", "textures/block/barn_foundation.png", (16, 16))
    cube("barn_foundation", "%s:block/barn_foundation" % NS)
    mount_file("textures/blocks/incubator_side.png", "textures/block/barn_incubator_side.png", (16, 16))
    mount_file("textures/blocks/incubator_top.png", "textures/block/barn_incubator_top.png", (16, 16))
    write("assets/%s/blockstates/barn_incubator.json" % NS,
          {"variants": {"": {"model": "%s:block/barn_incubator" % NS}}})
    write("assets/%s/models/block/barn_incubator.json" % NS, {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {"top": "%s:block/barn_incubator_top" % NS, "bottom": "%s:block/barn_incubator_side" % NS,
                     "side": "%s:block/barn_incubator_side" % NS}})
    # La barriere : les modeles de barriere vanilla, sur sa texture.
    mount_file("textures/blocks/barn_fence.png", "textures/block/barn_fence.png", (16, 16))
    for part in ("post", "side", "inventory"):
        write("assets/%s/models/block/barn_fence_%s.json" % (NS, part),
              {"parent": "minecraft:block/fence_%s" % part, "textures": {"texture": "%s:block/barn_fence" % NS}})
    side = "%s:block/barn_fence_side" % NS
    write("assets/%s/blockstates/barn_fence.json" % NS, {"multipart": [
        {"apply": {"model": "%s:block/barn_fence_post" % NS}},
        {"when": {"north": "true"}, "apply": {"model": side, "uvlock": True}},
        {"when": {"east": "true"}, "apply": {"model": side, "y": 90, "uvlock": True}},
        {"when": {"south": "true"}, "apply": {"model": side, "y": 180, "uvlock": True}},
        {"when": {"west": "true"}, "apply": {"model": side, "y": 270, "uvlock": True}},
    ]})
    write("assets/%s/items/barn_fence.json" % NS,
          {"model": {"type": "minecraft:model", "model": "%s:block/barn_fence_inventory" % NS}})
    tag("minecraft/tags/block/fences.json", ["%s:barn_fence" % NS])

    # Le gestionnaire : le BarnControllerModel (ModelBase 64 x 64) en elements.
    # Toutes ses boites ont l'origine d'UV (0, 0) dans le code : on garde ce
    # placement, le socle prend la caisse et les petites pieces le metal du
    # coin. Le rendu de 1.7.10 tournait le modele de 180 degres autour de Z :
    # x et y s'inversent, d'ou un angle de -45 pour le groupe incline.
    mount_file("textures/blocks/barncontroller.png", "textures/block/barn_controller.png", (64, 64))

    def box_faces(w, h, d, flat):
        quarter = lambda values: [v / 4.0 for v in values]
        faces = {"up": quarter([d, 0, d + w, d]), "down": quarter([d + w, 0, d + 2 * w, d])}
        if not flat:
            faces.update({"east": quarter([0, d, d, d + h]), "west": quarter([d + w, d, 2 * d + w, d + h]),
                          "north": quarter([d, d, d + w, d + h]),
                          "south": quarter([2 * d + w, d, 2 * d + 2 * w, d + h])})
        return {face: {"uv": uv, "texture": "#all"} for face, uv in faces.items()}

    def element(frm, to, size, rotation=None):
        w, h, d = size
        payload = {"from": frm, "to": to, "faces": box_faces(w, h, d, h == 0)}
        if rotation:
            payload["rotation"] = rotation
        return payload

    tilt = {"origin": [11.5, 13.5, 12.5], "axis": "x", "angle": -45}
    write("assets/%s/models/block/barn_controller.json" % NS, {
        "parent": "minecraft:block/block",
        "texture_size": [64, 64],
        "textures": {"all": "%s:block/barn_controller" % NS, "particle": "%s:block/barn_controller" % NS},
        "elements": [
            element([0, 0, 0], [16, 8, 16], (16, 8, 16)),
            element([11, 8.5, 12], [12, 13.5, 13], (1, 5, 1)),
            element([10.25, 8.5, 10.75], [13.25, 8.5, 13.75], (3, 0, 3)),
            element([2, 7.75, 5], [7, 8.75, 12], (5, 1, 7)),
            element([11, 13.5, 12], [12, 15.5, 13], (1, 2, 1), tilt),
            element([8, 13.5, 9], [15, 13.5, 16], (7, 0, 7), tilt),
        ]})
    write("assets/%s/blockstates/barn_controller.json" % NS,
          {"variants": {"": {"model": "%s:block/barn_controller" % NS}}})

    for name, label_fr, label_en in (("barn_controller", "Gestionnaire d'enclos", "Barn Controller"),
                                     ("barn_incubator", "Couveuse d'enclos", "Barn Incubator"),
                                     ("barn_foundation", "Fondation d'enclos", "Barn Foundation"),
                                     ("barn_fence", "Barriere d'enclos", "Barn Fence")):
        if name != "barn_fence":
            block_item(name)
        drops_itself(name)
        label("block", name, label_fr, label_en)
        tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, name)])

    # Sons : assets/mount/sounds.json, renommes sous palamod.
    sounds = {}
    for event, source in (("dancarok.walk", "dancarok/walk"), ("dancarok.idle", "dancarok/idle"),
                          ("dancarok.jump", "dancarok/jump"), ("dancarok.destroy", "dancarok/destroy"),
                          ("mount.summon", "summon"), ("mount.unsummon", "unsummon")):
        target = "mount/" + source.split("/")[-1] if "/" not in source else source
        mount_file("sounds/%s.ogg" % source, "sounds/%s.ogg" % target)
        sounds[event] = {"sounds": ["%s:%s" % (NS, target)]}
    write("assets/%s/sounds.json" % NS, sounds)

    fr["key.%s.mount" % NS] = "Monture GUI"
    en["key.%s.mount" % NS] = "Open Mount Menu"
    for key, label_fr, label_en in (
            ("tame_in_barn", "Vous devez mettre cette gemme dans une etable pour apprivoiser votre monture.",
             "You must put this gem in a barn to tame your mount."),
            ("captured", "Dancarok capture : mettez sa gemme dans un enclos pour l'apprivoiser",
             "Dancarok captured: put its gem in a barn to tame it"),
            ("no_energy", "La monture n'a plus d'energie", "Your mount is out of energy"),
            ("already_used", "Cette monture est deja utilisee.", "This mount is already in use."),
            ("not_yours", "Cette monture ne vous appartient pas.", "This mount doesn't belong to you."),
            ("none_equipped", "Aucune monture equipee", "No mount equipped"),
            ("level_required", "Votre monture doit etre niveau %s pour etre invoquee",
             "Your mount must be level %s to be summoned"),
            ("dead", "Votre monture n'a plus de vie", "Your mount has no health left"),
            ("hungry", "Vous devez nourrir votre monture", "You must feed your mount"),
            ("must_ride", "Vous devez etre sur votre monture pour cela", "You must be riding your mount"),
            ("summoning", "Invocation : restez immobile trois secondes", "Summoning: stand still for three seconds"),
            ("unsummoning", "Desinvocation : restez immobile trois secondes",
             "Unsummoning: stand still for three seconds"),
            ("countdown_cancelled", "Invocation annulee", "Summoning cancelled"),
            ("summoned", "Monture invoquee", "Mount summoned"),
            ("unsummoned", "Monture desinvoquee", "Mount unsummoned"),
            ("level_up", "Votre monture est niveau %s", "Your mount is now level %s"),
            ("not_hungry", "Votre monture n'a plus faim", "Your mount isn't hungry"),
            ("no_croquette", "Il vous faut des croquettes", "You need croquettes"),
            ("fed", "%s croquette(s) donnee(s) : energie %s/%s", "%s croquette(s) given: energy %s/%s"),
            ("not_summoned", "Aucune monture invoquee", "No mount summoned"),
            ("not_nearby", "Votre monture n'est pas a proximite de vous.", "Your mount isn't nearby."),
            ("name_length", "Le nom doit etre entre 4 et 16 caracteres", "The name must be 4 to 16 characters long"),
            ("renamed", "Monture renommee en %s", "Mount renamed to %s"),
            ("shared_xp_range", "L'XP doit etre entre 0 et 100 %%", "XP must be between 0 and 100%%"),
            ("shared_xp", "XP partagee a %s %%", "Shared XP set to %s%%"),
            ("given", "Vous avez recu une monture de niveau %s", "You received a level %s mount"),
            ("no_horn", "Votre monture ne possede pas de corne", "Your mount has no horn"),
            ("not_enough_energy", "Votre monture n'a pas assez d'energie (%s par coup)",
             "Your mount doesn't have enough energy (%s per strike)"),
            ("horn_broken", "Les cornes de votre monture ont casse", "Your mount's horns broke"),
            ("horn_worn", "Les cornes de votre monture n'ont plus beaucoup de durabilite",
             "Your mount's horns are almost worn out"),
            ("structure_invalid", "La structure de l'etable n'est pas valide", "The barn structure isn't valid"),
            ("structure_hay", "Votre structure doit etre composee d'un carre central de foin entre 3x3 et 7x7.",
             "Your structure needs a central square of hay between 3x3 and 7x7."),
            ("structure_fences", "Vous devez placer des barrieres sur vos fondations.",
             "You must place fences on your foundations."),
            ("structure_foundations", "Votre structure doit etre entouree de fondations. %s/%s",
             "Your structure must be surrounded by foundations. %s/%s"),
            ("structure_controllers", "Vous ne pouvez pas avoir plus de un centre de controle de monture",
             "You can't have more than one mount controller"),
            ("only_gems", "Vous ne pouvez placer que des gemmes", "You can only deposit gems"),
            ("barn_full", "Nombre de monture maximum atteint", "Maximum number of mounts reached"),
            ("tamed", "Monture apprivoisee", "Mount tamed"),
            ("in_care", "Cette monture est en soin", "This mount is in care"),
            ("already_equipped", "Une monture est deja equipee", "A mount is already equipped"),
            ("expired", "La gemme de cette monture est perimee", "This mount's gem has expired"),
            ("equipped", "Monture equipee", "Mount equipped"),
            ("unequipped", "Monture desequipee", "Mount unequipped"),
            ("bandage_count", "Nombre de bandage invalide", "Invalid number of bandages"),
            ("need_incubator", "Vous devez placer une couveuse dans votre etable",
             "You must place an incubator in your barn"),
            ("care_full", "Deux montures sont deja en soin", "Two mounts are already in care"),
            ("already_in_care", "Cette monture est deja en soin", "This mount is already in care"),
            ("not_enough_bandages", "Vous n'avez pas assez de bandage (%s)", "You don't have enough bandages (%s)"),
            ("in_care_now", "Monture placee en soin", "Mount placed in care"),
            ("bandage_min", "Vous ne pouvez pas mettre moins d'un bandage", "You can't use less than one bandage"),
            ("bandage_owned", "Vous n'avez que %s bandages sur vous", "You only have %s bandages on you"),
            ("fully_healed", "Votre monture ne peut etre soignee d'avantage", "Your mount can't be healed any further")):
        label("message", "mount." + key, label_fr, label_en)

    # Cornes : elles se fabriquent et se reparent a la palamachine
    # (PalamachineRecipes, chapitre 16), la defense d'elephant remplacee par un
    # bloc d'os. Les anciens substituts a l'etabli sont retires.

    # Apparition : aucune dans les jars de Paladium (les sauvages etaient poses
    # a la main sur le serveur Farmland). Rare, dans les plaines et savanes.
    write("data/%s/neoforge/biome_modifier/dancarok.json" % NS, {
        "type": "neoforge:add_spawns",
        "biomes": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:savanna",
                   "minecraft:savanna_plateau"],
        "spawners": {"type": "%s:dancarok" % NS, "weight": 2, "minCount": 1, "maxCount": 1}})

    # ---- dimension du Mineur (chapitre 15) ----
    #
    # modules/miner/dimminer. Textures : blocks/miner du pack (obsidienne,
    # minerai), le jar pour ce qu'il est seul a avoir — la pierre d'invocation
    # (items/invockerstone) et la nappe animee du portail.
    #
    # L'obsidienne wither : onze textures, une par palier de paladium donne
    # (icons[feed] ; les paliers 11 a 13 du jar ne sont jamais atteints).
    for feed in range(11):
        tex("blocks/miner/withered_obsidian_%d.png" % feed, "block/withered_obsidian_%d.png" % feed)
        write("assets/%s/models/block/withered_obsidian_%d.json" % (NS, feed), {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": "%s:block/withered_obsidian_%d" % (NS, feed)}})
    write("assets/%s/blockstates/withered_obsidian.json" % NS, {"variants": {
        "feed=%d" % feed: {"model": "%s:block/withered_obsidian_%d" % (NS, feed)} for feed in range(11)}})
    write("assets/%s/models/item/withered_obsidian.json" % NS,
          {"parent": "%s:block/withered_obsidian_0" % NS})
    write("assets/%s/items/withered_obsidian.json" % NS,
          {"model": {"type": "minecraft:model", "model": "%s:block/withered_obsidian_0" % NS}})
    # getDrops : toujours le bloc ; en plus, tant que le portail n'a jamais
    # ete allume (created), les blocs de paladium donnes.
    write("data/%s/loot_table/blocks/withered_obsidian.json" % NS, {
        "type": "minecraft:block",
        "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s:withered_obsidian" % NS}],
             "conditions": [{"condition": "minecraft:survives_explosion"}]},
            {"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [{
                "type": "minecraft:item", "name": "%s:paladium_block" % NS,
                "conditions": [{"condition": "minecraft:block_state_property",
                                "block": "%s:withered_obsidian" % NS,
                                "properties": {"feed": str(feed), "created": "false"}}],
                "functions": [{"function": "minecraft:set_count", "count": feed}]}
                for feed in range(10, 0, -1)]}],
             "conditions": [{"condition": "minecraft:survives_explosion"}]},
        ]})
    label("block", "withered_obsidian", "Obsidienne wither", "Withered Obsidian")
    tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:withered_obsidian" % NS])

    # Le portail : la nappe de 32 images de 48 x 48 et les deux modeles du
    # portail du Nether (meme epaisseur, 0.25). Ni objet ni butin.
    copy_animated("blocks/miner_portal.png", "block/miner_portal.png", (48, 1536))
    for axis, parent in (("x", "nether_portal_ns"), ("z", "nether_portal_ew")):
        write("assets/%s/models/block/miner_portal_%s.json" % (NS, parent.rsplit("_", 1)[1]), {
            "parent": "minecraft:block/%s" % parent,
            "textures": {"portal": "%s:block/miner_portal" % NS, "particle": "%s:block/miner_portal" % NS}})
    write("assets/%s/blockstates/miner_portal.json" % NS, {"variants": {
        "axis=x": {"model": "%s:block/miner_portal_ns" % NS},
        "axis=z": {"model": "%s:block/miner_portal_ew" % NS}}})
    write("data/%s/loot_table/blocks/miner_portal.json" % NS, {"type": "minecraft:block", "pools": []})
    label("block", "miner_portal", "Portail du Mineur", "Miner Portal")

    # Le minerai de Pierre d'Invocation (BlockMiner, durete 5, se rend
    # lui-meme) et sa pierre, au four (PWorld.addRecipesForFurnace, 1 XP).
    miner_block("invoker_ore", {"all": "blocks/miner/invoker_ore.png"},
                "Minerai de Pierre d'Invocation", "Invoker Stone Ore")
    tex("items/invockerstone.png", "item/invoker_stone.png")
    flat_item("invoker_stone", "%s:item/invoker_stone" % NS)
    label("item", "invoker_stone", "Pierre d'Invocation", "Invoker Stone")
    write("data/%s/recipe/invoker_stone.json" % NS, {
        "type": "minecraft:smelting", "experience": 1.0,
        "ingredient": "%s:invoker_ore" % NS, "result": {"id": "%s:invoker_stone" % NS}})

    # Les messages du chat. Les textes viennent du constant pool, prefixe
    # « [Paladium] » retire ; « 17 » devient le niveau reellement demande.
    for key, label_fr, label_en in (
            ("level", "Il vous faut etre niveau %s dans le metier de mineur pour acceder a cette dimension.",
             "You need to be level %s in the Miner job to enter this dimension."),
            ("not_owner", "Vous n'etes pas le proprietaire de ce portail.", "You aren't the owner of this portal."),
            ("no_portal", "Vous ne semblez pas etre dans un portail.", "You don't seem to be in a portal."),
            ("unavailable", "La dimension mineur n'est pas disponible sur ce serveur.",
             "The miner dimension isn't available on this server."),
            ("creating", "Creation de votre dimension mineur en cours", "Creating your miner dimension"),
            ("teleporting", "Teleportation dans votre dimension mineur en cours",
             "Teleporting you to your miner dimension"),
            ("title", "Dimension Mineur", "Miner Dimension"),
            ("subtitle", "Bienvenue dans la dimension du metier de mineur !", "Welcome to the Miner job's dimension!"),
            ("collapsing", "Votre dimension mineur va s'effondrer dans 30 secondes.",
             "Your miner dimension will collapse in 30 seconds."),
            ("collapsed", "Votre dimension s'est effondree !", "Your dimension has collapsed!"),
            ("border", "Vous avez atteint la limite de la dimension.", "You've reached the edge of the dimension."),
            ("bar", "Points restants : %s - Temps restant : %s", "Points left: %s - Time left: %s"),
            ("saved", "Vous avez failli mourir : vous voila de retour a votre point d'arrivee.",
             "You nearly died: you're back at your arrival point."),
            ("closing", "La dimension est entrain de se fermer.", "The dimension is closing."),
            ("no_container", "Vous ne pouvez pas poser de bloc contenant des items dans cette dimension.",
             "You can't place blocks that hold items in this dimension."),
            ("unbreakable", "Vous ne pouvez pas casser ce bloc dans cette dimension.",
             "You can't break this block in this dimension."),
            ("not_enough_points", "Vous n'avez pas assez de points pour casser ce bloc.",
             "You don't have enough points to break this block."),
            ("not_inside", "Vous n'etes pas dans la dimension mineur.", "You aren't in the miner dimension.")):
        label("message", "dimminer." + key, label_fr, label_en)

    # ---- pour le PvP (chapitre 16) ----
    #
    # Les sticks, les anneaux, l'aile et le deltaplane ; la palamachine qui
    # fabrique et repare les sticks et les anneaux ; les ingredients qui
    # manquaient (orbes de force et de saut, slimepad, spike en paladium).
    # Textures : le pack, le jar pour la palamachine, que le pack n'a pas.
    for name, texture, label_fr, label_en in (
            ("heal_stick", "HealStick", "Stick de regeneration", "Heal Stick"),
            ("speed_stick", "SpeedStick", "Stick de vitesse", "Speed Stick"),
            ("strength_stick", "StrenghtStick", "Stick de force", "Strength Stick"),
            ("jump_stick", "JumpStick", "Stick de saut", "Jump Stick"),
            ("god_stick", "StickOfGod", "Baton divin", "Stick of God"),
            ("damage_stick", "DamageStick", "Stick de dommage", "Damage Stick"),
            ("hyper_jump_stick", "HyperJumpStick", "Stick de super saut", "Hyper Jump Stick")):
        # isFull3D : le stick se tient comme un outil.
        tex("items/%s.png" % texture, "item/%s.png" % name)
        write("assets/%s/models/item/%s.json" % (NS, name),
              {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
        write("assets/%s/items/%s.json" % (NS, name),
              {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}})
        label("item", name, label_fr, label_en)
    for name, texture, label_fr, label_en in (
            ("strength_orb", "machine/orb_strenght", "Orbe de force", "Strength Orb"),
            ("jump_orb", "machine/orb_jump", "Orbe de saut", "Jump Orb"),
            ("small_ring", "small_ring", "Small Ring", "Small Ring"),
            ("medium_ring", "medium_ring", "Medium Ring", "Medium Ring"),
            ("big_ring", "medium_big", "Big Ring", "Big Ring"),
            ("endium_small_ring", "ring/endium_small", "Small Ring en endium", "Endium Small Ring"),
            ("endium_medium_ring", "ring/endium_medium", "Medium Ring en endium", "Endium Medium Ring"),
            ("endium_big_ring", "ring/endium_big", "Big Ring en endium", "Endium Big Ring"),
            ("wing", "wing", "Aile", "Wing"),
            ("hang_glider", "hangglider", "Hang Glider", "Hang Glider")):
        tex("items/%s.png" % texture, "item/%s.png" % name)
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, label_fr, label_en)
    for key, label_fr, label_en in (
            ("stick.repairs", "Reparation : %s / %s", "Repairs: %s / %s"),
            ("hang_glider.open", "Deploye - clic droit pour replier", "Deployed - right-click to fold"),
            ("hang_glider.closed", "Replie - clic droit pour deployer", "Folded - right-click to deploy"),
            ("ring_repair", "Reparation par anneau : %s / %s", "Ring repairs left: %s / %s")):
        label("tooltip", key, label_fr, label_en)
    label("message", "stick.cooldown", "Tu dois attendre que le baton se recharge : %s secondes !",
          "You must wait for the stick to recharge: %s seconds!")

    # La palamachine : cotes, dessus a part, dessous comme les cotes (getIcon).
    tex("blocks/machines/paladium_machine_block_side.png", "block/palamachine_side.png")
    tex("blocks/machines/paladium_machine_block_top.png", "block/palamachine_top.png")
    write("assets/%s/blockstates/palamachine.json" % NS,
          {"variants": {"": {"model": "%s:block/palamachine" % NS}}})
    write("assets/%s/models/block/palamachine.json" % NS, {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {"side": "%s:block/palamachine_side" % NS, "bottom": "%s:block/palamachine_side" % NS,
                     "top": "%s:block/palamachine_top" % NS}})
    block_item("palamachine")
    label("block", "palamachine", "Palamachine", "Palamachine")

    # Le slimepad : un coeur opaque dans une gaine translucide, comme le bloc de
    # slime vanilla, aux dimensions de setBlockBounds. Symetrique : les huit
    # directions partagent le modele.
    tex("blocks/slime/slime_green.png", "block/slimepad.png")
    tex("blocks/slime/slime_transparent_green.png", "block/slimepad_outer.png")

    def box(frm, to, texture):
        x0, y0, z0 = frm
        x1, y1, z1 = to
        horizontal = [x0, 16 - y1, x1, 16 - y0]
        depth = [z0, 16 - y1, z1, 16 - y0]
        top = [x0, z0, x1, z1]
        return {"from": frm, "to": to, "faces": {
            "down": {"uv": top, "texture": texture}, "up": {"uv": top, "texture": texture},
            "north": {"uv": horizontal, "texture": texture}, "south": {"uv": horizontal, "texture": texture},
            "west": {"uv": depth, "texture": texture}, "east": {"uv": depth, "texture": texture}}}

    write("assets/%s/models/block/slimepad.json" % NS, {
        "parent": "minecraft:block/block",
        "textures": {"particle": "%s:block/slimepad" % NS, "core": "%s:block/slimepad" % NS,
                     "outer": "%s:block/slimepad_outer" % NS},
        "elements": [box([4, 2, 4], [12, 9, 12], "#core"), box([2, 1, 2], [14, 10, 14], "#outer")]})
    write("assets/%s/blockstates/slimepad.json" % NS, {"variants": {
        "direction=%d" % direction: {"model": "%s:block/slimepad" % NS} for direction in range(8)}})
    block_item("slimepad")
    label("block", "slimepad", "Slimepad", "Slimepad")

    # Le spike en paladium : une pyramide de quatre degres qui pointe hors de
    # la face qui le porte. Dessus des degres a la texture en carres
    # concentriques (SpikePaladium_1), cotes a la texture en strates (_2).
    tex("blocks/spike/SpikePaladium_1.png", "block/paladium_spike_top.png")
    tex("blocks/spike/SpikePaladium_2.png", "block/paladium_spike_side.png")
    steps = []
    for step in range(4):
        low, high = step * 2, 16 - step * 2
        element = box([low, step * 4, low], [high, step * 4 + 4, high], "#side")
        element["faces"]["up"]["texture"] = "#top"
        element["faces"]["down"]["texture"] = "#top"
        steps.append(element)
    write("assets/%s/models/block/paladium_spike.json" % NS, {
        "parent": "minecraft:block/block",
        "textures": {"particle": "%s:block/paladium_spike_side" % NS,
                     "top": "%s:block/paladium_spike_top" % NS, "side": "%s:block/paladium_spike_side" % NS},
        "elements": steps})
    # Les rotations de la baguette de l'End, dont le modele pointe aussi vers le haut.
    write("assets/%s/blockstates/paladium_spike.json" % NS, {"variants": {
        "facing=%s" % facing: dict({"model": "%s:block/paladium_spike" % NS}, **rotation)
        for facing, rotation in (("up", {}), ("down", {"x": 180}), ("north", {"x": 90}),
                                 ("south", {"x": 90, "y": 180}), ("east", {"x": 90, "y": 90}),
                                 ("west", {"x": 90, "y": 270}))}})
    block_item("paladium_spike")
    label("block", "paladium_spike", "Spike en paladium", "Paladium Spike")

    for name in ("palamachine", "slimepad", "paladium_spike"):
        drops_itself(name)
    # setHarvestLevel("pickaxe", 2) : pioche en fer.
    tag("minecraft/tags/block/mineable/pickaxe.json",
        ["%s:palamachine" % NS, "%s:slimepad" % NS, "%s:paladium_spike" % NS])
    tag("minecraft/tags/block/needs_iron_tool.json",
        ["%s:palamachine" % NS, "%s:slimepad" % NS, "%s:paladium_spike" % NS])

    # ---- pierres legendaires et gant en endium (chapitre 17) ----
    for name, texture, label_fr, label_en in (
            ("legendary_stone_random", "RANDOM", "Pierre legendaire aleatoire", "Random Legendary Stone"),
            ("legendary_stone_teleportation", "TELEPORTATION", "Pierre legendaire de teleportation",
             "Teleportation Legendary Stone"),
            ("legendary_stone_invisibility", "INVISIBILITY", "Pierre legendaire d'invisibilite",
             "Invisibility Legendary Stone"),
            ("legendary_stone_fortune", "FORTUNE", "Pierre legendaire de fortune", "Fortune Legendary Stone"),
            ("legendary_stone_power", "POWER", "Pierre legendaire de pouvoir", "Power Legendary Stone"),
            ("legendary_stone_jobs", "JOBS", "Pierre legendaire de metier", "Jobs Legendary Stone"),
            ("legendary_stone_chaos", "CHAOS", "Pierre legendaire de chaos", "Chaos Legendary Stone")):
        tex("items/legendaryStone/LEGENDARYSTONE_%s.png" % texture, "item/%s.png" % name)
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, label_fr, label_en)
    # Le gant : l'icone doree de l'Infinity Gauntlet quand il porte les six
    # pierres (custom_model_data « infinity », pose par EndiumGauntletItem).
    tex("items/endium_gauntlet.png", "item/endium_gauntlet.png")
    tex("items/infinite_gauntlet.png", "item/infinite_gauntlet.png")
    for name in ("endium_gauntlet", "infinite_gauntlet"):
        write("assets/%s/models/item/%s.json" % (NS, name),
              {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    write("assets/%s/items/endium_gauntlet.json" % NS, {"model": {
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
        "cases": [{"when": "infinity",
                   "model": {"type": "minecraft:model", "model": "%s:item/infinite_gauntlet" % NS}}],
        "fallback": {"type": "minecraft:model", "model": "%s:item/endium_gauntlet" % NS}}})
    label("item", "endium_gauntlet", "Endium Gauntlet", "Endium Gauntlet")
    fr["item.%s.endium_gauntlet.infinity" % NS] = "Infinity Gauntlet"
    en["item.%s.endium_gauntlet.infinity" % NS] = "Infinity Gauntlet"
    fr["item.%s.endium_gauntlet.named" % NS] = "%s (%s)"
    en["item.%s.endium_gauntlet.named" % NS] = "%s (%s)"
    write("data/%s/recipe/endium_gauntlet.json" % NS, {"type": "%s:endium_gauntlet" % NS})
    for key, label_fr, label_en in (
            ("teleportation", "Teleportation", "Teleportation"), ("invisibility", "Invisibilite", "Invisibility"),
            ("fortune", "Fortune", "Fortune"), ("power", "Pouvoir", "Power"), ("jobs", "Metier", "Jobs"),
            ("chaos", "Chaos", "Chaos"), ("random", "Aleatoire", "Random")):
        label("legendary", key, label_fr, label_en)
    for key, label_fr, label_en in (
            ("legendary.wait_type", "Tu dois encore attendre %s avant de pouvoir utiliser ce type de pierre legendaire.",
             "You must wait %s more before using this type of legendary stone."),
            ("legendary.wait", "Tu dois encore attendre %s avant de pouvoir utiliser cette pierre legendaire.",
             "You must wait %s more before using this legendary stone."),
            ("legendary.random", "Tu as recu un super objet, une %s, mais a quoi cela sert-il ?",
             "You received a great item, a %s, but what is it for?"),
            ("legendary.teleport_overworld", "La pierre de teleportation ne fonctionne que dans l'Overworld.",
             "The teleportation stone only works in the Overworld."),
            ("legendary.teleported", "Une chose etrange est arrivee, tu as ete teleporte(e) a un autre endroit !",
             "Something strange happened: you were teleported somewhere else!"),
            ("legendary.teleport_failed", "Une chose etrange est arrivee, la teleportation n'a pas fonctionne !",
             "Something strange happened: the teleportation failed!"),
            ("legendary.invisible", "Une potion etrange est tombee sur toi, tu es maintenant invisible pendant 2 minutes !",
             "A strange potion fell on you: you are now invisible for 2 minutes!"),
            ("legendary.fortune", "La fortune est tombee sur toi !", "Fortune smiled on you!"),
            ("legendary.power", "Tu as recu un grand pouvoir : tu resistes au feu pendant 30 minutes !",
             "You received a great power: fire resistance for 30 minutes!"),
            ("legendary.jobs", "Tu as bien travaille : %s points d'experience de %s. Bravo !",
             "You worked hard: %s %s experience points. Well done!"),
            ("legendary.chaos", "Tu viens de lancer le chaos autour de toi !", "You just unleashed chaos around you!"),
            ("gauntlet.empty", "Il n'y a aucune pierre dans le gant !", "There is no stone in the gauntlet!"),
            ("gauntlet.selected", "Pierre selectionnee : %s", "Selected stone: %s")):
        label("message", key, label_fr, label_en)
    for key, label_fr, label_en in (
            ("legendary.random", "Surprise, que va-t-il y avoir dedans ?", "Surprise, what could be inside?"),
            ("legendary.ready", "Prete a l'utilisation !", "Ready to use!"),
            ("legendary.wait", "Encore %s avant de pouvoir l'utiliser", "%s left before it can be used"),
            ("gauntlet.stones", "Pierres :", "Stones:"),
            ("gauntlet.hint", "Accroupi + clic droit : pierre suivante", "Sneak + right-click: next stone")):
        label("tooltip", key, label_fr, label_en)
    for key, label_fr, label_en in (("hours", "%s h %s min", "%sh %smin"), ("minutes", "%s min %s s", "%smin %ss"),
                                    ("seconds", "%s s", "%ss")):
        label("duration", key, label_fr, label_en)

    # ---- donjons : le coeur jouable (chapitre 18) ----
    #
    # Les donjons sont de la V12, posterieurs a la 10.3.0 : rien dans le jar.
    # Les cles sont la cle en paladium de PalaMod teintee de la couleur du
    # donjon, les sacs de tier 2 et 3 le sac en cuir teinte ; les bouteilles
    # d'XP de metier existent telles quelles (items/job_bottle).
    def tinted(relative, destination, rgb):
        """Une texture du pack (ou du jar) passee a la couleur rgb, alpha garde."""
        from PIL import Image
        pack_root, jar_root = root
        source = None
        for base in (pack_root, jar_root):
            if base:
                candidate = os.path.join(base, "assets", NS, "textures", PACK_PATHS.get(relative, relative))
                if os.path.exists(candidate):
                    source = candidate
                    break
        if source is None:
            missing.append(relative)
            return
        image = Image.open(source).convert("RGBA")
        out = Image.new("RGBA", image.size)
        pixels, target = image.load(), out.load()
        for x in range(image.size[0]):
            for y in range(image.size[1]):
                r, g, b, a = pixels[x, y]
                light = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                shade = min(1.0, 0.25 + light * 1.1)
                target[x, y] = (int(rgb[0] * shade), int(rgb[1] * shade), int(rgb[2] * shade), a)
        full = os.path.join(RES, "assets", NS, "textures", destination)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        out.save(full)

    for name, rgb, extra, label_fr, label_en in (
            ("talikus_key", (96, 196, 64), "minecraft:wheat", "Cle de Talikus", "Talikus Key"),
            ("nimbria_key", (170, 170, 178), "minecraft:cobblestone", "Cle de Nimbria", "Nimbria Key"),
            ("vitalys_key", (206, 58, 48), "minecraft:rotten_flesh", "Cle de Vitalys", "Vitalys Key"),
            ("manelios_key", (64, 200, 210), "minecraft:poppy", "Cle de Manelios", "Manelios Key")):
        tinted("items/paladium_key.png", "item/%s.png" % name, rgb)
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, label_fr, label_en)
        # Le wiki : lingot de paladium vert, invoker stone, stick en paladium,
        # lingot de paladium et l'ingredient du metier. La grille n'est que
        # dans les images ; celle-ci met la pierre en tete et le metier au coeur.
        shaped(name, [" V ", "SXS", "PGP"], {
            "V": "%s:invoker_stone" % NS, "S": "%s:paladium_stick" % NS, "X": extra,
            "P": "%s:paladium_ingot" % NS, "G": "%s:green_paladium_ingot" % NS,
        }, "%s:%s" % (NS, name))

    tex("items/backpack.png", "item/dungeon_leather_backpack.png")
    flat_item("dungeon_leather_backpack", "%s:item/dungeon_leather_backpack" % NS)
    label("item", "dungeon_leather_backpack", "Sac de donjon en cuir", "Dungeon Leather Backpack")
    shaped("dungeon_leather_backpack", ["LCL", "LHL", "LIL"], {
        "L": "minecraft:leather", "C": "minecraft:chest", "H": "minecraft:hopper", "I": "%s:invoker_stone" % NS,
    }, "%s:dungeon_leather_backpack" % NS)
    for name, rgb, label_fr, label_en in (
            ("dungeon_backpack_t2", (92, 128, 220), "Sac de donjon tier 2", "Dungeon Backpack Tier 2"),
            ("dungeon_backpack_t3", (226, 176, 48), "Sac de donjon tier 3", "Dungeon Backpack Tier 3")):
        tinted("items/backpack.png", "item/%s.png" % name, rgb)
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, label_fr, label_en)

    for job, label_fr, label_en in (("farmer", "fermier", "Farmer"), ("miner", "mineur", "Miner"),
                                    ("hunter", "chasseur", "Hunter"), ("alchemist", "alchimiste", "Alchemist")):
        name = "job_bottle_%s" % job
        tex("items/job_bottle/%s.png" % job, "item/%s.png" % name)
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, "Bouteille d'XP de %s" % label_fr, "%s XP Bottle" % label_en)

    for key, label_fr, label_en in (
            ("dungeon_key", "Ouvre les paliers du donjon. XP de metier : %s.",
             "Opens the dungeon's floors. Job XP: %s."),
            ("dungeon_key.hint", "Clic droit : choisir un palier. Consommee a l'entree.",
             "Right-click: choose a floor. Consumed on entry."),
            ("dungeon_backpack", "Sac de donjon : %s piles de butin.", "Dungeon bag: %s stacks of loot."),
            ("dungeon_backpack.hint", "Il suffit de l'avoir sur soi en entrant.", "Just carry it when you enter."),
            ("job_bottle", "%s XP de %s", "%s %s XP"),
            ("job_bottle.hint", "Clic droit : boire. Accroupi : toute la pile.",
             "Right-click: drink. Sneak: the whole stack.")):
        label("tooltip", key, label_fr, label_en)
    fr["message.%s.job_bottle" % NS] = "+%s XP de %s"
    en["message.%s.job_bottle" % NS] = "+%s %s XP"
    for key, label_fr, label_en in (
            ("unavailable", "Les donjons ne sont pas disponibles sur ce serveur.",
             "Dungeons aren't available on this server."),
            ("already", "Vous etes deja dans un donjon.", "You're already in a dungeon."),
            ("locked", "Vous devez d'abord reussir le palier %s.", "You must clear floor %s first."),
            ("no_key", "Il vous faut une cle de %s en main.", "You need a %s key in hand."),
            ("start", "%s, palier %s (%s) : bonne chance !", "%s, floor %s (%s): good luck!"),
            ("party", "Groupe : %s", "Party: %s"),
            ("wave", "Vague %s/%s !", "Wave %s/%s!"),
            ("room_cleared", "Salle terminee ! Avancez vers une porte pour choisir la suite.",
             "Room cleared! Walk into a door to choose what comes next."),
            ("chest", "Un coffre est apparu au centre de la salle : chacun le sien.",
             "A chest appeared in the middle of the room: one for each of you."),
            ("chest_loot", "Coffre : %s", "Chest: %s"),
            ("chest_done", "Vous avez deja ouvert ce coffre.", "You already opened this chest."),
            ("jar_loot", "Jarre : %s", "Jar: %s"),
            ("chest_auto", "Coffre laisse, ramasse pour vous : %s", "Chest left behind, picked up for you: %s"),
            ("bag_full", "Sac plein, perdu : %s", "Bag full, lost: %s"),
            ("door", "%s ouvre la porte : %s", "%s opens the door: %s"),
            ("puzzle", "Enigme : trouvez et appuyez sur les %s boutons de la salle.",
             "Puzzle: find and press the room's %s buttons."),
            ("puzzle_progress", "Bouton trouve (%s/%s)", "Button found (%s/%s)"),
            ("boost", "Salle de boost : choisissez un bonus a l'autel, au centre.",
             "Boost room: pick a bonus at the altar in the middle."),
            ("boost_taken", "Bonus choisi : %s, pour 10 minutes.", "Bonus chosen: %s, for 10 minutes."),
            ("boost_already", "Vous avez deja choisi votre bonus.", "You already picked your bonus."),
            ("merchant", "Un marchand vous attend : clic droit pour commercer.",
             "A merchant is waiting: right-click to trade."),
            ("bought", "Achat : %s pour %s", "Bought: %s for %s"),
            ("poor", "Il vous faut %s.", "You need %s."),
            ("boss", "%s apparait !", "%s appears!"),
            ("boss_dead", "%s est vaincu !", "%s is defeated!"),
            ("success", "Palier %s de %s reussi !", "Floor %s of %s cleared!"),
            ("delivered", "Sac livre : %s piles. +%s XP de %s.", "Bag delivered: %s stacks. +%s %s XP."),
            ("unique", "Recompense unique : %s", "Unique reward: %s"),
            ("death", "Vous etes mort : le palier est echoue et votre sac est perdu.",
             "You died: the floor is failed and your bag is lost."),
            ("quit", "Vous avez quitte le donjon : le palier est echoue et votre sac est perdu.",
             "You left the dungeon: the floor is failed and your bag is lost."),
            ("member_left", "%s a quitte le donjon.", "%s left the dungeon."),
            ("not_inside", "Vous n'etes pas dans un donjon.", "You aren't in a dungeon."),
            ("returned", "Le donjon s'est ferme pendant votre absence : vous etes ramene.",
             "The dungeon closed while you were away: you've been brought back.")):
        label("message", "dungeon." + key, label_fr, label_en)

    # La dimension des instances : vide (un monde plat sans couche), sans
    # ciel ni monstre naturel. Toute la lumiere vient des salles.
    write("data/%s/worldgen/biome/dungeon.json" % NS, {
        "attributes": {"minecraft:visual/sky_color": "#000000", "minecraft:visual/fog_color": "#0b0b12"},
        "carvers": [],
        "downfall": 0.0,
        "effects": {"water_color": "#3f76e4"},
        "features": [],
        "has_precipitation": False,
        "spawn_costs": {},
        "spawners": {category: [] for category in (
            "ambient", "axolotls", "creature", "misc", "monster", "underground_water_creature",
            "water_ambient", "water_creature")},
        "temperature": 0.8,
    })
    write("data/%s/dimension_type/dungeon.json" % NS, {
        "ambient_light": 0.15,
        "attributes": {
            "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never"},
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:visual/ambient_light_color": "#141420",
            "minecraft:visual/fog_color": "#0b0b12",
            "minecraft:visual/fog_start_distance": 32.0,
            "minecraft:visual/fog_end_distance": 96.0,
            "minecraft:visual/sky_color": "#000000",
        },
        "coordinate_scale": 1.0,
        "has_ceiling": False,
        "has_ender_dragon_fight": False,
        "has_fixed_time": True,
        "has_skylight": False,
        "height": 256,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": 256,
        "min_y": 0,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": 0,
        "skybox": "none",
        "timelines": "#minecraft:universal",
    })
    write("data/%s/dimension/dungeon.json" % NS, {
        "type": "%s:dungeon" % NS,
        "generator": {"type": "minecraft:flat", "settings": {
            "biome": "%s:dungeon" % NS, "features": False, "lakes": False, "layers": [],
            "structure_overrides": []}},
    })

    # ---- donjons : equipement, essences, set antique (chapitre 19) ----
    #
    # Textures de Palamod Recreated (SILWOX / Solea Pixel, mod de fans 1.21,
    # « All Rights Reserved »), au choix du joueur : cinquieme argument, ou le
    # dossier voisin du jar de PalaMod. Le set antique n'y est pas : c'est le
    # legendaire de Talikus passe au bronze.
    recreated_root = sys.argv[5] if len(sys.argv) > 5 else (
        os.path.join(os.path.dirname(root[1].rstrip("/")), "nf121") if root[1] else None)

    def recreated_path(relative):
        return os.path.join(recreated_root or "", "assets", NS, "textures", relative)

    def recreated_tex(relative, destination, size=(16, 16)):
        source = recreated_path(relative)
        if not recreated_root or not os.path.exists(source):
            missing.append("recreated:" + relative)
            return
        if png_size(source) != size:
            wrong_size.append("recreated:%s : %s au lieu de %s" % (relative, png_size(source), size))
            return
        target = os.path.join(RES, "assets", NS, "textures", destination)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copyfile(source, target)

    def bronze(relative, destination):
        """Une texture de Palamod Recreated passee au degrade bronze-or, alpha garde."""
        from PIL import Image
        source = recreated_path(relative)
        if not recreated_root or not os.path.exists(source):
            missing.append("recreated:" + relative)
            return
        image = Image.open(source).convert("RGBA")
        out = Image.new("RGBA", image.size)
        pixels, target = image.load(), out.load()
        dark, bright = (58, 34, 14), (255, 214, 110)
        for x in range(image.size[0]):
            for y in range(image.size[1]):
                r, g, b, a = pixels[x, y]
                light = min(1.0, ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0) * 1.25)
                target[x, y] = tuple(int(dark[i] + (bright[i] - dark[i]) * light) for i in range(3)) + (a,)
        full = os.path.join(RES, "assets", NS, "textures", destination)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        out.save(full)

    def handheld_item(name, texture):
        write("assets/%s/models/item/%s.json" % (NS, name),
              {"parent": "minecraft:item/handheld", "textures": {"layer0": texture}})
        write("assets/%s/items/%s.json" % (NS, name),
              {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}})

    def equipment_layers(material, layer_1, layer_2):
        write("assets/%s/equipment/%s.json" % (NS, material), {"layers": {
            "humanoid": [{"texture": "%s:%s" % (NS, material)}],
            "humanoid_leggings": [{"texture": "%s:%s" % (NS, material)}]}})

    # Le nom et le pluriel feminin : « jambieres renforcees », « bottes legendaires ».
    PIECE_FR = {"helmet": ("Casque", False), "chestplate": ("Plastron", False),
                "leggings": ("Jambieres", True), "boots": ("Bottes", True)}
    PIECE_EN = {"helmet": "Helmet", "chestplate": "Chestplate", "leggings": "Leggings", "boots": "Boots"}
    PIECE_TAG = {"helmet": "head_armor", "chestplate": "chest_armor", "leggings": "leg_armor",
                 "boots": "foot_armor"}
    ELEMENTS = (("talikus", "Talikus", "forest", "de foret", "Forest",
                 ("forest_essence_fragment", "forest_essence", "forest_essence_concentrate")),
                ("nimbria", "Nimbria", "cavern", "de caverne", "Cavern",
                 ("cavern_essence_fragment", "cavern_essence", "cavern_essence_concentrate")),
                ("vitalys", "Vitalys", "flame", "de flamme", "Flame",
                 ("flame_essence_fragment", "flame_essence", "flame_essence_concentrate")),
                ("manelios", "Manelios", "aquatic", "aquatique", "Aquatic",
                 ("essence_aquatique_fragment", "essence_aquatique", "aquatic_essence_concentrate")))
    TIERS = (("", "", "", "", 0), ("reinforced", "renforce", "renforcee", "Reinforced", 1),
             ("legendary", "legendaire", "legendaire", "Legendary", 2))
    concentrates = []
    for dungeon, display, element, element_fr, element_en, sources in ELEMENTS:
        essences = []
        for (suffix, label_fr, label_en), source in zip(
                (("_fragment", "Fragment d'essence %s", "%s Essence Fragment"),
                 ("", "Essence %s", "%s Essence"),
                 ("_concentrate", "Essence %s concentree", "Concentrated %s Essence")), sources):
            name = "%s_essence%s" % (element, suffix)
            recreated_tex("item/%s.png" % source, "item/%s.png" % name)
            flat_item(name, "%s:item/%s" % (NS, name))
            label("item", name, label_fr % element_fr, label_en % element_en)
            essences.append(name)
        concentrates.append(essences[2])
        for tier, adj_m, adj_f, adj_en, index in TIERS:
            material = dungeon if not tier else "%s_%s" % (dungeon, tier)
            for piece in ("helmet", "chestplate", "leggings", "boots"):
                name = "%s_%s" % (material, piece)
                recreated_tex("item/%s.png" % name, "item/%s.png" % name)
                flat_item(name, "%s:item/%s" % (NS, name))
                noun, plural = PIECE_FR[piece]
                adjective = "" if not tier else " " + ((adj_f + "s") if plural else adj_m)
                label("item", name, "%s%s de %s" % (noun, adjective, display),
                      ("%s %s %s" % (adj_en, display, PIECE_EN[piece])).strip())
                tag("minecraft/tags/item/%s.json" % PIECE_TAG[piece], ["%s:%s" % (NS, name)])
                tag("minecraft/tags/item/trimmable_armor.json", ["%s:%s" % (NS, name)])
            name = "%s_sword" % material
            recreated_tex("item/%s.png" % name, "item/%s.png" % name)
            handheld_item(name, "%s:item/%s" % (NS, name))
            label("item", name, "Epee%s de %s" % ("" if not tier else " " + adj_f, display),
                  ("%s %s Sword" % (adj_en, display)).strip())
            tag("minecraft/tags/item/swords.json", ["%s:%s" % (NS, name)])
            for layer, suffix in (("humanoid", 1), ("humanoid_leggings", 2)):
                recreated_tex("models/armor/%s_layer_%d.png" % (material, suffix),
                              "entity/equipment/%s/%s.png" % (layer, material), size=(64, 32))
            equipment_layers(material, 1, 2)
            tag("%s/tags/item/repairs/%s.json" % (NS, material), ["%s:%s" % (NS, essences[index])])

    # L'antique : l'« Ancient armor » de Palamod Recreated, la seule armure
    # antique dessinee. Icones rendues en 3D (256 a 512 px), ramenees a 32 x 32 ;
    # armure portee en 3D (AntiqueArmorClient), sur sa texture de 256 x 256. Les
    # couches plates (le legendaire de Talikus au bronze) ne restent qu'en
    # secours, si le modele 3D ne se charge pas ; pas de garniture sur un modele 3D.
    def ancient_icon(relative, destination):
        from PIL import Image, ImageEnhance
        source = recreated_path(relative)
        if not recreated_root or not os.path.exists(source):
            missing.append("recreated:" + relative)
            return
        image = Image.open(source).convert("RGBA")
        image = image.crop(image.getbbox())
        side = max(image.size)
        square = Image.new("RGBA", (side, side))
        square.paste(image, ((side - image.size[0]) // 2, (side - image.size[1]) // 2))
        icon = ImageEnhance.Contrast(square.resize((32, 32), Image.LANCZOS)).enhance(1.25)
        pixels = icon.load()
        for x in range(32):
            for y in range(32):
                r, g, b, a = pixels[x, y]
                pixels[x, y] = (r, g, b, 255 if a > 100 else 0)
        full = os.path.join(RES, "assets", NS, "textures", destination)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        icon.save(full)

    for piece, label_fr in (("helmet", "Casque antique"), ("chestplate", "Plastron antique"),
                            ("leggings", "Pantalon antique"), ("boots", "Bottes antiques")):
        name = "antique_%s" % piece
        ancient_icon("item/ancient_%s.png" % piece, "item/%s.png" % name)
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, label_fr, "Antique %s" % PIECE_EN[piece])
        tag("minecraft/tags/item/%s.json" % PIECE_TAG[piece], ["%s:%s" % (NS, name)])
    recreated_tex("entities/ancient_armor.png", "entity/antique_armor.png", size=(256, 256))
    for layer, suffix in (("humanoid", 1), ("humanoid_leggings", 2)):
        bronze("models/armor/talikus_legendary_layer_%d.png" % suffix,
               "entity/equipment/%s/antique.png" % layer)
    equipment_layers("antique", 1, 2)
    tag("%s/tags/item/repairs/antique.json" % NS, ["%s:%s" % (NS, name) for name in concentrates])

    for key, label_fr, label_en in (
            ("dungeon_gear.armor", "En donjon : -%s%% de degats des monstres a %s, moitie ailleurs",
             "In dungeons: -%s%% damage from monsters in %s, half elsewhere"),
            ("dungeon_gear.sword", "En donjon : +%s%% de degats aux monstres a %s, moitie ailleurs",
             "In dungeons: +%s%% damage to monsters in %s, half elsewhere"),
            ("dungeon_gear.antique.armor", "En donjon : -%s%% de degats des monstres, dans tous les donjons",
             "In dungeons: -%s%% damage from monsters, in every dungeon"),
            ("dungeon_gear.antique.sword", "En donjon : +%s%% de degats aux monstres",
             "In dungeons: +%s%% damage to monsters"),
            ("dungeon_gear.antique_set", "Set complet : vol et invisibilite hors des donjons",
             "Full set: flight and invisibility outside dungeons"),
            ("dungeon_gear.antique_set.dungeon", "En donjon, set complet : -10%% de degats recus, +20%% de degats",
             "In dungeons, full set: -10%% damage taken, +20%% damage dealt"),
            ("dungeon_gear.repair", "Se repare a l'enclume avec : %s", "Repaired at the anvil with: %s"),
            ("dungeon_gear.repair_any", "Se repare a l'enclume avec une essence concentree",
             "Repaired at the anvil with any concentrated essence"),
            ("essence.fragment", "Repare l'equipement basique de %s a l'enclume",
             "Repairs %s basic gear at the anvil"),
            ("essence.essence", "Repare l'equipement renforce de %s a l'enclume",
             "Repairs %s reinforced gear at the anvil"),
            ("essence.concentrate", "Repare l'equipement legendaire de %s, et l'antique, a l'enclume",
             "Repairs %s legendary gear, and antique gear, at the anvil"),
            ("essence.hint", "Tombe dans les coffres des donjons.", "Found in dungeon chests.")):
        label("tooltip", key, label_fr, label_en)
    for key, label_fr, label_en in (
            ("grade", "Note %s : degats %s%%, survie %s%%, implication %s%%.",
             "Grade %s: damage %s%%, survival %s%%, involvement %s%%."),
            ("grade_chest", "Coffre de la note : %s", "Grade chest: %s")):
        label("message", "dungeon." + key, label_fr, label_en)

    # ---- donjons : runes et arbre de competences (chapitre 20) ----
    #
    # Aucune texture de rune n'existe, ni dans PalaMod ni dans Palamod
    # Recreated : une tablette de pierre gravee, dessinee ici pixel par pixel,
    # dont la gravure prend la couleur de la rarete. Le fragment en est l'eclat
    # superieur gauche.
    RUNE_TABLET = (
        "................",
        ".....######.....",
        "....#ssssss#....",
        "...#sssssssS#...",
        "...#sGsssGsS#...",
        "...#sGGsGGsS#...",
        "...#sGsGsGsS#...",
        "...#sGsssGsS#...",
        "...#sGsssGsS#...",
        "...#ssGsGssS#...",
        "...#sssGsssS#...",
        "...#sssssssS#...",
        "...#ssssssSS#...",
        "....#SSSSSS#....",
        ".....######.....",
        "................")
    RUNE_SHARD = (
        "................",
        "................",
        ".....####.......",
        "....#ssss#......",
        "...#sGsss#......",
        "...#sGGs#.......",
        "...#sGsG#.......",
        "...#sGss##......",
        "...#sGsss#......",
        "...#ssGs#.......",
        "....#ss#........",
        ".....##.........",
        "................",
        "................",
        "................",
        "................")

    def rune_texture(name, pattern, glyph):
        from PIL import Image
        palette = {"#": (38, 38, 46, 255), "s": (124, 124, 134, 255), "S": (92, 92, 102, 255),
                   "G": glyph + (255,), ".": (0, 0, 0, 0)}
        image = Image.new("RGBA", (16, 16))
        for y, row in enumerate(pattern):
            for x, cell in enumerate(row):
                image.putpixel((x, y), palette[cell])
        full = os.path.join(RES, "assets", NS, "textures", "item", name + ".png")
        os.makedirs(os.path.dirname(full), exist_ok=True)
        image.save(full)

    for name, pattern, glyph, label_fr, label_en in (
            ("rune_fragment_common", RUNE_SHARD, (210, 210, 220), "Fragment de rune commune", "Common Rune Fragment"),
            ("rune_fragment_rare", RUNE_SHARD, (80, 220, 255), "Fragment de rune rare", "Rare Rune Fragment"),
            ("rune_common", RUNE_TABLET, (236, 236, 244), "Rune commune", "Common Rune"),
            ("rune_rare", RUNE_TABLET, (80, 220, 255), "Rune rare", "Rare Rune"),
            ("rune_epic", RUNE_TABLET, (214, 110, 255), "Rune epique", "Epic Rune")):
        rune_texture(name, pattern, glyph)
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, label_fr, label_en)

    for key, label_fr, label_en in (("common", "commune", "common"), ("rare", "rare", "rare"),
                                    ("epic", "epique", "epic")):
        label("rarity", key, label_fr, label_en)
    for key, label_fr, label_en in (("attack", "Attaque", "Attack"), ("dodge", "Esquive", "Dodge"),
                                    ("crit", "Critique", "Critical"), ("defense", "Defense", "Defense")):
        label("rpg", key, label_fr, label_en)
    for key, label_fr, label_en in (
            ("rune.unrevealed", "Non revelee : clic droit pour decouvrir ses statistiques",
             "Unrevealed: right-click to discover its stats"),
            ("rune.tier", "Rune %s, tier %s", "%s rune, tier %s"),
            ("rune.hint", "Enclume : avec une rune pareille, tier suivant. Grinder : dans une armure de donjon.",
             "Anvil: with a matching rune, next tier. Grinder: into dungeon armor."),
            ("dungeon_gear.rune_slot", "Emplacement de rune libre (au grinder)", "Free rune slot (at the grinder)")):
        label("tooltip", key, label_fr, label_en)
    for key, label_fr, label_en in (
            ("rune.revealed", "La rune revele ses statistiques.", "The rune reveals its stats."),
            ("rpg.xp", "+%s XP RPG", "+%s RPG XP"),
            ("rpg.level", "Niveau RPG %s ! Un point de competence de plus (/profil).",
             "RPG level %s! One more skill point (/profil)."),
            ("rpg.validated", "Arbre valide : %s points depenses.", "Tree confirmed: %s points spent."),
            ("rpg.respec", "Arbre reinitialise : vos points sont rendus.", "Tree reset: your points are back."),
            ("rpg.respec_poor", "Il vous faut 25 000 $ pour reinitialiser l'arbre.",
             "You need $25,000 to reset the tree."),
            ("rpg.dodge", "Esquive !", "Dodged!")):
        label("message", key, label_fr, label_en)
    fr["button.%s.rpg_profile" % NS] = "Profil RPG"
    en["button.%s.rpg_profile" % NS] = "RPG Profile"

    # ---- defense de faction (chapitre 22, lot 22c) ----
    #
    # Obsidiennes renforcee et piegees, spikes et obsidiennes a spike, blocs
    # d'obsidienne, Fake Water, eponges, brouilleur, enclumes, amulettes et
    # objets. Textures : le pack, le jar pour ce que le pack n'a pas (les
    # obsidiennes knockback, gelee et anti-aggro, la porte, les eponges a lave,
    # le brouilleur, le modele du pistolet).
    def cube_model(name, textures, parent="minecraft:block/cube_all"):
        write("assets/%s/models/block/%s.json" % (NS, name), {"parent": parent, "textures": textures})

    def simple_block(name, label_fr, label_en, textures, parent="minecraft:block/cube_all"):
        cube_model(name, textures, parent)
        write("assets/%s/blockstates/%s.json" % (NS, name),
              {"variants": {"": {"model": "%s:block/%s" % (NS, name)}}})
        block_item(name)
        drops_itself(name)
        label("block", name, label_fr, label_en)

    # Une face avant (north dans le modele) tournee vers FACING, comme le piston.
    FACING_ROTATIONS = (("north", {}), ("south", {"y": 180}), ("east", {"y": 90}), ("west", {"y": 270}),
                        ("up", {"x": 270}), ("down", {"x": 90}))

    def front_model(name, front, side):
        cube_model(name, {"particle": side, "north": front, "south": side, "east": side, "west": side,
                          "up": side, "down": side}, "minecraft:block/cube")

    def facing_states(name, model, extra=""):
        return {"facing=%s%s" % (facing, extra): dict({"model": model}, **rotation)
                for facing, rotation in FACING_ROTATIONS}

    obsidian = "minecraft:block/obsidian"
    defense_removable = []

    # L'obsidienne renforcee : la texture de l'obsidienne (setBlockTextureName
    # « minecraft:obsidian »), ses escaliers et sa dalle.
    simple_block("hardened_obsidian", "Obsidienne renforcee", "Hardened Obsidian", {"all": obsidian})
    defense_removable.append("hardened_obsidian")

    def stairs(name, texture, label_fr, label_en):
        for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
            write("assets/%s/models/block/%s%s.json" % (NS, name, suffix), {
                "parent": "minecraft:block/%s" % parent,
                "textures": {"bottom": texture, "top": texture, "side": texture}})
        variants = {}
        for facing, base in (("east", 0), ("south", 90), ("west", 180), ("north", 270)):
            for half in ("bottom", "top"):
                for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                    model = "%s:block/%s%s" % (NS, name, "" if shape == "straight"
                                               else "_inner" if shape.startswith("inner") else "_outer")
                    y = base
                    if shape in ("inner_left", "outer_left"):
                        y -= 90
                    if half == "top":
                        if shape in ("inner_right", "outer_right"):
                            y += 90
                        elif shape in ("inner_left", "outer_left"):
                            y += 90
                    entry = {"model": model}
                    if half == "top":
                        entry["x"] = 180
                    if y % 360:
                        entry["y"] = y % 360
                    if half == "top" or y % 360:
                        entry["uvlock"] = True
                    variants["facing=%s,half=%s,shape=%s" % (facing, half, shape)] = entry
        write("assets/%s/blockstates/%s.json" % (NS, name), {"variants": variants})
        block_item(name)
        drops_itself(name)
        label("block", name, label_fr, label_en)

    def slab(name, texture, full_model, label_fr, label_en):
        write("assets/%s/models/block/%s.json" % (NS, name), {
            "parent": "minecraft:block/slab", "textures": {"bottom": texture, "top": texture, "side": texture}})
        write("assets/%s/models/block/%s_top.json" % (NS, name), {
            "parent": "minecraft:block/slab_top", "textures": {"bottom": texture, "top": texture, "side": texture}})
        write("assets/%s/blockstates/%s.json" % (NS, name), {"variants": {
            "type=bottom": {"model": "%s:block/%s" % (NS, name)},
            "type=top": {"model": "%s:block/%s_top" % (NS, name)},
            "type=double": {"model": full_model}}})
        block_item(name)
        # Une dalle double rend deux dalles.
        write("data/%s/loot_table/blocks/%s.json" % (NS, name), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, name),
                       "functions": [{"function": "minecraft:set_count", "count": 2, "add": False,
                                      "conditions": [{"condition": "minecraft:block_state_property",
                                                      "block": "%s:%s" % (NS, name),
                                                      "properties": {"type": "double"}}]},
                                     {"function": "minecraft:explosion_decay"}]}]}]})
        label("block", name, label_fr, label_en)

    stairs("hardened_obsidian_stairs", obsidian, "Escaliers en obsidienne renforcee", "Hardened Obsidian Stairs")
    slab("hardened_obsidian_slab", obsidian, "%s:block/hardened_obsidian" % NS,
         "Dalle en obsidienne renforcee", "Hardened Obsidian Slab")
    stairs("obsidian_stairs", obsidian, "Escaliers en obsidienne", "Obsidian Stairs")
    slab("obsidian_slab", obsidian, "minecraft:block/obsidian", "Dalle en obsidienne", "Obsidian Slab")
    defense_removable += ["hardened_obsidian_stairs", "hardened_obsidian_slab"]

    # La porte et la trappe en obsidienne : les modeles de la porte et de la
    # trappe vanilla, sur les textures de PalaMod.
    tex("blocks/decorative/obsidianBlockDoor_lower.png", "block/obsidian_door_bottom.png")
    tex("blocks/decorative/obsidianBlockDoor_upper.png", "block/obsidian_door_top.png")
    tex("blocks/decorative/obsidianTrapDoor.png", "block/obsidian_trapdoor.png")
    tex("items/decorative/obsidianItemDoor.png", "item/obsidian_door.png")
    door_textures = {"bottom": "%s:block/obsidian_door_bottom" % NS, "top": "%s:block/obsidian_door_top" % NS}
    for part in ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open",
                 "top_left", "top_left_open", "top_right", "top_right_open"):
        write("assets/%s/models/block/obsidian_door_%s.json" % (NS, part),
              {"parent": "minecraft:block/door_%s" % part, "textures": door_textures})
    door_variants = {}
    for facing, base in (("east", 0), ("south", 90), ("west", 180), ("north", 270)):
        for half in ("lower", "upper"):
            for hinge in ("left", "right"):
                for opened in ("false", "true"):
                    part = "%s_%s%s" % ("bottom" if half == "lower" else "top", hinge,
                                        "_open" if opened == "true" else "")
                    y = base
                    if opened == "true":
                        y += 90 if hinge == "left" else 270
                    entry = {"model": "%s:block/obsidian_door_%s" % (NS, part)}
                    if y % 360:
                        entry["y"] = y % 360
                    door_variants["facing=%s,half=%s,hinge=%s,open=%s" % (facing, half, hinge, opened)] = entry
    write("assets/%s/blockstates/obsidian_door.json" % NS, {"variants": door_variants})
    flat_item("obsidian_door", "%s:item/obsidian_door" % NS)
    write("data/%s/loot_table/blocks/obsidian_door.json" % NS, {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s:obsidian_door" % NS,
                   "conditions": [{"condition": "minecraft:block_state_property",
                                   "block": "%s:obsidian_door" % NS, "properties": {"half": "lower"}}]}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    label("block", "obsidian_door", "Porte en obsidienne", "Obsidian Door")
    trapdoor_texture = {"texture": "%s:block/obsidian_trapdoor" % NS}
    for part in ("bottom", "top", "open"):
        write("assets/%s/models/block/obsidian_trapdoor_%s.json" % (NS, part),
              {"parent": "minecraft:block/template_orientable_trapdoor_%s" % part, "textures": trapdoor_texture})
    trapdoor_variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for half in ("bottom", "top"):
            for opened in ("false", "true"):
                part = "open" if opened == "true" else half
                entry = {"model": "%s:block/obsidian_trapdoor_%s" % (NS, part)}
                rotation_y = y
                if opened == "true" and half == "top":
                    entry["x"] = 180
                    rotation_y = (y + 180) % 360
                if rotation_y:
                    entry["y"] = rotation_y
                for powered in ("false", "true"):
                    for waterlogged in ("false", "true"):
                        trapdoor_variants["facing=%s,half=%s,open=%s,powered=%s,waterlogged=%s"
                                          % (facing, half, opened, powered, waterlogged)] = entry
    write("assets/%s/blockstates/obsidian_trapdoor.json" % NS, {"variants": trapdoor_variants})
    write("assets/%s/models/item/obsidian_trapdoor.json" % NS, {"parent": "%s:block/obsidian_trapdoor_bottom" % NS})
    write("assets/%s/items/obsidian_trapdoor.json" % NS,
          {"model": {"type": "minecraft:model", "model": "%s:item/obsidian_trapdoor" % NS}})
    drops_itself("obsidian_trapdoor")
    label("block", "obsidian_trapdoor", "Trappe en obsidienne", "Obsidian Trapdoor")
    for name in ("obsidian_stairs", "obsidian_slab", "obsidian_door", "obsidian_trapdoor"):
        tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, name)])
        tag("minecraft/tags/block/needs_diamond_tool.json", ["%s:%s" % (NS, name)])
    tag("minecraft/tags/block/stairs.json", ["%s:obsidian_stairs" % NS, "%s:hardened_obsidian_stairs" % NS])
    tag("minecraft/tags/item/stairs.json", ["%s:obsidian_stairs" % NS, "%s:hardened_obsidian_stairs" % NS])
    tag("minecraft/tags/block/slabs.json", ["%s:obsidian_slab" % NS, "%s:hardened_obsidian_slab" % NS])
    tag("minecraft/tags/item/slabs.json", ["%s:obsidian_slab" % NS, "%s:hardened_obsidian_slab" % NS])
    tag("minecraft/tags/block/doors.json", ["%s:obsidian_door" % NS])
    tag("minecraft/tags/item/doors.json", ["%s:obsidian_door" % NS])
    tag("minecraft/tags/block/trapdoors.json", ["%s:obsidian_trapdoor" % NS])
    tag("minecraft/tags/item/trapdoors.json", ["%s:obsidian_trapdoor" % NS])

    # Les obsidiennes piegees (blocks/pillage/effects).
    for name, texture, label_fr, label_en in (
            ("poison_obsidian", "poison_obsi", "Obsidienne empoisonnee", "Poison Obsidian"),
            ("lava_obsidian", "lava_obsi", "Obsidienne de lave", "Lava Obsidian"),
            ("sulfuric_obsidian", "fake_obsi", "Obsidienne sulfurique", "Sulfuric Obsidian"),
            ("knockback_obsidian", "knockback_obsi", "Knockback obsidienne", "Knockback Obsidian"),
            ("frozen_obsidian", "frozen_obsi", "Obsidienne gelee", "Frozen Obsidian"),
            ("anti_aggro_obsidian", "anti_aggro_obsi", "Obsidienne anti-aggro", "Anti-Aggro Obsidian"),
            ("big_obsidian", "big_obsi", "Obsidienne compacte", "Big Obsidian"),
            ("wither_obsidian", "wither_obsi", "Obsidienne a wither", "Wither Obsidian")):
        tex("blocks/pillage/effects/%s.png" % texture, "block/%s.png" % name)
        simple_block(name, label_fr, label_en, {"all": "%s:block/%s" % (NS, name)})
        defense_removable.append(name)
    for name, texture, label_fr, label_en in (
            ("boom_obsidian", "boom_obsi", "Boom obsidienne", "Boom Obsidian"),
            ("mega_boom_obsidian", "mega_boom_obsi", "Mega boom obsidienne", "Mega Boom Obsidian")):
        for face in ("top", "side", "bottom"):
            tex("blocks/pillage/effects/%s_%s.png" % (texture, face), "block/%s_%s.png" % (name, face))
        simple_block(name, label_fr, label_en, {face: "%s:block/%s_%s" % (NS, name, face)
                                               for face in ("top", "side", "bottom")},
                     "minecraft:block/cube_bottom_top")
        defense_removable.append(name)

    # La slime obsidienne et les obsidiennes a spike : une face avant.
    tex("blocks/pillage/effects/slime_obsi.png", "block/slime_obsidian.png")
    tex("blocks/pillage/effects/slime_obsi_front.png", "block/slime_obsidian_front.png")
    front_model("slime_obsidian", "%s:block/slime_obsidian_front" % NS, "%s:block/slime_obsidian" % NS)
    write("assets/%s/blockstates/slime_obsidian.json" % NS,
          {"variants": facing_states("slime_obsidian", "%s:block/slime_obsidian" % NS)})
    block_item("slime_obsidian")
    drops_itself("slime_obsidian")
    label("block", "slime_obsidian", "Slime obsidienne", "Slime Obsidian")
    defense_removable.append("slime_obsidian")

    SPIKE_METALS = (("wood", "", "en bois", "Wood"), ("iron", "_1", "en fer", "Iron"),
                    ("gold", "_2", "en or", "Gold"), ("diamond", "_3", "en diamant", "Diamond"),
                    ("amethyst", "_4", "en amethyste", "Amethyst"), ("titane", "_5", "en titane", "Titane"),
                    ("paladium", "_6", "en paladium", "Paladium"))
    for metal, index, label_fr, label_en in SPIKE_METALS:
        name = "%s_spike_obsidian" % metal
        tex("blocks/pillage/effects/spike_obsi%s.png" % index, "block/%s.png" % name)
        tex("blocks/pillage/effects/spike_obsi%s_front.png" % index, "block/%s_front.png" % name)
        front_model(name, "%s:block/%s_front" % (NS, name), "%s:block/%s" % (NS, name))
        write("assets/%s/blockstates/%s.json" % (NS, name),
              {"variants": facing_states(name, "%s:block/%s" % (NS, name))})
        block_item(name)
        drops_itself(name)
        label("block", name, "Obsidienne a spike %s" % label_fr, "%s Spike Obsidian" % label_en)
        defense_removable.append(name)

    # L'obsidienne a redstone : rouge allumee, sombre eteinte (le dessus ne change pas).
    for face in ("top", "side", "bottom", "side_off", "bottom_off"):
        tex("blocks/pillage/effects/redstone_obsi_%s.png" % face, "block/redstone_obsidian_%s.png" % face)
    cube_model("redstone_obsidian", {"top": "%s:block/redstone_obsidian_top" % NS,
                                     "side": "%s:block/redstone_obsidian_side" % NS,
                                     "bottom": "%s:block/redstone_obsidian_bottom" % NS},
               "minecraft:block/cube_bottom_top")
    cube_model("redstone_obsidian_off", {"top": "%s:block/redstone_obsidian_top" % NS,
                                         "side": "%s:block/redstone_obsidian_side_off" % NS,
                                         "bottom": "%s:block/redstone_obsidian_bottom_off" % NS},
               "minecraft:block/cube_bottom_top")
    write("assets/%s/blockstates/redstone_obsidian.json" % NS, {"variants": {
        "power=%d" % power: {"model": "%s:block/redstone_obsidian%s" % (NS, "_off" if power == 0 else "")}
        for power in range(16)}})
    write("assets/%s/models/item/redstone_obsidian.json" % NS, {"parent": "%s:block/redstone_obsidian_off" % NS})
    write("assets/%s/items/redstone_obsidian.json" % NS,
          {"model": {"type": "minecraft:model", "model": "%s:item/redstone_obsidian" % NS}})
    drops_itself("redstone_obsidian")
    label("block", "redstone_obsidian", "Obsidienne a redstone", "Redstone Obsidian")
    defense_removable.append("redstone_obsidian")

    # L'observateur en obsidienne : la face et le dos de l'observateur vanilla,
    # l'obsidienne autour. PalaMod n'avait pas de texture propre.
    for powered, back in (("false", "observer_back"), ("true", "observer_back_on")):
        name = "observer_obsidian" + ("_on" if powered == "true" else "")
        cube_model(name, {"particle": obsidian, "north": "minecraft:block/observer_front",
                          "south": "minecraft:block/%s" % back, "east": obsidian, "west": obsidian,
                          "up": obsidian, "down": obsidian}, "minecraft:block/cube")
    observer_variants = {}
    for powered in ("false", "true"):
        model = "%s:block/observer_obsidian%s" % (NS, "_on" if powered == "true" else "")
        observer_variants.update(facing_states("observer_obsidian", model, ",powered=%s" % powered))
    write("assets/%s/blockstates/observer_obsidian.json" % NS, {"variants": observer_variants})
    block_item("observer_obsidian")
    drops_itself("observer_obsidian")
    label("block", "observer_obsidian", "Observateur en obsidienne", "Observer Obsidian")
    defense_removable.append("observer_obsidian")

    # Les spikes : la pyramide du spike en paladium, par metal. Dessus des
    # degres : la texture en carres concentriques (3e argument de BlockSpike).
    for metal, top, side, label_fr, label_en in (
            ("wood", "SpikeWood_2", "SpikeWood_1", "Spike en bois", "Wood Spike"),
            ("iron", "SpikeIron_1", "SpikeIron_2", "Spike en fer", "Iron Spike"),
            ("gold", "SpikeGold_2", "SpikeGold_1", "Spike en or", "Gold Spike"),
            ("diamond", "SpikeDiamond_2", "SpikeDiamond_1", "Spike en diamant", "Diamond Spike"),
            ("amethyst", "SpikeAmethyst_1", "SpikeAmethyst_2", "Spike en amethyste", "Amethyst Spike"),
            ("titane", "SpikeTitane_1", "SpikeTitane_2", "Spike en titane", "Titane Spike")):
        name = "%s_spike" % metal
        tex("blocks/spike/%s.png" % top, "block/%s_top.png" % name)
        tex("blocks/spike/%s.png" % side, "block/%s_side.png" % name)
        write("assets/%s/models/block/%s.json" % (NS, name), {
            "parent": "%s:block/paladium_spike" % NS,
            "textures": {"particle": "%s:block/%s_side" % (NS, name),
                         "top": "%s:block/%s_top" % (NS, name), "side": "%s:block/%s_side" % (NS, name)}})
        write("assets/%s/blockstates/%s.json" % (NS, name), {"variants": {
            "facing=%s" % facing: dict({"model": "%s:block/%s" % (NS, name)}, **rotation)
            for facing, rotation in (("up", {}), ("down", {"x": 180}), ("north", {"x": 90}),
                                     ("south", {"x": 90, "y": 180}), ("east", {"x": 90, "y": 90}),
                                     ("west", {"x": 90, "y": 270}))}})
        block_item(name)
        drops_itself(name)
        label("block", name, label_fr, label_en)
        if metal == "wood":
            tag("minecraft/tags/block/mineable/axe.json", ["%s:%s" % (NS, name)])
        else:
            tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, name)])
            tag("minecraft/tags/block/needs_iron_tool.json", ["%s:%s" % (NS, name)])

    # Les enclumes : le modele de l'enclume vanilla, corps et dessus de PalaMod.
    for metal, label_fr, label_en in (("amethyst", "Enclume en amethyste", "Amethyst Anvil"),
                                      ("titane", "Enclume en titane", "Titane Anvil"),
                                      ("paladium", "Enclume en paladium", "Paladium Anvil")):
        name = "%s_anvil" % metal
        tex("blocks/anvil/anvil_%s_tool_base.png" % metal, "block/%s_body.png" % name)
        tex("blocks/anvil/anvil_%s_tool_top.png" % metal, "block/%s_top.png" % name)
        cube_model(name, {"particle": "%s:block/%s_body" % (NS, name), "body": "%s:block/%s_body" % (NS, name),
                          "top": "%s:block/%s_top" % (NS, name)}, "minecraft:block/template_anvil")
        variants = {}
        for facing, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
            for hits in range(3):
                entry = {"model": "%s:block/%s" % (NS, name)}
                if y:
                    entry["y"] = y
                variants["facing=%s,hits=%d" % (facing, hits)] = entry
        write("assets/%s/blockstates/%s.json" % (NS, name), {"variants": variants})
        block_item(name)
        drops_itself(name)
        label("block", name, label_fr, label_en)
        defense_removable.append(name)

    # Eponges et brouilleur.
    tex("blocks/lava_sponge.png", "block/lava_sponge.png")
    tex("blocks/lava_sponge_wet.png", "block/wet_lava_sponge.png")
    # L'eponge compressee n'existe qu'en 32 x 32, dans le pack comme dans le jar.
    tex("blocks/pillage/compressed_sponge.png", "block/compressed_sponge.png", (32, 32))
    tex("blocks/pillage/coordinate_jammer.png", "block/coordinate_jammer.png")
    for name, label_fr, label_en in (("lava_sponge", "Eponge a lave", "Lava Sponge"),
                                     ("wet_lava_sponge", "Eponge a lave pleine", "Wet Lava Sponge"),
                                     ("compressed_sponge", "Eponge compressee", "Compressed Sponge"),
                                     ("coordinate_jammer", "Brouilleur de coordonnees", "Coordinate Jammer")):
        simple_block(name, label_fr, label_en, {"all": "%s:block/%s" % (NS, name)})
    for name in ("lava_sponge", "wet_lava_sponge", "compressed_sponge"):
        tag("minecraft/tags/block/mineable/hoe.json", ["%s:%s" % (NS, name)])
    tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:coordinate_jammer" % NS])
    tag("minecraft/tags/block/needs_stone_tool.json", ["%s:coordinate_jammer" % NS])

    tag("%s/tags/block/magical_tool_removable.json" % NS, ["%s:%s" % (NS, name) for name in defense_removable])
    # Comme l'obsidienne : le dragon ne les traverse pas.
    tag("minecraft/tags/block/dragon_immune.json", ["%s:%s" % (NS, name) for name in defense_removable])

    # La Fake Water : les textures animees de l'eau sulfurique de PalaMod.
    for source, target in (("blocks/fluids/sulfuricwater_still", "block/fluid/fake_water_still"),
                           ("blocks/fluids/sulfuricwater_flowing", "block/fluid/fake_water_flow")):
        found = False
        for origin in (root[0], root[1]):
            if not origin:
                continue
            base = os.path.join(origin, "assets", NS, "textures", source)
            if os.path.exists(base + ".png"):
                destination = os.path.join(RES, "assets", NS, "textures", target)
                os.makedirs(os.path.dirname(destination), exist_ok=True)
                shutil.copyfile(base + ".png", destination + ".png")
                if os.path.exists(base + ".png.mcmeta"):
                    shutil.copyfile(base + ".png.mcmeta", destination + ".png.mcmeta")
                found = True
                break
        if not found:
            missing.append(source + ".png")
    write("assets/%s/models/block/fake_water.json" % NS,
          {"textures": {"particle": "%s:block/fluid/fake_water_still" % NS}})
    write("assets/%s/blockstates/fake_water.json" % NS, {"variants": {"": {"model": "%s:block/fake_water" % NS}}})
    write("data/%s/loot_table/blocks/fake_water.json" % NS, {"type": "minecraft:block", "pools": []})
    label("block", "fake_water", "Fake Water", "Fake Water")

    # Les objets.
    for name, texture, label_fr, label_en in (
            ("fake_water_bucket", "items/fluids/bucket_sulfuric.png", "Seau de Fake Water", "Fake Water Bucket"),
            ("aggro_stone", "items/pillage/effects/agro_stone.png", "Pierre d'aggro", "Aggro Stone"),
            ("wither_skull_fragment", "items/WitherSkullFragment.png", "Fragment de crane de wither",
             "Wither Skull Fragment"),
            ("voyance_amulet", "items/voyance_amulet.png", "Amulette de voyance", "Voyance Amulet"),
            ("discretion_amulet", "items/discretion_amulet.png", "Amulette de discretion", "Discretion Amulet"),
            ("combat_amulet", "items/combat_amulet.png", "Amulette de combat", "Combat Amulet"),
            ("magma_amulet", "items/magma_amulet.png", "Amulette de magma", "Magma Amulet"),
            ("hunter_amulet", "items/hunter_amulet.png", "Amulette du chasseur", "Hunter Amulet")):
        tex(texture, "item/%s.png" % name)
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, label_fr, label_en)
    tex("items/magical_tool.png", "item/magical_tool.png")
    write("assets/%s/models/item/magical_tool.json" % NS,
          {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/magical_tool" % NS}})
    write("assets/%s/items/magical_tool.json" % NS,
          {"model": {"type": "minecraft:model", "model": "%s:item/magical_tool" % NS}})
    label("item", "magical_tool", "Outil magique", "Magical Tool")
    # Le home remover : la pierre de foyer du home finder, passee au rouge.
    tinted("items/pillage/home_finder/housing_stone.png", "item/home_remover.png", (214, 58, 44))
    flat_item("home_remover", "%s:item/home_remover" % NS)
    label("item", "home_remover", "Home remover", "Home Remover")

    # Le pistolet a liquide : le ModelPistolTank de PalaMod (Blockbench, 64 x 64),
    # converti en elements. x est inverse, comme pour la boite a golem ; les deux
    # pieces inclinees de 32,5 degres passent a 22,5, le pas des modeles.
    def pistol_uv(u, v, w, h, d):
        def rect(x0, y0, x1, y1):
            return [x0 / 4.0, y0 / 4.0, x1 / 4.0, y1 / 4.0]
        return {"up": rect(u + d, v, u + d + w, v + d), "down": rect(u + d + w, v, u + d + 2 * w, v + d),
                "west": rect(u + d + w, v + d, u + 2 * d + w, v + d + h), "east": rect(u, v + d, u + d, v + d + h),
                "north": rect(u + d, v + d, u + d + w, v + d + h),
                "south": rect(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)}

    def pistol_box(u, v, x, y, z, w, h, d, pivot_y, rotation=None, pivot_x=0.0):
        # Repere du modele : y vers le bas, origine du groupe a pivot_y.
        x0, x1 = 7.0 - (pivot_x + x + w), 7.0 - (pivot_x + x)
        y0, y1 = 24.0 - (pivot_y + y + h), 24.0 - (pivot_y + y)
        z0, z1 = 8.0 + z, 8.0 + z + d
        element = {"from": [x0, y0, z0], "to": [x1, y1, z1],
                   "faces": {face: {"texture": "#gun", "uv": uv} for face, uv in pistol_uv(u, v, w, h, d).items()}}
        if rotation is not None:
            element["rotation"] = {"origin": [7.0 - pivot_x, 24.0 - pivot_y, 8.0], "axis": "z", "angle": rotation}
        return element

    pistol_elements = [
        pistol_box(0, 0, -3, -12, -2, 10, 3, 4, 24),
        pistol_box(16, 7, -10, -11, -1, 7, 2, 2, 24),
        pistol_box(0, 7, -11, -12, -2, 6, 4, 4, 24),
        pistol_box(10, 15, -2, -9, -1, 7, 2, 2, 24),
        pistol_box(20, 11, 7, -11, -1, 1, 1, 2, 24),
        pistol_box(14, 22, 8, -12, -2, 1, 3, 4, 24),
        pistol_box(14, 19, -4, -13, -1, 7, 1, 2, 24),
        pistol_box(24, 22, 0, -3, -1, 1, 3, 2, 19, 22.5),
        pistol_box(0, 15, -3, -3, -2, 3, 9, 4, 17, -22.5, -1.0),
    ]
    pistol_display = {
        "gui": {"rotation": [0, 0, 0], "translation": [0, 0.5, 0], "scale": [0.62, 0.62, 0.62]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
        "fixed": {"rotation": [0, 180, 0], "scale": [0.6, 0.6, 0.6]},
        "thirdperson_righthand": {"rotation": [-180, -90, 90], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
        "thirdperson_lefthand": {"rotation": [-180, 90, -90], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
        "firstperson_righthand": {"rotation": [0, -85, 0], "translation": [1, 2.5, 0], "scale": [0.38, 0.38, 0.38]},
        "firstperson_lefthand": {"rotation": [0, 95, 0], "translation": [1, 2.5, 0], "scale": [0.38, 0.38, 0.38]},
    }
    for variant, source in (("", "pistol_tank_empty"), ("_water", "pistol_tank_water"),
                            ("_lava", "pistol_tank_lava"), ("_fluid", "pistol_tank")):
        tex("models/%s.png" % source, "item/pistol_tank%s.png" % (variant or "_empty"), (64, 64))
        write("assets/%s/models/item/pistol_tank%s.json" % (NS, variant), {
            "texture_size": [64, 64],
            "textures": {"gun": "%s:item/pistol_tank%s" % (NS, variant or "_empty"),
                         "particle": "%s:item/pistol_tank%s" % (NS, variant or "_empty")},
            "elements": pistol_elements, "display": pistol_display})
    write("assets/%s/items/pistol_tank.json" % NS, {"model": {
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
        "cases": [{"when": look, "model": {"type": "minecraft:model", "model": "%s:item/pistol_tank_%s" % (NS, look)}}
                  for look in ("water", "lava", "fluid")],
        "fallback": {"type": "minecraft:model", "model": "%s:item/pistol_tank" % NS}}})
    label("item", "pistol_tank", "Pistolet a liquide", "Liquid Pistol")

    for key, label_fr, label_en in (
            ("pistol_tank.empty", "Reservoir vide (%s seaux) - clic droit sur une source pour pomper",
             "Empty tank (%s buckets) - right-click a source to pump"),
            ("pistol_tank.load", "Reservoir : %s / %s seaux de %s", "Tank: %s / %s buckets of %s"),
            ("amulet.combine", "Se fond dans un plastron en paladium ou en endium, a l'enclume",
             "Combine with a paladium or endium chestplate in an anvil"),
            ("amulet.hunter", "+25 % d'XP de Chasseur", "+25% Hunter XP"),
            ("amulet.voyance", "Les monstres a 24 blocs brillent", "Monsters within 24 blocks glow"),
            ("amulet.discretion", "Les monstres te voient deux fois moins loin", "Monsters see you at half range"),
            ("amulet.combat", "+15 % de degats contre les monstres", "+15% damage against monsters"),
            ("amulet.magma", "Resistance au feu permanente", "Permanent fire resistance")):
        label("tooltip", key, label_fr, label_en)
    # La touche du menu de faction (lot 22a) vit dans ce fichier de langue :
    # la generer ici evite qu'une regeneration l'efface.
    fr["key.dohy.faction"] = "Faction (menu)"
    en["key.dohy.faction"] = "Faction (menu)"
    label("message", "home_remover", "Repaires effaces : %s monstre(s). Aucun ne naitra ici pendant une heure.",
          "Lairs cleared: %s monster(s). None will spawn here for an hour.")

    # Les recettes que palamod_crafts.json n'a pas : celles des obsidiennes
    # (ObsiEffect.registerRecipes, chacune dans sa classe), du pillage
    # (PPRegisterer) et des amulettes (hunter.init.Crafts).
    def components(item, potion):
        return {"neoforge:ingredient_type": "neoforge:components", "items": item,
                "components": {"minecraft:potion_contents": {"potion": potion}}, "strict": False}

    shapeless("hardened_obsidian", ["minecraft:obsidian"] * 4, "%s:hardened_obsidian" % NS, 4)
    # Les trois potions jetables de poison (metas 16388, 16420, 16452).
    for suffix, potion in (("", "minecraft:poison"), ("_strong", "minecraft:strong_poison"),
                           ("_long", "minecraft:long_poison")):
        shaped("poison_obsidian%s" % suffix, ["XXX", "XYX", "XXX"],
               {"X": "minecraft:obsidian", "Y": components("minecraft:splash_potion", potion)},
               "%s:poison_obsidian" % NS, 8)
    for name, extra in (("boom_obsidian", "minecraft:gunpowder"), ("lava_obsidian", "minecraft:lava_bucket"),
                        ("sulfuric_obsidian", "%s:fake_water_bucket" % NS),
                        ("redstone_obsidian", "minecraft:redstone")):
        shapeless(name, [extra, "minecraft:obsidian"], "%s:%s" % (NS, name))
        shapeless("%s_from_hardened" % name, [extra, "%s:hardened_obsidian" % NS], "%s:%s" % (NS, name))
    shaped("mega_boom_obsidian", [" G ", "TOT", " T "],
           {"G": "minecraft:gunpowder", "T": "minecraft:tnt", "O": "minecraft:obsidian"}, "%s:mega_boom_obsidian" % NS)
    shaped("knockback_obsidian", ["OOO", "OKO", "OOO"],
           {"O": "minecraft:obsidian", "K": "%s:knockback_orb" % NS}, "%s:knockback_obsidian" % NS, 4)
    shaped("frozen_obsidian", ["IOI", "OIO", "IOI"],
           {"I": "minecraft:packed_ice", "O": "minecraft:obsidian"}, "%s:frozen_obsidian" % NS, 2)
    shaped("anti_aggro_obsidian", ["XXX", "XNX", "XXX"],
           {"X": "minecraft:obsidian", "N": "minecraft:nether_star"}, "%s:anti_aggro_obsidian" % NS, 8)
    shaped("big_obsidian", ["XXX", "XYX", "XXX"],
           {"X": "minecraft:obsidian", "Y": "%s:paladium_core" % NS}, "%s:big_obsidian" % NS, 4)
    shaped("big_obsidian_from_hardened", ["XXX", "XYX", "XXX"],
           {"X": "%s:hardened_obsidian" % NS, "Y": "%s:paladium_core" % NS}, "%s:big_obsidian" % NS, 4)
    # Le coffre en fer d'Iron Chests n'est pas porte : un coffre vanilla le remplace.
    shaped("wither_obsidian", ["XXX", "YXY", "XXX"],
           {"X": "minecraft:obsidian", "Y": "minecraft:chest"}, "%s:wither_obsidian" % NS, 2)
    shaped("wither_obsidian_from_hardened", ["XXX", "YXY", "XXX"],
           {"X": "%s:hardened_obsidian" % NS, "Y": "minecraft:chest"}, "%s:wither_obsidian" % NS, 2)
    # RecipesSlimeObsidian : une obsidienne au milieu de huit slimepads.
    shaped("slime_obsidian", ["SSS", "SOS", "SSS"],
           {"S": "%s:slimepad" % NS, "O": "minecraft:obsidian"}, "%s:slime_obsidian" % NS, 2)
    # Sans recette dans PalaMod : un observateur sur de l'obsidienne renforcee.
    shapeless("observer_obsidian", ["minecraft:observer", "%s:hardened_obsidian" % NS],
              "%s:observer_obsidian" % NS)
    for metal, _index, _fr, _en in SPIKE_METALS:
        shapeless("%s_spike_obsidian" % metal, ["minecraft:obsidian", "%s:%s_spike" % (NS, metal)],
                  "%s:%s_spike_obsidian" % (NS, metal))
    shaped("compressed_sponge", ["XXX", "XYX", "XXX"],
           {"X": "minecraft:sponge", "Y": "%s:paladium_core" % NS}, "%s:compressed_sponge" % NS)
    shaped("coordinate_jammer", ["ZXZ", "XYX", "ZXZ"],
           {"Z": "%s:paladium_block" % NS, "X": "%s:findium" % NS, "Y": "%s:unclaim_finder_red" % NS},
           "%s:coordinate_jammer" % NS)
    shaped("fake_water_bucket", ["Y", "X"],
           {"Y": "%s:paladium_stick" % NS, "X": "minecraft:water_bucket"}, "%s:fake_water_bucket" % NS)
    shaped("aggro_stone", ["XXX", "YZY", "ZZZ"],
           {"X": "%s:paladium_ingot" % NS, "Y": "%s:wither_skull_fragment" % NS,
            "Z": "%s:orangeblue_seeds" % NS}, "%s:aggro_stone" % NS)
    shapeless("wither_skull_fragment", ["minecraft:wither_skeleton_skull"], "%s:wither_skull_fragment" % NS, 9)
    shaped("wither_skeleton_skull_from_fragments", ["YYY", "YYY", "YYY"],
           {"Y": "%s:wither_skull_fragment" % NS}, "minecraft:wither_skeleton_skull")
    shaped("hunter_amulet", ["BNB", " S ", " P "],
           {"B": "minecraft:blaze_powder", "N": "minecraft:nether_star", "S": "%s:diamond_string" % NS,
            "P": "%s:paladium_stick" % NS}, "%s:hunter_amulet" % NS)
    shaped("voyance_amulet", ["ABA", "CDC", "EFE"],
           {"A": "%s:harpagophytum_flower" % NS, "B": "%s:speed_orb" % NS, "C": "%s:heal_orb" % NS,
            "D": "%s:voidstone" % NS, "E": "%s:clathrusarcheri_flower" % NS, "F": "%s:strength_orb" % NS},
           "%s:voyance_amulet" % NS)
    shaped("magma_amulet", ["MFM", "PCP", "MPM"],
           {"M": "minecraft:magma_cream", "F": "%s:findium" % NS,
            "P": components("minecraft:potion", "minecraft:long_fire_resistance"),
            "C": "%s:paladium_core" % NS}, "%s:magma_amulet" % NS)
    # Desactivees sur Paladium, sans recette : la meme forme que l'amulette du chasseur.
    shaped("discretion_amulet", ["BNB", " S ", " P "],
           {"B": "minecraft:phantom_membrane", "N": "minecraft:ink_sac", "S": "minecraft:string",
            "P": "%s:paladium_stick" % NS}, "%s:discretion_amulet" % NS)
    shaped("combat_amulet", ["BNB", " S ", " P "],
           {"B": "%s:strength_orb" % NS, "N": "%s:paladium_core" % NS, "S": "minecraft:string",
            "P": "%s:paladium_stick" % NS}, "%s:combat_amulet" % NS)
    shaped("home_remover", ["OPO", "PEP", "OPO"],
           {"O": "minecraft:obsidian", "P": "%s:paladium_ingot" % NS, "E": "minecraft:ender_eye"},
           "%s:home_remover" % NS)
    # L'eponge a lave pleine se vide au four.
    write("data/%s/recipe/lava_sponge_from_smelting.json" % NS, {
        "type": "minecraft:smelting", "category": "blocks", "cookingtime": 200, "experience": 0.15,
        "ingredient": "%s:wet_lava_sponge" % NS, "result": {"id": "%s:lava_sponge" % NS}})

    # ---- pillage de faction (chapitre 22, lot 22d) ----
    #
    # Dynamites, TNT du pillage, detecteurs, explorateur de coffre, detecteur
    # de presence, seau extrapole, arc en paladium et ses modificateurs, fleche
    # de Switch, cagoule, ficelle de diamant, deux enchantements. Textures : le
    # pack, le jar pour ce que le pack n'a pas (le detecteur de presence, les
    # modificateurs d'arc, l'icone de la cagoule).
    for name, texture, label_fr, label_en in (
            ("dynamite", "items/explosive/dynamite.png", "Dynamite", "Dynamite"),
            ("ninja_dynamite", "items/explosive/dynamite_ninja.png", "Ninja dynamite", "Ninja Dynamite"),
            ("big_dynamite", "items/explosive/dynamite_big.png", "Big dynamite", "Big Dynamite"),
            ("unclaim_finder", "items/unclaimfinder.png", "Unclaim Finder vert", "Green Unclaim Finder"),
            ("unclaim_finder_orange", "items/unclaimfinder_orange.png", "Unclaim Finder orange",
             "Orange Unclaim Finder"),
            ("unclaim_finder_red", "items/unclaimfinder_rouge.png", "Unclaim Finder rouge", "Red Unclaim Finder"),
            ("unclaim_finder_blue", "items/unclaimfinder_bleu.png", "Unclaim Finder bleu", "Blue Unclaim Finder"),
            ("spawner_finder", "items/spawner_finder.png", "Spawner Finder", "Spawner Finder"),
            ("chest_explorer", "items/chestexplorer.png", "Explorateur de coffre", "Chest Explorer"),
            ("extrapolated_bucket", "items/extrapolatedbucket.png", "Seau extrapole", "Extrapolated Bucket"),
            ("switch_arrow", "items/arrow/arrow_switch.png", "Fleche de Switch", "Switch Arrow"),
            ("empty_bow_modifier", "items/machine/emptyBowModifier.png", "Modificateur d'arc vide",
             "Empty Bow Modifier"),
            ("range_bow_modifier", "items/machine/bowRangeModifier.png", "Modificateur Range", "Range Modifier"),
            ("speed_bow_modifier", "items/machine/bowSpeedModifier.png", "Modificateur Speed", "Speed Modifier"),
            ("hood_helmet", "items/armor/travel_hood.png", "Cagoule", "Hood"),
            # Le reste de l'armure de voyage (ItemArmorTravel) : voir plus bas.
            ("travel_boots", "items/armor/travel_boots.png", "Bottes de voyage", "Travel Boots"),
            ("travel_leggings", "items/armor/travel_leggings.png", "Jambieres de voyage", "Travel Leggings"),
            ("travel_jumpchest", "items/armor/travel_jumpchest.png", "Plastron de saut", "Jump Chestplate"),
            ("travel_slimyhelmet", "items/armor/travel_slimy.png", "Casque en slime", "Slimy Helmet"),
            ("travel_scubahelmet", "items/armor/travel_scuba.png", "Casque de plongee", "Scuba Helmet"),
            ("diamond_string", "items/diamondString.png", "Ficelle de diamant", "Diamond String")):
        tex(texture, "item/%s.png" % name)
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, label_fr, label_en)
    tag("minecraft/tags/item/arrows.json", ["%s:switch_arrow" % NS])
    tag("minecraft/tags/item/head_armor.json", ["%s:hood_helmet" % NS])

    # La cagoule portee : la couche HoodHelmet de PalaMod (64 x 32), casque seul.
    tex("models/HoodHelmet.png", "entity/equipment/humanoid/hood.png", (64, 32))
    write("assets/%s/equipment/hood.json" % NS, {"layers": {"humanoid": [{"texture": "%s:hood" % NS}]}})

    # L'armure de voyage (ItemArmorTravel) : une couche portee par piece, comme
    # getArmorTexture la choisissait. Les bottes et les jambieres du pack sont en
    # 64 x 32 (le jar les a en 128 x 64), les trois autres ne sont que dans le jar.
    for name, source, layer in (
            ("travel_boots", "models/travel_boots.png", "humanoid"),
            ("travel_leggings", "models/travel_leggings.png", "humanoid_leggings"),
            ("travel_jump", "models/JumpArmor.png", "humanoid"),
            ("travel_slimy", "models/Slimy_Helmet.png", "humanoid"),
            ("travel_scuba", "models/ScubaArmor.png", "humanoid")):
        tex(source, "entity/equipment/%s/%s.png" % (layer, name), (64, 32))
        write("assets/%s/equipment/%s.json" % (NS, name),
              {"layers": {layer: [{"texture": "%s:%s" % (NS, name)}]}})
    tag("minecraft/tags/item/foot_armor.json", ["%s:travel_boots" % NS])
    tag("minecraft/tags/item/leg_armor.json", ["%s:travel_leggings" % NS])
    tag("minecraft/tags/item/chest_armor.json", ["%s:travel_jumpchest" % NS])
    tag("minecraft/tags/item/head_armor.json", ["%s:travel_slimyhelmet" % NS, "%s:travel_scubahelmet" % NS])

    # L'arc en paladium : le modele de l'arc vanilla et ses trois etapes de tension.
    for suffix, source in (("", "paladium_bow"), ("_pulling_0", "paladium_bow_0"),
                           ("_pulling_1", "paladium_bow_1"), ("_pulling_2", "paladium_bow_2")):
        tex("items/weapons/%s.png" % source, "item/paladium_bow%s.png" % suffix)
        write("assets/%s/models/item/paladium_bow%s.json" % (NS, suffix),
              {"parent": "minecraft:item/bow", "textures": {"layer0": "%s:item/paladium_bow%s" % (NS, suffix)}})
    write("assets/%s/items/paladium_bow.json" % NS, {"model": {
        "type": "minecraft:condition", "property": "minecraft:using_item",
        "on_false": {"type": "minecraft:model", "model": "%s:item/paladium_bow" % NS},
        "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
                    "entries": [{"threshold": 0.65, "model": {"type": "minecraft:model",
                                                              "model": "%s:item/paladium_bow_pulling_1" % NS}},
                                {"threshold": 0.9, "model": {"type": "minecraft:model",
                                                             "model": "%s:item/paladium_bow_pulling_2" % NS}}],
                    "fallback": {"type": "minecraft:model", "model": "%s:item/paladium_bow_pulling_0" % NS}}}})
    label("item", "paladium_bow", "Arc en paladium", "Paladium Bow")
    tag("minecraft/tags/item/enchantable/bow.json", ["%s:paladium_bow" % NS])
    tag("minecraft/tags/item/enchantable/durability.json", ["%s:paladium_bow" % NS])

    # Les TNT : dessus, cote et dessous, comme la TNT vanilla.
    for name, label_fr, label_en in (("big_tnt", "TNT compacte", "Big TNT"),
                                     ("endium_tnt", "TNT en endium", "Endium TNT"),
                                     ("sponge_tnt", "TNT eponge", "Sponge TNT"),
                                     ("effect_tnt", "TNT magique", "Effect TNT"),
                                     ("wither_tnt", "TNT a wither", "Wither TNT")):
        for face in ("top", "side", "bottom"):
            tex("blocks/pillage/effects/%s_%s.png" % (name, face), "block/%s_%s.png" % (name, face))
        simple_block(name, label_fr, label_en, {face: "%s:block/%s_%s" % (NS, name, face)
                                               for face in ("top", "side", "bottom")},
                     "minecraft:block/cube_bottom_top")

    # Le detecteur de presence : rouge au repos, vert quand un monstre rode.
    tex("blocks/machines/onlinedetector_off.png", "block/online_detector.png")
    tex("blocks/machines/onlinedetector_on.png", "block/online_detector_on.png")
    cube_model("online_detector", {"all": "%s:block/online_detector" % NS})
    cube_model("online_detector_on", {"all": "%s:block/online_detector_on" % NS})
    write("assets/%s/blockstates/online_detector.json" % NS, {"variants": {
        "lit=false": {"model": "%s:block/online_detector" % NS},
        "lit=true": {"model": "%s:block/online_detector_on" % NS}}})
    block_item("online_detector")
    drops_itself("online_detector")
    label("block", "online_detector", "Detecteur de presence", "Presence Detector")
    tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:online_detector" % NS])
    tag("minecraft/tags/block/needs_stone_tool.json", ["%s:online_detector" % NS])

    # Ce que la TNT en endium perce : l'obsidienne, ses blocs, les obsidiennes
    # de defense. Pas les enclumes : elles encaissent les coups de wither.
    tag("%s/tags/block/endium_tnt_breakable.json" % NS,
        ["minecraft:obsidian", "minecraft:crying_obsidian"]
        + ["%s:%s" % (NS, name) for name in ("obsidian_stairs", "obsidian_slab", "obsidian_door",
                                             "obsidian_trapdoor")]
        + ["%s:%s" % (NS, name) for name in defense_removable if not name.endswith("_anvil")])

    # Les deux enchantements du pillage, a la table de l'Alchimiste seulement.
    write("data/%s/enchantment/unclaim_helmet.json" % NS, {
        "description": {"translate": "enchantment.%s.unclaim_helmet" % NS},
        "supported_items": "#minecraft:enchantable/head_armor",
        "weight": 1, "max_level": 1,
        "min_cost": {"base": 30, "per_level_above_first": 0},
        "max_cost": {"base": 50, "per_level_above_first": 0},
        "anvil_cost": 4, "slots": ["head"]})
    write("data/%s/enchantment/fake_water_protection.json" % NS, {
        "description": {"translate": "enchantment.%s.fake_water_protection" % NS},
        "supported_items": "#minecraft:enchantable/armor",
        "weight": 1, "max_level": 1,
        "min_cost": {"base": 30, "per_level_above_first": 0},
        "max_cost": {"base": 50, "per_level_above_first": 0},
        "anvil_cost": 4, "slots": ["armor"]})
    label("enchantment", "unclaim_helmet", "Unclaim Helmet", "Unclaim Helmet")
    label("enchantment", "unclaim_helmet.desc", "Le casque porte fait office d'Unclaim Finder vert",
          "The worn helmet works as a green Unclaim Finder")
    label("enchantment", "fake_water_protection", "Fake Water Protection", "Fake Water Protection")
    label("enchantment", "fake_water_protection.desc", "La Fake Water ne ronge plus",
          "Fake Water no longer hurts")
    label("message", "mystic_enchant.locked", "Verrouille par l'arbre de faction",
          "Locked by the faction tech tree")

    label("entity", "dynamite", "Dynamite", "Dynamite")
    label("entity", "primed_tnt", "TNT allumee", "Primed TNT")
    for key, label_fr, label_en in (
            ("dynamite", "Clic droit : la lancer. Elle se colle et saute deux secondes plus tard",
             "Right-click to throw. It sticks and blows two seconds later"),
            ("ninja_dynamite", "Plus forte que la dynamite, et sans un bruit", "Stronger than dynamite, and silent"),
            ("big_dynamite", "Presque la force d'une TNT", "Almost as strong as TNT"),
            ("unclaim_finder", "En main : les conteneurs de ton chunk", "In hand: containers in your chunk"),
            ("unclaim_finder_orange", "En main : les conteneurs des 9 chunks autour de toi",
             "In hand: containers in the 9 chunks around you"),
            ("unclaim_finder_red", "En main : les conteneurs des 25 chunks autour de toi",
             "In hand: containers in the 25 chunks around you"),
            ("unclaim_finder_blue", "En main : les conteneurs des 49 chunks, et la direction du plus proche",
             "In hand: containers in 49 chunks, and the way to the nearest"),
            ("spawner_finder", "En main : les spawners des 9 chunks autour de toi",
             "In hand: spawners in the 9 chunks around you"),
            ("chest_explorer", "Accroupi, clic droit sur un conteneur : son contenu, sans l'ouvrir",
             "Sneak and right-click a container: see inside without opening it"),
            ("extrapolated_bucket", "Clic droit sur un liquide : toute la nappe disparait",
             "Right-click a liquid: the whole pool disappears"),
            ("switch_arrow", "Tiree a l'arc en paladium : tu echanges ta place avec la cible",
             "Shot from a paladium bow: you swap places with the target"),
            ("empty_bow_modifier", "Avec une potion de force (Range) ou de rapidite longue (Speed)",
             "With a strength potion (Range) or a long swiftness potion (Speed)"),
            ("range_bow_modifier", "A l'enclume sur un arc en paladium : des fleches plus rapides",
             "Anvil it onto a paladium bow: faster arrows"),
            ("speed_bow_modifier", "A l'enclume sur un arc en paladium : il se bande plus vite",
             "Anvil it onto a paladium bow: it draws faster"),
            ("hood_helmet", "Une fois debloquee : les monstres te reperent de moins loin",
             "Once unlocked: monsters spot you from closer"),
            ("paladium_bow.modifiers", "Modificateurs : %s / %s", "Modifiers: %s / %s")):
        label("tooltip", key, label_fr, label_en)

    # Les recettes que palamod_crafts.json n'a pas : les TNT (chacune dans sa
    # classe, ObjectEffect.registerRecipes), les modificateurs d'arc (sans
    # forme, a potion), la fleche de Switch. Les finders orange, rouge et bleu
    # sortent de la palamachine (PalamachineRecipes).
    shaped("big_tnt", ["XXX", "XYX", "XXX"], {"X": "minecraft:tnt", "Y": "%s:paladium_core" % NS},
           "%s:big_tnt" % NS, 3)
    shaped("endium_tnt", ["XXX", "XYX", "XXX"], {"X": "minecraft:tnt", "Y": "%s:endium_nugget" % NS},
           "%s:endium_tnt" % NS, 4)
    shaped("sponge_tnt", ["XXX", "ZYZ", "XXX"],
           {"X": "minecraft:tnt", "Y": "%s:extrapolated_bucket" % NS, "Z": "%s:compressed_sponge" % NS},
           "%s:sponge_tnt" % NS, 2)
    # Toute potion jetable de poison a gauche, de faiblesse a droite (metas
    # 16388, 16420, 16452 et 16424, 16456) : six recettes, comme dans EffectTNT.
    for poison in ("poison", "strong_poison", "long_poison"):
        for weakness in ("weakness", "long_weakness"):
            shaped("effect_tnt_%s_%s" % (poison, weakness), ["XXX", "ZYA", "XXX"],
                   {"X": "minecraft:tnt", "Y": "%s:paladium_core" % NS,
                    "Z": components("minecraft:splash_potion", "minecraft:%s" % poison),
                    "A": components("minecraft:splash_potion", "minecraft:%s" % weakness)},
                   "%s:effect_tnt" % NS, 2)
    shaped("wither_tnt", ["ZZZ", "XYX", "XXX"],
           {"Z": "minecraft:wither_skeleton_skull", "X": "minecraft:tnt", "Y": "%s:paladium_core" % NS},
           "%s:wither_tnt" % NS)
    # Metas 8258 (rapidite longue) et 8201 (force).
    shapeless("speed_bow_modifier", ["%s:empty_bow_modifier" % NS,
                                     components("minecraft:potion", "minecraft:long_swiftness")],
              "%s:speed_bow_modifier" % NS)
    shapeless("range_bow_modifier", ["%s:empty_bow_modifier" % NS,
                                     components("minecraft:potion", "minecraft:strength")],
              "%s:range_bow_modifier" % NS)
    shaped("switch_arrow", [" A ", "APA", " A "], {"A": "minecraft:arrow", "P": "minecraft:ender_pearl"},
           "%s:switch_arrow" % NS, 4)

    # ---- withers du pillage (chapitre 22, lot 22d-bis) ----
    #
    # Le module PalaWither n'est pas dans les jars : ses recettes sont dans
    # PPalaWither.init, ses textures manquent sauf la gemme amusante, la tete
    # passive, la selle, la canne, la fleche de mi-vie et la poudre. Les autres
    # gemmes sont la gemme amusante teintee ; les peaux des withers et les
    # tetes sont redessinees d'apres le wiki (tools/wither_skins.py) ; les
    # ameliorations le
    # modificateur « more upgrade » teinte ; le laser reprend la boussole en
    # paladium du chasseur ; le receptacle est compose (obsidienne, crane de
    # wither, bandes de quartz electrifie).
    from PIL import Image as _Image, ImageEnhance as _Enhance

    def jar_image(relative):
        for base in (root[0], root[1]):
            if base:
                candidate = os.path.join(base, "assets", NS, "textures", relative)
                if os.path.exists(candidate):
                    return _Image.open(candidate).convert("RGBA")
        missing.append(relative)
        return None

    def save_texture(image, destination):
        full = os.path.join(RES, "assets", NS, "textures", destination)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        image.save(full)

    # Les gemmes : la gemme amusante, teintee aux couleurs du wither qu'elle invoque.
    WITHER_GEMS = (("wither_gem", (150, 150, 170), "Gemme de wither", "Wither Gem"),
                   ("baby_wither_gem", (120, 200, 240), "Gemme de baby wither", "Baby Wither Gem"),
                   ("passive_wither_gem", (150, 210, 110), "Gemme de wither passif", "Passive Wither Gem"),
                   ("thirsty_wither_gem", (225, 195, 140), "Gemme de wither assoiffe", "Thirsty Wither Gem"),
                   ("predator_wither_gem", (255, 120, 50), "Gemme de wither predateur", "Predator Wither Gem"),
                   ("supreme_wither_gem", (200, 120, 255), "Gemme de wither supreme", "Supreme Wither Gem"))
    tex("items/wither/funny_wither_gem.png", "item/funny_wither_gem.png")
    flat_item("funny_wither_gem", "%s:item/funny_wither_gem" % NS)
    label("item", "funny_wither_gem", "Gemme de wither amusant", "Funny Wither Gem")
    for name, rgb, label_fr, label_en in WITHER_GEMS:
        tinted("items/wither/funny_wither_gem.png", "item/%s.png" % name, rgb)
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, label_fr, label_en)

    # Les peaux des withers, d'apres les rendus du wiki (tools/wither_skins.py) :
    # peau, yeux lumineux, peau de charge, et l'icone de la tete a crafter.
    vanilla_wither = os.path.join(root[0] or "", "assets", "minecraft", "textures", "entity", "wither")
    if os.path.exists(os.path.join(vanilla_wither, "wither.png")):
        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
        import wither_skins
        wither_skins.generate(os.path.join(vanilla_wither, "wither.png"),
                              os.path.join(vanilla_wither, "wither_invulnerable.png"),
                              os.path.join(RES, "assets", NS, "textures", "entity", "wither"),
                              os.path.join(RES, "assets", NS, "textures", "item"))
    else:
        missing.append("peaux des withers (entity/wither/wither.png du pack)")
    for name, label_fr, label_en in (
            ("passive_wither_head", "Tete de wither passif", "Passive Wither Head"),
            ("thirsty_wither_head", "Tete de wither assoiffe", "Thirsty Wither Head"),
            ("predator_wither_head", "Tete de wither predateur", "Predator Wither Head"),
            ("supreme_wither_head", "Tete de wither supreme", "Supreme Wither Head")):
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, label_fr, label_en)

    for name, rgb, label_fr, label_en in (
            ("stealth_upgrade", (140, 120, 170), "Amelioration de discretion", "Stealth Upgrade"),
            ("wither_resistance_upgrade", (200, 200, 210), "Amelioration de resistance de wither",
             "Wither Resistance Upgrade"),
            ("wither_slime_upgrade", (120, 220, 110), "Amelioration visqueuse de wither", "Wither Slime Upgrade")):
        tinted("items/modifier/moreupgrade.png", "item/%s.png" % name, rgb)
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, label_fr, label_en)

    compass = jar_image("items/compass_paladium.png")
    if compass is not None:
        save_texture(compass.crop((0, 0, 16, 16)), "item/wither_target_laser.png")
    flat_item("wither_target_laser", "%s:item/wither_target_laser" % NS)
    label("item", "wither_target_laser", "Laser de ciblage", "Target Laser")
    for name, texture, label_fr, label_en in (
            ("wither_saddle", "items/pillage/effects/wither_saddle.png", "Selle de wither", "Wither Saddle"),
            ("mid_life_arrow", "items/pillage/effects/mid_life_arrow.png", "Fleche de mi-vie", "Mid-Life Arrow"),
            ("wither_dust", "items/wither_dust.png", "Poudre de wither", "Wither Dust")):
        tex(texture, "item/%s.png" % name)
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, label_fr, label_en)
    tex("items/pillage/effects/wither_fishingrod.png", "item/wither_fishing_rod.png")
    write("assets/%s/models/item/wither_fishing_rod.json" % NS,
          {"parent": "minecraft:item/handheld_rod", "textures": {"layer0": "%s:item/wither_fishing_rod" % NS}})
    write("assets/%s/items/wither_fishing_rod.json" % NS,
          {"model": {"type": "minecraft:model", "model": "%s:item/wither_fishing_rod" % NS}})
    label("item", "wither_fishing_rod", "Canne a peche de wither", "Wither Fishing Rod")
    tag("minecraft/tags/item/arrows.json", ["%s:mid_life_arrow" % NS])

    # Le quartz compresse (premier palier) et le quartz electrifie, anime.
    tex("blocks/pillage/home_finder/compressed_quartz_0.png", "block/compressed_quartz.png")
    simple_block("compressed_quartz", "Quartz compresse", "Compressed Quartz",
                 {"all": "%s:block/compressed_quartz" % NS})
    empowered = jar_image("blocks/pillage/home_finder/empowered_quartz.png")
    if empowered is not None:
        save_texture(empowered, "block/empowered_quartz.png")
        write("assets/%s/textures/block/empowered_quartz.png.mcmeta" % NS,
              {"animation": {"frametime": 2}})
    simple_block("empowered_quartz", "Quartz electrifie", "Empowered Quartz",
                 {"all": "%s:block/empowered_quartz" % NS})
    for name in ("compressed_quartz", "empowered_quartz", "wither_receptacle"):
        tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, name)])
        tag("minecraft/tags/block/needs_stone_tool.json", ["%s:%s" % (NS, name)])

    # Le receptacle : un autel qui suit sa recette (planches en bas, quartz
    # electrifie autour, crane de wither au milieu). Socle de planches de chene
    # noir, dalle d'obsidienne, quatre piliers de quartz electrifie coiffes de
    # quartz compresse, et le crane pose sur un coussinet de quartz, les yeux
    # allumes, tourne vers qui l'a pose (FACING).
    skull_png = os.path.join(root[0] or "", "assets", "minecraft", "textures", "entity", "skeleton",
                             "wither_skeleton.png")
    if os.path.exists(skull_png):
        head = _Image.open(skull_png).convert("RGBA")
        skull = _Image.new("RGBA", (16, 16))
        for box, at in (((8, 8, 16, 16), (0, 0)), ((0, 8, 8, 16), (8, 0)), ((8, 0, 16, 8), (0, 8)),
                        ((24, 8, 32, 16), (8, 8))):
            skull.paste(_Enhance.Brightness(head.crop(box)).enhance(1.7), at)
        for x, y in ((1, 4), (2, 4), (5, 4), (6, 4)):
            skull.putpixel((x, y), (214, 140, 255, 255))
        for x in range(2, 6):
            skull.putpixel((x, 6), (120, 60, 170, 255))
        save_texture(skull, "block/wither_receptacle_skull.png")
    else:
        missing.append("crane du receptacle (entity/skeleton/wither_skeleton.png)")

    def element(origin, to, texture, uvs=None, cull=()):
        faces = {}
        for face in ("north", "south", "east", "west", "up", "down"):
            entry = {"texture": texture}
            if uvs and face in uvs:
                entry["uv"] = uvs[face]
            if face in cull:
                entry["cullface"] = face
            faces[face] = entry
        return {"from": origin, "to": to, "faces": faces}

    receptacle = [element([0, 0, 0], [16, 2, 16], "#planks", cull=("north", "south", "east", "west", "down")),
                  element([1, 2, 1], [15, 4, 15], "#obsidian"),
                  element([5, 4, 5], [11, 5, 11], "#quartz")]
    for x, z in ((1.5, 1.5), (12.5, 1.5), (1.5, 12.5), (12.5, 12.5)):
        receptacle.append(element([x, 4, z], [x + 2, 14, z + 2], "#power"))
        receptacle.append(element([x - 0.5, 14, z - 0.5], [x + 2.5, 16, z + 2.5], "#quartz",
                                  cull=("up",)))
    receptacle.append(element([4, 5, 4], [12, 13, 12], "#skull", {
        "north": [0, 0, 8, 8], "east": [8, 0, 16, 8], "west": [8, 0, 16, 8], "south": [8, 8, 16, 16],
        "up": [0, 8, 8, 16], "down": [0, 8, 8, 16]}))
    write("assets/%s/models/block/wither_receptacle.json" % NS, {
        "parent": "minecraft:block/block",
        "textures": {"particle": "minecraft:block/obsidian", "planks": "minecraft:block/dark_oak_planks",
                     "obsidian": "minecraft:block/obsidian", "quartz": "%s:block/compressed_quartz" % NS,
                     "power": "%s:block/empowered_quartz" % NS, "skull": "%s:block/wither_receptacle_skull" % NS},
        "elements": receptacle})
    write("assets/%s/blockstates/wither_receptacle.json" % NS, {"variants": {
        "facing=%s" % facing: dict({"model": "%s:block/wither_receptacle" % NS}, **rotation)
        for facing, rotation in FACING_ROTATIONS if facing not in ("up", "down")}})
    block_item("wither_receptacle")
    drops_itself("wither_receptacle")
    label("block", "wither_receptacle", "Receptacle a wither", "Wither Receptacle")

    # La peau du wither amusant : 64 x 32 en 1.7.10, le modele de 26.2 en lit 64 x 64.
    funny = jar_image("entity/wither/funny_wither.png")
    if funny is not None:
        padded = _Image.new("RGBA", (64, 64))
        padded.paste(funny, (0, 0))
        save_texture(padded, "entity/wither/funny_wither.png")

    # Ses sons : l'invocation, la naissance, le cri.
    for source, event in (("loop", "funny_wither.loop"), ("spawn", "funny_wither.spawn"),
                          ("invoke", "funny_wither.invoke")):
        origin = os.path.join(root[1] or "", "assets", NS, "sounds", "entities", "funny_wither", source + ".ogg")
        if os.path.exists(origin):
            destination = os.path.join(RES, "assets", NS, "sounds", "funny_wither", source + ".ogg")
            os.makedirs(os.path.dirname(destination), exist_ok=True)
            shutil.copyfile(origin, destination)
        else:
            missing.append("sounds/entities/funny_wither/%s.ogg" % source)
        sounds[event] = {"sounds": ["%s:funny_wither/%s" % (NS, source)]}
    write("assets/%s/sounds.json" % NS, sounds)

    # ---- bases ennemies (chapitre 22, lot 22g) ----
    #
    # Les coffres des bases ennemies generees sur demande : une table par
    # taille, plus le coffre fort de la forteresse. Le butin suit la difficulte
    # (et les outils qu'elle demande) : minerais et explosifs a l'avant-poste,
    # paladium et TNT en endium a la base, endium et gemmes de wither a la
    # forteresse.
    def chest_loot(path, rolls, entries):
        pool_entries = []
        for item, weight, low, high in entries:
            entry = {"type": "minecraft:item", "name": item, "weight": weight}
            if high > 1:
                entry["functions"] = [{"function": "minecraft:set_count",
                                       "count": {"type": "minecraft:uniform", "min": low, "max": high}}]
            pool_entries.append(entry)
        write("data/%s/loot_table/%s.json" % (NS, path), {
            "type": "minecraft:chest",
            "pools": [{"rolls": {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]},
                       "entries": pool_entries}]})

    chest_loot("chests/enemy_avant_poste", (4, 7), [
        ("minecraft:iron_ingot", 20, 2, 7), ("minecraft:gold_ingot", 14, 1, 5),
        ("%s:amethyst_ingot" % NS, 16, 1, 4), ("%s:titane_ingot" % NS, 10, 1, 3),
        ("%s:paladium_ingot" % NS, 5, 1, 2), ("minecraft:tnt", 10, 1, 4), ("%s:dynamite" % NS, 10, 2, 6),
        ("minecraft:ender_pearl", 8, 1, 2), ("minecraft:emerald", 8, 1, 4), ("minecraft:golden_apple", 4, 1, 1)])
    chest_loot("chests/enemy_base", (5, 8), [
        ("%s:amethyst_ingot" % NS, 15, 2, 8), ("%s:titane_ingot" % NS, 15, 2, 6),
        ("%s:paladium_ingot" % NS, 12, 1, 4), ("%s:endium_nugget" % NS, 4, 1, 2), ("minecraft:diamond", 10, 1, 4),
        ("%s:dynamite" % NS, 10, 4, 10), ("%s:big_tnt" % NS, 6, 1, 3), ("%s:endium_tnt" % NS, 4, 1, 2),
        ("minecraft:ender_pearl", 8, 2, 4), ("%s:paladium_apple" % NS, 3, 1, 1),
        ("%s:baby_wither_gem" % NS, 2, 1, 1)])
    chest_loot("chests/enemy_forteresse", (6, 10), [
        ("%s:paladium_ingot" % NS, 15, 2, 8), ("%s:titane_ingot" % NS, 10, 4, 10),
        ("%s:endium_nugget" % NS, 8, 1, 4), ("%s:green_paladium_ingot" % NS, 4, 1, 2),
        ("minecraft:diamond", 10, 2, 6), ("%s:endium_tnt" % NS, 8, 1, 4), ("%s:big_dynamite" % NS, 6, 2, 6),
        ("%s:paladium_apple" % NS, 5, 1, 2), ("%s:wither_gem" % NS, 3, 1, 1),
        ("%s:passive_wither_gem" % NS, 1, 1, 1), ("%s:legendary_stone_random" % NS, 1, 1, 1),
        ("minecraft:nether_star", 1, 1, 1)])
    chest_loot("chests/enemy_coffre_fort", (3, 5), [
        ("%s:endium_ingot" % NS, 6, 1, 2), ("%s:paladium_block" % NS, 6, 1, 2),
        ("%s:green_paladium_ingot" % NS, 6, 1, 4), ("%s:legendary_stone_random" % NS, 3, 1, 1),
        ("%s:predator_wither_gem" % NS, 1, 1, 1)])

    # Les quartiers des bases ennemies (refonte du 2026-11-11) : des tables
    # secondaires, par ce que le quartier abrite. Bien en dessous de la salle
    # forte : c'est ce qu'on ramasse en chemin, pas la recompense.
    chest_loot("chests/enemy_vrac", (2, 4), [
        ("minecraft:cobblestone", 20, 16, 64), ("minecraft:coal", 14, 8, 32), ("minecraft:wheat", 10, 8, 32),
        ("minecraft:bone", 8, 4, 16), ("minecraft:iron_nugget", 10, 8, 32), ("minecraft:gravel", 8, 16, 48),
        ("minecraft:string", 6, 4, 16), ("minecraft:gunpowder", 6, 2, 8), ("minecraft:arrow", 8, 8, 24)])
    chest_loot("chests/enemy_grenier", (3, 5), [
        ("%s:kiwano" % NS, 12, 8, 32), ("%s:eggplant" % NS, 12, 8, 32), ("%s:chervil" % NS, 12, 8, 32),
        ("%s:orangeblue" % NS, 12, 8, 32), ("%s:kiwano_seeds" % NS, 6, 2, 8),
        ("%s:eggplant_seeds" % NS, 6, 2, 8), ("%s:chervil_seeds" % NS, 6, 2, 8),
        ("%s:orangeblue_seeds" % NS, 6, 2, 8), ("%s:xp_berry" % NS, 8, 4, 16),
        ("minecraft:bone_meal", 10, 8, 32), ("minecraft:hay_block", 8, 4, 16),
        ("%s:amethyst_seedplanter" % NS, 1, 1, 1)])
    chest_loot("chests/enemy_entrepot", (3, 6), [
        ("minecraft:iron_ingot", 14, 8, 24), ("minecraft:gold_ingot", 10, 4, 16),
        ("%s:amethyst_ingot" % NS, 14, 4, 12), ("%s:titane_ingot" % NS, 12, 3, 9),
        ("%s:paladium_ingot" % NS, 6, 1, 4), ("minecraft:diamond", 6, 1, 4), ("minecraft:redstone", 8, 8, 32),
        ("minecraft:lapis_lazuli", 8, 8, 24), ("minecraft:obsidian", 8, 8, 24),
        ("%s:compressed_amethyst" % NS, 2, 1, 1), ("%s:compressed_titane" % NS, 1, 1, 1)])
    chest_loot("chests/enemy_atelier", (3, 5), [
        ("%s:iron_particle" % NS, 12, 4, 16), ("%s:gold_particle" % NS, 10, 4, 12),
        ("%s:amethyst_particle" % NS, 10, 2, 8), ("%s:titane_particle" % NS, 8, 2, 6),
        ("%s:paladium_particle" % NS, 4, 1, 4), ("%s:gold_mixed_coal" % NS, 8, 2, 8),
        ("%s:amethyst_mixed_coal" % NS, 6, 2, 6), ("%s:titane_mixed_coal" % NS, 4, 1, 4),
        ("%s:pala_mixed_coal" % NS, 2, 1, 2), ("minecraft:coal_block", 8, 2, 8),
        ("%s:pickaxe_head" % NS, 1, 1, 1), ("%s:sword_head" % NS, 1, 1, 1)])
    chest_loot("chests/enemy_arsenal", (3, 5), [
        ("minecraft:tnt", 14, 2, 8), ("%s:dynamite" % NS, 14, 4, 12), ("%s:big_dynamite" % NS, 6, 2, 6),
        ("%s:big_tnt" % NS, 6, 1, 3), ("%s:endium_tnt" % NS, 2, 1, 2), ("minecraft:arrow", 12, 16, 48),
        ("minecraft:gunpowder", 10, 8, 24), ("minecraft:iron_sword", 6, 1, 1), ("minecraft:crossbow", 6, 1, 1),
        ("minecraft:shield", 5, 1, 1), ("minecraft:golden_apple", 3, 1, 2)])

    # ---- reserve d'or (chapitre 22, lot 22e) ----
    #
    # Les trois blocs de la reserve, d'apres le wiki (« Investissement Reserve
    # d'or ») ; le module n'est pas dans les jars. Le coffre-fort reprend la
    # face du coffre-fort de PalaMod ; la vitre est le verre du pack cerclee
    # d'acier, a demi transparente ; l'or liquide est la lave du pack passee a
    # l'or, avec son animation.
    pack_block = os.path.join(root[0] or "", "assets", "minecraft", "textures", "block")
    safe_png = os.path.join(root[0] or "", "assets", "palamod", "textures", "blocks", "safechest", "safe_side.png")
    if os.path.exists(safe_png):
        save_texture(_Image.open(safe_png).convert("RGBA"), "block/faction_safe.png")
    else:
        missing.append("coffre-fort de faction (blocks/safechest/safe_side.png)")
    glass_png = os.path.join(pack_block, "glass.png")
    if os.path.exists(glass_png):
        glass = _Image.open(glass_png).convert("RGBA")
        vault = _Image.new("RGBA", glass.size)
        size = glass.size[0]
        for x in range(size):
            for y in range(size):
                edge = min(x, y, size - 1 - x, size - 1 - y)
                r, g, b, a = glass.getpixel((x, y))
                if edge == 0:
                    vault.putpixel((x, y), (92, 98, 108, 255))
                elif edge == 1:
                    vault.putpixel((x, y), (148, 156, 168, 255))
                elif a > 0 and (r + g + b) > 500:
                    vault.putpixel((x, y), (236, 244, 252, 200))
                else:
                    vault.putpixel((x, y), (190, 214, 232, 70))
        for i in (2, 5, 8):
            vault.putpixel((min(i + 1, size - 3), size - 3 - i), (255, 230, 150, 150))
        save_texture(vault, "block/vault_glass.png")
    else:
        missing.append("vitre de coffre-fort (block/glass.png)")
    lava_png = os.path.join(pack_block, "lava_still.png")
    if os.path.exists(lava_png):
        lava = _Image.open(lava_png).convert("RGBA")
        gold = _Image.new("RGBA", lava.size)
        stops = [(120, 66, 8), (214, 150, 24), (255, 214, 72), (255, 248, 196)]
        for x in range(lava.size[0]):
            for y in range(lava.size[1]):
                r, g, b, a = lava.getpixel((x, y))
                t = min(1.0, max(0.0, ((0.299 * r + 0.587 * g + 0.114 * b) - 60.0) / 170.0)) * 3.0
                low = min(2, int(t))
                f = t - low
                c = [int(stops[low][k] + (stops[low + 1][k] - stops[low][k]) * f) for k in range(3)]
                gold.putpixel((x, y), (c[0], c[1], c[2], 255))
        save_texture(gold, "block/liquid_gold.png")
        meta = os.path.join(pack_block, "lava_still.png.mcmeta")
        if os.path.exists(meta):
            with open(meta, encoding="utf-8") as handle:
                write("assets/%s/textures/block/liquid_gold.png.mcmeta" % NS, json.load(handle))
    else:
        missing.append("or liquide (block/lava_still.png)")
    for name, label_fr, label_en, tool in (
            ("faction_safe", "Coffre-fort de faction", "Faction Safe", True),
            ("vault_glass", "Vitre de coffre-fort", "Vault Glass", True),
            ("liquid_gold", "Or liquide", "Liquid Gold", False)):
        write("assets/%s/models/block/%s.json" % (NS, name), {
            "parent": "minecraft:block/cube_all", "textures": {"all": "%s:block/%s" % (NS, name)}})
        write("assets/%s/blockstates/%s.json" % (NS, name), {"variants": {"": {"model": "%s:block/%s" % (NS, name)}}})
        block_item(name)
        drops_itself(name)
        label("block", name, label_fr, label_en)
        tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, name)])
        tag("minecraft/tags/block/%s.json" % ("needs_diamond_tool" if tool else "needs_iron_tool"),
            ["%s:%s" % (NS, name)])
    # Recettes du wiki ; l'obsidienne infusee (diamant) n'existe pas ici :
    # l'obsidienne renforcee la remplace.
    shaped("faction_safe", ["TOT", "OTO", "TOT"],
           {"T": "%s:compressed_titane" % NS, "O": "%s:hardened_obsidian" % NS}, "%s:faction_safe" % NS)
    # Des vitres (Blocks.field_150410_aZ), pas du verre : en verre, la grille
    # etait celle du bloc de grotte et l'une masquait l'autre.
    shaped("vault_glass", ["GGG", "GFG", "GGG"],
           {"G": "minecraft:glass_pane", "F": "%s:findium" % NS}, "%s:vault_glass" % NS)
    shaped("liquid_gold", ["QQQ", "QNQ", "QQQ"],
           {"Q": "%s:empowered_quartz" % NS, "N": "%s:endium_nugget" % NS}, "%s:liquid_gold" % NS)

    tag("%s/tags/block/wither_slime_blocks.json" % NS,
        ["minecraft:slime_block", "%s:slimepad" % NS, "%s:slime_obsidian" % NS])

    for name, label_fr, label_en in (("paladium_wither", "Wither", "Wither"),
                                     ("funny_wither", "Wither amusant", "Funny Wither"),
                                     ("baby_wither", "Baby wither", "Baby Wither"),
                                     ("passive_wither", "Wither passif", "Passive Wither"),
                                     ("thirsty_wither", "Wither assoiffe", "Thirsty Wither"),
                                     ("predator_wither", "Wither predateur", "Predator Wither"),
                                     ("supreme_wither", "Wither supreme", "Supreme Wither")):
        label("entity", name, label_fr, label_en)
    for key, label_fr, label_en in (
            ("wither_gem", "Au receptacle : le wither, a combattre", "In a receptacle: the wither, to fight"),
            ("funny_wither_gem", "Au receptacle : le wither amusant, meme force, autre allure",
             "In a receptacle: the funny wither, same strength, new look"),
            ("baby_wither_gem", "Au receptacle : un petit wither, plus fragile et moins fort",
             "In a receptacle: a small, weaker wither"),
            ("passive_wither_gem", "Au receptacle : un wither allie, qui n'attaque que les monstres",
             "In a receptacle: an allied wither that only attacks monsters"),
            ("thirsty_wither_gem", "Allie, insensible aux liquides, perce l'obsidienne ; il faut le pousser",
             "Allied, liquid-proof, breaks obsidian; must be pushed"),
            ("predator_wither_gem", "Allie, va ou le laser de ciblage l'envoie",
             "Allied, goes where the target laser sends it"),
            ("supreme_wither_gem", "Allie, intuable ; appelle des predateurs, saute au bout de 15 min",
             "Allied, unkillable; calls predators, blows up after 15 min"),
            ("wither_gem.time", "Invocation : %s", "Summoning: %s"),
            ("stealth_upgrade", "Sur ton wither : ni barre de vie ni bruit, une heure",
             "On your wither: no health bar, no sound, for an hour"),
            ("wither_resistance_upgrade", "Sur ton wither : degats hors joueurs divises par deux, une heure",
             "On your wither: non-player damage halved, for an hour"),
            ("wither_slime_upgrade", "Sur ton wither : il mange le slime, +20 PV par bloc, une heure",
             "On your wither: it eats slime, +20 HP per block, for an hour"),
            ("wither_target_laser", "Clic droit dans le vide : viser. Clic droit sur un predateur ou un supreme : l'y envoyer",
             "Right-click air to aim. Right-click a predator or supreme to send it"),
            ("wither_target_laser.target", "Cible : %s %s %s", "Target: %s %s %s"),
            ("wither_saddle", "Clic droit sur un wither pour le monter (une fois)",
             "Right-click a wither to ride it (once)"),
            ("wither_fishing_rod", "Sur un wither selle : il suit ton regard", "On a saddled wither: it follows your gaze"),
            ("mid_life_arrow", "Tiree a l'arc en paladium : un wither perd la moitie de sa vie",
             "Shot from a paladium bow: a wither loses half its health"),
            ("wither_dust", "Lachee par les sorcieres (Chasseur 20)", "Dropped by witches (Hunter 20)"),
            ("wither_receptacle", "Une gemme de wither en main, clic droit : l'invocation commence",
             "Right-click with a wither gem to start summoning")):
        label("tooltip", key, label_fr, label_en)

    # Les recettes de PPalaWither.init, et celles que le wiki ne donnait qu'en image.
    skull = "minecraft:wither_skeleton_skull"
    shaped("nether_star_from_fragments", ["XXX", "YYY", "XXX"],
           {"X": "%s:wither_skull_fragment" % NS, "Y": skull}, "minecraft:nether_star")
    shaped("wither_receptacle", [" X ", "XYX", " Z "],
           {"X": "%s:empowered_quartz" % NS, "Y": skull, "Z": "#minecraft:planks"}, "%s:wither_receptacle" % NS)
    # Le wither de base : la forme du wither vanilla, qui donne sa gemme au lieu du wither.
    shaped("wither_gem", ["SSS", "BBB", " B "], {"S": skull, "B": "minecraft:soul_sand"}, "%s:wither_gem" % NS)
    shaped("funny_wither_gem", [" Y ", "YWY", " Y "],
           {"Y": "minecraft:yellow_dye", "W": "%s:wither_gem" % NS}, "%s:funny_wither_gem" % NS)
    shaped("baby_wither_gem", [" X ", "XYX", " X "], {"X": "minecraft:egg", "Y": skull}, "%s:baby_wither_gem" % NS)
    shaped("passive_wither_head", [" Y ", "YXY", " Y "], {"Y": skull, "X": "minecraft:nether_star"},
           "%s:passive_wither_head" % NS)
    shaped("passive_wither_gem", ["XXX", "YYY", " Y "],
           {"X": "%s:passive_wither_head" % NS, "Y": "minecraft:soul_sand"}, "%s:passive_wither_gem" % NS)
    shaped("thirsty_wither_head", ["XYX", "YZY", "XYX"],
           {"X": "%s:empowered_quartz" % NS, "Y": "%s:extrapolated_bucket" % NS, "Z": "%s:passive_wither_head" % NS},
           "%s:thirsty_wither_head" % NS)
    shaped("predator_wither_head", ["XXX", "AZA", "XXX"],
           {"X": "%s:compressed_paladium" % NS, "A": "%s:compressed_endium" % NS,
            "Z": "%s:passive_wither_head" % NS}, "%s:predator_wither_head" % NS)
    # Le wiki : quatre pepites d'endium, quatre obsidiennes infusees de paladium
    # (l'obsidienne compacte, a coeur de paladium), une tete de predateur.
    shaped("supreme_wither_head", ["NON", "OPO", "NON"],
           {"N": "%s:endium_nugget" % NS, "O": "%s:big_obsidian" % NS, "P": "%s:predator_wither_head" % NS},
           "%s:supreme_wither_head" % NS)
    for gem, head, core in (("thirsty_wither_gem", "thirsty_wither_head", "compressed_endium"),
                            ("predator_wither_gem", "predator_wither_head", "endium_nugget"),
                            ("supreme_wither_gem", "supreme_wither_head", "endium_ingot")):
        shaped(gem, [" X ", "XYX", " X "], {"X": "%s:%s" % (NS, head), "Y": "%s:%s" % (NS, core)},
               "%s:%s" % (NS, gem))
    # La pate de camouflage de SecretRooms n'est pas portee : la membrane de phantom.
    for upgrade, ring in (("stealth_upgrade", "minecraft:phantom_membrane"),
                          ("wither_resistance_upgrade", "%s:compressed_paladium" % NS),
                          ("wither_slime_upgrade", "%s:slimepad" % NS)):
        shaped(upgrade, [" X ", "XYX", " X "], {"X": ring, "Y": "%s:passive_wither_head" % NS},
               "%s:%s" % (NS, upgrade))
    # La boussole du chasseur n'est pas portee : une boussole.
    shaped("wither_target_laser", [" XY", "YZY", " Y "],
           {"X": "minecraft:redstone_torch", "Y": "%s:empowered_quartz" % NS, "Z": "minecraft:compass"},
           "%s:wither_target_laser" % NS)
    shaped("wither_saddle", [" F ", "FSF", " F "],
           {"F": "%s:wither_skull_fragment" % NS, "S": "minecraft:saddle"}, "%s:wither_saddle" % NS)
    shapeless("wither_fishing_rod", ["minecraft:fishing_rod", "%s:wither_skull_fragment" % NS,
                                     "%s:wither_skull_fragment" % NS], "%s:wither_fishing_rod" % NS)
    shaped("mid_life_arrow", [" D ", "DAD", " D "], {"D": "%s:wither_dust" % NS, "A": "minecraft:arrow"},
           "%s:mid_life_arrow" % NS)
    shaped("wither_skull_fragment_from_dust", ["DD", "DD"], {"D": "%s:wither_dust" % NS},
           "%s:wither_skull_fragment" % NS)
    shaped("compressed_quartz", ["XXX", "XXX", "XXX"], {"X": "minecraft:quartz_block"}, "%s:compressed_quartz" % NS)
    shapeless("quartz_block_from_compressed", ["%s:compressed_quartz" % NS], "minecraft:quartz_block", 9)
    shaped("empowered_quartz", [" X ", "XYX", " X "],
           {"X": "%s:compressed_quartz" % NS, "Y": "%s:shiny_erable_wood" % NS}, "%s:empowered_quartz" % NS)

    # ---- enchantements : outils et cornes ----
    #
    # Demande du joueur (2026-11-10) : armures, outils et cornes s'enchantent.
    # PalaMod refusait les enchantements aux marteaux, aux excavatrices et aux
    # armes du grinder ; ils s'enchantent ici comme une pioche, une pelle, une
    # epee. Les cornes de monture prennent Solidite et Raccommodage (durability),
    # Fortune et Toucher de soie (mining_loot). EnchantabilityCheck verifie le
    # tout au demarrage du serveur.
    for metal in ("amethyst", "titane", "paladium"):
        tag("minecraft/tags/item/pickaxes.json", ["%s:%s_hammer" % (NS, metal)])
        tag("minecraft/tags/item/shovels.json", ["%s:%s_excavator" % (NS, metal)])
        tag("minecraft/tags/item/swords.json", ["%s:%s_broadsword" % (NS, metal), "%s:%s_fastsword" % (NS, metal)])
        tag("minecraft/tags/item/enchantable/durability.json", ["%s:%s_horn" % (NS, metal)])
        tag("minecraft/tags/item/enchantable/mining_loot.json", ["%s:%s_horn" % (NS, metal)])

    # ---- les recettes de fabrication ----
    #
    # Une seule source : data/palamod_crafts.json, le relevé de
    # tools/extract_crafts.py sur les quatre registres de PalaMod. Rien n'est
    # recopie a la main — c'est precisement une recopie a la main, depuis un
    # relevé encore faux, qui avait produit les charbons mixes a grille
    # inegale. Corriger l'extracteur corrige maintenant toutes les recettes.
    #
    # PORTED dit quelles recettes porter, par nom PalaMod -> identifiant.
    # INGREDIENTS traduit chaque ingredient, meta compris. Un ingredient absent
    # de la table fait ECHOUER le generateur : deviner une correspondance
    # donnerait une recette fausse qui se fabrique quand meme.
    PORTED = {
        # Intermediaires
        "STICK_AMETHYST": "amethyst_stick", "STICK_TITANE": "titane_stick",
        "STICK_PALADIUM": "paladium_stick", "PALADIUM_CORE": "paladium_core",
        # Montures : l'orbe de regeneration, le bandage et l'enclos.
        "ORB_HEAL": "heal_orb", "BANDAGE": "bandage", "BARN_FOUNDATION": "barn_foundation",
        "BARN_FENCE": "barn_fence", "INCUBATOR": "barn_incubator", "BARN_CONTROLLER": "barn_controller",
        "MIXED_ENDIUM_INGOT": "mixed_endium_ingot",
        "ORB_SPEED": "speed_orb", "MODIFIER_SPEED": "speed_modifier",
        "MODIFIER_FORTUNE": "fortune_modifier", "VOIDSTONE": "voidstone",
        # Charbons mixes
        "GOLDMIXEDCOAL": "gold_mixed_coal", "AMETHYSTMIXEDCOAL": "amethyst_mixed_coal",
        "TITANEMIXEDCOAL": "titane_mixed_coal", "PALAMIXEDCOAL": "pala_mixed_coal",
        # Machines et ameliorations
        "COBBLEBREAKER": "cobblebreaker", "CRUSHER": "crusher", "Totem": "totem_of_fertility",
        "COBBLEBREAKER_AMETHYST_UPGRADE": "cobblebreaker_amethyst_upgrade",
        "COBBLEBREAKER_TITANE_UPGRADE": "cobblebreaker_titane_upgrade",
        "COBBLEBREAKER_PALADIUM_UPGRADE": "cobblebreaker_paladium_upgrade",
        # Seedplanters : lvl4 est l'endium, lvl5 le paladium vert
        "SEEDPLANTER_LVL1": "amethyst_seedplanter", "SEEDPLANTER_LVL2": "titane_seedplanter",
        "SEEDPLANTER_LVL3": "paladium_seedplanter", "SEEDPLANTER_LVL4": "endium_seedplanter",
        "SEEDPLANTER_LVL5": "green_paladium_seedplanter",
        # Mineur (chapitre 8)
        "AMETHYST_EXCAVATOR": "amethyst_excavator", "TITANE_EXCAVATOR": "titane_excavator",
        "PALADIUM_EXCAVATOR": "paladium_excavator", "MAGNET": "magnet",
        "MOULA_STONE": "moula_stone", "CAPTURE_STONE": "capture_stone", "VOIDSTONE_MINAGE": "voidstone_minage",
        "CAVE_BLOCK": "cave_block", "ENDIUM_CAVE_BLOCK": "endium_cave_block",
        "PALADIUM_FURNACE": "paladium_furnace", "FORGE": "palaforge",
        "FURNACE_UPGRADE": "furnace_upgrade", "PALADIUM_HOPPER": "paladium_hopper",
        "AMETHYST_CHEST": "amethyst_chest", "TITANE_CHEST": "titane_chest",
        "PALADIUM_CHEST": "paladium_chest", "GPALADIUM_CHEST": "green_paladium_chest",
        "ENDIUM_CHEST": "endium_chest",
        # Alchimiste (chapitre 11) : CraftMod, sans le patron de boule de glu
        # (les boules de glu ne sont pas portees).
        "EXTRACTOR": "extractor", "FLASK": "flask",
        "TANK_GOLD": "gold_tank", "TANK_AMETHYSTE": "amethyst_tank",
        "TANK_TITANE": "titane_tank", "TANK_PALADIUM": "paladium_tank",
        "CAULDRON_BLOCK": "cauldron", "CAULDRON_CORE": "cauldron_core",
        "AMETHYSTE_PORTAL_BLOCK": "amethyst_portal_block", "TITANE_PORTAL_BLOCK": "titane_portal_block",
        "PALADIUM_PORTAL_BLOCK": "paladium_portal_block", "ENDIUM_PORTAL_BLOCK": "endium_portal_block",
        "ENDIUM_ANGLE_PORTAL_BLOCK": "endium_angle_portal_block", "ENDIUM_HEART": "endium_heart",
        "AMETHYSTE_PORTAL_KEY": "amethyst_portal_key", "TITANE_PORTAL_KEY": "titane_portal_key",
        "PALADIUM_PORTAL_KEY": "paladium_portal_key", "ENDIUM_PORTAL_KEY": "endium_portal_key",
        "MYSTICAL_BOOKSHELF": "mystical_bookshelf",
        # Grinder (chapitre 12) : PSRegister_Craft. Les outils en paladium, les
        # tetes et les lingots sortent du grinder lui-meme (GrinderRecipes).
        "GRINDER_BLOCK": "grinder", "GRINDER_CASING_BLOCK": "grinder_casing",
        "GRINDER_FRAME_BLOCK": "grinder_frame",
        "PATERN_AXE": "axe_pattern", "PATERN_HAMMER": "hammer_pattern", "PATERN_SHOVEL": "shovel_pattern",
        "PATERN_BROADSWORD": "broadsword_pattern", "PATERN_FASTSWORD": "fastsword_pattern",
        "PATERN_PICKAXE": "pickaxe_pattern", "PATERN_SWORD": "sword_pattern", "PATERN_INGOT": "ingot_pattern",
        "PATERN_BLOCK": "block_pattern", "PATERN_SOCKET": "socket_pattern",
        "MODIFIER_SMELT": "smelt_modifier", "MODIFIER_DAMAGE": "damage_modifier",
        "MODIFIER_FLAME": "flame_modifier", "MODIFIER_KNOCKBACK": "knockback_modifier",
        "MODIFIER_MOREUPGRADE": "more_upgrade_modifier", "ORB_KNOCKBACK": "knockback_orb",
        "HAMMER_AMETHYST": "amethyst_hammer", "HAMMER_TITANE": "titane_hammer",
        "BROADSWORD_AMETHYST": "amethyst_broadsword", "BROADSWORD_TITANE": "titane_broadsword",
        "FASTSWORD_AMETHYST": "amethyst_fastsword", "FASTSWORD_TITANE": "titane_fastsword",
        "COMPRESSED_XP_BERRY": "compressed_xp_berry",
        "GOD_PICKAXE": "god_pickaxe", "GOD_PICKAXE_AUTOSMELT": "god_pickaxe_auto_smelt",
        "PALADIUM_APPLE": "paladium_apple",
        # PvP (chapitre 16) : a l'etabli, les ingredients de la palamachine et
        # le deltaplane. Les sticks et les anneaux sortent de la palamachine
        # (PalamachineRecipes).
        "ORB_STRENGHT": "strength_orb", "ORB_JUMP": "jump_orb", "WING": "wing",
        "HANGGLIDER": "hang_glider", "SLIMEPAD_BLOCK": "slimepad", "SPIKE_PALADIUM": "paladium_spike",
        "PALADIUM_MACHINE_BLOCK": "palamachine",
        # Defense de faction (chapitre 22, lot 22c) : PRegister_Craft.
        "SPIKE_WOOD": "wood_spike", "SPIKE_IRON": "iron_spike", "SPIKE_GOLD": "gold_spike",
        "SPIKE_DIAMOND": "diamond_spike", "SPIKE_AMETHYST": "amethyst_spike", "SPIKE_TITANE": "titane_spike",
        "ANVIL_AMETHYST": "amethyst_anvil", "ANVIL_TITANE": "titane_anvil", "ANVIL_PALADIUM": "paladium_anvil",
        "MAGICAL_TOOL": "magical_tool", "LAVA_SPONGE": "lava_sponge", "PISTOL_TANK": "pistol_tank",
        "OBSIDIAN_STAIRS": "obsidian_stairs", "OBSIDIAN_SLAB": "obsidian_slab",
        "OBSIDIAN_HARDENED_STAIRS": "hardened_obsidian_stairs", "OBSIDIAN_HARDENED_SLAB": "hardened_obsidian_slab",
        "OBSIDIAN_ITEM_DOOR": "obsidian_door", "OBSIDIAN_TRAP_DOOR": "obsidian_trapdoor",
        # Pillage de faction (chapitre 22, lot 22d) : PRegister_Craft.
        "DIAMOND_STRING": "diamond_string", "PALADIUM_BOW": "paladium_bow", "TRAVEL_HOODHELMET": "hood_helmet",
        # Armure de voyage (2026-11-11) : la recompense du Fermier au niveau 55.
        "TRAVEL_BOOTS": "travel_boots", "TRAVEL_LEGGINGS": "travel_leggings",
        "TRAVEL_JUMPCHEST": "travel_jumpchest", "TRAVEL_SLIMYHELMET": "travel_slimyhelmet",
        "TRAVEL_SCUBAHELMET": "travel_scubahelmet",
        "CHESTEXPLORER": "chest_explorer", "BOWUPGRADE_EMPTY": "empty_bow_modifier",
        "ONLINE_DETECTOR_BLOCK": "online_detector", "DYNAMITE": "dynamite", "DYNAMITE_BIG": "big_dynamite",
        "DYNAMITE_NINJA": "ninja_dynamite", "EXTRAPOLATED_BUCKET": "extrapolated_bucket",
        "UNCLAIMFINDER": "unclaim_finder", "SPAWNER_FINDER": "spawner_finder",
    }
    # Un meme objet 1.7.10 a plusieurs metas, plusieurs objets en 26.2 : la
    # recette se choisit par (nom, meta). Sans cette table, les quatre outils de
    # construction prendraient tous la recette du premier.
    PORTED_BY_META = {
        ("BUILDER_WAND", 0): "amethyst_builder_wand",
        ("BUILDER_WAND", 1): "titane_builder_wand",
        ("BUILDER_WAND", 2): "paladium_builder_wand",
        ("BUILDER_WAND", 3): "endium_builder_wand",
    }
    # Les memes familles que EQUIPMENT_METALS plus haut : l'endium mixe n'a que
    # l'armure, l'endium n'a pas de pelle.
    for metal, palamod_metal, tools in (
            ("amethyst", "AMETHYST", ("SWORD", "PICKAXE", "AXE", "SHOVEL")),
            ("titane", "TITANE", ("SWORD", "PICKAXE", "AXE", "SHOVEL")),
            ("paladium", "PALADIUM", ("SWORD", "PICKAXE", "AXE", "SHOVEL")),
            ("green_paladium", "PALADIUM_GREEN", ("SWORD", "PICKAXE", "AXE", "SHOVEL")),
            ("mixed_endium", "MIXED_ENDIUM", ()),
            ("endium", "ENDIUM", ("SWORD", "PICKAXE", "AXE"))):
        for piece in tools + ("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"):
            PORTED["%s_%s" % (palamod_metal, piece)] = "%s_%s" % (metal, piece.lower())
    for metal in ("AMETHYST", "TITANE", "PALADIUM", "GREEN_PALADIUM", "ENDIUM"):
        PORTED["%s_RADIUS_HOE" % metal] = "%s_radius_hoe" % metal.lower()

    INGREDIENTS = {
        ("Items.field_151055_y", 0): "minecraft:stick",
        ("Items.field_151044_h", 0): "minecraft:coal",
        ("Items.field_151043_k", 0): "minecraft:gold_ingot",
        ("Items.field_151102_aT", 0): "minecraft:sugar",
        ("Blocks.field_150347_e", 0): "minecraft:cobblestone",
        ("Blocks.field_150486_ae", 0): "minecraft:chest",
        ("Blocks.field_150340_R", 0): "minecraft:gold_block",
        # Bloc de bois sans meta : le chene, et seulement lui en 1.7.10.
        ("Blocks.field_150364_r", 0): "minecraft:oak_log",
        # Tete meta 4 : creeper. Colorant meta 15 : poudre d'os.
        ("Items.field_151144_bL", 4): "minecraft:creeper_head",
        ("Items.field_151100_aR", 15): "minecraft:bone_meal",
        ("BlocksRegister.BLOCK_GPALADIUM", 0): "%s:green_paladium_block" % NS,
        ("BlocksRegister.COBBLEBREAKER", 0): "%s:cobblebreaker" % NS,
        ("ItemsRegister.FINDIUM", 0): "%s:findium" % NS,
        ("ItemsRegister.ENDIUM_FRAGMENT", 0): "%s:endium_fragment" % NS,
        ("ItemsRegister.ENDIUM_NUGGET", 0): "%s:endium_nugget" % NS,
        ("ItemsRegister.PALADIUM_GREEN_INGOT", 0): "%s:green_paladium_ingot" % NS,
        ("Items.field_151079_bi", 0): "minecraft:ender_pearl",
        ("Blocks.field_150339_S", 0): "minecraft:iron_block",
        ("BlocksRegister.BLOCK_AMETHYST", 0): "%s:amethyst_block" % NS,
        ("BlocksRegister.BLOCK_TITANE", 0): "%s:titane_block" % NS,
        ("BlocksRegister.BLOCK_PALADIUM", 0): "%s:paladium_block" % NS,
        ("Blocks.field_150359_w", 0): "minecraft:glass",
        # Argile durcie teintee meta 7 : la terre cuite grise.
        ("Blocks.field_150406_ce", 7): "minecraft:gray_terracotta",
        ("Items.field_151121_aF", 0): "minecraft:paper",
        ("Items.field_151007_F", 0): "minecraft:string",
        ("Items.field_151060_bw", 0): "minecraft:glistering_melon_slice",
        # Le gestionnaire d'enclos demande le coffre en or d'Iron Chests : la meta
        # vient de IronChestType.GOLD.ordinal(), calculee a l'execution, que le
        # releve lit comme 0 (le coffre en fer). C'est bien l'or (spec Mount §22).
        ("IronChest.ironChestBlock", 0): "%s:gold_chest" % NS,
        ("Blocks.field_150336_V", 0): "minecraft:bricks",
        ("Items.field_151065_br", 0): "minecraft:blaze_powder",
        ("Blocks.field_150343_Z", 0): "minecraft:obsidian",
        # Un Block passe tel quel a addRecipe vaut toutes ses metas
        # (new ItemStack(block, 1, 32767) dans CraftingManager) : toutes les
        # planches vanilla.
        ("Blocks.field_150344_f", 0): "#minecraft:planks",
        ("Items.field_151131_as", 0): "minecraft:water_bucket",
        ("Items.field_151122_aG", 0): "minecraft:book",
        ("ItemsRegister.LIGHTNING_POTION", 0): "%s:lightning_potion" % NS,
        ("ItemsRegister.ENDIUM_POLLEN", 0): "%s:endium_pollen" % NS,
        ("BlocksRegister.FLOWER_ENDIUM", 0): "%s:endium_flower" % NS,
        ("BlocksRegister.BLOCK_FINDIUM", 0): "%s:findium_block" % NS,
        ("BlocksRegister.SHINY_JACARANDA_WOOD", 0): "%s:shiny_jacaranda_wood" % NS,
        ("BlocksRegister.SHINY_JUDEECERCIS_WOOD", 0): "%s:shiny_judeecercis_wood" % NS,
        ("BlocksRegister.SHINY_ERABLE_WOOD", 0): "%s:shiny_erable_wood" % NS,
        ("BlocksRegister.SHINY_OSTRYA_WOOD", 0): "%s:shiny_ostrya_wood" % NS,
        # Grinder : baton de blaze, briquet, soupe de champignons, larme de ghast.
        ("Items.field_151072_bj", 0): "minecraft:blaze_rod",
        ("Items.field_151034_e", 0): "minecraft:apple",
        ("Items.field_151033_d", 0): "minecraft:flint_and_steel",
        ("Items.field_151009_A", 0): "minecraft:mushroom_stew",
        ("Items.field_151073_bk", 0): "minecraft:ghast_tear",
        ("PSRegister_Items.HAMMER_PALADIUM", 0): "%s:paladium_hammer" % NS,
        ("ItemsRegister.XP_BERRY", 0): "%s:xp_berry" % NS,
        # PvP : cuir (aile, deltaplane), verrue du Nether (orbe de saut), boule
        # de slime (slimepad), pierre (palamachine).
        ("Items.field_151116_aA", 0): "minecraft:leather",
        ("Items.field_151075_bm", 0): "minecraft:nether_wart",
        ("Items.field_151123_aH", 0): "minecraft:slime_ball",
        ("Blocks.field_150348_b", 0): "minecraft:stone",
        # Defense : epees de bois, de fer, d'or et de diamant (spikes), fer,
        # diamant, seau de lave et sable des ames (eponge a lave), l'eponge de
        # back2future, le bloc de quartz (pistolet a liquide).
        ("Items.field_151041_m", 0): "minecraft:wooden_sword",
        ("Items.field_151040_l", 0): "minecraft:iron_sword",
        ("Items.field_151010_B", 0): "minecraft:golden_sword",
        ("Items.field_151048_u", 0): "minecraft:diamond_sword",
        ("Items.field_151042_j", 0): "minecraft:iron_ingot",
        ("Items.field_151045_i", 0): "minecraft:diamond",
        ("Items.field_151129_at", 0): "minecraft:lava_bucket",
        ("Blocks.field_150425_aM", 0): "minecraft:soul_sand",
        ("ModBlocks.sponge", 0): "minecraft:sponge",
        ("Blocks.field_150371_ca", 0): "minecraft:quartz_block",
        # Pillage : la TNT, la laine (toutes ses couleurs, passee en Block), le
        # colorant meta 0 (poche d'encre), le spawner casse du Chasseur.
        ("Blocks.field_150335_W", 0): "minecraft:tnt",
        ("Blocks.field_150325_L", 0): "#minecraft:wool",
        ("Items.field_151100_aR", 0): "minecraft:ink_sac",
        ("SpawnerBlockRegistry.EMPTY_BROKEN_MOB_SPAWNER", 0): "%s:broken_spawner" % NS,
        # Cle declaree mais inutilisee du detecteur de presence : elle doit se
        # resoudre pour que le releve passe, puis le filtre la retire.
        ("ItemsRegister.WITHER_SKULLFRAGMENT", 0): "%s:wither_skull_fragment" % NS,
        # Armure de voyage : la plume, et la vitre du casque de plongee.
        ("Items.field_151008_G", 0): "minecraft:feather",
        ("Blocks.field_150410_aZ", 0): "minecraft:glass_pane",
    }
    for metal in ("AMETHYST", "TITANE", "PALADIUM", "ENDIUM"):
        INGREDIENTS[("ItemsRegister.%s_INGOT" % metal, 0)] = "%s:%s_ingot" % (NS, metal.lower())
        INGREDIENTS[("ItemsRegister.COMPRESSED_%s" % metal, 0)] = "%s:compressed_%s" % (NS, metal.lower())
    # Tout ce que PORTED produit peut servir d'ingredient a une autre recette.
    for palamod_name, ported in PORTED.items():
        for register in ("ItemsRegister", "BlocksRegister", "PSRegister_Items", "ItemsRegistry"):
            INGREDIENTS.setdefault(("%s.%s" % (register, palamod_name), 0), "%s:%s" % (NS, ported))

    crafts = json.load(open(os.path.join(os.path.dirname(__file__), "..", "data",
                                         "palamod_crafts.json"), encoding="utf-8"))
    emitted = {}
    for recipe in crafts:
        result = (recipe["result"]["item"] or "").split(".", 1)[-1]
        name = PORTED_BY_META.get((result, recipe["result"].get("meta", 0)), PORTED.get(result))
        if (result, 0) in PORTED_BY_META and name == PORTED.get(result):
            name = None
        # Les haches existent en deux grilles miroir et quelques recettes sont
        # declarees deux fois. 26.2 accepte le miroir de lui-meme : une recette
        # par objet, la premiere.
        if name is None or name in emitted or not recipe["keys"]:
            continue

        pattern = recipe["pattern"]
        width = max(len(row) for row in pattern)
        # Une grille de 1.7.10 est rectangulaire par construction : CraftingManager
        # concatene les lignes et decoupe par la largeur de la derniere. Une
        # ligne plus courte ici est donc un defaut de relevé, pas une donnee.
        if any(len(row) != width for row in pattern):
            raise SystemExit("recette de %s : lignes de largeurs inegales %r — "
                             "relevé fautif, relancer extract_crafts.py avec les .class"
                             % (name, pattern))

        keys = {}
        for key, ingredient in recipe["keys"].items():
            target = INGREDIENTS.get((ingredient["id"], ingredient["meta"]))
            if target is None:
                raise SystemExit("recette de %s : ingredient inconnu %s (meta %d) — "
                                 "l'ajouter a INGREDIENTS plutot que de deviner"
                                 % (name, ingredient["id"], ingredient["meta"]))
            keys[key] = target

        # Une case dont la lettre n'a pas de cle valait null en 1.7.10, donc
        # VIDE ; une cle qu'aucune case n'utilise ne servait a rien. 26.2 refuse
        # les deux (« Key defines symbols that aren't used in pattern »). On
        # porte ce que le jeu faisait : c'est le cas du cobblebreaker, ou la
        # pioche en paladium n'a jamais ete demandee.
        used = {char for row in pattern for char in row if char != " "}
        pattern = ["".join(char if char in keys else " " for char in row) for row in pattern]
        keys = {key: value for key, value in keys.items() if key in used}
        # Les lignes vides du bord ne changent rien : la grille moderne les retire
        # d'elle-meme. On les ote pour garder des fichiers lisibles ; une ligne
        # vide AU MILIEU (le cadre du grinder) reste, elle compte.
        while pattern and not pattern[0].strip():
            pattern = pattern[1:]
        while pattern and not pattern[-1].strip():
            pattern = pattern[:-1]

        shaped(name, pattern, keys, "%s:%s" % (NS, name), recipe["result"]["count"])
        emitted[name] = recipe

    missing_recipes = sorted((set(PORTED.values()) | set(PORTED_BY_META.values())) - set(emitted))
    if missing_recipes:
        raise SystemExit("aucune recette relevee pour : %s" % ", ".join(missing_recipes))
    print("recettes : %d portees depuis data/palamod_crafts.json" % len(emitted))
    # Les secondes recettes d'un meme objet, que la boucle ecarte : l'orbe de
    # regeneration a l'origine se fait aussi avec un livre.
    shaped("heal_orb_from_book", [" X ", "XZX", " X "],
           {"X": "%s:paladium_ingot" % NS, "Z": "minecraft:book"}, "%s:heal_orb" % NS)
    # Deux recettes de PRegister_Craft sur des objets vanilla : le gres rendu en
    # sable, l'obsidienne renforcee rendue en obsidienne (4 pour 4, l'inverse de
    # sa recette : rien a gagner a l'aller-retour).
    shapeless("sand_from_sandstone", ["minecraft:sandstone"], "minecraft:sand", count=4)
    shaped("obsidian_from_hardened", ["XX", "XX"], {"X": "%s:hardened_obsidian" % NS}, "minecraft:obsidian", 4)

    # ---- generation du monde ----
    #
    # La TAILLE DE FILON vient du code (BaseWorldGenerator, constructeurs de
    # WorldGenMinable). Le NOMBRE PAR CHUNK et la PROFONDEUR sont calibres, et
    # c'est volontaire : en 1.7.10 le monde allait de 0 a 255 et la deepslate
    # n'existait pas. Recopier les hauteurs d'origine donnerait des minerais
    # concentres au mauvais endroit d'un monde qui descend maintenant a -64.
    # On se cale donc sur l'analogue vanilla de chaque palier de pioche.
    for name, (size, count, min_y, max_y) in ORE_GEN.items():
        # « ore_amethyst » plutot que « ore_amethyst_ore » : le suffixe du
        # bloc ferait doublon dans le nom de la feature.
        feature = "ore_" + name.removesuffix("_ore")
        state = {"Name": "%s:%s" % (NS, name)}
        write("data/%s/worldgen/configured_feature/%s.json" % (NS, feature), {
            "type": "minecraft:ore",
            "config": {
                "size": size,
                "discard_chance_on_air_exposure": 0.0,
                # Deux cibles, meme bloc : sans la seconde le minerai
                # s'arreterait net a la frontiere de la deepslate, ou se
                # trouve desormais la moitie de la zone de minage.
                "targets": [
                    {"target": {"predicate_type": "minecraft:tag_match",
                                "tag": "minecraft:stone_ore_replaceables"},
                     "state": state},
                    {"target": {"predicate_type": "minecraft:tag_match",
                                "tag": "minecraft:deepslate_ore_replaceables"},
                     "state": state},
                ],
            },
        })
        write("data/%s/worldgen/placed_feature/%s.json" % (NS, feature), {
            "feature": "%s:%s" % (NS, feature),
            "placement": [
                {"type": "minecraft:count", "count": count},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:height_range",
                 "height": {"type": "minecraft:uniform",
                            "min_inclusive": {"absolute": min_y},
                            "max_inclusive": {"absolute": max_y}}},
                {"type": "minecraft:biome"},
            ],
        })

    # Le minerai de pepites d'endium : seulement dans la dimension du Mineur,
    # comme en 1.7.10 (BaseWorldGenerator, isMinerDimension). Un filon de 3
    # ramene a UN bloc — le code effacait les autres dans un cube de 17 — d'ou
    # replace_single_block. Le placement est plus bas, avec la dimension.
    nugget_state = {"Name": "%s:endium_nugget_ore" % NS}
    write("data/%s/worldgen/configured_feature/ore_endium_nugget.json" % NS, {
        "type": "minecraft:replace_single_block",
        "config": {"targets": [
            {"target": {"predicate_type": "minecraft:tag_match",
                        "tag": "minecraft:stone_ore_replaceables"},
             "state": nugget_state},
            {"target": {"predicate_type": "minecraft:tag_match",
                        "tag": "minecraft:deepslate_ore_replaceables"},
             "state": nugget_state},
        ]},
    })

    # Le minerai de Pierre d'Invocation : couches du findium, filon de 3. En
    # 1.7.10, quatre tirages a rand(2 x rarete du findium) == 1, soit 1 sur 10
    # chacun : 0,4 filon par chunk. Une chance sur deux ici.
    invoker_state = {"Name": "%s:invoker_ore" % NS}
    write("data/%s/worldgen/configured_feature/ore_invoker.json" % NS, {
        "type": "minecraft:ore",
        "config": {"size": 3, "discard_chance_on_air_exposure": 0.0, "targets": [
            {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"},
             "state": invoker_state},
            {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"},
             "state": invoker_state}]},
    })
    findium_min, findium_max = ORE_GEN["findium_ore"][2:]

    def placed(path, feature, frequency, min_y, max_y):
        write("data/%s/worldgen/placed_feature/%s.json" % (NS, path), {
            "feature": "%s:%s" % (NS, feature),
            "placement": frequency + [
                {"type": "minecraft:in_square"},
                {"type": "minecraft:height_range",
                 "height": {"type": "minecraft:uniform",
                            "min_inclusive": {"absolute": min_y},
                            "max_inclusive": {"absolute": max_y}}},
                {"type": "minecraft:biome"},
            ],
        })

    placed("ore_invoker", "ore_invoker", [{"type": "minecraft:rarity_filter", "chance": 2}],
           findium_min, findium_max)

    # Un seul modificateur de biome pour tous les minerais : NeoForge les ajoute
    # a l'etape underground_ores de tout biome de l'overworld.
    write("data/%s/neoforge/biome_modifier/ores.json" % NS, {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": ["%s:ore_%s" % (NS, name.removesuffix("_ore"))
                     for name in sorted(ORE_GEN)] + ["%s:ore_invoker" % NS],
        "step": "underground_ores",
    })

    # ---- la dimension du Mineur ----
    #
    # Le serveur « dimminer » de Paladium etait un overworld ordinaire ou
    # BaseWorldGenerator tirait les minerais PalaMod avec mult = 3 : essais =
    # ceil(base x 3), boucle i <= essais, donc ceil(3 base) + 1 tentatives au
    # lieu de base + 1. On applique ce rapport aux comptes DoHy de l'overworld,
    # sur les memes configured features. base = valeurs par defaut de
    # palamod.cfg.
    ORIGINAL_BASE = {"amethyst_ore": 5, "titane_ore": 4, "paladium_ore": 2, "paladium_green_ore": 1,
                     "findium_ore": 3, "trixium_ore": 3}
    dimension_ores = []
    for name, (_size, count, min_y, max_y) in sorted(ORE_GEN.items()):
        base = ORIGINAL_BASE[name]
        boosted = int(count * (math.ceil(base * 3) + 1) / (base + 1) + 0.5)
        feature = "ore_" + name.removesuffix("_ore")
        placed("miner/" + feature, feature, [{"type": "minecraft:count", "count": boosted}], min_y, max_y)
        dimension_ores.append("%s:miner/%s" % (NS, feature))
    # La pepite d'endium a son taux d'origine : quatre tirages a 1 sur 900,
    # un chunk sur 225. La Pierre d'Invocation : dix tirages a 1 sur 10.
    placed("miner/ore_endium_nugget", "ore_endium_nugget",
           [{"type": "minecraft:rarity_filter", "chance": 225}], -64, -54)
    placed("miner/ore_invoker", "ore_invoker", [{"type": "minecraft:count", "count": 1}],
           findium_min, findium_max)
    # Le buisson a XP : sept essais au lieu de trois, meme chance.
    placed("miner/xp_bush", "xp_bush", [{"type": "minecraft:count", "count": 7},
                                        {"type": "minecraft:rarity_filter", "chance": 5}], -32, 40)
    dimension_ores += ["%s:miner/ore_endium_nugget" % NS, "%s:miner/ore_invoker" % NS,
                       "%s:miner/xp_bush" % NS]

    # Les ruines a spawner (MinerRuinFeature) : les salles a monstres que la
    # dimension n'a pas, ou le marteau des cavernes recolte ses ames. En
    # surface, un essai par chunk a une chance sur 9, que le sol valide une
    # fois sur deux en plaine (mesure : une ruine tous les 18 chunks) ; sous
    # terre, une chance sur 10, que la roche valide presque toujours (une
    # salle ne se taille ni dans une grotte ni dans un lac).
    for feature in ("miner_ruin", "miner_crypt"):
        write("data/%s/worldgen/configured_feature/%s.json" % (NS, feature),
              {"type": "%s:%s" % (NS, feature), "config": {}})
    write("data/%s/worldgen/placed_feature/miner/ruin.json" % NS, {
        "feature": "%s:miner_ruin" % NS,
        "placement": [
            {"type": "minecraft:rarity_filter", "chance": 9},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"},
            {"type": "minecraft:biome"},
        ],
    })
    placed("miner/crypt", "miner_crypt", [{"type": "minecraft:rarity_filter", "chance": 10}], -48, 48)

    # Un seul biome, fixe : les plaines de 26.2 sans pluie, sans aucun mob
    # (l'original annulait tout EntityMob) et sans salle a monstres — les
    # ruines a spawner en tiennent lieu. Il n'est
    # pas dans #minecraft:is_overworld, donc aucun modificateur de biome DoHy
    # (minerais au taux normal, arbres, montures) ne s'y ajoute.
    write("data/%s/worldgen/biome/miner.json" % NS, {
        "attributes": {"minecraft:visual/sky_color": "#000000", "minecraft:visual/fog_color": "#000000"},
        "carvers": ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"],
        "downfall": 0.4,
        "effects": {"water_color": "#3f76e4"},
        "features": [
            [],
            ["minecraft:lake_lava_underground", "minecraft:lake_lava_surface"],
            ["minecraft:amethyst_geode"],
            ["%s:miner/crypt" % NS],
            ["%s:miner/ruin" % NS],
            [],
            ["minecraft:ore_dirt", "minecraft:ore_gravel", "minecraft:ore_granite_upper",
             "minecraft:ore_granite_lower", "minecraft:ore_diorite_upper", "minecraft:ore_diorite_lower",
             "minecraft:ore_andesite_upper", "minecraft:ore_andesite_lower", "minecraft:ore_tuff",
             "minecraft:ore_coal_upper", "minecraft:ore_coal_lower", "minecraft:ore_iron_upper",
             "minecraft:ore_iron_middle", "minecraft:ore_iron_small", "minecraft:ore_gold",
             "minecraft:ore_gold_lower", "minecraft:ore_redstone", "minecraft:ore_redstone_lower",
             "minecraft:ore_diamond", "minecraft:ore_diamond_medium", "minecraft:ore_diamond_large",
             "minecraft:ore_diamond_buried", "minecraft:ore_lapis", "minecraft:ore_lapis_buried",
             "minecraft:ore_copper", "minecraft:underwater_magma", "minecraft:disk_sand",
             "minecraft:disk_clay", "minecraft:disk_gravel"] + dimension_ores,
            [],
            ["minecraft:spring_water", "minecraft:spring_lava"],
            ["minecraft:glow_lichen", "minecraft:patch_tall_grass_2", "minecraft:patch_bush",
             "minecraft:trees_plains", "minecraft:flower_plains", "minecraft:patch_grass_plain",
             "minecraft:brown_mushroom_normal", "minecraft:red_mushroom_normal", "minecraft:patch_pumpkin",
             "minecraft:patch_sugar_cane", "minecraft:patch_firefly_bush_near_water"],
            ["minecraft:freeze_top_layer"],
        ],
        "has_precipitation": False,
        "spawn_costs": {},
        "spawners": {category: [] for category in (
            "ambient", "axolotls", "creature", "misc", "monster", "underground_water_creature",
            "water_ambient", "water_creature")},
        "temperature": 0.8,
    })

    # Le type : l'overworld (memes hauteurs, -64..320, pour reprendre les
    # placements DoHy) mais une nuit fixe — le client forcait worldTime a
    # 15000 et videait le ciel. Ciel et brouillard noirs, lumiere du ciel au
    # niveau de la nuit vanilla (timeline minecraft:day a 18000). Le
    # brouillard d'origine allait de 40 % de la vue a 10 % selon les points ;
    # on fixe le premier palier.
    write("data/%s/dimension_type/miner.json" % NS, {
        "ambient_light": 0.0,
        "attributes": {
            "minecraft:audio/ambient_sounds": {"mood": {
                "block_search_extent": 8, "offset": 2.0, "sound": "minecraft:ambient.cave",
                "tick_delay": 6000}},
            "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never"},
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:gameplay/sky_light_level": 4.0,
            "minecraft:visual/ambient_light_color": "#0a0a0a",
            "minecraft:visual/fog_color": "#000000",
            "minecraft:visual/fog_start_distance": 8.0,
            "minecraft:visual/fog_end_distance": 64.0,
            "minecraft:visual/sky_color": "#000000",
            "minecraft:visual/sky_light_color": "#7a7aff",
            "minecraft:visual/sky_light_factor": 0.24,
        },
        "coordinate_scale": 1.0,
        "has_ceiling": False,
        "has_ender_dragon_fight": False,
        "has_fixed_time": True,
        "has_skylight": True,
        "height": 384,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": 384,
        "min_y": -64,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": 0,
        "skybox": "none",
        "timelines": "#minecraft:universal",
    })
    # Le terrain de l'overworld (memes reglages de bruit, meme graine), un
    # seul biome.
    write("data/%s/dimension/miner.json" % NS, {
        "type": "%s:miner" % NS,
        "generator": {"type": "minecraft:noise",
                      "biome_source": {"type": "minecraft:fixed", "biome": "%s:miner" % NS},
                      "settings": "minecraft:overworld"},
    })

    # ---- events de faction (chapitre 22, lot 22f) ----
    #
    # Les cinq boss de l'event Boss gardent la texture de leur modele du PalaMod
    # (entity/boss/), a sa taille d'origine : la geometrie est traduite du
    # bytecode (tools/modelbase2java.py) et pointe dans ces feuilles telles
    # quelles. La Warzone est une dimension vide, au biome the_void : aucun
    # monstre n'y apparait, les arenes y sont baties par le code.
    boss_sizes = {"beep_boop": (128, 128), "tobalt": (256, 256), "azhur": (128, 128),
                  "morsula": (192, 192), "arachna": (128, 128)}
    for name, size in boss_sizes.items():
        source = os.path.join(root[1] or "", "assets", "palamod", "textures", "entity", "boss", name + ".png")
        if not root[1] or not os.path.exists(source):
            missing.append("boss:" + name)
            continue
        if png_size(source) != size:
            wrong_size.append("boss:%s : %s au lieu de %s" % (name, png_size(source), size))
            continue
        target = os.path.join(RES, "assets", NS, "textures", "entity", "boss", name + ".png")
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copyfile(source, target)
    for name, label_fr, label_en in (("boss_beep_boop", "Beep Boop", "Beep Boop"),
                                     ("boss_tobalt", "Tobalt", "Tobalt"),
                                     ("boss_azhur", "Azhur", "Azhur"),
                                     ("boss_morsula", "Morsula", "Morsula"),
                                     ("boss_arachna", "Arachna", "Arachna"),
                                     ("rival_fighter", "Combattant rival", "Rival Fighter"),
                                     ("volcania", "Volcania", "Volcania")):
        label("entity", name, label_fr, label_en)
    # Volcania, le dragon de l'Egg Hunt : la feuille du dragon de l'Ender, recoloree.
    volcania = os.path.join(root[1] or "", "assets", "palamod", "textures", "entity", "egghunt", "volcania.png")
    if not root[1] or not os.path.exists(volcania):
        missing.append("egghunt:volcania")
    elif png_size(volcania) != (256, 256):
        wrong_size.append("egghunt:volcania : %s au lieu de (256, 256)" % (png_size(volcania),))
    else:
        target = os.path.join(RES, "assets", NS, "textures", "entity", "egghunt", "volcania.png")
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copyfile(volcania, target)
    write("data/%s/dimension/warzone.json" % NS, {
        "type": "minecraft:overworld",
        "generator": {
            "type": "minecraft:flat",
            "settings": {
                "biome": "minecraft:the_void",
                "features": False,
                "lakes": False,
                "layers": [],
                "structure_overrides": [],
            },
        },
    })

    # ---- concours Trixium (lot 23a) ----
    #
    # La fabrique a nuage pose un nuage (BlockCloud, texture blocks/cloud.png)
    # mais s'affiche en icone plate (items/cloud.png), comme dans PalaMod. Le
    # nuage ne lache rien. L'enderchest de poche est une icone plate.
    miner_block("cloud", {"all": "blocks/cloud.png"}, "Nuage", "Cloud", drop=False, item=False, pickaxe=False)
    for name, texture, label_fr, label_en in (
            ("cloud", "cloud.png", "Fabrique a nuage", "Cloud Factory"),
            ("pocket_enderchest", "pocket_enderchest.png", "Enderchest de poche", "Pocket Enderchest")):
        if not copy_texture(root, "items/" + texture, "item/%s.png" % name, wrong_size):
            missing.append("items/" + texture)
        write("assets/%s/models/item/%s.json" % (NS, name), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "%s:item/%s" % (NS, name)},
        })
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)},
        })
        fr["item.%s.%s" % (NS, name)] = label_fr
        en["item.%s.%s" % (NS, name)] = label_en
    # La fabrique est un BlockItem : sans cle propre, elle prendrait le nom du
    # bloc. CloudItem.getName ajoute la duree choisie.
    fr["block.%s.cloud" % NS] = "Fabrique a nuage"
    en["block.%s.cloud" % NS] = "Cloud Factory"
    fr["item.%s.cloud.named" % NS] = "%s [%s]"
    en["item.%s.cloud.named" % NS] = "%s [%s]"
    fr["tooltip.%s.cloud.duration" % NS] = "Duree du nuage : %s"
    en["tooltip.%s.cloud.duration" % NS] = "Cloud duration: %s"
    fr["tooltip.%s.cloud.cycle" % NS] = "Maj + clic droit : changer la duree"
    en["tooltip.%s.cloud.cycle" % NS] = "Shift + right-click: change the duration"
    fr["tooltip.%s.pocket_enderchest" % NS] = "Clic droit : ton ender chest, hors combat"
    en["tooltip.%s.pocket_enderchest" % NS] = "Right-click: your ender chest, out of combat"

    # ---- controleur de spawners (PalaSpawner 7.0.0) ----
    # Ses textures ne sont que dans son jar : le dossier voisin de celui de
    # PalaMod, comme pour le golem gardien.
    spawner_root = os.path.join(os.path.dirname(root[1].rstrip("/")), "PalaSpawner-7.0.0") if root[1] else None

    def spawner_tex(relative, destination):
        source = os.path.join(spawner_root or "", "assets", "palaspawner", "textures", relative)
        if not spawner_root or not os.path.exists(source):
            missing.append("palaspawner:" + relative)
            return
        if png_size(source) != (16, 16):
            wrong_size.append("palaspawner:%s : %s au lieu de 16 x 16" % (relative, png_size(source)))
            return
        target = os.path.join(RES, "assets", NS, "textures", destination)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copyfile(source, target)

    structure_metals = (("one", "diamant", "Diamond", "minecraft:diamond", "minecraft:obsidian"),
                        ("two", "amethyste", "Amethyst", "%s:amethyst_ingot" % NS, "%s:spawner_structure_one" % NS),
                        ("three", "titane", "Titanium", "%s:titane_ingot" % NS, "%s:spawner_structure_two" % NS),
                        ("four", "paladium", "Paladium", "%s:paladium_ingot" % NS, "%s:spawner_structure_three" % NS))
    for tier, metal_fr, metal_en, metal, base in structure_metals:
        name = "spawner_structure_" + tier
        spawner_tex("blocks/%s.png" % name, "block/%s.png" % name)
        cube(name, "%s:block/%s" % (NS, name))
        block_item(name)
        drops_itself(name)
        label("block", name, "Obsidienne infusee (%s)" % metal_fr, "Infused Obsidian (%s)" % metal_en)
        # PPalaSpawnerCommonProxy.registerCrafts : MOM / OBO / MOM.
        shaped(name, ["MOM", "OBO", "MOM"], {"M": metal, "O": base, "B": "%s:paladium_block" % NS},
               "%s:%s" % (NS, name))
        tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, name)])
        tag("minecraft/tags/block/needs_diamond_tool.json", ["%s:%s" % (NS, name)])

    spawner_tex("blocks/spawn_controller.png", "block/spawner_controller.png")
    cube("spawner_controller", "%s:block/spawner_controller" % NS)
    block_item("spawner_controller")
    drops_itself("spawner_controller")
    label("block", "spawner_controller", "Controleur de spawner", "Spawner Controller")
    tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:spawner_controller" % NS])
    shaped("spawner_controller", ["OOO", "PSP", "OOO"], {
        "O": "minecraft:obsidian", "P": "%s:compressed_paladium" % NS, "S": "%s:empty_mob_spawner" % NS,
    }, "%s:spawner_controller" % NS)

    spawner_upgrades = (
        ("speed", "Amelioration de vitesse de spawner", "Spawner Speed Upgrade",
         "Un dixieme de temps en moins entre deux vagues", "One tenth less time between waves",
         {"O": "minecraft:obsidian", "P": "%s:compressed_paladium" % NS, "S": "minecraft:sugar"}),
        ("looting", "Amelioration de butin de spawner", "Spawner Looting Upgrade",
         "Dix chances sur cent de doubler le butin d'une creature", "10% chance to double a creature's loot",
         {"O": "minecraft:obsidian", "P": "%s:compressed_paladium" % NS, "S": "%s:paladium_sword" % NS}),
        ("slime", "Amelioration slime de spawner", "Spawner Slime Upgrade",
         "Une petite chance d'un slime en plus par creature", "A small chance of an extra slime per creature",
         {"O": "minecraft:obsidian", "P": "%s:compressed_paladium" % NS, "S": "%s:slimepad" % NS}),
    )
    for key, label_fr, label_en, tip_fr, tip_en, keys in spawner_upgrades:
        name = "upgrade_" + key
        spawner_tex("items/spawner_upgrade_%s.png" % key, "item/%s.png" % name)
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, label_fr, label_en)
        fr["item.%s.spawner_upgrade.%s" % (NS, key)] = tip_fr
        en["item.%s.spawner_upgrade.%s" % (NS, key)] = tip_en
        shaped(name, ["OOO", "PSP", "OOO"], keys, "%s:%s" % (NS, name))
    spawner_tex("items/spawner_upgrade_more.png", "item/upgrade_more.png")
    flat_item("upgrade_more", "%s:item/upgrade_more" % NS)
    label("item", "upgrade_more", "Amelioration de capacite de spawner", "Spawner Capacity Upgrade")
    fr["item.%s.spawner_upgrade.more" % NS] = "Ouvre les deux cases verrouillees, autorise les doublons"
    en["item.%s.spawner_upgrade.more" % NS] = "Opens both locked slots and allows duplicates"
    fr["item.%s.spawner_upgrade.where" % NS] = "Se pose dans un controleur de spawner"
    en["item.%s.spawner_upgrade.where" % NS] = "Goes into a spawner controller"
    # OWO / PSP / OBO : crane de wither squelette, modificateur « plus
    # d'ameliorations » du grinder, pepites d'endium, spawner vide.
    shaped("upgrade_more", ["OWO", "PSP", "OBO"], {
        "O": "minecraft:obsidian", "W": "minecraft:wither_skeleton_skull",
        "S": "%s:more_upgrade_modifier" % NS, "P": "%s:endium_nugget" % NS, "B": "%s:empty_mob_spawner" % NS,
    }, "%s:upgrade_more" % NS)

    for key, label_fr, label_en in (
            ("chunk", "Un seul controleur de spawner travaille par troncon.",
             "Only one spawner controller works per chunk."),
            ("invalid", "Votre structure n'est pas valide.", "Your structure is not valid."),
            ("complete", "La structure IV est complete.", "The tier IV structure is complete."),
            ("missing", "Structure %s : %s / %s blocs. Il manque : %s en %s %s %s.",
             "Structure %s: %s / %s blocks. Missing: %s at %s %s %s.")):
        fr["message.%s.spawner_controller.%s" % (NS, key)] = label_fr
        en["message.%s.spawner_controller.%s" % (NS, key)] = label_en

    # ---- boxes de la boutique (jar PalaShop) ----
    # Le bloc ne dessine rien (le coffre anime est une creature, voir
    # BoxBlockEntity) : son modele ne porte que la texture des eclats de casse.
    # L'icone est la vignette de la boutique de Paladium (1000 x 1000),
    # reduite a 32 x 32.
    shop_root = os.path.join(os.path.dirname(root[1].rstrip("/")), "PalaShop-7.8.0") if root[1] else None
    for box, label_fr, label_en, particle in (
            ("amethyst", "Box Amethyste", "Amethyst Box", "%s:block/amethyst_block" % NS),
            ("paladium", "Box Paladium", "Paladium Box", "%s:block/paladium_block" % NS),
            ("endium", "Box Endium", "Endium Box", "%s:block/endium_block" % NS),
            ("saharia", "Box Saharia", "Saharia Box", "minecraft:block/sandstone_top"),
            ("trixium", "Box Trixium", "Trixium Box", "%s:block/trixium_block" % NS),
            ("halloween", "Box Halloween", "Halloween Box", "minecraft:block/pumpkin_side")):
        name = "box_" + box
        thumbnail = os.path.join(shop_root or "", "assets", "palashop", "boxes", box, "thumbnail.png")
        if shop_root and os.path.exists(thumbnail):
            shrink_icon(thumbnail, "assets/%s/textures/item/%s.png" % (NS, name), 32)
        else:
            missing.append("palashop:boxes/%s/thumbnail.png" % box)
        write("assets/%s/blockstates/%s.json" % (NS, name),
              {"variants": {"": {"model": "%s:block/%s" % (NS, name)}}})
        write("assets/%s/models/block/%s.json" % (NS, name), {"textures": {"particle": particle}})
        flat_item(name, "%s:item/%s" % (NS, name))
        drops_itself(name)
        label("block", name, label_fr, label_en)
        tag("minecraft/tags/block/mineable/pickaxe.json", ["%s:%s" % (NS, name)])

    # Les cles : une par box, avec l'illustration de sa cle dans PalaShop
    # (boxes/<nom>/item.png, 1000 x 1000), reduite comme les vignettes. Les
    # trois caisses d'avant (commune, rare, legendaire) sont les boxes
    # d'amethyste, de paladium et d'endium : leurs cles gardent leur image.
    for box, label_fr, label_en in (
            ("amethyst", "Cle Amethyste", "Amethyst Key"),
            ("saharia", "Cle Saharia", "Saharia Key"),
            ("paladium", "Cle Paladium", "Paladium Key"),
            ("halloween", "Cle Halloween", "Halloween Key"),
            ("trixium", "Cle Trixium", "Trixium Key"),
            ("endium", "Cle Endium", "Endium Key")):
        name = "crate_key_" + box
        art = os.path.join(shop_root or "", "assets", "palashop", "boxes", box, "item.png")
        if shop_root and os.path.exists(art):
            shrink_icon(art, "assets/%s/textures/item/%s.png" % (NS, name), 32)
        else:
            missing.append("palashop:boxes/%s/item.png" % box)
        flat_item(name, "%s:item/%s" % (NS, name))
        label("item", name, label_fr, label_en)
    fr["item.%s.crate_key.use" % NS] = "Clic droit : ouvre sa box a la roulette"
    en["item.%s.crate_key.use" % NS] = "Right-click: opens its box on the wheel"
    fr["item.%s.crate_key.box" % NS] = "Sur le coffre pose de sa box : il s'ouvre, avec son animation"
    en["item.%s.crate_key.box" % NS] = "On its box's placed chest: it opens, with its animation"

    # ---- ecriture des tags accumules ----
    for path, entries in sorted(collected_tags.items()):
        write("data/%s" % path, {"replace": False, "values": sorted(entries)})

    # ---- langues ----
    fr["itemGroup.%s.materials" % NS] = "PalaMod — minerais et materiaux"
    en["itemGroup.%s.materials" % NS] = "PalaMod — Ores and Materials"
    for code, table in (("fr_fr", fr), ("en_us", en)):
        write("assets/%s/lang/%s.json" % (NS, code),
              {key: table[key] for key in sorted(table)})

    # ---- rapport ----
    print("textures : %d du pack officiel, %d du jar"
          % (provenance["pack"], len(provenance["jar"])))
    if provenance["jar"]:
        print("  absentes du pack, reprises du jar :")
        for path in sorted(provenance["jar"]):
            print("    " + path)
    print("items   : %d" % len(ITEMS))
    print("blocs   : %d (%d minerais, %d blocs de stockage)"
          % (len(blocks), len(ORES), len(STORAGE)))
    print("langues : %d cles" % len(fr))
    if missing:
        print("\nTEXTURES ABSENTES (%d) — a corriger, elles donneront du violet :" % len(missing))
        for path in missing:
            print("  " + path)
    if wrong_size:
        print("\nTEXTURES A LA MAUVAISE TAILLE (%d) — probablement un homonyme,"
              " verifier le sous-dossier :" % len(wrong_size))
        for line in wrong_size:
            print("  " + line)
    if missing or wrong_size:
        return 1
    print("\ntoutes les textures ont ete trouvees, toutes en %dx%d" % EXPECTED_SIZE)
    return 0


if __name__ == "__main__":
    sys.exit(main())

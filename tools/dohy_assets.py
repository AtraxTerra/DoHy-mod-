#!/usr/bin/env python3
"""Genere les ressources du contenu propre a DoHy (espace de noms `dohy`).

Le contenu repris de Paladium a son generateur (`palamod_assets.py`), qui lit
les textures du pack officiel. Ce qui ne vient pas de Paladium n'a pas de pack
ou puiser : ses textures sont **dessinees ici**, pixel par pixel, et rien n'est
copie d'un autre mod.

    python3 -I tools/dohy_assets.py

Le script ecrit dans `src/main/resources` : textures, modeles, etats de bloc,
definitions d'objet, tables de butin et recettes. Il est idempotent.

Une seule chose lui echappe : le tag `minecraft:mineable/pickaxe`, qui vit dans
un fichier que `palamod_assets.py` reecrit. C'est lui qui y inscrit les blocs
d'ici (chercher `dohy:` dans ce script).
"""

import json
import os
import struct
import zlib

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
NS = "dohy"


def write(path, payload):
    full = os.path.join(RES, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def write_png(path, rows):
    """Ecrit une PNG RGBA depuis des lignes de couleurs (r, g, b) ; None est transparent."""
    height = len(rows)
    width = len(rows[0])
    raw = bytearray()
    for row in rows:
        raw.append(0)
        for pixel in row:
            if pixel is None:
                raw.extend((0, 0, 0, 0))
            else:
                raw.extend((pixel[0], pixel[1], pixel[2], 255))

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    full = os.path.join(RES, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
                 + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def rgb(code):
    return ((code >> 16) & 255, (code >> 8) & 255, code & 255)


# -- Le serveur de trading ---------------------------------------------------------
#
# Une baie informatique : un chassis d'acier sombre, trois tiroirs en facade,
# chacun avec deux voyants, des ouies et une poignee. Allumee, les voyants
# passent au vert et a l'ambre.

EDGE = rgb(0x4B515C)      # l'arete du chassis
PANEL = rgb(0x3A3F48)     # la tole
SHADE = rgb(0x2B2F36)     # la tole dans l'ombre
DARK = rgb(0x1B1D22)      # le fond d'un tiroir, une ouie
SHINE = rgb(0x6A717D)     # un reflet, une poignee
RIVET = rgb(0x7C8490)
LED_OFF_A = rgb(0x1D3A24)
LED_OFF_B = rgb(0x4A3510)
LED_ON_A = rgb(0x3DFF6A)
LED_ON_B = rgb(0xFFB52E)
LED_ON_C = rgb(0x36A3FF)


def canvas(color):
    return [[color for _ in range(16)] for _ in range(16)]


def frame(rows):
    """Le cadre du chassis : une arete claire en haut et a gauche, sombre en bas et a droite."""
    for i in range(16):
        rows[0][i] = EDGE
        rows[i][0] = EDGE
        rows[15][i] = SHADE
        rows[i][15] = SHADE
    rows[0][15] = PANEL
    rows[15][0] = PANEL
    return rows


def server_front(lit):
    rows = frame(canvas(PANEL))
    for unit in range(3):
        top = 2 + unit * 4
        for y in range(top, top + 3):
            for x in range(2, 14):
                rows[y][x] = DARK
        # deux voyants a gauche
        first = (LED_ON_A if lit else LED_OFF_A)
        second = ((LED_ON_B if unit != 1 else LED_ON_C) if lit else LED_OFF_B)
        rows[top + 1][3] = first
        rows[top + 1][5] = second
        # les ouies : des traits clairs sur fond sombre
        for x in range(7, 12):
            rows[top][x] = SHADE
            rows[top + 2][x] = SHADE
            if x % 2 == 1:
                rows[top + 1][x] = PANEL
        # la poignee du tiroir
        rows[top][12] = SHINE
        rows[top + 1][12] = SHINE
        rows[top + 2][12] = SHINE
    return rows


def server_side():
    rows = frame(canvas(PANEL))
    for y in (4, 6, 8, 10):
        for x in range(3, 13):
            rows[y][x] = DARK
        for x in range(3, 13):
            rows[y + 1][x] = SHADE if y != 10 else rows[y + 1][x]
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        rows[y][x] = RIVET
    return rows


def server_top():
    rows = frame(canvas(PANEL))
    for y in range(3, 13):
        for x in range(3, 13):
            rows[y][x] = DARK if (x + y) % 2 == 0 else SHADE
    for i in range(2, 14):
        rows[2][i] = SHINE
        rows[13][i] = SHADE
        rows[i][2] = SHINE
        rows[i][13] = SHADE
    return rows


def trading_server():
    name = "trading_server"
    write_png("assets/%s/textures/block/%s_front.png" % (NS, name), server_front(False))
    write_png("assets/%s/textures/block/%s_front_on.png" % (NS, name), server_front(True))
    write_png("assets/%s/textures/block/%s_side.png" % (NS, name), server_side())
    write_png("assets/%s/textures/block/%s_top.png" % (NS, name), server_top())

    for suffix, front in (("", "_front"), ("_on", "_front_on")):
        write("assets/%s/models/block/%s%s.json" % (NS, name, suffix), {
            "parent": "minecraft:block/orientable",
            "textures": {
                "top": "%s:block/%s_top" % (NS, name),
                "front": "%s:block/%s%s" % (NS, name, front),
                "side": "%s:block/%s_side" % (NS, name),
            },
        })

    variants = {}
    for facing, angle in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in (False, True):
            variant = {"model": "%s:block/%s%s" % (NS, name, "_on" if lit else "")}
            if angle:
                variant["y"] = angle
            variants["facing=%s,lit=%s" % (facing, "true" if lit else "false")] = variant
    write("assets/%s/blockstates/%s.json" % (NS, name), {"variants": variants})

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

    # Avec NTM NEXT : la machine s'assemble dans sa machine d'assemblage, avec ses pieces.
    # Le nom du fichier est celui que NTM donne a la recette ; il doit rester unique.
    write("data/%s/recipe/assembly_machine/ass.dohy_trading_server.json" % NS, {
        "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "hbm"}],
        "type": "hbm:assembly_machine",
        "duration": 600,
        "input_items": [
            {"count": 8, "ingredient": "#c:plates/steel"},
            {"count": 4, "ingredient": "hbm:circuit_advanced"},
            {"count": 2, "ingredient": "hbm:crt_display"},
            {"ingredient": "hbm:motor"},
            {"count": 4, "ingredient": "palamod:paladium_ingot"},
        ],
        "order": 60000,
        "output_items": ["%s:%s" % (NS, name)],
        "power": 500,
    })

    # Sans NTM NEXT : une recette d'etabli, pour que le bloc ne soit pas inaccessible.
    write("data/%s/recipe/%s.json" % (NS, name), {
        "neoforge:conditions": [{
            "type": "neoforge:not",
            "value": {"type": "neoforge:mod_loaded", "modid": "hbm"},
        }],
        "type": "minecraft:crafting_shaped",
        "category": "redstone",
        "pattern": ["IRI", "DPD", "IGI"],
        "key": {
            "I": "minecraft:iron_block",
            "R": "minecraft:redstone_block",
            "D": "minecraft:diamond",
            "P": "palamod:paladium_ingot",
            "G": "minecraft:gold_block",
        },
        "result": {"id": "%s:%s" % (NS, name), "count": 1},
    })


# -- L'agriculture -----------------------------------------------------------------
#
# Neuf cereales, chacune avec sa culture (huit stades), sa semence, son grain et
# sa farine ; dix pains ; deux produits d'elevage ; une mangeoire, un abreuvoir,
# une machine a OGM, un moulin, une machine a pain, un biogenerateur. Tous les
# sprites sont traces ici a partir de quelques couleurs par cereale : rien ne
# vient d'un autre mod.

# cle, nom francais, nom anglais, OGM, tige jeune, tige mure, epi, epi sombre, forme de l'epi
CEREALS = [
    ("corn", "Mais", "Corn", False, 0x4E9A3A, 0x7FA33C, 0xF2C230, 0xC8931E, "cob"),
    ("barley", "Orge", "Barley", False, 0x5FA544, 0xCDA945, 0xE3BE4E, 0xB08A2C, "awn"),
    ("oat", "Avoine", "Oat", False, 0x5AA548, 0xBFB36A, 0xE8DCA0, 0xBFB070, "drop"),
    ("rye", "Seigle", "Rye", False, 0x4F8F4A, 0xA89A5E, 0xC9B27A, 0x8F7A4A, "long"),
    ("sunflower", "Tournesol", "Sunflower", False, 0x4E9A3A, 0x6C9A3A, 0xF6C21E, 0x5E3F1A, "disk"),
    ("soy", "Soja", "Soy", False, 0x3F8E3B, 0x6C9A3A, 0x9BBF4A, 0x6E8F30, "pod"),
    ("fonio", "Fonio", "Fonio", True, 0x4CA36A, 0x9FC58A, 0xF1EEDC, 0xBFC0A8, "spray"),
    ("sorghum", "Sorgho", "Sorghum", True, 0x4A9440, 0x8E8A3C, 0xB2452F, 0x7C2A1E, "cluster"),
    ("kamut", "Kamut", "Kamut", True, 0x58A04A, 0xD0A040, 0xF0A63A, 0xB5741E, "big"),
]

# La couleur du ble, pour sa farine et le pain du mineur.
WHEAT_HEAD = 0xDCC27A

# Les pains : cle, nom francais, nom anglais, couleur de mie, brille (pain d'OGM), garniture.
BREADS = [
    ("corn_bread", "Pain de mais", "Cornbread", 0xF2C230, False, None),
    ("oat_bread", "Pain d'avoine", "Oat Bread", 0xE8DCA0, False, None),
    ("rye_bread", "Pain de seigle", "Rye Bread", 0x8F7A4A, False, None),
    ("barley_bread", "Pain d'orge", "Barley Bread", 0xE3BE4E, False, None),
    ("soy_bread", "Pain de soja", "Soy Bread", 0x9BBF4A, False, None),
    ("sunflower_bread", "Pain de tournesol", "Sunflower Bread", 0xF6C21E, False, 0x3A2A16),
    ("miner_bread", "Pain du mineur", "Miner's Bread", WHEAT_HEAD, False, 0x4FD0E0),
    ("gingerbread", "Pain d'epices", "Gingerbread", 0x7A3F12, False, 0xFFF3D6),
    ("fonio_bread", "Pain de fonio", "Fonio Bread", 0xF1EEDC, True, None),
    ("sorghum_bread", "Pain de sorgho", "Sorghum Bread", 0xB2452F, True, None),
    ("kamut_bread", "Pain de kamut", "Kamut Bread", 0xF0A63A, True, None),
]

# Les infobulles des pains (le code fait foi : agri/Bread.java).
BREAD_LORE_FR = {
    "corn_bread": ["Un pain simple"],
    "oat_bread": ["Un pain simple, plus nourrissant"],
    "rye_bread": ["Le plus nourrissant des pains simples"],
    "barley_bread": ["Rapidite I et Celerite I, 20 s  -  +1 coeur"],
    "soy_bread": ["Regeneration II, 5 s  -  +1,5 coeur"],
    "sunflower_bread": ["Vol de vigueur I, 1 min"],
    "miner_bread": ["Flair du mineur, 5 min"],
    "gingerbread": ["Regeneration I 10 s, Resistance I 1 min  -  +2 coeurs"],
    "fonio_bread": ["Absorption I et Dernier rempart, 30 s  -  +2 coeurs"],
    "sorghum_bread": ["Force II, Rapidite II, Absorption I, 2 min  -  +2 coeurs",
                      "Regeneration II 30 s  -  Vol de vigueur II 1 min"],
    "kamut_bread": ["Leve les effets nefastes, puis Immunite 2 min"],
}
BREAD_LORE_EN = {
    "corn_bread": ["A plain bread"],
    "oat_bread": ["A plain bread, more filling"],
    "rye_bread": ["The most filling plain bread"],
    "barley_bread": ["Speed I and Haste I, 20 s  -  +1 heart"],
    "soy_bread": ["Regeneration II, 5 s  -  +1.5 hearts"],
    "sunflower_bread": ["Life Steal I, 1 min"],
    "miner_bread": ["Prospecting, 5 min"],
    "gingerbread": ["Regeneration I 10 s, Resistance I 1 min  -  +2 hearts"],
    "fonio_bread": ["Absorption I and Last Stand, 30 s  -  +2 hearts"],
    "sorghum_bread": ["Strength II, Speed II, Absorption I, 2 min  -  +2 hearts",
                      "Regeneration II 30 s  -  Life Steal II 1 min"],
    "kamut_bread": ["Clears harmful effects, then Immunity 2 min"],
}


def blank():
    return [[None for _ in range(16)] for _ in range(16)]


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(color, factor):
    return tuple(max(0, min(255, int(round(c * factor)))) for c in color)


def put(rows, x, y, color):
    if 0 <= x < 16 and 0 <= y < 16:
        rows[y][x] = color


STALKS = (2, 5, 8, 11, 14)
WOBBLE = (0, -1, 1, -1, 0)
HEIGHTS = (2, 4, 5, 7, 9, 11, 12, 13)


def crop_stage(cereal, stage):
    """Un stade de culture : cinq tiges qui montent, puis l'epi qui se forme et murit."""
    key, _, _, _, young, ripe, head, head_dark, shape = cereal
    young, ripe, head, head_dark = rgb(young), rgb(ripe), rgb(head), rgb(head_dark)
    rows = blank()
    ripeness = (0.0, 0.0, 0.0, 0.0, 0.1, 0.3, 0.65, 1.0)[stage]
    stalk = mix(young, ripe, ripeness)
    leaf = shade(stalk, 1.15)
    height_cap = 9 if shape == "pod" else 13
    for index, x in enumerate(STALKS):
        height = max(1, min(height_cap, HEIGHTS[stage] + WOBBLE[index]))
        top = 16 - height
        for y in range(top, 16):
            put(rows, x, y, stalk if (y + index) % 4 else shade(stalk, 0.85))
        # des feuilles, d'un cote puis de l'autre
        for step, y in enumerate(range(15, top + 1, -3)):
            side = -1 if (step + index) % 2 else 1
            put(rows, x + side, y - 1, leaf)
        if stage < 5:
            continue
        grown = stage - 4            # 1, 2, 3 : l'epi grossit
        tone = mix(mix(young, head, 0.5), head, (grown - 1) / 2.0)
        dark = mix(mix(young, head_dark, 0.5), head_dark, (grown - 1) / 2.0)
        if shape == "cob":
            # l'epi de mais pousse a mi-tige, dans sa gaine ; une aigrette en haut
            base = top + 4
            for dy in range(grown + 1):
                put(rows, x + 1, base + dy, tone if dy % 2 == 0 else dark)
            put(rows, x + 1, base - 1, leaf)
            put(rows, x, top - 1, shade(head, 1.1))
        elif shape == "drop":
            for dy in range(grown):
                put(rows, x + (1 if dy % 2 == 0 else -1), top + dy, tone if dy % 2 == 0 else dark)
        elif shape == "long":
            for dy in range(grown + 1):
                put(rows, x, top - 1 + dy, tone if dy % 2 == 0 else dark)
            put(rows, x, top - 2, dark)
        elif shape == "awn":
            for dy in range(grown):
                put(rows, x, top + dy, tone if dy % 2 == 0 else dark)
            put(rows, x - 1, top - 1, shade(head, 1.15))
            put(rows, x + 1, top - 1, shade(head, 1.15))
            if grown == 3:
                put(rows, x, top - 2, shade(head, 1.15))
        elif shape == "pod":
            for dy in range(grown):
                put(rows, x + (1 if index % 2 == 0 else -1), top + 2 + dy * 2, tone)
                put(rows, x + (1 if index % 2 == 0 else -1), top + 3 + dy * 2, dark)
        elif shape == "spray":
            put(rows, x, top - 1, tone)
            put(rows, x - 1, top, tone if grown > 1 else dark)
            put(rows, x + 1, top, tone if grown > 1 else dark)
            if grown == 3:
                put(rows, x - 1, top - 2, dark)
                put(rows, x + 1, top - 2, dark)
        elif shape == "cluster":
            for dy in range(grown):
                put(rows, x, top - 1 + dy, tone)
                put(rows, x - 1, top + dy, dark if dy % 2 else tone)
                put(rows, x + 1, top + dy, tone if dy % 2 else dark)
        elif shape == "disk":
            # le capitule du tournesol : un coeur sombre, des petales autour
            put(rows, x, top - 1, dark if grown > 1 else tone)
            if grown > 1:
                petal = head if grown == 3 else tone
                for (dx, dy) in ((-1, -1), (1, -1), (0, -2), (0, 0)):
                    put(rows, x + dx, top + dy, petal)
        else:  # big
            for dy in range(grown + 1):
                put(rows, x, top - 1 + dy, tone if dy % 2 == 0 else dark)
                put(rows, x + 1, top - 1 + dy, dark if dy % 2 == 0 else tone)
    return rows


OUTLINE = rgb(0x2A2118)


def seeds_icon(cereal):
    head, head_dark = rgb(cereal[6]), rgb(cereal[7])
    rows = blank()
    for (x, y) in ((4, 5), (9, 4), (6, 8), (11, 8), (4, 11), (9, 11)):
        put(rows, x, y, head)
        put(rows, x + 1, y, shade(head, 1.12))
        put(rows, x, y + 1, head_dark)
        put(rows, x + 1, y + 1, head)
        put(rows, x - 1, y + 1, OUTLINE)
        put(rows, x + 2, y + 1, OUTLINE)
        put(rows, x, y + 2, OUTLINE)
        put(rows, x + 1, y + 2, OUTLINE)
    return rows


def grain_icon(cereal):
    """Un epi en diagonale, sa tige en bas a gauche ; le mais et le soja ont leur forme."""
    ripe, head, head_dark, shape = rgb(cereal[5]), rgb(cereal[6]), rgb(cereal[7]), cereal[8]
    rows = blank()
    for i in range(5):                       # la tige
        put(rows, 2 + i, 13 - i, ripe)
        put(rows, 2 + i, 14 - i, shade(ripe, 0.75))
    if shape == "cob":
        for i in range(7):
            for w in (-1, 0, 1):
                put(rows, 7 + i + w, 8 - i, head if (i + w) % 2 == 0 else head_dark)
            put(rows, 7 + i - 2, 8 - i, OUTLINE)
            put(rows, 7 + i + 2, 8 - i, OUTLINE)
        put(rows, 5, 10, shade(ripe, 1.1))
        put(rows, 6, 11, shade(ripe, 1.1))
    elif shape == "disk":
        # un capitule vu de face : coeur sombre, couronne de petales
        for y in range(1, 12):
            for x in range(4, 15):
                dx, dy = x - 9, y - 6
                distance = dx * dx + dy * dy
                if distance <= 6:
                    put(rows, x, y, head_dark if (x + y) % 3 else shade(head_dark, 1.35))
                elif distance <= 26 and (abs(dx) != abs(dy) or distance <= 13):
                    put(rows, x, y, head if (x + y) % 2 == 0 else shade(head, 0.86))
    elif shape == "pod":
        for i in range(7):
            put(rows, 6 + i, 9 - i + (1 if i in (2, 3, 4) else 0), head)
            put(rows, 6 + i, 10 - i + (1 if i in (2, 3, 4) else 0), head_dark)
        for (x, y) in ((8, 8), (10, 6)):
            put(rows, x, y, shade(head, 1.2))
    else:
        for i in range(8):                   # les grains, de part et d'autre de l'axe
            put(rows, 6 + i, 9 - i, head_dark)
            if i % 2 == 0:
                put(rows, 5 + i, 8 - i, head)
                put(rows, 7 + i, 10 - i, head)
            else:
                put(rows, 5 + i, 8 - i, shade(head, 1.12))
                put(rows, 7 + i, 10 - i, shade(head, 0.9))
        if shape in ("awn", "spray", "long"):
            put(rows, 14, 0, shade(head, 1.15))
            put(rows, 13, 0, shade(head, 1.15))
            put(rows, 15, 2, shade(head, 1.15))
    return rows


def bread_icon(crumb, sparkle, topping):
    head = rgb(crumb)
    crust = mix(rgb(0xB8782E), head, 0.35)
    rows = blank()
    for y in range(5, 12):
        for x in range(2, 14):
            corner = (x in (2, 13) and y in (5, 11))
            if corner:
                continue
            edge = x in (2, 13) or y in (5, 11)
            color = shade(crust, 0.7) if edge else (shade(crust, 1.12) if y < 8 else crust)
            put(rows, x, y, color)
    for x in (5, 8, 11):                     # les entailles du boulanger
        put(rows, x, 7, shade(head, 1.15))
        put(rows, x - 1, 8, shade(head, 1.15))
    for x in range(3, 13):
        put(rows, x, 12, OUTLINE)
    if topping is not None:                  # des graines ou des eclats sur la croute
        for (x, y) in ((4, 6), (7, 9), (10, 6), (12, 9), (6, 10), (9, 7)):
            put(rows, x, y, rgb(topping))
    if sparkle:                              # un pain d'OGM brille
        for (x, y) in ((3, 3), (12, 4), (14, 9)):
            put(rows, x, y, rgb(0xFFFFFF))
        put(rows, 3, 2, rgb(0xCFF5D0))
        put(rows, 2, 3, rgb(0xCFF5D0))
        put(rows, 4, 3, rgb(0xCFF5D0))
        put(rows, 3, 4, rgb(0xCFF5D0))
    return rows


def flour_icon(color, sparkle):
    """Un sac de toile ouvert, la farine a ras bord, une pastille de la couleur du grain."""
    cloth, cloth_dark, cloth_light = rgb(0xC9B48A), rgb(0x8F7B55), rgb(0xE2D2AC)
    grain = rgb(color)
    flour = mix(rgb(0xFBF7EC), grain, 0.22)
    rows = blank()
    for y in range(5, 14):
        inset = 1 if y in (5, 13) else 0
        for x in range(3 + inset, 13 - inset):
            edge = x in (3 + inset, 12 - inset) or y == 13
            put(rows, x, y, cloth_dark if edge else (cloth_light if x < 6 else cloth))
    for x in range(4, 12):                   # la farine, bombee
        put(rows, x, 4, flour)
    for x in range(5, 11):
        put(rows, x, 3, flour if x % 2 else shade(flour, 0.93))
    for x in range(6, 10):
        put(rows, x, 2, flour)
    for x in range(4, 12):                   # l'ourlet du sac
        put(rows, x, 5, cloth_dark if x % 2 else cloth)
    for y in range(8, 11):                   # la pastille
        for x in range(6, 10):
            put(rows, x, y, grain if (x, y) not in ((6, 8), (9, 8), (6, 10), (9, 10)) else cloth)
    put(rows, 7, 9, shade(grain, 0.75))
    if sparkle:
        put(rows, 13, 2, rgb(0xFFFFFF))
        put(rows, 2, 7, rgb(0xCFF5D0))
    return rows


def effect_icon(kind):
    """Une icone d'effet, 18 x 18 comme celles du jeu."""
    rows = [[None for _ in range(18)] for _ in range(18)]

    def dot(x, y, color):
        if 0 <= x < 18 and 0 <= y < 18:
            rows[y][x] = rgb(color)

    if kind == "life_steal":                 # un coeur, une goutte qui y monte
        heart = ["..XX...XX..", ".XXXX.XXXX.", "XXXXXXXXXXX", "XXXXXXXXXXX", "XXXXXXXXXXX", ".XXXXXXXXX.",
                 "..XXXXXXX..", "...XXXXX...", "....XXX....", ".....X....."]
        for y, line in enumerate(heart):
            for x, cell in enumerate(line):
                if cell == "X":
                    dot(3 + x, 3 + y, 0xC2374B if (x + y) % 5 else 0xE0566A)
        for (x, y) in ((5, 5), (6, 4), (6, 5)):
            dot(x, y, 0xFFB3BF)
        for (x, y) in ((14, 13), (14, 14), (13, 15), (14, 15), (15, 15), (14, 16)):
            dot(x, y, 0xFF8496)
    elif kind == "last_stand":               # un bouclier fendu qui tient
        for y in range(2, 16):
            half = 6 if y < 9 else max(0, 6 - (y - 8))
            for x in range(9 - half, 9 + half):
                border = x in (9 - half, 9 + half - 1) or y == 2
                dot(x, y, 0x8A6A1C if border else (0xE8C34A if x < 9 else 0xC9A130))
        for (x, y) in ((9, 4), (8, 5), (9, 6), (10, 7), (9, 8), (8, 9), (9, 10)):
            dot(x, y, 0x3A2A08)
    elif kind == "immunity":                 # une croix dans un halo
        for y in range(1, 17):
            for x in range(1, 17):
                dx, dy = x - 8.5, y - 8.5
                distance = dx * dx + dy * dy
                if 42 <= distance <= 58:
                    dot(x, y, 0x7FD8C8)
        for y in range(4, 14):
            for x in range(4, 14):
                if 7 <= x <= 10 or 7 <= y <= 10:
                    dot(x, y, 0xF4FFFB if (7 <= x <= 10 and 7 <= y <= 10) else 0xB9F0E4)
    else:                                    # prospecting : une pioche et un eclat
        for i in range(9):
            dot(4 + i, 13 - i, 0x8B5A2B)
            dot(5 + i, 13 - i, 0x6A421C)
        for (x, y) in ((8, 3), (9, 3), (10, 3), (11, 4), (12, 4), (13, 5), (14, 6), (14, 7), (15, 8), (15, 9)):
            dot(x, y, 0xC9D2DA)
            dot(x, y + 1, 0x8E99A3)
        for (x, y) in ((3, 4), (4, 3), (4, 4), (4, 5), (5, 4)):
            dot(x, y, 0x8E7CC3)
        dot(4, 4, 0xE4DBFF)
        for (x, y) in ((13, 14), (14, 13), (14, 14), (14, 15), (15, 14)):
            dot(x, y, 0x4FD0E0)
    return rows


def enamel(color):
    """Une tole emaillee : la couleur, une arete claire en haut a gauche, sombre en bas a droite."""
    base = rgb(color)
    rows = [[base if (x * 5 + y * 3) % 17 else shade(base, 0.96) for x in range(16)] for y in range(16)]
    for i in range(16):
        rows[0][i] = shade(base, 1.18)
        rows[i][0] = shade(base, 1.18)
        rows[15][i] = shade(base, 0.62)
        rows[i][15] = shade(base, 0.62)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        rows[y][x] = shade(base, 0.55)
    return rows


def machine_side(color):
    rows = enamel(color)
    base = rgb(color)
    for y in (5, 7, 9):                      # des ouies
        for x in range(4, 12):
            rows[y][x] = shade(base, 0.45)
            rows[y + 1][x] = shade(base, 1.12)
    return rows


def machine_top(color, kind):
    rows = enamel(color)
    base = rgb(color)
    if kind == "flour_mill":                 # la tremie : un entonnoir carre
        for y in range(3, 13):
            for x in range(3, 13):
                ring = min(x - 3, 12 - x, y - 3, 12 - y)
                rows[y][x] = shade(base, (0.5, 0.42, 0.34, 0.26, 0.2)[min(ring, 4)])
        for (x, y) in ((7, 7), (8, 8), (7, 8), (8, 7)):
            rows[y][x] = rgb(0xE8D9A8)
    elif kind == "bread_machine":            # une plaque chauffante
        for y in range(3, 13):
            for x in range(3, 13):
                rows[y][x] = rgb(0x2E2A28) if (x + y) % 2 else rgb(0x3A3532)
        for x in range(4, 12):
            rows[5][x] = rgb(0x5A4A40)
            rows[10][x] = rgb(0x5A4A40)
    else:                                    # le biogenerateur : une trappe et son joint
        for y in range(4, 12):
            for x in range(4, 12):
                dx, dy = x - 7.5, y - 7.5
                distance = dx * dx + dy * dy
                if distance <= 17:
                    rows[y][x] = shade(base, 0.5) if distance > 11 else rgb(0x2E4A2C)
        rows[7][7] = rgb(0x9BE08A)
        rows[8][8] = rgb(0x9BE08A)
    return rows


def machine_front(color, kind, lit):
    rows = enamel(color)
    base = rgb(color)
    dark = shade(base, 0.4)
    if kind == "flour_mill":
        # la meule, vue de face, et la goulotte ou tombe la farine
        stone, stone_dark = rgb(0xB8B4AA), rgb(0x7E7A70)
        for y in range(2, 12):
            for x in range(3, 13):
                dx, dy = x - 7.5, y - 6.5
                distance = dx * dx + dy * dy
                if distance <= 22:
                    spoke = (x + y) % 4 == (1 if lit else 0)
                    rows[y][x] = stone_dark if distance > 17 or spoke else stone
        rows[6][7] = dark
        rows[6][8] = dark
        rows[7][7] = dark
        rows[7][8] = dark
        for x in range(6, 10):
            rows[12][x] = dark
            rows[13][x] = rgb(0xF6EFD8) if lit else shade(base, 0.7)
    elif kind == "bread_machine":
        # la porte du four et sa vitre
        for y in range(3, 12):
            for x in range(3, 13):
                border = x in (3, 12) or y in (3, 11)
                if border:
                    rows[y][x] = dark
                elif lit:
                    rows[y][x] = rgb(0xFFB02E) if (x + y) % 3 else rgb(0xFFD76A)
                else:
                    rows[y][x] = rgb(0x231B18) if (x + y) % 3 else rgb(0x2E2420)
        for x in range(5, 11):                # la miche derriere la vitre
            rows[9][x] = rgb(0xB8782E) if lit else rgb(0x3A2A20)
        for x in range(6, 10):
            rows[8][x] = rgb(0xD99A48) if lit else rgb(0x40302A)
        for x in range(4, 12):                # la poignee
            rows[13][x] = rgb(0xD8DCE0)
    else:
        # la cuve vitree du biogenerateur, et son voyant
        liquid = rgb(0x8FE86A) if lit else rgb(0x3B6A34)
        liquid_dark = rgb(0x4FB83C) if lit else rgb(0x2A4A26)
        for y in range(3, 13):
            for x in range(3, 10):
                border = x in (3, 9) or y in (3, 12)
                if border:
                    rows[y][x] = dark
                elif y < 6:
                    rows[y][x] = rgb(0xBFE4EA)
                else:
                    rows[y][x] = liquid if (x * 3 + y) % 4 else liquid_dark
        if lit:
            for (x, y) in ((5, 8), (7, 10), (6, 6)):
                rows[y][x] = rgb(0xEAFFD8)
        bolt = rgb(0xFFE14A) if lit else shade(base, 0.55)
        for (x, y) in ((12, 4), (11, 5), (11, 6), (12, 6), (13, 6), (12, 7), (12, 8), (11, 9)):
            rows[y][x] = bolt
        rows[12][12] = rgb(0x3DFF6A) if lit else rgb(0x1D3A24)
    return rows


def cream_icon():
    rows = blank()
    bowl, bowl_dark, cream, cream_dark = rgb(0x9A6B3F), rgb(0x6E4A29), rgb(0xFFF6DF), rgb(0xE6D8B4)
    for y in range(8, 13):
        inset = (0, 0, 1, 2, 3)[y - 8]
        for x in range(2 + inset, 14 - inset):
            put(rows, x, y, bowl if y < 11 else bowl_dark)
    for x in range(2, 14):
        put(rows, x, 7, bowl_dark)
    for x in range(3, 13):
        put(rows, x, 6, cream)
    for x in range(4, 12):
        put(rows, x, 5, cream if x % 3 else cream_dark)
    for x in range(6, 10):
        put(rows, x, 4, cream)
    put(rows, 7, 3, cream)
    return rows


def lard_icon():
    rows = blank()
    fat, fat_dark, edge = rgb(0xFBEFE6), rgb(0xE9CFC0), rgb(0xB89684)
    for y in range(5, 12):
        for x in range(3, 13):
            border = x in (3, 12) or y in (5, 11)
            put(rows, x, y, edge if border else (fat if (x + y) % 5 else fat_dark))
    for x in range(4, 12):
        put(rows, x, 6, rgb(0xFFFFFF))
    return rows


def wood_texture(seed_color):
    base = rgb(seed_color)
    rows = [[base for _ in range(16)] for _ in range(16)]
    for y in range(16):
        for x in range(16):
            tone = 1.0
            if y % 4 == 3:
                tone = 0.72                  # le joint entre deux planches
            elif (x * 7 + y * 3) % 11 == 0:
                tone = 0.88                  # une veine
            elif (x + y * 5) % 13 == 0:
                tone = 1.1
            rows[y][x] = shade(base, tone)
    return rows


def stone_texture():
    base = rgb(0x8A8D91)
    rows = [[base for _ in range(16)] for _ in range(16)]
    for y in range(16):
        for x in range(16):
            tone = 1.0
            if y % 8 == 7 or (x + (4 if (y // 8) % 2 else 0)) % 8 == 7:
                tone = 0.7                   # le mortier
            elif (x * 5 + y * 9) % 7 == 0:
                tone = 0.9
            elif (x * 3 + y * 11) % 17 == 0:
                tone = 1.1
            rows[y][x] = shade(base, tone)
    return rows


def fill_texture(color, light):
    base, bright = rgb(color), rgb(light)
    rows = [[base for _ in range(16)] for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if (x * 3 + y * 7) % 9 == 0:
                rows[y][x] = bright
            elif (x * 5 + y * 2) % 11 == 0:
                rows[y][x] = shade(base, 0.82)
    return rows


def basin_model(shell, fill, level):
    """Un bac : un fond, quatre parois, et le contenu a la hauteur du niveau."""
    all_faces = {face: {"texture": "#shell"} for face in ("north", "south", "east", "west", "up", "down")}
    elements = [
        {"from": [0, 0, 0], "to": [16, 2, 16], "faces": all_faces},
        {"from": [0, 2, 0], "to": [16, 8, 2], "faces": all_faces},
        {"from": [0, 2, 14], "to": [16, 8, 16], "faces": all_faces},
        {"from": [0, 2, 2], "to": [2, 8, 14], "faces": all_faces},
        {"from": [14, 2, 2], "to": [16, 8, 14], "faces": all_faces},
    ]
    textures = {"shell": shell, "particle": shell}
    if level > 0:
        textures["fill"] = fill
        elements.append({"from": [2, 2, 2], "to": [14, 2 + level * 1.75, 14],
                         "faces": {"up": {"texture": "#fill"}}})
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def gmo_textures():
    panel, edge, dark = rgb(0xD9DEE2), rgb(0xAEB6BD), rgb(0x6F7880)
    glass, liquid, liquid_light = rgb(0x9FD8E8), rgb(0x3FBF5A), rgb(0x8FE8A0)
    side = [[panel for _ in range(16)] for _ in range(16)]
    for i in range(16):
        side[0][i] = edge
        side[15][i] = dark
        side[i][0] = edge
        side[i][15] = dark
    for y in range(3, 13):                   # l'eprouvette
        for x in range(5, 11):
            border = x in (5, 10) or y in (3, 12)
            side[y][x] = dark if border else (glass if y < 7 else (liquid_light if (x + y) % 3 == 0 else liquid))
    for (x, y) in ((2, 3), (13, 3), (2, 12), (13, 12)):
        side[y][x] = dark
    side[5][12] = rgb(0x3DFF6A)
    side[7][12] = rgb(0xFFB52E)
    top = [[panel for _ in range(16)] for _ in range(16)]
    for i in range(16):
        top[0][i] = edge
        top[15][i] = dark
        top[i][0] = edge
        top[i][15] = dark
    for y in range(4, 12):                   # la boite de culture
        for x in range(4, 12):
            dx, dy = x - 7.5, y - 7.5
            distance = dx * dx + dy * dy
            if distance <= 16:
                top[y][x] = dark if distance > 10.5 else (liquid_light if (x * 3 + y) % 4 == 0 else liquid)
    bottom = [[shade(dark, 0.8) for _ in range(16)] for _ in range(16)]
    return side, top, bottom


def block_drops_itself(name, survives=True):
    entry = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, name)}]}
    if survives:
        entry["conditions"] = [{"condition": "minecraft:survives_explosion"}]
    write("data/%s/loot_table/blocks/%s.json" % (NS, name), {"type": "minecraft:block", "pools": [entry]})


def generated_item(name):
    write("assets/%s/models/item/%s.json" % (NS, name), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "%s:item/%s" % (NS, name)},
    })
    write("assets/%s/items/%s.json" % (NS, name), {
        "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)},
    })


def agriculture():
    for cereal in CEREALS:
        key, gmo = cereal[0], cereal[3]
        crop = key + "_crop"
        # la culture : huit stades, huit textures, comme le ble
        variants = {}
        for stage in range(8):
            write_png("assets/%s/textures/block/%s_stage_%d.png" % (NS, crop, stage), crop_stage(cereal, stage))
            write("assets/%s/models/block/%s_stage_%d.json" % (NS, crop, stage), {
                "parent": "minecraft:block/crop",
                "textures": {"crop": "%s:block/%s_stage_%d" % (NS, crop, stage)},
            })
            variants["age=%d" % stage] = {"model": "%s:block/%s_stage_%d" % (NS, crop, stage)}
        write("assets/%s/blockstates/%s.json" % (NS, crop), {"variants": variants})

        # le butin. Une cereale naturelle : mure, un a deux grains ; la semence
        # revient toujours, et la culture mure en rend d'autres. Un OGM : mure,
        # deux a quatre grains et AUCUNE semence — elle ne revient que si la
        # culture est arrachee avant d'etre mure.
        mature = {"condition": "minecraft:block_state_property", "block": "%s:%s" % (NS, crop),
                  "properties": {"age": "7"}}
        grain_pool = {"rolls": 1, "conditions": [mature], "entries": [{
            "type": "minecraft:item", "name": "%s:%s" % (NS, key),
            "functions": [
                {"function": "minecraft:set_count",
                 "count": {"type": "minecraft:uniform", "min": 2 if gmo else 1, "max": 4 if gmo else 2}},
                {"function": "minecraft:explosion_decay"},
            ],
        }]}
        seed_entry = {"type": "minecraft:item", "name": "%s:%s_seeds" % (NS, key)}
        if gmo:
            pools = [grain_pool, {
                "rolls": 1, "conditions": [{"condition": "minecraft:inverted", "term": mature}],
                "entries": [seed_entry],
            }]
        else:
            pools = [grain_pool, {"rolls": 1, "entries": [seed_entry]}, {
                "rolls": 1, "conditions": [mature], "entries": [dict(seed_entry, functions=[{
                    "function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                    "formula": "minecraft:binomial_with_bonus_count",
                    "parameters": {"extra": 2, "probability": 0.5714286},
                }, {"function": "minecraft:explosion_decay"}])],
            }]
        write("data/%s/loot_table/blocks/%s.json" % (NS, crop), {"type": "minecraft:block", "pools": pools})

        write_png("assets/%s/textures/item/%s_seeds.png" % (NS, key), seeds_icon(cereal))
        write_png("assets/%s/textures/item/%s.png" % (NS, key), grain_icon(cereal))
        write_png("assets/%s/textures/item/%s_flour.png" % (NS, key), flour_icon(cereal[6], gmo))
        for item in (key + "_seeds", key, key + "_flour"):
            generated_item(item)

    write_png("assets/%s/textures/item/wheat_flour.png" % NS, flour_icon(WHEAT_HEAD, False))
    generated_item("wheat_flour")

    # les pains : ils ne sortent que de la machine a pain, il n'y a donc pas de recette d'etabli
    for (key, _fr, _en, crumb, sparkle, topping) in BREADS:
        write_png("assets/%s/textures/item/%s.png" % (NS, key), bread_icon(crumb, sparkle, topping))
        generated_item(key)

    for effect in ("life_steal", "last_stand", "immunity", "prospecting"):
        write_png("assets/%s/textures/mob_effect/%s.png" % (NS, effect), effect_icon(effect))

    write_png("assets/%s/textures/item/cream.png" % NS, cream_icon())
    write_png("assets/%s/textures/item/lard.png" % NS, lard_icon())
    generated_item("cream")
    generated_item("lard")

    # la mangeoire et l'abreuvoir : le meme bac, en bois ou en pierre
    write_png("assets/%s/textures/block/trough.png" % NS, wood_texture(0x9A6B3F))
    write_png("assets/%s/textures/block/trough_feed.png" % NS, fill_texture(0xD9B24A, 0xF1D67C))
    write_png("assets/%s/textures/block/water_dispenser.png" % NS, stone_texture())
    write_png("assets/%s/textures/block/water_dispenser_water.png" % NS, fill_texture(0x3B6FD0, 0x6FA0EC))
    for name, fill in (("trough", "trough_feed"), ("water_dispenser", "water_dispenser_water")):
        variants = {}
        for level in range(4):
            write("assets/%s/models/block/%s_%d.json" % (NS, name, level),
                  basin_model("%s:block/%s" % (NS, name), "%s:block/%s" % (NS, fill), level))
            variants["fill=%d" % level] = {"model": "%s:block/%s_%d" % (NS, name, level)}
        write("assets/%s/blockstates/%s.json" % (NS, name), {"variants": variants})
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:block/%s_0" % (NS, name)},
        })
        block_drops_itself(name)

    write("data/%s/recipe/trough.json" % NS, {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["P P", "PPP"], "key": {"P": "#minecraft:planks"},
        "result": {"id": "%s:trough" % NS, "count": 1},
    })
    write("data/%s/recipe/water_dispenser.json" % NS, {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["S S", "SBS"], "key": {"S": "minecraft:cobblestone", "B": "minecraft:bucket"},
        "result": {"id": "%s:water_dispenser" % NS, "count": 1},
    })

    # la machine a OGM
    side, top, bottom = gmo_textures()
    write_png("assets/%s/textures/block/gmo_machine_side.png" % NS, side)
    write_png("assets/%s/textures/block/gmo_machine_top.png" % NS, top)
    write_png("assets/%s/textures/block/gmo_machine_bottom.png" % NS, bottom)
    write("assets/%s/models/block/gmo_machine.json" % NS, {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": "%s:block/gmo_machine_top" % NS,
            "bottom": "%s:block/gmo_machine_bottom" % NS,
            "side": "%s:block/gmo_machine_side" % NS,
        },
    })
    write("assets/%s/blockstates/gmo_machine.json" % NS,
          {"variants": {"": {"model": "%s:block/gmo_machine" % NS}}})
    write("assets/%s/items/gmo_machine.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:block/gmo_machine" % NS},
    })
    block_drops_itself("gmo_machine")
    write("data/%s/recipe/gmo_machine.json" % NS, {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["IGI", "DBD", "IRI"],
        "key": {
            "I": "minecraft:iron_ingot", "G": "minecraft:glass", "D": "minecraft:diamond",
            "B": "minecraft:bone_block", "R": "minecraft:redstone_block",
        },
        "result": {"id": "%s:gmo_machine" % NS, "count": 1},
    })

    # le moulin, la machine a pain, le biogenerateur : trois toles emaillees, une facade chacune
    machines = (
        ("flour_mill", 0xCBBE9E, "minecraft:grindstone", "minecraft:smooth_stone"),
        ("bread_machine", 0xA65A45, "minecraft:furnace", "minecraft:bricks"),
        ("biogenerator", 0x5E8C5A, "minecraft:composter", "minecraft:glass"),
    )
    for order, (name, color, core, shell) in enumerate(machines):
        write_png("assets/%s/textures/block/%s_front.png" % (NS, name), machine_front(color, name, False))
        write_png("assets/%s/textures/block/%s_front_on.png" % (NS, name), machine_front(color, name, True))
        write_png("assets/%s/textures/block/%s_side.png" % (NS, name), machine_side(color))
        write_png("assets/%s/textures/block/%s_top.png" % (NS, name), machine_top(color, name))
        for suffix, front in (("", "_front"), ("_on", "_front_on")):
            write("assets/%s/models/block/%s%s.json" % (NS, name, suffix), {
                "parent": "minecraft:block/orientable",
                "textures": {
                    "top": "%s:block/%s_top" % (NS, name),
                    "front": "%s:block/%s%s" % (NS, name, front),
                    "side": "%s:block/%s_side" % (NS, name),
                },
            })
        variants = {}
        for facing, angle in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for lit in (False, True):
                variant = {"model": "%s:block/%s%s" % (NS, name, "_on" if lit else "")}
                if angle:
                    variant["y"] = angle
                variants["facing=%s,lit=%s" % (facing, "true" if lit else "false")] = variant
        write("assets/%s/blockstates/%s.json" % (NS, name), {"variants": variants})
        write("assets/%s/items/%s.json" % (NS, name), {
            "model": {"type": "minecraft:model", "model": "%s:block/%s" % (NS, name)},
        })
        block_drops_itself(name)

        # Avec NTM NEXT : la machine d'assemblage. Sans : l'etabli.
        write("data/%s/recipe/assembly_machine/ass.dohy_%s.json" % (NS, name), {
            "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "hbm"}],
            "type": "hbm:assembly_machine",
            "duration": 200,
            "input_items": [
                {"count": 6, "ingredient": "#c:plates/steel"},
                {"ingredient": "hbm:circuit_basic"},
                {"ingredient": "hbm:motor"},
                {"ingredient": core},
            ],
            "order": 60010 + order,
            "output_items": ["%s:%s" % (NS, name)],
            "power": 100,
        })
        write("data/%s/recipe/%s.json" % (NS, name), {
            "neoforge:conditions": [{
                "type": "neoforge:not",
                "value": {"type": "neoforge:mod_loaded", "modid": "hbm"},
            }],
            "type": "minecraft:crafting_shaped",
            "category": "redstone",
            "pattern": ["ISI", "SCS", "IRI"],
            "key": {"I": "minecraft:iron_ingot", "S": shell, "C": core, "R": "minecraft:redstone_block"},
            "result": {"id": "%s:%s" % (NS, name), "count": 1},
        })

    # les farines et les pains, pour les contrats de production ; les cultures de cereales, pour ceux de recolte
    write("data/%s/tags/item/flours.json" % NS, {
        "replace": False,
        "values": ["%s:wheat_flour" % NS] + ["%s:%s_flour" % (NS, cereal[0]) for cereal in CEREALS],
    })
    write("data/%s/tags/item/breads.json" % NS, {
        "replace": False,
        "values": ["%s:%s" % (NS, bread[0]) for bread in BREADS],
    })
    write("data/%s/tags/block/cereal_crops.json" % NS, {
        "replace": False,
        "values": ["%s:%s_crop" % (NS, cereal[0]) for cereal in CEREALS],
    })

    # ce qu'une mangeoire et un biogenerateur acceptent : les grains d'ici, et le ble
    write("data/%s/tags/item/cereals.json" % NS, {
        "replace": False,
        "values": ["minecraft:wheat"] + ["%s:%s" % (NS, cereal[0]) for cereal in CEREALS],
    })


# -- Le spawn ------------------------------------------------------------------------
#
# La dimension (type, biome, generateur vide), la borne et ses sept ecrans, le bloc
# invisible des etiquettes. Le lieu lui-meme est dessine par spawn/SpawnBuilder.java.

# service, couleur de l'ecran, pictogramme 8 x 8
TERMINALS = [
    ("shop", 0x3DFF6A, ["..XXXX..", ".X.XX.X.", ".X.XX...", "..XXXX..", "...XX.X.", ".X.XX.X.", "..XXXX..", "...XX..."]),
    ("boutique", 0xFAC400, ["........", ".XXX....", "X...X...", "X...XXXX", "X...X.XX", ".XXX..X.", "........", "........"]),
    ("market", 0xFFB52E, ["......X.", "......X.", "...X..X.", "...X..X.", "X..X..X.", "X..X..X.", "X..X..X.", "XXXXXXXX"]),
    ("bank", 0x36A3FF, ["..XXXX..", ".X....X.", "X..XX..X", "X.X....X", "X.X....X", "X..XX..X", ".X....X.", "..XXXX.."]),
    ("contracts", 0x7C9BFF, [".XXXXX..", ".X...XX.", ".X.XX.X.", ".X....X.", ".X.XX.X.", ".X....X.", ".X.XX.X.", ".XXXXXX."]),
    ("museum", 0xEC4FB5, ["...XX...", "..XXXX..", ".XXXXXX.", ".X.XX.X.", ".X.XX.X.", ".X.XX.X.", ".XXXXXX.", "XXXXXXXX"]),
    ("return", 0xFF5A4A, ["........", "...X....", "..XX....", ".XXXXXXX", ".XXXXXXX", "..XX....", "...X....", "........"]),
]


def terminal_front(color, glyph):
    rows = canvas(PANEL)
    frame(rows)
    screen, glow = rgb(0x0E1114), rgb(color)
    for y in range(2, 12):                   # l'ecran
        for x in range(2, 14):
            border = x in (2, 13) or y in (2, 11)
            rows[y][x] = DARK if border else screen
    for y, line in enumerate(glyph):         # le pictogramme
        for x, cell in enumerate(line):
            if cell == "X":
                rows[3 + y][4 + x] = glow
    for x in range(3, 13):                   # la tablette et son voyant
        rows[13][x] = SHADE
    rows[13][12] = glow
    rows[13][3] = SHINE
    return rows


def terminal_side():
    rows = canvas(PANEL)
    frame(rows)
    for y in (4, 6, 8):
        for x in range(4, 12):
            rows[y][x] = DARK
            rows[y + 1][x] = SHINE
    return rows


def terminal_top():
    rows = canvas(PANEL)
    frame(rows)
    for y in range(4, 12):
        for x in range(4, 12):
            rows[y][x] = SHADE if (x + y) % 2 else DARK
    return rows


def spawn():
    sky = {"minecraft:visual/sky_color": "#7fa8e8", "minecraft:visual/fog_color": "#c9d8ee"}
    write("data/%s/worldgen/biome/spawn.json" % NS, {
        "attributes": sky,
        "carvers": [],
        "downfall": 0.0,
        "effects": {"water_color": "#3f76e4"},
        "features": [],
        "has_precipitation": False,
        "spawn_costs": {},
        "spawners": {group: [] for group in (
            "ambient", "axolotls", "creature", "misc", "monster", "underground_water_creature",
            "water_ambient", "water_creature")},
        "temperature": 0.8,
    })
    # Le ciel de l'Overworld, son horloge et son cycle ; les nuages plus bas que la place.
    write("data/%s/dimension_type/spawn.json" % NS, {
        "ambient_light": 0.12,
        "attributes": dict(sky, **{
            "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never"},
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:visual/ambient_light_color": "#0a0a0a",
            "minecraft:visual/cloud_color": "#e6ffffff",
            "minecraft:visual/cloud_height": 168.33,
        }),
        "coordinate_scale": 1.0,
        "default_clock": "minecraft:overworld",
        "has_ceiling": False,
        "has_ender_dragon_fight": False,
        "has_skylight": True,
        "height": 384,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": 384,
        "min_y": 0,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": 0,
        "timelines": "#minecraft:in_overworld",
    })
    write("data/%s/dimension/spawn.json" % NS, {
        "type": "%s:spawn" % NS,
        "generator": {
            "type": "minecraft:flat",
            "settings": {
                "biome": "%s:spawn" % NS,
                "features": False,
                "lakes": False,
                "layers": [],
                "structure_overrides": [],
            },
        },
    })

    write_png("assets/%s/textures/block/terminal_side.png" % NS, terminal_side())
    write_png("assets/%s/textures/block/terminal_top.png" % NS, terminal_top())
    variants = {}
    for (kind, color, glyph) in TERMINALS:
        write_png("assets/%s/textures/block/terminal_%s.png" % (NS, kind), terminal_front(color, glyph))
        write("assets/%s/models/block/terminal_%s.json" % (NS, kind), {
            "parent": "minecraft:block/orientable",
            "textures": {
                "top": "%s:block/terminal_top" % NS,
                "front": "%s:block/terminal_%s" % (NS, kind),
                "side": "%s:block/terminal_side" % NS,
            },
        })
        for facing, angle in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            variant = {"model": "%s:block/terminal_%s" % (NS, kind)}
            if angle:
                variant["y"] = angle
            variants["facing=%s,kind=%s" % (facing, kind)] = variant
    write("assets/%s/blockstates/terminal.json" % NS, {"variants": variants})
    write("assets/%s/items/terminal.json" % NS, {
        "model": {"type": "minecraft:model", "model": "%s:block/terminal_shop" % NS},
    })
    block_drops_itself("terminal")

    # l'etiquette flottante : un bloc sans forme, il lui faut quand meme un modele
    write("assets/%s/models/block/hologram.json" % NS, {"textures": {"particle": "%s:block/terminal_top" % NS}})
    write("assets/%s/blockstates/hologram.json" % NS, {"variants": {"": {"model": "%s:block/hologram" % NS}}})


def lang():
    fr = {
        "itemGroup.dohy.machines": "DoHy — machines",
        "itemGroup.dohy.agriculture": "DoHy — agriculture",
        "block.dohy.trading_server": "Serveur de trading",
        "block.dohy.terminal": "Borne du spawn",
        "block.dohy.hologram": "Etiquette flottante",
        "block.dohy.trough": "Mangeoire",
        "block.dohy.water_dispenser": "Abreuvoir",
        "block.dohy.gmo_machine": "Machine a OGM",
        "item.dohy.cream": "Creme",
        "item.dohy.lard": "Saindoux",
    }
    en = {
        "itemGroup.dohy.machines": "DoHy — machines",
        "itemGroup.dohy.agriculture": "DoHy — farming",
        "block.dohy.trading_server": "Trading Server",
        "block.dohy.terminal": "Spawn Terminal",
        "block.dohy.hologram": "Floating Label",
        "block.dohy.trough": "Trough",
        "block.dohy.water_dispenser": "Water Dispenser",
        "block.dohy.gmo_machine": "GMO Machine",
        "item.dohy.cream": "Cream",
        "item.dohy.lard": "Lard",
    }
    fr.update({
        "block.dohy.flour_mill": "Moulin",
        "block.dohy.bread_machine": "Machine a pain",
        "block.dohy.biogenerator": "Biogenerateur",
        "item.dohy.wheat_flour": "Farine de ble",
        "effect.dohy.life_steal": "Vol de vigueur",
        "effect.dohy.last_stand": "Dernier rempart",
        "effect.dohy.immunity": "Immunite",
        "effect.dohy.prospecting": "Flair du mineur",
        "tooltip.dohy.climate.cold": "Climat froid : taiga, toundra, sommets",
        "tooltip.dohy.climate.temperate": "Climat tempere : plaines, forets, marais",
        "tooltip.dohy.climate.hot": "Climat chaud : jungle, savane, desert",
        "tooltip.dohy.climate.gmo": "OGM : pousse partout, ne rend aucune semence",
    })
    en.update({
        "block.dohy.flour_mill": "Flour Mill",
        "block.dohy.bread_machine": "Bread Machine",
        "block.dohy.biogenerator": "Biogenerator",
        "item.dohy.wheat_flour": "Wheat Flour",
        "effect.dohy.life_steal": "Life Steal",
        "effect.dohy.last_stand": "Last Stand",
        "effect.dohy.immunity": "Immunity",
        "effect.dohy.prospecting": "Prospecting",
        "tooltip.dohy.climate.cold": "Cold climate: taiga, tundra, peaks",
        "tooltip.dohy.climate.temperate": "Temperate climate: plains, forests, swamps",
        "tooltip.dohy.climate.hot": "Hot climate: jungle, savanna, desert",
        "tooltip.dohy.climate.gmo": "GMO: grows anywhere, never gives seeds back",
    })
    for (key, name_fr, name_en, gmo, *_rest) in CEREALS:
        of = "d'" if name_fr[0] in "AEIOU" else "de "
        fr["block.dohy.%s_crop" % key] = "Culture %s%s" % (of, name_fr.lower())
        en["block.dohy.%s_crop" % key] = "%s Crop" % name_en
        fr["item.dohy.%s_seeds" % key] = "Semences %s%s" % (of, name_fr.lower())
        en["item.dohy.%s_seeds" % key] = "%s Seeds" % name_en
        fr["item.dohy.%s" % key] = name_fr
        en["item.dohy.%s" % key] = name_en
        fr["item.dohy.%s_flour" % key] = "Farine %s%s" % (of, name_fr.lower())
        en["item.dohy.%s_flour" % key] = "%s Flour" % name_en
    for (key, name_fr, name_en, *_rest) in BREADS:
        fr["item.dohy.%s" % key] = name_fr
        en["item.dohy.%s" % key] = name_en
        for index, line in enumerate(BREAD_LORE_FR[key]):
            fr["tooltip.dohy.%s.%d" % (key, index + 1)] = line
        for index, line in enumerate(BREAD_LORE_EN[key]):
            en["tooltip.dohy.%s.%d" % (key, index + 1)] = line
    write("assets/%s/lang/fr_fr.json" % NS, fr)
    write("assets/%s/lang/en_us.json" % NS, en)


def main():
    trading_server()
    agriculture()
    spawn()
    lang()
    print("Ressources dohy ecrites.")


if __name__ == "__main__":
    main()

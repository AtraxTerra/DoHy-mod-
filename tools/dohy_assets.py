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
    """Ecrit une PNG RGBA 16x16 depuis des lignes de couleurs (r, g, b)."""
    height = len(rows)
    width = len(rows[0])
    raw = bytearray()
    for row in rows:
        raw.append(0)
        for (r, g, b) in row:
            raw.extend((r, g, b, 255))

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


def lang():
    fr = {
        "itemGroup.dohy.machines": "DoHy — machines",
        "block.dohy.trading_server": "Serveur de trading",
    }
    en = {
        "itemGroup.dohy.machines": "DoHy — machines",
        "block.dohy.trading_server": "Trading Server",
    }
    write("assets/%s/lang/fr_fr.json" % NS, fr)
    write("assets/%s/lang/en_us.json" % NS, en)


def main():
    trading_server()
    lang()
    print("Ressources dohy ecrites.")


if __name__ == "__main__":
    main()

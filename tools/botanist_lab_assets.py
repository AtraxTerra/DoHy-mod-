#!/usr/bin/env python3
"""Le Botaniste, lot 2a : le pot, les trois plantes de base, l'arrosoir et la guitare.

    python3 -I tools/botanist_lab_assets.py [--preview DOSSIER]
    python3 -I tools/botanist_lab_assets.py --check

Les textures sont dessinees ici, pixel par pixel, en 16 x 16 : le pot (flanc,
rebord, terreau), la pousse et la plante fanee communes, le bourgeon et la fleur
de chaque plante, les graines, l'arrosoir et la guitare. Elles ne reprennent pas
les modeles de JobsReborn (des modeles animes) : ce sont des dessins originaux.

Ecrit aussi les modeles (le pot a elements, la plante en croix plantee dans le
terreau), le blockstate multipart (le pot toujours, la plante selon le stade et
la plante), les objets, la table de butin du pot, les recettes (celles de
JobsReborn pour l'arrosoir et la guitare, une recette a nous pour le pot) et les
noms.

`--check` (CI) refait le JSON et les noms en memoire et echoue si un fichier a
change ou si une texture manque. Pillow n'est requis que pour ecrire les textures.
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "palamod")
DATA = os.path.join(RES, "data", "palamod")
NS = "palamod"

# id : (nom fr, nom en, nom fr de la graine, nom en de la graine). Sans accent : police du jeu.
MUTATIONS = {
    "ironwheat": ("Ble de fer", "Ironwheat", "Graine de ble de fer", "Ironwheat Seed"),
    "moonfrost": ("Givrelune", "Moonfrost", "Graine de givrelune", "Moonfrost Seed"),
    "sand_coral": ("Corail des sables", "Sand Coral", "Graine de corail des sables", "Sand Coral Seed"),
}
POT = ("botanist_pot", "Pot de botaniste", "Botanist Pot")
TOOLS = {
    "watering_can": ("Arrosoir", "Watering Can"),
    "guitar": ("Guitare", "Guitar"),
}

# -- Couleurs --------------------------------------------------------------------------------

# La terre cuite du pot, et son bandeau rouge Paladium.
POT_COLORS = {"0": (104, 54, 36), "1": (146, 80, 52), "2": (178, 102, 68), "3": (206, 132, 92),
              "4": (226, 162, 118), "b": (84, 42, 28), "r": (170, 36, 24), "R": (229, 57, 38)}
SOIL_COLORS = {"d": (46, 30, 20), "m": (66, 44, 28), "l": (88, 60, 38), "p": (110, 78, 50)}
LEAF = {"l": (28, 68, 14), "L": (68, 132, 28), "h": (136, 194, 58), "S": (98, 164, 40)}
WILT = {"w": (118, 88, 50), "W": (86, 62, 36), "d": (150, 118, 74)}
IRON = {"s": (86, 94, 104), "S": (126, 134, 146), "e": (150, 152, 162), "E": (204, 206, 214),
        "g": (220, 178, 70), "l": (28, 68, 14), "L": (68, 132, 28)}
FROST = {"f": (84, 148, 172), "F": (146, 204, 222), "b": (150, 186, 236), "B": (214, 232, 255),
         "w": (255, 255, 255), "p": (186, 218, 252), "P": (238, 246, 255), "y": (255, 232, 140),
         "Y": (255, 248, 206)}
CORAL = {"c": (198, 116, 68), "C": (228, 158, 96), "t": (246, 204, 140), "k": (238, 108, 100),
         "K": (255, 156, 140)}
# Les graines : une rampe de six tons par plante, sombre -> clair.
SEED_RAMPS = {
    "ironwheat": [(36, 38, 44), (68, 72, 80), (108, 114, 124), (150, 156, 166), (198, 200, 208), (236, 222, 160)],
    "moonfrost": [(26, 54, 92), (52, 100, 154), (98, 160, 216), (160, 206, 246), (212, 234, 255), (255, 248, 206)],
    "sand_coral": [(104, 52, 26), (156, 88, 46), (198, 128, 72), (230, 170, 104), (246, 206, 146), (255, 150, 136)],
}
CAN = {"0": (40, 44, 52), "1": (78, 86, 96), "2": (124, 132, 144), "3": (174, 182, 192), "4": (220, 226, 232),
       "r": (176, 38, 26), "R": (236, 64, 44), "a": (96, 168, 236)}
WOOD = {"0": (58, 32, 16), "1": (104, 60, 28), "2": (150, 90, 44), "3": (194, 130, 70), "4": (226, 172, 104),
        "h": (30, 18, 10), "s": (236, 236, 236), "k": (210, 180, 60)}

# -- Motifs (16 x 16, « . » transparent) ----------------------------------------------------

# Le flanc : rebord (lignes 5-6, sur les bandes du rebord), ombre, bandeau, panse, pied.
POT_SIDE = [
    "1111111111111111",
    "1111111111111111",
    "1111111111111111",
    "1111111111111111",
    "1111111111111111",
    "4444444444444444",
    "2333333333333332",
    "0000000000000000",
    "1233211112111110",
    "RRRRRRRRRRRRRRRR",
    "rrrrrrrrrrrrrrrr",
    "1233211121111210",
    "1232111112111110",
    "1123211211111210",
    "0122111111111100",
    "bbbbbbbbbbbbbbbb",
]
# Le dessus du rebord : un anneau (1 a 15, troue de 3 a 13), arete interieure plus sombre.
POT_TOP = [
    "1111111111111111",
    "1444444444444441",
    "1433333333333341",
    "1430000000000341",
    "1430000000000341",
    "1430000000000341",
    "1430000000000341",
    "1430000000000341",
    "1430000000000341",
    "1430000000000341",
    "1430000000000341",
    "1430000000000341",
    "1430000000000341",
    "1433333333333341",
    "1444444444444441",
    "1111111111111111",
]
SOIL = [
    "mmlmmdmmlmmmdmmm",
    "mdmmmlmmmmdmmlmm",
    "mmmpmmmdmmmmmmdm",
    "lmmmmmmmmlmmpmmm",
    "mmdmmlmmmmmdmmml",
    "mmmmmmmpmmmmmmmm",
    "mlmmdmmmmlmmdmmm",
    "mmmmmmlmmmmmmmlm",
    "mdmmpmmmdmmmmmmm",
    "mmmmmmmmmmmlmdmm",
    "lmmdmmlmmpmmmmmm",
    "mmmmmmmmmmmmdmml",
    "mmlmmdmmmlmmmmmm",
    "mdmmmmmpmmmmlmdm",
    "mmmmlmmmmmdmmmmm",
    "mmdmmmmlmmmmmpmm",
]
SPROUT = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    ".........hh.....",
    "....hL..LLl.....",
    ".....LLlLl......",
    "......lSl.......",
    ".......S........",
    ".......S........",
]
WILTED = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "....dw....wd....",
    "...wWw....wWw...",
    "..ww.ww..ww.ww..",
    "..w...wWWw...w..",
    "......wWWw......",
    ".......Ww.......",
    ".......W........",
    ".......W........",
]
PLANTS = {
    "ironwheat": (IRON, [
        "................",
        "................",
        "................",
        "................",
        "................",
        "......E.........",
        ".....eEe..E.....",
        ".....eEe.eEe....",
        "......S..eEe....",
        "......S...S.....",
        "..E...S...S.....",
        ".eEe..S..S......",
        ".eEe.LS.LS......",
        "..S..lSlS.......",
        "...S..SS........",
        "....SSS.........",
    ], [
        "................",
        "................",
        "................",
        "....E......E....",
        "...eEg....eEe...",
        "...eEe..E.eEg...",
        "...gEe.eEeeEe...",
        "....S..eEg.S....",
        "....S..gEe.S....",
        ".E..S...S..S....",
        "eEe.S...S..S..E.",
        "eEg..S..S.S..eEe",
        "gEe..S..S.S..gEe",
        ".S..LSl.SS.Ll.S.",
        "..S.lLSSSSLl.S..",
        "...SSSSSSSSSSS..",
    ]),
    "moonfrost": (FROST, [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        ".......w........",
        "......bBb.......",
        "......bBb.......",
        ".......F........",
        "...F...F...F....",
        "..fFF..F..FFf...",
        "...fFF.F.FFf....",
        ".....fFFFf......",
        ".......F........",
        ".......F........",
    ], [
        "................",
        "................",
        "................",
        "................",
        ".......P........",
        "..P...pPp...P...",
        "...pP.pPp.Pp....",
        "....pPPYPPp.....",
        ".PpppPYyYPpppP..",
        "....pPPYPPp.....",
        "...pP.pPp.Pp....",
        "..P...pFp...P...",
        "..fF...F...Ff...",
        "...fFF.F.FFf....",
        ".....fFFFf......",
        ".......F........",
    ]),
    "sand_coral": (CORAL, [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "......k...k.....",
        "......c..kc.....",
        "..k...c..c......",
        "..c..cc..c..k...",
        "...c.c..cc..c...",
        "....cCCtC..c....",
        ".....CCCCCC.....",
    ], [
        "................",
        "................",
        "................",
        "....K......K....",
        "....k..K...k.K..",
        "..K.c..k..ck.k..",
        "..k.ct.c.c...c..",
        "...c.c.ct..K.c..",
        "...c..cc...k.c..",
        "....c.c...ct.c..",
        ".K..ct...c..cc..",
        ".k...cC.cC.cc...",
        "..c...CtC.cc....",
        "...cc.CCCcc.....",
        ".....cCtCC......",
        "....CCCCCCCC....",
    ]),
}
# La graine : une amande striee, et sa pointe de pousse.
SEED = [
    "................",
    "................",
    "................",
    ".........hL.....",
    "........lL......",
    ".......005......",
    "......01450.....",
    ".....0124430....",
    ".....0134320....",
    "....01243210....",
    "....01343210....",
    "....0124321.....",
    ".....012210.....",
    "......0110......",
    ".......00.......",
    "................",
]
WATERING_CAN = [
    "................",
    "................",
    "................",
    ".........0000...",
    "........0....0..",
    "a......0.....0..",
    ".a0...00000000..",
    "a.30.0233333320.",
    "...300234444320.",
    "....3023333332.0",
    ".....0RRRRRRRR0.",
    "......rrrrrrrr0.",
    "......02333332..",
    "......01222210..",
    ".......000000...",
    "................",
]
GUITAR = [
    "................",
    ".............kk.",
    "............k0kk",
    "...........0s0k.",
    "..........0s0...",
    ".........0s0....",
    "........0s0.....",
    "...1111s10......",
    "..123344s.......",
    ".1234hh4s1......",
    ".123hhhs31......",
    ".12344s331......",
    "..1233s31.......",
    "..11223311......",
    "...111111.......",
    "................",
]


def draw(rows, colors):
    from PIL import Image

    assert len(rows) == 16 and all(len(row) == 16 for row in rows), "un motif fait 16 x 16"
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, code in enumerate(row):
            if code != ".":
                image.putpixel((x, y), colors[code] + (255,))
    return image


def seed_colors(ramp):
    colors = {str(i): tone for i, tone in enumerate(ramp)}
    colors.update({"l": LEAF["l"], "L": LEAF["L"], "h": LEAF["h"]})
    return colors


def textures():
    images = {
        "textures/block/botanist_pot_side.png": draw(POT_SIDE, POT_COLORS),
        "textures/block/botanist_pot_top.png": draw(POT_TOP, POT_COLORS),
        "textures/block/botanist_pot_soil.png": draw(SOIL, SOIL_COLORS),
        "textures/block/botanist_sprout.png": draw(SPROUT, LEAF),
        "textures/block/botanist_wilted.png": draw(WILTED, WILT),
        "textures/item/watering_can.png": draw(WATERING_CAN, CAN),
        "textures/item/guitar.png": draw(GUITAR, WOOD),
    }
    for mutation, (colors, bud, bloom) in PLANTS.items():
        images["textures/block/%s_bud.png" % mutation] = draw(bud, colors)
        images["textures/block/%s_bloom.png" % mutation] = draw(bloom, colors)
        images["textures/item/%s_seed.png" % mutation] = draw(SEED, seed_colors(SEED_RAMPS[mutation]))
    return images


def texture_names():
    names = ["textures/block/botanist_pot_side.png", "textures/block/botanist_pot_top.png",
             "textures/block/botanist_pot_soil.png", "textures/block/botanist_sprout.png",
             "textures/block/botanist_wilted.png", "textures/item/watering_can.png", "textures/item/guitar.png"]
    for mutation in MUTATIONS:
        names += ["textures/block/%s_bud.png" % mutation, "textures/block/%s_bloom.png" % mutation,
                  "textures/item/%s_seed.png" % mutation]
    return names


# -- Modeles -----------------------------------------------------------------------------------

def face(uv, texture, cull=None):
    entry = {"uv": uv, "texture": texture}
    if cull:
        entry["cullface"] = cull
    return entry


def box(start, end, faces):
    return {"from": start, "to": end, "faces": faces}


def pot_model():
    """La panse (2 a 14, hauteur 9, terreau dessus) et le rebord en quatre bandes (1 a 15, de 9 a 11)."""
    side, top, soil = "#side", "#top", "#soil"
    elements = [
        box([2, 0, 2], [14, 9, 14], {
            "north": face([2, 7, 14, 16], side), "south": face([2, 7, 14, 16], side),
            "west": face([2, 7, 14, 16], side), "east": face([2, 7, 14, 16], side),
            "up": face([2, 2, 14, 14], soil), "down": face([2, 2, 14, 14], side, "down"),
        }),
    ]
    # Les bandes du rebord : chaque face interieure regarde le terreau.
    strips = [
        ([1, 9, 1], [15, 11, 3], [1, 1, 15, 3]),
        ([1, 9, 13], [15, 11, 15], [1, 13, 15, 15]),
        ([1, 9, 3], [3, 11, 13], [1, 3, 3, 13]),
        ([13, 9, 3], [15, 11, 13], [13, 3, 15, 13]),
    ]
    for start, end, uv_top in strips:
        width_x = end[0] - start[0]
        width_z = end[2] - start[2]
        elements.append(box(start, end, {
            "north": face([0, 5, width_x, 7], side), "south": face([0, 5, width_x, 7], side),
            "west": face([0, 5, width_z, 7], side), "east": face([0, 5, width_z, 7], side),
            "up": face(uv_top, top), "down": face(uv_top, top),
        }))
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"side": "%s:block/botanist_pot_side" % NS, "top": "%s:block/botanist_pot_top" % NS,
                         "soil": "%s:block/botanist_pot_soil" % NS, "particle": "%s:block/botanist_pot_side" % NS},
            "elements": elements}


def plant_template():
    """La plante en croix, plantee dans le terreau (y = 9), haute de 13 : les lignes 3 a 15 du dessin."""
    rotation = {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}
    uv = [0, 3, 16, 16]
    return {"ambientocclusion": False, "textures": {"particle": "#plant"}, "elements": [
        {"from": [0.8, 9, 8], "to": [15.2, 22, 8], "rotation": rotation, "shade": False,
         "faces": {"north": face(uv, "#plant"), "south": face(uv, "#plant")}},
        {"from": [8, 9, 0.8], "to": [8, 22, 15.2], "rotation": rotation, "shade": False,
         "faces": {"west": face(uv, "#plant"), "east": face(uv, "#plant")}},
    ]}


def plant(texture):
    return {"parent": "%s:block/botanist_pot_plant" % NS, "textures": {"plant": "%s:block/%s" % (NS, texture)}}


def blockstate():
    parts = [{"apply": {"model": "%s:block/botanist_pot" % NS}},
             {"when": {"stage": "seed"}, "apply": {"model": "%s:block/botanist_pot_sprout" % NS}},
             {"when": {"stage": "wilted"}, "apply": {"model": "%s:block/botanist_pot_wilted" % NS}}]
    for mutation in MUTATIONS:
        parts.append({"when": {"stage": "bud", "plant": mutation},
                      "apply": {"model": "%s:block/botanist_pot_%s_bud" % (NS, mutation)}})
        parts.append({"when": {"stage": "blooming", "plant": mutation},
                      "apply": {"model": "%s:block/botanist_pot_%s_bloom" % (NS, mutation)}})
    return {"multipart": parts}


def shaped(pattern, key, result):
    return {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern, "key": key,
            "result": {"id": "%s:%s" % (NS, result), "count": 1}}


def outputs():
    files = {}

    def put(root, path, payload):
        files[os.path.join(root, path)] = json.dumps(payload, indent=2, ensure_ascii=False) + "\n"

    def item(name, model):
        put(ASSETS, "items/%s.json" % name, {"model": {"type": "minecraft:model", "model": model}})

    put(ASSETS, "models/block/botanist_pot.json", pot_model())
    put(ASSETS, "models/block/botanist_pot_plant.json", plant_template())
    put(ASSETS, "models/block/botanist_pot_sprout.json", plant("botanist_sprout"))
    put(ASSETS, "models/block/botanist_pot_wilted.json", plant("botanist_wilted"))
    for mutation in MUTATIONS:
        put(ASSETS, "models/block/botanist_pot_%s_bud.json" % mutation, plant("%s_bud" % mutation))
        put(ASSETS, "models/block/botanist_pot_%s_bloom.json" % mutation, plant("%s_bloom" % mutation))
        seed = mutation + "_seed"
        put(ASSETS, "models/item/%s.json" % seed,
            {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, seed)}})
        item(seed, "%s:item/%s" % (NS, seed))
    put(ASSETS, "blockstates/botanist_pot.json", blockstate())
    item(POT[0], "%s:block/botanist_pot" % NS)
    for tool in TOOLS:
        put(ASSETS, "models/item/%s.json" % tool,
            {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/%s" % (NS, tool)}})
        item(tool, "%s:item/%s" % (NS, tool))
    put(DATA, "loot_table/blocks/botanist_pot.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s:botanist_pot" % NS}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # JobsBotanistRecipes : l'arrosoir et la guitare.
    put(DATA, "recipe/watering_can.json", shaped(
        ["IWI", "KIK", " K "],
        {"I": "%s:paladium_ingot" % NS, "W": "minecraft:water_bucket", "K": "%s:paladium_stick" % NS},
        "watering_can"))
    put(DATA, "recipe/guitar.json", shaped(
        [" SK", "PNS", "PP "],
        {"S": "%s:diamond_string" % NS, "K": "%s:paladium_stick" % NS, "P": "#minecraft:planks",
         "N": "minecraft:note_block"},
        "guitar"))
    # Le pot n'a pas de recette dans JobsReborn (ceux du laboratoire Genis) : terre cuite et terre.
    recipe = shaped(["T T", "TDT", "TTT"], {"T": "minecraft:terracotta", "D": "minecraft:dirt"}, POT[0])
    recipe["category"] = "misc"
    put(DATA, "recipe/botanist_pot.json", recipe)
    return files


LANG = {
    "fr_fr": {
        "tooltip.palamod.mutation_seed.climate": "Climat : %s",
        "tooltip.palamod.mutation_seed.growth": "Bourgeon en %s, fleur %s plus tard",
        "tooltip.palamod.mutation_seed.harvests": "%s recoltes, une fleur toutes les %s",
        "tooltip.palamod.mutation_seed.pot": "Se plante dans un pot de botaniste. Pousse pres de toi seulement.",
        "tooltip.palamod.botanist.climate.cold": "Glacial",
        "tooltip.palamod.botanist.climate.temperate": "Normal",
        "tooltip.palamod.botanist.climate.hot": "Desertique",
        "tooltip.palamod.watering_can.charges": "Charges : %s",
        "tooltip.palamod.watering_can.fill": "Clic droit sur une source d'eau pour le remplir.",
        "tooltip.palamod.guitar.use": "Joue un air aux plantes qui en reclament.",
        "tooltip.palamod.botanist.tool.use": "Clic droit sur une plante qui le reclame.",
        "message.palamod.botanist_lab.catch_up": "Laboratoire du botaniste, pour tes niveaux de Fermier :",
    },
    "en_us": {
        "tooltip.palamod.mutation_seed.climate": "Climate: %s",
        "tooltip.palamod.mutation_seed.growth": "Bud in %s, flower %s later",
        "tooltip.palamod.mutation_seed.harvests": "%s harvests, a flower every %s",
        "tooltip.palamod.mutation_seed.pot": "Plant in a botanist pot. Only grows near you.",
        "tooltip.palamod.botanist.climate.cold": "Glacial",
        "tooltip.palamod.botanist.climate.temperate": "Normal",
        "tooltip.palamod.botanist.climate.hot": "Desert",
        "tooltip.palamod.watering_can.charges": "Charges: %s",
        "tooltip.palamod.watering_can.fill": "Right-click a water source to fill it.",
        "tooltip.palamod.guitar.use": "Plays a tune to plants that ask for one.",
        "tooltip.palamod.botanist.tool.use": "Right-click a plant that asks for it.",
        "message.palamod.botanist_lab.catch_up": "Botanist lab, for your Farmer levels:",
    },
}


def lang_updates():
    updates = {}
    for code, entries in LANG.items():
        labels = dict(entries)
        french = code == "fr_fr"
        for mutation, (name_fr, name_en, seed_fr, seed_en) in MUTATIONS.items():
            labels["item.%s.%s_seed" % (NS, mutation)] = seed_fr if french else seed_en
        labels["block.%s.%s" % (NS, POT[0])] = POT[1] if french else POT[2]
        for tool, (tool_fr, tool_en) in TOOLS.items():
            labels["item.%s.%s" % (NS, tool)] = tool_fr if french else tool_en
        updates[code] = labels
    return updates


def main():
    check = "--check" in sys.argv
    preview = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    stale = []
    for path, content in outputs().items():
        current = open(path, encoding="utf-8").read() if os.path.exists(path) else None
        if current == content:
            continue
        if check:
            stale.append(os.path.relpath(path, ROOT))
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(content)
    for code, labels in lang_updates().items():
        path = os.path.join(ASSETS, "lang", "%s.json" % code)
        with open(path, encoding="utf-8") as handle:
            lang = json.load(handle)
        missing = {key: value for key, value in labels.items() if lang.get(key) != value}
        if not missing:
            continue
        if check:
            stale.append("%s : %s" % (os.path.relpath(path, ROOT), ", ".join(sorted(missing))))
            continue
        lang.update(missing)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(lang, handle, indent=2, ensure_ascii=False)
            handle.write("\n")
    if not check:
        for relative, image in textures().items():
            target = os.path.join(ASSETS, relative)
            os.makedirs(os.path.dirname(target), exist_ok=True)
            image.save(target)
        if preview:
            sheet(preview)
    for relative in texture_names():
        if not os.path.exists(os.path.join(ASSETS, relative)):
            stale.append("%s absente" % relative)
    if stale:
        for line in stale:
            print("::error::Laboratoire du botaniste : %s n'est pas a jour. "
                  "Lance 'python3 -I tools/botanist_lab_assets.py'." % line)
        sys.exit(1)
    print("Laboratoire du botaniste : pot, %d plantes, graines, arrosoir, guitare, recettes et noms a jour"
          % len(MUTATIONS))


def sheet(folder):
    """Une planche agrandie (x8) de toutes les textures, pour les regarder."""
    from PIL import Image

    images = textures()
    rows = [["textures/block/botanist_pot_side.png", "textures/block/botanist_pot_top.png",
             "textures/block/botanist_pot_soil.png", "textures/block/botanist_sprout.png",
             "textures/block/botanist_wilted.png"]]
    for mutation in MUTATIONS:
        rows.append(["textures/block/%s_bud.png" % mutation, "textures/block/%s_bloom.png" % mutation,
                     "textures/item/%s_seed.png" % mutation])
    rows.append(["textures/item/watering_can.png", "textures/item/guitar.png"])
    scale, gap = 8, 4
    board = Image.new("RGBA", (5 * (16 * scale + gap) + gap, len(rows) * (16 * scale + gap) + gap), (60, 60, 60, 255))
    for r, row in enumerate(rows):
        for c, name in enumerate(row):
            tile = images[name].resize((16 * scale, 16 * scale), Image.NEAREST)
            board.alpha_composite(tile, (gap + c * (16 * scale + gap), gap + r * (16 * scale + gap)))
    os.makedirs(folder, exist_ok=True)
    board.save(os.path.join(folder, "botanist_lab_sheet.png"))


if __name__ == "__main__":
    main()

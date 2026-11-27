#!/usr/bin/env python3
"""Le Botaniste, lot 1 : graines minerales, plants et endium organique.

    python3 -I tools/botanist_assets.py [--preview DOSSIER]
    python3 -I tools/botanist_assets.py --check

Les textures sont dessinees ici, pixel par pixel, en 16 x 16 (le format du
namespace palamod) : quatre stades par plant, une graine par minerai et l'endium
organique. Elles ne reprennent pas celles de JobsReborn ; seules les couleurs
viennent des matieres du mod (lingots, findium, trixium, pierre d'invocation,
pepite d'endium), pour qu'une graine se reconnaisse a son minerai.

Ecrit aussi les modeles (parent minecraft:block/crop, comme les cultures de
PalaMod), les blockstates (age 0 a 3), les objets, les tables de butin (vides :
le plant calcule sa recolte lui-meme, MineralCropBlock.getDrops), la recette de
l'endium organique et les noms.

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

# id : (nom fr de la graine, nom en, nom fr du plant, nom en du plant, rampe de six tons, sombre -> clair)
SEEDS = {
    "amethyst": ("Graine d'amethyste", "Amethyst Seed", "Plant d'amethyste", "Amethyst Plant",
                 [(50, 13, 77), (121, 35, 177), (172, 61, 211), (245, 104, 255), (255, 172, 249), (255, 220, 255)]),
    "titane": ("Graine de titane", "Titanium Seed", "Plant de titane", "Titanium Plant",
               [(38, 34, 34), (65, 59, 56), (95, 89, 84), (143, 137, 129), (168, 163, 156), (214, 212, 208)]),
    "paladium": ("Graine de paladium", "Paladium Seed", "Plant de paladium", "Paladium Plant",
                 [(95, 16, 6), (160, 30, 12), (212, 42, 22), (255, 111, 39), (255, 227, 101), (255, 247, 152)]),
    "green_paladium": ("Graine de paladium vert", "Green Paladium Seed", "Plant de paladium vert",
                       "Green Paladium Plant",
                       [(18, 61, 37), (33, 93, 60), (68, 170, 110), (105, 228, 161), (167, 251, 205), (240, 255, 248)]),
    "findium": ("Graine de findium", "Findium Seed", "Plant de findium", "Findium Plant",
                [(120, 26, 0), (190, 44, 0), (229, 90, 0), (254, 158, 0), (255, 214, 0), (255, 252, 140)]),
    "trixium": ("Graine de trixium", "Trixium Seed", "Plant de trixium", "Trixium Plant",
                [(16, 46, 72), (34, 92, 130), (70, 156, 206), (134, 222, 253), (196, 244, 255), (255, 255, 255)]),
    "invoker": ("Graine d'invoker", "Invoker Seed", "Plant d'invoker", "Invoker Plant",
                [(0, 52, 58), (0, 94, 103), (21, 137, 146), (7, 200, 220), (7, 239, 255), (220, 255, 255)]),
    "endium": ("Graine d'endium", "Endium Seed", "Plant d'endium", "Endium Plant",
               [(4, 4, 68), (13, 12, 103), (16, 15, 138), (43, 46, 189), (60, 62, 238), (227, 163, 255)]),
}
ORGANIC = ("organic_endium_nugget", "Endium organique", "Organic Endium")

# Le feuillage et la tige : la rampe des feuilles des cereales (agri_crop_assets.py).
LEAF = {"l": (28, 68, 14), "L": (68, 132, 28), "h": (136, 194, 58)}
STEM = {"s": (44, 98, 20), "S": (98, 164, 40)}
# L'endium organique : la pepite d'endium mangee de vert.
ORGANIC_COLORS = {"0": (4, 4, 68), "e": (43, 46, 189), "E": (60, 62, 238), "p": (227, 163, 255),
                  "g": (44, 98, 20), "G": (98, 164, 40), "H": (180, 222, 96)}

# Les stades : « . » vide, l/L/h feuilles, s/S tige, 0 a 5 la rampe du minerai.
STAGES = [
    [
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
        "................",
        "................",
        "......L..L......",
        "......lLLl......",
        ".......Ss.......",
        ".......Ss.......",
    ],
    [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "........L.......",
        ".......hL.......",
        ".....L.Ss.L.....",
        "....lLLSsLLl....",
        ".....ll.s.ll....",
        ".......Ss.......",
        ".......Ss.......",
        ".......Ss.......",
    ],
    [
        "................",
        "................",
        "................",
        ".......01.......",
        "......0231......",
        "......0331......",
        ".......Ss.......",
        "....LL.Ss.LL....",
        "...lLLhSshLLl...",
        "....ll.Ss.ll....",
        ".......Ss.......",
        "..LL...Ss...LL..",
        ".lLLh..Ss..hLLl.",
        "..ll...Ss...ll..",
        ".......Ss.......",
        ".......Ss.......",
    ],
    [
        ".......4........",
        "......045.......",
        "......2343......",
        "..4...2343...4..",
        ".045..12321.045.",
        ".234...1Ss..234.",
        ".123...Ss...123.",
        "..1.LL.Ss.LL.1..",
        "...lLLhSshLLl...",
        "....ll.Ss.ll....",
        ".......Ss.......",
        "..LL...Ss...LL..",
        ".lLLh..Ss..hLLl.",
        "..ll...Ss...ll..",
        ".......Ss.......",
        ".......Ss.......",
    ],
]

# La graine : une goutte du minerai, cerclee de sombre, et sa pousse.
SEED = [
    "................",
    "................",
    "..........Lh....",
    ".........lL.....",
    "......000s......",
    ".....01120......",
    "....0123320.....",
    "....0134320.....",
    "...012345320....",
    "...012345320....",
    "...01234321.....",
    "....0123210.....",
    ".....01110......",
    "......000.......",
    "................",
    "................",
]

ORGANIC_NUGGET = [
    "................",
    "................",
    "................",
    "................",
    "......00........",
    "....00Ge00......",
    "...0GeEEpe0.....",
    "...0eEpEEg0.....",
    "..0gEEeEEGe0....",
    "..0GHgEEeEG0....",
    "...0GGeEgG0.....",
    "....00GGG00.....",
    "......000.......",
    "................",
    "................",
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


def palette(ramp):
    colors = dict(LEAF)
    colors.update(STEM)
    colors.update({str(i): tone for i, tone in enumerate(ramp)})
    return colors


def textures():
    images = {}
    for seed, (_fr, _en, _cfr, _cen, ramp) in SEEDS.items():
        colors = palette(ramp)
        for stage, rows in enumerate(STAGES):
            images["textures/block/%s_crop_stage_%d.png" % (seed, stage)] = draw(rows, colors)
        images["textures/item/%s_seed.png" % seed] = draw(SEED, colors)
    images["textures/item/%s.png" % ORGANIC[0]] = draw(ORGANIC_NUGGET, ORGANIC_COLORS)
    return images


def outputs():
    files = {}

    def put(root, path, payload):
        files[os.path.join(root, path)] = json.dumps(payload, indent=2, ensure_ascii=False) + "\n"

    for seed in SEEDS:
        crop, item = seed + "_crop", seed + "_seed"
        for stage in range(4):
            put(ASSETS, "models/block/%s_stage_%d.json" % (crop, stage),
                {"parent": "minecraft:block/crop",
                 "textures": {"crop": "%s:block/%s_stage_%d" % (NS, crop, stage)}})
        put(ASSETS, "blockstates/%s.json" % crop,
            {"variants": {"age=%d" % stage: {"model": "%s:block/%s_stage_%d" % (NS, crop, stage)}
                          for stage in range(4)}})
        put(ASSETS, "models/item/%s.json" % item,
            {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, item)}})
        put(ASSETS, "items/%s.json" % item, {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, item)}})
        # Vide : MineralCropBlock.getDrops calcule la recolte (caracteres du plant, niveau de Fermier).
        put(DATA, "loot_table/blocks/%s.json" % crop, {"type": "minecraft:block", "pools": []})
    name = ORGANIC[0]
    put(ASSETS, "models/item/%s.json" % name,
        {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    put(ASSETS, "items/%s.json" % name, {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}})
    # JobsBotanistRecipes : un endium organique, une pepite d'endium, a l'etabli.
    put(DATA, "recipe/endium_nugget_from_organic_endium.json",
        {"type": "minecraft:crafting_shapeless", "ingredients": ["%s:%s" % (NS, name)],
         "result": {"id": "%s:endium_nugget" % NS, "count": 1}})
    return files


LANG = {
    "fr_fr": {
        "tooltip.palamod.mineral_seed.speed": "Vitesse : %s",
        "tooltip.palamod.mineral_seed.speed.slow": "Lente",
        "tooltip.palamod.mineral_seed.speed.medium": "Moyenne",
        "tooltip.palamod.mineral_seed.speed.fast": "Rapide",
        "tooltip.palamod.mineral_seed.offline": "Pousse hors ligne : %s",
        "tooltip.palamod.mineral_seed.yes": "Oui",
        "tooltip.palamod.mineral_seed.no": "Non",
        "tooltip.palamod.mineral_seed.growth": "Temps de pousse : %s",
        "tooltip.palamod.mineral_seed.soil": "Se plante sur terre labouree.",
        "tooltip.palamod.organic_endium_nugget.use": "Se change en pepite d'endium a l'etabli.",
        "message.palamod.mineral_seed.catch_up": "Graines du Botaniste, pour tes niveaux de Fermier :",
    },
    "en_us": {
        "tooltip.palamod.mineral_seed.speed": "Speed: %s",
        "tooltip.palamod.mineral_seed.speed.slow": "Slow",
        "tooltip.palamod.mineral_seed.speed.medium": "Medium",
        "tooltip.palamod.mineral_seed.speed.fast": "Fast",
        "tooltip.palamod.mineral_seed.offline": "Grows offline: %s",
        "tooltip.palamod.mineral_seed.yes": "Yes",
        "tooltip.palamod.mineral_seed.no": "No",
        "tooltip.palamod.mineral_seed.growth": "Growth time: %s",
        "tooltip.palamod.mineral_seed.soil": "Plant on farmland.",
        "tooltip.palamod.organic_endium_nugget.use": "Turns into an endium nugget at the crafting table.",
        "message.palamod.mineral_seed.catch_up": "Botanist seeds for your Farmer levels:",
    },
}


def lang_updates():
    updates = {}
    for code, entries in LANG.items():
        labels = dict(entries)
        french = code == "fr_fr"
        for seed, (seed_fr, seed_en, crop_fr, crop_en, _ramp) in SEEDS.items():
            labels["item.%s.%s_seed" % (NS, seed)] = seed_fr if french else seed_en
            labels["block.%s.%s_crop" % (NS, seed)] = crop_fr if french else crop_en
        labels["item.%s.%s" % (NS, ORGANIC[0])] = ORGANIC[1] if french else ORGANIC[2]
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
    names = ["textures/block/%s_crop_stage_%d.png" % (seed, stage) for seed in SEEDS for stage in range(4)]
    names += ["textures/item/%s_seed.png" % seed for seed in SEEDS] + ["textures/item/%s.png" % ORGANIC[0]]
    for relative in names:
        if not os.path.exists(os.path.join(ASSETS, relative)):
            stale.append("%s absente" % relative)
    if stale:
        for line in stale:
            print("::error::Botaniste : %s n'est pas a jour. Lance 'python3 -I tools/botanist_assets.py'." % line)
        sys.exit(1)
    print("Botaniste : %d graines, %d stades, textures, modeles, butins, recette et noms a jour"
          % (len(SEEDS), 4 * len(SEEDS)))


def sheet(folder):
    """Une planche agrandie (x8) de toutes les textures, pour les regarder."""
    from PIL import Image

    images = textures()
    rows = [["textures/block/%s_crop_stage_%d.png" % (seed, stage) for stage in range(4)]
            + ["textures/item/%s_seed.png" % seed] for seed in SEEDS]
    rows.append(["textures/item/%s.png" % ORGANIC[0]])
    scale, gap = 8, 4
    board = Image.new("RGBA", (5 * (16 * scale + gap) + gap, len(rows) * (16 * scale + gap) + gap), (60, 60, 60, 255))
    for r, row in enumerate(rows):
        for c, name in enumerate(row):
            tile = images[name].resize((16 * scale, 16 * scale), Image.NEAREST)
            board.alpha_composite(tile, (gap + c * (16 * scale + gap), gap + r * (16 * scale + gap)))
    os.makedirs(folder, exist_ok=True)
    board.save(os.path.join(folder, "botanist_sheet.png"))


if __name__ == "__main__":
    main()

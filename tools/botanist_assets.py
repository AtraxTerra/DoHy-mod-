#!/usr/bin/env python3
"""Le Botaniste, lot 1 : graines minerales, plants et endium organique.

    python3 -I tools/botanist_assets.py --import JobsReborn-1.92.0.pala [--preview DOSSIER]
    python3 -I tools/botanist_assets.py --check

Les textures sont celles de JobsReborn 1.92.0 (Paladium Games), copiees octet
pour octet depuis le jar fourni par le joueur : les quatre stades de chaque plant
(assets/jobs/textures/blocks/botanist/<minerai>_crop_stage_<n>.png), les graines
et l'endium organique (assets/jobs/textures/items/botanist/). Credit dans le
README (« Credits des textures »). Le jar n'est pas dans le depot : --import ne
sert qu'a les reprendre.

Ecrit aussi les modeles (parent minecraft:block/crop, comme les cultures de
PalaMod), les blockstates (age 0 a 3), les objets, les tables de butin (vides :
le plant calcule sa recolte lui-meme, MineralCropBlock.getDrops), la recette de
l'endium organique et les noms.

`--check` (CI) refait le JSON et les noms en memoire et echoue si un fichier a
change ou si une texture manque.
"""
import json
import os
import struct
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "palamod")
DATA = os.path.join(RES, "data", "palamod")
NS = "palamod"

# id : (nom fr de la graine, nom en, nom fr du plant, nom en du plant)
SEEDS = {
    "amethyst": ("Graine d'amethyste", "Amethyst Seed", "Plant d'amethyste", "Amethyst Plant"),
    "titane": ("Graine de titane", "Titanium Seed", "Plant de titane", "Titanium Plant"),
    "paladium": ("Graine de paladium", "Paladium Seed", "Plant de paladium", "Paladium Plant"),
    "green_paladium": ("Graine de paladium vert", "Green Paladium Seed", "Plant de paladium vert",
                       "Green Paladium Plant"),
    "findium": ("Graine de findium", "Findium Seed", "Plant de findium", "Findium Plant"),
    "trixium": ("Graine de trixium", "Trixium Seed", "Plant de trixium", "Trixium Plant"),
    "invoker": ("Graine d'invoker", "Invoker Seed", "Plant d'invoker", "Invoker Plant"),
    "endium": ("Graine d'endium", "Endium Seed", "Plant d'endium", "Endium Plant"),
}
ORGANIC = ("organic_endium_nugget", "Endium organique", "Organic Endium")

# Le jar de JobsReborn : chemin dans le jar -> chemin dans assets/palamod.
JAR_BLOCKS = "assets/jobs/textures/blocks/botanist/"
JAR_ITEMS = "assets/jobs/textures/items/botanist/"


def texture_map():
    """Ce que --import copie : (chemin dans le jar, chemin sous assets/palamod)."""
    pairs = []
    for seed in SEEDS:
        for stage in range(4):
            name = "%s_crop_stage_%d.png" % (seed, stage)
            pairs.append((JAR_BLOCKS + name, "textures/block/" + name))
        pairs.append((JAR_ITEMS + seed + "_seed.png", "textures/item/%s_seed.png" % seed))
    pairs.append((JAR_ITEMS + ORGANIC[0] + ".png", "textures/item/%s.png" % ORGANIC[0]))
    return pairs


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", data[16:24])


def import_textures(jar):
    """Copie les textures du jar, telles quelles : 16 x 16, rien n'est redessine."""
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        for source, target in texture_map():
            if source not in names:
                raise SystemExit("%s absent du jar %s" % (source, jar))
            data = archive.read(source)
            if png_size(data) != (16, 16):
                raise SystemExit("%s : pas un PNG de 16 x 16" % source)
            path = os.path.join(ASSETS, target)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "wb") as handle:
                handle.write(data)
    print("%d textures reprises de %s" % (len(texture_map()), os.path.basename(jar)))


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
        for seed, (seed_fr, seed_en, crop_fr, crop_en) in SEEDS.items():
            labels["item.%s.%s_seed" % (NS, seed)] = seed_fr if french else seed_en
            labels["block.%s.%s_crop" % (NS, seed)] = crop_fr if french else crop_en
        labels["item.%s.%s" % (NS, ORGANIC[0])] = ORGANIC[1] if french else ORGANIC[2]
        updates[code] = labels
    return updates


def main():
    check = "--check" in sys.argv
    preview = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    jar = sys.argv[sys.argv.index("--import") + 1] if "--import" in sys.argv else None
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
        if jar:
            import_textures(jar)
        if preview:
            sheet(preview)
    for _source, relative in texture_map():
        if not os.path.exists(os.path.join(ASSETS, relative)):
            stale.append("%s absente (--import JobsReborn-1.92.0.pala)" % relative)
    if stale:
        for line in stale:
            print("::error::Botaniste : %s n'est pas a jour. Lance 'python3 -I tools/botanist_assets.py'." % line)
        sys.exit(1)
    print("Botaniste : %d graines, %d stades, textures, modeles, butins, recette et noms a jour"
          % (len(SEEDS), 4 * len(SEEDS)))


def sheet(folder):
    """Une planche agrandie (x8) des textures, pour les regarder."""
    from PIL import Image

    rows = [["textures/block/%s_crop_stage_%d.png" % (seed, stage) for stage in range(4)]
            + ["textures/item/%s_seed.png" % seed] for seed in SEEDS]
    rows.append(["textures/item/%s.png" % ORGANIC[0]])
    scale, gap = 8, 4
    board = Image.new("RGBA", (5 * (16 * scale + gap) + gap, len(rows) * (16 * scale + gap) + gap), (60, 60, 60, 255))
    for r, row in enumerate(rows):
        for c, name in enumerate(row):
            tile = Image.open(os.path.join(ASSETS, name)).convert("RGBA").resize((16 * scale, 16 * scale), Image.NEAREST)
            board.alpha_composite(tile, (gap + c * (16 * scale + gap), gap + r * (16 * scale + gap)))
    os.makedirs(folder, exist_ok=True)
    board.save(os.path.join(folder, "botanist_sheet.png"))


if __name__ == "__main__":
    main()

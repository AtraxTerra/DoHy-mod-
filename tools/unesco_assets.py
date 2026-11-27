#!/usr/bin/env python3
"""La plaque UNESCO : modele, textures, etat de bloc, objet, nom, butin et recette.

    python3 -I tools/unesco_assets.py
    python3 -I tools/unesco_assets.py --check

Le programme UNESCO de NationsGlory n'a pas de bloc : la plaque est propre au mod. Une stele de
marbre clair (socle, colonne, chapiteau) dont le dessus et la face portent un temple d'or sur fond
bleu nuit, un pictogramme generique (fronton et colonnes), sans lettres ni logo. Textures de 16 x 16
dessinees ici (sans Pillow, la CI ne verifie que le JSON).
"""
import io
import json
import os
import sys

try:
    from PIL import Image
except ImportError:
    Image = None

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "dohy")
DATA = os.path.join(RES, "data", "dohy")
NAME = "unesco_plaque"

LANG = {
    "fr_fr": {"block.dohy.unesco_plaque": "Plaque UNESCO"},
    "en_us": {"block.dohy.unesco_plaque": "UNESCO Plaque"},
}


def rgb(value):
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), 255)


MARBLE = rgb("e6e2d8")
MARBLE_DARK = rgb("bdb7aa")
MARBLE_LIGHT = rgb("f7f4ec")
VEIN = rgb("a39d90")
NIGHT = rgb("1d3f8f")
NIGHT_DARK = rgb("142c66")
GOLD = rgb("d8a93f")
GOLD_LIGHT = rgb("f6d37a")
GOLD_DARK = rgb("8a6420")


def stone():
    image = Image.new("RGBA", (16, 16), MARBLE)
    for x in range(16):
        for y in range(16):
            if (x * 5 + y * 3) % 13 == 0:
                image.putpixel((x, y), MARBLE_LIGHT)
            elif (x + 2 * y) % 17 == 0:
                image.putpixel((x, y), VEIN)
    for i in range(16):
        image.putpixel((i, 15), MARBLE_DARK)
        image.putpixel((15, i), MARBLE_DARK)
    return image


def emblem():
    image = Image.new("RGBA", (16, 16), NIGHT)
    for i in range(16):
        for edge in ((i, 0), (i, 15), (0, i), (15, i)):
            image.putpixel(edge, GOLD)
    for i in range(1, 15):
        for edge in ((i, 1), (1, i)):
            image.putpixel(edge, GOLD_DARK)
    for x in range(2, 14):
        for y in range(2, 14):
            if (x + y) % 7 == 0:
                image.putpixel((x, y), NIGHT_DARK)
    # Le fronton.
    for row, half in ((3, 1), (4, 3), (5, 5)):
        for x in range(8 - half, 8 + half):
            image.putpixel((x, row), GOLD_LIGHT if row < 5 else GOLD)
    # Les colonnes et le socle.
    for x in (4, 7, 10):
        for y in range(6, 11):
            image.putpixel((x, y), GOLD_LIGHT)
            image.putpixel((x + 1, y), GOLD)
    for x in range(3, 13):
        image.putpixel((x, 11), GOLD)
        image.putpixel((x, 12), GOLD_DARK)
    return image


def band():
    image = Image.new("RGBA", (16, 16), GOLD)
    for x in range(16):
        image.putpixel((x, 0), GOLD_LIGHT)
        image.putpixel((x, 15), GOLD_DARK)
        if x % 4 == 1:
            for y in range(3, 13):
                image.putpixel((x, y), GOLD_DARK)
    return image


TEXTURES = {"unesco_plaque_stone": stone, "unesco_plaque_top": emblem, "unesco_plaque_side": band}


def tex(name):
    return "dohy:block/" + name


def face(texture, uv, cull=None):
    data = {"texture": "#" + texture, "uv": uv}
    if cull:
        data["cullface"] = cull
    return data


def box(frm, to, up, side, down, north=None):
    x0, y0, z0 = frm
    x1, y1, z1 = to
    return {"from": frm, "to": to, "faces": {
        "north": face(north or side, [x0, 16 - y1, x1, 16 - y0]),
        "south": face(side, [x0, 16 - y1, x1, 16 - y0]),
        "east": face(side, [z0, 16 - y1, z1, 16 - y0]),
        "west": face(side, [z0, 16 - y1, z1, 16 - y0]),
        "up": face(up, [x0, z0, x1, z1]),
        "down": face(down, [x0, z0, x1, z1], "down" if y0 == 0 else None),
    }}


def model():
    elements = [
        box([1, 0, 1], [15, 2, 15], "stone", "stone", "stone"),
        box([4, 2, 4], [12, 12, 12], "stone", "stone", "stone"),
        box([2, 12, 2], [14, 15, 14], "top", "side", "stone"),
        # La plaque sur la face de la colonne, tournee vers l'entree.
        {"from": [5, 4, 3.5], "to": [11, 10, 4], "faces": {
            "north": face("top", [3, 3, 13, 13]),
            "east": face("side", [0, 0, 1, 6]), "west": face("side", [0, 0, 1, 6]),
            "up": face("side", [0, 0, 6, 1]), "down": face("side", [0, 0, 6, 1])}},
    ]
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": tex("unesco_plaque_stone"), "stone": tex("unesco_plaque_stone"),
                         "top": tex("unesco_plaque_top"), "side": tex("unesco_plaque_side")},
            "elements": elements}


ROTATION = {"north": 0, "east": 90, "south": 180, "west": 270}


def blockstate():
    variants = {}
    for facing, y in ROTATION.items():
        entry = {"model": "dohy:block/" + NAME}
        if y:
            entry["y"] = y
        variants["facing=" + facing] = entry
    return {"variants": variants}


def json_bytes(data):
    return (json.dumps(data, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def outputs(images):
    files = {
        os.path.join(ASSETS, "models", "block", NAME + ".json"): json_bytes(model()),
        os.path.join(ASSETS, "blockstates", NAME + ".json"): json_bytes(blockstate()),
        os.path.join(ASSETS, "items", NAME + ".json"): json_bytes(
            {"model": {"type": "minecraft:model", "model": "dohy:block/" + NAME}}),
        os.path.join(DATA, "loot_table", "blocks", NAME + ".json"): json_bytes(
            {"type": "minecraft:block", "pools": [{"rolls": 1, "conditions": [
                {"condition": "minecraft:survives_explosion"}], "entries": [
                {"type": "minecraft:item", "name": "dohy:" + NAME}]}]}),
        os.path.join(DATA, "recipe", NAME + ".json"): json_bytes(
            {"type": "minecraft:crafting_shaped", "category": "building", "pattern": [" B ", "GQG", "QQQ"],
             "key": {"B": "minecraft:book", "G": "minecraft:gold_ingot", "Q": "minecraft:quartz_block"},
             "result": {"id": "dohy:" + NAME, "count": 1}}),
    }
    if images:
        for name, draw in TEXTURES.items():
            files[os.path.join(ASSETS, "textures", "block", name + ".png")] = draw()
    return files


def same(path, data):
    if not os.path.exists(path):
        return False
    if isinstance(data, bytes):
        with open(path, "rb") as handle:
            return handle.read() == data
    on_disk = Image.open(path).convert("RGBA")
    return on_disk.size == data.size and on_disk.tobytes() == data.convert("RGBA").tobytes()


def main():
    check = "--check" in sys.argv
    if "--preview" in sys.argv:
        target = sys.argv[sys.argv.index("--preview") + 1]
        sheet = Image.new("RGBA", (3 * 132, 132), (40, 40, 40, 255))
        for index, draw in enumerate(TEXTURES.values()):
            sheet.paste(draw().resize((128, 128), Image.NEAREST), (index * 132 + 2, 2))
        sheet.save(target)
        return
    images = Image is not None
    if not images and not check:
        raise SystemExit("Pillow manque : il faut Pillow pour ecrire les textures.")
    stale = []
    for path, data in outputs(images).items():
        if same(path, data):
            continue
        if check:
            stale.append(os.path.relpath(path, ROOT))
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if isinstance(data, bytes):
            with open(path, "wb") as handle:
                handle.write(data)
        else:
            buffer = io.BytesIO()
            data.save(buffer, format="PNG", optimize=True)
            with open(path, "wb") as handle:
                handle.write(buffer.getvalue())
    for code, labels in LANG.items():
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
    for name in TEXTURES:
        texture_path = os.path.join(ASSETS, "textures", "block", name + ".png")
        if not os.path.exists(texture_path):
            stale.append(os.path.relpath(texture_path, ROOT) + " absente")
    tag = os.path.join(RES, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json")
    with open(tag, encoding="utf-8") as handle:
        if "dohy:" + NAME not in json.load(handle)["values"]:
            stale.append("dohy:%s manque a mineable/pickaxe" % NAME)
    if stale:
        for line in stale:
            print("::error::UNESCO : %s n'est pas a jour. Lance 'python3 -I tools/unesco_assets.py'." % line)
        sys.exit(1)
    print("UNESCO : fichiers a jour" + ("" if images else " (sans Pillow : JSON seulement)"))


if __name__ == "__main__":
    main()

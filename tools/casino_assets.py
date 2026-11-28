#!/usr/bin/env python3
"""Le casino : textures, modeles, etats de bloc, objets, noms, butin et recettes.

    python3 -I tools/casino_assets.py
    python3 -I tools/casino_assets.py --check

Les blocs du casino de NationsGlory (machine aleatoire, de) et leurs supports de panneaux
(monnayeur, caisse des gains) n'existent dans aucun jar a disposition : le dessin est propre au mod.
Machine aleatoire : un boitier d'acier sombre a filet d'or, une fleche vers la sortie sur le dessus,
une prise d'entree derriere, une lampe de sortie devant. De : rouge a points blancs, la face tiree
sur le dessus. Monnayeur : laiton, fente a pieces et voyant. Caisse : bois et tapis vert, plateau a
pieces et voyant. Textures de 16 x 16 dessinees ici (sans Pillow, la CI ne verifie que le JSON).
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

LANG = {
    "fr_fr": {
        "block.dohy.random_machine": "Machine aleatoire",
        "block.dohy.dice": "De de casino",
        "block.dohy.coin_slot": "Monnayeur",
        "block.dohy.payout_box": "Caisse des gains",
    },
    "en_us": {
        "block.dohy.random_machine": "Random Machine",
        "block.dohy.dice": "Casino Die",
        "block.dohy.coin_slot": "Coin Slot",
        "block.dohy.payout_box": "Payout Box",
    },
}


def rgb(value):
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), 255)


EDGE = rgb("15181d")
STEEL_DARK = rgb("23272e")
STEEL = rgb("353b45")
STEEL_LIGHT = rgb("5d6672")
GOLD_DARK = rgb("8a6420")
GOLD = rgb("d8a93f")
GOLD_LIGHT = rgb("f6d37a")
RED_DARK = rgb("6e0f15")
RED = rgb("c8202a")
RED_LIGHT = rgb("ff5a3c")
GREEN_DARK = rgb("1d5a2c")
GREEN = rgb("2fbf55")
GREEN_LIGHT = rgb("8cff9a")
FELT = rgb("1f6b3f")
FELT_DARK = rgb("154a2c")
WOOD = rgb("6b4423")
WOOD_LIGHT = rgb("8a5a32")
WOOD_DARK = rgb("4a2d16")
WHITE = rgb("f4f1ea")
SHADE = rgb("c9c3b6")
BLACK = rgb("101010")


def canvas(fill):
    return Image.new("RGBA", (16, 16), fill)


def rect(image, x0, y0, x1, y1, color):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            image.putpixel((x, y), color)


def frame(image, color):
    for i in range(16):
        image.putpixel((i, 0), color)
        image.putpixel((i, 15), color)
        image.putpixel((0, i), color)
        image.putpixel((15, i), color)


def dots(image, points, color):
    for x, y in points:
        image.putpixel((x, y), color)


# -- La machine aleatoire ---------------------------------------------------------------------

def steel_base():
    image = canvas(STEEL)
    for y in range(1, 15):
        image.putpixel((1, y), STEEL_LIGHT)
        image.putpixel((14, y), STEEL_DARK)
    for x in range(1, 15):
        image.putpixel((x, 1), STEEL_LIGHT)
        image.putpixel((x, 14), STEEL_DARK)
    frame(image, EDGE)
    return image


def machine_side():
    image = steel_base()
    for x in range(2, 14):
        image.putpixel((x, 3), GOLD_DARK)
        image.putpixel((x, 12), GOLD_DARK)
    # Le signe pourcent.
    dots(image, [(5, 5), (6, 5), (5, 6), (6, 6), (10, 9), (11, 9), (10, 10), (11, 10)], GOLD)
    dots(image, [(10, 5), (9, 6), (9, 7), (8, 7), (7, 8), (7, 9), (6, 9), (5, 10)], GOLD_LIGHT)
    return image


def machine_top(open_):
    image = steel_base()
    arrow = GREEN_LIGHT if open_ else GOLD
    shade = GREEN if open_ else GOLD_DARK
    rect(image, 7, 6, 8, 12, arrow)
    for row, half in ((2, 1), (3, 2), (4, 3), (5, 4)):
        rect(image, 8 - half, row, 7 + half, row, arrow)
    dots(image, [(9, 6), (9, 7), (9, 8), (9, 9), (9, 10), (9, 11), (9, 12), (9, 3), (10, 4), (11, 5)], shade)
    lamp = RED_LIGHT if open_ else RED_DARK
    dots(image, [(3, 3), (12, 3), (3, 12), (12, 12)], lamp)
    return image


def machine_front(open_):
    image = steel_base()
    rect(image, 5, 5, 10, 10, EDGE)
    rect(image, 6, 6, 9, 9, GOLD_DARK)
    rect(image, 7, 7, 8, 8, RED_LIGHT if open_ else RED_DARK)
    if open_:
        dots(image, [(7, 6), (8, 6), (6, 7), (9, 7), (6, 8), (9, 8), (7, 9), (8, 9)], RED)
        dots(image, [(7, 7)], WHITE)
    for x in range(3, 13):
        image.putpixel((x, 13), GOLD_DARK)
    return image


def machine_back():
    image = steel_base()
    rect(image, 5, 5, 10, 10, EDGE)
    rect(image, 6, 6, 9, 9, GOLD)
    rect(image, 7, 7, 8, 8, STEEL_DARK)
    dots(image, [(7, 3), (8, 3), (7, 12), (8, 12)], GOLD_DARK)
    return image


def machine_bottom():
    image = steel_base()
    rect(image, 4, 4, 11, 11, STEEL_DARK)
    return image


# -- Le de ------------------------------------------------------------------------------------

PIPS = {
    1: [(7, 7)],
    2: [(3, 3), (11, 11)],
    3: [(3, 3), (7, 7), (11, 11)],
    4: [(3, 3), (11, 3), (3, 11), (11, 11)],
    5: [(3, 3), (11, 3), (7, 7), (3, 11), (11, 11)],
    6: [(3, 3), (11, 3), (3, 7), (11, 7), (3, 11), (11, 11)],
}


def dice_face(count):
    image = canvas(RED)
    for i in range(1, 15):
        image.putpixel((i, 1), RED_LIGHT)
        image.putpixel((1, i), RED_LIGHT)
    frame(image, RED_DARK)
    for x, y in PIPS[count]:
        image.putpixel((x, y), WHITE)
        image.putpixel((x + 1, y), WHITE)
        image.putpixel((x, y + 1), WHITE)
        image.putpixel((x + 1, y + 1), SHADE)
    return image


# Pour chaque face du dessus : celle du devant et celle de droite (les opposees font 7).
SIDES = {1: (2, 3), 2: (1, 3), 3: (1, 2), 4: (1, 2), 5: (1, 3), 6: (2, 3)}


# -- Le monnayeur -----------------------------------------------------------------------------

def brass_base():
    image = canvas(GOLD)
    for x in range(16):
        for y in range(16):
            if x % 3 == 0:
                image.putpixel((x, y), GOLD_DARK if (x + y) % 5 == 0 else GOLD)
            elif x % 3 == 1 and y % 4 == 2:
                image.putpixel((x, y), GOLD_LIGHT)
    frame(image, GOLD_DARK)
    return image


def coin_side():
    image = brass_base()
    dots(image, [(2, 2), (13, 2), (2, 13), (13, 13)], GOLD_LIGHT)
    dots(image, [(3, 3), (12, 3), (3, 12), (12, 12)], GOLD_DARK)
    return image


def coin_top():
    image = brass_base()
    rect(image, 3, 3, 12, 12, GOLD)
    # Un dollar grave.
    dots(image, [(7, 3), (7, 12), (6, 4), (7, 4), (8, 4), (9, 4), (5, 5), (5, 6), (6, 7), (7, 7), (8, 7),
                 (9, 8), (10, 9), (10, 10), (5, 11), (6, 11), (7, 11), (8, 11), (9, 11)], GOLD_DARK)
    frame(image, GOLD_DARK)
    return image


def coin_bottom():
    image = canvas(GOLD_DARK)
    frame(image, EDGE)
    return image


def coin_front(on):
    image = brass_base()
    rect(image, 3, 2, 12, 13, EDGE)
    rect(image, 4, 3, 11, 12, STEEL_DARK)
    rect(image, 6, 4, 9, 10, GOLD_DARK)
    rect(image, 7, 5, 8, 9, GOLD_LIGHT if on else BLACK)
    lamp = GREEN_LIGHT if on else RED_DARK
    rect(image, 7, 11, 8, 11, lamp)
    if on:
        dots(image, [(6, 11), (9, 11)], GREEN)
    return image


# -- La caisse des gains ----------------------------------------------------------------------

def wood_base():
    image = canvas(WOOD)
    for y in range(16):
        for x in range(16):
            if y % 4 == 3:
                image.putpixel((x, y), WOOD_DARK)
            elif (x * 7 + y * 3) % 11 == 0:
                image.putpixel((x, y), WOOD_LIGHT)
    return image


def box_side():
    image = wood_base()
    for x in range(16):
        image.putpixel((x, 0), GOLD)
        image.putpixel((x, 15), GOLD_DARK)
    frame(image, GOLD_DARK)
    return image


def box_top():
    image = canvas(FELT)
    for x in range(16):
        for y in range(16):
            if (x + y) % 6 == 0:
                image.putpixel((x, y), FELT_DARK)
    frame(image, GOLD)
    for i in range(1, 15):
        image.putpixel((i, 1), GOLD_DARK)
        image.putpixel((1, i), GOLD_DARK)
    dots(image, [(7, 5), (8, 5), (6, 6), (9, 6), (5, 7), (10, 7), (5, 8), (10, 8), (6, 9), (9, 9), (7, 10),
                 (8, 10)], GOLD)
    dots(image, [(7, 7), (8, 7), (7, 8), (8, 8)], GOLD_LIGHT)
    return image


def box_bottom():
    image = canvas(WOOD_DARK)
    frame(image, EDGE)
    return image


def box_front(on):
    image = wood_base()
    frame(image, GOLD_DARK)
    rect(image, 2, 2, 13, 4, FELT)
    lamp = GREEN_LIGHT if on else RED_DARK
    rect(image, 7, 3, 8, 3, lamp)
    rect(image, 2, 7, 13, 13, GOLD_DARK)
    rect(image, 3, 8, 12, 12, BLACK)
    coins = [(4, 12), (5, 12), (6, 12), (9, 12), (10, 12), (5, 11)]
    if on:
        coins += [(7, 12), (8, 12), (11, 12), (4, 11), (6, 11), (7, 11), (9, 11), (10, 11), (8, 10)]
    dots(image, coins, GOLD)
    if on:
        dots(image, [(6, 10), (10, 10), (8, 9)], GOLD_LIGHT)
    return image


def textures():
    files = {
        "random_machine_side": machine_side(),
        "random_machine_top": machine_top(False),
        "random_machine_top_open": machine_top(True),
        "random_machine_front": machine_front(False),
        "random_machine_front_open": machine_front(True),
        "random_machine_back": machine_back(),
        "random_machine_bottom": machine_bottom(),
        "coin_slot_side": coin_side(),
        "coin_slot_top": coin_top(),
        "coin_slot_bottom": coin_bottom(),
        "coin_slot_front": coin_front(False),
        "coin_slot_front_on": coin_front(True),
        "payout_box_side": box_side(),
        "payout_box_top": box_top(),
        "payout_box_bottom": box_bottom(),
        "payout_box_front": box_front(False),
        "payout_box_front_on": box_front(True),
    }
    for count in range(1, 7):
        files["dice_%d" % count] = dice_face(count)
    return files


TEXTURE_NAMES = (["random_machine_" + name for name in ("side", "top", "top_open", "front", "front_open", "back",
                                                          "bottom")]
                 + ["coin_slot_" + name for name in ("side", "top", "bottom", "front", "front_on")]
                 + ["payout_box_" + name for name in ("side", "top", "bottom", "front", "front_on")]
                 + ["dice_%d" % count for count in range(1, 7)])


# -- Les JSON ---------------------------------------------------------------------------------

def tex(name):
    return "dohy:block/" + name


def cube(north, south, east, west, up, down, particle):
    return {"parent": "minecraft:block/cube", "textures": {
        "particle": tex(particle), "north": tex(north), "south": tex(south), "east": tex(east),
        "west": tex(west), "up": tex(up), "down": tex(down)}}


def orientable(front, side, top, bottom):
    return {"parent": "minecraft:block/orientable_with_bottom", "textures": {
        "front": tex(front), "side": tex(side), "top": tex(top), "bottom": tex(bottom)}}


def models():
    out = {
        "random_machine": cube("random_machine_front", "random_machine_back", "random_machine_side",
                               "random_machine_side", "random_machine_top", "random_machine_bottom",
                               "random_machine_side"),
        "random_machine_open": cube("random_machine_front_open", "random_machine_back", "random_machine_side",
                                    "random_machine_side", "random_machine_top_open", "random_machine_bottom",
                                    "random_machine_side"),
        "coin_slot": orientable("coin_slot_front", "coin_slot_side", "coin_slot_top", "coin_slot_bottom"),
        "coin_slot_on": orientable("coin_slot_front_on", "coin_slot_side", "coin_slot_top", "coin_slot_bottom"),
        "payout_box": orientable("payout_box_front", "payout_box_side", "payout_box_top", "payout_box_bottom"),
        "payout_box_on": orientable("payout_box_front_on", "payout_box_side", "payout_box_top",
                                    "payout_box_bottom"),
    }
    for top in range(1, 7):
        front, right = SIDES[top]
        out["dice_%d" % top] = cube("dice_%d" % front, "dice_%d" % (7 - front), "dice_%d" % right,
                                    "dice_%d" % (7 - right), "dice_%d" % top, "dice_%d" % (7 - top),
                                    "dice_%d" % top)
    return out


ROTATION = {"north": 0, "east": 90, "south": 180, "west": 270}


def facing_states(model, flag, lit_model):
    variants = {}
    for facing, y in ROTATION.items():
        for value in ("false", "true"):
            entry = {"model": "dohy:block/" + (lit_model if value == "true" else model)}
            if y:
                entry["y"] = y
            variants["facing=%s,%s=%s" % (facing, flag, value)] = entry
    return {"variants": variants}


def blockstates():
    return {
        "random_machine": facing_states("random_machine", "open", "random_machine_open"),
        "coin_slot": facing_states("coin_slot", "powered", "coin_slot_on"),
        "payout_box": facing_states("payout_box", "powered", "payout_box_on"),
        "dice": {"variants": {"face=%d" % face: {"model": "dohy:block/dice_%d" % face} for face in range(1, 7)}},
    }


ITEM_MODELS = {"random_machine": "random_machine", "coin_slot": "coin_slot", "payout_box": "payout_box",
               "dice": "dice_5"}


def loot(name):
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "conditions": [
        {"condition": "minecraft:survives_explosion"}], "entries": [{"type": "minecraft:item",
                                                                      "name": "dohy:" + name}]}]}


def shaped(name, pattern, key):
    return {"type": "minecraft:crafting_shaped", "category": "redstone", "pattern": pattern, "key": key,
            "result": {"id": "dohy:" + name, "count": 1}}


RECIPES = {
    "random_machine": shaped("random_machine", [" T ", "GCG", "SSS"], {
        "T": "minecraft:redstone_torch", "G": "minecraft:gold_ingot", "C": "minecraft:comparator",
        "S": "minecraft:smooth_stone"}),
    "dice": shaped("dice", ["QRQ", "RBR", "QRQ"], {
        "Q": "minecraft:quartz", "R": "minecraft:red_dye", "B": "minecraft:bone_block"}),
    "coin_slot": shaped("coin_slot", ["GGG", "GHG", "IRI"], {
        "G": "minecraft:gold_ingot", "H": "minecraft:hopper", "I": "minecraft:iron_ingot",
        "R": "minecraft:redstone"}),
    "payout_box": shaped("payout_box", ["GDG", "GCG", "IRI"], {
        "G": "minecraft:gold_ingot", "D": "minecraft:dispenser", "C": "minecraft:chest",
        "I": "minecraft:iron_ingot", "R": "minecraft:redstone"}),
}


def json_bytes(data):
    return (json.dumps(data, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def outputs(images):
    files = {}
    for name, data in models().items():
        files[os.path.join(ASSETS, "models", "block", name + ".json")] = json_bytes(data)
    for name, data in blockstates().items():
        files[os.path.join(ASSETS, "blockstates", name + ".json")] = json_bytes(data)
    for name, model in ITEM_MODELS.items():
        files[os.path.join(ASSETS, "items", name + ".json")] = json_bytes(
            {"model": {"type": "minecraft:model", "model": "dohy:block/" + model}})
        files[os.path.join(DATA, "loot_table", "blocks", name + ".json")] = json_bytes(loot(name))
    for name, data in RECIPES.items():
        files[os.path.join(DATA, "recipe", name + ".json")] = json_bytes(data)
    if images:
        for name, image in textures().items():
            files[os.path.join(ASSETS, "textures", "block", name + ".png")] = image
    return files


def same(path, data):
    if not os.path.exists(path):
        return False
    if isinstance(data, bytes):
        with open(path, "rb") as handle:
            return handle.read() == data
    on_disk = Image.open(path).convert("RGBA")
    return on_disk.size == data.size and on_disk.tobytes() == data.convert("RGBA").tobytes()


def preview(path):
    """Une planche des textures, agrandies, pour l'oeil."""
    images = textures()
    names = TEXTURE_NAMES
    columns = 6
    rows = (len(names) + columns - 1) // columns
    sheet = Image.new("RGBA", (columns * 132, rows * 132), (40, 40, 40, 255))
    for index, name in enumerate(names):
        image = images[name].resize((128, 128), Image.NEAREST)
        sheet.paste(image, ((index % columns) * 132 + 2, (index // columns) * 132 + 2))
    sheet.save(path)


def main():
    check = "--check" in sys.argv
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
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
    for name in TEXTURE_NAMES:
        texture_path = os.path.join(ASSETS, "textures", "block", name + ".png")
        if not os.path.exists(texture_path):
            stale.append(os.path.relpath(texture_path, ROOT) + " absente")
    tag = os.path.join(RES, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json")
    with open(tag, encoding="utf-8") as handle:
        values = json.load(handle)["values"]
    for name in ITEM_MODELS:
        if "dohy:" + name not in values:
            stale.append("dohy:%s manque a mineable/pickaxe" % name)
    if stale:
        for line in stale:
            print("::error::Casino : %s n'est pas a jour. Lance 'python3 -I tools/casino_assets.py'." % line)
        sys.exit(1)
    print("Casino : fichiers a jour" + ("" if images else " (sans Pillow : JSON seulement)"))


if __name__ == "__main__":
    main()

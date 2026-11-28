#!/usr/bin/env python3
"""Les familiers : oeufs, objets plats, biomes du serpent et de l'escargot, recette et noms.

    python3 -I tools/pet_assets.py [--jar <jar de PalaMod extrait>]
    python3 -I tools/pet_assets.py --check

Les familiers eux-memes, leur cage et l'appat viennent du jar PalaPet
(tools/palapet_assets.py). Ici restent les deux oeufs d'apparition, dessines
(aucun jar n'en a), le JSON des objets plats (appat, friandise, potions d'XP de
familier, oeufs), les biomes du serpent et de l'escargot, la recette et le
butin de la cage, et les noms.

Le serpent et l'escargot gardent leur modele et leur texture de PalaMod 10.3.0
(`client/pet/SnakeGeometry.java` et `SnailGeometry.java`, traduits par
`tools/modelbase2java.py`) ; `--jar` recopie leurs textures, celles de la
friandise et des potions d'XP de familier, et les sons du serpent.

`--check` (CI) refait tout en memoire et echoue si un fichier genere a change.
Sans Pillow (la CI), il ne verifie que le JSON.
"""
import io
import json
import os
import shutil
import sys

try:
    from PIL import Image
except ImportError:  # la CI n'a pas Pillow : --check verifie alors le JSON
    Image = None

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "palamod")
DATA = os.path.join(RES, "data", "palamod")
NS = "palamod"

def rgb(value, alpha=255):
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


def shade(color, factor):
    return tuple(min(255, int(round(c * factor))) for c in color[:3]) + (color[3],)


# --------------------------------------------------------------------------------------------
# Les oeufs
# --------------------------------------------------------------------------------------------

def egg_texture(base, spots, outline):
    """Un oeuf d'apparition, 16 x 16, a la forme ovale, tachete."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = image.load()
    for y in range(16):
        for x in range(16):
            dx = (x + 0.5 - 8.0) / 5.4
            dy = (y + 0.5 - 8.8) / (6.6 if y > 8 else 7.2)
            r = dx * dx + dy * dy
            if r <= 1.0:
                color = base
                if r > 0.72:
                    color = outline
                elif (x * 7 + y * 13) % 11 in (0, 4) and r < 0.6:
                    color = spots
                elif x < 7 and y < 7 and r < 0.35:
                    color = shade(base, 1.18)
                px[x, y] = color
    return image


# --------------------------------------------------------------------------------------------
# Les fichiers
# --------------------------------------------------------------------------------------------

LANG = {
    "fr_fr": {
        "block.palamod.pet_cage": "Cage a familier",
        "item.palamod.pet_bait": "Appat a familier",
        "item.palamod.pet_snack": "Friandise pour familier",
        "item.palamod.pet_xp_small": "Petite potion d'XP de familier",
        "item.palamod.pet_xp_medium": "Moyenne potion d'XP de familier",
        "item.palamod.pet_xp_big": "Grande potion d'XP de familier",
        "item.palamod.snake_spawn_egg": "Oeuf d'apparition de serpent",
        "item.palamod.snail_spawn_egg": "Oeuf d'apparition d'escargot",
        "entity.palamod.snake": "Serpent",
        "entity.palamod.snail": "Escargot",
        "tooltip.palamod.pet_snack": "Clic droit : ton familier la croque (+%s bonheur, quete du jour)",
        "tooltip.palamod.pet_xp": "A boire : +%s XP pour ton familier",
        "tooltip.palamod.pet_bait": "Clic droit sur ta cage a familier : 5 minutes de piege",
        "key.dohy.pet_wheel": "Roue du familier",
        "subtitles.palamod.snake.living": "Le serpent siffle",
        "subtitles.palamod.snake.hurt": "Le serpent est blesse",
        "subtitles.palamod.snake.death": "Le serpent meurt",
    },
    "en_us": {
        "block.palamod.pet_cage": "Pet Cage",
        "item.palamod.pet_bait": "Pet Bait",
        "item.palamod.pet_snack": "Pet Snack",
        "item.palamod.pet_xp_small": "Small Pet XP Potion",
        "item.palamod.pet_xp_medium": "Medium Pet XP Potion",
        "item.palamod.pet_xp_big": "Big Pet XP Potion",
        "item.palamod.snake_spawn_egg": "Snake Spawn Egg",
        "item.palamod.snail_spawn_egg": "Snail Spawn Egg",
        "entity.palamod.snake": "Snake",
        "entity.palamod.snail": "Snail",
        "tooltip.palamod.pet_snack": "Right click: your pet eats it (+%s happiness, daily quest)",
        "tooltip.palamod.pet_xp": "Drink it: +%s XP for your pet",
        "tooltip.palamod.pet_bait": "Right click your pet cage: a 5 minute trap",
        "key.dohy.pet_wheel": "Pet wheel",
        "subtitles.palamod.snake.living": "Snake hisses",
        "subtitles.palamod.snake.hurt": "Snake hurts",
        "subtitles.palamod.snake.death": "Snake dies",
    },
}

# Les fichiers du jar : destination (sous assets/palamod) <- source (sous assets/palamod).
FROM_JAR = {
    "textures/entity/snake.png": "textures/entity/snake.png",
    "textures/entity/snail.png": "textures/entity/snail.png",
    "textures/item/pet_snack.png": "textures/items/pet_snack.png",
    "textures/item/pet_xp_small.png": "textures/items/pet_xp_small.png",
    "textures/item/pet_xp_medium.png": "textures/items/pet_xp_medium.png",
    "textures/item/pet_xp_big.png": "textures/items/pet_xp_big.png",
    "sounds/mob/snake/snake_living.ogg": "sounds/mob/snake/neytoxss/snake_living.ogg",
    "sounds/mob/snake/snake_hurt.ogg": "sounds/mob/snake/neytoxss/snake_hurt.ogg",
    "sounds/mob/snake/snake_death.ogg": "sounds/mob/snake/neytoxss/snake_death.ogg",
}


def json_bytes(data):
    return (json.dumps(data, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def png_bytes(image):
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    return buffer.getvalue()


def outputs(images=True):
    files = {}

    def put(path, payload):
        files[os.path.join(RES, path)] = json_bytes(payload)

    # La cage : son modele, son etat et son objet viennent de PalaPet (palapet_assets.py).
    put("data/%s/loot_table/blocks/pet_cage.json" % NS, {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": NS + ":pet_cage"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # La recette du wiki : quatre batons et quatre lingots de titane, une amethyste compressee.
    put("data/%s/recipe/pet_cage.json" % NS, {
        "type": "minecraft:crafting_shaped", "pattern": ["STS", "TCT", "STS"],
        "key": {"S": NS + ":titane_stick", "T": NS + ":titane_ingot", "C": NS + ":compressed_amethyst"},
        "result": {"id": NS + ":pet_cage", "count": 1}})

    # Les objets plats.
    for item in ("pet_bait", "pet_snack", "pet_xp_small", "pet_xp_medium", "pet_xp_big", "snake_spawn_egg",
                 "snail_spawn_egg"):
        put("assets/%s/models/item/%s.json" % (NS, item),
            {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, item)}})
        put("assets/%s/items/%s.json" % (NS, item),
            {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, item)}})

    # Les biomes : le serpent la nuit dans les pays chauds et humides, l'escargot au frais.
    put("data/%s/tags/worldgen/biome/snake_spawns.json" % NS, {"values": [
        "minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle", "minecraft:swamp",
        "minecraft:mangrove_swamp", "minecraft:desert", "minecraft:savanna", "minecraft:savanna_plateau",
        {"id": "#c:is_jungle", "required": False}, {"id": "#c:is_desert", "required": False},
        {"id": "#c:is_savanna", "required": False}, {"id": "#c:is_swamp", "required": False}]})
    put("data/%s/tags/worldgen/biome/snail_spawns.json" % NS, {"values": [
        "minecraft:forest", "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:flower_forest",
        "minecraft:plains", "minecraft:meadow", "minecraft:swamp", "minecraft:old_growth_birch_forest",
        {"id": "#c:is_forest", "required": False}, {"id": "#c:is_plains", "required": False},
        {"id": "#c:is_swamp", "required": False}]})
    put("data/%s/neoforge/biome_modifier/snake.json" % NS, {
        "type": "neoforge:add_spawns", "biomes": "#%s:snake_spawns" % NS,
        "spawners": {"type": NS + ":snake", "weight": 8, "minCount": 1, "maxCount": 1}})
    put("data/%s/neoforge/biome_modifier/snail.json" % NS, {
        "type": "neoforge:add_spawns", "biomes": "#%s:snail_spawns" % NS,
        "spawners": {"type": NS + ":snail", "weight": 6, "minCount": 1, "maxCount": 2}})

    if images:
        def image(path, img):
            files[os.path.join(ASSETS, "textures", path)] = img
        image("item/snake_spawn_egg.png", egg_texture(rgb("3f8f2f"), rgb("1f4f17"), rgb("2a5f20")))
        image("item/snail_spawn_egg.png", egg_texture(rgb("ffaebe"), rgb("8a5a3c"), rgb("d67f92")))
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
    args = sys.argv[1:]
    check = "--check" in args
    images = Image is not None
    if not images and not check:
        raise SystemExit("Pillow manque : il le faut pour ecrire les oeufs.")
    jar = args[args.index("--jar") + 1] if "--jar" in args else None
    files = outputs(images)
    stale = []
    for path, data in files.items():
        if same(path, data):
            continue
        if check:
            stale.append(os.path.relpath(path, ROOT))
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as handle:
            handle.write(data if isinstance(data, bytes) else png_bytes(data))

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

    for target, source in FROM_JAR.items():
        path = os.path.join(ASSETS, target)
        if jar and not check:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            shutil.copyfile(os.path.join(jar, "assets", "palamod", source), path)
        elif not os.path.exists(path):
            stale.append("%s absent (relancer avec --jar)" % os.path.relpath(path, ROOT))

    if stale:
        for line in stale:
            print("::error::familiers : %s n'est pas a jour. Lance 'python3 -I tools/pet_assets.py'." % line)
        sys.exit(1)
    note = "" if images else " (sans Pillow : JSON seulement)"
    print("familiers : %d fichiers a jour%s" % (len(files), note))


if __name__ == "__main__":
    main()

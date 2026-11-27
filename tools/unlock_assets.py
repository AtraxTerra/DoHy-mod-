#!/usr/bin/env python3
"""Les fichiers du lot « objets de deblocage » (palamod/unlock) : etats de bloc,
modeles, definitions d'objets, butins, recettes et tags.

    python3 tools/unlock_assets.py              # ecrit
    python3 tools/unlock_assets.py --check      # verifie (CI, sans Pillow)
    python3 tools/unlock_assets.py --textures DIR   # copie aussi les textures

Les textures viennent des jars fournis par le joueur, extraits dans DIR :
``PalaMod-10.3.0``, ``PalaJobs-8.0.0`` et le pack de textures officiel
``updated_faithfull_paladium_officiel``. Elles ne servent qu'a l'ecriture ; la CI
ne voit que le JSON.

Le modele de la table d'alchimiste vient de ``ModelAlchemyCreator`` (un modele
Techne/Tabula : une echelle par piece) ; il est ecrit a part, une fois, et
seulement verifie ici.
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
A = RES / "assets/palamod"
D = RES / "data/palamod"

COLORS = ["black", "red", "darkgreen", "brown", "blue", "purple", "yellow", "orange", "pink", "green"]
# Le colorant de chaque graine (CraftRegistry de PalaJobs) et la teinte (DyeColor.getTextureDiffuseColor).
DYES = {"black": "black_dye", "red": "red_dye", "darkgreen": "green_dye", "brown": "brown_dye", "blue": "blue_dye",
        "purple": "purple_dye", "yellow": "yellow_dye", "orange": "orange_dye", "pink": "pink_dye",
        "green": "lime_dye"}
TINTS = {"black": 0x1D1D21, "red": 0xB02E26, "darkgreen": 0x5E7C16, "brown": 0x835432, "blue": 0x3C44AA,
         "purple": 0x8932B8, "yellow": 0xFED83D, "orange": 0xF9801D, "pink": 0xF38BAA, "green": 0x80C71F}
COMPASSES = {"amethyst": "amethyst", "titane": "titane", "paladium": "paladium", "endium": "endium",
             "legendary": "legend"}

OUT = {}


def put(path, obj):
    OUT[path] = json.dumps(obj, indent=2, ensure_ascii=True) + "\n"


def model(name, parent, textures, **extra):
    data = {"parent": parent, "textures": textures}
    data.update(extra)
    put(A / f"models/{name}.json", data)


def item_def(name, model_ref, tint=None):
    m = {"type": "minecraft:model", "model": model_ref}
    if tint is not None:
        m["tints"] = [{"type": "minecraft:constant", "value": tint - (1 << 32) if tint >= (1 << 31) else tint}]
    put(A / f"items/{name}.json", {"model": m})


def simple_item(name, texture=None):
    model(f"item/{name}", "minecraft:item/generated", {"layer0": texture or f"palamod:item/{name}"})
    item_def(name, f"palamod:item/{name}")


def block_state(name, variants):
    put(A / f"blockstates/{name}.json", {"variants": variants})


def drops_self(name):
    put(D / f"loot_table/blocks/{name}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"palamod:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def drops(name, item):
    put(D / f"loot_table/blocks/{name}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": item}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def shaped(name, pattern, key, result, count=1):
    put(D / f"recipe/{name}.json", {"type": "minecraft:crafting_shaped", "pattern": pattern, "key": key,
                                    "result": {"id": result, "count": count}})


def shapeless(name, ingredients, result, count=1):
    put(D / f"recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "ingredients": ingredients,
                                    "result": {"id": result, "count": count}})


def tag(path, values):
    put(path, {"replace": False, "values": values})


def build():
    # -- Chasseur --------------------------------------------------------------------
    simple_item("endium_nametag")
    simple_item("infernal_knocker")
    simple_item("bamboo_boat")
    for item, tier in COMPASSES.items():
        name = f"hunter_compass_{item}"
        entries = []
        for frame in range(32):
            frame_model = f"palamod:item/compass/compass_{tier}{frame:02d}"
            model(f"item/compass/compass_{tier}{frame:02d}", "minecraft:item/generated",
                  {"layer0": f"palamod:item/compass/compass_{tier}{frame:02d}"})
            entries.append({"threshold": frame - 0.5, "model": {"type": "minecraft:model", "model": frame_model}})
        entries.append({"threshold": 31.5, "model": {"type": "minecraft:model",
                                                      "model": f"palamod:item/compass/compass_{tier}00"}})
        put(A / f"items/{name}.json", {"model": {"type": "minecraft:range_dispatch", "property": "minecraft:compass",
                                                 "target": "lodestone", "scale": 32.0, "entries": entries}})

    # Le rituel d'endium.
    model("block/endium_totem", "minecraft:block/cube_all", {"all": "palamod:block/endium_totem"})
    block_state("endium_totem", {"": {"model": "palamod:block/endium_totem"}})
    item_def("endium_totem", "palamod:block/endium_totem")
    drops_self("endium_totem")

    for suffix, active in (("", False), ("_active", True)):
        pre = "palamod:block/sacrifice_hotel" + ("_active" if active else "")
        model(f"block/sacrifice_hotel{suffix}", "minecraft:block/cube_bottom_top",
              {"top": pre + "_top", "side": pre + "_side", "bottom": "palamod:block/sacrifice_hotel_bottom"})
    block_state("sacrifice_hotel", {"active=false": {"model": "palamod:block/sacrifice_hotel"},
                                    "active=true": {"model": "palamod:block/sacrifice_hotel_active"}})
    item_def("sacrifice_hotel", "palamod:block/sacrifice_hotel")
    drops_self("sacrifice_hotel")

    # La poudre : une trainee a plat ; les traits de PalaMod sont nommes a l'envers
    # (line_vertical est un trait est-ouest).
    traces = {"alone": "cross_default", "cross": "cross", "north_south": "line_horizontal",
              "east_west": "line_vertical"}
    variants = {}
    for trace, texture in traces.items():
        tex = f"palamod:block/perlinpinpin_power_{texture}"
        put(A / f"models/block/perlinpinpin_power_{trace}.json", {
            "ambientocclusion": False,
            "textures": {"particle": tex, "trace": tex},
            "elements": [{"from": [0, 0.25, 0], "to": [16, 0.25, 16], "shade": False,
                          "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#trace"},
                                    "down": {"uv": [0, 16, 16, 0], "texture": "#trace"}}}]})
        for active in ("false", "true"):
            variants[f"active={active},trace={trace}"] = {"model": f"palamod:block/perlinpinpin_power_{trace}"}
    block_state("perlinpinpin_power", variants)
    model("item/perlinpinpin_power", "minecraft:item/generated", {"layer0": "palamod:block/perlinpinpin_power_cross"})
    item_def("perlinpinpin_power", "palamod:item/perlinpinpin_power")
    drops_self("perlinpinpin_power")

    # -- Alchimiste -------------------------------------------------------------------
    facing = {"north": 0, "east": 90, "south": 180, "west": 270}
    block_state("alchemy_creator", {f"facing={f},lit={lit}": ({"model": "palamod:block/alchemy_creator", "y": y}
                                                                if y else {"model": "palamod:block/alchemy_creator"})
                                    for f, y in facing.items() for lit in ("false", "true")})
    item_def("alchemy_creator", "palamod:block/alchemy_creator")
    drops_self("alchemy_creator")
    for name in ("wither_imbue", "fire_imbue", "poison_imbue", "sickness_potion",
                 "poison_arrow", "wither_arrow", "slowness_arrow", "frost_arrow"):
        simple_item(name)

    # -- Fermier : herbes colorees ------------------------------------------------------
    model("block/colored_grass", "minecraft:block/grass_block", {
        "top": "palamod:block/colored_grass_top", "side": "palamod:block/colored_grass_side",
        "overlay": "palamod:block/colored_grass_side_overlay", "bottom": "minecraft:block/dirt",
        "particle": "minecraft:block/dirt"})
    model("block/colored_tall_grass", "minecraft:block/tinted_cross", {"cross": "minecraft:block/short_grass"})
    for color in COLORS:
        tint = 0xFF000000 | TINTS[color]
        block_state(f"colored_grass_{color}", {"": {"model": "palamod:block/colored_grass"}})
        item_def(f"colored_grass_{color}", "palamod:block/colored_grass", tint)
        drops(f"colored_grass_{color}", "minecraft:dirt")

        model(f"block/colored_grass_seeds_{color}", "minecraft:block/crop",
              {"crop": f"palamod:block/colored_grass_seeds_{color}"})
        block_state(f"colored_grass_seeds_{color}", {"": {"model": f"palamod:block/colored_grass_seeds_{color}"}})
        model(f"item/colored_grass_seeds_{color}", "minecraft:item/generated",
              {"layer0": "palamod:item/colored_grass_seed"})
        item_def(f"colored_grass_seeds_{color}", f"palamod:item/colored_grass_seeds_{color}", tint)
        drops_self(f"colored_grass_seeds_{color}")
        shapeless(f"colored_grass_seeds_{color}", [f"minecraft:{DYES[color]}", "minecraft:wheat_seeds"],
                  f"palamod:colored_grass_seeds_{color}")

        block_state(f"colored_tall_grass_{color}", {"": {"model": "palamod:block/colored_tall_grass"}})
        model(f"item/colored_tall_grass_{color}", "minecraft:item/generated", {"layer0": "minecraft:block/short_grass"})
        item_def(f"colored_tall_grass_{color}", f"palamod:item/colored_tall_grass_{color}", tint)
        drops_self(f"colored_tall_grass_{color}")
        shapeless(f"colored_tall_grass_{color}", [f"palamod:colored_grass_seeds_{color}", "minecraft:short_grass"],
                  f"palamod:colored_tall_grass_{color}")

    # -- Fermier : le racinier ----------------------------------------------------------
    for age in range(3):
        model(f"block/root_seeds_stage_{age}", "minecraft:block/crop", {"crop": f"palamod:block/root_seeds_{age}"})
    block_state("root_seeds_block", {f"age={age}": {"model": f"palamod:block/root_seeds_stage_{age}"} for age in range(3)})
    simple_item("root_seeds_block", "palamod:item/root_seeds")
    drops("root_seeds_block", "palamod:root_seeds_block")
    model("block/root_leaves_on", "minecraft:block/leaves", {"all": "palamod:block/root_leaves_on"})
    model("block/root_leaves_off", "minecraft:block/leaves", {"all": "palamod:block/root_leaves_off"})
    block_state("root_leaves", {"blocked=false": {"model": "palamod:block/root_leaves_on"},
                                "blocked=true": {"model": "palamod:block/root_leaves_off"}})
    model("block/root_log", "minecraft:block/cube_column", {"end": "palamod:block/root_log_top",
                                                            "side": "palamod:block/root_log"})
    model("block/root_log_horizontal", "minecraft:block/cube_column_horizontal",
          {"end": "palamod:block/root_log_top", "side": "palamod:block/root_log"})
    block_state("root_log", {"axis=y": {"model": "palamod:block/root_log"},
                             "axis=z": {"model": "palamod:block/root_log_horizontal", "x": 90},
                             "axis=x": {"model": "palamod:block/root_log_horizontal", "x": 90, "y": 90}})
    item_def("root_log", "palamod:block/root_log")
    drops_self("root_log")
    model("block/root_planks", "minecraft:block/cube_all", {"all": "palamod:block/root_planks"})
    block_state("root_planks", {"": {"model": "palamod:block/root_planks"}})
    item_def("root_planks", "palamod:block/root_planks")
    drops_self("root_planks")
    for name in ("root_inferior", "root_last"):
        model(f"block/{name}", "minecraft:block/cross", {"cross": f"palamod:block/{name}"})
        block_state(name, {"": {"model": f"palamod:block/{name}"}})
    # Le feuillage et la racine ne rendent rien (getDrops : une liste vide).
    for name in ("root_leaves", "root_inferior", "root_last"):
        put(D / f"loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": []})

    # -- Recettes (relevees au bytecode) ---------------------------------------------------
    shaped("endium_nametag", [" A ", "BCB", "DBD"],
           {"A": "palamod:endium_nugget", "B": "palamod:findium", "C": "minecraft:name_tag",
            "D": "palamod:paladium_ingot"}, "palamod:endium_nametag")
    shaped("infernal_knocker", ["X", "Z", "Y"],
           {"X": "palamod:paladium_ingot", "Z": "palamod:knockback_orb", "Y": "palamod:paladium_stick"},
           "palamod:infernal_knocker")
    shaped("bamboo_boat", ["X X", "XXX"], {"X": "minecraft:bamboo_block"}, "palamod:bamboo_boat")
    compass_keys = {
        "amethyst": ("palamod:unclaim_finder", "palamod:amethyst_ingot", "minecraft:compass", None),
        "titane": ("palamod:unclaim_finder_orange", "palamod:titane_ingot", "minecraft:compass", None),
        "paladium": ("palamod:unclaim_finder_red", "palamod:paladium_ingot", "minecraft:compass", None),
        "endium": ("palamod:unclaim_finder_red", "palamod:paladium_ingot", "minecraft:compass", "palamod:endium_nugget"),
        "legendary": ("palamod:unclaim_finder_red", "palamod:paladium_ingot", "palamod:compressed_paladium", None),
    }
    for item, (finder, ingot, center, extra) in compass_keys.items():
        key = {"F": "palamod:findium", "U": finder, "I": ingot, "C": center}
        pattern = ["FUF", "ICI", "FIF"]
        if extra:
            key["E"] = extra
            pattern = ["FUF", "ICI", "FEF"]
        shaped(f"hunter_compass_{item}", pattern, key, f"palamod:hunter_compass_{item}")
    shaped("endium_totem", ["ABC", "DEF", "GHI"],
           {"A": "palamod:snake_venom", "B": "palamod:parrot_feather", "C": "palamod:turtle_scales",
            "D": "palamod:goat_shoe", "E": "minecraft:enchanting_table", "F": "palamod:wither_skull_fragment",
            "G": "palamod:meduse_hook", "H": "palamod:snail_shell", "I": "palamod:panda_drool"},
           "palamod:endium_totem")
    shaped("sacrifice_hotel", ["ABA", "CDE", "FFF"],
           {"A": "palamod:pala_mixed_coal", "B": "minecraft:book", "C": "palamod:hunter_amulet",
            "D": "palamod:mini_golem", "E": "palamod:voyance_amulet", "F": "minecraft:obsidian"},
           "palamod:sacrifice_hotel")
    # La glueball bleue n'est pas portee : une boule de slime la remplace.
    shaped("perlinpinpin_power", ["ABC", "BDB", "EBF"],
           {"A": "minecraft:glowstone_dust", "B": "palamod:paladium_ingot", "C": "minecraft:sugar",
            "D": "minecraft:slime_ball", "E": "minecraft:gunpowder", "F": "minecraft:redstone"},
           "palamod:perlinpinpin_power")
    shaped("alchemy_creator", ["YUY", "XZX", "XXX"],
           {"Y": "minecraft:blaze_powder", "U": "palamod:compressed_paladium", "X": "palamod:titane_ingot",
            "Z": "palamod:paladium_core"}, "palamod:alchemy_creator")
    shaped("root_seeds_block", [" S ", "DWD", "PPP"],
           {"S": "minecraft:wheat_seeds", "D": "palamod:dead_wood", "W": "palamod:angelic_water_bucket",
            "P": "minecraft:dead_bush"}, "palamod:root_seeds_block")
    shapeless("root_planks", ["palamod:root_log"], "palamod:root_planks", 4)

    # -- Tags ------------------------------------------------------------------------------
    tag(D / "tags/block/colored_grass.json", [f"palamod:colored_grass_{c}" for c in COLORS])
    tag(D / "tags/item/colored_grass_seeds.json", [f"palamod:colored_grass_seeds_{c}" for c in COLORS])
    tag(D / "tags/item/colored_tall_grass.json", [f"palamod:colored_tall_grass_{c}" for c in COLORS])
    tag(D / "tags/item/hunter_compass.json", [f"palamod:hunter_compass_{i}" for i in COMPASSES])
    tag(RES / "data/minecraft/tags/block/climbable.json", ["palamod:root_inferior", "palamod:root_last"])


def textures(src):
    """Copie les textures depuis les jars extraits (Pillow pour la frange de l'herbe et les icones d'effet)."""
    from PIL import Image
    import shutil
    pm = src / "PalaMod-10.3.0/assets/palamod/textures"
    pj = src / "PalaJobs-8.0.0/assets/palajobs/textures"
    pack = src / "updated_faithfull_paladium_officiel/assets/palamod/textures"
    copies = {
        "item/endium_nametag.png": pack / "items/endium_nametag.png",
        "item/infernal_knocker.png": pack / "items/InfernalKnocker.png",
        "item/bamboo_boat.png": pj / "items/bamboo_boat.png",
        "entity/bamboo_boat.png": pj / "entity/bamboo_boat.png",
        "block/endium_totem.png": pm / "blocks/endium_totem.png",
        "block/alchemy_creator.png": pm / "blocks/machines/alchemy_creator_block.png",
        "item/wither_imbue.png": pack / "items/potion/PotionWither.png",
        "item/fire_imbue.png": pack / "items/potion/PotionFire.png",
        "item/poison_imbue.png": pack / "items/potion/PotionPoison.png",
        "item/sickness_potion.png": pack / "items/potion/SicknessPotion.png",
        "item/poison_arrow.png": pack / "items/arrow/arrow_poison.png",
        "item/wither_arrow.png": pack / "items/arrow/arrow_wither.png",
        "item/slowness_arrow.png": pack / "items/arrow/arrow_slowness.png",
        "item/frost_arrow.png": pack / "items/arrow/arrow_frost.png",
        "block/colored_grass_top.png": pj / "blocks/colored_grass_top.png",
        "block/colored_grass_side.png": pj / "blocks/colored_grass_side.png",
        "item/colored_grass_seed.png": pj / "items/colored_grass_seed.png",
        "item/root_seeds.png": pj / "items/root_seeds.png",
    }
    for name in ("top", "side", "bottom", "active_top", "active_side"):
        copies[f"block/sacrifice_hotel_{name}.png"] = pm / f"blocks/sacrifice_hotel_{name}.png"
    for name in ("cross", "cross_default", "line_vertical", "line_horizontal"):
        copies[f"block/perlinpinpin_power_{name}.png"] = pm / f"blocks/perlinpinpin_power_{name}.png"
    for color in COLORS:
        copies[f"block/colored_grass_seeds_{color}.png"] = pj / f"blocks/colored_grass_seeds_{color}.png"
    for name in ("root_log", "root_log_top", "root_leaves_on", "root_leaves_off", "root_inferior", "root_last",
                 "root_planks", "root_seeds_0", "root_seeds_1", "root_seeds_2"):
        copies[f"block/{name}.png"] = pj / f"blocks/{name}.png"
    for tier in COMPASSES.values():
        for frame in range(32):
            copies[f"item/compass/compass_{tier}{frame:02d}.png"] = pack / f"items/compass/compass_{tier}{frame:02d}.png"
    for target, source in copies.items():
        dest = A / "textures" / target
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, dest)
        meta = pathlib.Path(str(source) + ".mcmeta")
        if meta.exists():
            shutil.copyfile(meta, pathlib.Path(str(dest) + ".mcmeta"))
    # La frange grise du flanc de l'herbe, a teinter a part (comme grass_block_side_overlay).
    side = Image.open(pj / "blocks/colored_grass_side.png").convert("RGBA")
    overlay = Image.new("RGBA", side.size, (0, 0, 0, 0))
    for y in range(side.height):
        for x in range(side.width):
            r, g, b, a = side.getpixel((x, y))
            if a and abs(r - g) < 8 and abs(g - b) < 8:
                overlay.putpixel((x, y), (r, g, b, a))
    overlay.save(A / "textures/block/colored_grass_side_overlay.png")
    # Les icones d'effet (18 x 18) : la premiere image de chaque potion, et la fleche glacante.
    icons = {"wither_imbue": pack / "items/potion/PotionWither.png", "fire_imbue": pack / "items/potion/PotionFire.png",
             "poison_imbue": pack / "items/potion/PotionPoison.png", "frost": pack / "items/arrow/arrow_frost.png"}
    for name, source in icons.items():
        image = Image.open(source).convert("RGBA")
        frame = image.crop((0, 0, image.width, image.width))
        icon = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
        icon.paste(frame, (1, 1))
        dest = A / f"textures/mob_effect/{name}.png"
        dest.parent.mkdir(parents=True, exist_ok=True)
        icon.save(dest)


def main(argv):
    build()
    if "--check" in argv:
        bad = [str(path.relative_to(ROOT)) for path, text in OUT.items()
               if not path.exists() or path.read_text(encoding="utf-8") != text]
        model_path = A / "models/block/alchemy_creator.json"
        if not model_path.exists():
            bad.append(str(model_path.relative_to(ROOT)))
        else:
            data = json.loads(model_path.read_text(encoding="utf-8"))
            for element in data["elements"]:
                for face in element["faces"].values():
                    if any(value < 0 or value > 16 for value in face["uv"]):
                        bad.append("alchemy_creator : UV hors de 0..16")
        if bad:
            print("Fichiers du lot de deblocage absents ou differents (relancer tools/unlock_assets.py) :")
            for path in bad:
                print("  " + path)
            return 1
        print(f"Lot de deblocage : {len(OUT)} fichiers a jour.")
        return 0
    for path, text in OUT.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    if "--textures" in argv:
        textures(pathlib.Path(argv[argv.index("--textures") + 1]))
    print(f"Lot de deblocage : {len(OUT)} fichiers ecrits.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

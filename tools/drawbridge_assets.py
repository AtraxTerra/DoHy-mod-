#!/usr/bin/env python3
"""Le pont-levis : textures, etats, modeles, objet, nom, butin, recette.

    python3 -I tools/drawbridge_assets.py <Palamod Recreated extrait>

Textures et recette : celles du dossier que le joueur a fourni (mod et depot
prives). La face avant regarde le nord dans le modele ; l'etat tourne le
modele selon FACING.
"""
import json
import os
import shutil
import sys

RES = "src/main/resources"
NS = "palamod"


def write_json(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(value, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def main():
    source = os.path.join(sys.argv[1], "assets", NS, "textures", "block")
    for face in ("front", "side", "top"):
        shutil.copyfile(os.path.join(source, "drawbridge_%s.png" % face),
                        os.path.join(RES, "assets", NS, "textures", "block", "drawbridge_%s.png" % face))
    write_json(os.path.join(RES, "assets", NS, "models/block/drawbridge.json"), {
        "parent": "minecraft:block/cube",
        "textures": {"down": "%s:block/drawbridge_top" % NS, "up": "%s:block/drawbridge_top" % NS,
                     "north": "%s:block/drawbridge_front" % NS, "east": "%s:block/drawbridge_side" % NS,
                     "south": "%s:block/drawbridge_side" % NS, "west": "%s:block/drawbridge_side" % NS,
                     "particle": "%s:block/drawbridge_top" % NS}})
    write_json(os.path.join(RES, "assets", NS, "blockstates/drawbridge.json"), {"variants": {
        "facing=north": {"model": "%s:block/drawbridge" % NS},
        "facing=east": {"model": "%s:block/drawbridge" % NS, "y": 90},
        "facing=south": {"model": "%s:block/drawbridge" % NS, "y": 180},
        "facing=west": {"model": "%s:block/drawbridge" % NS, "y": 270}}})
    write_json(os.path.join(RES, "assets", NS, "items/drawbridge.json"),
               {"model": {"type": "minecraft:model", "model": "%s:block/drawbridge" % NS}})
    write_json(os.path.join(RES, "data", NS, "loot_table/blocks/drawbridge.json"), {
        "type": "minecraft:block", "random_sequence": "%s:blocks/drawbridge" % NS,
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": "%s:drawbridge" % NS}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write_json(os.path.join(RES, "data", NS, "recipe/drawbridge.json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone", "pattern": ["P P", "SDS", "RRR"],
        "key": {"P": "minecraft:piston", "S": "minecraft:slime_block", "D": "minecraft:dispenser",
                "R": "minecraft:redstone"},
        "result": {"id": "%s:drawbridge" % NS, "count": 1}})
    for code, label in (("fr_fr", "Pont-levis"), ("en_us", "Drawbridge")):
        path = os.path.join(RES, "assets", NS, "lang", code + ".json")
        with open(path, encoding="utf-8") as handle:
            lang = json.load(handle)
        lang["block.%s.drawbridge" % NS] = label
        write_json(path, lang)
    print("pont-levis : textures, etats, modeles, butin, recette, noms")


if __name__ == "__main__":
    main()

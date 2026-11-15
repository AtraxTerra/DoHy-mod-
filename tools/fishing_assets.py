#!/usr/bin/env python3
"""La peche de PalaJobs : textures, modeles, objets, noms, recette, prix.

    python3 -I tools/fishing_assets.py <jar PalaJobs extrait>

Les textures sont celles du jar de PalaJobs que le joueur a fourni (mod et
depot prives) : les huit poissons, la canne en endium (lancee ou non), les
icones de rarete et la touche espace du mini-jeu.
"""
import json
import os
import shutil
import sys

RES = "src/main/resources"
NS = "palamod"

FISH = {
    "carp": ("Carpe", "Carp"), "bass": ("Bar", "Bass"), "ray": ("Raie manta", "Manta Ray"),
    "red_tuna": ("Thon rouge", "Red Tuna"), "moonfish": ("Poisson lune", "Moonfish"),
    "exp_fish": ("Poisson a XP", "XP Fish"), "whale": ("Baleine", "Whale"), "kraken": ("Kraken", "Kraken"),
}
# Prix de vente au shop (base_price) : plus cher selon la rarete ; buy_price = 4 x.
PRICES = {"carp": 45, "bass": 70, "ray": 95, "red_tuna": 150, "moonfish": 180, "exp_fish": 240,
          "whale": 3000, "kraken": 4500}


def write_json(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(value, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def main():
    source = os.path.join(sys.argv[1], "assets", "palajobs", "textures")
    tex = os.path.join(RES, "assets", NS, "textures")
    for name in FISH:
        shutil.copyfile(os.path.join(source, "items", name + ".png"), os.path.join(tex, "item", name + ".png"))
        write_json(os.path.join(RES, "assets", NS, "models/item/%s.json" % name),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
        write_json(os.path.join(RES, "assets", NS, "items/%s.json" % name),
                   {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}})
    shutil.copyfile(os.path.join(source, "items/endium_rod_uncast.png"), os.path.join(tex, "item/endium_fishing_rod.png"))
    shutil.copyfile(os.path.join(source, "items/endium_rod_cast.png"),
                    os.path.join(tex, "item/endium_fishing_rod_cast.png"))
    for model in ("endium_fishing_rod", "endium_fishing_rod_cast"):
        write_json(os.path.join(RES, "assets", NS, "models/item/%s.json" % model),
                   {"parent": "minecraft:item/handheld_rod", "textures": {"layer0": "%s:item/%s" % (NS, model)}})
    write_json(os.path.join(RES, "assets", NS, "items/endium_fishing_rod.json"), {"model": {
        "type": "minecraft:condition", "property": "minecraft:fishing_rod/cast",
        "on_true": {"type": "minecraft:model", "model": "%s:item/endium_fishing_rod_cast" % NS},
        "on_false": {"type": "minecraft:model", "model": "%s:item/endium_fishing_rod" % NS}}})
    gui = os.path.join(tex, "gui", "fishing")
    os.makedirs(os.path.join(gui, "icons"), exist_ok=True)
    for name in ("space.png", "space_pressed.png"):
        shutil.copyfile(os.path.join(source, "gui/fishing", name), os.path.join(gui, name))
    for name in ("common", "rare", "legendary", "mythic"):
        shutil.copyfile(os.path.join(source, "gui/fishing/icons", name + ".png"),
                        os.path.join(gui, "icons", name + ".png"))
    # La recette de la canne en endium : celle de la canne en paladium, l'endium en bout.
    write_json(os.path.join(RES, "data", NS, "recipe/endium_fishing_rod.json"), {
        "type": "minecraft:crafting_shaped", "pattern": ["  E", " SX", "S X"],
        "key": {"E": "palamod:endium_ingot", "S": "palamod:paladium_stick", "X": "minecraft:string"},
        "result": {"id": "palamod:endium_fishing_rod", "count": 1}})
    for code, index in (("fr_fr", 0), ("en_us", 1)):
        path = os.path.join(RES, "assets", NS, "lang", code + ".json")
        with open(path, encoding="utf-8") as handle:
            lang = json.load(handle)
        for name, labels in FISH.items():
            lang["item.%s.%s" % (NS, name)] = labels[index]
        lang["item.%s.endium_fishing_rod" % NS] = ("Canne a peche en endium", "Endium Fishing Rod")[index]
        write_json(path, lang)
    # Les prix : une ligne par poisson ajoutee a data/prices.csv (shop et marche),
    # avec les fins de ligne du fichier.
    with open("data/prices.csv", "rb") as handle:
        raw = handle.read()
    newline = b"\r\n" if b"\r\n" in raw else b"\n"
    known = {line.split(b",")[0] for line in raw.split(newline)}
    if not raw.endswith(newline):
        raw += newline
    for name, price in PRICES.items():
        item = ("%s:%s" % (NS, name)).encode()
        if item not in known:
            raw += b"%s,FOOD,FOOD,120.0,%d,2000,0.15,%d,peche de PalaJobs (mini-jeu)" % (item, price, price * 4) + newline
    with open("data/prices.csv", "wb") as handle:
        handle.write(raw)
    print("peche : %d poissons, canne en endium, interface, recette, prix" % len(FISH))


if __name__ == "__main__":
    main()

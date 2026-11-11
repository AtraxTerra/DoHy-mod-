#!/usr/bin/env python3
"""Valeur des caisses : ce que chaque lot vaut, et ce qu'une caisse rend en moyenne.

    python3 -I tools/crate_values.py              le tableau de chaque palier
    python3 -I tools/crate_values.py ITEM...      la valeur de quelques objets

La valeur d'un lot est sa **valeur boutique** : ce qu'il couterait de l'acheter
au shop (buy_price de data/prices.csv, prix NTM de data/tag_prices.json), ou,
pour un objet qui ne s'achete pas, ce que coutent ses ingredients achetes au
shop (recettes de PalaMod, la moins chere). Les objets qu'on ne trouve qu'en
caisse ou en recompense ont une cote fixee ici (EXCLUSIVE), un niveau
d'enchantement vaut ENCHANT_LEVEL, et chaque pierre portee par un gant compte
pour une pierre.

Le controle : une caisse rend entre 80 et 95 % de son prix en valeur boutique,
avec NTM NEXT et sans (ses lots sortent alors du tirage). Un joueur qui revend
tout recupere bien moins (la revente se fait au prix de base, le quart du prix
boutique), et un joueur qui voulait ces objets aurait paye un peu plus cher en
les achetant : la caisse reste un puits a argent. Le script sort en erreur si
un palier sort de la fourchette, ou si un lot n'a pas de valeur connue.
"""
import csv
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUY_MARKUP = 4
LOW, HIGH = 0.80, 0.95
RARITIES = ["common", "rare", "epic", "legendary"]

# Ingredients vanilla absents du shop : une cote a la louche, au cout de fabrication.
VANILLA = {
    "minecraft:stick": 2, "#minecraft:planks": 2, "#minecraft:wool": 30, "minecraft:paper": 20,
    "minecraft:book": 100, "minecraft:glass": 10, "minecraft:chest": 20, "minecraft:dirt": 1,
    "minecraft:iron_block": 9 * 60, "minecraft:gold_block": 9 * 240, "minecraft:quartz_block": 4 * 40,
    "minecraft:blaze_powder": 200, "minecraft:ender_eye": 300, "minecraft:tnt": 300,
    "minecraft:hopper": 340, "minecraft:observer": 150, "minecraft:water_bucket": 180,
    "minecraft:lava_bucket": 180, "minecraft:bone_meal": 10, "minecraft:bone_block": 90,
}

# Ce qui ne s'achete nulle part : une cote, en valeur boutique.
STONE = 20_000_000
EXCLUSIVE = {
    # Les six pierres sont au meme prix : l'aleatoire en donne une au hasard.
    "palamod:legendary_stone_random": STONE,
    "palamod:legendary_stone_teleportation": STONE,
    "palamod:legendary_stone_invisibility": STONE,
    "palamod:legendary_stone_fortune": STONE,
    "palamod:legendary_stone_power": STONE,
    "palamod:legendary_stone_jobs": STONE,
    "palamod:legendary_stone_chaos": STONE,
    # Le gant nu : sa recette, deux lingots d'endium et deux coeurs en paladium.
    # Chaque pierre qu'il porte ajoute la cote d'une pierre.
    "palamod:endium_gauntlet": 2 * 1_440_000 + 2 * 2_170,
    # Les recompenses du concours de trixium.
    "palamod:cloud": 400_000,
    "palamod:pocket_enderchest": 1_500_000,
}

# Un niveau d'enchantement pose sur un lot.
ENCHANT_LEVEL = 1_500


def load_shop():
    values = {}
    with open(os.path.join(ROOT, "data", "prices.csv"), newline="") as handle:
        for row in csv.DictReader(handle):
            buy = int(row["buy_price"] or 0)
            base = int(row["base_price"] or 0)
            values[row["item_id"]] = buy if buy > 0 else base * BUY_MARKUP
    return values


def load_ntm():
    with open(os.path.join(ROOT, "data", "tag_prices.json")) as handle:
        data = json.load(handle)
    values = dict(data["components"])
    tags = {}
    for material, spec in data["materials"].items():
        unit = data["levels"][spec["level"]]["buy_unit"]
        for form, factor in data["forms"].items():
            if form.startswith("_"):
                continue
            tags["%s/%s" % (form, material)] = round(unit * factor)
    return values, tags


def ingredient_key(value):
    if isinstance(value, str):
        return value
    if isinstance(value, list):
        return value[0] if value else None
    return None


def load_recipes():
    recipes = []
    for path in glob.glob(os.path.join(ROOT, "src/main/resources/data/palamod/recipe/*.json")):
        with open(path) as handle:
            data = json.load(handle)
        result = data.get("result")
        if not isinstance(result, dict) or "id" not in result:
            continue
        inputs = []
        if "pattern" in data:
            for line in data["pattern"]:
                for char in line:
                    if char != " ":
                        inputs.append(ingredient_key(data["key"].get(char)))
        elif "ingredients" in data:
            inputs = [ingredient_key(value) for value in data["ingredients"]]
        elif "ingredient" in data:
            inputs = [ingredient_key(data["ingredient"])]
        else:
            continue
        recipes.append((result["id"], int(result.get("count", 1)), inputs))
    return recipes


def build_values():
    values = dict(VANILLA)
    values.update(load_shop())
    ntm, tags = load_ntm()
    values.update(ntm)
    values.update(EXCLUSIVE)
    fixed = set(values)
    recipes = load_recipes()
    # Le cout de fabrication, propage jusqu'a stabilite. Un objet du shop garde son prix.
    for _ in range(12):
        changed = False
        for result, count, inputs in recipes:
            if result in fixed or any(item is None or item not in values for item in inputs):
                continue
            cost = sum(values[item] for item in inputs) / count
            if result not in values or cost < values[result] - 1e-9:
                values[result] = cost
                changed = True
        if not changed:
            break
    return values, tags


def prize_value(prize, values, tags):
    """Un objet (item ou tag) et ses composants : sa valeur, son libelle, et s'il vient de NTM."""
    count = prize.get("count", 1)
    if "tag" in prize:
        label = prize["tag"]
        unit = tags.get(label)
        ntm = True
    else:
        label = prize["item"]
        unit = values.get(label)
        ntm = label.startswith("hbm:")
    if unit is None:
        return None, "sans cote : " + label, ntm
    components = prize.get("components", {})
    gauntlet = components.get("palamod:gauntlet", {})
    unit += STONE * len(gauntlet.get("stones", []))
    unit += ENCHANT_LEVEL * sum(components.get("minecraft:enchantments", {}).values())
    return unit * count, label, ntm


def entry_value(entry, values, tags):
    """La valeur moyenne d'une entree, son libelle, et si elle disparait sans NTM."""
    if entry.get("money_max", 0) > 0:
        value = (entry.get("money_min", 0) + entry["money_max"]) / 2.0
    else:
        value = float(entry.get("money_min", 0))
    prizes = ([entry] if "item" in entry or "tag" in entry else []) + entry.get("items", [])
    labels = []
    ntm = False
    for prize in prizes:
        worth, label, from_ntm = prize_value(prize, values, tags)
        if worth is None:
            return None, label, from_ntm
        value += worth
        labels.append(label)
        ntm = ntm or from_ntm
    if "name" in entry:
        label = entry["name"]
    elif labels:
        label = labels[0]
    else:
        label = "argent %s-%s" % (short(entry.get("money_min", 0)), short(entry.get("money_max", 0)))
    return value, label, ntm


def short(amount):
    for unit, size in (("M", 1e6), ("k", 1e3)):
        if abs(amount) >= size:
            return ("%.2f" % (amount / size)).rstrip("0").rstrip(".") + " " + unit
    return "%d" % round(amount)


def report(values, tags):
    with open(os.path.join(ROOT, "data", "crates.json")) as handle:
        crates = json.load(handle)["crates"]
    failed = False
    for tier, crate in crates.items():
        entries = crate["entries"]
        price = crate["price"]
        print("\n== %s (%s) ==" % (tier, short(price)))
        rows = []
        for entry in entries:
            value, label, ntm = entry_value(entry, values, tags)
            rarity = entry.get("rarity", "common")
            if rarity not in RARITIES:
                print("  ERREUR rarete inconnue : %s" % rarity)
                failed = True
                continue
            if value is None:
                print("  ERREUR %s" % label)
                failed = True
                continue
            rows.append((entry.get("weight", 1), rarity, label, entry.get("count", 1), value, ntm))
        for with_ntm in (True, False):
            kept = [row for row in rows if with_ntm or not row[5]]
            total = sum(row[0] for row in kept)
            expected = sum(row[0] * row[4] for row in kept) / total
            by_rarity = {name: sum(row[0] for row in kept if row[1] == name) for name in RARITIES}
            if with_ntm:
                for weight, rarity, label, count, value, ntm in sorted(kept, key=lambda row: RARITIES.index(row[1])):
                    print("  %5.1f %%  %-9s %-36s x%-3d %9s  %5.1f x%s" % (
                        100.0 * weight / total, rarity, label, count, short(value), value / price,
                        "  NTM" if ntm else ""))
                for name in RARITIES:
                    group = [row for row in kept if row[1] == name]
                    if group:
                        mean = sum(row[0] * row[4] for row in group) / sum(row[0] for row in group)
                        print("  %-9s %5.1f %%  lot moyen %9s  (%.2f x le prix)" % (
                            name, 100.0 * by_rarity[name] / total, short(mean), mean / price))
            ratio = expected / price
            print("  -> %s : rend %s en moyenne, %.1f %% du prix" % (
                "avec NTM" if with_ntm else "sans NTM", short(expected), 100 * ratio))
            if not LOW <= ratio <= HIGH:
                print("  ERREUR : hors de la fourchette %d-%d %%" % (LOW * 100, HIGH * 100))
                failed = True
    return failed


def main(argv):
    values, tags = build_values()
    if argv:
        for item in argv:
            value = values.get(item, tags.get(item))
            print("%-45s %s" % (item, "?" if value is None else short(value)))
        return 0
    return 1 if report(values, tags) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

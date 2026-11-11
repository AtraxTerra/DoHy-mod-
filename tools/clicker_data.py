#!/usr/bin/env python3
"""Ecrit data/palamod/clicker.json : les batiments, les ameliorations et le Clic Shop du clicker.

    python3 -I tools/clicker_data.py <fr_FR.trad> <PaladiumClicker/src/assets>

Le clicker de Paladium (PalaClicker 6.1.2) garde ses regles dans le jar et ses
tables sur le serveur. Les regles sont portees en Java (palamod/clicker) ; ce
script reconstitue les tables, a partir de trois sources :

  1. `config/translations/fr_FR.trad` du client Paladium : les identifiants et
     les noms des 35 batiments, des 21 categories et de toutes les
     ameliorations — c'est lui qui dit lesquelles existent ;
  2. les releves de la communaute (github.com/BroMinee/PaladiumClicker,
     `src/assets/*.json`) : le prix des batiments, et le prix et les conditions
     des ameliorations relevees — toutes celles du clic et de la production
     globale, celles des seize premiers batiments, huit categories ;
  3. le wiki (« Le Clicker ») : l'effet de chaque type d'amelioration et le
     nombre d'exemplaires qu'il demande.

Ce qui manque est prolonge par les regles que ces releves suivent, ou fixe
ici ; chaque choix est commente a l'endroit ou il est fait et repris dans
docs/PALAMOD.md. Le Clic Shop n'est releve nulle part (ses offres tournent
cote serveur) : sa table est ecrite ici, avec les objets du mod.
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "palamod", "clicker.json")

# L'icone de chaque batiment : PalaClicker montre un objet (ClickerBuilding.item),
# donne par le serveur. Ici un objet du jeu ou du mod qui dit la meme chose.
BUILDING_ITEMS = {
    "abandoned_mine": "minecraft:wooden_pickaxe",
    "big_rocks_cave": "minecraft:cobblestone",
    "dwarves_mine": "minecraft:iron_pickaxe",
    "grandma_garden": "minecraft:cookie",
    "witch_cabin": "minecraft:cauldron",
    "buzzing_hive": "minecraft:bee_nest",
    "snowy_forest": "minecraft:spruce_sapling",
    "frozen_iceberg": "minecraft:packed_ice",
    "pirate_port": "minecraft:oak_boat",
    "volcanic_island": "minecraft:magma_block",
    "scorching_dunes": "minecraft:red_sand",
    "mystical_forest": "minecraft:cherry_sapling",
    "lush_jungle": "minecraft:jungle_sapling",
    "forgotten_temple": "minecraft:mossy_stone_bricks",
    "millennial_crypt": "minecraft:skeleton_skull",
    "bottomless_pit": "minecraft:coal_block",
    "autonomous_mine": "minecraft:powered_rail",
    "golem_farm": "minecraft:carved_pumpkin",
    "radioactive_cave": "palamod:green_paladium_ingot",
    "biological_lab": "minecraft:brewing_stand",
    "thermonuclear_submarine": "minecraft:nautilus_shell",
    "kraken_pit": "minecraft:ink_sac",
    "enigmatic_fissure": "minecraft:ender_eye",
    "corrupted_lands": "minecraft:crying_obsidian",
    "matrix_glitch": "minecraft:command_block",
    "quantum_computer": "minecraft:observer",
    "particle_accelerator": "minecraft:beacon",
    "time_travel_machine": "minecraft:clock",
    "prehistoric_cave": "minecraft:bone",
    "extraterrestrial_pyramid": "minecraft:end_stone",
    "atlantis": "minecraft:prismarine_bricks",
    "great_ancients_pantheon": "minecraft:sculk_shrieker",
    "earth_center": "minecraft:lava_bucket",
    "devil_lair": "minecraft:nether_bricks",
    "eden_garden": "minecraft:apple",
}

# Les 25 ameliorations du clic sont des aliments : l'objet que l'on clique.
CLICK_ITEMS = [
    "minecraft:melon_slice", "minecraft:carrot", "minecraft:baked_potato", "minecraft:bread",
    "minecraft:milk_bucket", "minecraft:cocoa_beans", "minecraft:sugar", "minecraft:cookie",
    "minecraft:snowball", "minecraft:mushroom_stew", "minecraft:cooked_cod", "minecraft:cooked_chicken",
    "minecraft:cooked_porkchop", "minecraft:pumpkin_pie", "minecraft:cake", "minecraft:cooked_mutton",
    "minecraft:cooked_beef", "minecraft:cooked_salmon", "minecraft:golden_apple", "minecraft:tropical_fish",
    "minecraft:pufferfish", "minecraft:rabbit_stew", "minecraft:golden_carrot", "minecraft:salmon",
    "minecraft:enchanted_golden_apple",
]

GLOBAL_ITEMS = [
    "minecraft:book", "minecraft:bricks", "minecraft:piston", "minecraft:glass_bottle", "minecraft:gold_ingot",
    "minecraft:enchanted_book", "minecraft:redstone", "minecraft:ender_pearl", "palamod:endium_ingot",
    "minecraft:nether_star",
]

# Les quatre metiers : la categorie, le metier de DoHy, l'outil de l'amelioration de terrain.
JOBS = {
    "miner": ("MINER", "minecraft:diamond_pickaxe"),
    "farmer": ("FARMER", "minecraft:diamond_hoe"),
    "hunter": ("HUNTER", "minecraft:diamond_sword"),
    "alchemist": ("ALCHEMIST", "minecraft:brewing_stand"),
}

# Les batiments de chaque categorie, par leur rang. Les huit premieres sont
# relevees (active_list_index des ameliorations de terrain et de categorie) ;
# les autres sont deduites du nom des batiments.
CATEGORIES = {
    "miner": [0, 1, 2, 15, 16, 18, 32],
    "farmer": [3, 9, 12, 23, 34],
    "hunter": [10, 11, 13, 14, 21, 31],
    "alchemist": [4, 5, 6, 22, 27, 33],
    "humanoid": [2, 8],
    "frozen": [6, 7],
    "on_water": [7, 8],
    "forest": [6, 11, 12],
    "old_civilization": [13, 14, 29, 30],
    "garden": [3, 34],
    "hostile_creature": [5, 21, 28],
    "hot": [9, 10, 32, 33],
    "strange_world": [22, 23, 24],
    "deep": [15, 21, 32],
    "technologic": [16, 19, 26, 27],
    "robot": [16, 17],
    "radioactive": [18, 20],
    "futuristic": [25, 26, 27],
    "under_water": [20, 21, 30],
    "computer": [24, 25],
    "old_god": [31, 33, 34],
}

# L'icone des ameliorations de categorie.
CATEGORY_ITEMS = {
    "category_miner_1": "palamod:paladium_pickaxe", "category_miner_2": "palamod:endium_pickaxe",
    "category_farmer_1": "palamod:paladium_seedplanter", "category_farmer_2": "palamod:endium_seedplanter",
    "category_hunter_1": "palamod:paladium_sword", "category_hunter_2": "palamod:endium_sword",
    "category_alchemist_1": "minecraft:obsidian", "category_alchemist_2": "minecraft:crying_obsidian",
    "category_humanoid": "minecraft:emerald", "category_frozen": "minecraft:leather_chestplate",
    "category_on_water": "minecraft:potion", "category_forest": "palamod:endium_axe",
    "category_robot": "minecraft:heart_of_the_sea", "category_strange_world": "minecraft:compass",
    "category_radioactive": "minecraft:iron_chestplate", "category_garden": "minecraft:bone_meal",
    "category_hot": "minecraft:water_bucket", "category_old_civilization": "minecraft:writable_book",
    "category_hostile_creature": "palamod:capture_sword", "category_technologic": "minecraft:repeater",
    "category_deep": "minecraft:sea_lantern", "category_futuristic": "minecraft:comparator",
    "category_computer": "minecraft:redstone_lamp", "category_under_water": "minecraft:turtle_helmet",
    "category_old_god": "minecraft:enchanted_book",
}

# Le Clic Shop. (identifiant, rayon, objet, nombre, premier batiment requis,
# batiment qui retire l'offre ou None, prix en part du prix de base du batiment
# requis, achats par rotation, poids du tirage).
#
# Une offre s'achete avec ce que couterait le batiment qui l'ouvre : c'est un
# choix entre produire plus et repartir avec quelque chose. Les quantites sont
# petites et les achats comptes ; voir docs/BALANCE.md.
SHOP = [
    ("daily_iron", "daily", "minecraft:iron_ingot", 8, 0, 9, 0.6, 3, 10),
    ("daily_bread", "daily", "minecraft:bread", 16, 0, 9, 0.4, 3, 10),
    ("daily_amethyst", "daily", "palamod:amethyst_ingot", 4, 1, 12, 0.6, 3, 10),
    ("daily_gold", "daily", "minecraft:gold_ingot", 4, 2, 12, 0.5, 3, 8),
    ("daily_titane", "daily", "palamod:titane_ingot", 4, 3, 16, 0.6, 3, 10),
    ("daily_xp", "daily", "minecraft:experience_bottle", 8, 4, None, 0.4, 3, 8),
    ("daily_diamond", "daily", "minecraft:diamond", 2, 5, None, 0.5, 2, 6),
    ("daily_paladium", "daily", "palamod:paladium_ingot", 4, 6, None, 0.5, 3, 10),
    ("daily_golden_apple", "daily", "minecraft:golden_apple", 1, 8, None, 0.3, 2, 6),
    ("daily_green_paladium", "daily", "palamod:green_paladium_ingot", 2, 12, None, 0.3, 2, 6),
    ("daily_endium_fragment", "daily", "palamod:endium_fragment", 1, 18, None, 0.2, 2, 4),

    ("weekly_iron", "weekly", "minecraft:iron_ingot", 32, 0, 10, 1.5, 2, 10),
    ("weekly_amethyst", "weekly", "palamod:amethyst_ingot", 16, 0, 14, 2.0, 2, 10),
    ("weekly_titane", "weekly", "palamod:titane_ingot", 16, 2, 18, 1.5, 2, 10),
    ("weekly_xp", "weekly", "minecraft:experience_bottle", 32, 3, None, 1.5, 2, 8),
    ("weekly_talikus_key", "weekly", "palamod:talikus_key", 1, 4, None, 1.0, 2, 6),
    ("weekly_paladium", "weekly", "palamod:paladium_ingot", 16, 5, None, 1.5, 2, 10),
    ("weekly_golden_apple", "weekly", "minecraft:golden_apple", 4, 6, None, 1.0, 2, 6),
    ("weekly_diamond", "weekly", "minecraft:diamond", 8, 7, None, 1.5, 2, 8),
    ("weekly_nimbria_key", "weekly", "palamod:nimbria_key", 1, 8, None, 1.0, 2, 6),
    ("weekly_paladium_block", "weekly", "palamod:paladium_block", 2, 10, None, 1.0, 2, 8),
    ("weekly_vitalys_key", "weekly", "palamod:vitalys_key", 1, 12, None, 1.0, 2, 6),
    ("weekly_green_paladium", "weekly", "palamod:green_paladium_ingot", 8, 14, None, 1.0, 2, 8),
    ("weekly_manelios_key", "weekly", "palamod:manelios_key", 1, 16, None, 1.0, 2, 6),
    ("weekly_endium_fragment", "weekly", "palamod:endium_fragment", 3, 20, None, 1.0, 2, 6),
    ("weekly_notch_apple", "weekly", "minecraft:enchanted_golden_apple", 1, 22, None, 1.0, 1, 4),
    ("weekly_endium_nugget", "weekly", "palamod:endium_nugget", 1, 24, None, 1.0, 1, 4),
    ("weekly_endium_ingot", "weekly", "palamod:endium_ingot", 1, 30, None, 1.0, 1, 2),
]
# Le rayon des metiers : des bouteilles d'XP, de plus en plus nombreuses.
for job in ("miner", "farmer", "hunter", "alchemist"):
    SHOP.append(("jobs_%s_1" % job, "jobs", "palamod:job_bottle_%s" % job, 1, 2, 10, 1.0, 2, 10))
    SHOP.append(("jobs_%s_3" % job, "jobs", "palamod:job_bottle_%s" % job, 3, 10, 20, 1.0, 2, 10))
    SHOP.append(("jobs_%s_5" % job, "jobs", "palamod:job_bottle_%s" % job, 5, 20, None, 1.0, 2, 10))


def number(value):
    return int(float(str(value).replace(",", ".")))


def conditions(entry):
    """Les conditions relevees : pieces recoltees, jour de la saison."""
    out = []
    for condition in entry.get("condition", []):
        if "coins" in condition:
            out.append({"type": "quantity", "value": number(condition["coins"])})
        elif "day" in condition and number(condition["day"]) > 0:
            out.append({"type": "time", "value": number(condition["day"])})
    return out


def main():
    trad = json.load(open(sys.argv[1], encoding="utf-8"))
    trad = trad.get("translations", trad)
    assets = sys.argv[2]

    def load(name):
        return json.load(open(os.path.join(assets, name + ".json"), encoding="utf-8"))

    def names(prefix):
        return [(key[len(prefix):], value) for key, value in trad.items() if key.startswith(prefix)]

    prices = [number(row["price"]) for row in sorted(load("building"), key=lambda row: row["index"])]
    ids = [key for key, _ in names("clicker.building.")]
    assert len(ids) == len(prices) == 35, (len(ids), len(prices))
    index_of = {key: index for index, key in enumerate(ids)}
    categories_of = {key: [] for key in ids}
    for category, members in CATEGORIES.items():
        for member in members:
            categories_of[ids[member]].append(category)

    buildings = [{"id": key, "name": name, "item": BUILDING_ITEMS[key], "price": prices[index_of[key]],
                  "categories": categories_of[key]} for key, name in names("clicker.building.")]
    upgrade_names = dict(names("clicker.upgrade."))
    upgrades = []

    def upgrade(key, item, price, kind, target, modifier, when, job=None):
        entry = {"id": key, "name": upgrade_names[key], "item": item, "price": int(price), "type": kind,
                 "target": target, "modifier": modifier, "conditions": when}
        if job:
            entry["job"] = job
        upgrades.append(entry)

    def owns(key, count):
        return {"type": "building", "id": key, "count": count}

    def after(key):
        return {"type": "upgrade", "id": key}

    # -- Le clic : chaque amelioration double le clic (1, 2, 4...). PalaClicker
    # additionne les modificateurs : 1 x (1 + somme), donc 2^(n-1) pour la n-ieme.
    # Wiki : « une amelioration superieure exige la precedente ».
    for row in sorted(load("CPS"), key=lambda row: row["index"]):
        rank = row["index"] + 1
        when = conditions(row) + ([after("click_%d" % (rank - 1))] if rank > 1 else [])
        upgrade("click_%d" % rank, CLICK_ITEMS[rank - 1], number(row["price"]), "click", "", float(2 ** (rank - 1)),
                when)

    # -- Production globale : +10 % chacune, dans l'ordre (wiki).
    for rank, row in enumerate(load("global_upgrade"), start=1):
        when = conditions(row) + ([after("global_%d" % (rank - 1))] if rank > 1 else [])
        upgrade("global_%d" % rank, GLOBAL_ITEMS[rank - 1], number(row["price"]), "global", "", 0.10, when)

    # -- Connaissance de terrain : +1 % par niveau du metier, sur sa categorie.
    order = ["miner", "farmer", "hunter", "alchemist"]
    for category, row in zip(order, load("terrain_upgrade")):
        job, item = JOBS[category]
        upgrade("terrain_" + category, item, number(row["price"]), "terrain", category, 0.01, conditions(row), job)

    # -- Par batiment. Les seize premiers sont releves ; au-dela, le prix suit le
    # multiple du prix du batiment que les releves suivent des le quatrieme
    # (x9, x19, x4, x2,25), et le jour avance de 5,5 par batiment, ce qui amene le
    # dernier au jour 147, celui de la derniere amelioration globale relevee.
    known = {}
    for name, rows in (("1", load("building_upgrade")[:16]), ("2", load("building_upgrade")[16:]),
                       ("many", load("many_upgrade")), ("posterior", load("posterior_upgrade"))):
        for row in rows:
            days = [number(c["day"]) for c in row["condition"] if "day" in c]
            known[(row["active_index"], name)] = (number(row["price"]), days[0] if days else 0)
    multiple = {"1": 9.0, "2": 19.0, "many": 4.0, "posterior": 2.25}
    shift = {"1": 0, "2": 1, "many": -1, "posterior": 0}
    # Le nombre d'exemplaires que chaque type demande : celui du wiki.
    copies = {"1": 5, "2": 10, "many": 7, "posterior": 4}
    kinds = {"1": ("building", 1.0), "2": ("building", 1.0), "many": ("many", 0.01), "posterior": ("posterior", 0.01)}
    for suffix in ("1", "2", "many", "posterior"):
        for key in ids:
            upgrade_id = "%s_%s" % (key, suffix)
            if upgrade_id not in upgrade_names:
                # La traduction ecrit une cle avec des majuscules ; c'est la meme amelioration.
                match = [name for name in upgrade_names if name.lower() == upgrade_id]
                if not match:
                    continue
                upgrade_names[upgrade_id] = upgrade_names[match[0]]
            index = index_of[key]
            if (index, suffix) in known:
                price, day = known[(index, suffix)]
            else:
                price = prices[index] * multiple[suffix]
                day = round(43 + (index - 15) * 5.5) + shift[suffix]
            when = ([{"type": "time", "value": day}] if day > 0 else []) + [owns(key, copies[suffix])]
            kind, modifier = kinds[suffix]
            upgrade(upgrade_id, BUILDING_ITEMS[key], price, kind, key, modifier, when)

    # -- Par categorie. Releve : les quatre outils en paladium donnent +10 % et
    # demandent un batiment ; les autres donnent +50 %. Wiki : +50 %, un
    # exemplaire de chaque batiment de la categorie. Les outils en paladium
    # gardent leur releve ; tout le reste suit le wiki. Un prix non releve vaut
    # quatre fois le dernier batiment de la categorie.
    recorded = {row["name"]: row for row in load("category_upgrade")}
    for key, name in names("clicker.upgrade.category_"):
        upgrade_id = "category_" + key
        category = key[:-2] if key[-2:] in ("_1", "_2") else key
        members = [ids[index] for index in CATEGORIES[category]]
        row = recorded.get(name)
        if key.endswith("_1"):
            need = [c for c in row["condition"] if "index" in c][0]
            when = [owns(ids[need["index"]], need["own"])]
            modifier = row["pourcentage"]
        else:
            when = [owns(member, 1) for member in members]
            if key.endswith("_2"):
                when.append(after("category_%s_1" % category))
            modifier = 0.5
        price = number(row["price"]) if row else prices[max(CATEGORIES[category])] * 4
        upgrade(upgrade_id, CATEGORY_ITEMS[upgrade_id], price, "category", category, modifier, when)

    missing = sorted(set(upgrade_names) - {entry["id"] for entry in upgrades}
                     - {name for name in upgrade_names if name.lower() != name})
    assert not missing, missing

    shop = []
    for key, kind, item, count, first, last, ratio, quantity, weight in SHOP:
        entry = {"id": key, "type": kind, "item": item, "count": count, "price": int(prices[first] * ratio),
                 "quantity": quantity, "weight": weight, "requirement": {ids[first]: 1}}
        if last is not None:
            entry["forbidden"] = {ids[last]: 1}
        shop.append(entry)

    data = {
        "_comment": "GENERE par tools/clicker_data.py : le clicker de Paladium (PalaClicker 6.1.2). "
                    "Noms : traductions du client. Prix et conditions : releves de la communaute et wiki. "
                    "Clic Shop : ecrit pour DoHy.",
        "categories": {key: name for key, name in names("clicker.category.")},
        "buildings": buildings,
        "upgrades": upgrades,
        "shop": shop,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=1, ensure_ascii=False)
        handle.write("\n")
    by_type = {}
    for entry in upgrades:
        by_type[entry["type"]] = by_type.get(entry["type"], 0) + 1
    print("%d batiments, %d ameliorations %s, %d offres" % (len(buildings), len(upgrades), by_type, len(shop)))


if __name__ == "__main__":
    main()

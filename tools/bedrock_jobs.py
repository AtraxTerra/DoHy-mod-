#!/usr/bin/env python3
"""Les sources d'XP du Mineur, du Fermier et du Chasseur, sur le bareme de Paladium Bedrock.

Usage :
    python3 -I tools/bedrock_jobs.py

Demande du joueur (2026-11-15) : « aide toi de https://palatracker.bromine.fr/xp-calculator/bedrock
et met a jour le systeme de metiers », puis le choix « tout recalculer sur Bedrock » (casse,
cuisson et creatures) et « la cuisson aux trois metiers ».

Le bareme. Les valeurs de BEDROCK sont celles du calculateur d'XP de PalaTracker (version Bedrock :
champ `bedrock` de `how_to_xp` dans `src/lib/constants.ts` de BroMinee/PaladiumClicker, identique
au site le 2026-11-15). Elles sont reprises telles quelles : une XP de Bedrock vaut une XP de DoHy.
Pourquoi ce rapport : il faut la meme XP pour le niveau 50 de DoHy (1 031 701) que pour le 10 de
Bedrock (1 061 305), et les niveaux de DoHy valent ceux de Bedrock multiplies par 5. Avant, DoHy
demande jusqu'a deux fois plus d'XP pour un meme palier ; apres, de moins en moins (le niveau 100
demande 6,2 M, le 20 de Bedrock 35,8 M). Un niveau de Bedrock devient un niveau de DoHy fois 5 :
c'est le niveau a partir duquel la source rapporte, en dessous elle ne donne rien.

Ce que Bedrock ne connait pas. Les sources propres a DoHy (blocs du jeu recent, minerais de
PalaMod que Bedrock n'a pas, cultures de DoHy, creatures de fin de jeu) gardent leur place : leur
ancienne valeur est multipliee par le rapport mediane Bedrock / ancien de leur famille, calcule
ici sur les sources que les deux tables partagent, et arrondie (a l'unite sous 100, a deux
chiffres au-dessus). Leur niveau suit la source de Bedrock la plus proche (voir LEVELS).

Le script reecrit les sources de ces trois metiers dans data/job_progression.json ; l'alchimiste
(absent de Bedrock), la courbe et les primes ne bougent pas.
"""
import json
import os
import statistics

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
PROGRESSION = os.path.join(ROOT, "data", "job_progression.json")

# -- Le bareme de Bedrock (PalaTracker) : (identifiant DoHy, XP, niveau de Bedrock) --------------

BEDROCK = {
    "miner": {
        "blocks": [
            ("minecraft:andesite", 1, 0), ("minecraft:granite", 1, 0), ("minecraft:diorite", 1, 0),
            ("minecraft:tuff", 3, 0),
            ("minecraft:coal_ore", 1, 0), ("minecraft:deepslate_coal_ore", 2, 10),
            ("minecraft:nether_quartz_ore", 6, 0),
            ("minecraft:copper_ore", 2, 0), ("minecraft:deepslate_copper_ore", 3, 10),
            ("minecraft:obsidian", 6, 0),
            ("minecraft:lapis_ore", 8, 0), ("minecraft:deepslate_lapis_ore", 12, 10),
            ("minecraft:redstone_ore", 6, 0), ("minecraft:deepslate_redstone_ore", 15, 10),
            ("minecraft:emerald_ore", 30, 0), ("minecraft:deepslate_emerald_ore", 45, 10),
            ("minecraft:iron_ore", 3, 0), ("minecraft:deepslate_iron_ore", 5, 10),
            ("minecraft:gold_ore", 6, 0),
            ("minecraft:diamond_ore", 20, 2), ("minecraft:deepslate_diamond_ore", 30, 10),
            ("palamod:amethyst_ore", 18, 5), ("palamod:titane_ore", 27, 5),
            ("palamod:paladium_ore", 36, 5), ("palamod:findium_ore", 100, 5),
            # « Nugget Green Paladium » : le paladium vert de Bedrock, le plus proche du minerai de PalaMod.
            ("palamod:paladium_green_ore", 40, 15),
        ],
        "smelted": [
            ("minecraft:copper_ingot", 1, 2), ("minecraft:iron_ingot", 2, 2), ("minecraft:gold_ingot", 3, 3),
            ("palamod:amethyst_ingot", 4, 5),
            ("palamod:titane_ingot", 5, 5), ("palamod:titane_ingot", 50, 6),
            ("palamod:paladium_ingot", 6, 5), ("palamod:paladium_ingot", 150, 8),
            ("palamod:green_paladium_ingot", 200, 12), ("palamod:green_paladium_ingot", 400, 15),
        ],
    },
    "farmer": {
        "blocks": [
            ("minecraft:wheat", 1, 0), ("minecraft:potatoes", 2, 1), ("minecraft:carrots", 3, 3),
            ("minecraft:beetroots", 8, 5), ("minecraft:cactus", 1, 7), ("minecraft:sugar_cane", 2, 9),
            ("minecraft:pumpkin", 3, 11), ("minecraft:melon", 5, 13),
            ("palamod:eggplant_crop", 20, 3), ("palamod:chervil_crop", 40, 6), ("palamod:kiwano_crop", 10, 14),
            ("palamod:orangeblue_crop", 200, 19),
        ],
        "smelted": [("minecraft:baked_potato", 2, 2)],
        "crafted": [("minecraft:bread", 5, 4), ("minecraft:pumpkin_pie", 2, 15)],
    },
    "hunter": {
        "entities": [
            ("minecraft:cow", 80, 0), ("minecraft:sheep", 90, 1), ("minecraft:chicken", 100, 2),
            ("minecraft:pig", 110, 3), ("minecraft:zombie", 160, 4), ("minecraft:skeleton", 200, 6),
            ("minecraft:creeper", 140, 8), ("minecraft:spider", 210, 8), ("minecraft:cave_spider", 220, 10),
            ("minecraft:bat", 300, 12), ("minecraft:wither_skeleton", 400, 14), ("minecraft:blaze", 500, 16),
        ],
        "smelted": [
            ("minecraft:cooked_beef", 40, 1), ("minecraft:cooked_mutton", 45, 2), ("minecraft:cooked_salmon", 45, 2),
            ("minecraft:cooked_chicken", 50, 3), ("minecraft:cooked_porkchop", 55, 4),
            ("minecraft:cooked_rabbit", 55, 4),
        ],
    },
}

# Bedrock, deduit : le minerai d'or des abimes n'y a pas de ligne. Les variantes des abimes y valent
# 1,5 fois la pierre (mediane : cuivre, lapis, emeraude, diamant), au niveau 10.
INFERRED = {"miner": {"blocks": [("minecraft:deepslate_gold_ore", 9, 10)]}}

# Ajoutees depuis le bareme, avec leur contenu : gardees telles quelles a chaque regeneration. Le
# sable d'epice d'Arrakis (2026-11-17) : 2 XP de Mineur des le niveau 0.
EXTRA = {"miner": {"blocks": [("dohy:spice_sand", 2, 0)]}}

# -- L'ancienne table de DoHy (avant le 2026-11-15), pour les rapports et les sources propres --------

OLD = {
    "miner": {
        "minecraft:coal_ore": 5, "minecraft:deepslate_coal_ore": 6, "minecraft:iron_ore": 12,
        "minecraft:deepslate_iron_ore": 14, "minecraft:copper_ore": 10, "minecraft:deepslate_copper_ore": 12,
        "minecraft:gold_ore": 20, "minecraft:deepslate_gold_ore": 23, "minecraft:redstone_ore": 18,
        "minecraft:deepslate_redstone_ore": 21, "minecraft:lapis_ore": 22, "minecraft:deepslate_lapis_ore": 25,
        "minecraft:diamond_ore": 90, "minecraft:deepslate_diamond_ore": 100, "minecraft:emerald_ore": 110,
        "minecraft:deepslate_emerald_ore": 125, "minecraft:nether_quartz_ore": 8, "minecraft:nether_gold_ore": 7,
        "minecraft:ancient_debris": 400, "minecraft:sulfur": 8, "minecraft:cinnabar": 8,
        "minecraft:sulfur_spike": 6, "minecraft:amethyst_cluster": 15,
        "palamod:amethyst_ore": 12, "palamod:titane_ore": 16, "palamod:paladium_ore": 30,
        "palamod:findium_ore": 75, "palamod:trixium_ore": 140, "palamod:invoker_ore": 170,
        "#dohy:mining_xp/low": 30, "#dohy:mining_xp/mid": 80, "#dohy:mining_xp/high": 180,
        "#dohy:mining_xp/elite": 350,
    },
    "farmer": {
        "minecraft:wheat": 2, "minecraft:carrots": 2, "minecraft:potatoes": 2, "minecraft:beetroots": 2,
        "minecraft:nether_wart": 2, "minecraft:melon": 3, "minecraft:pumpkin": 3, "minecraft:sugar_cane": 1,
        "minecraft:cocoa": 3, "minecraft:sweet_berry_bush": 2, "minecraft:torchflower_crop": 40,
        "minecraft:pitcher_crop": 40, "palamod:eggplant_crop": 8, "palamod:chervil_crop": 15,
        "palamod:kiwano_crop": 30, "palamod:orangeblue_crop": 60, "dohy:corn_crop": 4, "dohy:oat_crop": 4,
        "dohy:rye_crop": 4, "dohy:barley_crop": 4, "dohy:soy_crop": 4, "dohy:sunflower_crop": 4,
        "dohy:fonio_crop": 14, "dohy:sorghum_crop": 14, "dohy:kamut_crop": 14,
    },
    # Les animaux comptaient pour le Fermier ; sur Bedrock, ils sont au Chasseur.
    "animals": {"minecraft:cow": 3, "minecraft:pig": 3, "minecraft:sheep": 3, "minecraft:chicken": 2,
                "minecraft:rabbit": 3},
    "hunter": {
        "minecraft:zombie": 4, "minecraft:skeleton": 5, "minecraft:spider": 4, "minecraft:creeper": 7,
        "minecraft:enderman": 12, "minecraft:witch": 18, "minecraft:blaze": 15,
        "minecraft:wither_skeleton": 25, "minecraft:guardian": 10, "minecraft:elder_guardian": 120,
        "minecraft:shulker": 30, "minecraft:piglin_brute": 35, "minecraft:warden": 800,
        "minecraft:wither": 1200, "minecraft:ender_dragon": 2000, "minecraft:sulfur_cube": 8,
    },
}

# Les sources propres a DoHy : (famille, niveau de Bedrock). Le niveau vient de la source de Bedrock
# la plus proche : la variante des abimes pour les debris antiques, le palier des minerais de
# PalaMod pour le trixium et l'invocateur, et pour les creatures celui de la creature de Bedrock la
# plus forte qui ne rapporte pas davantage (les boss face aux Arty).
LEVELS = {
    "miner": {
        "minecraft:ancient_debris": ("vanilla", 10), "minecraft:nether_gold_ore": ("vanilla", 0),
        "minecraft:sulfur": ("vanilla", 0), "minecraft:cinnabar": ("vanilla", 0),
        "minecraft:sulfur_spike": ("vanilla", 0), "minecraft:amethyst_cluster": ("vanilla", 0),
        "palamod:trixium_ore": ("palamod", 5), "palamod:invoker_ore": ("palamod", 5),
        "#dohy:mining_xp/low": ("vanilla", 0), "#dohy:mining_xp/mid": ("vanilla", 0),
        "#dohy:mining_xp/high": ("vanilla", 0), "#dohy:mining_xp/elite": ("vanilla", 0),
    },
    "farmer": {key: ("crops", 0) for key in (
        "minecraft:nether_wart", "minecraft:cocoa", "minecraft:sweet_berry_bush", "minecraft:torchflower_crop",
        "minecraft:pitcher_crop", "dohy:corn_crop", "dohy:oat_crop", "dohy:rye_crop", "dohy:barley_crop",
        "dohy:soy_crop", "dohy:sunflower_crop", "dohy:fonio_crop", "dohy:sorghum_crop", "dohy:kamut_crop")},
    "hunter": {
        "minecraft:rabbit": ("animals", 4),
        "minecraft:enderman": ("hostile", None), "minecraft:witch": ("hostile", None),
        "minecraft:guardian": ("hostile", None), "minecraft:shulker": ("hostile", None),
        "minecraft:piglin_brute": ("hostile", None), "minecraft:sulfur_cube": ("hostile", None),
        "minecraft:elder_guardian": ("boss", None), "minecraft:warden": ("boss", None),
        "minecraft:wither": ("boss", None), "minecraft:ender_dragon": ("boss", None),
    },
}

# Les boss de Bedrock (Paladium Arty, Green Paladium Arty, Endium Arty) : (XP, niveau de Bedrock).
ARTY = [(1000, 0), (20000, 16), (40000, 19)]

# Les creatures de PalaMod que Bedrock n'a pas : leur XP de PalaJobs (HunterRegisterer,
# EntityKillObjective) et leur niveau de PalaJobs, sur 20 comme ceux de Bedrock. Elles passent au
# rapport median Bedrock / PalaJobs des animaux que les deux baremes partagent (vache, mouton,
# poulet et cochon valent 14 XP dans PalaJobs), arrondi comme les autres : l'elephant, 15 XP de
# PalaJobs au niveau 8, vaut ainsi a peu pres une vache. L'escargot (25 XP au niveau 0) et le
# serpent (120 XP au niveau 15) viennent du meme HunterRegisterer ; ils donnent l'appat des
# familiers.
PALAJOBS_ANIMALS = {"minecraft:cow": 14, "minecraft:sheep": 14, "minecraft:chicken": 14, "minecraft:pig": 14}
PALAMOD_CREATURES = {"palamod:elephant": (15, 8), "palamod:snail": (25, 0), "palamod:snake": (120, 15)}

HOSTILE = ("minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:creeper", "minecraft:blaze",
           "minecraft:wither_skeleton")
PALAMOD = ("palamod:amethyst_ore", "palamod:titane_ore", "palamod:paladium_ore", "palamod:findium_ore")


def rounded(value):
    if value < 100:
        return max(1, int(round(value)))
    digits = len(str(int(value))) - 2
    return int(round(value / 10 ** digits)) * 10 ** digits


def ratios():
    """Le rapport median Bedrock / ancien de chaque famille, sur les sources communes."""
    bedrock = {job: {key: xp for kind in tables.values() for key, xp, _ in kind} for job, tables in BEDROCK.items()}
    miner = [(bedrock["miner"][k] / v, k) for k, v in OLD["miner"].items() if k in bedrock["miner"]]
    families = {
        "vanilla": [r for r, k in miner if k.startswith("minecraft:")],
        "palamod": [r for r, k in miner if k in PALAMOD],
        "crops": [bedrock["farmer"][k] / v for k, v in OLD["farmer"].items() if k in bedrock["farmer"]],
        "animals": [bedrock["hunter"][k] / v for k, v in OLD["animals"].items() if k in bedrock["hunter"]],
        "hostile": [bedrock["hunter"][k] / OLD["hunter"][k] for k in HOSTILE],
        "palajobs": [bedrock["hunter"][k] / v for k, v in PALAJOBS_ANIMALS.items() if k in bedrock["hunter"]],
    }
    return {name: statistics.median(values) for name, values in families.items()}


def level_for(xp, family, hunter):
    """Le niveau de Bedrock d'une creature propre a DoHy, d'apres celles de Bedrock."""
    if family == "boss":
        return max(level for arty, level in ARTY if arty <= xp)
    candidates = [(value, level) for value, level in hunter if value <= xp]
    return max(candidates)[1] if candidates else 0


def tiers(entries):
    """{identifiant: valeur} : un nombre pour un seul palier au niveau 0, sinon la liste des paliers."""
    grouped = {}
    for key, xp, level in entries:
        grouped.setdefault(key, []).append({"level": level * 5, "xp": xp})
    result = {}
    for key, steps in grouped.items():
        steps.sort(key=lambda step: step["level"])
        if len(steps) == 1 and steps[0]["level"] == 0:
            result[key] = steps[0]["xp"]
        elif len(steps) == 1:
            result[key] = steps[0]
        else:
            result[key] = steps
    return result


def main():
    factor = ratios()
    hostile = [(xp, level) for key, xp, level in BEDROCK["hunter"]["entities"] if key in HOSTILE
               or key in ("minecraft:cave_spider", "minecraft:bat")]
    derived = {"miner": [], "farmer": [], "hunter": []}
    for job, table in LEVELS.items():
        for key, (family, level) in table.items():
            old = OLD["animals" if family == "animals" else job][key]
            xp = rounded(old * factor["hostile" if family == "boss" else family])
            if level is None:
                level = level_for(xp, family, hostile)
            derived[job].append((key, xp, level))
    for key, (xp, level) in PALAMOD_CREATURES.items():
        derived["hunter"].append((key, rounded(xp * factor["palajobs"]), level))

    root = json.load(open(PROGRESSION, encoding="utf-8"))
    notes = {
        "miner": "Bareme de Paladium Bedrock (tools/bedrock_jobs.py) : XP de PalaTracker telles quelles, "
                 "niveaux de Bedrock x 5, en dessous du niveau la source ne rapporte rien. La pierre et "
                 "l'ardoise ne rapportent rien, comme sur Bedrock. smelted : chaque objet sorti cuit du four, "
                 "du haut fourneau ou du palafurnace a la main, ou d'un outil en fusion.",
        "farmer": "Bareme de Paladium Bedrock (tools/bedrock_jobs.py). Les animaux comptent pour le Chasseur, "
                  "comme sur Bedrock. crafted : chaque objet fabrique a l'etabli.",
        "hunter": "Bareme de Paladium Bedrock (tools/bedrock_jobs.py), animaux compris. Une creature nee d'un "
                  "spawner, ou tuee au controleur, ne rapporte qu'un quarantieme ; un petit, rien. Les "
                  "creatures de PalaMod (l'elephant, l'escargot, le serpent) : leur XP de PalaJobs au rapport des "
                  "animaux.",
    }
    for job in ("miner", "farmer", "hunter"):
        sources = {"_bedrock": notes[job]}
        for kind in ("blocks", "entities", "smelted", "crafted"):
            entries = list(BEDROCK[job].get(kind, []))
            entries += INFERRED.get(job, {}).get(kind, [])
            entries += EXTRA.get(job, {}).get(kind, [])
            if kind == ("entities" if job == "hunter" else "blocks"):
                entries += derived[job]
            if entries:
                sources[kind] = tiers(entries)
        root["jobs"][job]["sources"] = sources
    root["_comment"] = ("Progression des quatre metiers : courbe, primes de palier et sources d'XP. Editable a "
                        "chaud via /dohy reload. Les deblocages et les recompenses en objets sont dans jobs.json, "
                        "genere depuis le bytecode de PalaMod. Depuis le 2026-11-15, les sources du Mineur, du "
                        "Fermier et du Chasseur suivent le bareme de Paladium Bedrock (tools/bedrock_jobs.py) ; "
                        "celles de l'Alchimiste, absent de Bedrock, restent celles de DoHy. Une valeur est un "
                        "nombre (des le niveau 0) ou des paliers {level, xp} : le plus haut atteint s'applique.")
    with open(PROGRESSION, "w", encoding="utf-8") as fh:
        json.dump(root, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    print("rapports medians :", {name: round(value, 3) for name, value in factor.items()})
    for job in ("miner", "farmer", "hunter"):
        print(job, ":", ", ".join("%s %s (%s)" % (k.split(":")[1], x, l * 5) for k, x, l in derived[job]))


if __name__ == "__main__":
    main()

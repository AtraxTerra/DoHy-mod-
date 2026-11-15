#!/usr/bin/env python3
"""Les minerais de NTM NEXT dans la Mine (`dohy:mine`).

Usage :
    python3 -I tools/mine_ntm_ores.py chemin/vers/hbm.jar

Pourquoi des copies. La Mine reprenait les placed features de NTM NEXT telles quelles (tag
`dohy:mine_mod_ores`). Or presque toutes commencent par le placement `hbm:world_config`, qui lit
l'interrupteur `overworld_ores` du datapack ; celui de la Terre le coupe, pour que les minerais de
NTM ne viennent plus que des filons par pays. Avec lui, la Mine n'avait donc plus un seul minerai
de NTM.

Ce script lit les placed features dans le jar de NTM NEXT, retire `overworld_ores` de leur
placement (les autres interrupteurs restent : le coltan garde `coltan_spawn`, coupe par defaut dans
NTM), et ecrit des copies sous `data/dohy/worldgen/placed_feature/mine/hbm/`. Elles pointent sur
les configured features de NTM, ne se chargent que si NTM NEXT est la (`neoforge:conditions`), et
le tag `dohy:mine_mod_ores` (ecrit par `tools/dohy_assets.py`) les reference en entrees
facultatives.

Absents volontairement : l'australium (NTM ne le pose que dans un anneau de 350 a 700 blocs du
centre du monde, et les zones de la Mine sont a 200 000 blocs et plus), les gisements de bedrock et
de petrole (pour des machines, inutiles dans une mine remise a neuf toutes les cinq heures) et les
poches de gaz inflammable (un danger, pas un minerai).

Releve sur NTM NEXT 1.0.3+1b8c6c2d. Le jar est une donnee : il est lu comme une archive, rien n'en
est execute.
"""
import json
import os
import sys
import zipfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "dohy", "worldgen", "placed_feature", "mine", "hbm")
TAG = os.path.join(ROOT, "src", "main", "resources", "data", "dohy", "tags", "worldgen", "placed_feature",
                   "mine_mod_ores.json")

# Dans l'ordre de pose : les strates d'abord (le gneiss avant les minerais qui le remplacent),
# comme NTM les pose a l'etape des modifications locales, avant celle des minerais.
NAMES = [
    "stratum_gneiss", "stratum_hematite", "stratum_bauxite", "stratum_malachite",
    "ore_cave_sulfur", "ore_cave_asbestos",
    "ore_uranium", "ore_thorium", "ore_titanium", "ore_sulfur", "ore_aluminium", "ore_fluorite",
    "ore_niter", "ore_tungsten", "ore_lead", "ore_beryllium", "ore_rare", "ore_lignite", "ore_asbestos",
    "ore_cinnabar", "ore_cobalt", "ore_coltan", "ore_alexandrite", "ore_limestone",
    "ore_gneiss_iron", "ore_gneiss_gold", "ore_gneiss_copper", "ore_gneiss_asbestos", "ore_gneiss_lithium",
    "ore_gneiss_uranium", "ore_gneiss_rare", "ore_gneiss_gas",
    "cluster_iron", "cluster_titanium", "cluster_aluminium", "cluster_copper",
    "deposit_iron", "deposit_titanium", "deposit_tungsten", "deposit_cinnabar", "deposit_zirconium",
    "deposit_borax", "deposit_coltan",
]

SWITCH = "overworld_ores"
CONDITIONS = [{"type": "neoforge:mod_loaded", "modid": "hbm"}]


def copy(placed):
    """La copie d'une placed feature : le meme placement, sans l'interrupteur des minerais d'overworld."""
    placement = []
    for modifier in placed["placement"]:
        if modifier.get("type") == "hbm:world_config":
            switches = [name for name in modifier.get("switches", []) if name != SWITCH]
            if not switches:
                continue
            modifier = dict(modifier, switches=switches)
        placement.append(modifier)
    return {"neoforge:conditions": CONDITIONS, "feature": placed["feature"], "placement": placement}


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    os.makedirs(OUT, exist_ok=True)
    with zipfile.ZipFile(sys.argv[1]) as jar:
        for name in NAMES:
            placed = json.loads(jar.read("data/hbm/worldgen/placed_feature/%s.json" % name))
            result = copy(placed)
            with open(os.path.join(OUT, name + ".json"), "w", encoding="utf-8") as fh:
                json.dump(result, fh, indent=2, ensure_ascii=False)
                fh.write("\n")
            kept = [m["type"] for m in result["placement"]]
            print("%-22s %s" % (name, ", ".join(kept)))
    stale = sorted(set(f[:-5] for f in os.listdir(OUT) if f.endswith(".json")) - set(NAMES))
    for name in stale:
        os.remove(os.path.join(OUT, name + ".json"))
    tag = {entry["id"] for entry in json.load(open(TAG, encoding="utf-8"))["values"]}
    missing = [name for name in NAMES if "dohy:mine/hbm/" + name not in tag]
    if missing:
        sys.exit("absents du tag mine_mod_ores (relancer tools/dohy_assets.py) : " + ", ".join(missing))
    print("%d copies ecrites, toutes dans le tag" % len(NAMES))


if __name__ == "__main__":
    main()

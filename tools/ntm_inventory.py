#!/usr/bin/env python3
"""Inventaire des machines de NTM NEXT, NTM NEXT: Space et NTM: Cursed (chapitre 23).

Lit les trois jars comme des archives (rien n'est execute) et ecrit
tools/data/ntm_machines.json : chaque bloc fonctionnel (pas les minerais, les
blocs decoratifs, les fluides, les pieces de multibloc), avec son mod, son nom
anglais et son nom francais quand le mod en donne un.

    python3 -I tools/ntm_inventory.py <hbm.jar> <hbmspace.jar> <leafia.jar>

Les jars ne sont pas dans le depot : ils viennent du dossier mods du client.
L'inventaire, lui, y est, et tools/check_tech_catalogue.py le lit.
"""
import json
import pathlib
import re
import sys
import zipfile

OUT = pathlib.Path("tools/data/ntm_machines.json")

# Ce qui n'est pas une machine : minerais, decor, fluides, pieces de structure.
DECOR = re.compile(
    r"^(ore_|cluster_|concrete|brick_|deco_|stone_|letter_|lightstone|glass|sand|stalac|stalag|meteor_(?!diverter)|"
    r"reinforced_|steel_(beam|grate|wall|scaffold|roof|corner|poles|slab|stairs)|ducrete|waste_|crate_|cm_|rail_|"
    r"barbed|gas_(?!dock)|radio_|multiblock_|asphalt|basalt|gneiss|depth_|dirt|frozen_|pink_|pvc_|rubber_|laythe_|duna_|"
    r"eve_|ike_|minmus_|moho_|moon_|dres_|tile_lab|fence|block_|ash_|plant_dead|fire_|balefire|digamma|corium|"
    r"fallout|foam|burning|acid_block|chlorine|geysir|glyphid|crashed_|dungeon_|filing_|bobblehead|book_|boat|"
    r"boxcar|broadcaster|custom_machine|catwalk|light_|elevator_light|crimson_|lwrwreck|"
    r"modular_turbine_.*_(glass|smooth)|seal_|silo_hatch|vinyl|astroturf|ladder|lattice|primed|eu_log|bf_log|flesh|"
    r"carbonized|charred|salted|det_cord|barricade|cmb_|test_|slop|mercury|bromine|dry_ice|ferric|fluoride|"
    r"leafia_concrete|balecorium|baleonite|digammitite|broof|ccl_|impact_|leaves_layer|logic_block|mush|mud_|"
    r"obj_tester|oil_spill|part_emitter|pedestal|platemetal|plushie|rad_lava|rebar|sellafield|skeleton|slag|"
    r"snowglobe|statue|taint|tektite|toxic|vent_|vine_|volcan|wood_|schrabidic|sulfuric|spotlight_beam|"
    r"floodlight_beam|gravel_|crystal_|ancient_scrap|lantern|lamp_|trapdoor|water_door|round_airlock|qe_|"
    r"secure_access|sliding_seal|transition_seal|large_vehicle|cargo_door|fire_door|door_(metal|office|red|fuckoff)|"
    r"control_torch|elevator_(s6floor|skyliftfloor)|hev_battery|tape_recorder|radiobox|fan$|fireworks|"
    r"oil_barrel|pet_leaves|plant_tall_laythe|plant_flower_(mint|strawberry)|sapling|hydrobay_end_cell|"
    r"capacitor_complex|propulsion_creative|stirling_creative|fusion_klystron_creative|dfc_cemitter|"
    r"machine_battery$|machine_fensu|machine_turbine$|machine_large_turbine|machine_boiler_off|"
    r"machine_difurnace_rtg|machine_reactor$|bm_power_box|control_panel_custom|door_bunker_|"
    r"machine_stirling_creative)")


def names(archive, namespace, language):
    try:
        return json.loads(archive.read(f"assets/{namespace}/lang/{language}.json"))
    except KeyError:
        return {}


def label(table, namespace, block):
    return table.get(f"block.{namespace}.{block}", table.get(f"tile.{block}.name", ""))


def main(paths):
    if len(paths) != 3:
        print(__doc__)
        return 2
    rows = []
    for path, mod, namespace in zip(paths, ("NTM NEXT", "NTM NEXT: Space", "NTM: Cursed"),
                                    ("hbm", "hbmspace", "leafia")):
        archive = zipfile.ZipFile(path)
        english = names(archive, namespace, "en_us")
        french = names(archive, namespace, "fr_fr")
        prefix = f"assets/{namespace}/blockstates/"
        for entry in sorted(archive.namelist()):
            if not entry.startswith(prefix) or not entry.endswith(".json"):
                continue
            block = entry[len(prefix):-5]
            if DECOR.match(block):
                continue
            rows.append({"id": f"{namespace}:{block}", "mod": mod, "en": label(english, namespace, block),
                         "fr": label(french, namespace, block)})
    OUT.write_text(json.dumps(rows, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    counts = {}
    for row in rows:
        counts[row["mod"]] = counts.get(row["mod"], 0) + 1
    print(f"{len(rows)} machines : {counts} -> {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

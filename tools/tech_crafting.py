#!/usr/bin/env python3
"""Les technologies de la nation se fabriquent-elles dans l'ordre ? (chapitre 23, lots 23e a 23i)

    python3 -I tools/tech_crafting.py <hbm.jar> [<hbmspace.jar> <leafia.jar>] [--domaine energie,industrie]

Les trois mods de NTM sont lus comme des archives (recettes et tags de leurs dossiers data), rien
n'est execute. Pour chaque palier de chaque domaine (data/tech/*.json), le script se place dans
l'etat ou le joueur peut le rechercher — le palier d'avant, ses prerequis et leurs prerequis, les
paliers d'office — et verifie :

  - que les pieces de son cout se fabriquent dans cet etat (il faut les avoir pour lancer la
    recherche) ;
  - que ses machines se fabriquent une fois le palier acquis.

Une recette se fait si sa machine (assembleuse, soudeuse, enclume du bon niveau...) est posable dans
cet etat et fabricable, si chacune de ses cases a un ingredient fabricable (une case accepte souvent
plusieurs objets, un tag), et si ses fluides coulent. Les objets du jeu de base, et ceux qu'aucune
recette ne produit (minerais, butin), sont supposes disponibles — sauf ce qui ne se trouve que sur
une autre planete, qui attend Espace III, et ce que le code de NTM fabrique (CODE_MADE : les pieces
coulees par la fonderie, les pastilles des reacteurs), qui attend sa machine. Les recettes de
demontage (« recycle ») ne comptent pas. Un fluide coule si une recette
faisable le produit, si une machine posable le tire du monde ou le chauffe (WORLD_FLUIDS : puits de
petrole, prise d'air, chaudieres), ou si rien ne le produit (le lait d'une vache) ; l'eau et la lave
se prennent au seau. Pour chaque objet qui manque, le script dit ce qui le bloque : la machine (et
son palier), l'ingredient ou le fluide.

Le jar n'est pas dans le depot : il vient du dossier mods du client, comme pour
tools/ntm_inventory.py. Code retour 1 si un palier demande ce qu'on ne peut pas encore fabriquer.
"""
import fnmatch
import json
import pathlib
import re
import sys
import zipfile

TECH = pathlib.Path("data/tech")
FREE = pathlib.Path("data/tech_free.json")

# Les recettes que l'etabli, le four ou un geste du jeu suffisent a faire.
VANILLA_TYPES = {
    "minecraft:crafting_shaped", "minecraft:crafting_shapeless", "minecraft:smelting", "minecraft:blasting",
    "minecraft:smoking", "minecraft:campfire_cooking", "minecraft:stonecutting", "minecraft:smithing_transform",
    "minecraft:crafting_transmute", "hbm:crafting_shaped", "hbm:crafting_shapeless", "hbm:cargo_shell",
    "hbm:duct_retype", "hbm:duct_untype", "hbm:grenade_assembly", "hbm:tag_result", "hbm:mku", "hbm:dyes",
    "leafia:crafting_shaped", "leafia:crafting_shapeless", "hbmspace:crafting_shaped",
    "hbmspace:crafting_shapeless",
}

# La machine de chaque type de recette de NTM (le premier nom de chaque liste suffit a la faire).
STATIONS = {
    "hbm:assembly_machine": ["hbm:machine_assembly_machine", "hbm:machine_assembly_factory"],
    "hbm:precision_assembler": ["hbm:machine_precass"],
    "hbm:soldering": ["hbm:machine_soldering_station"],
    "hbm:arc_welder": ["hbm:machine_arc_welder"],
    "hbm:press": ["hbm:machine_press", "hbm:machine_epress", "hbm:machine_conveyor_press"],
    "hbm:chemical_plant": ["hbm:machine_chemical_plant", "hbm:machine_chemical_factory"],
    "hbm:blast_furnace": ["hbm:machine_blast_furnace"],
    "hbm:shredder": ["hbm:machine_shredder"],
    "hbm:crucible": ["hbm:machine_crucible"],
    "hbm:arc_furnace": ["hbm:machine_arc_furnace"],
    "hbm:rotary_furnace": ["hbm:machine_rotary_furnace"],
    "hbm:combination_oven": ["hbm:furnace_combination"],
    "hbm:electrolyser_metal": ["hbm:machine_electrolyser"],
    "hbm:electrolyser_fluid": ["hbm:machine_electrolyser"],
    "hbm:mixer": ["hbm:machine_mixer"],
    "hbm:centrifuge": ["hbm:machine_centrifuge"],
    "hbm:crystallizer": ["hbm:machine_crystallizer"],
    "hbm:silex": ["hbm:machine_silex"],
    "hbm:cyclotron": ["hbm:machine_cyclotron"],
    "hbm:plasma_forge": ["hbm:fusion_plasma_forge"],
    "hbm:supercomputer": ["hbm:machine_supercomputer"],
    "hbm:annihilator": ["hbm:machine_annihilator"],
    "hbm:pyro_oven": ["hbm:machine_pyrooven"],
    "hbm:coker": ["hbm:machine_coker"],
    "hbm:fraction_tower": ["hbm:machine_fraction_tower"],
    "hbm:cracking_tower": ["hbm:machine_cracking_tower"],
    "hbm:liquefaction": ["hbm:machine_liquefactor"],
    "hbm:solidification": ["hbm:machine_solidifier"],
    "hbm:purex": ["hbm:machine_purex"],
    "hbm:breeder": ["hbm:fusion_breeder"],
    "hbm:radiolysis": ["hbm:machine_radiolysis"],
    "hbm:outgasser": ["hbm:rbmk_outgasser"],
    "hbm:ammo_press": ["hbm:machine_ammo_press"],
    "hbm:gas_centrifuge": ["hbm:machine_gascent"],
    "hbm:strand_caster": ["hbm:machine_strand_caster"],
    "hbm:electric_furnace": ["hbm:machine_electric_furnace"],
    "hbm:sawmill": ["hbm:machine_sawmill"],
    "hbm:rock_mill": ["hbm:machine_rockmill"],
    "hbm:exposure_chamber": ["hbm:machine_exposure_chamber"],
    "hbm:particle_accelerator": ["hbm:pa_source"],
    "hbm:fusion": ["hbm:fusion_torus"],
    "hbm:refinery": ["hbm:machine_refinery"],
    "hbm:vacuum_refinery": ["hbm:machine_vacuum_distill"],
    "hbm:hydrotreater": ["hbm:machine_hydrotreater"],
    "hbm:catalytic_reformer": ["hbm:machine_catalytic_reformer"],
    "hbm:compressor": ["hbm:machine_compressor", "hbm:machine_compressor_compact"],
    "hbmspace:alkylation": ["hbmspace:machine_alkylation"],
    "hbmspace:cryo_distill": ["hbmspace:machine_cryo_distill"],
    "hbmspace:vacuum_circuit": ["hbmspace:machine_vacuum_circuit"],
    "leafia:dfc": ["hbm:dfc_core"],
    "leafia:salt_separator": ["leafia:salt_separator"],
    "leafia:sol_blaster": ["leafia:sol_blaster"],
    "leafia:rtg_blast_furnace": ["leafia:machine_difurnace_rtg"],
}

# Le niveau de chaque enclume de NTM (BlockAnvil, ModBlocks dans le jar 1.0.5).
ANVILS = {
    "hbm:anvil_iron": 1, "hbm:anvil_lead": 1, "hbm:anvil_steel": 2, "hbm:anvil_desh": 3,
    "hbm:anvil_ferrouranium": 4, "hbm:anvil_saturnite": 5, "hbm:anvil_bismuth_bronze": 5,
    "hbm:anvil_arsenic_bronze": 5, "hbm:anvil_schrabidate": 6, "hbm:anvil_dnt": 7, "hbm:anvil_osmiridium": 8,
    "hbm:anvil_murky": 1916169,
}

# Les familles de tags communs que NeoForge remplit avec les objets du jeu de base (#c:dyes/green,
# #c:glass_panes...) : toujours remplies, quelle que soit la couleur ou la variante.
VANILLA_FAMILIES = {
    "dyes", "dyed", "glass_blocks", "glass_panes", "chests", "barrels", "stones", "cobblestones", "sands",
    "gravels", "seeds", "crops", "foods", "tools", "armors", "rods", "strings", "slime_balls", "leathers",
    "feathers", "bones", "gunpowders", "ender_pearls", "obsidians", "netherracks", "end_stones", "bricks",
    "buckets", "fences", "fence_gates", "mushrooms", "eggs", "heads", "flowers", "sandstone_blocks",
    "player_workstations", "villager_job_sites", "wooden_chests", "wooden_barrels", "wooden_rods", "wooden_fences",
    "glass", "concretes_powder", "music_discs", "enchanting_fuels", "ropes", "clumps", "shulker_boxes",
}

# Les familles de tags communs que NeoForge remplit, matiere par matiere, avec le jeu de base.
NEOFORGE_FAMILIES = {"ingots", "nuggets", "dusts", "gems", "ores", "raw_materials", "storage_blocks", "rods",
                     "raw_blocks", "ores_in_ground"}

# Les matieres du jeu de base : un tag commun de ces familles (#c:ingots/iron) qui les nomme est rempli.
VANILLA_MATERIALS = {
    "iron", "gold", "copper", "coal", "diamond", "emerald", "lapis", "redstone", "quartz", "netherite",
    "glowstone", "amethyst", "obsidian", "stone", "cobblestone", "sand", "glass", "wooden", "wood", "planks",
    "string", "leather", "bone", "gunpowder", "slime", "slime_ball", "ender_pearl", "blaze", "blaze_rod",
    "feather", "flint", "clay", "brick", "bricks", "netherrack", "end_stone", "gravel", "ice", "snow",
    "wheat", "sugar", "paper", "book", "logs", "log", "sticks", "rods", "wool", "dyes", "eggs", "seeds",
    "crops", "mushrooms", "nether_star", "prismarine", "glass_panes", "chests", "barrels", "fences",
    "fence_gates", "storage_blocks", "nuggets", "raw_materials", "ores", "gems", "dusts", "ingots",
    "deepslate", "sandstone", "granite", "diorite", "andesite", "tuff", "calcite", "basalt", "blackstone",
}


# Les fluides qu'une machine tire du monde (puits, prise d'air) ou chauffe (une chaudiere change
# l'eau en vapeur et le petrole brut en petrole chaud), et que les donnees des recettes ignorent : le
# code de NTM les produit. L'eau et la lave se prennent au seau.
WORLD_FLUIDS = {
    "hbm:oil": (["hbm:machine_well", "hbm:machine_pumpjack", "hbm:machine_fracking_tower"], None),
    "hbm:gas": (["hbm:machine_well", "hbm:machine_pumpjack", "hbm:machine_fracking_tower"], None),
    "hbm:air": (["hbm:machine_intake"], None),
    "hbm:heavywater": (["hbm:machine_deuterium_extractor", "hbm:machine_deuterium_tower"], None),
    "hbm:steam": (["hbm:machine_boiler", "hbm:machine_industrial_boiler", "hbm:machine_solar_boiler",
                   "leafia:hp_boiler"], "minecraft:water"),
    "hbm:hotoil": (["hbm:machine_boiler", "hbm:machine_industrial_boiler"], "hbm:oil"),
    "hbm:hotcrackoil": (["hbm:machine_boiler", "hbm:machine_industrial_boiler"], "hbm:crackoil"),
    "hbm:hotoil_ds": (["hbm:machine_boiler", "hbm:machine_industrial_boiler"], "hbm:oil_ds"),
    "hbm:hotcrackoil_ds": (["hbm:machine_boiler", "hbm:machine_industrial_boiler"], "hbm:crackoil_ds"),
}
FREE_FLUIDS = {"minecraft:water", "minecraft:lava", "minecraft:empty"}

# Les planetes de HBM Space (data/hbmspace/dimension) : ce qui ne s'y trouve qu'elles attend les
# fusees, Espace III. Leurs generateurs sont dans le code (« hbmspace:rubber_plant »), pas dans les
# donnees : les matieres premieres d'une planete se reconnaissent a leur nom (ore_iron_duna), les
# forets de Tekto (caoutchouc, vinyle, PVC) sont nommees ici, et ce que seuls des blocs de planete
# donnent quand on les casse (leur table de butin) en vient aussi.
PLANETS = ("dres", "duna", "eve", "ike", "laythe", "minmus", "moho", "mun", "tekto", "thatmo", "orbit")
SPACE_ONLY = tuple(f"hbmspace:*_{p}" for p in PLANETS) + tuple(f"hbmspace:{p}_*" for p in PLANETS) + (
    "hbmspace:rubber_*", "hbmspace:leaf_rubber", "hbmspace:vinyl_*", "hbmspace:pvc_*", "hbmspace:sapling_pvc",
    "hbmspace:sapling_vinyl", "hbmspace:moon_rock")
SPACE_GATE = ("espace", 3)

# Ce que le code de NTM fabrique sans recette dans les donnees, et la machine qu'il y faut : la fonderie
# coule plaques, fils epais et pieces d'arme dans ses moules, et ses alliages (ferro-uranium...) ; les
# reacteurs usent leur combustible en pastilles. Seulement pour un objet qu'aucune recette ne produit.
CODE_MADE = {
    "hbm:plate_cast_*": "hbm:machine_crucible", "hbm:wire_dense_*": "hbm:machine_crucible",
    "hbm:part_mechanism_*": "hbm:machine_crucible", "hbm:part_barrel_*": "hbm:machine_crucible",
    "hbm:part_receiver_*": "hbm:machine_crucible", "hbm:part_grip_*": "hbm:machine_crucible",
    "hbm:ingot_*": "hbm:machine_crucible",
    "hbm:pile_rod_*": "hbm:pile_block", "hbm:rod_zirnox_*": "hbm:reactor_zirnox",
    "hbm:rbmk_pellet_*": "hbm:rbmk_fuel_rod", "hbmspace:rbmk_pellet_*": "hbm:rbmk_fuel_rod",
    "hbm:pwr_fuel_*": "hbm:pwr_fuel", "hbm:watz_pellet_*": "hbm:watz", "hbmspace:watz_pellet_*": "hbm:watz",
    "hbm:pellet_rtg_depleted_*": "hbm:machine_rtg_grey", "hbm:blade_*": "hbm:machine_crucible",
    "hbm:bedrock_ore_fragment_*": "hbm:machine_ore_slopper", "hbm:arc_electrode_burnt_*": "hbm:machine_arc_furnace",
}


def load_json(zipped, name):
    try:
        return json.loads(zipped.read(name))
    except (ValueError, KeyError):
        return None


def alternatives(value):
    """Ce qui remplit une case de recette (objets ou « #tag ») : l'un ou l'autre suffit."""
    if value is None:
        return ()
    if isinstance(value, str):
        return (value,)
    if isinstance(value, list):
        result = []
        for item in value:
            result.extend(alternatives(item))
        return tuple(dict.fromkeys(result))
    if isinstance(value, dict):
        if value.get("neoforge:ingredient_type") == "hbm:fluid_content":
            return ("~" + value["fluid"],) if "fluid" in value else ()
        for key in ("ingredient", "ingredients", "children", "base", "items"):
            if key in value:
                return alternatives(value[key])
        if "item" in value:
            return (value["item"],)
        if "tag" in value:
            return ("#" + value["tag"],)
        if "id" in value:
            return (value["id"],)
    return ()


def slots(value):
    """Les cases d'une liste d'ingredients : chaque element en est une."""
    if value is None:
        return []
    if isinstance(value, list):
        return [alternatives(item) for item in value]
    return [alternatives(value)]


def output_ids(value):
    if value is None:
        return []
    if isinstance(value, str):
        return [value]
    if isinstance(value, list):
        result = []
        for item in value:
            result.extend(output_ids(item))
        return result
    if isinstance(value, dict):
        if "data" in value:
            return output_ids(value["data"])
        if "id" in value:
            return [value["id"]]
        if "item" in value:
            return [value["item"]]
    return []


class Book:
    """Les recettes et les tags des trois mods."""

    def __init__(self, jars):
        self.recipes = {}
        self.fluids = {}
        self.tags = {}
        self.unknown = {}
        dropped_by = {}
        for jar in jars:
            with zipfile.ZipFile(jar) as zipped:
                for name in zipped.namelist():
                    if not name.endswith(".json"):
                        continue
                    parts = name.split("/")
                    if len(parts) == 5 and parts[0] == "data" and parts[2] == "loot_table" and parts[3] == "blocks":
                        block = parts[1] + ":" + parts[4][:-5]
                        text = zipped.read(name).decode("utf-8", "replace")
                        for item in set(re.findall(r'"name"\s*:\s*"([a-z0-9_]+:[a-z0-9_/]+)"', text)):
                            dropped_by.setdefault(item, set()).add(block)
                        continue
                    if len(parts) >= 4 and parts[0] == "data" and parts[2] == "tags" and parts[3] in ("item", "items"):
                        data = load_json(zipped, name)
                        if data:
                            tag = parts[1] + ":" + "/".join(parts[4:])[:-5]
                            values = []
                            for entry in data.get("values", []):
                                values.append(entry["id"] if isinstance(entry, dict) else entry)
                            self.tags.setdefault(tag, set()).update(values)
                    elif len(parts) >= 4 and parts[0] == "data" and parts[2] == "recipe":
                        data = load_json(zipped, name)
                        if data:
                            self.add(data, name)
        # Ce que ne donnent que des blocs d'autres planetes (la feuille de sel des plantes de Laythe).
        self.space = {item for item, blocks in dropped_by.items() if item.startswith("hbmspace:")
                      and all(any(fnmatch.fnmatch(b, p) for p in SPACE_ONLY) for b in blocks)}

    def add(self, data, name):
        kind = data.get("type", "")
        base = name.rsplit("/", 1)[-1]
        if ".package." in base or ".unpackage." in base or base.endswith("emptypackage.json") or "recycle" in base:
            # Mettre un fluide en paquet et l'en sortir, demonter une machine deja faite : rien de neuf.
            return
        inputs = []
        outputs = []
        tier = 0
        if kind.endswith("crafting_shaped"):
            inputs = [alternatives(value) for value in data.get("key", {}).values()]
            outputs = output_ids(data.get("result"))
        elif kind.endswith("crafting_shapeless"):
            inputs = slots(data.get("ingredients"))
            outputs = output_ids(data.get("result"))
        elif kind in ("minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking",
                      "minecraft:stonecutting"):
            inputs = [alternatives(data.get("ingredient"))]
            outputs = output_ids(data.get("result"))
        elif kind in ("minecraft:smithing_transform", "minecraft:crafting_transmute"):
            inputs = [alternatives(data.get(key)) for key in ("template", "base", "addition", "input", "material")
                      if key in data]
            outputs = output_ids(data.get("result"))
        else:
            for key in ("input_items", "pcb", "solder", "inputs", "ingredients", "input"):
                inputs.extend(slots(data.get(key)))
            for key in ("output_items", "outputs", "output", "result", "results"):
                outputs.extend(output_ids(data.get(key)))
            tier = int(data.get("tier", 0) or 0)
        if kind not in VANILLA_TYPES and kind not in STATIONS and kind not in ("hbm:anvil_construction",
                                                                               "hbm:anvil_smithing"):
            self.unknown[kind] = self.unknown.get(kind, 0) + 1
        fluids_in = tuple(dict.fromkeys(f["type"] for f in data.get("input_fluids", []) or []
                                        if isinstance(f, dict) and "type" in f))
        fluids_out = [f["type"] for f in data.get("output_fluids", []) or [] if isinstance(f, dict) and "type" in f]
        # Une case vide (une forme inconnue) ne demande rien.
        recipe = (kind, tier, tuple(dict.fromkeys(slot for slot in inputs if slot)), fluids_in, name)
        for output in dict.fromkeys(outputs):
            self.recipes.setdefault(output, []).append(recipe)
        for output in dict.fromkeys(fluids_out):
            self.fluids.setdefault(output, []).append(recipe)

    def members(self, tag, seen=None):
        seen = seen or set()
        if tag in seen:
            return set()
        seen.add(tag)
        result = set()
        for value in self.tags.get(tag, ()):
            if value.startswith("#"):
                result |= self.members(value[1:], seen)
            else:
                result.add(value)
        return result


class Tree:
    """Les domaines, leurs paliers, les machines verrouillees."""

    def __init__(self):
        self.domains = {}
        self.lock = {}
        for path in sorted(TECH.glob("*.json")):
            domain = json.loads(path.read_text(encoding="utf-8"))
            self.domains[domain["id"]] = domain
            for tier in domain["tiers"]:
                for machine in tier.get("machines", []):
                    self.lock.setdefault(machine["id"], (domain["id"], tier["tier"]))
        self.free = json.loads(FREE.read_text(encoding="utf-8")).get("patterns", [])

    def tier(self, domain, number):
        for tier in self.domains[domain]["tiers"]:
            if tier["tier"] == number:
                return tier
        return None

    def office(self):
        reached = set()
        for domain in self.domains.values():
            for tier in domain["tiers"]:
                if tier.get("start"):
                    reached.add((domain["id"], tier["tier"]))
        return reached

    def closure(self, needs):
        """Tout ce qu'il faut avoir pour tenir ces paliers : les paliers d'avant, et leurs prerequis."""
        reached = self.office()
        stack = list(needs)
        while stack:
            domain, number = stack.pop()
            for level in range(1, number + 1):
                key = (domain, level)
                if key in reached:
                    continue
                reached.add(key)
                tier = self.tier(domain, level)
                for need in tier.get("requires", []):
                    name, _, value = need.partition(":")
                    stack.append((name, int(value)))
        return reached


def available(book, tree, reached):
    """Ce qu'on fabrique dans cet etat : point fixe sur les recettes (objets et fluides)."""
    have = set()
    flowing = set(FREE_FLUIDS)
    in_space = SPACE_GATE in reached

    def item_ok(identifier):
        if identifier.startswith("~"):
            return fluid_ok(identifier[1:])
        if identifier.startswith("#"):
            tag = identifier[1:]
            if tag.startswith("minecraft:"):
                return True
            members = book.members(tag)
            if any(item_ok(member) for member in members if not member.startswith("#")):
                return True
            if not tag.startswith("c:"):
                return False
            path = tag[2:].split("/")
            if path[0] in VANILLA_FAMILIES:
                return True
            # #c:ingots/iron : NeoForge y range le lingot du jeu de base ; #c:plates/iron n'a que ceux de NTM.
            return (path[0] in NEOFORGE_FAMILIES and any(part in VANILLA_MATERIALS for part in path[1:])) or (
                    len(path) == 1 and path[0] in VANILLA_MATERIALS)
        if identifier.startswith("minecraft:") or identifier in have:
            return True
        if identifier in book.recipes:
            return False
        for pattern, machine in CODE_MADE.items():
            if fnmatch.fnmatch(identifier, pattern):
                return placed(machine)
        return in_space or not (identifier in book.space
                                or any(fnmatch.fnmatch(identifier, pattern) for pattern in SPACE_ONLY))

    def placed(machine):
        lock = tree.lock.get(machine)
        return (lock is None or lock in reached) and item_ok(machine)

    def fluid_ok(fluid):
        if fluid in flowing:
            return True
        if fluid in WORLD_FLUIDS:
            return False
        # Un fluide qu'aucune recette ne produit vient du monde (le lait d'une vache...).
        return fluid not in book.fluids

    def station_ok(kind, tier):
        if kind in VANILLA_TYPES:
            return True
        if kind == "hbm:anvil_construction" or kind == "hbm:anvil_smithing":
            stations = [anvil for anvil, level in ANVILS.items() if level >= max(1, tier)]
        else:
            stations = STATIONS.get(kind)
        if not stations:
            return False
        return any(placed(station) for station in stations)

    def doable(recipe):
        kind, tier, inputs, fluids_in, _ = recipe
        return (station_ok(kind, tier) and all(any(item_ok(i) for i in slot) for slot in inputs)
                and all(fluid_ok(f) for f in fluids_in))

    changed = True
    while changed:
        changed = False
        for fluid, (sources, feed) in WORLD_FLUIDS.items():
            if fluid not in flowing and (feed is None or fluid_ok(feed)) and any(placed(m) for m in sources):
                flowing.add(fluid)
                changed = True
        for output, recipes in book.recipes.items():
            if output not in have and any(doable(recipe) for recipe in recipes):
                have.add(output)
                changed = True
        for fluid, recipes in book.fluids.items():
            if fluid not in flowing and any(doable(recipe) for recipe in recipes):
                flowing.add(fluid)
                changed = True
    return have, item_ok, station_ok, fluid_ok


def why(book, tree, reached, tools, identifier):
    """Ce qui bloque un objet : la machine qui manque (et son palier), l'ingredient ou le fluide."""
    _, item_ok, station_ok, fluid_ok = tools
    recipes = book.recipes.get(identifier, [])
    reasons = []
    for kind, tier, inputs, fluids_in, _ in recipes:
        if not station_ok(kind, tier):
            if kind in ("hbm:anvil_construction", "hbm:anvil_smithing"):
                stations = [anvil for anvil, level in ANVILS.items() if level >= max(1, tier)]
                label = f"enclume de niveau {tier}"
            else:
                stations = STATIONS.get(kind, [])
                label = kind
            locks = sorted({tree.lock[s] for s in stations if s in tree.lock and tree.lock[s] not in reached})
            needs = ", ".join(f"{d} {t}" for d, t in locks) or "fabrication de la machine"
            reasons.append(f"{label} ({needs})")
            continue
        missing = ["|".join(slot[:2]) for slot in inputs if not any(item_ok(i) for i in slot)]
        missing += [f"fluide {f}" for f in fluids_in if not fluid_ok(f)]
        if not missing:
            return []
        reasons.append("il manque " + ", ".join(missing[:3]))
    return reasons[:3]


def main(argv):
    jars = [a for a in argv if not a.startswith("--")]
    only = None
    for arg in argv:
        if arg.startswith("--domaine"):
            only = set(arg.split("=", 1)[1].split(",")) if "=" in arg else None
    if not jars:
        print(__doc__)
        return 2
    book = Book(jars)
    tree = Tree()
    print(f"{sum(len(r) for r in book.recipes.values())} recettes pour {len(book.recipes)} objets, "
          f"{len(book.tags)} tags ; types sans machine connue : "
          + (", ".join(f"{k} {v}" for k, v in sorted(book.unknown.items())) or "aucun"))
    cache = {}
    problems = 0
    for domain_id, domain in tree.domains.items():
        if only and domain_id not in only:
            continue
        for tier in domain["tiers"]:
            number = tier["tier"]
            needs = [(domain_id, number - 1)] if number > 1 else []
            for need in tier.get("requires", []):
                name, _, value = need.partition(":")
                needs.append((name, int(value)))
            before = frozenset(tree.closure(needs))
            after = frozenset(tree.closure(needs + [(domain_id, number)]))
            for state in (before, after):
                if state not in cache:
                    cache[state] = available(book, tree, set(state))
            lines = []
            item_ok = cache[before][1]
            for item in tier.get("cost", {}).get("items", []):
                if not item_ok(item["id"]):
                    lines.append(f"  cout : {item['id']} -> " + " ; ".join(
                        why(book, tree, before, cache[before], item["id"])))
            item_ok = cache[after][1]
            for machine in tier.get("machines", []):
                identifier = machine["id"]
                if any(fnmatch.fnmatch(identifier, f) for f in tree.free):
                    continue
                if identifier.startswith("dohy:"):
                    continue
                if not item_ok(identifier):
                    lines.append(f"  machine : {identifier} -> " + " ; ".join(
                        why(book, tree, after, cache[after], identifier)))
            status = "ok" if not lines else f"{len(lines)} probleme(s)"
            print(f"{domain_id} {number} ({tier['name']}) : {status}")
            for line in lines:
                print(line)
            problems += len(lines)
    print(f"{problems} probleme(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

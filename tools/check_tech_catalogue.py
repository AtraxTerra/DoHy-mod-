#!/usr/bin/env python3
"""Verifie les arbres de technologie de la nation (chapitre 23, docs/NATION.md).

    python3 -I tools/check_tech_catalogue.py [--orphans]

Les neuf domaines vivent dans data/tech/<domaine>.json (data/dohy/tech/ dans le
jar), les machines laissees libres dans data/tech_free.json.

- chaque motif deverrouille au moins une machine connue : l'inventaire des mods
  (tools/data/ntm_machines.json, ecrit par tools/ntm_inventory.py) ou un bloc,
  un objet de DoHy ; les blocs DoHy a venir sont annonces, pas refuses ;
- une machine n'est deverrouillee que par un seul palier ;
- la liste « machines » d'un palier (les noms affiches quand le mod manque) est
  exactement ce que ses motifs designent ;
- un prerequis nomme un domaine et un palier qui existent, et jamais le palier
  lui-meme ni un palier plus haut du meme domaine ; pas de boucle entre domaines ;
- un domaine repris de l'ancien arbre (Gouvernement, Economie, Defense) range
  chaque ancien palier une fois, dans l'ordre ; un autre domaine donne un cout
  (argent et points de recherche) a chaque palier qui n'est pas acquis d'office ;
- les bonus ont un type connu, une valeur positive et un libelle ;
- aucun texte affiche n'a d'accent (les menus n'en portent pas).

Avec --orphans : la liste des machines que l'arbre ne range encore nulle part
(hors de la liste « free » : plantes, ruines, fluides, decor laisses libres).
Code retour 1 et une ligne ::error:: par probleme.
"""
import fnmatch
import json
import pathlib
import sys

TECH = pathlib.Path("data/tech")
FREE = pathlib.Path("data/tech_free.json")
INVENTORY = pathlib.Path("tools/data/ntm_machines.json")
ASSETS = pathlib.Path("src/main/resources/assets/dohy")

# Les blocs DoHy que l'arbre annonce avant qu'ils existent, et le lot qui les fera.
FUTURE = {}

# Les bonus que le moteur connait (faction/tech/TechPerks.java).
BONUSES = {"labs", "parallel", "research_per_country", "market_tax", "mines_money", "lab_energy",
           "machine_energy", "miner_money", "mines_ore", "vault_slots", "research", "oil_fields",
           "farmer_xp", "agri_speed", "farmer_money", "nuclear_loot", "soldier_hp"}

# Les anciennes branches et leur nombre de paliers (faction/FactionTree.java).
LEGACY = {"global": 12, "defense": 12, "pillage": 12, "economy": 6}


def ascii_errors(where, value, errors):
    if isinstance(value, str):
        if any(ord(char) > 127 for char in value):
            errors.append(f"{where} : accent dans « {value} »")
    elif isinstance(value, list):
        for item in value:
            ascii_errors(where, item, errors)
    elif isinstance(value, dict):
        for item in value.values():
            ascii_errors(where, item, errors)


def main(argv):
    domains = []
    for path in sorted(TECH.glob("*.json")):
        domain = json.loads(path.read_text(encoding="utf-8"))
        if domain.get("id") != path.stem:
            print(f"::error::{path} : l'identifiant doit etre « {path.stem} »")
            return 1
        domains.append(domain)
    domains.sort(key=lambda d: d.get("order", 0))
    inventory = {row["id"]: row for row in json.loads(INVENTORY.read_text(encoding="utf-8"))}
    dohy = {"dohy:" + p.stem for p in (ASSETS / "blockstates").glob("*.json")}
    dohy |= {"dohy:" + p.stem for p in (ASSETS / "items").glob("*.json")}
    known = set(inventory) | dohy
    errors = []
    owner = {}
    tiers = {d["id"]: {t["tier"] for t in d["tiers"]} for d in domains}
    legacy_seen = {}
    nodes = 0
    for domain in domains:
        did = domain["id"]
        ascii_errors(did, {k: v for k, v in domain.items() if k != "tiers"}, errors)
        numbers = [t["tier"] for t in domain["tiers"]]
        if numbers != list(range(1, len(numbers) + 1)):
            errors.append(f"{did} : paliers {numbers}, il faut 1, 2, 3... dans l'ordre")
        merged = any(t.get("legacy") for t in domain["tiers"])
        for tier in domain["tiers"]:
            nodes += 1
            where = f"{did}:{tier['tier']}"
            ascii_errors(where, tier.get("name", ""), errors)
            expected = []
            for pattern in tier.get("unlocks", []):
                if pattern in FUTURE:
                    print(f"a venir ({FUTURE[pattern]}) : {pattern} ({where})")
                    expected.append(pattern)
                    continue
                matched = sorted(fnmatch.filter(known, pattern))
                if not matched:
                    errors.append(f"{where} : « {pattern} » ne nomme aucune machine connue")
                for machine in matched:
                    if machine in owner and owner[machine] != where:
                        errors.append(f"{machine} deverrouille deux fois : {owner[machine]} et {where}")
                    owner.setdefault(machine, where)
                    if machine not in expected:
                        expected.append(machine)
            listed = [m["id"] for m in tier.get("machines", [])]
            for machine in tier.get("machines", []):
                if not machine.get("name"):
                    errors.append(f"{where} : {machine['id']} sans nom")
                ascii_errors(where, machine.get("name", ""), errors)
            if sorted(listed) != sorted(expected):
                missing = sorted(set(expected) - set(listed))
                extra = sorted(set(listed) - set(expected))
                errors.append(f"{where} : liste des machines a refaire (manquent {missing[:5]}, en trop {extra[:5]})")
            for need in tier.get("requires", []):
                name, _, level = need.partition(":")
                if name not in tiers or not level.isdigit() or int(level) not in tiers[name]:
                    errors.append(f"{where} : prerequis inconnu « {need} »")
                elif name == did and int(level) >= tier["tier"]:
                    errors.append(f"{where} : prerequis « {need} » dans son propre domaine, pas en dessous")
            for bonus in tier.get("bonuses", []):
                if bonus.get("type") not in BONUSES:
                    errors.append(f"{where} : bonus inconnu « {bonus.get('type')} »")
                if not isinstance(bonus.get("value"), int) or bonus["value"] <= 0:
                    errors.append(f"{where} : bonus {bonus.get('type')} sans valeur positive")
                if not bonus.get("label"):
                    errors.append(f"{where} : bonus {bonus.get('type')} sans libelle")
                ascii_errors(where, bonus.get("label", ""), errors)
            if merged:
                if tier.get("cost"):
                    errors.append(f"{where} : un domaine repris se paie palier par palier de l'ancien arbre, pas de cout")
                for step in tier.get("legacy", []):
                    branch, _, number = step.partition(":")
                    if branch not in LEGACY or not number.isdigit() or not 1 <= int(number) <= LEGACY[branch]:
                        errors.append(f"{where} : ancien palier inconnu « {step} »")
                        continue
                    if step in legacy_seen:
                        errors.append(f"{where} : ancien palier « {step} » deja range en {legacy_seen[step]}")
                    legacy_seen[step] = where
            elif not tier.get("start"):
                cost = tier.get("cost")
                if not cost or cost.get("money", 0) <= 0 or cost.get("points", 0) <= 0:
                    errors.append(f"{where} : palier sans cout (argent et points de recherche)")
                elif "hours" in cost:
                    errors.append(f"{where} : « hours » n'existe plus, les laboratoires font des points (lot 23c)")
                else:
                    for item in cost.get("items", []):
                        if item.get("count", 0) <= 0 or not item.get("label") or ":" not in item.get("id", ""):
                            errors.append(f"{where} : piece de cout incomplete {item}")
                        ascii_errors(where, item.get("label", ""), errors)
    for branch, count in LEGACY.items():
        for number in range(1, count + 1):
            step = f"{branch}:{number}"
            if step not in legacy_seen:
                errors.append(f"ancien palier « {step} » range nulle part : il serait perdu")
        order = [legacy_seen.get(f"{branch}:{n}") for n in range(1, count + 1)]
        levels = [int(where.split(":")[1]) for where in order if where]
        if levels != sorted(levels):
            errors.append(f"anciens paliers « {branch} » dans le desordre : {order}")
    # Pas de boucle entre domaines : un palier ne peut pas, de proche en proche, se demander lui-meme.
    graph = {}
    for domain in domains:
        for tier in domain["tiers"]:
            node = (domain["id"], tier["tier"])
            needs = [(n.partition(":")[0], int(n.partition(":")[2])) for n in tier.get("requires", [])
                     if n.partition(":")[2].isdigit()]
            if tier["tier"] > 1:
                needs.append((domain["id"], tier["tier"] - 1))
            graph[node] = needs
    state = {}

    def visit(node, path):
        if state.get(node) == 1:
            errors.append("boucle de prerequis : " + " -> ".join(f"{d}:{t}" for d, t in path + [node]))
            return
        if state.get(node) == 2:
            return
        state[node] = 1
        for need in graph.get(node, []):
            visit(need, path + [node])
        state[node] = 2

    for node in graph:
        visit(node, [])
    free = json.loads(FREE.read_text(encoding="utf-8")).get("patterns", [])
    for pattern in free:
        if not fnmatch.filter(known, pattern):
            errors.append(f"libre : « {pattern} » ne nomme aucune machine connue")
    orphans = sorted(m for m in inventory if m not in owner and not any(fnmatch.fnmatch(m, f) for f in free))
    by_mod = {}
    for machine in orphans:
        by_mod.setdefault(inventory[machine]["mod"], []).append(machine)
    print(f"{len(domains)} domaines, {nodes} paliers, {len(owner)} machines rangees, {len(legacy_seen)} anciens"
          f" paliers repris ; pas encore rangees : "
          + (", ".join(f"{mod} {len(ids)}" for mod, ids in sorted(by_mod.items())) or "aucune"))
    if "--orphans" in argv:
        for mod, ids in sorted(by_mod.items()):
            print(f"== {mod}")
            for machine in ids:
                row = inventory[machine]
                print(f"  {machine}  ({row['fr'] or row['en']})")
    for error in errors:
        print(f"::error::{error}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

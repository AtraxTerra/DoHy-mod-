#!/usr/bin/env python3
"""Verifie le brouillon des arbres de technologie (chapitre 23, docs/NATION.md).

    python3 -I tools/check_tech_catalogue.py [--orphans]

- chaque motif deverrouille au moins une machine connue : l'inventaire des mods
  (tools/data/ntm_machines.json, ecrit par tools/ntm_inventory.py) ou un bloc,
  un objet de DoHy ; les blocs DoHy a venir sont annonces, pas refuses ;
- une machine n'est deverrouillee que par un seul palier ;
- un prerequis nomme un domaine et un palier qui existent, et jamais le palier
  lui-meme ni un palier plus haut du meme domaine.

Avec --orphans : la liste des machines que l'arbre ne range encore nulle part
(hors de la liste « free » : plantes, ruines, fluides, decor laisses libres).
Code retour 1 et une ligne ::error:: par probleme.
"""
import fnmatch
import json
import pathlib
import sys

CATALOGUE = pathlib.Path("tools/data/tech_catalogue.json")
INVENTORY = pathlib.Path("tools/data/ntm_machines.json")
ASSETS = pathlib.Path("src/main/resources/assets/dohy")

# Les blocs DoHy que l'arbre annonce avant qu'ils existent, et le lot qui les fera.
FUTURE = {"dohy:laboratoire": "23c"}


def main(argv):
    catalogue = json.loads(CATALOGUE.read_text(encoding="utf-8"))
    inventory = {row["id"]: row for row in json.loads(INVENTORY.read_text(encoding="utf-8"))}
    dohy = {"dohy:" + p.stem for p in (ASSETS / "blockstates").glob("*.json")}
    dohy |= {"dohy:" + p.stem for p in (ASSETS / "items").glob("*.json")}
    known = set(inventory) | dohy
    errors = []
    owner = {}
    tiers = {}
    for domain in catalogue["domains"]:
        tiers[domain["id"]] = {tier["tier"] for tier in domain["tiers"]}
    nodes = 0
    for domain in catalogue["domains"]:
        for tier in domain["tiers"]:
            nodes += 1
            where = f"{domain['id']}:{tier['tier']}"
            for pattern in tier.get("unlocks", []):
                if pattern in FUTURE:
                    print(f"a venir ({FUTURE[pattern]}) : {pattern} ({where})")
                    continue
                matched = fnmatch.filter(known, pattern)
                if not matched:
                    errors.append(f"{where} : « {pattern} » ne nomme aucune machine connue")
                for machine in matched:
                    if machine in owner and owner[machine] != where:
                        errors.append(f"{machine} deverrouille deux fois : {owner[machine]} et {where}")
                    owner.setdefault(machine, where)
            for need in tier.get("requires", []):
                name, _, level = need.partition(":")
                if name not in tiers or not level.isdigit() or int(level) not in tiers[name]:
                    errors.append(f"{where} : prerequis inconnu « {need} »")
                elif name == domain["id"] and int(level) >= tier["tier"]:
                    errors.append(f"{where} : prerequis « {need} » dans son propre domaine, pas en dessous")
    free = catalogue.get("free", {}).get("patterns", [])
    for pattern in free:
        if not fnmatch.filter(known, pattern):
            errors.append(f"libre : « {pattern} » ne nomme aucune machine connue")
    orphans = sorted(m for m in inventory if m not in owner and not any(fnmatch.fnmatch(m, f) for f in free))
    by_mod = {}
    for machine in orphans:
        by_mod.setdefault(inventory[machine]["mod"], []).append(machine)
    print(f"{len(catalogue['domains'])} domaines, {nodes} paliers, {len(owner)} machines rangees ;"
          f" pas encore rangees : " + ", ".join(f"{mod} {len(ids)}" for mod, ids in sorted(by_mod.items())))
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
